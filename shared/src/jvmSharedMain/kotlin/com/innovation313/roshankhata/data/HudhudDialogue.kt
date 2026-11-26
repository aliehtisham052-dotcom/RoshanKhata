package com.innovation313.roshankhata.data

/**
 * What a talk with Hudhud is about, and the turns that only make sense inside one (26 Nov 2026, Marhala 7 of the master plan:
 * "conversation: memory, 'no, the other Ali', follow-ups"; exit gate: stale-memory mistakes 0).
 *
 * Before this the phone's sheet and the computer's panel each kept one field, `before` (the last question carried on), and
 * nothing ever cleared it but closing the sheet: a business switched or a cashier signed in under the panel still carried the
 * owner's last figure; "nahi, doosra Ali" was read as the list of who has not paid ("nahi" is also "not paid"); "haan" to a
 * question he had just asked was "can you hear me"; a name said in answer to "whose number?" opened that man's balance, not
 * his number. One [State] now holds the talk, both platforms keep it the same way, and one reader ([read]) answers the turns
 * that belong to the talk before the sentence is read on its own ([Hudhud.understand], with [State.about] as its `before`).
 *
 * Nothing here writes (D7): "haan" takes only a reading that answers ([safe]) — never an entry form, a saved name or a change
 * to the look; to a choice it does not pick one, it asks the choice again.
 *
 * What clears the talk ([current], [after]): another business's book, another person signed in or other permissions (a cashier
 * must never be handed the owner's carried figure), ten quiet minutes, a greeting or a goodbye. A new person, product or
 * question replaces the old by itself: the talk is always the last question that carries ([carries]).
 */
object HudhudDialogue {

    /** Ten quiet minutes and the talk is over: "aur pichle mahine?" next morning is not about last night's figure. */
    const val TIME_OUT_MS = 10 * 60_000L

    /**
     * The talk: [book] and [who] it belongs to, when it last moved ([at]), what it is about ([about]: the question the next
     * one carries on from — its person, product, figure and span), the question he asked back ([pending]: a choice offered,
     * whose, which way the money runs, a guess) and the last question answered ([last], whatever it was: a correction mends
     * only the turn just before it).
     */
    data class State(
        val book: Long? = null,
        val who: String? = null,
        val at: Long = 0L,
        val about: Hudhud.Ask? = null,
        val pending: Hudhud.Ask? = null,
        val last: Hudhud.Ask? = null
    ) {
        /** The customers or suppliers the talk is about. */
        val person: List<Long>? get() = about?.let { peopleOf(it) }
        /** The product the talk is about. */
        val product: List<Long>? get() = about?.let { goodsOf(it) }
        /** The span of days the talk is about. */
        val span: VoiceQuestion.Window? get() = about?.let { Hudhud.spanOf(it) }
    }

    /**
     * Who asks, as the talk knows him: the account signed in and what he may see. A change of any part ends the talk ([current]).
     * The platforms pass `Access.account(Access.current)` and the gate's answers (AnswerGate), the same way on both.
     */
    fun who(account: String, reports: Boolean, staff: Boolean): String = "$account|r=$reports|s=$staff"

    /**
     * The talk as it stands for a question asked now in [book] by [who]: the same talk, or a new one when the book or the person
     * changed or it lay quiet longer than [TIME_OUT_MS]. Never a talk of another book or person.
     */
    fun current(s: State, book: Long, who: String, now: Long): State =
        if (s.book != book || s.who != who || (s.at > 0L && now - s.at > TIME_OUT_MS) || now < s.at) State(book, who, now) else s

    /**
     * The talk after [asked] was answered ([reverent]: a matter of faith or his story, which is a detour — the shop's talk goes on
     * from where it was). A greeting or a goodbye starts it again; a change to his voice ("dobara bolo") leaves it as it was, so a
     * question he asked back can still be answered after it is said again.
     */
    fun after(s: State, asked: Hudhud.Ask, reverent: Boolean, now: Long): State {
        val taken = (asked as? Hudhud.Ask.Guessed)?.ask ?: asked
        if (taken is Hudhud.Ask.Voice || taken is Hudhud.Ask.Look || taken is Hudhud.Ask.LookDone) return s.copy(at = now)
        if (ends(taken)) return State(s.book, s.who, now)
        if (reverent) return s.copy(at = now, pending = null)
        return s.copy(
            at = now,
            about = if (carries(taken, reverent)) taken else s.about,
            pending = asksBack(asked),
            last = taken
        )
    }

