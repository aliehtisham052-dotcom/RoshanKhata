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
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.BillSummary
import com.innovation313.roshankhata.data.EntryNumber
import com.innovation313.roshankhata.data.ExpiryWindow
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.ui.BillAdapter
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    private fun showAddItemDialog(onDone: (BillItem) -> Unit) {
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

        var expiry: Long? = null

        btnExpiry.setOnClickListener {
            // Default the picker a year out — pesticide typically carries a
            // long shelf life, and starting at today would mean scrolling.
            val start = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
            pickDate(start) { picked ->
                expiry = picked
                btnExpiry.text = getString(R.string.due_date_set, Format.dateOnly(picked))
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_item)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val product = etProduct.text.toString().trim()
                if (product.isEmpty()) {
                    Toast.makeText(this, R.string.enter_product, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val qty = etQty.text.toString().trim().toDoubleOrNull() ?: 1.0

                onDone(
                    BillItem(
                        billId = 0,  // filled in when the bill is saved
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

        lifecycleScope.launch {
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
                val count = dao.totalEntryCount()
                dao.insertEntry(
                    LedgerEntry(
                        partyId = supplier.id,
                        amount = total,
                        isGiven = false,
                        note = billNumber?.let { "Bill $it" } ?: note,
                        entryNumber = EntryNumber.next(count)
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

            pendingItems.forEach { item ->
                dao.insertBillItem(item.copy(billId = billId))
            }
            pendingItems.clear()

            Toast.makeText(
                this@BillsActivity,
                if (paidCash) R.string.bill_saved else R.string.bill_saved_credit,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------- Viewing / deleting ----------

    private fun showBillActions(bill: BillSummary) {
        val options = arrayOf(
            getString(R.string.view_items),
            getString(R.string.delete_bill)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bill_actions)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showItems(bill)
                    1 -> confirmDeleteBill(bill)
                }
            }
            .show()
    }

    private fun showItems(bill: BillSummary) {
        lifecycleScope.launch {
            val items = dao.billItems(bill.id)

            val text = if (items.isEmpty()) {
                getString(R.string.no_items_yet)
            } else {
                items.joinToString("\n\n") { i ->
                    buildString {
                        append(i.productName)
                        append("\n")
                        append(Format.qty(i.quantity, i.unit))
                        i.rate?.let {
                            append(" @ ")
                            append(Format.money(it))
                        }
                        append("\n")
                        append(
                            if (i.batchNumber.isNullOrBlank()) {
                                getString(R.string.batch_none)
                            } else {
                                getString(R.string.batch_label, i.batchNumber)
                            }
                        )
                        i.expiryDate?.let {
                            append("\n")
                            append(getString(R.string.expiry_date))
                            append(": ")
                            append(Format.dateOnly(it))
                        }
                    }
                }
            }

            MaterialAlertDialogBuilder(this@BillsActivity)
                .setTitle(bill.billNumber ?: bill.partyName)
                .setMessage(text)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
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
