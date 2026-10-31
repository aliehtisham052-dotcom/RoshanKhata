#!/usr/bin/env python3
"""Regenerate the computer's stock register (the inspector's document) from the phone's.

The phone's InspectorReport draws the register an agriculture-department
inspector asks for. The Windows app prints the SAME document by running the
same code on the android.graphics stand-in (desktop/.../gfx) and the Context
stand-in the invoices use (desktop/.../invoice/Context.kt). This script copies
the file across with only its imports and the Android-only edges changed (the
PDF file is not written: the pages go to the app's own preview, which saves or
prints them), so a change on the phone reaches the computer by re-running it:

    python3 scripts/gen_desktop_inspector.py

CI runs it with --check (the Strings job) and fails when the copy is stale.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/innovation313/roshankhata/data/InspectorReport.kt"
OUT = ROOT / "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/invoice/InspectorReport.kt"

HEADER = (
    "// GENERATED from app/src/main/java/com/innovation313/roshankhata/data/InspectorReport.kt\n"
    "// by scripts/gen_desktop_inspector.py. Do not edit: change the phone's file and re-run.\n"
)

IMPORTS = """package com.innovation313.roshankhata.desktop.invoice

import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.Fefo
import com.innovation313.roshankhata.data.InspectorBatch
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.NameOrder
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.RegisterStyle
import com.innovation313.roshankhata.data.Stock
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.Color
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.Path
import com.innovation313.roshankhata.desktop.gfx.PdfDocument
import com.innovation313.roshankhata.desktop.gfx.RectF
import com.innovation313.roshankhata.desktop.gfx.Typeface
import com.innovation313.roshankhata.ui.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
"""


def body(text: str) -> str:
    lines = text.splitlines(keepends=True)
    i = 0
    while i < len(lines) and (lines[i].startswith("package ") or lines[i].startswith("import ") or not lines[i].strip()):
        i += 1
    return "".join(lines[i:])


def must(pattern: str, text: str, repl: str, count: int = 1, flags: int = 0) -> str:
    new, n = re.subn(pattern, repl, text, flags=flags)
    if n != count:
        sys.exit(f"gen_desktop_inspector: expected {count} match(es) of {pattern!r}, found {n} - the phone's file changed shape")
    return new


def report() -> str:
    text = body(SRC.read_text())
    # No file is written on the computer: build gives the finished pages, the preview saves or prints them.
    text = must(r"    suspend fun build\(context: Context, dao: KhataDao, windowDays: Int, style: RegisterStyle = RegisterStyle.AGRI\): File\? \{", text,
                "    suspend fun build(context: Context, dao: KhataDao, windowDays: Int, style: RegisterStyle = RegisterStyle.AGRI): PdfDocument? {")
    text = must(
        r"\n    private fun render\(context: Context, d: ReportData\): File\? \{.*?\n    }\n",
        text,
        "\n    private fun render(context: Context, d: ReportData): PdfDocument {\n"
        "        val pageCount = try {\n"
        "            val (counting, n) = draw(context, d, null)\n"
        "            counting.close()\n"
        "            n\n"
        "        } catch (e: Exception) {\n"
        "            null\n"
        "        }\n"
        "        return draw(context, d, pageCount).first\n"
        "    }\n",
        flags=re.S,
    )
    # The phone names android.graphics.Rect in full for the bitmap's source; the stand-in's Rect.
    text = text.replace("android.graphics.Rect(", "com.innovation313.roshankhata.desktop.gfx.Rect(")
    code = "\n".join(l for l in text.splitlines() if not l.lstrip().startswith(("*", "//", "/*")))
    if "android." in code:
        sys.exit("gen_desktop_inspector: an Android call is left: " + re.search(r"android\.[\w.]+", code).group(0))
    for left in ("FileOutputStream", "cacheDir", "applyLinks"):
        if left in code:
            sys.exit(f"gen_desktop_inspector: {left} is left in the copy")
    return HEADER + IMPORTS + "\n" + text


def main() -> None:
    check = "--check" in sys.argv
    text = report()
    if check:
        if not OUT.is_file() or OUT.read_text() != text:
            sys.exit("Stale (run python3 scripts/gen_desktop_inspector.py): InspectorReport.kt")
        print("the computer's stock register matches the phone's")
        return
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text)
    print("wrote", OUT.name)


if __name__ == "__main__":
    main()
