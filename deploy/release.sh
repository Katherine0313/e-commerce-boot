#!/usr/bin/env bash
# 构建并推送服务镜像（8 个 Java 服务 + 1 个 Python ai-service），可选通过 SSH 部署。
# common 是依赖库，不能单独部署。
# 详见 deploy/README.md 第 11 节。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="${DEPLOY_HOST:-ecommerce}"
REMOTE_BASE="${REMOTE_BASE:-/www/java}"
# auth-service 排最后：它停止签发 shopId 后，旧版本服务会拒绝新 token，必须等其它服务先升级
ALL_SERVICES=(product-service order-service aftersale-service user-service
              file-service gateway ai-service auth-service)
AI_DIR="${AI_SERVICE_DIR:-$ROOT/../recommender-python}"

NO_BUILD=0
NO_DEPLOY=0
NO_AI=0
DRY_RUN=0
ROLLBACK_TAG=""
POSITIONAL=()

usage() {
  cat <<'EOF'
用法:
  ./deploy/release.sh [选项] <service...|all>

选项:
  --no-build          跳过 Maven 打包（使用现有 target jar）
  --no-deploy         只构建/推送，不通过 SSH 部署
  --no-ai             使用 all 时排除 ai-service
  --dry-run           只打印命令，不执行
  --rollback <tag>    回滚到指定 tag（不构建、不推送，只拉取并重启）
  -h, --help          显示帮助

环境变量:
  REGISTRY     镜像仓库地址/命名空间（必填，或写在 deploy/.env）
  TAG          镜像版本（默认：日期-git短SHA）
  DEPLOY_HOST  SSH 别名或 user@host（默认 ecommerce）
  PLATFORM     可选，如 linux/amd64
  AI_SERVICE_DIR  recommender-python 目录（默认 ../recommender-python）

示例:
  ./deploy/release.sh order-service
  ./deploy/release.sh order-service product-service
  ./deploy/release.sh all
  ./deploy/release.sh --dry-run all
  ./deploy/release.sh --rollback 20261027-abc1234 order-service
  # 全量发布，跳过 ai-service
  ./deploy/release.sh --no-ai all
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1; shift ;;
    --no-deploy) NO_DEPLOY=1; shift ;;
    --no-ai) NO_AI=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --rollback)
      [[ $# -ge 2 && -n "$2" ]] || { echo "--rollback 需要指定 tag" >&2; exit 1; }
      ROLLBACK_TAG="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "未知选项: $1" >&2; usage; exit 1 ;;
    *) POSITIONAL+=("$1"); shift ;;
  esac
done

[[ ${#POSITIONAL[@]} -gt 0 ]] || { usage; exit 1; }

SERVICES=()
for arg in "${POSITIONAL[@]}"; do
  if [[ "$arg" == "all" ]]; then
    SERVICES=("${ALL_SERVICES[@]}")
    break
  fi
  SERVICES+=("$arg")
done

if [[ $NO_AI -eq 1 ]]; then
  FILTERED=()
  for svc in "${SERVICES[@]}"; do
    [[ "$svc" == "ai-service" ]] || FILTERED+=("$svc")
  done
  SERVICES=("${FILTERED[@]}")
  [[ ${#SERVICES[@]} -gt 0 ]] || { echo "--no-ai 后没有可发布的服务" >&2; exit 1; }
fi

for svc in "${SERVICES[@]}"; do
  if [[ ! " ${ALL_SERVICES[*]} " =~ " $svc " ]]; then
    echo "不支持的服务: $svc" >&2
    echo "可选: ${ALL_SERVICES[*]}" >&2
    echo "提示: common 是依赖库，不能单独部署" >&2
    exit 1
  fi
done

# REGISTRY：优先环境变量，其次 deploy/.env（本地没有 .env 时直接 export）
REGISTRY="${REGISTRY:-}"
if [[ -z "$REGISTRY" && -f "$ROOT/deploy/.env" ]]; then
  REGISTRY="$(grep -E '^REGISTRY=' "$ROOT/deploy/.env" | tail -n1 \
    | cut -d= -f2- | sed 's/\r$//' | tr -d '"' | tr -d "'")"
fi
if [[ -z "$REGISTRY" ]]; then
  echo "未配置 REGISTRY。请 export REGISTRY=... 或在 deploy/.env 写入 REGISTRY=..." >&2
  exit 1
fi
if [[ "$REGISTRY" == *"your-namespace"* ]]; then
  echo "REGISTRY 还是占位值，请换成 TCR 控制台里的仓库地址/命名空间" >&2
  exit 1
fi
REGISTRY="${REGISTRY%/}"

if [[ -n "$ROLLBACK_TAG" ]]; then
  TAG="$ROLLBACK_TAG"
else
  if git -C "$ROOT" rev-parse --short HEAD >/dev/null 2>&1; then
    COMMIT="$(git -C "$ROOT" rev-parse --short HEAD)"
    if [[ -n "$(git -C "$ROOT" status --porcelain)" ]]; then
      COMMIT="${COMMIT}-dirty"
      echo "警告: 工作区有未提交改动，镜像 TAG 会带 -dirty" >&2
    fi
    DEFAULT_TAG="$(date +%Y%m%d)-$COMMIT"
  else
    DEFAULT_TAG="$(date +%Y%m%d%H%M%S)"
  fi
  TAG="${TAG:-$DEFAULT_TAG}"
fi

if [[ $DRY_RUN -eq 0 ]]; then
  if ! docker info >/dev/null 2>&1; then
    echo "Docker 未启动，请先启动 Docker Desktop" >&2
    exit 1
  fi
fi

# 部署前确认：SSH 可用，且服务器 compose 已同步 image/REGISTRY 配置
if [[ $DRY_RUN -eq 0 && $NO_DEPLOY -eq 0 ]]; then
  if ! ssh -o BatchMode=yes -o ConnectTimeout=8 "$SERVER" \
      "grep -q 'REGISTRY' '$REMOTE_BASE/docker-compose.yml'" 2>/dev/null; then
    echo "无法确认服务器 compose 或 SSH 未配置，请先检查：" >&2
    echo "  1) SSH 免密：ssh $SERVER true" >&2
    echo "  2) 同步 compose：scp deploy/docker-compose.yml $SERVER:$REMOTE_BASE/docker-compose.yml" >&2
    exit 1
  fi
fi

run() {
  if [[ $DRY_RUN -eq 1 ]]; then
    printf '[dry-run] '
    printf '%q ' "$@"
    printf '\n'
  else
    "$@"
  fi
}

# 把每个服务固定到当前运行的镜像 tag，写进服务器 docker-compose.override.yml。
# 之后直接 docker compose up -d（不带 TAG）也会按各自镜像启动，不会拿全局 TAG 去拉没发布过的服务。
sync_override() {
  if [[ $DRY_RUN -eq 1 ]]; then
    echo "[dry-run] ssh $SERVER '重新生成 $REMOTE_BASE/docker-compose.override.yml'"
    return
  fi
  echo "==> 同步 docker-compose.override.yml（固定各服务当前镜像）"
  ssh "$SERVER" "cd '$REMOTE_BASE' && bash -s" <<'REMOTE_SCRIPT'
set -e
{
  echo '# 由 deploy/release.sh 自动生成：把每个服务固定到当前运行的镜像 tag'
  echo '# 目的：单服务发布后，直接 docker compose up -d 不会拿全局 TAG 去拉其它服务'
  echo 'services:'
  for svc in product-service order-service aftersale-service user-service file-service ai-service gateway auth-service; do
    img=$(docker inspect -f '{{.Config.Image}}' "$svc" 2>/dev/null || true)
    if [ -n "$img" ]; then
      printf '  %s:\n    image: %s\n' "$svc" "$img"
    fi
  done
} > docker-compose.override.yml
REMOTE_SCRIPT
}

MVN="mvn"
if [[ -x "$ROOT/mvnw" ]]; then
  MVN="$ROOT/mvnw"
fi

JAVA_TO_BUILD=()
for svc in "${SERVICES[@]}"; do
  [[ "$svc" == "ai-service" ]] || JAVA_TO_BUILD+=("$svc")
done

if [[ -z "$ROLLBACK_TAG" && $NO_BUILD -eq 0 && ${#JAVA_TO_BUILD[@]} -gt 0 ]]; then
  PL_LIST="$(IFS=,; echo "${JAVA_TO_BUILD[*]}")"
  echo "==> Maven 打包: $PL_LIST"
  run "$MVN" -pl "$PL_LIST" -am -DskipTests package
fi

if [[ -z "$ROLLBACK_TAG" ]]; then
  for svc in "${SERVICES[@]}"; do
    if [[ "$svc" == "ai-service" ]]; then
      # Python 服务：用 recommender-python 自己的 Dockerfile
      CONTEXT="$AI_DIR"
      DOCKERFILE="$AI_DIR/Dockerfile"
      if [[ ! -f "$DOCKERFILE" || ! -d "$AI_DIR/app" ]]; then
        echo "找不到 ai-service 源码或 Dockerfile：$AI_DIR" >&2
        echo "可用 AI_SERVICE_DIR 指定 recommender-python 目录" >&2
        exit 1
      fi
    else
      # Java 服务：jar 在 target/ 下，context 用 target
      CONTEXT="$ROOT/$svc/target"
      DOCKERFILE="$ROOT/deploy/Dockerfile"
      JAR="$(ls -1t "$CONTEXT/$svc"-*.jar 2>/dev/null | head -n1 || true)"
      if [[ -z "$JAR" ]]; then
        echo "找不到 $svc 的 jar：$CONTEXT/$svc-*.jar" >&2
        echo "请先打包，或去掉 --no-build" >&2
        exit 1
      fi
    fi
    IMAGE="$REGISTRY/$svc:$TAG"
    echo "==> 构建 $svc -> $IMAGE"
    BUILD_ARGS=(docker build -f "$DOCKERFILE" -t "$IMAGE")
    if [[ -n "${PLATFORM:-}" ]]; then
      BUILD_ARGS+=(--platform "$PLATFORM")
    fi
    BUILD_ARGS+=("$CONTEXT")
    run "${BUILD_ARGS[@]}"
    echo "==> 推送 $IMAGE"
    run docker push "$IMAGE"
  done
fi

if [[ $NO_DEPLOY -eq 1 ]]; then
  echo "==> 已跳过部署（--no-deploy）"
  exit 0
fi

for svc in "${SERVICES[@]}"; do
  echo "==> 部署 $svc (TAG=$TAG)"
  # 只 pull 目标镜像；--no-deps 防止 gateway 的 depends_on 带着其它服务按新 tag 去拉
  # -f docker-compose.yml：部署时只认 TAG，不吃 override 里固定的旧镜像
  REMOTE_CMD="cd '$REMOTE_BASE' && docker pull '$REGISTRY/$svc:$TAG' && REGISTRY='$REGISTRY' TAG='$TAG' docker compose -f docker-compose.yml up -d --no-build --no-deps '$svc'"
  run ssh "$SERVER" "$REMOTE_CMD"
  if [[ $DRY_RUN -eq 0 ]]; then
    sleep 2
    STATUS="$(ssh "$SERVER" "docker inspect -f '{{.State.Status}}' '$svc' 2>/dev/null" || true)"
    if [[ -n "$STATUS" ]]; then
      echo "    $svc: $STATUS"
    else
      echo "    警告: 没查到 $svc 容器，请到服务器执行 docker logs $svc 查看" >&2
    fi
  fi
  # 每部署成功一个就落一次 override：后续服务失败也不会让已发布的版本在下次全量 up 时回退
  sync_override
done

echo "==> 完成"
