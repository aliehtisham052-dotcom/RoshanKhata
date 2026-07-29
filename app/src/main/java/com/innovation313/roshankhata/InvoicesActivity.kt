package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
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
import com.innovation313.roshankhata.data.InvoiceSummary
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.lineTotal
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.InvoiceAdapter
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
        val etPhone: EditText = view.findViewById(R.id.etInvoicePhone)
        val btnDate: MaterialButton = view.findViewById(R.id.btnInvoiceDate)
        val etNote: EditText = view.findViewById(R.id.etInvoiceNote)

        etCustomer.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, partyNames)
        )

        var invoiceDate = System.currentTimeMillis()
        btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(invoiceDate))
        btnDate.setOnClickListener {
            pickDate(invoiceDate) { picked ->
                invoiceDate = picked
                btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(picked))
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
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .setPositiveButton(R.string.save) { _, _ ->
                saveInvoice(
                    customerName = etCustomer.text.toString().trim(),
                    customerPhone = etPhone.text.toString().trim().ifEmpty { null },
                    invoiceDate = invoiceDate,
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
        note: String?
    ) {
        showAddItemDialog { item ->
            pendingItems.add(item)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.item_added)
                .setMessage(getString(R.string.items_count, pendingItems.size))
                .setNeutralButton(R.string.add_item) { _, _ ->
                    collectItems(customerName, customerPhone, invoiceDate, note)
                }
                .setPositiveButton(R.string.save) { _, _ ->
                    saveInvoice(customerName, customerPhone, invoiceDate, note)
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
        val options = arrayOf(getString(R.string.view), getString(R.string.delete))
        MaterialAlertDialogBuilder(this)
            .setTitle(invoice.customerName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewInvoice(invoice)
                    1 -> confirmDeleteInvoice(invoice)
                }
            }
            .show()
    }

    /**
     * A plain itemised readout, not yet the printed design — the 10 template
     * PDFs are the next phase, once this data layer is proven. Good enough to
     * check an invoice is right before that work starts.
     */
    private fun viewInvoice(invoice: InvoiceSummary) {
        lifecycleScope.launch {
            val items = dao.invoiceItems(invoice.id)
            val body = buildString {
                append(invoice.invoiceNumber)
                append(" · ")
                append(Format.dateOnly(invoice.invoiceDate))
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
                append(getString(R.string.invoice_total, Format.money(invoice.grandTotal)))
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
