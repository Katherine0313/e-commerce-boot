#!/usr/bin/env python3
"""按人工核对过的 product_curation.json 重写商品 title / specs。

数据来源：../product_curation.json（id -> selling + specs）
产物：../12-fix-product-titles-specs.sql
运行：
  DB_USERNAME=... DB_PASSWORD=... ../recommender-python/.venv/Scripts/python.exe apply_curation.py
加 --apply 直接写本地库。

规则：
- title = 商品名 + 卖点 + 官方正品 + 现货速发，不再拼接 specs 数值
- 只更新 1~126；127~136（机械表）是手写数据，保持不动
- 写入前做品牌/品类一致性校验，发现矛盾直接退出
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

try:
    import pymysql
except ImportError:
    sys.exit("缺少 pymysql，请用 recommender-python/.venv/Scripts/python.exe 运行")

ROOT = Path(__file__).resolve().parents[1]
CURATION = ROOT / "product_curation.json"
OUT = ROOT / "12-fix-product-titles-specs.sql"

DB = dict(
    host=os.getenv("DB_HOST", "127.0.0.1"),
    port=int(os.getenv("DB_PORT", "3306")),
    user=os.getenv("DB_USERNAME", "e_commerce_db"),
    password=os.getenv("DB_PASSWORD", ""),
    database=os.getenv("DB_NAME", "e_commerce_db"),
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
)

APPLE = re.compile(r"苹果|iPhone|iPad|MacBook|AirPods|Watch|HomePod")
NON_APPLE_CHIP = re.compile(r"骁龙|麒麟|天玑|Ryzen|酷睿|Ultra [579]|i[579]-|RTX|核显")
M_CHIP = re.compile(r"(?<![A-Za-z0-9])M[1-4](?:\s?(?:Pro|Max))?")
CASE_IDS = set(range(79, 85))       # 手机壳不能有接口
SENSOR_IDS = {111, 112}            # 门磁/人体传感器不能有摄像头参数
SWITCH_IDS = {118}                 # 智能开关不能有色温


def sql_str(value):
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def make_title(name, selling):
    parts = [name, selling, "官方正品", "现货速发"]
    result, seen = [], set()
    for p in parts:
        p = (p or "").strip()
        if p and p not in seen:
            seen.add(p)
            result.append(p)
    return " ".join(result)


def validate(rows, curation):
    """返回错误列表；空列表表示通过。"""
    errors, missing = [], []
    for pid in range(1, 127):
        if str(pid) not in curation:
            missing.append(pid)
    if missing:
        errors.append(f"缺少 {len(missing)} 个商品的数据: {missing}")

    by_id = {r["id"]: r for r in rows}
    for pid_s, item in curation.items():
        pid = int(pid_s)
        row = by_id.get(pid)
        if not row:
            errors.append(f"{pid}: 数据库里不存在")
            continue
        selling = item.get("selling", "")
        specs = item.get("specs") or []
        if not selling.strip():
            errors.append(f"{pid}: selling 为空")
        if not (3 <= len(specs) <= 7):
            errors.append(f"{pid}: specs 数量 {len(specs)} 不在 3~7")
        text = selling + " " + " ".join(s["value"] for s in specs)
        name = row["name"]
        if APPLE.search(name) and NON_APPLE_CHIP.search(text):
            errors.append(f"{pid} {name}: 苹果设备出现非苹果芯片 -> {text}")
        if "MacBook" not in name and "苹果" not in name and M_CHIP.search(text):
            errors.append(f"{pid} {name}: 非苹果设备出现 M 系芯片 -> {text}")
        if pid in CASE_IDS and "接口" in " ".join(s["name"] for s in specs):
            errors.append(f"{pid} {name}: 手机壳出现接口字段")
        if pid in SENSOR_IDS and re.search(r"分辨率|夜视|摄像头", text):
            errors.append(f"{pid} {name}: 传感器出现摄像头参数")
        if pid in SWITCH_IDS and "色温" in " ".join(s["name"] for s in specs):
            errors.append(f"{pid} {name}: 智能开关出现色温字段")
        for s in specs:
            if not s.get("name") or not str(s.get("value", "")).strip():
                errors.append(f"{pid}: 存在空字段 {s}")
    return errors


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="生成 SQL 后直接写本地库")
    args = ap.parse_args()

    if not CURATION.exists():
        sys.exit(f"缺少数据文件 {CURATION}")
    curation = json.loads(CURATION.read_text(encoding="utf-8"))

    if not DB["password"]:
        sys.exit("请通过环境变量 DB_PASSWORD 提供数据库密码")
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, name, brand, category_id FROM products ORDER BY id")
            rows = cur.fetchall()
    finally:
        conn.close()

    errors = validate(rows, curation)
    if errors:
        print("校验失败，未生成 SQL：")
        for e in errors:
            print("  -", e)
        sys.exit(1)

    lines = [
        "-- 人工核对后的商品 title / specs 修正（id 1~126）；127~136 机械表为手写数据，不在此范围",
        "-- 由 generator/apply_curation.py 根据 product_curation.json 生成",
        "SET NAMES utf8mb4;",
        "",
    ]
    for pid in range(1, 127):
        item = curation[str(pid)]
        row = next(r for r in rows if r["id"] == pid)
        title = make_title(row["name"], item["selling"])
        specs = json.dumps(item["specs"], ensure_ascii=False, separators=(",", ":"))
        lines.append(
            f"UPDATE products SET title = {sql_str(title)}, specs = {sql_str(specs)} WHERE id = {pid};"
        )
    lines += ["", "-- 验证：标题里不应再出现被拼接的参数尾部，specs 不为空", "SELECT COUNT(*) AS total FROM products;"]

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"已生成 {OUT}（126 条）")

    if args.apply:
        conn = pymysql.connect(**DB)
        try:
            with conn.cursor() as cur:
                cur.execute("SET NAMES utf8mb4")
                for pid in range(1, 127):
                    item = curation[str(pid)]
                    row = next(r for r in rows if r["id"] == pid)
                    title = make_title(row["name"], item["selling"])
                    specs = json.dumps(item["specs"], ensure_ascii=False, separators=(",", ":"))
                    cur.execute(
                        "UPDATE products SET title=%s, specs=%s WHERE id=%s",
                        (title, specs, pid),
                    )
            conn.commit()
        finally:
            conn.close()
        print("已写入本地库 126 条")


if __name__ == "__main__":
    main()
