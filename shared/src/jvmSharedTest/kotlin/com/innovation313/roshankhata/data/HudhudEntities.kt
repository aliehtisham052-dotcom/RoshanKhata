package com.innovation313.roshankhata.data

/**
 * The entities a reading carries (16 Nov 2026, audit T10): which way the money goes, how much, whom, which goods and
 * which days. A reading can have the right meaning and still the wrong amount, side, person, product or days — the
 * errors that cost a shopkeeper — so [HudhudEntityMetricsTest] compares these one by one, apart from the meaning.
 *
 * What is unwrapped is what [HudhudShown] unwraps (a guess, "as it stands now", the first choice of "did you mean"), so
 * the meaning and the entities are read from the same ask; two questions in one sentence and a comparison carry the
 * entities of both halves. A span the book cannot reach ([Hudhud.Ask.SpanLimit]) carries none: it is a refusal.
 */
object HudhudEntities {

    /** A side is compared only with a side of its own family: what is owed (balance) or money that moved (flow). */
    enum class Family { BALANCE, FLOW }

    /**
     * [RECEIVE]: what customers owe the shop ("to get", who owes most, who has not paid); [PAY]: what the shop owes;
     * [IN]: money that came to the shop (a payment received, "I got"); [OUT]: what the shop gave ("I gave", paid out).
     */
    enum class Side(val family: Family) {
        RECEIVE(Family.BALANCE), PAY(Family.BALANCE), IN(Family.FLOW), OUT(Family.FLOW);

        val opposite: Side get() = when (this) {
            RECEIVE -> PAY
            PAY -> RECEIVE
            IN -> OUT
            OUT -> IN
        }
    }

    class Carried {
        val sides = LinkedHashSet<Side>()
        val people = LinkedHashSet<Long>()
        val goods = LinkedHashSet<Long>()
        val windows = LinkedHashSet<VoiceQuestion.Window>()
        val amounts = ArrayList<Double>()
    }

    fun of(a: Hudhud.Ask): Carried = Carried().also { add(a, it) }

    private fun add(a: Hudhud.Ask, c: Carried) {
        when (a) {
            is Hudhud.Ask.Guessed -> add(a.ask, c)
            is Hudhud.Ask.Now -> add(a.ask, c)
            is Hudhud.Ask.DidYouMean -> a.options.firstOrNull()?.let { add(it, c) }
            is Hudhud.Ask.Both -> { add(a.first, c); add(a.second, c) }
            is Hudhud.Ask.Compare -> { add(a.first, c); add(a.second, c) }
            is Hudhud.Ask.Figure -> {
                sideOf(a.kind)?.let { c.sides += it }
                windowOf(a.kind)?.let { c.windows += it }
            }
            is Hudhud.Ask.Entry -> {
                c.amounts += a.amount
                c.sides += if (a.isGiven) Side.OUT else Side.IN
                c.people += a.ids
            }
            is Hudhud.Ask.Moved -> {
                c.sides += if (a.got) Side.IN else Side.OUT
                c.people += a.ids
                c.windows += a.window
            }
            is Hudhud.Ask.OnDays -> {
                when (a.of) {
                    Hudhud.DatedOf.PAID, Hudhud.DatedOf.COLLECTED -> c.sides += Side.IN
                    Hudhud.DatedOf.TOOK -> c.sides += Side.OUT
                    else -> {}
                }
                c.people += a.ids
            }
            is Hudhud.Ask.Collected -> { c.sides += Side.IN; a.window?.let { c.windows += it } }
            is Hudhud.Ask.Parties -> c.people += a.ids
            is Hudhud.Ask.WhenPays -> c.people += a.ids
            is Hudhud.Ask.Person -> c.people += a.ids
            is Hudhud.Ask.Record -> { c.people += a.ids; a.window?.let { c.windows += it } }
            is Hudhud.Ask.Counter -> { c.people += a.ids; a.window?.let { c.windows += it } }
            is Hudhud.Ask.Paper -> c.people += a.ids
            is Hudhud.Ask.Goods -> { c.goods += a.ids; a.window?.let { c.windows += it } }
            is Hudhud.Ask.Period -> c.windows += a.window
            is Hudhud.Ask.Count -> a.window?.let { c.windows += it }
            is Hudhud.Ask.Deliveries -> a.window?.let { c.windows += it }
            is Hudhud.Ask.Activity -> a.window?.let { c.windows += it }
            is Hudhud.Ask.Cashbook -> a.window?.let { c.windows += it }
            is Hudhud.Ask.Returns -> a.window?.let { c.windows += it }
            is Hudhud.Ask.TopGoods -> a.window?.let { c.windows += it }
            else -> {}
        }
    }

