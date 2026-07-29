package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.DismissedDuplicate
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PartyPhoto
import com.innovation313.roshankhata.ui.DuplicateDetector
import com.innovation313.roshankhata.ui.DuplicateGroupAdapter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Records that may be the same customer, entered twice.
 *
 * This screen never merges anything by itself — it only groups and shows.
 * [DuplicateDetector] finds candidates by name and by phone; the owner picks
 * which record survives, reads a plain account of what moving everything
 * onto it means, and confirms before a single row changes.
 *
 * A merge is not the same shape of undo as a normal delete. The merged-away
 * party lands in the recycle bin like any other, but its entries do not go
 * with it — they already belong to the survivor by then — so restoring it
 * back out of the bin brings back an empty name, not a second ledger. That
 * is stated plainly in the confirm dialog rather than left for the owner to
 * discover later.
 */
class DuplicateCustomersActivity : AppCompatActivity() {

    private lateinit var adapter: DuplicateGroupAdapter
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duplicate_customers)
        ScreenInsets.on(this)

        tvEmpty = findViewById(R.id.tvNoDuplicates)

        adapter = DuplicateGroupAdapter { group -> reviewGroup(group) }
        val rv: RecyclerView = findViewById(R.id.rvDuplicates)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        refresh()
    }

    /** A merge just run, or an entry added elsewhere, changes what is left to find. */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val parties = dao.observePartiesWithBalance().first()
            val candidates = parties.map {
                DuplicateDetector.Candidate(
                    partyId = it.id,
                    name = it.name,
                    phone = it.phone,
                    isCustomer = it.isCustomer,
                    balance = it.balance
                )
            }
            val dismissedKeys = dao.getDismissedDuplicateKeys().toSet()
            val groups = DuplicateDetector.find(candidates, dismissedKeys)
            adapter.submitList(groups)
            tvEmpty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Step 1: which record survives. Nothing is written yet. */
    private fun reviewGroup(group: DuplicateDetector.Group) {
        val options = group.members.map { m ->
            val phone = m.phone?.takeIf { it.isNotBlank() } ?: "—"
            "${m.name}  —  $phone  —  ${Format.money(m.balance)}"
        }.toTypedArray()

        var chosen = 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.duplicate_pick_primary_title)
            .setMessage(R.string.duplicate_pick_primary_message)
            .setSingleChoiceItems(options, chosen) { _, which -> chosen = which }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.duplicate_not_duplicate) { dialog, _ ->
                dialog.dismiss()
                dismissGroup(group)
            }
            .setPositiveButton(R.string.duplicate_next) { dialog, _ ->
                dialog.dismiss()
                confirmMerge(group, chosen)
            }
            .show()
    }

    /**
     * The owner has looked and these are different people. Recorded by the
     * group's current member-id set (see [DuplicateDetector.groupKey]) so it
     * stops being suggested — but only until something about the group
     * itself actually changes, which naturally produces a different key and
     * surfaces it again on its own.
     */
    private fun dismissGroup(group: DuplicateDetector.Group) {
        val key = DuplicateDetector.groupKey(group.members)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.dismissDuplicate(DismissedDuplicate(key, System.currentTimeMillis()))
            }
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this@DuplicateCustomersActivity, R.string.duplicate_dismissed, Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    /** Step 2: a full account of what merging does, before it does it. */
    private fun confirmMerge(group: DuplicateDetector.Group, survivorIndex: Int) {
        val survivor = group.members[survivorIndex]
        val losers = group.members.filterIndexed { i, _ -> i != survivorIndex }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.duplicate_merge_confirm_title, survivor.name))
            .setMessage(R.string.duplicate_merge_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.duplicate_merge_go) { _, _ ->
                runMerge(losers.map { it.partyId }, survivor.partyId, survivor.name)
            }
            .show()
    }

    /**
     * Runs on [AppScope] — the same reason [ProductsActivity.runLink] does.
     * A sweep across several rows, one @Transaction; leaving this screen the
     * instant after tapping Merge must not cut it off halfway.
     */
    private fun runMerge(loserIds: List<Long>, survivorId: Long, survivorName: String) {
        AppScope.launch {
            try {
                dao.mergeParties(loserIds, survivorId)
                // File IO, not part of the DB transaction — deliberately
                // after it succeeds, and harmless to repeat if this activity
                // were ever re-entered mid-way, since it is a no-op once the
                // survivor already has a photo or the loser's file is gone.
                for (loserId in loserIds) {
                    PartyPhoto.transferOnMerge(this@DuplicateCustomersActivity, loserId, survivorId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(
                            this@DuplicateCustomersActivity,
                            R.string.duplicate_merge_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@DuplicateCustomersActivity,
                        getString(R.string.duplicate_merge_done, survivorName),
                        Toast.LENGTH_LONG
                    ).show()
                    refresh()
                }
            }
        }
    }
}
