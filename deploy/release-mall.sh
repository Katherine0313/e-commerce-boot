#!/usr/bin/env bash
# 发布 PC 商城（ecommerce-mall）到 xiaojiecpn.com。
#
# 与后台管理端（../e-commerce → www.jiemids.com）共用 release-web.sh，
# 但这里把 WEB_DIR / WEB_ROOT 写死，避免两个站点搞混发错。
#
# 用法：
#   ./deploy/release-mall.sh              # 构建 + 发布
#   ./deploy/release-mall.sh --no-build   # 只发布现有 dist，不重新构建
#   ./deploy/release-mall.sh --dry-run    # 只打印命令，不执行
#   ./deploy/release-mall.sh --rollback   # 回滚到上一次发布前的备份
#
# 环境变量：
#   MALL_DIR        商城工程目录（默认 ../ecommerce-mall，可用此覆盖）
#   MALL_WEB_ROOT   服务器站点目录（默认 /www/wwwroot/xiaojiecpn.com）
#
# 详见 deploy/README.md 第 12 节。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 商城工程目录：默认与 boot 仓库同级（e-commerce-boot/../ecommerce-mall）
MALL_DIR="${MALL_DIR:-$SCRIPT_DIR/../../ecommerce-mall}"
# 服务器站点目录：宝塔建站时的目录名，改域名要同步改这里
WEB_ROOT="${MALL_WEB_ROOT:-/www/wwwroot/xiaojiecpn.com}"

# 前置检查：工程目录必须存在且是 Vue 工程，避免把空目录传上去
if [ ! -f "$MALL_DIR/package.json" ]; then
  echo "找不到商城工程：$MALL_DIR" >&2
  echo "  可用 MALL_DIR=/path/to/ecommerce-mall ./deploy/release-mall.sh 覆盖" >&2
  exit 1
fi

export WEB_DIR="$MALL_DIR"
export WEB_ROOT

echo "==> 发布目标：$WEB_DIR → $WEB_ROOT"

exec "$SCRIPT_DIR/release-web.sh" "$@"
