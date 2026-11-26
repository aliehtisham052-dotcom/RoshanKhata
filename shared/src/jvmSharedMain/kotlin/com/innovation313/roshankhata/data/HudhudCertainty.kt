package com.innovation313.roshankhata.data

/**
 * How sure a reading is, part by part (Marhala 6 of the master plan, 26 Nov 2026): what [Hudhud.understand] made of a sentence
 * — the meaning, the person, the product, the amount, the span of days, the money's side and the language — each said as one
 * [Level], from what the reading already carries and the sentence's own words.
 *
 * An ADAPTER, never a second reader: it calls the helpers [Hudhud] and [VoiceQuestion] already use (the side said by the words,
 * the span said by the words, the sense's own score) and changes no reading. [HudhudCoordinator] reads it to say which parts of
 * an answer stood on a default; it does not turn an answer into a question (D15 asks only where [Hudhud] already asks: the
 * side of a figure nobody said, [Hudhud.Ask.DidYouMean] with [Hudhud.Unsure.SIDE]).
 *
 * Levels, most sure first:
 *  - [Level.SURE]      the sentence says it, or the rules found exactly one.
 *  - [Level.ASKED]     Hudhud asks it back first (D15).
 *  - [Level.CHOICE]    several are offered (two Alis, two products, the spans he can give).
 *  - [Level.GUESSED]   the sense's reading, written over the card as "Taken as" (D1).
 *  - [Level.DEFAULT]   nobody said it; the reading's own default stands (today for a span, the shop's to get for a debt).
 *  - [Level.MISSING]   the question needs it and the sentence gave none ("whose?", "which item?").
 *  - [Level.NOT_ASKED] the question has no such part.
 */
object HudhudCertainty {

    enum class Part { MEANING, PERSON, PRODUCT, AMOUNT, SPAN, SIDE, LANGUAGE }

    enum class Level { SURE, ASKED, CHOICE, GUESSED, DEFAULT, MISSING, NOT_ASKED }

    /** The weak levels: an answer that stands on one of them says so ([Vector.weak]). */
    val WEAK = setOf(Level.CHOICE, Level.GUESSED, Level.DEFAULT, Level.MISSING)

    /**
     * One reading's certainty. [meaningScore] is the sense's own share for its first meaning (0 to 1), null when no sense was
     * asked or none is loaded; [language] the language the sentence was read in, as given.
     */
    data class Vector(val levels: Map<Part, Level>, val meaningScore: Double? = null, val language: String? = null) {
        operator fun get(p: Part): Level = levels[p] ?: Level.NOT_ASKED

        /** The parts that stood on a default, a guess, a choice or nothing. */
        val weak: List<Part> get() = Part.values().filter { get(it) in WEAK }
    }

    /**
     * The certainty of [ask], the reading [Hudhud.understand] made of [heard]. [withSense]: also ask the learnt sense for its
     * score (a few milliseconds; null when no model is loaded).
     */
    fun of(
        heard: String, ask: Hudhud.Ask, names: List<Hudhud.Name> = emptyList(), goods: List<Hudhud.Name> = emptyList(),
        topics: List<Hudhud.Topic> = emptyList(), language: String? = null, withSense: Boolean = false
    ): Vector {
        val inner = innerOf(ask)
        val levels = LinkedHashMap<Part, Level>()
        levels[Part.MEANING] = meaningOf(ask)
        levels[Part.PERSON] = personOf(inner)
        levels[Part.PRODUCT] = productOf(inner)
        levels[Part.AMOUNT] = amountOf(inner)
        levels[Part.SPAN] = spanOf(heard, inner)
        levels[Part.SIDE] = sideOf(heard, ask, inner, language)
        levels[Part.LANGUAGE] = if (language.isNullOrBlank()) Level.DEFAULT else Level.SURE
        val score = if (withSense) {
            runCatching { Hudhud.bySense(heard, names, topics, language, goods)?.second }.getOrNull()
        } else null
        return Vector(levels, score, language)
    }

    /** The reading inside the wrappers that only add a note ("as it stands now", "taken as"). */
    internal fun innerOf(ask: Hudhud.Ask): Hudhud.Ask = when (ask) {
        is Hudhud.Ask.Guessed -> innerOf(ask.ask)
        is Hudhud.Ask.Now -> innerOf(ask.ask)
        else -> ask
    }

