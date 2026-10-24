#!/usr/bin/env python3
"""Regenerate the computer's posters from the phone's.

The poster designs are drawn by the phone's PosterKit, PosterDesigns,
PosterFlat, PosterGlyphs, PosterNewStock, PosterWords and PosterMotion, with
the coin and seal faces of poster3d and the occasion and field types of
data/. The Windows app draws the SAME posters, flat (the 3D heroes need
Filament, which is the phone's), by running that code on its
android.graphics stand-in (desktop/.../gfx). This copies the files with
their package and imports changed and the few Android-only edges cut:

    python3 scripts/gen_desktop_poster.py

CI runs it with --check and fails when the copies are stale.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main/java/com/innovation313/roshankhata"
OUT = ROOT / "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/poster"
PKG = "com.innovation313.roshankhata.desktop.poster"
GFX_PKG = "com.innovation313.roshankhata.desktop.gfx"
INV_PKG = "com.innovation313.roshankhata.desktop.invoice"

FILES = [
    "ui/PosterKit.kt", "ui/PosterDesigns.kt", "ui/PosterFlat.kt", "ui/PosterGlyphs.kt",
    "ui/PosterNewStock.kt", "ui/PosterWords.kt", "ui/PosterMotion.kt",
    "ui/poster3d/Hero.kt", "ui/poster3d/CoinFace.kt", "ui/poster3d/SealFace.kt",
    "data/Poster.kt", "data/PosterFields.kt",
]

HEADER = (
    "// GENERATED from app/src/main/java/com/innovation313/roshankhata/{name}\n"
    "// by scripts/gen_desktop_poster.py. Do not edit: change the phone's file and re-run.\n"
)

GFX = {"Bitmap", "BlurMaskFilter", "Canvas", "Color", "DashPathEffect", "LinearGradient", "Matrix", "Paint",
       "Path", "PointF", "RadialGradient", "Rect", "RectF", "Shader", "SweepGradient", "Typeface"}
TEXT = {"Layout", "StaticLayout", "TextDirectionHeuristics", "TextPaint"}
DROPPED = ("import android.os.SystemClock", "import android.provider.Settings", "import android.util.AttributeSet",
           "import android.view.View", "import org.json.")


def must(pattern: str, text: str, repl: str, name: str, count: int = 1, flags: int = 0) -> str:
    new, n = re.subn(pattern, repl, text, flags=flags)
    if n != count:
        sys.exit(f"gen_desktop_poster: expected {count} match(es) of {pattern!r} in {name}, found {n}"
                 " - the phone's file changed shape")
    return new


def imports(name: str, text: str) -> str:
    out = []
    for line in text.splitlines(keepends=True):
        s = line.rstrip("\n")
        if s.startswith("package "):
            out.append(f"package {PKG}\n")
            continue
        if not s.startswith("import "):
            out.append(line)
            continue
        if s.startswith(DROPPED):
            continue
        m = re.match(r"import android\.graphics\.(\w+)$", s)
        if m:
            if m.group(1) not in GFX:
                sys.exit(f"gen_desktop_poster: {name} imports android.graphics.{m.group(1)}, which the stand-in does not have")
            out.append(f"import {GFX_PKG}.{m.group(1)}\n")
            continue
        m = re.match(r"import android\.text\.(\w+)$", s)
        if m:
            if m.group(1) not in TEXT:
                sys.exit(f"gen_desktop_poster: {name} imports android.text.{m.group(1)}, which the stand-in does not have")
            out.append(f"import {GFX_PKG}.{m.group(1)}\n")
            continue
        if s == "import androidx.core.graphics.PathParser":
            out.append(f"import {GFX_PKG}.PathParser\n")
            continue
        if s == "import android.content.Context":
            out.append(f"import {INV_PKG}.Context\n")
            continue
        if s == "import com.innovation313.roshankhata.R":
            out.append(f"import {INV_PKG}.R\n")
            continue
        if s in ("import com.innovation313.roshankhata.data.InvoiceFonts", "import com.innovation313.roshankhata.data.quantity"):
            out.append(f"import {INV_PKG}.{s.rsplit('.', 1)[1]}\n")
            continue
        m = re.match(r"import com\.innovation313\.roshankhata\.data\.(PosterOccasion|PosterFields|TradeVocab)$", s)
        if m:
            out.append(f"import {PKG}.{m.group(1)}\n")
            continue
        m = re.match(r"import com\.innovation313\.roshankhata\.ui\.(?:poster3d\.)?([\w.]+)$", s)
        if m:
            out.append(f"import {PKG}.{m.group(1)}\n")
            continue
        out.append(line)
    return "".join(out)


def special(name: str, text: str) -> str:
    if name == "ui/PosterMotion.kt":
        # The live preview's View stays on the phone; the drawing is shared.
        text = must(r"\n/\*\*\n \* The view the live preview lays over the poster.*\Z", text, "\n", name, flags=re.S)
        text = must(r"(private fun clipOut\(c: Canvas, r: RectF\) \{\n).*?(\n    \})",
                    text, r"\1        c.clipOutRect(r)\2", name, flags=re.S)
    if name == "data/PosterFields.kt":
        # The phone keeps the choices in its preferences as JSON; the computer keeps its own (PosterPane).
        text = must(r"\n    fun toJson\(\): JSONObject = .*?\n    }\n", text, "\n", name, flags=re.S)
        text = must(r"\n        /\*\* Anything missing or unreadable.*?\n    }\n}\n", text, "\n    }\n}\n", name, flags=re.S)
    if name == "data/Poster.kt":
        # A string is named, not numbered, on the computer (R.string.x is W.x).
        text = must(r"val title: Int,", text, "val title: String,", name)
        text = must(r"val message: Int,", text, "val message: String,", name)
        text = must(r'CUSTOM\("custom", 0, 0,', text, 'CUSTOM("custom", "", "",', name)
    if name == "ui/PosterDesigns.kt":
        text = must(r"private fun str\(page: Page, id: Int\)", text, "private fun str(page: Page, id: String)", name)
    return text


def convert(name: str) -> str:
    text = (APP / name).read_text()
    text = imports(name, text)
    text = special(name, text)
    text = text.replace("android.graphics.", GFX_PKG + ".").replace("android.text.", GFX_PKG + ".")
    text = text.replace("com.innovation313.roshankhata.ui.poster3d.", PKG + ".")
    code = "\n".join(l for l in text.splitlines() if not l.lstrip().startswith(("*", "//", "/*")))
    left = re.search(r"\bandroid\.[\w.]+|\bandroidx\.[\w.]+|\borg\.json\b|\bJSONObject\b", code)
    if left:
        sys.exit(f"gen_desktop_poster: an Android call is left in {name}: {left.group(0)}")
    return HEADER.format(name=name) + text


def main() -> None:
    check = "--check" in sys.argv
    outputs = {OUT / pathlib.Path(n).name: convert(n) for n in FILES}
    stale = [p for p, t in outputs.items() if not p.is_file() or p.read_text() != t]
    if check:
        if stale:
            sys.exit("Stale (run python3 scripts/gen_desktop_poster.py): " + ", ".join(p.name for p in stale))
        print("desktop posters match the phone's")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for p, t in outputs.items():
        p.write_text(t)
    print("wrote", ", ".join(p.name for p in outputs))


if __name__ == "__main__":
    main()
