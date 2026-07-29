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
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.BinAdapter
import com.innovation313.roshankhata.ui.BinItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.tvBinEmpty)

        adapter = BinAdapter(
            onRestore = { item -> confirmRestore(item) },
            onDeleteForever = { item -> confirmDeleteForever(item) }
        )
        val rv: RecyclerView = findViewById(R.id.rvBin)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnEmptyBin).setOnClickListener { confirmEmptyBin() }
        findViewById<MaterialButton>(R.id.btnDeleteAllParties).setOnClickListener {
            confirmDeleteAllParties()
        }

        purgeExpired()
        observeBin()
    }

    /**
     * Clear the whole customer list into the bin.
     *
     * Asked twice, and the second question carries the count — "delete 1162
     * customers" is a different sentence from "delete all", and the number is
     * what makes someone stop. Nothing is destroyed: this is the same soft
     * delete a single customer gets, so it all lands here and can be restored.
     */
    private fun confirmDeleteAllParties() {
        lifecycleScope.launch {
            val count = dao.countActiveParties()
            if (count == 0) {
                Toast.makeText(
                    this@RecycleBinActivity,
                    R.string.no_parties_to_delete,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            MaterialAlertDialogBuilder(this@RecycleBinActivity)
                .setTitle(R.string.delete_all_parties)
                .setMessage(getString(R.string.delete_all_parties_confirm, count))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_label) { _, _ ->
                    // Second ask. The first is a question; this one is the
                    // decision, and it says plainly what will be gone.
                    MaterialAlertDialogBuilder(this@RecycleBinActivity)
                        .setTitle(R.string.delete_all_parties_final_title)
                        .setMessage(getString(R.string.delete_all_parties_final, count))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete) { _, _ -> deleteAllParties() }
                        .show()
                }
                .show()
        }
    }

    /**
     * AppScope, because this is TWO writes that have to travel together.
     *
     * A single delete cancelled halfway is merely a command that did not run,
     * and the owner sees the row still there and taps again. This is not that:
     * cancelled between the two calls, every entry is in the bin while its
     * party is not, and the shared timestamp that reunites them on restore
     * never gets written to the parties at all. The book would be left in a
     * state no screen in this app knows how to describe.
     */
    private fun deleteAllParties() {
        AppScope.launch {
            val now = System.currentTimeMillis()
            // Entries first, then the parties, both stamped the same — a
            // restore reunites them by that timestamp.
            dao.softDeleteAllEntries(now)
            dao.softDeleteAllParties(now)
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@RecycleBinActivity,
                        R.string.moved_to_bin,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Anything past the retention window is genuinely gone — no silent
     * hoarding.
     *
     * On AppScope for the same two-step reason as the others, though this one
     * is the mildest of them: it runs itself on every visit, so an
     * interrupted pass would be finished by the next one. It is here for
     * consistency rather than because leaving it would be dangerous.
     */
    private fun purgeExpired() {
        AppScope.launch {
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

    /**
     * AppScope: restoring a party is two writes, and the halfway state is the
     * one that would frighten a shopkeeper most — the customer back on the
     * list with an empty ledger, every entry still in the bin, looking
     * exactly like their history had been lost.
     */
    private fun restore(item: BinItem) {
        AppScope.launch {
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
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@RecycleBinActivity,
                        R.string.restored,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Permanently remove one binned row.
     *
     * Named in the question rather than asked generically — "Delete Abbas
     * Kichia Arti forever?" is a different sentence from "delete this?",
     * and the name is what lets someone catch a wrong tap before it is
     * irreversible. A party warns about its entries too, because they go
     * with it: the transactions table cascades on partyId, so this really
     * is the whole customer's history, not just the name row.
     */
    private fun confirmDeleteForever(item: BinItem) {
        val what = when (item) {
            is BinItem.DeletedParty -> item.name
            is BinItem.DeletedEntry -> "${item.entryNumber} · ${item.partyName}"
        }
        val message = when (item) {
            is BinItem.DeletedParty -> getString(R.string.delete_party_forever_confirm, what)
            is BinItem.DeletedEntry -> getString(R.string.delete_entry_forever_confirm, what)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_forever)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_forever) { _, _ ->
                // AppScope, like the other permanent deletions here: a hard
                // delete interrupted by the screen closing would leave the
                // row half-gone from the user's point of view.
                AppScope.launch {
                    when (item) {
                        is BinItem.DeletedParty -> dao.purgeParty(item.id)
                        is BinItem.DeletedEntry -> dao.purgeEntry(item.id)
                    }
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(
                                this@RecycleBinActivity,
                                R.string.deleted_forever_done,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun confirmEmptyBin() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.empty_bin)
            .setMessage(R.string.empty_bin_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_forever) { _, _ ->
                // AppScope: two permanent deletions. Stopping between them
                // leaves parties whose every entry is already gone forever —
                // rows that look restorable and would come back empty.
                AppScope.launch {
                    dao.purgeAllEntries()
                    dao.purgeAllParties()
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(
                                this@RecycleBinActivity,
                                R.string.bin_emptied,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .show()
    }
}
