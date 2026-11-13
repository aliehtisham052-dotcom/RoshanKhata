package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ln

/**
 * Hudhud's ears (12 Nov 2026): what a question typed or spoken to him asks for, worked out on the phone from the
 * words alone, in the app's nine languages and in the scripts a recogniser writes them in.
 *
 * Like [VoiceQuestion], whose eight questions it keeps word for word, it is deliberately not a chat model: the book
 * stays on the phone, a fixed list can be tested, and every answer only READS. The screen always shows what was
 * understood beside the answer, so a misheard question shows as the wrong question, never as a wrong figure for the
 * right one; and when nothing fits it says so ([Ask.Unknown]) rather than guess.
 *
 * The order a question is read in: a "how" question asks for a help page first; then a sentence that is only small
 * talk or "what can you tell me" (13 Nov 2026: how he is, who he is, thanks, goodbye, praise); then an entry said as a fact ("Ali ko
 * 500 diye": an amount and which way it went, no question word), which only opens the form filled in; then a customer
 * or supplier the question names — when he will pay ("kab dega"), what he paid or took over a span of days ("Ali ne
 * kal kitna diya"), else his balance — unless it asks one of the figures, which wins ("aaj ki sale" is today's sale
 * even in a book with a party called Sale); one of VoiceQuestion's figures, over the span of days it names; who is
 * likely to pay now; the backup; the drawer; today's summary; a help page; small talk inside a longer sentence; a greeting. A figure's word counts only as
 * a whole word ([VoiceQuestion.classify]): "Saleem" and "Ali Wholesale" are names, not the sales.
 */
object Hudhud {

    /** What a question asks for. */
    sealed class Ask {
        /** One of VoiceQuestion's eight figures. */
        data class Figure(val kind: VoiceQuestion.Kind) : Ask()

        /** Customers or suppliers the question names, best first; several when it could be any of them. */
        data class Parties(val ids: List<Long>) : Ask()

        /** When the book was last backed up, and whether that is in order. */
        object BackupStatus : Ask()

        /** What should be in the drawer now, by the books. */
        object Drawer : Ask()

        /** A help page, by the key the screen gave it ([Topic.key]). */
        data class Help(val key: String) : Ask()

        /** "Assalam-o-Alaikum": answered with its reply. */
        object Salaam : Ask()

        /** "Hello": answered with what Hudhud can do. */
        object Hello : Ask()

        /** A sale or a profit over a span of days other than the eight figures' own: yesterday's sale, last month's profit. */
        data class Period(val measure: Measure, val window: VoiceQuestion.Window) : Ask()

        /** What the named customer paid ([got]) or took over a span of days: "Ali ne kal kitna diya". */
        data class Moved(val ids: List<Long>, val window: VoiceQuestion.Window, val got: Boolean) : Ask()

        /**
         * An entry said as a fact: the form opens filled in on the customer's page and the owner saves it; nothing is
         * written here. [ids]: the one customer, or those it could be (none: every customer is offered, nearest first,
         * by [nameWords]). [isGiven]: the shop gave, as VoiceEntry reads it.
         */
        data class Entry(val amount: Double, val isGiven: Boolean, val ids: List<Long>, val nameWords: List<String>) : Ask()

        /** When the named customer is likely to pay, by the Payment Radar; none named: who is likely to pay now. */
        data class WhenPays(val ids: List<Long>) : Ask()

        /** What needs the owner today: the morning's summary, and "good morning". */
        object Today : Ask()

        /** "What can you tell me?" (13 Nov 2026, the owner): the list of what Hudhud knows, so nobody has to guess. */
        object Abilities : Ask()

        /**
         * A little talk with him (13 Nov 2026, the owner: "haal chaal… friendly environment"): how he is, who he is,
         * thanks, goodbye, praise, and "I'm fine" back. [salaam]: it came with a salaam, which he answers first.
         */
        data class Chat(val talk: Talk, val salaam: Boolean = false) : Ask()

        /** Nothing Hudhud knows; he says so and offers what he can answer. */
        object Unknown : Ask()

        // 13 Nov 2026, the owner: "har angle se Hudhud ko pata hona chahiye… lekin accuracy, fake data nahi". Each
        // of these is read from the book by the rule of the screen it names, and only read.

        /** A named customer's or supplier's fact other than his balance: his number, last payment, limit… */
        data class Person(val ids: List<Long>, val facet: Facet) : Ask()

        /** A product the question names: its price, stock, purchase rate, suppliers, last delivery, buyers, sale. */
        data class Goods(val ids: List<Long>, val facet: GoodsFacet, val window: VoiceQuestion.Window? = null) : Ask()

        /** The shop's own profile: its name, address, phone, tax number… */
        data class Shop(val facet: ShopFacet) : Ask()

        /** About Hudhud and the app: what the app is, who made them, where the book stays, the languages, the owner. */
        data class About(val topic: AboutTopic) : Ask()

        /** Today's date, the Islamic date and the time. */
        object Clock : Ask()

        /** How many of something there are, or were made over a span of days ([window]; none: all of them). */
        data class Count(val thing: Thing, val window: VoiceQuestion.Window? = null) : Ask()

        /** Where one of the book's pages stands: cheques, plans, supplier bills, low stock, the whole book. */
        data class Status(val of: StatusOf) : Ask()

        /** Goods that came in from suppliers (their bills) over a span of days, newest first. */
        data class Deliveries(val window: VoiceQuestion.Window? = null) : Ask()

        /** What happened in the book over a span of days, and who did it: the owner's check and balance. */
        data class Activity(val window: VoiceQuestion.Window? = null) : Ask()

        /** Two or three things the words could mean: each offered as a tap, never guessed. */
        data class DidYouMean(val options: List<Ask>) : Ask()

        /** Money that came in over a span of days: customers' payments and the cashbook's income (the day book's rule). */
        data class Collected(val window: VoiceQuestion.Window? = null) : Ask()

        /** A question about somebody that names nobody ("uska number", "limit kitni hai"): he asks whose. */
        object NeedName : Ask()
    }

    /** What a [Ask.Period] adds up. */
    enum class Measure { SALES, PROFIT }

    /** What is asked about a named customer or supplier, beside his balance. */
    enum class Facet { PHONE, DETAILS, LAST_PAYMENT, LAST_ENTRY, SINCE, LIMIT, PROMISE, ENTRIES }

    /** What is asked about a product: everything at once ([OVERVIEW]) when only its name is said. */
    enum class GoodsFacet { OVERVIEW, PRICE, STOCK, COST, SUPPLIERS, LAST_IN, BUYERS, SOLD }

    /** The shop's profile, one field or [ALL]. */
    enum class ShopFacet { ALL, NAME, ADDRESS, PHONE, EMAIL, TAX, BANK, CURRENCY, COUNTRY, TRADE }

    /** About Hudhud and the app. */
    enum class AboutTopic { APP, MAKER, PRIVACY, LANGUAGES, VERSION, OWNER_NAME }

    /** What can be counted. */
    enum class Thing { CUSTOMERS, SUPPLIERS, PRODUCTS, STAFF, ENTRIES, INVOICES, BILLS, TICKETS, CALCULATIONS, APP_OPENS }

    /** A page whose standing can be told. */
    enum class StatusOf { CHEQUES, PLANS, BILLS, LOW_STOCK, BOOK, STAFF }

    /** The small talk he answers. */
    enum class Talk { HOW_ARE_YOU, WHO_ARE_YOU, THANKS, BYE, PRAISE, FINE }

    /** A customer or supplier of the book, as the question may name him. */
    data class Name(val id: Long, val name: String)

    /**
     * A help page: its [key], its [title] and the rest of its words, in every language the phone gives them.
     * [feature]: the page of one of the app's features ("Cheques", "Staff"), whose name is never taken for a
     * customer's; a question-and-answer page's title is a sentence, and its words (محفوظ, "keep") may well be one.
     */
    data class Topic(val key: String, val title: String, val text: String, val feature: Boolean = true)

    /**
     * The first of the recogniser's guesses Hudhud knows, with that guess; else the first guess, not known. [language]
     * is the recogniser's (a Persian or Indonesian figure is read its own way, as voice entry reads it).
     */
    /**
     * [goods]: the shop's products by name, so "Urea ka rate" is the product's. [before]: what the last question on
     * the sheet was taken for, so "uska number?" or "aur kal?" carry on from it (13 Nov 2026). A guess that is only a
     * choice ([Ask.DidYouMean]) gives way to a later guess Hudhud knows outright.
     */
    fun understand(
        candidates: List<String>, names: List<Name>, topics: List<Topic>, language: String? = null,
        goods: List<Name> = emptyList(), before: Ask? = null
    ): Pair<String, Ask> {
        var choice: Pair<String, Ask>? = null
        for (c in candidates) {
            val ask = understand(c, names, topics, language, goods, before)
            if (ask is Ask.DidYouMean) {
                if (choice == null) choice = c to ask
                continue
            }
            if (ask != Ask.Unknown) return c to ask
        }
        return choice ?: ((candidates.firstOrNull() ?: "") to Ask.Unknown)
    }

    fun understand(
        heard: String, names: List<Name>, topics: List<Topic>, language: String? = null,
        goods: List<Name> = emptyList(), before: Ask? = null
    ): Ask {
        val said = words(heard)
        if (said.isEmpty()) return Ask.Unknown
        val joined = said.joinToString(" ")
        val how = has(joined, HOW)
        if (how) helpOf(said, topics)?.let { return Ask.Help(it) }
        // A sentence that is nothing but small talk, or "what can you tell me" (13 Nov 2026): taken before any name,
        // so "Allah Hafiz" is a goodbye and not a customer called Hafiz; one with any other word goes on as before.
        talk(said, whole = true)?.let { return it }
        // What each word means, in any order (13 Nov 2026, Brain v2): "aap ka kya naam hai" and "naam kya hai aap ka"
        // are one question. A word the book knows as somebody's or a product's name is never read by its sound.
        val figureWords = VoiceQuestion.keywordsIn(joined)
        val mind = HudhudMind.read(said) { w ->
            w in QUIET_SET || w in DAY_WORDS || w in figureWords || knownName(w, names) || knownName(w, goods)
        }
        aboutHim(mind, joined)?.let { return it }
        val entry = VoiceEntry.parse(heard, emptyList(), language)
        val asking = heard.any { it == '?' || it == '\u061F' } || has(joined, QUESTION) || said.first() in ASKS_FIRST
        // An entry said as a fact: the form opens filled in, the owner saves it (voice entry's own reading).
        val amount = entry.amount
        val given = entry.isGiven
        if (!asking && amount != null && amount > 0.0 && given != null) {
            val nameWords = VoiceEntry.nameWords(heard)
            return Ask.Entry(amount, given, partiesOf(names, nameWords), nameWords)
        }
        val figure = VoiceQuestion.classify(heard)
        val window = VoiceQuestion.windowOf(heard)
        val named = whoIsNamed(said, names, topics, mind, window)
        val product = if (named.isEmpty()) whoIsNamed(said, goods, topics, mind, window) else emptyList()
        // "When did he last pay" is the past: only a payment still to come is the Radar's.
        val willPay = (has(joined, WILL_PAY) || ((has(joined, WHEN) || has(joined, WHO)) && has(joined, PAY))) &&
            !mind.has(HudhudMind.C.LAST) && "did" !in said
        followUp(mind, named, product, figure, window, willPay, before)?.let { return it }
        // "Uska number" with nobody before it: he asks whose, he does not guess.
        if (mind.has(HudhudMind.C.HIS) && named.isEmpty() && product.isEmpty() && !willPay && figure == null &&
            (before == null || (partyOf(before) == null && goodsOf(before) == null)) && !mind.any(HudhudMind.C.DATA, HudhudMind.C.ACCOUNT) &&
            facetOf(mind).let { it != null && (it != Facet.DETAILS || mind.any(HudhudMind.C.FATHER, HudhudMind.C.VILLAGE, HudhudMind.C.ADDRESS)) }
        ) return Ask.NeedName
        if (named.isNotEmpty()) {
            if (willPay) return Ask.WhenPays(named)
            facetOf(mind)?.let { return Ask.Person(named, it) }
            if (figure == null || figure == VoiceQuestion.Kind.TO_GET || figure == VoiceQuestion.Kind.TO_GIVE) {
                if (window != null && given != null && amount == null) return Ask.Moved(named, window, got = !given)
                return Ask.Parties(named)
            }
        }
        if (product.isNotEmpty()) goodsAsk(mind, product, figure, window)?.let { return it }
        // "Kitne cheque baqi hain": the cheques' own "baqi", not what the khata is owed.
        if (figure in OWED_FIGURES && mind.any(HudhudMind.C.CHEQUE, HudhudMind.C.PLAN, HudhudMind.C.BILL, HudhudMind.C.SALARY, HudhudMind.C.STAFF)) {
            book(mind, window)?.let { return it }
        }
        if (figure in OWED_FIGURES && mind.any(HudhudMind.C.LIMIT, HudhudMind.C.PROMISE) && named.isEmpty() && product.isEmpty()) {
            return Ask.NeedName
        }
        if (figure != null) return figureOver(figure, window)
        if (willPay && (has(joined, WHEN) || has(joined, WHO))) return Ask.WhenPays(emptyList())
        if (has(joined, BACKUP)) return Ask.BackupStatus
        if (has(joined, DRAWER)) return Ask.Drawer
        book(mind, window)?.let { return it }
        if (has(joined, TODAY_ASK)) return Ask.Today
        helpOf(said, topics)?.let { return Ask.Help(it) }
        // Small talk inside a longer sentence ("thanks a lot", "kaise ho yaar sab theek"): only when nothing else fits.
        talk(said, whole = false)?.let { return it }
        if (has(joined, SALAAM)) return Ask.Salaam
        if (has(joined, HELLO)) return Ask.Hello
        guess(mind)?.let { return it }
        return Ask.Unknown
    }

