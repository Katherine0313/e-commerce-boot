#!/bin/bash
# 在生产服务器上执行指定 SQL 文件：bash run-sql-prod.sh /www/java/sql/xxx.sql
# 读取 /www/java/.env 的数据库账号，自动连本机 MySQL。
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "用法: bash run-sql-prod.sh <sql文件路径>"
  exit 1
fi

cd /www/java
set -a
. ./.env
set +a

mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" --default-character-set=utf8mb4 e_commerce_db -e "source $1"
