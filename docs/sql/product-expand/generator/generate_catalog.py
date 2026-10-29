#!/usr/bin/env python3
"""生成商品扩容 SQL 与图片（不直接写数据库）。

产物：
  docs/sql/product-expand/03-backfill-specs.sql   # 现有 50 个商品补 specs
  docs/sql/product-expand/04-products-51-300.sql  # 250 个新商品
  upload/image/upload/51.webp ~ 300.webp
  upload/image/opt/upload/51.webp ~ 300.webp

运行（需 pymysql + Pillow，可用 recommender-python/.venv 的解释器）：
  DB_USERNAME=e_commerce_db DB_PASSWORD=123456 \
  python generate_catalog.py

只读数据库，用于读取现有 50 个商品；所有写入都输出成 SQL 文件。
"""

import json
import os
import random
import sys
from pathlib import Path

try:
    import pymysql
except ImportError:
    sys.exit("缺少 pymysql，请用 recommender-python/.venv/Scripts/python.exe 运行")
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[4]
OUT_DIR = Path(__file__).resolve().parents[1]
IMG_DIR = ROOT / "upload" / "image" / "upload"
OPT_DIR = ROOT / "upload" / "image" / "opt" / "upload"

DB = dict(
    host=os.getenv("DB_HOST", "127.0.0.1"),
    port=int(os.getenv("DB_PORT", "3306")),
    user=os.getenv("DB_USERNAME", "e_commerce_db"),
    password=os.getenv("DB_PASSWORD", ""),
    database=os.getenv("DB_NAME", "e_commerce_db"),
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
)

# 叶子类目：id -> (价格下限, 价格上限, 品牌, 型号)
LEAVES = {
    101: (5999, 12999, ["苹果"], ["iPhone 15", "iPhone 15 Pro", "iPhone 16", "iPhone 16 Pro", "iPhone 16 Pro Max"]),
    102: (1999, 7999, ["华为", "小米", "OPPO", "vivo", "荣耀"], ["Mate 70", "小米15", "Find X8", "X200", "Magic7"]),
    201: (6999, 15999, ["联想", "华硕", "戴尔", "惠普"], ["拯救者Y9000P", "ROG魔霸9", "游匣G16", "暗影精灵11"]),
    202: (7999, 14999, ["联想", "戴尔", "惠普", "华为"], ["ThinkPad X1 Carbon", "Latitude 7455", "EliteBook 840", "MateBook B7"]),
    203: (4999, 10999, ["苹果", "华为", "小米", "联想"], ["MacBook Air M3", "MateBook X Pro", "RedmiBook Pro 16", "小新Pro 14"]),
    301: (2999, 8999, ["苹果"], ["iPad Air", "iPad Pro", "iPad mini", "iPad 11"]),
    302: (1499, 4999, ["华为", "小米", "三星", "联想"], ["MatePad Pro", "平板7 Pro", "Tab S10", "拯救者Y700"]),
    401: (899, 3999, ["索尼", "Bose", "苹果", "华为"], ["WH-1000XM5", "QC45", "AirPods Max", "FreeBuds Studio"]),
    402: (299, 1999, ["苹果", "华为", "小米", "索尼"], ["AirPods Pro 2", "FreeBuds Pro 4", "Buds 5 Pro", "WF-1000XM5"]),
    403: (299, 2999, ["索尼", "JBL", "华为", "小米"], ["SRS-XB33", "Charge 5", "Sound Joy", "Sound Pro"]),
    501: (999, 3999, ["苹果", "华为", "小米", "OPPO"], ["Watch Series 10", "Watch 4 Pro", "Watch S4", "Watch X"]),
    502: (499, 2999, ["华为", "佳明", "小米", "高驰"], ["Watch GT 5", "Forerunner 265", "Watch S4 Sport", "Pace 3"]),
    601: (4999, 29999, ["索尼", "佳能", "尼康", "富士"], ["A7C II", "EOS R8", "Z6III", "X-T5"]),
    701: (29, 499, ["苹果", "华为", "小米", "绿联"], ["MagSafe手机壳", "硅胶保护壳", "素皮手机壳", "防摔壳"]),
    702: (49, 999, ["罗技", "联想", "绿联", "小米"], ["MX Master 3S", "USB-C扩展坞", "HDMI线", "无线鼠标"]),
    703: (99, 1999, ["三星", "闪迪", "金士顿", "西部数据"], ["T7移动固态", "Extreme Pro SD卡", "U盘", "移动硬盘"]),
    704: (39, 599, ["Anker", "小米", "绿联", "苹果"], ["65W氮化镓充电器", "移动电源3", "充电头", "数据线"]),
    801: (199, 1999, ["小米", "华为", "苹果", "天猫精灵"], ["Sound Pro", "Sound X", "HomePod mini", "智能音箱"]),
    802: (199, 2999, ["小米", "萤石", "华为", "360"], ["智能摄像头", "C6摄像头", "智选摄像头", "可视门铃"]),
    803: (49, 999, ["小米", "Yeelight", "飞利浦", "欧普"], ["米家台灯", "吸顶灯", "Hue智能灯", "智能灯泡"]),
    804: (999, 4999, ["石头", "科沃斯", "小米", "追觅"], ["G20扫拖机器人", "T30扫地机", "扫拖机器人", "X40扫地机"]),
}

