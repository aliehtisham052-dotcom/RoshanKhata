package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W

/**
 * The Coordinator's outcome (Marhala 6 of the master plan, 26 Nov 2026): what kind of answer one reading and its card came
 * to — one of seven, so a test, the check log or a later screen can say "this was a guess", "this was a refusal", "the book
 * had no record" without reading the words.
 *
 * An ADAPTER, never a decision: it reads what [Hudhud.understand] made of a sentence and what [AnswerBook.answer] made of
 * that reading, and changes neither. The seven outcomes:
 *
 *  - [Outcome.ACCEPT]     answered from the book (a figure, a list, a page, a reply); a true zero of the book's state ("every
 *                         supplier bill is paid", "nobody owes you") is an answer too.
 *  - [Outcome.CLARIFY]    asked back first or offered to choose: the money's side nobody said (D15), "whose?", "which item?",
 *                         the spans he can give.
 *  - [Outcome.SAFE_GUESS] answered as "Taken as: …" with the other readings as chips (D1) — a [Hudhud.Ask.Guessed], and a
 *                         [Hudhud.Ask.DidYouMean] without D15's question, which [AnswerBook] answers as its first meaning.
 *  - [Outcome.REFUSE]     not answered: a figure the role may not see, a read-only copy asked to write, off the book, not
 *                         understood, past chats he keeps none of.
 *  - [Outcome.DRAFT]      a form filled for the owner to save (D7): an entry, two entries, a name to keep.
 *  - [Outcome.NO_DATA]    the book holds no record of what was asked ("No sales recorded …"), said in words, never "Rs 0".
 *  - [Outcome.ERROR]      the book could not be read ([AnswerBook.failed]): no guess.
 *
 * [text] looks a word up by its key ([AnswerEnv.getString] on a platform; tests may pass the key itself): the card's lines
 * are words, so a refusal or a "none" line is known by the word it was made from. Every "none" word the answer layer uses is
 * placed in [NO_RECORD], [STATE_ZERO] or [NOT_AN_OUTCOME] — HudhudCoordinatorTest reads the answer layer's sources and fails
 * for a word placed nowhere, so a new "none" line cannot be missed.
 */
object HudhudCoordinator {

    enum class Outcome { ACCEPT, CLARIFY, SAFE_GUESS, REFUSE, DRAFT, NO_DATA, ERROR }

    /**
     * An outcome, why it was given (a short code: "side", "role", "no_record", …) and, when a certainty was given, the parts the
     * reading stood on a default, a guess or a choice for ([HudhudCertainty.Vector.weak]) — said, never acted on.
     */
    data class Verdict(val outcome: Outcome, val why: String, val weak: List<HudhudCertainty.Part> = emptyList())

    /** A refusal's word and its reason code. */
    val REFUSALS: Map<String, String> = linkedMapOf(
        W.hudhud_owner_only to "role",
        W.hudhud_owner_only_exact to "role",
        W.viewer_refused to "viewer",
        W.radar_state_off to "radar_off",
        W.hudhud_off_topic to "off_topic",
        W.hudhud_unknown to "not_understood",
        W.hudhud_no_history to "no_history"
    )

    /** "The book has no record of this": nothing written in the span, no such field, no such person's record. */
    val NO_RECORD: List<String> = listOf(
        W.hudhud_activity_none, W.hudhud_batches_none, W.hudhud_buyers_none, W.hudhud_buyers_none_span,
        W.hudhud_cashbook_none, W.hudhud_cheques_none_for, W.hudhud_collected_none, W.hudhud_cost_none,
        W.hudhud_count_none, W.hudhud_counter_none, W.hudhud_cover_no_sales, W.hudhud_deliveries_none,
        W.hudhud_limit_none, W.hudhud_no_payment, W.hudhud_no_phone, W.hudhud_none_yet, W.hudhud_note_none,
        W.hudhud_note_nobody, W.hudhud_plan_none_for, W.hudhud_plans_closed_none, W.hudhud_price_none,
        W.hudhud_product_fields_none, W.hudhud_promise_none, W.hudhud_rates_none, W.hudhud_returns_none,
        W.hudhud_scheme_none, W.hudhud_sold_none, W.hudhud_staff_paid_none, W.hudhud_took_none, W.hudhud_took_none_span,
        W.hudhud_top_goods_none, W.hudhud_trend_none, W.hudhud_v_again_none, W.insights_profit_none, W.party_no_entries,
        W.po_none, W.schemes_empty, W.staff_none, W.no_products, W.voice_no_party, W.cf_empty
    )

