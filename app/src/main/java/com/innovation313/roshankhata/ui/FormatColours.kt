package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Money

// Format is shared; the colours it pairs with are the phone's resources.

/** The colour for [Format.signedTotal] on a dark header. Green up, red down, plain at zero. */
fun Format.signedTotalColourOnDark(value: Double): Int = when {
    value > 0 -> R.color.bal_i_owe_on_dark
    value < 0 -> R.color.bal_owed_to_me_on_dark
    else -> R.color.white
}

/**
 * The colour that goes with customerBalance(): red for money to collect,
 * green for money owed out, neutral when settled. Returns a colour RES id.
 */
fun Format.customerBalanceColour(value: Double): Int = when {
    Money.isPositive(value) -> R.color.bal_owed_to_me
    Money.isNegative(value) -> R.color.bal_i_owe
    else -> R.color.text_muted
}
