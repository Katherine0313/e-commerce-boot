#!/bin/bash
# 商品扩容前的生产库备份：bash backup-prod-db-product-expand.sh
set -euo pipefail

cd /www/java
set -a
. ./.env
set +a

mkdir -p /www/backup
OUT="/www/backup/e_commerce_db_before_product_expand_$(date +%F_%H%M).sql"

OPTS="--single-transaction --routines --triggers --default-character-set=utf8mb4"
if mysqldump --no-tablespaces --version >/dev/null 2>&1; then
  OPTS="$OPTS --no-tablespaces"
fi

echo "备份到: $OUT"
if ! mysqldump -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" $OPTS --result-file="$OUT" e_commerce_db; then
  echo "备份失败，请检查错误；不要继续执行 SQL"
  exit 1
fi
if ! grep -q "Dump completed" "$OUT"; then
  echo "备份文件不完整，不要继续执行 SQL"
  exit 1
fi
ls -lh "$OUT"
echo "备份完成且校验通过"
