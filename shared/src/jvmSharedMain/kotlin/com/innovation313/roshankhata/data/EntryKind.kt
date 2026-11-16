package com.innovation313.roshankhata.data

/**
 * What a khata entry is, beyond which way it went ([LedgerEntry.entryKind], schema 45, 16 Nov 2026 — the owner's
 * decision D-02 of the audit): an ordinary entry, or an OPENING balance — money owed from before the book, brought
 * in from another app's file ([KhataDao.importOpeningBalances]) or marked so by the owner afterwards.
 *
 * An opening balance is money owed, not money moved today: it stays in every balance, statement, dues by age, the
 * Payment Radar's debt and Zakat, and is left out of every figure that counts SALES (Sale Insights, the sales report,
 * profit, the day report, the 21:00 notice, the best customers and products, customers who stopped coming, the
 * assistant's sale answers). Before this, a customer imported with Rs 50,000 owed read as Rs 50,000 sold on the day
 * of the import.
 *
 * Money LENT is not a kind of its own (D-03): the existing Qarz-e-Hasna mark ([LedgerEntry.isQarzeHasna]) already
 * means exactly "money lent, not a sale" in every place that counts — sales, profit, the sales register, the Radar's
 * pace, customers who stopped coming, Zakat (counted apart, as a loan expected back), the statement's line and the day
 * book. The entry forms now name it as a loan (`entry_cash_lent`) so the owner finds it.
 *
 * Stored as text, as [PaymentMethod]'s keys are: a value a newer release writes reads here as [NORMAL] ([of]),
 * which counts as before rather than as something this release cannot name. The SQL in [KhataDao] spells the
 * values as literals ('NORMAL'); OpeningBalanceTest holds the two equal.
 */
object EntryKind {
    const val NORMAL = "NORMAL"
    const val OPENING = "OPENING"

    val ALL: List<String> = listOf(NORMAL, OPENING)

    /** The stored value as one this release knows: anything else (null, blank, a newer release's word) is [NORMAL]. */
    fun of(raw: String?): String = raw?.trim()?.uppercase()?.takeIf { it in ALL } ?: NORMAL

    /** Whether an entry of this kind counts in the sales figures (the one rule the DAO's `t.entryKind = 'NORMAL'` writes). */
    fun countsAsSale(kind: String?): Boolean = of(kind) == NORMAL

    /**
     * Why [e] may not be an opening balance, or null when it may (the rule [KhataDao.setEntryKind] keeps, and the
     * screens ask before offering the choice). An opening balance is one figure owed from before the book:
     *  - no goods: goods handed over are a sale, counted in stock (a line on the entry or [hasGoods]);
     *  - not a loan: a Qarz-e-Hasna entry is lent money and already kept out of sales;
     *  - not written by another record (a counter ticket, a bill, a plan, a cheque, a return — [owner]): those are what
     *    they are, and are changed where they were made;
     *  - not one half of a paid-now sale ([LedgerEntry.pairedEntryId]): the two halves must stay alike;
     *  - not in the bin.
     */
    fun refusal(e: LedgerEntry, hasGoods: Boolean, owner: String?): Refusal? = when {
        e.isDeleted -> Refusal.BINNED
        hasGoods || !e.itemName.isNullOrBlank() || e.productId != null -> Refusal.GOODS
        e.isQarzeHasna -> Refusal.LOAN
        e.saleId != null || !owner.isNullOrEmpty() -> Refusal.LINKED
        e.pairedEntryId != null -> Refusal.PAIRED
        else -> null
    }

    enum class Refusal { BINNED, GOODS, LOAN, LINKED, PAIRED }

    /** The audit row's detail for a change of kind: "NORMAL>OPENING". */
    fun auditDetail(from: String, to: String): String = "${of(from)}>${of(to)}"

    /**
     * The kind an audit detail changed to, or null when the detail is not one [auditDetail] wrote. A PIN's giver may
     * follow it (" · Ali", [Access.allowedNote]).
     */
    fun changedTo(detail: String?): String? =
        detail?.substringBefore(" \u00b7 ")?.split('>')?.takeIf { it.size == 2 }?.get(1)?.trim()?.uppercase()?.takeIf { it in ALL }
}