    private fun meaningOf(ask: Hudhud.Ask): Level = when (ask) {
        is Hudhud.Ask.Guessed -> Level.GUESSED
        is Hudhud.Ask.Now -> meaningOf(ask.ask)
        is Hudhud.Ask.DidYouMean -> when {
            ask.unsure != null -> Level.ASKED
            ask.options.isEmpty() -> Level.MISSING
            else -> Level.CHOICE
        }
        Hudhud.Ask.Unknown -> Level.MISSING
        is Hudhud.Ask.OffTopic -> if (ask.why == Hudhud.OffTopicWhy.GENERAL) Level.MISSING else Level.SURE
        else -> Level.SURE
    }

    private fun count(ids: List<Long>): Level = when (ids.size) {
        0 -> Level.MISSING
        1 -> Level.SURE
        else -> Level.CHOICE
    }

    private fun personOf(a: Hudhud.Ask): Level = when (a) {
        is Hudhud.Ask.Parties -> count(a.ids)
        is Hudhud.Ask.Person -> count(a.ids)
        is Hudhud.Ask.Moved -> count(a.ids)
        is Hudhud.Ask.Took -> count(a.ids)
        is Hudhud.Ask.Entry -> count(a.ids)
        is Hudhud.Ask.Entries -> worst(a.entries.map { count(it.ids) })
        // Nobody named is the list of likely payers, a question of its own: no person was asked.
        is Hudhud.Ask.WhenPays -> if (a.ids.isEmpty()) Level.NOT_ASKED else count(a.ids)
        is Hudhud.Ask.NeedName -> Level.MISSING
        else -> Level.NOT_ASKED
    }

    private fun productOf(a: Hudhud.Ask): Level = when (a) {
        is Hudhud.Ask.Goods -> count(a.ids)
        is Hudhud.Ask.Took -> count(a.goods)
        is Hudhud.Ask.NeedGoods -> Level.MISSING
        else -> Level.NOT_ASKED
    }

    private fun amountOf(a: Hudhud.Ask): Level = when (a) {
        // An entry is made only from an amount the sentence said (Hudhud.askFor refuses one with none).
        is Hudhud.Ask.Entry -> if (a.amount > 0.0) Level.SURE else Level.MISSING
        is Hudhud.Ask.Entries -> if (a.entries.all { it.amount > 0.0 }) Level.SURE else Level.MISSING
        else -> Level.NOT_ASKED
    }

    /** Asks that are read over a span of days, said or defaulted (today, or the screen's own). */
    private fun carriesSpan(a: Hudhud.Ask): Boolean = Hudhud.spanOf(a) != null || when (a) {
        is Hudhud.Ask.Count, is Hudhud.Ask.Deliveries, is Hudhud.Ask.Activity, is Hudhud.Ask.Collected, is Hudhud.Ask.Cashbook,
        is Hudhud.Ask.Returns, is Hudhud.Ask.TopGoods, is Hudhud.Ask.Counter, is Hudhud.Ask.Why, is Hudhud.Ask.Record -> true
        else -> false
    }

    private fun spanOf(heard: String, a: Hudhud.Ask): Level = when {
        a is Hudhud.Ask.OnDays -> Level.SURE
        a is Hudhud.Ask.SpanLimit -> Level.CHOICE
        !carriesSpan(a) -> Level.NOT_ASKED
        VoiceQuestion.windowOf(heard) != null -> Level.SURE
        else -> Level.DEFAULT
    }

    /** The figures that carry one side of what is owed. */
    private val SIDED = setOf(
        VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.TO_GIVE, VoiceQuestion.Kind.NOT_PAID,
        VoiceQuestion.Kind.WHO_OWES_MOST, VoiceQuestion.Kind.WHO_OWES_LEAST
    )

    private fun sideOf(heard: String, ask: Hudhud.Ask, inner: Hudhud.Ask, language: String?): Level {
        if (ask is Hudhud.Ask.DidYouMean && ask.unsure == Hudhud.Unsure.SIDE) return Level.ASKED
        return when (inner) {
            is Hudhud.Ask.Figure -> if (inner.kind !in SIDED) Level.NOT_ASKED
                else if (VoiceQuestion.sideSaid(heard) || VoiceQuestion.payableSaid(heard)) Level.SURE else Level.DEFAULT
            is Hudhud.Ask.Entry, is Hudhud.Ask.Entries ->
                if (VoiceEntry.parse(heard, emptyList(), language).isGiven != null) Level.SURE else Level.DEFAULT
            else -> Level.NOT_ASKED
        }
    }

    /** The weakest of several levels (an entry of two, one of them a choice, is a choice). */
    private fun worst(all: List<Level>): Level = all.maxByOrNull { it.ordinal } ?: Level.NOT_ASKED
}
