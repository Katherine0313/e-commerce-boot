#!/usr/bin/env python3
"""把商品原图（PNG）转成压缩后的 WebP，并按用途放到对应目录。

命名约定：
  51.png    → 商品主图     upload/image/upload/51.webp + opt/upload/51.webp（400 缩略图）
  51-1.png  → 详情图(body) upload/image/body/51-1.webp
  51-2.png  → 详情图(body) upload/image/body/51-2.webp

源目录默认桌面 image 文件夹，可用第一个参数覆盖。
运行：../recommender-python/.venv/Scripts/python.exe convert_product_images.py [源目录]
"""
import re
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("缺少 Pillow，请用 recommender-python/.venv/Scripts/python.exe 运行")

ROOT = Path(__file__).resolve().parents[4]
UPLOAD_DIR = ROOT / "upload" / "image" / "upload"
OPT_DIR = ROOT / "upload" / "image" / "opt" / "upload"
BODY_DIR = ROOT / "upload" / "image" / "body"

MAIN_SIZE = 800        # 主图边长（列表/详情统一 800）
OPT_SIZE = 400         # 首页/列表缩略图
BODY_MAX = 1200        # 详情图最大边长（小程序 750rpx 宽，1200 足够清晰）


def fit_square(im, size):
    """等比缩放后居中贴到白色正方形画布，非正方形原图不拉伸变形。"""
    im = im.copy()
    im.thumbnail((size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (size, size), (255, 255, 255))
    canvas.paste(im, ((size - im.width) // 2, (size - im.height) // 2))
    return canvas


def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / "Desktop" / "image"
    if not src.is_dir():
        sys.exit(f"源目录不存在：{src}")

    for d in (UPLOAD_DIR, OPT_DIR, BODY_DIR):
        d.mkdir(parents=True, exist_ok=True)

    main_cnt = body_cnt = 0
    src_bytes = out_bytes = 0
    for f in sorted(src.glob("*.png")):
        m = re.fullmatch(r"(\d+)(?:-(\d+))?\.png", f.name)
        if not m:
            print("跳过不识别的文件：", f.name)
            continue
        pid, part = int(m.group(1)), m.group(2)
        im = Image.open(f).convert("RGB")
        src_bytes += f.stat().st_size
        if part is None:
            # 主图：统一 800x800（非正方形等比缩放+白底），再生成 400 缩略图
            fit_square(im, MAIN_SIZE).save(
                UPLOAD_DIR / f"{pid}.webp", "WEBP", quality=85, method=6)
            fit_square(im, OPT_SIZE).save(
                OPT_DIR / f"{pid}.webp", "WEBP", quality=80, method=6)
            out_bytes += (UPLOAD_DIR / f"{pid}.webp").stat().st_size
            out_bytes += (OPT_DIR / f"{pid}.webp").stat().st_size
            main_cnt += 1
        else:
            # 详情图：保持比例，最长边压到 1200
            im.thumbnail((BODY_MAX, BODY_MAX), Image.Resampling.LANCZOS)
            out = BODY_DIR / f"{pid}-{part}.webp"
            im.save(out, "WEBP", quality=80, method=6)
            out_bytes += out.stat().st_size
            body_cnt += 1

    print(f"主图 {main_cnt} 张 → {UPLOAD_DIR} / {OPT_DIR}")
    print(f"详情图 {body_cnt} 张 → {BODY_DIR}")
    print(f"原始 {src_bytes / 1024 / 1024:.1f} MB → 输出 {out_bytes / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
