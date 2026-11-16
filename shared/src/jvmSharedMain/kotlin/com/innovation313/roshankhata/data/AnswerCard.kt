package com.innovation313.roshankhata.data

/**
 * One answer of Hudhud's, as both platforms show it (16 Nov 2026, audit T22): made once, in :shared, by [AnswerBook] and the
 * readers behind it, so the phone's sheet and the computer's panel can never say two different things for one question. It
 * holds only what fits both: the words and figures, the rows, which page it comes from ([ScreenId], each platform opens its own
 * page for it) and what that page opens on ([OpenWith]). How it is drawn, said aloud or played stays on each platform.
 *
 * Every field keeps the meaning the phone's `HudhudBook.Answer` gave it (the phone turns this card into that one, field by
 * field, so its sheet and the golden answers saw no change). A word is named by its key (`W.x`), never by an Android id.
 */
data class AnswerCard(
    val mood: Mood,
    /** The small heading over the figure. */
    val label: String? = null,
    /** The one big figure. */
    val figure: String? = null,
    val lines: List<String> = emptyList(),
    val rows: List<Row> = emptyList(),
    /** The page the answer comes from, opened by the card's button. */
    val open: ScreenId? = null,
    /** A customer's own page instead, by id. */
    val openParty: Long? = null,
    /** What the page opens on: the sales report's days, the entry form filled in, the day book's day, a product's buyers. */
    val openWith: OpenWith? = null,
    /** The button's own words (a word key), when "Open" would not say what it does. */
    val openLabel: String? = null,
    /** The big figure is an amount of money: not said aloud while Home's balance is hidden. */
    val money: Boolean = false,
    /**
     * What to say instead of the card itself, made with an [AnswerEnv] in the voice's language (a help page's first sentences,
     * a greeting, a customer's likely days without the Radar's judgement); null: the whole card is said ([AnswerBook.speechOf]).
     */
    val spoken: ((AnswerEnv) -> String)? = null,
    /** "Did you mean": the questions offered as chips under the card. */
    val options: List<Hudhud.Ask> = emptyList(),
    /** The words of the chips in [options] when the usual chip words would not say them (a span each chip says). */
    val optionLabels: Map<Hudhud.Ask, String> = emptyMap(),
    /** The button opens Hudhud's voice settings (what he calls the owner). */
    val voiceSettings: Boolean = false,
    /** "Open" shows the automations' own window, where the owner turns them on or off. */
    val automations: Boolean = false,
    /** The question this answers: asked again in the voice's language when that is another. */
    val ask: Hudhud.Ask? = null,
    /** What a sentence nothing fitted exactly was taken as: written over the card; such an answer is not said aloud. */
    val tookAs: String? = null,
    /** Not said aloud: a choice between names, a guess. */
    val quiet: Boolean = false,
    /** One of his little acts, played where he is drawn. */
    val act: Hudhud.Move? = null,
    /** Values the card shows and the voice never says (a phone number, an account number, an email, a tax number). */
    val secrets: List<String> = emptyList(),
    /** A change to how he speaks, asked of him: made by the platform as the card arrives. */
    val voice: Hudhud.VoiceChange? = null,
    /** Verses shown under the words, with the note that this is a brief meaning. */
    val verses: List<Verse> = emptyList(),
    /** A line under the verses (that the words above are a brief meaning, not a translation). */
    val verseNote: String? = null,
    /** A matter of faith or of the Quran's own story: shown quietly (the phone's Personal tab), with nothing leading on. */
    val reverent: Boolean = false,
    /** A change to the look he is asked to make (text size or theme): the card itself is not shown, the platform makes it. */
    val look: HudhudLook.Made? = null,
    /** The change that takes back the one just made: the card's button says Undo and makes it. */
    val lookUndo: HudhudLook.Made? = null
) {
    /** How an answer sits with the shop: [GOOD] news Hudhud is glad of, [CARE] something to see to, else [CALM]. */
    enum class Mood { CALM, GOOD, CARE }

    /**
     * A customer or supplier on the card: tapped, his page opens — with the entry form filled in when the row offers him for an
     * entry ([amount], [isGiven]). A row that is not a customer opens [open] instead; a row with [ask] asks it when tapped.
     */
    data class Row(
        val partyId: Long,
        val name: String,
        val figure: String,
        val sub: String?,
        val amount: Double? = null,
        val isGiven: Boolean = true,
        val open: ScreenId? = null,
        val ask: Hudhud.Ask? = null
    )

    /** A verse the card shows: its surah's Arabic name, where it is, and its Arabic text — shown, never said aloud. */
    data class Verse(val surah: String, val ref: String, val arabic: String)
}

/**
 * What a page opens on, typed (it was an Android Bundle): each platform turns it into its own page's starting point. The
 * phone's keys are the same extras as before, so the page opens exactly as it did.
 */
sealed interface OpenWith {
    /** The sales report on one of its own periods ([com.innovation313.roshankhata.screens.ReportPeriod]'s name). */
    data class SalesPeriod(val period: String) : OpenWith
    /** The sales report from [firstDay] to [lastDay] (each a day's first moment), as the range picker gives them. */
    data class SalesDays(val firstDay: Long, val lastDay: Long) : OpenWith
    /** A customer's entry form filled in (voice entry's own two values). */
    data class EntryForm(val amount: Double, val isGiven: Boolean) : OpenWith
    /** Who bought a product. */
    data class ProductBuyers(val productId: Long, val productName: String) : OpenWith
    /** The day book on the day that begins at [day]. */
    data class Day(val day: Long) : OpenWith
}

/**
 * The pages an answer can come from, named once for both platforms (the phone maps each to its screen, the computer to its
 * section). A new page is added here and to both maps, which are `when`s with no else: neither builds until it says where.
 */
enum class ScreenId {
    ABOUT, AGING, ALERTS, AUDIT, BACKUP, BILLS, BUSINESS_CARD, BUSINESS_SETTINGS, BUSINESS_SWITCH, CALCULATOR, CASH_FLOW,
    CASHBOOK, CHEQUES, COUNTER_SALE, DAY_BOOK, DUPLICATE_CUSTOMERS, EXPIRING, FOLLOW_UP, HELP, INSIGHTS, INVOICES, KHATA,
    LANGUAGE, LAPSED, MERA_KHATA, ORDERS, PAYMENT_VOICE, PLANS, POSTER, PRODUCT_CUSTOMERS, PRODUCTS, RATE_LIST, RECYCLE_BIN,
    SALES_REPORT, SCHEMES, SEASONS, SHIFT, SNAPSHOTS, STAFF, SUPPLIER_RATES, VIEWER, WARIS, ZAKAT
}

/** A row of the bell as Hudhud reads it: what kind and its title, in the app's language. */
class AnswerAlert(val kind: Kind, val title: String) {
    /** The bell's kinds Hudhud tells apart (the phone's HomeAlerts.Kind, the computer's LedgerAlerts and backup notes). */
    enum class Kind { CHEQUE, PLAN, BILL, EXPIRING, STOCK, DRAWER, BACKUP, BACKUP_FAILED, BACKUP_OTHER_PHONE, CASHFLOW, UPDATED, SYNC, LATE }
}
