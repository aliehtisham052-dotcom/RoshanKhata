package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.AnswerCard.Mood
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import kotlin.math.abs

/**
 * The knowledge cards (Marhala 9, lane B, 26 Nov 2026) for [Hudhud.Ask.Knowledge]: nothing of the book is read, so every role is
 * told the same (no figure of the shop is on any of them).
 *
 * - **An atom** ([KnowKind.GENERAL_GUIDANCE], [KnowKind.VERIFIED]): its title, meaning, example and exception in the app's language,
 *   then what kind of answer it is — "general guidance … not yet checked by a person" until the atom's `reviewed_by` names a person —
 *   and its source with the day it was read and the Formula Book ids it rests on. The kind is the atom's own, read from the table
 *   the app ships ([HudhudKnowledge.atom]), so a card can never say "checked" of an atom nobody has checked.
 * - **A worked figure** ([KnowKind.CALCULATED]): the margin, markup or profit the sentence asked, worked by the Formula Book's own
 *   functions ([PriceMath], F66, F67) from the two figures the sentence gave — said to be those figures, never the book's — with
 *   the other two beside it and the atom's source.
 * - **A question back** ([KnowKind.NEEDS_CLARIFICATION]): which figure is the cost and which the sale price, with an example of
 *   how to say it.
 * - **A refusal** ([KnowKind.UNSUPPORTED]): the plan's limits, naming who to ask; a tax rate names the FBR with the day its site was
 *   read.
 *
 * What is said aloud is the card's own sentences ([AnswerCard.spoken]), never its source line.
 */
object AnswerKnowledge {

    /** The day fbr.gov.pk was read for the tax refusal's "where to check" (plan v1 "Never" 6: a tax rule only with its source and day). */
    const val TAX_SOURCE = "Federal Board of Revenue (FBR), fbr.gov.pk"
    const val TAX_READ = "2026-11-26"

    fun answer(c: AnswerEnv, ask: Hudhud.Ask.Knowledge): AnswerCard = when (ask.kind) {
        KnowKind.UNSUPPORTED -> refusal(c, ask.limit ?: KnowLimit.DOSE)
        KnowKind.NEEDS_CLARIFICATION -> AnswerCard(
            Mood.CALM, label = c.getString(W.hudhud_kn_worked_title), lines = listOf(c.getString(W.hudhud_kn_need_roles)),
            spoken = { w -> w.getString(W.hudhud_kn_need_roles) }
        )
        KnowKind.CALCULATED -> worked(c, ask)
        KnowKind.VERIFIED, KnowKind.GENERAL_GUIDANCE -> atomCard(c, ask.atom)
    }

    private fun refusal(c: AnswerEnv, limit: KnowLimit): AnswerCard {
        val words = when (limit) {
            KnowLimit.PESTICIDE -> W.hudhud_kn_refuse_pesticide
            KnowLimit.MEDICINE -> W.hudhud_kn_refuse_medicine
            KnowLimit.DOSE -> W.hudhud_kn_refuse_dose
            KnowLimit.TAX_RATE -> W.hudhud_kn_refuse_tax
            KnowLimit.RULING -> W.hudhud_kn_refuse_ruling
        }
        val lines = ArrayList<String>()
        lines += c.getString(words)
        if (limit == KnowLimit.TAX_RATE) lines += c.getString(W.hudhud_kn_source, TAX_SOURCE, dayText(c, TAX_READ))
        return AnswerCard(Mood.CALM, label = c.getString(W.hudhud_kn_limit_title), lines = lines, spoken = { w -> w.getString(words) })
    }

    private fun atomCard(c: AnswerEnv, id: String?): AnswerCard {
        val atom = HudhudKnowledge.atom(id) ?: return AnswerBook.unknown(c)
        fun said(w: AnswerEnv): List<String> = listOfNotNull(
            w.getString(atom.definition),
            atom.example?.let { w.getString(W.hudhud_kn_example, w.getString(it)) },
            atom.exception?.let { w.getString(it) },
            w.getString(if (atom.kind == KnowKind.VERIFIED) W.hudhud_kn_verified else W.hudhud_kn_guidance)
        )
        return AnswerCard(
            Mood.CALM, label = c.getString(atom.title), lines = said(c) + sourceLine(c, atom),
            spoken = { w -> said(w).joinToString(" ") }
        )
    }

    /** "Source: <name>, read <day> · Formula Book F66": the source, the day it was read and the formulas the atom rests on. */
    private fun sourceLine(c: AnswerEnv, atom: KnowledgeAtom): String {
        val src = c.getString(W.hudhud_kn_source, atom.sources.joinToString(" / "), dayText(c, atom.dateRead))
        return if (atom.formulas.isEmpty()) src else src + " · " + c.getString(W.hudhud_kn_formula, atom.formulas.joinToString(", "))
    }

