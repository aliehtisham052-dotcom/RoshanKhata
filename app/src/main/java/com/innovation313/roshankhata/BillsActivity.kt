package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.choices

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.ui.BrandDatePickerDialog
import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.data.TradeVocab
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import com.innovation313.roshankhata.data.BillPhoto
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.ScannedBill
import com.innovation313.roshankhata.data.BillSummary
import com.innovation313.roshankhata.data.ExpiryWindow
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.labelOf
import com.innovation313.roshankhata.ui.SmartSuggest
import com.innovation313.roshankhata.ui.attach
import com.innovation313.roshankhata.ui.asSuggestions
import com.innovation313.roshankhata.ui.withStarters
import com.innovation313.roshankhata.ui.BillAdapter
import com.innovation313.roshankhata.ui.BillScanFlow
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ProductDetailsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import com.innovation313.roshankhata.data.Digits

/**
 * Supplier bills: stock bought in.
 *
 * The money rule, unchanged from everywhere else in this app: THE LEDGER IS THE
 * MONEY. Stock taken on credit writes ONE ledger entry recording what is now
 * owed, and the bill points at it. The bill keeps no balance of its own — if it
 * did, the two would drift apart and there would be two different answers to
 * "what do I owe them?", with no way to tell which was true.
 *
 * What the bill adds is the paperwork a ledger entry cannot hold: bill number,
 * batch numbers, expiry dates. That is the part that matters when the
 * Agriculture Department inspector arrives, or when a batch goes bad and has to
 * be traced back to the supplier it came from.
 */
class BillsActivity : BaseActivity() {

    private lateinit var adapter: BillAdapter
    private lateinit var tvEmpty: View
    private lateinit var btnExpiring: MaterialButton

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var parties: List<PartyWithBalance> = emptyList()

    // A new bill asked for from a supplier's khata: opened, with that
    // supplier filled in, as soon as the party list is loaded. Once only.
    private var pendingSupplier: String? = null

    companion object {
        const val EXTRA_NEW_BILL_SUPPLIER = "new_bill_supplier"

        private const val STATE_SCAN_PHOTO = "scan_photo"
        private const val STATE_SCAN_SUPPLIER = "scan_supplier"
        private const val STATE_SCAN_NUMBER = "scan_number"
        private const val STATE_SCAN_TOTAL = "scan_total"
        private const val STATE_SCAN_DATE = "scan_date"
        private const val STATE_SCAN_DUE = "scan_due"
        private const val STATE_SCAN_CASH = "scan_cash"
        private const val STATE_SCAN_NOTE = "scan_note"
        private const val STATE_SCAN_KEPT = "scan_kept"
    }

    /** Items being collected for the bill currently being entered. */
    private val pendingItems = mutableListOf<BillItem>()

    // ---------- Scan bill (1 Oct) ----------
    //
    // A photo of the supplier's printed bill is read on the phone (BillOcr +
    // BillScan) and offered back to the owner to check; what they keep goes
    // into the same form and the same pending items as typing would. Nothing
    // from a photo is ever saved without passing through the form.
    //
    // The form is closed while the camera or gallery is open (another app is
    // in front), so what was typed is held as [scanDraft] and the form is
    // reopened from it afterwards — kept across onSaveInstanceState too,
    // because Android may close this screen while the camera is up.

    private var scanDraft: BillDraft? = null

    /**
     * The scanned bill's picture, already copied into the app's private
     * bill-photo storage (BillPhoto), waiting to go with the bill being
     * entered. On a credit bill it becomes the khata entry's bill photo, so
     * the paper is one tap away from the debt it created; a cash bill has no
     * entry to hold it, so it is let go. Read and cleared with pendingItems.
     */
    private var scanKeptPhoto: String? = null

    private fun dropKeptPhoto() {
        scanKeptPhoto?.let { path -> AppScope.launch { runCatching { java.io.File(path).delete() } } }
        scanKeptPhoto = null
    }

