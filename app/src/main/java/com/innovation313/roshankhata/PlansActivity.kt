package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.ui.BrandDatePickerDialog
import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.ui.Calc

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import com.innovation313.roshankhata.data.Installment
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PlanSchedule
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentPlan
import com.innovation313.roshankhata.data.PlanProgress
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PlanAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
class PlansActivity : BaseActivity() {

    private lateinit var adapter: PlanAdapter
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var parties: List<PartyWithBalance> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plans)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.tvNoPlans)
        // "After the harvest" is an agri shop's instalment; others read the
        // same help without it (9 Oct).
        if (!com.innovation313.roshankhata.data.TradeFeatures.seasons(this)) tvEmpty.setText(R.string.no_plans_general)

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
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etParty: AutoCompleteTextView = view.findViewById(R.id.etPlanParty)
        val tvOwes: TextView = view.findViewById(R.id.tvPlanOwes)
        val etTotal: EditText = view.findViewById(R.id.etPlanTotal)
        val etInstallment: EditText = view.findViewById(R.id.etPlanInstallment)
        val btnDue: MaterialButton = view.findViewById(R.id.btnPlanDueDate)
        val etNote: EditText = view.findViewById(R.id.etPlanNote)

        // Total and instalment on the app's own calculator pad (21 Oct): one
        // pad, typing into whichever of the two has the focus.
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = view,
            pad = view.findViewById(R.id.planKeypad),
            fields = listOf(etTotal, etInstallment),
            results = mapOf(
                etTotal to view.findViewById<TextView>(R.id.tvPlanTotalResult),
                etInstallment to view.findViewById<TextView>(R.id.tvPlanInstallmentResult)
            )
        )

        etParty.setAdapter(
            ArrayAdapter(
                this,
                R.layout.item_dropdown_row,
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
            if (party != null && Money.isPositive(party.balance)) {
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
            BrandDatePickerDialog(
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

                val total = Calc.evalAmount(etTotal.text.toString())
                if (total == null || total <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // AppScope, not lifecycleScope — a quick Back press right
                // after Save must not be able to cancel this plan before it
                // reaches the disk. See AppScope's own comment.
                AppScope.launch {
                    // Note what is NOT happening here: no ledger entry. The
                    // debt is already recorded. This only writes the promise.
                    dao.insertPlan(
                        PaymentPlan(
                            partyId = party.id,
                            totalAmount = total,
                            installmentAmount = Calc.evalAmount(etInstallment.text.toString()),
                            nextDueDate = nextDue,
                            note = etNote.text.toString().trim().ifEmpty { null }
                        )
                    )
                }
            }
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
    }

    private fun showPlanActions(plan: PlanProgress) {
        if (plan.isClosed) {
            ChoiceSheet.show(this, plan.partyName, listOf(ChoiceSheet.delete(getString(R.string.delete_plan)))) {
                confirmDeletePlan(plan)
            }
            return
        }

        // The next payment day can be changed or removed at any time (21 Oct 2026, the owner).
        val actions = buildList<Pair<ChoiceSheet.Option, () -> Unit>> {
            add(ChoiceSheet.Option(getString(R.string.record_payment), icon = R.drawable.ic_arrow_got,
                iconTint = R.color.bal_i_owe, tile = R.color.summary_give_bg) to { showRecordPaymentDialog(plan) })
            add(ChoiceSheet.Option(getString(R.string.plan_next_change), icon = R.drawable.ic_calendar,
                iconTint = R.color.tile_settings_fg, tile = R.color.tile_settings_bg) to {
                askNextDue(plan.id, plan.partyName, plan.nextDueDate ?: PlanSchedule.suggestNext(null))
            })
            if (plan.nextDueDate != null) add(ChoiceSheet.Option(getString(R.string.plan_next_clear)) to {
                AppScope.launch { dao.setPlanNextDue(plan.id, null) }
                Unit
            })
            add(ChoiceSheet.done(getString(R.string.close_plan)) to { closePlan(plan) })
            add(ChoiceSheet.delete(getString(R.string.delete_plan)) to { confirmDeletePlan(plan) })
        }

        ChoiceSheet.show(this, getString(R.string.plan_actions), actions.map { it.first }) { which ->
            actions[which].second()
        }
    }

    /**
     * The plan's next payment day, picked by the owner: from the plan's actions, and
     * after each payment with the suggested day already chosen (PlanSchedule). Cancel
     * leaves the day as it was.
     */
    private fun askNextDue(planId: Long, partyName: String, start: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        BrandDatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                AppScope.launch {
                    dao.setPlanNextDue(planId, picked)
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) Toast.makeText(
                            this@PlansActivity, getString(R.string.plan_next_saved, Format.dateOnly(picked)), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply { setTitle(getString(R.string.plan_next_ask, partyName)) }.show()
    }

    private fun showRecordPaymentDialog(plan: PlanProgress) {
        val view = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etAmount: EditText = view.findViewById(R.id.etAmount)
        val etNote: EditText = view.findViewById(R.id.etNote)
        // An instalment has no receipt of its own to sign (18 Oct).
        view.findViewById<android.view.View>(R.id.sigRow)?.visibility = android.view.View.GONE

        // This dialog is reused from the ledger; the fields that make no sense
        // for an instalment are hidden rather than left to confuse.
        view.findViewById<View>(R.id.cbQarzeHasna).visibility = View.GONE
        view.findViewById<View>(R.id.rgRecovery).visibility = View.GONE
        view.findViewById<View>(R.id.tvRecoveryLabel).visibility = View.GONE
        view.findViewById<View>(R.id.etItemName).visibility = View.GONE
        view.findViewById<View>(R.id.etQuantity).visibility = View.GONE
        view.findViewById<View>(R.id.etUnit).visibility = View.GONE
        // Nothing here opens the details, so the row that offers to would be
        // a button that does nothing. It was already inert on this screen;
        // now that it sits outside step one it would also be the last thing
        // above Save, which is worse.
        view.findViewById<View>(R.id.btnMoreDetails).visibility = View.GONE
        // Bill photo and date are wired only by the ledger's entry screen; here
        // they would be chips that do nothing.
        view.findViewById<View>(R.id.btnAddBill).visibility = View.GONE
        view.findViewById<View>(R.id.btnEntryDate).visibility = View.GONE

        plan.installmentAmount?.let { etAmount.setText(Format.plain(it)) }

        // The note sits in step two, which nothing on this dialog opens, so
        // there was no way to write one (21 Oct). Step two is shown with the
        // note alone: everything else in it is the ledger's (goods, rate,
        // signature, qarz-e-hasna, recovery) and stays hidden here.
        val stepTwo = view.findViewById<android.view.ViewGroup>(R.id.stepTwo)
        for (i in 0 until stepTwo.childCount) {
            val child = stepTwo.getChildAt(i)
            child.visibility = if (child === etNote) View.VISIBLE else View.GONE
        }
        stepTwo.visibility = View.VISIBLE

        // The layout has always carried the calculator pad (entryKeypad) and
        // the backspace in the box, but nothing here bound them: the keys
        // were dead and the phone's keyboard came up instead (21 Oct). Bound
        // now as on the add-entry form; the note gets the phone's keyboard
        // and the pad steps aside for it.
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = view,
            pad = view.findViewById(R.id.entryKeypad),
            fields = listOf(etAmount),
            results = mapOf(etAmount to view.findViewById<TextView>(R.id.tvAmountResult))
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.record_payment_title)
            .setMessage(getString(R.string.record_payment_help, plan.partyName))
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = Calc.evalAmount(etAmount.text.toString())
                if (amount == null) {
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
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
    }

    /**
     * ONE ledger entry, and one only. The instalment row exists to track the
     * arrangement; the ledger entry is the money. Writing the amount twice —
     * once as a payment, once as a ledger entry of its own — would halve the
     * customer's balance for money they paid once.
     */
    /**
     * The writes run on [AppScope] so a quick Back press right after Save
     * cannot cancel the payment before it reaches the disk — see AppScope's
     * own comment. The toast is different: it touches a screen the owner may
     * already have left, so it hops back to the main thread and is shown only
     * if this Activity is still the one on screen. The payment is recorded
     * either way; the toast is a courtesy, not a condition of saving.
     */
    private fun recordPayment(plan: PlanProgress, amount: Double, note: String) {
        val noteText = note.ifEmpty { getString(R.string.plan_note_prefix) }
        AppScope.launch {
            // The entry, the instalment and the plan's closing in ONE transaction (21 Oct 2026, the
            // critical review): written one after another, a failure half way left a payment in the
            // khata with no instalment, or an instalment that closed nothing. The computer's own call.
            // Money coming IN from the customer: it reduces what they owe; once the agreed total is
            // met the plan closes rather than staying open with a full progress bar.
            dao.recordPlanPayment(
                planId = plan.id,
                partyId = plan.partyId,
                amount = amount,
                paidBefore = plan.paidSoFar,
                entryNote = noteText,
                note = note.ifEmpty { null }
            )
            // Still open: the next payment day is asked, the suggestion already chosen (21 Oct 2026:
            // the day never moved on, and a plan paid on time read as late for ever).
            if (plan.paidSoFar + amount < plan.totalAmount) withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) askNextDue(plan.id, plan.partyName, PlanSchedule.suggestNext(plan.nextDueDate))
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@PlansActivity,
                        R.string.payment_recorded,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun closePlan(plan: PlanProgress) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { closePlan(plan) }) return
        lifecycleScope.launch {
            AppScope.launch {
                dao.getPlan(plan.id)?.let { existing ->
                    dao.updatePlan(
                        existing.copy(isClosed = true, closedAt = System.currentTimeMillis())
                    )
                }
            }.join()
            Toast.makeText(this@PlansActivity, R.string.plan_closed_msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Deleting the arrangement does NOT unwind the payments. That money really
     * was received and really is in the ledger — removing it because a plan was
     * tidied away would be inventing a debt the customer already settled.
     */
    private fun confirmDeletePlan(plan: PlanProgress) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmDeletePlan(plan) }) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_plan)
            .setMessage(R.string.delete_plan_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    AppScope.launch { dao.softDeletePlan(plan.id) }.join()
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
