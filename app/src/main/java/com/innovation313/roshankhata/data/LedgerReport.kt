package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.innovation313.roshankhata.ui.Format
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A printable PDF of every entry across every customer within one date
 * window — "what happened this week", not "how do the books stand today".
 *
 * This is the piece [BusinessReport] does not cover: that report is a
 * current-balance snapshot with no entries and no date choice at all.
 * Built alongside it in the same visual language (same header band, same
 * "not a backup" warning placed first) so a shopkeeper printing either one
 * recognises it as the same family of document, not a stray new design.
 *
 * Same warning, same reason as [BusinessReport]: a PDF cannot be read back
 * into the app, and the real backup is the .txt file, not this.
 */
object LedgerReport {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private const val NAVY = 0xFF094C2E.toInt()
    private const val GOLD = 0xFFE1AF3F.toInt()
    private const val RED = 0xFFC0392B.toInt()
    private const val GREEN = 0xFF1E8449.toInt()
    private const val GREY = 0xFF7A7A7A.toInt()
    private const val WARN_BG = 0xFFFFF6E0.toInt()
    private const val WARN_FG = 0xFF5C4A16.toInt()

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
    private val entryDateFmt = SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH)
    private val fileFmt = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH)

    /**
     * @param rangeLabel what a person reads on the page — "Today", "This
     * Month", "12 Jun 2026 – 19 Jun 2026" — already formatted by
     * [com.innovation313.roshankhata.ui.DateRangeFilter], not rebuilt here.
     * @return the written file, or null if nothing could be written.
     */
    suspend fun build(context: Context, dao: KhataDao, from: Long, to: Long, rangeLabel: String): File? {
        return try {
            val entries = dao.entriesInRange(from, to)
            render(context, entries, rangeLabel)
        } catch (e: Exception) {
            null
        }
    }

    private fun render(context: Context, entries: List<EntryWithParty>, rangeLabel: String): File? {
        val doc = PdfDocument()

        val title = Paint().apply { color = Color.WHITE; textSize = 22f; isFakeBoldText = true; isAntiAlias = true }
        val tagline = Paint().apply { color = GOLD; textSize = 11f; isAntiAlias = true }
        val section = Paint().apply { color = NAVY; textSize = 14f; isFakeBoldText = true; isAntiAlias = true }
        val body = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val bodyBold = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val muted = Paint().apply { color = GREY; textSize = 10f; isAntiAlias = true }
        val warnText = Paint().apply { color = WARN_FG; textSize = 10f; isAntiAlias = true }
        val red = Paint().apply { color = RED; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val green = Paint().apply { color = GREEN; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val tableHeaderFg = Paint().apply { color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
        val navyFill = Paint().apply { color = NAVY }
        val warnFill = Paint().apply { color = WARN_BG }
        val tableHeaderFill = Paint().apply { color = GREY }
        val rule = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.6f }
        // A translucent wash rather than opaque light-gray: on white it prints
        // the same stripe, but it no longer BLANKS the watermark underneath —
        // an opaque zebra chopped the mark into slices wherever the table
        // crossed it, which the owner rightly called unprofessional.
        val zebra = Paint().apply { color = 0x08000000 }

        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas: Canvas = page.canvas
        var y: Float
        var pageNo = 1

        val brandLogo = PdfBranding.logo(context)
        val businessName = BusinessProfile.businessName(context) ?: "Roshan Khata"

        fun header(): Float {
            // Before anything else on the page: the watermark is a
            // background, so every rule, figure and band that follows sits
            // on top of it rather than under it.
            PdfBranding.drawWatermark(context, canvas, PAGE_W, PAGE_H, NAVY)
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 74f, navyFill)
            canvas.drawText(businessName, MARGIN, 30f, title)
            canvas.drawText("Ledger Report \u00B7 $rangeLabel", MARGIN, 48f, tagline)
            canvas.drawText("Generated ${dateFmt.format(Date())}", MARGIN, 64f, tagline)
            PdfBranding.drawInHeader(canvas, brandLogo, PAGE_W, MARGIN, 74f)
            return 100f
        }

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = header()
        }

        y = header()

        // ---- The warning first, same placement and same reason as BusinessReport ----
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 40f, warnFill)
        canvas.drawText(
            "This is a report to read and print \u2014 it is NOT a backup.",
            MARGIN + 10f, y + 16f,
            Paint(warnText).apply { isFakeBoldText = true }
        )
        canvas.drawText(
            "Roshan Khata cannot restore your records from a PDF.",
            MARGIN + 10f, y + 30f, warnText
        )
        y += 54f

        // ---- Summary for the period ----
        val gave = entries.filter { it.isGiven }.sumOf { it.amount }
        val got = entries.filter { !it.isGiven }.sumOf { it.amount }

        canvas.drawText("Summary", MARGIN, y, section)
        y += 20f

        fun line(label: String, value: String, paint: Paint = body) {
            canvas.drawText(label, MARGIN, y, body)
            val w = paint.measureText(value)
            canvas.drawText(value, PAGE_W - MARGIN - w, y, paint)
            y += 16f
        }

        line("I gave (total)", Format.money(gave), red)
        line("I got (total)", Format.money(got), green)
        line("Net change", Format.money(gave - got), bodyBold)
        line("Entries in this period", entries.size.toString())

        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += 20f

        // ---- Entries, oldest first, every party mixed together ----
        canvas.drawText("Entries", MARGIN, y, section)
        y += 18f

        if (entries.isEmpty()) {
            canvas.drawText("No entries in this period.", MARGIN, y, muted)
            y += 16f
        } else {
            // Two money columns, the way a statement reads: what went out (I
            // gave) and what came in (I got), each in its own place so the eye
            // never has to work out which direction a number is. Column edges
            // are the RIGHT of each money column; text is right-aligned to them.
            val xDate = MARGIN + 4f
            val xParty = MARGIN + 88f
            val xGave = PAGE_W - MARGIN - 92f   // right edge of the "I gave" column
            val xGot = PAGE_W - MARGIN - 4f      // right edge of the "I got" column
            val partyMaxW = xGave - 96f - xParty // room for the name before the money starts

            fun tableHeader() {
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 18f, tableHeaderFill)
                canvas.drawText("DATE", xDate, y + 12.5f, tableHeaderFg)
                canvas.drawText("PARTY", xParty, y + 12.5f, tableHeaderFg)
                canvas.drawText(
                    "I GAVE", xGave - tableHeaderFg.measureText("I GAVE"), y + 12.5f, tableHeaderFg
                )
                canvas.drawText(
                    "I GOT", xGot - tableHeaderFg.measureText("I GOT"), y + 12.5f, tableHeaderFg
                )
                y += 18f
            }

            // Shorten a name that would collide with the money columns, so a
            // long "Abbas Ghala Mandi Pasrur Dokandar" never overruns the amount.
            fun clip(text: String, paint: Paint, maxW: Float): String {
                if (paint.measureText(text) <= maxW) return text
                var end = text.length
                while (end > 1 && paint.measureText(text.substring(0, end) + "\u2026") > maxW) end--
                return text.substring(0, end) + "\u2026"
            }

            tableHeader()

            entries.forEachIndexed { index, e ->
                // A row is taller when it carries a note line beneath the name.
                val hasNote = !e.note.isNullOrBlank()
                val rowH = if (hasNote) 30f else 20f

                if (y + rowH > PAGE_H - 60f) {
                    newPage()
                    canvas.drawText("Entries (continued)", MARGIN, y, section)
                    y += 18f
                    tableHeader()
                }
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowH, zebra)

                val baseline = y + 14f
                canvas.drawText(entryDateFmt.format(Date(e.timestamp)), xDate, baseline, muted)
                canvas.drawText(clip(e.partyName, body, partyMaxW), xParty, baseline, body)

                // The amount sits in exactly one of the two columns.
                val money = Format.money(e.amount)
                if (e.isGiven) {
                    canvas.drawText(money, xGave - red.measureText(money), baseline, red)
                } else {
                    canvas.drawText(money, xGot - green.measureText(money), baseline, green)
                }

                // The note, if any, on its own quiet line under the name — this
                // is the "what was it for" the owner asked to see in the report.
                if (hasNote) {
                    val note = clip(e.note!!.trim(), muted, xGave - xParty - 8f)
                    canvas.drawText(note, xParty, baseline + 12f, muted)
                }

                y += rowH
            }
        }

        // ---- Footer ----
        if (y > PAGE_H - 60f) newPage()
        y = PAGE_H - 34f
        canvas.drawText("Roshan Khata \u00B7 Page $pageNo", MARGIN, y, muted)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "statements").apply { mkdirs() }
        val file = File(dir, "RoshanKhata_LedgerReport_${fileFmt.format(Date())}.pdf")

        return try {
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            file
        } catch (e: Exception) {
            doc.close()
            null
        }
    }
}