    private fun sideOf(k: VoiceQuestion.Kind): Side? = when (k) {
        VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.WHO_OWES_MOST, VoiceQuestion.Kind.WHO_OWES_LEAST,
        VoiceQuestion.Kind.NOT_PAID -> Side.RECEIVE
        VoiceQuestion.Kind.TO_GIVE -> Side.PAY
        else -> null
    }

    private fun windowOf(k: VoiceQuestion.Kind): VoiceQuestion.Window? = when (k) {
        VoiceQuestion.Kind.SALES_TODAY -> VoiceQuestion.Window.TODAY
        VoiceQuestion.Kind.SALES_MONTH, VoiceQuestion.Kind.PROFIT_MONTH -> VoiceQuestion.Window.THIS_MONTH
        else -> null
    }

    /**
     * The side a reading NAMED in a set stands for ("Figure:TO_GET", "Collected", "PaidOut"), as [HudhudShown] spells
     * it; null for a reading whose spelling says no side ("Entry" and "Moved" carry one, but their spelling does not).
     */
    fun sideOfShown(shown: String): Side? = when {
        shown == "Figure:TO_GET" || shown == "Figure:WHO_OWES_MOST" || shown == "Figure:WHO_OWES_LEAST" ||
            shown == "Figure:NOT_PAID" -> Side.RECEIVE
        shown == "Figure:TO_GIVE" -> Side.PAY
        shown == "Collected" -> Side.IN
        shown == "PaidOut" -> Side.OUT
        else -> null
    }

    /** The span a reading NAMED in a set stands for: only a count spells its days ("Count:ENTRIES:TODAY"). */
    fun windowOfShown(shown: String): VoiceQuestion.Window? {
        val parts = shown.split(':')
        if (parts.size != 3 || parts[0] != "Count") return null
        return VoiceQuestion.Window.values().firstOrNull { it.name == parts[2] }
    }

    /**
     * The one amount written in digits in [sentence] (any script's digits), or null when there is none, more than one, or
     * the figure is joined to a letter or a mark ("5k", "1.5", "10,000"): those are not read here, never guessed.
     */
    fun digitAmount(sentence: String): Double? {
        val runs = Regex("\\p{Nd}[\\p{Nd}.,]*").findAll(sentence).toList()
        if (runs.size != 1) return null
        val r = runs[0]
        if (r.value.any { !Character.isDigit(it) }) return null
        val before = sentence.getOrNull(r.range.first - 1)
        val after = sentence.getOrNull(r.range.last + 1)
        if ((before != null && Character.isLetter(before)) || (after != null && Character.isLetter(after))) return null
        var v = 0.0
        for (ch in r.value) v = v * 10 + Character.digit(ch, 10)
        return v
    }

    /**
     * The book's entities written whole in [sentence]: each spelling's id (several spellings may share one id). A Latin
     * spelling is matched as whole words, ignoring case; another script's spelling may carry a joined ending after it
     * ("আলী রাজাকে"). Nothing is read by sound or by a fold: only what is written.
     */
    fun written(sentence: String, spellings: Map<String, Long>): Set<Long> {
        val found = LinkedHashSet<Long>()
        for ((s, id) in spellings) {
            val latin = s.all { it.code < 128 }
            val rx = if (latin) Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(s) + "(?![\\p{L}\\p{N}])")
            else Regex("(?<![\\p{L}\\p{M}])" + Regex.escape(s))
            if (rx.containsMatchIn(sentence)) found += id
        }
        return found
    }
}
