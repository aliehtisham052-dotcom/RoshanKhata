package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.EntryNumber
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.ui.EntryAdapter
import com.innovation313.roshankhata.ui.EntryRow
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A single party's ledger: full entry history with running balances,
 * plus the two core actions — "I Gave" and "I Got".
 */
class PartyDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTY_ID = "party_id"
    }

    private var partyId: Long = 0
    private lateinit var adapter: EntryAdapter
    private lateinit var tvPartyName: TextView
    private lateinit var tvPartyBalance: TextView
    private lateinit var tvBalanceHint: TextView
    private lateinit var tvNoEntries: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_party_detail)

        partyId = intent.getLongExtra(EXTRA_PARTY_ID, 0)
        if (partyId == 0L) {
            finish()
            return
        }

        tvPartyName = findViewById(R.id.tvPartyName)
        tvPartyBalance = findViewById(R.id.tvPartyBalance)
        tvBalanceHint = findViewById(R.id.tvBalanceHint)
        tvNoEntries = findViewById(R.id.tvNoEntries)

        adapter = EntryAdapter { entry -> confirmDeleteEntry(entry) }
        val rv: RecyclerView = findViewById(R.id.rvEntries)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnGave).setOnClickListener { showAddEntryDialog(true) }
        findViewById<MaterialButton>(R.id.btnGot).setOnClickListener { showAddEntryDialog(false) }

        loadParty()
        observeEntries()
    }

    private fun loadParty() {
        lifecycleScope.launch {
            dao.getParty(partyId)?.let { tvPartyName.text = it.name }
        }
    }

    private fun observeEntries() {
        lifecycleScope.launch {
            // Entries arrive newest-first. Running balance must be computed
            // oldest-first, then mapped back so each row shows the balance
            // as it stood right after that entry.
            dao.observeEntries(partyId).collectLatest { newestFirst ->
                val oldestFirst = newestFirst.reversed()

                var running = 0.0
                val rowsOldestFirst = oldestFirst.map { e ->
                    running += if (e.isGiven) e.amount else -e.amount
                    EntryRow(e, running)
                }

                adapter.submit(rowsOldestFirst.reversed())
                tvNoEntries.visibility = if (newestFirst.isEmpty()) View.VISIBLE else View.GONE

                updateBalanceHeader(running)
            }
        }
    }

    private fun updateBalanceHeader(balance: Double) {
        tvPartyBalance.text = Format.money(balance)
        when {
            balance > 0 -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.green_got))
                tvBalanceHint.setText(R.string.you_will_get)
            }
            balance < 0 -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.red_gave))
                tvBalanceHint.setText(R.string.you_will_give)
            }
            else -> {
                tvPartyBalance.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
                tvBalanceHint.setText(R.string.settled)
            }
        }
    }

    private fun showAddEntryDialog(isGiven: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        val etAmount: EditText = view.findViewById(R.id.etAmount)
        val etNote: EditText = view.findViewById(R.id.etNote)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isGiven) R.string.i_gave else R.string.i_got)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = etAmount.text.toString().trim().toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val note = etNote.text.toString().trim().ifEmpty { null }

                lifecycleScope.launch {
                    val count = dao.totalEntryCount()
                    dao.insertEntry(
                        LedgerEntry(
                            partyId = partyId,
                            amount = amount,
                            isGiven = isGiven,
                            note = note,
                            entryNumber = EntryNumber.next(count)
                        )
                    )
                }
            }
            .show()
    }

    /** Deleting an entry is reversible — it moves to the Recycle Bin. */
    private fun confirmDeleteEntry(entry: LedgerEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(R.string.delete_entry_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    dao.softDeleteEntry(entry.id)
                    Toast.makeText(
                        this@PartyDetailActivity,
                        R.string.moved_to_bin,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }
}