    /** A true zero of the book's state, said as good news or a fact: an answer ([Outcome.ACCEPT]), not a missing record. */
    val STATE_ZERO: List<String> = listOf(
        W.hudhud_bills_none, W.hudhud_cheques_none, W.hudhud_cheques_bounced_none, W.hudhud_cheques_cleared_none,
        W.hudhud_invoices_none, W.hudhud_low_none, W.hudhud_plans_none, W.hudhud_salary_none, W.hudhud_qarz_none,
        W.hudhud_likely_none, W.hudhud_attendance_none, W.vq_nobody, W.aging_none, W.cf_no_gap
    )

    /** Words that look like a "none" by their key but are a part of a line, a label or a setting, never a whole answer. */
    val NOT_AN_OUTCOME: List<String> = listOf(
        W.hudhud_no_phone_short, W.hudhud_l_no_reason, W.hudhud_l_off, W.hudhud_auto_off, W.hudhud_not_set,
        W.seal_choice_none, W.staff_role_none, W.stock_no_movement, W.plan_next_due_none, W.hudhud_v_off, W.vq_not_paid,
        W.vq_not_paid_body, W.hudhud_why_no_cost, W.hudhud_why_not_down, W.hudhud_why_not_up, W.hudhud_why_only_book,
        W.hudhud_trend_from_nothing, W.hudhud_t_written_off, W.pos_round_off, W.hudhud_kn_no_figure, W.hudhud_credit_not_late
    )

    /**
     * The outcome of [ask] answered with [card]. [certainty] (from [HudhudCertainty.of]) only adds the weak parts to the verdict;
     * it never changes the outcome (D15 asks only where [Hudhud] already asks).
     */
    fun outcomeOf(
        ask: Hudhud.Ask, card: AnswerCard, text: (String) -> String, certainty: HudhudCertainty.Vector? = null
    ): Verdict {
        val v = decide(ask, card, Words(text))
        return if (certainty == null) v else v.copy(weak = certainty.weak)
    }

