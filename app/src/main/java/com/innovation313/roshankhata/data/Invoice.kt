package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A printable bill handed to a customer — deliberately NOT the khata ledger.
 *
 * THE LEDGER IS THE MONEY everywhere else in this app; an invoice is the one
 * place that rule is set aside on purpose. A shopkeeper hands out an invoice
 * for a cash sale that never touches anyone's balance, or to quote a price
 * before anything is agreed — writing either into the ledger would put a debt
 * on a customer's account that was never owed. So [customerName] is a plain
 * snapshot copied from whichever party the owner picked or typed, not a link:
 * no partyId, no foreign key, nothing here ever changes a balance, and this
 * table has no view onto khata data at all beyond that one copied name.
 *
 * [invoiceNumber] follows [SupplierBill]/[LedgerEntry]'s own pattern — read
 * and reserved inside one @Transaction (see
 * [KhataDao.saveInvoiceWithItems]) so two near-simultaneous invoices can
 * never collide on the same printed number.
 */
@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Set by [KhataDao.saveInvoiceWithItems] itself; the value passed in is ignored. */
    val invoiceNumber: String = "",

    /** A snapshot of a name — see the class doc. Never a link back to a party. */
    val customerName: String,
    val customerPhone: String? = null,

    /** Date on the document. May not be the moment it was entered. */
    val invoiceDate: Long = System.currentTimeMillis(),

    val note: String? = null,

    /** Which print design this invoice uses. Templates are numbered from 1. */
    val templateId: Int = 1,

    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/**
 * One priced line on an invoice.
 *
 * Free text, the same as a bill's [BillItem.productName] — an invoice line is
 * whatever the owner is selling today, not necessarily a [Product] this shop
 * tracks stock for. No batch, no expiry, no link to stock: those exist to
 * protect purchased-in stock against an Insecticide Act inspection, which is
 * not what a customer-facing price document is for.
 */
@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("invoiceId")]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemName: String,
    val quantity: Double,
    val unit: String? = null,
    val rate: Double,
    val isDeleted: Boolean = false
)

/**
 * Line total. An extension, not a column — same reasoning as
 * [BillItem.lineTotal]: Room would otherwise try to map it as one.
 */
val InvoiceItem.lineTotal: Double
    get() = quantity * rate

/** An invoice with enough of its items rolled up to show in a list without loading them all. */
data class InvoiceSummary(
    val id: Long,
    val invoiceNumber: String,
    val customerName: String,
    val invoiceDate: Long,
    val itemCount: Int,
    val grandTotal: Double
)

/**
 * The one place an invoice number is built, the same shape as [EntryNumber].
 * A separate sequence from receipt numbers on purpose: an invoice is not a
 * khata entry, and the two should never look like the same series to a
 * customer holding both kinds of paper.
 */
object InvoiceNumber {
    fun next(existingCount: Int): String = "INV-%06d".format(existingCount + 1)
}
