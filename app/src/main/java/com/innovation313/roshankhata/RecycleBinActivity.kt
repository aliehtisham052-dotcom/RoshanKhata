package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.BillPhoto
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Cheque
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.Scheme
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.data.totals
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.string
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.BinAdapter
import com.innovation313.roshankhata.ui.Format
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
class RecycleBinActivity : BaseActivity() {

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
        purgeExpired()
        observeBin()
    }

    private fun purgeExpired() {
        AppScope.launch {
            val now = System.currentTimeMillis()
            val cutoff = now - (RETENTION_DAYS * DAY_MS)
            // The cashbook lines, cheques, bills and documents the bin lists since 8 Nov 2026 were binned for months
            // before it showed them: their 30 days start the first time the bin is opened with this version, so
            // nothing the owner never saw is swept (binKindsDue).
            val kindsDue = binKindsDue(now)
            // Photos of what is purged go too (BillPhoto.purgingOrphans).
            BillPhoto.purgingOrphans(applicationContext, dao) {
                if (kindsDue) dao.purgeBinKinds(cutoff, false)
                dao.purgeOldEntries(cutoff)
                dao.purgeOldParties(cutoff)
            }
        }
    }

    /**
     * True once 30 days have passed since this shop's bin first listed the five newer kinds (written the first time
     * it is opened). Per shop; a device-only note, not part of the book.
     */
    private fun binKindsDue(now: Long): Boolean {
        val prefs = getSharedPreferences("bin_kinds" + Businesses.suffix(this), MODE_PRIVATE)
        val since = prefs.getLong("since", 0L)
        if (since == 0L) {
            prefs.edit().putLong("since", now).apply()
            return false
        }
        return now - since >= RETENTION_DAYS * DAY_MS
    }

    /** The newer kinds, read together so one list is built from all seven (combine takes five flows at a time). */
    private class Newer(val cheques: List<Cheque>, val bills: List<SupplierBill>, val docs: List<Invoice>, val schemes: List<Scheme>)

    private fun observeBin() {
        lifecycleScope.launch {
            val older = combine(
                dao.observeDeletedParties(),
                dao.observeDeletedEntries(),
                dao.observeDeletedCash()
            ) { parties, entries, cash -> Triple(parties, entries, cash) }
            val newer = combine(
                dao.observeDeletedCheques(),
                dao.observeDeletedBills(),
                dao.observeDeletedInvoices(),
                dao.observeDeletedSchemes()
            ) { cheques, bills, docs, schemes -> Newer(cheques, bills, docs, schemes) }
            combine(older, newer) { a, b -> a to b }
                .collectLatest { (first, more) ->
                    val (parties, entries, cash) = first

                    val partyItems = parties.map { p ->
                        BinItem.DeletedParty(
                            id = p.id,
                            name = p.name,
                            phone = p.phone,
                            balance = dao.binnedPartyBalance(p.id),
                            entryCount = dao.binnedPartyEntryCount(p.id),
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

                    val cashItems = cash.map { c ->
                        BinItem.DeletedCash(c.id, c.isIncome, c.amount, c.category, c.note, c.deletedAt ?: 0L)
                    }
                    val chequeItems = more.cheques.map { c ->
                        BinItem.DeletedCheque(
                            c.id, dao.getPartyName(c.partyId).orEmpty(), c.amount, c.isReceived, c.chequeNumber, c.deletedAt ?: 0L
                        )
                    }
                    val billItems = more.bills.map { b ->
                        BinItem.DeletedBill(b.id, dao.getPartyName(b.partyId).orEmpty(), b.billNumber, b.totalAmount, b.deletedAt ?: 0L)
                    }
                    val docItems = more.docs.map { d ->
                        BinItem.DeletedDoc(d.id, d.kind, d.invoiceNumber, d.customerName, d.totals(dao.invoiceItems(d.id)).grandTotal, d.deletedAt ?: 0L)
                    }
                    val schemeItems = more.schemes.map { sc -> BinItem.DeletedScheme(sc.id, sc.name, sc.company, 0L) }

                    val all = (partyItems + entryItems + cashItems + chequeItems + billItems + docItems + schemeItems)
                        .sortedByDescending { it.deletedAt }
                    adapter.submit(all)
                    tvEmpty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    private fun confirmRestore(item: BinItem) {
        lifecycleScope.launch {
            // The other half of a cash sale, binned too, comes back with it: said before it does (3 Nov 2026 audit, M13).
            val pair = (item as? BinItem.DeletedEntry)?.let { withContext(Dispatchers.IO) { runCatching { dao.binnedPairOf(it.id) }.getOrNull() } }
            if (isFinishing || isDestroyed) return@launch
            val message = when (item) {
                is BinItem.DeletedParty -> getString(R.string.restore_party_confirm, item.name)
                is BinItem.DeletedEntry -> getString(R.string.restore_entry_confirm) +
                    (pair?.let { "\n\n" + getString(R.string.restore_pair_too, it.entryNumber) } ?: "")
                is BinItem.DeletedCash -> getString(R.string.bin_restore_cash_confirm)
                is BinItem.DeletedCheque -> getString(R.string.bin_restore_cheque_confirm)
                is BinItem.DeletedBill -> getString(R.string.bin_restore_bill_confirm)
                is BinItem.DeletedDoc -> getString(R.string.bin_restore_doc_confirm)
                is BinItem.DeletedScheme -> getString(R.string.bin_restore_scheme_confirm)
            }

            MaterialAlertDialogBuilder(this@RecycleBinActivity)
                .setTitle(R.string.restore)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore) { _, _ -> restore(item) }
                .show()
        }
    }

    /**
     * AppScope: restoring a party is two writes, and the halfway state is the
     * one that would frighten a shopkeeper most — the customer back on the
     * list with an empty ledger, every entry still in the bin, looking
     * exactly like their history had been lost.
     */
    private fun restore(item: BinItem) {
        AppScope.launch {
            // A kind the bin cannot bring back safely says why (blocked) instead of "Restored".
            var blocked: Int? = null
            when (item) {
                is BinItem.DeletedParty -> {
                    // Bring the party back together with the entries that
                    // were swept in with it, so the ledger is whole again.
                    dao.restorePartyWithEntries(item.id, item.deletedAt)
                }
                is BinItem.DeletedEntry -> {
                    // Guard: an entry must not surface into a ledger whose
                    // party is still sitting in the bin. A cash sale's binned
                    // other half comes back with it (3 Nov 2026 audit, M13).
                    dao.restoreEntryWithPair(item.id)
                }
                // Each of the newer kinds undoes exactly what its own delete did (KhataDao: the bin section).
                is BinItem.DeletedCash -> if (dao.restoreCashRow(item.id) != 1) blocked = R.string.bin_restore_gone
                is BinItem.DeletedCheque -> if (!dao.restoreCheque(item.id)) blocked = R.string.bin_restore_cheque_blocked
                is BinItem.DeletedBill -> if (!dao.restoreSupplierBill(item.id)) blocked = R.string.bin_restore_bill_blocked
                is BinItem.DeletedDoc -> if (!dao.restoreDocument(item.id)) blocked = R.string.bin_restore_gone
                is BinItem.DeletedScheme -> if (dao.restoreScheme(item.id) != 1) blocked = R.string.bin_restore_gone
            }
            val reason: Int? = blocked
            if (reason == null) {
                // L75 (8 Nov 2026 review): who brought it back is written down, as who put it in the bin is.
                runCatching {
                    dao.log(com.innovation313.roshankhata.data.AuditAction.BIN_RESTORED, com.innovation313.roshankhata.data.Access.userName,
                        tableOf(item), item.id)
                }
            }
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    if (reason == null) {
                        Toast.makeText(this@RecycleBinActivity, R.string.restored, Toast.LENGTH_SHORT).show()
                    } else {
                        MaterialAlertDialogBuilder(this@RecycleBinActivity)
                            .setTitle(R.string.restore)
                            .setMessage(reason)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
        }
    }

    /** The table a bin row came from, as the audit log names it. */
    private fun tableOf(item: BinItem): String = when (item) {
        is BinItem.DeletedParty -> "parties"
        is BinItem.DeletedEntry -> "transactions"
        is BinItem.DeletedCash -> "cashbook"
        is BinItem.DeletedCheque -> "cheques"
        is BinItem.DeletedBill -> "supplier_bills"
        is BinItem.DeletedDoc -> "invoices"
        is BinItem.DeletedScheme -> "schemes"
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
            is BinItem.DeletedCash -> listOf(item.category, Format.money(item.amount)).filter { it.isNotBlank() }.joinToString(" · ")
            is BinItem.DeletedCheque -> listOfNotNull(item.chequeNumber?.takeIf { it.isNotBlank() }, item.partyName.takeIf { it.isNotBlank() }, Format.money(item.amount))
                .joinToString(" · ")
            is BinItem.DeletedBill -> listOfNotNull(item.billNumber?.takeIf { it.isNotBlank() }, item.partyName.takeIf { it.isNotBlank() }, Format.money(item.amount))
                .joinToString(" · ")
            is BinItem.DeletedDoc -> "${item.number} · ${item.customerName}"
            is BinItem.DeletedScheme -> item.name
        }
        val message = when (item) {
            is BinItem.DeletedParty -> buildString {
                append(getString(R.string.delete_party_forever_confirm, what))
                // The one moment the figure matters most. Restoring is
                // reversible; this is not, and destroying a book that still
                // has money in it should require seeing the money first.
                if (Money.isNotZero(item.balance)) {
                    append("\n\n")
                    append(
                        getString(
                            if (Money.isPositive(item.balance)) R.string.bin_party_owed_to_me
                            else R.string.bin_party_i_owe,
                            Format.money(item.balance)
                        )
                    )
                }
            }
            is BinItem.DeletedEntry, is BinItem.DeletedCash, is BinItem.DeletedCheque, is BinItem.DeletedBill,
            is BinItem.DeletedDoc, is BinItem.DeletedScheme -> getString(R.string.delete_entry_forever_confirm, what)
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
                    // A customer with supplier bills, cheques, plans or counter tickets is kept, and
                    // said so (22 Oct 2026, the review: the screen said "deleted forever" either way).
                    val gone = BillPhoto.purgingOrphans(applicationContext, dao) {
                        when (item) {
                            is BinItem.DeletedParty -> { dao.keepEntryNumberHigh(); dao.purgeParty(item.id) > 0 } // L67: its entries' RK numbers are not given out again
                            is BinItem.DeletedEntry -> { dao.purgeEntry(item.id); true }
                            is BinItem.DeletedCash -> { dao.purgeCashRow(item.id); true }
                            is BinItem.DeletedCheque -> { dao.purgeChequeRow(item.id); true }
                            is BinItem.DeletedBill -> { dao.purgeSupplierBill(item.id); true }
                            is BinItem.DeletedDoc -> { dao.purgeDocument(item.id); true }
                            is BinItem.DeletedScheme -> { dao.purgeScheme(item.id); true }
                        }
                    }
                    runCatching {
                        dao.log(com.innovation313.roshankhata.data.AuditAction.BIN_PURGED, com.innovation313.roshankhata.data.Access.userName,
                            tableOf(item), item.id)
                    }
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            if (gone) {
                                Toast.makeText(
                                    this@RecycleBinActivity,
                                    R.string.deleted_forever_done,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                MaterialAlertDialogBuilder(this@RecycleBinActivity)
                                    .setTitle(R.string.delete_forever)
                                    .setMessage(R.string.bin_party_kept)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
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
                // AppScope: two permanent deletions, finished even if the
                // screen closes. The first takes only entries binned on their
                // own; a binned customer's entries go with him in the second
                // (the cascade), so a customer the bin keeps — his bills,
                // cheques, plans or counter tickets — keeps them too, and
                // comes back whole (22 Oct 2026, the review).
                AppScope.launch {
                    val kept = BillPhoto.purgingOrphans(applicationContext, dao) {
                        // The cashbook lines, cheques, bills, documents and schemes first: a bill takes the entries it owned.
                        dao.purgeBinKinds(0L, true)
                        dao.purgeAllEntries()
                        dao.purgeAllParties()
                        dao.binnedPartyCount()
                    }
                    runCatching { dao.log(com.innovation313.roshankhata.data.AuditAction.BIN_EMPTIED, com.innovation313.roshankhata.data.Access.userName) }
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            if (kept == 0) {
                                Toast.makeText(
                                    this@RecycleBinActivity,
                                    R.string.bin_emptied,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                MaterialAlertDialogBuilder(this@RecycleBinActivity)
                                    .setTitle(R.string.empty_bin)
                                    .setMessage(Digits.string(resources, R.string.bin_emptied_kept, kept))
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    }
                }
            }
            .show()
    }
}
