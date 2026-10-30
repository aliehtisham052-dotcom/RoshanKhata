package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.ui.ChoiceSheet
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.totals
import com.innovation313.roshankhata.data.DocActions
import com.innovation313.roshankhata.data.DocKind
import com.innovation313.roshankhata.data.DocRefused
import com.innovation313.roshankhata.data.DocStatus
import com.innovation313.roshankhata.data.WordIds
import com.innovation313.roshankhata.data.InvoicePdfExport
import com.innovation313.roshankhata.data.InvoiceSummary
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.lineTotal
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.InvoiceAdapter
import com.innovation313.roshankhata.ui.NumberWords
import com.innovation313.roshankhata.ui.amountInWords
import com.innovation313.roshankhata.ui.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Documents screen (29 Oct 2026, schema 40; "Invoices" before): every
 * customer-facing document — quotation, proforma, sales order, delivery
 * challan, invoice — in one list under kind pills, each row its number, its
 * status tag and its total. Printable documents, deliberately NOT the khata
 * ledger: see the class doc on [com.innovation313.roshankhata.data.Invoice].
 * In one line: this screen never reads or writes a balance, a customer name
 * typed or picked here is a plain snapshot, and nothing here can put money on
 * anyone's account.
 *
 * The list only — adding one happens on its own screen,
 * [InvoiceEditorActivity], asked its kind first (Invoice by default). The
 * three-dot actions follow the document's status ([DocActions], the same rule
 * as the computer's): Accept, Convert to the next kind, Mark as Cancelled,
 * Edit and Delete while it is live; a converted or cancelled document is read,
 * printed and sent, never changed.
 */
class InvoicesActivity : BaseActivity() {

    private lateinit var adapter: InvoiceAdapter
    private lateinit var tvEmpty: TextView

