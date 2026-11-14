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

        /**
         * A question about somebody that names nobody ("uska number", "limit kitni hai"): he tells what he can without a
         * name — the latest customers with that fact beside each ([facet]; none: their balances) — instead of asking.
         */
        data class NeedName(val facet: Facet? = null) : Ask()

        /** What the cashbook took in and paid out over a span of days (13 Nov 2026, the owner: every page's day). */
        data class Cashbook(val window: VoiceQuestion.Window? = null) : Ask()

        /** Goods that came back at the counter over a span of days. */
        data class Returns(val window: VoiceQuestion.Window? = null) : Ask()

        /** One of his little acts on the screen (13 Nov 2026, the owner: "urr ke dikhao"): he flies, dances or hops. */
        data class Act(val move: Move) : Ask()

        /**
         * A change to how he speaks, asked of him (14 Nov 2026, the owner's list: "phir se batao", "ahista bolo", "chup ho
         * jao", "awaaz band karo"): only when the sentence is nothing else, so "phir se Ali ka hisaab batao" is Ali's.
         */
        data class Voice(val change: VoiceChange) : Ask()

        /** What sold most over a span of days (14 Nov 2026: "sab se zyada kya bik raha hai"), by Sale Insights' rule. */
        data class TopGoods(val window: VoiceQuestion.Window? = null) : Ask()

        /** The Qarz-e-Hasna in the book (14 Nov 2026): lent without profit and still to come back, by Zakat's own rule. */
        object Qarz : Ask()

        /** The automations (14 Nov 2026): which are on and at what hour. Changed only on their own screen, by the owner. */
        object Automations : Ask()

        /**
         * A product's fact with no product named ("is cheez ka stock", "khad kis kis ne li", 14 Nov 2026): he tells which
         * products he can answer for, as chips, instead of guessing one.
         */
        data class NeedGoods(val facet: GoodsFacet) : Ask()

        /**
         * The name the owner asks to be called by (14 Nov 2026, the owner's check logs: "my name is …", "mera naam … hai",
         * "mujhe … bulao" were read as a question about his name, as the shop's name or as a customer): [name] is kept on
         * this phone only (HudhudPrefs) and he calls the owner by it from now on. Said by the rules alone, never the sense's.
         */
        data class CallMe(val name: String) : Ask()

        /**
         * What he took a sentence to mean when nothing fitted it exactly (13 Nov 2026, the owner: "aap ka kaam batana
         * hai, poochhna nahi"): [ask] is answered, and the card says what he took it as, so a wrong guess is seen.
         */
        data class Guessed(val ask: Ask) : Ask()
    }

    /** His little acts. */
    enum class Move { FLY, DANCE, JUMP }

    /** How he is asked to speak: the last answer again, slower, faster, stop now, his voice off or on, or louder. */
    enum class VoiceChange { AGAIN, SLOWER, FASTER, QUIET, OFF, ON, VOLUME }

    /** What a [Ask.Period] adds up. */
    enum class Measure { SALES, PROFIT }

    /**
     * What is asked about a named customer or supplier, beside his balance. [NOTE] is the owner's private note on him
     * (14 Nov 2026): read by [HudhudNote] only, never by the sense (see [decide]).
     */
    enum class Facet { PHONE, DETAILS, LAST_PAYMENT, LAST_ENTRY, SINCE, LIMIT, PROMISE, ENTRIES, NOTE }

    /** What is asked about a product: everything at once ([OVERVIEW]) when only its name is said. */
    enum class GoodsFacet { OVERVIEW, PRICE, STOCK, COST, SUPPLIERS, LAST_IN, BUYERS, SOLD }

    /** The shop's profile, one field or [ALL]. */
    enum class ShopFacet { ALL, NAME, ADDRESS, PHONE, EMAIL, TAX, BANK, CURRENCY, COUNTRY, TRADE }

    /** About Hudhud and the app. */
    enum class AboutTopic {
        APP, MAKER, PRIVACY, LANGUAGES, VERSION, OWNER_NAME,
        /** "Why is your name Hudhud?" (14 Nov 2026, the owner): the hoopoe of Prophet Sulaiman, who brought true news. */
        NAME_STORY,
        /**
         * Only asked for, never added to another answer (14 Nov 2026, the owner: "har sawal ke jawab mein nahi, sirf us ke
         * specific sawal par"): Allah's oneness, hamd and sana with the tasbeeh of all creation, and the Prophet's ﷺ risalat.
         * [HudhudFaith] reads them, and only a sentence of nothing but those words.
         */
        TAWHEED, HAMD, RISALAT,
        /**
         * The app's own history and Hudhud's own story (14 Nov 2026, the owner: "app kab bani, kab publish hui, Hudhud kab add
         * hua" and "Hudhud apni aap-beeti sunaye"): when the work began, when the app was published, when Hudhud was added,
         * the whole timeline, and the hoopoe's story as the Quran tells it with its outcome. Only asked for, never added to
         * another answer; [HudhudStory] reads them, and only a sentence of nothing but those words.
         */
        APP_STARTED, APP_PUBLISHED, HUDHUD_ADDED, APP_JOURNEY, HOOPOE_STORY
    }

    /** What can be counted. */
    enum class Thing { CUSTOMERS, SUPPLIERS, PRODUCTS, STAFF, ENTRIES, INVOICES, BILLS, TICKETS, CALCULATIONS, APP_OPENS }

    /** A page whose standing can be told. */
    enum class StatusOf { CHEQUES, PLANS, BILLS, LOW_STOCK, BOOK, STAFF }

    /** The small talk he answers. */
    enum class Talk {
        HOW_ARE_YOU, WHO_ARE_YOU, THANKS, BYE, PRAISE, FINE,
        /** "Can you talk to me?", "are you there?", "can you hear me?" (13 Nov 2026). */
        CAN_TALK,
        /** Only a calling, no question: "bhai jaan", "Hudhud", "suno", "janab" (13 Nov 2026). */
        ADDRESS
    }

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
            if (ask is Ask.DidYouMean || ask is Ask.Guessed) {
                if (choice == null) choice = c to ask
                continue
            }
            if (ask != Ask.Unknown) return c to ask
        }
        return choice ?: ((candidates.firstOrNull() ?: "") to Ask.Unknown)
    }

    /**
     * What one sentence asks (13 Nov 2026, the second brain): the rules read it word by word ([byRules]) and
     * [HudhudSense] reads it whole, against the bank of examples; [decide] keeps the rules' reading wherever it rests
     * on firm ground (a name and what is asked of it, an amount said as an entry, a figure, a page) and lets the
     * sense speak where the rules found nothing, or only a guess — a name heard in a common word ("بات", "بھائی"), a
     * bare hello, a help page found by its words. Nothing is guessed silently: a reading the sense is not sure of is
     * answered as [Ask.Guessed], and the card says what it was taken as.
     */
    fun understand(
        heard: String, names: List<Name>, topics: List<Topic>, language: String? = null,
        goods: List<Name> = emptyList(), before: Ask? = null
    ): Ask {
        val ruled = byRules(heard, names, topics, language, goods, before)
        if (!HudhudSense.ready || words(heard).isEmpty()) return byWay(heard, ruled)
        val guesses = HudhudSense.rank(
            heard,
            isName = { w -> w !in QUIET_SET && w !in VOCATIVE_SET && !HudhudMind.isHardWord(w) && knownName(w, names) },
            isItem = { w -> w !in QUIET_SET && !HudhudMind.isHardWord(w) && knownName(w, goods) }
        )
        return byWay(heard, decide(heard, ruled, guesses, names, topics, language, goods, before))
    }

    /**
     * Money that came in is never the answer to money that went out (14 Nov 2026, the owner's sentences: "aaj maine kis
     * ko kitna diya", "aaj supplier ko kitna diya", "aaj kis ko payment ki" were all answered with the day's collections).
     * The shop paying somebody is the day's dealings ([Ask.Activity]: who, which way, how much); its spending
     * ("kharcha") is the cashbook's money out ([Ask.Cashbook]). A sentence that also says who paid the shop
     * ("kis kis ne payment ki", "wasooli") stays money in.
     */
    private fun byWay(heard: String, a: Ask): Ask {
        val came = (a as? Ask.Collected) ?: ((a as? Ask.Guessed)?.ask as? Ask.Collected) ?: return a
        val joined = words(heard).joinToString(" ")
        if (!has(joined, PAID_OUT) || has(joined, CAME_IN)) return a
        return if (has(joined, SPENT)) Ask.Cashbook(came.window) else Ask.Activity(came.window)
    }

    /** The shop paying out: "maine … diya", "kis ko", "adaigi ki", "kharcha", "I paid"… in the nine languages. */
    private val PAID_OUT = listOf(
        "maine", "mainay", "main ne", "mai ne", "hum ne", "humne", "hamne", "kis ko", "kisko", "kis kis ko", "ko diya",
        "ko diye", "ko di", "ko payment", "adaigi ki", "ada ki", "payment ki", "payment kar", "kharcha", "kharch", "kharche",
        "bahar gaye", "bahar gaya", "ko kitna diya", "ko kitne diye", "supplier ko", "jo diya", "diye hue", "chukaya",
        "chuka diya", "payment out", "paise nikle", "paise bahar", "raqam bheji", "i paid", "i gave", "we paid", "paid out", "spent", "money out", "went out", "to whom",
        "میں نے", "ہم نے", "کس کو", "کسے", "کو دیا", "کو دیے", "کو دی", "ادائیگی کی", "خرچ", "خرچہ", "خرچے", "باہر گئے",
        "मैंने", "हमने", "किस को", "किसको", "को दिया", "को दिए", "खर्च", "আমি দিয়েছি", "কাকে", "খরচ", "دفعت", "أنفقت", "لمن",
        "پرداخت کردم", "دادم", "به کی", "خرج", "مون ڏنا", "ڪنهن کي", "saya bayar", "saya membayar", "kepada siapa", "pengeluaran"
    )

    /** The shop being paid: kept as money in even with a word of paying in it. */
    private val CAME_IN = listOf(
        "kis ne", "kisne", "kis kis ne", "wasooli", "wusooli", "wasool", "mile", "mila", "aaye", "aaya", "aayi", "received",
        "collected", "came in", "got paid", "وصولی", "وصول", "ملے", "آئے", "کس نے", "किस ने", "किसने", "वसूली", "मिले",
        "আদায়", "পেয়েছি", "استلمت", "تحصيل", "دریافت", "diterima"
    )

    /** Spending, the cashbook's money out. */
    private val SPENT = listOf("kharcha", "kharch", "kharche", "spent", "expense", "expenses", "خرچ", "خرچہ", "خرچے", "खर्च",
        "খরচ", "مصروف", "مصاريف", "هزینه", "pengeluaran")

    /** The sense's own reading of [heard] (tests: how far it reaches alone); null when the bank finds nothing. */
    internal fun bySense(heard: String, names: List<Name>, topics: List<Topic>, language: String?, goods: List<Name>): Pair<Ask, Double>? {
        val guesses = HudhudSense.rank(
            heard,
            isName = { w -> w !in QUIET_SET && w !in VOCATIVE_SET && !HudhudMind.isHardWord(w) && knownName(w, names) },
            isItem = { w -> w !in QUIET_SET && !HudhudMind.isHardWord(w) && knownName(w, goods) }
        )
        val top = guesses.firstOrNull() ?: return null
        val ask = askFor(top.label, heard, names, topics, language, goods) ?: return null
        return ask to top.score
    }

    /** How far the rules' reading may be trusted against the sense's ([decide]). */
    private enum class Ground { NONE, WEAK, MEDIUM, FIRM }

    /** The question words: a sentence with one of them asks something, it does not only call him. */
    private val ASKING = setOf(
        HudhudMind.C.WHAT, HudhudMind.C.WHO, HudhudMind.C.HOW_MANY, HudhudMind.C.WHEN, HudhudMind.C.WHERE,
        HudhudMind.C.WHICH, HudhudMind.C.HOW_IS
    )

    /** The meanings that are about one person: the sense may only sharpen a reading of somebody into one of these. */
    private fun personal(label: String) = label.startsWith("party_") || label.startsWith("entry_")

    /** The owner's private note on somebody, or on whoever has one ("whose?"): the rules' reading, never the sense's. */
    private fun isNoteAsk(a: Ask): Boolean =
        (a is Ask.Person && a.facet == Facet.NOTE) || (a is Ask.NeedName && a.facet == Facet.NOTE)

    private fun groundOf(a: Ask): Ground = when (a) {
        Ask.Unknown, is Ask.DidYouMean, is Ask.NeedName, is Ask.Guessed -> Ground.NONE
        Ask.Hello -> Ground.WEAK
        is Ask.Parties -> if (a.ids.size > 1) Ground.WEAK else Ground.MEDIUM
        is Ask.Shop, is Ask.Help -> Ground.MEDIUM
        else -> Ground.FIRM
    }

    /**
     * How sure the sense is, as its share of the meanings (0 to 1; measured on the held-out set, 13 Nov 2026: of the
     * readings at 0.9 or more, nine in ten were right; at 0.6 two in three; under 0.3 one in three).
     */
    internal const val SENSE_SURE = 0.60
    /** From here a reading is answered and the card says what it was taken as ([Ask.Guessed]); below, not used. */
    internal const val SENSE_MAYBE = 0.30
    /**
     * How sure the sense must be to turn a reading the rules made on firm ground: nearly certain (measured on the
     * held-out set, 13 Nov 2026: from 0.80 to 0.95 the share right is the same, and above 0.80 the rules' own figures —
     * "is maheenay ki sale", "المباع هذا الشهر", the app's own chips — were turned into something else).
     */
    internal const val SENSE_OVERRULE = 0.95
    /** How sure it must be to turn a named person into something else. */
    internal const val SENSE_CERTAIN = 0.92

    /** The customers or suppliers a reading names (none for a reading of the whole shop). */
    private fun namedBy(a: Ask): List<Long> = when (a) {
        is Ask.Parties -> a.ids
        is Ask.Person -> a.ids
        is Ask.Moved -> a.ids
        is Ask.WhenPays -> a.ids
        is Ask.Entry -> a.ids
        else -> emptyList()
    }

    /**
     * Whether [heard] names one of [ids] surely: a word of his name four letters or more (as the search folds it) that is
     * no word of the language's own, or two words of one name. "Ali" alone is short enough to be "على" or "اللي" said in
     * Arabic; "Bashir" or "Ali Raza" is the man.
     */
    private fun namedSurely(heard: String, names: List<Name>, ids: List<Long>): Boolean {
        val said = words(heard).filter { it !in QUIET_SET && it !in VOCATIVE_SET && !HudhudMind.isHardWord(it) }
        if (said.isEmpty()) return false
        for (n in names) {
            if (n.id !in ids) continue
            val own = words(n.name)
            val folds = own.map { NameSearch.fold(it) }.filter { it.isNotEmpty() }.toSet()
            // Across scripts a name meets its spelling by its consonants ("بشیر" and "Bashir"): three or more is a name.
            val bones = own.map { HudhudMind.skeleton(it) }.filter { it.length >= 3 }.toSet()
            val hits = said.filter { w -> NameSearch.fold(w) in folds || HudhudMind.skeleton(w) in bones }
            if (hits.distinct().size >= 2) return true
            if (hits.any { w -> NameSearch.fold(w).length >= 4 || HudhudMind.skeleton(w).length >= 3 }) return true
        }
        return false
    }

    /**
     * Whether any word of [heard] is a word of [ids]' names (by its spelling or its consonants). None: the person came
     * from the question before ("uska number?", a chip under his answer) — carried on, as surely as named.
     */
    private fun namedAtAll(heard: String, names: List<Name>, ids: List<Long>): Boolean {
        val said = words(heard).filter { it !in QUIET_SET && it !in VOCATIVE_SET }
        for (n in names) {
            if (n.id !in ids) continue
            val own = words(n.name)
            val folds = own.map { NameSearch.fold(it) }.filter { it.isNotEmpty() }.toSet()
            val bones = own.map { HudhudMind.skeleton(it) }.filter { it.length >= 2 }.toSet()
            if (said.any { w -> NameSearch.fold(w) in folds || HudhudMind.skeleton(w) in bones }) return true
        }
        return false
    }

    /** True when every word that asks something is a word of [id]'s name: the sentence only names him. */
    private fun onlyNames(heard: String, names: List<Name>, id: Long): Boolean {
        val own = names.firstOrNull { it.id == id }?.let { words(it.name).map { w -> NameSearch.fold(w) }.toSet() } ?: return false
        return words(heard).all { it in QUIET_SET || it in VOCATIVE_SET || NameSearch.fold(it) in own }
    }

    private fun decide(
        heard: String, ruled: Ask, guesses: List<HudhudSense.Guess>, names: List<Name>, topics: List<Topic>,
        language: String?, goods: List<Name>, before: Ask? = null
    ): Ask {
        // The owner's private note on somebody (14 Nov 2026): read by [HudhudNote] alone. The bank has no meaning for it,
        // and the sense, which learnt "details" and "number" from sentences with a name in them, would turn "Ali ka note"
        // into his details. Before anything else, so no branch below can reach it.
        if (isNoteAsk(ruled)) return ruled
        val top = guesses.firstOrNull() ?: return ruled
        if (labelOf(ruled) == top.label) return ruled
        val p = top.score
        fun theirs(): Ask? = askFor(top.label, heard, names, topics, language, goods)
        // Somebody the book holds, named: the sense may tell what is asked of him ("Ali kab dega" is his day, not his
        // balance), but turns him into something else only when nearly certain — "Bashir ka hisaab" is Bashir's,
        // whatever the word "hisaab" says alone; a sentence that is nothing but his name is his. The same for a reading
        // that already says what is asked of him (his last payment, his limit): measured on the held-out set, a sense
        // sure of "money collected" for "Ali ki aakhri wusooli" was wrong every time the rules had found Ali.
        val who = namedBy(ruled)
        if (who.isNotEmpty()) {
            val refine = if (ruled is Ask.Parties) SENSE_MAYBE else SENSE_OVERRULE
            // Named surely (a word of his name that is no common word, or two of its words): only talk turns him into
            // something else ("Kamal kar diya" is praise even in a book with a Kamal), and a question of the shop's
            // figures that names him is his ("Bashir ka hisaab"). Named only by a short word that may be another word
            // ("Ali" in a book, "على" in Arabic): a question of the shop the sense is all but certain of wins.
            // Carried on from the question before ("uska number?", a chip under his answer): as sure as named.
            val carried = before != null && namedBy(before).any { it in who } && !namedAtAll(heard, names, who)
            val sure = carried || namedSurely(heard, names, who)
            return when {
                personal(top.label) && p >= refine -> theirs()?.takeIf { it !is Ask.NeedName } ?: ruled
                who.size == 1 && onlyNames(heard, names, who.first()) -> ruled
                top.label in TALK && p >= SENSE_CERTAIN -> theirs() ?: ruled
                !sure && top.label in NO_SLOT && p >= SENSE_CERTAIN -> theirs() ?: ruled
                else -> ruled
            }
        }
        // "A customer's number", "kisi ki limit": somebody's fact with nobody named — the latest customers with it, as
        // the rules read it; the sense may only sharpen which fact.
        if (ruled is Ask.NeedName && ruled.facet != null) {
            return if (personal(top.label) && p >= SENSE_SURE) theirs()?.takeIf { it is Ask.NeedName || it is Ask.Person } ?: ruled
            else ruled
        }
        // A product the book holds, named: what is asked of it may be sharpened (its price, its stock, who bought it),
        // but it is not turned into a question of the whole shop — "Urea ki is hafte ki sale" is Urea's, not the
        // week's sales, however sure the sense is of the words without the product.
        if (ruled is Ask.Goods && ruled.ids.isNotEmpty()) {
            return if (top.label.startsWith("goods_") && p >= SENSE_OVERRULE) theirs()?.takeIf { it is Ask.Goods && it.ids.isNotEmpty() } ?: ruled
            else ruled
        }
        // A question that asks for the way ("kaise", "kahan", "tareeqa", "how", "where", "کیسے"…) and whose words the rules
        // found on one of the app's own help pages is asked of that page; the sense, which learnt few such sentences, may
        // read it as another meaning only when all but certain. 14 Nov 2026, the owner's 12,082 questions run through
        // him: of the 2,554 that asked the way, the rules had found a page for about 2,250 and the sense turned some 1,000
        // of those into a figure, a count, "about the app" or small talk.
        // "Why is your name Hudhud?" is read by the rules alone (a why with his name or "your name"): the bank has no such
        // meaning, and the sense was all but sure such a sentence asked who he is.
        if (ruled is Ask.About && (ruled.topic == AboutTopic.NAME_STORY || ruled.topic in FAITH_TOPICS || ruled.topic in STORY_TOPICS)) return ruled
        // A feature named by its own word (14 Nov 2026), and the asks only the rules make: never turned by the sense.
        if (ruled is Ask.Help && featureNamed(heard)) return ruled
        if (ruled is Ask.Voice || ruled is Ask.TopGoods || ruled == Ask.Qarz || ruled == Ask.Automations || ruled is Ask.NeedGoods ||
            ruled is Ask.CallMe
        ) {
            return ruled
        }
        // "<somebody> ne … dene hain" (14 Nov 2026, the owner's check log): somebody else has to give, so the money is the
        // shop's to get. The sense learnt "mujhe dene hain" (the shop gives) from first-person sentences and turned such a
        // sentence into the opposite at 0.99; the rules read who the doer is, so their reading stays. Only against a reading
        // that turns the direction: "Aaj kis kis ne paise dene hain?" asks WHO is to pay (the list of those due, or unpaid),
        // not what the shop will get in all, and the sense is right to say so (the owner's list, L003).
        if (ruled is Ask.Figure && ruled.kind in OWED_TO_US && VoiceQuestion.hasOtherDoer(heard) && top.label !in WHO_IS_TO_PAY) return ruled
        if (ruled is Ask.Help && asksTheWay(heard)) {
            return if (p >= SENSE_OVERRULE) theirs() ?: ruled else ruled
        }
        return when (groundOf(ruled)) {
            Ground.NONE -> {
                val ask = theirs() ?: return ruled
                when {
                    // "Whose?" with a list of the latest customers says more than a guess at somebody's fact.
                    ruled is Ask.NeedName && ask is Ask.NeedName -> ruled
                    p >= SENSE_SURE -> ask
                    p >= SENSE_MAYBE -> Ask.Guessed(ask)
                    else -> ruled
                }
            }
            Ground.WEAK, Ground.MEDIUM -> if (p >= SENSE_MAYBE) theirs() ?: ruled else ruled
            Ground.FIRM -> if (p >= SENSE_OVERRULE) theirs() ?: ruled else ruled
        }
    }

    /**
     * The nearest things [heard] could be asking, for the chips under an answer he could not give (13 Nov 2026, the
     * owner: "kehne wala jo keyword de raha hai, us hisaab se suggestion de"): the sense's next meanings, each as a
     * question of the book; none that would need a name the words do not give.
     */
    fun suggest(heard: String, names: List<Name>, topics: List<Topic>, language: String?, goods: List<Name>, skip: Ask? = null): List<Ask> {
        if (!HudhudSense.ready) return closest(heard)
        val skipLabel = skip?.let { labelOf(if (it is Ask.Guessed) it.ask else it) }
        val out = LinkedHashSet<Ask>()
        for (g in HudhudSense.rank(heard, top = 8)) {
            if (g.label == skipLabel || g.score < SUGGEST_FLOOR) continue
            val a = askFor(g.label, heard, names, topics, language, goods) ?: continue
            if (a is Ask.NeedName || a is Ask.Entry) continue
            out += a
            if (out.size == MAX_SUGGEST) break
        }
        return out.toList().ifEmpty { closest(heard) }
    }

    private const val SUGGEST_FLOOR = 0.02
    private const val MAX_SUGGEST = 3

    /** The figures that are money owed TO the shop: kept when the doer of "dene hain" is somebody else ([decide]). */
    private val OWED_TO_US = setOf(
        VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.WHO_OWES_MOST, VoiceQuestion.Kind.WHO_OWES_LEAST
    )

    /** Sense readings that list WHO is to pay (due today, unpaid): a sharper reading of "kis ne dene hain", not its opposite. */
    private val WHO_IS_TO_PAY = setOf("likely_payers", "not_paid")

    /** Whose the app is: "whose", "who owns", "kis ki", "malik", "milik siapa", "مال کیست", "ڪنهن جي", "কার" — beside the word for the app. */
    private val WHOSE_APP = listOf(
        "whose", "who owns", "owner of", "belongs to", "app owner", "kis ki", "kis ka", "kis ke", "kiski", "kiska", "kiske", "malik",
        "maalik", "کس کی", "کس کا", "کس کے", "مالک", "لمن", "مال کیست", "مال کیه", "ڪنهن جي", "ڪنهن جو", "مالڪ", "किस की", "किस का",
        "किसकी", "किसका", "मालिक", "কার", "মালিক", "milik", "pemilik", "punya siapa"
    )

    /** The faith's own questions: read by [HudhudFaith] alone, never turned by the sense ([decide]). */
    internal val FAITH_TOPICS = setOf(AboutTopic.TAWHEED, AboutTopic.HAMD, AboutTopic.RISALAT)

    private fun faithTopic(t: HudhudFaith.Topic): AboutTopic = when (t) {
        HudhudFaith.Topic.TAWHEED -> AboutTopic.TAWHEED
        HudhudFaith.Topic.HAMD -> AboutTopic.HAMD
        HudhudFaith.Topic.RISALAT -> AboutTopic.RISALAT
    }

    /** The app's history and his own story: read by [HudhudStory] alone, never turned by the sense ([decide]). */
    internal val STORY_TOPICS = setOf(
        AboutTopic.APP_STARTED, AboutTopic.APP_PUBLISHED, AboutTopic.HUDHUD_ADDED, AboutTopic.APP_JOURNEY, AboutTopic.HOOPOE_STORY
    )

    private fun storyTopic(t: HudhudStory.Topic): AboutTopic = when (t) {
        HudhudStory.Topic.STARTED -> AboutTopic.APP_STARTED
        HudhudStory.Topic.PUBLISHED -> AboutTopic.APP_PUBLISHED
        HudhudStory.Topic.HUDHUD -> AboutTopic.HUDHUD_ADDED
        HudhudStory.Topic.JOURNEY -> AboutTopic.APP_JOURNEY
        HudhudStory.Topic.HOOPOE -> AboutTopic.HOOPOE_STORY
    }

    /** Meanings that are talk with him, not a question of the book: words a name may sound like ("kamal", "shukriya"). */
    private val TALK = setOf(
        "who_are_you", "how_are_you", "can_talk", "address", "salaam", "hello", "thanks", "bye", "praise", "abilities",
        "act_fly", "act_dance", "act_jump"
    )

    /** Meanings that need nothing from the sentence but themselves (no name, no product, no amount). */
    private val NO_SLOT = setOf(
        "who_are_you", "abilities", "how_are_you", "can_talk", "address", "salaam", "hello", "thanks", "bye", "praise",
        "owner_name", "maker", "app_about", "privacy", "languages", "date_time", "today_summary", "sales_today",
        "sales_period", "profit", "to_get", "to_give", "who_owes_most", "who_owes_least", "not_paid", "likely_payers", "collected",
        "drawer", "expiring", "low_stock", "shop_info", "count_customers", "count_products", "bills_today",
        "invoices_today", "tickets_today", "entries_today", "calculator_uses", "app_opens", "cheques_status",
        "plans_status", "staff_salary", "deliveries", "activity", "backup_status", "book_summary", "cashbook_today",
        "returns_today", "help_howto", "act_fly", "act_dance", "act_jump"
    )

    /** The bank's meaning for a reading the rules made; null for a reading that is no meaning yet (unknown, a choice). */
    internal fun labelOf(a: Ask): String? = when (a) {
        is Ask.Chat -> when (a.talk) {
            Talk.WHO_ARE_YOU -> "who_are_you"
            Talk.HOW_ARE_YOU, Talk.FINE -> "how_are_you"
            Talk.CAN_TALK -> "can_talk"
            Talk.ADDRESS -> "address"
            Talk.THANKS -> "thanks"
            Talk.BYE -> "bye"
            Talk.PRAISE -> "praise"
        }
        Ask.Abilities -> "abilities"
        Ask.Salaam -> "salaam"
        Ask.Hello -> "hello"
        is Ask.About -> when (a.topic) {
            AboutTopic.OWNER_NAME -> "owner_name"
            AboutTopic.MAKER -> "maker"
            AboutTopic.APP, AboutTopic.VERSION -> "app_about"
            AboutTopic.PRIVACY -> "privacy"
            AboutTopic.LANGUAGES -> "languages"
            AboutTopic.NAME_STORY -> "name_story"
            // The faith's own questions have no meaning in the bank: only the rules read them ([HudhudFaith]).
            AboutTopic.TAWHEED, AboutTopic.HAMD, AboutTopic.RISALAT -> null
            // The app's history and his story likewise: only [HudhudStory] reads them.
            AboutTopic.APP_STARTED, AboutTopic.APP_PUBLISHED, AboutTopic.HUDHUD_ADDED, AboutTopic.APP_JOURNEY,
            AboutTopic.HOOPOE_STORY -> null
        }
        Ask.Clock -> "date_time"
        Ask.Today -> "today_summary"
        is Ask.Figure -> when (a.kind) {
            VoiceQuestion.Kind.SALES_TODAY -> "sales_today"
            VoiceQuestion.Kind.SALES_MONTH -> "sales_period"
            VoiceQuestion.Kind.PROFIT_MONTH -> "profit"
            VoiceQuestion.Kind.TO_GET -> "to_get"
            VoiceQuestion.Kind.TO_GIVE -> "to_give"
            VoiceQuestion.Kind.WHO_OWES_MOST -> "who_owes_most"
            VoiceQuestion.Kind.WHO_OWES_LEAST -> "who_owes_least"
            VoiceQuestion.Kind.NOT_PAID -> "not_paid"
            VoiceQuestion.Kind.EXPIRING -> "expiring"
        }
        is Ask.Period -> if (a.measure == Measure.PROFIT) "profit" else if (a.window == VoiceQuestion.Window.TODAY) "sales_today" else "sales_period"
        is Ask.WhenPays -> if (a.ids.isEmpty()) "likely_payers" else "party_when_pays"
        is Ask.Collected -> "collected"
        Ask.Drawer -> "drawer"
        is Ask.Parties, is Ask.Moved -> "party_balance"
        is Ask.Person -> when (a.facet) {
            Facet.PHONE -> "party_phone"
            Facet.LAST_PAYMENT -> "party_last_payment"
            Facet.LIMIT -> "party_limit"
            Facet.PROMISE -> "party_promise"
            // The private note has no meaning in the bank: only [HudhudNote] reads it, and the sense never turns it.
            Facet.NOTE -> null
            else -> "party_details"
        }
        is Ask.Entry -> if (a.isGiven) "entry_gave" else "entry_got"
        is Ask.Goods -> when (a.facet) {
            GoodsFacet.OVERVIEW, GoodsFacet.PRICE -> "goods_price"
            GoodsFacet.STOCK -> "goods_stock"
            GoodsFacet.SUPPLIERS, GoodsFacet.COST, GoodsFacet.LAST_IN -> "goods_suppliers"
            GoodsFacet.BUYERS -> "goods_buyers"
            GoodsFacet.SOLD -> "goods_sold"
        }
        is Ask.Shop -> "shop_info"
        is Ask.Count -> when (a.thing) {
            Thing.CUSTOMERS, Thing.SUPPLIERS -> "count_customers"
            Thing.PRODUCTS -> "count_products"
            Thing.STAFF -> "staff_salary"
            Thing.ENTRIES -> "entries_today"
            Thing.INVOICES -> "invoices_today"
            Thing.BILLS -> "bills_today"
            Thing.TICKETS -> "tickets_today"
            Thing.CALCULATIONS -> "calculator_uses"
            Thing.APP_OPENS -> "app_opens"
        }
        is Ask.Status -> when (a.of) {
            StatusOf.CHEQUES -> "cheques_status"
            StatusOf.PLANS -> "plans_status"
            StatusOf.BILLS -> "bills_today"
            StatusOf.LOW_STOCK -> "low_stock"
            StatusOf.BOOK -> "book_summary"
            StatusOf.STAFF -> "staff_salary"
        }
        is Ask.Deliveries -> "deliveries"
        is Ask.Activity -> "activity"
        Ask.BackupStatus -> "backup_status"
        is Ask.Cashbook -> "cashbook_today"
        is Ask.Returns -> "returns_today"
        is Ask.Help -> "help_howto"
        is Ask.Act -> when (a.move) {
            Move.FLY -> "act_fly"
            Move.DANCE -> "act_dance"
            Move.JUMP -> "act_jump"
        }
        Ask.Unknown, is Ask.DidYouMean, is Ask.NeedName, is Ask.Guessed, is Ask.Voice, is Ask.TopGoods, Ask.Qarz,
        Ask.Automations, is Ask.NeedGoods, is Ask.CallMe -> null
    }

    /**
     * The question a meaning of the bank asks of the book, with what the sentence itself says: the span of days, the
     * name and the product the rules find in it, an amount; a meaning that needs a name the sentence does not give
     * is "whose?" ([Ask.NeedName]) — never somebody guessed.
     */
    internal fun askFor(
        label: String, heard: String, names: List<Name>, topics: List<Topic>, language: String?, goods: List<Name>
    ): Ask? {
        val said = words(heard)
        val joined = said.joinToString(" ")
        val salaam = has(joined, SALAAM)
        val window = VoiceQuestion.windowOf(heard)
        val m = HudhudMind.read(said) { w -> w in QUIET_SET || knownName(w, names) || knownName(w, goods) }
        val named by lazy { whoIsNamed(said, names, topics, m, window) }
        val product by lazy { whoIsNamed(said, goods, topics, m, window) }
        fun person(f: Facet): Ask = if (named.isEmpty()) Ask.NeedName(f) else Ask.Person(named, f)
        // A product's question that names no product: the shop's sale for "sold", else no reading of the sense's.
        fun goodsOf(f: GoodsFacet): Ask? = when {
            product.isNotEmpty() -> Ask.Goods(product, f, if (f == GoodsFacet.SOLD) window else null)
            f == GoodsFacet.SOLD -> figureOver(VoiceQuestion.Kind.SALES_MONTH, window)
            else -> null
        }
        return when (label) {
            "who_are_you" -> Ask.Chat(Talk.WHO_ARE_YOU, salaam)
            "abilities" -> Ask.Abilities
            "how_are_you" -> Ask.Chat(Talk.HOW_ARE_YOU, salaam)
            "can_talk" -> Ask.Chat(Talk.CAN_TALK, salaam)
            "address" -> Ask.Chat(Talk.ADDRESS, salaam)
            "salaam" -> Ask.Salaam
            "hello" -> Ask.Hello
            "thanks" -> Ask.Chat(Talk.THANKS, salaam)
            "bye" -> Ask.Chat(Talk.BYE, salaam)
            "praise" -> Ask.Chat(Talk.PRAISE, salaam)
            "owner_name" -> Ask.About(AboutTopic.OWNER_NAME)
            "maker" -> Ask.About(AboutTopic.MAKER)
            "app_about" -> Ask.About(AboutTopic.APP)
            "privacy" -> Ask.About(AboutTopic.PRIVACY)
            "languages" -> Ask.About(AboutTopic.LANGUAGES)
            "name_story" -> Ask.About(AboutTopic.NAME_STORY)
            "date_time" -> Ask.Clock
            "today_summary" -> Ask.Today
            "sales_today" -> figureOver(VoiceQuestion.Kind.SALES_TODAY, window)
            "sales_period" -> figureOver(VoiceQuestion.Kind.SALES_MONTH, window)
            "profit" -> figureOver(VoiceQuestion.Kind.PROFIT_MONTH, window)
            "to_get" -> Ask.Figure(VoiceQuestion.Kind.TO_GET)
            "to_give" -> Ask.Figure(VoiceQuestion.Kind.TO_GIVE)
            "who_owes_most" -> Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST)
            "who_owes_least" -> Ask.Figure(VoiceQuestion.Kind.WHO_OWES_LEAST)
            "not_paid" -> Ask.Figure(VoiceQuestion.Kind.NOT_PAID)
            "expiring" -> Ask.Figure(VoiceQuestion.Kind.EXPIRING)
            "likely_payers" -> Ask.WhenPays(emptyList())
            "collected" -> Ask.Collected(window)
            "drawer" -> Ask.Drawer
            "low_stock" -> Ask.Status(StatusOf.LOW_STOCK)
            "party_balance" -> if (named.isEmpty()) Ask.NeedName(null) else Ask.Parties(named)
            "party_phone" -> person(Facet.PHONE)
            "party_last_payment" -> person(Facet.LAST_PAYMENT)
            "party_limit" -> person(Facet.LIMIT)
            "party_promise" -> person(Facet.PROMISE)
            "party_details" -> person(Facet.DETAILS)
            "party_when_pays" -> Ask.WhenPays(named)
            "entry_gave", "entry_got" -> {
                val entry = VoiceEntry.parse(heard, emptyList(), language)
                val amount = entry.amount
                if (amount == null || amount <= 0.0) {
                    if (named.isEmpty()) Ask.NeedName(null) else Ask.Parties(named)
                } else {
                    val nameWords = VoiceEntry.nameWords(heard)
                    Ask.Entry(amount, entry.isGiven ?: (label == "entry_gave"), partiesOf(names, nameWords), nameWords)
                }
            }
            "goods_price" -> goodsOf(GoodsFacet.PRICE)
            "goods_stock" -> goodsOf(GoodsFacet.STOCK)
            "goods_suppliers" -> goodsOf(GoodsFacet.SUPPLIERS)
            "goods_buyers" -> goodsOf(GoodsFacet.BUYERS)
            "goods_sold" -> goodsOf(GoodsFacet.SOLD)
            "shop_info" -> Ask.Shop(
                when {
                    m.has(HudhudMind.C.PHONE) -> ShopFacet.PHONE
                    m.has(HudhudMind.C.ADDRESS) -> ShopFacet.ADDRESS
                    m.has(HudhudMind.C.NAME) -> ShopFacet.NAME
                    else -> ShopFacet.ALL
                }
            )
            "count_customers" -> Ask.Count(if (m.has(HudhudMind.C.SUPPLIER) && !m.has(HudhudMind.C.CUSTOMER)) Thing.SUPPLIERS else Thing.CUSTOMERS)
            "count_products" -> Ask.Count(Thing.PRODUCTS)
            "bills_today" -> Ask.Count(Thing.BILLS, window)
            "invoices_today" -> Ask.Count(Thing.INVOICES, window)
            "tickets_today" -> Ask.Count(Thing.TICKETS, window)
            "entries_today" -> Ask.Count(Thing.ENTRIES, window)
            "calculator_uses" -> Ask.Count(Thing.CALCULATIONS, window)
            "app_opens" -> Ask.Count(Thing.APP_OPENS, window)
            "cheques_status" -> Ask.Status(StatusOf.CHEQUES)
            "plans_status" -> Ask.Status(StatusOf.PLANS)
            "staff_salary" -> Ask.Status(StatusOf.STAFF)
            "deliveries" -> Ask.Deliveries(window)
            "activity" -> Ask.Activity(window)
            "backup_status" -> Ask.BackupStatus
            "book_summary" -> Ask.Status(StatusOf.BOOK)
            "cashbook_today" -> Ask.Cashbook(window)
            "returns_today" -> Ask.Returns(window)
            "help_howto" -> helpOf(said, topics)?.let { Ask.Help(it) } ?: Ask.Abilities
            "act_fly" -> Ask.Act(Move.FLY)
            "act_dance" -> Ask.Act(Move.DANCE)
            "act_jump" -> Ask.Act(Move.JUMP)
            else -> null
        }
    }

    internal fun byRules(
        heard: String, names: List<Name>, topics: List<Topic>, language: String? = null,
        goods: List<Name> = emptyList(), before: Ask? = null
    ): Ask {
        val said = words(heard)
        if (said.isEmpty()) return Ask.Unknown
        val joined = said.joinToString(" ")
        // The app's own history and his own story (14 Nov 2026, the owner: "app kab publish hui", "Hudhud kab add hua", "Hudhud
        // apni aap-beeti sunao"): answered only to a sentence that is nothing but those words, and before the how-to pages ("how
        // old is the app" holds a "how"). "Ali ki kahani" and "Roshan ka khata kab bana" are never read here: a word of the book
        // or of somebody's name is never a history's.
        HudhudStory.read(heard, language) { w -> knownName(w, names) || knownName(w, goods) }?.let { return Ask.About(storyTopic(it)) }
        val how = has(joined, HOW)
        if (how) helpOf(said, topics)?.let { return Ask.Help(it) }
        // A sentence that is nothing but small talk, or "what can you tell me" (13 Nov 2026): taken before any name,
        // so "Allah Hafiz" is a goodbye and not a customer called Hafiz; one with any other word goes on as before.
        voiceChange(said, joined)?.let { return Ask.Voice(it) }
        // "My name is …", "call me …", "mera naam … hai", "mujhe … bulao" (14 Nov 2026): the owner tells him the name to be
        // called by. Taken before any name or concept — "mera naam Ali hai" opens no customer called Ali.
        callMeName(heard)?.let { return Ask.CallMe(it) }
        talk(said, whole = true)?.let { return it }
        // Only a calling and nothing asked (13 Nov 2026, the owner: "bhai jaan" opened a customer called "...Bhai"):
        // he answers to being called — "ji, haazir hoon" — and looks for nobody.
        // A greeting with his name ("hello Hudhud", "salaam bhai") stays a greeting, and "Hudhud kya hai" a question.
        if (said.any { it in VOCATIVE_SET } && said.none { it in GREETING_SET } && '?' !in heard && '\u061F' !in heard &&
            said.none { w -> HudhudMind.meaningOf(w).any { it in ASKING } } &&
            said.all { it in VOCATIVE_SET || it in QUIET_SET || HudhudMind.meaningOf(it) == YOU_ONLY }
        ) return Ask.Chat(Talk.ADDRESS)
        // Tawheed, hamd and sana, the Prophet's ﷺ risalat (14 Nov 2026, the owner): answered only to a sentence that is nothing
        // but those words — "Allah kaun hai", "hamd o sana", "Nabi kareem kaun hain"; "Allah Ditta ka hisaab" is a customer's.
        HudhudFaith.read(heard) { w -> knownName(w, names) }?.let { return Ask.About(faithTopic(it)) }
        // What each word means, in any order (13 Nov 2026, Brain v2): "aap ka kya naam hai" and "naam kya hai aap ka"
        // are one question. A word the book knows as somebody's or a product's name is never read by its sound.
        val figureWords = VoiceQuestion.keywordsIn(joined)
        val mind = HudhudMind.read(said, language) { w ->
            w in QUIET_SET || w in DAY_WORDS || w in figureWords || knownName(w, names) || knownName(w, goods)
        }
        aboutHim(mind, joined) { w -> knownName(w, names) || knownName(w, goods) }?.let { return it }
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
        // The owner's private note on somebody (14 Nov 2026): "Ali ka note", "Ali's private note", "uska niji note", "kis ka note
        // hai". Only a sentence of nothing but the note's words, a name and little words ([HudhudNote]); any other is read as
        // before. Taken before the features and the follow-ups: a carried "uska note" is the note, not the balance.
        HudhudNote.read(heard, calling = { it in VOCATIVE_SET }) { w ->
            knownName(w, names) || knownName(bengaliStem(w), names) || acrossScripts(names, listOf(w)).isNotEmpty()
        }?.let { n -> noteAsk(n, said, names, topics, mind, window, before)?.let { return it } }
        if (named.isEmpty() && product.isEmpty()) byFeature(said, joined, mind, window, topics)?.let { return it }
        followUp(mind, named, product, figure, window, willPay, before)?.let { return it }
        // "Aap ko kisi cheez ka pata hai?" asks what he knows (13 Nov 2026, the owner's screenshot: it was read as the
        // shop's address, "ka pata"); with a name it is about that person ("aap ko Ali ka pata hai").
        if (knowsFrame(said) && named.isEmpty() && product.isEmpty() && figure == null && !willPay) return Ask.Abilities
        // "Uska number" with nobody before it: he asks whose, he does not guess.
        if (mind.has(HudhudMind.C.HIS) && named.isEmpty() && product.isEmpty() && !willPay && figure == null &&
            (before == null || (partyOf(before) == null && goodsOf(before) == null)) && !mind.any(HudhudMind.C.DATA, HudhudMind.C.ACCOUNT) &&
            facetOf(mind).let { it != null && (it != Facet.DETAILS || mind.any(HudhudMind.C.FATHER, HudhudMind.C.VILLAGE, HudhudMind.C.ADDRESS)) }
        ) return Ask.NeedName(facetOf(mind))
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
            return Ask.NeedName(if (mind.has(HudhudMind.C.LIMIT)) Facet.LIMIT else Facet.PROMISE)
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

    // ------------------------------------------------------------------------------------------- "Call me …", 14 Nov

    /** A name to be called by is at most this many letters (the phone keeps it in HudhudPrefs.CALL_ME_MAX). */
    private const val NAME_MAX = 30

    /**
     * The name the owner asks to be called by (14 Nov 2026, the owner's check logs: "my name is …", "mera naam … hai"
     * and "mujhe … bulao" were read as a question about his name, as the shop's name or as a customer): the name in
     * its own script, or null when the sentence is no such statement.
     *
     * Two shapes only — the name after "my name (is)", "call me", "mera naam"… in the nine languages, and the name
     * between "mujhe" and a calling verb ("mujhe Ali bulao", "مجھے علی بلاؤ") — and never "I am …" (as often "I am
     * tired"). It is a statement and nothing else: a question, a how-to, a sentence with other things in it, a word
     * that means something to Hudhud and a calling or telling word are never a name, so "mera naam bata do" and "do
     * you know my name" ask the owner's name and set none. One to three words, 30 letters at most.
     */
    internal fun callMeName(heard: String): String? {
        val raw = ArrayList<String>()
        val low = ArrayList<String>()
        for (t in heard.split(NAME_SPLIT)) {
            val n = norm(t)
            if (n.isEmpty() || ' ' in n) continue
            raw += t
            low += n
        }
        if (low.size < 2 || low.size > 14) return null
        val joined = low.joinToString(" ")
        if ('?' in heard || '؟' in heard || has(joined, QUESTION) || has(joined, HOW) || low.any { it in ASKS_FIRST }) return null
        // "My name is Ali", "call me Ali", "mera naam Ali hai", "اسمي علي": the name follows the marker, a copula may come first.
        findPhrase(low, CALL_ME_BEFORE)?.let { (start, end) ->
            if (!low.subList(0, start).all(::callLead)) return null
            var i = end
            while (i < low.size && low[i] in COPULAS) i++
            val taken = takeName(raw, low, i) ?: return null
            return if (low.drop(taken.second).all(::callRest)) taken.first else null
        }
        // "Mujhe Ali bulao", "مجھے علی کہو", "মুझে …": the name stands between "me" and the calling verb.
        findPhrase(low, CALL_ME_TO)?.let { (start, end) ->
            if (!low.subList(0, start).all(::callLead)) return null
            var i = end
            while (i < low.size && (low[i] in SKIP_AFTER_ME || low[i] in COPULAS)) i++
            val taken = takeName(raw, low, i) ?: return null
            val rest = low.drop(taken.second)
            // "Mujhe Ali ne bulaya" is Ali calling him.
            return if (rest.any { it in CALL_VERBS } && rest.none { it in DOER_MARKS } && rest.all(::callRest)) taken.first else null
        }
        return null
    }

    /** The name's one to three words starting at [from], in their own script, and where the next word stands. */
    private fun takeName(raw: List<String>, low: List<String>, from: Int): Pair<String, Int>? {
        val parts = ArrayList<String>()
        var i = from
        while (i < low.size && parts.size < 3 && nameWord(low[i])) {
            val r = raw[i]
            // A name said in lower case is written with its capital; Arabic scripts have none to change.
            parts += if (r.none { it.isUpperCase() }) r.replaceFirstChar { it.titlecase(java.util.Locale.ROOT) } else r
            i++
        }
        if (parts.isEmpty()) return null
        val name = parts.joinToString(" ")
        return if (name.length <= NAME_MAX) name to i else null
    }

    /** A word that can be a name: it is no little word, no calling or telling word, no question and means nothing to him. */
    private fun nameWord(w: String): Boolean =
        w.length in 2..20 && w !in QUIET_SET && w !in VOCATIVE_SET && w !in FILLER_SET && w !in GREETING_SET &&
            w !in COPULAS && w !in CALL_VERBS && w !in CALL_REST && w !in DAY_WORDS && w !in NOT_A_NAME &&
            w !in QUESTION_SET && w !in ASKS_FIRST && !HudhudMind.isHardWord(w)

    /** What may stand before the marker: a calling, a polite lead-in, "can you", "from now on". */
    private fun callLead(w: String): Boolean =
        w in VOCATIVE_SET || w in FILLER_SET || w in GREETING_SET || w in CALL_LEAD || w in CALL_REST || w in DAY_WORDS ||
            pronounLike(w)

    /** What may stand after the name: a copula, "ji", "please", "hamesha", "isi naam se", "yaad rakhna", the calling verb. */
    private fun callRest(w: String): Boolean =
        w in COPULAS || w in VOCATIVE_SET || w in FILLER_SET || w in CALL_VERBS || w in CALL_REST || w in DAY_WORDS ||
            pronounLike(w)

    /** "I", "you", "this", "own", "name": the pronouns and "naam" of "isi naam se bulao". */
    private fun pronounLike(w: String): Boolean {
        val m = HudhudMind.meaningOf(w)
        return m.isNotEmpty() && m.all { it in PRONOUN_CONCEPTS }
    }

    private val PRONOUN_CONCEPTS = setOf(HudhudMind.C.YOU, HudhudMind.C.ME, HudhudMind.C.THIS, HudhudMind.C.OWN, HudhudMind.C.NAME)

    /** The first run of whole words in [low] that is one of [phrases] (longest first at each place): start and end. */
    private fun findPhrase(low: List<String>, phrases: List<List<String>>): Pair<Int, Int>? {
        for (start in low.indices) {
            for (p in phrases) {
                if (start + p.size <= low.size && p.indices.all { low[start + it] == p[it] }) return start to start + p.size
            }
        }
        return null
    }

    /** Spaces and every mark between words: a name's own letters (any script) and their signs stay together. */
    private val NAME_SPLIT = Regex("[^\\p{L}\\p{M}]+")

    private fun phrasesOf(vararg list: String): List<List<String>> = list.map { words(it) }.filter { it.isNotEmpty() }.sortedByDescending { it.size }

    private fun wordsOf(vararg list: String): Set<String> = list.flatMap { words(it) }.toSet()

    /** The words before the name: "my name is", "call me", "mera naam", "میرا نام", "اسمي", "nama saya", "panggil saya"… */
    private val CALL_ME_BEFORE: List<List<String>> by lazy {
        phrasesOf(
            "my name is", "my name", "my full name is", "my full name", "call me", "address me as", "refer to me as",
            "i am called", "i m called", "i am known as", "people call me", "everyone calls me", "they call me", "friends call me",
            "mera naam", "mera nam", "mera name", "mera poora naam", "mera pura naam", "mera puraa naam", "main ka naam",
            "mai ka naam",
            "میرا نام", "میرا پورا نام", "मेरा नाम", "मेरा पूरा नाम", "আমার নাম", "আমার পুরো নাম",
            "اسمي", "نادني", "ناديني", "تناديني", "ادعني", "سمني", "اسم من", "اسمم", "نام من", "نامم",
            "منهنجو نالو", "منھنجو نالو",
            "nama saya", "nama aku", "namaku", "nama gue", "nama gua", "panggil saya", "panggil aku", "panggil gue", "panggil gua"
        )
    }

    /** "Me", where the name follows and a calling verb comes after it: "mujhe Ali bulao", "منو علی صدا کن". */
    private val CALL_ME_TO: List<List<String>> by lazy {
        phrasesOf(
            "mujhe", "mujhko", "mujh ko", "mjhe", "muje", "mujhay", "mujhy", "mujko", "mjhko",
            "مجھے", "مجھ کو", "مجھکو", "मुझे", "मुझको", "मुझ को", "আমাকে", "আমায়",
            "منو", "مرا", "من را", "به من", "بهم", "مون کي", "مونکي", "مانکي"
        )
    }

    /** Between "mujhe" and the name: "mujhe aaj se Ali bulana", "mujhe sirf Ali kaho". */
    private val SKIP_AFTER_ME: Set<String> by lazy {
        wordsOf(
            "aaj", "ab", "se", "sirf", "bas", "hamesha", "hameshah", "always", "just", "only", "from", "now", "today", "hi", "to",
            "toh", "naam", "nam", "name",
            "آج", "اب", "سے", "صرف", "بس", "ہمیشہ", "ہی", "تو", "نام", "आज", "अब", "से", "सिर्फ", "बस", "हमेशा", "ही", "तो", "नाम",
            "আজ", "এখন", "থেকে", "শুধু", "সবসময়", "নাম", "امروز", "حالا", "از", "فقط", "همیشه", "نام", "اڄ", "هاڻي", "کان", "هميشه"
        )
    }

    /** To call somebody something, in the nine languages (a verb, in every form a voice gives). */
    private val CALL_VERBS: Set<String> by lazy {
        wordsOf(
            "bulao", "bulana", "bulaiye", "bulaiyega", "bulaye", "bulayen", "bulayein", "bulaen", "bulain", "bula", "bulaya",
            "bulaoge", "pukaro", "pukarna", "pukariye", "pukaren", "pukarein", "kaho", "kehna", "kahiye", "kahen", "kahein",
            "kehlo", "bolo", "bolna", "boliye", "bolen", "bolein",
            "بلاؤ", "بلانا", "بلائیے", "بلائیں", "بلایا", "بلاو", "پکارو", "پکارنا", "پکاریں", "پکاریے", "کہو", "کہنا", "کہیں",
            "کہیے", "بولو", "بولنا", "بولیں", "بلا", "بلاؤں",
            "बुलाओ", "बुलाना", "बुलाइए", "बुलाएं", "बुलाया", "बुला", "पुकारो", "पुकारना", "कहो", "कहना", "कहिए", "कहें", "बोलो",
            "बोलना",
            "ডাকো", "ডাকুন", "ডাকবে", "ডাকবেন", "ডেকো", "ডাকতে", "ডাক", "বলো", "বলুন", "বলবে", "বলবেন",
            "صدا", "بخوان", "بخوانید", "بگو", "بگویید", "بگین",
            "سڏيو", "سڏ", "سڏجو", "سڏيندا", "چئو", "چئجو", "سڏين"
        )
    }

    /** The doer's mark: "mujhe Ali ne bulaya" — Ali called him. */
    private val DOER_MARKS: Set<String> by lazy { wordsOf("ne", "نے", "ने", "নে") }

    /** Little words that may stand after the name (and before the marker): the do-verbs and "isi naam se". */
    private val CALL_REST: Set<String> by lazy {
        wordsOf(
            "ke", "ka", "ki", "se", "kehkar", "kehke", "bolkar", "bolke", "bulakar", "karo", "kar", "karna", "karen", "karein",
            "kare", "kariye", "kijiye", "kijiyega", "kijye", "kijyega", "kro", "krna", "lo", "lijiye", "lijye", "dena", "do",
            "de", "rakho", "rakhna", "rakhiye", "rakh", "yaad", "yad", "note", "ok", "okay", "theek", "thik", "samjhe", "samjhi",
            "samjho", "ye", "yeh", "yehi", "isi", "usi", "hi", "to", "toh", "na", "ji", "jee", "jii", "please", "plz", "pls", "ab",
            "hamesha", "always", "aaj", "now", "from", "on", "as", "set", "remember", "save", "keep", "it", "that", "thanks",
            "thank", "you", "sir", "shukriya", "shukria", "mehrbani", "meharbani", "can", "could", "would", "will", "kindly",
            "just", "only", "like", "want", "wish", "prefer", "need", "wanna", "i", "d", "ll",
            "کے", "کا", "کی", "سے", "کہہ", "کر", "کرو", "کرنا", "کریں", "کریے", "کیجیے", "کیجئے", "لو", "لیجیے", "دینا", "دو", "دے",
            "رکھو", "رکھنا", "رکھیں", "رکھیے", "یاد", "نوٹ", "ٹھیک", "سمجھے", "یہی", "اسی", "ہی", "تو", "نا", "جی", "اب", "ہمیشہ",
            "آج", "شکریہ", "مہربانی",
            "के", "का", "की", "से", "कहकर", "कर", "करो", "करना", "करें", "कीजिए", "कीजिये", "लो", "लीजिए", "देना", "दो", "दे",
            "रखो", "रखना", "रखें", "याद", "नोट", "ठीक", "समझे", "यही", "इसी", "ही", "तो", "ना", "जी", "अब", "हमेशा", "आज",
            "शुक्रिया", "मेहरबानी",
            "করো", "করুন", "রাখো", "রাখুন", "মনে", "ঠিক", "বলে", "নামে", "নাম", "এখন", "সবসময়", "ধন্যবাদ",
            "کن", "کنید", "بزن", "بزنید", "را", "هم", "ممنون", "لطفا",
            "ڪريو", "ڪر", "رکو", "ياد", "هاڻي", "هميشه", "مهرباني",
            "ya", "saja", "aja", "dong", "deh", "nih", "terima", "kasih", "tolong", "ok"
        )
    }

    /** What may stand before the marker: "can you", "please", "from now on", "I want you to". */
    private val CALL_LEAD: Set<String> by lazy {
        wordsOf(
            "can", "could", "would", "will", "you", "u", "from", "now", "on", "today", "always", "hereafter", "kindly", "just",
            "only", "i", "want", "wish", "wanna", "like", "d", "ll", "prefer", "need", "to", "that", "please", "plz", "pls", "so",
            "well", "and", "listen", "hey", "dekho", "dekhiye", "suno", "suniye", "acha", "achha", "theek", "thik", "ok", "okay",
            "toh", "to", "phir", "aur", "ji", "aap", "tum", "ab", "se", "aaj", "hamesha", "ye", "yeh", "ek", "baat", "bat",
            "يمكنك", "ان", "فضلك", "سمحت", "لو", "اب", "سے", "آج", "ہمیشہ", "سنو", "دیکھو", "اچھا", "ٹھیک", "پھر", "اور", "جی",
            "آپ", "تم", "ایک", "بات", "अब", "से", "आज", "हमेशा", "सुनो", "देखो", "अच्छा", "ठीक", "फिर", "और", "जी", "आप", "तुम",
            "एक", "बात", "এখন", "থেকে", "আজ", "শোনো", "ঠিক", "আর", "امروز", "حالا", "از", "گوش", "کن", "اڄ", "هاڻي", "کان"
        )
    }

    /** "Is", "am", "hai", "ہے", "है", "است", "آهي", "adalah": the copula between the name and what it names. */
    private val COPULAS: Set<String> by lazy {
        wordsOf(
            "is", "s", "am", "are", "hai", "he", "hy", "hain", "hoon", "hun", "hu", "ہے", "ہیں", "ہوں", "ہو", "है", "हैं", "हूं",
            "হয়", "হলো", "হল", "আছে", "است", "هست", "هستم", "آهي", "ialah", "adalah", "هو"
        )
    }

    /** Words that are no name and that no concept or list above holds: places, times, "naam" itself. */
    private val NOT_A_NAME: Set<String> by lazy {
        wordsOf(
            "later", "back", "tomorrow", "tonight", "again", "soon", "if", "at", "by", "par", "pe", "per", "pay", "bhi", "phir",
            "call", "calling", "refer",
            "bas", "sirf", "tab", "jab", "pehle", "baad", "parso", "kabhi", "never", "naam", "nam", "name", "bata", "btao", "batao",
            "samjhao", "dikha", "sunao", "suna", "pata", "baare", "bare", "about", "mutalliq", "kehta", "kehti", "kehte",
            "bolta", "bolti", "bolte", "zara", "zaroor", "jaldi", "bilkul", "sach", "jhoot", "galat", "sahi", "yeh", "woh",
            "نام", "نمبر", "پر", "پہ", "پھر", "بس", "صرف", "تب", "جب", "پہلے", "بعد", "کبھی", "بتا", "پتہ", "بارے", "زرا",
            "ضرور", "جلدی", "بالکل", "सच", "झूठ", "गलत", "सही", "नंबर", "पर", "फिर", "बस", "सिर्फ", "तब", "जब", "पहले", "बाद",
            "कभी", "बता", "पता", "बारे", "ज़रा", "जरूर", "जल्दी", "बिल्कुल"
        )
    }

    /** The question words, one by one: none of them is ever a name. */
    private val QUESTION_SET: Set<String> by lazy { QUESTION.flatMap { words(it) }.toSet() }

    // ------------------------------------------------------------------------------------------- Asked of him, 14 Nov

    /**
     * How he is asked to speak (14 Nov 2026, the owner's 1,000 sentences, "Samajh nahi aya, dobara"): the change, when
     * the sentence holds nothing but its words, his name, "his voice" and small words — "phir se Ali ka hisaab batao"
     * asks Ali's account. "Awaaz tez karo" is louder, which only the phone's own buttons can do; "tez bolo" is faster.
     */
    private fun voiceChange(said: List<String>, joined: String): VoiceChange? {
        val voiceWord = has(joined, VOICE_WORDS)
        val change = when {
            has(joined, VOICE_OFF) -> VoiceChange.OFF
            has(joined, VOICE_ON) -> VoiceChange.ON
            voiceWord && has(joined, LOUDER) -> VoiceChange.VOLUME
            has(joined, SLOWER) -> VoiceChange.SLOWER
            has(joined, FASTER) -> VoiceChange.FASTER
            has(joined, SAY_AGAIN) -> VoiceChange.AGAIN
            has(joined, BE_QUIET) -> VoiceChange.QUIET
            else -> return null
        }
        val own = (VOICE_OFF + VOICE_ON + LOUDER + SLOWER + FASTER + SAY_AGAIN + BE_QUIET + VOICE_WORDS + PLEASE_DO)
            .flatMap { words(it) }.toSet()
        return change.takeIf { said.all { it in own || it in QUIET_SET || it in VOCATIVE_SET || it in HIS_OWN_NAME } }
    }

    private val VOICE_WORDS = listOf("awaaz", "awaz", "aawaz", "awaaz ko", "voice", "sound", "volume", "آواز", "اواز", "صوت",
        "الصوت", "صدا", "صدای", "آواز را", "آواز کي", "आवाज़", "आवाज", "আওয়াজ", "আওয়াজ", "কণ্ঠ", "suara")
    private val PLEASE_DO = listOf("karo", "kar do", "kardo", "kijiye", "kariye", "karein", "please", "plz", "zara", "thora",
        "thoda", "jee", "ji", "mein", "se", "کرو", "کریں", "کیجیے", "کردو", "ذرا", "تھوڑا", "مہربانی", "کر", "من فضلك", "لو سمحت",
        "لطفا", "لطفاً", "کن", "بکن", "करो", "करें", "कीजिए", "ज़रा", "थोड़ा", "करुन", "করো", "করুন", "একটু", "tolong", "coba",
        "bolo", "bolein", "boliye", "baat", "بولو", "بولیں", "بات", "बोलो", "बोलिए", "বলো", "বলুন", "bicara", "بگو", "صحبت کن",
        "تكلم", "تحدث")
    private val VOICE_OFF = listOf("awaaz band", "awaz band", "awaaz bund", "voice off", "mute", "sound off", "awaaz khatam",
        "آواز بند", "صوت إيقاف", "اكتم الصوت", "كتم الصوت", "صدا را قطع", "بی‌صدا", "آواز بند ڪريو", "आवाज़ बंद", "आवाज बंद",
        "আওয়াজ বন্ধ", "আওয়াজ বন্ধ", "matikan suara", "suara mati", "bisukan")
    private val VOICE_ON = listOf("awaaz chalu", "awaz chalu", "awaaz on", "awaz on", "awaaz kholo", "voice on", "unmute",
        "sound on", "bol kar batao", "bol ke batao", "آواز کھولو", "آواز چلاؤ", "آواز آن", "شغل الصوت", "صدا را روشن",
        "आवाज़ चालू", "आवाज चालू", "আওয়াজ চালু", "আওয়াজ চালু", "nyalakan suara", "suara nyala")
    private val LOUDER = listOf("tez", "tez karo", "oonchi", "unchi", "buland", "zor se", "louder", "loud", "dheemi", "halki",
        "kam karo", "تیز", "اونچی", "بلند", "ارفع", "أعلى", "بلندتر", "تيز", "तेज़", "तेज", "ऊंची", "জোরে", "বাড়াও", "keras")
    private val SLOWER = listOf("ahista", "aahista", "ahesta", "ahista bolo", "dheere", "dhire", "araam se bolo", "aram se bolo",
        "slow", "slowly", "slower", "speak slowly", "آہستہ", "آهستہ", "آرام سے بولو", "ببطء", "أبطأ", "آهسته", "آهسته‌تر",
        "آهستي", "धीरे", "धीमे", "आराम से बोलो", "আস্তে", "ধীরে", "pelan", "pelan pelan", "lebih pelan", "perlahan")
    private val FASTER = listOf("tez bolo", "jaldi bolo", "jaldi jaldi", "faster", "speak faster", "quicker", "تیز بولو",
        "جلدی بولو", "أسرع", "بسرعة", "سریع‌تر", "تندتر", "تيز ڳالهايو", "तेज़ बोलो", "तेज बोलो", "जल्दी बोलो", "দ্রুত বলো",
        "তাড়াতাড়ি বলো", "lebih cepat", "cepat")
    private val SAY_AGAIN = listOf("phir se", "phir", "dobara", "dubara", "do bara", "ek baar phir", "ek dafa phir", "phir batao",
        "wohi sawal dobara", "wohi sawal", "repeat", "say again", "say it again", "again", "once more", "one more time",
        "samajh nahi aya", "samajh nahi aaya", "samajh nahin aaya", "samjha nahi", "didn't understand", "did not understand",
        "batao", "sunao", "kaho", "پھر سے", "پھر", "دوبارہ", "ایک بار پھر", "سمجھ نہیں آیا", "سمجھ نہیں آئی", "بتاؤ",
        "بتائیں", "سناؤ", "سنائیں", "کہو", "مرة أخرى", "مرة ثانية", "أعد", "كرر", "لم أفهم", "ما فهمت", "دوباره", "یک بار دیگر",
        "تکرار کن", "نفهمیدم", "ٻيهر", "وري", "سمجهه ۾ نه آيو", "ٻڌايو", "फिर से", "फिर", "दोबारा", "एक बार फिर", "समझ नहीं आया",
        "बताओ", "सुनाओ", "আবার", "আরেকবার", "বুঝিনি", "বুঝতে পারিনি", "বলো", "ulangi", "sekali lagi", "tidak paham",
        "nggak ngerti", "lagi")
    private val BE_QUIET = listOf("chup", "chup ho jao", "chup karo", "chupp", "khamosh", "bas karo", "bas", "rehne do",
        "rehnay do", "chhodo", "chodo", "stop", "be quiet", "stop talking", "enough", "never mind", "چپ", "چپ ہو جاؤ", "خاموش",
        "بس کرو", "بس", "رہنے دو", "چھوڑو", "اسكت", "توقف", "كفى", "خلاص", "ساکت", "بسه", "ول کن", "خاموش ٿي وڃو", "بس ڪريو",
        "چپ ٿي وڃ", "चुप", "चुप हो जाओ", "बस करो", "बस", "रहने दो", "छोड़ो", "চুপ", "থামো", "থাক", "ব্যস", "diam", "berhenti",
        "sudah", "cukup")

    /**
     * The book's features named by their own word (14 Nov 2026, the owner's 1,000 sentences: "Zakat ka hisaab karo" was
     * read as tickets, "Doosri dukan kholo" as the shop's profile, "Bill ka PDF banao" as a count of supplier bills).
     * The pages Hudhud has no figure for open through their help page, which says what the screen does and opens it —
     * only a page this book offers ([topics]). Qarz-e-Hasna, the best sellers and the automations he answers himself.
     */
    private fun byFeature(
        said: List<String>, joined: String, m: HudhudMind.Read, window: VoiceQuestion.Window?, topics: List<Topic>
    ): Ask? {
        fun page(key: String): Ask? = if (topics.any { it.key == key }) Ask.Help(key) else null
        // A concept said by its own word, not met by sound: "karna" sounds like Indonesian "karyawan" (staff).
        fun firm(c: HudhudMind.C) = m.said.indices.any { it !in m.bySound && c in m.at(it) }
        val counting = m.has(HudhudMind.C.HOW_MANY)
        if (has(joined, QARZ)) return Ask.Qarz
        if (has(joined, ZAKAT)) page("screen_zakat")?.let { return it }
        if (has(joined, AUTOMATION)) return Ask.Automations
        if (has(joined, BUSINESSES)) page("faq_business")?.let { return it }
        if (has(joined, SEASONS)) page("screen_seasons")?.let { return it }
        if (has(joined, SCHEMES)) page("screen_schemes")?.let { return it }
        if (has(joined, POSTERS)) page("screen_posters")?.let { return it }
        if (has(joined, CARDS)) {
            page(if (m.has(HudhudMind.C.CUSTOMER)) "intro_customer_qr" else "screen_card")?.let { return it }
        }
        val expiry = VoiceQuestion.classify(joined) == VoiceQuestion.Kind.EXPIRING
        if (has(joined, CASH_FLOW) || (has(joined, AHEAD) && !expiry && !has(joined, CAME_ALREADY) &&
                !m.any(HudhudMind.C.STOCK, HudhudMind.C.LEFT, HudhudMind.C.CHEQUE, HudhudMind.C.PLAN))
        ) {
            page("screen_cashflow")?.let { return it }
        }
        val billWord = has(joined, BILL_WORDS)
        if (!counting && (has(joined, DOCUMENTS) || (billWord && has(joined, MAKE_DOC) && !has(joined, UTILITY) &&
                !has(joined, SUPPLIER_SIDE_WORDS)))
        ) page("intro_documents")?.let { return it }
        if (has(joined, UTILITY)) page("intro_cashbook")?.let { return it }
        // Keeping the book safe (14 Nov 2026, the owner's 1,000 sentences: "PIN bhool gaya" was read as somebody's
        // details, "Naya PIN banana hai" as a hello): the lock's and the hiding's own help pages.
        if (has(joined, LOCK_WORDS)) page("faq_lock")?.let { return it }
        if (has(joined, HIDE_WORDS)) page("faq_hide")?.let { return it }
        if (has(joined, DATA_LEAVES)) return Ask.About(AboutTopic.PRIVACY)
        if (has(joined, SAFETY_WORDS) && !m.has(HudhudMind.C.DATA) && VoiceQuestion.classify(joined) == null) {
            page("faq_lock")?.let { return it }
        }
        // Something new to put in the book ("naya mulazim add karna hai", "naya bill daalna hai"): the page of that
        // feature, which says how. Without this the help search took "add" to the page about adding a business.
        if (has(joined, ADD_NEW) && !counting && VoiceQuestion.classify(joined) == null && !m.has(HudhudMind.C.CAME) &&
            !has(joined, GOODS_CAME)
        ) {
            val key = when {
                billWord || m.has(HudhudMind.C.BILL) -> "screen_bills"
                firm(HudhudMind.C.STAFF) -> "intro_staff"
                m.has(HudhudMind.C.CHEQUE) -> "intro_cheques"
                m.has(HudhudMind.C.PLAN) -> "intro_plans"
                m.has(HudhudMind.C.GOODS) -> "screen_products"
                m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER, HudhudMind.C.ENTRY) -> "screen_khata"
                else -> null
            }
            key?.let { page(it) }?.let { return it }
        }
        // The staff by name of the feature, nobody named: what they are owed, or how to add one and what they see.
        if (firm(HudhudMind.C.STAFF) && !counting && VoiceQuestion.classify(joined) == null &&
            !m.any(HudhudMind.C.TICKET, HudhudMind.C.ENTRY, HudhudMind.C.HAPPENED)
        ) {
            if (has(joined, ACCESS_WORDS)) page("intro_staff")?.let { return it }
            if (m.any(HudhudMind.C.ACCOUNT, HudhudMind.C.PENDING) || has(joined, ACCOUNT_OF_DAY)) {
                return Ask.Status(StatusOf.STAFF)
            }
        }
        // Putting an entry right, or bringing back what was deleted: he cannot do it himself, the help page says how.
        if (has(joined, BIN_WORDS)) page("screen_bin")?.let { return it }
        if (has(joined, FIX_WORDS) && (m.any(HudhudMind.C.ENTRY, HudhudMind.C.CUSTOMER) || has(joined, FIX_WHAT))) {
            page("faq_delete")?.let { return it }
        }
        // "Aaj ka khata", "aaj ka hisaab kitab", "aaj ka din kaisa raha": the day's own summary, not the whole book.
        if ((window == VoiceQuestion.Window.TODAY && has(joined, ACCOUNT_OF_DAY) && VoiceQuestion.classify(joined) == null &&
                !m.any(HudhudMind.C.WHO, HudhudMind.C.ENTRY, HudhudMind.C.CHEQUE, HudhudMind.C.CAME)) ||
            has(joined, DAY_WENT)
        ) return Ask.Today
        // Someone who does not yet know his way ("mujhe samajh nahi aa raha", "pehle kya seekhun", "madad chahiye"):
        // what he can do, as a list to tap.
        if (has(joined, GUIDE_ME)) return Ask.Abilities
        // Goods the shop bought (14 Nov 2026, the owner's screenshot: "آج میں نے کس سے سامان خریدا" opened the purchase
        // orders' help): what came in on the suppliers' bills, over the days asked.
        if ((has(joined, BOUGHT) && (has(joined, I_WE) || m.has(HudhudMind.C.FROM_WHOM) || has(joined, SUPPLIER_SIDE_WORDS))) ||
            (has(joined, SUPPLIER_SIDE_WORDS) && has(joined, GOODS_CAME))
        ) return Ask.Deliveries(window)
        if (has(joined, BEST_SELLING) || (has(joined, MOST) && has(joined, SELLING)) || (has(joined, WHAT_ALL) && has(joined, SELLING))) {
            return Ask.TopGoods(window)
        }
        if (window != null && m.has(HudhudMind.C.CREDIT) && !has(joined, PAID_BACK) && !has(joined, UNTIL_NOW) &&
            (has(joined, TOOK_OR_GAVE) || window == VoiceQuestion.Window.TODAY)
        ) {
            return Ask.Activity(window)
        }
        if (has(joined, SOME_GOODS) && VoiceQuestion.classify(joined) == null) {
            val facet = when {
                m.has(HudhudMind.C.PRICE) -> GoodsFacet.PRICE
                m.has(HudhudMind.C.COST) -> GoodsFacet.COST
                m.any(HudhudMind.C.STOCK, HudhudMind.C.LEFT) -> GoodsFacet.STOCK
                m.has(HudhudMind.C.SUPPLIER) -> GoodsFacet.SUPPLIERS
                m.any(HudhudMind.C.BOUGHT, HudhudMind.C.FROM_WHOM, HudhudMind.C.CUSTOMER, HudhudMind.C.WHO) -> GoodsFacet.BUYERS
                else -> null
            }
            if (facet != null) return Ask.NeedGoods(facet)
        }
        return null
    }

    /** Whether [heard] names one of the features [byFeature] opens by its page, or asks to add something new to one. */
    private fun featureNamed(heard: String): Boolean {
        val joined = words(heard).joinToString(" ")
        return listOf(ZAKAT, BUSINESSES, SEASONS, SCHEMES, POSTERS, CARDS, CASH_FLOW, AHEAD, DOCUMENTS, UTILITY, BIN_WORDS, FIX_WORDS,
            LOCK_WORDS, HIDE_WORDS, ADD_NEW)
            .any { has(joined, it) } ||
            (has(joined, BILL_WORDS) && has(joined, MAKE_DOC))
    }

    private val LOCK_WORDS = listOf("lock", "app lock", "applock", "pin", "pin code", "password", "passcode", "fingerprint",
        "finger print", "screen lock", "taala", "tala lagao", "لاک", "تالا", "پن", "پن کوڈ", "پاس ورڈ", "فنگر پرنٹ", "قفل",
        "رمز", "رمز المرور", "كلمة المرور", "بصمة", "بصمة الإصبع", "گذرواژه", "رمز عبور", "اثر انگشت", "لاڪ", "پاسورڊ", "ताला",
        "लॉक", "पिन", "पासवर्ड", "फिंगरप्रिंट", "লক", "পিন", "পাসওয়ার্ড", "আঙুলের ছাপ", "kunci", "kata sandi", "sandi",
        "sidik jari")
    private val HIDE_WORDS = listOf("chhupa", "chupa", "chhupana", "chupana", "chhupao", "chupao", "chhup", "hide", "hidden",
        "screenshot", "screen shot", "چھپا", "چھپانا", "چھپاؤ", "سکرین شاٹ", "اسکرین شاٹ", "إخفاء", "اخفاء", "أخفي", "لقطة الشاشة",
        "پنهان", "مخفی", "اسکرین‌شات", "لڪاءِ", "لڪايو", "छुपा", "छुपाना", "छिपा", "छिपाना", "स्क्रीनशॉट", "লুকাও", "লুকানো",
        "লুকিয়ে", "স্ক্রিনশট", "sembunyikan", "sembunyi", "tangkapan layar")
    private val SAFETY_WORDS = listOf("safe", "safety", "secure", "security", "mehfooz", "mahfooz", "mehfuz", "hifazat",
        "hifazat karun", "محفوظ", "حفاظت", "آمن", "أمان", "الأمان", "امنیت", "ایمن", "سلامت", "حفاظت", "सुरक्षित", "सुरक्षा",
        "নিরাপদ", "নিরাপত্তা", "aman", "keamanan")
    /** Whether the book leaves the phone: the privacy answer, not a help page. */
    private val DATA_LEAVES = listOf("internet par jata", "internet pe jata", "online jata", "server par", "bahar jata",
        "kisi ko dikhta", "share hota", "share karta", "kahan jata", "upload hota", "انٹرنیٹ پر جاتا", "باہر جاتا", "کسی کو دکھتا",
        "يذهب إلى الإنترنت", "يرسل", "به اینترنت می‌رود", "ارسال می‌شود", "इंटरनेट पर जाता", "बाहर जाता", "ইন্টারনেটে যায়",
        "বাইরে যায়", "dikirim ke internet", "dibagikan")
    private val ADD_NEW = listOf("naya", "nayi", "naye", "new", "add", "add karna", "add karo", "shamil", "darj karna", "daalna",
        "dalna", "نیا", "نئی", "نئے", "شامل", "ایڈ", "جديد", "جديدة", "أضف", "إضافة", "جدید", "اضافه", "افزودن", "نئون", "نئين",
        "شامل ڪريو", "नया", "नई", "नए", "जोड़ना", "जोड़ो", "ऐड", "নতুন", "যোগ", "যোগ করুন", "baru", "tambah", "tambahkan")
    /** What a member of staff may see or do. */
    private val ACCESS_WORDS = listOf("access", "ijazat", "ikhtiyar", "permission", "kya kya dikhe", "kya dikhta", "kya dekh",
        "role", "اجازت", "اختیار", "رسائی", "صلاحية", "صلاحيات", "دسترسی", "اجازه", "اجازت", "अनुमति", "पहुँच", "অনুমতি",
        "অ্যাক্সেস", "akses", "izin")
    private val QARZ = listOf("qarz e hasna", "qarze hasna", "qarz hasna", "qarzehasna", "qarz e hasana", "qarz e husna",
        "karz e hasna", "qarz-e-hasna", "قرض حسنہ", "قرضِ حسنہ", "قرض حسنه", "قرض الحسن", "قرضا حسنا", "قرض حسن",
        "قرض‌الحسنه", "قرض الحسنه", "قرض حسنو", "क़र्ज़ ए हसना", "कर्ज़ ए हसना", "कर्ज ए हसना", "क़र्ज़-ए-हसना", "করজে হাসানা",
        "কর্জে হাসানা", "কর্জে হাসানাহ", "qardhul hasan", "qardh hasan", "pinjaman kebajikan")
    private val ZAKAT = listOf("zakat", "zakaat", "zakath", "zakāt", "nisab", "nisaab", "زکوٰۃ", "زکوۃ", "زکات", "زكاة",
        "الزكاة", "زکوات", "زڪوات", "نصاب", "النصاب", "ज़कात", "जकात", "निसाब", "যাকাত", "জাকাত", "নিসাব", "zakat mal")
    private val AUTOMATION = listOf("automation", "automations", "automatic", "khud ba khud", "khudkar", "آٹومیشن",
        "آٹومیشنز", "خودکار", "أتمتة", "تلقائي", "اتوماسیون", "آٽوميشن", "ऑटोमेशन", "স্বয়ংক্রিয়", "অটোমেশন", "otomatis",
        "otomasi")
    private val BUSINESSES = listOf("doosri dukan", "dusri dukan", "doosre karobar", "dono dukan", "dono dukanon",
        "dukanon", "dukaanon", "har dukan", "pehli dukan", "ek dukan", "kisi aur dukan", "naye shehar", "naya karobar",
        "karobar add", "karobar badlo", "karobar badalna", "karobar select", "karobar ka naam", "kaun sa karobar",
        "dukan badlo", "dukan badalni", "ghar ka khata", "ghar ka kharcha alag", "another shop", "other shop", "both shops",
        "second shop", "switch shop", "new business", "دوسری دکان", "دونوں دکان", "دکانوں", "نیا کاروبار", "کاروبار بدلو",
        "دکان بدلو", "متجر آخر", "المتاجر", "نشاط جديد", "مغازه دیگر", "کسب‌وکار جدید", "مغازه‌ها", "ٻيو دڪان", "دڪانن",
        "दूसरी दुकान", "दोनों दुकान", "दुकानों", "नया कारोबार", "अन्य दोकान", "অন্য দোকান", "দুই দোকান", "দোকানগুলো",
        "নতুন ব্যবসা", "toko lain", "toko kedua", "usaha baru", "ganti toko")
    private val SEASONS = listOf("season", "seasons", "fasal", "fasl", "faslon", "kharif", "khareef", "rabi", "rabbi",
        "harvest", "فصل", "فصلوں", "خریف", "ربیع", "سیزن", "خريف", "ربيع", "موسم الحصاد", "سيزن", "سیزن", "فصل", "खरीफ",
        "रबी", "फसल", "सीज़न", "सीजन", "ফসল", "মৌসুম", "খরিফ", "রবি", "musim", "panen", "musim tanam")
    private val SCHEMES = listOf("scheme", "schemes", "skeem", "iskeem", "اسکیم", "سکیم", "اسکیمیں", "اسڪيم", "عرض الشركة",
        "طرح فروش", "स्कीम", "স্কিম", "skema", "skema promo")
    private val POSTERS = listOf("poster", "posters", "ishtehar", "ishtihar", "اشتہار", "پوسٹر", "پوستر", "ملصق", "पोस्टर",
        "পোস্টার", "spanduk", "brosur")
    private val CARDS = listOf("card", "cards", "visiting card", "business card", "dukan card", "کارڈ", "کارت", "کارت ویزیت",
        "بطاقة", "بطاقة عمل", "ڪارڊ", "कार्ड", "विजिटिंग कार्ड", "কার্ড", "ভিজিটিং কার্ড", "kartu", "kartu nama")
    private val CASH_FLOW = listOf("cash flow", "cashflow", "andaza", "andaaza", "forecast", "tangi", "kam padenge",
        "kam pad jayenge", "aana jana", "کیش فلو", "اندازہ", "تنگی", "التدفق النقدي", "توقع", "جریان نقدی", "پیش‌بینی",
        "انداز", "कैश फ्लो", "अंदाज़ा", "पूर्वानुमान", "ক্যাশ ফ্লো", "পূর্বাভাস", "arus kas", "perkiraan")
    private val AHEAD = listOf("agle", "agla", "agli", "aainda", "ainda", "aane wale", "aane wali", "aane wala", "anay walay",
        "next", "coming", "upcoming", "اگلے", "اگلا", "اگلی", "آئندہ", "آنے والے", "آنے والی", "القادم", "القادمة", "المقبل",
        "المقبلة", "آینده", "بعدی", "ايندڙ", "ايندڙ", "अगले", "अगला", "आने वाले", "आगामी", "আগামী", "সামনের", "berikutnya",
        "mendatang", "depan")
    private val DOCUMENTS = listOf("invoice", "invoices", "quotation", "challan", "proforma", "انوائس", "کوٹیشن", "چالان",
        "فاتورة", "عرض سعر", "فاکتور", "پیش‌فاکتور", "انوائيس", "इनवॉइस", "कोटेशन", "চালান", "ইনভয়েস", "কোটেশন", "faktur",
        "penawaran")
    private val BILL_WORDS = listOf("bill", "bills", "bil", "بل", "بِل", "بلز", "بلوں", "صورتحساب", "بل جو", "बिल", "বিল",
        "nota", "tagihan")
    /** "Aaj tak", "ab tak": until now — the whole book, not a day. */
    private val UNTIL_NOW = listOf("aaj tak", "ab tak", "abhi tak", "till now", "so far", "until now", "آج تک", "اب تک",
        "حتى الآن", "تا امروز", "تا حالا", "اڄ تائين", "आज तक", "अब तक", "আজ পর্যন্ত", "এখন পর্যন্ত", "sampai hari ini",
        "sejauh ini")
    private val BIN_WORDS = listOf("recycle bin", "bin kholo", "bin", "delete hui", "delete kiya hua", "mit gaya", "mit gayi",
        "ghalti se mit", "wapas lana", "wapas laao", "restore", "undelete", "ری سائیکل بن", "بن کھولو", "واپس لانا", "سلة المحذوفات",
        "استرجاع", "سطل بازیافت", "بازگردانی", "रीसायकल बिन", "वापस लाना", "রিসাইকেল বিন", "ফিরিয়ে আনা", "tempat sampah", "pulihkan")
    private val FIX_WORDS = listOf("delete", "hata do", "hatao", "mita do", "mitao", "galat", "ghalat", "theek karni", "theek karna",
        "sahi karni", "badalni", "badalna", "badlo", "undo", "edit", "correct", "wrong", "ڈیلیٹ", "ہٹا دو", "مٹا دو", "غلط",
        "درست کرنی", "ٹھیک کرنی", "بدلنی", "حذف", "احذف", "خطأ", "تعديل", "اصلاح", "ویرایش", "اشتباه", "غلط ٿي", "ڊليٽ", "हटा दो",
        "गलत", "ग़लत", "ठीक करनी", "बदलनी", "डिलीट", "মুছে", "মুছুন", "ভুল", "ঠিক করতে", "বদলাতে", "hapus", "salah", "ubah",
        "perbaiki")
    private val FIX_WHAT = listOf("raqam", "amount", "aadmi", "tareekh", "date", "entry", "indiraaj", "khata", "رقم", "اندراج",
        "تاریخ", "کھاتہ", "قيد", "مبلغ", "ثبت", "مبلغ", "रकम", "तारीख", "एंट्री", "টাকা", "তারিখ", "এন্ট্রি", "jumlah", "tanggal",
        "catatan")
    private val ACCOUNT_OF_DAY = listOf("khata", "khaata", "hisaab", "hisab", "hisaab kitab", "bahi", "bahi khata", "lena dena",
        "len den", "roznamcha", "summary", "khulasa", "account", "کھاتہ", "کھاتا", "حساب", "حساب کتاب", "لین دین",
        "روزنامچہ", "خلاصہ", "الحساب", "ملخص", "حساب‌ها", "کاتو", "حساب ڪتاب", "खाता", "हिसाब", "लेन देन",
        "बही", "খাতা", "হিসাব", "লেনদেন", "buku", "ringkasan")
    private val DAY_WENT = listOf("din kaisa raha", "din kesa raha", "aaj kaisa raha", "aaj ka din kaisa", "how was today",
        "how did today go", "دن کیسا رہا", "آج کیسا رہا", "كيف كان اليوم", "امروز چطور بود", "ڏينهن ڪيئن رهيو", "दिन कैसा रहा",
        "আজকের দিন কেমন", "hari ini bagaimana")
    private val GUIDE_ME = listOf("samajh nahi aa raha", "samajh nahin aa raha", "samajh nahi aata", "kya seekhun",
        "pehle kya seekhun", "pehle kya karun", "sab features", "features batao", "training", "sikhao", "sikha do",
        "button kahan", "madad chahiye", "help me", "how to use", "kaise chalaun", "kaise use karun", "guide me",
        "سمجھ نہیں آ رہا", "سکھاؤ", "سکھا دو", "رہنمائی", "مدد چاہیے", "لا أفهم", "ساعدني", "علمني", "متوجه نمی‌شوم",
        "راهنمایی", "کمکم کن", "سمجهه ۾ نٿو اچي", "سيکاريو", "मदद चाहिए", "समझ नहीं आ रहा", "सिखाओ", "বুঝতে পারছি না",
        "শেখাও", "সাহায্য দরকার", "tidak mengerti", "ajari", "bantu saya")
    private val BOUGHT = listOf("khareeda", "kharida", "khareedi", "kharidi", "khareede", "kharide", "khareeday", "kharid kiya",
        "bought", "purchased", "purchase kiya", "mangwaya", "mangwayi", "mangwaye", "خریدا", "خریدی", "خریدے", "منگوایا",
        "منگوائی", "اشتريت", "اشترينا", "شريت", "خریدم", "خریدیم", "خرید کردم", "خريد ڪيو", "ورتو", "खरीदा", "खरीदी", "खरीदे",
        "मंगवाया", "কিনেছি", "কিনলাম", "কিনেছিলাম", "membeli", "beli", "dibeli", "kubeli")
    private val I_WE = listOf("maine", "main ne", "mein ne", "mene", "hum ne", "humne", "hamne", "میں نے", "ہم نے", "i", "we",
        "मैंने", "मैने", "हमने", "আমি", "আমরা", "saya", "aku", "kami", "من", "ما", "أنا", "نحن", "مون", "اسان")
    private val GOODS_CAME = listOf("maal liya", "maal aaya", "maal aya", "maal bheja", "saman aaya", "saman aya", "maal mila",
        "goods came", "stock came", "مال لیا", "مال آیا", "سامان آیا", "مال بھیجا", "माल लिया", "माल आया", "সামগ্রী এসেছে",
        "মাল এসেছে", "barang datang", "barang masuk")
    private val SUPPLIER_SIDE_WORDS = listOf("supplier", "suppliers", "company", "dealer", "mill", "maal wale", "maal wala",
        "سپلائر", "کمپنی", "ڈیلر", "المورد", "الشركة", "تامین‌کننده", "شرکت", "سپلائر", "सप्लायर", "कंपनी", "সরবরাহকারী",
        "কোম্পানি", "pemasok", "perusahaan")
    private val CAME_ALREADY = listOf("aaye", "aye", "aaya", "aya", "aai", "aayi", "mile", "mila", "came", "آئے", "آیا", "ملے",
        "आए", "आया", "মিলেছে", "এসেছে", "datang")
    private val MAKE_DOC = listOf("banana", "banao", "bana do", "banaun", "banayen", "bhejo", "bhejna", "bhej do", "pdf",
        "print", "copy", "naqal", "cancel", "design", "template", "letterhead", "muhar", "stamp", "make", "create", "send",
        "بناؤ", "بنانا", "بنائیں", "بھیجو", "بھیجنا", "پرنٹ", "اعمل", "أرسل", "بساز", "بفرست", "بڻايو", "موڪليو", "बनाओ",
        "बनाना", "भेजो", "प्रिंट", "বানাও", "তৈরি", "পাঠাও", "প্রিন্ট", "buat", "kirim", "cetak")
    private val UTILITY = listOf("bijli", "bijlee", "gas ka", "gas bill", "pani ka bill", "internet ka bill", "internet bill", "kiraya", "kiraaya",
        "kiraye", "rent", "electricity", "بجلی", "گیس", "کرایہ", "کرایا", "الكهرباء", "الإيجار", "برق", "گاز", "اجاره",
        "بجلي", "ڪرايو", "बिजली", "गैस", "किराया", "বিদ্যুৎ", "গ্যাস", "ভাড়া", "listrik", "sewa")
    private val BEST_SELLING = listOf("top selling", "best selling", "bestseller", "best seller", "top item", "top items",
        "tez chal raha", "zyada bikti", "zyada bikta", "zyada bik raha", "zyada bik rahi", "سب سے زیادہ بکنے", "زیادہ بکتا",
        "الأكثر مبيعا", "الأكثر مبيعاً", "پرفروش", "سڀ کان وڌيڪ وڪرو", "सबसे ज़्यादा बिकने", "सबसे ज्यादा बिकने", "সবচেয়ে বেশি বিক্রি",
        "terlaris", "paling laku")
    private val MOST = listOf("sab se zyada", "sabse zyada", "sab se ziada", "most", "top", "سب سے زیادہ", "الأكثر", "بیشترین",
        "سڀ کان وڌيڪ", "सबसे ज़्यादा", "सबसे ज्यादा", "সবচেয়ে বেশি", "paling banyak", "paling")
    private val SELLING = listOf("bik", "bikta", "bikti", "bika", "biki", "bik raha", "bikri", "sale", "sales", "becha",
        "bechi", "sold", "selling", "sell", "بکا", "بکی", "بکتا", "بکتی", "فروخت", "بيع", "مبيعا", "مبيعاً", "فروش", "فروخته",
        "وڪرو", "बिका", "बिकी", "बिकता", "बिक्री", "বিক্রি", "terjual", "laku")
    private val WHAT_ALL = listOf("kya kya", "kaun kaun", "what all", "کیا کیا", "کون کون", "ماذا", "چه چیزهایی", "ڇا ڇا",
        "क्या क्या", "কী কী", "apa saja")
    private val TOOK_OR_GAVE = listOf("liya", "liye", "li", "diya", "diye", "di", "gaya", "gaye", "hua", "hue", "chadha",
        "barha", "utha", "took", "gave", "given", "taken", "لیا", "لی", "دیا", "دی", "گیا", "ہوا", "چڑھا", "أخذ", "أعطى",
        "گرفت", "داد", "ورتو", "ڏنو", "लिया", "दिया", "गया", "हुआ", "নিল", "নিয়েছে", "দিল", "দিয়েছি", "হয়েছে", "ambil",
        "diambil", "kasih", "beri")
    private val PAID_BACK = listOf("utara", "utaara", "chukaya", "chuka diya", "wapas", "jama", "paid back", "repaid",
        "اتارا", "چکایا", "واپس", "جمع", "سدد", "پرداخت", "ادا", "उतारा", "चुकाया", "वापस", "জমা", "শোধ", "dibayar", "lunas")
    private val SOME_GOODS = listOf("ye cheez", "yeh cheez", "is cheez", "iss cheez", "is item", "ye item", "yeh item",
        "is maal", "ye maal", "is dawai", "ye dawai", "is product", "ye product", "this item", "this product", "khad", "dawai",
        "beej", "spray", "is cheez ka", "یہ چیز", "اس چیز", "اس آئٹم", "یہ مال", "اس مال", "کھاد", "دوائی", "بیج", "سپرے",
        "هذا المنتج", "هذه السلعة", "این کالا", "این جنس", "اهو سامان", "هي شيءِ", "यह चीज़", "इस चीज़", "इस आइटम", "खाद", "दवाई",
        "बीज", "এই জিনিস", "এই পণ্য", "সার", "ওষুধ", "বীজ", "barang ini", "produk ini", "pupuk", "obat", "benih")

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
    private fun aboutHim(m: HudhudMind.Read, joined: String, known: (String) -> Boolean = { false }): Ask? {
        val salaam = has(joined, SALAAM)
        fun only(vararg cs: HudhudMind.C) = m.onlyOf(cs.toSet(), ::quiet)
        // The owner's own name asked with a word or two he does not know in it ("mera naam kya ha", "aap mera naam jante
        // ho"; a recogniser's slip): up to two such words are let by, never one that is a name of the book.
        fun onlyOrBare(vararg cs: HudhudMind.C): Boolean {
            val allowed = cs.toSet()
            var bare = 0
            for (i in m.said.indices) {
                val w = m.said[i]
                if (quiet(w) || m.at(i).any { it in allowed }) continue
                if (m.at(i).isEmpty() && !known(w) && ++bare <= 2) continue
                return false
            }
            return true
        }
        val you = m.any(HudhudMind.C.YOU, HudhudMind.C.THIS, HudhudMind.C.OWN, HudhudMind.C.APP)
        // "Hudhud", or a hello with nothing else: he answers with what he can do.
        if (m.all == setOf(HudhudMind.C.YOU) && only(HudhudMind.C.YOU) && m.said.size <= 3) return when {
            salaam -> Ask.Salaam
            m.said.any { it in GREETING_SET } -> Ask.Hello
            // Only called by his name: "ji, haazir hoon" (13 Nov 2026).
            else -> Ask.Chat(Talk.ADDRESS)
        }
        // "Why is your name Hudhud?", "Hudhud kyun?" (14 Nov 2026, the owner): the story of his name — a why with his
        // name, or with "your name"; "customer ka naam kyun nahi aa raha" names nobody's name of his and goes on.
        val calledByName = m.said.any { it in HIS_OWN_NAME }
        if (has(joined, WHY) && (calledByName || (m.has(HudhudMind.C.YOU) && m.has(HudhudMind.C.NAME))) &&
            !m.any(HudhudMind.C.MADE, HudhudMind.C.MAKER) && !mineIsNearer(m)
        ) return Ask.About(AboutTopic.NAME_STORY)
        // "Whose app is this", "is app ka malik kaun hai", "who owns this app" (14 Nov 2026, the owner: Hudhud himself says who the
        // developer is, so that anyone who doubts it hears it from him): the app's maker — never the shop's owner ("dukaan ka malik"
        // has no "app" in it) and never a question about him ("whose app is this" was read as "who are you").
        // Persian's "مال کیست" holds "مال", which is also "goods": the phrase itself says whose.
        if ((m.has(HudhudMind.C.APP) || has(joined, listOf("برنامه", "برنامہ"))) && has(joined, WHOSE_APP) &&
            (has(joined, listOf("مال کیست", "مال کیه")) ||
                onlyOrBare(HudhudMind.C.APP, HudhudMind.C.THIS, HudhudMind.C.WHO, HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.NAME,
                    HudhudMind.C.MAKER, HudhudMind.C.YOU, HudhudMind.C.OWN)) && !mineIsNearer(m)
        ) return Ask.About(AboutTopic.MAKER)
        if ((you || m.has(HudhudMind.C.ME)) && m.any(HudhudMind.C.NAME, HudhudMind.C.WHO) && !m.any(HudhudMind.C.MADE, HudhudMind.C.MAKER) &&
            (only(HudhudMind.C.YOU, HudhudMind.C.THIS, HudhudMind.C.OWN, HudhudMind.C.APP, HudhudMind.C.ME, HudhudMind.C.NAME,
                HudhudMind.C.WHO, HudhudMind.C.WHAT, HudhudMind.C.TELL, HudhudMind.C.KNOW, HudhudMind.C.ABLE) ||
                (m.has(HudhudMind.C.ME) && m.has(HudhudMind.C.NAME) && onlyOrBare(HudhudMind.C.YOU, HudhudMind.C.THIS, HudhudMind.C.OWN,
                    HudhudMind.C.APP, HudhudMind.C.ME, HudhudMind.C.NAME, HudhudMind.C.WHO, HudhudMind.C.WHAT, HudhudMind.C.TELL,
                    HudhudMind.C.KNOW, HudhudMind.C.ABLE)))
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

    /** "Why", in the nine languages: the story of his name ([aboutHim]). */
    private val WHY = listOf(
        "kyun", "kyon", "kyu", "kiyon", "kiun", "kis liye", "kisliye", "kis wajah", "why", "how come",
        "کیوں", "کس لیے", "کس وجہ", "ڇو", "ڇالاءِ", "لماذا", "لما", "ليش", "چرا", "برای چه", "क्यों", "किस लिए", "কেন",
        "kenapa", "mengapa"
    )

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
        ) return Ask.NeedName(if (m.has(HudhudMind.C.PHONE)) Facet.PHONE else Facet.DETAILS)
        when {
            m.has(HudhudMind.C.OWNER) && m.has(HudhudMind.C.WHO) && m.has(HudhudMind.C.SHOP) -> return Ask.About(AboutTopic.OWNER_NAME)
            m.has(HudhudMind.C.EMAIL) && !his -> return Ask.Shop(ShopFacet.EMAIL)
            m.has(HudhudMind.C.TAX) && !m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.INVOICE) -> return Ask.Shop(ShopFacet.TAX)
            m.has(HudhudMind.C.BANK) && !his -> return Ask.Shop(ShopFacet.BANK)
            m.has(HudhudMind.C.CURRENCY) -> return Ask.Shop(ShopFacet.CURRENCY)
            m.has(HudhudMind.C.COUNTRY) && !his -> return Ask.Shop(ShopFacet.COUNTRY)
            m.has(HudhudMind.C.SHOP) && m.has(HudhudMind.C.TRADE) -> return Ask.Shop(ShopFacet.TRADE)
            // "Mera naam" is the owner's own name, "dukaan ka naam" the shop's (14 Nov 2026, the owner's check logs: "mera naam
            // kya hai" with a word he did not know in it answered the shop's name).
            m.has(HudhudMind.C.NAME) && m.has(HudhudMind.C.ME) && !m.any(HudhudMind.C.SHOP, HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER,
                HudhudMind.C.GOODS, HudhudMind.C.STAFF) -> return Ask.About(AboutTopic.OWNER_NAME)
            mine && m.has(HudhudMind.C.NAME) -> return Ask.Shop(ShopFacet.NAME)
            m.has(HudhudMind.C.PHONE) && mine && !his -> return Ask.Shop(ShopFacet.PHONE)
            // "Number" alone: the shop's, or somebody's whose name was not said.
            m.has(HudhudMind.C.PHONE) && !his && !m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER, HudhudMind.C.STAFF) ->
                return Ask.DidYouMean(listOf(Ask.Shop(ShopFacet.PHONE), Ask.NeedName(Facet.PHONE)))
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
            // 13 Nov 2026: the cashbook's day ("aaj ki cashbook"; "kharcha kitna hua" is the sense's), goods that came back.
            m.has(HudhudMind.C.CASHBOOK) && (asks || m.any(HudhudMind.C.MONEY, HudhudMind.C.CAME, HudhudMind.C.ALL)) ->
                return Ask.Cashbook(window)
            m.has(HudhudMind.C.RETURN) && (asks || m.any(HudhudMind.C.GOODS, HudhudMind.C.CAME, HudhudMind.C.HAPPENED)) ->
                return Ask.Returns(window)
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
            return Ask.DidYouMean(listOf(Ask.Status(StatusOf.BOOK), Ask.NeedName(Facet.LAST_ENTRY)))
        }
        // Somebody's limit, promise, father or village, with no name: he asks whose.
        if (m.any(HudhudMind.C.LIMIT, HudhudMind.C.PROMISE, HudhudMind.C.FATHER, HudhudMind.C.VILLAGE) ||
            m.has(HudhudMind.C.PAYMENT) && m.has(HudhudMind.C.LAST)
        ) return Ask.NeedName(facetOf(m))
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
     * What the note reader's sentence asks (14 Nov 2026): the note of the person the words name; of the person the talk
     * was about when it names nobody ("uska note", "private note?", a bare "note?") unless it asks for everybody's or for
     * "my" notes; else "whose?" with the customers who have one ("kis kis ka note hai", "mere notes", "note dikhao" with
     * nobody before it). Never a guess, never a question back: the list is the answer. A bare "note" is his too: it fell
     * to the sense, which read it as "jump".
     *
     * The note's own word asks and never names: a book with a "Note House" in it found that customer in every "uska note" and
     * "kis kis ka note hai" (the word matched his name), so who is named is read from the sentence's other words. And a
     * customer whose own name holds the word, said as often as his name holds it and with nothing that says it is the note
     * ("niji", "uska", "mera"), is the customer and not the ask: "Note House" is his balance, "Note House ka note" his note.
     * Null then: the sentence goes on to the rest of the reading as if the note reader had not looked at it.
     */
    private fun noteAsk(
        n: HudhudNote.Read, said: List<String>, names: List<Name>, topics: List<Topic>, m: HudhudMind.Read,
        window: VoiceQuestion.Window?, before: Ask?
    ): Ask? {
        val named = whoIsNamed(said.filterNot { HudhudNote.isNoteWord(it) }, names, topics, m, window)
        if (named.isNotEmpty()) {
            val noteWords = said.count { HudhudNote.isNoteWord(it) }
            val inTheirNames = names.filter { it.id in named }.maxOfOrNull { nm -> words(nm.name).count { HudhudNote.isNoteWord(it) } } ?: 0
            if (inTheirNames > 0 && noteWords <= inTheirNames && !n.specific) return null
            return Ask.Person(named, Facet.NOTE)
        }
        val carried = before?.let { partyOf(it) }
        if (carried != null && !n.anyone && !n.mine) return Ask.Person(carried, Facet.NOTE)
        return Ask.NeedName(Facet.NOTE)
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
        country.oic -> Greeting.SALAAM
        hour in 4..11 -> Greeting.MORNING
        hour in 12..16 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }

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
        "نسخ احتياطي", "النسخ الاحتياطي", "نسخة احتياطية", "बैकअप", "ব্যাকআপ", "cadangan",
        // Arabic and Farsi say the English word too ("آخر باك أب", 13 Nov 2026: the held-out set).
        "باك أب", "باك اب", "باكاب", "الباك اب", "بک آپ", "بکاپ", "बैक अप", "ব্যাক আপ"
    )

    /**
     * The drawer — the shop's galla — and the cash in hand. Bare English "cash" is left out: it is half of Cash flow.
     * Sindhi's گلي, Farsi's صندوق and Bengali's ক্যাশ are the app's own drawer chip in those languages (12 Nov 2026).
     */
    private val DRAWER = listOf(
        "galla", "gala", "galle", "gallay", "galley", "galy", "drawer", "cash in hand", "naqdi", "naqd",
        "گلہ", "گلا", "گلے", "نقدی", "نقد", "الدرج", "صندوق النقد", "دخل", "گلي", "صندوق",
        "गल्ला", "गल्ले", "नकद", "नकदी", "हाथ में नकद", "ক্যাশ বাক্স", "ক্যাশ", "ড্রয়ার", "নগদ", "laci", "uang tunai",
        // Close the day, whose count is the drawer's (13 Nov 2026: "aaj din band kiya" read as the date).
        "din band", "din bandh", "day close", "close the day", "close day", "دن بند", "दिन बंद", "দিন বন্ধ", "اغلاق اليوم",
        "إغلاق اليوم", "بستن روز", "ڏينهن بند", "tutup hari"
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
            w.length >= 3 && w.any { it.isLetter() } && w !in STOP_SET && w !in INTENT_SET && w !in VOCATIVE_SET && w !in QUIET_SET &&
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

    /**
     * Words that call him or address him and ask nothing (13 Nov 2026, the owner: "bhai jaan tab hota hai jab hum
     * kisi ko mukhatab karte hain"): his name, "bhai", "bhai jaan", "janab", "suno"… in the nine languages. A sentence
     * of only these is a calling ([Talk.ADDRESS]); and none of them is ever taken as a customer's name by itself —
     * "Saleem bhai" is Saleem, "bhai jaan" is nobody.
     */
    private val VOCATIVE = listOf(
        "hudhud", "hoopoe", "bhai", "bhaijaan", "bhaijan", "bhaiya", "bhaiyya", "bhaya", "bhayya", "jaan", "jan", "janab",
        "jenab", "jnab", "sahab", "sahib", "saab", "sab ji", "sir", "boss", "ustad", "ustaad", "yaar", "yar", "yara",
        "dost", "brother", "bro", "buddy", "dear", "mate", "friend", "hey", "hei", "oye", "oy", "oi", "suno", "sun",
        "suniye", "suniyе", "sunen", "sunain", "sunein", "listen", "ji", "jee", "jii", "huzoor", "hazoor", "bhaijee",
        "ہدہد", "بھائی", "بھائی جان", "بھیا", "بھیّا", "جان", "جناب", "صاحب", "سر", "باس", "استاد", "یار", "دوست", "سنو",
        "سنیں", "سنیے", "سنئے", "جی", "حضور", "اوئے",
        "हुदहुद", "भाई", "भाईजान", "भैया", "भइया", "जान", "जनाब", "साहब", "साहिब", "सर", "बॉस", "उस्ताद", "यार",
        "दोस्त", "सुनो", "सुनिए", "सुनिये", "जी", "हुज़ूर",
        "হুদহুদ", "ভাই", "ভাইয়া", "ভাইজান", "শোনো", "শুনুন", "শুনেন", "জি", "স্যার", "দোস্ত", "বন্ধু",
        "هدهد", "يا", "أخي", "اخي", "أخ", "اخ", "أستاذ", "استاذ", "اسمع", "اسمعني", "حبيبي", "صديقي", "يا أخي",
        "داداش", "آقا", "برادر", "رفیق", "جان", "گوش کن", "عزیزم",
        "ادا", "سائين", "ٻڌ", "ٻڌو", "ڀاءُ", "ڀاء",
        "bang", "pak", "kak", "mas", "mbak", "dengar", "gan", "bos", "abang", "om"
    )

    private val VOCATIVE_SET: Set<String> by lazy { VOCATIVE.flatMap { words(it) }.toSet() }

    /** A word that only means "you" ("aap", "Hudhud"): with a calling, still only a calling. */
    private val YOU_ONLY = setOf(HudhudMind.C.YOU)

    /** "Pata", "hai", "ko" and "kya" as "aap ko … pata hai" needs them ([knowsFrame]). */
    private val PATA: Set<String> by lazy { listOf("pata", "patta", "pta", "پتہ", "پتا", "पता").map { norm(it) }.toSet() }
    private val IS_WORDS: Set<String> by lazy { listOf("hai", "he", "hy", "hain", "ہے", "ہیں", "है", "हैं").map { norm(it) }.toSet() }
    private val KO: Set<String> by lazy { listOf("ko", "کو", "को").map { norm(it) }.toSet() }
    private val TO_YOU: Set<String> by lazy {
        listOf("aapko", "apko", "tumhe", "tumhein", "tujhe", "تمہیں", "تجھے", "تمھیں", "तुम्हें", "तुझे", "आपको").map { norm(it) }.toSet()
    }
    private val WHAT_NEXT: Set<String> by lazy { listOf("kya", "kia", "kiya", "کیا", "क्या").map { norm(it) }.toSet() }

    /**
     * "Aap ko … pata hai": knowing, said to him — "pata" with "hai" after it, said to "you" ("aap ko", "tumhein"), and
     * not "pata kya hai", which asks an address.
     */
    private fun knowsFrame(said: List<String>): Boolean {
        val p = said.indexOfFirst { it in PATA }
        if (p < 0) return false
        if (said.getOrNull(p + 1) in WHAT_NEXT) return false
        if (said.drop(p + 1).none { it in IS_WORDS }) return false
        return said.indices.any { i ->
            said[i] in TO_YOU || (HudhudMind.C.YOU in HudhudMind.meaningOf(said[i]) && said.getOrNull(i + 1) in KO)
        }
    }

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
        "in", "۾", "في", "جا", "تو",
        // Arabic's "the one who" (dialect), "to" and "with/at": each folds to a sound like "Ali" ("اللي" is "alli"), and
        // a customer called Ali was found in half the Arabic questions (13 Nov 2026, the held-out set).
        "اللي", "اللى", "إلى", "الى", "عند",
        // "On him", "on us", "on me"…: Arabic's "على" with its endings, read as "Ali" + a letter.
        "عليه", "عليها", "عليهم", "علينا", "عليك", "عليكم", "عليا", "عندنا", "عندي", "عنده"
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
        // The words that only ask (how, where) say nothing of the page: "kahan" found the data page's title
        // ("Mera data kahan rehta hai?") for every "… kahan hai" (14 Nov 2026, the owner's question lists).
        val q = said.filter { it.length >= 3 && it !in STOP_SET && norm(it) !in HOW_SET && norm(it) !in WAY_SET }.distinct()
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

    /** The words that ask for the way to do something or the place it is done: [HOW] and "where" in the nine languages. */
    private val WAY: List<String> by lazy {
        HOW + listOf(
            "kahan", "kahaan", "kahan se", "kidhar", "kithe", "kithon", "where", "which button", "kaunsa button", "konsa button",
            "کہاں", "کدھر", "کس طرح", "کہاں سے", "ڪٿي", "ڪٿان", "طريقو", "أين", "وين", "طريقة", "کجا", "روش",
            "कहाँ", "कहां", "किस तरह", "কোথায়", "কোথা থেকে", "উপায়", "di mana", "dimana", "langkah"
        )
    }

    private val WAY_SET: Set<String> by lazy { WAY.flatMap { words(it) }.toSet() }

    /** Whether [heard] asks for the way (or the place) rather than a figure ([decide]'s help pages). */
    internal fun asksTheWay(heard: String): Boolean = has(words(heard).joinToString(" "), WAY)
}