    /**
     * Whether a question is what the next one carries on from (16 Nov 2026, the phone's and the computer's one rule, moved here
     * from AnswerBook.carriesOn): not a question back, a choice, a change to his voice or the look, a name to call the owner by,
     * a span he cannot give, a matter of faith.
     */
    fun carries(taken: Hudhud.Ask, reverent: Boolean): Boolean =
        !reverent && taken !is Hudhud.Ask.DidYouMean && taken != Hudhud.Ask.Unknown &&
            taken !is Hudhud.Ask.NeedName && taken !is Hudhud.Ask.Voice && taken !is Hudhud.Ask.NeedGoods &&
            taken !is Hudhud.Ask.CallMe && taken !is Hudhud.Ask.Look && taken !is Hudhud.Ask.LookDone &&
            taken !is Hudhud.Ask.SpanLimit

    /** A greeting or a goodbye: the talk before is over. */
    private fun ends(a: Hudhud.Ask): Boolean =
        a == Hudhud.Ask.Salaam || a == Hudhud.Ask.Hello || (a is Hudhud.Ask.Chat && (a.talk == Hudhud.Talk.BYE || a.salaam))

    /** The question an answer asks back, which the next turn may answer: a choice, "whose?", which way, a guess. */
    private fun asksBack(a: Hudhud.Ask): Hudhud.Ask? = when (a) {
        is Hudhud.Ask.DidYouMean, is Hudhud.Ask.NeedName, is Hudhud.Ask.NeedGoods, is Hudhud.Ask.Guessed -> a
        is Hudhud.Ask.Parties, is Hudhud.Ask.Person, is Hudhud.Ask.Moved, is Hudhud.Ask.WhenPays ->
            a.takeIf { (peopleOf(it)?.size ?: 0) > 1 }
        is Hudhud.Ask.Goods -> a.takeIf { it.ids.size > 1 }
        else -> null
    }

    /**
     * What [candidates] (the recogniser's guesses, best first) ask inside the talk [s]: a turn of the talk itself — yes, no,
     * cancel, the first or second of a choice, a correction, a name given to "whose?" — when the first guess is one; else the
     * sentence read on its own, carried on from [State.about].
     */
    fun read(
        candidates: List<String>, names: List<Hudhud.Name>, topics: List<Hudhud.Topic>, language: String?,
        goods: List<Hudhud.Name> = emptyList(), staff: List<Hudhud.Name> = emptyList(), s: State
    ): Pair<String, Hudhud.Ask> {
        val first = candidates.firstOrNull() ?: return "" to Hudhud.Ask.Unknown
        turn(first, names, topics, language, goods, staff, s)?.let { return first to it }
        return Hudhud.understand(candidates, names, topics, language, goods, s.about, staff)
    }

    /** [read] for one sentence. */
    fun read(
        heard: String, names: List<Hudhud.Name>, topics: List<Hudhud.Topic>, language: String?,
        goods: List<Hudhud.Name> = emptyList(), staff: List<Hudhud.Name> = emptyList(), s: State
    ): Hudhud.Ask = read(listOf(heard), names, topics, language, goods, staff, s).second

    /** A turn that belongs to the talk, or null for a sentence to be read on its own. */
    private fun turn(
        heard: String, names: List<Hudhud.Name>, topics: List<Hudhud.Topic>, language: String?,
        goods: List<Hudhud.Name>, staff: List<Hudhud.Name>, s: State
    ): Hudhud.Ask? {
        val said = words(heard).filter { it !in FILLERS }
        if (said.isEmpty()) return null
        val pending = s.pending
        val talking = pending != null || s.last != null
        val alone = said.joinToString(" ")
        if (pending != null) {
            if (alone in YES) return yes(pending)
            if (alone in NO) return no(pending)
            ordinal(said)?.let { n -> nth(pending, n)?.let { return it } }
        }
        if (talking && (alone in NO || alone in CANCEL)) return CANCELLED
        // "Nahi, doosra Ali", "no, the other Ali", "nahi, Ali Hassan", "doosra Ali": only the part corrected changes.
        val last = s.last
        if (last != null) {
            corrected(said, last, names, topics, language, goods, staff, s)?.let { return it }
        }
        // A name, or a product, given to the question he asked back: "Ali Raza" after "whose number?".
        if (pending != null) answered(heard, pending, names, topics, language, goods, staff)?.let { return it }
        // "Aur Bashir ka?", "اور بشیر کا؟", "وبشير؟", "Bashir?": the same question asked of another man.
        return andAnother(said, s.about, names, language)
    }