    /** Scanner, camera or gallery — the one flow every bill-reading screen shares. */
    private val scanFlow = BillScanFlow(
        activity = this,
        onPhoto = { uri, temp -> readBillPhoto(uri, temp) },
        onNothing = { reopenFromScan() }
    )

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        scanFlow.saveState(outState, STATE_SCAN_PHOTO)
        scanKeptPhoto?.let { outState.putString(STATE_SCAN_KEPT, it) }
        scanDraft?.let { d ->
            outState.putString(STATE_SCAN_SUPPLIER, d.supplierName)
            outState.putString(STATE_SCAN_NUMBER, d.billNumber)
            d.total?.let { outState.putDouble(STATE_SCAN_TOTAL, it) }
            outState.putLong(STATE_SCAN_DATE, d.billDate)
            d.dueDate?.let { outState.putLong(STATE_SCAN_DUE, it) }
            d.paidWith?.let { outState.putString(STATE_SCAN_CASH, it) }
            outState.putString(STATE_SCAN_NOTE, d.note)
        }
    }

    private fun restoreScanState(state: Bundle?) {
        state ?: return
        scanFlow.restoreState(state, STATE_SCAN_PHOTO)
        scanKeptPhoto = state.getString(STATE_SCAN_KEPT)
        val supplier = state.getString(STATE_SCAN_SUPPLIER) ?: return
        scanDraft = BillDraft(
            supplierName = supplier,
            billNumber = state.getString(STATE_SCAN_NUMBER),
            total = if (state.containsKey(STATE_SCAN_TOTAL)) state.getDouble(STATE_SCAN_TOTAL) else null,
            billDate = state.getLong(STATE_SCAN_DATE, System.currentTimeMillis()),
            dueDate = if (state.containsKey(STATE_SCAN_DUE)) state.getLong(STATE_SCAN_DUE) else null,
            paidWith = state.getString(STATE_SCAN_CASH),
            note = state.getString(STATE_SCAN_NOTE)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bills)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.emptyBills)
        findViewById<MaterialButton>(R.id.btnFirstBill).setOnClickListener { startNewBill() }
        btnExpiring = findViewById(R.id.btnExpiring)
        findViewById<android.view.View>(R.id.btnSupplierRates).setOnClickListener {
            startActivity(android.content.Intent(this, SupplierRatesActivity::class.java))
        }

        adapter = BillAdapter { bill -> showBillActions(bill) }
        val rv: RecyclerView = findViewById(R.id.rvBills)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddBill).setOnClickListener {
            startNewBill()
        }

        btnExpiring.setOnClickListener {
            startActivity(Intent(this, ExpiringActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnTrace).setOnClickListener { showTraceDialog() }
        findViewById<MaterialButton>(R.id.btnSchemes).setOnClickListener {
            startActivity(android.content.Intent(this, SchemesActivity::class.java))
        }

        if (savedInstanceState == null) {
            pendingSupplier = intent.getStringExtra(EXTRA_NEW_BILL_SUPPLIER)?.takeIf { it.isNotBlank() }
        }
        restoreScanState(savedInstanceState)
        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            dao.observeBills().collectLatest { list ->
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                // One way to add a bill at a time (13 Oct): an empty page has
                // its own "Add the first bill", so the floating button waits
                // until there is a list to sit over. On a helper's read-only
                // phone it stays hidden either way.
                if (!com.innovation313.roshankhata.data.ViewerMode.isOn(this@BillsActivity)) {
                    findViewById<View>(R.id.fabAddBill).visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
                renderStats(list)
            }
        }

        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest {
                parties = it
                pendingSupplier?.let { name ->
                    pendingSupplier = null
                    startNewBill(name)
                }
            }
        }

        // The expiry banner only appears when there is something to act on. A
        // permanent red button that usually means nothing would train the owner
        // to ignore it — and then it would be ignored on the day it mattered.
        val cutoff = System.currentTimeMillis() + ExpiryWindow.WARN_MS
        lifecycleScope.launch {
            // Batches that still hold something, by the one rule (Marhala 3): a sold-out or written-off lot no longer counts.
            com.innovation313.roshankhata.data.Fefo.observeExpiringCount(dao, cutoff).collectLatest { count ->
                findViewById<TextView>(R.id.tvStatExpiring).text = "%,d".format(Digits.FIGURES, count)
                if (count > 0) {
                    btnExpiring.visibility = View.VISIBLE
                    btnExpiring.text = Digits.quantity(resources, R.plurals.n_expiring, count, count)
                } else {
                    btnExpiring.visibility = View.GONE
                }
            }
        }
    }

    /**
     * The figures above the list (13 Oct). "You owe" is each supplier's
     * live ledger balance counted once, not the bills added up: a payment
     * made in the khata is not tied to any one bill, so only the balance
     * says what is really still owed.
     */
    private fun renderStats(list: List<com.innovation313.roshankhata.data.BillSummary>) {
        findViewById<TextView>(R.id.tvStatBills).text = "%,d".format(Digits.FIGURES, list.size)
        val owed = list.distinctBy { it.partyId }.sumOf { (-it.supplierBalance).coerceAtLeast(0.0) }
        findViewById<TextView>(R.id.tvStatOwe).text = Format.money(owed)
    }

    // ---------- Entering a bill ----------

    /** Everything typed on the bill form, so it can be put back unchanged. */
    private data class BillDraft(
        val supplierName: String,
        val billNumber: String?,
        val total: Double?,
        val billDate: Long,
        val dueDate: Long?,
        /** How it was paid now: null on credit (udhaar), else a PaymentMethod key ("cash" takes it from the drawer). */
        val paidWith: String?,
        val note: String?
    )

    /**
     * [restore] reopens the form exactly as the owner left it (with the items
     * already added still pending) when a save could not go through: nothing
     * he typed is ever thrown away. A first-time supplier no longer stops the
     * form opening either; the name is offered as a new supplier on Save.
     */
    private fun startNewBill(prefillSupplier: String? = null, restore: BillDraft? = null) {
        if (restore == null) {
            pendingItems.clear()
            dropKeptPhoto()           // left over from a bill that was never saved
        }

        val view = layoutInflater.inflate(R.layout.dialog_add_bill, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etSupplier: AutoCompleteTextView = view.findViewById(R.id.etBillSupplier)
        val etNumber: EditText = view.findViewById(R.id.etBillNumber)
        val etTotal: EditText = view.findViewById(R.id.etBillTotal)
        val btnDate: MaterialButton = view.findViewById(R.id.btnBillDate)
        val btnDue: MaterialButton = view.findViewById(R.id.btnBillDue)
        val cgPaid: com.google.android.material.chip.ChipGroup = view.findViewById(R.id.cgBillPaid)
        val tvEffect: TextView = view.findViewById(R.id.tvBillLedgerEffect)
        val etNote: EditText = view.findViewById(R.id.etBillNote)
        val btnScan: MaterialButton = view.findViewById(R.id.btnBillScan)
        val tvPending: TextView = view.findViewById(R.id.tvBillPendingItems)
        val llItems: LinearLayout = view.findViewById(R.id.llBillItems)

        // Lines already waiting to go with this bill (from a scan, or added
        // before a save that had to be reopened), so the owner can see them.
        renderPendingItems(tvPending, llItems)

        etSupplier.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_dropdown_row,
                parties.map { it.name }
            )
        )
        (restore?.supplierName ?: prefillSupplier)?.let { etSupplier.setText(it, false) }
        restore?.billNumber?.let { etNumber.setText(it) }
        restore?.total?.let { etTotal.setText(com.innovation313.roshankhata.ui.Calc.trim(it)) }
        restore?.note?.let { etNote.setText(it) }
        // How it was settled (4 Nov 2026): On credit first and chosen, as most supplier bills are; paid now in cash
        // (the drawer), or by bank, wallet, cheque or online (not the drawer). The old single "paid in cash" tick was
        // easy to leave on by mistake, and then the bill was "Complete" and its total came out of the drawer.
        val paidKeys = HashMap<Int, String?>()
        (listOf<String?>(null) + PaymentMethod.ALL).forEach { key ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, cgPaid, false) as com.google.android.material.chip.Chip
            chip.id = View.generateViewId()
            chip.text = if (key == null) getString(R.string.bill_paid_credit)
                else getString(R.string.bill_paid_now, PaymentMethod.labelOf(this, key) ?: key)
            paidKeys[chip.id] = key
            cgPaid.addView(chip)
            if (key == restore?.paidWith) cgPaid.check(chip.id)
        }
        fun paidWith(): String? = paidKeys[cgPaid.checkedChipId]
        // The old "tick this only if…" help spoke of a tick; the line under the choice now says what each one does.
        view.findViewById<TextView>(R.id.tvPaidCashHelp).visibility = View.GONE

        var billDate = restore?.billDate ?: System.currentTimeMillis()
        var dueDate: Long? = restore?.dueDate

        btnDate.text = getString(R.string.bill_date_set, Format.dateOnly(billDate))
        dueDate?.let { btnDue.text = getString(R.string.due_date_set, Format.dateOnly(it)) }

        btnDate.setOnClickListener {
            pickDate(billDate) { picked ->
                billDate = picked
                btnDate.text = getString(R.string.bill_date_set, Format.dateOnly(picked))
            }
        }

        btnDue.setOnClickListener {
            pickDate(System.currentTimeMillis()) { picked ->
                dueDate = picked
                btnDue.text = getString(R.string.due_date_set, Format.dateOnly(picked))
            }
        }

        // Say what the bill is about to do to the ledger, before it does it.
        fun refreshEffect() {
            val amount = com.innovation313.roshankhata.ui.Calc.evalAmount(etTotal.text.toString())
            val supplier = etSupplier.text.toString().trim()

            val how = paidWith()
            if (amount != null && amount > 0 && supplier.isNotEmpty()) {
                tvEffect.visibility = View.VISIBLE
                tvEffect.text = when (how) {
                    null -> getString(R.string.bill_credit_note, Format.money(amount), supplier)
                    PaymentMethod.CASH -> getString(R.string.bill_cash_note, Format.money(amount))
                    else -> getString(R.string.bill_other_note, Format.money(amount), supplier, PaymentMethod.labelOf(this, how) ?: how)
                }
            } else {
                tvEffect.visibility = View.GONE
            }
        }

        // After the first check (lesson 4): the listener only follows the chip.
        cgPaid.setOnCheckedStateChangeListener { _, _ -> refreshEffect() }
        etTotal.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) refreshEffect() }
        etSupplier.setOnItemClickListener { _, _, _, _ -> refreshEffect() }
        if (restore != null) refreshEffect()

        // The total on the app's own calculator pad (21 Oct). The pad keeps
        // the focus in the box, so the ledger line also follows the typing
        // rather than waiting for the focus to leave.
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = view,
            pad = view.findViewById(R.id.billKeypad),
            fields = listOf(etTotal),
            results = mapOf(etTotal to view.findViewById<TextView>(R.id.tvBillTotalResult))
        )
        etTotal.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = refreshEffect()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val form = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_bill)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.add_item) { _, _ ->
                // Items are collected first, then the bill is saved with them.
                collectItems(
                    supplierName = etSupplier.text.toString().trim(),
                    billNumber = etNumber.text.toString().trim().ifEmpty { null },
                    total = com.innovation313.roshankhata.ui.Calc.evalAmount(etTotal.text.toString()),
                    billDate = billDate,
                    dueDate = dueDate,
                    paidWith = paidWith(),
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .setPositiveButton(R.string.save) { _, _ ->
                saveBill(
                    supplierName = etSupplier.text.toString().trim(),
                    billNumber = etNumber.text.toString().trim().ifEmpty { null },
                    total = com.innovation313.roshankhata.ui.Calc.evalAmount(etTotal.text.toString()),
                    billDate = billDate,
                    dueDate = dueDate,
                    paidWith = paidWith(),
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .show().also { com.innovation313.roshankhata.ui.CalcPad.dock(it, view, view.findViewById(R.id.billKeypad)); com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }

        // Back, a tap beside the form or Cancel threw a typed bill away in
        // silence (the owner's phone, 4 Nov 2026). An untouched form still
        // closes at once; anything typed asks first.
        val blankDate = billDate
        guardTyped(
            dialog = form,
            title = R.string.bill_discard_title,
            message = R.string.bill_discard_msg,
            typed = {
                etSupplier.text.toString().trim() != prefillSupplier.orEmpty().trim() ||
                    etNumber.text.isNotBlank() || etTotal.text.isNotBlank() || etNote.text.isNotBlank() ||
                    paidWith() != null || dueDate != null || billDate != blankDate ||
                    pendingItems.isNotEmpty() || scanKeptPhoto != null
            },
            onDiscard = {
                pendingItems.clear()
                dropKeptPhoto()
            }
        )

        btnScan.setOnClickListener {
            val draft = BillDraft(
                supplierName = etSupplier.text.toString().trim(),
                billNumber = etNumber.text.toString().trim().ifEmpty { null },
                total = com.innovation313.roshankhata.ui.Calc.evalAmount(etTotal.text.toString()),
                billDate = billDate,
                dueDate = dueDate,
                paidWith = paidWith(),
                note = etNote.text.toString().trim().ifEmpty { null }
            )
            form.dismiss()
            chooseScanSource(draft)
        }
    }

    /**
     * Asks before a bill form closes with anything typed in it (4 Nov 2026):
     * Back and Cancel ask "Discard?" (Keep editing / Discard) when [typed] says
     * something is there, and close at once when nothing is; a tap beside the
     * form no longer closes it at all. A callback on the dialog's own
     * dispatcher, not onBackPressed/KEYCODE_BACK, so it also works on
     * Android 16+ (as the add-entry form's details step does).
     */
    private fun guardTyped(
        dialog: androidx.appcompat.app.AlertDialog,
        title: Int,
        message: Int,
        typed: () -> Boolean,
        onDiscard: () -> Unit = {}
    ) {
        dialog.setCanceledOnTouchOutside(false)
        fun leave() {
            if (!typed()) {
                dialog.dismiss()
                return
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.bp_leave_keep, null)
                .setPositiveButton(R.string.bp_leave_discard) { _, _ ->
                    onDiscard()
                    dialog.dismiss()
                }
                .show()
        }
        dialog.onBackPressedDispatcher.addCallback(object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener { leave() }
    }

    /**
     * The bill's waiting product lines, shown on the form itself: name,
     * quantity x rate = amount, batch and expiry, each tappable to change or
     * remove before saving. Until now a scan's lines were only counted, so
     * the owner could not see — or correct — what had been read.
     */
    private fun renderPendingItems(tvPending: TextView, llItems: LinearLayout) {
        val notes = buildList {
            if (pendingItems.isNotEmpty()) add(getString(
                R.string.bill_scan_items_ready,
                Digits.quantity(resources, R.plurals.items_count, pendingItems.size, pendingItems.size)
            ))
            if (scanKeptPhoto != null) add(getString(R.string.bill_scan_photo_kept))
            if (pendingItems.isNotEmpty()) add(getString(R.string.bill_items_tap_hint))
        }
        tvPending.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        tvPending.text = notes.joinToString("\n")

        llItems.removeAllViews()
        llItems.visibility = if (pendingItems.isEmpty()) View.GONE else View.VISIBLE
        val dp = resources.displayMetrics.density
        pendingItems.forEachIndexed { index, item ->
            val row = TextView(this).apply {
                text = buildString {
                    append(item.productName)
                    append("\n")
                    val rate = item.rate
                    if (rate != null) {
                        append(getString(
                            R.string.bill_item_figures, Format.plain(item.quantity),
                            Format.money(rate), Format.money(item.quantity * rate)
                        ))
                    } else {
                        append(Format.plain(item.quantity))
                    }
                    item.unit?.let { append(" ").append(UnitWords.label(it)) }
                    item.batchNumber?.let { append("\n").append(getString(R.string.batch_label, it)) }
                    item.expiryDate?.let { append("\n").append(getString(R.string.bill_scan_expiry, Format.dateOnly(it))) }
                }
                textSize = 13f
                setTextColor(getColor(R.color.ink))
                setBackgroundResource(R.drawable.bg_editor_card)
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    ChoiceSheet.show(
                        this@BillsActivity, item.productName,
                        listOf(ChoiceSheet.edit(getString(R.string.edit_item)), ChoiceSheet.delete(getString(R.string.delete_item)))
                    ) { which ->
                        if (which == 0) {
                            showAddItemDialog(existing = item) { changed ->
                                val at = pendingItems.indexOf(item)
                                if (at >= 0) pendingItems[at] = changed
                                renderPendingItems(tvPending, llItems)
                            }
                        } else {
                            pendingItems.remove(item)
                            renderPendingItems(tvPending, llItems)
                        }
                    }
                }
            }
            llItems.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = if (index == 0) 0 else (6 * dp).toInt() })
        }
    }

    // ---------- Scan bill ----------

    private fun chooseScanSource(draft: BillDraft) {
        scanDraft = draft
        scanFlow.chooseSource()
    }

    /** The form again, exactly as it was before the scan was started. */
    private fun reopenFromScan(draft: BillDraft? = scanDraft) {
        scanDraft = null
        startNewBill(restore = draft ?: BillDraft("", null, null, System.currentTimeMillis(), null, null, null))
    }

    private fun readBillPhoto(photo: android.net.Uri, temp: java.io.File?) {
        lifecycleScope.launch {
            val known = runCatching { dao.productsOnce().map { it.name } }.getOrDefault(emptyList())
            val suppliers = parties.filter { !it.isCustomer }.map { it.name }
            when (val outcome = BillScanFlow.read(this@BillsActivity, photo, temp, known, suppliers)) {
                is BillScanFlow.Outcome.Found -> showScanReview(outcome.bill, outcome.keptPhoto)
                else -> BillScanFlow.showProblem(this@BillsActivity, outcome) { reopenFromScan() }
            }
        }
    }

    /**
     * What was read, checked by the owner (BillScanFlow.review); the ticked
     * lines go into the pending items and only blank fields are filled.
     */
    private fun showScanReview(bill: ScannedBill, keptPhoto: String?) {
        BillScanFlow.review(
            activity = this,
            bill = bill,
            keptPhoto = keptPhoto,
            showSupplier = true,
            onUse = { kept ->
                if (keptPhoto != null) {
                    dropKeptPhoto()          // a newer scan replaces an earlier one
                    scanKeptPhoto = keptPhoto
                }
                val draft = scanDraft ?: BillDraft("", null, null, System.currentTimeMillis(), null, null, null)
                kept.forEach { item ->
                    pendingItems.add(
                        BillItem(
                            billId = 0,
                            productName = item.name,
                            batchNumber = item.batch,
                            expiryDate = item.expiry,
                            quantity = item.quantity,
                            unit = item.unit,
                            rate = item.rate
                        )
                    )
                }
                // Only blanks are filled: anything the owner typed stays theirs.
                // The bill's own total if it was read, else the ticked lines'.
                val merged = draft.copy(
                    supplierName = draft.supplierName.ifBlank { bill.supplierName.orEmpty() },
                    billNumber = draft.billNumber ?: bill.billNumber,
                    total = draft.total ?: bill.total ?: kept.sumOf { it.amount }.takeIf { it > 0 },
                    billDate = bill.billDate ?: draft.billDate
                )
                reopenFromScan(merged)
            },
            onCancel = { reopenFromScan() }
        )
    }

    /** Add items one at a time, then save the bill with all of them. */
    private fun collectItems(
        supplierName: String,
        billNumber: String?,
        total: Double?,
        billDate: Long,
        dueDate: Long?,
        paidWith: String?,
        note: String?
    ) {
        // The bill typed so far: leaving the item form or the "item added" box
        // goes back to it rather than losing it (4 Nov 2026).
        val draft = BillDraft(supplierName, billNumber, total, billDate, dueDate, paidWith, note)
        showAddItemDialog(onClosed = { startNewBill(restore = draft) }) { item ->
            pendingItems.add(item)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.item_added)
                .setOnCancelListener { startNewBill(restore = draft) }
                .setMessage(Digits.quantity(resources, 
                    R.plurals.items_count, pendingItems.size, pendingItems.size
                ))
                .setNeutralButton(R.string.add_item) { _, _ ->
                    collectItems(supplierName, billNumber, total, billDate, dueDate, paidWith, note)
                }
                .setPositiveButton(R.string.save) { _, _ ->
                    saveBill(supplierName, billNumber, total, billDate, dueDate, paidWith, note)
                }
                .show()
        }
    }

    /**
     * The one form for both adding a new item and editing an existing one.
     * [existing] pre-fills every field and its id/note/productId ride through
     * unchanged on [BillItem.copy] — only what this form actually shows can
     * be changed by using it.
     */
    private fun showAddItemDialog(existing: BillItem? = null, onClosed: (() -> Unit)? = null, onDone: (BillItem) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_add_bill_item, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etProduct: AutoCompleteTextView = view.findViewById(R.id.etProductName)
        val etBatch: EditText = view.findViewById(R.id.etBatchNumber)
        val btnExpiry: MaterialButton = view.findViewById(R.id.btnExpiry)
        val etQty: EditText = view.findViewById(R.id.etItemQty)
        val etUnit: AutoCompleteTextView = view.findViewById(R.id.etItemUnit)
        // "Unit" alone (30 Oct 2026): the trade's examples were cut at this half-width box's edge.
        // A tap on the empty box lists the units, the shop's own first.
        etUnit.hint = getString(R.string.unit_short)
        etUnit.setOnClickListener { if (etUnit.text.isNullOrBlank()) etUnit.showDropDown() }
        val etRate: EditText = view.findViewById(R.id.etItemRate)

        etUnit.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_dropdown_row,
                TradeVocab.unitChoices(this)
            )
        )

        // Products already known to the shop, offered while the name is
        // typed. This matters more on a bill than anywhere else: a supplier's
        // delivery is where a product first enters the book, and a name
        // spelled a second way here creates a second product that the sale
        // side will never find.
        lifecycleScope.launch {
            SmartSuggest.attach(etProduct, dao.productsOnce().withStarters(this@BillsActivity))
        }

        var expiry: Long? = existing?.expiryDate

        if (existing != null) {
            // Filtering off for a pre-fill: setText on a suggestion field asks the
            // adapter to match, and an Edit Item dialog would open with the dropdown
            // already covering the fields it just filled in.
            etProduct.setText(existing.productName, false)
            etBatch.setText(existing.batchNumber ?: "")
            etQty.setText(Format.plain(existing.quantity))
            etUnit.setText(UnitWords.label(existing.unit), false)
            etRate.setText(existing.rate?.let { Format.plain(it) } ?: "")
            expiry?.let { btnExpiry.text = getString(R.string.bill_scan_expiry, Format.dateOnly(it)) }
        }

        // As filled, to tell an item he changed from one he only opened.
        val loadedItem = listOf(etProduct, etBatch, etQty, etUnit, etRate).map { it.text.toString() }

        btnExpiry.setOnClickListener {
            // Opens on TODAY, not a year ahead.
            //
            // It used to jump forward twelve months on the reasoning that
            // pesticide carries a long shelf life. That was backwards. The
            // stock that matters most is the stock expiring SOON — the drum
            // with two months left, the one that can still be sold or returned.
            // Opening a year out buried exactly that case behind a scroll back,
            // and made the easy stock easy while making the urgent stock hard.
            //
            // Today is also simply where a person's thumb expects to land.
            pickDate(expiry ?: System.currentTimeMillis()) { picked ->
                expiry = picked
                btnExpiry.text = getString(R.string.bill_scan_expiry, Format.dateOnly(picked))
            }
        }

        var done = false
        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_item else R.string.edit_item)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val product = etProduct.text.toString().trim()
                if (product.isEmpty()) {
                    Toast.makeText(this, R.string.enter_product, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                done = true

                val qty = Digits.parse(etQty.text) ?: 1.0

                val base = existing ?: BillItem(billId = 0, productName = product, quantity = qty)
                onDone(
                    base.copy(
                        productName = product,
                        batchNumber = etBatch.text.toString().trim().ifEmpty { null },
                        expiryDate = expiry,
                        quantity = qty,
                        unit = UnitWords.canonical(etUnit.text.toString()),
                        rate = Digits.parse(etRate.text)
                    )
                )
            }
            .show().also { itemForm ->
                com.innovation313.roshankhata.ui.KeyboardFollow.dialog(itemForm)
                guardTyped(
                    dialog = itemForm,
                    title = R.string.bp_leave_title,
                    message = R.string.bp_leave_msg,
                    typed = { listOf(etProduct, etBatch, etQty, etUnit, etRate).map { it.text.toString() } != loadedItem || expiry != existing?.expiryDate }
                )
                // Closed without an item (Cancel, Back, Discard): the caller's form comes back.
                if (onClosed != null) itemForm.setOnDismissListener { if (!done && !isFinishing && !isDestroyed) onClosed() }
            }
    }

    private fun saveBill(
        supplierName: String,
        billNumber: String?,
        total: Double?,
        billDate: Long,
        dueDate: Long?,
        paidWith: String?,
        note: String?
    ) {
        // The dialog has already closed by the time this runs, so every stop
        // below reopens the form as it was rather than losing the bill.
        val draft = BillDraft(supplierName, billNumber, total, billDate, dueDate, paidWith, note)

        if (supplierName.isEmpty()) {
            Toast.makeText(this, R.string.enter_supplier, Toast.LENGTH_LONG).show()
            startNewBill(restore = draft)
            return
        }

        // Checked before a new supplier is offered, so a supplier is never
        // created for a bill that then cannot be saved.
        if (total == null || total <= 0.0) {
            Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
            startNewBill(restore = draft)
            return
        }

        val supplier = parties.firstOrNull { it.name.equals(supplierName, ignoreCase = true) }

        // A name not in the khata yet: the owner's first bill from a new
        // supplier. Offer to add them here rather than send him off to make
        // the party first and type the whole bill again.
        if (supplier == null) {
            // A camera misreading one letter ("Sincrop" for "Suncrop") must
            // not quietly start a second khata for the same company. The
            // closest existing supplier is OFFERED, never chosen for him.
            val similar = com.innovation313.roshankhata.data.BillScan.similarKnown(
                supplierName, parties.filter { !it.isCustomer }.map { it.name }
            )?.let { name -> parties.firstOrNull { it.name == name } }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bill_new_supplier_title)
                .setMessage(
                    getString(R.string.bill_new_supplier_msg, supplierName) +
                        (similar?.let { "\n\n" + getString(R.string.bill_similar_supplier, it.name) } ?: "")
                )
                .setNegativeButton(R.string.back) { _, _ -> startNewBill(restore = draft) }
                .setOnCancelListener { startNewBill(restore = draft) }
                .apply {
                    if (similar != null) setNeutralButton(getString(R.string.bill_use_existing, similar.name)) { _, _ ->
                        dispatchSaveBill(similar.id, null, billNumber, total, billDate, dueDate, paidWith, note)
                    }
                }
                .setPositiveButton(R.string.bill_new_supplier_yes) { _, _ ->
                    dispatchSaveBill(null, supplierName, billNumber, total, billDate, dueDate, paidWith, note)
                }
                .show()
            return
        }

        // The name matched a party saved as a CUSTOMER. That may be exactly
        // right — one man can be both the shop's customer and its supplier —
        // or it may be a same-name slip about to put a supplier's debt on a
        // customer's account. Only the owner knows which, so it asks, the
        // same way the duplicate-name check asks when adding a party. The
        // promotion list filters strictly by this flag, so a bill quietly
        // attached to the wrong kind of party would also quietly change who
        // gets promotional messages.
        if (supplier.isCustomer) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bill_to_customer_title)
                .setMessage(getString(R.string.bill_to_customer_warn, supplier.name))
                .setNegativeButton(R.string.cancel) { _, _ -> startNewBill(restore = draft) }
                .setOnCancelListener { startNewBill(restore = draft) }
                .setPositiveButton(R.string.proceed_anyway) { _, _ ->
                    dispatchSaveBill(supplier.id, null, billNumber, total, billDate, dueDate, paidWith, note)
                }
                .show()
            return
        }

        dispatchSaveBill(supplier.id, null, billNumber, total, billDate, dueDate, paidWith, note)
    }

    /**
     * [supplierId] is the saved supplier; when it is null, [newSupplierName]
     * is added as a supplier first, in the same background job as the bill,
     * so the party and its bill are written together.
     */
    private fun dispatchSaveBill(
        supplierId: Long?,
        newSupplierName: String?,
        billNumber: String?,
        total: Double,
        billDate: Long,
        dueDate: Long?,
        paidWith: String?,
        note: String?
    ) {
        // The three writes below run on AppScope, not lifecycleScope — a
        // bill is money (the ledger entry), paperwork (the bill row), and
        // batch records (the items), and a quick Back press right after
        // tapping Save must not be able to cancel any of them partway
        // through. See AppScope's own comment.
        //
        // pendingItems is read and cleared here, on the caller's own
        // lifecycleScope-backed thread, BEFORE the AppScope block below is
        // even built — so a second bill started immediately after this one
        // can never see items meant for this one still sitting in the list.
        val itemsForThisBill = pendingItems.toList()
        pendingItems.clear()
        val photoForThisBill = scanKeptPhoto
        scanKeptPhoto = null
        // Read on the main thread, before the background save: who sent the
        // goods, for the label-details suggestions afterwards.
        val supplierLabel = newSupplierName ?: parties.firstOrNull { it.id == supplierId }?.name

        AppScope.launch {
            // A new supplier: reuse one of the same name if it appeared in the
            // meantime (a second tap), otherwise add it as a supplier.
            val partyId = supplierId
                ?: dao.findPartyByName(newSupplierName.orEmpty())?.id
                ?: dao.insertParty(Party(name = newSupplierName.orEmpty().trim(), isCustomer = false))

            // ONE ledger entry, and only when the stock was taken on credit.
            //
            // isGiven = false means money owed BY me TO them — the supplier's
            // balance goes negative, which is exactly what "I owe them" means
            // in this ledger's terms.
            //
            // Paid in cash means nothing is owed, so nothing belongs in the
            // ledger against them. Writing an entry anyway and immediately
            // cancelling it would leave two phantom rows in their account.
            // The debt, the bill and its items go in as ONE transaction
            // (dao.insertSupplierBill): all saved, or none — never a debt in
            // the supplier's khata with no bill behind it.
            //
            // The ledger entry exists only when the stock was taken on credit.
            // isGiven = false means money owed BY me TO them — the supplier's
            // balance goes negative, which is exactly what "I owe them" means
            // in this ledger's terms. Paid in cash means nothing is owed, so
            // nothing belongs in the ledger against them.
            //
            // Each item's product is born the moment its name first appears on
            // a bill (findOrCreateProduct is idempotent and restores a deleted
            // product of the same name rather than inserting a twin), so a sale
            // of it can offer its batches at once, with no backfill run needed.
            val createdProducts = dao.insertSupplierBill(
                // Cash now: no entry (the drawer pays it; billsAndStaffCashBetween counts it). On credit: the debt.
                // By bank, wallet, cheque or online: the debt and its payment together (paidBy), so the supplier's
                // khata shows both and the drawer neither.
                entry = if (paidWith == PaymentMethod.CASH) null else LedgerEntry(
                    partyId = partyId,
                    amount = total,
                    isGiven = false,
                    note = billNumber?.let { getString(R.string.bill_entry_note, it) } ?: note,
                    entryNumber = "",
                    billPhotoPath = photoForThisBill
                ),
                bill = SupplierBill(
                    partyId = partyId,
                    billNumber = billNumber,
                    totalAmount = total,
                    billDate = billDate,
                    dueDate = dueDate,
                    ledgerEntryId = null,
                    isPaidInFull = paidWith != null,
                    note = note,
                    // Cash: no khata entry, so the bill keeps its own picture.
                    photoPath = if (paidWith == PaymentMethod.CASH) photoForThisBill else null
                ),
                items = itemsForThisBill,
                paidBy = paidWith?.takeIf { it != PaymentMethod.CASH }
            )

            // The toast touches a screen the owner may already have left, so
            // it hops back to the main thread and is shown only if this
            // Activity is still the one on screen. The bill is saved either
            // way; the toast is a courtesy, not a condition of saving.
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@BillsActivity,
                        if (paidWith == PaymentMethod.CASH) R.string.bill_saved else R.string.bill_saved_credit,
                        Toast.LENGTH_LONG
                    ).show()

                    // THE LABEL IS IN THEIR HAND RIGHT NOW. Company, technical
                    // name, formulation and Reg# are facts about the product
                    // rather than about this bill, so they are not bill fields
                    // — filling them per bill would mean retyping them every
                    // time and letting two bills disagree about one product.
                    // But the only moment they are easy to answer is while the
                    // bottle is on the counter, so this is where they are
                    // asked for, once, after the bill is safely saved.
                    // What this bill says about each product (formulation, type,
                    // unit, the supplier as company), offered in the label form.
                    val guesses = createdProducts.distinctBy { it.id }.associate { product ->
                        val line = itemsForThisBill.firstOrNull {
                            com.innovation313.roshankhata.data.ProductName.key(it.productName) == product.nameKey
                        }
                        product.id to com.innovation313.roshankhata.data.LabelGuess.of(
                            product.name, line?.unit, supplierLabel,
                            com.innovation313.roshankhata.data.Trade.current(this@BillsActivity)
                        )
                    }
                    offerLabelDetails(createdProducts.distinctBy { it.id }, guesses)
                }
            }
        }
    }

    /**
     * Ask, once, for the label details of the products this bill just created
     * or touched — and only for the ones actually missing something.
     *
     * WHY IT IS A PROMPT AND NOT A FIELD. The four compliance answers belong
     * to the product, not to the bill: sulfur's company and registration
     * number are the same on the tenth bill as on the first. Put them in the
     * bill form and they get retyped every time, the form gets longer for the
     * one thing done daily, and two bills are free to disagree about one
     * product — which is exactly the contradiction the register would then
     * print. Put them only on the Products screen and they are answered
     * months later, or never, because the label has long since gone.
     *
     * So: the bill stays short, the product keeps the facts, and the asking
     * happens here — after the save, never before it. Nothing about this can
     * affect whether the bill was recorded; by the time this runs the bill,
     * its items and its ledger entry are already written.
     *
     * "Missing" is [ProductDetailsDialog.needsLabelDetails], which is the
     * register's own rule rather than a second one invented here.
     */
    private fun offerLabelDetails(
        products: List<Product>,
        guesses: Map<Long, com.innovation313.roshankhata.data.LabelGuess.Guess> = emptyMap()
    ) {
        // Only where an inspector reads labels (agri, medical): any other trade's form has no such fields, so
        // the question could never be answered and came after every bill (31 Oct 2026, the review).
        if (!com.innovation313.roshankhata.data.TradeFeatures.inspectorReports(this)) return
        val incomplete = products.filter { ProductDetailsDialog.needsLabelDetails(it) }
        if (incomplete.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_details_title)
            .setMessage(
                getString(
                    R.string.label_details_ask,
                    incomplete.joinToString(", ") { it.name }
                )
            )
            // Later is a real answer, not a nag postponed: the register keeps
            // listing these under "label details still missing", so nothing is
            // lost by saying no here.
            .setNegativeButton(R.string.label_details_later, null)
            .setPositiveButton(R.string.label_details_fill) { _, _ ->
                fillLabelDetails(incomplete, 0, guesses)
            }
            .show()
    }

    /**
     * One product at a time, in order, each dialog opening the next when it
     * closes — saved or skipped.
     *
     * Recursion rather than a loop because the dialogs are asynchronous: a
     * for-loop would stack every dialog on screen at once, and the owner would
     * be filling the last product's form first.
     */
    private fun fillLabelDetails(
        queue: List<Product>,
        index: Int,
        guesses: Map<Long, com.innovation313.roshankhata.data.LabelGuess.Guess> = emptyMap()
    ) {
        if (index < 0 || index >= queue.size || isFinishing || isDestroyed) return
        val next = { fillLabelDetails(queue, index + 1, guesses) }
        lifecycleScope.launch {
            // Read fresh: after Back, the owner must see what he saved a
            // moment ago, not the copy the queue was built from.
            val product = dao.productById(queue[index].id) ?: queue[index]
            if (isFinishing || isDestroyed) return@launch
            ProductDetailsDialog.show(
                activity = this@BillsActivity,
                product = product,
                dao = dao,
                onSaved = next,
                onDismissed = next,
                guess = guesses[product.id],
                step = ProductDetailsDialog.Step(
                    position = index,
                    total = queue.size,
                    onBack = if (index > 0) ({ fillLabelDetails(queue, index - 1, guesses) }) else null
                )
            )
        }
    }

    // ---------- Viewing / deleting ----------

    private fun showBillActions(bill: BillSummary) {
        lifecycleScope.launch {
            // A cash bill may carry its own photo (v21); offer it only then.
            val photo = dao.getBill(bill.id)?.photoPath?.takeIf { it.isNotBlank() }
            // A bill saved as paid in cash can be put on credit (4 Nov 2026): the owner's own correction.
            val cashBill = bill.isPaidInFull && bill.ledgerEntryId == null
            val actions = listOfNotNull<Pair<ChoiceSheet.Option, () -> Unit>>(
                ChoiceSheet.edit(getString(R.string.edit_bill)) to { startEditBill(bill) },
                ChoiceSheet.batch(getString(R.string.manage_items)) to { manageItems(bill.id) },
                if (cashBill) ChoiceSheet.edit(getString(R.string.bill_to_credit)) to { confirmBillToCredit(bill) } else null,
                ChoiceSheet.delete(getString(R.string.delete_bill)) to { confirmDeleteBill(bill) },
                photo?.let { p -> ChoiceSheet.photo(getString(R.string.view_bill_photo)) to { showBillPhoto(p) } }
            )

            ChoiceSheet.show(this@BillsActivity, getString(R.string.bill_actions), actions.map { it.first }) { which ->
                actions.getOrNull(which)?.second?.invoke()
            }
        }
    }

    /**
     * "This bill was not paid in cash": its total goes into the supplier's khata as owed, and the cash drawer stops
     * counting it (KhataDao.billCashToCredit). Asked first; a manager's PIN where editing a bill needs one.
     */
    private fun confirmBillToCredit(bill: BillSummary) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmBillToCredit(bill) }) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bill_to_credit)
            .setMessage(getString(R.string.bill_to_credit_msg, Format.money(bill.totalAmount), bill.partyName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.bill_paid_credit) { _, _ ->
                val note = bill.billNumber?.let { getString(R.string.bill_entry_note, it) } ?: bill.note
                AppScope.launch { dao.billCashToCredit(bill.id, note) }
            }
            .show()
    }

    /** The kept picture of a cash bill, full width; decoded off the main thread. */
    private fun showBillPhoto(path: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BillPhoto.load(path) }
            if (isFinishing || isDestroyed) return@launch
            if (bitmap == null) {
                Toast.makeText(this@BillsActivity, R.string.bill_photo_missing, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val image = android.widget.ImageView(this@BillsActivity).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                contentDescription = getString(R.string.bill_photo_label)
            }
            MaterialAlertDialogBuilder(this@BillsActivity)
                .setTitle(R.string.bill_photo_label)
                .setView(android.widget.ScrollView(this@BillsActivity).apply { addView(image) })
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // ---------- Editing an existing bill ----------

    /**
     * The supplier and the paid-cash status cannot be changed from here — see
     * the strings shown for why. Everything else about a bill can be a typo:
     * a wrong number, a wrong total, a wrong date. Correcting those is what
     * this is for.
     */
    private fun startEditBill(billSummary: BillSummary) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { startEditBill(billSummary) }) return
        lifecycleScope.launch {
            val existing = dao.getBill(billSummary.id) ?: return@launch
            showEditBillDialog(existing, billSummary.partyName)
        }
    }

    private fun showEditBillDialog(existing: SupplierBill, supplierName: String) {
        val view = layoutInflater.inflate(R.layout.dialog_add_bill, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etSupplier: AutoCompleteTextView = view.findViewById(R.id.etBillSupplier)
        val etNumber: EditText = view.findViewById(R.id.etBillNumber)
        val etTotal: EditText = view.findViewById(R.id.etBillTotal)
        val btnDate: MaterialButton = view.findViewById(R.id.btnBillDate)
        val btnDue: MaterialButton = view.findViewById(R.id.btnBillDue)
        val cgPaid: com.google.android.material.chip.ChipGroup = view.findViewById(R.id.cgBillPaid)
        val tvPaidCashHelp: TextView = view.findViewById(R.id.tvPaidCashHelp)
        val tvEffect: TextView = view.findViewById(R.id.tvBillLedgerEffect)
        val etNote: EditText = view.findViewById(R.id.etBillNote)

        // Reassigning a bill to a different supplier would mean moving a real
        // debt from one party's account to another's — a different and much
        // riskier operation than correcting this bill's own details, so it is
        // not offered here.
        etSupplier.setText(supplierName)
        etSupplier.isEnabled = false

        etNumber.setText(existing.billNumber ?: "")
        etTotal.setText(Format.plain(existing.totalAmount))
        etNote.setText(existing.note ?: "")

        // The corrected total on the app's own calculator pad (21 Oct).
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = view,
            pad = view.findViewById(R.id.billKeypad),
            fields = listOf(etTotal),
            results = mapOf(etTotal to view.findViewById<TextView>(R.id.tvBillTotalResult))
        )

        var billDate = existing.billDate
        var dueDate = existing.dueDate

        btnDate.text = getString(R.string.bill_date_set, Format.dateOnly(billDate))
        dueDate?.let { btnDue.text = getString(R.string.due_date_set, Format.dateOnly(it)) }

        btnDate.setOnClickListener {
            pickDate(billDate) { picked ->
                billDate = picked
                btnDate.text = getString(R.string.bill_date_set, Format.dateOnly(picked))
            }
        }
        btnDue.setOnClickListener {
            pickDate(dueDate ?: System.currentTimeMillis()) { picked ->
                dueDate = picked
                btnDue.text = getString(R.string.due_date_set, Format.dateOnly(picked))
            }
        }

        // Whether this bill was paid in cash or on credit cannot be changed
        // here either. Toggling it would mean either inventing a debt that
        // was never owed, or erasing one that really was — and the ledger
        // entry, if there is one, is not touched by this screen at all
        // except to keep its amount in step. Settling a debt happens by
        // recording a payment in the ledger, not by editing this checkbox.
        // Shown as it was saved, and not changed here (one chip, not a choice).
        val method = if (existing.isPaidInFull && existing.ledgerEntryId == null) PaymentMethod.CASH else null
        cgPaid.addView((layoutInflater.inflate(R.layout.item_rate_list_chip, cgPaid, false) as com.google.android.material.chip.Chip).apply {
            id = View.generateViewId()
            text = when {
                method != null -> getString(R.string.bill_paid_now, PaymentMethod.labelOf(this@BillsActivity, method) ?: method)
                existing.isPaidInFull -> getString(R.string.bill_state_paid)
                else -> getString(R.string.bill_paid_credit)
            }
            isChecked = true
            isEnabled = false
        })
        tvPaidCashHelp.text = getString(R.string.edit_bill_cash_locked)
        tvEffect.visibility = View.GONE

        // As loaded, to tell a corrected bill from an untouched one when he leaves.
        val loadedNumber = etNumber.text.toString()
        val loadedTotal = etTotal.text.toString()
        val loadedNote = etNote.text.toString()
        val loadedDate = billDate
        val loadedDue = dueDate

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_bill)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val total = com.innovation313.roshankhata.ui.Calc.evalAmount(etTotal.text.toString())
                if (total == null || total <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveEditedBill(
                    existing = existing,
                    billNumber = etNumber.text.toString().trim().ifEmpty { null },
                    total = total,
                    billDate = billDate,
                    dueDate = dueDate,
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .show().also { editForm ->
                com.innovation313.roshankhata.ui.CalcPad.dock(editForm, view, view.findViewById(R.id.billKeypad))
                com.innovation313.roshankhata.ui.KeyboardFollow.dialog(editForm)
                guardTyped(
                    dialog = editForm,
                    title = R.string.bp_leave_title,
                    message = R.string.bp_leave_msg,
                    typed = {
                        etNumber.text.toString() != loadedNumber || etTotal.text.toString() != loadedTotal ||
                            etNote.text.toString() != loadedNote || billDate != loadedDate || dueDate != loadedDue
                    }
                )
            }
    }

    /**
     * Runs on AppScope — a quick Back press right after Save must not be able
     * to cancel this partway through. See AppScope's own comment.
     */
    private fun saveEditedBill(
        existing: SupplierBill,
        billNumber: String?,
        total: Double,
        billDate: Long,
        dueDate: Long?,
        note: String?
    ) {
        AppScope.launch {
            // The bill and its khata entry in ONE transaction (21 Oct 2026, the critical review), the
            // computer's own call: THE LEDGER IS THE MONEY, and a failure between the two writes left
            // the bill and the khata saying different amounts. The entry's note is rebuilt as it was
            // when the bill was first saved, so a corrected bill number shows in the ledger too.
            dao.editSupplierBill(
                existing.copy(
                    billNumber = billNumber,
                    totalAmount = total,
                    billDate = billDate,
                    dueDate = dueDate,
                    note = note
                ),
                billNumber?.let { getString(R.string.bill_entry_note, it) } ?: note
            )

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this@BillsActivity, R.string.bill_updated, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    // ---------- Managing an existing bill's items ----------

    private fun manageItems(billId: Long) {
        lifecycleScope.launch {
            val items = dao.billItems(billId)
            showItemsList(billId, items)
        }
    }

    private fun showItemsList(billId: Long, items: List<BillItem>) {
        val labels = items.map { i ->
            buildString {
                append(i.productName)
                append(" — ")
                append(Format.qty(i.quantity, i.unit))
                if (!i.batchNumber.isNullOrBlank()) {
                    append(" · ")
                    append(getString(R.string.batch_label, i.batchNumber))
                }
            }
        }
        // Each line a card - its name, and quantity and batch beneath - and
        // the last card adds a line (10 Oct).
        val choices = labels.map { l ->
            val parts = l.split(" \u2014 ", limit = 2)
            ChoiceSheet.batch(parts[0], parts.getOrNull(1))
        } + ChoiceSheet.add(getString(R.string.add_item))

        ChoiceSheet.show(this, getString(R.string.manage_items), choices) { which ->
            if (which == items.size) {
                showAddItemDialog { newItem ->
                    AppScope.launch {
                        // Same birth rule as saveBill: naming a product on a bill line creates it if it does not
                        // exist yet. Through the DAO's one transaction with its audit row, as the computer does
                        // (3 Nov 2026 audit, M12): two separate writes and no BILL_EDITED row before.
                        dao.saveBillLine(billId, newItem.copy(id = 0L))
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) manageItems(billId)
                        }
                    }
                }
            } else {
                showItemActions(billId, items[which])
            }
        }
    }

    private fun showItemActions(billId: Long, item: BillItem) {
        val options = listOf(
            ChoiceSheet.edit(getString(R.string.edit_item)), ChoiceSheet.delete(getString(R.string.delete_item)),
            ChoiceSheet.buyers(getString(R.string.batch_buyers_action)), ChoiceSheet.sendBack(getString(R.string.return_action))
        )
        ChoiceSheet.show(this, item.productName, options) { which ->
            when (which) {
                2 -> com.innovation313.roshankhata.ui.BatchBuyersDialog.show(this, lifecycleScope, dao, item.id)
                3 -> com.innovation313.roshankhata.ui.BatchReturnDialog.show(this, lifecycleScope, dao, item.id)
                0 -> showAddItemDialog(existing = item) { updated ->
                    AppScope.launch {
                        // The name on the line decides which product the
                        // line is of. Renaming "urea" to "DAP" and leaving
                        // productId pointing at urea would silently count
                        // this stock against the wrong product, so the
                        // link is re-derived from the final name — created
                        // if that product does not exist yet, same as a
                        // new line.
                        // One transaction with its audit row (M12), the computer's saveBillLine.
                        dao.saveBillLine(billId, updated.copy(id = item.id))
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) manageItems(billId)
                        }
                    }
                }
                1 -> confirmDeleteItem(billId, item)
            }
        }
    }

    /**
     * Soft delete, like everywhere else — the batch record is kept, not
     * erased, because it may still be the answer to "which batch did this
     * customer's sale come from" long after the line itself was judged a
     * mistake. This never touches the bill's own ledger entry or its total;
     * if the total should change too, that is done from Edit Bill, not here.
     */
    private fun confirmDeleteItem(billId: Long, item: BillItem) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmDeleteItem(billId, item) }) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_item)
            .setMessage(getString(R.string.delete_item_confirm, item.productName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                AppScope.launch {
                    dao.softDeleteBillItem(item.id)
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) manageItems(billId)
                    }
                }
            }
            .show()
    }

    /**
     * Deleting a bill takes a credit bill's own ledger entry with it — to the
     * Recycle Bin, not away for good. A debt with no bill behind it reads as a
     * phantom amount owed, so the two leave together; the bin keeps the entry
     * restorable for RecycleBinActivity.RETENTION_DAYS in case the delete was
     * a mistake. The dialog and the toast say exactly this (they once claimed
     * the entry stayed, which the code had stopped doing).
     */
    private fun confirmDeleteBill(bill: BillSummary) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmDeleteBill(bill) }) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_bill)
            .setMessage(R.string.delete_bill_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    // Keep the ledger in step — the same "THE LEDGER IS THE
                    // MONEY" rule the edit path follows. A credit bill points at
                    // one ledger entry (its "I owe them" line); deleting the
                    // bill without that entry would leave the debt standing in
                    // the account with no bill behind it, which reads as a
                    // phantom amount owed. So the linked entry goes with it.
                    // A cash bill has no entry (ledgerEntryId is null) and this
                    // simply does nothing.
                    // AppScope + join: the delete must finish even if this
                    // screen closes mid-flight; join keeps the toast honest
                    // by showing it only after the write is truly done.
                    AppScope.launch {
                        // The entry and the bill in ONE transaction, with its BILL_DELETED row (3 Nov 2026 audit,
                        // M12): two writes left the supplier's debt binned while the bill and its stock stayed.
                        dao.deleteSupplierBill(bill.id)
                        // A cash bill has no recycle-bin way back, so its
                        // photo goes now; a credit bill's photo stays on its
                        // binned entry until that is purged.
                        dao.getBill(bill.id)?.photoPath?.let { path ->
                            dao.clearSupplierBillPhoto(bill.id)
                            BillPhoto.delete(path)
                        }
                    }.join()
                    Toast.makeText(
                        this@BillsActivity,
                        R.string.bill_deleted,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    // ---------- Batch trace ----------

    /**
     * Search a batch number back to its source.
     *
     * This is the feature that matters when an inspector asks, or when a sample
     * comes back questioned: which supplier, which bill, which date. From a
     * record — not from memory and a drawer full of paper.
     */
    private fun showTraceDialog() {
        val input = com.innovation313.roshankhata.ui.FormBox.style(EditText(this).apply {
            hint = getString(R.string.trace_batch_hint)
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trace_batch)
            .setMessage(R.string.trace_batch_help)
            .setView(com.innovation313.roshankhata.ui.FormBox.column(this, input))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.search_action) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isEmpty()) return@setPositiveButton

                lifecycleScope.launch {
                    val results = dao.findByBatch(query)

                    val text = if (results.isEmpty()) {
                        getString(R.string.trace_no_results)
                    } else {
                        results.joinToString("\n\n") { r ->
                            buildString {
                                append(r.productName)
                                append("\n")
                                append(getString(R.string.batch_label, r.batchNumber ?: "-"))
                                append("\n")
                                append(
                                    if (r.billNumber.isNullOrBlank()) {
                                        getString(R.string.from_supplier, r.partyName)
                                    } else {
                                        getString(
                                            R.string.from_supplier_bill,
                                            r.partyName,
                                            r.billNumber
                                        )
                                    }
                                )
                                append("\n")
                                append(getString(R.string.expiry_date))
                                append(": ")
                                append(Format.dateOnly(r.expiryDate))
                            }
                        }
                    }

                    val dialog = MaterialAlertDialogBuilder(this@BillsActivity)
                        .setTitle(Digits.quantity(resources, 
                            R.plurals.trace_results, results.size, results.size
                        ))
                        .setPositiveButton(R.string.ok, null)
                    if (results.isEmpty()) {
                        dialog.setMessage(text).show()
                    } else {
                        // Each found batch opens on: who bought it, or send it back (2 Oct).
                        // A card per batch since 10 Oct: its first line on the card,
                        // the rest beneath.
                        val found = text.split("\n\n").map { block ->
                            val lines = block.split("\n", limit = 2)
                            ChoiceSheet.batch(lines[0], lines.getOrNull(1))
                        }
                        ChoiceSheet.show(
                            this@BillsActivity,
                            Digits.quantity(resources, R.plurals.trace_results, results.size, results.size),
                            found
                        ) { i ->
                            com.innovation313.roshankhata.ui.BatchReturnDialog.chooser(
                                this@BillsActivity, lifecycleScope, dao, results[i].itemId
                            )
                        }
                    }
                }
            }
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
    }

    private fun pickDate(startAt: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = startAt }

        BrandDatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0)
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
