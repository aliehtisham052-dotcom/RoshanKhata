package com.innovation313.roshankhata.data

import androidx.room.ColumnInfo
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
 *
 * Since schema 40 (29 Oct 2026) the row is every customer-facing document,
 * not the invoice alone: [kind] says whether it is a quotation, a proforma
 * invoice, a sales order, a delivery challan or the invoice ([DocKind]), each
 * numbered in its own series; [status] where it stands ([DocStatus]); and
 * [sourceDocId] which document it was made from. The invoice IS the
 * document — one table, one editor, ten designs, one sync, one backup.
 */
// isDeleted+kind+invoiceDate: schema 41 (30 Oct 2026) — the Documents page's kind pills and newest-first window.
@Entity(tableName = "invoices", indices = [Index("uid"), Index("sourceDocId"), Index(value = ["isDeleted", "kind", "invoiceDate"])])
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Set by [KhataDao.saveInvoiceWithItems] itself; the value passed in is ignored. */
    val invoiceNumber: String = "",

    /** A snapshot of a name — see the class doc. Never a link back to a party. */
    val customerName: String,
    val customerPhone: String? = null,

    /** Date on the document. May not be the moment it was entered. */
    val invoiceDate: Long = System.currentTimeMillis(),

    /** When payment is expected. Null when nothing was agreed — most cash sales. */
    val dueDate: Long? = null,

    /**
     * Percent, not an amount — 5.0 means 5%. Applied to the item subtotal.
     * Null means the template that shows a tax line simply omits it, rather
     * than printing a 0% row on every invoice a shop that never charges tax
     * creates.
     */
    val taxPercent: Double? = null,

    /** Same shape as [taxPercent], applied before it. Null omits the line. */
    val discountPercent: Double? = null,

    /**
     * A single extra line — freight, labour, loading — added after tax, not
     * a percentage of anything. Both null or both set; there is no meaning
     * to an amount with no label or a label with no amount, so the UI keeps
     * them together.
     */
    val additionalChargeLabel: String? = null,
    val additionalChargeAmount: Double? = null,

    /**
     * What has actually been handed over against this invoice, if the
     * owner records it here — separate from the khata ledger, which is
     * still the only place a real payment is tracked for a customer who
     * has an account. Null means nothing was recorded, not that nothing was
     * paid; a template shows a Balance Due line only when this is set.
     */
    val receivedAmount: Double? = null,

    val note: String? = null,

    /** Which print design this invoice uses. Templates are numbered from 1. */
    val templateId: Int = 1,

    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,

    /**
     * The khata sale this invoice was made from (v22), or null for one typed
     * from scratch. Lets that entry open its existing invoice instead of
     * making a second one for the same sale. A plain number, not a foreign
     * key: the customer name on an invoice is already a snapshot, and binning
     * the entry must not touch a document that may have been handed over.
     */
    val sourceEntryId: Long? = null,

    /**
     * A discount in money rather than percent (v31, 13 Oct 2026): "Rs 50 off".
     * When set it is the discount and [discountPercent] is ignored, so an
     * invoice carries one kind or the other, never both. Never more than the
     * item subtotal. Null on every invoice made before v31.
     */
    val discountAmount: Double? = null,

    /**
     * This row's identity across copies of the book (schema 36): a random id
     * given once, kept by every backup and restore, the same on the phone and
     * the computer for a row that came from one copy. What a restore compares
     * to say what would be lost, and what a merge matches rows by. Null only
     * for a moment on a row restored from an old file; restoreAll fills it.
     */
    val uid: String? = Uid.next(),

    /**
     * Which document this row is (schema 40): one of [DocKind.saved]. Every
     * row written before is [DocKind.INVOICE]. Decides the number's series
     * (Q-, PI-, SO-, DC-, INV-), the heading on paper, and whether prices are
     * printed at all (a challan's are not).
     */
    @ColumnInfo(defaultValue = "INVOICE") val kind: String = DocKind.INVOICE,

    /**
     * Where the document stands (schema 40): [DocStatus.OPEN] until the
     * customer agrees ([DocStatus.ACCEPTED]), it is stepped on
     * ([DocStatus.CONVERTED]) or dropped ([DocStatus.CANCELLED]). Changed only
     * in the DAO, from the state that allows it (lesson 52); a converted or
     * cancelled document is never edited again.
     */
    @ColumnInfo(defaultValue = "OPEN") val status: String = DocStatus.OPEN,

    /**
     * The document this one was made from by [KhataDao.convertDocument]
     * (schema 40) — the quotation behind a sales order, the challan behind an
     * invoice — or null for one started from scratch. A plain row id like
     * [sourceEntryId], not a foreign key: the source stays on record, marked
     * converted, and binning it must not touch a document already handed over.
     * Travels as a uid in live sync and is re-pointed on a merge.
     */
    val sourceDocId: Long? = null
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
    indices = [Index("invoiceId"), Index("uid")]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemName: String,
    val quantity: Double,
    val unit: String? = null,
    val rate: Double,
    val isDeleted: Boolean = false,
    /** This row's identity across devices and backups (schema 38, live sync). */
    val uid: String? = Uid.next()
)