    /**
     * "And <name>?" (or the name alone) after a question asked of one man — his number, when he pays, what he paid over some days:
     * the same question of the man named (Roman Urdu and English already read so; Urdu and Hindi script gave the new man's balance,
     * Arabic's joined "و" nothing at all). Only "and", the name and the little words that link it: anything else is a question of its
     * own. Never after an entry or another kind of question: "aur Bashir?" after "Ali ko 500 diye" opens no form in his name.
     */
    private fun andAnother(said: List<String>, about: Hudhud.Ask?, names: List<Hudhud.Name>, language: String?): Hudhud.Ask? {
        about ?: return null
        if (about !is Hudhud.Ask.Person && about !is Hudhud.Ask.WhenPays && about !is Hudhud.Ask.Moved) return null
        val before = peopleOf(about)?.takeIf { it.size == 1 } ?: return null
        val ws = said.flatMap { w -> if (w.length > 3 && w.startsWith(JOINED_AND)) listOf(JOINED_AND, w.drop(1)) else listOf(w) }
        val rest = (if (ws.firstOrNull() in AND) ws.drop(1) else ws).filter { it !in LINKS && it !in ONE_WORDS }
        if (rest.isEmpty() || !onlyName(rest, names, language)) return null
        val ids = peopleIn(rest.joinToString(" "), names, language).takeIf { it.isNotEmpty() && it != before } ?: return null
        return withPeople(about, ids)
    }

    /** The talk's own answer to "cancel" or a "no" with nothing else: the question back is dropped, he waits for the next. */
    private val CANCELLED = Hudhud.Ask.Chat(Hudhud.Talk.ADDRESS)

    /**
     * "Haan" to the question he asked: the reading he took when it was a guess, the first meaning of a choice of meanings when it
     * answers ([safe]); to a choice of people, which way, or "whose?" — the same question again (yes picks none of them).
     */
    private fun yes(p: Hudhud.Ask): Hudhud.Ask = when {
        p is Hudhud.Ask.Guessed && safe(p.ask) -> p.ask
        p is Hudhud.Ask.DidYouMean && p.unsure == null && p.options.size == 1 && safe(p.options.first()) -> p.options.first()
        p is Hudhud.Ask.DidYouMean && p.unsure == null && p.options.firstOrNull()?.let(::safe) == true -> p.options.first()
        else -> p
    }

    /** "Nahi" to a choice of meanings: the next meaning, the rest still offered; to anything else, the question is dropped. */
    private fun no(p: Hudhud.Ask): Hudhud.Ask =
        if (p is Hudhud.Ask.DidYouMean && p.unsure == null && p.options.size > 1 && p.options.drop(1).all(::safe))
            Hudhud.Ask.DidYouMean(p.options.drop(1)) else CANCELLED

    /** A reading "haan" may take: one that only reads. Never an entry form, a name to keep, the look, his voice (D7). */
    internal fun safe(a: Hudhud.Ask): Boolean = when (a) {
        is Hudhud.Ask.Entry, is Hudhud.Ask.Entries, is Hudhud.Ask.CallMe, is Hudhud.Ask.Look, is Hudhud.Ask.LookDone,
        is Hudhud.Ask.Voice, is Hudhud.Ask.Act, Hudhud.Ask.Unknown -> false
        is Hudhud.Ask.Guessed -> safe(a.ask)
        is Hudhud.Ask.Now -> safe(a.ask)
        is Hudhud.Ask.DidYouMean -> false
        else -> true
    }

