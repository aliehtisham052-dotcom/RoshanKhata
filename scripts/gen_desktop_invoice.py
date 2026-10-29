#!/usr/bin/env python3
"""Regenerate the computer's invoice designs from the phone's.

The ten invoice designs are drawn by the phone's InvoicePdfExport and
InvoiceTemplateKit. The Windows app prints the SAME designs by running the
same code on a small android.graphics stand-in (desktop/.../gfx). This
script copies both files across with only their imports and the
Android-only edges changed (PDF file writing, the PdfRenderer preview), so a
design fixed on the phone is fixed on the computer by re-running it:

    python3 scripts/gen_desktop_invoice.py

CI runs it with --check and fails when the copies are stale.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/innovation313/roshankhata/data"
OUT = ROOT / "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/invoice"

HEADER = (
    "// GENERATED from app/src/main/java/com/innovation313/roshankhata/data/{name}\n"
    "// by scripts/gen_desktop_invoice.py. Do not edit: change the phone's file and re-run.\n"
)

IMPORTS = """package com.innovation313.roshankhata.desktop.invoice

import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.DocKind
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoiceTotals
import com.innovation313.roshankhata.data.lineTotal
import com.innovation313.roshankhata.data.totals
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.Color
import com.innovation313.roshankhata.desktop.gfx.LinearGradient
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.PdfDocument
import com.innovation313.roshankhata.desktop.gfx.Rect
import com.innovation313.roshankhata.desktop.gfx.RectF
import com.innovation313.roshankhata.desktop.gfx.Shader
import com.innovation313.roshankhata.desktop.gfx.Typeface
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NumberWords
import java.io.File
import java.util.Date
"""


def body(text: str) -> str:
    """Everything after the import block."""
    lines = text.splitlines(keepends=True)
    i = 0
    while i < len(lines) and (lines[i].startswith("package ") or lines[i].startswith("import ") or not lines[i].strip()):
        i += 1
    return "".join(lines[i:])


def must(pattern: str, text: str, repl: str, count: int = 1, flags: int = 0) -> str:
    new, n = re.subn(pattern, repl, text, flags=flags)
    if n != count:
        sys.exit(f"gen_desktop_invoice: expected {count} match(es) of {pattern!r}, found {n} - the phone's file changed shape")
    return new


def kit() -> str:
    text = body((SRC / "InvoiceTemplateKit.kt").read_text())
    return HEADER.format(name="InvoiceTemplateKit.kt") + IMPORTS + "\n" + text


def export() -> str:
    text = body((SRC / "InvoicePdfExport.kt").read_text())
    # The preview rasterised a written PDF with android.graphics.pdf.PdfRenderer;
    # the computer draws its preview straight from the pages (PdfDocument.picture).
    text = must(r"\n    /\*\*\n     \* A live preview.*?\n    fun renderPreviewBitmap\(.*?\n    }\n", text, "\n", flags=re.S)
    text = must(r"\n    private fun outputFile\(context: Context, invoice: Invoice\): File \{.*?\n    }\n",
                text, "\n    @Suppress(\"UNUSED_PARAMETER\")\n    private fun outputFile(context: Context, invoice: Invoice): File? = null\n", flags=re.S)
    text = must(r"\n    private fun writeAndClose\(doc: PdfDocument, file: File\): File\? \{.*?\n    }\n",
                text, "\n    @Suppress(\"UNUSED_PARAMETER\")\n    private fun writeAndClose(doc: PdfDocument, file: File?): PdfDocument = doc\n", flags=re.S)
    # No file is written: a design is a finished set of pages, printed or shown.
    text = must(r"\): File\? \{", text, "): PdfDocument? {", count=text.count("): File? {"))
    text = text.replace("android.graphics.DashPathEffect(", "com.innovation313.roshankhata.desktop.gfx.DashPathEffect(")
    code = "\n".join(l for l in text.splitlines() if not l.lstrip().startswith(("*", "//", "/*")))
    if "android." in code:
        sys.exit("gen_desktop_invoice: an Android call is left in InvoicePdfExport: " + re.search(r"android\.[\w.]+", code).group(0))
    return HEADER.format(name="InvoicePdfExport.kt") + IMPORTS + "\n" + text


def main() -> None:
    check = "--check" in sys.argv
    outputs = {OUT / "InvoiceTemplateKit.kt": kit(), OUT / "InvoicePdfExport.kt": export()}
    stale = [p for p, t in outputs.items() if not p.is_file() or p.read_text() != t]
    if check:
        if stale:
            sys.exit("Stale (run python3 scripts/gen_desktop_invoice.py): " + ", ".join(p.name for p in stale))
        print("desktop invoice designs match the phone's")
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for p, t in outputs.items():
        p.write_text(t)
    print("wrote", ", ".join(p.name for p in outputs))


if __name__ == "__main__":
    main()
