package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The Recycle Bin (KMP Phase 6h): customers deleted with their entries, and
 * entries deleted on their own (an entry swept in with its customer comes
 * back with the customer and is not listed here — the phone's rule, which
 * is the DAO's query). Each has Restore. Kept 30 days, as the note says.
 */
@Composable
fun RecycleBinScreen(
    parties: List<Party>,
    entries: List<LedgerEntry>,
    partyName: (Long) -> String,
    onRestoreParty: (Party) -> Unit,
    onRestoreEntry: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    Column(modifier.fillMaxSize().background(c.pageBg)) {
        Text(word(W.bin_retention_note), Modifier.padding(16.dp), color = c.textMuted, fontSize = 13.sp)
        if (parties.isEmpty() && entries.isEmpty()) {
            Text(word(W.bin_empty), Modifier.padding(16.dp), color = c.ink, fontSize = 15.sp)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(parties, key = { "p" + it.id }) { p ->
                BinRow(
                    title = Format.name(p.name),
                    detail = word(W.bin_party_note),
                    stamp = p.deletedAt?.let { Format.dateTime(it) },
                    onRestore = { onRestoreParty(p) }
                )
            }
            items(entries, key = { "e" + it.id }) { e ->
                BinRow(
                    title = Format.name(partyName(e.partyId)) + "  ·  " + Format.money(e.amount),
                    detail = word(if (e.isGiven) W.you_gave else W.you_got) + "  ·  " + e.entryNumber,
                    stamp = e.deletedAt?.let { Format.dateTime(it) },
                    onRestore = { onRestoreEntry(e) }
                )
            }
        }
    }
}

@Composable
private fun BinRow(title: String, detail: String, stamp: String?, onRestore: () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = c.textMuted, fontSize = 13.sp)
            stamp?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
        }
        TextButton(onClick = onRestore) { Text(word(W.restore), color = c.brandGreenText, fontWeight = FontWeight.Bold) }
    }
    HorizontalDivider(color = c.pageBg)
}
