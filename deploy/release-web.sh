#!/usr/bin/env bash
# 构建并发布后台管理端（e-commerce）静态站点到 www.jiemids.com。
# 流程：本地 npm run build → tar 上传到服务器 /tmp → rsync 同步 → 保留 .env 等运行时文件。
# 详见 deploy/README.md 第 12 节。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="${WEB_DIR:-$ROOT/../e-commerce}"
SERVER="${DEPLOY_HOST:-ecommerce}"
WEB_ROOT="${WEB_ROOT:-/www/wwwroot/jiemids.com}"
# 备份放站点根目录之外：wwwroot 下的任何目录都能被外网访问，源码 bak 会泄露
BACKUP_ROOT="${BACKUP_ROOT:-/www/backup/web}"
KEEP_BACKUPS="${KEEP_BACKUPS:-3}"
# 站点名用于备份目录命名，如 jiemids.com.bak.20261029132723
SITE="$(basename "$WEB_ROOT")"

NO_BUILD=0
DRY_RUN=0
ROLLBACK=0

usage() {
  cat <<'EOF'
用法:
  ./deploy/release-web.sh [选项]

选项:
  --no-build     跳过 npm run build（直接发布现有 dist）
  --dry-run      只打印命令，不执行
  --rollback     回滚到最近一次发布前的备份
  -h, --help     显示帮助

环境变量:
  WEB_DIR        后台前端目录（默认 ../e-commerce）
  DEPLOY_HOST    SSH 别名或 user@host（默认 ecommerce）
  WEB_ROOT       服务器站点目录（默认 /www/wwwroot/jiemids.com）
  BACKUP_ROOT    备份目录（默认 /www/backup/web，必须在站点根目录之外）
  KEEP_BACKUPS   保留的备份份数（默认 3）

示例:
  ./deploy/release-web.sh
  ./deploy/release-web.sh --no-build
  ./deploy/release-web.sh --rollback
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --rollback) ROLLBACK=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知选项: $1" >&2; usage; exit 1 ;;
  esac
done

run() {
  if [[ $DRY_RUN -eq 1 ]]; then
    printf '[dry-run] '
    printf '%q ' "$@"
    printf '\n'
  else
    "$@"
  fi
}

# 回滚：把最近一份备份同步回站点目录，不依赖本地构建
if [[ $ROLLBACK -eq 1 ]]; then
  run ssh "$SERVER" "
    set -e
    latest=\$(ls -1dt '$BACKUP_ROOT/$SITE.bak.'* 2>/dev/null | head -n1 || true)
    if [ -z \"\$latest\" ]; then
      echo '没有可回滚的备份' >&2
      exit 1
    fi
    rsync -a --delete \\
      --exclude '.env' --exclude '.htaccess' --exclude '.user.ini' \\
      --exclude '.well-known' --exclude 'public' \\
      \"\$latest\"/ '$WEB_ROOT/'
    echo \"已回滚到: \$latest\"
  "
  exit 0
fi

if [[ $NO_BUILD -eq 0 ]]; then
  echo "==> 构建后台前端: $WEB_DIR"
  run bash -c "cd '$WEB_DIR' && npm run build"
fi

DIST="$WEB_DIR/dist"
if [[ ! -f "$DIST/index.html" ]]; then
  echo "找不到 $DIST/index.html，请先构建或去掉 --no-build" >&2
  exit 1
fi

# 发布前做硬链接快照：只占很少磁盘，用来回滚
# .user.ini 被宝塔设为不可变（chattr +i），不能硬链接，回滚也用不到，直接排除
BACKUP_TS="$(date +%Y%m%d%H%M%S)"
run ssh "$SERVER" "
  set -e
  if [ -d '$WEB_ROOT' ]; then
    mkdir -p '$BACKUP_ROOT'
    rsync -a --link-dest='$WEB_ROOT' --exclude '.user.ini' '$WEB_ROOT/' '$BACKUP_ROOT/$SITE.bak.$BACKUP_TS/'
  fi
"

echo "==> 上传 dist"
if [[ $DRY_RUN -eq 1 ]]; then
  echo "[dry-run] tar czf - -C '$DIST' . | ssh '$SERVER' '解压到 /tmp/admin-dist 后 rsync 到 $WEB_ROOT'"
else
  tar czf - -C "$DIST" . | ssh "$SERVER" "
    set -e
    rm -rf /tmp/admin-dist
    mkdir -p /tmp/admin-dist
    tar xzf - -C /tmp/admin-dist
    chown -R www:www /tmp/admin-dist
    rsync -a --delete \
      --exclude '.env' --exclude '.htaccess' --exclude '.user.ini' \
      --exclude '.well-known' --exclude 'public' \
      /tmp/admin-dist/ '$WEB_ROOT/'
    rm -rf /tmp/admin-dist
  "
fi

# 只保留最近 N 份备份
run ssh "$SERVER" "ls -1dt '$BACKUP_ROOT/$SITE.bak.'* 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm -rf"

echo "==> 完成：$WEB_ROOT（https://$(basename "$WEB_ROOT")）"
