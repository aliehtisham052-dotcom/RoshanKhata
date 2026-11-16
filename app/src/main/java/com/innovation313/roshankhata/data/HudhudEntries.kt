package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.HudhudBook.Answer
import com.innovation313.roshankhata.data.HudhudBook.Mood
import com.innovation313.roshankhata.data.HudhudBook.Row
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch

/**
 * Two entries said in one sentence ([Hudhud.Ask.Entries], 16 Nov 2026): "Ali ko 500 diye aur Bashir se 300 liye" was one wrong
 * entry. He fills one at a time: the card lists each entry, and each row — the customer the words name, or the ones they could be —
 * opens that customer's form filled in with that entry's amount and way, for the owner to check and save. Nothing is written here,
 * and a helper's read-only phone is told it cannot write, as for one entry.
 */
object HudhudEntries {

    /** Customers offered for an entry whose words name nobody surely. */
    private const val PICK = 3

    fun answer(c: Context, ask: Hudhud.Ask.Entries, parties: List<PartyWithBalance>): Answer {
        val label = c.getString(R.string.hudhud_l_entries)
        if (ViewerMode.isOn(c)) return Answer(Mood.CALM, label = label, lines = listOf(c.getString(R.string.viewer_refused)), ask = ask)
        val lines = ArrayList<String>()
        lines += c.getString(R.string.hudhud_entry_one_at_a_time)
        val rows = ArrayList<Row>()
        for (e in ask.entries) {
            val way = c.getString(if (e.isGiven) R.string.i_gave else R.string.i_got)
            val found = e.ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
            val choices = found.ifEmpty {
                NameSearch.rankSpoken(parties.sortedByDescending { it.lastActivity }, e.nameWords) { it.name }.take(PICK)
            }
            val day = e.day?.let { Hudhud.entryTime(it) }?.let { " · " + Format.dateOnly(it) } ?: ""
            lines += way + " · " + HudhudBook.money(e.amount) + (found.singleOrNull()?.let { " · " + Format.isolate(it.name) } ?: "") + day
            for (p in choices) rows += Row(p.id, p.name, HudhudBook.money(e.amount), way, amount = e.amount, isGiven = e.isGiven)
        }
        return Answer(Mood.CALM, label = label, lines = lines, rows = rows, money = true, quiet = true, ask = ask)
    }
}
