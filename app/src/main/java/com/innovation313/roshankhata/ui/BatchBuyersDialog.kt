package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.Digits

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.PartyDetailActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.KhataDao
import kotlinx.coroutines.launch

/**
 * "Who bought this batch?" (2 Oct) — opened from a bill's item or an
 * expiring batch. Lists each customer with how much and when, biggest first;
 * a tap opens their khata. When some sales of the product were saved with no
 * batch, the title says so: a recall list that looks complete but is not is
 * worse than none.
 */
object BatchBuyersDialog {

    fun show(activity: Activity, scope: LifecycleCoroutineScope, dao: KhataDao, billItemId: Long) {
        scope.launch {
            val item = dao.getBillItem(billItemId) ?: return@launch
            val buyers = dao.buyersOfBatch(billItemId)
            val untagged = item.productId?.let { dao.untaggedSalesOfProduct(it) } ?: 0
            if (activity.isFinishing || activity.isDestroyed) return@launch

            val batch = item.batchNumber?.takeIf { it.isNotBlank() } ?: "—"
            val title = activity.getString(R.string.batch_buyers_title, item.productName, batch)
            val note = if (untagged > 0)
                Digits.quantity(activity.resources, R.plurals.batch_buyers_untagged, untagged, untagged)
            else null

            val builder = MaterialAlertDialogBuilder(activity).setTitle(title)
            if (buyers.isEmpty()) {
                builder.setMessage(
                    listOfNotNull(activity.getString(R.string.batch_buyers_none), note).joinToString("\n\n")
                )
            } else {
                val labels = buyers.map { b ->
                    val qty = b.quantity?.let { Format.qty(it, b.unit) } ?: "—"
                    "${b.partyName}  ·  $qty  ·  ${Format.dateOnly(b.lastAt)}"
                }.toTypedArray()
                // A message cannot sit beside a list in one dialog, so the
                // missing-batch warning rides in the title.
                if (note != null) builder.setTitle("$title\n$note")
                builder.setItems(labels) { _, i ->
                    activity.startActivity(
                        Intent(activity, PartyDetailActivity::class.java)
                            .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, buyers[i].partyId)
                    )
                }
            }
            builder.setNegativeButton(R.string.close, null).show()
        }
    }
}
