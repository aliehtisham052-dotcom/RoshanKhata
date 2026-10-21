package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A counter sale (POS Phase 1, 21 Oct 2026, schema 32): one ticket rung up
 * at the counter — its lines, how it was paid, and what it posted.
 *
 * WHAT IT IS NOT. It is not a new ledger. The money still lives where it
 * always has: a customer's sale is a khata "I gave" entry with its goods
 * lines, each payment an "I got" against it (cash, bank, cheque, online),
 * and a walk-in's payments are cashbook income. The sale row is the DOCUMENT
 * that tied those writes together — the number printed on the receipt, the
 * figures as they stood that moment, and the ids of every row it posted, so
 * a void or a return can find and reverse exactly those rows and no others.
 *
 * IMMUTABLE ONCE POSTED. A posted sale is never edited: a wrong one is
 * voided ([SaleStatus.VOID], reason required, every posted row binned) and
 * rung again. The figures here are therefore a snapshot by design — a product's
 * price changing tomorrow, or a line's product being renamed, does not reach
 * back into a receipt already in a customer's hand.
 *
 * STOCK. For a customer's sale the goods lines are copied onto the khata
 * entry (entry_items), which the stock sums already read. For a walk-in sale
 * there is no khata entry, so [KhataDao.soldPerProduct] also counts
 * sale_items of walk-in sales. Never both — SaleStockTest holds it.
 *
 * [grandTotal] is what the customer owes for this ticket; [paidTotal] what was
 * handed over now; the difference went to the customer's khata (and is zero,
 * always, for a walk-in — [SaleMath.check] refuses anything else).
 */
@Entity(
    tableName = "sales",
    indices = [Index("partyId"), Index("timestamp"), Index("status")]
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Set by [KhataDao.postSale] itself ("S-000123"); the value passed in is ignored. */
    val saleNumber: String = "",

    /** Null = a walk-in with no account. No foreign key: a sale outlives a binned customer. */
    val partyId: Long? = null,

    /** The customer's name as it stood when the receipt was printed. */
    val customerName: String? = null,

    val timestamp: Long = System.currentTimeMillis(),

    /** Σ line totals before any discount. */
    val subtotal: Double,

    /** Bill-level discount in money (line discounts are inside the lines). */
    val discountAmount: Double = 0.0,

    /** Tax on (subtotal − discount), as a rate and as the money it came to. */
    val taxPercent: Double? = null,
    val taxAmount: Double = 0.0,

    /** The few paise added or dropped so the ticket ends on a whole figure. */
    val roundOff: Double = 0.0,

    val grandTotal: Double,

    /** Σ payments taken now. */
    val paidTotal: Double,

    /** One of [SaleStatus]. */
    val status: String = SaleStatus.DONE,

    val note: String? = null,

    /** The khata "I gave" entry this sale posted (customer sales only). */
    val ledgerEntryId: Long? = null,

    /** The printed invoice made from this sale, if the owner asked for one. */
    val invoiceId: Long? = null,

    /** Who rang it up (free text, as [LedgerEntry.createdBy]). */
    val createdBy: String? = null,

    /** Why and when it was voided; null while it stands. */
    val voidReason: String? = null,
    val voidedAt: Long? = null,

    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,

    /**
     * This row's identity across copies of the book (schema 36): a random id
     * given once, kept by every backup and restore, the same on the phone and
     * the computer for a row that came from one copy. What a restore compares
     * to say what would be lost, and what a merge matches rows by. Null only
     * for a moment on a row restored from an old file; restoreAll fills it.
     */
    val uid: String? = Uid.next()
) {
    /** What went to the customer's account. Zero for a walk-in by construction. */
    val creditAmount: Double get() = LineMath.round(grandTotal - paidTotal)
}

/**
 * Stored as keys, never as the translated word (the same rule as
 * [PaymentMethod]): a ticket's state must read the same in every language.
 */
object SaleStatus {
    /** Posted: stock moved, money written. */
    const val DONE = "done"
    /** Parked at the counter — nothing posted yet; the next customer is served meanwhile. */
    const val HELD = "held"
    /** Cancelled after posting; every row it wrote is in the bin. */
    const val VOID = "void"
}

/**
 * One line of a sale. Free text plus an optional product, as a goods line is
 * everywhere else; [rate] and [discount] are per the moment of the sale.
 */
