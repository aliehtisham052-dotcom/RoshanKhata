package com.innovation313.roshankhata.data

/**
 * The roles matrix of the assistant's answers (plan B7, 24 Nov 2026): for every kind of question ([Hudhud.Ask]) and every
 * role, who may be told what. This file is only DATA, with no reference to Access, Room or the answer classes, so the
 * plain-Kotlin harness can compile it; three tests read it:
 *
 *  - [HudhudRolesTableTest] (runs in the harness) holds the table to the source: every kind of question and every facet
 *    has a row, and the answer code asks the gate the row says it asks;
 *  - HudhudRolesMatrixTest (desktopTest, over a real book) asks every kind as every role and holds the cards to the table;
 *  - HudhudRolesAnswersTest (phone, Robolectric) and HudhudRolesDeskTest (the computer's panel) do the same over their own
 *    environment.
 *
 * All names, numbers and amounts here are invented (lesson 80). A marker is a text no real answer holds by chance.
 */
object HudhudRolesTable {

    /** Who is asking. [HELPER] is a read-only copy of the book (ViewerMode, a secrets = false export). */
    enum class Role { OWNER, MANAGER, CASHIER, LOCKED, HELPER, DESK_CASHIER }

    /** What a card needs before it tells a fact: the gate's own name ([AnswerGate]). */
    enum class Gate { OPEN, REPORTS, STAFF, SETTINGS }

    /**
     * How a kind of question is held:
     *  - [OPEN]   nothing private; anyone who may open the sheet is told;
     *  - [GATED]  the whole card needs its [Gate];
     *  - [FACETS] the gate depends on the facet; see the facet tables below;
     *  - [WRAP]   it only carries other questions and says what they say;
     *  - [FORM]   it offers a form to fill in; a read-only copy refuses it.
     */
    enum class Held { OPEN, GATED, FACETS, WRAP, FORM }

    data class Row(val held: Held, val gate: Gate = Gate.OPEN, val why: String = "")

    /** One row for every [Hudhud.Ask] subtype, by its simple name. */
    val ASK: Map<String, Row> = mapOf(
        "Figure" to Row(Held.GATED, Gate.REPORTS, "every sum; a cashier is told only his own sales today"),
        "Parties" to Row(Held.OPEN, why = "a customer's balance: the Khata list is every signed-in user's"),
        "BackupStatus" to Row(Held.OPEN, why = "told to everyone; the Backup screen is the owner's (finding F1)"),
        "Drawer" to Row(Held.GATED, Gate.REPORTS, "the cash expected would say how much could go missing unseen"),
        "Help" to Row(Held.OPEN, why = "a how-to page; its last line names who may open the screen"),
        "Salaam" to Row(Held.OPEN), "Hello" to Row(Held.OPEN), "Chat" to Row(Held.OPEN), "Unknown" to Row(Held.OPEN),
        "OffTopic" to Row(Held.OPEN, why = "a sum typed by the owner is worked out, never read from the book"),
        "Abilities" to Row(Held.OPEN, why = "the list is cut to what this person may ask (reports, viewer)"),
        "Period" to Row(Held.GATED, Gate.REPORTS),
        "Moved" to Row(Held.OPEN, why = "what one named customer paid or took: his own Khata page (finding F2)"),
        "Entry" to Row(Held.FORM, why = "the card says what was understood; the phone's read-only copy cannot write"),
        "Entries" to Row(Held.FORM),
        "WhenPays" to Row(Held.GATED, Gate.REPORTS, "the Payment Radar is behind REPORTS"),
        "Today" to Row(Held.FACETS, why = "the day's counts for all, the figures with REPORTS"),
        "Person" to Row(Held.FACETS, why = "see PERSON"),
        "Goods" to Row(Held.FACETS, why = "see GOODS"),
        "Shop" to Row(Held.FACETS, why = "see SHOP"),
        "About" to Row(Held.OPEN, why = "the app, its maker, its history; no book data"),
        "Clock" to Row(Held.OPEN),
        "Count" to Row(Held.FACETS, why = "see COUNT"),
        "Status" to Row(Held.FACETS, why = "see STATUS"),
        "Record" to Row(Held.FACETS, why = "see RECORD"),
        "Deliveries" to Row(Held.OPEN, why = "the bills that came: the Supplier bills screen is every signed-in user's (CI, 24 Nov 2026)"),
        "Activity" to Row(Held.GATED, Gate.REPORTS, "who did what and for how much"),
        "Collected" to Row(Held.GATED, Gate.REPORTS),
        "NeedName" to Row(Held.FACETS, why = "the private note's list says a read-only copy holds none"),
        "Cashbook" to Row(Held.GATED, Gate.REPORTS),
        "Returns" to Row(Held.GATED, Gate.REPORTS),
        "Counter" to Row(Held.FACETS, why = "see COUNTER: every facet is REPORTS"),
        "Act" to Row(Held.OPEN), "Voice" to Row(Held.OPEN), "Look" to Row(Held.OPEN), "LookDone" to Row(Held.OPEN),
        "CallMe" to Row(Held.OPEN, why = "kept on this phone only, never in the book"),
        "TopGoods" to Row(Held.GATED, Gate.REPORTS),
        "Qarz" to Row(Held.GATED, Gate.REPORTS),
        "Automations" to Row(Held.OPEN, why = "which notices are on and when; no figure (finding F3)"),
        "NeedGoods" to Row(Held.OPEN, why = "product names only; the facet's own answer is gated when it is asked"),
        "Paper" to Row(Held.FACETS, why = "see PAPER"),
        "OnDays" to Row(Held.FACETS, why = "see DATED"),
        "Both" to Row(Held.WRAP), "Compare" to Row(Held.GATED, Gate.REPORTS), "SpanLimit" to Row(Held.WRAP),
        "Now" to Row(Held.WRAP), "Guessed" to Row(Held.WRAP), "DidYouMean" to Row(Held.WRAP),
        "Why" to Row(Held.GATED, Gate.REPORTS, "facts about a figure, never a cause the book cannot show"),
        "Took" to Row(Held.OPEN, why = "what one named customer took: his own Khata page, as Moved"),
        "NoHistory" to Row(Held.OPEN)
    )

