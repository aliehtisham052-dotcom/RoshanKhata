package com.innovation313.roshankhata.data

/**
 * How money moved on a khata entry: the one rule both entry forms save by and the drawer counts by (17 Nov 2026,
 * the owner's decision T43). Plain Kotlin, no Room, so it is tested without a database too.
 *
 * Many shops never open the Cashbook and write everything on the party's khata. A supplier's "I got" is usually goods
 * taken on credit — nothing enters the drawer (8 Nov 2026 review, C2: both forms used to pre-tick Cash on it and the
 * drawer read Rs 40,000 short). But a supplier sometimes hands money back (change, a refund, an advance returned), and
 * that cash IS in the drawer. So a supplier's "I got" asks "What did you get?": Goods (on credit), chosen to begin
 * with, saves no way of paying; Money asks how it was paid (Cash chosen to begin with) and marks the entry
 * [EntryKind.MONEY].
 *
 * Why a mark on the entry and not only its way of paying: rows the old forms wrote carry "cash" on a supplier's
 * "I got" by default, and they must never start counting. Only an entry the new choice wrote is MONEY — no row written
 * before this rule, in any book, backup or copy, can be — so the drawer counts a supplier's cash "I got" exactly when
 * the owner said Money. No schema change: [LedgerEntry.entryKind] (schema 45) already says what an entry is beyond its
 * direction, and a release that does not know MONEY reads it as NORMAL ([EntryKind.of]) — counted as before.
 */
object KhataMoney {
    /** [EntryKind.MONEY]'s stored text. Spelled here so this file needs nothing of Room; EntryKind takes it from here. */
    const val MONEY = "MONEY"

    /** [PaymentMethod.CASH]'s stored text (the DAO's SQL spells it as 'cash'; KhataMoneyBookTest holds the three equal). */
    const val CASH = "cash"

    /**
     * Whether the entry says how money moved (a way of paying), as both forms save it:
     *  - goods on the entry (sold, returned, sent back to a supplier) were not paid by any way: none;
     *  - money paid TO a supplier (M40, 8 Nov 2026): yes — cash from the drawer;
     *  - money received from a customer: yes;
     *  - a supplier's "I got": only when the owner said Money ([supplierMoney]); goods on credit carry none (C2).
     * A customer's "I gave" (a sale) is not decided here: the forms keep what they always kept for it.
     */
    fun carriesMethod(isGiven: Boolean, isCustomer: Boolean, hasGoods: Boolean, supplierMoney: Boolean): Boolean = when {
        hasGoods -> false
        isGiven -> !isCustomer
        isCustomer -> true
        else -> supplierMoney
    }

    /** Whether the entry is written as [EntryKind.MONEY]: a supplier's "I got" the owner called Money, with no goods on it. */
    fun isSupplierMoney(isGiven: Boolean, isCustomer: Boolean, hasGoods: Boolean, supplierMoney: Boolean): Boolean =
        !isGiven && !isCustomer && supplierMoney && !hasGoods

    /**
     * Whether an entry's amount moved through the drawer — the rule [KhataDao.khataCashBetween]'s SQL writes: marked
     * Cash, and either money paid out (to anyone), money from a customer, or a supplier's "I got" marked Money. A
     * supplier's "I got" marked Cash by the old forms' default ([kind] NORMAL) stays out.
     */
    fun countsInDrawer(isGiven: Boolean, isCustomer: Boolean, method: String?, kind: String?): Boolean =
        method == CASH && (isGiven || isCustomer || kind == MONEY)
}