@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleId"), Index("productId"), Index("billItemId")]
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    /** Where this line sits on the ticket, from 0 — the order it was rung. */
    val lineNo: Int = 0,
    val productId: Long? = null,
    val itemName: String,
    val quantity: Double,
    val unit: String? = null,
    /** Per [unit], before [discount]. */
    val rate: Double,
    /** Money off this line, not a percent. */
    val discount: Double = 0.0,
    /** The batch it came out of, where known (carries the expiry with it). */
    val billItemId: Long? = null,
    /**
     * What this unit cost the shop when it was rung up, where the book knew
     * it (the product's cost price, else its last purchase rate). A snapshot:
     * the profit on this ticket is fixed the day it was made.
     */
    val costRate: Double? = null,
    val isDeleted: Boolean = false,

    /**
     * The tax on this line (schema 36): its rate in percent, the money it came
     * to, and whether the line's price already held it. A line with no rate
     * carries no tax of its own (the ticket's own tax % may still apply).
     * Fixed at the sale, never recalculated.
     */
    val taxRate: Double? = null,
    val taxAmount: Double? = null,
    val taxIncluded: Boolean? = null
) {
    /** quantity × rate − discount, to the paisa. */
    val lineTotal: Double get() = SaleMath.lineTotal(quantity, rate, discount)
}

/**
 * One payment on a sale. A ticket may carry several — 10,000 in cash and the
 * rest on the account, or cash and a bank transfer — each posted as its own
 * row in the khata or cashbook, remembered here by id.
 */
@Entity(
    tableName = "sale_payments",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleId")]
)
data class SalePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    /** One of [PaymentMethod]'s keys. */
    val method: String,
    val amount: Double,
    /** A transaction id, cheque number, last digits — whatever the owner noted. */
    val reference: String? = null,
    /** The khata "I got" row this became (customer sales). */
    val ledgerEntryId: Long? = null,
    /** The cashbook row this became (walk-in sales). */
    val cashEntryId: Long? = null
)

/** A sale with its lines and payments, as a screen or a receipt needs it. */
data class SaleWithLines(
    val sale: Sale,
    val items: List<SaleItem>,
    val payments: List<SalePayment>
)

/** A sale as the list shows it. */
data class SaleSummary(
    val id: Long,
    val saleNumber: String,
    val customerName: String?,
    val timestamp: Long,
    val grandTotal: Double,
    val paidTotal: Double,
    val status: String,
    val itemCount: Int
)

/**
 * The arithmetic of a ticket, in one place and tested, so the counter, the
 * receipt, the khata entry and the report never disagree on what a sale came to.
 *
 * Money is rounded to the paisa at every step with HALF_UP, as [LineMath]
 * does; the only unrounded figure is the rate itself.
 */
object SaleMath {

