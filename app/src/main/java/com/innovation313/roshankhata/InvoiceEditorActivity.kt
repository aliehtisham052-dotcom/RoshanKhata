package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoicePdfExport
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Making an invoice as a three-step walkthrough, in the owner's own order:
 * customer, then items, then the design.
 *
 * Asked for repeatedly and explicitly — the earlier single long form put the
 * design choice at the top, which is the opposite of how the owner works and
 * of how the app's own onboarding walkthrough already behaves. Each step
 * shows only what belongs to it, and Next will not move on until that step
 * actually has what it needs.
 *
 * The preview appears on the design step, where it is the thing being
 * decided — not above a form it merely duplicates.
 *
 * See the class doc on [Invoice]: nothing here reads or writes a balance.
 */
class InvoiceEditorActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private lateinit var tvStep: TextView
    private lateinit var stepCustomer: View
    private lateinit var stepItems: View
    private lateinit var stepDesign: View
    private lateinit var btnBack: MaterialButton
    private lateinit var btnNext: MaterialButton

    private lateinit var etCustomer: AutoCompleteTextView
    private lateinit var etPhone: EditText
    private lateinit var btnDate: MaterialButton
    private lateinit var btnDue: MaterialButton
    private lateinit var etInvoiceNumber: EditText
    private lateinit var itemRows: LinearLayout
    // Everything below lives in the "More options" dialog rather than a
    // permanent view, so these are plain state — set when the dialog is
    // saved, read back in to pre-fill it next time it opens.
    private var discountPercent: Double? = null
    private var taxPercent: Double? = null
    private var note: String? = null
    private var chargeLabel: String? = null
    private var chargeAmount: Double? = null
    private var receivedAmount: Double? = null
    private lateinit var rgTemplate: RadioGroup
    private lateinit var ivPreview: ImageView
    private lateinit var pbLoading: ProgressBar

    private var step = 1
    private var invoiceDate = System.currentTimeMillis()
    private var dueDate: Long? = null
    private var renderJob: Job? = null

    /** One inflated item row and the fields inside it. */
    private class Row(
        val view: View,
        val name: EditText,
        val qty: EditText,
        val unit: EditText,
        val rate: EditText
    )

    private val rows = mutableListOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice_editor)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        setSupportActionBar(findViewById<Toolbar>(R.id.editorToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvStep = findViewById(R.id.tvStep)
        stepCustomer = findViewById(R.id.stepCustomer)
        stepItems = findViewById(R.id.stepItems)
        stepDesign = findViewById(R.id.stepDesign)
        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)

        etCustomer = findViewById(R.id.etInvoiceCustomer)
        etPhone = findViewById(R.id.etInvoicePhone)
        btnDate = findViewById(R.id.btnInvoiceDate)
        btnDue = findViewById(R.id.btnInvoiceDueDate)
        etInvoiceNumber = findViewById(R.id.etInvoiceNumber)
        itemRows = findViewById(R.id.itemRowsContainer)
        rgTemplate = findViewById(R.id.rgInvoiceTemplate)
        ivPreview = findViewById(R.id.ivInvoicePreview)
        pbLoading = findViewById(R.id.pbPreviewLoading)

        btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(invoiceDate))
        btnDate.setOnClickListener {
            pickDate(invoiceDate) {
                invoiceDate = it
                btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(it))
            }
        }
        btnDue.setOnClickListener {
            pickDate(dueDate ?: invoiceDate) {
                dueDate = it
                btnDue.text = getString(R.string.due_date_set, Format.dateOnly(it))
            }
        }

        findViewById<MaterialButton>(R.id.btnAddRow).setOnClickListener { addRow() }
        findViewById<MaterialButton>(R.id.btnMoreOptions).setOnClickListener { showMoreOptions() }
        findViewById<MaterialButton>(R.id.btnShopDetails).setOnClickListener {
            startActivity(android.content.Intent(this, BusinessSettingsActivity::class.java))
        }
        rgTemplate.setOnCheckedChangeListener { _, _ -> renderPreview() }

        btnBack.setOnClickListener { goBack() }
        btnNext.setOnClickListener { goNext() }

        lifecycleScope.launch {
            val parties = dao.allPartiesForInvoice()
            // Keyed by name for the click lookup below. Where the same name
            // appears on more than one party, the first one wins — this was
            // always a plain convenience lookup, never a link to a party
            // row, so there is no "correct" one to prefer beyond that.
            val phoneByName = parties.associateBy({ it.name }, { it.phone })

            etCustomer.setAdapter(
                ArrayAdapter(
                    this@InvoiceEditorActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    parties.map { it.name }.distinct()
                )
            )
            etCustomer.setOnItemClickListener { _, _, position, _ ->
                val picked = etCustomer.adapter.getItem(position) as? String ?: return@setOnItemClickListener
                phoneByName[picked]?.takeIf { it.isNotBlank() }?.let { etPhone.setText(it) }
            }
        }

        addRow()
        showStep(1)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Picks up any shop-detail change (stamp, QR, name, terms) made while away on step 3. */
    override fun onResume() {
        super.onResume()
        if (step == 3) renderPreview()
    }

    override fun onBackPressed() {
        if (step > 1) goBack() else super.onBackPressed()
    }

    // ---------- Steps ----------

    private fun showStep(which: Int) {
        step = which
        stepCustomer.visibility = if (which == 1) View.VISIBLE else View.GONE
        stepItems.visibility = if (which == 2) View.VISIBLE else View.GONE
        stepDesign.visibility = if (which == 3) View.VISIBLE else View.GONE

        tvStep.text = getString(
            when (which) {
                1 -> R.string.invoice_step_customer
                2 -> R.string.invoice_step_items
                else -> R.string.invoice_step_design
            }
        )

        btnBack.visibility = if (which == 1) View.INVISIBLE else View.VISIBLE
        btnNext.setText(if (which == 3) R.string.save else R.string.invoice_next)

        if (which == 3) renderPreview()
    }

    private fun goBack() {
        if (step > 1) showStep(step - 1)
    }

    /** Next refuses to move on until the step it is leaving actually has what it needs. */
    private fun goNext() {
        when (step) {
            1 -> {
                if (etCustomer.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, R.string.invoice_customer_required, Toast.LENGTH_SHORT).show()
                    return
                }
                showStep(2)
            }
            2 -> {
                if (validItems().isEmpty()) {
                    Toast.makeText(this, R.string.invoice_needs_item, Toast.LENGTH_SHORT).show()
                    return
                }
                showStep(3)
            }
            else -> save()
        }
    }

    // ---------- Items ----------

    private fun addRow() {
        val v = layoutInflater.inflate(R.layout.item_invoice_editor_row, itemRows, false)
        val row = Row(
            v,
            v.findViewById(R.id.etRowName),
            v.findViewById(R.id.etRowQty),
            v.findViewById(R.id.etRowUnit),
            v.findViewById(R.id.etRowRate)
        )
        v.findViewById<ImageView>(R.id.btnRemoveRow).setOnClickListener {
            // The last row stays: with none at all there is nothing to type into.
            if (rows.size > 1) {
                itemRows.removeView(v)
                rows.remove(row)
            }
        }
        rows.add(row)
        itemRows.addView(v)
    }

    private fun validItems(): List<InvoiceItem> = rows.mapNotNull { r ->
        val name = r.name.text.toString().trim()
        val qty = r.qty.text.toString().trim().toDoubleOrNull()
        val rate = r.rate.text.toString().trim().toDoubleOrNull()
        if (name.isEmpty() || qty == null || qty <= 0 || rate == null || rate < 0) return@mapNotNull null
        InvoiceItem(
            invoiceId = 0,
            itemName = name,
            quantity = qty,
            unit = r.unit.text.toString().trim().ifEmpty { null },
            rate = rate
        )
    }

    // ---------- The draft, and its preview on step 3 ----------

    private fun templateId(): Int =
        if (rgTemplate.checkedRadioButtonId == R.id.rbTemplateThermal) 10 else 1

    /**
     * Every optional field in one dialog, pre-filled from whatever was set
     * last time it was opened. Fields are plain text/number inputs, not
     * bound to any live preview — nothing here needs to redraw the page
     * until Save (step 3), so a simple read-on-save is enough.
     */
    private fun showMoreOptions() {
        val view = layoutInflater.inflate(R.layout.dialog_invoice_more_options, null)
        val etDiscount: EditText = view.findViewById(R.id.etInvoiceDiscount)
        val etTax: EditText = view.findViewById(R.id.etInvoiceTax)
        val etChargeLabel: EditText = view.findViewById(R.id.etChargeLabel)
        val etChargeAmount: EditText = view.findViewById(R.id.etChargeAmount)
        val etReceived: EditText = view.findViewById(R.id.etReceived)
        val etNote: EditText = view.findViewById(R.id.etInvoiceNote)

        etDiscount.setText(discountPercent?.let { Format.plain(it) } ?: "")
        etTax.setText(taxPercent?.let { Format.plain(it) } ?: "")
        etChargeLabel.setText(chargeLabel.orEmpty())
        etChargeAmount.setText(chargeAmount?.let { Format.plain(it) } ?: "")
        etReceived.setText(receivedAmount?.let { Format.plain(it) } ?: "")
        etNote.setText(note.orEmpty())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.invoice_more_options)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                discountPercent = etDiscount.text.toString().trim().toDoubleOrNull()
                taxPercent = etTax.text.toString().trim().toDoubleOrNull()
                chargeLabel = etChargeLabel.text.toString().trim().ifEmpty { null }
                chargeAmount = etChargeAmount.text.toString().trim().toDoubleOrNull()
                receivedAmount = etReceived.text.toString().trim().toDoubleOrNull()
                note = etNote.text.toString().trim().ifEmpty { null }
            }
            .show()
    }

    private fun draft(): Invoice {
        // A label with no amount, or an amount with no label, is not a
        // usable extra charge — treated the same as neither being set.
        val hasCharge = chargeLabel != null && chargeAmount != null

        return Invoice(
            invoiceNumber = etInvoiceNumber.text.toString().trim(),
            customerName = etCustomer.text.toString().trim(),
            customerPhone = etPhone.text.toString().trim().ifEmpty { null },
            invoiceDate = invoiceDate,
            dueDate = dueDate,
            discountPercent = discountPercent,
            taxPercent = taxPercent,
            additionalChargeLabel = if (hasCharge) chargeLabel else null,
            additionalChargeAmount = if (hasCharge) chargeAmount else null,
            receivedAmount = receivedAmount,
            templateId = templateId(),
            note = note
        )
    }

    private fun renderPreview() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            pbLoading.visibility = View.VISIBLE
            val invoice = draft()
            val items = validItems()
            val bmp = withContext(Dispatchers.IO) {
                InvoicePdfExport.renderPreviewBitmap(this@InvoiceEditorActivity, invoice, items)
            }
            if (isFinishing || isDestroyed) return@launch
            pbLoading.visibility = View.GONE
            if (bmp != null) ivPreview.setImageBitmap(bmp)
        }
    }

    // ---------- Save ----------

    private fun save() {
        val items = validItems()
        val invoice = draft()

        if (invoice.customerName.isEmpty()) {
            showStep(1)
            Toast.makeText(this, R.string.invoice_customer_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (items.isEmpty()) {
            showStep(2)
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
