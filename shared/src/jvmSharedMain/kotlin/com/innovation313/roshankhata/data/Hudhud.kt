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
    }

    /** What a [Ask.Period] adds up. */
    enum class Measure { SALES, PROFIT }

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
    fun understand(candidates: List<String>, names: List<Name>, topics: List<Topic>, language: String? = null): Pair<String, Ask> {
        for (c in candidates) {
            val ask = understand(c, names, topics, language)
            if (ask != Ask.Unknown) return c to ask
        }
        return (candidates.firstOrNull() ?: "") to Ask.Unknown
    }

    fun understand(heard: String, names: List<Name>, topics: List<Topic>, language: String? = null): Ask {
        val said = words(heard)
        if (said.isEmpty()) return Ask.Unknown
        val joined = said.joinToString(" ")
        val how = has(joined, HOW)
        if (how) helpOf(said, topics)?.let { return Ask.Help(it) }
        // A sentence that is nothing but small talk, or "what can you tell me" (13 Nov 2026): taken before any name,
        // so "Allah Hafiz" is a goodbye and not a customer called Hafiz; one with any other word goes on as before.
        talk(said, whole = true)?.let { return it }
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
        val named = namedIn(said, names, topics)
        val willPay = has(joined, WILL_PAY) || ((has(joined, WHEN) || has(joined, WHO)) && has(joined, PAY))
        if (named.isNotEmpty()) {
            if (willPay) return Ask.WhenPays(named)
            if (figure == null || figure == VoiceQuestion.Kind.TO_GET || figure == VoiceQuestion.Kind.TO_GIVE) {
                if (window != null && given != null && amount == null) return Ask.Moved(named, window, got = !given)
                return Ask.Parties(named)
            }
        }
        if (figure != null) return figureOver(figure, window)
        if (willPay && (has(joined, WHEN) || has(joined, WHO))) return Ask.WhenPays(emptyList())
        if (has(joined, BACKUP)) return Ask.BackupStatus
        if (has(joined, DRAWER)) return Ask.Drawer
        if (has(joined, TODAY_ASK)) return Ask.Today
        helpOf(said, topics)?.let { return Ask.Help(it) }
        // Small talk inside a longer sentence ("thanks a lot", "kaise ho yaar sab theek"): only when nothing else fits.
        talk(said, whole = false)?.let { return it }
        if (has(joined, SALAAM)) return Ask.Salaam
        if (has(joined, HELLO)) return Ask.Hello
        return Ask.Unknown
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
        return nearBest(names, nameWords)
    }

    /** Every name the said words recognise (not merely come near) and that scores close to the best, at most five. */
    private fun nearBest(names: List<Name>, words: List<String>): List<Long> {
        val scored = NameSearch.scoreSpoken(names, words) { it.name }.filter { it.strongHits > 0 }
        val best = scored.firstOrNull()?.score ?: return emptyList()
        if (best <= 0.0) return emptyList()
        return scored.takeWhile { it.score >= best * NEAR_BEST }.take(MAX_NAMES).map { it.item.id }
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
    private val WHEN = listOf("kab", "kb", "when", "کب", "कब", "কবে", "ڪڏهن", "متى", "چه وقت", "چه زمانی", "kapan")

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
    private fun namedIn(said: List<String>, names: List<Name>, topics: List<Topic>): List<Long> {
        if (names.isEmpty()) return emptyList()
        val features = topics.filter { it.feature }.flatMap { words(it.title) }.toSet()
        val asked = VoiceQuestion.keywordsIn(said.joinToString(" "))
        val left = said.filter { w ->
            w.length >= 3 && w.any { it.isLetter() } && w !in STOP_SET && w !in INTENT_SET &&
                w !in asked && w !in features
        }
        if (left.isEmpty()) return emptyList()
        NameSearch.confidentMatch(names, left) { it.name }?.let { return listOf(it.id) }
        return nearBest(names, left)
    }

    private const val NEAR_BEST = 0.6
    private const val MAX_NAMES = 5

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
