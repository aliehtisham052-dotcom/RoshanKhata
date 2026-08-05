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

        adapter = InvoiceAdapter { invoice -> showInvoiceActions(invoice) }
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
        val options = arrayOf(
            getString(R.string.view),
            getString(R.string.edit),
            getString(R.string.invoice_share_pdf),
            getString(R.string.delete)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(invoice.customerName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewInvoice(invoice)
                    1 -> editInvoice(invoice)
                    2 -> sharePdf(invoice)
                    3 -> confirmDeleteInvoice(invoice)
                }
            }
            .show()
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
            val full = dao.getInvoice(invoice.id) ?: return@launch
            val items = dao.invoiceItems(invoice.id)

            val file = withContext(Dispatchers.IO) {
                InvoicePdfExport.build(this@InvoicesActivity, full, items)
            }

            if (file == null) {
                Toast.makeText(this@InvoicesActivity, R.string.invoice_pdf_failed, Toast.LENGTH_LONG).show()
                return@launch
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@InvoicesActivity, "$packageName.fileprovider", file
            )
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