/**
 * Line total. An extension, not a column — same reasoning as
 * [BillItem.lineTotal]: Room would otherwise try to map it as one.
 */
val InvoiceItem.lineTotal: Double
    get() = quantity * rate

/**
 * Subtotal, discount, tax, additional charge, and the final figure — one
 * place, so the PDF, the list screen's SQL, and the plain-text view all
 * agree on what "the total" means. Discount applies to the item subtotal;
 * tax applies after discount; the additional charge (freight, labour) is
 * added last, after tax, the ordinary invoicing convention for a charge
 * that is not itself part of what was sold.
 *
 * [received] and [balanceDue] are about payment, not pricing — kept in the
 * same place anyway because a template showing one total figure needs both
 * halves computed the same consistent way, not derived separately by
 * whichever screen happens to need it.
 */
data class InvoiceTotals(
    val subtotal: Double,
    val discountAmount: Double,
    val taxAmount: Double,
    val additionalCharge: Double,
    val grandTotal: Double,
    val received: Double,
    val balanceDue: Double
)

object InvoiceMath {
    fun totals(
        items: List<InvoiceItem>,
        discountPercent: Double?,
        taxPercent: Double?,
        additionalChargeAmount: Double? = null,
        receivedAmount: Double? = null,
        discountAmount: Double? = null
    ): InvoiceTotals {
        val subtotal = items.sumOf { it.lineTotal }
        val discountAmount = if (discountAmount != null) {
            discountAmount.coerceIn(0.0, subtotal.coerceAtLeast(0.0))
        } else {
            discountPercent?.takeIf { it != 0.0 }?.let { subtotal * it / 100.0 } ?: 0.0
        }
        val afterDiscount = subtotal - discountAmount
        val taxAmount = taxPercent?.takeIf { it != 0.0 }?.let { afterDiscount * it / 100.0 } ?: 0.0
        val charge = additionalChargeAmount ?: 0.0
        val grandTotal = afterDiscount + taxAmount + charge
        val received = receivedAmount ?: 0.0
        return InvoiceTotals(
            subtotal, discountAmount, taxAmount, charge, grandTotal,
            received, grandTotal - received
        )
    }
}

/** This invoice's totals over [items], with every pricing field it carries. */
fun Invoice.totals(items: List<InvoiceItem>): InvoiceTotals = InvoiceMath.totals(
    items, discountPercent, taxPercent, additionalChargeAmount, receivedAmount, discountAmount
)

// Invoice.discountLabel() formats with the app's Format, so it lives in the
// app (data/InvoiceLabels.kt), KMP Phase 3.

/** An invoice with enough of its items rolled up to show in a list without loading them all. */
data class InvoiceSummary(
    val id: Long,
    val invoiceNumber: String,
    val customerName: String,
    val invoiceDate: Long,
    val itemCount: Int,
    val grandTotal: Double,
    /** Which document and where it stands (schema 40) — the list says "Quotation · Accepted", not only a number. */
    val kind: String = DocKind.INVOICE,
    val status: String = DocStatus.OPEN
)

/**
 * The one place an invoice number is built, the same shape as [EntryNumber].
 * A separate sequence from receipt numbers on purpose: an invoice is not a
 * khata entry, and the two should never look like the same series to a
 * customer holding both kinds of paper.
 */
object InvoiceNumber {
    /** The next invoice number — [DocKind.INVOICE]'s series, as every caller before schema 40 asked for it. */
    fun next(existingCount: Int): String = next(DocKind.INVOICE, existingCount)

    /**
     * The next number in [kind]'s own series (schema 40): its prefix
     * ([DocKind.prefix]), six digits, and this device's letter while the book
     * syncs ([NumberMark]) — Q-000001, SO-000013-B. A quotation and an
     * invoice never share a number: a customer holding both sees two series.
     */
    fun next(kind: String, existingCount: Int): String =
        DocKind.prefix(kind) + "%06d".format(Digits.FIGURES, existingCount + 1) + NumberMark.suffix()
}
