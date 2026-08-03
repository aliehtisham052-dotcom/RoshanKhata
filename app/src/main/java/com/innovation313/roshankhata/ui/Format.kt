package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Money

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {

    private val dateTimeFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)
    private val dateOnlyFmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)

    fun money(value: Double): String {
        val rounded = abs(value)
        return if (rounded % 1.0 == 0.0) {
            "Rs %,.0f".format(rounded)
        } else {
            "Rs %,.2f".format(rounded)
        }
    }

    /**
     * A plain in-minus-out total, carrying its arithmetic sign.
     *
     * Deliberately NOT [customerBalance], which inverts the sign because a
     * khata balance is read from the shopkeeper's side. A cashbook net is not
     * a balance owed by anyone — it is money in less money out, and it means
     * exactly what the arithmetic says. Inverting it here would turn a
     * shortfall into a surplus on screen.
     *
     * The minus is U+2212, matching the sign the cashbook rows already use,
     * rather than a hyphen that reads as a dash at a glance.
     */
    fun signedTotal(value: Double): String = when {
        value > 0 -> "+ ${money(value)}"
        value < 0 -> "− ${money(value)}"
        else -> money(value)
    }

    /** The colour for [signedTotal] on a dark header. Green up, red down, plain at zero. */
    fun signedTotalColourOnDark(value: Double): Int = when {
        value > 0 -> R.color.bal_i_owe_on_dark
        value < 0 -> R.color.bal_owed_to_me_on_dark
        else -> R.color.white
    }

    /**
     * A customer balance with its sign, the shopkeeper's way round.
     *
     * A positive balance means the customer owes the shop — money the owner has
     * to collect — and shows as "- Rs X". A negative balance means the shop owes
     * the customer and shows as "+ Rs X". This is the opposite of a bank
     * statement, and deliberately so: it matches how a dukandaar reads their
     * own khata, and how the apps they already use present it.
     */
    fun customerBalance(value: Double): String {
        val amount = money(value)
        return when {
            // Money.isPositive / isNegative rather than > 0 and < 0. A settled
            // ledger can sit a millionth of a paisa off zero, and the plain
            // comparison put a minus sign in front of a customer who owed
            // nothing — on a figure that printed as Rs 0.
            Money.isPositive(value) -> "- $amount"
            Money.isNegative(value) -> "+ $amount"
            else -> amount
        }
    }

    /**
     * The colour that goes with customerBalance(): red for money to collect,
     * green for money owed out, neutral when settled. Returns a colour RES id.
     */
    fun customerBalanceColour(value: Double): Int = when {
        Money.isPositive(value) -> R.color.bal_owed_to_me
        Money.isNegative(value) -> R.color.bal_i_owe
        else -> R.color.text_muted
    }

    fun dateTime(millis: Long): String = dateTimeFmt.format(Date(millis))

    fun dateOnly(millis: Long): String = dateOnlyFmt.format(Date(millis))

    /**
     * "5 bag — Urea", or just "Urea" if no quantity was given.
     * Returns null when nothing was recorded, so the caller can hide the row.
     */
    fun goods(itemName: String?, quantity: Double?, unit: String?): String? {
        val item = itemName?.trim().orEmpty()
        if (item.isEmpty() && quantity == null) return null

        val qtyPart = quantity?.let { q ->
            val n = if (q % 1.0 == 0.0) "%,.0f".format(q) else "%,.2f".format(q)
            val u = unit?.trim().orEmpty()
            if (u.isEmpty()) n else "$n $u"
        }

        return when {
            qtyPart != null && item.isNotEmpty() -> "$qtyPart — $item"
            qtyPart != null -> qtyPart
            else -> item
        }
    }

    /** Bare number for an input field — no currency symbol, no thousands separator. */
    fun plain(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /**
     * Quantity and unit alone — "5 bottles", "12.5 litre".
     *
     * Separate from goods(), which folds the item name in too. On a supplier
     * bill the product is already shown on its own line, so goods() would print
     * it twice.
     */
    fun qty(quantity: Double, unit: String?): String {
        val n = if (quantity % 1.0 == 0.0) "%,.0f".format(quantity)
        else "%,.2f".format(quantity)
        val u = unit?.trim().orEmpty()
        return if (u.isEmpty()) n else "$n $u"
    }
}
