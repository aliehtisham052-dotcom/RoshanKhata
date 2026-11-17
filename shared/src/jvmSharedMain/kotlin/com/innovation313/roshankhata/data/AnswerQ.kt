package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.data.AnswerCard.Row
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W

/**
 * The answers for two readings of 17 Nov 2026 (audit L004 and L019), kept apart from AnswerBook and AnswerFacts so the two could be
 * written side by side. AnswerBook's `answerOf` sends them here:
 *
 *     is Hudhud.Ask.Took -> AnswerQ.took(context, dao, ask, parties)
 *     Hudhud.Ask.NoHistory -> AnswerQ.noHistory(context)
 *
 * Both only READ. Every figure is the screen's own: a customer's last purchase of a product is the Who-bought page's
 * ([KhataDao.customersWhoBought]: his newest "I gave" carrying the product), the rate he was charged the entry form's own hint
 * ([KhataDao.lastSaleRate]), a supplier's the newest bill line Purchase rates reads ([KhataDao.supplierRateLinesOnce], behind
 * REPORTS as every purchase rate).
 */
object AnswerQ {

    /**
     * "Ali Raza ne Urea kab liya", "when did Ali Raza buy Urea" ([Hudhud.Ask.Took]): the day he last took it, and the rate he was
     * charged; for a supplier, the day it last came on his bill. Over the days asked when a span was said. Nothing found is said as
     * such, never a guess; several people or several products named are a choice.
     */
    suspend fun took(c: AnswerEnv, dao: KhataDao, ask: Hudhud.Ask.Took, parties: List<PartyWithBalance>): AnswerCard {
        val who = ask.ids.mapNotNull { id -> parties.firstOrNull { it.id == id } }
        if (who.isEmpty()) return AnswerBook.unknown(c)
        if (who.size > 1) return AnswerBook.pick(c, who)
        val p = who.first()
        val products = dao.productsOnce()
        val found = ask.goods.mapNotNull { id -> products.firstOrNull { it.id == id } }
        if (found.isEmpty()) return AnswerBook.unknown(c)
        if (found.size > 1) {
            return AnswerCard(Mood.CALM, label = c.getString(W.hudhud_pick_party),
                rows = found.map { g -> Row(0, g.name, "", g.company, ask = Hudhud.Ask.Took(listOf(p.id), listOf(g.id), ask.window)) },
                quiet = true)
        }
        val g = found.first()
        val window = ask.window
        val (from, to) = if (window == null) 0L to Long.MAX_VALUE else Hudhud.span(window, c.now())
        val label = p.name + " · " + g.name + (window?.let { " · " + c.getString(AnswerBook.windowWord(it)) } ?: "")
        fun none() = AnswerCard(Mood.CALM, label = label,
            lines = listOf(c.getString(if (window == null) W.hudhud_took_none else W.hudhud_took_none_span,
                Format.isolate(p.name), Format.isolate(g.name))),
            openParty = p.id)
        if (!p.isCustomer) {
            // A supplier: the bill lines of this product from him, newest first (Purchase rates' own rows).
            val lines = dao.supplierRateLinesOnce().filter { l ->
                l.supplierId == p.id && l.billDate >= from && l.billDate < to &&
                    (l.productId == g.id || (l.productId == null && l.productName.trim().equals(g.name.trim(), ignoreCase = true)))
            }.sortedByDescending { it.billDate }
            val last = lines.firstOrNull() ?: return none()
            val rate = if (AnswerFacts.reports()) listOf(AnswerFacts.line(c, W.hudhud_l_price, perUnit(last.rate, last.unit))) else emptyList()
            return AnswerCard(Mood.CALM, label = label, figure = Format.dateOnly(last.billDate),
                lines = listOf(c.getString(W.hudhud_l_last_in)) + rate, open = ScreenId.BILLS)
        }
        val bought = dao.customersWhoBought(g.id, from, to).firstOrNull { it.id == p.id }
        if (bought == null || bought.lastActivity <= 0L) return none()
        // The rate the entry form hints for him (any rate type): only a real, priced sale counts.
        val rate = dao.lastSaleRate(p.id, g.id)
        return AnswerCard(Mood.CALM, label = label, figure = Format.dateOnly(bought.lastActivity),
            lines = listOfNotNull(c.getString(W.hudhud_l_took_last),
                rate?.let { AnswerFacts.line(c, W.hudhud_l_price, perUnit(it.rate, it.unit)) }),
            openParty = p.id)
    }

    /** A rate with its unit, as the product card says it. */
    private fun perUnit(v: Double, unit: String?): String =
        AnswerBook.money(v) + (unit?.takeIf { it.isNotBlank() }?.let { " / " + UnitWords.label(it) } ?: "")

    /**
     * "Pichli chat dikhao", "chat history" ([Hudhud.Ask.NoHistory]): he shows no past conversations; each answer is read from the book
     * as it stands now. Said plainly, where the day's activity was told before.
     */
    fun noHistory(c: AnswerEnv) = AnswerCard(Mood.CALM, lines = listOf(c.getString(W.hudhud_no_history)))
}
