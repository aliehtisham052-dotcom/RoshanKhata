package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.BinAdapter
import com.innovation313.roshankhata.ui.BinItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Recycle Bin — nothing is destroyed on delete. Items sit here until the
 * owner restores them, empties the bin, or the retention window lapses.
 */
class RecycleBinActivity : AppCompatActivity() {

    companion object {
        /** Items older than this are purged automatically on open. */
        const val RETENTION_DAYS = 30L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    private lateinit var adapter: BinAdapter
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycle_bin)

        tvEmpty = findViewById(R.id.tvBinEmpty)

        adapter = BinAdapter { item -> confirmRestore(item) }
        val rv: RecyclerView = findViewById(R.id.rvBin)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnEmptyBin).setOnClickListener { confirmEmptyBin() }

        purgeExpired()
        observeBin()
    }

    /** Anything past the retention window is genuinely gone — no silent hoarding. */
    private fun purgeExpired() {
        lifecycleScope.launch {
            val cutoff = System.currentTimeMillis() - (RETENTION_DAYS * DAY_MS)
            dao.purgeOldEntries(cutoff)
            dao.purgeOldParties(cutoff)
        }
    }

    private fun observeBin() {
        lifecycleScope.launch {
            combine(
                dao.observeDeletedParties(),
                dao.observeDeletedEntries()
            ) { parties, entries -> parties to entries }
                .collectLatest { (parties, entries) ->

                    val partyItems = parties.map { p ->
                        BinItem.DeletedParty(
                            id = p.id,
                            name = p.name,
                            phone = p.phone,
                            deletedAt = p.deletedAt ?: 0L
                        )
                    }

                    val entryItems = entries.map { e ->
                        BinItem.DeletedEntry(
                            id = e.id,
                            partyName = dao.getPartyName(e.partyId).orEmpty(),
                            amount = e.amount,
                            isGiven = e.isGiven,
                            entryNumber = e.entryNumber,
                            deletedAt = e.deletedAt ?: 0L
                        )
                    }

                    val all = (partyItems + entryItems).sortedByDescending { it.deletedAt }
                    adapter.submit(all)
                    tvEmpty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    private fun confirmRestore(item: BinItem) {
        val message = when (item) {
            is BinItem.DeletedParty -> getString(R.string.restore_party_confirm, item.name)
            is BinItem.DeletedEntry -> getString(R.string.restore_entry_confirm)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.restore) { _, _ -> restore(item) }
            .show()
    }

    private fun restore(item: BinItem) {
        lifecycleScope.launch {
            when (item) {
                is BinItem.DeletedParty -> {
                    // Bring the party back together with the entries that
                    // were swept in with it, so the ledger is whole again.
                    dao.restoreParty(item.id)
                    dao.restoreEntriesOfParty(item.id, item.deletedAt)
                }
                is BinItem.DeletedEntry -> {
                    // Guard: an entry must not surface into a ledger whose
                    // party is still sitting in the bin.
                    dao.restoreEntry(item.id)
                }
            }
            Toast.makeText(this@RecycleBinActivity, R.string.restored, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmEmptyBin() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.empty_bin)
            .setMessage(R.string.empty_bin_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_forever) { _, _ ->
                lifecycleScope.launch {
                    dao.purgeAllEntries()
                    dao.purgeAllParties()
                    Toast.makeText(this@RecycleBinActivity, R.string.bin_emptied, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
