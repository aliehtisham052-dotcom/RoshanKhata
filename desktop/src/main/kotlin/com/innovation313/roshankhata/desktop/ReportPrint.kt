package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.EntryWithParty
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.awt.Color
import java.util.Date

/**
 * The Khata Report on paper (KMP Phase 7e; Marhala 4, part 7 — the phone's data/LedgerReport.kt, page for
 * page): the shop on the green band with the report's name and period and when it was made; that the paper
 * is a report and not a backup; the two totals, never netted, and how many entries; then every entry across
 * every party, oldest first, the money in its own column (I gave, I got) and the note on a quiet line under
 * the name. Saved as a PDF or printed from the preview ([StatementPrint.show]).
 */
class ReportPrint(
    private val words: Words,
    rtl: Boolean,
    private val shopName: String,
    private val period: String,
    entries: List<EntryWithParty>,
    made: Long = System.currentTimeMillis()
) : PhoneSheet(rtl) {

    private val rows = entries.sortedWith(compareBy({ it.timestamp }, { it.id }))
    private val madeAt = DateWords.formatter("dd MMM yyyy, HH:mm").format(Date(made))

    override fun Pen.head(): Double {
        box(0.0, 0.0, PAGE_W, 74.0, BAND)
        text(fit(shopName, font(22f, true), PAGE_W - 2 * MARGIN), MARGIN, 30.0, font(22f, true), Color.WHITE)
        text(fit(words.text(W.pdf_rep_ledger_title) + " · " + period, font(11f), PAGE_W - 2 * MARGIN), MARGIN, 48.0, font(11f), GOLD)
        text(words.text(W.pdf_rep_generated, madeAt), MARGIN, 64.0, font(11f), GOLD)
        return 100.0
    }

    override fun footer(page: Int): String = words.text(W.pdf_rep_footer_page, words.text(W.app_name), page)

    override fun Pen.write() {
        note(listOf(words.text(W.pdf_rep_not_backup), words.text(W.pdf_rep_not_backup_restore, words.text(W.app_name))), boldFirst = true)

        // Two totals, never netted: one customer's sale less another's payment is nobody's balance.
        section(words.text(W.pdf_rep_summary))
        line(words.text(W.pdf_rep_total_gave), Format.money(rows.filter { it.isGiven }.sumOf { it.amount }), font(11f, true), RED)
        line(words.text(W.pdf_rep_total_got), Format.money(rows.filter { !it.isGiven }.sumOf { it.amount }), font(11f, true), GREEN)
        line(words.text(W.pdf_rep_entry_count), rows.size.toString())
        divider()

        text(words.text(W.pdf_rep_entries), MARGIN, y, font(14f, true), BAND)
        y += 18.0
        if (rows.isEmpty()) {
            text(words.text(W.no_entries_in_range), MARGIN, y, font(10f), GREY)
            y += 16.0
            return
        }
        // The money columns end at their right edges, the way a statement reads.
        val xDate = MARGIN + 4.0
        val xParty = MARGIN + 88.0
        val xGave = PAGE_W - MARGIN - 92.0
        val xGot = PAGE_W - MARGIN - 4.0
        val partyRoom = xGave - 96.0 - xParty
        val head = listOf(
            Triple(words.text(W.pdf_rep_col_date), xDate, false), Triple(words.text(W.pdf_rep_col_party), xParty, false),
            Triple(words.text(W.pdf_rep_col_gave), xGave, true), Triple(words.text(W.pdf_rep_col_got), xGot, true)
        )
        tableHead(head)
        val dayFmt = DateWords.formatter("dd MMM, HH:mm")
        rows.forEachIndexed { i, e ->
            val note = e.note?.trim()?.takeIf { it.isNotEmpty() }
            val h = if (note != null) 30.0 else 20.0
            if (turnFor(h)) {
                text(words.text(W.pdf_rep_entries_continued), MARGIN, y, font(14f, true), BAND)
                y += 18.0
                tableHead(head)
            }
            if (i % 2 == 1) box(MARGIN, y, PAGE_W - MARGIN, y + h, ZEBRA)
            val base = y + 14.0
            // A date is never cut: one too wide for its column (a long month name) is drawn smaller.
            val date = dayFmt.format(Date(e.timestamp))
            val room = xParty - xDate - 6.0
            val dateFont = width(date, font(10f)).let { w -> if (w <= room) font(10f) else font((10.0 * room / w).toFloat()) }
            text(date, xDate, base, dateFont, GREY)
            text(fit(e.partyName, font(11f), partyRoom), xParty, base, font(11f), Color.BLACK)
            val money = Format.money(e.amount)
            if (e.isGiven) text(money, xGave, base, font(11f, true), RED, end = true)
            else text(money, xGot, base, font(11f, true), GREEN, end = true)
            if (note != null) text(fit(note, font(10f), xGave - xParty - 8.0), xParty, base + 12.0, font(10f), GREY)
            y += h
        }
    }
}
