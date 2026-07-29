package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoiceMath
import com.innovation313.roshankhata.data.InvoicePdfExport
import com.innovation313.roshankhata.data.InvoiceSummary
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.lineTotal
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.InvoiceAdapter
import com.innovation313.roshankhata.ui.NumberWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Printable invoices — deliberately NOT the khata ledger.
 *
 * See the class doc on [Invoice] for the full reasoning. In one line: this
 * screen never reads or writes a balance, a customer name typed or picked
 * here is a plain snapshot, and nothing here can put money on anyone's
 * account. Phase 1 only — saving, listing, viewing the items, and deleting.
 * The actual printed PDF (the 10 template designs already agreed) and
 * WhatsApp sharing are later work, once this foundation is proven.
 */
class InvoicesActivity : AppCompatActivity() {

    private lateinit var adapter: InvoiceAdapter
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var partyNames: List<String> = emptyList()

    /** Items being collected for the invoice currently being entered. */
    private val pendingItems = mutableListOf<InvoiceItem>()

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
            startNewInvoice()
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
        lifecycleScope.launch {
            partyNames = dao.allPartyNamesForInvoice()
        }
    }

    // ---------- Entering an invoice ----------

    private fun startNewInvoice() {
        pendingItems.clear()

        val view = layoutInflater.inflate(R.layout.dialog_add_invoice, null)
        val etCustomer: AutoCompleteTextView = view.findViewById(R.id.etInvoiceCustomer)
        val rgTemplate: RadioGroup = view.findViewById(R.id.rgInvoiceTemplate)
        val etPhone: EditText = view.findViewById(R.id.etInvoicePhone)
        val btnDate: MaterialButton = view.findViewById(R.id.btnInvoiceDate)
        val btnDue: MaterialButton = view.findViewById(R.id.btnInvoiceDueDate)
        val etDiscount: EditText = view.findViewById(R.id.etInvoiceDiscount)
        val etTax: EditText = view.findViewById(R.id.etInvoiceTax)
        val etNote: EditText = view.findViewById(R.id.etInvoiceNote)

        etCustomer.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, partyNames)
        )

        fun selectedTemplateId(): Int =
            if (rgTemplate.checkedRadioButtonId == R.id.rbTemplateThermal) 10 else 1

        var invoiceDate = System.currentTimeMillis()
        var dueDate: Long? = null

        btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(invoiceDate))
        btnDate.setOnClickListener {
            pickDate(invoiceDate) { picked ->
                invoiceDate = picked
                btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(picked))
            }
        }

        btnDue.setOnClickListener {
            pickDate(dueDate ?: invoiceDate) { picked ->
                dueDate = picked
                btnDue.text = getString(R.string.due_date_set, Format.dateOnly(picked))
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_invoice)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.add_item) { _, _ ->
                collectItems(
                    customerName = etCustomer.text.toString().trim(),
                    customerPhone = etPhone.text.toString().trim().ifEmpty { null },
                    invoiceDate = invoiceDate,
                    dueDate = dueDate,
                    discountPercent = etDiscount.text.toString().trim().toDoubleOrNull(),
                    taxPercent = etTax.text.toString().trim().toDoubleOrNull(),
                    templateId = selectedTemplateId(),
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .setPositiveButton(R.string.save) { _, _ ->
                saveInvoice(
                    customerName = etCustomer.text.toString().trim(),
                    customerPhone = etPhone.text.toString().trim().ifEmpty { null },
                    invoiceDate = invoiceDate,
                    dueDate = dueDate,
                    discountPercent = etDiscount.text.toString().trim().toDoubleOrNull(),
                    taxPercent = etTax.text.toString().trim().toDoubleOrNull(),
                    templateId = selectedTemplateId(),
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .show()
    }

    /** Add items one at a time, then save the invoice with all of them. */
    private fun collectItems(
        customerName: String,
        customerPhone: String?,
        invoiceDate: Long,
        dueDate: Long?,
        discountPercent: Double?,
        taxPercent: Double?,
        templateId: Int,
        note: String?
    ) {
        showAddItemDialog { item ->
            pendingItems.add(item)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.item_added)
                .setMessage(getString(R.string.items_count, pendingItems.size))
                .setNeutralButton(R.string.add_item) { _, _ ->
                    collectItems(customerName, customerPhone, invoiceDate, dueDate, discountPercent, taxPercent, templateId, note)
                }
                .setPositiveButton(R.string.save) { _, _ ->
                    saveInvoice(customerName, customerPhone, invoiceDate, dueDate, discountPercent, taxPercent, templateId, note)
                }
                .show()
        }
    }

    private fun showAddItemDialog(onDone: (InvoiceItem) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_add_invoice_item, null)
        val etName: EditText = view.findViewById(R.id.etInvoiceItemName)
        val etQty: EditText = view.findViewById(R.id.etInvoiceItemQty)
        val etUnit: AutoCompleteTextView = view.findViewById(R.id.etInvoiceItemUnit)
        val etRate: EditText = view.findViewById(R.id.etInvoiceItemRate)

        etUnit.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, resources.getStringArray(R.array.units))
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_item)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = etName.text.toString().trim()
                val qty = etQty.text.toString().trim().toDoubleOrNull()
                val rate = etRate.text.toString().trim().toDoubleOrNull()

                if (name.isEmpty() || qty == null || qty <= 0 || rate == null || rate < 0) {
                    Toast.makeText(this, R.string.invoice_item_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                onDone(
                    InvoiceItem(
                        invoiceId = 0,
                        itemName = name,
                        quantity = qty,
                        unit = etUnit.text.toString().trim().ifEmpty { null },
                        rate = rate
                    )
                )
            }
            .show()
    }

    /**
     * Runs on [AppScope], same reasoning as every other multi-step creation
     * write in this app (see [BillsActivity.saveBill]) — leaving this screen
     * the instant after tapping Save must not cut the invoice or its items
     * off half-written.
     */
    private fun saveInvoice(
        customerName: String,
        customerPhone: String?,
        invoiceDate: Long,
        dueDate: Long?,
        discountPercent: Double?,
        taxPercent: Double?,
        templateId: Int,
        note: String?
    ) {
        if (customerName.isEmpty()) {
            Toast.makeText(this, R.string.invoice_customer_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (pendingItems.isEmpty()) {
            Toast.makeText(this, R.string.invoice_needs_item, Toast.LENGTH_SHORT).show()
            return
        }

        val invoice = Invoice(
            customerName = customerName,
            customerPhone = customerPhone,
            invoiceDate = invoiceDate,
            dueDate = dueDate,
            discountPercent = discountPercent,
            taxPercent = taxPercent,
            templateId = templateId,
            note = note
        )
        val items = pendingItems.toList()

        AppScope.launch {
            dao.saveInvoiceWithItems(invoice, items)
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this@InvoicesActivity, R.string.invoice_saved, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------- Viewing / deleting a saved invoice ----------

    private fun showInvoiceActions(invoice: InvoiceSummary) {
        val options = arrayOf(getString(R.string.view), getString(R.string.invoice_share_pdf), getString(R.string.delete))
        MaterialAlertDialogBuilder(this)
            .setTitle(invoice.customerName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewInvoice(invoice)
                    1 -> chooseTemplateAndShare(invoice)
                    2 -> confirmDeleteInvoice(invoice)
                }
            }
            .show()
    }

    /**
     * Only two of the ten finalised templates exist so far — see the class
     * doc — so this is a real choice for now, not a formality. Picking one
     * updates the invoice's own templateId, so re-sharing later remembers
     * what was chosen rather than asking again from scratch.
     */
    private fun chooseTemplateAndShare(invoice: InvoiceSummary) {
        val templates = arrayOf(
            getString(R.string.invoice_template_teal),
            getString(R.string.invoice_template_thermal)
        )
        val templateIds = intArrayOf(1, 10)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.invoice_choose_template)
            .setItems(templates) { _, which ->
                sharePdf(invoice, templateIds[which])
            }
            .show()
    }

    private fun sharePdf(invoice: InvoiceSummary, templateId: Int) {
        lifecycleScope.launch {
            val full = dao.getInvoice(invoice.id) ?: return@launch
            val items = dao.invoiceItems(invoice.id)

            if (full.templateId != templateId) {
                dao.updateInvoice(full.copy(templateId = templateId))
            }

            val file = withContext(Dispatchers.IO) {
                InvoicePdfExport.build(this@InvoicesActivity, full.copy(templateId = templateId), items)
            }

            if (file == null) {
                Toast.makeText(this@InvoicesActivity, R.string.invoice_pdf_failed, Toast.LENGTH_LONG).show()
                return@launch
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@InvoicesActivity, "$packageName.fileprovider", file
            )
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, invoice.invoiceNumber)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(share, getString(R.string.invoice_share_pdf)))
        }
    }

    /**
     * A plain itemised readout, not yet the printed design — the 10 template
     * PDFs are the next phase, once this data layer is proven. Good enough to
     * check an invoice is right before that work starts.
     */
    private fun viewInvoice(invoice: InvoiceSummary) {
        lifecycleScope.launch {
            val full = dao.getInvoice(invoice.id) ?: return@launch
            val items = dao.invoiceItems(invoice.id)
            val totals = InvoiceMath.totals(items, full.discountPercent, full.taxPercent)

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
                append("\n")
                append(getString(R.string.invoice_total, Format.money(totals.grandTotal)))
                append("\n")
                append(NumberWords.rupeesInWords(totals.grandTotal))
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
                    dao.softDeleteInvoice(invoice.id)
                    Toast.makeText(this@InvoicesActivity, R.string.invoice_deleted, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
