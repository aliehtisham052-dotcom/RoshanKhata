#!/usr/bin/env python3
"""Regenerate the computer's business cards from the phone's.

The twenty-four business card designs are drawn by the phone's
CardTemplates, CardGlyphs and CardLogo. The Windows app draws the SAME cards
by running that code on its android.graphics stand-in (desktop/.../gfx).
This copies the three files with only their package and imports changed:

    python3 scripts/gen_desktop_cards.py

CI runs it with --check and fails when the copies are stale.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/innovation313/roshankhata/ui"
OUT = ROOT / "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/card"
PKG = "com.innovation313.roshankhata.desktop.card"

HEADER = (
    "// GENERATED from app/src/main/java/com/innovation313/roshankhata/ui/{name}\n"
    "// by scripts/gen_desktop_cards.py. Do not edit: change the phone's file and re-run.\n"
)

GFX = {"Bitmap", "Canvas", "Color", "DashPathEffect", "LinearGradient", "Matrix", "Paint", "Path",
       "RadialGradient", "Rect", "RectF", "Shader", "Typeface"}


def convert(name: str) -> str:
    text = (SRC / name).read_text()
    out = []
    for line in text.splitlines(keepends=True):
        if line.startswith("package "):
            out.append(f"package {PKG}\n")
            continue
        m = re.match(r"import android\.graphics\.(\w+)\s*$", line)
        if m:
            cls = m.group(1)
            if cls not in GFX:
                sys.exit(f"gen_desktop_cards: {name} imports android.graphics.{cls}, which the stand-in does not have")
            out.append(f"import com.innovation313.roshankhata.desktop.gfx.{cls}\n")
            continue
        if line.startswith("import android.content.Context"):
            out.append("import com.innovation313.roshankhata.desktop.invoice.Context\n")
            continue
        if line.startswith("import com.innovation313.roshankhata.R\n"):
            out.append("import com.innovation313.roshankhata.desktop.invoice.R\n")
            continue
        if line.startswith("import com.innovation313.roshankhata.data.InvoiceFonts"):
            out.append("import com.innovation313.roshankhata.desktop.invoice.InvoiceFonts\n")
            continue
        out.append(line)
    text = "".join(out)
    # A design's name is a string resource on the phone, a word name (W.x) here.
    text = text.replace("val labelRes: Int,", "val labelRes: String,")
    code = "\n".join(l for l in text.splitlines() if not l.lstrip().startswith(("*", "//", "/*")))
    left = re.search(r"\bandroid\.[\w.]+", code)
    if left:
        sys.exit(f"gen_desktop_cards: an Android call is left in {name}: {left.group(0)}")
    return HEADER.format(name=name) + text


def main() -> None:
    check = "--check" in sys.argv
    outputs = {OUT / n: convert(n) for n in ("CardTemplates.kt", "CardGlyphs.kt", "CardLogo.kt")}
    stale = [p for p, t in outputs.items() if not p.is_file() or p.read_text() != t]
    if check:
        if stale:
            sys.exit("Stale (run python3 scripts/gen_desktop_cards.py): " + ", ".join(p.name for p in stale))
        print("desktop business cards match the phone's")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for p, t in outputs.items():
        p.write_text(t)
    print("wrote", ", ".join(p.name for p in outputs))


if __name__ == "__main__":
    main()