    /** The [n]th (from 1) of a choice: of people as the card lists them, of meanings as the chips offer them. */
    private fun nth(p: Hudhud.Ask, n: Int): Hudhud.Ask? {
        if (p is Hudhud.Ask.DidYouMean) return p.options.getOrNull(n - 1)?.takeIf(::safe)
        if (p is Hudhud.Ask.Goods && p.ids.size > 1) return p.ids.getOrNull(n - 1)?.let { p.copy(ids = listOf(it)) }
        val ids = peopleOf(p)?.takeIf { it.size > 1 } ?: return null
        return ids.getOrNull(n - 1)?.let { withPeople(p, listOf(it)) }
    }

    /**
     * A correction of the question just answered ([last]): a "no" or "wrong" in front, or "the other …", and then a name, a
     * product or "the other one". Only what is corrected changes — the facet, the span, which way — the rest stays.
     *  - "the other Ali" / "nahi, doosra": the people the name (or the last man's first name) could be, less the ones just answered;
     *  - "nahi, Ali Hassan" / "no, DAP": that man, that product, asked the same thing;
     *  - anything else ("nahi, pichle mahine"): not a turn of the talk; the whole sentence is read on its own, carried on from it.
     */
    private fun corrected(
        said: List<String>, last: Hudhud.Ask, names: List<Hudhud.Name>, topics: List<Hudhud.Topic>, language: String?,
        goods: List<Hudhud.Name>, staff: List<Hudhud.Name>, s: State
    ): Hudhud.Ask? {
        var i = 0
        while (i < said.size && said[i] in NO_FRONT) i++
        val marked = i > 0
        val rest = said.drop(i)
        if (rest.isEmpty()) return null
        val other = rest.any { it in OTHER }
        if (!marked && !other) return null
        val people = peopleOf(last)
        val things = goodsOf(last)
        if (other) {
            val nameWords = rest.filter { it !in OTHER && it !in ONE_WORDS }
            // "The other one" after a choice he showed: the second of it.
            if ((people?.size ?: 0) > 1 || (things?.size ?: 0) > 1) return nth(last, 2)
            if (people != null) {
                val words = nameWords.ifEmpty { firstNameOf(people, names)?.let { listOf(it) } ?: return askWhose(last) }
                val all = peopleIn(words.joinToString(" "), names, language)
                val left = all.filter { it !in people }
                return if (left.isEmpty()) askWhose(last) else withPeople(last, left)
            }
            if (things != null) {
                val words = nameWords.ifEmpty { firstNameOf(things, goods)?.let { listOf(it) } ?: return null }
                val left = goodsIn(words.joinToString(" "), goods, language).filter { it !in things }
                return if (left.isEmpty()) Hudhud.Ask.NeedGoods((last as? Hudhud.Ask.Goods)?.facet ?: Hudhud.GoodsFacet.OVERVIEW)
                else withGoods(last, left)
            }
            return null
        }
        // Marked: the rest read on its own, from the talk. A bare name or product puts itself in the last question's place.
        val read = Hudhud.understand(rest.joinToString(" "), names, topics, language, goods, s.about, staff)
        val inner = (read as? Hudhud.Ask.Guessed)?.ask ?: read
        if (people != null && onlyName(rest, names, language)) {
            peopleOf(inner)?.let { return withPeople(last, it) }
        }
        if (things != null && inner is Hudhud.Ask.Goods && inner.facet == Hudhud.GoodsFacet.OVERVIEW && inner.window == null) {
            return withGoods(last, inner.ids)
        }
        // Anything else after a "no" is not taken as a correction — the "no" may be the sentence's own ("nahi bika maal" is the dead
        // stock); the whole sentence is read on its own, carried on from the talk ("nahi, pichle mahine" is the same figure over it).
        return null
    }

    /**
     * A name or a product given in answer to the question he asked back: "Ali Raza" after "whose number?" is Ali Raza's number;
     * "Ali Hassan" after a choice of Alis is the question asked of him; "DAP" after "which product's price?" is its price.
     */
    private fun answered(
        heard: String, p: Hudhud.Ask, names: List<Hudhud.Name>, topics: List<Hudhud.Topic>, language: String?,
        goods: List<Hudhud.Name>, staff: List<Hudhud.Name>
    ): Hudhud.Ask? {
        val read = Hudhud.understand(heard, names, topics, language, goods, null, staff)
        if (p is Hudhud.Ask.NeedGoods) {
            return (read as? Hudhud.Ask.Goods)?.takeIf { it.facet == Hudhud.GoodsFacet.OVERVIEW && it.window == null && it.ids.isNotEmpty() }
                ?.let { Hudhud.Ask.Goods(it.ids, p.facet) }
        }
        val ids = (read as? Hudhud.Ask.Parties)?.ids?.takeIf { it.isNotEmpty() } ?: return null
        if (!onlyName(words(heard).filter { it !in FILLERS }, names, language)) return null
        return when {
            p is Hudhud.Ask.NeedName -> p.facet?.let { Hudhud.Ask.Person(ids, it) } ?: Hudhud.Ask.Parties(ids)
            (peopleOf(p)?.size ?: 0) > 1 -> withPeople(p, ids)
            else -> null
        }
    }

