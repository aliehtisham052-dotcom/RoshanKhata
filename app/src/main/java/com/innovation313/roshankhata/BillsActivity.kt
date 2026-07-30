package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
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
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.BillSummary
import com.innovation313.roshankhata.data.ExpiryWindow
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.ui.BillAdapter
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

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
class BillsActivity : AppCompatActivity() {

    private lateinit var adapter: BillAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var btnExpiring: MaterialButton

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var parties: List<PartyWithBalance> = emptyList()

    /** Items being collected for the bill currently being entered. */
    private val pendingItems = mutableListOf<BillItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bills)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.tvNoBills)
        btnExpiring = findViewById(R.id.btnExpiring)

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

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            dao.observeBills().collectLatest { list ->
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest { parties = it }
        }

        // The expiry banner only appears when there is something to act on. A
        // permanent red button that usually means nothing would train the owner
        // to ignore it — and then it would be ignored on the day it mattered.
        val cutoff = System.currentTimeMillis() + ExpiryWindow.WARN_MS
        lifecycleScope.launch {
            dao.observeExpiringCount(cutoff).collectLatest { count ->
                if (count > 0) {
                    btnExpiring.visibility = View.VISIBLE
                    btnExpiring.text = getString(R.string.expiring_badge, count)
                } else {
                    btnExpiring.visibility = View.GONE
                }
            }
        }
    }

    // ---------- Entering a bill ----------

    private fun startNewBill() {
        if (parties.isEmpty()) {
            Toast.makeText(this, R.string.enter_supplier, Toast.LENGTH_LONG).show()
            return
        }

        pendingItems.clear()

        val view = layoutInflater.inflate(R.layout.dialog_add_bill, null)
        val etSupplier: AutoCompleteTextView = view.findViewById(R.id.etBillSupplier)
        val etNumber: EditText = view.findViewById(R.id.etBillNumber)
        val etTotal: EditText = view.findViewById(R.id.etBillTotal)
        val btnDate: MaterialButton = view.findViewById(R.id.btnBillDate)
        val btnDue: MaterialButton = view.findViewById(R.id.btnBillDue)
        val cbPaidCash: CheckBox = view.findViewById(R.id.cbPaidCash)
        val tvEffect: TextView = view.findViewById(R.id.tvBillLedgerEffect)
        val etNote: EditText = view.findViewById(R.id.etBillNote)

        etSupplier.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                parties.map { it.name }
            )
        )

        var billDate = System.currentTimeMillis()
        var dueDate: Long? = null

        btnDate.text = getString(R.string.due_date_set, Format.dateOnly(billDate))

        btnDate.setOnClickListener {
            pickDate(billDate) { picked ->
                billDate = picked
                btnDate.text = getString(R.string.due_date_set, Format.dateOnly(picked))
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
            val amount = etTotal.text.toString().trim().toDoubleOrNull()
            val supplier = etSupplier.text.toString().trim()

            if (!cbPaidCash.isChecked && amount != null && amount > 0 && supplier.isNotEmpty()) {
                tvEffect.visibility = View.VISIBLE
                tvEffect.text = getString(
                    R.string.bill_credit_note,
                    Format.money(amount),
                    supplier
                )
            } else {
                tvEffect.visibility = View.GONE
            }
        }

        cbPaidCash.setOnCheckedChangeListener { _, _ -> refreshEffect() }
        etTotal.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) refreshEffect() }
        etSupplier.setOnItemClickListener { _, _, _, _ -> refreshEffect() }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_bill)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.add_item) { _, _ ->
                // Items are collected first, then the bill is saved with them.
                collectItems(
                    supplierName = etSupplier.text.toString().trim(),
                    billNumber = etNumber.text.toString().trim().ifEmpty { null },
                    total = etTotal.text.toString().trim().toDoubleOrNull(),
                    billDate = billDate,
                    dueDate = dueDate,
                    paidCash = cbPaidCash.isChecked,
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .setPositiveButton(R.string.save) { _, _ ->
                saveBill(
                    supplierName = etSupplier.text.toString().trim(),
                    billNumber = etNumber.text.toString().trim().ifEmpty { null },
                    total = etTotal.text.toString().trim().toDoubleOrNull(),
                    billDate = billDate,
                    dueDate = dueDate,
                    paidCash = cbPaidCash.isChecked,
                    note = etNote.text.toString().trim().ifEmpty { null }
                )
            }
            .show()
    }

    /** Add items one at a time, then save the bill with all of them. */
    private fun collectItems(
        supplierName: String,
        billNumber: String?,
        total: Double?,
        billDate: Long,
        dueDate: Long?,
        paidCash: Boolean,
        note: String?
    ) {
        showAddItemDialog { item ->
            pendingItems.add(item)

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.item_added)
                .setMessage(getString(R.string.items_count, pendingItems.size))
                .setNeutralButton(R.string.add_item) { _, _ ->
                    collectItems(supplierName, billNumber, total, billDate, dueDate, paidCash, note)
                }
                .setPositiveButton(R.string.save) { _, _ ->
                    saveBill(supplierName, billNumber, total, billDate, dueDate, paidCash, note)
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
    private fun showAddItemDialog(existing: BillItem? = null, onDone: (BillItem) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_add_bill_item, null)
        val etProduct: EditText = view.findViewById(R.id.etProductName)
        val etBatch: EditText = view.findViewById(R.id.etBatchNumber)
        val btnExpiry: MaterialButton = view.findViewById(R.id.btnExpiry)
        val etQty: EditText = view.findViewById(R.id.etItemQty)
        val etUnit: AutoCompleteTextView = view.findViewById(R.id.etItemUnit)
        val etRate: EditText = view.findViewById(R.id.etItemRate)

        etUnit.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.units)
            )
        )

        var expiry: Long? = existing?.expiryDate

        if (existing != null) {
            etProduct.setText(existing.productName)
            etBatch.setText(existing.batchNumber ?: "")
            etQty.setText(Format.plain(existing.quantity))
            etUnit.setText(existing.unit ?: "", false)
            etRate.setText(existing.rate?.let { Format.plain(it) } ?: "")
            expiry?.let { btnExpiry.text = getString(R.string.due_date_set, Format.dateOnly(it)) }
        }

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
                btnExpiry.text = getString(R.string.due_date_set, Format.dateOnly(picked))
            }
        }

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

                val qty = etQty.text.toString().trim().toDoubleOrNull() ?: 1.0

                val base = existing ?: BillItem(billId = 0, productName = product, quantity = qty)
                onDone(
                    base.copy(
                        productName = product,
                        batchNumber = etBatch.text.toString().trim().ifEmpty { null },
                        expiryDate = expiry,
                        quantity = qty,
                        unit = etUnit.text.toString().trim().ifEmpty { null },
                        rate = etRate.text.toString().trim().toDoubleOrNull()
                    )
                )
            }
            .show()
    }

    private fun saveBill(
        supplierName: String,
        billNumber: String?,
        total: Double?,
        billDate: Long,
        dueDate: Long?,
        paidCash: Boolean,
        note: String?
    ) {
        val supplier = parties.firstOrNull { it.name.equals(supplierName, ignoreCase = true) }
        if (supplier == null) {
            Toast.makeText(this, R.string.enter_supplier, Toast.LENGTH_LONG).show()
            return
        }

        if (total == null || total <= 0.0) {
            Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
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
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.proceed_anyway) { _, _ ->
                    dispatchSaveBill(supplier, billNumber, total, billDate, dueDate, paidCash, note)
                }
                .show()
            return
        }

        dispatchSaveBill(supplier, billNumber, total, billDate, dueDate, paidCash, note)
    }

    private fun dispatchSaveBill(
        supplier: PartyWithBalance,
        billNumber: String?,
        total: Double,
        billDate: Long,
        dueDate: Long?,
        paidCash: Boolean,
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

        AppScope.launch {
            // ONE ledger entry, and only when the stock was taken on credit.
            //
            // isGiven = false means money owed BY me TO them — the supplier's
            // balance goes negative, which is exactly what "I owe them" means
            // in this ledger's terms.
            //
            // Paid in cash means nothing is owed, so nothing belongs in the
            // ledger against them. Writing an entry anyway and immediately
            // cancelling it would leave two phantom rows in their account.
            val ledgerId: Long? = if (paidCash) {
                null
            } else {
                dao.insertEntryNumbered(
                    LedgerEntry(
                        partyId = supplier.id,
                        amount = total,
                        isGiven = false,
                        note = billNumber?.let { "Bill $it" } ?: note,
                        entryNumber = ""
                    )
                )
            }

            val billId = dao.insertBill(
                SupplierBill(
                    partyId = supplier.id,
                    billNumber = billNumber,
                    totalAmount = total,
                    billDate = billDate,
                    dueDate = dueDate,
                    ledgerEntryId = ledgerId,
                    isPaidInFull = paidCash,
                    note = note
                )
            )

            itemsForThisBill.forEach { item ->
                // The product is born HERE, the moment its name first appears
                // on a bill — which is what the Products screen's empty state
                // has promised all along. findOrCreateProduct is idempotent
                // and restores a deleted product of the same name rather than
                // inserting a twin, so typing "Urea" twice can never make two.
                // Tagging productId now also means a sale of this product can
                // offer its batches immediately, with no backfill run needed.
                val product = dao.findOrCreateProduct(
                    name = item.productName,
                    defaultUnit = item.unit
                )
                dao.insertBillItem(item.copy(billId = billId, productId = product.id))
            }

            // The toast touches a screen the owner may already have left, so
            // it hops back to the main thread and is shown only if this
            // Activity is still the one on screen. The bill is saved either
            // way; the toast is a courtesy, not a condition of saving.
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@BillsActivity,
                        if (paidCash) R.string.bill_saved else R.string.bill_saved_credit,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ---------- Viewing / deleting ----------

    private fun showBillActions(bill: BillSummary) {
        val options = arrayOf(
            getString(R.string.edit_bill),
            getString(R.string.manage_items),
            getString(R.string.delete_bill)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bill_actions)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startEditBill(bill)
                    1 -> manageItems(bill.id)
                    2 -> confirmDeleteBill(bill)
                }
            }
            .show()
    }

    // ---------- Editing an existing bill ----------

    /**
     * The supplier and the paid-cash status cannot be changed from here — see
     * the strings shown for why. Everything else about a bill can be a typo:
     * a wrong number, a wrong total, a wrong date. Correcting those is what
     * this is for.
     */
    private fun startEditBill(billSummary: BillSummary) {
        lifecycleScope.launch {
            val existing = dao.getBill(billSummary.id) ?: return@launch
            showEditBillDialog(existing, billSummary.partyName)
        }
    }

    private fun showEditBillDialog(existing: SupplierBill, supplierName: String) {
        val view = layoutInflater.inflate(R.layout.dialog_add_bill, null)
        val etSupplier: AutoCompleteTextView = view.findViewById(R.id.etBillSupplier)
        val etNumber: EditText = view.findViewById(R.id.etBillNumber)
        val etTotal: EditText = view.findViewById(R.id.etBillTotal)
        val btnDate: MaterialButton = view.findViewById(R.id.btnBillDate)
        val btnDue: MaterialButton = view.findViewById(R.id.btnBillDue)
        val cbPaidCash: CheckBox = view.findViewById(R.id.cbPaidCash)
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

        var billDate = existing.billDate
        var dueDate = existing.dueDate

        btnDate.text = getString(R.string.due_date_set, Format.dateOnly(billDate))
        dueDate?.let { btnDue.text = getString(R.string.due_date_set, Format.dateOnly(it)) }

        btnDate.setOnClickListener {
            pickDate(billDate) { picked ->
                billDate = picked
                btnDate.text = getString(R.string.due_date_set, Format.dateOnly(picked))
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
        cbPaidCash.isChecked = existing.isPaidInFull
        cbPaidCash.isEnabled = false
        tvPaidCashHelp.text = getString(R.string.edit_bill_cash_locked)
        tvEffect.visibility = View.GONE

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_bill)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val total = etTotal.text.toString().trim().toDoubleOrNull()
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
            .show()
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
            dao.updateBill(
                existing.copy(
                    billNumber = billNumber,
                    totalAmount = total,
                    billDate = billDate,
                    dueDate = dueDate,
                    note = note
                )
            )

            // Keep the ledger entry in step — see this file's own THE LEDGER
            // IS THE MONEY rule. A bill on credit points at one entry; if the
            // amount here changes and that entry does not follow, there are
            // two different answers to "what do I owe them?" and no way to
            // tell which is true. The note is recomputed the same way it was
            // built when the bill was first saved, so a corrected bill number
            // is not left showing the old one in the ledger.
            existing.ledgerEntryId?.let { entryId ->
                dao.getEntry(entryId)?.let { entry ->
                    dao.updateEntry(
                        entry.copy(
                            amount = total,
                            note = billNumber?.let { "Bill $it" } ?: note
                        )
                    )
                }
            }

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
        }.toTypedArray() + getString(R.string.add_item)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manage_items)
            .setItems(labels) { _, which ->
                if (which == items.size) {
                    showAddItemDialog { newItem ->
                        AppScope.launch {
                            // Same birth rule as saveBill: naming a product on
                            // a bill line creates it if it does not exist yet.
                            val product = dao.findOrCreateProduct(
                                name = newItem.productName,
                                defaultUnit = newItem.unit
                            )
                            dao.insertBillItem(
                                newItem.copy(billId = billId, productId = product.id)
                            )
                            withContext(Dispatchers.Main) {
                                if (!isFinishing && !isDestroyed) manageItems(billId)
                            }
                        }
                    }
                } else {
                    showItemActions(billId, items[which])
                }
            }
            .setNegativeButton(R.string.ok, null)
            .show()
    }

    private fun showItemActions(billId: Long, item: BillItem) {
        val options = arrayOf(getString(R.string.edit_item), getString(R.string.delete_item))
        MaterialAlertDialogBuilder(this)
            .setTitle(item.productName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddItemDialog(existing = item) { updated ->
                        AppScope.launch {
                            // The name on the line decides which product the
                            // line is of. Renaming "urea" to "DAP" and leaving
                            // productId pointing at urea would silently count
                            // this stock against the wrong product, so the
                            // link is re-derived from the final name — created
                            // if that product does not exist yet, same as a
                            // new line.
                            val product = dao.findOrCreateProduct(
                                name = updated.productName,
                                defaultUnit = updated.unit
                            )
                            dao.updateBillItem(
                                updated.copy(id = item.id, billId = billId, productId = product.id)
                            )
                            withContext(Dispatchers.Main) {
                                if (!isFinishing && !isDestroyed) manageItems(billId)
                            }
                        }
                    }
                    1 -> confirmDeleteItem(billId, item)
                }
            }
            .show()
    }

    /**
     * Soft delete, like everywhere else — the batch record is kept, not
     * erased, because it may still be the answer to "which batch did this
     * customer's sale come from" long after the line itself was judged a
     * mistake. This never touches the bill's own ledger entry or its total;
     * if the total should change too, that is done from Edit Bill, not here.
     */
    private fun confirmDeleteItem(billId: Long, item: BillItem) {
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
     * Deleting a bill removes the paperwork, NOT the ledger entry.
     *
     * The stock really did arrive and the money really is owed. Unwinding the
     * ledger because a document was tidied away would erase a real debt to a
     * real supplier — who would still, quite rightly, expect to be paid.
     */
    private fun confirmDeleteBill(bill: BillSummary) {
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
                    dao.getBill(bill.id)?.ledgerEntryId?.let { entryId ->
                        dao.softDeleteEntry(entryId)
                    }
                    dao.softDeleteBill(bill.id)
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
        val input = EditText(this).apply {
            hint = getString(R.string.trace_batch_hint)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trace_batch)
            .setMessage(R.string.trace_batch_help)
            .setView(input)
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

                    MaterialAlertDialogBuilder(this@BillsActivity)
                        .setTitle(getString(R.string.trace_results, results.size))
                        .setMessage(text)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }
            .show()
    }

    private fun pickDate(startAt: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = startAt }

        DatePickerDialog(
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