    /** The kind pill: null is every kind. */
    private val kindShown = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoices)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.tvNoInvoices)

        // Row tap opens the invoice itself; the actions live behind the
        // three-dot button. The owner's point: opening a bill should show
        // the bill, not a menu about it.
        adapter = InvoiceAdapter(
            onOpen = { invoice -> previewPdf(invoice) },
            onMore = { invoice -> showInvoiceActions(invoice) }
        )
        val rv: RecyclerView = findViewById(R.id.rvInvoices)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        // The newest page first, more as the owner scrolls (23 Oct 2026, the review: every row was read).
        com.innovation313.roshankhata.ui.GrowingList.attach(rv, listLimit) { listShown }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddInvoice).setOnClickListener { askKindThenAdd() }
        buildKindPills()
        findViewById<android.widget.ImageButton>(R.id.btnInvoiceSettings).setOnClickListener {
            startActivity(Intent(this, InvoiceSettingsActivity::class.java))
        }

        observe()
    }

    private val listLimit = kotlinx.coroutines.flow.MutableStateFlow(com.innovation313.roshankhata.data.ListWindow.PAGE)
    private var listShown = 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observe() {
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(listLimit, kindShown) { limit, kind -> limit to kind }
                .flatMapLatest { (limit, kind) -> dao.observeDocPage(kind, limit) }
                .collectLatest { list ->
                    listShown = list.size
                    adapter.submit(list)
                    // The long "no invoices yet" invitation only when the book has none at all; a kind pill
                    // with nothing under it says so in one line.
                    tvEmpty.setText(if (kindShown.value == null) R.string.no_invoices else R.string.no_entries_yet)
                    tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    /** The kinds the pill row offers, in the chain's order (the proforma is under All and in the Add picker). */
    private val pillKinds: List<String?> = listOf(null, DocKind.QUOTATION, DocKind.SALES_ORDER, DocKind.CHALLAN, DocKind.INVOICE)

    private fun kindWord(kind: String): String = getString(WordIds.string(DocKind.wordKey(kind)))

    /** All · Quotation · Sales order · Challan · Invoice, the same words as the computer's pills. */
    private fun buildKindPills() {
        val group = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipKinds)
        pillKinds.forEach { k ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, group, false) as com.google.android.material.chip.Chip
            chip.id = View.generateViewId()
            chip.text = if (k == null) getString(R.string.range_all) else kindWord(k)
            chip.tag = k ?: ""
            chip.isChecked = k == kindShown.value
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val c = ids.firstOrNull()?.let { g.findViewById<com.google.android.material.chip.Chip>(it) } ?: return@setOnCheckedStateChangeListener
            kindShown.value = (c.tag as String).ifEmpty { null }
        }
    }

    /** Add asks which document first — the invoice marked, the way most are made — then opens the form with it. */
    private fun askKindThenAdd() {
        val kinds = DocKind.saved
        var picked = kinds.indexOf(DocKind.INVOICE)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.doc_pick_kind)
            .setSingleChoiceItems(kinds.map { kindWord(it) }.toTypedArray(), picked) { _, which -> picked = which }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                startActivity(Intent(this, InvoiceEditorActivity::class.java).putExtra(InvoiceEditorActivity.EXTRA_KIND, kinds[picked]))
            }
            .show()
    }

    // ---------- Viewing / deleting a saved invoice ----------

    private fun showInvoiceActions(invoice: InvoiceSummary) {
        // No "Preview" here: tapping the row itself already opens the printed
        // design (previewPdf), so the same action in this list was a second
        // door to the same room. View stays — it is the plain itemised
        // readout for checking figures, which the printed design is not.
        // What the status allows comes from DocActions, the computer's rule too: a converted or
        // cancelled document offers no Edit, Convert, Cancel or Delete.
        val live = DocActions.canEdit(invoice.status)
        val target = DocActions.convertTarget(invoice.kind, invoice.status)
        val options = ArrayList<Pair<ChoiceSheet.Option, () -> Unit>>()
        options += ChoiceSheet.view(getString(R.string.view)) to { viewInvoice(invoice) }
        if (live) options += ChoiceSheet.edit(getString(R.string.edit)) to { editInvoice(invoice) }
        options += ChoiceSheet.whatsapp(getString(R.string.invoice_send_whatsapp)) to { sendPdfToCustomer(invoice) }
        options += ChoiceSheet.pdf(getString(R.string.invoice_share_pdf)) to { sharePdf(invoice) }
        if (DocActions.canAccept(invoice.kind, invoice.status)) options += ChoiceSheet.done(getString(R.string.doc_accept)) to { acceptDocument(invoice) }
        if (target != null) options += ChoiceSheet.Option(getString(R.string.doc_convert_to, kindWord(target)), icon = R.drawable.ic_arrow_forward, iconTint = R.color.tile_invoice_fg, tile = R.color.tile_invoice_bg) to { convertDocument(invoice, target) }
        if (DocActions.canCancel(invoice.status)) options += ChoiceSheet.stop(getString(R.string.mark_cancelled)) to { confirmCancelDocument(invoice) }
        if (live) options += ChoiceSheet.delete(getString(R.string.delete)) to { confirmDeleteInvoice(invoice) }
        ChoiceSheet.show(this, invoice.customerName, options.map { it.first }) { which -> options.getOrNull(which)?.second?.invoke() }
    }

    // ---------- Where a document goes next: accept, convert, cancel ----------

    /** A refused document write in the owner's words: doc_locked for one no longer live, else the app's "not saved". */
    private fun docFailText(e: Throwable): String {
        val key = (e as? DocRefused)?.let { DocActions.refusalWordKey(it.why) }
        return if (key != null) getString(WordIds.string(key)) else getString(R.string.action_failed_detail, e.message ?: e.javaClass.simpleName)
    }

    private fun tell(text: String) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.documents_title).setMessage(text).setPositiveButton(R.string.ok, null).show()
    }

    /** OPEN → ACCEPTED (a quotation, a proforma, a sales order): changing the book, so the book's own gate. */
    private fun acceptDocument(invoice: InvoiceSummary) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { acceptDocument(invoice) }) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { dao.acceptDocument(invoice.id) } }
                .onFailure { tell(docFailText(it)) }
        }
    }

    /** The step on: the DAO copies the document into [to] and marks this one converted; the new one opens. */
    private fun convertDocument(invoice: InvoiceSummary, to: String) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { convertDocument(invoice, to) }) return
        lifecycleScope.launch {
            val made = withContext(Dispatchers.IO) { runCatching { dao.convertDocument(invoice.id, to) } }
            val id = made.getOrElse { tell(docFailText(it)); return@launch }
            // The new document is of another kind: show every kind so it is in the list, then open it.
            val group = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipKinds)
            for (i in 0 until group.childCount) {
                val ch = group.getChildAt(i) as com.google.android.material.chip.Chip
                if (ch.tag == "") group.check(ch.id)
            }
            kindShown.value = null
            val file = buildPdfFile(id) ?: return@launch
            com.innovation313.roshankhata.ui.PdfShare.open(this@InvoicesActivity, file)
        }
    }

    /** Cancel keeps the document on record under its number (delete nothing, reverse it); asked first, in red. */
    private fun confirmCancelDocument(invoice: InvoiceSummary) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmCancelDocument(invoice) }) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.mark_cancelled) + " · " + invoice.invoiceNumber)
            .setMessage(getString(R.string.doc_cancel_confirm, invoice.invoiceNumber))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.mark_cancelled) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { runCatching { dao.cancelDocument(invoice.id) } }
                        .onFailure { tell(docFailText(it)) }
                }
            }
            .show()
            .also { d -> d.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.bal_owed_to_me)) }
    }

    /**
     * The invoice, to the customer it was written for, without a contact hunt.
     *
     * The generic share sheet asks the owner to pick an app and then pick the
     * person — for an invoice that already carries that person's number, both
     * questions are already answered, and picking the wrong name from a
     * contact list sends someone else's bill to them.
     *
     * WhatsApp exposes no documented way to attach a file to a named chat.
     * The undocumented "jid" extra usually opens the right one; when it does
     * not, WhatsApp falls back to its own picker with the PDF already
     * attached, which is still one step fewer than the share sheet. If
     * WhatsApp is not installed at all the plain chooser opens instead, so
     * the invoice can still go out by email or Bluetooth.
     */
    private fun sendPdfToCustomer(invoice: InvoiceSummary) {
        lifecycleScope.launch {
            val full = dao.getInvoice(invoice.id) ?: return@launch

            val number = full.customerPhone?.let { Reminder.toWhatsAppNumber(it) }
            if (number.isNullOrBlank()) {
                // No number on this invoice — say why rather than opening
                // WhatsApp at nobody.
                Toast.makeText(this@InvoicesActivity, R.string.no_phone_number, Toast.LENGTH_LONG).show()
                return@launch
            }

            val uri = buildPdfUri(full.id) ?: return@launch

            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, invoice.invoiceNumber)
                putExtra("jid", "$number@s.whatsapp.net")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(send)
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(
                    this@InvoicesActivity, R.string.whatsapp_not_installed, Toast.LENGTH_LONG
                ).show()
                sharePdf(invoice)
            }
        }
    }

    /** Builds the PDF file itself, or null after reporting why. */
    private suspend fun buildPdfFile(invoiceId: Long): java.io.File? {
        val full = dao.getInvoice(invoiceId) ?: return null
        val items = dao.invoiceItems(invoiceId)

        val file = withContext(Dispatchers.IO) {
            InvoicePdfExport.build(this@InvoicesActivity, full, items)
        }
        if (file == null) {
            Toast.makeText(this@InvoicesActivity, R.string.invoice_pdf_failed, Toast.LENGTH_LONG).show()
        }
        return file
    }

    /** Builds the PDF and returns a shareable uri, or null after reporting why. */
    private suspend fun buildPdfUri(invoiceId: Long): android.net.Uri? {
        val file = buildPdfFile(invoiceId) ?: return null
        return androidx.core.content.FileProvider.getUriForFile(
            this@InvoicesActivity, "$packageName.fileprovider", file
        )
    }

    /**
     * The invoice in its printed design, opened to read — not sent anywhere.
     *
     * Built from the design already saved on the invoice, exactly as sharing
     * does, so what is previewed is what the customer receives. Nothing here
     * writes: the file lands in the share cache and the invoice row is not
     * touched.
     */
    private fun previewPdf(invoice: InvoiceSummary) {
        lifecycleScope.launch {
            val file = buildPdfFile(invoice.id) ?: return@launch
            com.innovation313.roshankhata.ui.PdfShare.open(this@InvoicesActivity, file)
        }
    }

    private fun editInvoice(invoice: InvoiceSummary) {
        // A converted or cancelled document is never edited (the editor and the DAO refuse it too).
        if (!DocActions.canEdit(invoice.status)) { tell(getString(R.string.doc_locked)); return }
        startActivity(
            Intent(this, InvoiceEditorActivity::class.java)
                .putExtra(InvoiceEditorActivity.EXTRA_INVOICE_ID, invoice.id)
        )
    }

    /**
     * Shares the invoice in the design it was already saved with.
     *
     * This used to open a template picker first, which the owner rightly
     * called pointless: the design is chosen on step 3 while the invoice is
     * being written, so asking again at share time is asking a question
     * already answered — and worse, it made picking a different one here
     * quietly rewrite the saved invoice's design as a side effect of
     * sharing it. Changing the design now belongs where it always belonged,
     * in Edit, which reopens that same step 3 carousel.
     */
    private fun sharePdf(invoice: InvoiceSummary) {
        lifecycleScope.launch {
            val uri = buildPdfUri(invoice.id) ?: return@launch

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, invoice.invoiceNumber)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.invoice_share_pdf)))
        }
    }

    /**
     * A plain itemised readout, not yet the printed design — good enough to
     * check an invoice's numbers without generating a file for it.
     */
    private fun viewInvoice(invoice: InvoiceSummary) {
        lifecycleScope.launch {
            val full = dao.getInvoice(invoice.id) ?: return@launch
            val items = dao.invoiceItems(invoice.id)
            val totals = full.totals(items)

            val source = full.sourceDocId?.let { dao.getInvoice(it) }
            val body = buildString {
                // The kind and where it stands, then the number and the day.
                append(kindWord(full.kind))
                if (full.status != DocStatus.OPEN) { append(" · "); append(getString(WordIds.string(DocStatus.wordKey(full.status)))) }
                append("\n")
                append(invoice.invoiceNumber)
                append(" · ")
                append(Format.dateOnly(invoice.invoiceDate))
                source?.let {
                    append("\n")
                    append(getString(R.string.doc_converted_from, it.invoiceNumber))
                }
                full.dueDate?.let {
                    append("\n")
                    append(getString(R.string.due_date_set, Format.dateOnly(it)))
                }
                append("\n\n")
                items.forEach { item ->
                    append(item.itemName)
                    append("  —  ")
                    append(Format.qty(item.quantity, item.unit))
                    append(" × ")
                    append(Format.money(item.rate))
                    append("  =  ")
                    append(Format.money(item.lineTotal))
                    append("\n")
                }
                append("\n")
                append(getString(R.string.invoice_subtotal, Format.money(totals.subtotal)))
                if (totals.discountAmount > 0) {
                    append("\n")
                    append(
                        if (full.discountAmount != null) getString(R.string.invoice_discount_amount_line, Format.money(totals.discountAmount))
                        else getString(R.string.invoice_discount_line, Format.plain(full.discountPercent ?: 0.0), Format.money(totals.discountAmount))
                    )
                }
                if (totals.taxAmount > 0) {
                    append("\n")
                    append(getString(R.string.invoice_tax_line, Format.plain(full.taxPercent ?: 0.0), Format.money(totals.taxAmount)))
                }
                if (totals.additionalCharge > 0) {
                    append("\n")
                    append(full.additionalChargeLabel.orEmpty())
                    append(": ")
                    append(Format.money(totals.additionalCharge))
                }
                append("\n")
                append(getString(R.string.invoice_total, Format.money(totals.grandTotal)))
                if (full.receivedAmount != null) {
                    append("\n")
                    append(getString(R.string.invoice_received_line, Format.money(totals.received)))
                    append("\n")
                    append(getString(R.string.invoice_balance_due_line, Format.money(totals.balanceDue)))
                }
                append("\n")
                append(NumberWords.amountInWords(this@InvoicesActivity, totals.grandTotal))
            }

            val dialog = MaterialAlertDialogBuilder(this@InvoicesActivity)
                .setTitle(invoice.customerName)
                .setMessage(body)
                .setPositiveButton(R.string.ok, null)
            // The document this one was made from opens from here (the "From Q-…" link).
            source?.let { from ->
                dialog.setNeutralButton(getString(R.string.doc_converted_from, from.invoiceNumber)) { _, _ ->
                    lifecycleScope.launch {
                        val file = buildPdfFile(from.id) ?: return@launch
                        com.innovation313.roshankhata.ui.PdfShare.open(this@InvoicesActivity, file)
                    }
                }
            }
            dialog.show()
        }
    }

    private fun confirmDeleteInvoice(invoice: InvoiceSummary) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmDeleteInvoice(invoice) }) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_invoice_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    var done = false
                    val job = AppScope.launch { done = dao.deleteDocument(invoice.id) }
                    job.join()
                    // A failed write was said already ("not saved"); no "deleted" after it (23 Oct 2026).
                    if (job.isCancelled) return@launch
                    // Refused in the DAO: converted or cancelled meanwhile (another device, a stale menu).
                    Toast.makeText(this@InvoicesActivity, if (done) R.string.invoice_deleted else R.string.doc_locked, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