    // ------------------------------------------------------------------------------------------- Brain v2

    private fun quiet(w: String) = w in QUIET_SET || HudhudMind.meaningOf(w) == AND_ONLY

    private val AND_ONLY = setOf(HudhudMind.C.AND)

    private val OWED_FIGURES = setOf(VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.TO_GIVE, VoiceQuestion.Kind.NOT_PAID)

    /** Quiet, and the words of a span of days ("aaj", "kal", "is hafte"): for questions that take a day. */
    private fun quietOrDay(w: String) = quiet(w) || w in DAY_WORDS

    /**
     * About Hudhud himself, the app, the owner and the day \u2014 asked in any order, with any little words: his name ("aap
     * ka kya naam hai", "naam batao apna", "ye kaun hai"), how he is, what he can tell, who made him, what the app is,
     * the languages, the version, where the book stays, the owner's own name, today's date. Only when the sentence is
     * nothing else: a word that could be somebody's name sends the question on.
     */
    private fun aboutHim(m: HudhudMind.Read, joined: String): Ask? {
        val salaam = has(joined, SALAAM)
        fun only(vararg cs: HudhudMind.C) = m.onlyOf(cs.toSet(), ::quiet)
        val you = m.any(HudhudMind.C.YOU, HudhudMind.C.THIS, HudhudMind.C.OWN, HudhudMind.C.APP)
        // "Hudhud", or a hello with nothing else: he answers with what he can do.
        if (m.all == setOf(HudhudMind.C.YOU) && only(HudhudMind.C.YOU) && m.said.size <= 3) return Ask.Hello
        if ((you || m.has(HudhudMind.C.ME)) && m.any(HudhudMind.C.NAME, HudhudMind.C.WHO) && !m.any(HudhudMind.C.MADE, HudhudMind.C.MAKER) &&
            only(HudhudMind.C.YOU, HudhudMind.C.THIS, HudhudMind.C.OWN, HudhudMind.C.APP, HudhudMind.C.ME, HudhudMind.C.NAME,
                HudhudMind.C.WHO, HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.KNOW, HudhudMind.C.ABLE)
        ) return if (mineIsNearer(m)) Ask.About(AboutTopic.OWNER_NAME) else Ask.Chat(Talk.WHO_ARE_YOU, salaam)
        if (m.has(HudhudMind.C.YOU) && m.has(HudhudMind.C.WHAT) && only(HudhudMind.C.YOU, HudhudMind.C.WHAT, HudhudMind.C.TELL)) {
            return Ask.Chat(Talk.WHO_ARE_YOU, salaam)
        }
        if (m.has(HudhudMind.C.HEALTH) && only(HudhudMind.C.HEALTH, HudhudMind.C.YOU, HudhudMind.C.WHAT, HudhudMind.C.HOW_IS, HudhudMind.C.TELL)) {
            return Ask.Chat(Talk.HOW_ARE_YOU, salaam)
        }
        if (m.has(HudhudMind.C.YOU) && m.has(HudhudMind.C.HOW_IS) && only(HudhudMind.C.YOU, HudhudMind.C.HOW_IS, HudhudMind.C.WHAT)) {
            return Ask.Chat(Talk.HOW_ARE_YOU, salaam)
        }
        if (m.any(HudhudMind.C.ABLE, HudhudMind.C.KNOW) && m.has(HudhudMind.C.WHAT) &&
            only(HudhudMind.C.ABLE, HudhudMind.C.KNOW, HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.DO, HudhudMind.C.YOU, HudhudMind.C.ALL)
        ) return Ask.Abilities
        if ((m.has(HudhudMind.C.MAKER) || (m.has(HudhudMind.C.WHO) && m.has(HudhudMind.C.MADE))) &&
            only(HudhudMind.C.MAKER, HudhudMind.C.WHO, HudhudMind.C.MADE, HudhudMind.C.YOU, HudhudMind.C.APP,
                HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.NAME)
        ) return Ask.About(AboutTopic.MAKER)
        if (m.has(HudhudMind.C.APP) && m.any(HudhudMind.C.WHAT, HudhudMind.C.INFO, HudhudMind.C.TELL) &&
            only(HudhudMind.C.APP, HudhudMind.C.WHAT, HudhudMind.C.INFO, HudhudMind.C.TELL, HudhudMind.C.ALL, HudhudMind.C.THIS)
        ) return Ask.About(AboutTopic.APP)
        if (m.has(HudhudMind.C.LANGUAGE) &&
            only(HudhudMind.C.LANGUAGE, HudhudMind.C.WHICH, HudhudMind.C.WHAT, HudhudMind.C.HOW_MANY, HudhudMind.C.YOU,
                HudhudMind.C.KNOW, HudhudMind.C.ABLE, HudhudMind.C.TELL, HudhudMind.C.ALL, HudhudMind.C.DO, HudhudMind.C.APP)
        ) return Ask.About(AboutTopic.LANGUAGES)
        if (m.has(HudhudMind.C.VERSION) &&
            only(HudhudMind.C.VERSION, HudhudMind.C.APP, HudhudMind.C.WHAT, HudhudMind.C.WHICH, HudhudMind.C.YOU, HudhudMind.C.TELL)
        ) return Ask.About(AboutTopic.VERSION)
        if (m.has(HudhudMind.C.SAFE) &&
            only(HudhudMind.C.SAFE, HudhudMind.C.DATA, HudhudMind.C.YOU, HudhudMind.C.APP, HudhudMind.C.WHAT, HudhudMind.C.WHERE,
                HudhudMind.C.ME, HudhudMind.C.TELL, HudhudMind.C.ALL, HudhudMind.C.INFO, HudhudMind.C.HIS, HudhudMind.C.WHO,
                HudhudMind.C.CAME, HudhudMind.C.ACCOUNT) ||
            m.has(HudhudMind.C.DATA) && m.has(HudhudMind.C.WHERE) &&
            only(HudhudMind.C.DATA, HudhudMind.C.WHERE, HudhudMind.C.ME, HudhudMind.C.YOU, HudhudMind.C.APP, HudhudMind.C.WHAT,
                HudhudMind.C.CAME, HudhudMind.C.TELL, HudhudMind.C.INFO, HudhudMind.C.HIS)
        ) return Ask.About(AboutTopic.PRIVACY)
        if (clock(m)) return Ask.Clock
        return null
    }

    /**
     * "Your name" or "my name": the word for whose stands nearest the name ("may I know your name" is his, "do you know
     * my name" the owner's); with only "my", the owner's; a tie is his — the sheet is a talk with him.
     */
    private fun mineIsNearer(m: HudhudMind.Read): Boolean {
        val anchor = m.said.indices.firstOrNull { HudhudMind.C.NAME in m.at(it) }
            ?: m.said.indices.firstOrNull { HudhudMind.C.WHO in m.at(it) } ?: return false
        // A word that is itself "my name" or "your name" (اسمي, اسمك, namaku, namamu) decides.
        val own = m.at(anchor)
        if (HudhudMind.C.ME in own && HudhudMind.C.YOU !in own) return true
        if (HudhudMind.C.YOU in own) return false
        fun nearest(vararg cs: HudhudMind.C): Int = m.said.indices
            // "Hudhud" said to him is calling him, not "your".
            .filter { i -> i != anchor && m.said[i] !in HIS_OWN_NAME && cs.any { it in m.at(i) } }
            .minOfOrNull { kotlin.math.abs(it - anchor) } ?: Int.MAX_VALUE
        return nearest(HudhudMind.C.ME) < nearest(HudhudMind.C.YOU, HudhudMind.C.OWN, HudhudMind.C.THIS, HudhudMind.C.APP)
    }

    private val HIS_OWN_NAME: Set<String> by lazy {
        listOf("hudhud", "hoopoe", "ہدہد", "هدهد", "हुदहुद", "হুদহুদ", "الهدهد").map { norm(it) }.toSet()
    }

    /** Today's date, the day or the time: "aaj kya tareekh hai", "aaj kaunsa din hai", "kitne baje hain". */
    private fun clock(m: HudhudMind.Read): Boolean {
        val days = setOf(HudhudMind.C.DAY, HudhudMind.C.THIS)
        if (m.has(HudhudMind.C.DATE) && m.onlyOf(
                setOf(HudhudMind.C.DATE, HudhudMind.C.WHAT, HudhudMind.C.WHICH, HudhudMind.C.HOW_MANY, HudhudMind.C.TELL,
                    HudhudMind.C.TIME, HudhudMind.C.YOU, HudhudMind.C.ME) + days, ::quietOrDay
            )
        ) return true
        if (m.has(HudhudMind.C.DAY) && m.any(HudhudMind.C.WHICH, HudhudMind.C.WHAT) && m.said.any { it in TODAY_WORDS } &&
            m.onlyOf(setOf(HudhudMind.C.WHICH, HudhudMind.C.WHAT, HudhudMind.C.TELL) + days, ::quietOrDay)
        ) return true
        return m.has(HudhudMind.C.TIME) && m.any(HudhudMind.C.WHAT, HudhudMind.C.HOW_MANY) &&
            m.onlyOf(setOf(HudhudMind.C.TIME, HudhudMind.C.WHAT, HudhudMind.C.HOW_MANY, HudhudMind.C.TELL) + days, ::quietOrDay)
    }

