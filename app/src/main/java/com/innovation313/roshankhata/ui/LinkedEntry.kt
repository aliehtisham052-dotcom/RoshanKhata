package com.innovation313.roshankhata.ui

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.EntryOwner
import com.innovation313.roshankhata.data.KhataDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Before a khata entry is edited or deleted (21 Oct 2026, the critical
 * review): an entry a bill, a plan, a cheque, a counter sale or a return
 * wrote is changed from that record, so the two can never disagree. [go]
 * runs when the entry is its own; otherwise the owner is told where to go.
 */
object LinkedEntry {
    fun check(activity: Activity, dao: KhataDao, entryId: Long, go: () -> Unit) {
        AppScope.launch {
            val owner = withContext(Dispatchers.IO) { runCatching { dao.entryOwner(entryId) }.getOrNull() }
            withContext(Dispatchers.Main) {
                if (activity.isFinishing || activity.isDestroyed) return@withContext
                if (owner == null) { go(); return@withContext }
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.entry_linked_title)
                    .setMessage(AndroidWords(activity).text(EntryOwner.wordFor(owner)))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }
}
