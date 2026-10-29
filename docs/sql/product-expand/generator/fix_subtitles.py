#!/usr/bin/env python3
"""按淘宝风格重写商品副标题 sub_title：销量数字 + 可开发票 + 品类口碑短语。

原则：一句话、短、抓转化（销量/口碑/服务），用「 | 」分隔，总长控制在 30 字内。
营销数字按商品 id 稳定生成，重复运行结果一致。

只读数据库，输出 docs/sql/product-expand/07-fix-subtitles.sql。
运行：DB_USERNAME=... DB_PASSWORD=... ../recommender-python/.venv/Scripts/python.exe fix_subtitles.py
"""
import os
import random
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
OUT = Path(__file__).resolve().parents[1] / "07-fix-subtitles.sql"

# 营销销量区间（只用于演示文案，不是真实销量）
SALES_RANGE = {
    "手机": (600, 6000),
    "电脑": (200, 2000),
    "平板": (300, 3000),
    "耳机音箱": (800, 9000),
    "手表": (400, 4000),
    "相机": (80, 800),
    "数码配件": (1500, 20000),
    "智能家居": (600, 12000),
}


def sql_str(value):
    return "NULL" if value is None else "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def fmt_count(n):
    """1200 → 1200+，12800 → 1万+，450 → 450+"""
    if n >= 10000:
        return f"{max(1, round(n / 10000))}万"
    if n >= 1000:
        return str(int(n // 100 * 100))
    return str(int(n // 50 * 50))


def pick_phrase(name, cat):
    """按品类 + 商品名关键词挑一句口碑/卖点短语。"""
    n = name.lower()
    if cat == "手机":
        if "iphone" in n:
            return '多人评价"外观精致"'
        if "华为" in name:
            return "信号续航好评多"
        return "性能影像获好评"
    if cat == "电脑":
        if "macbook" in n:
            return "性能续航双在线"
        if any(k in n for k in ("拯救者", "rog", "游匣", "暗影精灵", "魔霸")):
            return "高刷电竞屏好评多"
        if any(k in n for k in ("thinkpad", "latitude")):
            return "办公稳定好评多"
        return "轻薄便携口碑好"
    if cat == "平板":
        return "学习办公皆宜" if "ipad" in n else "大屏影音好评多"
    if cat == "耳机音箱":
        if any(k in n for k in ("qc", "wh-", "ultra")):
            return "降噪效果获好评"
        if any(k in n for k in ("airpods", "freebuds", "linkbuds", "wf-", "真无线")):
            return "佩戴舒适好评多"
        return "音质震撼好评多"
    if cat == "手表":
        return "户外运动好评多" if any(k in n for k in ("ultra", "forerunner", "gt")) else "健康监测口碑好"
    if cat == "相机":
        return "摄影玩家好评多"
    if cat == "数码配件":
        if any(k in n for k in ("鼠标", "键盘")):
            return "办公游戏皆宜"
        if any(k in n for k in ("硬盘", "sd卡", "tf卡", "u盘", "读卡器")):
            return "读写稳定好评多"
        if any(k in n for k in ("充电器", "充电宝", "移动电源")):
            return "快充安全好评多"
        if "壳" in n:
            return "手感防摔好评多"
        if any(k in n for k in ("扩展坞", "hub")):
            return "接口丰富好评多"
        return "正品保障好评多"
    if cat == "智能家居":
        if "扫地" in n or "扫拖" in n:
            return "解放双手好评多"
        if "摄像头" in n:
            return "家庭安防好评多"
        if any(k in n for k in ("传感器", "门磁")):
            return "智能联动更省心"
        if any(k in n for k in ("灯", "灯泡", "灯带", "吸顶灯")):
            return "氛围感获好评"
        if "开关" in n:
            return "远程控制更省心"
        return "语音控制更省心"
    return "正品保障好评多"


def build(pid, name, cat):
    """生成一条副标题：三种句式轮换，避免整店文案千篇一律。"""
    rnd = random.Random(pid * 7919)
    low, high = SALES_RANGE.get(cat, (300, 3000))
    sales = fmt_count(rnd.randint(low, high))
    cart = fmt_count(rnd.randint(low * 3, high * 5))
    phrase = pick_phrase(name, cat)
    # 句式轮换：已售 → 加购 → 短语前置；用「 | 」分隔，小程序/PC 展示比中文逗号清晰
    tpl = pid % 3
    if tpl == 0:
        return f"已售{sales}+ | 可开发票 | {phrase}"
    if tpl == 1:
        return f"可开发票 | {phrase} | 超{cart}人加购"
    return f"{phrase} | 已售{sales}+ | 可开发票"


def main():
    if not DB["password"]:
        sys.exit("请通过环境变量 DB_PASSWORD 提供数据库密码")
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT p.id, p.name, COALESCE(c2.name, c.name, '') AS cat
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN categories c2 ON c.parent_id = c2.id
                ORDER BY p.id
            """)
            rows = cur.fetchall()
    finally:
        conn.close()

    lines = ["-- 按淘宝风格重写商品副标题：销量 + 可开发票 + 品类口碑（营销数字，非真实销量）",
             "SET NAMES utf8mb4;", ""]
    for r in rows:
        sub = build(r["id"], r["name"], r["cat"])
        lines.append(f"UPDATE products SET sub_title = {sql_str(sub)} WHERE id = {r['id']};")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"生成 {len(rows)} 条副标题更新：{OUT}")


if __name__ == "__main__":
    main()
