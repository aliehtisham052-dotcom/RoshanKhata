package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.Cheque
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.ChequeWithParty
import com.innovation313.roshankhata.data.EntryNumber
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.ui.ChequeAdapter
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Cheque register.
 *
 * The central rule: a cheque is NOT money until it clears. It is held here,
 * outside the ledger, and only posted to the balance the moment the owner
 * confirms the funds actually arrived. Booking a cheque as payment on the day
 * it is handed over is how a bounced cheque quietly corrupts a ledger — the
 * balance says paid, the money never came, and it surfaces weeks later.
 */
class ChequesActivity : AppCompatActivity() {

    private lateinit var adapter: ChequeAdapter
    private lateinit var tvNoCheques: TextView
    private lateinit var tvDueSummary: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var parties: List<PartyWithBalance> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cheques)

        tvNoCheques = findViewById(R.id.tvNoCheques)
        tvDueSummary = findViewById(R.id.tvDueSummary)

        adapter = ChequeAdapter { cheque -> showChequeActions(cheque) }
        val rv: RecyclerView = findViewById(R.id.rvCheques)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddCheque).setOnClickListener {
            showAddChequeDialog()
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            dao.observeCheques().collectLatest { list ->
                adapter.submit(list)
                tvNoCheques.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // The subtitle should describe what is actually on the screen.
        //
        // It used to count only cheques falling due and say "Nothing due yet"
        // otherwise — which read as nonsense once a cheque had been cleared.
        // There was nothing "yet" about it: the cheque was settled, and the
        // header was still talking as though something were pending.
        lifecycleScope.launch {
            combine(
                dao.observeCheques(),
                dao.observeDueChequeCount(System.currentTimeMillis())
            ) { all, dueCount -> all to dueCount }
                .collectLatest { (all, dueCount) ->
                    val pending = all.count { it.status == ChequeStatus.PENDING }

                    tvDueSummary.text = when {
                        all.isEmpty() -> getString(R.string.due_summary_empty)
                        dueCount > 0 -> getString(R.string.due_summary, dueCount)
                        pending > 0 -> getString(R.string.due_summary_pending, pending)
                        else -> getString(R.string.due_summary_all_settled)
                    }
                }
        }

        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest { parties = it }
        }
    }

    private fun showAddChequeDialog() {
        if (parties.isEmpty()) {
            Toast.makeText(this, R.string.select_party_first, Toast.LENGTH_LONG).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_add_cheque, null)
        val etParty: AutoCompleteTextView = view.findViewById(R.id.etChequeParty)
        val rbReceived: RadioButton = view.findViewById(R.id.rbReceived)
        val etAmount: EditText = view.findViewById(R.id.etChequeAmount)
        val btnDate: MaterialButton = view.findViewById(R.id.btnPickDueDate)
        val etNumber: EditText = view.findViewById(R.id.etChequeNumber)
        val etBank: EditText = view.findViewById(R.id.etBankName)
        val etNote: EditText = view.findViewById(R.id.etChequeNote)

        etParty.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                parties.map { it.name }
            )
        )

        var dueDate: Long? = null

        btnDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    dueDate = picked.timeInMillis
                    btnDate.text = getString(
                        R.string.due_date_set,
                        Format.dateOnly(picked.timeInMillis)
                    )
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_cheque)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val partyName = etParty.text.toString().trim()
                val party = parties.firstOrNull { it.name.equals(partyName, ignoreCase = true) }

                if (party == null) {
                    Toast.makeText(this, R.string.select_party_first, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val amount = etAmount.text.toString().trim().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val due = dueDate
                if (due == null) {
                    Toast.makeText(this, R.string.pick_date_first, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    dao.insertCheque(
                        Cheque(
                            partyId = party.id,
                            amount = amount,
                            isReceived = rbReceived.isChecked,
                            chequeNumber = etNumber.text.toString().trim().ifEmpty { null },
                            bankName = etBank.text.toString().trim().ifEmpty { null },
                            dueDate = due,
                            note = etNote.text.toString().trim().ifEmpty { null }
                        )
                    )
                }
            }
            .show()
    }

    private fun showChequeActions(cheque: ChequeWithParty) {
        // Already settled — nothing left to decide, only to remove.
        if (cheque.status != ChequeStatus.PENDING) {
            MaterialAlertDialogBuilder(this)
                .setTitle(cheque.partyName)
                .setItems(arrayOf(getString(R.string.delete_cheque))) { _, _ ->
                    lifecycleScope.launch { dao.softDeleteCheque(cheque.id) }
                }
                .show()
            return
        }

        val options = arrayOf(
            getString(R.string.mark_cleared),
            getString(R.string.mark_bounced),
            getString(R.string.mark_cancelled),
            getString(R.string.delete_cheque)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cheque_actions)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmCleared(cheque)
                    1 -> confirmBounced(cheque)
                    2 -> settle(cheque, ChequeStatus.CANCELLED, R.string.cheque_marked_cancelled)
                    3 -> lifecycleScope.launch { dao.softDeleteCheque(cheque.id) }
                }
            }
            .show()
    }

    /**
     * The only path by which a cheque becomes money. Guarded by a confirmation
     * that says plainly what it will do, because it moves a real balance.
     */
    private fun confirmCleared(cheque: ChequeWithParty) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mark_cleared)
            .setMessage(getString(R.string.cleared_confirm, cheque.partyName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.mark_cleared) { _, _ -> postCleared(cheque) }
            .show()
    }

    private fun postCleared(cheque: ChequeWithParty) {
        lifecycleScope.launch {
            val count = dao.totalEntryCount()

            // A cheque received from a customer is money coming IN — it reduces
            // what they owe, so it is an "I Got". A cheque we issued is money
            // going OUT. Getting this backwards would invert the balance.
            val entryId = dao.insertEntry(
                LedgerEntry(
                    partyId = cheque.partyId,
                    amount = cheque.amount,
                    isGiven = !cheque.isReceived,
                    note = buildString {
                        append(
                            getString(
                                R.string.cheque_note_prefix,
                                cheque.chequeNumber?.let { "#$it" }.orEmpty()
                            ).trim()
                        )
                        cheque.bankName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    entryNumber = EntryNumber.next(count)
                )
            )

            dao.getCheque(cheque.id)?.let { existing ->
                dao.updateCheque(
                    existing.copy(
                        status = ChequeStatus.CLEARED,
                        settledAt = System.currentTimeMillis(),
                        ledgerEntryId = entryId
                    )
                )
            }

            Toast.makeText(
                this@ChequesActivity,
                R.string.cheque_cleared_posted,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * A bounced cheque posts nothing. The debt was never paid — it simply
     * stayed owed, and the ledger already says so.
     */
    private fun confirmBounced(cheque: ChequeWithParty) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mark_bounced)
            .setMessage(R.string.bounced_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.mark_bounced) { _, _ ->
                settle(cheque, ChequeStatus.BOUNCED, R.string.cheque_marked_bounced)
            }
            .show()
    }

    private fun settle(cheque: ChequeWithParty, status: Int, messageRes: Int) {
        lifecycleScope.launch {
            dao.getCheque(cheque.id)?.let { existing ->
                dao.updateCheque(
                    existing.copy(
                        status = status,
                        settledAt = System.currentTimeMillis()
                    )
                )
            }
            Toast.makeText(this@ChequesActivity, messageRes, Toast.LENGTH_SHORT).show()
        }
    }
}