# 新商品 id 段（每个叶子的新增数量与 04 方案一致）
RANGES = {
    101: (51, 61), 102: (62, 70), 201: (71, 81), 202: (82, 91), 203: (92, 101),
    301: (102, 112), 302: (113, 123), 401: (124, 134), 402: (135, 145), 403: (146, 156),
    501: (157, 167), 502: (168, 177), 601: (178, 188),
    701: (189, 202), 702: (203, 216), 703: (217, 230), 704: (231, 244),
    801: (245, 258), 802: (259, 272), 803: (273, 286), 804: (287, 300),
}

# 新商品图片代表图（复用现有 1~50 中同品类图）
REP_IMAGE = {
    101: 8, 102: 3, 201: 35, 202: 10, 203: 15, 301: 14, 302: 44,
    401: 19, 402: 18, 403: 21, 501: 47, 502: 48, 601: 50,
    701: 7, 702: 9, 703: 36, 704: 37,
    801: 21, 802: 45, 803: 46, 804: 49,
}

VARIANTS = ["标准版", "增强版", "Plus版", "Ultra版", "2025款", "尊享版"]

CHIP = {
    "苹果": "A18 Pro", "华为": "麒麟9010", "小米": "骁龙8 Gen3",
    "OPPO": "天玑9400", "vivo": "天玑9400", "荣耀": "骁龙8 Gen3",
}


def spec(name, value):
    return {"name": name, "value": str(value)}


