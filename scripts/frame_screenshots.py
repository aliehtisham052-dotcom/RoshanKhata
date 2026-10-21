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
# Hindi and Bengali captions (21 Oct 2026): the runner's Noto fonts (fonts-noto-core,
# installed by screenshots.yml). The first file that exists is used.
FONT_HI = ["/usr/share/fonts/truetype/noto/NotoSansDevanagari-Bold.ttf", "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf"]
FONT_BN = ["/usr/share/fonts/truetype/noto/NotoSansBengali-Bold.ttf", "/usr/share/fonts/truetype/noto/NotoSansBengali-Regular.ttf"]

RTL = {"ur", "ar", "fa"}


def font_for(lang):
    if lang in RTL:
        return FONT_UR
    if lang == "hi":
        return next((f for f in FONT_HI if os.path.exists(f)), FONT_EN)
    if lang == "bn":
        return next((f for f in FONT_BN if os.path.exists(f)), FONT_EN)
    return FONT_EN

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
    # The other listing languages (21 Oct 2026): the same six benefits, each in its own words.
    "ar": {
        "01-home": ("كل حساب، بلمسة واحدة", "الدفتر والصندوق والشيكات والفواتير وأكثر"),
        "02-khata": ("من عليه ماذا، بنظرة", "ابحث بالاسم أو الرقم، أو قُلها"),
        "03-customer": ("أعطيت وأخذت، مع رصيد جارٍ", "أرسل كشف الحساب على واتساب"),
        "04-cashbook": ("نقد اليوم، داخل وخارج", "وضع اليوم بلا آلة حاسبة"),
        "05-insights": ("اعرف شهرك", "المبيعات والتحصيل ومن يتأخر"),
        "06-backup": ("دفاترك في أمان", "نسخ احتياطي على Google Drive الخاص بك"),
    },
    "fa": {
        "01-home": ("هر حساب، با یک لمس", "دفتر، صندوق، چک، فاکتور و بیشتر"),
        "02-khata": ("چه کسی چقدر بدهکار است، در یک نگاه", "با نام یا شماره بگردید، یا بگویید"),
        "03-customer": ("دادم و گرفتم، با ماندهٔ جاری", "صورت‌حساب را در واتس‌اپ بفرستید"),
        "04-cashbook": ("نقد روز، ورودی و خروجی", "وضعیت امروز بدون ماشین‌حساب"),
        "05-insights": ("ماه خود را بشناسید", "فروش، وصولی و چه کسی دیر می‌دهد"),
        "06-backup": ("دفترهای شما، در امان", "پشتیبان در Google Drive خودتان"),
    },
    "hi": {
        "01-home": ("हर खाता, एक टैप पर", "खाता, कैशबुक, चेक, बिल और बहुत कुछ"),
        "02-khata": ("किसका कितना बाकी, एक नज़र में", "नाम या नंबर से ढूँढें, या बोलें"),
        "03-customer": ("दिया और लिया, चालू बैलेंस के साथ", "स्टेटमेंट WhatsApp पर भेजें"),
        "04-cashbook": ("रोज़ का कैश, आया और गया", "आज की स्थिति बिना कैलकुलेटर"),
        "05-insights": ("अपना महीना जानें", "बिक्री, वसूली और कौन देर करता है"),
        "06-backup": ("आपकी बही, सुरक्षित", "अपनी Google Drive पर बैकअप"),
    },
    "bn": {
        "01-home": ("প্রতিটি হিসাব, এক ট্যাপে", "খাতা, ক্যাশবুক, চেক, বিল আর আরও"),
        "02-khata": ("কার কাছে কত পাওনা, এক নজরে", "নাম বা নম্বরে খুঁজুন, বা বলুন"),
        "03-customer": ("দিলাম আর পেলাম, চলতি ব্যালান্সসহ", "বিবরণী হোয়াটসঅ্যাপে পাঠান"),
        "04-cashbook": ("দিনের নগদ, আয় ও ব্যয়", "আজকের অবস্থা ক্যালকুলেটর ছাড়া"),
        "05-insights": ("আপনার মাস জানুন", "বিক্রি, আদায় আর কে দেরি করে"),
        "06-backup": ("আপনার খাতা, নিরাপদ", "নিজের Google Drive-এ ব্যাকআপ"),
    },
    "in": {
        "01-home": ("Setiap akun, satu ketukan", "Buku utang, kas, cek, tagihan, dan lainnya"),
        "02-khata": ("Siapa berutang berapa, sekilas", "Cari nama atau nomor, atau ucapkan"),
        "03-customer": ("Beri dan terima, dengan saldo berjalan", "Kirim laporan lewat WhatsApp"),
        "04-cashbook": ("Kas harian, masuk dan keluar", "Posisi hari ini tanpa kalkulator"),
        "05-insights": ("Kenali bulan Anda", "Penjualan, penagihan, dan siapa yang telat"),
        "06-backup": ("Buku Anda, tetap aman", "Cadangan ke Google Drive Anda sendiri"),
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
    rtl = lang in RTL
    title, sub = CAPTIONS[lang].get(key, ("", ""))
    face = font_for(lang)
    f_title = ImageFont.truetype(face, 64 if rtl else 58)
    f_sub = ImageFont.truetype(face, 40 if rtl else 34)
    # Pillow shapes the text with Raqm when the script needs it (Arabic joins, Indic conjuncts).
    kw = dict(direction="rtl", language=lang) if rtl else dict(language=lang if lang != "in" else "id")
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
