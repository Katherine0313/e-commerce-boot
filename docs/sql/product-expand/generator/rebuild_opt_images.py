#!/usr/bin/env python3
"""补齐 / 重新压缩商品列表缩略图（opt/upload/{id}.webp）。

只从现有 800px 主图 upload/image/upload/{id}.webp 生成，不依赖桌面原图：
  缺失 opt 的商品  → 生成 400x400 白底缩略图
  已有 opt > 30KB → 重新压缩（400x400，quality 74，method 6）

商品卡实际只需要 400px；quality 74 一般能压到 5~20KB，首屏图片体积明显下降。
运行：python docs/sql/product-expand/generator/rebuild_opt_images.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
UPLOAD_DIR = ROOT / "upload" / "image" / "upload"
OPT_DIR = ROOT / "upload" / "image" / "opt" / "upload"

OPT_SIZE = 400
QUALITY = 74
BIG_THRESHOLD = 30_000  # 超过 30KB（十进制）的缩略图重新压


def to_square(im: Image.Image, size: int) -> Image.Image:
    """等比缩放后居中贴到白色正方形画布，避免非正方形图片被拉伸。"""
    im = im.convert("RGBA")
    im.thumbnail((size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (size, size), (255, 255, 255))
    # 用 alpha 通道作 mask 贴到白底，透明区域不会变黑
    canvas.paste(im, ((size - im.width) // 2, (size - im.height) // 2), im)
    return canvas


def main() -> None:
    if not OPT_DIR.exists():
        OPT_DIR.mkdir(parents=True, exist_ok=True)

    targets = []
    for src in sorted(UPLOAD_DIR.glob("*.webp")):
        try:
            pid = int(src.stem)
        except ValueError:
            continue
        opt = OPT_DIR / src.name
        if not opt.exists():
            targets.append((pid, src, opt, None))
        elif opt.stat().st_size > BIG_THRESHOLD:
            targets.append((pid, src, opt, opt.stat().st_size))

    if not targets:
        print("没有需要处理的缩略图")
        return

    before = under = 0
    for pid, src, opt, old_size in targets:
        im = Image.open(src)
        out = to_square(im, OPT_SIZE)
        out.save(opt, "WEBP", quality=QUALITY, method=6)
        new_size = opt.stat().st_size
        before += old_size or src.stat().st_size
        under += new_size
        print(f"{pid:>3}.webp  {old_size or '-':>8} -> {new_size:>7} B")

    print(f"\n处理 {len(targets)} 张，输出合计 {under / 1024:.1f} KB")
    over = [(p, o.stat().st_size) for p, _, o, _ in targets if o.stat().st_size > BIG_THRESHOLD]
    if over:
        print("仍超过 30KB：", over)


if __name__ == "__main__":
    main()