    private fun decide(ask: Hudhud.Ask, card: AnswerCard, w: Words): Verdict {
        // The book could not be read: whatever was asked, no guess was made.
        if (card.lines.any { w.failed(it) }) return Verdict(Outcome.ERROR, "read_failed")
        return when (ask) {
            is Hudhud.Ask.Guessed -> Verdict(Outcome.SAFE_GUESS, "taken_as")
            is Hudhud.Ask.DidYouMean -> when {
                ask.unsure != null -> Verdict(Outcome.CLARIFY, ask.unsure.name.lowercase())
                ask.options.isEmpty() -> Verdict(Outcome.REFUSE, "not_understood")
                else -> Verdict(Outcome.SAFE_GUESS, "first_of_choice")
            }
            is Hudhud.Ask.NeedName -> Verdict(Outcome.CLARIFY, "whose")
            is Hudhud.Ask.NeedGoods -> Verdict(Outcome.CLARIFY, "which_item")
            is Hudhud.Ask.SpanLimit -> Verdict(Outcome.CLARIFY, "span")
            // An entry fills a form; a read-only copy, or a book with nobody in it, says so instead.
            is Hudhud.Ask.Entry, is Hudhud.Ask.Entries -> byCard(card, w) { Verdict(Outcome.DRAFT, "entry_form") }
            // A name heard is offered to keep; once kept (the card's Save) it is his own device setting, done.
            is Hudhud.Ask.CallMe -> if (ask.save) Verdict(Outcome.ACCEPT, "name_kept") else Verdict(Outcome.DRAFT, "name_offer")
            is Hudhud.Ask.OffTopic -> if (ask.why == Hudhud.OffTopicWhy.SUM) Verdict(Outcome.ACCEPT, "calculator")
                else Verdict(Outcome.REFUSE, "off_topic")
            Hudhud.Ask.Unknown -> Verdict(Outcome.REFUSE, "not_understood")
            Hudhud.Ask.NoHistory -> Verdict(Outcome.REFUSE, "no_history")
            // Lane B (Marhala 9): a limit (dose, tax rate, ruling) is refused with whom to ask; roles not clear ask back;
            // nothing to say is a refusal; an atom or a worked figure answers.
            is Hudhud.Ask.Knowledge -> when {
                ask.limit != null -> Verdict(Outcome.REFUSE, "knowledge_limit")
                ask.kind == KnowKind.NEEDS_CLARIFICATION -> Verdict(Outcome.CLARIFY, "knowledge_roles")
                ask.kind == KnowKind.UNSUPPORTED -> Verdict(Outcome.REFUSE, "knowledge_unsupported")
                else -> Verdict(Outcome.ACCEPT, "knowledge")
            }
            // "As it stands now" only adds a note: the card is the inner reading's.
            is Hudhud.Ask.Now -> decide(ask.ask, card, w)
            is Hudhud.Ask.Figure, is Hudhud.Ask.Parties, Hudhud.Ask.BackupStatus, Hudhud.Ask.Drawer, is Hudhud.Ask.Help,
            Hudhud.Ask.Salaam, Hudhud.Ask.Hello, is Hudhud.Ask.Period, is Hudhud.Ask.Moved, is Hudhud.Ask.WhenPays,
            Hudhud.Ask.Today, Hudhud.Ask.Abilities, is Hudhud.Ask.Chat, is Hudhud.Ask.Person, is Hudhud.Ask.Goods,
            is Hudhud.Ask.Shop, is Hudhud.Ask.About, Hudhud.Ask.Clock, is Hudhud.Ask.Count, is Hudhud.Ask.Status,
            is Hudhud.Ask.Record, is Hudhud.Ask.Deliveries, is Hudhud.Ask.Activity, is Hudhud.Ask.Collected,
            is Hudhud.Ask.Cashbook, is Hudhud.Ask.Returns, is Hudhud.Ask.Counter, is Hudhud.Ask.Act, is Hudhud.Ask.Voice,
            is Hudhud.Ask.TopGoods, Hudhud.Ask.Qarz, Hudhud.Ask.Automations, is Hudhud.Ask.Paper, is Hudhud.Ask.Look,
            is Hudhud.Ask.LookDone, is Hudhud.Ask.OnDays, is Hudhud.Ask.Both, is Hudhud.Ask.Compare, is Hudhud.Ask.Why,
            is Hudhud.Ask.Took, is Hudhud.Ask.Trend -> byCard(card, w) { Verdict(Outcome.ACCEPT, "answered") }
            // More credit with nobody named (Marhala 10): he asks whose, as for any fact of somebody's.
            is Hudhud.Ask.Advise -> if (ask.of == Hudhud.AdviseOf.MORE_CREDIT && ask.ids.isEmpty()) Verdict(Outcome.CLARIFY, "whose")
                else byCard(card, w) { Verdict(Outcome.ACCEPT, "answered") }
        }
    }

    /**
     * What the card itself says, for a reading that answers: a refusal or a missing record shows as a card with no figure and
     * no rows whose line is one of those words; anything else is [otherwise].
     */
    private inline fun byCard(card: AnswerCard, w: Words, otherwise: () -> Verdict): Verdict {
        if (card.figure == null && card.rows.isEmpty()) {
            for (line in card.lines) {
                w.refusal(line)?.let { return Verdict(Outcome.REFUSE, it) }
            }
            if (card.lines.any { w.noRecord(it) }) return Verdict(Outcome.NO_DATA, "no_record")
        }
        return otherwise()
    }

    /** The words looked up once per verdict, each as a pattern its arguments may fill. */
    private class Words(private val text: (String) -> String) {
        private fun pattern(key: String): Regex? {
            val raw = runCatching { text(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parts = raw.split(ARG)
            return Regex(parts.joinToString("(.+?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL)
        }

        private val failure: String? = runCatching { HudhudReadFailure.words(text).trim() }.getOrNull()
        private val refusals: List<Pair<Regex, String>> by lazy { REFUSALS.mapNotNull { (k, why) -> pattern(k)?.let { it to why } } }
        private val none: List<Regex> by lazy { NO_RECORD.mapNotNull { pattern(it) } }

        fun failed(line: String): Boolean = failure != null && line.trim() == failure
        fun refusal(line: String): String? = refusals.firstOrNull { it.first.matches(line.trim()) }?.second
        fun noRecord(line: String): Boolean = none.any { it.matches(line.trim()) }
    }

    /** A format argument in a word ("%1\$s", "%d", "%s"). */
    private val ARG = Regex("%(\\d+\\$)?[sd]")
}