    fun lineTotal(quantity: Double, rate: Double, discount: Double = 0.0): Double =
        BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(rate))
            .subtract(BigDecimal.valueOf(discount))
            .setScale(2, RoundingMode.HALF_UP).toDouble()

    data class Totals(
        val subtotal: Double,
        val discount: Double,
        val taxable: Double,
        /** Tax added on top of the prices (the ticket's tax %, and lines whose prices do not hold their tax). */
        val tax: Double,
        val roundOff: Double,
        val grandTotal: Double,
        /** Tax already inside prices that hold it (schema 36): part of [taxable], shown, never added again. */
        val taxIncluded: Double = 0.0
    )

    /**
     * Subtotal → bill discount → tax → round-off → grand total.
     *
     * [discount] is capped at the subtotal (a ticket cannot come to less than
     * nothing) and never below zero. [roundToWhole] drops or adds the paise
     * so the customer is asked for a whole figure, as counters do; the
     * difference is kept as [Totals.roundOff] so the books still add up.
     */
    fun totals(
        lines: List<SaleItem>,
        discount: Double = 0.0,
        taxPercent: Double? = null,
        roundToWhole: Boolean = false
    ): Totals {
        val live = lines.filter { !it.isDeleted }
        val subtotal = LineMath.round(live.sumOf { it.lineTotal })
        val disc = LineMath.round(discount.coerceIn(0.0, subtotal.coerceAtLeast(0.0)))
        val taxable = LineMath.round(subtotal - disc)
        val ownRates = live.any { (it.taxRate ?: 0.0) > 0.0 }
        val (tax, included) = if (!ownRates) {
            // The ticket's tax % on the whole, as it always was.
            (taxPercent?.takeIf { it > 0.0 }?.let { LineMath.round(taxable * it / 100.0) } ?: 0.0) to 0.0
        } else {
            val taxes = lineTaxes(live, disc, taxPercent)
            var added = 0.0
            var inside = 0.0
            live.forEachIndexed { i, l -> if (l.taxIncluded == true && (l.taxRate ?: 0.0) > 0.0) inside += taxes[i] else added += taxes[i] }
            LineMath.round(added) to LineMath.round(inside)
        }
        val exact = LineMath.round(taxable + tax)
        val grand = if (roundToWhole) Math.rint(exact) else exact
        return Totals(subtotal, disc, taxable, tax, LineMath.round(grand - exact), LineMath.round(grand), included)
    }

    /**
     * Each live line's tax (schema 36), in line order: the bill discount is
     * shared over the lines by their worth first. A line with its own rate:
     * inside its price when [SaleItem.taxIncluded] (r / (100 + r) of it), else
     * on top (r / 100). A line without one takes the ticket's [taxPercent], on
     * top. Rounded to the paisa line by line.
     */
    fun lineTaxes(lines: List<SaleItem>, discount: Double, taxPercent: Double?): List<Double> {
        val live = lines.filter { !it.isDeleted }
        val subtotal = live.sumOf { it.lineTotal }
        val share = if (subtotal > 0.0) ((subtotal - discount.coerceIn(0.0, subtotal)) / subtotal) else 0.0
        return live.map { l ->
            val base = l.lineTotal * share
            val r = l.taxRate?.takeIf { it > 0.0 }
            when {
                r != null && l.taxIncluded == true -> LineMath.round(base * r / (100.0 + r))
                r != null -> LineMath.round(base * r / 100.0)
                taxPercent != null && taxPercent > 0.0 -> LineMath.round(base * taxPercent / 100.0)
                else -> 0.0
            }
        }
    }

    /** Why a ticket cannot be posted; the screen words each one. */
    enum class Problem {
        NO_LINES, BAD_QUANTITY, BAD_RATE, BAD_DISCOUNT, BAD_PAYMENT, BAD_METHOD,
        OVERPAID, WALK_IN_UNPAID, EMPTY_NAME,
        /** The counter's own (21 Oct 2026): a figure typed that is not one, caught before it counts as nothing. */
        RATE_MISSING, UNREADABLE_DISCOUNT, BAD_TAX, UNREADABLE_PAYMENT
    }

    /** Why a posted ticket may not be voided (21 Oct 2026); null from [KhataDao.voidRefusal] when it may. */
    enum class VoidRefusal {
        /** Not a posted ticket (held, already void, binned). */
        NOT_DONE,
        /** Goods from it have come back: voiding would give them back twice. */
        HAS_RETURNS,
        /** Its day has been closed: the closed day's figures stay as counted; a return is dated today. */
        DAY_CLOSED
    }

    /**
     * Every check a sale must pass before anything is written. Returns the
     * first problem found, or null when the ticket is sound. The DAO runs this
     * again inside the transaction — the screen's check is a courtesy, the
     * DAO's is the guarantee.
     */
    fun check(
        lines: List<SaleItem>,
        payments: List<SalePayment>,
        grandTotal: Double,
        hasParty: Boolean
    ): Problem? {
        val live = lines.filter { !it.isDeleted }
        if (live.isEmpty()) return Problem.NO_LINES
        for (l in live) {
            if (l.itemName.isBlank()) return Problem.EMPTY_NAME
            if (l.quantity <= 0.0 || l.quantity.isNaN()) return Problem.BAD_QUANTITY
            if (l.rate < 0.0 || l.rate.isNaN()) return Problem.BAD_RATE
            if (l.discount < 0.0 || l.discount > l.quantity * l.rate + 0.005) return Problem.BAD_DISCOUNT
        }
        for (p in payments) {
            if (p.amount <= 0.0 || p.amount.isNaN()) return Problem.BAD_PAYMENT
            if (p.method !in PaymentMethod.ALL) return Problem.BAD_METHOD
        }
        val paid = LineMath.round(payments.sumOf { it.amount })
        if (paid > grandTotal + 0.005) return Problem.OVERPAID
        if (!hasParty && paid < grandTotal - 0.005) return Problem.WALK_IN_UNPAID
        return null
    }

    /** Σ payments, to the paisa. */
    fun paid(payments: List<SalePayment>): Double = LineMath.round(payments.sumOf { it.amount })

    /** The profit this ticket made, where every line's cost is known; null otherwise. */
    fun profit(items: List<SaleItem>, totals: Totals): Double? {
        var cost = BigDecimal.ZERO
        for (i in items.filter { !it.isDeleted }) {
            val c = i.costRate ?: return null
            cost = cost.add(BigDecimal.valueOf(c).multiply(BigDecimal.valueOf(i.quantity)))
        }
        // Tax inside the prices is the government's, not the shop's.
        return LineMath.round(totals.taxable - totals.taxIncluded - cost.toDouble())
    }
}

