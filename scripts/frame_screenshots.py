#!/usr/bin/env python3
"""Frame the emulator's store screenshots for the Play listing.

usage: frame_screenshots.py <in-dir> <out-dir>

<in-dir>/<lang>/NN-name.png (from StoreScreenshotsTest) becomes
<out-dir>/<lang>/NN-name.png: the phone screen, slightly inset with rounded
corners, on the brand paper, with a caption above it in that language.
1080 x 1920 out, the portrait size Play accepts for phone screenshots.

Captions are the listing's own words: one benefit per screen, Urdu for the
local shop, English for everyone else (the bilingual rule in CLAUDE.md).
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
FONT_EN = os.path.join(ROOT, "app/src/main/res/font/manrope_bold.ttf")
FONT_UR = os.path.join(ROOT, "store/fonts/NotoNaskhArabic.ttf")

W, H = 1080, 1920
PAPER = (247, 246, 242)
INK = (15, 74, 51)
SOFT = (62, 107, 85)
GOLD = (138, 106, 21)

CAPTIONS = {
    "en": {
        "01-home": ("Every account, one tap away", "Khata, cashbook, cheques, bills and more"),
        "02-khata": ("Who owes what, at a glance", "Search by name or number, or speak it"),
        "03-customer": ("Gave and got, with a running balance", "Share a statement on WhatsApp"),
        "04-cashbook": ("Daily cash, in and out", "Today's position without a calculator"),
        "05-insights": ("Know your month", "Sales, recoveries and who pays late"),
        "06-backup": ("Your books, kept safe", "Backup to your own Google Drive"),
    },
    "ur": {
        "01-home": ("ہر حساب، ایک ٹیپ پر", "کھاتہ، کیش بک، چیک، بل اور بہت کچھ"),
        "02-khata": ("کس نے کتنا دینا ہے، ایک نظر میں", "نام یا نمبر سے ڈھونڈیں، یا بول کر"),
        "03-customer": ("دیا اور لیا، بقایا کے ساتھ", "اسٹیٹمنٹ واٹس ایپ پر بھیجیں"),
        "04-cashbook": ("روز کا کیش، آمد اور خرچ", "آج کی پوزیشن بغیر کیلکولیٹر"),
        "05-insights": ("اپنا مہینہ جانیں", "بکری، وصولی اور کون دیر کرتا ہے"),
        "06-backup": ("آپ کا کھاتہ، محفوظ", "اپنی گوگل ڈرائیو پر بیک اپ"),
    },
}


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def frame(src, lang, key, dst):
    shot = Image.open(src).convert("RGB")
    canvas = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(canvas)
    rtl = lang == "ur"
    title, sub = CAPTIONS[lang].get(key, ("", ""))
    f_title = ImageFont.truetype(FONT_UR if rtl else FONT_EN, 64 if rtl else 58)
    f_sub = ImageFont.truetype(FONT_UR if rtl else FONT_EN, 40 if rtl else 34)
    kw = dict(direction="rtl", language="ur") if rtl else dict(language="en")
    d.text((W / 2, 150), title, font=f_title, fill=INK, anchor="mm", **kw)
    d.text((W / 2, 235), sub, font=f_sub, fill=SOFT, anchor="mm", **kw)
    d.rounded_rectangle((W / 2 - 40, 288, W / 2 + 40, 294), 3, fill=GOLD)

    # The phone screen, scaled to sit under the caption with a little air.
    top, bottom, side = 330, 60, 80
    box_w, box_h = W - 2 * side, H - top - bottom
    s = min(box_w / shot.width, box_h / shot.height)
    scaled = shot.resize((round(shot.width * s), round(shot.height * s)), Image.LANCZOS)
    x = (W - scaled.width) // 2
    y = top
    shadow = Image.new("RGBA", (scaled.width + 60, scaled.height + 60), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((30, 40, scaled.width + 30, scaled.height + 50), 44, fill=(20, 40, 30, 70))
    from PIL import ImageFilter
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    canvas.paste(shadow, (x - 30, y - 30), shadow)
    tile = rounded(scaled, 44)
    canvas.paste(tile, (x, y), tile)
    canvas.save(dst, "PNG", optimize=True)


def main(src_root, dst_root):
    n = 0
    for lang in sorted(os.listdir(src_root)):
        d = os.path.join(src_root, lang)
        if not os.path.isdir(d) or lang not in CAPTIONS:
            continue
        os.makedirs(os.path.join(dst_root, lang), exist_ok=True)
        for f in sorted(os.listdir(d)):
            if not f.endswith(".png"):
                continue
            frame(os.path.join(d, f), lang, f[:-4], os.path.join(dst_root, lang, f))
            n += 1
    print(f"framed {n} screenshots into {dst_root}")
    return 0 if n else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
