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

It first writes the phone's ui/PosterEmblems.kt from the shop-type icons the
picker draws (res/drawable/ic_trade_*.xml, 13 Nov 2026): the New stock poster's
flat crate wears the shop's own glyph, and the glyph's path data is read from
those files, never typed twice.

CI runs it with --check and fails when the copies (or the emblem table) are stale.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main/java/com/innovation313/roshankhata"
OUT = ROOT / "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/poster"
ICONS = ROOT / "app/src/main/res/drawable"
EMBLEMS = APP / "ui/PosterEmblems.kt"
PKG = "com.innovation313.roshankhata.desktop.poster"
GFX_PKG = "com.innovation313.roshankhata.desktop.gfx"
INV_PKG = "com.innovation313.roshankhata.desktop.invoice"

FILES = [
    "ui/PosterKit.kt", "ui/PosterFoot.kt", "ui/PosterDesigns.kt", "ui/PosterFlat.kt", "ui/PosterGlyphs.kt",
    "ui/PosterNewStock.kt", "ui/PosterEmblems.kt", "ui/PosterWords.kt", "ui/PosterMotion.kt",
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


EMBLEM_HEAD = """// GENERATED by scripts/gen_desktop_poster.py from app/src/main/res/drawable/ic_trade_*.xml.
// Do not edit: change the icon and re-run the script.
package com.innovation313.roshankhata.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.PathParser
import java.util.concurrent.ConcurrentHashMap

/**
 * The shop-type glyphs the picker draws (one two-tone family, 24-unit paths: the second tone is the light
 * part), drawn large on a poster in gold (13 Nov 2026). The New stock poster's flat crate wears the shop's
 * own glyph instead of an empty box. The path data is read from the icons by the generator, so a changed
 * icon changes the poster with it. Keys are the trade's name in lower case ([TradeVocab.key]).
 */
object PosterEmblems {

    private val DATA: Map<String, Array<String>> = mapOf(
@@DATA@@
    )

    private val paths = ConcurrentHashMap<String, Path>()

    private fun path(data: String): Path = paths.getOrPut(data) { PathParser.createPathFromPathData(data) }

    private fun withAlpha(colour: Int, alpha: Int): Int = (alpha shl 24) or (colour and 0xFFFFFF)

    /** Whether the trade has a glyph of its own; any other key is drawn as the "other" shop's. */
    fun has(key: String): Boolean = key in DATA

    /**
     * The glyph [size] pixels square, centred on ([cx], [cy]): its main shape in a gradient from [light] down to
     * [dark], its second tone in the same at 40%.
     */
    fun draw(c: Canvas, key: String, cx: Float, cy: Float, size: Float, light: Int, dark: Int) {
        val d = DATA[key] ?: DATA["other"] ?: return
        val m = Matrix()
        val s = size / 24f
        m.setScale(s, s)
        m.postTranslate(cx - size / 2f, cy - size / 2f)
        for (i in 0..1) {
            val p = Path(path(d[i]))
            p.transform(m)
            val alpha = if (i == 0) 0x66 else 0xFF
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, cy - size / 2f, 0f, cy + size / 2f,
                withAlpha(light, alpha), withAlpha(dark, alpha), Shader.TileMode.CLAMP)
            c.drawPath(p, paint)
        }
    }
}
"""


def emblem_source() -> str:
    """The phone's PosterEmblems.kt, from the shop-type icons: (second tone, main shape) path data per trade."""
    import xml.etree.ElementTree as ET
    ns = "{http://schemas.android.com/apk/res/android}"
    rows = []
    for f in sorted(ICONS.glob("ic_trade_*.xml")):
        key = f.stem[len("ic_trade_"):]
        root = ET.parse(f).getroot()
        if root.get(ns + "viewportWidth") != "24" or root.get(ns + "viewportHeight") != "24":
            sys.exit(f"gen_desktop_poster: {f.name} is not a 24 x 24 icon")
        soft = [p for p in root.findall("path") if p.get(ns + "fillAlpha") == "0.4"]
        main = [p for p in root.findall("path") if p.get(ns + "fillAlpha") is None]
        if len(root.findall("path")) != 2 or len(soft) != 1 or len(main) != 1:
            sys.exit(f"gen_desktop_poster: {f.name} is not one main shape and one 40% tone")
        pair = []
        for p in (soft[0], main[0]):
            data = " ".join((p.get(ns + "pathData") or "").split())
            if not data or '"' in data or "$" in data:
                sys.exit(f"gen_desktop_poster: {f.name} has path data that cannot be written as a Kotlin string")
            pair.append(data)
        rows.append((key, pair))
    if "other" not in {k for k, _ in rows} or len(rows) < 25:
        sys.exit("gen_desktop_poster: the shop-type icons are not all there (25 with 'other')")
    body = ",\n".join(
        f'        "{k}" to arrayOf(\n            "{a}",\n            "{b}")' for k, (a, b) in rows)
    return EMBLEM_HEAD.replace("@@DATA@@", body)


def main() -> None:
    check = "--check" in sys.argv
    emblems = emblem_source()
    if check:
        if not EMBLEMS.is_file() or EMBLEMS.read_text() != emblems:
            sys.exit("Stale (run python3 scripts/gen_desktop_poster.py): " + EMBLEMS.name)
    else:
        EMBLEMS.write_text(emblems)
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
