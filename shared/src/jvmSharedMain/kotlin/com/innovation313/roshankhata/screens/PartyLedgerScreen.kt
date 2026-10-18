package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
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
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    Column(modifier.fillMaxSize().background(c.pageBg)) {
        Column(Modifier.fillMaxWidth().background(c.surface).padding(20.dp)) {
            Text(Format.name(name), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            phone?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 14.sp) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(word(W.net_balance), Modifier.weight(1f), color = c.textMuted, fontSize = 14.sp)
                Text(
                    Format.customerBalance(balance),
                    Modifier.testTag("ledgerBalance"),
                    color = balanceColour(balance), fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
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
                items(entries, key = { it.id }) { e -> EntryRow(e) }
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
private fun EntryRow(e: LedgerEntry) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 20.dp, vertical = 12.dp),
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
