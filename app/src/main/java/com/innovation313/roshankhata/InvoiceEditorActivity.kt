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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceFeatureSettings
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoicePdfExport
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.TemplatePagerAdapter
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

    companion object {
        /** Present only when editing a saved invoice; absent means creating a new one. */
        const val EXTRA_INVOICE_ID = "invoice_id"
    }

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Null while creating a new invoice; set once an existing one has been loaded for editing. */
    private var editingInvoiceId: Long? = null

    /**
     * Set only when editing a saved invoice, and only until the first
     * render — the ViewPager2 has no slides at all until renderAllPreviews()
     * populates the adapter, so setCurrentItem() called any earlier has
     * nothing to select. Applied once real slides exist, then cleared, so
     * a later re-render (returning from Business Settings) does not keep
     * resetting a page the owner has since swiped away from.
     */
    private var pendingTemplateId: Int? = null

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
    private lateinit var vpTemplates: ViewPager2
    private lateinit var tvTemplateCaption: TextView
    private lateinit var templateDots: LinearLayout
    private lateinit var tvSwipeHint: TextView
    private lateinit var pbLoading: ProgressBar
    private val templatePagerAdapter = TemplatePagerAdapter()

    /**
     * Every template offered, in the order slides appear — the one list a
     * new template gets added to. Names are the exact strings already used
     * elsewhere for these two designs, so the caption under the carousel
     * says the same thing the share-time picker always has.
     */
    private val templateIds = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val templateNameRes = listOf(
        R.string.invoice_template_teal,
        R.string.invoice_template_black_gold,
        R.string.invoice_template_gradient,
        R.string.invoice_template_green_retail,
        R.string.invoice_template_minimal_slate,
        R.string.invoice_template_indigo_tech,
        R.string.invoice_template_warm_orange,
        R.string.invoice_template_classic_cream,
        R.string.invoice_template_crimson_bold,
        R.string.invoice_template_thermal
    )

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
        vpTemplates = findViewById(R.id.vpTemplates)
        tvTemplateCaption = findViewById(R.id.tvTemplateCaption)
        templateDots = findViewById(R.id.templateDots)
        tvSwipeHint = findViewById(R.id.tvSwipeHint)
        pbLoading = findViewById(R.id.pbPreviewLoading)
        vpTemplates.adapter = templatePagerAdapter
        vpTemplates.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showTemplatePosition(position)
            }
        })
        buildTemplateDots()
        showTemplatePosition(0)

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
        // The owner can turn either off entirely in Invoice Settings —
        // checked fresh every time this screen opens, since a switch
        // flipped there while an invoice was being written should still
        // take effect on the next one.
        btnDue.visibility = if (InvoiceFeatureSettings.dueDateEnabled(this)) View.VISIBLE else View.GONE

        findViewById<MaterialButton>(R.id.btnAddRow).setOnClickListener { addRow() }
        val btnMore = findViewById<MaterialButton>(R.id.btnMoreOptions)
        btnMore.setOnClickListener { showMoreOptions() }
        btnMore.visibility = if (InvoiceFeatureSettings.anyOptionalFieldEnabled(this)) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnShopDetails).setOnClickListener {
            startActivity(android.content.Intent(this, BusinessSettingsActivity::class.java))
        }
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

        val editId = intent.getLongExtra(EXTRA_INVOICE_ID, -1L).takeIf { it > 0 }
        if (editId != null) {
            supportActionBar?.setTitle(R.string.edit_invoice)
            lifecycleScope.launch { loadForEditing(editId) }
        } else {
            addRow()
            showStep(1)
        }
    }

    /**
     * Fills every field from a saved invoice instead of starting blank —
     * the same three steps, the same validation, just pre-filled. Nothing
     * here is written until Save is pressed, same as creating a new one.
     */
    private suspend fun loadForEditing(id: Long) {
        val invoice = dao.getInvoice(id)
        if (invoice == null) {
            finish()
            return
        }
        val items = dao.invoiceItems(id)
        editingInvoiceId = id

        etCustomer.setText(invoice.customerName)
        etPhone.setText(invoice.customerPhone.orEmpty())
        etInvoiceNumber.setText(invoice.invoiceNumber)
        invoiceDate = invoice.invoiceDate
        btnDate.text = getString(R.string.invoice_date_set, Format.dateOnly(invoiceDate))
        invoice.dueDate?.let {
            dueDate = it
            btnDue.text = getString(R.string.due_date_set, Format.dateOnly(it))
        }
        discountPercent = invoice.discountPercent
        taxPercent = invoice.taxPercent
        chargeLabel = invoice.additionalChargeLabel
        chargeAmount = invoice.additionalChargeAmount
        receivedAmount = invoice.receivedAmount
        note = invoice.note
        pendingTemplateId = invoice.templateId

        if (items.isEmpty()) addRow() else items.forEach { addRow(it) }

        showStep(1)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Picks up any shop-detail change (stamp, QR, name, terms) made while away on step 3. */
    override fun onResume() {
        super.onResume()
        if (step == 3) renderAllPreviews()
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

        // GONE, not just invisible — an invisible view still reserves its
        // weighted share of the row, which is exactly why Next looked small
        // and pushed to one side on Step 1 instead of filling the width.
        btnBack.visibility = if (which == 1) View.GONE else View.VISIBLE
        btnNext.setText(if (which == 3) R.string.save else R.string.invoice_next)

        if (which == 3) renderAllPreviews()
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

    private fun addRow(existing: InvoiceItem? = null) {
        val v = layoutInflater.inflate(R.layout.item_invoice_editor_row, itemRows, false)
        val row = Row(
            v,
            v.findViewById(R.id.etRowName),
            v.findViewById(R.id.etRowQty),
            v.findViewById(R.id.etRowUnit),
            v.findViewById(R.id.etRowRate)
        )
        if (existing != null) {
            row.name.setText(existing.itemName)
            row.qty.setText(Format.plain(existing.quantity))
            row.unit.setText(existing.unit.orEmpty())
            row.rate.setText(Format.plain(existing.rate))
        }
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
        templateIds.getOrElse(vpTemplates.currentItem) { templateIds.first() }

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

        // Discount and tax share a row but toggle independently — hiding
        // one still leaves the other its own line. Extra charge is a pair
        // (a label with no amount means nothing), so it hides as one row.
        etDiscount.visibility = if (InvoiceFeatureSettings.discountEnabled(this)) View.VISIBLE else View.GONE
        etTax.visibility = if (InvoiceFeatureSettings.taxEnabled(this)) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.rowExtraChargeFields).visibility =
            if (InvoiceFeatureSettings.extraChargeEnabled(this)) View.VISIBLE else View.GONE
        etReceived.visibility = if (InvoiceFeatureSettings.receivedEnabled(this)) View.VISIBLE else View.GONE
        etNote.visibility = if (InvoiceFeatureSettings.noteEnabled(this)) View.VISIBLE else View.GONE

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
                // A field hidden by a switch keeps whatever it already held
                // rather than being read (and possibly cleared) from a view
                // that was never shown — turning Tax off should not erase a
                // tax figure already set earlier in the same invoice.
                if (InvoiceFeatureSettings.discountEnabled(this)) {
                    discountPercent = etDiscount.text.toString().trim().toDoubleOrNull()
                }
                if (InvoiceFeatureSettings.taxEnabled(this)) {
                    taxPercent = etTax.text.toString().trim().toDoubleOrNull()
                }
                if (InvoiceFeatureSettings.extraChargeEnabled(this)) {
                    chargeLabel = etChargeLabel.text.toString().trim().ifEmpty { null }
                    chargeAmount = etChargeAmount.text.toString().trim().toDoubleOrNull()
                }
                if (InvoiceFeatureSettings.receivedEnabled(this)) {
                    receivedAmount = etReceived.text.toString().trim().toDoubleOrNull()
                }
                if (InvoiceFeatureSettings.noteEnabled(this)) {
                    note = etNote.text.toString().trim().ifEmpty { null }
                }
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

    /**
     * One dot per template, built once — the carousel gave no sign at all
     * that more than one design existed, so nothing on screen invited a
     * swipe. Dots plus a counted hint make both the count and the gesture
     * obvious without needing to discover them.
     */
    private fun buildTemplateDots() {
        templateDots.removeAllViews()
        val size = (8 * resources.displayMetrics.density).toInt()
        val gap = (5 * resources.displayMetrics.density).toInt()
        repeat(templateIds.size) { index ->
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(size, size)
            if (index > 0) lp.marginStart = gap
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.dot_page_indicator)
            templateDots.addView(dot)
        }
    }

    private fun showTemplatePosition(position: Int) {
        tvTemplateCaption.setText(templateNameRes.getOrElse(position) { templateNameRes.first() })
        tvSwipeHint.text = getString(R.string.invoice_swipe_designs, position + 1, templateIds.size)
        val active = androidx.core.content.ContextCompat.getColor(this, R.color.brand_green)
        val inactive = androidx.core.content.ContextCompat.getColor(this, R.color.text_muted)
        for (i in 0 until templateDots.childCount) {
            templateDots.getChildAt(i).backgroundTintList =
                android.content.res.ColorStateList.valueOf(if (i == position) active else inactive)
        }
    }

    /**
     * Renders every template's own slide, not just the one currently
     * shown — the whole point of a swipeable carousel is that flicking to
     * the next design is instant, not another PDF-and-rasterise wait per
     * swipe. Cheap enough at today's two templates; if that stops being
     * true once more of the ten exist, this is the one place to revisit.
     */
    private fun renderAllPreviews() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            pbLoading.visibility = View.VISIBLE
            val baseInvoice = draft()
            val items = validItems()
            val bitmaps = withContext(Dispatchers.IO) {
                templateIds.map { id ->
                    InvoicePdfExport.renderPreviewBitmap(
                        this@InvoiceEditorActivity, baseInvoice.copy(templateId = id), items
                    )
                }
            }
            if (isFinishing || isDestroyed) return@launch
            pbLoading.visibility = View.GONE
            templatePagerAdapter.submit(bitmaps)

            pendingTemplateId?.let { id ->
                vpTemplates.setCurrentItem(templateIds.indexOf(id).coerceAtLeast(0), false)
                pendingTemplateId = null
            }
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
            val editId = editingInvoiceId
            if (editId != null) {
                dao.updateInvoiceWithItems(invoice.copy(id = editId), items)
            } else {
                dao.saveInvoiceWithItems(invoice, items)
            }
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
