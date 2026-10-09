package com.innovation313.roshankhata.ui

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Trade

/**
 * "What kind of shop is this?" - the one place the trade is chosen (9 Oct).
 *
 * The trade decides the examples in every hint, the units offered first,
 * the product types, a new book's starter goods, and which trade-only
 * features show (crop seasons, spray advice, inspector reports). Until
 * now it could only be set from Profile -> Shop type, so a new install and
 * every business added later began as an agriculture shop and stayed one
 * unless the owner found that setting: a dairy's second shop read urea and
 * Rabi. The question is now asked where a book begins - the first run and
 * "Add business" - and Profile uses the same list.
 *
 * Not cancelable when [required]: a new book must have a trade, and
 * "General shop" is always there for one that fits none.
 */
object TradePicker {

    fun show(
        activity: Activity,
        current: Trade?,
        required: Boolean,
        onPicked: (Trade) -> Unit
    ) {
        val trades = Trade.entries
        val labels = trades.map { it.label(activity) }.toTypedArray()
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.trade_pick_title)
            .setSingleChoiceItems(labels, current?.let { trades.indexOf(it) } ?: -1) { dialog, which ->
                dialog.dismiss()
                onPicked(trades[which])
            }
            .setCancelable(!required)
        if (!required) builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }
}
