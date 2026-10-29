package com.innovation313.roshankhata.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.innovation313.roshankhata.data.DocActions
import com.innovation313.roshankhata.data.DocKind
import com.innovation313.roshankhata.data.DocRefused
import com.innovation313.roshankhata.data.DocStatus
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.NoteDocument
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.DocumentPrint
import com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import java.awt.print.Printable

/**
 * The Documents page's words and sheets on the computer (29 Oct 2026, the
 * documents' second part): the kind pills, the status tag, how a refusal is
 * said, and the two print-only notes drawn through the invoice designs.
 */

/** The word for a document's kind ([DocKind.wordKey]) in the app's language. */
@Composable
@ReadOnlyComposable
fun docKindWord(kind: String): String = word(DocKind.wordKey(kind))

/** The word for a document's status ([DocStatus.wordKey]) in the app's language. */
@Composable
@ReadOnlyComposable
fun docStatusWord(status: String): String = word(DocStatus.wordKey(status))

/**
 * The status as a small tag beside a document's number — none for OPEN (the
 * ordinary state says nothing), green for ACCEPTED, grey for CONVERTED (it
 * lives on as its next step), red for CANCELLED.
 */
@Composable
fun DocStatusTag(status: String) {
    val c = LocalRoshanColors.current
    when (status) {
        DocStatus.ACCEPTED -> Tag(docStatusWord(status), Look.accent, Look.accentTint)
        DocStatus.CONVERTED -> Tag(docStatusWord(status), c.inkSoft, Look.subtle)
        DocStatus.CANCELLED -> Tag(docStatusWord(status), c.owedToMe, Look.getTint)
        else -> Unit
    }
}

/**
 * The kinds the pill row offers, in the chain's order: All, then quotation,
 * sales order, challan and invoice. The proforma is left out of the row to
 * keep it short on a laptop (it is in the Add picker and shows under All).
 */
val DOC_PILLS: List<String?> = listOf(null, DocKind.QUOTATION, DocKind.SALES_ORDER, DocKind.CHALLAN, DocKind.INVOICE)

/**
 * A document write's refusal in the owner's words: a document no longer live
 * is [W.doc_locked] (change the new one); anything else is the app's generic
 * "not saved" with what the data layer said, as every other failed write.
 */
fun docFailText(words: Words, e: Throwable): String {
    val refused = e as? DocRefused
    val key = refused?.let { DocActions.refusalWordKey(it.why) }
    return if (key != null) words.text(key) else failText(words, e)
}

/**
 * The two print-only notes as the sheet the preview shows, saves and prints
 * (StatementPrint.show): the shop's chosen design through the phone's
 * drawing code, else the plain layout. Nothing is written — a credit note is
 * its return and a debit note its khata entry ([NoteDocument]).
 */
object NoteSheets {
    /** The design a note is drawn in: the shop's own default is 1; a note has no saved row to carry one. */
    const val TEMPLATE = 1

    /** A customer's return ([returnId]) as a credit note, or null when the return is gone. */
    suspend fun creditNote(dao: KhataDao, returnId: Long, designs: Context, plain: (Invoice, List<InvoiceItem>) -> Printable): Pair<Printable, String>? {
        val ret = dao.getReturn(returnId)?.takeIf { !it.isDeleted } ?: return null
        val items = dao.returnItems(returnId)
        val party = ret.partyId?.let { dao.getParty(it) }
        val (inv, lines) = NoteDocument.creditNote(ret, items, party)
        return sheet(inv.copy(templateId = TEMPLATE), lines, designs, plain) to inv.invoiceNumber
    }

    /** Goods sent back to a supplier (the "I gave" [entry] with its [lines]) as a debit note. */
    suspend fun debitNote(dao: KhataDao, entry: LedgerEntry, lines: List<EntryItem>, designs: Context, plain: (Invoice, List<InvoiceItem>) -> Printable): Pair<Printable, String> {
        val party = dao.getParty(entry.partyId)
        val (inv, rows) = NoteDocument.debitNote(entry, lines, party)
        return sheet(inv.copy(templateId = TEMPLATE), rows, designs, plain) to inv.invoiceNumber
    }

    private fun sheet(inv: Invoice, lines: List<InvoiceItem>, designs: Context, plain: (Invoice, List<InvoiceItem>) -> Printable): Printable =
        InvoiceDesigns.pages(designs, inv, lines)?.let { DocumentPrint(it) } ?: plain(inv, lines)
}
