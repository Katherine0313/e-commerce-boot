#!/usr/bin/env python3
"""按「名称 + 卖点 + 参数 + 官方正品」重写所有商品的主标题 title。

只读数据库，输出 docs/sql/product-expand/05-fix-titles.sql。
运行：DB_USERNAME=... DB_PASSWORD=... ../recommender-python/.venv/Scripts/python.exe fix_titles.py
"""
import json
import os
import sys
from pathlib import Path

try:
    import pymysql
except ImportError:
    sys.exit("缺少 pymysql，请用 recommender-python/.venv/Scripts/python.exe 运行")

DB = dict(
    host=os.getenv("DB_HOST", "127.0.0.1"), port=int(os.getenv("DB_PORT", "3306")),
    user=os.getenv("DB_USERNAME", "e_commerce_db"), password=os.getenv("DB_PASSWORD", ""),
    database=os.getenv("DB_NAME", "e_commerce_db"), charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
)
OUT = Path(__file__).resolve().parents[1] / "05-fix-titles.sql"
CURATION_FILE = Path(__file__).resolve().parents[1] / "product_curation.json"
CURATION = json.loads(CURATION_FILE.read_text(encoding="utf-8")) if CURATION_FILE.exists() else {}


def sql_str(value):
    return "NULL" if value is None else "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def clean(text):
    return (text or "").replace("，", " ").replace(",", " ").replace("。", " ").strip()


def make_title(name, subtitle):
    # 只拼名称 + 卖点，不再追加 specs 数值（随机参数会导致标题自相矛盾）
    parts = [name, clean(subtitle), "官方正品", "现货速发"]
    # 去空 + 去重（保持顺序）
    result, seen = [], set()
    for p in parts:
        if p and p not in seen:
            seen.add(p)
            result.append(p)
    return " ".join(result)


def main():
    if not DB["password"]:
        sys.exit("请通过环境变量 DB_PASSWORD 提供数据库密码")
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, name, sub_title, specs FROM products ORDER BY id")
            rows = cur.fetchall()
    finally:
        conn.close()

    lines = ["-- 重写商品 title：名称 + 卖点 + 参数 + 官方正品", "SET NAMES utf8mb4;", ""]
    for r in rows:
        selling = CURATION.get(str(r["id"]), {}).get("selling") or r["sub_title"]
        title = make_title(r["name"], selling)
        lines.append(f"UPDATE products SET title = {sql_str(title)} WHERE id = {r['id']};")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"生成 {len(rows)} 条 title 更新：{OUT}")


if __name__ == "__main__":
    main()
