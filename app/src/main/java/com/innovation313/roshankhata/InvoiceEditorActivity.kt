package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Writing an invoice ON the invoice.
 *
 * The owner asked for exactly one thing on screen — the printed design —
 * and to type onto it directly, after an earlier version showed a preview
 * above and a form below saying the same thing twice. So the page fills the
 * screen, tapping a value on it opens an editor sitting over that value,
 * and the page redraws with the new text in place.
 *
 * How that is possible without a second drawing routine: [InvoicePdfExport]
 * records where it drew each editable value while producing the real PDF,
 * measured from the very Paint that drew it. The editor hit-tests those
 * boxes. Nothing here re-implements a template, so nothing here can disagree
 * with the file that ends up being shared.
 *
 * What stays as ordinary buttons is only what has nowhere to live on the
 * page: the design choice, the values not printed until they have one
 * (dates, discount, tax), adding a line, and saving.
 *
 * See the class doc on [Invoice]: still nothing here reads or writes a
 * balance.
 */
class InvoiceEditorActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private lateinit var ivPreview: ImageView
    private lateinit var previewFrame: FrameLayout
    private lateinit var etInline: EditText
    private lateinit var pbLoading: ProgressBar

    // ---- The draft being written ----
    private var customerName = ""
    private var customerPhone: String? = null
    private var note: String? = null
    private var invoiceDate = System.currentTimeMillis()
    private var dueDate: Long? = null
    private var discountPercent: Double? = null
    private var taxPercent: Double? = null
    private var templateId = 1
    private val items = mutableListOf(InvoiceItem(invoiceId = 0, itemName = "", quantity = 1.0, rate = 0.0))

    /** Where each editable value landed on the page last time it was drawn. */
    private var boxes: List<InvoicePdfExport.FieldBox> = emptyList()
    private var pageWidth = 1
    private var renderJob: Job? = null

    /** Which value the inline editor is currently sitting on, if any. */
    private var editingField: InvoicePdfExport.Field? = null
    private var editingIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice_editor)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        setSupportActionBar(findViewById<Toolbar>(R.id.editorToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ivPreview = findViewById(R.id.ivInvoicePreview)
        previewFrame = findViewById(R.id.previewFrame)
        etInline = findViewById(R.id.etInline)
        pbLoading = findViewById(R.id.pbPreviewLoading)

        // A GestureDetector, not a raw ACTION_UP: consuming every touch would
        // have stopped the page scrolling at all, and a long invoice has to
        // scroll. Only a genuine single tap is taken; drags fall through to
        // the ScrollView.
        val taps = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.x, e.y)
                return true
            }
        })
        ivPreview.setOnTouchListener { v, event ->
            val handled = taps.onTouchEvent(event)
            if (handled && event.action == MotionEvent.ACTION_UP) v.performClick()
            handled
        }

        etInline.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitInline()
                true
            } else {
                false
            }
        }

        findViewById<MaterialButton>(R.id.btnDesign).setOnClickListener { chooseDesign() }
        findViewById<MaterialButton>(R.id.btnMore).setOnClickListener { showMore() }
        findViewById<MaterialButton>(R.id.btnAddRow).setOnClickListener {
            items.add(InvoiceItem(invoiceId = 0, itemName = "", quantity = 1.0, rate = 0.0))
            scheduleRender()
        }
        findViewById<MaterialButton>(R.id.btnSaveInvoice).setOnClickListener { save() }

        scheduleRender()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ---------- Tapping a value on the page ----------

    /**
     * The page is drawn at whatever width the screen gives it, so a tap has
     * to be converted back into the PDF's own point coordinates before it
     * can be matched against the recorded boxes.
     */
    private fun scale(): Float =
        if (pageWidth <= 0) 1f else ivPreview.width.toFloat() / pageWidth

    private fun handleTap(x: Float, y: Float) {
        if (editingField != null) commitInline()

        val s = scale()
        if (s <= 0f) return
        val px = x / s
        val py = y / s

        // Reach, in PDF points. A 10pt line is a small target on a phone, so
        // a tap near one still counts — but only near: too generous and a tap
        // meant for one line would open the one above it.
        val reachY = 14f
        val reachX = 40f

        val hit = boxes
            .map { box ->
                val dy = kotlin.math.abs(py - box.rect.centerY())
                val dx = when {
                    px < box.rect.left -> box.rect.left - px
                    px > box.rect.right -> px - box.rect.right
                    else -> 0f
                }
                box to (dx to dy)
            }
            .filter { (_, d) -> d.second <= reachY && d.first <= reachX }
            // Vertical agreement first: on a page of stacked lines, being on
            // the right line matters more than being near the text sideways.
            .minByOrNull { (_, d) -> d.second * 4f + d.first }
            ?.first
            ?: return

        openInline(hit)
    }

    private fun openInline(box: InvoicePdfExport.FieldBox) {
        editingField = box.field
        editingIndex = box.itemIndex

        val s = scale()
        val r = RectF(box.rect.left * s, box.rect.top * s, box.rect.right * s, box.rect.bottom * s)

        val minW = (110 * resources.displayMetrics.density).toInt()
        val lp = etInline.layoutParams as FrameLayout.LayoutParams
        lp.width = maxOf(r.width().toInt(), minW)
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        lp.leftMargin = r.left.toInt().coerceAtLeast(0)
        lp.topMargin = (r.top - 6 * resources.displayMetrics.density).toInt().coerceAtLeast(0)
        etInline.layoutParams = lp

        val numeric = box.field == InvoicePdfExport.Field.ITEM_QTY ||
            box.field == InvoicePdfExport.Field.ITEM_RATE ||
            box.field == InvoicePdfExport.Field.PHONE
        etInline.inputType =
            if (box.field == InvoicePdfExport.Field.PHONE) android.text.InputType.TYPE_CLASS_PHONE
            else if (numeric) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            else android.text.InputType.TYPE_CLASS_TEXT

        etInline.setText(currentValueOf(box))
        etInline.setSelection(etInline.text.length)
        etInline.visibility = View.VISIBLE
        etInline.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(etInline, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun currentValueOf(box: InvoicePdfExport.FieldBox): String = when (box.field) {
        InvoicePdfExport.Field.CUSTOMER -> customerName
        InvoicePdfExport.Field.PHONE -> customerPhone.orEmpty()
        InvoicePdfExport.Field.NOTE -> note.orEmpty()
        InvoicePdfExport.Field.ITEM_NAME -> items.getOrNull(box.itemIndex)?.itemName.orEmpty()
        InvoicePdfExport.Field.ITEM_QTY ->
            items.getOrNull(box.itemIndex)?.quantity?.let { Format.plain(it) }.orEmpty()
        InvoicePdfExport.Field.ITEM_RATE ->
            items.getOrNull(box.itemIndex)?.rate?.let { Format.plain(it) }.orEmpty()
    }

    private fun commitInline() {
        val field = editingField ?: return
        val text = etInline.text.toString().trim()
        val index = editingIndex

        when (field) {
            InvoicePdfExport.Field.CUSTOMER -> customerName = text
            InvoicePdfExport.Field.PHONE -> customerPhone = text.ifEmpty { null }
            InvoicePdfExport.Field.NOTE -> note = text.ifEmpty { null }
            InvoicePdfExport.Field.ITEM_NAME ->
                items.getOrNull(index)?.let { items[index] = it.copy(itemName = text) }
            InvoicePdfExport.Field.ITEM_QTY ->
                items.getOrNull(index)?.let {
                    items[index] = it.copy(quantity = text.toDoubleOrNull() ?: it.quantity)
                }
            InvoicePdfExport.Field.ITEM_RATE ->
                items.getOrNull(index)?.let {
                    items[index] = it.copy(rate = text.toDoubleOrNull() ?: it.rate)
                }
        }

        editingField = null
        editingIndex = -1
        etInline.visibility = View.GONE
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etInline.windowToken, 0)
        scheduleRender()
    }

    // ---------- Everything with nowhere to live on the page ----------

    private fun chooseDesign() {
        val names = arrayOf(
            getString(R.string.invoice_template_teal),
            getString(R.string.invoice_template_thermal)
        )
        val ids = intArrayOf(1, 10)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.invoice_choose_template)
            .setItems(names) { _, which ->
                templateId = ids[which]
                scheduleRender()
            }
            .show()
    }

    private fun showMore() {
        val options = arrayOf(
            getString(R.string.pick_invoice_date),
            getString(R.string.invoice_due_date_hint),
            getString(R.string.invoice_discount_hint),
            getString(R.string.invoice_tax_hint),
            getString(R.string.invoice_remove_last_item)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.invoice_btn_more)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickDate(invoiceDate) { invoiceDate = it; scheduleRender() }
                    1 -> pickDate(dueDate ?: invoiceDate) { dueDate = it; scheduleRender() }
                    2 -> askNumber(R.string.invoice_discount_hint, discountPercent) {
                        discountPercent = it; scheduleRender()
                    }
                    3 -> askNumber(R.string.invoice_tax_hint, taxPercent) {
                        taxPercent = it; scheduleRender()
                    }
                    4 -> {
                        if (items.size > 1) {
                            items.removeAt(items.size - 1)
                            scheduleRender()
                        }
                    }
                }
            }
            .show()
    }

    private fun askNumber(titleRes: Int, current: Double?, onDone: (Double?) -> Unit) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(current?.let { Format.plain(it) } ?: "")
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val wrap = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(wrap)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                onDone(input.text.toString().trim().toDoubleOrNull())
            }
            .show()
    }

    // ---------- Redrawing the page ----------

    private fun draft(): Invoice = Invoice(
        customerName = customerName.ifEmpty { getString(R.string.invoice_customer_hint) },
        customerPhone = customerPhone,
        invoiceDate = invoiceDate,
        dueDate = dueDate,
        discountPercent = discountPercent,
        taxPercent = taxPercent,
        templateId = templateId,
        note = note
    )

    /**
     * Every row is drawn, even a blank one just added — an empty line still
     * needs to appear on the page for there to be anywhere to tap and start
     * typing it. Only [save] insists on real values.
     */
    private fun drawableItems(): List<InvoiceItem> = items.map {
        if (it.itemName.isBlank()) it.copy(itemName = getString(R.string.invoice_item_name_hint)) else it
    }

    private fun scheduleRender() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            delay(250)
            pbLoading.visibility = View.VISIBLE
            val invoice = draft()
            val list = drawableItems()
            val preview = withContext(Dispatchers.IO) {
                InvoicePdfExport.renderPreview(this@InvoiceEditorActivity, invoice, list)
            }
            if (isFinishing || isDestroyed) return@launch
            pbLoading.visibility = View.GONE
            if (preview != null) {
                ivPreview.setImageBitmap(preview.bitmap)
                boxes = preview.boxes
                pageWidth = preview.pageWidth
            }
        }
    }

    // ---------- Save ----------

    private fun save() {
        if (editingField != null) commitInline()

        val real = items.filter {
            it.itemName.isNotBlank() && it.quantity > 0 && it.rate >= 0
        }

        if (customerName.isBlank()) {
            Toast.makeText(this, R.string.invoice_customer_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (real.isEmpty()) {
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

        AppScope.launch {
            dao.saveInvoiceWithItems(invoice, real)
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
