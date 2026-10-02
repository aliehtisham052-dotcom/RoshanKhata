package com.innovation313.roshankhata.ui

import android.app.Activity
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.LineMath
import kotlinx.coroutines.launch

/**
 * C2 (2 Oct): send part of a batch back to the supplier before it expires.
 *
 * Recorded as what it is in the books: goods GIVEN to the supplier, on the
 * supplier's own khata, with the line tagged to this exact batch. That one
 * entry does both jobs the existing arithmetic already understands — the
 * batch's stock drops (sales and returns both leave the shelf as "given"
 * lines), and what the shop owes the supplier drops by the credit value. No
 * second stock rule was added; Sales Insights count customers only, so a
 * return is never mistaken for a sale.
 */
object BatchReturnDialog {

    fun show(activity: Activity, scope: LifecycleCoroutineScope, dao: KhataDao, billItemId: Long) {
        scope.launch {
            val item = dao.getBillItem(billItemId) ?: return@launch
            val bill = dao.getBill(item.billId) ?: return@launch
            val supplier = dao.getParty(bill.partyId)
            val remaining = item.productId
                ?.let { pid -> dao.batchOptionsForProduct(pid).firstOrNull { it.id == item.id }?.remaining }
                ?: item.quantity
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (remaining <= 0.0) {
                Toast.makeText(activity, R.string.return_nothing_left, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val view = activity.layoutInflater.inflate(R.layout.dialog_batch_return, null)
            val etQty = view.findViewById<EditText>(R.id.etReturnQty)
            val etRate = view.findViewById<EditText>(R.id.etReturnRate)
            val tvTotal = view.findViewById<TextView>(R.id.tvReturnTotal)
            view.findViewById<TextView>(R.id.tvReturnInfo).text = activity.getString(
                R.string.return_info, item.productName, item.batchNumber ?: "—",
                Format.qty(remaining, item.unit), supplier?.name ?: "—"
            )
            etQty.setText(Format.plain(remaining))
            item.rate?.let { etRate.setText(Format.plain(it)) }

            fun total(): Double? {
                val q = Digits.parse(etQty.text.toString()) ?: return null
                val r = Digits.parse(etRate.text.toString()) ?: return null
                return LineMath.lineTotal(q, r)
            }
            fun refresh() {
                tvTotal.text = total()?.let { activity.getString(R.string.return_total, Format.money(it)) }.orEmpty()
            }
            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) = refresh()
            }
            etQty.addTextChangedListener(watcher)
            etRate.addTextChangedListener(watcher)
            refresh()

            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.return_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val q = Digits.parse(etQty.text.toString())
                    val r = Digits.parse(etRate.text.toString())
                    val amount = total()
                    when {
                        q == null || q <= 0.0 || q > remaining + 1e-9 ->
                            Toast.makeText(activity, R.string.return_bad_qty, Toast.LENGTH_SHORT).show()
                        r == null || r <= 0.0 || amount == null || amount <= 0.0 ->
                            Toast.makeText(activity, R.string.return_bad_rate, Toast.LENGTH_SHORT).show()
                        else -> AppScope.launch {
                            val line = EntryItem.ofGoods(
                                item.productName, q, item.unit, item.productId, item.id, r
                            )
                            dao.insertEntryWithItems(
                                LedgerEntry(
                                    partyId = bill.partyId,
                                    amount = amount,
                                    isGiven = true,
                                    note = activity.getString(R.string.return_note, item.productName, item.batchNumber ?: "—"),
                                    entryNumber = ""
                                ),
                                listOfNotNull(line)
                            )
                            activity.runOnUiThread {
                                Toast.makeText(activity, R.string.return_saved, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .show()
        }
    }

    /** Expiring batch tapped: who bought it, or send the rest back. */
    fun chooser(activity: Activity, scope: LifecycleCoroutineScope, dao: KhataDao, billItemId: Long) {
        MaterialAlertDialogBuilder(activity)
            .setItems(
                arrayOf(activity.getString(R.string.batch_buyers_action), activity.getString(R.string.return_action))
            ) { _, i ->
                if (i == 0) BatchBuyersDialog.show(activity, scope, dao, billItemId)
                else show(activity, scope, dao, billItemId)
            }
            .show()
    }
}
