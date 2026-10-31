#!/usr/bin/env bash
# 线上站点健康巡检：检查 C 端商城、后台管理端、小程序 API 是否正常。
#
# 关键点：商城首页 **不做 cache-buster**，请求的就是用户实际访问的那个 URL。
# 2026-10-31 商城首页 404 的根因是 EdgeOne 边缘缓存里存着旧的 nginx 404 页面，
# 源站一直是好的 —— 只有按用户视角检查才能发现这类"源站好、线上坏"的问题。
# 接口类检查会加随机参数绕过缓存，确保测的是当前真实响应。
#
# 告警：设置 HEALTHCHECK_WEBHOOK（企业微信/钉钉机器人地址）即可推送；
# 未设置时只写日志和状态文件。
# 详见 deploy/README.md 第 13 节。
set -uo pipefail

# 检查项：名称|URL|期望出现的字符串|是否绕过缓存(1=是)
CHECKS=(
  "商城首页|https://www.xiaojiecpn.com/|杰米商城|0"
  "商城商品接口|https://www.xiaojiecpn.com/api/products/page?pageNum=1&pageSize=1|\"success\":true|1"
  "商城静态资源|https://www.xiaojiecpn.com/index.html|<title>杰米商城|1"
  "后台管理端|https://www.jiemids.com/|杰米多多电商后台管理系统|1"
  "小程序首页接口|https://api.jiemids.com/api/home|\"success\":true|1"
  "小程序商品接口|https://api.jiemids.com/api/products?pageNum=1&pageSize=1|\"success\":true|1"
)

TIMEOUT="${HEALTHCHECK_TIMEOUT:-15}"
LOG_FILE="${HEALTHCHECK_LOG:-/www/logs/healthcheck.log}"
STATUS_FILE="${HEALTHCHECK_STATUS:-/www/logs/healthcheck.status}"
WEBHOOK="${HEALTHCHECK_WEBHOOK:-}"
STATE_DIR="${HEALTHCHECK_STATE:-/www/logs}"
# 连续失败几次才告警，避免网络抖动误报
FAIL_THRESHOLD="${HEALTHCHECK_FAIL_THRESHOLD:-2}"

INSTALL_CRON=0
UNINSTALL_CRON=0

usage() {
  cat <<'EOF'
用法:
  ./deploy/healthcheck.sh [选项]

选项:
  --install-cron    在本机安装 cron 巡检（每 5 分钟一次），仅在服务器上执行
  --uninstall-cron  移除 cron 巡检
  -h, --help         显示帮助

环境变量:
  HEALTHCHECK_WEBHOOK       企业微信/钉钉机器人地址（可选，设了才推送告警）
  HEALTHCHECK_LOG           日志文件（默认 /www/logs/healthcheck.log）
  HEALTHCHECK_STATUS        状态文件（默认 /www/logs/healthcheck.status）
  HEALTHCHECK_TIMEOUT       单个检查超时秒数（默认 15）
  HEALTHCHECK_FAIL_THRESHOLD  连续失败几次才告警（默认 2）

示例:
  ./deploy/healthcheck.sh                # 巡检一次
  ./deploy/healthcheck.sh --install-cron # 服务器上装定时巡检
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install-cron) INSTALL_CRON=1; shift ;;
    --uninstall-cron) UNINSTALL_CRON=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

SELF="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"

# cron 里没有环境变量，允许在脚本同目录放 healthcheck.env 配告警 webhook
# 例：HEALTHCHECK_WEBHOOK="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
if [[ -f "$(dirname "$SELF")/healthcheck.env" ]]; then
  # shellcheck disable=SC1091
  source "$(dirname "$SELF")/healthcheck.env"
  # env 文件里的值优先于脚本默认值
  WEBHOOK="${HEALTHCHECK_WEBHOOK:-$WEBHOOK}"
fi

install_cron() {
  mkdir -p "$STATE_DIR"
  # cron 的 PATH 很短，curl 可能找不到，这里显式带上
  local cron_line="*/5 * * * * PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin $SELF >/dev/null 2>&1"
  ( crontab -l 2>/dev/null | grep -v "healthcheck.sh" ; echo "$cron_line" ) | crontab -
  echo "已安装 cron: $cron_line"
  crontab -l | grep healthcheck
}

