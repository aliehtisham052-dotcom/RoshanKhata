package com.innovation313.roshankhata

import com.innovation313.roshankhata.ui.Calc

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
import com.innovation313.roshankhata.data.EntryNumber
import com.innovation313.roshankhata.data.Installment
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentPlan
import com.innovation313.roshankhata.data.PlanProgress
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PlanAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Payment plans: an arrangement for clearing a debt in instalments.
 *
 * The rule this feature turns on: a plan is a PROMISE, not a debt. What the
 * party owes is already in the ledger, put there when the goods went out.
 * Creating a plan adds nothing to their balance, and recording a payment posts
 * exactly ONE ledger entry — never two. Double-counting here would have the
 * customer chased for money they never owed.
 */
class PlansActivity : AppCompatActivity() {

    private lateinit var adapter: PlanAdapter
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var parties: List<PartyWithBalance> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plans)

        tvEmpty = findViewById(R.id.tvNoPlans)

        adapter = PlanAdapter { plan -> showPlanActions(plan) }
        val rv: RecyclerView = findViewById(R.id.rvPlans)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddPlan).setOnClickListener {
            showAddPlanDialog()
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            dao.observePlans().collectLatest { list ->
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest { parties = it }
        }
    }

    private fun showAddPlanDialog() {
        if (parties.isEmpty()) {
            Toast.makeText(this, R.string.select_party_first, Toast.LENGTH_LONG).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_add_plan, null)
        val etParty: AutoCompleteTextView = view.findViewById(R.id.etPlanParty)
        val tvOwes: TextView = view.findViewById(R.id.tvPlanOwes)
        val etTotal: EditText = view.findViewById(R.id.etPlanTotal)
        val etInstallment: EditText = view.findViewById(R.id.etPlanInstallment)
        val btnDue: MaterialButton = view.findViewById(R.id.btnPlanDueDate)
        val etNote: EditText = view.findViewById(R.id.etPlanNote)

        etParty.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                parties.map { it.name }
            )
        )

        // Show what they already owe, and offer it as the total — that is
        // almost always the figure being arranged, and retyping it invites a
        // typo that would make the plan disagree with the ledger.
        etParty.setOnItemClickListener { _, _, _, _ ->
            val party = parties.firstOrNull {
                it.name.equals(etParty.text.toString().trim(), ignoreCase = true)
            }
            if (party != null && party.balance > 0) {
                tvOwes.text = getString(R.string.plan_owes_now, Format.money(party.balance))
                if (etTotal.text.isNullOrBlank()) {
                    etTotal.setText(Format.plain(party.balance))
                }
            } else {
                tvOwes.setText(R.string.plan_owes_nothing)
            }
        }

        var nextDue: Long? = null

        btnDue.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val picked = Calendar.getInstance().apply {
                        set(y, m, d, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    nextDue = picked.timeInMillis
                    btnDue.text = getString(R.string.due_date_set, Format.dateOnly(picked.timeInMillis))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_plan)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val party = parties.firstOrNull {
                    it.name.equals(etParty.text.toString().trim(), ignoreCase = true)
                }
                if (party == null) {
                    Toast.makeText(this, R.string.select_party_first, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val total = etTotal.text.toString().trim().toDoubleOrNull()
                if (total == null || total <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    // Note what is NOT happening here: no ledger entry. The
                    // debt is already recorded. This only writes the promise.
                    dao.insertPlan(
                        PaymentPlan(
                            partyId = party.id,
                            totalAmount = total,
                            installmentAmount = etInstallment.text.toString().trim()
                                .toDoubleOrNull()?.takeIf { it > 0 },
                            nextDueDate = nextDue,
                            note = etNote.text.toString().trim().ifEmpty { null }
                        )
                    )
                }
            }
            .show()
    }

    private fun showPlanActions(plan: PlanProgress) {
        if (plan.isClosed) {
            MaterialAlertDialogBuilder(this)
                .setTitle(plan.partyName)
                .setItems(arrayOf(getString(R.string.delete_plan))) { _, _ ->
                    confirmDeletePlan(plan)
                }
                .show()
            return
        }

        val options = arrayOf(
            getString(R.string.record_payment),
            getString(R.string.close_plan),
            getString(R.string.delete_plan)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.plan_actions)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRecordPaymentDialog(plan)
                    1 -> closePlan(plan)
                    2 -> confirmDeletePlan(plan)
                }
            }
            .show()
    }

    private fun showRecordPaymentDialog(plan: PlanProgress) {
        val view = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        val etAmount: EditText = view.findViewById(R.id.etAmount)
        val etNote: EditText = view.findViewById(R.id.etNote)

        // This dialog is reused from the ledger; the fields that make no sense
        // for an instalment are hidden rather than left to confuse.
        view.findViewById<View>(R.id.cbQarzeHasna).visibility = View.GONE
        view.findViewById<View>(R.id.rgRecovery).visibility = View.GONE
        view.findViewById<View>(R.id.tvRecoveryLabel).visibility = View.GONE
        view.findViewById<View>(R.id.etItemName).visibility = View.GONE
        view.findViewById<View>(R.id.etQuantity).visibility = View.GONE
        view.findViewById<View>(R.id.etUnit).visibility = View.GONE

        plan.installmentAmount?.let { etAmount.setText(Format.plain(it)) }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.record_payment_title)
            .setMessage(getString(R.string.record_payment_help, plan.partyName))
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = Calc.eval(etAmount.text.toString())
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Overpaying is allowed — people do hand over a round figure —
                // but say so, because it may equally be a typo.
                if (amount > plan.remaining) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.record_payment_title)
                        .setMessage(
                            getString(
                                R.string.plan_overpay_warning,
                                Format.money(plan.remaining)
                            )
                        )
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.proceed_anyway) { _, _ ->
                            recordPayment(plan, amount, etNote.text.toString().trim())
                        }
                        .show()
                } else {
                    recordPayment(plan, amount, etNote.text.toString().trim())
                }
            }
            .show()
    }

    /**
     * ONE ledger entry, and one only. The instalment row exists to track the
     * arrangement; the ledger entry is the money. Writing the amount twice —
     * once as a payment, once as a ledger entry of its own — would halve the
     * customer's balance for money they paid once.
     */
    private fun recordPayment(plan: PlanProgress, amount: Double, note: String) {
        lifecycleScope.launch {
            val count = dao.totalEntryCount()

            val entryId = dao.insertEntry(
                LedgerEntry(
                    partyId = plan.partyId,
                    amount = amount,
                    // Money coming IN from the customer: it reduces what they owe.
                    isGiven = false,
                    note = note.ifEmpty { getString(R.string.plan_note_prefix) },
                    entryNumber = EntryNumber.next(count)
                )
            )

            dao.insertInstallment(
                Installment(
                    planId = plan.id,
                    amount = amount,
                    ledgerEntryId = entryId,
                    note = note.ifEmpty { null }
                )
            )

            // Once the agreed total is met, close the plan rather than leaving
            // it open forever with a full progress bar.
            val paid = plan.paidSoFar + amount
            if (paid >= plan.totalAmount) {
                dao.getPlan(plan.id)?.let { existing ->
                    dao.updatePlan(
                        existing.copy(
                            isClosed = true,
                            closedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            Toast.makeText(
                this@PlansActivity,
                R.string.payment_recorded,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun closePlan(plan: PlanProgress) {
        lifecycleScope.launch {
            dao.getPlan(plan.id)?.let { existing ->
                dao.updatePlan(
                    existing.copy(isClosed = true, closedAt = System.currentTimeMillis())
                )
            }
            Toast.makeText(this@PlansActivity, R.string.plan_closed_msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Deleting the arrangement does NOT unwind the payments. That money really
     * was received and really is in the ledger — removing it because a plan was
     * tidied away would be inventing a debt the customer already settled.
     */
    private fun confirmDeletePlan(plan: PlanProgress) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_plan)
            .setMessage(R.string.delete_plan_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    dao.softDeletePlan(plan.id)
                    Toast.makeText(
                        this@PlansActivity,
                        R.string.plan_deleted,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }
}
