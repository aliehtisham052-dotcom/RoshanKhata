#!/usr/bin/env python3
"""
Right-to-left PDF guard.

Every PDF the app prints (invoice, ledger, statement, business, register,
inspector) is mirrored for Urdu, Sindhi, Farsi and Arabic by
data/PdfRtl.kt: the page is flipped, and each piece of text and each
picture is flipped back so it reads the right way round.

That only works if every page is started, and every text and bitmap drawn,
through PdfRtl. One raw canvas.drawText(...) in a PDF file prints as mirror
writing in those four languages, and nothing else would catch it: English
and Roman Urdu look perfect. So this fails on any direct drawText,
drawBitmap or startPage in the PDF code (the data/ package), outside
PdfRtl.kt itself.
"""
import glob, re, sys

ROOT = "app/src/main/java/com/innovation313/roshankhata/data"
RAW = re.compile(r'(?<!PdfRtl)\.(drawText|drawBitmap|startPage|drawTextOnPath|drawTextRun|drawPosText)\(')

problems = []
for path in sorted(glob.glob(f"{ROOT}/**/*.kt", recursive=True)):
    if path.endswith("/PdfRtl.kt"):
        continue
    for n, line in enumerate(open(encoding="utf-8", file=path), 1):
        code = line.split("//", 1)[0]
        for m in RAW.finditer(code):
            problems.append(f"{path}:{n}: direct .{m.group(1)}( — use PdfRtl.{m.group(1)}(...) so right-to-left PDFs stay readable")

if problems:
    print("\n".join(problems))
    print(f"\n{len(problems)} direct PDF draw call(s). See data/PdfRtl.kt.")
    sys.exit(1)
print("PDF RTL guard: every PDF page, text and bitmap goes through PdfRtl.")