uninstall_cron() {
  crontab -l 2>/dev/null | grep -v "healthcheck.sh" | crontab -
  echo "已移除 healthcheck cron"
}

if [[ $INSTALL_CRON -eq 1 ]]; then
  install_cron
  exit 0
fi
if [[ $UNINSTALL_CRON -eq 1 ]]; then
  uninstall_cron
  exit 0
fi

# ---------------------------------------------------------------- 巡检主体

log_line() {
  local line
  line="$(date '+%Y-%m-%d %H:%M:%S') $1"
  echo "$line"
  if [[ -n "$LOG_FILE" ]]; then
    mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null || true
    echo "$line" >> "$LOG_FILE"
  fi
}

# 推送企业微信/钉钉机器人
notify() {
  local content="$1"
  [[ -z "$WEBHOOK" ]] && return 0
  # 企业微信与钉钉的 text 消息体一致；失败只记日志，不影响巡检结果
  local payload
  payload=$(printf '{"msgtype":"text","text":{"content":"%s"}}' \
    "$(printf '%s' "$content" | sed 's/\\/\\\\/g; s/"/\\"/g' | tr '\n' ' ')")
  curl -s -m 10 -X POST -H 'Content-Type: application/json' -d "$payload" "$WEBHOOK" >/dev/null 2>&1 \
    || log_line "WARN 告警推送失败（webhook 返回异常）"
}

FAILED=0
REPORT=""
TS=$(date +%s)

for item in "${CHECKS[@]}"; do
  IFS='|' read -r name url expect bust <<< "$item"
  target="$url"
  if [[ "$bust" == "1" ]]; then
    # 加时间戳绕过边缘缓存，确保测到的是当前真实响应
    if [[ "$url" == *"?"* ]]; then
      target="${url}&_hc=${TS}"
    else
      target="${url}?_hc=${TS}"
    fi
  fi

  body=$(curl -s -m "$TIMEOUT" -H 'Cache-Control: no-cache' -w '\n__STATUS__%{http_code}' "$target" 2>/dev/null)
  code=$(printf '%s' "$body" | sed -n 's/.*__STATUS__//p' | tail -1)
  body=$(printf '%s' "$body" | sed 's/\n__STATUS__[0-9]*$//')

  if [[ "$code" != "200" ]]; then
    log_line "FAIL $name http=$code 期望200 url=$url"
    FAILED=$((FAILED + 1))
    REPORT="${REPORT}❌ ${name}: HTTP ${code}\n"
    continue
  fi
  if ! printf '%s' "$body" | grep -q -- "$expect"; then
    log_line "FAIL $name 内容不含 '$expect' url=$url"
    FAILED=$((FAILED + 1))
    REPORT="${REPORT}❌ ${name}: 内容异常（不含 ${expect}）\n"
    continue
  fi
  log_line "OK   $name http=$code"
done

mkdir -p "$(dirname "$STATUS_FILE")" 2>/dev/null || true

if [[ $FAILED -eq 0 ]]; then
  echo "0" > "$STATUS_FILE"
  rm -f "${STATE_DIR}/healthcheck.failcount" 2>/dev/null || true
  exit 0
fi

# 连续失败计数：第一次失败只记录，第二次才告警，避免抖动误报
COUNT_FILE="${STATE_DIR}/healthcheck.failcount"
count=$(cat "$COUNT_FILE" 2>/dev/null || echo 0)
count=$((count + 1))
echo "$count" > "$COUNT_FILE"

if [[ "$count" -ge "$FAIL_THRESHOLD" ]]; then
  echo "FAIL" > "$STATUS_FILE"
  notify "【线上巡检告警】杰米商城 ${FAILED} 项异常（连续失败 ${count} 次）
${REPORT}
时间：$(date '+%Y-%m-%d %H:%M:%S')"
  log_line "ALERT 连续失败 ${count} 次，已推送告警"
else
  echo "WARN" > "$STATUS_FILE"
  log_line "WARN 失败 ${count}/${FAIL_THRESHOLD}，达到阈值后才告警"
fi

exit 1
