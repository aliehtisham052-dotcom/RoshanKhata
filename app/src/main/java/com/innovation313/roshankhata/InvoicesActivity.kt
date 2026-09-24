package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.InvoiceMath
import com.innovation313.roshankhata.data.InvoicePdfExport
import com.innovation313.roshankhata.data.InvoiceSummary
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.lineTotal
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.InvoiceAdapter
import com.innovation313.roshankhata.ui.NumberWords
import com.innovation313.roshankhata.ui.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Printable invoices — deliberately NOT the khata ledger.
 *
 * See the class doc on [com.innovation313.roshankhata.data.Invoice] for the
 * full reasoning. In one line: this screen never reads or writes a balance,
 * a customer name typed or picked here is a plain snapshot, and nothing
 * here can put money on anyone's account.
 *
 * The list only — adding one now happens on its own screen,
 * [InvoiceEditorActivity], with the actual printed design visible live
 * while it is filled in.
 */
class InvoicesActivity : AppCompatActivity() {

    private lateinit var adapter: InvoiceAdapter
    private lateinit var tvEmpty: TextView

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

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddInvoice).setOnClickListener {
            startActivity(Intent(this, InvoiceEditorActivity::class.java))
        }
        findViewById<android.widget.ImageButton>(R.id.btnInvoiceSettings).setOnClickListener {
            startActivity(Intent(this, InvoiceSettingsActivity::class.java))
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            dao.observeInvoices().collectLatest { list ->
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // ---------- Viewing / deleting a saved invoice ----------

    private fun showInvoiceActions(invoice: InvoiceSummary) {
        // No "Preview" here: tapping the row itself already opens the printed
        // design (previewPdf), so the same action in this list was a second
        // door to the same room. View stays — it is the plain itemised
        // readout for checking figures, which the printed design is not.
        val options = arrayOf(
            getString(R.string.view),
            getString(R.string.edit),
            getString(R.string.invoice_send_whatsapp),
            getString(R.string.invoice_share_pdf),
            getString(R.string.delete)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(invoice.customerName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewInvoice(invoice)
                    1 -> editInvoice(invoice)
                    2 -> sendPdfToCustomer(invoice)
                    3 -> sharePdf(invoice)
                    4 -> confirmDeleteInvoice(invoice)
                }
            }
            .show()
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
            val totals = InvoiceMath.totals(items, full.discountPercent, full.taxPercent, full.additionalChargeAmount, full.receivedAmount)

            val body = buildString {
                append(invoice.invoiceNumber)
                append(" · ")
                append(Format.dateOnly(invoice.invoiceDate))
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
                    append(getString(R.string.invoice_discount_line, Format.plain(full.discountPercent ?: 0.0), Format.money(totals.discountAmount)))
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
                append(NumberWords.rupeesInWords(this@InvoicesActivity, totals.grandTotal))
            }

            MaterialAlertDialogBuilder(this@InvoicesActivity)
                .setTitle(invoice.customerName)
                .setMessage(body)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun confirmDeleteInvoice(invoice: InvoiceSummary) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_invoice_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    AppScope.launch { dao.softDeleteInvoice(invoice.id) }.join()
                    Toast.makeText(this@InvoicesActivity, R.string.invoice_deleted, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
