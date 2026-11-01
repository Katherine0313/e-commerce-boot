#!/bin/bash
# 生产库备份脚本：在服务器上执行（bash backup-prod-db.sh）
# 读取 /www/java/.env 的 DB_USERNAME / DB_PASSWORD，备份到 /www/backup/
set -euo pipefail

cd /www/java
set -a
. ./.env
set +a

mkdir -p /www/backup
OUT="/www/backup/e_commerce_db_$(date +%F_%H%M).sql"

OPTS="--single-transaction --routines --triggers --default-character-set=utf8mb4"
# 8.0 默认导出表空间，普通账号没 PROCESS 权限会报错；5.7 没这个参数
if mysqldump --no-tablespaces --version >/dev/null 2>&1; then
  OPTS="$OPTS --no-tablespaces"
fi

echo "MySQL 版本: $(mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" -N -e 'SELECT VERSION()')"
echo "备份到: $OUT"

if ! mysqldump -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" $OPTS --result-file="$OUT" e_commerce_db; then
  echo "备份失败，请检查上面的错误；不要继续执行 01"
  exit 1
fi

if ! grep -q "Dump completed" "$OUT"; then
  echo "备份文件不完整（缺少 Dump completed），不要继续执行 01"
  exit 1
fi

ls -lh "$OUT"
echo "备份完成且校验通过"
