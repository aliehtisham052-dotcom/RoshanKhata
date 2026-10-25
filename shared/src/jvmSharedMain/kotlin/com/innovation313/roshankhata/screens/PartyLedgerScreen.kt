package com.innovation313.roshankhata.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.words.W

/*
 * A party's balance in words and colour, for every screen that shows one. The shared customer page that lived
 * here (KMP Phase 5/6b) was the computer's alone; the computer draws its own since Marhala 4 (25 Oct 2026,
 * KhataCustomer.kt, the owner's mockup), so only these two rules stay shared.
 */

/**
 * Which way a party's balance runs, as a word's name (25 Oct 2026, the audit):
 * "You will get" for money to collect, "You will give" for money owed out,
 * "Settled" at zero — the phone's customer page words. Every place that shows a
 * balance without its side (a receipt, an import, a delete question) says it.
 */
fun balanceSideWord(balance: Double): String = when {
    Money.isPositive(balance) -> W.you_will_get
    Money.isNegative(balance) -> W.you_will_give
    else -> W.settled
}

/** The list's and the page's balance colour: red to collect, green owed out, plain when settled. */
@Composable
fun balanceColour(balance: Double): Color {
    val c = LocalRoshanColors.current
    return when {
        Money.isPositive(balance) -> c.owedToMe
        Money.isNegative(balance) -> c.iOwe
        else -> c.textMuted
    }
}