    /** Facet tables: "Enum.ENTRY" to the gate its card asks. An entry not listed is a failure of the table test. */
    private fun facets(prefix: String, gate: Gate, vararg names: String) = names.associate { "$prefix.$it" to gate }

    /** A customer's facts: his Khata page is open to every signed-in user; the note is held by "a read-only copy holds none". */
    val PERSON: Map<String, Gate> =
        facets("Facet", Gate.OPEN, "PHONE", "DETAILS", "LAST_PAYMENT", "LAST_ENTRY", "SINCE", "LIMIT", "PROMISE", "ENTRIES", "NOTE")

    /**
     * A product's facts. COST, SUPPLIERS, SOLD and COVER are the owner's and a manager's (REPORTS); a last delivery's rate and
     * an overview's rate lines are REPORTS inside an otherwise open card (listed [GOODS_PARTLY]).
     */
    val GOODS: Map<String, Gate> =
        facets("GoodsFacet", Gate.OPEN, "OVERVIEW", "PRICE", "STOCK", "LAST_IN", "BUYERS", "LABEL", "BARCODE", "PACK", "TAX", "SHELF",
            "ADDED", "BATCHES", "SCHEME") +
            facets("GoodsFacet", Gate.REPORTS, "COST", "SUPPLIERS", "SOLD", "COVER")

    /** Open cards that hold a REPORTS-only line (a rate), checked in the source by [HudhudRolesTableTest]. */
    val GOODS_PARTLY: Set<String> = setOf("GoodsFacet.OVERVIEW", "GoodsFacet.LAST_IN")

    /** The shop's profile: the name, address, phone, currency and trade are printed on what a cashier hands out. */
    val SHOP: Map<String, Gate> =
        facets("ShopFacet", Gate.OPEN, "NAME", "ADDRESS", "PHONE", "CURRENCY", "TRADE") +
            facets("ShopFacet", Gate.SETTINGS, "EMAIL", "TAX", "BANK", "COUNTRY") +
            ("ShopFacet.ALL" to Gate.OPEN)

    /** Counts: the staff head count is the owner's; the others tell money only with REPORTS. */
    val COUNT: Map<String, Gate> =
        facets("Thing", Gate.OPEN, "CUSTOMERS", "SUPPLIERS", "PRODUCTS", "CALCULATIONS", "APP_OPENS") +
            facets("Thing", Gate.STAFF, "STAFF") +
            facets("Thing", Gate.OPEN, "ENTRIES", "INVOICES", "BILLS", "TICKETS")

    /** The counts whose card also holds an amount behind REPORTS. */
    val COUNT_PARTLY: Set<String> = setOf("Thing.ENTRIES", "Thing.INVOICES", "Thing.BILLS", "Thing.TICKETS")

    /** "Which are still open": the cheques, plans, bills, the book as a whole and the salaries. */
    val STATUS: Map<String, Gate> =
        facets("StatusOf", Gate.REPORTS, "CHEQUES", "PLANS", "BOOK", "INVOICES") +
            facets("StatusOf", Gate.OPEN, "BILLS") +
            facets("StatusOf", Gate.STAFF, "STAFF") +
            facets("StatusOf", Gate.OPEN, "LOW_STOCK")