    /** "Whose?" for a correction that left nobody: the question's own fact, or the balance. */
    private fun askWhose(last: Hudhud.Ask): Hudhud.Ask = Hudhud.Ask.NeedName((last as? Hudhud.Ask.Person)?.facet)

    /** True when [said] is a name and the little words around a name, nothing that asks a thing of its own. */
    private fun onlyName(said: List<String>, names: List<Hudhud.Name>, language: String?): Boolean {
        val content = said.filter { it !in LINKS && it !in FILLERS && it !in ONE_WORDS }
        if (content.isEmpty()) return false
        return content.all { w -> peopleIn(w, names, language).isNotEmpty() || namePart(w, names) }
    }

    private fun namePart(w: String, names: List<Hudhud.Name>): Boolean = names.any { n -> w in words(n.name) }

    /** The people [text] (a name, in any script) could be, as the sheet would find them. */
    private fun peopleIn(text: String, names: List<Hudhud.Name>, language: String?): List<Long> {
        val local = words(text)
        val byWords = if (local.isEmpty()) emptyList() else names.filter { n -> local.all { it in words(n.name) } }.map { it.id }
        if (byWords.isNotEmpty()) return byWords
        val read = Hudhud.understand(text, names, emptyList(), language, emptyList(), null)
        return peopleOf((read as? Hudhud.Ask.Guessed)?.ask ?: read).orEmpty()
    }

    private fun goodsIn(text: String, goods: List<Hudhud.Name>, language: String?): List<Long> {
        val local = words(text)
        val byWords = if (local.isEmpty()) emptyList() else goods.filter { n -> local.all { it in words(n.name) } }.map { it.id }
        if (byWords.isNotEmpty()) return byWords
        val read = Hudhud.understand(text, emptyList(), emptyList(), language, goods, null)
        return ((read as? Hudhud.Ask.Guessed)?.ask ?: read).let { goodsOf(it) }.orEmpty()
    }

    /** The first word of the one name [ids] holds: "Ali" of Ali Raza, so "the other one" finds the other Alis. */
    private fun firstNameOf(ids: List<Long>, list: List<Hudhud.Name>): String? =
        ids.singleOrNull()?.let { id -> list.firstOrNull { it.id == id } }?.let { words(it.name).firstOrNull() }

    /** The people a question is about (Hudhud's own list of asks that name somebody). */
    internal fun peopleOf(a: Hudhud.Ask): List<Long>? = when (a) {
        is Hudhud.Ask.Parties -> a.ids
        is Hudhud.Ask.Person -> a.ids
        is Hudhud.Ask.WhenPays -> a.ids
        is Hudhud.Ask.Moved -> a.ids
        is Hudhud.Ask.Entry -> a.ids
        is Hudhud.Ask.OnDays -> a.ids
        is Hudhud.Ask.Took -> a.ids
        is Hudhud.Ask.Guessed -> peopleOf(a.ask)
        is Hudhud.Ask.Now -> peopleOf(a.ask)
        else -> null
    }?.takeIf { it.isNotEmpty() }

    internal fun goodsOf(a: Hudhud.Ask): List<Long>? = when (a) {
        is Hudhud.Ask.Goods -> a.ids
        is Hudhud.Ask.Took -> a.goods
        is Hudhud.Ask.Guessed -> goodsOf(a.ask)
        is Hudhud.Ask.Now -> goodsOf(a.ask)
        else -> null
    }?.takeIf { it.isNotEmpty() }