    private fun worked(c: AnswerEnv, ask: Hudhud.Ask.Knowledge): AnswerCard {
        val cost = ask.cost
        val sale = ask.sale
        val atom = HudhudKnowledge.atom(ask.atom)
        val title = c.getString(W.hudhud_kn_worked_title)
        if (cost == null || sale == null || atom == null) {
            return AnswerCard(Mood.CALM, label = title, lines = listOf(c.getString(W.hudhud_kn_need_roles)),
                spoken = { w -> w.getString(W.hudhud_kn_need_roles) })
        }
        val margin = PriceMath.marginPercent(cost, sale)
        val markup = PriceMath.markupPercent(cost, sale)
        val profit = PriceMath.profit(cost, sale)
        val marginTitle = HudhudKnowledge.atom("K201")?.title ?: W.hudhud_kn_k201_title
        val markupTitle = HudhudKnowledge.atom("K202")?.title ?: W.hudhud_kn_k202_title
        val (nameKey, value) = when (ask.asked ?: KnowMeasure.MARGIN) {
            KnowMeasure.MARGIN -> marginTitle to margin?.let(::percent)
            KnowMeasure.MARKUP -> markupTitle to markup?.let(::percent)
            KnowMeasure.PROFIT -> W.hudhud_kn_profit to profit?.let { AnswerBook.money(it) }
        }
        val lines = ArrayList<String>()
        lines += c.getString(W.hudhud_kn_worked_note)
        lines += c.getString(W.hudhud_kn_cost_sale, AnswerBook.money(cost), AnswerBook.money(sale))
        if (value == null) lines += c.getString(W.hudhud_kn_no_figure)
        profit?.let { lines += c.getString(W.hudhud_kn_profit) + ": " + AnswerBook.money(it) }
        margin?.let { lines += c.getString(marginTitle) + ": " + percent(it) }
        markup?.let { lines += c.getString(markupTitle) + ": " + percent(it) }
        lines += sourceLine(c, atom)
        return AnswerCard(
            Mood.CALM, label = title + " · " + c.getString(nameKey), figure = value, lines = lines,
            spoken = { w ->
                if (value == null) w.getString(W.hudhud_kn_no_figure)
                else w.getString(W.hudhud_kn_worked_say, w.getString(nameKey), value, AnswerBook.money(cost), AnswerBook.money(sale)) +
                    " " + w.getString(W.hudhud_kn_worked_note)
            }
        )
    }

    /** A percent as the cards write it: two decimals at most (half up), the minus outside the left-to-right run. */
    internal fun percent(v: Double): String {
        val two = BigDecimal.valueOf(abs(v)).setScale(2, RoundingMode.HALF_UP).toDouble()
        return (if (v < 0 && two != 0.0) "−" else "") + Format.ltr(Format.percent(two) + "%")
    }

    /** A day of the atoms' table (YYYY-MM-DD) in the reader's words, at midday so no time zone moves it. */
    private fun dayText(c: AnswerEnv, iso: String): String {
        val p = iso.split('-').mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return iso
        val millis = Calendar.getInstance().apply { clear(); set(p[0], p[1] - 1, p[2], 12, 0, 0) }.timeInMillis
        return DateWords.format("d MMMM yyyy", millis, c.locale)
    }

    // ------------------------------------------------------------------------------------------------ chips

    /** A chip's words for a knowledge question: the atom's own title; none for a worked figure, a question back or a refusal. */
    fun chipLabel(c: AnswerEnv, ask: Hudhud.Ask.Knowledge): String? =
        if (ask.kind == KnowKind.VERIFIED || ask.kind == KnowKind.GENERAL_GUIDANCE) HudhudKnowledge.atom(ask.atom)?.let { c.getString(it.title) } else null

    /** What follows an atom or a worked figure: the atoms beside it. A refusal and a question back lead nowhere. */
    fun next(ask: Hudhud.Ask.Knowledge): List<Hudhud.Ask> {
        val ids = when {
            ask.kind == KnowKind.CALCULATED -> listOf("K203", "K201", "K202")
            ask.kind == KnowKind.UNSUPPORTED || ask.kind == KnowKind.NEEDS_CLARIFICATION -> emptyList()
            else -> when (ask.atom) {
                "K101" -> listOf("K102", "K103", "K104")
                "K102" -> listOf("K101", "K103")
                "K103" -> listOf("K101", "K102", "K104")
                "K104" -> listOf("K101", "K103")
                "K201" -> listOf("K202", "K203", "K206")
                "K202" -> listOf("K201", "K203", "K206")
                "K203" -> listOf("K201", "K202", "K206")
                "K204" -> listOf("K205", "K201")
                "K205" -> listOf("K204", "K201")
                "K206" -> listOf("K203", "K202", "K201")
                else -> emptyList()
            }
        }
        return ids.mapNotNull { id -> HudhudKnowledge.atom(id)?.let { Hudhud.Ask.Knowledge(it.kind, atom = id) } }
    }
}