    val RECORD: Map<String, Gate> =
        facets("RecordFacet", Gate.STAFF, "STAFF_PAY", "STAFF_PAID", "ATTENDANCE", "STAFF_PERSON") +
            facets("RecordFacet", Gate.REPORTS, "PLAN", "PLAN_CLOSED", "CHEQUE")

    val COUNTER: Map<String, Gate> = facets(
        "CounterFacet", Gate.REPORTS, "BIGGEST", "LAST_TICKET", "BY_CASHIER", "DISCOUNTS", "TAX", "ON_ACCOUNT", "PAY_WAYS", "VOIDED",
        "RETURN_REASONS", "RETURN_WAYS", "RETURN_GOODS", "SHIFTS", "SHIFT_SHORT", "DAY_COUNT", "LOYALTY", "WRITTEN_OFF",
        "STOCK_CHANGES", "LAST_COUNT"
    )

    val PAPER: Map<String, Gate> = facets("PaperOf", Gate.REPORTS, "ORDER", "INVOICE", "SCHEMES") +
        facets("PaperOf", Gate.OPEN, "BILL")

    /** Every span-and-measure ask is a figure. */
    val MEASURE: Map<String, Gate> = facets("Measure", Gate.REPORTS, "SALES", "PROFIT")
    val WHY: Map<String, Gate> = facets("WhyOf", Gate.REPORTS, "SALES", "PROFIT", "EXPENSES", "COLLECTED", "DUES")
    // A named customer's paid or took on a day is his own Khata page, as Moved (CI, 24 Nov 2026).
    val DATED: Map<String, Gate> = facets("DatedOf", Gate.REPORTS, "SALES", "PROFIT", "COLLECTED", "ACTIVITY") +
        facets("DatedOf", Gate.OPEN, "PAID", "TOOK")

    /** Enum class simple name to its table, for the coverage test. */
    val TABLES: Map<String, Map<String, Gate>> = mapOf(
        "Facet" to PERSON, "GoodsFacet" to GOODS, "ShopFacet" to SHOP, "Thing" to COUNT, "StatusOf" to STATUS,
        "RecordFacet" to RECORD, "CounterFacet" to COUNTER, "PaperOf" to PAPER, "Measure" to MEASURE, "WhyOf" to WHY,
        "DatedOf" to DATED
    )

    /**
     * What each role holds of the gates. The helper's phone (a read-only copy) is the owner of its copy: counter mode is off
     * there, so REPORTS, STAFF and SETTINGS are all open; what it lacks is in the book (no private note, no cost price, no PIN
     * hash) and in [viewer], which refuses the forms.
     */
    fun may(role: Role, gate: Gate): Boolean = when (gate) {
        Gate.OPEN -> true
        Gate.REPORTS -> role == Role.OWNER || role == Role.MANAGER || role == Role.HELPER
        Gate.STAFF, Gate.SETTINGS -> role == Role.OWNER || role == Role.HELPER
    }

    /** True for the role whose book is a read-only copy: it refuses [Held.FORM] cards. */
    fun viewer(role: Role): Boolean = role == Role.HELPER

    // ------------------------------------------------------------------------------------------------ the markers

    /**
     * Distinct invented values, each with the gate its data class sits behind and the words the matrix looks for in a card.
     * [digits] is what must not appear (commas, spaces and signs taken out of the card's text first).
     */
    data class Marker(val name: String, val gate: Gate, val text: String, val note: String)

    /** Money markers are amounts whose digits no other figure of the seed book shares. */
    val MARKERS: List<Marker> = listOf(
        Marker("cost price", Gate.REPORTS, "7731", "a product's own cost price"),
        Marker("salary", Gate.STAFF, "48617", "a staff member's monthly pay"),
        Marker("tax number", Gate.SETTINGS, "RLTAX-5520", "the shop's tax number"),
        Marker("bank account", Gate.SETTINGS, "RLBANK-0419", "the shop's account or IBAN"),
        Marker("email", Gate.SETTINGS, "roles.shop.5520@example.invalid", "the shop's email"),
        Marker("country", Gate.SETTINGS, "Fiji", "the shop's country name (the roles profile sets Fiji, a country no other card names)"),
        Marker("private note", Gate.OPEN, "RLNOTE-3306", "a customer's private note: told by design, never to a read-only copy"),
        Marker("sales total", Gate.REPORTS, "61357", "the day's walk-in sale: a total the shop made, never a cashier's own")
    )
}