    /** The same question asked of [ids]: every other part of it — the fact, the span, which way — as it was. */
    internal fun withPeople(a: Hudhud.Ask, ids: List<Long>): Hudhud.Ask = when (a) {
        is Hudhud.Ask.Parties -> Hudhud.Ask.Parties(ids)
        is Hudhud.Ask.Person -> a.copy(ids = ids)
        is Hudhud.Ask.WhenPays -> a.copy(ids = ids)
        is Hudhud.Ask.Moved -> a.copy(ids = ids)
        is Hudhud.Ask.Entry -> a.copy(ids = ids)
        is Hudhud.Ask.OnDays -> a.copy(ids = ids)
        is Hudhud.Ask.Took -> a.copy(ids = ids)
        is Hudhud.Ask.Guessed -> withPeople(a.ask, ids)
        is Hudhud.Ask.Now -> Hudhud.Ask.Now(withPeople(a.ask, ids))
        else -> Hudhud.Ask.Parties(ids)
    }

    private fun withGoods(a: Hudhud.Ask, ids: List<Long>): Hudhud.Ask = when (a) {
        is Hudhud.Ask.Goods -> a.copy(ids = ids)
        is Hudhud.Ask.Took -> a.copy(goods = ids)
        is Hudhud.Ask.Guessed -> withGoods(a.ask, ids)
        is Hudhud.Ask.Now -> Hudhud.Ask.Now(withGoods(a.ask, ids))
        else -> Hudhud.Ask.Goods(ids, Hudhud.GoodsFacet.OVERVIEW)
    }

    /** "Pehla", "the second", "تیسرا" alone (with "wala", "one" around it): the place in a choice, from 1. */
    private fun ordinal(said: List<String>): Int? {
        val rest = said.filter { it !in ONE_WORDS }
        if (rest.size != 1) return null
        return ORDINALS.entries.firstOrNull { (_, ws) -> rest[0] in ws }?.key
    }

    private fun words(s: String): List<String> = VoiceQuestion.normalise(s).split(' ').filter { it.isNotEmpty() }

    private fun set(vararg w: String): Set<String> = w.map { VoiceQuestion.normalise(it) }.filter { it.isNotEmpty() }.toSet()

    // Word lists by language. Roman Urdu, English, Urdu and Hindi from the app's own usage; Sindhi, Bengali, Farsi and Arabic
    // are for a native read (D17), Indonesian from common use.

    /** Words around a dialogue turn that change nothing: "ji", "please", "bhai", his own name. */
    private val FILLERS = set(
        "ji", "jee", "please", "plz", "bhai", "yaar", "hudhud", "sir", "janab", "to", "na",
        "جی", "بھائی", "ہدہد", "یار", "जी", "भाई", "हुदहुद", "ভাই", "জি", "يا", "لطفا", "لطفاً", "tolong", "dong", "deh", "pak"
    )

    /** A whole "yes" (the sentence is nothing else). */
    private val YES = set(
        "haan", "han", "haa", "haan ji", "ji haan", "jee haan", "theek hai", "thik hai", "theek", "sahi", "sahi hai", "bilkul", "hanji",
        "yes", "yeah", "yep", "ok", "okay", "sure", "right", "correct", "yes please",
        "ہاں", "ہاں جی", "جی ہاں", "ٹھیک ہے", "ٹھیک", "درست", "بالکل", "صحیح",
        "हाँ", "हां", "हा", "हाँ जी", "जी हाँ", "ठीक है", "सही", "बिल्कुल",
        "হ্যাঁ", "হাঁ", "ঠিক আছে", "ঠিক", "জি হ্যাঁ",
        "نعم", "أجل", "ايوه", "صح", "تمام", "طيب",
        "بله", "آره", "باشه", "درسته", "آری",
        "ها", "هائو", "جي ها", "ٺيڪ", "ٺيڪ آهي",
        "ya", "iya", "betul", "benar", "oke", "baik"
    )

    /** A whole "no". */
    private val NO = set(
        "nahi", "nahin", "nai", "nahi ji", "ji nahi", "jee nahi", "galat", "ghalat", "galat hai",
        "no", "nope", "nah", "wrong", "not that", "not this", "no no",
        "نہیں", "نہیں جی", "جی نہیں", "غلط", "غلط ہے",
        "नहीं", "नही", "जी नहीं", "गलत", "गलत है",
        "না", "ভুল", "না না",
        "لا", "خطأ", "غلط", "ليس هذا",
        "نه", "نخیر", "اشتباه", "اشتباهه",
        "نہ", "نه نه", "غلط آهي",
        "tidak", "bukan", "salah", "bukan itu", "nggak", "gak"
    )