/**
 * The one place a sale number is built — its own series, apart from receipts
 * (RK-) and invoices (INV-): a counter ticket is neither.
 */
object SaleNumber {
    const val PREFIX = "S-"
    fun next(highest: Int): String = PREFIX + "%06d".format(Digits.FIGURES, highest + 1)
}

/**
 * Who did what (POS Phase 1; the full screen is Phase 5). Every write a
 * shopkeeper would want to answer for later — a sale, a void, a discount
 * past the usual, a restore — leaves one row here. Append-only: nothing
 * updates or deletes a row short of a restore replacing the whole book.
 */
@Entity(tableName = "audit_log", indices = [Index("timestamp"), Index(value = ["refTable", "refId"])])
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    /** Who, as the app knew it (null until staff logins exist). */
    val user: String? = null,
    /** One of [AuditAction]. */
    val action: String,
    /** Which table and row it concerns ("sales", 12). */
    val refTable: String? = null,
    val refId: Long? = null,
    /** A short, language-free note: "S-000123 12,450.00". */
    val detail: String? = null,

    /**
     * The chain (schema 36): SHA-256 of the previous row's hash and this
     * row's own fields. An edited or removed row breaks every hash after it,
     * which [AuditChain.verify] finds. It shows tampering; it does not stop a
     * determined rewrite of the whole file, and says so.
     */
    val hash: String? = null
)

object AuditAction {
    const val SALE_POSTED = "sale_posted"
    const val SALE_VOIDED = "sale_voided"
    const val SALE_HELD = "sale_held"
    const val RETURN_POSTED = "return_posted"
    const val RETURN_VOIDED = "return_voided"
    const val DISCOUNT_OVERRIDE = "discount_override"
    const val MANAGER_OVERRIDE = "manager_override"
    const val PRICE_CHANGED = "price_changed"
    const val SIGN_IN = "sign_in"
    const val RESTORE = "restore"
    const val EXPORT = "export"
    const val STOCK_COUNT = "stock_count"
    const val STOCK_ADJUSTED = "stock_adjusted"
    const val ORDER_RECEIVED = "order_received"
    // The owner's PIN and counter mode (21 Oct 2026, the critical review).
    const val OWNER_PIN_SET = "owner_pin_set"
    const val COUNTER_ON = "counter_on"
    const val COUNTER_OFF = "counter_off"
    /** Five wrong PINs in a row: the device waits. */
    const val PIN_WAIT = "pin_wait"
    const val SIGN_OUT = "sign_out"
    const val SHIFT_OPENED = "shift_opened"
    const val SHIFT_CLOSED = "shift_closed"
    const val TRANSFER_OUT = "transfer_out"
    const val TRANSFER_IN = "transfer_in"
    const val BOOK_MERGED = "book_merged"
    // What is already written, changed or removed, and the cashbook (21 Oct 2026, the critical review):
    // until then an edit or a delete left no trace of who did it.
    const val ENTRY_EDITED = "entry_edited"
    const val ENTRY_DELETED = "entry_deleted"
    const val CASH_ADDED = "cash_added"
    const val CASH_EDITED = "cash_edited"
    const val CASH_DELETED = "cash_deleted"
    const val PARTY_DELETED = "party_deleted"
    const val DAY_CLOSED = "day_closed"
}
