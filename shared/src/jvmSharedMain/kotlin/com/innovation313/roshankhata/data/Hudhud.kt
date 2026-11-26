package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.ui.TextMemo
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
        data class Entry(
            val amount: Double, val isGiven: Boolean, val ids: List<Long>, val nameWords: List<String>,
            /**
             * The day the sentence says it happened (16 Nov 2026): "Ali ko kal 500 diye", "parson", "12/11 ko" ([VoiceQuestion.entryDay]);
             * null: now. The form opens on [entryTime] of it, never in the future.
             */
            val day: VoiceQuestion.Dated? = null
        ) : Ask()

        /**
         * Two entries said in one sentence (16 Nov 2026): "Ali ko 500 diye aur Bashir se 300 liye" was one wrong entry — 500 given,
         * to a choice of Ali and Bashir. Each is offered on its own, to be filled one at a time; nothing is written.
         */
        data class Entries(val entries: List<Entry>) : Ask()

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

        /**
         * Not a question of the shop's book (16 Nov 2026, audit T7 / H-05, H-08, the owner: refuse plainly): the weather, a
         * cricket score, a joke — said plainly that he helps only with the book and the app — or a sum to work out, never an
         * entry: [sum] is the expression when it was written with signs and [result] the calculator's own answer ([Calc.eval]).
         */
        data class OffTopic(val why: OffTopicWhy, val sum: String? = null, val result: Double? = null) : Ask()

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

        /**
         * Where one of the book's pages stands: cheques, plans, supplier bills, low stock, the whole book, the invoices not paid in
         * full. [cheques] narrows a cheques question to the ones bounced or cleared (15 Nov 2026); null is the pending ones as before.
         */
        data class Status(val of: StatusOf, val cheques: ChequeState? = null) : Ask()

        /**
         * A record the book keeps on a page of its own (15 Nov 2026, the catalog's staff, plans and cheques gap): a staff member's
         * pay, attendance or details, a customer's plan with its instalments, a customer's cheques with the day each was written
         * and settled. [ids]: the staff named (pay, attendance, details: the staff list the caller passed to [understand]) or the
         * customers named (plans, cheques). The staff are not in the customers' list, so the caller gives its staff names apart.
         */
        data class Record(
            val facet: RecordFacet, val ids: List<Long> = emptyList(),
            val window: VoiceQuestion.Window? = null
        ) : Ask()

        /** Goods that came in from suppliers (their bills) over a span of days, newest first. */
        data class Deliveries(val window: VoiceQuestion.Window? = null) : Ask()

        /** What happened in the book over a span of days, and who did it: the owner's check and balance. */
        data class Activity(val window: VoiceQuestion.Window? = null) : Ask()

        /**
         * Two or three things the words could mean: each offered as a tap, never guessed. With [unsure] null the first is answered
         * and the others are chips (D1, "Taken as"). With [unsure] set (D15, 25 Nov 2026) nothing is answered: what the sentence left
         * uncertain — which way the money runs — is asked first ("lene hain ya dene?"), each answer a chip ([Unsure]).
         */
        data class DidYouMean(val options: List<Ask>, val unsure: Unsure? = null) : Ask()

        /**
         * A span of days he cannot give for this question (15 Nov 2026, the owner: "pichle 3 mahine ki bikri", a whole
         * year and "aaj tak" were answered as this month's figure, a wrong sum said with a straight face). He says so and
         * offers the same question over the spans he can give, each a tap ([options]). [long]: the longer spans (the last
         * 6 months, a year, the whole book) are among them — only the asks that read sums can be given those ([longOk]);
         * the others list rows, which are read a day, a week or a month at a time.
         */
        data class SpanLimit(val options: List<Ask>, val long: Boolean) : Ask()

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

        /**
         * The counter's own books (15 Nov 2026, the catalog's counter group): the day's bills, who voided, why goods came back, the
         * shifts and the drawer's count, loyalty points, goods written off and the stock counts — read by [HudhudCounter] only
         * (the bank has no meaning for them, so [decide] never turns it). [window] is the span of days, null for "the last one"; [ids] are
         * the customers named (only the loyalty points of one customer carry them).
         */
        data class Counter(val facet: CounterFacet, val window: VoiceQuestion.Window? = null, val ids: List<Long> = emptyList()) : Ask()

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
        data class NeedGoods(val facet: GoodsFacet, val code: String? = null) : Ask()

        /**
         * A paper the book holds, asked for by what it says (15 Nov 2026, [HudhudRecords]): the newest supplier bills, purchase orders
         * or invoices — their number, note, lines — or the company schemes and how they stand. [ids] names the supplier (or the
         * customer, for an invoice) when the sentence does. Said by the rules alone ([decide] never lets the sense turn it).
         */
        data class Paper(val of: PaperOf, val ids: List<Long> = emptyList()) : Ask()

        /**
         * The name the owner asks to be called by (14 Nov 2026, the owner's check logs: "my name is …", "mera naam … hai",
         * "mujhe … bulao" were read as a question about his name, as the shop's name or as a customer): [name] is kept on
         * this phone only (HudhudPrefs) and he calls the owner by it from now on. Said by the rules alone, never the sense's.
         */
        data class CallMe(val name: String, val save: Boolean = false) : Ask()

        /**
         * How the app looks, said to him (14 Nov 2026, the owner's yes to the settings tiers): the text size or the theme
         * ("text size large karo", "dark mode", "font bara karo", "theme badlo"). [request] is what the sentence asks; the
         * app's side reads where the setting stands, changes it at the owner's word with an Undo on the card, or only says
         * where it stands and offers the choices. Said by the rules alone, never the sense's. A data class, so a choice
         * offered under a card (which carries one) is read back as itself.
         */
        data class Look(val request: HudhudLook.Request) : Ask()

        /**
         * A look change that was made — or taken back — shown on its card after the screen was rebuilt for it: [made] says the
         * two values. Carried by the app (the card's Undo, the sheet opened again), never read from a sentence.
         */
        data class LookDone(val made: HudhudLook.Made) : Ask()

        /**
         * What he took a sentence to mean when nothing fitted it exactly (13 Nov 2026, the owner: "aap ka kaam batana
         * hai, poochhna nahi"): [ask] is answered, and the card says what he took it as, so a wrong guess is seen.
         */
        data class Guessed(val ask: Ask) : Ask()

        /**
         * A question about how the book stands NOW — a count of customers, a customer's balance or number, a product's price
         * or stock, the drawer — that came with a span of days ("pichle saal kitne customers", "Ali ka hisaab last month")
         * (15 Nov 2026). The book keeps no earlier standing of these, so [ask] is answered as it stands today and the card says
         * so first, instead of leaving the span out in silence, as if the figure were that span's. A guess stays a guess
         * ([Guessed] around it).
         */
        data class Now(val ask: Ask) : Ask()

        /**
         * A sale, a profit, the money that came in, the day's dealings, or what a customer paid ([DatedOf.PAID]) or took
         * ([DatedOf.TOOK]) over a day or a month named outright (16 Nov 2026, audit T31): "15 tareekh ki sale", "12 November
         * ko kitne paise aaye", "Ali ne parson kitna diya". [ids] is the customer for PAID and TOOK. The app reads it with
         * the very calls of the span asks ([Period], [Collected], [Activity], [Moved]) over [Hudhud.span] of [days].
         */
        data class OnDays(val of: DatedOf, val days: VoiceQuestion.Dated, val ids: List<Long> = emptyList()) : Ask()

        /**
         * Two questions in one sentence (16 Nov 2026, audit T32 / H-03): "Ali Raza ka balance aur aaj ki sale batao" answered
         * one half and dropped the other. Read only when each half alone is a firm question of the book and the whole was
         * read as one of them; the app answers each by its own reader and its own gate, on one card.
         */
        data class Both(val first: Ask, val second: Ask) : Ask()

        /**
         * A sale or a profit over two spans side by side, and the difference, [first] less [second] (16 Nov 2026, audit T32 /
         * H-04: "is mahine aur pichle mahine ki sale ka farq" answered only last month). [first] and [second] are each a
         * [Period] or an [OnDays] of the same measure; no third figure is ever worked out when either is unknown.
         */
        data class Compare(val first: Ask, val second: Ask) : Ask()

        /**
         * "Why" about a figure of the book (16 Nov 2026, audit T8, the owner's H-10): "is mahine sale kam kyun hui", "profit kyun
         * gira", "why are expenses up". Read by [HudhudWhyAsk] alone, never by the sense. The app answers with facts only: [window]'s
         * figure beside the span before it ([HudhudWhyAsk.spans]), the signed difference and the parts that moved most — never a
         * cause the book cannot show. [down]: the way the sentence asked (true: "why did it fall", false: "why did it rise", null:
         * not said); the card says plainly when the figure went the other way.
         */
        data class Why(val of: WhyOf, val window: VoiceQuestion.Window, val down: Boolean? = null) : Ask()

        /**
         * What a named customer or supplier took of a named product (17 Nov 2026, audit L004): "Ali Raza ne Urea kab liya", "when
         * did Ali Raza buy Urea", "Ali Raza ne is mahine kitni Urea li". It was his account, or (by the sense) the day he may pay.
         * Only the rules make it ([tookOf]); answered by AnswerQ.took: the day he last took it and the rate he was charged, or a
         * supplier's last bill line of it. [window]: the days asked, none for the whole book.
         */
        data class Took(val ids: List<Long>, val goods: List<Long>, val window: VoiceQuestion.Window? = null) : Ask()

        /**
         * Past talks asked for (17 Nov 2026, audit L019): "pichli chat dikhao", "hamari purani baatein", "chat history". He shows no
         * past conversations; the card says so (AnswerQ.noHistory), where the day's activity was told before.
         */
        object NoHistory : Ask()

        /**
         * Whether the sales are going up or down (25 Nov 2026, plan H5): "is my sale going up or down", "kya sale barh rahi hai",
         * "sales trend this week". The sales over [window] beside the span before it (the same length of time while the span is
         * still running, [HudhudWhyAsk.spans]), said as up, down or about the same, with both figures and the change. Read by
         * [HudhudAdviceAsk] alone, never by the sense; REPORTS ([AnswerAdvisor.trend]). It carries over a span like the figures do
         * ("aur pichle mahine?").
         */
        data class Trend(val window: VoiceQuestion.Window) : Ask()

        /**
         * The shop's advisor (25 Nov 2026, plan H5): [AdviseOf.CHASE] — whom to chase first, the oldest dues by Dues by age's rule with
         * the Radar's late payers; [AdviseOf.NEXT_30] — what is promised to come in over the next 30 days, the Cash flow page's own
         * figure. Read by [HudhudAdviceAsk] alone, never by the sense; REPORTS ([AnswerAdvisor.advise]).
         */
        data class Advise(val of: AdviseOf) : Ask()
    }

    /**
     * What a sentence left uncertain, asked before anything is answered (D15, the owner's decision of 25 Nov 2026; Marhala 1):
     * [SIDE] — which way the money runs, the shop's to get or its to give ("Kitna qarz baqi hai", "Kis ki payment late ho gayi").
     * Only the rules say so ([VoiceQuestion.sideUnsure]); the learnt sense never decides a side the words do not state.
     */
    enum class Unsure { SIDE }

    /** What a day or a month named outright is asked of ([Ask.OnDays]). */
    enum class DatedOf { SALES, PROFIT, COLLECTED, ACTIVITY, PAID, TOOK }

    /** What a "why" is asked about ([Ask.Why]): the sales, the profit, the expenses, the money that came in, the customers' credit. */
    enum class WhyOf { SALES, PROFIT, EXPENSES, COLLECTED, DUES }

    /** What the shop's advisor is asked ([Ask.Advise]): whom to chase first, what will come in over the next 30 days. */
    enum class AdviseOf { CHASE, NEXT_30 }

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
    /** [COVER]: how many days what is on the shelf lasts at the pace it has been selling ([StockCover]). */
    enum class GoodsFacet { OVERVIEW, PRICE, STOCK, COST, SUPPLIERS, LAST_IN, BUYERS, SOLD, COVER,
        // The product's own record (15 Nov 2026, [HudhudRecords]): the rules' asks alone, no meaning in the bank.
        LABEL, BARCODE, PACK, TAX, SHELF, ADDED, BATCHES, SCHEME }

    /** Which paper a [Ask.Paper] is about. */
    enum class PaperOf { BILL, ORDER, INVOICE, SCHEMES }

    /** The product facets only the rules read: the sense learnt no meaning for them, so it never turns them. */
    internal val RECORD_FACETS = setOf(GoodsFacet.LABEL, GoodsFacet.BARCODE, GoodsFacet.PACK, GoodsFacet.TAX, GoodsFacet.SHELF,
        GoodsFacet.ADDED, GoodsFacet.BATCHES, GoodsFacet.SCHEME)

    /**
     * What is asked of the counter's books ([Ask.Counter]). The day's bills: [BIGGEST], [LAST_TICKET], [BY_CASHIER], [DISCOUNTS],
     * [TAX], [ON_ACCOUNT], [PAY_WAYS], [VOIDED]. Goods that came back: [RETURN_REASONS], [RETURN_WAYS], [RETURN_GOODS]. The till:
     * [SHIFTS], [SHIFT_SHORT], [DAY_COUNT]. Points and the stock's own books: [LOYALTY], [WRITTEN_OFF], [STOCK_CHANGES], [LAST_COUNT].
     */
    enum class CounterFacet {
        BIGGEST, LAST_TICKET, BY_CASHIER, DISCOUNTS, TAX, ON_ACCOUNT, PAY_WAYS, VOIDED,
        RETURN_REASONS, RETURN_WAYS, RETURN_GOODS, SHIFTS, SHIFT_SHORT, DAY_COUNT, LOYALTY, WRITTEN_OFF, STOCK_CHANGES, LAST_COUNT
    }

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
    enum class StatusOf { CHEQUES, PLANS, BILLS, LOW_STOCK, BOOK, STAFF, INVOICES }

    /** Which cheques a cheques question is about: the ones waiting (the default), the ones that came back, the ones that were paid. */
    enum class ChequeState { PENDING, BOUNCED, CLEARED }

    /** Which record an [Ask.Record] is about: the staff's pay, what was paid them, who was away, who they are; a plan; closed plans; a customer's cheques. */
    enum class RecordFacet { STAFF_PAY, STAFF_PAID, ATTENDANCE, STAFF_PERSON, PLAN, PLAN_CLOSED, CHEQUE }

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
     * The book's names as a sentence is read (15 Nov 2026): [VoiceQuestion.normalise] takes the Devanagari nukta off every word that
     * is said ("ज़" and "ज" are one word to a listener), so a name saved with it ("रज़ा") is read the same way, or the very word
     * asked would never meet the name. [NameSearch.fold] is the whole app's search and keeps the nukta's sound: it is not touched.
     * Bengali's nukta stays in a sentence, so it stays in a name too.
     */
    private fun asRead(list: List<Name>): List<Name> {
        if (list.none { n -> n.name.any { it == '\u093C' || it in '\u0958'..'\u095F' } }) return list
        return list.map { n ->
            val t = java.text.Normalizer.normalize(n.name, java.text.Normalizer.Form.NFC).replace("\u093C", "")
            if (t == n.name) n else Name(n.id, t)
        }
    }

    /**
     * A help page: its [key], its [title] and the rest of its words, in every language the phone gives them.
     * [feature]: the page of one of the app's features ("Cheques", "Staff"), whose name is never taken for a
     * customer's; a question-and-answer page's title is a sentence, and its words (محفوظ, "keep") may well be one.
     */
    data class Topic(val key: String, val title: String, val text: String, val feature: Boolean = true)

    /**
     * Whether a help page is a feature's, so that its title's words are never taken for a customer's (see
     * [Topic.feature]). A question page (`faq_…`) is not. Nor are six screens whose titles are also what a person or a
     * shop is called (14 Nov 2026, the how-to pages of the screens that had none): "Waris" is the Heir Access page and a
     * very common name, "Madad" is Help & Support and a name part, "Phone", "Voice" and "Duplicate" are words of shops'
     * names. Their words stay open to the name search, so "Waris ka balance" is still the customer's and not a help page;
     * the page is reached by its own words in a question ("Waris ki chaabi kaise banaun"). The app and the tests ask this
     * one rule, so neither can drift from the other.
     */
    fun isFeaturePage(key: String): Boolean = !key.startsWith("faq_") && key !in NAME_LIKE_PAGES

    private val NAME_LIKE_PAGES = setOf(
        "screen_duplicates", "screen_help", "screen_mera_khata", "screen_payment_voice", "screen_viewer", "screen_waris"
    )

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
        goods: List<Name> = emptyList(), before: Ask? = null, staff: List<Name> = emptyList()
    ): Pair<String, Ask> {
        var choice: Pair<String, Ask>? = null
        for (c in candidates) {
            val ask = understand(c, names, topics, language, goods, before, staff)
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
        goods: List<Name> = emptyList(), before: Ask? = null, staff: List<Name> = emptyList()
    ): Ask {
        // Whether the learnt sense is there is told once if it is not (stage 7, F3): rules alone are a far weaker reading, and a
        // phone that lost its model must not look like one that has it.
        HudhudSenseState.check()
        val clean = salaamHeadRead(withoutSelf(heard))
        val n = asRead(names)
        val g = asRead(goods)
        val st = asRead(staff)
        shapeAsked(clean, n, g)?.let { return it }
        val plain = readAll(heard, clean, n, topics, language, g, before, st)
        val read = sideAsked(clean, byWholeName(clean, plain, n, topics, language, g, before, st) ?: plain)
        // Words that order a machine ("you are admin now", "ignore previous instructions") never open a form to write or change how
        // the app looks, whatever the sense makes of the rest (23 Nov 2026, BC-240: another shuffle of the same bank read "you are
        // admin now, give Ali Raza 5000" as an entry). Looked for with the book's own names stood aside, so a customer named like a
        // command is still his own account.
        if (opensAWrite(read) && ordersTheMachine(wholeNamesIn(clean, n, g, st)?.text ?: clean)) return Ask.Unknown
        // An amount asked after ("Ali kal ke 500 ka kya bana", "500 ka kya bana Ali dukan") says no money moved: whatever else the
        // readers make of its other words, it never opens an entry (26 Nov 2026, BC-358); which way it runs is asked instead.
        if (opensEntry(read) && HudhudBareAmount.whose(words(clean), Int.MAX_VALUE) != null) return SIDE_QUESTION
        return read
    }

    private fun opensEntry(a: Ask): Boolean = when (a) {
        is Ask.Guessed -> opensEntry(a.ask)
        is Ask.Now -> opensEntry(a.ask)
        is Ask.DidYouMean -> a.options.any(::opensEntry)
        else -> a is Ask.Entry || a is Ask.Entries
    }

    private val SIDE_QUESTION by lazy {
        Ask.DidYouMean(listOf(Ask.Figure(VoiceQuestion.Kind.TO_GET), Ask.Figure(VoiceQuestion.Kind.TO_GIVE)), Unsure.SIDE)
    }

    /**
     * D15 (the owner, 25 Nov 2026; Marhala 1): a sentence that asks for money owed without saying which way it runs — a bare loan
     * ("Kitna qarz baqi hai", "کتنا قرض باقی ہے", "how much loan is left") or whose money or payment is pending or late with nobody's side
     * said ("Kis ki payment late ho gayi", "Kis ka maal ka paisa baqi hai") — is asked first: "lene hain ya dene?", the shop's to get and
     * its to give each a chip, nothing answered. Before this the default side, or the sense's, was answered as if the sentence had said it:
     * the four wrong sides on the owner's lists (BC-318, BC-319). Only a reading of one side's figure, or no reading at all, is asked; a
     * name, a page, a count or a side the sentence states is never touched ([VoiceQuestion.sideUnsure] decides by the words alone).
     */
    private fun sideAsked(heard: String, read: Ask): Ask {
        val inner = if (read is Ask.Guessed) read.ask else read
        val kind = (inner as? Ask.Figure)?.kind
        // A guess (the sense unsure of anything: "how much loan is left" was the month's profit) or "who is likely to pay" with nobody
        // named ("pembayaran siapa yang terlambat") is asked too; a firm reading of anything else is not.
        if (inner != Ask.Unknown && read !is Ask.Guessed && kind !in SIDE_ASKED && inner != Ask.WhenPays(emptyList())) return read
        if (!VoiceQuestion.sideUnsure(heard)) return read
        // Late or not paid is the receivable side's list of who has not paid; owed alone is the total to get.
        val get = if (kind == VoiceQuestion.Kind.NOT_PAID || VoiceQuestion.saysLate(heard)) VoiceQuestion.Kind.NOT_PAID else VoiceQuestion.Kind.TO_GET
        return Ask.DidYouMean(listOf(Ask.Figure(get), Ask.Figure(VoiceQuestion.Kind.TO_GIVE)), Unsure.SIDE)
    }

    /**
     * Two shapes whose words say what is asked whoever reads them (26 Nov 2026), answered by rule before the readers and the sense:
     * - an amount asked after with nobody, nothing and no side ([HudhudBareAmount], BC-356): "500 ka kya bana?" asks which way first,
     *   as [sideAsked] does — never an entry, never a page found by the figure;
     * - whether the shop has a thing ([HudhudInStock], BC-357): a product of the book is its stock; a word no reader of the book or
     *   the language knows ("سكر" with no product by it) asks which product, never the privacy page its sound met.
     */
    private fun shapeAsked(clean: String, n: List<Name>, g: List<Name>): Ask? {
        val said = words(clean)
        HudhudBareAmount.whose(said)?.let { whose ->
            if (whose.isEmpty()) return SIDE_QUESTION
            // "Ali ke 500 ka kya bana", "Ali bhai ke 500 ka kya bana": his money asked after, not a giving said — his account, never
            // an entry opened in his name. Only the words that are names of the book name him; the others are left aside.
            val named = whose.filter { knownName(it, n) }
            if (named.isNotEmpty()) resolveNames(n, named, named).takeIf { it.isNotEmpty() }?.let { return Ask.Parties(it) }
        }
        val thing = HudhudInStock.thing(said) ?: return null
        if (g.isNotEmpty()) {
            val ids = resolveNames(g, thing, thing)
            if (ids.isNotEmpty()) return Ask.Goods(ids, GoodsFacet.STOCK)
        }
        // A thing no reader knows is asked about only in Arabic script, where its sound met a word of the app's (the class found):
        // a Latin word that is no product ("is there any problem", "do you have feelings") is left to the readers that know it.
        fun untaught(w: String) = w.length >= 2 && w !in QUIET_SET && w !in VOCATIVE_SET && w !in STOP_SET &&
            HudhudMind.meaningOf(w).isEmpty() && !HudhudMind.isHardWord(w) && !HudhudSense.knowsWord(w)
        if (!thing.all { scriptOf(it) == Script.ARABIC } || thing.any { knownName(it, n) || knownName(it, g) }) return null
        // One word the app does not know is enough: a single thing must be one, and a word of a list may be another language's word
        // too ("سكر وملح وأرز": "ارز" is rice in Arabic and a currency in Farsi).
        return if (thing.any(::untaught)) Ask.NeedGoods(GoodsFacet.STOCK) else null
    }

    /** The figures that carry one side of what is owed, which [sideAsked] asks instead of answering. */
    private val SIDE_ASKED = setOf(VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.TO_GIVE, VoiceQuestion.Kind.NOT_PAID)

    /** A reading that opens a form to write, or changes how the app looks (the plain one, or inside a guess or a choice). */
    private fun opensAWrite(a: Ask): Boolean = when (a) {
        is Ask.Guessed -> opensAWrite(a.ask)
        is Ask.Now -> opensAWrite(a.ask)
        is Ask.DidYouMean -> a.options.any(::opensAWrite)
        else -> a is Ask.Entry || a is Ask.Entries || a is Ask.Look || a is Ask.LookDone
    }

    /** The sentence orders the machine itself rather than asking the book (HudhudInjectionTest's class, audit T36). */
    private fun ordersTheMachine(text: String): Boolean = has(words(text).joinToString(" "), MACHINE_ORDER)

    private val MACHINE_ORDER = listOf(
        "you are admin", "you are the admin", "you are an admin", "you are now admin", "you are now the admin", "admin now",
        "act as admin", "i am the admin", "i am admin", "ignore previous", "ignore all", "ignore the above", "ignore your",
        "previous instructions", "all instructions", "your instructions", "forget everything", "forget all", "forget your",
        "system prompt", "developer mode", "jailbreak", "drop table", "delete from",
        "tum admin ho", "aap admin ho", "ab tum admin", "hidayat bhool", "hidayaat bhool", "hidayat ko bhool", "hidayaat ko bhool",
        "پچھلی ہدایات", "ہدایات بھول", "تم ایڈمن ہو", "पिछली हिदायत", "पिछले निर्देश", "निर्देश भूल", "तुम एडमिन हो"
    )

    /** The whole reading of one sentence: the rules and the sense, two spans compared, two questions, off the book. */
    private fun readAll(
        heard: String, clean: String, n: List<Name>, topics: List<Topic>, language: String?, g: List<Name>, before: Ask?, st: List<Name>
    ): Ask {
        val whole = understandRead(clean, n, topics, language, g, before, st)
        val read = compareOf(clean, n, g) ?: bothOf(clean, whole, n, topics, language, g, before, st) ?: whole
        return offTopic(heard, whatIf(heard, read, topics))
    }

    // ------------------------------------------------------------------------------- a name made of the words of a question

    /**
     * A name of the book made of the words of a question (16 Nov 2026, audit T38 / BC-036): a product "DELETE ALL", a customer
     * "Aaj Ki Sale". Every reader that runs before the name search (the delete order, the low stock, the day's dealings, the
     * figures) reads such a name's own words as the question, and the name search sets those very words aside as the
     * question's, so "DELETE ALL ka rate" opened the delete page and "Aaj Ki Sale ka balance" was the day's sale. The private
     * note met the same class for its one word (lesson 122, [noteAsk]); this is the rule for every word and every reader.
     *
     * When a name is said WHOLE ([wholeNamesIn]: every word of a name of two words or more, in its order, or a single-word name of
     * four letters or more that is no word of a question), the sentence is read again with the name stood in by a word that
     * means nothing ([MASK_WORDS]) — the readers then see only the rest of the sentence, the name search and the sense find the
     * stand-in as that customer, supplier, staff member or product. The second reading is taken only when:
     * - the first reading named none of those (a reading that found him already stands), and
     * - the second names him and asks something OF him ([asksOfMasked]): a fact of his (number, limit, a product's rate or stock),
     *   or, for his bare account or a product's overview, the sentence links to the name ("ka", "ki", "'s", "of", Bengali "-এর")
     *   or says his account in its other words ("balance", "hisaab").
     * With nothing asked of him the first reading stands; it reads the sentence as a book without him does, because neither the
     * name search ([namedIn]) nor the sense ([senseNameSlot]) takes a word of a question for a name ("فروش امروز چقدر است"
     * with a customer "فروش امروز" was "whose?" while the sense did).
     * So "aaj ki sale kitni hai" in a book with a customer "Aaj Ki Sale" stays the day's sale (nothing is asked of him), "Ali Raza
     * ka khata delete kar do" stays the delete page (the second reading names nobody either), and a bare "DELETE ALL" is read as
     * before. A single-word name that is itself a word of a question ("Sale", "Note") is never stood in: the sentence's own
     * question is far likelier, and such a name stays reachable with a fact of his ("Sale ka number" names him in the first reading).
     */
    private fun byWholeName(
        clean: String, plain: Ask, n: List<Name>, topics: List<Topic>, language: String?, g: List<Name>, before: Ask?, st: List<Name>
    ): Ask? {
        val mask = wholeNamesIn(clean, n, g, st) ?: return null
        if (namesMasked(plain, mask)) return null
        val read = readAll(mask.text, mask.text, mask.names, topics, language, mask.goods, before, mask.staff)
        return if (namesMasked(read, mask) && asksOfMasked(read, mask)) unmasked(read, mask) else null
    }

    /**
     * The sentence with each name said whole stood in by a word of [MASK_WORDS], and the lists with those names renamed to it.
     * [ids]: the customers' and suppliers' ([partyIds]), the products' ([goodsIds]) and the staff's ([staffIds]) stood in.
     * [linked]: a word that links to a name stands beside one ("X ka", "X's", "of X", "X-এর"). [rest]: the sentence's other words.
     */
    private class Masked(
        val text: String, val names: List<Name>, val goods: List<Name>, val staff: List<Name>,
        val partyIds: Set<Long>, val goodsIds: Set<Long>, val staffIds: Set<Long>,
        val linked: Boolean, val rest: List<String>, val said: Map<String, List<String>>
    )

    /** Stand-ins that mean nothing in any language and are far from each other (no search comes near one from another). */
    private val MASK_WORDS = listOf("qqbookone", "qqbooktwo", "qqbookthree", "qqbookfour")

    /** Words that link to the name before them ("Ali ka", "علی کا", "अली का", Sindhi "علي جو") — and English "'s", left as "s". */
    private val LINK_AFTER = setOf("ka", "ki", "ke", "kay", "kaa", "کا", "کی", "کے", "का", "की", "के", "جو", "جي", "جا", "s", "এর", "র")
        .map { norm(it) }.toSet()
    /** Words that link to the name after them ("the rate of X", "rate dari X"). */
    private val LINK_BEFORE = setOf("of", "dari").map { norm(it) }.toSet()

    /** A single word that a question is made of: a concept, a figure's word, a little word, a word of the language. */
    private fun questionWord(w: String): Boolean =
        HudhudMind.meaningOf(w).isNotEmpty() || HudhudMind.isSoftWord(w) || VoiceQuestion.keywordsIn(w).isNotEmpty() ||
            w in STOP_SET || w in INTENT_SET || w in QUIET_SET || w in VOCATIVE_SET || w in ERASE_FIRST || HudhudNote.isNoteWord(w) ||
            HudhudSense.knowsWord(w)

    /**
     * Whether the sense is shown [w] as a name of [list] (its "qqname"/"qqitem" slot). Never a little word, a concept or a figure's
     * own word ("sale", "aaj", "baqaya"): the name search sets those aside as the question's ([namedIn]), and the sense must read
     * them the same way, or a customer "Aaj Ki Sale" turned "sale ka hisaab" into "whose?" (16 Nov 2026, BC-036). A name made of
     * such words is still found when it is said whole ([byWholeName]).
     */
    private fun senseNameSlot(w: String, list: List<Name>): Boolean =
        w !in QUIET_SET && !HudhudMind.isHardWord(w) && VoiceQuestion.keywordsIn(w).isEmpty() && knownName(w, list)

    private fun wholeNamesIn(clean: String, n: List<Name>, g: List<Name>, st: List<Name>): Masked? {
        val raw = clean.trim().split(SPACE_RUN).filter { it.isNotEmpty() }
        // Each said word, with the raw word it came from (a raw "Sale's" gives "sale" and "s").
        val flat = ArrayList<String>()
        val from = ArrayList<Int>()
        raw.forEachIndexed { r, t -> words(t).forEach { flat.add(it); from.add(r) } }
        if (flat.isEmpty()) return null
        // Every name that may be stood in, longest first; a run of the sentence is taken by one name only.
        data class Cand(val kind: Int, val name: Name, val parts: List<String>)
        val cands = listOf(n, g, st).flatMapIndexed { kind, list ->
            list.mapNotNull { nm ->
                val parts = nameWords(nm.name)
                val ok = parts.size >= 2 || (parts.size == 1 && parts[0].length >= 4 && parts[0].any { it.isLetter() } && !questionWord(parts[0]))
                if (ok) Cand(kind, nm, parts) else null
            }
        }.sortedByDescending { it.parts.size }
        val taken = BooleanArray(flat.size)
        val runs = ArrayList<Triple<Int, Int, Cand>>()
        for (c in cands) {
            if (runs.size == MASK_WORDS.size) break
            val k = c.parts.size
            var i = 0
            while (i + k <= flat.size) {
                val match = (0 until k).all { j ->
                    val w = flat[i + j]
                    !taken[i + j] && (w == c.parts[j] || (j == k - 1 && bengaliStem(w) == c.parts[j]))
                }
                if (match) {
                    for (j in i until i + k) taken[j] = true
                    runs.add(Triple(i, i + k, c))
                    break
                }
                i++
            }
        }
        if (runs.isEmpty()) return null
        runs.sortBy { it.first }
        val out = ArrayList<String>()
        val renamed = HashMap<Pair<Int, Long>, String>()
        val said = HashMap<String, List<String>>()
        var linked = false
        var i = 0
        var r = 0
        while (i < flat.size) {
            val run = runs.firstOrNull { it.first == i }
            if (run != null) {
                val token = MASK_WORDS[runs.indexOf(run)]
                renamed[run.third.kind to run.third.name.id] = token
                said[token] = flat.subList(run.first, run.second).toList()
                // A Bengali case ending joined to the name's last word links it ("ডিলিট অলের").
                if (flat[run.second - 1] != run.third.parts.last()) linked = true
                if (flat.getOrNull(run.second) in LINK_AFTER || flat.getOrNull(run.first - 1) in LINK_BEFORE) linked = true
                out.add(token)
                i = run.second
                continue
            }
            // A raw word kept whole when every word it gave is outside the names (its figure, "1,500", stays as written).
            val rawIx = from[i]
            val all = from.indices.filter { from[it] == rawIx }
            if (all.none { taken[it] }) {
                if (rawIx >= r) out.add(raw[rawIx])
                r = rawIx + 1
                i = all.last() + 1
            } else {
                out.add(flat[i])
                i++
            }
        }
        // A question mark the stand-in took with its raw word stays: whether the sentence asks is read from it.
        val asks = runs.any { run -> (run.first until run.second).any { raw[from[it]].any { ch -> ch == '?' || ch == '\u061F' } } }
        val text = out.joinToString(" ") + if (asks) " ?" else ""
        fun rename(list: List<Name>, kind: Int) = list.map { nm -> renamed[kind to nm.id]?.let { Name(nm.id, it) } ?: nm }
        val rest = flat.indices.filter { !taken[it] }.map { flat[it] }
        return Masked(
            text, rename(n, 0), rename(g, 1), rename(st, 2),
            runs.filter { it.third.kind == 0 }.map { it.third.name.id }.toSet(),
            runs.filter { it.third.kind == 1 }.map { it.third.name.id }.toSet(),
            runs.filter { it.third.kind == 2 }.map { it.third.name.id }.toSet(),
            linked, rest, said
        )
    }

    /** Whether [a] names one of the stood-in names: a customer or supplier, a product or a staff member. */
    private fun namesMasked(a: Ask, m: Masked): Boolean = when (a) {
        is Ask.Guessed -> namesMasked(a.ask, m)
        is Ask.Now -> namesMasked(a.ask, m)
        is Ask.Both -> namesMasked(a.first, m) || namesMasked(a.second, m)
        is Ask.Compare -> namesMasked(a.first, m) || namesMasked(a.second, m)
        is Ask.DidYouMean -> a.options.any { namesMasked(it, m) }
        is Ask.SpanLimit -> a.options.any { namesMasked(it, m) }
        is Ask.Goods -> a.ids.any { it in m.goodsIds }
        is Ask.Record -> a.ids.any { it in m.partyIds || it in m.staffIds }
        is Ask.Parties -> a.ids.any { it in m.partyIds }
        is Ask.Person -> a.ids.any { it in m.partyIds }
        is Ask.WhenPays -> a.ids.any { it in m.partyIds }
        is Ask.Moved -> a.ids.any { it in m.partyIds }
        is Ask.Entry -> a.ids.any { it in m.partyIds }
        is Ask.OnDays -> a.ids.any { it in m.partyIds }
        is Ask.Counter -> a.ids.any { it in m.partyIds }
        is Ask.Paper -> a.ids.any { it in m.partyIds }
        else -> false
    }

    /**
     * Whether the reading asks something of the stood-in name: a fact of his or of the product (the rest of the sentence said it,
     * the stand-in carries no meaning), or his bare account or the product's overview with a word that links to the name or says
     * the account. A bare name with "kitni hai" asks nothing of him: "aaj ki sale kitni hai" is the day's sale.
     */
    private fun asksOfMasked(a: Ask, m: Masked): Boolean {
        val inner = when (a) {
            is Ask.Guessed -> a.ask
            is Ask.Now -> a.ask
            else -> a
        }
        val restJoined = m.rest.joinToString(" ")
        val account = m.rest.isNotEmpty() && (
            VoiceQuestion.classify(restJoined).let { it == VoiceQuestion.Kind.TO_GET || it == VoiceQuestion.Kind.TO_GIVE } ||
                m.rest.any { HudhudMind.C.ACCOUNT in HudhudMind.meaningOf(it) })
        // A word of the rest that says a fact (a rate, a number, a limit, the stock), not only how much or who: the reading's facet
        // then comes from the sentence, not from a guess about a bare name ("آج کی سیل کتنی ہے" is no limit of anybody's).
        val fact = m.linked || account || m.rest.any { w -> HudhudMind.meaningOf(w).any { it !in PLAIN_ASK && it != HudhudMind.C.GOODS } } ||
            VoiceQuestion.keywordsIn(restJoined).isNotEmpty()
        return when (inner) {
            is Ask.Parties -> m.linked || account
            is Ask.Goods -> (inner.facet != GoodsFacet.OVERVIEW && fact) || m.linked
            is Ask.Entry -> inner.ids.isNotEmpty()
            else -> fact
        }
    }

    /** Concepts that only ask (how much, who, which, you, this): beside a bare name they ask nothing of him by themselves. */
    private val PLAIN_ASK = setOf(
        HudhudMind.C.WHAT, HudhudMind.C.WHO, HudhudMind.C.HOW_MANY, HudhudMind.C.WHICH, HudhudMind.C.YOU, HudhudMind.C.ME,
        HudhudMind.C.THIS, HudhudMind.C.HIS, HudhudMind.C.AND, HudhudMind.C.OWN, HudhudMind.C.ALL, HudhudMind.C.TELL,
        HudhudMind.C.DO, HudhudMind.C.KNOW, HudhudMind.C.ABLE, HudhudMind.C.HOW_IS
    )

    /** [a] with the name the owner said in place of a stand-in where a reading carries the words ([Ask.Entry]'s name words). */
    private fun unmasked(a: Ask, m: Masked): Ask = when (a) {
        is Ask.Entry -> a.copy(nameWords = a.nameWords.flatMap { m.said[it] ?: listOf(it) })
        is Ask.Guessed -> Ask.Guessed(unmasked(a.ask, m))
        else -> a
    }

    // ------------------------------------------------------------------------------------ two questions, or two spans compared

    /** The words that join two questions or two spans: "and", "with", "versus" and, for a comparison only, "from". */
    private val AND_WORDS = setOf("aur", "or", "and", "اور", "और", "এবং", "আর", "و", "dan", "serta", "۽").map { norm(it) }.toSet()
    private val COMPARE_JOIN = AND_WORDS + setOf("with", "vs", "versus", "dengan", "مع", "با", "se", "سے", "से").map { norm(it) }.toSet()

    /**
     * A comparison's own words (16 Nov 2026, audit T32): the difference and to compare, in the nine languages — Urdu, Farsi and
     * Arabic فرق, Urdu موازنہ / مقابلہ, Arabic مقارنة, Farsi مقایسه, Hindi फ़र्क / अंतर / तुलना, Bengali পার্থক্য / তুলনা,
     * Indonesian selisih / perbedaan / bandingkan.
     */
    private val COMPARE_WORDS = listOf("farq", "fark", "farak", "difference", "compare", "comparison", "compared", "versus", "vs",
        "muqabla", "muqabala", "mawazna", "tulna", "فرق", "موازنہ", "مقابلہ", "مقارنة", "قارن", "مقایسه", "मुक़ाबला", "फ़र्क", "अंतर",
        "तुलना", "পার্থক্য", "তুলনা", "selisih", "perbedaan", "perbandingan", "bandingkan", "beda")

    /**
     * A sale or a profit over two spans, compared (16 Nov 2026, audit T32 / H-04): "is mahine aur pichle mahine ki sale ka
     * farq", "compare this month's sales with last month". A comparison word, a sale or a profit word, nobody and no product
     * named, and two different spans on the two sides of a joining word — each a span he tells ([VoiceQuestion.windowOf]) or a
     * day or a month named outright. Anything less is left to the usual reading.
     */
    private fun compareOf(heard: String, names: List<Name>, goods: List<Name>): Ask? {
        val said = words(heard)
        if (!has(said.joinToString(" "), COMPARE_WORDS)) return null
        // "Aaj aur kal ka muqabla karo" names no figure: a comparison of days is the sale's (Insights compares the sales).
        val measure = when (VoiceQuestion.classify(heard)) {
            VoiceQuestion.Kind.SALES_TODAY, VoiceQuestion.Kind.SALES_MONTH, null -> Measure.SALES
            VoiceQuestion.Kind.PROFIT_MONTH -> Measure.PROFIT
            else -> return null
        }
        if (said.any { knownName(it, names) || knownName(it, goods) }) return null
        val raw = heard.trim().split(SPACE_RUN)
        for (i in 1 until raw.size - 1) {
            if (norm(raw[i]) !in COMPARE_JOIN) continue
            val a = spanAsk(measure, raw.subList(0, i).joinToString(" ")) ?: continue
            val b = spanAsk(measure, raw.subList(i + 1, raw.size).joinToString(" ")) ?: continue
            if (a == b) continue
            // The later span first, so the difference says how the later stands against the earlier, however it was said
            // ("pichle mahine se is mahine ka muqabla").
            return if (startOf(b) > startOf(a)) Ask.Compare(b, a) else Ask.Compare(a, b)
        }
        return null
    }

    /** Where one side of a comparison begins (a day not yet come: never). */
    private fun startOf(a: Ask): Long = when (a) {
        is Ask.Period -> span(a.window).first
        is Ask.OnDays -> span(a.days)?.first ?: Long.MIN_VALUE
        else -> Long.MIN_VALUE
    }

    /** One side of a comparison: the measure over the span or the day that side names, or null when it names none or another. */
    private fun spanAsk(measure: Measure, part: String): Ask? {
        VoiceQuestion.datedOf(part)?.let { return Ask.OnDays(if (measure == Measure.SALES) DatedOf.SALES else DatedOf.PROFIT, it) }
        if (VoiceQuestion.unsupportedSpan(part)) return null
        return VoiceQuestion.windowOf(part)?.let { Ask.Period(measure, it) }
    }

    private val SPACE_RUN = Regex("\\s+")

    /**
     * Two questions in one sentence (16 Nov 2026, audit T32 / H-03): each side of an "and" read alone through the whole reading;
     * both are taken only when each side is a firm question of the book on its own ([firm], two words or more), they differ,
     * and the whole sentence was read as one of them — the other half was being dropped. "Ali Raza aur Bashir Ahmad ka
     * balance", read whole as both men, is not split.
     */
    private fun bothOf(
        heard: String, whole: Ask, names: List<Name>, topics: List<Topic>, language: String?, goods: List<Name>, before: Ask?,
        staff: List<Name>
    ): Ask? {
        val raw = heard.trim().split(SPACE_RUN)
        if (raw.size < 5) return null
        val wholeAsk = (whole as? Ask.Guessed)?.ask ?: whole
        // A card that already holds both sides of an "and" ("kas masuk dan keluar hari ini": the cashbook's money in and out,
        // the day's dealings, the day's summary, the whole book) is never split.
        if (wholeAsk is Ask.Cashbook || wholeAsk is Ask.Activity || wholeAsk == Ask.Today ||
            (wholeAsk is Ask.Status && wholeAsk.of == StatusOf.BOOK)
        ) return null
        for (i in 2 until raw.size - 2) {
            if (norm(raw[i]) !in AND_WORDS) continue
            val a = understandRead(raw.subList(0, i).joinToString(" "), names, topics, language, goods, before, staff)
            if (!firm(a)) continue
            val b = understandRead(raw.subList(i + 1, raw.size).joinToString(" "), names, topics, language, goods, before, staff)
            if (!firm(b) || a == b) continue
            if (wholeAsk != a && wholeAsk != b) continue
            return Ask.Both(a, b)
        }
        return null
    }

    /** A question of the book read outright, with nothing guessed: a half that may stand beside another ([bothOf]). */
    private fun firm(a: Ask): Boolean = when (a) {
        is Ask.Figure, is Ask.Period, is Ask.OnDays, is Ask.Person, is Ask.Collected, is Ask.Activity, is Ask.Count, is Ask.Status,
        Ask.Drawer, is Ask.Cashbook, is Ask.Returns, is Ask.Deliveries, is Ask.TopGoods, Ask.Qarz, Ask.BackupStatus -> true
        is Ask.Parties -> a.ids.isNotEmpty()
        is Ask.Goods -> a.ids.isNotEmpty()
        is Ask.Moved -> a.ids.size == 1
        is Ask.WhenPays -> a.ids.size == 1
        else -> false
    }

    /** Why a sentence is not a question of the book ([Ask.OffTopic]). */
    enum class OffTopicWhy { GENERAL, SUM }

    /**
     * Off the book (16 Nov 2026, audit T7). "500 aur 300 ka total kya hai" and "2+2 kitne hote hain" opened an entry form;
     * "cricket score" showed the shop's profile, "joke sunao" the owner's name, "Pakistan ka capital" the app's About. Only a
     * sentence that names nobody and no product of the book, and says no entry (no giving or taking verb):
     * - a sum: two figures joined by a sign (+ × * ÷, and - or / with a space on both sides: "12/11" is a date, "0300-1234567"
     *   a number), or two figures with a word of adding ("total", "jama", "jod", "sum", "plus"); a sign's sum is worked out by
     *   the calculator's own [Calc.eval], never by guessing;
     * - a topic no shop's book holds: the weather, cricket, a joke, a film, a song, politics, a capital city — and no word of
     *   the book's figures beside it ("Kis cheez ki sale mausam ke saath badhti hai" is a sales question and stays one;
     *   "mausam" alone is also the season, so only "mausam kaisa" is the weather).
     */
    private fun offTopic(heard: String, ask: Ask): Ask {
        // Talk ("kaise ho") is kept unless it carries a topic word: "joke sunao" was taken as "how are you" by the sense.
        if (ask is Ask.Voice || ask is Ask.Look || ask is Ask.CallMe || ask is Ask.Act || ask is Ask.Both || ask is Ask.Compare ||
            ask is Ask.OnDays || ask is Ask.Abilities || (ask is Ask.About && (ask.topic in STORY_TOPICS || ask.topic in FAITH_TOPICS))
        ) return ask
        if (partyOf(ask) != null || goodsOf(ask) != null) return ask
        if (VoiceEntry.parse(heard, emptyList()).isGiven != null) return ask
        // A word of the book's figures beside the signs ("12 / 11 ki sale") makes it the book's question, the slash a date's.
        val bookFigure = VoiceQuestion.classify(heard) != null
        val joined = words(heard).joinToString(" ")
        // A sum is read before a help page found by its words ("1200 aur 800 jama karo" found the business page by "jama"),
        // unless the sentence asks how ("jama kaise karun").
        if (!bookFigure && !has(joined, HOW)) {
            SIGN_SUM.find(heard)?.let { m ->
                val expr = m.value.trim()
                val result = com.innovation313.roshankhata.ui.Calc.eval(expr.replace('×', '*').replace('÷', '/').replace('x', '*').replace('X', '*'))
                return Ask.OffTopic(OffTopicWhy.SUM, expr, result)
            }
            if (FIGURE.findAll(heard).count() >= 2 && has(joined, ADDING)) return Ask.OffTopic(OffTopicWhy.SUM)
        }
        if (ask is Ask.Help) return ask
        if (has(joined, OFF_BOOK) && !bookFigure) return Ask.OffTopic(OffTopicWhy.GENERAL)
        return ask
    }

    private val FIGURE = Regex("\\p{Nd}[\\p{Nd},.]*")
    private val SIGN_SUM = Regex("\\p{Nd}[\\p{Nd},.]*(\\s*[+×*÷xX]\\s*|\\s+[-/]\\s+)\\p{Nd}[\\p{Nd},.]*")

    private val ADDING = listOf("total", "jama", "jod", "jor", "sum", "plus", "add", "ٹوٹل", "جمع", "जोड़", "जोड़ो", "टोटल",
        "مجموع", "جمع کن", "jumlah", "যোগ", "মোট কত")

    private val OFF_BOOK = listOf(
        "cricket", "کرکٹ", "ڪرڪيٽ", "क्रिकेट", "ক্রিকেট", "كريكيت", "کریکت", "kriket",
        "weather", "mausam kaisa", "mosam kaisa", "موسم کیسا", "मौसम कैसा", "الطقس", "آب و هوا", "আবহাওয়া", "cuaca",
        "joke", "jokes", "latifa", "lateefa", "لطیفہ", "لطیفے", "चुटकुला", "चुटकुले", "কৌতুক", "نكتة", "جوک", "lelucon",
        "movie", "movies", "film", "فلم", "फ़िल्म", "फिल्म", "সিনেমা", "فيلم", "فیلم",
        "song", "songs", "گانا", "गाना", "أغنية", "آهنگ", "lagu",
        "politics", "siyasat", "سیاست", "سياسة", "राजनीति", "রাজনীতি", "politik",
        "capital city", "capital of", "pakistan ka capital", "india ka capital", "daral hukumat", "darul hukumat", "دارالحکومت",
        "राजधानी", "রাজধানী", "عاصمة", "پایتخت", "ibu kota"
    )

    /**
     * A "what if" about how the app behaves (16 Nov 2026, the owner's lists 2101-3100 and 6101-7100: "agar sale price minus likhi jaye
     * to kya system rokega?" showed this month's sales, "agar chat delete kar doon ..." the day's activity). A figure of the book is no
     * answer to a hypothetical about the app: he says he cannot answer it yet (and offers what he can tell) — unless a help page was
     * found by its words, or the sentence names somebody or something of the book. Only a sentence that BEGINS with the word for "if"
     * in Urdu, Roman Urdu, Hindi, Bengali, Sindhi or Farsi (or English "if"/"what if") and runs to six words or more: Arabic's "لو"
     * (please) and Indonesian's "kalau" (how about…) are everyday asks, and a real ask starts with what it asks.
     */
    private fun whatIf(heard: String, ask: Ask, topics: List<Topic>): Ask {
        val w = words(heard)
        val joined = w.joinToString(" ")
        // "Kya app mein zakat ka hisaab ho sakta hai", "can the app make posters", "kya aap visiting card bana sakte ho" (17 Nov 2026,
        // L019, the owner's lists 2101-3100 and 6101-7100): a question about what the app can do that names one of its features is
        // answered with that feature's page, which says what it does and opens it; it was "I cannot answer this yet" or the list of
        // what he can tell. Only a page this book offers; a feature the app does not have stays unknown.
        val asksAbility = ask == Ask.Abilities || ask == Ask.Unknown || (ask is Ask.Guessed && ask.ask == Ask.Abilities)
        if (asksAbility && w.size >= 4 && (appCan(w, joined) || ask == Ask.Abilities)) capabilityPage(joined, topics)?.let { return Ask.Help(it) }
        if (ask is Ask.Help || ask == Ask.Unknown || ask is Ask.Chat || ask is Ask.Voice || (ask is Ask.About && ask.topic != AboutTopic.PRIVACY) || ask is Ask.Look ||
            ask is Ask.CallMe || ask is Ask.Abilities || ask is Ask.DidYouMean || ask is Ask.Entry || ask is Ask.Entries || ask == Ask.NoHistory
        ) return ask
        if (partyOf(ask) != null || goodsOf(ask) != null) return ask
        if (w.size < 6) return ask
        val first = if (w[0] == "what" && w[1] == "if") "if" else w[0]
        // "Can the delivery record be exported to Excel?", "is there an option for leave?": a question about what the app has, not
        // about the book — only the passive and the "is there" forms (Urdu, Roman Urdu, Hindi, and English's "can the app"); "can you
        // tell me" asks him and is kept. A feature it names with a page is that page (above, and here for a reading of the book).
        if (appCan(w, joined)) return capabilityPage(joined, topics)?.let { Ask.Help(it) } ?: Ask.Unknown
        if (first !in IF_WORDS) return ask
        // "If it is possible, tell me today's sales": a polite way to ask, not a hypothetical.
        if (has(w.take(5).joinToString(" "), IF_POLITE)) return ask
        return Ask.Unknown
    }

    private val IF_POLITE = listOf("ممکن ہو", "ہو سکے", "ہوسکے", "mumkin ho", "ho sake", "ho sakay", "if possible", "if you can", "if you could", "please",
        "براہ مہربانی", "مہربانی", "meharbani", "mehrbani", "kindly", "संभव हो", "हो सके", "মুমকিন হলে", "সম্ভব হলে", "ممکن باشد", "اگر میشه", "اگر می‌شه")

    private val ASKS_IF = setOf("کیا", "kya", "kia", "क्या").map { norm(it) }.toSet()
    private val APP_CAN = listOf("جا سکتا", "جا سکتی", "جا سکتے", "ہو سکتا", "ہو سکتی", "ہو سکتے", "موجود ہے", "موجود ہیں", "آپشن",
        "ja sakta", "ja sakti", "ja sakte", "ho sakta", "ho sakti", "ho sakte", "mojood hai", "mojood hain", "option", "optional",
        "जा सकता", "जा सकती", "जा सकते", "हो सकता", "हो सकती", "हो सकते", "मौजूद है", "मौजूद हैं", "विकल्प")

    private val IF_WORDS = setOf("if", "اگر", "agar", "agr", "अगर", "यदि", "যদি", "اگه", "جيڪڏهن", "جيكڏهن").map { norm(it) }.toSet()

    /** A question about what the app has: "kya … ho sakta hai / mojood hai" (Urdu, Roman Urdu, Hindi), "can the app …", "is there an option …". */
    private fun appCan(w: List<String>, joined: String): Boolean =
        (w[0] in ASKS_IF && has(joined, APP_CAN)) || (w[0] in EN_CAN && has(joined, EN_APP_CAN))

    private val EN_CAN = setOf("can", "does", "is", "are", "will", "could")
    private val EN_APP_CAN = listOf("the app", "this app", "an option", "a feature", "option to", "feature to", "possible to")

    /**
     * The page of the feature a question about the app names (17 Nov 2026, L019): the how-to's own thing first ([howToPage]), then
     * the features named by their own word ([byFeature]'s lists). Null when it names none, or none this book offers. A card beside
     * a way of paying is a payment's ([cameInByWay]), never the business card.
     */
    internal fun capabilityPage(joined: String, topics: List<Topic>): String? {
        howToPage(joined, topics)?.let { return it }
        val key = when {
            has(joined, ZAKAT) -> "screen_zakat"
            has(joined, SEASONS) -> "screen_seasons"
            has(joined, SCHEMES) -> "screen_schemes"
            has(joined, POSTERS) -> "screen_posters"
            has(joined, CARDS) && !cameInByWay(joined) -> "screen_card"
            has(joined, CASH_FLOW) -> "screen_cashflow"
            has(joined, LAPSED) -> "screen_lapsed"
            has(joined, BIN_WORDS) -> "screen_bin"
            has(joined, BUSINESSES) -> "faq_business"
            else -> null
        } ?: return null
        return key.takeIf { k -> topics.any { it.key == k } }
    }

    /**
     * Past talks asked for (17 Nov 2026, L019, the owner's lists: "pichli chat dikhao", "chat history", "hamari purani baatein" were the
     * day's activity): a word of talk with a word of the past or of a record. "Pichli baat phir se batao" asks him to repeat and is
     * not here (a single "baat" is no chat); a sentence with a name of the book is that person's.
     */
    internal fun pastTalk(joined: String): Boolean = has(joined, CHAT_WORDS) && has(joined, PAST_TALK)

    private val CHAT_WORDS = listOf("chat", "chats", "baat cheet", "baatcheet", "baat chit", "baatein", "baaten", "batein", "guftagu",
        "conversation", "conversations", "talks", "بات چیت", "باتیں", "گفتگو", "چیٹ", "चैट", "बातचीत", "बातें", "চ্যাট", "কথোপকথন",
        "المحادثة", "المحادثات", "الدردشة", "محادثة", "گفتگوها", "چت", "obrolan", "percakapan")
    private val PAST_TALK = listOf("history", "pichli", "pichhli", "pichle", "purani", "puraani", "previous", "past", "old", "earlier",
        "record", "saved", "save", "yaad", "پچھلی", "پچھلے", "پرانی", "سابقہ", "ریکارڈ", "محفوظ", "पिछली", "पिछले", "पुरानी", "रिकॉर्ड",
        "আগের", "পুরনো", "পুরানো", "السابقة", "القديمة", "سجل", "قبلی", "قدیمی", "تاریخچه", "sebelumnya", "lama", "riwayat")

    /**
     * Money that came in by its ways (17 Nov 2026, L004: "cash aur card se kitna aaya" opened the business card's page, "cash aur bank se
     * kitna aaya" the cashbook): a way of paying, a "by" and a word of money that came. "Card" is also the shop's business card: beside a
     * way of paying it is a payment's. The answer is the money that came in, which its card tells way by way ([Ask.Collected]).
     */
    internal fun cameInByWay(joined: String): Boolean =
        has(joined, PAY_WAYS) && has(joined, BY_WAY) && has(joined, CAME_BY_WAY) && !has(joined, PAID_OUT)

    private val PAY_WAYS = listOf("cash", "naqd", "naqad", "card", "cards", "debit card", "credit card", "online", "bank", "bank transfer",
        "wallet", "jazzcash", "jazz cash", "easypaisa", "easy paisa", "upi", "نقد", "کیش", "کارڈ", "آن لائن", "آنلائن", "بینک", "والٹ",
        "जैज़कैश", "कैश", "नकद", "कार्ड", "ऑनलाइन", "बैंक", "tunai", "kartu", "transfer")
    private val BY_WAY = listOf("se", "by", "via", "through", "سے", "से", "lewat", "melalui", "pakai")
    private val CAME_BY_WAY = listOf("aaya", "aaye", "aayi", "aai", "aya", "aye", "mila", "mile", "mili", "came", "came in", "received", "got",
        "آیا", "آئے", "آئی", "ملا", "ملے", "ملی", "आया", "आए", "आई", "मिला", "मिले", "मिली", "masuk", "diterima")

    /** Indonesian's word for the app's supplier bills (its Bills tile says "Tagihan") with "biggest" (17 Nov 2026, L004). */
    internal fun biggestTagihan(joined: String): Boolean =
        has(joined, listOf("tagihan")) && has(joined, listOf("terbesar", "paling besar", "terbanyak", "paling banyak"))

    /** Showing papers: "aaj ke sab bill dikhao", "show today's bills" (17 Nov 2026, L004). */
    /** "Tell me", "show me" (for a bare "sale batao"). */
    private val TELL_ME = listOf("batao", "bata do", "batado", "bataiye", "bataen", "tell me", "tell", "show me", "بتاؤ", "بتا دو", "بتائیں", "बताओ", "बता दो", "বলো", "বলুন", "beritahu", "sebutkan", "أخبرني", "بگو")

    private val SHOW_WORDS = listOf("dikhao", "dikha do", "dikhado", "dikhaiye", "dikhayen", "dikhana", "show", "list", "دکھاؤ", "دکھا دو",
        "دکھائیں", "दिखाओ", "दिखा दो", "दिखाइए", "দেখাও", "দেখান", "tampilkan", "اعرض", "نشان بده")

    private fun understandRead(
        heard: String, names: List<Name>, topics: List<Topic>, language: String?,
        goods: List<Name>, before: Ask?, staff: List<Name> = emptyList()
    ): Ask {
        val carried = carriedOf(before)
        // "ورقم هاتفه", "و قیمتش" after somebody's or a product's answer: the pronoun joined to the word (review O).
        if (carried != null && (partyOf(carried) != null || goodsOf(carried) != null)) {
            val bare = pronounBare(heard)
            if (bare != heard) return understandRead(bare, names, topics, language, goods, before, staff)
        }
        val ruled = sideShaped(heard, byRules(heard, names, topics, language, goods, carried, staff))
        val dated = VoiceQuestion.datedOf(heard) { w -> knownName(w, names) || knownName(w, goods) }
        if (!HudhudSense.ready || words(heard).isEmpty()) return goodsShape(heard, partOfDay(heard, limitSpan(heard, withDate(heard, dated, withCheque(heard, withCover(heard, byWay(heard, ruled), carried))), dated != null)))
        val guesses = HudhudSense.rank(
            senseHeard(heard, names, goods),
            isName = { w -> w !in VOCATIVE_SET && senseNameSlot(w, names) },
            isItem = { w -> senseNameSlot(w, goods) }
        )
        return goodsShape(heard, partOfDay(heard, limitSpan(heard, withDate(heard, dated, withCheque(heard, withCover(heard, byWay(heard, decide(heard, ruled, guesses, names, topics, language, goods, carried)), carried))), dated != null)))
    }

    /**
     * A money side said by the sentence's whole shape ([VoiceQuestion.sideStated]: "bazaar mein mera kitna paisa hai", "log mujhe kitna
     * denge") is the shop's figure where the other readers found nothing or only the privacy page (23 Nov 2026, BC-240: the rules took
     * "bazaar mein mera kitna paisa hai" for the privacy page and only the app's one shuffle of the sense set it right).
     */
    private fun sideShaped(heard: String, ruled: Ask): Ask {
        // A supplier's money asked outright (25 Nov 2026, BC-240): where the rules found a count of suppliers, a help page or nothing.
        val paying = ruled == Ask.Unknown || ruled == Ask.About(AboutTopic.PRIVACY) || ruled == Ask.Count(Thing.SUPPLIERS) ||
            (ruled is Ask.Help && !asksTheWay(heard))
        if (paying && VoiceQuestion.payableAsked(heard)) return Ask.Figure(VoiceQuestion.Kind.TO_GIVE)
        if (ruled != Ask.Unknown && ruled != Ask.About(AboutTopic.PRIVACY) && !(ruled is Ask.Help && !asksTheWay(heard))) return ruled
        if (!VoiceQuestion.sideStated(heard)) return ruled
        val k = VoiceQuestion.classify(heard) ?: return ruled
        return if (k == VoiceQuestion.Kind.TO_GET || k == VoiceQuestion.Kind.TO_GIVE) Ask.Figure(k) else ruled
    }

    /**
     * A day or a month named outright (16 Nov 2026, audit T31): the sale, the profit, the money in, the day's dealings or a
     * customer's paid or taken are asked over that day ([Ask.OnDays]) — they were this month's figure ("15 tareekh ki sale"),
     * or a choice of spans ("parson ki sale"). A sentence that plainly asks the sale or the profit and names nobody is read so
     * whatever else it was taken for ("pichle jumme ki sale" was the last counter bill). "15 tareekh ko kya hua" asks what
     * happened that day, not the date. Any other question with a day said is left to [limitSpan]: it is told he cannot give
     * that span, never answered over another.
     */
    private fun withDate(heard: String, dated: VoiceQuestion.Dated?, ask: Ask): Ask {
        dated ?: return ask
        val inner = (ask as? Ask.Guessed)?.ask ?: ask
        val clock = if (inner == Ask.Clock && dated.month == null) Ask.OnDays(DatedOf.ACTIVITY, dated) else null
        val plain = if (partyOf(inner) != null || goodsOf(inner) != null) null else when (VoiceQuestion.classify(heard)) {
            VoiceQuestion.Kind.SALES_TODAY, VoiceQuestion.Kind.SALES_MONTH -> Ask.OnDays(DatedOf.SALES, dated)
            VoiceQuestion.Kind.PROFIT_MONTH -> Ask.OnDays(DatedOf.PROFIT, dated)
            else -> null
        }
        val read: Ask = datedAsk(inner, dated) ?: clock ?: plain ?: return ask
        return if (ask is Ask.Guessed) Ask.Guessed(read) else read
    }

    /** [a] over a day or a month named outright, when it is a question asked over a span of days that can be. */
    private fun datedAsk(a: Ask, d: VoiceQuestion.Dated): Ask? = when (a) {
        is Ask.Figure -> when (a.kind) {
            VoiceQuestion.Kind.SALES_TODAY, VoiceQuestion.Kind.SALES_MONTH -> Ask.OnDays(DatedOf.SALES, d)
            VoiceQuestion.Kind.PROFIT_MONTH -> Ask.OnDays(DatedOf.PROFIT, d)
            else -> null
        }
        is Ask.Period -> Ask.OnDays(if (a.measure == Measure.SALES) DatedOf.SALES else DatedOf.PROFIT, d)
        is Ask.Collected -> Ask.OnDays(DatedOf.COLLECTED, d)
        is Ask.Activity -> Ask.OnDays(DatedOf.ACTIVITY, d)
        is Ask.Moved -> Ask.OnDays(if (a.got) DatedOf.PAID else DatedOf.TOOK, d, a.ids)
        is Ask.SpanLimit -> a.options.firstNotNullOfOrNull { datedAsk(it, d) }
        is Ask.Now -> datedAsk(a.ask, d)
        else -> null
    }

    /**
     * The sentence asks the sale, the profit or money (the eight figures' sale, profit and debt words, or money), and says no word
     * of goods or stock: "kharab" in it is the weather's or the day's, not goods written off.
     */
    private fun asksMoneyNotGoods(heard: String, mind: HudhudMind.Read): Boolean {
        if (mind.any(HudhudMind.C.GOODS) || mind.any(HudhudMind.C.STOCK)) return false
        val kind = VoiceQuestion.classify(heard)
        return kind == VoiceQuestion.Kind.SALES_TODAY || kind == VoiceQuestion.Kind.SALES_MONTH || kind == VoiceQuestion.Kind.PROFIT_MONTH ||
            mind.any(HudhudMind.C.MONEY)
    }

    /** A named product asked in a shape the facet words miss ("Urea kitne ki hai"): the price, the cost or the shelf it asks. */
    private fun goodsShape(heard: String, ask: Ask): Ask {
        if (ask !is Ask.Goods) return ask
        val facet = HudhudEveryday.goodsFacet(heard, ask.facet) ?: return ask
        return ask.copy(facet = facet)
    }

    /**
     * A part of the day ("this morning", "shaam tak ki sale") is answered with the day's figure, because the book keeps no hour
     * (15 Nov 2026): told as a guess ("taken as …") unless it runs up to now ("since morning"), when the day's figure is the exact one.
     */
    private fun partOfDay(heard: String, ask: Ask): Ask {
        val part = VoiceQuestion.partOfDay(heard) ?: return ask
        if (part.toNow || !spanMatters(ask)) return ask
        val today = (ask is Ask.Figure && ask.kind == VoiceQuestion.Kind.SALES_TODAY) || spanOf(ask) == VoiceQuestion.Window.TODAY
        return if (today) Ask.Guessed(ask) else ask
    }

    // ------------------------------------------------------------------------------------- which cheques

    /**
     * "Kis ka cheque bounce hua" (15 Nov 2026, the catalog's cheques gap): a cheques question that says the cheque came back or was
     * paid asks for those, not for the ones still waiting. Only a question the rules or the sense already read as the cheques is
     * narrowed, and only by a word of the past ("cleared", "wasool hua"): "kab clear hoga" asks about one that has not.
     */
    private fun withCheque(heard: String, ask: Ask): Ask = when {
        ask is Ask.Now -> Ask.Now(withCheque(heard, ask.ask))
        ask is Ask.Guessed -> withCheque(heard, ask.ask).let { if (it == ask.ask) ask else it }
        ask is Ask.Status && ask.of == StatusOf.CHEQUES && ask.cheques == null ->
            chequeState(words(heard).joinToString(" "))?.let { Ask.Status(StatusOf.CHEQUES, it) } ?: ask
        else -> ask
    }

    /** The cheques a sentence names by what became of them; null for the ones still waiting. */
    internal fun chequeState(joined: String): ChequeState? = when {
        has(joined, BOUNCED_WORDS) -> ChequeState.BOUNCED
        has(joined, CLEARED_WORDS) -> ChequeState.CLEARED
        else -> null
    }

    private val ENTRY_DETAIL_WORDS = listOf("note", "notes", "memo", "number", "no", "reference", "ref", "tid", "who wrote", "written by",
        "who entered", "who made", "how was it paid", "paid by", "payment method", "method", "what was written", "what did it say",
        "yaaddasht", "kis ne likha", "kis ne likhi", "kisne likha", "kisne likhi", "kis ne dali", "kaise ada", "kaise di", "tareeqa",
        "tariqa", "tarika", "kya likha", "kya likhi", "kya likha tha", "kya likhi thi", "نوٹ", "نمبر", "ریفرنس", "حوالہ", "کس نے لکھی",
        "کس نے لکھا", "کسی نے لکھا", "کیسے ادا", "طریقہ", "کیا لکھا", "नोट", "नंबर", "नम्बर", "रेफरेंस", "किसने लिखा", "किस ने लिखा",
        "किसने लिखी", "कैसे दिया", "तरीका", "क्या लिखा", "নোট", "নম্বর", "রেফারেন্স", "কে লিখেছে", "কিভাবে দিয়েছে", "পদ্ধতি", "কী লেখা",
        "ملاحظة", "ملاحظات", "رقم", "مرجع", "من كتب", "من كتبه", "طريقة الدفع", "كيف دفع", "ماذا كتب", "یادداشت", "شماره", "چه کسی نوشت",
        "چه کسی ثبت", "روش پرداخت", "چه نوشته", "نوٽ", "ڪنهن لکيو", "ڪيئن ادا", "طريقو", "catatan", "nomor", "referensi",
        "siapa yang menulis", "siapa yang mencatat", "cara bayar", "dibayar lewat", "apa yang ditulis")
    private val BOUNCED_WORDS = listOf("bounce", "bounced", "bounces", "bouncing", "dishonour", "dishonoured", "dishonor", "dishonored",
        "rejected", "wapas aa gaya", "wapas aaya", "wapas aayi", "wapas aa gayi", "wapas aaye", "wapas aa gaye", "wapis aa gaya",
        "wapis aaya", "wapis aa gayi", "return hua", "return ho gaya", "return aaya", "baounce", "باؤنس", "بائونس", "باونس",
        "واپس آ گیا", "واپس آگیا", "واپس آیا", "واپس آئی", "واپس آ گئی", "واپس آئے", "ریجیکٹ", "बाउंस", "बाउन्स", "वापस आ गया",
        "वापस आया", "वापस आए", "বাউন্স", "ফেরত এসেছে", "ارتد", "ارتدت", "مرتجع", "مرتجعة", "مرتد", "برگشت", "برگشتی",
        "برگشت خورد", "ditolak", "ditolak bank", "tolak")
    private val CLEARED_WORDS = listOf("cleared", "clear ho gaya", "clear ho gaye", "clear ho gayi", "clear hua", "clear hue",
        "clear ho chuka", "wasool hua", "wasool hue", "wasool ho gaya", "wasool ho gaye", "wasool ho gayi", "vasool hua",
        "cash ho gaya", "cash ho gaye", "cash hua", "settled", "کلیئر ہو گیا", "کلیئر ہوا", "کلیئر ہو گئے", "کلیئر ہو گئی",
        "وصول ہو گیا", "وصول ہو گئے", "وصول ہو گئی", "وصول ہوا", "وصول ہوئے", "क्लियर हो गया", "क्लियर हुआ", "क्लियर हो गए",
        "वसूल हो गया", "تم صرفه", "تم صرفها", "تم تحصيله", "تم تحصيلها", "ক্লিয়ার হয়েছে", "sudah cair", "sudah dicairkan",
        "dicairkan", "پاس شد", "پاس شده", "وصول شد")

    // ------------------------------------------------------------------------------------- staff, plans and cheques: the records

    /**
     * A staff member's pay, what was paid them, who was away, who they are; a customer's plan or his cheques (15 Nov 2026, the
     * catalog's GAP group "staff, plans, cheques"). Taken only from words that are the record's own, and only where the
     * sentence is about people at work or about a plan or a cheque of a named customer; any other sentence is read as before.
     * The staff are not in the customers' list: the caller gives their names apart ([staffIds], what [understand]'s `staff` list
     * names), so a word that is nobody's name is never taken for one. A staff question that names nobody, says no day and no
     * "who" is the staff's dues as before ([Ask.Status] of the staff).
     */
    internal fun recordOf(
        said: List<String>, joined: String, mind: HudhudMind.Read, window: VoiceQuestion.Window?,
        named: List<Long>, product: List<Long>, before: Ask?, staffIds: List<Long>
    ): Ask? {
        if (product.isNotEmpty()) return null
        val who = staffIds
        // A staff word by sound alone is no staff word ("karna" sounds like a worker's name): only the word itself.
        val staffWord = mind.said.indices.any { it !in mind.bySound && HudhudMind.C.STAFF in mind.at(it) } || has(joined, STAFF_EXTRA)
        val salary = mind.has(HudhudMind.C.SALARY)
        val customerSide = mind.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER, HudhudMind.C.GOODS, HudhudMind.C.BILL, HudhudMind.C.INVOICE) ||
            // An invoice word the lexicon lacks ("انوائسز ... نہیں آیا", review O) is a paper's money, never somebody who did not come.
            has(joined, INVOICE_WORDS)
        // Staff said with no name, no day and no "who" is the staff's dues as before (the owner's own sentences read so): a record needs one of the three.
        fun staffRec(f: RecordFacet, strong: Boolean = false): Ask? =
            if (!strong && who.isEmpty() && window == null && !mind.has(HudhudMind.C.WHO)) null else Ask.Record(f, who, window = window)
        // What was changed and by whom: the audit log's own name, or "who" with a verb of doing that the lexicon lacks (Indonesian "mengedit").
        if (has(joined, AUDIT_WORDS) || (mind.has(HudhudMind.C.WHO) && has(joined, AUDIT_VERBS) && !customerSide)) return Ask.Activity(window)
        // Who was away, or who is here: "aaj kaun absent hai", "Imran ki chhuttiyan", "who is present today".
        if (!customerSide) {
            val askedWho = staffWord || mind.any(HudhudMind.C.WHO, HudhudMind.C.WHICH, HudhudMind.C.HOW_MANY, HudhudMind.C.ALL) || who.isNotEmpty()
            val away = has(joined, ABSENT_WORDS) || (askedWho && has(joined, LEAVE_WORDS)) ||
                (has(joined, ATTENDANCE_NOUNS) && (window != null || staffWord || who.isNotEmpty()))
            val here = has(joined, PRESENT_WORDS) && (staffWord || (mind.has(HudhudMind.C.WHO) && window != null))
            if (away || here) return staffRec(RecordFacet.ATTENDANCE, strong = has(joined, ABSENT_WORDS) || here)
        }
        // "What is Kashif's pay": the word "pay" is also what a customer does, so it counts only beside a name no customer has.
        val payNoun = who.isNotEmpty() && named.isEmpty() && has(joined, PAY_NOUN_WORDS) && !has(joined, AGED_WORDS)
        val staffy = staffWord || salary || has(joined, ADVANCE_WORDS) || payNoun
        if (staffy && !customerSide) {
            val person = has(joined, ROLE_WORDS) || has(joined, JOIN_WORDS) || has(joined, LEFT_WORDS) ||
                (mind.has(HudhudMind.C.SINCE_WHEN) && staffWord) ||
                (staffWord && (mind.has(HudhudMind.C.PHONE) || has(joined, STAFF_PHONE_WORDS)))
            if (person && !salary) return staffRec(RecordFacet.STAFF_PERSON, strong = true)
            val paidSaid = (mind.has(HudhudMind.C.PAYMENT) && !payNoun) || has(joined, ADVANCE_WORDS) || has(joined, PAID_WORDS)
            val dueSaid = mind.has(HudhudMind.C.PENDING) || has(joined, STAFF_DUE_WORDS)
            if (paidSaid && !dueSaid) staffRec(RecordFacet.STAFF_PAID)?.let { return it }
            // An advance said by its word with no name and no day (review O: "staff ko advance kitna diya" was a count of the staff):
            // the staff's standing, which the held-out set reads it as.
            if (has(joined, ADVANCE_WORDS) && staffWord) return Ask.Status(StatusOf.STAFF)
            if ((salary || payNoun) && who.isNotEmpty()) return staffRec(RecordFacet.STAFF_PAY)
        }
        // "Imran ka role", "kab join kiya", "who is the manager": a name or a "who" and a word only a person at work has, without the word "staff".
        val personWord = has(joined, ROLE_WORDS) || has(joined, JOIN_WORDS) || has(joined, LEFT_WORDS)
        if (!customerSide && !staffy && personWord && ((who.isNotEmpty() && named.isEmpty()) || ((mind.has(HudhudMind.C.WHO) || has(joined, WHO_EXTRA)) && has(joined, ROLE_WORDS)))) {
            return staffRec(RecordFacet.STAFF_PERSON, strong = true)
        }
        // "Imran ko kitna diya" with nobody of that name among the customers: the app looks among the staff.
        if (!customerSide && who.isNotEmpty() && named.isEmpty() && !staffy && mind.has(HudhudMind.C.PAYMENT) && mind.has(HudhudMind.C.HOW_MANY) &&
            !mind.has(HudhudMind.C.CASHBOOK)
        ) return staffRec(RecordFacet.STAFF_PAID)
        // A plan of a customer, and plans that were closed: "Ali ki qist kab aani hai", "plan kab band hua".
        if ((mind.has(HudhudMind.C.PLAN) || has(joined, PLAN_EXTRA)) && !salary) {
            val ids = named.ifEmpty { if (mind.has(HudhudMind.C.HIS)) before?.let { partyOf(it) } ?: emptyList() else emptyList() }
            if (has(joined, PLAN_CLOSED_WORDS) || (has(joined, PLAN_ENDED_STEMS) && has(joined, PLAN_OVER_WORDS) && !has(joined, PAID_WORDS))) {
                return Ask.Record(RecordFacet.PLAN_CLOSED, ids, window = window)
            }
            if (ids.isNotEmpty()) return Ask.Record(RecordFacet.PLAN, ids, window = window)
        }
        // A customer's cheques with the day each was written and settled: "Ali ka cheque kab clear hua".
        if (mind.has(HudhudMind.C.CHEQUE) && !salary && !has(joined, CHECK_AS_VERB)) {
            val ids = named.ifEmpty { if (mind.has(HudhudMind.C.HIS)) before?.let { partyOf(it) } ?: emptyList() else emptyList() }
            if (ids.isNotEmpty()) return Ask.Record(RecordFacet.CHEQUE, ids, window = window)
        }
        return null
    }

    /**
     * A customer (or a supplier) and a product named in one question (17 Nov 2026, audit L004): what he took of it ([Ask.Took]). The
     * product is looked for only among the words that are not his name, so "Urea Traders" stays the man; money said ("Urea ke paise kab
     * dega", "payment") is his payment, two questions joined by "and" are read apart ([bothOf]), and a sentence that names no product
     * is read as before.
     */
    private fun tookOf(
        said: List<String>, m: HudhudMind.Read, named: List<Long>, names: List<Name>, goods: List<Name>, topics: List<Topic>,
        window: VoiceQuestion.Window?
    ): Ask? {
        if (goods.isEmpty() || m.any(HudhudMind.C.PAYMENT, HudhudMind.C.MONEY) || said.any { it in AND_WORDS }) return null
        val his = names.filter { it.id in named }.flatMap { nameWords(it.name) }.map { NameSearch.fold(it) }.toSet()
        val rest = said.filter { NameSearch.fold(it) !in his }
        if (rest.isEmpty()) return null
        val product = whoIsNamed(rest, goods, topics, m, window)
        return if (product.isEmpty()) null else Ask.Took(named, product, window)
    }

    // -- The staff: away, here, paid, who they are (words in the nine languages; Sindhi, Bengali and Farsi wanting a native read).
    private val ABSENT_WORDS = listOf("absent", "absentee", "absentees", "absence", "not present", "did not come", "didn't come", "not come",
        "ghair hazir", "ghair haazir", "gair hazir", "gair haazir", "ghairhazir", "hazir nahi", "haazir nahi", "nahi aaya", "nahi aya",
        "nahin aaya", "nahi aaye", "nahi aayi", "aadha din", "adha din", "half day", "half-day", "غیر حاضر", "غیرحاضر", "غائب",
        "حاضر نہیں", "نہیں آیا", "نہیں آئے", "نہیں آئی", "آدھا دن", "آدھے دن", "غير حاضر", "اڌ ڏينهن", "نه آيو", "نه آيا", "غياب", "غائبين",
        "لم يحضر", "لم يأت", "نصف يوم", "غایب", "غایبین", "غیبت", "نیامده", "نصف روز", "نیم روز", "अनुपस्थित", "गैरहाज़िर", "गैरहाजिर",
        "नहीं आया", "नहीं आए", "नहीं आई", "आधा दिन", "অনুপস্থিত", "আসেনি", "অর্ধ দিন", "আধা দিন", "absen", "tidak masuk", "tidak hadir",
        "setengah hari", "غاب", "غابوا", "تغيب")
    /** The attendance by its own name: a day or a person must come with it ("حضور" is also how a respected man is addressed). */
    private val ATTENDANCE_NOUNS = listOf("attendance", "hazri", "haazri", "حاضری", "حاضري", "हाज़री", "हाजरी", "উপস্থিতি", "الحضور", "حضور", "حضور و غیاب",
        "kehadiran", "absensi")
    /** The audit log by its name in each language, and the verbs of doing that name who changed what. */
    private val AUDIT_WORDS = listOf("audit", "audit log", "آڈٹ", "آڊٽ", "ऑडिट", "অডিট", "تدقيق", "سجل التدقيق", "ممیزی", "log audit", "who did what", "kisne kya kiya",
        "kis ne kya kiya", "کس نے کیا کیا", "किसने क्या किया", "কে কী করেছে", "من فعل ماذا", "چه کسی چه کرد")
    private val AUDIT_VERBS = listOf("mengedit", "menghapus", "mengubah", "diedit", "diubah", "dihapus", "melakukan", "dilakukan", "ubah", "hapus")
    /** A holiday or a leave: a word a shop may also say of itself, so only in a sentence that asks who. */
    private val LEAVE_WORDS = listOf("on leave", "leave", "leaves", "chutti", "chhutti", "chuttiyan", "chhuttiyan",
        "چھٹی", "چھٹیاں", "موڪل", "موڪلون", "إجازة", "اجازة", "مرخصی", "छुट्टी", "छुट्टियां", "छुट्टियाँ", "ছুটি", "ছুটিতে", "izin", "cuti", "libur")
    private val PRESENT_WORDS = listOf("present", "hazir", "haazir", "حاضر", "हाज़िर", "हाजिर", "উপস্থিত", "حاضر", "hadir", "حضر")
    private val ADVANCE_WORDS = listOf("advance", "advances", "peshgi", "peshgee", "پیشگی", "ایڈوانس", "अग्रिम", "एडवांस", "अडवांस", "অগ্রিম",
        "অ্যাডভান্স", "ايڊوانس", "ايڊوانس", "سلفة", "سلف", "مساعده گرفت", "مساعده گرفته", "مساعده دادیم", "مساعده داده", "پیش‌پرداخت", "پیش پرداخت", "avans", "uang muka", "kasbon")
    private val PAID_WORDS = listOf("di", "dee", "given", "gave", "give", "دی", "दी", "دادم", "دادیم", "dibayar", "dibayarkan", "دفعت", "أعطيت",
        "ڏني", "ڏنو", "ڏنا", "দিয়েছি", "দিলাম", "دفعنا", "دفعت", "دفع", "أعطينا", "اعطينا", "سددنا", "صرفنا", "المدفوعة", "مدفوعة", "المدفوعات", "مدفوعات",
        "دادند", "پرداخت کردیم", "پرداخت شده", "paid", "did we pay", "did i pay", "have we paid", "we paid")
    private val STAFF_DUE_WORDS = listOf("baqi", "baaqi", "bakaya", "due", "pending", "remaining", "left", "outstanding", "owed", "deni hai",
        "dena hai", "dene hain", "باقی", "بقایا", "देना है", "देनी है", "बाकी", "बकाया", "বাকি", "বকেয়া", "متبقي", "باقي", "مستحق", "باقی‌مانده",
        "sisa", "tersisa", "kurang")
    private val ROLE_WORDS = listOf("role", "designation", "ohda", "ohdah", "manager", "cashier", "عہدہ", "عہدے", "رول", "منیجر", "کیشئر", "भूमिका",
        "ओहदा", "पद", "रोल", "मैनेजर", "ক্যাশিয়ার", "ম্যানেজার", "ভূমিকা", "পদ", "منصب", "وظيفة", "سمت", "نقش", "جایگاه", "عهدو", "دور", "مدير", "manajernya", "المدير", "مدیر",
        "مئنيجر", "peran", "jabatan", "kasir", "manajer")
    private val JOIN_WORDS = listOf("join", "joined", "joining", "bharti", "bhartee", "shamil hua", "جوائن", "شامل ہوا", "ज्वाइन", "जॉइन", "भर्ती",
        "যোগ দিয়েছে", "شامل ٿيو", "جوائن ڪيو", "انضم", "التحق", "استخدم", "استخدام شد", "bergabung", "masuk kerja", "mulai kerja")
    private val LEFT_WORDS = listOf("quit", "resigned", "chhod diya", "chhor diya", "chor diya", "kaam chhod", "nikal diya", "active", "inactive",
        "still working", "working here", "kaam par hai", "ڪم تي آهي", "काम पर है", "چھوڑ دیا", "کام چھوڑ", "نکال دیا", "کام پر ہے", "छोड़ दिया", "काम छोड़", "निकाल दिया", "ছেড়ে দিয়েছে",
        "استقال", "ما زال يعمل", "ترک کرد", "استعفا", "هنوز کار می کند", "এখনও কাজ করছে", "berhenti", "resign", "masih kerja")
    /** Staff by a plural or a form the lexicon lacks. */
    /** "Who is" in Farsi, a word the lexicon does not give as a question's "who". */
    private val WHO_EXTRA = listOf("کیست", "کیه")
    private val STAFF_EXTRA = listOf("staf", "کارمندان", "کارکنان", "للموظفين", "الموظفين", "ملازمان",
        // Urdu's "staff" as a body (review O: "عملے کو ایڈوانس کتنا دیا" was money collected).
        "عملے", "عملہ", "عملے کو")
    private val STAFF_PHONE_WORDS = listOf("أرقام", "ارقام", "هاتف", "شماره", "شماره ها", "nomor", "telepon", "نمبرز")
    /** "Pay" and "earns" as the staff's: only beside a name no customer has ([recordOf]). */
    private val PAY_NOUN_WORDS = listOf("pay", "wage", "wages", "earn", "earns", "earning", "earnings", "kamata", "kamaye", "kamai", "کماتا", "کماتی",
        "कमाता", "يكسب", "درآمد", "উপার্জন")
    /** A small word a pay question carries that is no part of a name. */
    // -- Plans closed: only a word of the past or the adjective ("kab khatam hogi" is a plan still running).
    private val PLAN_CLOSED_WORDS = listOf("closed", "close", "completed", "finished", "ended", "paid off", "fully paid", "band plan", "band plans",
        "band hone", "بند ہونے", "بند ٿي", "بند ٿيل", "ختم ٿي ويون", "ختم ٿي", "مڪمل ٿي", "پوريون ٿي ويون", "مغلقة", "المغلقة", "أغلقت", "انتهت",
        "اكتملت", "مكتملة", "المنتهية", "منتهية", "بسته شد", "بسته شده", "تمام شد", "تمام شده", "کامل شد", "کامل شده", "بسته", "बंद हो चुके",
        "বন্ধ প্ল্যান", "বন্ধ প্ল্যানগুলো", "ditutup", "selesai", "lunas", "sudah lunas")
    /** A plan's end said in two parts, in any order: what ended ("band", "khatam", "poora") and that it is over ("hua", "ho gaye"). */
    private val PLAN_ENDED_STEMS = listOf("band", "khatam", "khatm", "mukammal", "pura", "poora", "puri", "poori", "complete", "ختم", "بند", "مکمل",
        "پورا", "پوری", "पूरा", "पूरी", "खत्म", "बंद", "समाप्त", "বন্ধ", "শেষ", "সম্পূর্ণ")
    private val PLAN_OVER_WORDS = listOf("hua", "hui", "hue", "gaya", "gayi", "gaye", "gayin", "chuka", "chuki", "chuke", "ہوا", "ہوئی", "ہوئے", "گیا",
        "گئی", "گئے", "گئیں", "چکا", "چکی", "چکے", "हुआ", "हुई", "हुए", "गया", "गई", "गए", "गईं", "चुका", "चुके", "হয়েছে", "হলো", "গেছে", "ويون", "ٿيو")
    /** "Check" as a thing to do, not a cheque ("Ali Raza ki limit check karo", "लिमिट चेक करो", "لمٹ چیک کرو"): a cheque word followed by a verb of doing. */
    private val CHECK_AS_VERB = listOf("check karo", "check kar do", "check kardo", "check kijiye", "check kijye", "check kariye", "check karen", "check kare", "check krna",
        "check karna", "چیک کرو", "چیک کر دو", "چیک کریں", "چیک کیجیے", "چیک کیجئے", "چیک کرنا", "चेक करो", "चेक कर दो", "चेक करें", "चेक कीजिए", "चेक करना",
        "چيڪ ڪريو", "چيڪ ڪر", "চেক করো", "চেক কর", "চেক করুন")
    /** A plan by a word the lexicon has not (Urdu and Hindi "plan", Hindi "yojana", Farsi "tarh"). */
    private val PLAN_EXTRA = listOf("پلان", "پلانز", "प्लान", "योजना", "طرح", "طرح ها", "خطط", "الخطط", "প্ল্যানগুলো", "কিস্তিতে")

    // ------------------------------------------------------------------------------------- how long the shelf lasts

    /**
     * "Urea kitne din chalegi?" (15 Nov 2026, #30): a question about a named product's shelf — its stock or all of it — that
     * also asks for how long it lasts is the shelf's cover ([GoodsFacet.COVER], [StockCover]). Only a stock, overview, cost or sold
     * reading is turned ("in a month" reads as a sale's span): "kafi mehnga" stays the price, "kitne din pehle aaya" the last delivery. A product carried from
     * the answer before is the product when the sentence names none and asks nothing else (a bare "kitne din chalega?").
     */
    private fun withCover(heard: String, ask: Ask, carried: Ask?): Ask {
        val joined = words(heard).joinToString(" ")
        val covers = asksCover(joined)
        return when {
            !covers -> ask
            ask is Ask.Goods && (ask.facet == GoodsFacet.OVERVIEW || ask.facet == GoodsFacet.STOCK || ask.facet == GoodsFacet.COST ||
                ask.facet == GoodsFacet.SOLD) ->
                Ask.Goods(ask.ids, GoodsFacet.COVER)
            // A guess whose cover reading is sure is no guess any more.
            ask is Ask.Guessed -> withCover(heard, ask.ask, carried).let { if (it == ask.ask) ask else it }
            ask is Ask.NeedGoods && (ask.facet == GoodsFacet.STOCK) -> Ask.NeedGoods(GoodsFacet.COVER)
            // No product said: the one the talk was about, else the products to choose from (told, not asked). A sentence with a
            // lasting word and a time word is never a date (Clock), a language (About), a drawer or a figure of the shop; the
            // readings that name nobody and nothing give way.
            namesNothing(ask) -> if (carried is Ask.Goods && carried.ids.size == 1) Ask.Goods(carried.ids, GoodsFacet.COVER) else Ask.NeedGoods(GoodsFacet.COVER)
            else -> ask
        }
    }

    private fun namesNothing(ask: Ask): Boolean = ask == Ask.Unknown || ask == Ask.Clock || ask is Ask.About || ask is Ask.Drawer ||
        // "Expiry wala maal kab khatam hoga" is the expiring batches, not a product's shelf. Money owed is never the shelf either
        // (review O: "kin logon ne kaafi dino se paisa nahi diya" holds "kaafi" and "dino" and was taken for how long goods last).
        (ask is Ask.Figure && ask.kind != VoiceQuestion.Kind.EXPIRING && ask.kind !in MONEY_OWED_FIGURES) || ask is Ask.Count || ask is Ask.Today || (ask is Ask.WhenPays && ask.ids.isEmpty())

    /**
     * Whether a sentence asks how long the shelf lasts: a word for lasting with a word for time ("kitne din chalega",
     * "how long will it last", "will it run out in a week"), a run-out word with a "when" ("kab khatam hogi"), or the
     * phrase "days of stock" ("kitne din ka stock"). A lasting word alone ("kafi", "enough") is not enough: it is the
     * stock's "is there enough" or the price's "quite dear".
     */
    internal fun asksCoverForTest(joined: String): Boolean = asksCover(words(joined).joinToString(" "))

    internal fun asksCover(joined: String): Boolean =
        has(joined, COVER_PHRASES) ||
            (has(joined, LASTING) && has(joined, FOR_TIME)) ||
            (has(joined, RUNS_OUT) && has(joined, WHEN_WORDS)) ||
            (has(joined, listOf("how many days")) && !has(joined, listOf("since", "ago", "old", "passed")))

    /**
     * What the last question carries on as (15 Nov 2026): an answer that said "this is how it stands now" is the question it
     * answered, a choice of spans is the first of them (so "aur pichle saal?" after it asks the same thing over that year),
     * a guess is what it guessed. Without this the next sentence could not find the customer or the product the card was about.
     */
    private fun carriedOf(before: Ask?): Ask? = when (before) {
        is Ask.Now -> carriedOf(before.ask)
        is Ask.SpanLimit -> carriedOf(before.options.firstOrNull())
        is Ask.Guessed -> carriedOf(before.ask)
        // The talk goes on from the question of the last half, the later span, or the question asked of a named day
        // ("aur kal ki?" after "15 tareekh ki sale" is yesterday's sale) (16 Nov 2026).
        is Ask.Both -> carriedOf(before.second)
        is Ask.Compare -> carriedOf(before.first)
        is Ask.OnDays -> when (before.of) {
            DatedOf.SALES -> Ask.Period(Measure.SALES, VoiceQuestion.Window.TODAY)
            DatedOf.PROFIT -> Ask.Period(Measure.PROFIT, VoiceQuestion.Window.TODAY)
            DatedOf.COLLECTED -> Ask.Collected(VoiceQuestion.Window.TODAY)
            DatedOf.ACTIVITY -> Ask.Activity(VoiceQuestion.Window.TODAY)
            DatedOf.PAID, DatedOf.TOOK -> Ask.Moved(before.ids, VoiceQuestion.Window.TODAY, before.of == DatedOf.PAID)
        }
        else -> before
    }

    // ------------------------------------------------------------------------------------- the spans he can give

    /**
     * Asks whose answer is one sum, or a few rows chosen in the book (a product's quantity, the best sellers, what one
     * customer paid), and so can be given over any span, however long. The others list the day's rows — entries,
     * invoices, bills, tickets, deliveries, the cashbook, returns — or count what the phone keeps for forty days only
     * (the calculator, the app's opens), and are given a day, a week or a month at a time (lesson 70: a phone reads a big
     * answer 2 MB at a time, running the query again for each).
     */
    private fun longOk(a: Ask): Boolean =
        a is Ask.Period || a is Ask.Moved || a is Ask.TopGoods || a is Ask.Took || a is Ask.Trend || (a is Ask.Goods && (a.facet == GoodsFacet.SOLD || a.facet == GoodsFacet.BUYERS)) ||
            (a is Ask.Figure && spanMatters(a)) // the sales and profit figures: Period, over any span

    /** What a count counts over a span of days; the people, the products and the staff are the same over any span. */
    private val COUNTED_OVER_A_SPAN = setOf(Thing.ENTRIES, Thing.INVOICES, Thing.BILLS, Thing.TICKETS, Thing.CALCULATIONS, Thing.APP_OPENS)

    /** Whether what the ask answers changes with the span of days said. */
    private fun spanMatters(a: Ask): Boolean = when (a) {
        is Ask.Figure -> a.kind == VoiceQuestion.Kind.SALES_TODAY || a.kind == VoiceQuestion.Kind.SALES_MONTH ||
            a.kind == VoiceQuestion.Kind.PROFIT_MONTH
        is Ask.Period, is Ask.Moved, is Ask.Deliveries, is Ask.Activity, is Ask.Collected, is Ask.Cashbook, is Ask.Returns,
        is Ask.TopGoods, is Ask.Took, is Ask.Trend -> true
        // The counter's books by the day (15 Nov 2026): the last bill, the last count and the last close name no span.
        is Ask.Counter -> a.facet != CounterFacet.LAST_TICKET && a.facet != CounterFacet.LAST_COUNT
        is Ask.Count -> a.thing in COUNTED_OVER_A_SPAN
        // Who bought it is a sum over the days asked as much as how much sold (16 Nov: it was answered "as it stands now").
        is Ask.Goods -> a.facet == GoodsFacet.SOLD || a.facet == GoodsFacet.BUYERS
        else -> false
    }

    /** The span the ask names, when it names one (the app words a choice of spans by them). */
    fun spanOf(a: Ask): VoiceQuestion.Window? = when (a) {
        is Ask.Period -> a.window
        is Ask.Moved -> a.window
        is Ask.Count -> a.window
        is Ask.Deliveries -> a.window
        is Ask.Activity -> a.window
        is Ask.Collected -> a.window
        is Ask.Cashbook -> a.window
        is Ask.Returns -> a.window
        is Ask.TopGoods -> a.window
        is Ask.Counter -> a.window
        is Ask.Goods -> a.window
        is Ask.Took -> a.window
        is Ask.Trend -> a.window
        else -> null
    }

    /** The same question over another span of days. */
    private fun over(a: Ask, w: VoiceQuestion.Window): Ask = when (a) {
        is Ask.Figure -> figureOver(a.kind, w)
        // (No Ask.Period here: a sentence that names a span he gives names no span he cannot, so a Period never reaches
        // [limitSpan]; a chip's ask is kept whole by [keepWithinReach].)
        is Ask.Moved -> a.copy(window = w)
        is Ask.Count -> a.copy(window = w)
        is Ask.Deliveries -> a.copy(window = w)
        is Ask.Activity -> a.copy(window = w)
        is Ask.Collected -> a.copy(window = w)
        is Ask.Cashbook -> a.copy(window = w)
        is Ask.Returns -> a.copy(window = w)
        is Ask.TopGoods -> a.copy(window = w)
        is Ask.Counter -> a.copy(window = w)
        is Ask.Goods -> a.copy(window = w)
        is Ask.Took -> a.copy(window = w)
        is Ask.Trend -> a.copy(window = w)
        else -> a
    }

    /** The spans offered for an ask that reads rows, and for one that reads sums. */
    private val SHORT_SPANS = listOf(
        VoiceQuestion.Window.TODAY, VoiceQuestion.Window.THIS_WEEK, VoiceQuestion.Window.THIS_MONTH, VoiceQuestion.Window.LAST_MONTH
    )
    private val LONG_SPANS = listOf(
        VoiceQuestion.Window.THIS_MONTH, VoiceQuestion.Window.LAST_MONTH, VoiceQuestion.Window.LAST_6_MONTHS,
        VoiceQuestion.Window.THIS_YEAR, VoiceQuestion.Window.LAST_YEAR, VoiceQuestion.Window.ALL_TIME
    )

    /**
     * A span he cannot give is never turned into one he can (15 Nov 2026). Two cases: the sentence counts days, weeks,
     * months or years he has no span for ("last 3 months", "10 days ago", "the first 2 weeks", the fiscal year —
     * [VoiceQuestion.unsupportedSpan]), or it names a longer span ([VoiceQuestion.Window.isLong]) for a question that
     * lists rows. Either way the question is kept and he says which spans he can give, each a tap; a figure for another
     * span is never said in its place. A guess stays a guess ([Ask.Guessed]).
     */
    private fun limitSpan(heard: String, ask: Ask, dateSaid: Boolean = false): Ask {
        val inner = if (ask is Ask.Guessed) ask.ask else ask
        if (!spanMatters(inner)) return nowWithSpan(heard, ask, inner, dateSaid)
        val sums = longOk(inner)
        val tooLong = !sums && spanOf(inner)?.isLong == true
        // A day or a month named outright for a question that cannot be asked over one (16 Nov 2026): the spans he tells.
        if (!tooLong && !(heard.isNotEmpty() && (VoiceQuestion.unsupportedSpan(heard) || dateSaid))) return ask
        // The whole book has nothing before it: a trend is never offered over it (25 Nov 2026, plan H5).
        val spans = (if (sums) LONG_SPANS else SHORT_SPANS).filter { !(inner is Ask.Trend && it == VoiceQuestion.Window.ALL_TIME) }
        val limit = Ask.SpanLimit(spans.map { over(inner, it) }, long = sums)
        return if (ask is Ask.Guessed) Ask.Guessed(limit) else limit
    }

    /**
     * A question about how the book stands now, asked with a span ("pichle saal kitne customers", "Ali ka hisaab last month",
     * "last year ka drawer") (15 Nov 2026): the book keeps no earlier standing of these, so the answer is today's and the
     * card says so first ([Ask.Now]) — the span left out in silence read as that span's own figure. [inner] is an ask whose
     * answer does not change with a span ([spanMatters] said no). Today's span and the whole book's change nothing, and a
     * sentence with no span is answered as it was.
     */
    private fun nowWithSpan(heard: String, ask: Ask, inner: Ask, dateSaid: Boolean = false): Ask {
        if (heard.isEmpty() || !standsNow(inner) || !(dateSaid || spanSaid(heard))) return ask
        val now = Ask.Now(inner)
        return if (ask is Ask.Guessed) Ask.Guessed(now) else now
    }

    /** The asks that tell how the book stands today: its balances, counts, a person's or a product's facts, the drawer. */
    private fun standsNow(a: Ask): Boolean = when (a) {
        is Ask.Figure, is Ask.Parties, is Ask.Person, is Ask.Status, is Ask.Count, Ask.Drawer, Ask.Qarz -> true
        // The cover's "in a week" is part of its own question, not a span the book cannot give.
        is Ask.Goods -> a.facet != GoodsFacet.COVER
        else -> false
    }

    /** Whether the sentence says a span other than today and the whole book: the two that "stands now" already is. */
    private fun spanSaid(heard: String): Boolean {
        if (VoiceQuestion.unsupportedSpan(heard)) return true
        val w = VoiceQuestion.windowOf(heard) ?: return false
        return w != VoiceQuestion.Window.TODAY && w != VoiceQuestion.Window.ALL_TIME
    }

    /**
     * The one door every ask passes before the book is read: a longer span never reaches a reader that lists rows, however
     * the ask came (a tap on a chip, an answer repeated, a question carried on from the last one).
     */
    fun keepWithinReach(ask: Ask): Ask = limitSpan("", ask)

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
            senseHeard(heard, names, goods),
            isName = { w -> w !in VOCATIVE_SET && senseNameSlot(w, names) },
            isItem = { w -> senseNameSlot(w, goods) }
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

    /**
     * What each word of [said] means, as the rules read a sentence ([byRules] and [rulesOwnFacet] read it alike): a word the
     * book knows as somebody's or a product's name, a day word or a figure's word is never read by its sound.
     */
    private fun mindOf(said: List<String>, joined: String, names: List<Name>, goods: List<Name>, language: String?): HudhudMind.Read {
        val figureWords = VoiceQuestion.keywordsIn(joined)
        return HudhudMind.read(said, language) { w ->
            // A thank-you's own word is never matched by sound to something else ("thanks" read as staff, 15 Nov 2026).
            w in QUIET_SET || w in COURTESY_WORDS || w in DAY_WORDS || w in figureWords || knownName(w, names) || knownName(w, goods)
        }
    }

    /**
     * The three things about a customer only the rules know (15 Nov 2026): since when he deals with the shop, his last entry
     * and how many entries he has. The bank has no meaning of their own ([labelOf] gives each "party_details"), so a sense
     * sure of "last payment" turned "Ali Raza ki aakhri entry" into his last payment, and a sense sure of a count of the book
     * dropped the man from "अली रज़ा की कितनी एंट्री" (his name, saved in Latin letters, is not named surely by two short
     * Devanagari words, and a question of the shop the sense is all but certain of then wins). The rules' reading stands when
     * the sentence says entry (or "since when", or the first entry) and says no payment: with a payment word in it the sense
     * is the better judge, "Ali Raza ka aakhri paisa kab aaya" being his last payment.
     */
    private fun rulesOwnFacet(ruled: Ask, heard: String, names: List<Name>, goods: List<Name>, language: String?): Boolean {
        val facet = (ruled as? Ask.Person)?.facet ?: return false
        if (facet != Facet.SINCE && facet != Facet.LAST_ENTRY && facet != Facet.ENTRIES) return false
        val said = words(heard)
        val m = mindOf(said, said.joinToString(" "), names, goods, language)
        if (m.has(HudhudMind.C.PAYMENT)) return false
        return if (facet == Facet.SINCE) m.has(HudhudMind.C.SINCE_WHEN) || (m.has(HudhudMind.C.FIRST) && m.has(HudhudMind.C.ENTRY))
        // The last time his goods came ("آخری مال کب آیا", 25 Nov 2026, H4): his last entry, not the sense's last payment.
        else m.has(HudhudMind.C.ENTRY) || (facet == Facet.LAST_ENTRY && m.has(HudhudMind.C.GOODS) && m.has(HudhudMind.C.CAME))
    }

    /** How many words of [m] mean "last": "Ali Raza ka last payment last month" has two, the facet's and the span's. */
    private fun lastWordsIn(m: HudhudMind.Read): Int = m.said.indices.count { HudhudMind.C.LAST in m.at(it) }

    /**
     * Whether the one "last" of a sentence is its span's own (15 Nov 2026): "Ali Raza last month", "uska pichle saal ka hisaab", "what
     * did he pay last year". The span is one he tells or one he cannot, its words hold "last", and there is no second "last" — that
     * one is the facet's ("Ali Raza ka last payment last month": his last payment, over a span he cannot filter). One rule for the
     * rules ([byRules], [followUp]) and for [decide], so that the sense, which learnt "last" as a last payment, does not turn the
     * span's word into one, and the rules do not read it as his last entry.
     */
    private fun lastIsSpansOwn(heard: String, m: HudhudMind.Read): Boolean =
        (VoiceQuestion.unsupportedSpan(heard) || VoiceQuestion.lastIsSpan(heard)) && lastWordsIn(m) < 2

    private fun spanOwnsLast(heard: String, names: List<Name>, goods: List<Name>, language: String?): Boolean {
        if (VoiceQuestion.windowOf(heard) == null && !VoiceQuestion.unsupportedSpan(heard)) return false
        val said = words(heard)
        return lastIsSpansOwn(heard, mindOf(said, said.joinToString(" "), names, goods, language))
    }

    /**
     * The pages whose questions use words no figure or count of the book uses (the heir's key, the helper's phone, merging duplicates, the
     * payment voice, the home tour, the customer's khata link, Mera Khata): a rules' reading of one stands against a sense that is not
     * nearly certain (25 Nov 2026, H4: the retrained sense answered 0.3-0.7 "about the privacy", "bills today", "unknown" to seven of them).
     */
    private const val OWN_PAGE_SENSE = 0.80
    private val OWN_VOCABULARY_PAGES = setOf("screen_waris", "screen_viewer", "screen_duplicates", "screen_payment_voice", "screen_help",
        "faq_khata_link", "screen_mera_khata")

    private fun groundOf(a: Ask): Ground = when (a) {
        Ask.Unknown, is Ask.DidYouMean, is Ask.SpanLimit, is Ask.NeedName, is Ask.Guessed, is Ask.Now -> Ground.NONE
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
    /**
     * How sure the 15-seed sense must be to turn a reading the rules made on FIRM ground (H4, 25 Nov 2026, measured on the held-out set):
     * it is right 68% of the time at 0.95-0.97, 96% at 0.98-0.99 and 98% from 0.99 (the single perceptron was right 78% at 0.95-0.97).
     * Goods readings and help pages keep [SENSE_OVERRULE], where the ensemble is right.
     */
    internal const val SENSE_FIRM_TURN = 0.99
    /** How sure it must be to turn a named person into something else. */
    internal const val SENSE_CERTAIN = 0.92

    /** The customers or suppliers a reading names (none for a reading of the whole shop). */
    private fun namedBy(a: Ask): List<Long> = when (a) {
        is Ask.Parties -> a.ids
        is Ask.Person -> a.ids
        is Ask.Moved -> a.ids
        is Ask.WhenPays -> a.ids
        is Ask.Entry -> a.ids
        is Ask.Took -> a.ids
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
            val own = nameWords(n.name)
            val folds = own.map { NameSearch.fold(it) }.filter { it.isNotEmpty() }.toSet()
            // Across scripts a name meets its spelling by its consonants ("بشیر" and "Bashir"): three or more is a name.
            val bones = own.map { HudhudMind.skeleton(it) }.filter { it.length >= 3 }.toSet()
            val parts = partsOf(n.name)
            val hits = HashSet<String>()
            var distinctive = false
            for (w in said) {
                val f = NameSearch.fold(w)
                val sk = HudhudMind.skeleton(w)
                val bySpelling = f in folds || sk in bones
                // A name of few consonants ("Ali Raza") is met in Hindi, Bengali or Arabic script only by [bridges].
                val across = bridgedLatin(saidForms(w), parts)
                if (!bySpelling && across == null) continue
                hits += w
                if (bySpelling && (f.length >= 4 || sk.length >= 3)) distinctive = true
                if (across != null && (across.length >= 4 || HudhudMind.skeleton(across).length >= 3)) distinctive = true
            }
            if (hits.size >= 2 || distinctive) return true
        }
        return false
    }

    /**
     * Whether any word of [heard] is a word of [ids]' names (by its spelling, its consonants, or — in another script — its
     * [bridges]). None: the person came from the question before ("uska number?", a chip under his answer) — carried on, as
     * surely as named.
     */
    private fun namedAtAll(heard: String, names: List<Name>, ids: List<Long>): Boolean {
        val said = words(heard).filter { it !in QUIET_SET && it !in VOCATIVE_SET }
        for (n in names) {
            if (n.id !in ids) continue
            val own = nameWords(n.name)
            val folds = own.map { NameSearch.fold(it) }.filter { it.isNotEmpty() }.toSet()
            val bones = own.map { HudhudMind.skeleton(it) }.filter { it.length >= 2 }.toSet()
            val parts = partsOf(n.name)
            if (said.any { w ->
                    NameSearch.fold(w) in folds || HudhudMind.skeleton(w) in bones ||
                        (!HudhudMind.isHardWord(w) && bridgedLatin(saidForms(w), parts) != null)
                }) return true
        }
        return false
    }

    /** True when every word that asks something is a word of [id]'s name: the sentence only names him. */
    private fun onlyNames(heard: String, names: List<Name>, id: Long): Boolean {
        val own = names.firstOrNull { it.id == id }?.let { nameWords(it.name).map { w -> NameSearch.fold(w) }.toSet() } ?: return false
        return words(heard).all { it in QUIET_SET || it in VOCATIVE_SET || NameSearch.fold(it) in own }
    }

    /**
     * A sentence that is his name and nothing else — no little word, which may carry the ask (Urdu "رقم" is an amount, Arabic's a
     * number): only the words of his name, in its own letters or in another script's ("علی رضا" for "Ali Raza", met on the letters
     * as the search meets it), and a calling ("Ali bhai"). Kept his account by rule ([decide]).
     */
    private fun nameAlone(heard: String, names: List<Name>, id: Long): Boolean {
        val name = names.firstOrNull { it.id == id } ?: return false
        val own = nameWords(name.name).map { w -> NameSearch.fold(w) }.toSet()
        val parts = partsOf(name.name)
        val said = words(heard)
        // A greeting or a thank-you in front of his name is not a question either (25 Nov 2026, H4: "salam Ali" was his details under the retrained sense).
        return said.isNotEmpty() && said.all { it in VOCATIVE_SET || it in COURTESY_TOKENS || NameSearch.fold(it) in own || bridgedLatin(saidForms(it), parts) != null }
    }

    private val SIDES = setOf(VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.TO_GIVE)
    private val SIDE_LABELS = setOf("to_get", "to_give")
    /** The sense's labels that read money as the shop's to get (16 Nov 2026, audit T10). */
    private val RECEIVABLE_LABELS = setOf("to_get", "who_owes_most", "who_owes_least", "not_paid")
    private val RECEIVABLE_KINDS = setOf(VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.WHO_OWES_MOST,
        VoiceQuestion.Kind.WHO_OWES_LEAST, VoiceQuestion.Kind.NOT_PAID)

    private fun decide(
        heard: String, ruled: Ask, guesses: List<HudhudSense.Guess>, names: List<Name>, topics: List<Topic>,
        language: String?, goods: List<Name>, before: Ask? = null
    ): Ask {
        // The owner's private note on somebody (14 Nov 2026): read by [HudhudNote] alone. The bank has no meaning for it,
        // and the sense, which learnt "details" and "number" from sentences with a name in them, would turn "Ali ka note"
        // into his details. Before anything else, so no branch below can reach it.
        if (isNoteAsk(ruled)) return ruled
        // The word for today said alone is the day's summary, whatever a shuffle of the sense makes of one word (25 Nov 2026, H4: 0.51 against 0.44).
        if (ruled == Ask.Unknown && words(heard).size == 1 && VoiceQuestion.windowOf(heard) == VoiceQuestion.Window.TODAY) return Ask.Today
        // A product's own record, a paper's fields (15 Nov 2026): only the rules read them, the sense learnt no meaning for them.
        if (ruled is Ask.Paper || (ruled is Ask.Goods && ruled.facet in RECORD_FACETS)) return ruled
        // A customer and a product in one question, past talks asked for (17 Nov 2026, L004 / L019): only the rules read them; the
        // sense, which learnt a named man's "kab" as the day he may pay, turned "Ali Raza ne Urea kab liya" into his Radar days.
        if (ruled is Ask.Took || ruled == Ask.NoHistory) return ruled
        // His first day, his last entry, how many entries he has (15 Nov 2026): the bank has no meaning for them either.
        if (rulesOwnFacet(ruled, heard, names, goods, language)) return ruled
        // A span said on purpose, carried on or said alone (15 Nov 2026): the rules' reading, never the sense's.
        if (ruled == spanReading(heard, before)) return ruled
        // What somebody paid or took over a span the sentence states ("Ali Raza ne pichle saal diye", "Ali paid me last
        // month"): the sense learnt "pichle … diye" as his last payment from sentences with no span in them, and answered
        // it at 0.95 for every span but this year's and today's (15 Nov 2026). The rules read the span, so the sum stands. A
        // span he cannot give is read as this month's and told with the spans he has: the sense may not turn that either.
        if (ruled is Ask.Moved && (VoiceQuestion.windowOf(heard) == ruled.window || VoiceQuestion.unsupportedSpan(heard))) return ruled
        // A courtesy the sense learnt as a whole sentence ("salam Ali Raza ka number" -> salaam) never overturns what the
        // rules read when the sentence also asks the book (15 Nov 2026; courtesyOnly).
        // The shop's to pay, said outright (16 Nov 2026, audit T10): "kis supplier ko nahi diya", "sab se zyada kis ko dena hai",
        // "kis kis ka paisa mere paas hai". The rules read these as TO_GIVE ([VoiceQuestion.classify]); the sense's receivable
        // reading never turns one, and an unrelated rules' reading with a receivable guess goes to the shop's payables.
        // Seed safety (25 Nov 2026, BC-240, lesson 163): what the rules say about the money side or an entry is never turned by a shuffle of the
        // sense. The shop's payables asked outright; an entry said as a fact ("Ali Raza ne paanch sau diye" was his promised day under one
        // shuffle). (A side the rules read from a word of giving or getting needs no guard: the sense must be 0.95 sure to turn it.)
        if (ruled == Ask.Figure(VoiceQuestion.Kind.TO_GIVE) && VoiceQuestion.payableAsked(heard)) return ruled
        if (ruled is Ask.Entry) return ruled
        val first = guesses.firstOrNull() ?: return ruled
        if (first.label in RECEIVABLE_LABELS && VoiceQuestion.payableSaid(heard) &&
            namedBy(ruled).isEmpty() && ruled !is Ask.Goods && !askedWhoSupplies(ruled, first, heard)
        ) return Ask.Figure(VoiceQuestion.Kind.TO_GIVE)
        val top = if (first.label in COURTESY && !courtesyOnly(words(heard), names, goods)) {
            guesses.firstOrNull { it.label !in COURTESY } ?: return ruled
        } else first
        if (labelOf(ruled) == top.label) return ruled
        // The shop's own "I will pay Ali" (Marhala 1, BC-323): the sense, which learnt "will pay" as the customer's promised day, may not
        // turn the shop's payment into the day the customer pays it.
        if ((top.label == "party_when_pays" || top.label == "likely_payers") && VoiceQuestion.shopWillPay(heard)) return ruled
        // Which way the money is owed (16 Nov 2026, audit T11): the rules read the side from who gives and who owes ("باید بدم",
        // "بدهی مشتریان به مغازه", "utang aku"); the sense learnt the words of debt without the side and turned three of them the
        // opposite way at 0.95. A side the sentence says outright is never turned by the sense; a bare word of debt still may be.
        if (ruled is Ask.Figure && ruled.kind in SIDES && (top.label in SIDE_LABELS || top.label in RECEIVABLE_LABELS) &&
            (ruled.kind == VoiceQuestion.Kind.TO_GIVE) == (top.label in RECEIVABLE_LABELS) && VoiceQuestion.sideSaid(heard)) return ruled
        // "Ali Raza last month", "Ali Raza pichle saal" (15 Nov 2026): the "last" is the span's. The rules read a named person with
        // nothing else asked (his account, or the one fact his words give), and the sense, which learnt "last" as his last
        // payment from sentences with no span in them, was sure of that at 0.95. Only that reading is turned back: a span's word
        // is no payment word. A second "last" ("Ali Raza ka last payment last month") is the facet's, and stays with the sense.
        if (top.label == "party_last_payment" && (ruled is Ask.Parties || ruled is Ask.Person) &&
            spanOwnsLast(heard, names, goods, language)
        ) return ruled
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
                // A sentence that is nothing but his name is his account, by rule (17 Nov 2026, the lead's retrain check: a retrained
                // sense turned "Ali Raza" and "علی رضا" into his details at 0.95). Before the sense's sharpening of a facet.
                who.size == 1 && nameAlone(heard, names, who.first()) -> ruled
                // An order about him ("Saleem Bhai ka udhaar maaf kar do", "Ali Raza ko WhatsApp bhej do ke paise de"): his account stays; the retrained sense
                // took the verb for his limit or the day he pays (25 Nov 2026, H4). Nothing is written either way.
                ruled is Ask.Parties && saysOrderDo(heard) -> ruled
                // A named man with a vague sense (the two best meanings within a hair of each other): his account stays (25 Nov 2026, H4).
                ruled is Ask.Parties && top.label == "party_details" && tied(guesses, top) -> ruled
                // "Tariq Butt ki payment": a word of payment with no more asked is his account; "details" is no payment (25 Nov 2026, H4).
                ruled is Ask.Parties && top.label == "party_details" && saysPayment(heard, names, goods, language) -> ruled
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
            // A shape the everyday reader says outright ("what did I buy Urea for" is its cost) is the rules' (25 Nov 2026, H4: the retrained sense said suppliers).
            if (HudhudEveryday.goodsFacet(heard, ruled.facet) != null) return ruled
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
        // "Duplicate customers kahan hain": the page's own two words; the sense, which learnt customers alone, counted them (25 Nov 2026, H4).
        if (ruled == Ask.Help("screen_duplicates") && has(words(heard).joinToString(" "), listOf("duplicate", "duplicates", "ڈپلیکیٹ", "डुप्लिकेट"))) return ruled
        // The invoices with a balance still due: only the rules make this ask (the bank has no meaning for it), so the sense,
        // which learnt every "invoices" as a count of the ones made, never turns it.
        if (ruled == Ask.Status(StatusOf.INVOICES)) return ruled
        // The shop's own tax number: "number" alone made the retrained sense (0.80) a customer's phone (25 Nov 2026, H4).
        if (ruled == Ask.Shop(ShopFacet.TAX)) return ruled
        // "Mera hisaab kya hai" in Hindi script: the sense, which has the Hindi "hisaab" only in privacy sentences, was 0.98 sure of the privacy page.
        if (ruled == Ask.Status(StatusOf.BOOK) && has(words(heard).joinToString(" "), listOf("मेरा हिसाब", "मेरा हिसाब", "mera hisaab", "میرا حساب"))) return ruled
        // "Cement ka supplier kaun hai": a supplier named with no word of money or paying is not "who has not paid"; the page the rules found stands.
        if (askedWhoSupplies(ruled, top, heard)) return ruled
        // Who made a payment today (no word of "not"): the list of those who paid, never the ones who did not (25 Nov 2026, H4: the sense 0.96).
        if (ruled is Ask.Collected && top.label == "not_paid" && !has(words(heard).joinToString(" "), NOT_SAID)) return ruled
        // Someone who does not yet know his way (15 Nov 2026, review D): "mujhe samajh nahi aa raha kya poochun" is a request for the
        // list of what he can tell; the sense, hearing "mujhe" and "samajh", took it for "do you know my name" at 0.9 and more.
        if (ruled == Ask.Abilities && has(words(heard).joinToString(" "), GUIDE_ME)) return ruled
        // Review O (15 Nov 2026): what the rules read from the words the sense was never taught. Which way money is owed, said in the
        // accountant's words ("receivables", "على الزبائن", "pay me"): the sense took them for money that came in. The day's own
        // summary ("aaj din kaisa raha"): the sense took it for the time. Credit given over a span ("is mahine kitna udhaar diya
        // gaya"): the sense took it for money collected, the opposite figure.
        if (ruled is Ask.Figure && (ruled.kind == VoiceQuestion.Kind.TO_GET || ruled.kind == VoiceQuestion.Kind.TO_GIVE) &&
            has(words(heard).joinToString(" "), OWED_SAID)
        ) return ruled
        if (ruled == Ask.Today && has(words(heard).joinToString(" "), DAY_WENT)) return ruled
        // Review D (16 Nov 2026): the phrases of three classes the sense was never taught — what the shop owes a supplier, the head count of
        // staff in Bengali, what is running short in Arabic and Farsi (the sense took the last for the day's summary).
        val saidAll = words(heard).joinToString(" ")
        if (ruled == Ask.Figure(VoiceQuestion.Kind.TO_GIVE) && has(saidAll, OWE_SUPPLIER)) return ruled
        // 25 Nov 2026 (H4, the retrain): five classes of the rules' own reading that the old sense happened to turn the right way and the retrained one
        // no longer does, each said by its words and then by the sense — never by the sense alone.
        // The takings of the day, said as "earned" ("aaj dukaan ne kitna kamaya"): the day's sales (a month's "kamaya" is the profit, a figure the rules read).
        if ((ruled is Ask.Help || ruled == Ask.Unknown) && VoiceQuestion.windowOf(heard) == VoiceQuestion.Window.TODAY && has(saidAll, EARNED)) {
            return Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
        }
        // The till said in the past of the day ("aaj gala kitna hua"): what the till took, the day's sales; "gala mein kitna hai" stays the drawer.
        if (ruled == Ask.Drawer && VoiceQuestion.windowOf(heard) == VoiceQuestion.Window.TODAY && has(saidAll, TOOK_PLACE) &&
            top.label == "sales_today" && p >= SENSE_MAYBE) return Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
        // "Cash kitna bacha hai": what is left of the cash is the drawer; the profit is a month's word the sentence does not say.
        if (ruled is Ask.Help && top.label == "profit" && has(saidAll, CASH_WORD) && has(saidAll, LEFT_WORD)) return Ask.Drawer
        // "Aaj ka bechna kitna hua": the day's selling, never the cashbook (money in and out is said in other words).
        if ((ruled is Ask.Help || ruled == Ask.Unknown) && top.label == "cashbook_today" && VoiceQuestion.windowOf(heard) == VoiceQuestion.Window.TODAY &&
            has(saidAll, SELLING_WORD) && !has(saidAll, IN_AND_OUT)) return Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
        // "Aaj kis ko payment ki": money paid out to somebody today, not who will pay.
        if (top.label == "likely_payers" && has(saidAll, PAID_TO)) return Ask.Activity(VoiceQuestion.Window.TODAY)
        // "How much money is stuck outside": "stuck" is within one letter of "stock" by sound, so the rules read the low stock; a sentence that says money
        // (a word whose own meaning is money, not a sound match) and the sense sure it is what the shop is to get or give is that money.
        if (ruled is Ask.Status && ruled.of == StatusOf.LOW_STOCK && saysMoney(heard) && top.label in OWED_LABELS && p >= SENSE_OVERRULE) return theirs() ?: ruled
        // Cash coming in and going out together is the cashbook, though "cash" alone is the drawer.
        if (ruled == Ask.Drawer && top.label == "cashbook_today" && has(saidAll, IN_AND_OUT)) return Ask.Cashbook(VoiceQuestion.Window.TODAY)
        // A bare "sale batao" (no span word anywhere): the shop's day; the rules' month is for a sentence that names a month.
        if (ruled == Ask.Figure(VoiceQuestion.Kind.SALES_MONTH) && top.label == "sales_today" && p >= SENSE_SURE && VoiceQuestion.spanWordsIn(heard).isEmpty() && has(saidAll, TELL_ME)) {
            return Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)
        }
        // "Did anything sell today or not": the day's doings, which the rules read as the day's sales figure.
        if (ruled == Ask.Figure(VoiceQuestion.Kind.SALES_TODAY) && top.label == "activity" && p >= SENSE_MAYBE && has(saidAll, OR_NOT)) {
            return theirs() ?: ruled
        }
        // A tenseless word of paying with nobody named ("siapa saja yang akan bayar saya") may as well be who has not paid: the sense settles it.
        if (ruled == Ask.WhenPays(emptyList()) && !has(saidAll, WILL_PAY) && top.label == "not_paid" && p >= 0.5) return theirs() ?: ruled
        // Who owes the shop the most, said with Arabic's verb of debt ("من مدين لي بأكثر"): the sense read it as the total to get (BC-256).
        if (ruled == Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST) && VoiceQuestion.owesMeSaid(heard)) return ruled
        if (ruled == Ask.Count(Thing.STAFF) && has(saidAll, STAFF_HEADCOUNT)) return ruled
        if (ruled == Ask.Status(StatusOf.LOW_STOCK) && has(saidAll, RUNNING_SHORT)) return ruled
        if (ruled is Ask.Activity && ruled.window != null && creditGivenOver(words(heard).joinToString(" "))) return ruled
        if (ruled is Ask.Returns && has(words(heard).joinToString(" "), CAME_BACK)) return ruled
        // A staff member's pay or attendance, a customer's plan or cheques: only the rules make this ask (the bank has no meaning for it).
        if (ruled is Ask.Record) return ruled
        // The counter's books (15 Nov 2026): only the rules make this ask — the bank has no meaning for it, and the sense learnt a
        // bill as a supplier's, a return as the day's returns and a drawer as the drawer's figure (lesson 145).
        if (ruled is Ask.Counter) return ruled
        if (ruled is Ask.Figure && ruled.kind == VoiceQuestion.Kind.NOT_PAID && remindPage(words(heard).joinToString(" "))) return ruled
        // The everyday phrasings the rules read from words alone (who paid, where the money went, what sells best): the bank learnt
        // none of them as such, and the sense turned them at 0.95 into the opposite (who did NOT pay, who owes MOST).
        if (ruled == HudhudEveryday.read(heard, VoiceQuestion.windowOf(heard))) return ruled
        if (ruled is Ask.Figure && ruled.kind == VoiceQuestion.Kind.WHO_OWES_LEAST) return ruled
        // A bare "bill" with no supplier or purchase word (15 Nov 2026, the owner's list): which bill is said by the rules' own
        // question (utility or supplier), never settled by the sense, which learnt every "bill" as a count of supplier bills.
        if (isBareBillChoice(ruled, heard)) return ruled
        // The rules' own readings of 17 Nov 2026 (L004), each from words the bank never taught: money in by its ways, Indonesian's biggest
        // "tagihan", bare bills shown (invoices first). The sense took them for the business card, money in, a count of supplier bills.
        if (ruled is Ask.Collected && cameInByWay(saidAll)) return ruled
        if (ruled == Ask.Status(StatusOf.BILLS) && biggestTagihan(saidAll)) return ruled
        if (ruled is Ask.DidYouMean && ruled.options.firstOrNull() is Ask.Count && (ruled.options.first() as Ask.Count).thing == Thing.INVOICES &&
            has(saidAll, SHOW_WORDS)
        ) return ruled
        if (ruled is Ask.Voice || ruled is Ask.TopGoods || ruled == Ask.Qarz || ruled == Ask.Automations || ruled is Ask.NeedGoods ||
            ruled is Ask.CallMe || ruled is Ask.Look || ruled is Ask.Why || ruled is Ask.Entries || ruled is Ask.Act ||
            ruled is Ask.Trend || ruled is Ask.Advise
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
                val ask = theirs()?.takeUnless { questionMadeName(it, heard, names, goods, ruled) } ?: return ruled
                when {
                    // "Whose?" with a list of the latest customers says more than a guess at somebody's fact.
                    ruled is Ask.NeedName && ask is Ask.NeedName -> ruled
                    p >= SENSE_SURE -> ask
                    p >= SENSE_MAYBE -> Ask.Guessed(ask)
                    else -> ruled
                }
            }
            Ground.WEAK, Ground.MEDIUM -> if (p >= SENSE_MAYBE) {
                val ask = theirs()?.takeUnless { questionMadeName(it, heard, names, goods, ruled) }
                // A page of its own vocabulary stands against a middling sense that answers anything but a figure of money (25 Nov 2026, H4).
                if (ruled is Ask.Help && ruled.key in OWN_VOCABULARY_PAGES && ask !is Ask.Figure && p < OWN_PAGE_SENSE) ruled else ask ?: ruled
            } else ruled
            Ground.FIRM -> if (p >= SENSE_FIRM_TURN) (theirs()?.takeUnless { questionMadeName(it, heard, names, goods, ruled) } ?: ruled) else ruled
        }
    }

    /** Whether the sense's best meaning has a runner-up within [TIE_MARGIN] of it: it does not know which of two things is asked. */
    private fun tied(guesses: List<HudhudSense.Guess>, top: HudhudSense.Guess): Boolean {
        val next = guesses.firstOrNull { it !== top && it.label != top.label } ?: return false
        return top.score - next.score < TIE_MARGIN
    }

    private const val TIE_MARGIN = 0.05

    /**
     * "Cement ka supplier kaun hai": a supplier named, no word of money or paying, the sense's "who has not paid" against a help page the rules
     * found. Not the shop's payables; the page stands (25 Nov 2026, H4).
     */
    private fun askedWhoSupplies(ruled: Ask, top: HudhudSense.Guess, heard: String): Boolean =
        ruled is Ask.Help && top.label == "not_paid" && !saysMoney(heard) && words(heard).any { HudhudMind.C.SUPPLIER in HudhudMind.meaningOf(it) }

    /** A word of paying or of money said as itself (never by its sound: "cement" sounds like "payment"). */
    private fun saysMoney(heard: String): Boolean =
        words(heard).any { w -> HudhudMind.meaningOf(w).any { it == HudhudMind.C.PAYMENT || it == HudhudMind.C.MONEY } }

    private fun saysPayment(heard: String, names: List<Name>, goods: List<Name>, language: String?): Boolean {
        val said = words(heard)
        return mindOf(said, said.joinToString(" "), names, goods, language).has(HudhudMind.C.PAYMENT)
    }

    /**
     * A product whose name is made of a question's words, met only by the sense (25 Nov 2026, H4, the retrain): "low stock wala maal
     * kaun sa hai" in a book with a product "Low Stock Wala" is the low stock, as in a book without him. The sense takes the name's
     * words for the product and answers a question about it; nothing links the name ("ka", "of") and the rules named nobody, so the
     * sentence's own question stands ([byWholeName] reads the sentence again when something IS asked of him).
     */
    private fun questionMadeName(ask: Ask, heard: String, names: List<Name>, goods: List<Name>, ruled: Ask): Boolean {
        if (ask !is Ask.Goods || ask.ids.isEmpty() || ruled is Ask.Goods || namedBy(ruled).isNotEmpty()) return false
        val mask = wholeNamesIn(heard, names, goods, emptyList()) ?: return false
        return !mask.linked && ask.ids.all { it in mask.goodsIds }
    }

    /**
     * The nearest things [heard] could be asking, for the chips under an answer he could not give (13 Nov 2026, the
     * owner: "kehne wala jo keyword de raha hai, us hisaab se suggestion de"): the sense's next meanings, each as a
     * question of the book; none that would need a name the words do not give.
     */
    fun suggest(heard: String, names: List<Name>, topics: List<Topic>, language: String?, goods: List<Name>, skip: Ask? = null): List<Ask> =
        suggestRead(heard, asRead(names), topics, language, asRead(goods), skip)

    private fun suggestRead(heard: String, names: List<Name>, topics: List<Topic>, language: String?, goods: List<Name>, skip: Ask?): List<Ask> {
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
            GoodsFacet.STOCK, GoodsFacet.COVER -> "goods_stock"
            GoodsFacet.SUPPLIERS, GoodsFacet.COST, GoodsFacet.LAST_IN -> "goods_suppliers"
            GoodsFacet.BUYERS -> "goods_buyers"
            GoodsFacet.SOLD -> "goods_sold"
            // A product's own record has no meaning in the bank: only the rules read it ([HudhudRecords]).
            GoodsFacet.LABEL, GoodsFacet.BARCODE, GoodsFacet.PACK, GoodsFacet.TAX, GoodsFacet.SHELF, GoodsFacet.ADDED,
            GoodsFacet.BATCHES, GoodsFacet.SCHEME -> null
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
            // The unpaid invoices have no meaning in the bank: only the rules read them ([unpaidInvoices]), the sense never turns it.
            StatusOf.INVOICES -> null
        }
        is Ask.Deliveries -> "deliveries"
        is Ask.Activity -> "activity"
        Ask.BackupStatus -> "backup_status"
        is Ask.Cashbook -> "cashbook_today"
        is Ask.Returns -> "returns_today"
        is Ask.Counter -> null
        is Ask.Help -> "help_howto"
        is Ask.Act -> when (a.move) {
            Move.FLY -> "act_fly"
            Move.DANCE -> "act_dance"
            Move.JUMP -> "act_jump"
        }
        is Ask.OnDays, is Ask.Both, is Ask.Compare,
        Ask.Unknown, is Ask.DidYouMean, is Ask.SpanLimit, is Ask.NeedName, is Ask.Guessed, is Ask.Now, is Ask.Voice, is Ask.TopGoods, Ask.Qarz,
        Ask.Automations, is Ask.NeedGoods, is Ask.CallMe, is Ask.Look, is Ask.LookDone, is Ask.Paper, is Ask.Record, is Ask.OffTopic, is Ask.Why, is Ask.Entries,
        is Ask.Took, Ask.NoHistory, is Ask.Trend, is Ask.Advise -> null
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
                // The figures of a date are no amount ("Ali ko 12 November ko 500 diye" is 500, never 12).
                val entry = VoiceEntry.parse(VoiceQuestion.withoutDate(heard) { w -> knownName(w, names) || knownName(w, goods) }, emptyList(), language)
                val amount = entry.amount
                // The count of a span ("6" of "pichle 6 mahine") is no amount of money: the rules' reading stands.
                if (amount != null && amount in VoiceQuestion.spanFigures(heard)) return null
                // "Maaf kar do", "bhej do": the order's "do", no amount (review O, [orderDo]); the rules' reading stands.
                if (amount != null && orderDo(heard, amount)) return null
                // A side nobody said, for nobody named (16 Nov 2026, audit BC-037): "abc 500" or markup with a figure is no
                // entry — the sense's label alone never decides which way money went (audit T11). With a person named the form
                // opens as before and the owner sees the side before Save.
                if (amount != null && entry.isGiven == null && named.isEmpty()) return null
                // A sum written with signs ("Ali Raza ka hisaab 500 + 300") is no amount of an entry with no side said, named or not (25 Nov 2026, H4).
                if (amount != null && entry.isGiven == null && SIGN_SUM.containsMatchIn(heard)) return null
                // A promise ("Ali kal 500 dega") is no entry, whoever reads it (BC-113): the rules' reading stands.
                if (VoiceQuestion.saysFuture(heard)) return null
                // A giving denied or a sum still owed is no entry either (Marhala 1, BC-321, BC-322): the rules' reading stands.
                if (VoiceQuestion.moneyNotMoved(heard)) return null
                if (amount == null || amount <= 0.0) {
                    if (named.isEmpty()) Ask.NeedName(null) else Ask.Parties(named)
                } else {
                    val nameWords = VoiceEntry.nameWords(heard)
                    twoEntries(heard, names, goods, language)
                        ?: Ask.Entry(amount, sindhiToMe(heard, entry.isGiven) ?: (label == "entry_gave"), partiesOf(names, nameWords), nameWords, entryDayOf(heard, names, goods))
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
        goods: List<Name> = emptyList(), before: Ask? = null, staff: List<Name> = emptyList()
    ): Ask {
        val said = words(heard)
        if (said.isEmpty()) return Ask.Unknown
        val joined = said.joinToString(" ")
        // How much must I pay the supplier (review D, 16 Nov 2026): what the shop owes, never a count of suppliers.
        if (has(joined, OWE_SUPPLIER)) return Ask.Figure(VoiceQuestion.Kind.TO_GIVE)
        // Which suppliers I have not paid (24 Nov 2026, BC-256, Arabic): the word of a supplier with the shop's own "I have not paid" —
        // what the shop owes there, as English "which suppliers have I not paid" is. HudhudMind took the supplier with "which" as
        // the goods that came in (Deliveries), the same wrong answer for a payable.
        if (unpaidSuppliersArabic(joined)) return Ask.Figure(VoiceQuestion.Kind.TO_GIVE)
        // The app's own history and his own story (14 Nov 2026, the owner: "app kab publish hui", "Hudhud kab add hua", "Hudhud
        // apni aap-beeti sunao"): answered only to a sentence that is nothing but those words, and before the how-to pages ("how
        // old is the app" holds a "how"). "Ali ki kahani" and "Roshan ka khata kab bana" are never read here: a word of the book
        // or of somebody's name is never a history's.
        HudhudStory.read(heard, language) { w -> knownName(w, names) || knownName(w, goods) }?.let { return Ask.About(storyTopic(it)) }
        // Past talks asked for (17 Nov 2026, L019): he shows none, and says so, where the day's activity was told before.
        if (pastTalk(joined) && said.none { knownName(it, names) || knownName(it, goods) }) return Ask.NoHistory
        // "Why" about a figure of the book (16 Nov 2026, audit T8): "is mahine sale kam kyun hui", "profit kyun gira". A strict reader
        // (every word its own, a span or a little word); with a name, a figure or any other word the sentence is read as before.
        HudhudWhyAsk.read(heard, isName = { w -> knownName(w, names) || knownName(w, goods) }, calling = { it in VOCATIVE_SET })?.let { return it }
        // The shop's advisor (25 Nov 2026, plan H5): is the sale going up or down, whom to chase first, what will come in over the next
        // 30 days. The same strict reading as "why" (every word its own or a little word), read after it, so a "why" is always the why's.
        HudhudAdviceAsk.read(heard, isName = { w -> knownName(w, names) || knownName(w, goods) }, calling = { it in VOCATIVE_SET })?.let { return it }
        // What a shop says every day (15 Nov 2026, the everyday-phrasing review): who paid and who does not, where the money went,
        // what left the drawer, what sells best. Words alone, only when no name of the book is in the sentence; [decide] keeps it.
        // A total stated by the sentence's shape ("مردم در کل چقدر به من می‌دهند": how much in all will people give me) is that total,
        // not the everyday "who owes most" its words also hold (17 Nov 2026, the lead's retrain check; [VoiceQuestion.sideStated]).
        if (!totalStated(heard, joined)) {
            HudhudEveryday.read(heard, VoiceQuestion.windowOf(heard)) { w -> knownName(w, names) || knownName(w, goods) }?.let { return it }
        }
        val how = has(joined, HOW)
        if (how) (howToPage(joined, topics) ?: helpOf(said, topics))?.let { return Ask.Help(it) }
        // A delete said as an order (15 Nov 2026, review O): "Ali Raza ka khata delete kar do" showed his balance, as if done. He
        // deletes nothing; the help page says where deleting and the bin are. Before any name, so the person is not the answer.
        if (erasesOrder(joined)) topics.firstOrNull { it.key == "faq_delete" }?.let { return Ask.Help(it.key) }
        // A sentence that is nothing but small talk, or "what can you tell me" (13 Nov 2026): taken before any name,
        // so "Allah Hafiz" is a goodbye and not a customer called Hafiz; one with any other word goes on as before.
        voiceChange(said, joined)?.let { return Ask.Voice(it) }
        // His acts, said by their verb alone (25 Nov 2026, H4: the retrained sense read "حلق يا هدهد" as a calling and "हुदहुद एक बार उछलो"
        // as a count of the app's openings): a sentence made only of an act's verb and little words is that act.
        actMove(said, joined)?.let { return Ask.Act(it) }
        // "My name is …", "call me …", "mera naam … hai", "mujhe … bulao" (14 Nov 2026): the owner tells him the name to be
        // called by. Taken before any name or concept — "mera naam Ali hai" opens no customer called Ali.
        callMeName(heard)?.let { return Ask.CallMe(it) }
        // The text size or the theme (14 Nov 2026, the tiers the owner approved): "font bara karo", "dark mode", "theme badlo".
        // A strict reader like the note's — a sentence with any word it does not know is read as before.
        HudhudLook.read(heard) { it in VOCATIVE_SET }?.let { return Ask.Look(it) }
        talk(said, whole = true, names, goods)?.let { return it }
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
        val mind = mindOf(said, joined, names, goods, language)
        aboutHim(mind, joined) { w -> knownName(w, names) || knownName(w, goods) }?.let { return it }
        // The figures of a date are no amount ("Ali ko 12 November ko 500 diye" is 500, never 12).
        val entry = VoiceEntry.parse(VoiceQuestion.withoutDate(heard) { w -> knownName(w, names) || knownName(w, goods) }, emptyList(), language)
        val asking = heard.any { it == '?' || it == '\u061F' } || has(joined, QUESTION) || said.first() in ASKS_FIRST
        // An entry said as a fact: the form opens filled in, the owner saves it (voice entry's own reading).
        // The count of a span ("3" of "pichle 3 mahine") is no amount of money.
        val amount = entry.amount?.takeUnless { it in VoiceQuestion.spanFigures(heard) || orderDo(heard, it) }
        val given = sindhiToMe(heard, entry.isGiven)
        // Two entries joined by "and": each offered on its own, never one wrong entry made of both. Before the single entry, which
        // needs one way for the whole sentence: "gave" and "got" together left it none, and the sentence was read as a person.
        // A verb in the future ("Ali kal 500 dega", "will pay") is a promise, never money that moved (BC-113): no entry. Nor is a giving
        // denied or a sum still owed ("Ali ne 500 nahi diye", "Ali se 500 lene hain": Marhala 1, BC-321, BC-322) — the rules read on, and
        // a named person is his account, with the balance on its own side.
        val promise = VoiceQuestion.saysFuture(heard) || VoiceQuestion.moneyNotMoved(heard)
        if (!asking && !promise) twoEntries(heard, names, goods, language)?.let { return it }
        if (!asking && !promise && amount != null && amount > 0.0 && given != null) {
            val nameWords = VoiceEntry.nameWords(heard)
            return Ask.Entry(amount, given, partiesOf(names, nameWords), nameWords, entryDayOf(heard, names, goods))
        }
        val figure = VoiceQuestion.classify(heard)
        val window = VoiceQuestion.windowOf(heard)
        // A span he has no window for ("pichle 3 mahine") stands in as one where the question carries on or says who gave
        // or took: it then reaches [limitSpan] as the question it is, and is not read as somebody's balance.
        val spanSaid = window ?: if (VoiceQuestion.unsupportedSpan(heard)) VoiceQuestion.Window.THIS_MONTH else null
        val spanOwn = if (spanSaid == null) SpanWords.NONE else SpanWords(
            VoiceQuestion.spanWordsIn(joined), spanSaid in VoiceQuestion.LAST_WINDOWS || VoiceQuestion.unsupportedSpan(heard)
        )
        val named = whoIsNamed(said, names, topics, mind, window)
        val product = if (named.isEmpty()) whoIsNamed(said, goods, topics, mind, window) else emptyList()
        // "When did he last pay" is the past: only a payment still to come is the Radar's.
        // The shop's own "I will pay" ("I will pay Ali tomorrow", "main Ali ko kal dunga") is the shop paying, never the day the customer
        // pays it (Marhala 1, BC-323: it was Ali's promised day, the other side's money).
        val willPay = (has(joined, WILL_PAY) || ((has(joined, WHEN) || has(joined, WHO)) && has(joined, PAY))) &&
            !mind.has(HudhudMind.C.LAST) && "did" !in said && !HudhudEveryday.negated(joined) && !VoiceQuestion.shopWillPay(heard)
        // The owner's private note on somebody (14 Nov 2026): "Ali ka note", "Ali's private note", "uska niji note", "kis ka note
        // hai". Only a sentence of nothing but the note's words, a name and little words ([HudhudNote]); any other is read as
        // before. Taken before the features and the follow-ups: a carried "uska note" is the note, not the balance.
        HudhudNote.read(heard, calling = { it in VOCATIVE_SET }) { w ->
            knownName(w, names) || knownName(bengaliStem(w), names)
        }?.let { n -> noteAsk(n, said, names, topics, mind, window, before)?.let { return it } }
        // A named customer's loyalty points ("Ali Raza ke loyalty points kitne hain"): a sentence that says the points and names him.
        if (named.isNotEmpty() && product.isEmpty() && !how && HudhudCounter.loyaltyWord(joined)) return Ask.Counter(CounterFacet.LOYALTY, window, named)
        // The counter's own books (15 Nov 2026): "aaj ka sab se bada bill", "kis cashier ne void kiya", "kis wajah se return hua".
        if (named.isEmpty() && product.isEmpty()) {
            HudhudCounter.read(joined, said, how, has(joined, SUPPLIER_SIDE_WORDS) || has(joined, PURCHASE_SIDE) || mind.any(HudhudMind.C.SUPPLIER),
                spanOwnsLast = spanSaid != null && lastIsSpansOwn(heard, mind))
                // "Weather kharab tha to aaj ki sale kitni hui" (16 Nov 2026): a damaged word is goods written off only when the
                // sentence is about goods or stock, never when it asks the sale, the profit or the money and names no goods.
                ?.takeUnless { it == CounterFacet.WRITTEN_OFF && asksMoneyNotGoods(heard, mind) }
                ?.let { return Ask.Counter(it, window ?: HudhudCounter.defaultWindow(it)) }
        }
        // Staff pay and attendance, a customer's plan, a customer's cheques (15 Nov 2026): the rules' own reading, ahead of the features.
        recordOf(said, joined, mind, window, named, product, before, whoIsNamed(said, staff, topics, mind, window))?.let { return it }
        if (named.isEmpty() && product.isEmpty()) byFeature(said, joined, mind, window, topics)?.let { return it }
        // The sentence's one "last" is the span's ("uska last month", "what did Ali pay last year"), not a last entry or payment.
        val spanLast = spanSaid != null && lastIsSpansOwn(heard, mind)
        followUp(mind, named, product, figure, spanSaid, spanOwn, willPay, spanLast, before)?.let { return it }
        // A span said alone with no answer to carry on ("pichle saal?", "last week?", "since the beginning", "pichle 3 mahine"): the
        // one figure the book gives over a span is its sales, so that is what he takes it for — the card says so ([Ask.Guessed])
        // and offers the other readings, and a span he cannot give gets the ones he can ([limitSpan]). Before this each language
        // read such a word by what else it means (the sense took "kal?" for a hop and "last week?" for the backup, "pichle
        // hafte?" for somebody's last payment). Today alone keeps the day's summary the sense gives it.
        if (spanSaid != null && spanSaid != VoiceQuestion.Window.TODAY && named.isEmpty() && product.isEmpty() &&
            figure == null && !willPay && onlySpan(mind, spanOwn)
        ) {
            return Ask.Guessed(figureOver(VoiceQuestion.Kind.SALES_MONTH, spanSaid))
        }
        // "Aap ko kisi cheez ka pata hai?" asks what he knows (13 Nov 2026, the owner's screenshot: it was read as the
        // shop's address, "ka pata"); with a name it is about that person ("aap ko Ali ka pata hai").
        if (knowsFrame(said) && named.isEmpty() && product.isEmpty() && figure == null && !willPay) return Ask.Abilities
        // "Uska number" with nobody before it: he asks whose, he does not guess.
        if (mind.has(HudhudMind.C.HIS) && named.isEmpty() && product.isEmpty() && !willPay && figure == null &&
            (before == null || (partyOf(before) == null && goodsOf(before) == null)) && !mind.any(HudhudMind.C.DATA, HudhudMind.C.ACCOUNT) &&
            facetOf(mind).let { it != null && (it != Facet.DETAILS || mind.any(HudhudMind.C.FATHER, HudhudMind.C.VILLAGE, HudhudMind.C.ADDRESS)) }
        ) return Ask.NeedName(facetOf(mind))
        if (named.isNotEmpty()) {
            tookOf(said, mind, named, names, goods, topics, window)?.let { return it }
            if (willPay) return Ask.WhenPays(named)
            // "How much did Bashir pay last month": the "last" is the span's, not his last payment (the same words in English).
            // A span he cannot give is the span's too ("2 din pehle", "the last fiscal year", "pehle 6 mahine"): its words hold
            // "first" and "last" as well, and "Ali ne 2 din pehle kitna diya" was his first day (15 Nov 2026). It is read as a
            // sum over the stand-in span and told with the spans he has ([limitSpan]), never answered as another question.
            // The same whether or not the sentence says who gave or took: "Ali Raza ka hisaab last month" is his account, not his
            // last entry (15 Nov 2026).
            val unsupported = VoiceQuestion.unsupportedSpan(heard)
            // A second "last" in the sentence is the facet's own ("Ali Raza ka last payment last month": his last payment, over a
            // span he cannot filter); a single one is the span's.
            val spanOwnsLast = spanLast
            // The same for the whole book: "since the beginning", "shuru se ab tak" hold the words of "since when is he a
            // customer" (15 Nov 2026: "Ali ne shuru se ab tak kitna diya" was his first day).
            val spanOwnsSince = spanSaid != null && (unsupported || spanSaid == VoiceQuestion.Window.ALL_TIME)
            // A paper of his, by what it holds (15 Nov 2026): "Syngenta ke bill ka number", "Ali ke invoice mein kya kya likha".
            if (spanSaid == null && figure == null) recordPaper(joined, mind, mind.has(HudhudMind.C.HOW_MANY), named = true)?.let {
                return Ask.Paper(it, named)
            }
            // What an entry or a payment itself holds (15 Nov 2026): its note, number, reference, way of paying, who wrote it —
            // "Ali Raza ki entry ka note", "Ali ka payment reference", "last entry kis ne likhi". The entry word is needed, so a
            // bare "Ali ka number" stays his phone; a count or a span is another question.
            if (spanSaid == null && figure == null && !mind.has(HudhudMind.C.HOW_MANY) && has(joined, ENTRY_DETAIL_WORDS)) {
                if (mind.has(HudhudMind.C.ENTRY)) return Ask.Person(named, Facet.LAST_ENTRY)
                if (mind.has(HudhudMind.C.PAYMENT)) return Ask.Person(named, Facet.LAST_PAYMENT)
            }
            facetOf(mind, spanLast)?.let { f ->
                val spansOwn = (spanOwnsLast && (f == Facet.LAST_PAYMENT || f == Facet.LAST_ENTRY)) || (spanOwnsSince && f == Facet.SINCE) ||
                    (spanOwnsSince && f == Facet.LAST_PAYMENT && !mind.has(HudhudMind.C.LAST) && mind.has(HudhudMind.C.SINCE_WHEN))
                if (!spansOwn) return Ask.Person(named, f)
            }
            // A named man who did not pay ("Ali Raza ne 500 nahi diye", Marhala 1, BC-321): his own account, the balance on its own side —
            // never the list of everybody who has not paid, and never an entry.
            if (figure == null || figure == VoiceQuestion.Kind.TO_GET || figure == VoiceQuestion.Kind.TO_GIVE ||
                figure == VoiceQuestion.Kind.NOT_PAID) {
                if (spanSaid != null && given != null && amount == null) return Ask.Moved(named, spanSaid, got = !given)
                // The noun of money that came, over a span, with no verb to say who gave ("Ali Raza ki payment last month", "علی کی
                // ادائیگی پچھلے مہینے"): what he paid in it, as "uski payment last month" is after his answer ([followUp]). It was his
                // account, or — by the sense — his last payment, with the span dropped (15 Nov 2026).
                if (spanSaid != null && amount == null && figure == null && mind.has(HudhudMind.C.PAYMENT)) {
                    return Ask.Moved(named, spanSaid, got = true)
                }
                return Ask.Parties(named)
            }
        }
        // "Urea kitni bik gayi": the stem alone is selling, with a count asked and nothing said against it. Not in the figure words
        // (VoiceQuestion): "kaun sa maal nahi bik raha" is the dead stock and "bik" there turned two of the owner's lists (15 Nov 2026).
        val soldStem = figure == null && mind.has(HudhudMind.C.HOW_MANY) && has(joined, listOf("bik", "बिक")) &&
            !has(joined, listOf("nahi", "nahin", "نہیں", "نہیں", "नहीं", "not"))
        if (product.isNotEmpty()) goodsAsk(mind, product, if (soldStem) VoiceQuestion.Kind.SALES_MONTH else figure, window)?.let { return it }
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
        talk(said, whole = false, names, goods)?.let { return it }
        // A greeting is the whole answer only when nothing else in the sentence asks the book (15 Nov 2026).
        val greetingOnly = courtesyOnly(said, names, goods)
        if (greetingOnly && has(joined, SALAAM)) return Ask.Salaam
        if (greetingOnly && has(joined, HELLO)) return Ask.Hello
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

    private fun actMove(said: List<String>, joined: String): Move? {
        val move = when {
            has(joined, ACT_FLY) -> Move.FLY
            has(joined, ACT_DANCE) -> Move.DANCE
            has(joined, ACT_JUMP) -> Move.JUMP
            else -> return null
        }
        val own = (ACT_FLY + ACT_DANCE + ACT_JUMP + ACT_LITTLE + PLEASE_DO).flatMap { words(it) }.toSet()
        return move.takeIf { said.all { it in own || it in QUIET_SET || it in VOCATIVE_SET || it in HIS_OWN_NAME } }
    }

    private val ACT_FLY = listOf("fly", "urr", "udo", "uro", "اڑ", "اڑو", "حلق", "طير", "پرواز", "اڏام", "اڏامو", "उड़", "उड़ो", "उड", "উড়", "উড়ে",
        "terbang")
    private val ACT_DANCE = listOf("dance", "naach", "naacho", "nacho", "ناچ", "ناچو", "نچ", "ارقص", "رقص", "برقص", "नाच", "नाचो", "নাচ", "নেচে",
        "menari", "joget")
    private val ACT_JUMP = listOf("hop", "jump", "uchhal", "uchlo", "اچھل", "اچھلو", "ٹپ", "نط", "بپر", "उछल", "उछलो", "লাফ", "লাফাও", "lompat")
    private val ACT_LITTLE = listOf("ek", "ek baar", "baar", "bar", "once", "one", "a", "little", "sekali", "ذرا", "ایک", "بار", "مرة", "مرہ", "एक",
        "बार", "একবার", "একটু", "کمی", "شوية", "يلا", "ورني", "ببینم", "جان", "ته", "ڏي", "ادا", "هڪ", "around", "the", "screen", "show", "dikhao",
        "دکھاؤ", "दिखाओ", "দেখাও", "کر", "کے", "کر کے", "ke", "kar", "کن", "بزن", "تو")

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
        // A head count in the words Bengali uses for people at work (review D: "কতজন কাজ করে" was the percent help page) and what is
        // running short in Arabic and Farsi ("ما الذي ينقص", "چه چیزهایی تمام می‌شود": Farsi's was the expiring batches).
        if (has(joined, STAFF_HEADCOUNT) && !m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER)) return Ask.Count(Thing.STAFF)
        if (has(joined, RUNNING_SHORT) && !m.any(HudhudMind.C.CASHBOOK, HudhudMind.C.MONEY, HudhudMind.C.BILL)) return Ask.Status(StatusOf.LOW_STOCK)
        if (has(joined, ZAKAT)) page("screen_zakat")?.let { return it }
        if (has(joined, AUTOMATION)) return Ask.Automations
        if (has(joined, BUSINESSES)) page("faq_business")?.let { return it }
        if (has(joined, SEASONS)) page("screen_seasons")?.let { return it }
        if (has(joined, SCHEMES)) {
            if (HudhudRecords.schemesStand(joined, m)) return Ask.Paper(PaperOf.SCHEMES)
            page("screen_schemes")?.let { return it }
        }
        if (has(joined, POSTERS)) page("screen_posters")?.let { return it }
        // Money that came in by its ways, and Indonesian's biggest "tagihan" (17 Nov 2026, L004): before the business card's page.
        if (cameInByWay(joined)) return Ask.Collected(window)
        if (biggestTagihan(joined)) return Ask.Status(StatusOf.BILLS)
        if (has(joined, CARDS)) {
            page(if (m.has(HudhudMind.C.CUSTOMER)) "intro_customer_qr" else "screen_card")?.let { return it }
        }
        // Customers who stopped coming (15 Nov 2026, review O: "kaun sa gahak aana band ho gaya" was the unpaid list, "which customers
        // stopped coming" the cash flow's "coming"): their own page, before the words of what is coming.
        if (has(joined, LAPSED)) page("screen_lapsed")?.let { return it }
        // Goods that came back ("kal kitna maal wapis aaya", review O: it was the goods that came in from suppliers): the returns.
        // Not money: money that came back is a repayment ("kitne paise wapas aaye") and a cheque that came back has bounced.
        if (has(joined, CAME_BACK) && !has(joined, SUPPLIER_SIDE_WORDS) && !m.any(HudhudMind.C.SUPPLIER, HudhudMind.C.CHEQUE, HudhudMind.C.MONEY)) {
            return Ask.Returns(window)
        }
        val expiry = VoiceQuestion.classify(joined) == VoiceQuestion.Kind.EXPIRING
        if (has(joined, CASH_FLOW) || (has(joined, AHEAD) && !expiry && !has(joined, CAME_ALREADY) &&
                !m.any(HudhudMind.C.STOCK, HudhudMind.C.LEFT, HudhudMind.C.CHEQUE, HudhudMind.C.PLAN))
        ) {
            page("screen_cashflow")?.let { return it }
        }
        // The invoices not paid in full (15 Nov 2026, the catalog's invoices gap: "Kis invoice ka paisa baqi hai" was the shop's
        // whole to-get, "Which invoices are unpaid" a count of invoices made). A quotation, a proforma, a sales order or a challan
        // is not an invoice and has no balance: the documents page lists each kind with its count ("How many quotations are open"
        // was the app's opens).
        if (unpaidInvoices(joined)) return Ask.Status(StatusOf.INVOICES)
        // The papers by what they hold (15 Nov 2026, the catalog's records group): a supplier bill's number, note and lines, a purchase
        // order's state, an invoice's lines, the schemes that run. Only the rules read them ([HudhudRecords], [decide]).
        recordPaper(joined, m, counting)?.let { return Ask.Paper(it) }
        // A supplier's bills not paid (15 Nov 2026, review O: "unpaid supplier bills" was a count of every bill): the bills that wait.
        // After the papers: a bill's own number or note is the paper's.
        if (unpaidSupplierBills(joined, m)) return Ask.Status(StatusOf.BILLS)
        if (has(joined, OTHER_DOCS)) page("intro_documents")?.let { return it }
        val billWord = has(joined, BILL_WORDS)
        if (!counting && (has(joined, DOCUMENTS) || (billWord && has(joined, MAKE_DOC) && !has(joined, UTILITY) &&
                !has(joined, SUPPLIER_SIDE_WORDS)))
        ) page("intro_documents")?.let { return it }
        if (has(joined, UTILITY)) page("intro_cashbook")?.let { return it }
        // A bare "bill" (15 Nov 2026, the owner's 1,000 sentences: "Bill WhatsApp par bhejo", "Kaun sa bill baqi hai", "Aaj ke
        // sab bill dikhao" were all read as the supplier bills). A shop says "bill" for three different papers — the one it
        // hands a customer, the one a supplier sends it, the one the light company sends it — and the word alone does not say
        // which. So no supplier or purchase word, no delivery ("aaye") and no name: what is DONE to it says it. Making,
        // sending, changing it or one customer's: the customer's bill, i.e. the documents page. Owing, late, a date, a
        // reminder: a bill to pay — the supplier's or the utility's, which the words cannot tell, so he is asked (the first
        // reading is the cashbook's, where a shop writes down what it paid out; the other is the supplier bills that wait).
        if (billWord && !m.any(HudhudMind.C.SUPPLIER, HudhudMind.C.CAME) && !has(joined, SUPPLIER_SIDE_WORDS) && !has(joined, PURCHASE_SIDE)) {
            when {
                has(joined, BILL_EDIT) || m.has(HudhudMind.C.CUSTOMER) -> page("intro_documents")?.let { return it }
                // Shown (17 Nov 2026, L004, the owner's K0652 "Aaj ke sab bill dikhao" meant his customers' bills): the invoices of
                // the days asked, with the supplier bills of the same days offered beside them.
                has(joined, SHOW_WORDS) && !has(joined, BILL_DUE) ->
                    return Ask.DidYouMean(listOf(Ask.Count(Thing.INVOICES, window), Ask.Count(Thing.BILLS, window)))
                has(joined, BILL_DUE) -> {
                    val cashbook = page("intro_cashbook")
                    if (cashbook != null) return Ask.DidYouMean(listOf(cashbook, Ask.Status(StatusOf.BILLS)))
                }
            }
        }
        // Whom to remind, what to chase, a message for the money (15 Nov 2026, the owner's list: "Kis ko yaad dilana hai" was read as
        // who made the app, "Kis ko phone karna hai" as somebody's phone number): the Follow-up page, which lists them and sends the
        // reminder one by one. Hudhud writes and sends nothing himself; nobody named here (byFeature runs only with none).
        if (remindPage(joined)) {
            // "Whom" is asked: the ones who have not paid, as the book holds them; else the page that does the reminding.
            if (has(joined, WHOM)) return Ask.Figure(VoiceQuestion.Kind.NOT_PAID)
            page("intro_followup")?.let { return it }
        }
        // Old or doubtful dues (15 Nov 2026, the owner's list: "kis ka udhaar sab se purana hai", "kis ka udhaar doob sakta hai",
        // "kaun payment dene mein sust hai"): the dues by age page ranks every customer by how long his money has been unpaid.
        if (agedDues(joined)) page("screen_aging")?.let { return it }
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
        // The best or the worst of the goods by profit, or the ones that do not move (15 Nov 2026, the owner's list): Insights ranks
        // the products by profit, and the sales report names the dead stock. Only an item word with the ranking or the loss.
        itemRankPage(joined)?.let { key -> page(key)?.let { return it } }
        if (window != null && m.has(HudhudMind.C.CREDIT) && !has(joined, PAID_BACK) && !has(joined, UNTIL_NOW) &&
            (has(joined, TOOK_OR_GAVE) || window == VoiceQuestion.Window.TODAY)
        ) {
            return Ask.Activity(window)
        }
        // A barcode said or typed with its word: which product it belongs to ([HudhudRecords.codeIn]).
        HudhudRecords.codeIn(joined)?.takeIf { HudhudRecords.asksWhoseCode(joined) }?.let { return Ask.NeedGoods(GoodsFacet.BARCODE, it) }
        if (has(joined, SOME_GOODS) && VoiceQuestion.classify(joined) == null) {
            val facet = HudhudRecords.facet(joined, m, null) ?: when {
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

    /** The paper a sentence asks about by what it holds ([HudhudRecords.paper]), with this file's own word lists for what it excludes. */
    private fun recordPaper(joined: String, m: HudhudMind.Read, counting: Boolean, named: Boolean = false): PaperOf? = HudhudRecords.paper(
        joined, m, makes = has(joined, MAKE_DOC), supplierSide = named || has(joined, SUPPLIER_SIDE_WORDS), purchaseSide = has(joined, PURCHASE_SIDE),
        billWord = has(joined, BILL_WORDS), documents = has(joined, DOCUMENTS), otherDocs = has(joined, OTHER_DOCS), utility = has(joined, UTILITY),
        counting = counting, named = named, standing = has(joined, BILL_DUE) || has(joined, UNPAID_WORDS)
    )

    /** Whether a sentence is about reminding customers for money, in no bill's or supplier's sense. */
    /** Old or doubtful money owed: an ageing word together with a word for money owed, nobody and no product named. */
    internal fun agedDues(joined: String): Boolean =
        has(joined, AGED_WORDS) && has(joined, OWED_WORDS) && !has(joined, BILL_WORDS) && !has(joined, SUPPLIER_SIDE_WORDS) &&
            !has(joined, PURCHASE_SIDE) && !has(joined, listOf("stock", "maal", "saman", "item", "cheez"))

    private val AGED_WORDS = listOf("sab se purana", "sabse purana", "purana udhaar", "purane udhaar", "bohat dair se", "bahut der se",
        "dair se baqi", "der se baqi", "mahine se pada", "mahino se pada", "tareekh guzar", "guzar gayi", "doob sakta", "doob jayega",
        "doobne", "sust", "mushkil lag rahi", "overdue", "oldest", "long overdue", "slow to pay", "slow payer", "slow payers",
        "bad debt", "bad debts", "longest", "the longest", "could be lost", "may be lost", "سب سے پرانا", "بہت دیر سے", "ڈوب سکتا", "ڈوب جائے", "تاریخ گزر",
        "سست", "सबसे पुराना", "बहुत देर से", "डूब सकता", "तारीख निकल", "सुस्त", "أقدم", "متأخر", "متأخرات", "قد يضيع", "قديم", "سڀ کان پراڻو",
        "قدیمی‌ترین", "معوق", "دیرکرد", "সবচেয়ে পুরনো", "ডুবে যেতে", "paling lama", "menunggak", "tunggakan", "jatuh tempo", "macet")
    private val OWED_WORDS = listOf("udhaar", "udhar", "baqi", "hisaab", "hisab", "wasooli", "wusooli", "payment", "payments", "paise", "pay", "paying",
        "debt", "debts", "dues", "owed", "owes", "owe", "due", "balance", "paisa", "money", "پیسہ", "पैसा", "اُدھار", "ادھار", "باقی", "حساب", "وصولی", "पैसे", "उधार",
        "बाकी", "हिसाब", "دين", "ديون", "حساب", "بدهی", "ادھار", "বাকি", "হিসাব", "টাকা", "piutang", "utang", "hutang", "pembayaran")

    /** An invoice word with a word for not paid, no supplier, purchase or bill word: the invoices with a balance still due. */
    internal fun unpaidInvoices(joined: String): Boolean =
        has(joined, INVOICE_WORDS) && has(joined, UNPAID_WORDS) && !has(joined, BILL_WORDS) && !has(joined, SUPPLIER_SIDE_WORDS) &&
            !has(joined, PURCHASE_SIDE)

    private val INVOICE_WORDS = listOf("invoice", "invoices", "فاکتورهای", "انوائسز", "انوائسوں", "انوائس", "انوائیس", "انوائيس", "इनवॉइस", "इनवाइस", "فاتورة", "فاتوره",
        "فواتير", "فاکتور", "فاکتورها", "ইনভয়েস", "faktur")
    private val UNPAID_WORDS = listOf("unpaid", "not paid",
        // 15 Nov 2026, review O: "haven't been paid", and the money "that has not come" said of a paper.
        "haven t been paid", "hasn t been paid", "not been paid", "paisa nahi aaya", "paise nahi aaye", "پیسہ نہیں آیا", "पैसा नहीं आया", "pending", "overdue", "outstanding", "balance", "due", "baqi", "baaqi",
        "bakaya", "wasool nahi", "ada nahi", "paisa baqi", "ادا نہیں", "باقی", "باقي", "وصول نہیں", "बकाया", "बाकी", "अदा नहीं",
        "वसूल नहीं", "غير مدفوعة", "غير مدفوعه", "غير مسددة", "مستحقة", "متبقي", "متأخرة", "বাকি", "বকেয়া", "পরিশোধ হয়নি",
        "belum dibayar", "belum lunas", "belum", "tunggakan", "jatuh tempo", "پرداخت نشده", "باقی‌مانده", "معوق", "معوقه")
    /** A document that is not an invoice: it has no balance, and the documents page counts each kind. */
    private val OTHER_DOCS = listOf("quotation", "quotations", "proforma", "sales order", "sales orders", "challan", "challans",
        "delivery note", "delivery notes", "کوٹیشن", "چالان", "عرض سعر", "عروض الأسعار", "پیش‌فاکتور", "کوٹیشنز", "कोटेशन", "चालान",
        "কোটেশন", "চালান", "penawaran", "surat jalan")

    internal fun remindPage(joined: String): Boolean =
        has(joined, REMIND_WORDS) && !has(joined, BILL_WORDS) && !has(joined, SUPPLIER_SIDE_WORDS) && !has(joined, PURCHASE_SIDE) &&
            !has(joined, listOf("automation", "automations", "notification", "notifications", "alarm", "stock", "maal", "saman", "item", "cheez"))

    /** The page for a sentence that ranks the goods by profit or says which do not move; null for any other. */
    internal fun itemRankPage(joined: String): String? {
        if (has(joined, listOf("kitne", "kitni", "how many", "count"))) return null
        val item = has(joined, ITEM_WORDS)
        return when {
            item && has(joined, PROFIT_WORDS) -> "screen_insights"
            item && has(joined, NOT_MOVING) -> "screen_sales_report"
            // "Sab se kam kya bikta hai": the least sold, with no day, month or customer asked about (the sales of a day are a figure).
            has(joined, LEAST) && has(joined, SELLING) && !has(joined, NOT_GOODS) -> "screen_sales_report"
            else -> null
        }
    }

    /** The rules' own "which bill?" question for a bill word with no supplier or purchase word beside it. */
    private fun isBareBillChoice(ruled: Ask, heard: String): Boolean {
        if (ruled !is Ask.DidYouMean) return false
        val first = ruled.options.firstOrNull() as? Ask.Help ?: return false
        if (first.key != "intro_cashbook" || ruled.options.getOrNull(1) != Ask.Status(StatusOf.BILLS)) return false
        val joined = words(heard).joinToString(" ")
        return has(joined, BILL_WORDS) && has(joined, BILL_DUE) && !has(joined, SUPPLIER_SIDE_WORDS) && !has(joined, PURCHASE_SIDE)
    }

    /** Whether [heard] names one of the features [byFeature] opens by its page, or asks to add something new to one. */
    private fun featureNamed(heard: String): Boolean {
        val joined = words(heard).joinToString(" ")
        return listOf(ZAKAT, BUSINESSES, SEASONS, SCHEMES, POSTERS, CARDS, CASH_FLOW, AHEAD, DOCUMENTS, UTILITY, BIN_WORDS, FIX_WORDS,
            LOCK_WORDS, HIDE_WORDS, ADD_NEW, STAFF_HEADCOUNT, OWE_SUPPLIER, RUNNING_SHORT)
            .any { has(joined, it) } ||
            (has(joined, BILL_WORDS) && has(joined, MAKE_DOC)) ||
            (has(joined, BILL_WORDS) && has(joined, BILL_EDIT) && !has(joined, SUPPLIER_SIDE_WORDS) && !has(joined, PURCHASE_SIDE)) ||
            itemRankPage(joined) != null ||
            remindPage(joined) || agedDues(joined) || has(joined, LAPSED) || erasesOrder(joined) ||
            (asksTheWay(heard) && listOf(NEW_PHONE, LANGUAGE_WORDS, DELETE_WORDS, PLAN_WORDS, listOf("پیش فاکتور")).any { has(joined, it) }) ||
            (has(joined, OTHER_DOCS))
    }

    private val LOCK_WORDS = listOf("lock", "app lock", "applock", "pin", "pin code", "password", "passcode", "fingerprint",
        "finger print", "screen lock", "taala", "tala lagao", "لاک", "تالا", "پن", "پن کوڈ", "پاس ورڈ", "فنگر پرنٹ", "قفل",
        "رمز", "رمز المرور", "كلمة المرور", "بصمة", "بصمة الإصبع", "گذرواژه", "رمز عبور", "اثر انگشت", "لاڪ", "پاسورڊ", "ताला",
        "लॉक", "पिन", "पासवर्ड", "फिंगरप्रिंट", "লক", "পিন", "পাসওয়ার্ড", "আঙুলের ছাপ", "kunci", "kata sandi", "sandi",
        "sidik jari",
        // 15 Nov 2026, review O: the recovery code and Arabic's "secret number" are the lock's too, never a figure.
        "recovery code", "recover code", "أقفل", "اقفل", "رقم سري", "الرقم السري", "رقم السر", "رقم سر", "السري")
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
        "dalna", "نیا", "نئی", "نئے", "شامل", "ایڈ", "جديد", "جديدة", "جديدا", "أضف", "أضيف", "اضيف", "إضافة", "جدید", "اضافه", "افزودن", "نئون", "نئين",
        "شامل ڪريو", "नया", "नई", "नए", "जोड़ना", "जोड़ो", "ऐड", "নতুন", "যোগ", "যোগ করুন", "baru", "tambah", "tambahkan")
    /** What a member of staff may see or do. */
    private val ACCESS_WORDS = listOf("access", "ijazat", "ikhtiyar", "permission", "kya kya dikhe", "kya dikhta", "kya dekh",
        "role", "اجازت", "اختیار", "رسائی", "صلاحية", "صلاحيات", "دسترسی", "اجازه", "اجازت", "अनुमति", "पहुँच", "অনুমতি",
        "অ্যাক্সেস", "akses", "izin")
    private val QARZ = listOf("qarz e hasna", "qarze hasna", "qarz hasna", "qarzehasna", "qarz e hasana", "qarz e husna",
        "karz e hasna", "qarz-e-hasna", "قرض حسنہ", "قرضِ حسنہ", "قرض حسنه", "قرض الحسن", "قرضا حسنا", "قرض حسن",
        "قرض‌الحسنه", "قرض الحسنه", "قرض حسنو", "क़र्ज़ ए हसना", "कर्ज़ ए हसना", "कर्ज ए हसना", "क़र्ज़-ए-हसना", "করজে হাসানা",
        "কর্জে হাসানা", "কর্জে হাসানাহ", "qardhul hasan", "qardh hasan", "pinjaman kebajikan",
        // 15 Nov 2026, review D: the same loan said as "a loan without interest".
        "interest free loan", "interest free loans", "loan without interest", "loans without interest", "no interest loan",
        "zero interest loan", "bina sood ka qarz", "bina sood ke qarz", "bina sood qarz", "sood ke baghair qarz", "bila sood qarz",
        "بلا سود قرض", "بغیر سود کے قرض", "بغیر سود قرض", "سود کے بغیر قرض", "قرض بدون فوائد", "قرض بلا فوائد", "قرض بدون فائدة",
        "قرض بلا فائدة", "وام بدون بهره", "وام بی بهره", "وام بی‌بهره", "قرض بدون بهره", "بنا سود قرض", "बिना ब्याज का कर्ज",
        "बिना ब्याज कर्ज", "बिना ब्याज का क़र्ज़", "बिना ब्याज का उधार", "সুদমুক্ত ঋণ", "সুদ ছাড়া ঋণ", "বিনা সুদে ঋণ",
        "সুদবিহীন ঋণ", "সুদহীন ঋণ", "pinjaman tanpa bunga", "utang tanpa bunga", "pinjaman bebas bunga")
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
        "طرح فروش", "स्कीम", "স্কিম", "skema", "skema promo",
        // 15 Nov 2026 (records group): how the offers of a company are said where "scheme" is not the word.
        "عروض الشركة", "عروض الشركات", "عروض الموردين", "العروض الجارية", "عروض جارية", "طرح تشویقی", "طرح های تشویقی",
        "طرح‌های تشویقی", "طرح های فعال", "طرح‌های فعال", "offers", "ऑफर", "অফার")
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
        "استرجاع", "سطل بازیافت", "بازگردانی", "रीसायकल बिन", "वापस लाना", "রিসাইকেল বিন", "ফিরিয়ে আনা", "tempat sampah", "pulihkan",
        // 15 Nov 2026, review O: Indonesian "deleted by accident".
        "terhapus", "tidak sengaja terhapus")
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
        "سپلائر", "کمپنی", "ڈیلر", "المورد", "الموردين", "الشركة", "تامین‌کننده", "تامین کننده", "شرکت", "سپلائر", "सप्लायर", "कंपनी", "সরবরাহকারী",
        "কোম্পানি", "pemasok", "perusahaan")
    private val CAME_ALREADY = listOf("aaye", "aye", "aaya", "aya", "aai", "aayi", "mile", "mila", "came", "آئے", "آیا", "ملے",
        "आए", "आया", "মিলেছে", "এসেছে", "datang")
    private val MAKE_DOC = listOf("banana", "banao", "bana do", "banaun", "banayen", "bhejo", "bhejna", "bhej do", "pdf",
        "print", "copy", "naqal", "cancel", "design", "template", "letterhead", "muhar", "stamp", "make", "create", "send",
        "بناؤ", "بنانا", "بنائیں", "بھیجو", "بھیجنا", "پرنٹ", "اعمل", "أرسل", "بساز", "بفرست", "بڻايو", "موڪليو", "बनाओ",
        "बनाना", "भेजो", "प्रिंट", "বানাও", "তৈরি", "পাঠাও", "প্রিন্ট", "buat", "kirim", "cetak")
    /** What a shop does to the bill it hands a customer, said without a word of the document's kind. */
    private val BILL_EDIT = listOf("discount", "chhoot", "chhut", "qeemat", "rate badlo", "naam likh", "number likh", "likhna",
        "badlo", "badalna", "edit", "muhar", "whatsapp", "cheez add", "item add", "ڈسکاؤنٹ", "چھوٹ", "قیمت بدلو", "نام لکھ",
        "بدلو", "واٹس ایپ", "خصم", "سعر", "غيّر", "واتساب", "تخفیف", "قیمت", "تغییر", "ڇوٽ", "डिस्काउंट", "छूट", "कीमत बदलो",
        "बदलो", "व्हाट्सएप", "ছাড়", "দাম বদলাও", "হোয়াটসঅ্যাপ", "diskon", "ubah harga", "ganti")
    /** A bill that is owed: still to pay, late, its day, a reminder for it. */
    private val BILL_DUE = listOf("baqi", "baqaya", "bacha", "late", "dene", "deni", "dena", "ada", "tareekh", "yaad dahani",
        "yaad", "due", "pending", "unpaid", "bakaya", "باقی", "بقایا", "بچا", "دینے", "دینی", "دینا", "ادا", "تاریخ", "یاد دہانی",
        "مستحق", "متأخر", "المتبقي", "تذكير", "سررسید", "باقی‌مانده", "پرداخت", "باقي", "ڏيڻ", "बाकी", "देने", "देना", "तारीख",
        "याद", "বাকি", "দিতে", "তারিখ", "jatuh tempo", "belum dibayar", "tenggat", "tunggakan")
    /** The purchase side of a bill said without "supplier": goods bought. */
    private val PURCHASE_SIDE = listOf("maal ka", "maal ke", "purchase", "khareed", "kharid", "stock ka", "مال کا", "مال کے",
        "خرید", "شراء", "مشتريات", "خرید", "खरीद", "माल का", "ক্রয়", "pembelian")
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
    private val STAFF_HEADCOUNT = listOf("কতজন কাজ করে", "কতজন কাজ করেন", "কয়জন কাজ করে", "কয়জন কাজ করেন", "কত জন কাজ করে", "কত জন কাজ করেন")
    /** "How much must I pay the supplier": what the shop owes, not a count of suppliers (the count's "how many" is also "how much" in Arabic and Indonesian). */
    private val OWE_SUPPLIER = listOf("أدفع للمورد", "ادفع للمورد", "أدفع للموردين", "ادفع للموردين", "أدين للمورد", "علي للمورد", "على للمورد", "للمورد علي",
        "bayar ke pemasok", "bayar kepada pemasok", "bayar ke supplier", "bayar kepada supplier", "utang ke pemasok", "utang saya ke pemasok", "utang kepada pemasok")
    /**
     * Arabic "I have not paid" (24 Nov 2026, BC-256): the shop's own unpaid, first person — "لم أدفع", "لم أسدد", "ما دفعت". Said of a
     * customer in the third person ("لم يدفعوا لي") it is [VoiceQuestion]'s NOT_PAID and never here.
     */
    private val NOT_PAID_BY_ME = listOf("لم أدفع", "لم ادفع", "لم أسدد", "لم اسدد", "ما دفعت", "ما سددت", "لم نسدد", "لم ندفع")
    private val SUPPLIER_ARABIC = listOf("المورد", "الموردين", "الموردون", "مورد", "موردين", "موردون", "الشركات", "شركات")

    /** An Arabic sentence that says which suppliers the shop has not paid: what it owes them, never the goods that came. */
    private fun unpaidSuppliersArabic(joined: String): Boolean = has(joined, NOT_PAID_BY_ME) && has(joined, SUPPLIER_ARABIC)

    private val RUNNING_SHORT = listOf("ما الذي ينقص", "ما الذى ينقص", "ما الذي ينفد", "ما الذي قارب على النفاد", "ما الذي يوشك على النفاد",
        "چه چیزهایی تمام می‌شود", "چه چیزی تمام می‌شود", "چه چیزهایی تمام می شود", "چه چیزی تمام می شود", "چه چیزهایی کم است", "چه چیزی کم است")
    private val WHOM = listOf("kis ko", "kisko", "kin ko", "kin se", "kis se", "kaun", "kon", "kis", "kin", "who", "whom", "کس کو", "کن کو",
        "کس سے", "کن سے", "کون", "کس", "من", "مين", "کی را", "چه کسی", "किस को", "किसको", "किन को", "कौन", "किस", "কাকে", "কে", "কারা",
        "ڪنهن کي", "ڪير", "siapa", "kepada siapa")
    private val REMIND_WORDS = listOf("yaad nahi dilaya", "yaad nahi dilana", "yaad nahi dilao", "yaad dilana", "yaad dilao", "yaad dila", "yaad dilaya", "yaad dilane", "yaad dahani", "yaad-dahani",
        "yaad dehani", "reminder", "reminders", "remind", "follow up", "followup", "follow-up", "phone karna", "phone karun", "phone karne",
        "call karna", "call karun", "payment ka message", "payment ki request", "payment request", "paise mangne", "paise mangna",
        "paise mangun", "wasooli ki baari", "ek saath message", "sab ko message", "یاد دلانا", "یاد دلاؤ", "یاد دلانے", "یاد دہانی",
        "ریمائنڈر", "فالو اپ", "فون کرنا", "فون کرنے", "پیمنٹ کا میسج", "پیسے مانگنے", "وصولی کی باری", "تذكير", "ذكّر", "ذكر", "تذكيرات",
        "اتصل", "مطالبة", "یادآوری", "یادآور", "پیگیری", "تماس بگیرم", "ياد ڏيارڻ", "ياد ڏياريو", "रिमाइंडर", "याद दिला", "याद दिलाना",
        "फॉलो अप", "फोन करना", "पेमेंट का मैसेज", "पैसे मांगने", "রিমাইন্ডার", "মনে করিয়ে", "ফলো আপ", "ফোন করতে", "ingatkan",
        "mengingatkan", "pengingat", "tagih", "menagih", "tindak lanjut")
    private val ITEM_WORDS = listOf("cheez", "cheezein", "cheezon", "chiz", "item", "items", "maal", "saman", "samaan", "product",
        "products", "goods", "چیز", "چیزیں", "آئٹم", "مال", "سامان", "پروڈکٹ", "منتج", "سلعة", "بضاعة", "كالا", "کالا", "جنس",
        "شيءِ", "सामान", "माल", "चीज़", "चीज", "आइटम", "জিনিস", "মাল", "পণ্য", "barang", "produk")
    private val PROFIT_WORDS = listOf("munafa", "munafe", "nafa", "faida", "fayda", "faide", "kamai", "profit", "nuqsan", "nuksan",
        "loss", "margin", "منافع", "منافع", "فائدہ", "فائدے", "کمائی", "نقصان", "ربح", "أرباح", "خسارة", "سود", "زیان", "نفع",
        "फायदा", "मुनाफा", "नुकसान", "कमाई", "লাভ", "ক্ষতি", "আয়", "untung", "laba", "rugi")
    private val NOT_MOVING = listOf("chal nahi raha", "nahi chal raha", "nahi bik", "nahi bikta", "nahi bikti", "nahi bika", "bohat dair se",
        "dead stock", "not selling", "not moving", "slow moving", "paisa phansa", "phansa hua", "نہیں بک", "نہیں چل", "پھنسا",
        "لا يباع", "راكد", "نمی‌فروشد", "فروش نمی", "नहीं बिक", "नहीं चल", "फंसा", "বিক্রি হচ্ছে না", "আটকে", "tidak laku", "tidak terjual",
        "macet")
    private val NOT_GOODS = listOf("din", "day", "days", "mahina", "mahine", "month", "hafta", "hafte", "week", "customer", "customers",
        "gahak", "گاہک", "دن", "مہینہ", "ہفتہ", "کسٹمر", "یوم", "شهر", "عميل", "روز", "ماه", "ग्राहक", "दिन", "महीना", "গ্রাহক", "দিন",
        "hari", "bulan", "pelanggan")
    private val LEAST = listOf("sab se kam", "sabse kam", "sab se kum", "least", "lowest", "worst", "kam se kam", "سب سے کم",
        "الأقل", "کمترین", "سڀ کان گھٽ", "सबसे कम", "সবচেয়ে কম", "paling sedikit", "paling kurang")
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

    // The thank-you and goodbye words ask nothing either (15 Nov 2026): "thanks what is my balance" is read as the balance.
    private fun quiet(w: String) = w in QUIET_SET || w in COURTESY_WORDS || HudhudMind.meaningOf(w) == AND_ONLY

    private val AND_ONLY = setOf(HudhudMind.C.AND)

    /** Every figure of money owed, either way (review O). */
    private val MONEY_OWED_FIGURES = setOf(VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.TO_GIVE, VoiceQuestion.Kind.NOT_PAID,
        VoiceQuestion.Kind.WHO_OWES_MOST, VoiceQuestion.Kind.WHO_OWES_LEAST)

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
        // The word for "safe" may be a person ("Aman", "Mehfooz", "Aamna"): when the book has someone by it, only a privacy word of
        // the sentence's own (the data, the app) makes it a question about privacy — "who is Aman" and "Aman ka hisaab" are his.
        val safeIsAName = m.said.any { w ->
            HudhudMind.isSoftWord(w) && HudhudMind.C.SAFE in HudhudMind.meaningOf(w) && known(w)
        }
        // Safety said in its own spelling, never only by the sound of another word (26 Nov 2026, BC-357): the Arabic "سكر" (sugar) meets
        // "secure" by its consonants, and "هل عندنا سكر" (have we sugar?) was the privacy page.
        val safeSpelt = m.said.indices.any { HudhudMind.C.SAFE in m.at(it) && it !in m.bySound }
        if (safeSpelt && (!safeIsAName || m.any(HudhudMind.C.DATA, HudhudMind.C.APP)) &&
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
        // Attendance is no date (17 Nov 2026, L004): Hindi "हाजरी" sounds like "हिजरी" (the Islamic date) to the concept words, and
        // "आज की हाज़री" was told the date. The staff reader reads it ([recordOf]).
        if (has(m.said.joinToString(" "), ATTENDANCE_NOUNS)) return false
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
    /**
     * What is asked of a customer by the concepts of a sentence. [spanLast]: its one "last" is a span's ([lastIsSpansOwn]), no word
     * for his last payment or last entry — "Ali Raza ke baare mein last year" is his details, "uski kitni entries last year" how many
     * entries he has, and neither the last entry (15 Nov 2026).
     */
    private fun facetOf(m: HudhudMind.Read, spanLast: Boolean = false): Facet? {
        val last = !spanLast && m.has(HudhudMind.C.LAST)
        return when {
        m.has(HudhudMind.C.PHONE) -> Facet.PHONE
        m.has(HudhudMind.C.LIMIT) -> Facet.LIMIT
        m.has(HudhudMind.C.PROMISE) -> Facet.PROMISE
        // "Since when has he not paid" is his last payment, not the day he began (25 Nov 2026, H4: the rules said SINCE, the retrained sense 0.89 sure of the payment).
        (m.has(HudhudMind.C.SINCE_WHEN) && !m.has(HudhudMind.C.PAYMENT)) || (m.has(HudhudMind.C.FIRST) && m.any(HudhudMind.C.ENTRY, HudhudMind.C.WHEN, HudhudMind.C.DATE, HudhudMind.C.PAYMENT)) -> Facet.SINCE
        m.has(HudhudMind.C.PAYMENT) && (last || m.any(HudhudMind.C.WHEN, HudhudMind.C.DATE, HudhudMind.C.SINCE_WHEN)) -> Facet.LAST_PAYMENT
        last || (m.has(HudhudMind.C.ENTRY) && m.any(HudhudMind.C.WHEN, HudhudMind.C.DATE)) -> Facet.LAST_ENTRY
        m.any(HudhudMind.C.ENTRY, HudhudMind.C.TIMES) && m.any(HudhudMind.C.HOW_MANY, HudhudMind.C.ALL) -> Facet.ENTRIES
        m.any(HudhudMind.C.FATHER, HudhudMind.C.VILLAGE, HudhudMind.C.ADDRESS, HudhudMind.C.INFO, HudhudMind.C.WHERE, HudhudMind.C.NAME) -> Facet.DETAILS
        m.has(HudhudMind.C.WHO) && !m.any(HudhudMind.C.PAYMENT, HudhudMind.C.ENTRY, HudhudMind.C.HOW_MANY) -> Facet.DETAILS
        else -> null
        }
    }

    /**
     * What is asked about a named product: its suppliers' rates (cheaper, dearer, from whom), when it last came, its
     * purchase rate, who bought it, what is left, its price, how much of it sold; everything when only its name is
     * said. A figure the product does not change (this month's profit, what expires) is left to the figure.
     */
    private fun goodsAsk(m: HudhudMind.Read, ids: List<Long>, figure: VoiceQuestion.Kind?, window: VoiceQuestion.Window?): Ask? {
        // A product's own record — its label, barcode, pack, tax, shelf, day added, batches, schemes (15 Nov 2026, [HudhudRecords]).
        HudhudRecords.facet(m.said.joinToString(" "), m, figure)?.let { return Ask.Goods(ids, it) }
        val sold = figure == VoiceQuestion.Kind.SALES_TODAY || figure == VoiceQuestion.Kind.SALES_MONTH
        val buyers = (m.has(HudhudMind.C.WHO) && m.has(HudhudMind.C.BOUGHT) && !m.has(HudhudMind.C.PRICE)) || m.has(HudhudMind.C.CUSTOMER)
        val facet = when {
            sold -> GoodsFacet.SOLD
            m.any(HudhudMind.C.CHEAP, HudhudMind.C.DEAR, HudhudMind.C.SUPPLIER, HudhudMind.C.FROM_WHOM) -> GoodsFacet.SUPPLIERS
            m.has(HudhudMind.C.CAME) && m.any(HudhudMind.C.LAST, HudhudMind.C.WHEN, HudhudMind.C.DATE) -> GoodsFacet.LAST_IN
            // How long since it came ("Urea aaye kitne din ho gaye", "how many days since Urea came"; 17 Nov 2026, L004): the last
            // delivery, whose day the card gives. It was the product's overview.
            cameAgo(m.said.joinToString(" "), m) -> GoodsFacet.LAST_IN
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

    /** A how-much with its side stated ([VoiceQuestion.sideStated]) and no "who": the shop's total to get or to give. */
    private fun totalStated(heard: String, joined: String): Boolean =
        VoiceQuestion.sideStated(heard) && has(joined, HOW_MUCH_SAID) && !has(joined, WHO_SAID)

    private val HOW_MUCH_SAID = listOf("kitna", "kitni", "kitne", "how much", "in total", "کتنا", "کتنی", "کتنے", "कितना", "कितनी", "कितने",
        "چقدر", "در کل", "berapa", "كم", "কত", "ڪيترو", "ڪيتري")
    private val WHO_SAID = listOf("kaun", "kon", "kis", "who", "whom", "کون", "کس", "कौन", "किस", "কে", "কারা", "ڪير", "ڪنهن", "چه کسی", "کیست",
        "siapa", "من هو", "من الذي")

    /** How long since something came: a word of coming beside a word of time gone by ([ELAPSED]). */
    internal fun cameAgo(joined: String, m: HudhudMind.Read): Boolean =
        (m.has(HudhudMind.C.CAME) || has(joined, CAME_ALREADY)) && has(joined, ELAPSED)

    private val ELAPSED = listOf("kitne din ho gaye", "kitne din ho gae", "kitne din hogaye", "kitne din hue", "kitne din huay", "kitne din se",
        "kitna arsa", "kitna waqt ho gaya", "kitna time ho gaya", "how many days since", "how long since", "how long ago", "days ago",
        "کتنے دن ہو گئے", "کتنے دن ہوگئے", "کتنے دن ہوئے", "کتنا عرصہ", "کتنا وقت ہو گیا", "कितने दिन हो गए", "कितने दिन हुए",
        "कितना समय हो गया")

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
            // "Number kis ka hai" asks whose (25 Nov 2026, H4: the retrained sense took the "kis" for who owes most): a person's number.
            m.has(HudhudMind.C.PHONE) && m.has(HudhudMind.C.WHO) && !his && !mine && !m.any(HudhudMind.C.CUSTOMER, HudhudMind.C.SUPPLIER, HudhudMind.C.STAFF) ->
                return Ask.NeedName(Facet.PHONE)
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
            (m.any(HudhudMind.C.GOODS, HudhudMind.C.STOCK, HudhudMind.C.SUPPLIER, HudhudMind.C.BILL, HudhudMind.C.LAST) || m.has(HudhudMind.C.WHEN)) ||
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
        window: VoiceQuestion.Window?, spanOwn: SpanWords, willPay: Boolean, spanLast: Boolean, before: Ask?
    ): Ask? {
        if (before == null) return null
        val plain = plainAround(m, spanOwn)
        // A span said with "how much" is the same: "aur pichle saal kitna?" (15 Nov 2026: it was the cashbook, or the tickets).
        val carrying = plainAround(m, spanOwn, SPAN_ASKS)
        if (m.has(HudhudMind.C.HIS) && named.isEmpty() && product.isEmpty()) {
            partyOf(before)?.let { ids ->
                if (willPay) return Ask.WhenPays(ids)
                facetOf(m, spanLast)?.let { return Ask.Person(ids, it) }
                // "Aur uska pata" (review O): "pata" is "know" alone, and of him it is his address.
                if (m.said.any { it in PATA_WORDS }) return Ask.Person(ids, Facet.DETAILS)
                if (window != null && m.has(HudhudMind.C.PAYMENT)) {
                    val got = (before as? Ask.Moved)?.got ?: true
                    return Ask.Moved(ids, window, got)
                }
                if (figure == null || figure == VoiceQuestion.Kind.TO_GET || figure == VoiceQuestion.Kind.TO_GIVE) return Ask.Parties(ids)
            }
            goodsOf(before)?.let { ids -> goodsAsk(m, ids, figure, window)?.let { return it } }
        }
        // A sentence that is nothing but a span carries the last question over it — before the bare facets below: English's
        // "last week?" holds the word of "last payment" and, after what a customer paid, was his last entry (15 Nov 2026).
        if (named.isEmpty() && product.isEmpty() && figure == null && window != null && carrying) carryOver(before, window)?.let { return it }
        // A bare question about the one the last answer was about, with no "uska" (13 Nov 2026: the chips' own words,
        // typed or said — "number?", "aakhri adaigi", "waade ka din", "tafseel", "kab dega?", "rate?", "stock?").
        // Nobody else is named and the sentence asks nothing of its own; a figure word stays the shop's figure, except
        // the "udhaar" of "udhaar ki hadd" (a limit) and of a promise.
        if (named.isEmpty() && product.isEmpty()) {
            partyOf(before)?.takeIf { it.size == 1 }?.let { ids ->
                if (willPay && !m.has(HudhudMind.C.WHO)) return Ask.WhenPays(ids)
                val facet = facetOf(m, spanLast) ?: bareFacet(m)
                val ownWords = figure == null || (figure in OWED_FIGURES && facet in OWED_FACETS)
                if (facet != null && ownWords && m.onlyOf(PERSON_OK, ::quietOrDay)) return Ask.Person(ids, facet)
                if (figure == null && m.has(HudhudMind.C.ACCOUNT) && m.onlyOf(BALANCE_OK, ::quiet)) return Ask.Parties(ids)
            }
            goodsOf(before)?.takeIf { it.size == 1 }?.let { ids ->
                // "Barcode?", "aur expiry?", "registration number" after a product's answer: its own record (15 Nov 2026).
                if (onlyRecordWords(m)) HudhudRecords.facet(m.said.joinToString(" "), m, figure)?.let { return Ask.Goods(ids, it) }
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
                // "Aur Ali?" after the shop's sales or their trend: what he took in the same days; after the money that came in: what he
                // paid in them (25 Nov 2026, plan H5). The span is the last answer's own unless the sentence says another.
                is Ask.Figure -> if (before.kind == VoiceQuestion.Kind.SALES_TODAY || before.kind == VoiceQuestion.Kind.SALES_MONTH) {
                    val own = if (before.kind == VoiceQuestion.Kind.SALES_TODAY) VoiceQuestion.Window.TODAY else VoiceQuestion.Window.THIS_MONTH
                    return Ask.Moved(named, window ?: own, false)
                }
                is Ask.Period -> if (before.measure == Measure.SALES) return Ask.Moved(named, window ?: before.window, false)
                is Ask.Trend -> return Ask.Moved(named, window ?: before.window, false)
                is Ask.Collected -> return Ask.Moved(named, window ?: before.window ?: VoiceQuestion.Window.TODAY, true)
                else -> {}
            }
        }
        if (product.isNotEmpty() && named.isEmpty() && plain && before is Ask.Goods) {
            return Ask.Goods(product, before.facet, window ?: before.window)
        }
        return null
    }

    /**
     * The question of the last answer, over another span of days ("aur pichle saal?", "and last week", "aur shuru se ab
     * tak"): the sales or the profit, what a customer paid, a count, the deliveries, the day's doings, the money that came in,
     * the cashbook, the returns, the top goods, a product's sale. Null for an answer that has no span (a balance, a phone).
     */
    private fun carryOver(before: Ask, window: VoiceQuestion.Window): Ask? = when (before) {
        is Ask.Figure -> when (before.kind) {
            VoiceQuestion.Kind.SALES_TODAY, VoiceQuestion.Kind.SALES_MONTH -> figureOver(VoiceQuestion.Kind.SALES_MONTH, window)
            VoiceQuestion.Kind.PROFIT_MONTH -> figureOver(VoiceQuestion.Kind.PROFIT_MONTH, window)
            else -> null
        }
        is Ask.Period -> figureOver(
            if (before.measure == Measure.SALES) VoiceQuestion.Kind.SALES_MONTH else VoiceQuestion.Kind.PROFIT_MONTH, window
        )
        is Ask.Moved -> Ask.Moved(before.ids, window, before.got)
        // "Aur pichle mahine?" after a trend: the same trend over that month and the one before it (25 Nov 2026, plan H5).
        is Ask.Trend -> if (window == VoiceQuestion.Window.ALL_TIME) null else Ask.Trend(window)
        is Ask.Count -> Ask.Count(before.thing, window)
        is Ask.Deliveries -> Ask.Deliveries(window)
        is Ask.Activity -> Ask.Activity(window)
        is Ask.Collected -> Ask.Collected(window)
        is Ask.Cashbook -> Ask.Cashbook(window)
        is Ask.Returns -> Ask.Returns(window)
        is Ask.TopGoods -> Ask.TopGoods(window)
        is Ask.Counter -> if (spanMatters(before)) Ask.Counter(before.facet, window, before.ids) else null
        is Ask.Goods -> Ask.Goods(before.ids, before.facet, window)
        else -> null
    }

    /**
     * True when a sentence adds nothing of its own to the question it carries on: every word means nothing Hudhud knows, is
     * one of the follow-up's little words ([FOLLOW_OK]), is a word of the span itself ("last" in "last year", the "since" and
     * "beginning" of "since the beginning" — see [VoiceQuestion.spanWordsIn]) or is the "last" of a last span that the app
     * language writes in another word (Bengali "শেষ ৬ মাস", Arabic "آخر 6 أشهر").
     */
    private fun plainAround(m: HudhudMind.Read, own: SpanWords, extra: Set<HudhudMind.C> = emptySet()): Boolean = m.said.indices.all { i ->
        m.said[i] in own.words || m.at(i).all { it in FOLLOW_OK || it in extra || (own.last && it == HudhudMind.C.LAST) }
    }

    /**
     * True when a sentence is nothing but a span: its words, a count, the day's words and the little words between, each
     * one of them known — a word of its own that Hudhud knows not ("pichle saal bikri wala"), or a name, is not a bare span.
     */
    private fun onlySpan(m: HudhudMind.Read, own: SpanWords): Boolean = m.said.indices.all { i ->
        val w = m.said[i]
        quietOrDay(w) || w in own.words || w == JOINING_AND || w.all { it.isDigit() } || VoiceEntry.countWord(w) != null ||
            m.at(i).let { c -> c.isNotEmpty() && c.all { it in FOLLOW_OK || (own.last && it == HudhudMind.C.LAST) } }
    }

    /** Arabic's and Persian's "and" is one letter (also a letter of many words): it joins and means nothing else in a span. */
    private const val JOINING_AND = "و"

    /**
     * The span a sentence says ([VoiceQuestion.spanWordsIn]) and whether its "last" is the span's own (a last span, or a count
     * he cannot give: "last 3 months"): the words that a carried question or a bare span may count as the span's.
     */
    private class SpanWords(val words: Set<String>, val last: Boolean) {
        companion object { val NONE = SpanWords(emptySet(), false) }
    }

    /**
     * The reading a span said on purpose gets (15 Nov 2026): carried on from the answer before when there is one, else the
     * sales over it taken as a guess. The sense is never allowed to turn it ([decide]): it learnt no sentence of "and last
     * year", and sure of one word it turned the carried question into another (Farsi "و پارسال" into the cashbook, an English
     * "and last 6 months" after a profit into the sales).
     */
    private fun spanReading(heard: String, before: Ask?): Ask? {
        val window = VoiceQuestion.windowOf(heard) ?: if (VoiceQuestion.unsupportedSpan(heard)) VoiceQuestion.Window.THIS_MONTH else return null
        return before?.let { carryOver(it, window) } ?: Ask.Guessed(figureOver(VoiceQuestion.Kind.SALES_MONTH, window))
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

    /** A bare question that is nothing but a product's record field and the little words that carry on a talk. */
    private fun onlyRecordWords(m: HudhudMind.Read): Boolean = m.said.indices.all { i ->
        quietOrDay(m.said[i]) || VoiceQuestion.saysAny(m.said[i], HudhudRecords.FIELD_WORDS) ||
            m.at(i).let { c -> c.isNotEmpty() && c.all { it in GOODS_OK } }
    }

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
        is Ask.OnDays -> a.ids
        is Ask.Took -> a.ids
        else -> null
    }?.takeIf { it.isNotEmpty() }

    /** The product an answer was about, for "iska rate\u2026". */
    private fun goodsOf(a: Ask): List<Long>? = (a as? Ask.Goods)?.ids?.takeIf { it.isNotEmpty() }

    /** One of the book's own names (a customer, a supplier, a product), recognised outright. */
    /** Tests: [knownName], whose quick index ([Prepared]) HudhudNameIndexTest holds to the slow search. */
    internal fun knownNameForTest(w: String, names: List<Name>): Boolean = knownName(w, names)

    private fun knownName(w: String, names: List<Name>): Boolean =
        names.isNotEmpty() && !shortOrdinaryPrefix(w, names) &&
            ((!foldBlocked(w, names) && prepared(names).strong(w)) ||
                meetsAcrossScripts(w, names))

    /**
     * Whether a said word may not name one of [names] by the search's sound alone (15 Nov 2026). The search folds every script
     * into one alphabet, so a word of Hindi, Bengali or Arabic script that is only a word of the language sounds like a
     * Latin name: "बहुत" ("very") was every Butt in the book, "चलो" ("let us go") the product Chlorpyrifos, "যাবে" ("will
     * go") a Javed — in the HEAD of 14 Nov as well, found by trying every word the bank taught against a book of seventy-two
     * names. A word the bank taught is the language's ([ordinaryWord]: every name in it is a placeholder), so it
     * names a customer or a product only when the book holds it in that same script (the owner typed it so), or, in Arabic
     * script, when its letters meet the Latin spelling's ([bridges], read by [meetsAcrossScripts] and [acrossScripts], which
     * this does not touch). Latin words keep the search as it was.
     */
    /**
     * A short word of the language (at most four letters: "par", "pe", "ko", "kal") is no name by being the start of one (15 Nov 2026,
     * the owner's list: "WhatsApp par yaad dahani bhejo" was the product Paracetamol, "kis par" its buyers). It names somebody or
     * something only as a whole word of that name, as the book holds it. Latin letters only: the other scripts have [foldBlocked].
     */
    private fun shortOrdinaryPrefix(w: String, names: List<Name>): Boolean {
        if (w.length > 4 || scriptOf(w) != Script.LATIN || !ordinaryWord(Form(w))) return false
        return w !in prepared(names).words
    }

    private fun foldBlocked(w: String, names: List<Name>): Boolean {
        val script = scriptOf(w)
        if (script == Script.LATIN || !ordinaryWord(Form(w))) return false
        val fw = NameSearch.fold(w)
        val book = prepared(names)
        return !(book.wordsIn(script).contains(w) || book.foldsIn(script).contains(fw))
    }

    /**
     * Whether [w] is a word of one of [names] written in another script ([bridges]) — a Hindi, Bengali or Arabic-script word
     * against a name saved in Latin letters, or the other way. Never a word the language itself uses
     * ([HudhudMind.isHardWord]). One notion for the sense's name slot, the protection of a named person and the name
     * search ([acrossScripts]); before it the sense saw "আলী রজা" as two unknown words, not as a customer.
     */
    private fun meetsAcrossScripts(w: String, names: List<Name>): Boolean {
        if (HudhudMind.isHardWord(w)) return false
        val forms = saidForms(w)
        val book = prepared(names)
        if (!mayBridge(forms, book)) return false
        // Only a part of the other side's script can bridge ([bridges]): the same answer as asking every name in turn.
        return forms.any { f -> book.partsAcross(f.script).any { p -> bridges(f, p) } }
    }

    /**
     * Whoever (or whichever product) the words name. A concept word that is also a common name part ("Malik", "Store",
     * "Din", "Aman") is looked at only when the plain words name nobody; then a single such word gives way to a question
     * about the shop ("dukaan ka malik kaun hai" is not a customer called Malik), while two of them together still name
     * someone ("Malik Store ka number"). It gives way only to a question the word itself makes: a shop question that is
     * only "whose?" ([asksWhose]: "number" with nobody named) is the very question the name answers, so "Aman ka number"
     * and "Mehfooz ki limit" are the customers'. When the plain words name several ("Baz Muhammad" among the
     * Muhammads) the soft word narrows them, never widens.
     *
     * A word that calls him or befriends ([VOCATIVE], [FILLER]) is read the same way when it is also somebody's name
     * ([CALLS_AS_NAMES]): "رفیق" is "friend" in Persian and Rafiq in Urdu letters, "dost", "yar" and "jan" begin Dost
     * Muhammad, Yar Muhammad and Jan Muhammad. Never a name beside a name that is plain ("Ali bhai"), a name only when the
     * book has such a person and the rest names nobody. Only the words listed: "okay" or "dear" never look for anybody.
     */
    private fun whoIsNamed(
        said: List<String>, names: List<Name>, topics: List<Topic>, m: HudhudMind.Read, window: VoiceQuestion.Window?
    ): List<Long> {
        if (names.isEmpty()) return emptyList()
        val plain = namedIn(said, names, topics, soft = false)
        val soft = said.count { HudhudMind.isSoftWord(it) } + (if (said.any { it in CALLS_AS_NAMES }) 1 else 0)
        if (soft == 0 || plain.size == 1) return plain
        val loose = namedIn(said, names, topics, soft = true)
        if (plain.isNotEmpty()) return loose.filter { it in plain }.ifEmpty { plain }
        if (loose.isNotEmpty() && soft < 2) {
            val shop = book(m, window)
            if (shop != null && !asksWhose(shop)) return emptyList()
            // "dost aaj ka khata kaisa raha": the day's own summary is a question of the shop too (25 Nov 2026, H4: the sense used to turn it).
            val joined = said.joinToString(" ")
            if (window == VoiceQuestion.Window.TODAY && has(joined, ACCOUNT_OF_DAY) && VoiceQuestion.classify(joined) == null &&
                !m.any(HudhudMind.C.WHO, HudhudMind.C.ENTRY, HudhudMind.C.CHEQUE, HudhudMind.C.CAME)) return emptyList()
        }
        return loose
    }

    /** A shop reading that only asks "whose?": a person's fact with nobody named, which a name in the sentence answers. */
    private fun asksWhose(a: Ask): Boolean =
        a is Ask.NeedName || (a is Ask.DidYouMean && a.options.any { it is Ask.NeedName })

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
            val inTheirNames = names.filter { it.id in named }.maxOfOrNull { nm -> nameWords(nm.name).count { HudhudNote.isNoteWord(it) } } ?: 0
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
     * month and last month by the calendar — the same spans the screens they open read. The longer spans (15 Nov 2026):
     * the last 6 months counted whole back from today, this year and last year January to December by the calendar, and
     * the whole book from the first moment to tonight.
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
        fun plusYears(t: Long, years: Int) = startOf(at(t).apply { add(Calendar.YEAR, years) })
        val year = startOf(at(today).apply { set(Calendar.DAY_OF_YEAR, 1) })
        return when (window) {
            VoiceQuestion.Window.TODAY -> today to tomorrow
            VoiceQuestion.Window.YESTERDAY -> plusDays(today, -1) to today
            VoiceQuestion.Window.THIS_WEEK -> week to tomorrow
            VoiceQuestion.Window.LAST_WEEK -> plusDays(week, -7) to week
            VoiceQuestion.Window.THIS_MONTH -> month to plusMonths(today, 1)
            VoiceQuestion.Window.LAST_MONTH -> plusMonths(today, -1) to month
            // Six months back from today, counted whole: today and the days since the same date six months ago (on the
            // 15th of November, from the 16th of May), so no day is counted twice and the first day is shown on the card.
            VoiceQuestion.Window.LAST_6_MONTHS -> plusDays(startOf(at(today).apply { add(Calendar.MONTH, -6) }), 1) to tomorrow
            VoiceQuestion.Window.THIS_YEAR -> year to plusYears(year, 1)
            VoiceQuestion.Window.LAST_YEAR -> plusYears(year, -1) to year
            VoiceQuestion.Window.ALL_TIME -> 0L to tomorrow
        }
    }

    /**
     * The days of a day or a month named outright ([VoiceQuestion.Dated], 16 Nov 2026, audit T31), as [from, to), or null when
     * that day has not yet come (a figure is never said for it). A day is the whole calendar day, a month the whole calendar
     * month (the screens' own spans). What was not said is the most recent such day or month that is not in the future: "15
     * tareekh" on the 10th is last month's 15th, "November" in March is last November; a month without that day (the 31st) is
     * passed over for the one before it. "Pichle mahine ki 15 tareekh" is the 15th of last month, "pichle saal November" last
     * year's November; "parson" is the day before yesterday; "pichle jumme" the last Friday before today.
     */
    fun span(d: VoiceQuestion.Dated, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long>? {
        fun at(t: Long) = Calendar.getInstance(tz).apply { timeInMillis = t }
        fun startOf(c: Calendar): Long = c.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = startOf(at(now))
        fun dayOf(y: Int, m: Int, day: Int): Long? {
            val c = Calendar.getInstance(tz).apply { clear(); set(Calendar.YEAR, y); set(Calendar.MONTH, m - 1); set(Calendar.DAY_OF_MONTH, 1) }
            if (day > c.getActualMaximum(Calendar.DAY_OF_MONTH)) return null
            c.set(Calendar.DAY_OF_MONTH, day)
            return startOf(c)
        }
        fun wholeDay(start: Long) = start to startOf(at(start).apply { add(Calendar.DAY_OF_MONTH, 1) })
        fun wholeMonth(y: Int, m: Int): Pair<Long, Long> {
            val first = dayOf(y, m, 1)!!
            return first to startOf(at(first).apply { add(Calendar.MONTH, 1) })
        }
        val c = at(today)
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        d.daysBack?.let { back -> return wholeDay(startOf(at(today).apply { add(Calendar.DAY_OF_MONTH, -back) })) }
        d.weekday?.let { wd ->
            var t = startOf(at(today).apply { add(Calendar.DAY_OF_MONTH, -1) })
            while (at(t).get(Calendar.DAY_OF_WEEK) != wd) t = startOf(at(t).apply { add(Calendar.DAY_OF_MONTH, -1) })
            return wholeDay(t)
        }
        val day = d.day
        val month = d.month
        if (month == null) {
            day ?: return null
            // A day of this month or of last month as said, else the most recent month that has that day by today.
            d.monthsBack?.let { back ->
                val ref = at(today).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -back) }
                val t = dayOf(ref.get(Calendar.YEAR), ref.get(Calendar.MONTH) + 1, day) ?: return null
                return if (t > today) null else wholeDay(t)
            }
            for (back in 0..12) {
                val ref = at(today).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -back) }
                val t = dayOf(ref.get(Calendar.YEAR), ref.get(Calendar.MONTH) + 1, day) ?: continue
                if (t <= today) return wholeDay(t)
            }
            return null
        }
        val year = d.year ?: d.yearsBack?.let { y - it }
        if (year != null) {
            if (day == null) return if (year > y || (year == y && month > m)) null else wholeMonth(year, month)
            val t = dayOf(year, month, day) ?: return null
            return if (t > today) null else wholeDay(t)
        }
        if (day == null) return wholeMonth(if (month <= m) y else y - 1, month)
        // The most recent such day by today; the 29th of February waits up to eight years for its year.
        for (back in 0..8) {
            val t = dayOf(y - back, month, day) ?: continue
            if (t <= today) return wholeDay(t)
        }
        return null
    }

    /**
     * The moment an entry said with a day is written at (16 Nov 2026): that day at the time of day it is now, never later than the
     * day's end and never in the future; null for a day that has not yet come (the form then opens on now, as with no day said).
     */
    fun entryTime(d: VoiceQuestion.Dated, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): Long? {
        val (from, to) = span(d, now, tz) ?: return null
        val today = span(VoiceQuestion.Window.TODAY, now, tz).first
        // Never in the future: the day is today or before ([span] gives no day to come), and the form clamps to now again.
        return minOf(from + (now - today), to - 1)
    }

    private fun entryDayOf(heard: String, names: List<Name>, goods: List<Name>): VoiceQuestion.Dated? =
        VoiceQuestion.entryDay(heard) { w -> knownName(w, names) || knownName(w, goods) }

    /**
     * Two entries said in one sentence, joined by "and" in any of the nine languages ([AND_WORDS]): "Ali ko 500 diye aur Bashir se
     * 300 liye", "I gave Ali 500 and got 300 from Bashir". Each half must say an entry on its own — an amount and which way it went —
     * or it is one entry as before ("Ali aur Bashir ko 500 diye" names two people for one amount; "500 aur 300 diye" is one sentence
     * the owner checks on the form). A half that names no day takes the day the whole sentence names.
     */
    private fun twoEntries(heard: String, names: List<Name>, goods: List<Name>, language: String?): Ask.Entries? {
        val raw = heard.trim().split(SPACE_RUN)
        if (raw.size < 5) return null
        val whole = entryDayOf(heard, names, goods)
        for (i in 2 until raw.size - 2) {
            if (norm(raw[i]) !in AND_WORDS) continue
            val halves = listOf(raw.subList(0, i).joinToString(" "), raw.subList(i + 1, raw.size).joinToString(" "))
            val entries = halves.map { half ->
                val noDate = VoiceQuestion.withoutDate(half) { w -> knownName(w, names) || knownName(w, goods) }
                val e = VoiceEntry.parse(noDate, emptyList(), language)
                val amount = e.amount?.takeUnless { it in VoiceQuestion.spanFigures(half) || orderDo(half, it) }
                val given = sindhiToMe(half, e.isGiven)
                // (A half that is a promise, a denial or a debt still owed never gets here: the whole sentence says it too, and both
                // callers refuse such a sentence first — BC-113, BC-321, BC-322.)
                if (amount == null || amount <= 0.0 || given == null) null
                else VoiceEntry.nameWords(half).let { w -> Ask.Entry(amount, given, partiesOf(names, w), w, entryDayOf(half, names, goods) ?: whole) }
            }
            if (entries.any { it == null }) continue
            return Ask.Entries(entries.filterNotNull())
        }
        return null
    }

    /**
     * Sindhi's "to me" (24 Nov 2026, stage 7 A1): "Ali Raza مون کي 500 ڏنا" is Ali giving the shop 500 — money IN — but the verb
     * "ڏنا" (gave) alone read it as the shop giving. The two words "مون کي" together are Sindhi's dative (Urdu says مجھے; "I gave
     * Ali" is "مون Ali کي ڏنا", where the "I" and "کي" are apart), so a "gave" with them is the other side's. Never turns a
     * side VoiceEntry did not say, and never a "got".
     */
    private fun sindhiToMe(heard: String, given: Boolean?): Boolean? =
        if (given == true && SINDHI_TO_ME.containsMatchIn(heard)) false else given

    private val SINDHI_TO_ME = Regex("(^|\\s)\u0645\u0648\u0646\\s?\u06A9\u064A(\\s|$)")

    /** The customer an entry names: one when NameSearch is sure, else those it could be (at most five), else none. */
    private fun partiesOf(names: List<Name>, nameWords: List<String>): List<Long> {
        if (names.isEmpty() || nameWords.isEmpty()) return emptyList()
        return resolveNames(names, nameWords, nameWords)
    }

    /**
     * The names the said words name: the one [NameSearch] is confident of, else those it recognises near the best, else those
     * the letters meet across scripts ([acrossScripts]). [stemmed] are the words for the search proper (a Bengali case ending
     * off), [plain] the words for the bridge, which reads the ending itself.
     *
     * The search is confident from a fold, and a fold loses what a word of another script does not write: "امن" folds to "amn",
     * which BEGINS the folded "Aamna" and only comes near the folded "Aman", so a book with an Aman Khan and an Aamna Bibi
     * answered Aamna's number to "امن کا نمبر" (15 Nov 2026) — and the letters meet both alike. When the letters meet several
     * names as well as the confident one, the answer is a choice, never a guess. Same-script and Latin-only words never reach
     * the bridge ([mayBridge]), so nothing changes for them.
     */
    private fun resolveNames(names: List<Name>, stemmed: List<String>, plain: List<String>): List<Long> {
        NameSearch.confidentMatch(names, stemmed) { it.name }?.let { sure ->
            val across = acrossScripts(names, plain)
            return if (across.size > 1 && sure.id in across) across else listOf(sure.id)
        }
        return nearBest(names, stemmed).ifEmpty { acrossScripts(names, plain) }
    }

    /** Every name the said words recognise (not merely come near) and that scores close to the best, at most five. */
    private fun nearBest(names: List<Name>, words: List<String>): List<Long> {
        val scored = NameSearch.scoreSpoken(names, words) { it.name }.filter { it.strongHits > 0 }
        val best = scored.firstOrNull()?.score ?: return emptyList()
        if (best <= 0.0) return emptyList()
        return scored.takeWhile { it.score >= best * NEAR_BEST }.take(MAX_NAMES).map { it.item.id }
    }

    /**
     * A name saved in one script and said in another (13 Nov 2026; Hindi and Bengali 15 Nov 2026). Urdu, Sindhi, Arabic and
     * Persian leave the short vowels unwritten, so a recogniser's "بشیر" folds to "bsir" while the book's "Bashir" folds to
     * "basir", and the name search finds only a near miss. Hindi and Bengali write every vowel but the inherent "a" ("बशीर" is
     * "bsir" too, "रज़ा" "rza"): a customer saved in Roman letters and asked for in Hindi or Bengali was not found at all —
     * "बशीर अहमद का नंबर" was answered "whose?", "বশীর আহমদের হিসাব" was the whole book's, and a note's question was a jump.
     * Here a word meets a word of a name when one is in another script and the other in Latin letters. Arabic script is read on
     * its letters by [HudhudNameBridge] (the consonants one for one, a written long vowel needing a vowel of its own sort in the Latin
     * spelling, "و" and "ی" the consonants w, v and y where the Latin has one, a name that starts with a vowel written with "ا"
     * or "ع": "بشیر" is Bashir, "عمران" Imran, "مصطفی" Mustafa, "نوید" Naveed; "Bushra" is not "بشیر" nor "Asim" "وسیم").
     * Hindi and Bengali write every vowel but the inherent "a": their word, as the search folds it, meets the Latin spelling with
     * its missing vowels put in ("b[a]sir"); one vowel may stand for another ("मोहम्मद" for "Muhammad") and z is read as j and w
     * as b, which they do not tell apart. "Bushra" ("busra") is not "बशीर" ("bsir").
     * A name is met by as many of its words as the sentence has, and only the names met most are offered, at most five — a choice,
     * as [nearBest] gives one; none when more would be.
     */
    /**
     * The book's names a spoken sentence meets across scripts (17 Nov 2026, audit L037): the Khata microphone's own search
     * (NameSearch) folds both sides and missed "بشیر" or "बशीर" for a customer saved as "Bashir". The same words [namedIn] gives the
     * bridge — no little word, question word, word of a concept or calling, no short ordinary word ([shortOrdinaryPrefix]) — and the
     * same bridge ([acrossScripts]). One id is the man; several are a choice, never a guess; none when nothing was met.
     */
    fun acrossScriptsOf(heard: String, names: List<Name>): List<Long> {
        if (names.isEmpty()) return emptyList()
        val n = asRead(names)
        val left = words(heard).filter { w ->
            w.length >= 3 && w.any { it.isLetter() } && w !in STOP_SET && w !in INTENT_SET && !HudhudMind.isHardWord(w) &&
                !HudhudMind.isSoftWord(w) && w !in QUIET_SET && w !in VOCATIVE_SET && !shortOrdinaryPrefix(w, n)
        }
        return if (left.isEmpty()) emptyList() else acrossScripts(n, left)
    }

    private fun acrossScripts(names: List<Name>, words: List<String>): List<Long> {
        // A Bengali word is read both ways, with its case ending and without ("বশীর" is Bashir; "আলীর" is Ali's): it counts once.
        val said = words.map { saidForms(it) }
        val book = prepared(names)
        if (!mayBridge(said.flatten(), book)) return emptyList()
        val met = names.mapIndexed { i, n ->
            val parts = book.forms[i]
            n to said.count { forms -> bridgedLatin(forms, parts) != null }
        }.filter { it.second > 0 }
        val best = met.maxOfOrNull { it.second } ?: return emptyList()
        // One product saved under several names ("Urea" and its other name "یوریا", one id) is met once (25 Nov 2026, BC-352): the
        // glides let the Latin word meet the Urdu name too, and [1, 1] was a choice between the product and itself.
        val nearest = met.filter { it.second == best }.map { it.first.id }.distinct()
        return if (nearest.size in 1..MAX_NAMES) nearest else emptyList()
    }

    /**
     * A said word as the search reads it, with its script; a Bengali word is read both ways, with its case ending and without, and an
     * Arabic word with the article joined to it both with and without it ([withoutArticle]).
     */
    private fun saidForms(w: String): List<Form> =
        listOfNotNull(w, bengaliStem(w), withoutArticle(w)).distinct().map { Form(it) }

    /**
     * An Arabic-script word with the article "ال" joined to it, without it (26 Nov 2026, BC-355): "كم سعر اليوريا" (the price of the
     * urea) named no product saved as "Urea", while "كم سعر يوريا" did — the article's "ال" read as letters of the name. The sense was
     * given the same word without its article on 13 Nov (lesson 111); the name bridge had not been. Only with three letters left at
     * least, none of the letters only Urdu, Sindhi or Persian write ("الٹیاں" is an Urdu word, not an article), and a stem that is no
     * word of the language — not one the bank taught ([HudhudSense.knowsWord]: "ربح", "قرض") nor of a concept
     * ([HudhudMind.isHardWord]): the article never makes "ربح" a name. (A whole word the bank taught has a taught stem: no taught
     * word with the article has an untaught stem, measured 26 Nov 2026, so the whole word is not asked again.)
     */
    internal fun withoutArticle(w: String): String? {
        if (!w.startsWith(ARTICLE) || w.length < ARTICLE.length + HudhudNameBridge.MIN_LETTERS) return null
        if (w.any { it in HudhudMind.NOT_ARABIC_LETTERS }) return null
        val stem = w.substring(ARTICLE.length)
        if (HudhudSense.knowsWord(stem) || HudhudMind.isHardWord(stem)) return null
        return stem
    }

    private const val ARTICLE = "ال"

    /**
     * The words of a name as the search reads them, each with its script ("Ali Raza" is "ali" and "raza"). Made once per spelling
     * ([NAME_FORMS]): a Form keeps its letters once they are read, so the bridge does not read the whole book's letters again for every
     * word of every sentence (26 Nov 2026, BC-353).
     */
    private fun partsOf(name: String): List<Form> = NAME_FORMS.get(name) { nm ->
        nm.split(' ', '-', '.').filter { it.isNotBlank() }.map { Form(it) }.filter { it.folded.isNotEmpty() }
    }

    /** [words] of one of the book's names, made once per spelling (26 Nov 2026, BC-353): a name is read the same way every time. */
    private fun nameWords(name: String): List<String> = NAME_WORDS.get(name, ::words)

    /** Each of [nameWords] as the search folds it (an empty fold kept); [Prepared] keeps them, once per book. */
    private fun nameFolds(name: String): List<String> = nameWords(name).map { NameSearch.fold(it) }

    /**
     * What the readers make of a name of the book, kept by the name's own letters (26 Nov 2026, BC-353). Before them a question
     * against a shop of 1,000 names normalised, folded and cut every name again for every word asked: 4.7 times the time of a book
     * of nine on the computer, 1.3 s at the middle on CI's emulator. Keyed by the spelling, never by an id or a list, so a renamed
     * customer is another key and no answer can be stale; bounded ([TextMemo.NAMES_CAP]) so a deleted name leaves in time.
     */
    private val NAME_FORMS = TextMemo<List<Form>>(TextMemo.NAMES_CAP)
    private val NAME_WORDS = TextMemo<List<String>>(TextMemo.NAMES_CAP)

    /** How many names the memos hold now (the speed test reports it; nothing reads it to decide). */
    internal fun nameMemoSizes(): List<Int> = listOf(NAME_FORMS.size, NAME_WORDS.size)

    /** Whether anything can bridge at all: only when a said word or a name is not written in Latin letters. */
    private fun mayBridge(said: List<Form>, book: Prepared): Boolean =
        said.any { it.script != Script.LATIN } || book.nonLatin

    /**
     * A list of the book's names as the readers ask it, made once (26 Nov 2026, BC-353): each name's [partsOf], every word and fold
     * of every name by script, every part the search compares ([NameSearch.spokenParts]) in order for its prefix question, and
     * whether any name is not in Latin letters. [knownName] is asked for every word of a sentence by a dozen readers, each time
     * over the whole book; with this it looks a word up instead. Nothing in it but what the names' own letters make, so it is
     * the same answer as walking the list.
     */
    private class Prepared(val spelled: Array<String>, val forms: Array<List<Form>>) {
        val nonLatin: Boolean = spelled.any { scriptOf(it) != Script.LATIN }
        /** Every [nameWords] of every name ([shortOrdinaryPrefix]). */
        val words: Set<String> = spelled.flatMapTo(HashSet()) { nameWords(it) }
        private val byScript = spelled.groupBy { scriptOf(it) }
        private val wordsBy = byScript.mapValues { (_, l) -> l.flatMapTo(HashSet()) { nameWords(it) } }
        private val foldsBy = byScript.mapValues { (_, l) -> l.flatMapTo(HashSet()) { nameFolds(it) } }
        /** [foldBlocked]: the words, and their folds, of the names written in [script]. */
        fun wordsIn(script: Script): Set<String> = wordsBy[script].orEmpty()
        fun foldsIn(script: Script): Set<String> = foldsBy[script].orEmpty()
        private val latinParts: List<Form> = forms.flatMap { l -> l.filter { it.script == Script.LATIN } }
        private val otherParts: List<Form> = forms.flatMap { l -> l.filter { it.script != Script.LATIN } }
        /** The parts a word written in [script] may bridge to: Latin ones for another script, the others for Latin. */
        fun partsAcross(script: Script): List<Form> = if (script == Script.LATIN) otherParts else latinParts
        private val spoken: Set<String> = spelled.flatMapTo(HashSet()) { NameSearch.spokenParts(it) }
        private val sortedSpoken: Array<String> = spoken.toTypedArray().also { it.sort() }
        /** `NameSearch.hasStrongHit(names, listOf(w))` by lookup: the same word, or a part it begins on enough letters. */
        fun strong(w: String): Boolean {
            val s = NameSearch.fold(w)
            if (s.isEmpty()) return false
            if (s in spoken) return true
            if (!NameSearch.strongPrefixLength(s)) return false
            // The first part at or after [s] in order is the one that begins with it, if any does.
            var at = java.util.Arrays.binarySearch(sortedSpoken, s)
            if (at < 0) at = -at - 1
            return at < sortedSpoken.size && NameSearch.strongWord(sortedSpoken[at], s)
        }

        fun same(names: List<Name>): Boolean {
            if (names.size != spelled.size) return false
            for (i in spelled.indices) if (names[i].name != spelled[i]) return false
            return true
        }
    }

    /** The last few books asked ([Prepared]): a sentence asks the customers, the goods and the staff again and again. */
    private val PREPARED = arrayOfNulls<Prepared>(4)
    private var preparedNext = 0

    private fun prepared(names: List<Name>): Prepared {
        synchronized(PREPARED) { for (p in PREPARED) if (p != null && p.same(names)) return p }
        val spelled = Array(names.size) { names[it].name }
        val made = Prepared(spelled, Array(spelled.size) { partsOf(spelled[it]) })
        synchronized(PREPARED) {
            PREPARED[preparedNext] = made
            preparedNext = (preparedNext + 1) % PREPARED.size
        }
        return made
    }

    /**
     * The Latin spelling (as folded) that a said word meets in one of a name's [parts] written in another script, or null when
     * it meets none. One notion of "this word is a word of that name across scripts" ([acrossScripts], [knownName],
     * [namedSurely], [namedAtAll]); the Latin side is what says how distinctive the word is.
     */
    private fun bridgedLatin(forms: List<Form>, parts: List<Form>): String? {
        for (w in forms) for (p in parts) {
            if (bridges(w, p)) return if (w.script == Script.LATIN) w.folded else p.folded
        }
        return null
    }

    /**
     * One word as the search reads it: as written ([raw]), folded to the search's one alphabet ([folded]) and the script it is
     * written in. Arabic script and Latin letters are met on their own letters ([HudhudNameBridge]; the fold has lost what it needs),
     * which are made once, and only when a word of another script is put beside it.
     */
    private class Form(val raw: String) {
        val folded: String = NameSearch.fold(raw)
        val script: Script = scriptOf(raw)
        /** As [VoiceQuestion.normalise] writes it: the spelling the sense's vocabulary and [CALLS_AS_NAMES] are kept in. */
        val norm: String by lazy(LazyThreadSafetyMode.PUBLICATION) { VoiceQuestion.normalise(raw) }
        /** Whether the word is "why" in some language ([HudhudWhyAsk.isWhyWord]), read once: [bridges] asks it of every pair. */
        val why: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) { HudhudWhyAsk.isWhyWord(norm) }
        val letters: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
            if (script == Script.ARABIC) HudhudNameBridge.arabicLetters(raw) else HudhudNameBridge.latinLetters(raw)
        }
        /** A Latin spelling's "ea" read as two sounds ("Urea" folded "uria", not "uri"); null when it has none ([indicMeetsLatin]). */
        val eaApart: String? by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val low = raw.lowercase()
            if (script != Script.LATIN || "ea" !in low) null else NameSearch.fold(low.replace("ea", "ia")).takeIf { it != folded }
        }
    }

    /** The scripts a name is written in that the search folds to Latin letters without being able to put its vowels back. */
    private enum class Script { LATIN, ARABIC, INDIC }

    private fun scriptOf(w: String): Script = when {
        w.any { it in '\u0600'..'\u06FF' } -> Script.ARABIC
        w.any { it in '\u0900'..'\u09FF' } -> Script.INDIC
        else -> Script.LATIN
    }

    /** Whether two words of different scripts, one of them Latin, are one name. */
    private fun bridges(a: Form, b: Form): Boolean = when {
        // Only a Latin word and one of another script can bridge; asked first, as it is the cheap question and most pairs of a book
        // end there (26 Nov 2026, BC-353). It changes no answer: every branch below needs such a pair.
        (a.script == Script.LATIN) == (b.script == Script.LATIN) -> false
        // "Why" in any script is never a name (16 Nov 2026, BC-110: Bengali "কেন" was the customer Naeem Khan, so "এই মাসে বিক্রি কম
        // কেন" — why are sales down this month — and a bare "কেন" answered with his balance).
        a.why || b.why -> false
        a.script == Script.ARABIC && b.script == Script.LATIN -> arabicMeets(a, b)
        a.script == Script.LATIN && b.script == Script.ARABIC -> arabicMeets(b, a)
        a.script == Script.INDIC && b.script == Script.LATIN -> !ordinaryWord(a) && indicMeetsLatin(a, b)
        a.script == Script.LATIN && b.script == Script.INDIC -> !ordinaryWord(b) && indicMeetsLatin(b, a)
        else -> false
    }

    /**
     * An Arabic-script word against a Latin one, on their letters ([HudhudNameBridge]), with the "you" glide read too ("یوریا"
     * for Urea, 25 Nov 2026, BC-350: a product saved in Latin letters was never met by its Urdu, Sindhi, Persian or Arabic
     * spelling, so "یوریا کا ریٹ" named no product). The language's own "یو" words are kept away by the glide's own rule (a
     * loanword writes its vowels, a verb does not), not by the bank's vocabulary: the bank taught none of them.
     */
    private fun arabicMeets(arabic: Form, latin: Form): Boolean =
        HudhudNameBridge.meetsLetters(arabic.letters, latin.letters) || HudhudNameBridge.meetsYouGlide(arabic.letters, latin.letters)

    /**
     * Whether a Hindi or Bengali [word] is a word of the language that the bank taught, not somebody's name (15 Nov 2026). The
     * consonants of a short word fall by chance on a Latin name's: Bengali "উড়ে" (flying) is "ure" and "Urea" has them, Hindi
     * "बात" (talk) is "bat" and "Butt" has them, and the loose vowels [indicMeets] allows made the product and the customer
     * the question was about (the held-out set 1,066 → 1,065, and "बात करना आता है" answered Butt's details). Every name in
     * the bank is a placeholder, so a word it taught ([HudhudSense.knowsWord]) is never one; of sixty Hindi and Bengali names
     * tried, only the words that call or befriend ("दोस्त", "यार", "ভাই") were taught, and those [CALLS_AS_NAMES] keeps as
     * names, as the soft pass reads them. Not used for Arabic script, whose letters [HudhudNameBridge] meets one for one and
     * whose "علی" is the bank's "on" and the name Ali alike.
     */
    private fun ordinaryWord(word: Form): Boolean =
        word.norm !in CALLS_AS_NAMES && (HudhudSense.knowsWord(word.norm) || HudhudWhyAsk.isWhyWord(word.norm))

    /**
     * [bare] is a Hindi or Bengali word as the search folds it — every vowel written but the inherent "a" — and [latin] a name
     * as the search folds it. They are one name when the Latin spelling is the other with its missing vowels put in, a vowel
     * standing for another where the scripts write both ("mohmd" and "muhamad"), z and j one letter (the scripts do not tell
     * them apart) and w the letter these scripts write it with — Hindi's v ("b" once folded), Bengali's "ওয়" ("oy"). A
     * consonant must be the same: "Bushra" ("busra") is not "बशीर" ("bsir"), however its vowels fall. The bare word is already
     * folded from Hindi or Bengali, so it never holds a z or a w: only the Latin name is read so.
     */
    private fun indicMeets(bare: String, latin: String): Boolean {
        if (bare.length < 3) return false
        val l = latin.replace('z', 'j')
        return indicGlides(bare, l).any { b ->
            b.length >= 3 && (indicRun(b, l.replace('w', 'b')) || ('w' in l && indicRun(b, l.replace("w", "oy"))))
        }
    }

    /**
     * A Hindi or Bengali word against a Latin one, as the search folds both ([indicMeets]). The fold reads a Latin "ea" as one long
     * "i" ("Urea" is "uri"), but it is as often two sounds, "e-a", which Hindi and Bengali write whole ("यूरिया", "ইউরিয়া"): a Latin
     * spelling with an "ea" is read that way too ([Form.eaApart], 25 Nov 2026, BC-351).
     */
    private fun indicMeetsLatin(indic: Form, latin: Form): Boolean =
        indicMeets(indic.folded, latin.folded) || latin.eaApart?.let { indicMeets(indic.folded, it) } == true

    /**
     * [bare] and the ways it may be read with the "y" glides Hindi and Bengali write and a Latin spelling does not (25 Nov 2026,
     * BC-351; the Arabic script's own is [HudhudNameBridge.meetsYouGlide]): a first "u" said "you" is "यू" ("yu") or "ইউ" ("iu") —
     * "यूरिया" and "ইউরিয়া" are Urea, read only against a Latin spelling that begins "u" or "eu" — Bengali's "ইউ" is the "yu" of a
     * Latin "y" ("ইউসুফ" is Yusuf), and a "y" between two vowels is the glide of "-ia" ("शाज़िया" is Shazia, "-iya" for "-ia"
     * and "-ea"). Each reading keeps every consonant: only a written "y" can go, never a letter the Latin spelling needs.
     */
    private fun indicGlides(bare: String, latin: String): List<String> {
        val starts = ArrayList<String>(3).apply { add(bare) }
        if ((bare.startsWith("yu") || bare.startsWith("iu")) && (latin.startsWith("u") || latin.startsWith("eu"))) starts += bare.drop(1)
        if (bare.startsWith("iu") && latin.startsWith("y")) starts += "y" + bare.drop(1)
        val all = ArrayList<String>(starts)
        for (b in starts) {
            val off = withoutGlideY(b)
            if (off != b) all += off
        }
        return all
    }

    /** [bare] with every "y" that stands between two vowels taken out ("sajiya" is "sajia"); the word itself when there is none. */
    private fun withoutGlideY(bare: String): String {
        if ('y' !in bare) return bare
        val sb = StringBuilder(bare.length)
        for (i in bare.indices) {
            val c = bare[i]
            if (c == 'y' && i > 0 && i < bare.length - 1 && bare[i - 1] in "aeiou" && bare[i + 1] in "aeiou") continue
            sb.append(c)
        }
        return sb.toString()
    }

    private fun indicRun(bare: String, latin: String): Boolean {
        var i = 0
        for (c in latin) {
            when {
                i < bare.length && c == bare[i] -> i++
                c in "aeiou" -> if (i < bare.length && bare[i] in "aeiou") i++
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

    /** "How many days of stock": a phrase that is a shelf's cover by itself. */
    private val COVER_PHRASES = listOf(
        "days left", "days of stock", "days worth", "days of supply", "how many days will", "how long will", "how long does", "how long would",
        "kitne din ka", "kitne din ke liye", "kitne din baqi", "kitne din bache", "kitne din bachay", "kitne din tak", "din ka stock",
        "din ka maal", "din ka saman", "kitne dino",
        "کتنے دن کا", "کتنے دن کے لیے", "کتنے دن تک", "کتنے دن باقی", "کتنے دن بچے", "دن کا اسٹاک", "دن کا مال", "دن کا سامان",
        "ڪيترن ڏينهن جو", "ڪيترا ڏينهن هلندو", "ڪيترا ڏينهن ڪافي", "ڏينهن جو اسٽاڪ", "ڏينهن جو مال",
        "كم يوم يكفي", "كم يوما يكفي", "كم يوم سيكفي", "كم يوم سيدوم", "كم يوم يدوم", "كم يوما سيكفي", "كم يوم سيستمر", "أيام المخزون", "ايام المخزون", "كم يوما تكفي", "كم يوم تكفي",
        "چند روز کفایت", "برای چند روز", "چند روز دوام", "چند روز می‌ماند", "چند روز میماند", "روز موجودی", "چند روز کافی", "چند روز دیگر",
        "कितने दिन का", "कितने दिन के लिए", "कितने दिन तक", "कितने दिन बचे", "कितने दिन बाकी", "दिन का स्टॉक", "दिन का माल", "दिन का सामान",
        "কত দিনের", "কতদিনের", "কত দিন চলবে", "কতদিন চলবে", "কত দিন চলে", "কতদিন চলে", "দিনের স্টক", "দিনের মাল", "কতদিন টিকবে", "কতদিন যথেষ্ট",
        "cukup untuk berapa hari", "cukup berapa hari", "tahan berapa hari", "bertahan berapa hari", "untuk berapa hari", "berapa hari lagi", "stok untuk berapa hari", "stok berapa hari"
    )

    /** A word for lasting, running out or being enough; it asks for the shelf's cover only with a word for time ([FOR_TIME]). */
    private val LASTING = listOf(
        "lasts", "lasting", "last for", "will last", "would last", "to last", "enough", "suffice", "suffices", "sufficient", "cover", "covers",
        "chalega", "chalegi", "chalay", "chalenge", "chal jayegi", "chal jayega", "chal jaye", "chal jayenge", "chale", "chalta", "chalti", "chalte", "chlega", "chlegi", "tikega", "tikegi", "kafi", "kaafi", "kifayat",
        "چلے", "چلتا", "چلتی", "چلیں", "ٹکے", "کافی", "کفایت",
        "هلندو", "هلندي", "هلندا", "ڪافي", "ٽڪندو",
        "يكفي", "تكفي", "سيكفي", "ستكفي", "كافي", "يدوم", "سيدوم", "يستمر", "سيستمر", "تكفينا", "يكفينا",
        "کفایت", "دوام", "کافی", "می‌ماند", "میماند", "کفاف",
        "चलेगा", "चलेगी", "चलेंगे", "चलता", "चलती", "टिकेगा", "टिकेगी", "काफी", "काफ़ी", "कितना चलेगा",
        "চলবে", "চলে", "টিকবে", "যথেষ্ট", "কুলাবে",
        "tahan", "bertahan", "cukup", "mencukupi", "awet"
    )

    /** Words for running out or finishing; they ask for the cover only with a "when" ([WHEN_WORDS]): "urea khatam ho gayi" is the stock. */
    private val RUNS_OUT = listOf(
        "run out", "runs out", "finish", "finishes", "sell out",
        "khatam", "khalas", "khtm", "khatm",
        "ختم",
        "ينفد", "ينفذ", "ينتهي", "تنتهي", "سينفد", "ستنفد", "سينتهي", "ستنتهي",
        "تمام", "تموم",
        "खत्म", "ख़त्म", "खतम",
        "শেষ", "ফুরাবে", "ফুরিয়ে",
        "habis", "akan habis", "kehabisan"
    )

    /** The word for time that makes a lasting word a question about days. */
    private val FOR_TIME = listOf(
        "days", "day", "week", "weeks", "month", "months", "how long", "how soon", "until when", "till when", "by when",
        "din", "dino", "dinon", "hafta", "hafte", "haftay", "haftey", "mahina", "mahine", "mahinay", "kab tak", "kitna waqt",
        "دن", "دنوں", "ہفتہ", "ہفتے", "مہینہ", "مہینے", "کب تک", "کتنا وقت",
        "ڏينهن", "هفتو", "هفتا", "مهينو", "مهينا", "ڪڏهن تائين",
        "يوم", "ايام", "أيام", "يوما", "اسبوع", "أسبوع", "اسابيع", "شهر", "الى متى", "إلى متى", "كم من الوقت",
        "روز", "هفته", "ماه", "تا کی", "چه مدت",
        "दिन", "दिनों", "हफ्ते", "हफ़्ते", "हफ्ता", "महीने", "महीना", "कब तक", "कितना समय",
        "দিন", "দিনের", "সপ্তাহ", "মাস", "কতদিন", "কত দিন", "কত সময়",
        "hari", "minggu", "bulan", "berapa lama", "sampai kapan"
    )

    /** "When", for a run-out word. */
    private val WHEN_WORDS = listOf(
        "when", "how long", "how soon", "until when", "till when", "by when",
        "kab", "kab tak", "kitne din", "kitne dino", "kitne hafte", "kitne mahine",
        "کب", "کب تک", "کتنے دن",
        "ڪڏهن", "ڪڏهن تائين", "ڪيترا ڏينهن",
        "متى", "إلى متى", "الى متى", "كم يوم", "كم يوما", "كم من الوقت",
        "چه وقت", "چند روز", "تا کی", "چه مدت",
        "कब", "कब तक", "कितने दिन",
        "কবে", "কতদিন", "কত দিন",
        "kapan", "sampai kapan", "berapa hari", "berapa lama"
    )

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
        "galla", "gala", "galle", "gallay", "galley", "galy", "drawer", "in the till", "the till", "cash in hand", "naqdi", "naqd",
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
    /** "Earned" in Roman Urdu, for the day's takings ("aaj dukaan ne kitna kamaya": the owner's own lists read it as the day's sales; a month's "kamaya" is the profit, read first by the rules). */
    private val EARNED = listOf("kamaya", "kamai", "kama liya")

    private val OWED_LABELS = setOf("to_get", "to_give")

    /** Cash and "left" in Roman Urdu, Urdu, Hindi: "Cash kitna bacha hai" is the drawer, not the month's profit (H4: the 15-seed sense read it as profit at 0.56). */
    private val CASH_WORD = listOf("cash", "کیش", "कैश")
    private val LEFT_WORD = listOf("bacha", "bachi", "bache", "bachta", "بچا", "بچی", "بچے", "बचा", "बची", "बचे")

    /** "Selling" as a noun or an infinitive ("aaj ka bechna kitna hua"), the day's sales. */
    private val SELLING_WORD = listOf("bechna", "bechnay", "bechne", "بیچنا", "بیچنے", "बेचना", "बेचने")

    /** "Whom did I pay" (to somebody, past): money going out, the day's doings, not who will pay. */
    private val PAID_TO = listOf("kis ko payment ki", "kisko payment ki", "kis ko payment kar di", "kis ko payment di", "کس کو پیمنٹ کی", "کس کو ادائیگی کی", "किसको पेमेंट की", "किस को पेमेंट की")

    /** "Happened", "came to be": the past of a figure of the day ("aaj gala kitna hua"). */
    private val TOOK_PLACE = listOf("hua", "hui", "hoa", "ہوا", "ہوئی", "हुआ", "हुई")

    /** Money in and out said together: the cashbook ("امروز ورودی و خروجی نقد چقدر بود"). */
    private val IN_AND_OUT = listOf("ورودی و خروجی", "ورودی خروجی", "in and out", "in out", "in aur out", "aamad o raft", "آمد و رفت", "اندر ۽ ٻاهر")

    /** "…or not" closing a yes/no about the day ("আজ কিছু বিক্রি হলো কি না"). */
    private val OR_NOT = listOf("কি না", "কিনা", "ya nahi", "ya nahin", "یا نہیں", "या नहीं", "or not", "atau tidak", "ya tidak", "أم لا", "يا لا", "یا نه")

    private val WILL_PAY = listOf(
        "dega", "degi", "denge", "dein ge", "dain ge", "de ga", "de gi", "ada karega", "ada karegi", "ada karenge",
        "lautayega", "chukayega",
        "دے گا", "دیگا", "دے گی", "دیگی", "دیں گے", "دینگے", "ادا کرے گا", "ادا کریگا", "ادا کرے گی", "ادا کریں گے",
        "لوٹائے گا", "واپس کرے گا", "देगा", "देगी", "देंगे", "चुकाएगा", "चुकाएगी", "लौटाएगा", "अदा करेगा",
        "দেবে", "দিবে", "দেবেন", "দিবেন", "শোধ করবে", "পরিশোধ করবে", "ڏيندو", "ڏيندي", "ڏيندا", "ادا ڪندو", "ادا ڪندي",
        "يدفع", "سيدفع", "يسدد", "سيسدد", "أطالبه", "اطالبه", "أطالبهم", "اطالبهم", "می‌دهد", "میدهد", "میده", "می‌پردازد", "میپردازد", "پرداخت می‌کند"
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

    /** The four courtesy labels: a hello is not a question, whatever follows it (15 Nov 2026). */
    private val COURTESY = setOf("salaam", "hello", "thanks", "bye")

    /** Every word the courtesy phrases are made of ("shukriya", "good", "morning", "bye", "phir"...). */
    private val COURTESY_WORDS: Set<String> by lazy { (SALAAM + SALAAM_TAIL + HELLO + THANKS + BYE).flatMap { words(it) }.toSet() }

    /** Concepts of the pronouns and the little verbs: they point at somebody or do something, they ask nothing of the book. */
    private val LITTLE_C = setOf(
        HudhudMind.C.YOU, HudhudMind.C.OWN, HudhudMind.C.ME, HudhudMind.C.THIS, HudhudMind.C.HIS, HudhudMind.C.AND, HudhudMind.C.HOW_IS, HudhudMind.C.HEALTH, HudhudMind.C.DAY, HudhudMind.C.TIME, HudhudMind.C.TELL, HudhudMind.C.DO, HudhudMind.C.ABLE, HudhudMind.C.KNOW
    )

    /** The one-word thank-yous, goodbyes and greetings ("shukriya", "thanks", "bye", "salam", "hello", "شڪريو"): courtesy and nothing else. */
    private val COURTESY_TOKENS: Set<String> by lazy { (THANKS + BYE + SALAAM + HELLO).filter { ' ' !in it }.map { norm(it) }.toSet() }

    /**
     * What the sense is shown (15 Nov 2026): a sentence that asks the book without its one-word thank-yous. The sense learnt
     * "شڪريو" and "thanks" beside courtesy, and in front of a question it read the question as something else (privacy,
     * staff); the rules never see the difference, so the sense must not either. A courtesy-only sentence is shown whole.
     */
    internal fun senseHeardForTest(heard: String, names: List<Name>, goods: List<Name>): String = senseHeard(heard, names, goods)
    internal fun asksBookForTest(w: String, names: List<Name>, goods: List<Name>): Boolean = asksBook(w, names, goods)

    private fun senseHeard(heard: String, names: List<Name>, goods: List<Name>): String {
        val said = words(heard)
        if (said.none { it in COURTESY_TOKENS } || courtesyOnly(said, names, goods)) return heard
        val rest = said.filter { it !in COURTESY_TOKENS }
        return if (rest.isEmpty()) heard else rest.joinToString(" ")
    }

    /** Words that ask the book and have no concept ("how", "total", "show me"): a short closed list, in the nine languages. */
    private val ASK_EXTRA = setOf(
        "how", "total", "kul", "show", "dikhao", "dikhaen", "dikhaiye", "tunjukkan",
        "رقم", "المبلغ", "تومان", "اعرض", "দেখাও", "মোট", "कुल", "दिखाओ", "rupiah"
    )

    /**
     * Whether [w] asks the book (15 Nov 2026): a customer, supplier or product of this book, a word that means something of the
     * book or a question word, a figure word (profit, owe, sales — never the span's own "today"), or one of [ASK_EXTRA].
     * Courtesy words, callings, friendly fillers and his own name never ask; a word nobody knows is little ("thanks so much").
     */
    private fun asksBook(w: String, names: List<Name>, goods: List<Name>): Boolean {
        if (w in COURTESY_WORDS || w in VOCATIVE_SET || w in FILLER_SET || w in HIS_OWN_NAME) return false
        if (knownName(w, names) || knownName(w, goods)) return true
        if (HudhudMind.meaningOf(w).any { it !in LITTLE_C }) return true
        if (w in ASK_EXTRA) return true
        return (VoiceQuestion.keywordsIn(w) - VoiceQuestion.spanWordsIn(w)).isNotEmpty()
    }

    /** A sentence of courtesy and little words, nothing that asks the book — the only kind a greeting or a thank-you may answer. */
    private fun courtesyOnly(said: List<String>, names: List<Name>, goods: List<Name>): Boolean =
        said.none { asksBook(it, names, goods) }

    /**
     * Small talk, or "what can you tell me" (13 Nov 2026). [whole]: only when every word of the sentence is the
     * talk's own, a question's little word, a greeting or a friendly filler ("yaar", "bhai") — then it is answered
     * before any name is looked for. Else (the loose reading, last of all) when the talk is said anywhere; "I'm fine"
     * and a lone "help" are taken only whole, they mean too much inside a longer sentence.
     */
    private fun talk(said: List<String>, whole: Boolean, names: List<Name> = emptyList(), goods: List<Name> = emptyList()): Ask? {
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
        // Thanks and goodbye are courtesy only when nothing else in the sentence asks the book: "thanks what is my balance"
        // is a question with a thank-you in front (15 Nov 2026).
        if (found.first is Ask.Chat && (found.first as Ask.Chat).talk.let { it == Talk.THANKS || it == Talk.BYE } &&
            !courtesyOnly(said, names, goods)
        ) return null
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
        "bohat achha", "bohot acha", "bohot achha", "bohot khoob", "bahut acha", "bahut achha", "acha jawab", "achha jawab", "accha jawab", "kamal kar diya", "kamaal kar diya", "kamal hai", "kamaal hai", "mashallah", "masha allah",
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

    /** The core of a salaam's second half, "alaikum" in the ways it is written ("wa" and the blessings after it are [SALAAM_TAIL]'s only). */
    private val SALAAM_CORE_SET: Set<String> by lazy {
        listOf(
            "alaikum", "alaykum", "alaikom", "alekum", "aleikum", "alikum", "alykum", "alaikam",
            "علیکم", "عليكم", "عليڪم", "अलैकुम", "আলাইকুম"
        ).flatMap { words(it) }.toSet()
    }

    /** What may stand between a salaam's first word and "alaikum": "Aslam o alaikum", "اسلام و علیکم". */
    private val SALAAM_JOINERS: Set<String> by lazy { listOf("o", "u", "wa", "و", "ओ", "ও").flatMap { words(it) }.toSet() }

    private val WORD_RUN = Regex("[\\p{L}\\p{M}\\p{N}]+")

    /**
     * (15 Nov 2026) A salaam written without its article — "Aslam o alaikum", "اسلام علیکم", "असलाम अलैकुम" — opens with a word
     * that is also a man's name. With an Aslam or an Islam in the book it opened that man's account instead of greeting back:
     * the owner's very first words to Hudhud. The word just before "alaikum" (one "o" or "wa" between) belongs to the
     * greeting; it is said as "assalam", the greeting everything else already knows. Any other sentence comes back as it
     * was, and so does everything of this one but that word: its spelling, its "?" and the rest.
     */
    internal fun salaamHeadRead(heard: String): String {
        val runs = WORD_RUN.findAll(heard).toList()
        val ws = runs.map { norm(it.value) }
        val core = ws.indexOfFirst { it in SALAAM_CORE_SET }
        if (core < 1) return heard
        var head = core - 1
        if (ws[head] in SALAAM_JOINERS && head >= 1) head--
        if (ws[head].isEmpty() || ws[head] in SALAAM_JOINERS || ws[head] in GREETING_SET) return heard
        return heard.replaceRange(runs[head].range, "assalam")
    }

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
        // a common name part that is also a concept ("Malik", "Store"), and a word that calls him but is also a name ("dost",
        // "رفیق": [CALLS_AS_NAMES]), only when [soft].
        val left = said.filter { w ->
            w.length >= 3 && w.any { it.isLetter() } && w !in STOP_SET && w !in INTENT_SET &&
                w !in asked && w !in features && !HudhudMind.isHardWord(w) && (soft || !HudhudMind.isSoftWord(w)) &&
                ((w !in QUIET_SET && w !in VOCATIVE_SET) || (soft && w in CALLS_AS_NAMES)) && !shortOrdinaryPrefix(w, names)
        }
        if (left.isEmpty()) return emptyList()
        // A Bengali case ending comes off for the search proper ("আলীর" meets "আলী"); the bridge across scripts is given the
        // plain words as well, because the ending's letter can be the name's own ("বশীর" is Bashir, not "বশী" and an ending).
        val stemmed = left.filter { !foldBlocked(it, names) }.map { bengaliStem(it) }
        return resolveNames(names, stemmed, left)
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

    /**
     * Words that call him or befriend ([VOCATIVE], [FILLER]) and are also a part of somebody's name in the countries the
     * app is made for: Dost Muhammad, Yar Muhammad, Jan Muhammad, Sahib Khan, Hazoor Bakhsh, Pyare Lal, and Rafiq, which
     * Persian writes "رفیق" and means "friend" (15 Nov 2026: "رفیق کا نمبر" asked "whose?" in a book with a Rafiq). They
     * name somebody only when the book has such a person and the plain words name nobody ([whoIsNamed]). A word is added
     * here only for a name it really is a part of, with a test.
     */
    private val CALLS_AS_NAMES: Set<String> by lazy {
        listOf(
            "dost", "yar", "yaar", "jan", "jaan", "sahib", "sahab", "hazoor", "huzoor", "pyare", "pyaare", "ustad", "ustaad",
            "دوست", "یار", "جان", "صاحب", "رفیق", "حضور", "پیارے", "استاد",
            "दोस्त", "यार", "जान", "साहब", "साहिब", "हुज़ूर", "प्यारे", "उस्ताद",
            "দোস্ত", "ইয়ার", "জান", "সাহেব", "হুজুর",
            "رفيق", "صديق", "يار", "دوست"
        ).flatMap { words(it) }.toSet()
    }

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
            "আজকের", "গতকাল", "সপ্তাহ", "মাস", "গত", "এই", "اڄ", "ڪالهه", "هفتو", "هفتي", "مهينو", "مهيني", "هن", "اليوم", "أمس",
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

    /** What a span said with nothing else may also ask, to carry the last question over it: "how much last year?". */
    private val SPAN_ASKS = setOf(HudhudMind.C.HOW_MANY)

    // ------------------------------------------------------------------------------------------------ help pages

    /**
     * The help page the question is about, or null. Each said word is worth what it narrows across the pages
     * (as NameSearch weighs a name's words): a word on one page counts for a lot, a word on every page for almost
     * nothing; a word in a page's title counts twice. A word of four letters or more meets its plural or stem
     * ("cheque", "cheques"). A page that scores less than one word found on two pages is not chosen.
     */
    // ------------------------------------------------------------------- everyday asks, review O (15 Nov 2026)

    /**
     * The page a how-to asks for by the thing it names (15 Nov 2026, review O: 1,167 everyday sentences in nine languages). The
     * help search weighs every word of a page, and a how-to's own verb pulled it to the wrong one: "naya gahak kaise daalun" was the
     * delete page, "backup kaise lete hain" the restore page, "zabaan kaise badlun" the second shop's, "taala kaise lagaun" the
     * percent sums'. The thing named says the page, most particular first; null leaves it to the search. Only a page the app has.
     */
    internal fun howToPage(joined: String, topics: List<Topic>): String? {
        val key = when {
            has(joined, NEW_PHONE) -> "faq_restore"
            has(joined, BACKUP) -> "faq_backup"
            has(joined, LANGUAGE_WORDS) -> "faq_language"
            // "Kunci ahli waris" is the heir's key, not the app's lock: the search finds the heir's page.
            has(joined, LOCK_WORDS) && !has(joined, HEIR_WORDS) -> "faq_lock"
            has(joined, HIDE_WORDS) -> "faq_hide"
            has(joined, DELETE_WORDS) -> "faq_delete"
            has(joined, ADD_NEW) && has(joined, CUSTOMER_WORDS) -> "screen_khata"
            has(joined, PLAN_WORDS) -> "intro_plans"
            has(joined, OTHER_DOCS) || has(joined, DOCUMENTS) || has(joined, listOf("پیش فاکتور")) -> "intro_documents"
            else -> null
        } ?: return null
        return key.takeIf { k -> topics.any { it.key == k } }
    }

    /**
     * Who is asking, said before or after the question (15 Nov 2026, review O): "main cashier hun, aaj ki sale batao", "I'm the
     * manager, give me this month's profit", "...main cashier hun". The words say who the speaker is, not what he asks: "cashier"
     * read the counter's books, "manager" a staff member's record, "malik" the owner's name. The clause is taken out and the rest is
     * read; the app's own sign-in, not these words, decides what he may see. Urdu, Hindi, Sindhi and Farsi need the verb ("hun",
     * "هستم"), for "mein cashier" is also "in cashier"; English needs "am".
     */
    internal fun withoutSelf(heard: String): String {
        val toks = heard.split(' ').filter { it.isNotEmpty() }
        val low = toks.map { norm(it) }
        for (i in low.indices) {
            val self = low[i]
            if (self !in SELF_WORDS && self != "i") continue
            var j = i + 1
            if (self == "i") { if (low.getOrNull(j) !in setOf("am", "m")) continue; j++ }
            if (low.getOrNull(j) in ARTICLES) j++
            if (low.getOrNull(j) !in ROLE_SELF) continue
            j++
            val verb = low.getOrNull(j) in AM_WORDS
            if (verb) j++ else if (self in NEEDS_VERB) continue
            val rest = (toks.subList(0, i) + toks.subList(j, toks.size)).joinToString(" ").trim(' ', ',', '،', '.', '-')
            return if (words(rest).isEmpty()) heard else rest
        }
        return heard
    }

    private val SELF_WORDS: Set<String> by lazy { listOf("main", "mai", "mein", "میں", "मैं", "मै", "آئون", "من", "أنا", "انا", "আমি", "saya", "aku").map { norm(it) }.toSet() }
    private val NEEDS_VERB: Set<String> by lazy { listOf("main", "mai", "mein", "میں", "मैं", "मै", "آئون", "من").map { norm(it) }.toSet() }
    private val ARTICLES: Set<String> by lazy { listOf("the", "a", "an", "ek", "aik", "ایک", "एक", "seorang").map { norm(it) }.toSet() }
    private val ROLE_SELF: Set<String> by lazy {
        listOf("cashier", "kashier", "manager", "munshi", "helper", "salesman", "owner", "malik", "کیشیئر", "کیشئر", "مینیجر", "منیجر",
            "منشی", "ہیلپر", "مالک", "कैशियर", "मैनेजर", "मुंशी", "हेल्पर", "मालिक", "ক্যাশিয়ার", "ম্যানেজার", "মালিক", "ڪيشيئر",
            "منيجر", "مالڪ", "صندوقدار", "مدیر", "کاشیر", "الكاشير", "كاشير", "المدير", "مدير", "المالك", "kasir", "manajer", "pemilik")
            .map { norm(it) }.toSet()
    }
    private val AM_WORDS: Set<String> by lazy { listOf("hun", "hoon", "hu", "hoo", "am", "ہوں", "हूं", "हूँ", "हू", "آهيان", "هستم", "ام").map { norm(it) }.toSet() }

    /**
     * A follow-up in Arabic or Farsi writes "his" joined to the word ("هاتفه" his phone, "سعره" its price, "قیمتش" its price) and
     * often "and" before it ("ورقم"); the word alone is what the rules know (15 Nov 2026, review O: "ورقم هاتفه" after Ali Raza's
     * account was "whose?"). Short sentences only, and a word is cut only when it means nothing as it is and its stem means
     * something, so "فروش" (a sale) keeps its "ش". The sentence comes back unchanged when no word is cut.
     */
    internal fun pronounBare(heard: String): String {
        val ws = words(heard)
        if (ws.isEmpty() || ws.size > 4 || ws.none { w -> w.any { it in '\u0600'..'\u06FF' } }) return heard
        var cut = false
        val out = ws.mapNotNull { w ->
            if (w in JOINED_HIS) { cut = true; return@mapNotNull null }
            if (HudhudMind.meaningOf(w).isNotEmpty()) return@mapNotNull w
            // "وDAP": "and" joined to a product's Latin name.
            if (w.length > 2 && w.startsWith("و") && w.drop(1).all { it in 'a'..'z' || it.isDigit() }) { cut = true; return@mapNotNull w.drop(1) }
            val tries = buildList {
                val noAnd = if (w.length > 3 && w.startsWith("و")) w.substring(1) else null
                for (base in listOfNotNull(w, noAnd)) {
                    add(base)
                    for (e in HIS_ENDINGS) if (base.length > e.length + 2 && base.endsWith(e)) add(base.dropLast(e.length))
                }
            }
            val hit = tries.drop(1).firstOrNull { t -> HudhudMind.meaningOf(t).any { it in FACT_CONCEPTS } }
            if (hit != null) { cut = true; hit } else w
        }
        return if (cut && out.isNotEmpty()) out.joinToString(" ") else heard
    }

    /** The facts a follow-up asks of him or of it: only a stem that means one of these is cut out of its pronoun. */
    private val FACT_CONCEPTS = setOf(HudhudMind.C.PHONE, HudhudMind.C.ADDRESS, HudhudMind.C.VILLAGE, HudhudMind.C.FATHER, HudhudMind.C.NAME,
        HudhudMind.C.LIMIT, HudhudMind.C.PROMISE, HudhudMind.C.PAYMENT, HudhudMind.C.ENTRY, HudhudMind.C.PRICE, HudhudMind.C.STOCK,
        HudhudMind.C.COST, HudhudMind.C.SUPPLIER, HudhudMind.C.ACCOUNT)

    private val HIS_ENDINGS: List<String> by lazy { listOf("ها", "هم", "ه", "اش", "ش").map { norm(it) }.sortedByDescending { it.length } }
    private val JOINED_HIS: Set<String> by lazy { listOf("اش", "و").map { norm(it) }.toSet() }
    private val PATA_WORDS: Set<String> by lazy { listOf("pata", "patta", "پتہ", "پتا", "पता").map { norm(it) }.toSet() }

    /** The words of money owed that review O added (the accountant's, Arabic's "on the customers", "pay me"): see [decide]. */
    private val OWED_SAID = listOf("receivable", "receivables", "payable", "payables", "على الزبائن", "على العملاء", "wusool karna", "wasool karna",
        "wusool karne", "wasool karne", "وصول کرنا", "वसूल करना", "পাব", "পাবো", "pay me", "pay us", "بدهکارند", "ada karna", "ادا کرنا",
        "अदा करना",
        // 23 Nov 2026 (BC-240): Bengali's and Hindi's own accountant words for receivables and payables. Another shuffle of the same
        // bank read "টোটাল পাওনা" (total receivable) as somebody's phone number; the side these words say is never the sense's.
        "পাওনা", "দেনা", "लेनदारी", "देनदारी")

    /**
     * Credit given: a word of credit and a word of giving (review O). Only an [Ask.Activity] the rules read is kept by it, and the
     * rules read one only with no word of money paid back and no "until now".
     */
    private fun creditGivenOver(joined: String): Boolean = has(joined, CREDIT_SAID) && has(joined, TOOK_OR_GAVE)

    private val CREDIT_SAID = listOf("udhaar", "udhar", "credit", "ادھار", "उधार", "utang", "piutang", "اڌار", "دين", "نسیه", "বাকিতে", "ধার")

    /** Goods that came back to the shop (review O). */
    private val CAME_BACK = listOf("wapis aaya", "wapis aaye", "wapis aayi", "wapas aaya", "wapas aaye", "wapas aayi", "wapis aya", "واپس آیا",
        "واپس آئے", "واپس آئی", "वापस आया", "वापस आए", "वापस आई", "came back", "brought back")

    private val NEW_PHONE = listOf("naya phone", "naye phone", "nayi phone", "naya mobile", "naye mobile", "new phone", "new mobile",
        "phone badla", "phone badal", "phone kho", "phone gum", "mobile kho", "phone change", "phone lost", "lost my phone", "restore",
        "ganti hp", "hp baru", "ponsel baru", "pindahin", "pindah hp", "نیا فون", "نئے فون", "نئی فون", "فون بدل", "فون گم", "فون کھو",
        "नया फोन", "नए फोन", "फोन बदल", "फोन खो", "নতুন ফোন", "ফোন হারা", "هاتف جديد", "الهاتف الجديد", "جوال جديد", "استعادة",
        "گوشی جدید", "گوشی نو", "بازیابی", "نئون فون", "pulihkan")
    private val HEIR_WORDS = listOf("waris", "ahli waris", "heir", "heirs", "وارث", "ورثاء", "ورثه", "वारिस", "উত্তরাধিকারী", "وارث")
    private val LANGUAGE_WORDS = listOf("zabaan", "zaban", "zuban", "zubaan", "language", "bahasa", "زبان", "زبانیں", "भाषा", "ज़बान",
        "जबान", "ভাষা", "اللغة", "لغة", "ٻولي")
    private val DELETE_WORDS = listOf("delete", "ڈیلیٹ", "डिलीट", "ডিলিট", "حذف", "hapus", "মুছ", "মুছে", "mitana", "mitaun", "مٹا",
        "مٹانا", "ڊليٽ")
    private val CUSTOMER_WORDS = listOf("gahak", "gaahak", "grahak", "customer", "customers", "pelanggan", "گاہک", "گاہکوں", "ग्राहक",
        "গ্রাহক", "খদ্দের", "কাস্টমার", "زبون", "زبونا", "عميل", "مشتری", "گراهڪ", "party", "parti")
    private val PLAN_WORDS = listOf("qist", "qisten", "qiston", "installment", "installments", "instalment", "instalments", "قسط", "قسطیں",
        "किस्त", "किश्त", "কিস্তি", "cicilan", "angsuran")

    /**
     * A delete said as an order (15 Nov 2026, review O): Hudhud deletes nothing, so the sentence is never the person or the entries it
     * names. Only the order's own forms ("delete kar do", "hata do", "احذف", "پاک کن"); a sentence that tells or asks about a
     * delete ("kisne kya delete kiya", "delete ho gaya") is another question. English and Indonesian say the order first.
     */
    internal fun erasesOrder(joined: String): Boolean {
        if (has(joined, ERASE_ORDER)) return true
        val first = joined.substringBefore(' ')
        return first in ERASE_FIRST || (first in PLEASE_FIRST && joined.split(' ').getOrNull(1) in ERASE_FIRST)
    }

    private val ERASE_ORDER = listOf("delete kar do", "delete kardo", "delete karo", "delete kar den", "delete kar dein", "delete kijiye",
        "hata do", "hata den", "hata dein", "mita do", "mita den", "mita dein", "ڈیلیٹ کر دو", "ڈیلیٹ کرو", "ڈیلیٹ کر دیں", "ہٹا دو",
        "ہٹا دیں", "مٹا دو", "مٹا دیں", "डिलीट कर दो", "डिलीट करो", "डिलीट कर दें", "हटा दो", "हटा दें", "मिटा दो", "मिटा दें",
        "মুছে দাও", "মুছে দিন", "মুছে ফেলো", "মুছে ফেল", "ডিলিট করো", "ডিলিট কর", "ডিলিট করে দাও", "احذف", "امسح", "پاک کن", "پاکش کن",
        "حذف کن", "حذفش کن", "ڊليٽ ڪريو", "ڊليٽ ڪر", "ڊليٽ ڪري ڇڏيو", "ميٽي ڇڏيو")
    private val ERASE_FIRST = setOf("delete", "remove", "erase", "wipe", "hapus", "hapuskan")
    private val PLEASE_FIRST = setOf("please", "pls", "plz", "tolong", "can", "kindly")

    /**
     * "Kar do", "bhej do", "bata do": the order's "do", never the number two (15 Nov 2026, review O). Voice entry's numbers read the
     * word "do" as 2, so "Saleem Bhai ka udhaar maaf kar do" opened an entry of 2 rupees. An amount of 2 said with no figure, whose
     * every "do" follows a verb of the order, is no amount ("do sau" is 200, never 2, so it never reaches here).
     */
    internal fun orderDo(heard: String, amount: Double): Boolean {
        if (amount != 2.0 || heard.any { it.isDigit() }) return false
        val w = words(heard)
        val at = w.indices.filter { w[it] in TWO_WORDS }
        return at.isNotEmpty() && at.all { i -> i > 0 && w[i - 1] in ORDER_STEMS }
    }

    /** Whether a "do" in the sentence follows a verb of the order ("kar do", "bhej do"). */
    private fun saysOrderDo(heard: String): Boolean {
        val w = words(heard)
        return w.indices.any { i -> i > 0 && w[i] in TWO_WORDS && w[i - 1] in ORDER_STEMS }
    }

    private val NOT_SAID = listOf("nahi", "nahin", "nhi", "نہیں", "نہی", "नहीं", "not", "never", "no", "لم", "لا", "نه", "نمی", "نکرد", "tidak", "belum", "না", "নি", "ڪونه", "unpaid", "baqi", "pending")

    private val TWO_WORDS = setOf("do", "دو", "दो")
    private val ORDER_STEMS = setOf("kar", "bhej", "de", "dikha", "bata", "hata", "mita", "maaf", "likh", "daal", "dal", "nikal", "bana",
        "samjha", "sun", "کر", "بھیج", "دے", "دکھا", "بتا", "ہٹا", "مٹا", "معاف", "لکھ", "ڈال", "نکال", "بنا", "कर", "भेज", "दे", "दिखा",
        "बता", "हटा", "मिटा", "माफ", "लिख", "डाल", "निकाल", "बना")

    /**
     * A supplier's bill with a word for not paid (review O): the bills that wait, never a count of every bill. Arabic and Farsi call
     * a supplier's bill an invoice ("فواتير الموردين", "فاکتورهای تامین کننده"): beside a supplier's word it is his bill.
     */
    private fun unpaidSupplierBills(joined: String, m: HudhudMind.Read): Boolean =
        (has(joined, BILL_WORDS) || has(joined, INVOICE_WORDS)) &&
            (has(joined, SUPPLIER_SIDE_WORDS) || has(joined, PURCHASE_SIDE) || m.has(HudhudMind.C.SUPPLIER)) &&
            (has(joined, UNPAID_WORDS) || has(joined, LEFT_OVER))

    private val LEFT_OVER = listOf("reh gaya", "reh gayi", "reh gaye", "rah gaya", "rah gayi", "رہ گیا", "رہ گئی", "रह गया", "रह गई",
        "remaining", "left to pay", "غير المدفوعة", "پرداخت نشده", "belum dibayar")

    /** Customers who stopped coming (review O): the "stopped coming" page. Only the phrases that say it; "nahi aate" alone may be money. */
    private val LAPSED = listOf("aana band", "ana band", "aane band", "aana chhod", "aana chor", "ab nahi aate", "ab nahi aata",
        "ab nahin aate", "stopped coming", "stop coming", "don t come anymore", "not coming anymore", "no longer come",
        "آنا بند", "اب نہیں آتے", "اب نہیں آتا", "आना बंद", "अब नहीं आते", "अब नहीं आता", "tidak datang lagi", "sudah tidak datang",
        "tak datang lagi", "আসা বন্ধ", "আর আসে না", "لم يعد يأتي", "لم يعودوا", "توقف عن المجيء", "دیگر نمی آید", "دیگر نمی آیند",
        "دیگه نمیاد", "اچڻ بند")

    private fun helpOf(said: List<String>, topics: List<Topic>): String? {
        if (topics.isEmpty()) return null
        // The words that only ask (how, where) say nothing of the page: "kahan" found the data page's title
        // ("Mera data kahan rehta hai?") for every "… kahan hai" (14 Nov 2026, the owner's question lists).
        val q = said.filter { it.length >= 3 && it !in STOP_SET && norm(it) !in HOW_SET && norm(it) !in WAY_SET && it !in HELP_FILLER }.distinct()
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
        if (scores[best] < ln(1.0 + n / 2.0)) return null
        return topics[best].key
    }

    /**
     * Little words of a sentence that name no page (16 Nov 2026, the owner's lists: "ایک", "میں", "اگر", "بعد", "جائے" found the heir's-key
     * page in sixty sentences, each for words every page of the app has): left out when a help page is looked for by its words.
     */
    private val HELP_FILLER: Set<String> by lazy {
        listOf("اگر", "ایک", "میں", "بعد", "جائے", "جایے", "ہوں", "ہوگا", "ہوگی", "ہوگے", "کسی", "کوئی", "ایسے", "ایسا", "بھی", "سکتا", "سکتی", "سکتے",
            "کرنے", "کرتے", "کریں", "کروں", "کرے", "دیں", "دوں", "لیے", "ساتھ", "دوران", "پہلے", "پھر", "تمام", "اپنے", "اپنی", "اپنا",
            "agar", "baad", "phir", "bhi", "koi", "sakta", "sakti", "sakte", "jaye", "karne", "karna", "tamam", "apne", "apni",
            "अगर", "बाद", "कोई", "सकता", "सकती", "सकते", "जाए", "करने", "सभी", "अपने", "अपनी").map { norm(it) }.toSet()
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