def build_specs(category_id, brand, price, rng):
    """按叶子类目生成 5~7 个参数，保证类目语义。"""
    if category_id in (101, 102):
        return [
            spec("屏幕尺寸", rng.choice(["6.1英寸", "6.7英寸", "6.8英寸"])),
            spec("处理器", CHIP.get(brand, "骁龙8 Gen3")),
            spec("运行内存", rng.choice(["8GB", "12GB", "16GB"])),
            spec("机身存储", rng.choice(["128GB", "256GB", "512GB", "1TB"])),
            spec("后置摄像头", rng.choice(["4800万像素", "5000万像素", "1亿像素"])),
            spec("电池容量", rng.choice(["3274mAh", "4400mAh", "5000mAh", "5500mAh"])),
            spec("网络", "5G"),
        ]
    if category_id in (201, 202, 203):
        gpu = "RTX 4060" if category_id == 201 else "核显"
        if category_id == 201:
            gpu = rng.choice(["RTX 4060", "RTX 4070", "RTX 4080"])
        cpu = rng.choice(["i7-14650HX", "i9-14900HX", "Ryzen 9 8945HS", "Ultra 7 155H", "M3 Pro"])
        weight = rng.choice(["1.25kg", "1.45kg", "1.65kg", "2.2kg", "2.6kg"]) if category_id != 201 else rng.choice(["2.2kg", "2.4kg", "2.6kg"])
        return [
            spec("屏幕尺寸", rng.choice(["14英寸", "15.6英寸", "16英寸"])),
            spec("处理器", cpu),
            spec("显卡", gpu),
            spec("内存", rng.choice(["16GB", "32GB", "64GB"])),
            spec("硬盘", rng.choice(["512GB SSD", "1TB SSD", "2TB SSD"])),
            spec("重量", weight),
            spec("电池", rng.choice(["56Wh", "70Wh", "84Wh", "99Wh"])),
        ]
    if category_id in (301, 302):
        return [
            spec("屏幕", rng.choice(["10.9英寸", "11英寸", "12.9英寸", "13英寸"])),
            spec("处理器", rng.choice(["M2", "M4", "骁龙8 Gen3", "天玑9300"])),
            spec("内存", rng.choice(["8GB", "12GB", "16GB"])),
            spec("存储", rng.choice(["128GB", "256GB", "512GB", "1TB"])),
            spec("电池", rng.choice(["7600mAh", "10000mAh", "12000mAh"])),
            spec("重量", rng.choice(["460g", "520g", "680g"])),
        ]
    if category_id in (401, 402):
        return [
            spec("类型", "头戴式" if category_id == 401 else "真无线入耳式"),
            spec("降噪", rng.choice(["主动降噪", "混合降噪", "通话降噪"])),
            spec("蓝牙版本", rng.choice(["5.3", "5.4"])),
            spec("续航", rng.choice(["6小时", "8小时", "20小时", "40小时"])),
            spec("重量", rng.choice(["4.8g", "5.4g", "254g"])),
        ]
    if category_id == 403:
        return [
            spec("类型", "便携蓝牙音箱"),
            spec("功率", rng.choice(["20W", "30W", "40W", "60W"])),
            spec("蓝牙", "5.3"),
            spec("防水", rng.choice(["IPX4", "IPX7", "IP67"])),
            spec("续航", rng.choice(["12小时", "18小时", "24小时"])),
        ]
    if category_id in (501, 502):
        return [
            spec("屏幕", rng.choice(["1.4英寸AMOLED", "1.5英寸AMOLED", "1.9英寸OLED"])),
            spec("续航", rng.choice(["2天", "7天", "14天", "30天"])),
            spec("防水", rng.choice(["5ATM", "IP68", "10ATM"])),
            spec("健康监测", "心率/血氧/睡眠"),
            spec("表壳材质", rng.choice(["铝合金", "不锈钢", "钛金属"])),
        ]
    if category_id == 601:
        return [
            spec("传感器", rng.choice(["全画幅CMOS", "APS-C CMOS"])),
            spec("像素", rng.choice(["2420万", "3300万", "6100万"])),
            spec("视频", rng.choice(["4K 60p", "4K 120p", "6K 30p"])),
            spec("连拍", rng.choice(["10张/秒", "20张/秒", "30张/秒"])),
            spec("防抖", "机身五轴防抖"),
            spec("重量", rng.choice(["485g", "530g", "670g"])),
        ]
    if category_id in (701, 702):
        return [
            spec("兼容设备", rng.choice(["iPhone 15/16", "多数手机", "笔记本/平板", "通用"])),
            spec("材质", rng.choice(["TPU", "硅胶", "铝合金", "尼龙编织"])),
            spec("接口", rng.choice(["USB-C", "Lightning", "3.5mm", "HDMI"])),
            spec("尺寸", rng.choice(["标准", "加大", "迷你"])),
        ]
    if category_id == 703:
        return [
            spec("容量", rng.choice(["128GB", "256GB", "512GB", "1TB", "2TB"])),
            spec("接口", rng.choice(["USB-C 3.2", "USB 3.2", "NVMe"])),
            spec("读写速度", rng.choice(["550MB/s", "1050MB/s", "2000MB/s"])),
            spec("尺寸", rng.choice(["2.5英寸", "M.2 2280", "卡片式"])),
        ]
    if category_id == 704:
        return [
            spec("功率", rng.choice(["20W", "33W", "65W", "100W"])),
            spec("接口", rng.choice(["USB-C", "USB-A", "双口"])),
            spec("协议", rng.choice(["PD3.0", "QC4+", "PPS"])),
            spec("线长/容量", rng.choice(["1m", "1.5m", "10000mAh", "20000mAh"])),
        ]
    if category_id == 801:
        return [
            spec("语音助手", rng.choice(["小爱同学", "小艺", "Siri", "天猫精灵"])),
            spec("扬声器", rng.choice(["10W", "20W", "30W"])),
            spec("连接", rng.choice(["WiFi+蓝牙", "蓝牙5.3"])),
            spec("尺寸", rng.choice(["迷你", "标准", "大尺寸"])),
        ]
    if category_id == 802:
        return [
            spec("分辨率", rng.choice(["1080P", "2K", "4K"])),
            spec("夜视", rng.choice(["红外夜视", "全彩夜视"])),
            spec("存储", rng.choice(["TF卡", "云存储", "本地NVR"])),
            spec("连接", "WiFi"),
        ]
    if category_id == 803:
        return [
            spec("功率", rng.choice(["5W", "9W", "12W", "18W"])),
            spec("色温", rng.choice(["2700K-6500K", "3000K-5700K"])),
            spec("连接", rng.choice(["WiFi", "蓝牙Mesh", "Zigbee"])),
            spec("寿命", rng.choice(["25000小时", "50000小时"])),
        ]
    if category_id == 804:
        return [
            spec("吸力", rng.choice(["5000Pa", "8000Pa", "10000Pa"])),
            spec("续航", rng.choice(["120分钟", "180分钟", "240分钟"])),
            spec("尘盒", rng.choice(["300ml", "400ml", "500ml"])),
            spec("导航", rng.choice(["LDS激光导航", "视觉导航", "dToF导航"])),
            spec("噪音", rng.choice(["55dB", "60dB", "65dB"])),
        ]
    return [spec("规格", "官方标配")]


