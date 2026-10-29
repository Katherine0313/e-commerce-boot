#!/usr/bin/env python3
"""生成商品种子 SQL 与唯一占位图（不直接写数据库）。

目标：每个叶子分类补齐到 6 个商品（型号不重复），现有 50 个真实商品保留。
新增商品 id 从 51 开始连续，图片 `/image/upload/{id}.webp`。

产物：
  docs/sql/product-expand/03a-remove-old-seed.sql
  docs/sql/product-expand/03-backfill-specs.sql
  docs/sql/product-expand/04-products-seed.sql
  upload/image/upload/51.webp ...（新种子）
  upload/image/opt/upload/51.webp ...

运行（需 pymysql + Pillow，用 recommender-python/.venv 的解释器）：
  DB_USERNAME=e_commerce_db DB_PASSWORD=123456 \
  ../recommender-python/.venv/Scripts/python.exe generate_catalog.py
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

# 每个叶子目标商品数（现有 50 个保留，不够的补到 6）
TARGET_PER_LEAF = 6
SEED_ID_START = 51

# 叶子类目 -> (价格下限, 价格上限, [(品牌, 型号), ...])
LEAVES = {
    101: (5999, 12999, [
        ("苹果", "iPhone 15"), ("苹果", "iPhone 15 Pro"), ("苹果", "iPhone 15 Pro Max"),
        ("苹果", "iPhone 16"), ("苹果", "iPhone 16 Pro"), ("苹果", "iPhone 16 Pro Max"),
        ("苹果", "iPhone 14"), ("苹果", "iPhone 14 Pro"), ("苹果", "iPhone 13"), ("苹果", "iPhone SE 3"),
    ]),
    102: (1999, 7999, [
        ("华为", "Mate 70"), ("华为", "Mate 70 Pro"), ("华为", "Pura 70"), ("华为", "Pura 70 Pro"),
        ("小米", "15 Ultra"), ("小米", "红米K80 Pro"), ("OPPO", "Find X8"), ("vivo", "X200 Pro"),
        ("荣耀", "Magic7 Pro"), ("一加", "13"),
    ]),
    201: (6999, 15999, [
        ("联想", "拯救者Y9000P"), ("联想", "拯救者Y7000P"), ("华硕", "ROG魔霸9"), ("华硕", "ROG枪神8"),
        ("华硕", "天选5 Pro"), ("戴尔", "游匣G16"), ("惠普", "暗影精灵10"), ("惠普", "暗影精灵11"),
        ("宏碁", "掠夺者擎Neo"), ("雷神", "ZERO"),
    ]),
    202: (7999, 14999, [
        ("苹果", "MacBook Pro 14"), ("苹果", "MacBook Pro 16"), ("联想", "ThinkPad X1 Carbon"),
        ("联想", "ThinkPad T14p"), ("戴尔", "Latitude 7455"), ("戴尔", "灵越14 Plus"),
        ("惠普", "EliteBook 840"), ("惠普", "战66七代"), ("华为", "MateBook B7"), ("联想", "昭阳X7"),
    ]),
    203: (4999, 10999, [
        ("苹果", "MacBook Air 15"), ("苹果", "MacBook Air M3"), ("华为", "MateBook X Pro"),
        ("小米", "RedmiBook Pro 16"), ("联想", "小新Pro 14"), ("联想", "ThinkBook 14+"),
        ("华硕", "无畏Pro15"), ("惠普", "星Book Pro 14"), ("机械革命", "无界14"), ("荣耀", "MagicBook Art 14"),
    ]),
    301: (2999, 8999, [
        ("苹果", "iPad Air"), ("苹果", "iPad Pro 12.9"), ("苹果", "iPad 10th Gen"), ("苹果", "iPad mini 7"),
        ("苹果", "iPad Pro 11 M4"), ("苹果", "iPad Air M2"), ("苹果", "iPad 9"), ("苹果", "iPad mini 6"),
        ("苹果", "iPad Pro 13 M4"), ("苹果", "iPad 10.9"),
    ]),
    302: (1499, 4999, [
        ("联想", "Tab P12"), ("联想", "Y700 2024"), ("华为", "MatePad Pro"), ("华为", "MatePad 11.5"),
        ("小米", "平板7 Pro"), ("小米", "平板6S Pro"), ("三星", "Tab S10+"), ("OPPO", "Pad3 Pro"),
        ("vivo", "Pad3 Pro"), ("荣耀", "平板V9"),
    ]),
    401: (899, 3999, [
        ("索尼", "WH-1000XM5"), ("索尼", "WH-1000XM4"), ("索尼", "WH-CH720N"), ("苹果", "AirPods Max"),
        ("Bose", "QC45"), ("Bose", "QC Ultra"), ("华为", "FreeBuds Studio"), ("漫步者", "STAX SPIRIT S5"),
        ("森海塞尔", "Momentum 4"), ("铁三角", "ATH-M50xBT2"),
    ]),
    402: (299, 1999, [
        ("苹果", "AirPods Pro"), ("苹果", "AirPods 4"), ("索尼", "LinkBuds S"), ("索尼", "WF-1000XM5"),
        ("索尼", "WF-C700N"), ("华为", "FreeBuds Pro 4"), ("小米", "Buds 5 Pro"), ("OPPO", "Enco X3"),
        ("vivo", "TWS 4 Pro"), ("荣耀", "Earbuds 3 Pro"),
    ]),
    403: (299, 2999, [
        ("索尼", "SRS-XB33"), ("索尼", "SRS-XG300"), ("哈曼卡顿", "Aura Studio 4"), ("JBL", "Charge 5"),
        ("JBL", "Flip 6"), ("Bose", "SoundLink Flex"), ("华为", "Sound Joy"), ("小米", "Sound Pro"),
        ("Marshall", "Emberton II"), ("B&O", "Beosound A1"),
    ]),
    # 运动手表已合并到智能手表（原 502），价格区间放宽到覆盖入门运动表
    501: (499, 3999, [
        ("苹果", "Watch Series 9"), ("苹果", "Watch Series 10"), ("苹果", "Watch Ultra 2"), ("华为", "Watch 4 Pro"),
        ("华为", "Watch GT 5"), ("三星", "Galaxy Watch7"), ("小米", "Watch S3"), ("OPPO", "Watch X"),
        ("苹果", "Watch Ultra"), ("华为", "Watch Ultimate"), ("华为", "Watch Fit 3"), ("华为", "Watch GT 4"),
        ("小米", "Watch S4 Sport"), ("佳明", "Forerunner 265"), ("佳明", "Forerunner 965"),
        ("佳明", "Instinct 2"), ("高驰", "Pace 3"), ("高驰", "Apex 2 Pro"),
    ]),
    601: (4999, 29999, [
        ("尼康", "Z8"), ("尼康", "Zf"), ("尼康", "Z6III"), ("索尼", "A7C II"), ("索尼", "A7 IV"),
        ("索尼", "A6700"), ("佳能", "EOS R8"), ("佳能", "EOS R6 II"), ("佳能", "EOS R50"),
        ("富士", "X-T5"), ("富士", "X-S20"), ("富士", "X100VI"),
    ]),
    701: (29, 499, [
        ("苹果", "MagSafe手机壳"), ("华为", "硅胶保护壳"), ("华为", "素皮手机壳"), ("小米", "防摔壳"),
        ("小米", "透明壳"), ("绿联", "磁吸壳"), ("绿联", "皮革壳"), ("绿联", "支架壳"),
        ("绿联", "液态硅胶壳"), ("绿联", "磨砂壳"),
    ]),
    702: (49, 999, [
        ("罗技", "MX Master 3S"), ("罗技", "无线鼠标"), ("罗技", "机械键盘"), ("罗技", "蓝牙键盘"),
        ("联想", "USB-C扩展坞"), ("联想", "USB Hub"), ("联想", "笔记本支架"), ("绿联", "HDMI线"),
        ("绿联", "鼠标垫"), ("小米", "摄像头"),
    ]),
    703: (99, 1999, [
        ("三星", "T7移动固态"), ("三星", "PSSD"), ("闪迪", "Extreme Pro SD卡"), ("闪迪", "TF卡"),
        ("金士顿", "U盘"), ("金士顿", "读卡器"), ("西部数据", "移动硬盘"), ("西部数据", "硬盘盒"),
        ("英睿达", "NVMe SSD"), ("闪迪", "内存卡"),
    ]),
    704: (39, 599, [
        ("Anker", "65W氮化镓充电器"), ("Anker", "多口充电器"), ("Anker", "车载充电器"),
        ("小米", "移动电源3"), ("小米", "充电宝"), ("小米", "磁吸充电宝"), ("小米", "充电头"),
        ("绿联", "数据线"), ("绿联", "快充线"), ("苹果", "无线充电器"),
    ]),
    801: (199, 1999, [
        ("小米", "Sound Pro"), ("小米", "小爱音箱Pro"), ("华为", "Sound X"), ("华为", "Sound Joy"),
        ("苹果", "HomePod mini"), ("天猫精灵", "X6"), ("百度", "小度智能音箱"), ("漫步者", "花再"),
        ("JBL", "音乐金砖"), ("索尼", "智能音箱"),
    ]),
    802: (199, 2999, [
        ("小米", "智能摄像头"), ("小米", "室外摄像头"), ("小米", "门磁传感器"), ("小米", "人体传感器"),
        ("萤石", "C6摄像头"), ("萤石", "云台摄像头"), ("萤石", "智能猫眼"), ("萤石", "报警器"),
        ("华为", "智选摄像头"), ("360", "可视门铃"),
    ]),
    803: (49, 999, [
        ("小米", "米家台灯"), ("小米", "智能灯泡"), ("小米", "智能灯带"), ("小米", "智能开关"),
        ("Yeelight", "吸顶灯"), ("Yeelight", "氛围灯"), ("飞利浦", "Hue智能灯"), ("飞利浦", "筒灯"),
        ("欧普", "射灯"), ("欧普", "护眼台灯"),
    ]),
    804: (999, 4999, [
        ("石头", "G20扫拖机器人"), ("石头", "P10"), ("科沃斯", "T30扫地机"), ("科沃斯", "T20"),
        ("小米", "扫拖机器人"), ("小米", "扫拖2"), ("追觅", "X40扫地机"), ("追觅", "S30"),
        ("云鲸", "J4"), ("添可", "芙万"),
    ]),
}


TYPE_KEYWORDS = {
    "5G手机": ["手机"], "游戏笔记本": ["笔记本", "电脑"], "商务笔记本": ["笔记本", "电脑"],
    "轻薄笔记本": ["笔记本", "电脑"], "平板电脑": ["平板"], "头戴式耳机": ["耳机"],
    "真无线蓝牙耳机": ["耳机"], "蓝牙音箱": ["音箱"], "智能手表": ["手表"],
    "数码相机": ["相机"], "手机保护壳": ["壳"], "蓝牙键盘": ["键盘"], "机械键盘": ["键盘"],
    "键盘": ["键盘"], "无线鼠标": ["鼠标"], "扩展坞": ["扩展坞"], "笔记本支架": ["支架"],
    "HDMI视频线": ["线"], "视频线": ["线"], "鼠标垫": ["垫"], "电脑摄像头": ["摄像头"],
    "存储卡": ["卡"], "U盘": ["U盘"], "移动固态硬盘": ["固态", "SSD", "硬盘"], "移动硬盘": ["硬盘"],
    "读卡器": ["读卡器"], "充电器": ["充电器", "充电头", "氮化镓"], "移动电源": ["移动电源", "充电宝"],
    "无线充电器": ["无线充电"], "数据线": ["线"], "智能音箱": ["音箱"], "智能摄像头": ["摄像头"],
    "智能门铃": ["门铃", "猫眼"], "智能传感器": ["传感器"], "智能报警器": ["报警器"],
    "护眼台灯": ["台灯"], "智能吸顶灯": ["吸顶灯", "筒灯", "射灯"], "智能灯带": ["灯带"],
    "智能开关": ["开关"], "智能灯泡": ["灯泡", "Hue"], "扫地机器人": ["机器人"],
}

TYPE_SUBTITLE = {
    "5G手机": "5G全网通，高清影像系统，长续航快充，正品保障，现货速发",
    "游戏笔记本": "高性能处理器与独立显卡，高刷电竞屏，畅玩主流游戏",
    "商务笔记本": "稳定办公性能，丰富接口，指纹/人脸安全登录，续航持久",
    "轻薄笔记本": "轻薄便携机身，长续航，高清屏幕，适合通勤与移动办公",
    "平板电脑": "高清大屏，轻薄机身，长续航，学习办公娱乐一机搞定",
    "头戴式耳机": "主动降噪，长续航，佩戴舒适，通勤旅行皆适合",
    "真无线蓝牙耳机": "主动降噪，低延迟连接，长续航，运动通勤皆适合",
    "蓝牙音箱": "大功率输出，防水防尘，长续航，户外便携好声音",
    "智能手表": "健康监测，长续航，多运动模式，消息提醒抬手即看",
    "数码相机": "高像素传感器，快速对焦，视频防抖，创作拍摄更轻松",
    "手机保护壳": "精准开孔，防摔耐磨，亲肤手感，多机型可选",
    "蓝牙键盘": "蓝牙/多模连接，静音按键，长续航，办公游戏两相宜",
    "机械键盘": "机械轴体，手感扎实，RGB背光，办公游戏两相宜",
    "键盘": "多模连接，静音按键，长续航，办公游戏两相宜",
    "无线鼠标": "无线连接，高精度传感器，长续航，办公游戏皆适合",
    "扩展坞": "多接口扩展，高速传输，即插即用，笔记本/平板通用",
    "笔记本支架": "铝合金材质，多档调节，散热稳固，缓解颈椎疲劳",
    "HDMI视频线": "高清视频传输，抗干扰屏蔽，接口镀金，连接稳定",
    "视频线": "高清视频传输，抗干扰屏蔽，接口镀金，连接稳定",
    "鼠标垫": "顺滑表面，底部防滑，加厚耐用，办公游戏皆适合",
    "电脑摄像头": "高清画质，自动补光，内置麦克风，网课会议更清晰",
    "存储卡": "高速读写，稳定耐用，兼容相机/手机/行车记录仪",
    "U盘": "高速传输，小巧便携，金属机身，办公学习好帮手",
    "移动固态硬盘": "高速读写，小巧便携，防震耐用，大文件秒传",
    "移动硬盘": "大容量存储，USB高速传输，即插即用，数据备份安心",
    "读卡器": "多卡直读，高速传输，即插即用，摄影办公必备",
    "充电器": "快充协议兼容，多口输出，小巧便携，安全温控",
    "移动电源": "大容量快充，多口输出，轻薄便携，出行不断电",
    "无线充电器": "磁吸无线快充，随放随充，安全温控，桌面更整洁",
    "数据线": "快充传输二合一，编织耐用，接口镀金，稳定不伤机",
    "智能音箱": "语音控制，饱满音质，智能联动，家居生活更便捷",
    "智能摄像头": "高清画质，夜视监控，手机远程查看，家庭安防",
    "智能门铃": "高清可视，远程对讲，夜视监控，门前动态随时掌握",
    "智能传感器": "小巧免打孔，智能联动，低功耗长续航，安防更省心",
    "智能报警器": "高分贝报警，手机远程提醒，智能联动，守护家庭安全",
    "护眼台灯": "无频闪护眼，色温可调，智能控制，学习办公更舒适",
    "智能吸顶灯": "多档色温亮度，手机/语音控制，节能耐用，客厅卧室通用",
    "智能灯带": "RGB全彩，手机调光调色，可裁剪安装，氛围感拉满",
    "智能开关": "手机远程控制，定时开关，智能联动，安装简单",
    "智能灯泡": "手机调光调色，定时开关，智能联动，即装即用",
    "扫地机器人": "大吸力清扫，智能导航，扫拖一体，自动回充",
}


def kind_of(category_id, model):
    if category_id in (101, 102): return "phone"
    if category_id in (201, 202, 203): return "laptop"
    if category_id in (301, 302): return "tablet"
    if category_id == 401: return "headphone"
    if category_id == 402: return "earbud"
    if category_id == 403: return "speaker"
    if category_id == 501: return "watch"
    if category_id == 601: return "camera"
    if category_id == 701: return "case"
    if category_id == 702:
        if "键盘" in model: return "keyboard"
        # 「MX Master 3S」这类专业型号名不含「鼠标」二字，按型号兜底识别
        if ("鼠标" in model or "MX Master" in model) and "垫" not in model: return "mouse"
        if "扩展坞" in model or "Hub" in model: return "dock"
        if "支架" in model: return "stand"
        if "线" in model: return "cable"
        if "垫" in model: return "pad"
        if "摄像头" in model: return "webcam"
    if category_id == 703:
        if "SD卡" in model or "TF卡" in model or "内存卡" in model: return "card"
        if "U盘" in model: return "usb"
        if "固态" in model or "SSD" in model or "PSSD" in model: return "ssd"
        if "硬盘" in model: return "hdd"
        if "读卡器" in model: return "reader"
    if category_id == 704:
        if "线" in model: return "cable_charge"
        if "电源" in model or "充电宝" in model: return "powerbank"
        if "无线" in model or "磁吸" in model: return "wireless"
        return "charger"
    if category_id == 801: return "smart_speaker"
    if category_id == 802:
        if "门铃" in model or "猫眼" in model: return "doorbell"
        if "传感器" in model: return "sensor"
        if "报警器" in model: return "alarm"
        return "ipcam"
    if category_id == 803:
        if "台灯" in model: return "desk_lamp"
        if "吸顶灯" in model or "筒灯" in model or "射灯" in model: return "ceiling_lamp"
        if "灯带" in model: return "light_strip"
        if "开关" in model: return "switch"
        return "bulb"
    if category_id == 804: return "robot"
    return "generic"


def type_of(category_id, model, kind):
    if kind == "phone": return "5G手机"
    if kind == "laptop": return {201: "游戏笔记本", 202: "商务笔记本", 203: "轻薄笔记本"}.get(category_id, "笔记本电脑")
    if kind == "tablet": return "平板电脑"
    if kind == "headphone": return "头戴式耳机"
    if kind == "earbud": return "真无线蓝牙耳机"
    if kind == "speaker": return "蓝牙音箱"
    if kind == "watch": return "智能手表"
    if kind == "camera": return "数码相机"
    if kind == "case": return "手机保护壳"
    if kind == "keyboard": return "机械键盘" if "机械" in model else ("蓝牙键盘" if "蓝牙" in model else "键盘")
    if kind == "mouse": return "无线鼠标"
    if kind == "dock": return "扩展坞"
    if kind == "stand": return "笔记本支架"
    if kind == "cable": return "HDMI视频线" if "HDMI" in model else "视频线"
    if kind == "pad": return "鼠标垫"
    if kind == "webcam": return "电脑摄像头"
    if kind == "card": return "存储卡"
    if kind == "usb": return "U盘"
    if kind == "ssd": return "移动固态硬盘"
    if kind == "hdd": return "移动硬盘"
    if kind == "reader": return "读卡器"
    if kind == "charger": return "充电器"
    if kind == "powerbank": return "移动电源"
    if kind == "wireless": return "无线充电器"
    if kind == "cable_charge": return "数据线"
    if kind == "smart_speaker": return "智能音箱"
    if kind == "ipcam": return "智能摄像头"
    if kind == "doorbell": return "智能门铃"
    if kind == "sensor": return "智能传感器"
    if kind == "alarm": return "智能报警器"
    if kind == "desk_lamp": return "护眼台灯"
    if kind == "ceiling_lamp": return "智能吸顶灯"
    if kind == "light_strip": return "智能灯带"
    if kind == "switch": return "智能开关"
    if kind == "bulb": return "智能灯泡"
    if kind == "robot": return "扫地机器人"
    return "商品"


def display_name(brand, model, type_name):
    base = f"{brand} {model}" if brand.isascii() else f"{brand}{model}"
    if any(k in model for k in TYPE_KEYWORDS.get(type_name, [])):
        # 型号本身已经说明品类；移动固态/摄像头等补足到可读
        if type_name == "移动固态硬盘" and "硬盘" not in model:
            return base + "硬盘"
        if type_name == "电脑摄像头" and "电脑" not in model:
            return base + " 电脑摄像头"
        return base
    return base + " " + type_name


def subtitle_for(type_name):
    return TYPE_SUBTITLE.get(type_name, type_name + "，品牌正品，品质保障，支持七天无理由退换，现货速发")


def spec(name, value):
    return {"name": name, "value": str(value)}


def build_specs(category_id, brand, model, price, rng):
    kind = kind_of(category_id, model)
    if category_id == 702:
        if kind == "keyboard":
            return [spec("连接方式", rng.choice(["蓝牙5.0", "2.4G+蓝牙", "有线/蓝牙双模"])),
                    spec("键位数", rng.choice(["87键", "104键", "75键"])),
                    spec("续航", rng.choice(["6个月", "12个月", "24个月"])),
                    spec("兼容系统", rng.choice(["Windows/macOS", "多系统兼容"]))]
        if kind == "mouse":
            return [spec("连接方式", rng.choice(["蓝牙5.0", "2.4G+蓝牙", "USB接收器"])),
                    spec("DPI", rng.choice(["4000DPI", "8000DPI", "26000DPI"])),
                    spec("续航", rng.choice(["60天", "70天", "90天"])),
                    spec("按键数", rng.choice(["6键", "7键", "8键"]))]
        if kind == "dock":
            return [spec("接口", rng.choice(["USB-C", "USB-A", "HDMI+USB-C"])),
                    spec("传输速度", rng.choice(["5Gbps", "10Gbps", "40Gbps"])),
                    spec("兼容设备", rng.choice(["笔记本/平板", "手机/平板", "通用"])),
                    spec("材质", rng.choice(["铝合金", "ABS", "金属"]))]
        if kind == "stand":
            return [spec("材质", rng.choice(["铝合金", "ABS", "硅胶"])),
                    spec("尺寸", rng.choice(["标准", "加大", "折叠款"])),
                    spec("兼容设备", rng.choice(["11-17英寸笔记本", "平板/手机", "通用"])),
                    spec("调节方式", rng.choice(["多档调节", "无级调节"]))]
        if kind == "cable":
            return [spec("长度", rng.choice(["1m", "1.5m", "2m"])),
                    spec("接口", rng.choice(["HDMI 2.1", "HDMI 2.0", "USB-C"])),
                    spec("传输速度", rng.choice(["18Gbps", "48Gbps", "10Gbps"])),
                    spec("材质", rng.choice(["尼龙编织", "PVC", "铝合金"]))]
        if kind == "pad":
            return [spec("尺寸", rng.choice(["中号", "大号", "超大号"])),
                    spec("材质", rng.choice(["天然橡胶", "PU皮革", "布面"])),
                    spec("厚度", rng.choice(["2mm", "3mm", "4mm"])),
                    spec("防滑", "底部防滑")]
        if kind == "webcam":
            return [spec("分辨率", rng.choice(["1080P", "2K", "4K"])), spec("接口", "USB"),
                    spec("夜视", rng.choice(["自动补光", "红外夜视"])),
                    spec("麦克风", rng.choice(["内置双麦", "内置单麦"]))]
    if category_id == 703:
        if kind == "reader":
            return [spec("接口", rng.choice(["USB-C", "USB 3.0"])),
                    spec("支持卡型", rng.choice(["SD/TF双卡", "SD卡", "TF卡"])),
                    spec("传输速度", rng.choice(["104MB/s", "312MB/s"])),
                    spec("材质", rng.choice(["ABS", "铝合金"]))]
        return [spec("容量", rng.choice(["128GB", "256GB", "512GB", "1TB", "2TB"])),
                spec("接口", rng.choice(["USB-C 3.2", "USB 3.2", "NVMe"])),
                spec("读写速度", rng.choice(["550MB/s", "1050MB/s", "2000MB/s"])),
                spec("尺寸", rng.choice(["2.5英寸", "M.2 2280", "卡片式"]))]
    if category_id == 704:
        return [spec("功率", rng.choice(["20W", "33W", "65W", "100W"])),
                spec("接口", rng.choice(["USB-C", "USB-A", "双口"])),
                spec("协议", rng.choice(["PD3.0", "QC4+", "PPS"])),
                spec("线长/容量", rng.choice(["1m", "1.5m", "10000mAh", "20000mAh"]))]
    if category_id in (101, 102):
        return [
            spec("屏幕尺寸", rng.choice(["6.1英寸", "6.7英寸", "6.8英寸"])),
            spec("处理器", {"苹果": "A18 Pro", "华为": "麒麟9010", "小米": "骁龙8 Gen3"}.get(brand, "骁龙8 Gen3")),
            spec("运行内存", rng.choice(["8GB", "12GB", "16GB"])),
            spec("机身存储", rng.choice(["128GB", "256GB", "512GB", "1TB"])),
            spec("后置摄像头", rng.choice(["4800万像素", "5000万像素", "1亿像素"])),
            spec("电池容量", rng.choice(["3274mAh", "4400mAh", "5000mAh", "5500mAh"])),
            spec("网络", "5G"),
        ]
    if category_id in (201, 202, 203):
        gpu = rng.choice(["RTX 4060", "RTX 4070", "RTX 4080"]) if category_id == 201 else "核显"
        weight = rng.choice(["2.2kg", "2.4kg", "2.6kg"]) if category_id == 201 else rng.choice(["1.25kg", "1.45kg", "1.65kg"])
        return [
            spec("屏幕尺寸", rng.choice(["14英寸", "15.6英寸", "16英寸"])),
            spec("处理器", rng.choice(["i7-14650HX", "i9-14900HX", "Ryzen 9 8945HS", "Ultra 7 155H", "M3 Pro"])),
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
            spec("类型", "便携蓝牙音箱"), spec("功率", rng.choice(["20W", "30W", "40W", "60W"])),
            spec("蓝牙", "5.3"), spec("防水", rng.choice(["IPX4", "IPX7", "IP67"])),
            spec("续航", rng.choice(["12小时", "18小时", "24小时"])),
        ]
    if category_id == 501:
        return [
            spec("屏幕", rng.choice(["1.4英寸AMOLED", "1.5英寸AMOLED", "1.9英寸OLED"])),
            spec("续航", rng.choice(["2天", "7天", "14天", "30天"])),
            spec("防水", rng.choice(["5ATM", "IP68", "10ATM"])),
            spec("健康监测", "心率/血氧/睡眠"), spec("表壳材质", rng.choice(["铝合金", "不锈钢", "钛金属"])),
        ]
    if category_id == 601:
        return [
            spec("传感器", rng.choice(["全画幅CMOS", "APS-C CMOS"])),
            spec("像素", rng.choice(["2420万", "3300万", "6100万"])),
            spec("视频", rng.choice(["4K 60p", "4K 120p", "6K 30p"])),
            spec("连拍", rng.choice(["10张/秒", "20张/秒", "30张/秒"])),
            spec("防抖", "机身五轴防抖"), spec("重量", rng.choice(["485g", "530g", "670g"])),
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



def make_title(name, subtitle, specs):
    values = [s["value"] for s in specs if s.get("value")]
    parts = [name, (subtitle or "").replace("，", " ").strip()]
    parts.extend(values[:3])
    parts.extend(["官方正品", "现货速发"])
    result, seen = [], set()
    for item in parts:
        if item and item not in seen:
            seen.add(item)
            result.append(item)
    return " ".join(result)

def make_product(pid, category_id, brand, model, existing_names):
    low, high, _ = LEAVES[category_id]
    kind = kind_of(category_id, model)
    type_name = type_of(category_id, model, kind)
    name = display_name(brand, model, type_name)
    if name in existing_names:
        return None
    rng = random.Random(pid)
    price = round(rng.uniform(low, high), 2)
    market = round(price * rng.uniform(1.02, 1.15), 2)
    cost = round(price * rng.uniform(0.85, 0.92), 2)
    specs = build_specs(category_id, brand, model, price, rng)
    highlight = " / ".join(s["value"] for s in specs[:2])
    return dict(
        id=pid, category_id=category_id, brand=brand, name=name,
        sub_title=subtitle_for(type_name),
        title=make_title(name, subtitle_for(type_name), specs),
        image=f"/image/upload/{pid}.webp", price=price, market=market, cost=cost,
        stock=rng.randint(50, 500), specs=specs_json(specs), code=f"SEED-{pid}",
    )


def generate(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT id, name, brand, category_id, sales_price FROM products WHERE id <= 50 ORDER BY id")
        existing = cur.fetchall()
    existing_names = {r["name"] for r in existing}
    existing_count = {}
    for r in existing:
        existing_count[r["category_id"]] = existing_count.get(r["category_id"], 0) + 1

    rows = []
    pid = SEED_ID_START
    for category_id, (low, high, pairs) in LEAVES.items():
        need = max(0, TARGET_PER_LEAF - existing_count.get(category_id, 0))
        used_models = set()
        for brand, model in pairs:
            if need <= 0:
                break
            # 型号已存在（现有商品的名称里包含该型号）或已生成，跳过，避免近似重复
            if any(model in name for name in existing_names) or model in used_models:
                continue
            product = make_product(pid, category_id, brand, model, existing_names)
            if product is None:
                continue
            rows.append(product)
            existing_names.add(product["name"])
            used_models.add(model)
            pid += 1
            need -= 1

    # 现有 50 个补 specs（只补空值）
    backfill = ["-- 现有 50 个商品补 specs（只补空值，重复执行不会覆盖人工修改）", "SET NAMES utf8mb4;", ""]
    for r in existing:
        category_id = r["category_id"] or 101
        rng = random.Random(r["id"])
        specs = build_specs(category_id, r["brand"] or "", r["name"] or "", float(r["sales_price"] or 0), rng)
        backfill.append(
            "UPDATE products SET specs = {} WHERE id = {} AND specs IS NULL;".format(
                sql_str(specs_json(specs)), r["id"]
            )
        )

    # 清理旧种子（先清引用，再删商品）
    cleanup = [
        "-- 删除旧的 51 开头种子商品（旧版有伪变体重复）；先清购物车/库存引用",
        "SET NAMES utf8mb4;",
        "DELETE FROM cart WHERE product_id BETWEEN 51 AND 300;",
        "DELETE FROM stock WHERE product_id BETWEEN 51 AND 300;",
        "DELETE FROM products WHERE id BETWEEN 51 AND 300 AND product_code LIKE 'SEED-%';",
    ]

    # 新商品 INSERT
    chunks = []
    for start in range(0, len(rows), 40):
        part = rows[start:start + 40]
        values = []
        for r in part:
            values.append(
                "({},{},NULL,{},{},{},{},{},NULL,'spot',1,{},{},{},{},{},NULL,NULL,{},'2026-10-01 10:00:00','2026-10-01 10:00:00')".format(
                    r["id"], r["category_id"], sql_str(r["name"]), sql_str(r["sub_title"]),
                    sql_str(r["brand"]), sql_str(r["title"]), sql_str(r["image"]),
                    r["price"], r["market"], r["cost"], sql_str(r["code"]), r["stock"],
                    sql_str(r["specs"]),
                )
            )
        chunks.append(
            "INSERT IGNORE INTO products (id, category_id, tag_id, name, sub_title, brand, title, image, body, "
            "sales_mode, listing_status, sales_price, market_price, cost_price, product_code, stock, weight, volume, "
            "specs, create_time, update_time) VALUES\n" + ",\n".join(values) + ";"
        )

    return backfill, cleanup, chunks, rows


def load_font(size):
    for path in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/arial.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def generate_images(rows):
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    OPT_DIR.mkdir(parents=True, exist_ok=True)
    # 清掉旧种子图（51~300），新商品会重新生成
    for base in (IMG_DIR, OPT_DIR):
        for i in range(51, 301):
            p = base / f"{i}.webp"
            if p.exists():
                p.unlink()

    font_big = load_font(72)
    font_small = load_font(28)
    for r in rows:
        rng = random.Random(r["id"])
        # 每个商品一个不同的底色，避免图片看起来重复
        r0, g0, b0 = rng.randint(30, 200), rng.randint(30, 200), rng.randint(30, 200)
        img = Image.new("RGB", (800, 800), (r0, g0, b0))
        draw = ImageDraw.Draw(img)
        # 底部深色信息区
        draw.rectangle([0, 560, 800, 800], fill=(20, 20, 20))
        draw.text((40, 600), r["brand"], font=font_big, fill=(255, 255, 255))
        # 型号行去掉开头的品牌，避免图上「苹果 / 苹果iPhone」重复
        model_text = r["name"][len(r["brand"]):] if r["name"].startswith(r["brand"]) else r["name"]
        draw.text((40, 690), model_text, font=font_small, fill=(235, 235, 235))
        draw.text((40, 735), f"SEED-{r['id']}", font=font_small, fill=(180, 180, 180))
        img.save(IMG_DIR / f"{r['id']}.webp", "WEBP", quality=85)
        opt = img.resize((400, 400), Image.Resampling.LANCZOS)
        opt.save(OPT_DIR / f"{r['id']}.webp", "WEBP", quality=80)


def main():
    if not DB["password"]:
        sys.exit("请通过环境变量 DB_PASSWORD 提供数据库密码")
    conn = pymysql.connect(**DB)
    try:
        backfill, cleanup, chunks, rows = generate(conn)
    finally:
        conn.close()

    (OUT_DIR / "03-backfill-specs.sql").write_text("\n".join(backfill) + "\n", encoding="utf-8")
    (OUT_DIR / "03a-remove-old-seed.sql").write_text("\n".join(cleanup) + "\n", encoding="utf-8")
    (OUT_DIR / "04-products-seed.sql").write_text(
        "-- 按型号去重后的种子商品（现有一件型号只保留一件）；id 从 51 开始\nSET NAMES utf8mb4;\n\n"
        + "\n\n".join(chunks) + "\n",
        encoding="utf-8",
    )
    generate_images(rows)
    print(f"新种子商品 {len(rows)} 个，id {SEED_ID_START}~{SEED_ID_START + len(rows) - 1}")


if __name__ == "__main__":
    main()
