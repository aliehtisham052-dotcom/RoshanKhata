package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * One customer's page (KMP Phase 5/6b): the name and what is owed, the
 * entries newest first — when, which way, how much, the note — and the two
 * buttons every shopkeeper's khata has, You gave and You got.
 *
 * The balance is the ledger's own sum (gave minus got), shown the
 * shopkeeper's way round by Format.customerBalance, in the same colours as
 * the phone's list: red for money to collect, green for money owed out.
 */
@Composable
fun PartyLedgerScreen(
    name: String,
    phone: String?,
    balance: Double,
    entries: List<LedgerEntry>,
    onGave: () -> Unit,
    onGot: () -> Unit,
    onStatement: (() -> Unit)? = null,
    /** An entry was clicked: open it to edit or delete. */
    onEntry: ((LedgerEntry) -> Unit)? = null,
    /** Moves the customer and their entries to the Recycle Bin (the caller confirms). */
    onDeleteParty: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Opens the customer's details to edit (the caller shows the form). */
    onEditParty: (() -> Unit)? = null
) {
    val c = LocalRoshanColors.current
    Column(modifier.fillMaxSize().background(c.pageBg)) {
        Column(Modifier.fillMaxWidth().background(c.surface).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.name(name), Modifier.weight(1f), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (onStatement != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onStatement,
                        modifier = Modifier.testTag("ledgerStatement"),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, c.brandGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.brandGreenText)
                    ) { Text(word(W.statement_pdf)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(phone?.let { Format.ltr(it) } ?: "", Modifier.weight(1f), color = c.textMuted, fontSize = 14.sp)
                if (onEditParty != null) {
                    androidx.compose.material3.TextButton(onClick = onEditParty) {
                        Text(word(W.edit_party), color = c.brandGreenText, fontSize = 13.sp)
                    }
                }
                if (onDeleteParty != null) {
                    androidx.compose.material3.TextButton(onClick = onDeleteParty, modifier = Modifier.testTag("ledgerDeleteParty")) {
                        Text(word(W.delete_party), color = c.owedToMe, fontSize = 13.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(word(W.net_balance), Modifier.weight(1f), color = c.textMuted, fontSize = 14.sp)
                // The figure says which way it runs in words, as the phone's customer page does
                // (25 Oct 2026, the audit: "- Rs 5,000" alone read as money the shop owes).
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Format.customerBalance(balance),
                        Modifier.testTag("ledgerBalance"),
                        color = balanceColour(balance), fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        word(balanceSideWord(balance)),
                        Modifier.testTag("ledgerBalanceSide"),
                        color = balanceColour(balance), fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            Text(
                word(W.no_entries_yet),
                Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                color = c.textMuted, fontSize = 15.sp
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(entries, key = { it.id }) { e -> EntryRow(e, onEntry) }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onGave,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("ledgerGave"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.gaveFill, contentColor = Color.White)
            ) { Text(word(W.you_gave), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onGot,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("ledgerGot"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen, contentColor = Color.White)
            ) { Text(word(W.you_got), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun EntryRow(e: LedgerEntry, onEntry: ((LedgerEntry) -> Unit)?) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth().background(c.surface)
            .let { m -> if (onEntry != null) m.clickable { onEntry(e) } else m }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(Format.dateTime(e.timestamp), color = c.ink, fontSize = 14.sp)
            e.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textMuted, fontSize = 13.sp) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                word(if (e.isGiven) W.you_gave else W.you_got),
                color = c.textMuted, fontSize = 12.sp
            )
            Text(
                Format.money(e.amount),
                color = if (e.isGiven) c.owedToMe else c.iOwe,
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
    }
    HorizontalDivider(color = c.pageBg)
}

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
