#!/usr/bin/env python3
"""生成 127~136 机械普通手表商品的唯一占位图（原图 + opt 缩略图）。

只生成图片，不写数据库；SQL 见 10-mechanical-watches.sql。
运行：../recommender-python/.venv/Scripts/python.exe add_mechanical_watches.py
"""
import os
import random
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("缺少 Pillow，请用 recommender-python/.venv/Scripts/python.exe 运行")

ROOT = Path(__file__).resolve().parents[4]
IMG_DIR = ROOT / "upload" / "image" / "upload"
OPT_DIR = ROOT / "upload" / "image" / "opt" / "upload"

# (id, 品牌, 型号文字) —— 与 10-mechanical-watches.sql 对应
WATCHES = [
    (127, "天梭", "力洛克 T006 机械男表"),
    (128, "浪琴", "名匠 L2.628 机械男表"),
    (129, "精工", "5号 SRPD55K1 自动机械表"),
    (130, "卡西欧", "EDIFICE EFV-100D 石英表"),
    (131, "美度", "贝伦赛丽 M8600 机械表"),
    (132, "汉米尔顿", "卡其野战 H69439931 机械表"),
    (133, "雪铁纳", "DS PH200M 潜水机械表"),
    (134, "飞亚达", "经典系列 GA802057 机械男表"),
    (135, "西铁城", "NH8350-83L 自动机械表"),
    (136, "雷达", "晶萃 R30939102 石英表"),
]


def load_font(size):
    for path in ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/arial.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def main():
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    OPT_DIR.mkdir(parents=True, exist_ok=True)
    font_big = load_font(72)
    font_small = load_font(28)
    for pid, brand, model in WATCHES:
        # 按 id 生成稳定底色，和已有种子图风格一致
        rng = random.Random(pid)
        bg = (rng.randint(30, 200), rng.randint(30, 200), rng.randint(30, 200))
        img = Image.new("RGB", (800, 800), bg)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 560, 800, 800], fill=(20, 20, 20))
        draw.text((40, 600), brand, font=font_big, fill=(255, 255, 255))
        draw.text((40, 690), model, font=font_small, fill=(235, 235, 235))
        draw.text((40, 735), f"SEED-{pid}", font=font_small, fill=(180, 180, 180))
        img.save(IMG_DIR / f"{pid}.webp", "WEBP", quality=85)
        img.resize((400, 400), Image.Resampling.LANCZOS).save(OPT_DIR / f"{pid}.webp", "WEBP", quality=80)
    print(f"已生成 {len(WATCHES)} 张占位图：{IMG_DIR}")


if __name__ == "__main__":
    main()