    /**
     * What is asked about a named customer or supplier beside his balance; null when only the balance is (his name
     * alone, "ka hisaab", "kitna baqaya"). "Kab dega" (when he will pay) is the Radar's and is taken before this.
     */
    private fun facetOf(m: HudhudMind.Read): Facet? = when {
        m.has(HudhudMind.C.PHONE) -> Facet.PHONE
        m.has(HudhudMind.C.LIMIT) -> Facet.LIMIT
        m.has(HudhudMind.C.PROMISE) -> Facet.PROMISE
        m.has(HudhudMind.C.SINCE_WHEN) || (m.has(HudhudMind.C.FIRST) && m.any(HudhudMind.C.ENTRY, HudhudMind.C.WHEN, HudhudMind.C.DATE, HudhudMind.C.PAYMENT)) -> Facet.SINCE
        m.has(HudhudMind.C.PAYMENT) && m.any(HudhudMind.C.LAST, HudhudMind.C.WHEN, HudhudMind.C.DATE) -> Facet.LAST_PAYMENT
        m.has(HudhudMind.C.LAST) || (m.has(HudhudMind.C.ENTRY) && m.any(HudhudMind.C.WHEN, HudhudMind.C.DATE)) -> Facet.LAST_ENTRY
        m.any(HudhudMind.C.ENTRY, HudhudMind.C.TIMES) && m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.ALL) -> Facet.ENTRIES
        m.any(HudhudMind.C.FATHER, HudhudMind.C.VILLAGE, HudhudMind.C.ADDRESS, HudhudMind.C.INFO, HudhudMind.C.WHERE, HudhudMind.C.NAME) -> Facet.DETAILS
        m.has(HudhudMind.C.WHO) && !m.any(HudhudMind.C.PAYMENT, HudhudMind.C.ENTRY, HudhudMind.C.HOW_MANY) -> Facet.DETAILS
        else -> null
    }

    /**
     * What is asked about a named product: its suppliers' rates (cheaper, dearer, from whom), when it last came, its
     * purchase rate, who bought it, what is left, its price, how much of it sold; everything when only its name is
     * said. A figure the product does not change (this month's profit, what expires) is left to the figure.
     */
    private fun goodsAsk(m: HudhudMind.Read, ids: List<Long>, figure: VoiceQuestion.Kind?, window: VoiceQuestion.Window?): Ask? {
        val sold = figure == VoiceQuestion.Kind.SALES_TODAY || figure == VoiceQuestion.Kind.SALES_MONTH
        val buyers = (m.has(HudhudMind.C.WHO) && m.has(HudhudMind.C.BOUGHT) && !m.has(HudhudMind.C.PRICE)) || m.has(HudhudMind.C.CUSTOMER)
        val facet = when {
            sold -> GoodsFacet.SOLD
            m.any(HudhudMind.C.CHEAP, HudhudMind.C.DEAR, HudhudMind.C.SUPPLIER, HudhudMind.C.FROM_WHOM) -> GoodsFacet.SUPPLIERS
            m.has(HudhudMind.C.CAME) && m.any(HudhudMind.C.LAST, HudhudMind.C.WHEN, HudhudMind.C.DATE) -> GoodsFacet.LAST_IN
            buyers -> GoodsFacet.BUYERS
            m.has(HudhudMind.C.COST) || (m.has(HudhudMind.C.BOUGHT) && m.has(HudhudMind.C.HOW_MANY) && !m.has(HudhudMind.C.WHO)) -> GoodsFacet.COST
            m.has(HudhudMind.C.WHO) && m.has(HudhudMind.C.BOUGHT) -> GoodsFacet.BUYERS
            m.any(HudhudMind.C.STOCK, HudhudMind.C.LEFT, HudhudMind.C.PENDING) || figure == VoiceQuestion.Kind.TO_GET ||
                figure == VoiceQuestion.Kind.TO_GIVE -> GoodsFacet.STOCK
            m.has(HudhudMind.C.PRICE) -> GoodsFacet.PRICE
            figure == null -> GoodsFacet.OVERVIEW
            else -> return null
        }
        val w = if (facet != GoodsFacet.SOLD) window
        else window ?: if (figure == VoiceQuestion.Kind.SALES_TODAY) VoiceQuestion.Window.TODAY else VoiceQuestion.Window.THIS_MONTH
        return Ask.Goods(ids, facet, w)
    }

    /**
     * The rest of the book, when nobody and no product is named: the shop's profile, goods that came in, how many of
     * something, where cheques, plans, bills and stock stand, what happened today and who did it, the whole book.
     */
    private fun book(m: HudhudMind.Read, window: VoiceQuestion.Window?): Ask? {
        val mine = m.any(HudhudMind.C.SHOP, HudhudMind.C.ME, HudhudMind.C.OWN)
        val his = m.has(HudhudMind.C.HIS)
        // Money that came in: "aaj kitne paise aaye", "aaj kitni wasooli hui", "money in today".
        val page = m.any(HudhudMind.C.CHEQUE, HudhudMind.C.PLAN, HudhudMind.C.BILL, HudhudMind.C.STAFF, HudhudMind.C.SALARY, HudhudMind.C.INVOICE)
        if (!page && (m.has(HudhudMind.C.CAME) && m.has(HudhudMind.C.MONEY) ||
                m.has(HudhudMind.C.PAYMENT) && (m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.ALL) || window != null) && !m.has(HudhudMind.C.LAST))
        ) return Ask.Collected(window)
        // A customer's or a supplier's number, address, limit or promise with no name: whose? ("kisi gahak ka number").
        if (m.any(HudhudMind.C.PHONE, HudhudMind.C.ADDRESS) && m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER) &&
            !m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.ALL)
        ) return Ask.NeedName
        when {
            m.has(HudhudMind.C.OWNER) && m.has(HudhudMind.C.WHO) && m.has(HudhudMind.C.SHOP) -> return Ask.About(AboutTopic.OWNER_NAME)
            m.has(HudhudMind.C.EMAIL) && !his -> return Ask.Shop(ShopFacet.EMAIL)
            m.has(HudhudMind.C.TAX) && !m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.INVOICE) -> return Ask.Shop(ShopFacet.TAX)
            m.has(HudhudMind.C.BANK) && !his -> return Ask.Shop(ShopFacet.BANK)
            m.has(HudhudMind.C.CURRENCY) -> return Ask.Shop(ShopFacet.CURRENCY)
            m.has(HudhudMind.C.COUNTRY) && !his -> return Ask.Shop(ShopFacet.COUNTRY)
            m.has(HudhudMind.C.SHOP) && m.has(HudhudMind.C.TRADE) -> return Ask.Shop(ShopFacet.TRADE)
            mine && m.has(HudhudMind.C.NAME) -> return Ask.Shop(ShopFacet.NAME)
            m.has(HudhudMind.C.PHONE) && mine && !his -> return Ask.Shop(ShopFacet.PHONE)
            // "Number" alone: the shop's, or somebody's whose name was not said.
            m.has(HudhudMind.C.PHONE) && !his && !m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER, HudhudMind.C.STAFF) ->
                return Ask.DidYouMean(listOf(Ask.Shop(ShopFacet.PHONE), Ask.NeedName))
            m.has(HudhudMind.C.ADDRESS) && !his && !m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER) -> return Ask.Shop(ShopFacet.ADDRESS)
            m.has(HudhudMind.C.SHOP) && m.any(HudhudMind.C.INFO, HudhudMind.C.ALL, HudhudMind.C.TELL) &&
                !m.any(HudhudMind.C.HAPPENED, HudhudMind.C.REPORT) -> return Ask.Shop(ShopFacet.ALL)
            m.has(HudhudMind.C.SHOP) && m.onlyOf(setOf(HudhudMind.C.SHOP, HudhudMind.C.WHAT, HudhudMind.C.ME), ::quiet) ->
                return Ask.Shop(ShopFacet.ALL)
        }
        // The staff's salary still to pay this month: "salary kitni deni hai", "kis ki tankhwah baqi hai".
        if (m.has(HudhudMind.C.SALARY) && (m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.PENDING, HudhudMind.C.WHO, HudhudMind.C.ALL) || window != null)) {
            return Ask.Status(StatusOf.STAFF)
        }
        val many = m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.ALL) || window != null
        // Goods that came in: "aaj kis supplier se maal aaya", "aakhri delivery kab aayi".
        if (m.has(HudhudMind.C.CAME) && !m.has(HudhudMind.C.PAYMENT) && !(m.has(HudhudMind.C.BILL) && m.has(HudhudMind.C.HOW_MANY)) &&
            (m.any(HudhudMind.C.GOODS, HudhudMind.C.SUPPLIER, HudhudMind.C.BILL, HudhudMind.C.LAST) || m.has(HudhudMind.C.WHEN)) ||
            m.has(HudhudMind.C.SUPPLIER) && m.any(HudhudMind.C.FROM_WHOM, HudhudMind.C.WHICH, HudhudMind.C.WHO) && m.any(HudhudMind.C.GOODS, HudhudMind.C.CAME)
        ) return Ask.Deliveries(window)
        when {
            m.has(HudhudMind.C.CALCULATOR) && (m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.TIMES, HudhudMind.C.HAPPENED, HudhudMind.C.USED) || window != null) ->
                return Ask.Count(Thing.CALCULATIONS, window)
            (m.has(HudhudMind.C.APP) && m.any(HudhudMind.C.OPEN, HudhudMind.C.TIMES, HudhudMind.C.HOW_MANY)) ||
                (m.has(HudhudMind.C.OPEN) && m.has(HudhudMind.C.TIMES)) -> return Ask.Count(Thing.APP_OPENS, window)
            m.has(HudhudMind.C.INVOICE) && m.has(HudhudMind.C.SUPPLIER) && m.has(HudhudMind.C.PENDING) -> return Ask.Status(StatusOf.BILLS)
            m.has(HudhudMind.C.INVOICE) && m.has(HudhudMind.C.SUPPLIER) && many -> return Ask.Count(Thing.BILLS, window)
            m.has(HudhudMind.C.INVOICE) && (many || m.has(HudhudMind.C.MADE)) -> return Ask.Count(Thing.INVOICES, window)
            m.has(HudhudMind.C.BILL) && m.has(HudhudMind.C.PENDING) -> return Ask.Status(StatusOf.BILLS)
            m.has(HudhudMind.C.BILL) && many -> return Ask.Count(Thing.BILLS, window)
            m.has(HudhudMind.C.TICKET) && (many || m.has(HudhudMind.C.MADE)) -> return Ask.Count(Thing.TICKETS, window)
            m.has(HudhudMind.C.ENTRY) && (many || m.has(HudhudMind.C.MADE)) -> return Ask.Count(Thing.ENTRIES, window)
            m.has(HudhudMind.C.CUSTOMER) && m.has(HudhudMind.C.HOW_MANY) -> return Ask.Count(Thing.CUSTOMERS)
            m.has(HudhudMind.C.SUPPLIER) && m.has(HudhudMind.C.HOW_MANY) -> return Ask.Count(Thing.SUPPLIERS)
            m.has(HudhudMind.C.STAFF) && m.has(HudhudMind.C.HOW_MANY) -> return Ask.Count(Thing.STAFF)
            m.any(HudhudMind.C.GOODS, HudhudMind.C.STOCK) && m.has(HudhudMind.C.LOW) -> return Ask.Status(StatusOf.LOW_STOCK)
            m.has(HudhudMind.C.GOODS) && m.has(HudhudMind.C.HOW_MANY) -> return Ask.Count(Thing.PRODUCTS)
        }
        val asks = m.any(HudhudMind.C.PENDING, HudhudMind.C.HOW_MANY, HudhudMind.C.WHEN, HudhudMind.C.ALL, HudhudMind.C.WHICH, HudhudMind.C.WHO) ||
            window != null
        when {
            m.has(HudhudMind.C.CHEQUE) && asks -> return Ask.Status(StatusOf.CHEQUES)
            m.has(HudhudMind.C.PLAN) && asks -> return Ask.Status(StatusOf.PLANS)
            m.has(HudhudMind.C.STOCK) && m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.WHAT, HudhudMind.C.ALL, HudhudMind.C.WHICH) ->
                return Ask.Status(StatusOf.LOW_STOCK)
        }
        // What happened and who did it: "aaj kya kya hua", "aaj ki report", "kis ne kya delete kiya".
        if (m.has(HudhudMind.C.REPORT) && !m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.GOODS) ||
            m.has(HudhudMind.C.HAPPENED) && m.any(HudhudMind.C.WHAT, HudhudMind.C.ALL) && m.onlyOf(ACTIVITY_OK, ::quietOrDay) ||
            m.any(HudhudMind.C.EDITED, HudhudMind.C.DELETED) && m.any(HudhudMind.C.WHO, HudhudMind.C.WHAT, HudhudMind.C.HOW_MANY, HudhudMind.C.ALL)
        ) return Ask.Activity(window)
        // The whole book: "jitna data hai uski information", "khate ki maloomat", "mera hisaab".
        if (m.any(HudhudMind.C.DATA, HudhudMind.C.INFO, HudhudMind.C.ACCOUNT) && m.onlyOf(BOOK_OK, ::quietOrDay)) return Ask.Status(StatusOf.BOOK)
        // "Aakhri entry" with nobody named: the book's last entry (its summary says when), or somebody's.
        if (m.has(HudhudMind.C.ENTRY) && m.has(HudhudMind.C.LAST) && !m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.PAYMENT)) {
            return Ask.DidYouMean(listOf(Ask.Status(StatusOf.BOOK), Ask.NeedName))
        }
        // Somebody's limit, promise, father or village, with no name: he asks whose.
        if (m.any(HudhudMind.C.LIMIT, HudhudMind.C.PROMISE, HudhudMind.C.FATHER, HudhudMind.C.VILLAGE) ||
            m.has(HudhudMind.C.PAYMENT) && m.has(HudhudMind.C.LAST)
        ) return Ask.NeedName
        return null
    }

    /** A word that alone could be his name, the owner's or the shop's: the three are offered, none guessed. */
    private fun guess(m: HudhudMind.Read): Ask? {
        if (m.has(HudhudMind.C.NAME) && m.onlyOf(setOf(HudhudMind.C.NAME, HudhudMind.C.WHAT, HudhudMind.C.TELL), ::quiet)) {
            return Ask.DidYouMean(listOf(Ask.Chat(Talk.WHO_ARE_YOU), Ask.About(AboutTopic.OWNER_NAME), Ask.Shop(ShopFacet.NAME)))
        }
        return null
    }

    /**
     * A question that carries on from the last answer on the sheet (13 Nov 2026): "uska number?" (the one it was
     * about), "aur Bashir ka?" (someone else, the same question), "aur kal?" (the same figure, another span of days),
     * "aur DAP?" (another product). Only when the sentence asks nothing of its own.
     */
    private fun followUp(
        m: HudhudMind.Read, named: List<Long>, product: List<Long>, figure: VoiceQuestion.Kind?,
        window: VoiceQuestion.Window?, willPay: Boolean, before: Ask?
    ): Ask? {
        if (before == null) return null
        val plain = m.all.all { it in FOLLOW_OK }
        if (m.has(HudhudMind.C.HIS) && named.isEmpty() && product.isEmpty()) {
            partyOf(before)?.let { ids ->
                if (willPay) return Ask.WhenPays(ids)
                facetOf(m)?.let { return Ask.Person(ids, it) }
                if (window != null && m.has(HudhudMind.C.PAYMENT)) {
                    val got = (before as? Ask.Moved)?.got ?: true
                    return Ask.Moved(ids, window, got)
                }
                if (figure == null || figure == VoiceQuestion.Kind.TO_GET || figure == VoiceQuestion.Kind.TO_GIVE) return Ask.Parties(ids)
            }
            goodsOf(before)?.let { ids -> goodsAsk(m, ids, figure, window)?.let { return it } }
        }
        // A bare question about the one the last answer was about, with no "uska" (13 Nov 2026: the chips' own words,
        // typed or said — "number?", "aakhri adaigi", "waade ka din", "tafseel", "kab dega?", "rate?", "stock?").
        // Nobody else is named and the sentence asks nothing of its own; a figure word stays the shop's figure, except
        // the "udhaar" of "udhaar ki hadd" (a limit) and of a promise.
        if (named.isEmpty() && product.isEmpty()) {
            partyOf(before)?.takeIf { it.size == 1 }?.let { ids ->
                if (willPay && !m.has(HudhudMind.C.WHO)) return Ask.WhenPays(ids)
                val facet = facetOf(m) ?: bareFacet(m)
                val ownWords = figure == null || (figure in OWED_FIGURES && facet in OWED_FACETS)
                if (facet != null && ownWords && m.onlyOf(PERSON_OK, ::quietOrDay)) return Ask.Person(ids, facet)
                if (figure == null && m.has(HudhudMind.C.ACCOUNT) && m.onlyOf(BALANCE_OK, ::quiet)) return Ask.Parties(ids)
            }
            goodsOf(before)?.takeIf { it.size == 1 }?.let { ids ->
                if (figure == null && GOODS_FACETS.any { m.has(it) } && m.onlyOf(GOODS_OK, ::quietOrDay)) {
                    goodsAsk(m, ids, null, window)?.let { return it }
                }
            }
        }
        if (named.isNotEmpty() && figure == null && !willPay && plain) {
            when (before) {
                is Ask.Person -> if (window == null) return Ask.Person(named, before.facet)
                is Ask.WhenPays -> if (window == null && before.ids.isNotEmpty()) return Ask.WhenPays(named)
                is Ask.Moved -> return Ask.Moved(named, window ?: before.window, before.got)
                else -> {}
            }
        }
        if (product.isNotEmpty() && named.isEmpty() && plain && before is Ask.Goods) {
            return Ask.Goods(product, before.facet, window ?: before.window)
        }
        if (named.isEmpty() && product.isEmpty() && figure == null && window != null && plain) {
            return when (before) {
                is Ask.Figure -> when (before.kind) {
                    VoiceQuestion.Kind.SALES_TODAY, VoiceQuestion.Kind.SALES_MONTH -> figureOver(VoiceQuestion.Kind.SALES_MONTH, window)
                    VoiceQuestion.Kind.PROFIT_MONTH -> figureOver(VoiceQuestion.Kind.PROFIT_MONTH, window)
                    else -> null
                }
                is Ask.Period -> figureOver(
                    if (before.measure == Measure.SALES) VoiceQuestion.Kind.SALES_MONTH else VoiceQuestion.Kind.PROFIT_MONTH, window
                )
                is Ask.Moved -> Ask.Moved(before.ids, window, before.got)
                is Ask.Count -> Ask.Count(before.thing, window)
                is Ask.Deliveries -> Ask.Deliveries(window)
                is Ask.Activity -> Ask.Activity(window)
                is Ask.Goods -> Ask.Goods(before.ids, before.facet, window)
                else -> null
            }
        }
        return null
    }

    /** A facet word said alone after a customer: his entries, his last payment. */
    private fun bareFacet(m: HudhudMind.Read): Facet? = when {
        m.has(HudhudMind.C.ENTRY) -> Facet.ENTRIES
        m.has(HudhudMind.C.PAYMENT) -> Facet.LAST_PAYMENT
        else -> null
    }

    /** Facets a figure's word may stand in ("udhaar ki hadd": a limit, not what the khata is owed). */
    private val OWED_FACETS = setOf(Facet.LIMIT, Facet.PROMISE)

    /** What a bare question about a customer may say: his facts' words and the little words around them. */
    private val PERSON_OK = setOf(
        HudhudMind.C.PHONE, HudhudMind.C.LIMIT, HudhudMind.C.PROMISE, HudhudMind.C.SINCE_WHEN, HudhudMind.C.FIRST,
        HudhudMind.C.ENTRY, HudhudMind.C.WHEN, HudhudMind.C.DATE, HudhudMind.C.DAY, HudhudMind.C.PAYMENT, HudhudMind.C.LAST,
        HudhudMind.C.TIMES, HudhudMind.C.HOW_MANY, HudhudMind.C.ALL, HudhudMind.C.FATHER, HudhudMind.C.VILLAGE,
        HudhudMind.C.ADDRESS, HudhudMind.C.INFO, HudhudMind.C.WHERE, HudhudMind.C.NAME, HudhudMind.C.WHO, HudhudMind.C.CREDIT,
        HudhudMind.C.PENDING, HudhudMind.C.KNOW, HudhudMind.C.HAPPENED, HudhudMind.C.AND, HudhudMind.C.WHAT, HudhudMind.C.TELL,
        HudhudMind.C.HIS, HudhudMind.C.THIS
    )

    /** "Balance?", "hisaab?" after a customer: his balance. */
    private val BALANCE_OK = setOf(
        HudhudMind.C.ACCOUNT, HudhudMind.C.HOW_MANY, HudhudMind.C.PENDING, HudhudMind.C.MONEY, HudhudMind.C.ALL,
        HudhudMind.C.CREDIT, HudhudMind.C.AND, HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.HIS, HudhudMind.C.THIS
    )

    /** The words that ask something of a product. */
    private val GOODS_FACETS = listOf(
        HudhudMind.C.PRICE, HudhudMind.C.STOCK, HudhudMind.C.LEFT, HudhudMind.C.COST, HudhudMind.C.CHEAP, HudhudMind.C.DEAR,
        HudhudMind.C.SUPPLIER, HudhudMind.C.FROM_WHOM, HudhudMind.C.CAME, HudhudMind.C.BOUGHT, HudhudMind.C.CUSTOMER,
        HudhudMind.C.INFO
    )

    /** What a bare question about a product may say beside them. */
    private val GOODS_OK = GOODS_FACETS.toSet() + setOf(
        HudhudMind.C.LAST, HudhudMind.C.WHEN, HudhudMind.C.DATE, HudhudMind.C.DAY, HudhudMind.C.WHO, HudhudMind.C.HOW_MANY,
        HudhudMind.C.ALL, HudhudMind.C.PENDING, HudhudMind.C.CREDIT, HudhudMind.C.GOODS, HudhudMind.C.WHICH, HudhudMind.C.TIMES,
        HudhudMind.C.HAPPENED, HudhudMind.C.AND, HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.HIS, HudhudMind.C.THIS
    )

    /** The customers or suppliers an answer was about, for "uska\u2026". */
    private fun partyOf(a: Ask): List<Long>? = when (a) {
        is Ask.Parties -> a.ids
        is Ask.Person -> a.ids
        is Ask.WhenPays -> a.ids
        is Ask.Moved -> a.ids
        is Ask.Entry -> a.ids
        else -> null
    }?.takeIf { it.isNotEmpty() }

    /** The product an answer was about, for "iska rate\u2026". */
    private fun goodsOf(a: Ask): List<Long>? = (a as? Ask.Goods)?.ids?.takeIf { it.isNotEmpty() }

    /** One of the book's own names (a customer, a supplier, a product), recognised outright. */
    private fun knownName(w: String, names: List<Name>): Boolean =
        names.isNotEmpty() && NameSearch.scoreSpoken(names, listOf(w)) { it.name }.any { it.strongHits > 0 }

    /**
     * Whoever (or whichever product) the words name. A concept word that is also a common name part ("Malik", "Store",
     * "Din") is looked at only when the plain words name nobody; then a single such word gives way to a question about
     * the shop ("dukaan ka malik kaun hai" is not a customer called Malik), while two of them together still name
     * someone ("Malik Store ka number").
     */
    private fun whoIsNamed(
        said: List<String>, names: List<Name>, topics: List<Topic>, m: HudhudMind.Read, window: VoiceQuestion.Window?
    ): List<Long> {
        if (names.isEmpty()) return emptyList()
        namedIn(said, names, topics, soft = false).takeIf { it.isNotEmpty() }?.let { return it }
        val soft = said.count { HudhudMind.isSoftWord(it) }
        if (soft == 0) return emptyList()
        val loose = namedIn(said, names, topics, soft = true)
        if (loose.isNotEmpty() && soft < 2 && book(m, window) != null) return emptyList()
        return loose
    }

    /**
     * Some likely questions for words he could not place (13 Nov 2026): offered as taps under "You can ask", nearest
     * first, at most three; none when the words point nowhere.
     */
    fun closest(heard: String): List<Ask> {
        val said = words(heard)
        if (said.isEmpty()) return emptyList()
        val m = HudhudMind.read(said)
        val out = LinkedHashSet<Ask>()
        fun on(vararg cs: HudhudMind.C) = m.any(*cs)
        if (on(HudhudMind.C.NAME, HudhudMind.C.YOU)) out += Ask.Chat(Talk.WHO_ARE_YOU)
        if (on(HudhudMind.C.PHONE, HudhudMind.C.ADDRESS, HudhudMind.C.SHOP, HudhudMind.C.EMAIL, HudhudMind.C.BANK)) out += Ask.Shop(ShopFacet.ALL)
        if (on(HudhudMind.C.CUSTOMER)) out += Ask.Count(Thing.CUSTOMERS)
        if (on(HudhudMind.C.SUPPLIER, HudhudMind.C.CAME, HudhudMind.C.BILL)) out += Ask.Deliveries(null)
        if (on(HudhudMind.C.GOODS, HudhudMind.C.STOCK, HudhudMind.C.PRICE, HudhudMind.C.LOW)) out += Ask.Status(StatusOf.LOW_STOCK)
        if (on(HudhudMind.C.CHEQUE)) out += Ask.Status(StatusOf.CHEQUES)
        if (on(HudhudMind.C.PLAN)) out += Ask.Status(StatusOf.PLANS)
        if (on(HudhudMind.C.INVOICE)) out += Ask.Count(Thing.INVOICES, VoiceQuestion.Window.TODAY)
        if (on(HudhudMind.C.DATE, HudhudMind.C.DAY, HudhudMind.C.TIME)) out += Ask.Clock
        if (on(HudhudMind.C.CALCULATOR)) out += Ask.Count(Thing.CALCULATIONS, VoiceQuestion.Window.TODAY)
        if (on(HudhudMind.C.APP, HudhudMind.C.OPEN)) out += Ask.Count(Thing.APP_OPENS, VoiceQuestion.Window.TODAY)
        if (on(HudhudMind.C.REPORT, HudhudMind.C.HAPPENED, HudhudMind.C.EDITED, HudhudMind.C.DELETED)) out += Ask.Activity(VoiceQuestion.Window.TODAY)
        if (on(HudhudMind.C.MONEY, HudhudMind.C.PAYMENT)) out += Ask.Collected(VoiceQuestion.Window.TODAY)
        if (on(HudhudMind.C.SALARY, HudhudMind.C.STAFF)) out += Ask.Status(StatusOf.STAFF)
        if (on(HudhudMind.C.DATA, HudhudMind.C.INFO, HudhudMind.C.ALL, HudhudMind.C.ACCOUNT)) out += Ask.Status(StatusOf.BOOK)
        return out.take(3)
    }

    /**
     * One of the eight figures, over the span of days the question names: the sales and the profit take any span (the
     * eight's own day and month stay [Ask.Figure], so their chips and headings are unchanged); the rest have none.
     */
    private fun figureOver(kind: VoiceQuestion.Kind, window: VoiceQuestion.Window?): Ask = when (kind) {
        VoiceQuestion.Kind.SALES_TODAY, VoiceQuestion.Kind.SALES_MONTH -> {
            val own = if (kind == VoiceQuestion.Kind.SALES_TODAY) VoiceQuestion.Window.TODAY else VoiceQuestion.Window.THIS_MONTH
            when (window ?: own) {
                VoiceQuestion.Window.TODAY -> Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
                VoiceQuestion.Window.THIS_MONTH -> Ask.Figure(VoiceQuestion.Kind.SALES_MONTH)
                else -> Ask.Period(Measure.SALES, window ?: own)
            }
        }
        VoiceQuestion.Kind.PROFIT_MONTH ->
            if (window == null || window == VoiceQuestion.Window.THIS_MONTH) Ask.Figure(kind) else Ask.Period(Measure.PROFIT, window)
        else -> Ask.Figure(kind)
    }

    /**
     * The first and last moment of a span of days, as [from, to): today and yesterday whole, this week from the shop's
     * own first day (PartyWhen.THIS_WEEK, the sales report's week) to tonight, last week the seven days before it, this
     * month and last month by the calendar — the same spans the screens they open read.
     */
    fun span(window: VoiceQuestion.Window, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        fun startOf(c: Calendar): Long = c.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun at(t: Long) = Calendar.getInstance(tz).apply { timeInMillis = t }
        fun plusDays(t: Long, days: Int) = startOf(at(t).apply { add(Calendar.DAY_OF_MONTH, days) })
        fun plusMonths(t: Long, months: Int) = startOf(at(t).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, months) })
        val today = startOf(at(now))
        val tomorrow = plusDays(today, 1)
        val week = PartyWhen.THIS_WEEK.window(now, tz)?.first ?: today
        val month = plusMonths(today, 0)
        return when (window) {
            VoiceQuestion.Window.TODAY -> today to tomorrow
            VoiceQuestion.Window.YESTERDAY -> plusDays(today, -1) to today
            VoiceQuestion.Window.THIS_WEEK -> week to tomorrow
            VoiceQuestion.Window.LAST_WEEK -> plusDays(week, -7) to week
            VoiceQuestion.Window.THIS_MONTH -> month to plusMonths(today, 1)
            VoiceQuestion.Window.LAST_MONTH -> plusMonths(today, -1) to month
        }
    }

    /** The customer an entry names: one when NameSearch is sure, else those it could be (at most five), else none. */
    private fun partiesOf(names: List<Name>, nameWords: List<String>): List<Long> {
        if (names.isEmpty() || nameWords.isEmpty()) return emptyList()
        NameSearch.confidentMatch(names, nameWords) { it.name }?.let { return listOf(it.id) }
        return nearBest(names, nameWords).ifEmpty { acrossScripts(names, nameWords) }
    }

    /** Every name the said words recognise (not merely come near) and that scores close to the best, at most five. */
    private fun nearBest(names: List<Name>, words: List<String>): List<Long> {
        val scored = NameSearch.scoreSpoken(names, words) { it.name }.filter { it.strongHits > 0 }
        val best = scored.firstOrNull()?.score ?: return emptyList()
        if (best <= 0.0) return emptyList()
        return scored.takeWhile { it.score >= best * NEAR_BEST }.take(MAX_NAMES).map { it.item.id }
    }

    /**
     * A name saved in one script and said in the other (13 Nov 2026): Urdu, Sindhi, Arabic and Persian leave the short
     * vowels unwritten, so a recogniser's "بشیر" folds to "bsir" while the book's "Bashir" folds to "basir", and the
     * name search finds only a near miss — a question in Urdu about a customer saved in Roman letters found nobody.
     * Here a word meets a word of a name when one is in those scripts, the other in Latin letters, and the Latin
     * spelling is the other with only vowels put in ("b[a]sir"); "Bushra" ("busra") is not "بشیر" ("bsir"). Every name
     * met so is offered, at most five — a choice, as [nearBest] gives one; none when more would be.
     */
    private fun acrossScripts(names: List<Name>, words: List<String>): List<Long> {
        val said = words.map { it to arabicScript(it) }.map { (w, a) -> NameSearch.fold(w) to a }.filter { it.first.length >= 3 }
        if (said.none { it.second } && names.none { n -> n.name.any { c -> c in '\u0600'..'\u06FF' } }) return emptyList()
        val met = names.filter { n ->
            val parts = n.name.split(' ', '-', '.').filter { it.isNotBlank() }.map { NameSearch.fold(it) to arabicScript(it) }
                .filter { it.first.isNotEmpty() }
            said.any { (w, wArabic) ->
                parts.any { (p, pArabic) ->
                    when {
                        wArabic && !pArabic -> vowelsPutIn(w, p)
                        !wArabic && pArabic -> vowelsPutIn(p, w)
                        else -> false
                    }
                }
            }
        }
        return if (met.size in 1..MAX_NAMES) met.map { it.id } else emptyList()
    }

    private fun arabicScript(w: String) = w.any { it in '\u0600'..'\u06FF' }

    /** [latin] is [bare] with only vowels added to it: "basir" is "bsir" with an "a"; "busra" is not. */
    private fun vowelsPutIn(bare: String, latin: String): Boolean {
        if (latin.length < bare.length || bare.length < 3) return false
        var i = 0
        for (c in latin) {
            when {
                i < bare.length && c == bare[i] -> i++
                c in "aeiou" -> {}
                else -> return false
            }
        }
        return i == bare.length
    }

    // ------------------------------------------------------------------------------------------------ the greeting

    /** How Hudhud greets the owner on Home: salaam, or the time of day's own greeting. */
    enum class Greeting { SALAAM, MORNING, AFTERNOON, EVENING }

    /**
     * The owner's rule (12 Nov 2026): "Assalam-o-Alaikum" in Pakistan and the Muslim countries — the member states of
     * the Organisation of Islamic Cooperation among the app's countries — at any hour; elsewhere the time of day's own
     * greeting, so "good morning" is never said in the evening (morning from 4 to noon, afternoon to 5).
     */
    fun greeting(country: ShopCountry, hour: Int): Greeting = when {
        country in SALAAM_COUNTRIES -> Greeting.SALAAM
        hour in 4..11 -> Greeting.MORNING
        hour in 12..16 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }

    /** The app's countries that are members of the OIC (Wikipedia, "Member states of the OIC", checked 12 Nov 2026). */
    private val SALAAM_COUNTRIES = setOf(
        ShopCountry.PAKISTAN, ShopCountry.BANGLADESH, ShopCountry.SAUDI_ARABIA, ShopCountry.UAE, ShopCountry.QATAR,
        ShopCountry.OMAN, ShopCountry.KUWAIT, ShopCountry.BAHRAIN, ShopCountry.EGYPT, ShopCountry.IRAN,
        ShopCountry.AFGHANISTAN, ShopCountry.INDONESIA, ShopCountry.MALAYSIA, ShopCountry.TURKEY, ShopCountry.NIGERIA,
        ShopCountry.IRAQ, ShopCountry.JORDAN, ShopCountry.LEBANON, ShopCountry.SYRIA, ShopCountry.PALESTINE,
        ShopCountry.YEMEN, ShopCountry.MOROCCO, ShopCountry.ALGERIA, ShopCountry.TUNISIA, ShopCountry.LIBYA,
        ShopCountry.SUDAN, ShopCountry.MAURITANIA, ShopCountry.TAJIKISTAN
    )

    // ------------------------------------------------------------------------------------------------ the words

    /**
     * Hudhud's spelling of a text is [VoiceQuestion.normalise]'s: one spelling for the figures' words, the names and
     * the help pages, so a word is never one thing to one of them and another to the next.
     */
    internal fun norm(s: String): String = VoiceQuestion.normalise(s)

    private fun words(s: String): List<String> = norm(s).split(' ').filter { it.isNotEmpty() }

    /**
     * True when one of [list] is said, by the figures' own rule ([VoiceQuestion.saysAny]): a single word as a whole
     * word or with its language's joined pieces (so سلام is not found inside اسلام, but ড্রয়ার is in ড্রয়ারে), a phrase
     * as a run of whole words.
     */
    private fun has(joined: String, list: List<String>): Boolean = VoiceQuestion.saysAny(joined, list)

    /**
     * "How": a question that asks how to do something asks for a help page first. English "how" only as "how to",
     * "how do"…: "how much" and "how many" ask for a figure.
     */
    private val HOW = listOf(
        "kaise", "kese", "kaisay", "kesay", "kaisy", "kesy", "tareeqa", "tarika", "tariqa",
        "how to", "how do", "how does", "how can", "how should", "how i",
        "کیسے", "کیسی", "طریقہ", "ڪيئن", "كيف", "چگونه", "چطور", "कैसे", "तरीका",
        "কিভাবে", "কীভাবে", "bagaimana", "gimana", "cara"
    )

    /** The backup, asked about by name. */
    private val BACKUP = listOf(
        "backup", "back up", "bakup", "bekap", "بیک اپ", "بیک‌اپ", "بيڪ اپ", "بیک‌آپ", "پشتیبان", "پشتیبان‌گیری",
        "نسخ احتياطي", "النسخ الاحتياطي", "نسخة احتياطية", "बैकअप", "ব্যাকআপ", "cadangan"
    )

    /**
     * The drawer — the shop's galla — and the cash in hand. Bare English "cash" is left out: it is half of Cash flow.
     * Sindhi's گلي, Farsi's صندوق and Bengali's ক্যাশ are the app's own drawer chip in those languages (12 Nov 2026).
     */
    private val DRAWER = listOf(
        "galla", "gala", "galle", "gallay", "galley", "galy", "drawer", "cash in hand", "naqdi", "naqd",
        "گلہ", "گلا", "گلے", "نقدی", "نقد", "الدرج", "صندوق النقد", "دخل", "گلي", "صندوق",
        "गल्ला", "गल्ले", "नकद", "नकदी", "हाथ में नकद", "ক্যাশ বাক্স", "ক্যাশ", "ড্রয়ার", "নগদ", "laci", "uang tunai"
    )

    /**
     * Words that make a sentence a question: an amount said with one of them ("Ali ko 500 kab diye?") is asked about,
     * never an entry to write. Persian's کی is left out (Urdu's "of"), and so are kya, kia and کیا, which Urdu also
     * writes for "did" ("wasool kia", "وصول کیا"): they ask only as a sentence's first word ([ASKS_FIRST]).
     */
    private val QUESTION = listOf(
        "kitna", "kitni", "kitne", "kab", "kaun", "kon", "kis", "how much", "how many", "when", "who",
        "what", "did", "does", "کتنا", "کتنی", "کتنے", "کب", "کون", "کس", "कितना", "कितनी", "कितने", "कब",
        "क्या", "कौन", "किस", "কত", "কবে", "কি", "কী", "কে", "কার", "ڪيترو", "ڪيترا", "ڪيتري", "ڪڏهن", "ڇا", "ڪير",
        "كم", "متى", "هل", "ماذا", "چقدر", "چند", "آیا", "چه", "berapa", "kapan", "apa", "siapa", "apakah"
    )

    /** The question words that ask only at the start ("kya Ali ne diye?"): later in a sentence they are "did". */
    private val ASKS_FIRST: Set<String> by lazy { listOf("kya", "kia", "kyaa", "کیا").map { norm(it) }.toSet() }

    /** "Will pay": a payment still to come, in the words a shop asks it ("Ali kab dega", "who will pay today"). */
    private val WILL_PAY = listOf(
        "dega", "degi", "denge", "dein ge", "dain ge", "de ga", "de gi", "ada karega", "ada karegi", "ada karenge",
        "lautayega", "chukayega",
        "دے گا", "دیگا", "دے گی", "دیگی", "دیں گے", "دینگے", "ادا کرے گا", "ادا کریگا", "ادا کرے گی", "ادا کریں گے",
        "لوٹائے گا", "واپس کرے گا", "देगा", "देगी", "देंगे", "चुकाएगा", "चुकाएगी", "लौटाएगा", "अदा करेगा",
        "দেবে", "দিবে", "দেবেন", "দিবেন", "শোধ করবে", "পরিশোধ করবে", "ڏيندو", "ڏيندي", "ڏيندا", "ادا ڪندو", "ادا ڪندي",
        "يدفع", "سيدفع", "يسدد", "سيسدد", "می‌دهد", "میدهد", "میده", "می‌پردازد", "میپردازد", "پرداخت می‌کند"
    )

    /**
     * Paying, in words with no tense of their own ("pay", Indonesian "bayar"): a payment to come only beside "when" or
     * "who" ("when will Ali pay", "kapan Ali bayar"); alone they may as well be past ("did Ali pay").
     */
    private val PAY = listOf("pay", "pays", "paying", "repay", "bayar", "membayar", "melunasi")

    /** "When", for "kab dega"; Persian's کی is left out (Urdu's "of"), its چه وقت kept. */
    private val WHEN = listOf("kab", "kb", "when", "کب", "कब", "কবে", "ڪڏهن", "متى", "چه وقت", "چه زمانی", "کی می", "kapan")

    /** "Who", for "aaj kaun dega". */
    private val WHO = listOf("kaun", "kon", "who", "کون", "कौन", "কে", "কারা", "ڪير", "من", "چه کسی", "siapa")

    /**
     * Today's summary, and "good morning": the morning greeting answered with what needs the owner today. The app's own
     * chip says it in each language ("Aaj ka khulasa", "Ringkasan hari ini").
     */
    private val TODAY_ASK = listOf(
        "khulasa", "khulaasa", "kholasa", "aaj kya hai", "aaj ka kaam", "aaj kya karna", "subah bakhair", "subha bakhair",
        "summary", "briefing", "agenda", "what's on today", "whats on today", "today's work", "good morning",
        "خلاصہ", "آج کیا ہے", "آج کا کام", "صبح بخیر", "सारांश", "खुलासा", "आज क्या है", "आज का काम", "सुप्रभात",
        "गुड मॉर्निंग", "সারসংক্ষেপ", "আজ কী আছে", "আজকের কাজ", "সুপ্রভাত", "শুভ সকাল", "خلاصو", "اڄ ڇا آهي", "اڄ جو ڪم",
        "صبح بخير", "ملخص", "ماذا لدي اليوم", "صباح الخير", "خلاصه", "کارهای امروز", "امروز چه خبر",
        "ringkasan", "rangkuman", "apa hari ini", "agenda hari ini", "selamat pagi"
    )

    /** "Assalam-o-Alaikum", in the ways it is written and heard. */
    private val SALAAM = listOf(
        "assalam", "asalam", "assalamu", "assalamualaikum", "asalamualaikum", "salam", "salaam", "aoa", "slam",
        "السلام", "سلام", "अस्सलाम", "सलाम", "আসসালামু", "আসসালামুয়ালাইকুম", "সালাম"
    )

    /** "Hello", "hi". */
    private val HELLO = listOf("hello", "helo", "hi", "hey", "ہیلو", "हेलो", "हैलो", "হ্যালো", "halo", "مرحبا")

    // ------------------------------------------------------------------------------------------------ small talk

    /**
     * Small talk, or "what can you tell me" (13 Nov 2026). [whole]: only when every word of the sentence is the
     * talk's own, a question's little word, a greeting or a friendly filler ("yaar", "bhai") — then it is answered
     * before any name is looked for. Else (the loose reading, last of all) when the talk is said anywhere; "I'm fine"
     * and a lone "help" are taken only whole, they mean too much inside a longer sentence.
     */
    private fun talk(said: List<String>, whole: Boolean): Ask? {
        val joined = said.joinToString(" ")
        val salaam = has(joined, SALAAM)
        val found: Pair<Ask, List<String>> = when {
            has(joined, ABILITIES) -> Ask.Abilities to ABILITIES
            has(joined, HOW_ARE_YOU) -> Ask.Chat(Talk.HOW_ARE_YOU, salaam) to HOW_ARE_YOU
            has(joined, WHO_ARE_YOU) -> Ask.Chat(Talk.WHO_ARE_YOU, salaam) to WHO_ARE_YOU
            has(joined, THANKS) -> Ask.Chat(Talk.THANKS, salaam) to THANKS
            has(joined, BYE) -> Ask.Chat(Talk.BYE, salaam) to BYE
            has(joined, PRAISE) -> Ask.Chat(Talk.PRAISE, salaam) to PRAISE
            whole && has(joined, FINE) -> Ask.Chat(Talk.FINE, salaam) to FINE
            whole && has(joined, HELP_ALONE) -> Ask.Abilities to HELP_ALONE
            else -> return null
        }
        if (!whole) return found.first
        val own = found.second.flatMap { words(it) }.toSet()
        return if (said.all { it in own || it in STOP_SET || it in FILLER_SET || it in GREETING_SET }) found.first else null
    }

    /**
     * "What can you tell me / what can you do", in the nine languages. Each language's chip says one of these
     * (hudhud_chip_abilities; HudhudWordsTest holds every chip to it).
     */
    private val ABILITIES = listOf(
        "kya bata sakte", "kya bta sakte", "kya bata sakty", "kia bata sakte", "kya kar sakte", "kya kr sakte",
        "kya kar sakty", "kia kar sakte", "kya kya bata", "kya kya kar", "kya kya pooch", "kya kya puch",
        "kya pooch sakta", "kya puch sakta", "kya pooch sakti", "tum kya karte", "aap kya karte", "tumhara kaam",
        "aap ka kaam", "kya madad kar sakte",
        "what can you tell", "what can you do", "what do you do", "what can i ask", "what can you help",
        "what else can you", "what are you able", "what do you know", "your features",
        "کیا بتا سکتے", "کیا کر سکتے", "کیا کیا بتا", "کیا کیا کر", "کیا پوچھ سکتا", "کیا پوچھ سکتی", "کیا کیا پوچھ",
        "آپ کا کام", "تمہارا کام", "کیا مدد کر سکتے",
        "क्या बता सकते", "क्या कर सकते", "क्या क्या बता", "क्या क्या कर", "क्या पूछ सकता", "क्या पूछ सकती",
        "आपका काम", "तुम्हारा काम", "क्या मदद कर सकते",
        "কী বলতে পারেন", "কি বলতে পারেন", "কী বলতে পারো", "কি বলতে পারো", "কী করতে পারেন", "কি করতে পারেন",
        "কী করতে পারো", "কি করতে পারো", "কী জিজ্ঞাসা করতে পারি", "কি জিজ্ঞাসা করতে পারি",
        "ماذا يمكنك", "ماذا تستطيع", "ماذا تعرف", "بماذا تساعد", "بماذا يمكنك", "ماذا يمكنك أن تخبرني",
        "چه کارهایی", "چه کارهایی می‌توانی بکنی", "چه کاری می‌توانی", "چه کار می‌توانی", "چه چیزهایی",
        "چی می‌تونی", "چه می‌توانی", "چه کمکی",
        "ڇا ٻڌائي سگهو", "ڇا ڪري سگهو", "ڇا ٻڌائي سگهين", "ڇا ڪري سگهين", "توهان ڇا ٻڌائي سگهو ٿا",
        "bisa apa saja", "kamu bisa apa", "anda bisa apa", "apa yang bisa kamu", "apa yang bisa anda",
        "apa yang bisa anda lakukan", "apa saja yang bisa"
    )

    /** "How are you", "kya haal hai", "sunao". */
    private val HOW_ARE_YOU = listOf(
        "kaise ho", "kese ho", "kaisay ho", "kaise hain", "kese hain", "kaisay hain", "kaise hai", "kya haal", "kia haal",
        "kya hal", "kia hal", "haal chaal", "hal chal", "haal hai", "sunao", "sunaao", "sunaen", "kya chal raha",
        "how are you", "how r u", "how are u", "how's it going", "hows it going", "how is it going", "how are things",
        "how do you do", "how you doing",
        "کیسے ہو", "کیسے ہیں", "کیا حال", "حال چال", "حال ہے", "سناؤ", "سنائیں", "کیا چل رہا",
        "कैसे हो", "कैसे हैं", "क्या हाल", "हाल चाल", "हालचाल", "सुनाओ",
        "কেমন আছো", "কেমন আছেন", "কেমন আছিস", "কী খবর", "কি খবর",
        "كيف حالك", "كيف الحال", "كيفك", "شلونك", "كيف أنت",
        "حالت چطوره", "حالت چطور است", "حال شما", "حالتون چطوره", "چطوری", "خوبی",
        "ڪيئن آهيو", "ڪيئن آهين", "ڇا حال", "حال احوال",
        "apa kabar", "gimana kabar", "bagaimana kabar", "kabar kamu", "kabar anda"
    )

    /** "Who are you", "what is your name". */
    private val WHO_ARE_YOU = listOf(
        "kaun ho", "kon ho", "kaun hain", "kon hain", "tum kaun", "aap kaun", "ap kon", "tumhara naam", "aap ka naam",
        "apka naam", "tumhara nam", "apna naam",
        "who are you", "what is your name", "whats your name", "what's your name", "your name",
        "کون ہو", "کون ہیں", "آپ کون", "تم کون", "آپ کا نام", "تمہارا نام", "اپنا نام",
        "कौन हो", "कौन हैं", "आप कौन", "तुम कौन", "आपका नाम", "तुम्हारा नाम",
        "তুমি কে", "আপনি কে", "তোমার নাম", "আপনার নাম",
        "من أنت", "ما اسمك", "اسمك",
        "تو کی هستی", "شما کی هستید", "اسمت چیه", "اسم شما", "اسمت",
        "توهان ڪير آهيو", "تون ڪير آهين", "توهان جو نالو", "تنهنجو نالو",
        "siapa kamu", "kamu siapa", "siapa anda", "anda siapa", "siapa namamu", "nama kamu", "nama anda"
    )

    /** Thanks. */
    private val THANKS = listOf(
        "shukriya", "shukria", "shukarya", "shukriyah", "thanks", "thank you", "thank u", "thanku", "thx",
        "jazakallah", "jazak allah", "meharbani", "mehrbani",
        "شکریہ", "جزاک اللہ", "مہربانی",
        "शुक्रिया", "धन्यवाद", "थैंक यू", "मेहरबानी",
        "ধন্যবাদ", "শুকরিয়া", "থ্যাংক ইউ",
        "شكرا", "جزاك الله", "مشكور",
        "مرسی", "ممنون", "ممنونم", "سپاس", "متشکرم", "تشکر",
        "مهرباني", "شڪريو", "ٿورا",
        "terima kasih", "terimakasih", "makasih", "trims"
    )

    /** Goodbye; a lone "Hafiz" is a name, so only the whole "Allah Hafiz". */
    private val BYE = listOf(
        "allah hafiz", "allah hafez", "khuda hafiz", "khudahafiz", "allahhafiz", "fi amanillah", "bye", "bye bye",
        "goodbye", "good bye", "good night", "see you", "phir milenge", "phir milte",
        "اللہ حافظ", "خدا حافظ", "فی امان اللہ", "پھر ملیں گے", "پھر ملتے ہیں", "شب بخیر",
        "अलविदा", "फिर मिलेंगे", "फिर मिलते हैं", "बाय", "शुभ रात्रि", "अल्लाह हाफ़िज़", "खुदा हाफ़िज़",
        "আল্লাহ হাফেজ", "খোদা হাফেজ", "বিদায়", "আবার দেখা হবে", "শুভ রাত্রি",
        "مع السلامة", "في أمان الله", "وداعا", "تصبح على خير", "إلى اللقاء",
        "خداحافظ", "خدانگهدار", "بدرود",
        "الله حافظ", "موڪلاڻي",
        "sampai jumpa", "selamat tinggal", "dadah", "selamat malam", "sampai nanti"
    )

    /**
     * Praise. Words that are also people's names (Kamal, Mumtaz, Azeem, Bagus) are only in a phrase here: said alone
     * they ask for that customer.
     */
    private val PRAISE = listOf(
        "shabash", "shaabash", "shabaash", "zabardast", "zabrdast", "bohat khoob", "bahut khoob", "bohat acha",
        "bohat achha", "kamal kar diya", "kamaal kar diya", "kamal hai", "kamaal hai", "mashallah", "masha allah",
        "mashaallah", "wah",
        "well done", "good job", "great job", "great", "awesome", "excellent", "nice", "very good", "good work", "amazing",
        "شاباش", "زبردست", "بہت خوب", "بہت اچھا", "کمال کر دیا", "کمال ہے", "ماشاءاللہ", "ماشااللہ", "ماشا اللہ", "واہ",
        "शाबाश", "बहुत बढ़िया", "बढ़िया", "जबरदस्त", "कमाल कर दिया", "कमाल है", "बहुत खूब", "वाह",
        "সাবাশ", "দারুণ", "খুব ভালো", "চমৎকার", "অসাধারণ",
        "أحسنت", "رائع", "ما شاء الله", "ماشاء الله",
        "آفرین", "عالی", "دمت گرم", "ماشالله", "ماشاالله", "خیلی خوب",
        "شاباس", "واه", "ڪمال ڪري ڇڏيو",
        "hebat", "mantap", "keren", "luar biasa", "bagus sekali", "kerja bagus"
    )

    /** "I'm fine" — his own question back ("How's the shop today?") answered. Whole sentences only. */
    private val FINE = listOf(
        "theek", "thik", "theek hoon", "thik hoon", "theek hun", "main theek", "sab theek", "alhamdulillah",
        "alhamdolillah", "al hamdulillah", "fine", "i'm fine", "im fine", "i am fine", "good", "i'm good", "im good",
        "all good", "doing well",
        "ٹھیک", "ٹھیک ہوں", "میں ٹھیک", "سب ٹھیک", "الحمدللہ", "الحمد للہ",
        "ठीक", "ठीक हूँ", "ठीक हूं", "मैं ठीक", "सब ठीक",
        "ভালো আছি", "ভাল আছি", "ভালো", "আলহামদুলিল্লাহ",
        "بخير", "أنا بخير", "الحمد لله",
        "خوبم", "خوب هستم", "من خوبم",
        "ٺيڪ", "ٺيڪ آهيان", "الحمدلله", "خير آهي",
        "baik", "baik baik saja", "kabar baik", "alhamdulillah baik", "sehat"
    )

    /** A lone "help" / "madad": what he can do. Whole sentences only ("cheque help" is the Cheques page). */
    private val HELP_ALONE = listOf(
        "help", "help me", "help chahiye", "madad", "madad karo", "madad karein", "madad chahiye",
        "مدد", "مدد کریں", "مدد چاہیے", "मदद", "मदद करो", "सहायता", "সাহায্য", "সাহায্য করুন",
        "مساعدة", "ساعدني", "کمک", "کمک کن", "مدد ڪريو", "bantuan", "tolong", "bantu saya"
    )

    /** Friendly words around small talk ("yaar", "dost", "jaan"): they do not make it a question about anyone. */
    private val FILLER = listOf(
        "yaar", "yar", "dost", "jaan", "jani", "pyare", "pyaare", "bro", "buddy", "friend", "dear", "achha", "acha",
        "accha", "ok", "okay", "oye", "oy", "janab", "sahab", "sahib", "o",
        "یار", "دوست", "جان", "جانی", "پیارے", "اچھا", "جناب", "صاحب",
        "यार", "दोस्त", "अच्छा", "जनाब", "साहब",
        "বন্ধু", "ভাই", "আচ্ছা",
        "يا", "صديقي", "عزيزي",
        "عزیزم",
        "teman", "kak", "mas", "pak", "bu", "dong", "ya"
    )

    private val FILLER_SET: Set<String> by lazy { FILLER.flatMap { words(it) }.toSet() }

    /** The rest of a salaam, never a name: "alaikum" after "assalam o". */
    private val SALAAM_TAIL = listOf(
        "alaikum", "alaykum", "alaikom", "alekum", "aleikum", "walaikum", "wa", "warahmatullah", "wabarakatuh",
        "علیکم", "عليكم", "عليڪم", "ورحمۃ", "ورحمة", "وبرکاتہ", "وبركاته",
        "अलैकुम", "আলাইকুম"
    )

    /** A salaam or a hello said with the talk ("Assalam o alaikum, kaise hain?"). */
    private val GREETING_SET: Set<String> by lazy { (SALAAM + SALAAM_TAIL + HELLO).flatMap { words(it) }.toSet() }

    /**
     * Words a question is made of that name nobody: question words, the little words between them and the words
     * for a balance, in the nine languages. A name is looked for only among what is left, so "Kabir" is not found
     * in "kab", nor anyone in "kitna".
     */
    private val STOP = listOf(
        // Roman Urdu and Hindi in Latin letters
        "kitna", "kitni", "kitne", "kya", "kia", "kyaa", "kaun", "kon", "kis", "kisi", "kisne", "kab", "hai", "hain",
        "he", "ha", "ka", "ki", "ke", "ko", "se", "ne", "ho", "hua", "hoa", "huwa", "mera", "meri", "mere", "mujhe",
        "muje", "mjhe", "main", "mein", "me", "mai", "batao", "batain", "bataen", "bataiye", "btao", "dikhao",
        "dikhaen", "bhai", "jee", "ji", "sir", "please", "plz", "aap", "ap", "tum", "yeh", "ye", "wo", "woh", "is",
        "us", "abhi", "sab", "sabse", "zara", "hisaab", "hisab", "khata", "khaata", "account", "balance", "total",
        "kul", "raqam", "paise", "paisa", "rupay", "rupees", "rs", "hudhud", "hoopoe", "wala", "wali",
        // English
        "how", "much", "many", "what", "who", "whose", "when", "where", "which", "are", "was", "the", "a", "an",
        "my", "i", "of", "for", "from", "to", "does", "did", "do", "owe", "owes", "show", "tell", "can", "you", "your",
        "all", "and", "or", "today", "now", "will", "would", "this", "that", "last", "not", "nahi", "nahin", "tidak", "belum",
        "نہیں", "नहीं",
        // Urdu
        "کتنا", "کتنی", "کتنے", "کیا", "کون", "کس", "کسی", "کب", "ہے", "ہیں", "کا", "کی", "کے", "کو", "سے", "نے",
        "ہو", "ہوا", "میرا", "میری", "میرے", "مجھے", "میں", "بتاؤ", "بتائیں", "بتائیے", "دکھاؤ", "دکھائیں", "حساب",
        "کھاتہ", "کھاتے", "کھاتا", "بیلنس", "کل", "رقم", "پیسے", "روپے", "آپ", "یہ", "وہ", "اس", "اب", "سب", "ہدہد",
        "والا", "والی",
        // Hindi
        "कितना", "कितनी", "कितने", "क्या", "कौन", "किस", "किसी", "कब", "है", "हैं", "का", "की", "के", "को", "से",
        "ने", "हो", "हुआ", "मेरा", "मेरी", "मेरे", "मुझे", "में", "बताओ", "बताइए", "दिखाओ", "हिसाब", "खाता", "बैलेंस",
        "कुल", "रकम", "पैसे", "रुपये", "आप", "यह", "वह", "इस", "अब", "सब", "हुदहुद", "वाला", "वाली",
        // Bengali
        "কত", "কি", "কী", "কে", "কার", "কাকে", "কবে", "আছে", "এর", "আমার", "আমাকে", "বলো", "বলুন", "দেখাও",
        "হিসাব", "খাতা", "ব্যালেন্স", "মোট", "টাকা", "আপনি", "এই", "সব", "হুদহুদ",
        // Indonesian
        "berapa", "apa", "siapa", "kapan", "ada", "saya", "aku", "punya", "tunjukkan", "saldo", "uang", "rupiah",
        "yang", "di", "dari", "ini", "itu", "semua",
        // Arabic
        "كم", "ما", "من", "متى", "هل", "رصيد", "حساب", "عند", "لي", "المبلغ", "اعرض", "الهدهد", "هدهد",
        // Farsi
        "چقدر", "چند", "چه", "کی", "کدام", "است", "مانده", "مرا", "بگو", "همه", "این", "آن", "پول", "تومان",
        // Sindhi
        "ڪيترو", "ڪيترا", "ڪيتري", "ڇا", "ڪير", "ڪڏهن", "آهي", "آهن", "جو", "جي", "کي", "کان", "منهنجو", "کاتو", "سڀ"
    )

    private val STOP_SET: Set<String> by lazy { STOP.map { norm(it) }.toSet() }

    /** The single words of the intents above, and the words for money moving: never taken for a name either. */
    private val INTENT_SET: Set<String> by lazy {
        listOf(HOW, BACKUP, DRAWER, SALAAM, SALAAM_TAIL, HELLO, WILL_PAY, PAY, WHEN, WHO, TODAY_ASK, VoiceEntry.directionWords())
            .flatten().flatMap { words(it) }.toSet()
    }

    // ------------------------------------------------------------------------------------------------ the names

    /**
     * The customers or suppliers the question names: one when [NameSearch] is confident, else every one the said
     * words recognise (not merely come near) and that scores close to the best, at most five — a choice for the
     * owner, never a guess. Only words left once the question's own words are set aside are looked at, and only
     * words of three letters or more.
     */
    private fun namedIn(said: List<String>, names: List<Name>, topics: List<Topic>, soft: Boolean = true): List<Long> {
        if (names.isEmpty()) return emptyList()
        val features = topics.filter { it.feature }.flatMap { words(it.title) }.toSet()
        val asked = VoiceQuestion.keywordsIn(said.joinToString(" "))
        // Brain v2 (13 Nov 2026): a word Hudhud reads as a concept ("number", "rate", "aakhri") is not a name either;
        // a common name part that is also a concept ("Malik", "Store") only when [soft].
        val left = said.filter { w ->
            w.length >= 3 && w.any { it.isLetter() } && w !in STOP_SET && w !in INTENT_SET &&
                w !in asked && w !in features && !HudhudMind.isHardWord(w) && (soft || !HudhudMind.isSoftWord(w))
        }.map { bengaliStem(it) }
        if (left.isEmpty()) return emptyList()
        NameSearch.confidentMatch(names, left) { it.name }?.let { return listOf(it.id) }
        return nearBest(names, left).ifEmpty { acrossScripts(names, left) }
    }

    /**
     * A Bengali name with its case ending ("আলীর", Ali's): the ending comes off, so the name meets the book's "Ali"
     * outright rather than only coming near it (13 Nov 2026, every language: the owner).
     */
    private fun bengaliStem(w: String): String {
        if (w.isEmpty() || w[0] !in 'ঀ'..'৿') return w
        for (end in BENGALI_NAME_ENDS) {
            if (w.length > end.length + 1 && w.endsWith(end)) return w.dropLast(end.length)
        }
        return w
    }

    private val BENGALI_NAME_ENDS by lazy { listOf("এর", "ের", "কে", "র").map { norm(it) } }

    private const val NEAR_BEST = 0.6
    private const val MAX_NAMES = 5

    // ------------------------------------------------------------------------------------------------ Brain v2 words

    /** Words that ask nothing by themselves: the little words between, a greeting, a friendly "yaar", "what's" tail. */
    private val QUIET_SET: Set<String> by lazy {
        (STOP + FILLER + SALAAM + SALAAM_TAIL + HELLO + QUIET_MORE).flatMap { words(it) }.toSet()
    }

    private val QUIET_MORE = listOf(
        "hoon", "hun", "hu", "tha", "thi", "thay", "hy", "h", "good", "shubh", "full", "zara", "please", "pls", "jitna",
        "jitni", "jitne", "jo", "jis", "s", "t", "d", "ll", "re", "ve", "m", "am", "be", "it", "batana", "btana",
        "bataen", "batayen", "bataiye", "bataye", "batado", "dein", "den", "de", "na", "to", "hi", "ab", "please",
        "ہوں", "تھا", "تھی", "جو", "جتنا", "ذرا", "हूँ", "हूं", "था", "थी", "जो", "जितना", "जरा",
        "আছে", "আছেন", "হয়", "ছিল", "هو", "هي", "هل", "است", "هست", "هستی", "هستید", "بود", "را",
        "آهيو", "آهين", "adalah", "ialah", "sih", "kah", "may", "about", "baare", "bare", "mutalliq", "بارے", "बारे",
        "التي", "الذي", "ٿا", "jata", "jati", "jate", "jaata", "goes", "جاتا", "जाता", "do", "khate", "khatay", "khaate",
        "खाते", "खाता", "apakah", "ہا", "ہای", "ای", "yang",
        // "in" in four tongues ("in stock", "اسٽاڪ ۾", "في المخزون"), Sindhi's "of", and Urdu's "to" (then) — Persian's
        // informal "you", which a question to Hudhud does not need ("چه کسی تو را ساخت").
        "in", "۾", "في", "جا", "تو"
    )

    /** The words of a span of days, which a question about the day or its activity may carry. */
    private val DAY_WORDS: Set<String> by lazy {
        listOf(
            "aaj", "today", "kal", "yesterday", "hafta", "hafte", "week", "mahina", "mahine", "month", "is", "iss", "this",
            "pichle", "pichla", "pichhle", "last", "previous", "guzishta", "آج", "کل", "ہفتہ", "ہفتے", "مہینہ", "مہینے", "ماہ",
            "پچھلے", "گزشتہ", "اس", "आज", "कल", "हफ्ता", "हफ्ते", "सप्ताह", "महीना", "महीने", "माह", "पिछले", "इस", "আজ",
            "আজকের", "গতকাল", "সপ্তাহ", "মাস", "গত", "এই", "اڄ", "ڪالهه", "هفتو", "هفتي", "مهينو", "مهيني", "اليوم", "أمس",
            "امس", "أسبوع", "اسبوع", "شهر", "الماضي", "هذا", "امروز", "دیروز", "هفته", "ماه", "گذشته", "این", "hari", "ini",
            "kemarin", "minggu", "pekan", "bulan", "lalu"
        ).map { norm(it) }.toSet()
    }

    /** "Today", for "aaj kaunsa din hai". */
    private val TODAY_WORDS: Set<String> by lazy {
        listOf("aaj", "today", "آج", "आज", "আজ", "আজকে", "اڄ", "اليوم", "امروز", "ini").map { norm(it) }.toSet()
    }

    /** What a question about the day's activity may also say ("aaj dukaan mein kya kya hua"). */
    private val ACTIVITY_OK = setOf(
        HudhudMind.C.HAPPENED, HudhudMind.C.WHAT, HudhudMind.C.ALL, HudhudMind.C.SHOP, HudhudMind.C.TELL, HudhudMind.C.ME,
        HudhudMind.C.WHO, HudhudMind.C.DO, HudhudMind.C.REPORT, HudhudMind.C.APP, HudhudMind.C.THIS, HudhudMind.C.DAY,
        HudhudMind.C.ENTRY, HudhudMind.C.AND
    )

    /** What a question about the whole book may also say ("jitna data hai uski information"). */
    private val BOOK_OK = setOf(
        HudhudMind.C.DATA, HudhudMind.C.INFO, HudhudMind.C.ALL, HudhudMind.C.HOW_MANY, HudhudMind.C.WHAT, HudhudMind.C.TELL,
        HudhudMind.C.ME, HudhudMind.C.SHOP, HudhudMind.C.APP, HudhudMind.C.KNOW, HudhudMind.C.HIS, HudhudMind.C.THIS,
        HudhudMind.C.YOU, HudhudMind.C.ACCOUNT
    )

    /** What a follow-up may say beside the name, product or span it changes ("aur", "uska", "kya"). */
    private val FOLLOW_OK = setOf(HudhudMind.C.AND, HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.HIS, HudhudMind.C.THIS)

    // ------------------------------------------------------------------------------------------------ help pages

    /**
     * The help page the question is about, or null. Each said word is worth what it narrows across the pages
     * (as NameSearch weighs a name's words): a word on one page counts for a lot, a word on every page for almost
     * nothing; a word in a page's title counts twice. A word of four letters or more meets its plural or stem
     * ("cheque", "cheques"). A page that scores less than one word found on two pages is not chosen.
     */
    private fun helpOf(said: List<String>, topics: List<Topic>): String? {
        if (topics.isEmpty()) return null
        val q = said.filter { it.length >= 3 && it !in STOP_SET && norm(it) !in HOW_SET }.distinct()
        if (q.isEmpty()) return null
        val docs = topics.map { t -> words(t.title).toSet() to words(t.text).toSet() }
        fun hit(w: String, set: Set<String>) = set.any { d ->
            d == w || (minOf(d.length, w.length) >= 4 && (d.startsWith(w) || w.startsWith(d)))
        }
        val n = topics.size.toDouble()
        val worth = q.associateWith { w ->
            val found = docs.count { (title, text) -> hit(w, title) || hit(w, text) }
            if (found == 0) 0.0 else ln(1.0 + n / found)
        }
        val scores = docs.map { (title, text) ->
            q.sumOf { w ->
                when {
                    hit(w, title) -> 2.0 * worth.getValue(w)
                    hit(w, text) -> worth.getValue(w)
                    else -> 0.0
                }
            }
        }
        val best = scores.indices.maxByOrNull { scores[it] } ?: return null
        // The least a page must score: one word found on at most two pages, or a word in its title.
        return if (scores[best] >= ln(1.0 + n / 2.0)) topics[best].key else null
    }

    private val HOW_SET: Set<String> by lazy { HOW.flatMap { words(it) }.toSet() }
}