    /** A "no" or "wrong" said in front of a correction. */
    private val NO_FRONT = set(
        "nahi", "nahin", "nai", "galat", "ghalat", "no", "nope", "wrong", "not",
        "نہیں", "غلط", "नहीं", "नही", "गलत", "না", "ভুল", "لا", "خطأ", "نه", "نخیر", "اشتباه", "نہ",
        "tidak", "bukan", "salah"
    )

    /** A whole "cancel", "leave it". */
    private val CANCEL = set(
        "cancel", "cancel karo", "cancel kar do", "rehne do", "rehne dein", "rahne do", "chhoro", "chhor do", "choro", "chodo", "chod do",
        "jane do", "jaane do", "koi baat nahi", "never mind", "nevermind", "forget it", "leave it", "skip",
        "کینسل", "کینسل کرو", "رہنے دو", "رہنے دیں", "چھوڑو", "چھوڑ دو", "جانے دو", "کوئی بات نہیں",
        "कैंसल", "रहने दो", "छोड़ो", "छोड़ दो", "जाने दो", "कोई बात नहीं",
        "বাদ দাও", "থাক", "বাতিল", "বাতিল করো",
        "إلغاء", "الغ", "انس الأمر", "دعه",
        "لغو", "ولش کن", "بیخیال", "بی خیال",
        "ڇڏيو", "ڇڏ", "رهڻ ڏيو",
        "batal", "batalkan", "lupakan", "tidak jadi", "gak jadi", "nggak jadi"
    )

    /** "Other", "another": "doosra Ali", "the other Ali", "علی دیگر". */
    private val OTHER = set(
        "doosra", "dusra", "doosri", "dusri", "doosre", "dusre", "aur koi",
        "other", "another", "different",
        "دوسرا", "دوسری", "دوسرے",
        "दूसरा", "दूसरी", "दूसरे",
        "অন্য", "আরেক", "আরেকজন",
        "الآخر", "غيره", "الثاني",
        "دیگر", "دیگه", "دیگری",
        "ٻيو", "ٻي", "ٻيا",
        "lain", "lainnya", "satunya"
    )

    /** "Wala", "one", "the": words around an ordinal or "the other" that name nothing. */
    private val ONE_WORDS = set(
        "wala", "wali", "wale", "walay", "one", "the", "that", "this", "ko", "ka", "ki", "ke",
        "والا", "والی", "والے", "वाला", "वाली", "वाले", "টা", "জন", "واحد", "یکی", "وارو", "yang"
    )

    /** Little words that link a name and ask nothing ("Ali Hassan ka", "علی حسن کا"). */
    private val LINKS = set("ka", "ki", "ke", "ko", "se", "کا", "کی", "کے", "کو", "का", "की", "के", "को", "এর", "জو", "جو", "جي")

    /** "And": "aur Bashir ka?", "and Bashir's?", "اور", "और", "আর", Arabic and Farsi "و", Sindhi "۽", Indonesian "dan"/"kalau". */
    private val AND = set("aur", "and", "اور", "और", "আর", "و", "۽", "dan", "kalau", "bagaimana")

    /** Arabic's and Farsi's "and" is one letter joined to the next word ("وبشير"). */
    private val JOINED_AND = VoiceQuestion.normalise("و")

    private val ORDINALS: Map<Int, Set<String>> by lazy {
        mapOf(
            1 to set("pehla", "pahla", "pehli", "pahli", "first", "پہلا", "پہلی", "पहला", "पहली", "প্রথম", "الأول", "اول", "اولی", "پهريون", "pertama"),
            2 to set("doosra", "dusra", "doosri", "dusri", "second", "دوسرا", "دوسری", "दूसरा", "दूसरी", "দ্বিতীয়", "الثاني", "دوم", "ٻيو", "kedua"),
            3 to set("teesra", "tisra", "teesri", "tisri", "third", "تیسرا", "تیسری", "तीसरा", "तीसरी", "তৃতীয়", "الثالث", "سوم", "ٽيون", "ketiga")
        )
    }
}
