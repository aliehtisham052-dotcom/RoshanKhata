package com.innovation313.roshankhata.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * Makes rectangles in a finished PDF tappable.
 *
 * Android's PdfDocument can draw a "DOWNLOAD" button but has no call to make it
 * a link: what it writes is pictures of text, and a picture of a button does
 * nothing. A PDF link is a separate object (an annotation) that sits on the
 * page and says "this rectangle opens this address".
 *
 * So the link is added afterwards, as an incremental update: the original
 * bytes are left exactly as they were, and a few objects plus a new page entry
 * are appended after them. That is the way the PDF format itself allows a file
 * to be changed without rewriting it, and it means the document the owner
 * already trusts is never re-encoded.
 *
 * It understands the one shape PdfDocument writes (Skia: a plain xref table,
 * plain page objects, no compressed object streams). Anything else, or
 * anything unexpected, and it does nothing and says so: the file stays a
 * correct PDF whose button simply is not tappable. It never leaves a half-made
 * file, because the result is written beside the original and only swapped in
 * once it is complete.
 */
object PdfLinks {

    /** A tappable area, in the same top-left, 1/72-inch units the page was drawn in. */
    class Link(
        val page: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val url: String
    )

    /** True only if the links were added. False leaves [file] untouched. */
    fun addLinks(file: File, links: List<Link>): Boolean {
        if (links.isEmpty()) return false
        return try {
            val updated = append(file.readBytes(), links) ?: return false
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(updated)
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** The whole file with the links appended, or null if it is not a shape this understands. */
    internal fun append(original: ByteArray, links: List<Link>): ByteArray? {
        val text = String(original, Charsets.ISO_8859_1)

        // ---- Where the old table is, and what the old trailer said ----
        val startXrefAt = text.lastIndexOf("startxref")
        if (startXrefAt < 0) return null
        val prev = Regex("""startxref\s+(\d+)""").find(text, startXrefAt)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        // A file whose table is a compressed stream needs a compressed update.
        if (!text.startsWith("xref", prev)) return null
        val trailerAt = text.indexOf("trailer", prev)
        if (trailerAt < 0 || trailerAt > startXrefAt) return null
        val trailer = text.substring(trailerAt, startXrefAt)

        val size = Regex("""/Size\s+(\d+)""").find(trailer)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val root = Regex("""/Root\s+(\d+)\s+\d+\s+R""").find(trailer) ?: return null
        val rootNumber = root.groupValues[1].toInt()
        val info = Regex("""/Info\s+\d+\s+\d+\s+R""").find(trailer)?.value
        val id = Regex("""/ID\s*\[[^\]]*]""").find(trailer)?.value

        // ---- Which object is page 0, page 1, ... ----
        fun body(number: Int): String? {
            val start = Regex("(?m)^$number 0 obj\\s").find(text) ?: return null
            val end = text.indexOf("endobj", start.range.last)
            if (end < 0) return null
            return text.substring(start.range.last + 1, end).trim()
        }

        val pagesNumber = Regex("""/Pages\s+(\d+)\s+\d+\s+R""")
            .find(body(rootNumber) ?: return null)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val pagesBody = body(pagesNumber) ?: return null
        val kids = Regex("""/Kids\s*\[([^\]]*)]""").find(pagesBody)?.groupValues?.get(1) ?: return null
        val pageNumbers = Regex("""(\d+)\s+\d+\s+R""").findAll(kids).map { it.groupValues[1].toInt() }.toList()
        if (pageNumbers.isEmpty()) return null

        val pageTag = Regex("""/Type\s*/Page(?![A-Za-z])""")
        val byPage = links.filter { it.page in pageNumbers.indices }.groupBy { it.page }.toSortedMap()
        if (byPage.isEmpty()) return null

        // ---- Append ----
        val out = ByteArrayOutputStream()
        out.write(original)
        if (original.isNotEmpty() && original.last() != '\n'.code.toByte()) out.write('\n'.code)

        val offsets = sortedMapOf<Int, Int>()
        fun put(number: Int, content: String) {
            offsets[number] = out.size()
            out.write("$number 0 obj\n$content\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        var next = size
        for ((pageIndex, pageLinks) in byPage) {
            val pageNumber = pageNumbers[pageIndex]
            val dict = body(pageNumber) ?: return null
            // Only a plain page dictionary that has no annotations of its own.
            if (!pageTag.containsMatchIn(dict) || !dict.startsWith("<<") || !dict.endsWith(">>")) return null
            if (dict.contains("/Annots") || dict.contains("stream")) return null
            val box = Regex("""/MediaBox\s*\[\s*(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s*]""").find(dict) ?: return null
            val lowerY = box.groupValues[2].toFloatOrNull() ?: return null
            val upperY = box.groupValues[4].toFloatOrNull() ?: return null
            val pageTop = upperY

            val refs = StringBuilder()
            for (link in pageLinks) {
                val url = pdfString(link.url) ?: return null
                // The page was drawn from the top; PDF measures from the bottom.
                val x1 = minOf(link.left, link.right)
                val x2 = maxOf(link.left, link.right)
                val y1 = pageTop - maxOf(link.top, link.bottom)
                val y2 = pageTop - minOf(link.top, link.bottom)
                if (y1 < lowerY - 1f) return null
                val number = next++
                put(
                    number,
                    "<</Type /Annot /Subtype /Link /Rect [${n(x1)} ${n(y1)} ${n(x2)} ${n(y2)}] " +
                        "/Border [0 0 0] /F 4 /A <</S /URI /URI ($url)>>>>"
                )
                refs.append("$number 0 R ")
            }
            put(pageNumber, dict.removeSuffix(">>") + "/Annots [${refs.toString().trim()}]\n>>")
        }

        // ---- New table: only the objects that changed or were added ----
        val xrefAt = out.size()
        val table = StringBuilder("xref\n")
        for ((number, offset) in offsets) {
            table.append("$number 1\n").append(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        table.append("trailer\n<</Size $next ${root.value}")
        info?.let { table.append(" $it") }
        id?.let { table.append(" $it") }
        table.append(" /Prev $prev>>\nstartxref\n$xrefAt\n%%EOF\n")
        out.write(table.toString().toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    /** A PDF literal string for [s], or null if it holds anything but plain printable ASCII. */
    private fun pdfString(s: String): String? {
        if (s.any { it.code < 0x20 || it.code > 0x7E }) return null
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }

    private fun n(v: Float): String = String.format(Locale.US, "%.2f", v)
}
