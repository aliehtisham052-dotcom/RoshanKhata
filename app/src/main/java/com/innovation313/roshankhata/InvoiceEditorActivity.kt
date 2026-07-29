package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoicePdfExport
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Filling in an invoice with the actual printed design visible the whole
 * time, updating as the owner types — the same idea as [BusinessCardActivity]'s
 * live card preview, at the owner's own request after finding the design
 * only appeared once an invoice was already saved.
 *
 * Replaces the earlier dialog-chain way of adding an invoice. See the class
 * doc on [Invoice]: still nothing here reads or writes a balance.
 */
class InvoiceEditorActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }
    private var partyNames: List<String> = emptyList()

    private lateinit var ivPreview: ImageView
    private lateinit var pbLoading: ProgressBar
    private lateinit var rgTemplate: RadioGroup
    private lateinit var etCustomer: AutoCompleteTextView
    private lateinit var etPhone: EditText
    private lateinit var btnDate: MaterialButton
    private lateinit var btnDue: MaterialButton
    private lateinit var etDiscount: EditText
    private lateinit var etTax: EditText
    private lateinit var etNote: EditText
    private lateinit var itemRowsContainer: LinearLayout

    private var invoiceDate = System.currentTimeMillis()
    private var dueDate: Long? = null

    /** One inflated row and the fields inside it, tracked so it can be read back and removed. */
    private class ItemRow(
        val view: View,
        val etName: EditText,
        val etQty: EditText,
        val etUnit: EditText,
        val etRate: EditText
    )

    private val rows = mutableListOf<ItemRow>()

    /**
     * The preview re-renders on every keystroke's worth of change, but
     * actually rendering means writing a PDF and rasterising it — too much
     * to do on every single character. This is cancelled and restarted on
     * each change, so only the render after typing genuinely pauses runs.
     */
    private var renderJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice_editor)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        setSupportActionBar(findViewById<Toolbar>(R.id.editorToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ivPreview = findViewById(R.id.ivInvoicePreview)
        pbLoading = findViewById(R.id.pbPreviewLoading)
        rgTemplate = findViewById(R.id.rgInvoiceTemplate)
        etCustomer = findViewById(R.id.etInvoiceCustomer)
        etPhone = findViewById(R.id.etInvoicePhone)
        btnDate = findViewById(R.id.btnInvoiceDate)
        btnDue = findViewById(R.id.btnInvoiceDueDate)
        etDiscount = findViewById(R.id.etInvoiceDiscount)
        etTax = findViewById(R.id.etInvoiceTax)
        etNote = findViewById(R.id.etInvoiceNote)
        itemRowsContainer = findViewById(R.id.itemRowsContainer)

        btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(invoiceDate))
        btnDate.setOnClickListener {
            pickDate(invoiceDate) { picked ->
                invoiceDate = picked
                btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(picked))
                scheduleRender()
            }
        }
        btnDue.setOnClickListener {
            pickDate(dueDate ?: invoiceDate) { picked ->
                dueDate = picked
                btnDue.text = getString(R.string.due_date_set, Format.dateOnly(picked))
                scheduleRender()
            }
        }

        val watcher = renderOnChange()
        listOf(etCustomer, etPhone, etDiscount, etTax, etNote).forEach { it.addTextChangedListener(watcher) }
        rgTemplate.setOnCheckedChangeListener { _, _ -> scheduleRender() }

        findViewById<MaterialButton>(R.id.btnAddRow).setOnClickListener { addItemRow() }
        findViewById<MaterialButton>(R.id.btnSaveInvoice).setOnClickListener { save() }

        lifecycleScope.launch {
            partyNames = dao.allPartyNamesForInvoice()
            etCustomer.setAdapter(ArrayAdapter(this@InvoiceEditorActivity, android.R.layout.simple_dropdown_item_1line, partyNames))
        }

        addItemRow()
        scheduleRender()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun renderOnChange(): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) = scheduleRender()
    }

    // ---------- Item rows ----------

    private fun addItemRow() {
        val rowView = layoutInflater.inflate(R.layout.item_invoice_editor_row, itemRowsContainer, false)
        val etName: EditText = rowView.findViewById(R.id.etRowName)
        val etQty: EditText = rowView.findViewById(R.id.etRowQty)
        val etUnit: EditText = rowView.findViewById(R.id.etRowUnit)
        val etRate: EditText = rowView.findViewById(R.id.etRowRate)

        val watcher = renderOnChange()
        listOf(etName, etQty, etUnit, etRate).forEach { it.addTextChangedListener(watcher) }

        val row = ItemRow(rowView, etName, etQty, etUnit, etRate)
        rowView.findViewById<ImageView>(R.id.btnRemoveRow).setOnClickListener {
            itemRowsContainer.removeView(rowView)
            rows.remove(row)
            scheduleRender()
        }

        rows.add(row)
        itemRowsContainer.addView(rowView)
    }

    // ---------- Reading the form ----------

    private fun currentTemplateId(): Int =
        if (rgTemplate.checkedRadioButtonId == R.id.rbTemplateThermal) 10 else 1

    private fun currentInvoiceDraft(): Invoice = Invoice(
        customerName = etCustomer.text.toString().trim(),
        customerPhone = etPhone.text.toString().trim().ifEmpty { null },
        invoiceDate = invoiceDate,
        dueDate = dueDate,
        discountPercent = etDiscount.text.toString().trim().toDoubleOrNull(),
        taxPercent = etTax.text.toString().trim().toDoubleOrNull(),
        templateId = currentTemplateId(),
        note = etNote.text.toString().trim().ifEmpty { null }
    )

    /** Only rows with a name, a quantity, and a rate count — a half-filled row previews as if it were not there yet. */
    private fun currentValidItems(): List<InvoiceItem> = rows.mapNotNull { row ->
        val name = row.etName.text.toString().trim()
        val qty = row.etQty.text.toString().trim().toDoubleOrNull()
        val rate = row.etRate.text.toString().trim().toDoubleOrNull()
        if (name.isEmpty() || qty == null || qty <= 0 || rate == null || rate < 0) return@mapNotNull null
        InvoiceItem(
            invoiceId = 0,
            itemName = name,
            quantity = qty,
            unit = row.etUnit.text.toString().trim().ifEmpty { null },
            rate = rate
        )
    }

    // ---------- Live preview ----------

    private fun scheduleRender() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            delay(400)
            val invoice = currentInvoiceDraft()
            val items = currentValidItems()

            if (invoice.customerName.isEmpty() || items.isEmpty()) {
                ivPreview.setImageDrawable(null)
                pbLoading.visibility = View.GONE
                return@launch
            }

            pbLoading.visibility = View.VISIBLE
            val bmp = withContext(Dispatchers.IO) {
                InvoicePdfExport.renderPreviewBitmap(this@InvoiceEditorActivity, invoice, items)
            }
            if (!isFinishing && !isDestroyed) {
                pbLoading.visibility = View.GONE
                if (bmp != null) ivPreview.setImageBitmap(bmp)
            }
        }
    }

    // ---------- Save ----------

    private fun save() {
        val invoice = currentInvoiceDraft()
        val items = currentValidItems()

        if (invoice.customerName.isEmpty()) {
            Toast.makeText(this, R.string.invoice_customer_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.invoice_needs_item, Toast.LENGTH_SHORT).show()
            return
        }

        AppScope.launch {
            dao.saveInvoiceWithItems(invoice, items)
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this@InvoiceEditorActivity, R.string.invoice_saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
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