def sql_str(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def specs_json(specs):
    return json.dumps(specs, ensure_ascii=False, separators=(",", ":"))


def new_product(pid, category_id, index):
    low, high, brands, models = LEAVES[category_id]
    rng = random.Random(pid)
    brand = brands[index % len(brands)]
    model = models[index % len(models)]
    variant = VARIANTS[index % len(VARIANTS)]
    name = f"{brand}{model} {variant}"
    price = round(rng.uniform(low, high), 2)
    market = round(price * rng.uniform(1.02, 1.15), 2)
    cost = round(price * rng.uniform(0.85, 0.92), 2)
    specs = build_specs(category_id, brand, price, rng)
    highlight = " / ".join(s["value"] for s in specs[:2])
    return dict(
        id=pid, category_id=category_id, tag_id=None, name=name,
        sub_title=highlight,
        brand=brand, description=f"{name}，{highlight}，正品保障，支持七天无理由退换。",
        image=f"/image/upload/{pid}.webp", body=None, sales_mode="spot",
        listing_status=1, sales_price=price, market_price=market, cost_price=cost,
        product_code=f"SEED-{pid}", stock=rng.randint(50, 500),
        weight=None, volume=None, specs=specs_json(specs),
        create_time="2026-10-01 10:00:00", update_time="2026-10-01 10:00:00",
    )


def generate_inserts():
    rows = []
    for category_id, (start, end) in RANGES.items():
        for i, pid in enumerate(range(start, end + 1)):
            rows.append(new_product(pid, category_id, i))
    chunks = []
    for start in range(0, len(rows), 50):
        part = rows[start:start + 50]
        values = []
        for r in part:
            values.append(
                "({},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{},{})".format(
                    r["id"], r["category_id"], sql_str(r["tag_id"]), sql_str(r["name"]),
                    sql_str(r["sub_title"]), sql_str(r["brand"]), sql_str(r["description"]),
                    sql_str(r["image"]), sql_str(r["body"]), sql_str(r["sales_mode"]),
                    r["listing_status"], r["sales_price"], r["market_price"], r["cost_price"],
                    sql_str(r["product_code"]), r["stock"], sql_str(r["weight"]),
                    sql_str(r["volume"]), sql_str(r["specs"]), sql_str(r["create_time"]),
                    sql_str(r["update_time"]),
                )
            )
        chunks.append(
            "INSERT IGNORE INTO products (id, category_id, tag_id, name, sub_title, brand, description, image, body, "
            "sales_mode, listing_status, sales_price, market_price, cost_price, product_code, stock, weight, volume, "
            "specs, create_time, update_time) VALUES\n" + ",\n".join(values) + ";"
        )
    return chunks


def generate_backfill(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT id, name, brand, category_id, sales_price FROM products WHERE id <= 50 ORDER BY id")
        rows = cur.fetchall()
    lines = ["-- 现有 50 个商品补 specs（只补空值，重复执行不会覆盖人工修改）", "SET NAMES utf8mb4;", ""]
    for r in rows:
        category_id = r["category_id"] or 101
        rng = random.Random(r["id"])
        specs = build_specs(category_id, r["brand"] or "", float(r["sales_price"] or 0), rng)
        lines.append(
            "UPDATE products SET specs = {} WHERE id = {} AND specs IS NULL;".format(
                sql_str(specs_json(specs)), r["id"]
            )
        )
    return "\n".join(lines) + "\n"


def load_font(size):
    for path in ("C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def generate_images():
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    OPT_DIR.mkdir(parents=True, exist_ok=True)
    font_big = load_font(120)
    font_small = load_font(40)
    missing = []
    for category_id, (start, end) in RANGES.items():
        rep = REP_IMAGE.get(category_id, 1)
        src = IMG_DIR / f"{rep}.webp"
        if not src.exists():
            missing.append(str(src))
            base = Image.new("RGB", (2048, 2048), (245, 245, 245))
        else:
            base = Image.open(src).convert("RGB")
        for pid in range(start, end + 1):
            img = base.copy()
            draw = ImageDraw.Draw(img)
            draw.rectangle([0, img.height - 260, img.width, img.height], fill=(0, 0, 0))
            draw.text((60, img.height - 210), f"SEED-{pid}", font=font_big, fill=(255, 255, 255))
            img.save(IMG_DIR / f"{pid}.webp", "WEBP", quality=85)
            opt = img.resize((400, 400), Image.Resampling.LANCZOS)
            opt_draw = ImageDraw.Draw(opt)
            opt_draw.rectangle([0, opt.height - 60, opt.width, opt.height], fill=(0, 0, 0))
            opt_draw.text((12, opt.height - 50), f"SEED-{pid}", font=font_small, fill=(255, 255, 255))
            opt.save(OPT_DIR / f"{pid}.webp", "WEBP", quality=80)
    if missing:
        print("警告：以下代表图不存在，已用纯色占位：", missing)


def main():
    if not DB["password"]:
        sys.exit("请通过环境变量 DB_PASSWORD 提供数据库密码")
    conn = pymysql.connect(**DB)
    try:
        backfill = generate_backfill(conn)
    finally:
        conn.close()

    (OUT_DIR / "03-backfill-specs.sql").write_text(backfill, encoding="utf-8")
    inserts = generate_inserts()
    (OUT_DIR / "04-products-51-300.sql").write_text(
        "-- 250 个种子商品（id 51~300，product_code=SEED-xxx）\nSET NAMES utf8mb4;\n\n"
        + "\n\n".join(inserts) + "\n",
        encoding="utf-8",
    )
    generate_images()
    print("已生成：")
    print(" ", OUT_DIR / "03-backfill-specs.sql")
    print(" ", OUT_DIR / "04-products-51-300.sql")
    print(" ", IMG_DIR / "51.webp ~ 300.webp")
    print(" ", OPT_DIR / "51.webp ~ 300.webp")


if __name__ == "__main__":
    main()
