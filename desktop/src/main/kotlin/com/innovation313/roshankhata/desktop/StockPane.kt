package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.InspectorBatch
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The stock register on the computer (KMP Phase 7y), the batch part of the
 * phone's InspectorReport: every batch still on the shelf (arrived minus
 * what was sold from that batch — "not more than", the safe direction),
 * its expiry, expired ones in red and those inside the chosen 30/60/90-day
 * window marked. A fact about today, so no date range. Printed or saved as
 * PDF. The phone's fuller compliance PDF (label checks, company totals) is
 * its own drawing code and is not on the computer yet.
 */
@Composable
fun StockPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?, reload: Int) {
    val c = LocalRoshanColors.current
    var batches by remember { mutableStateOf<List<InspectorBatch>>(emptyList()) }
    var window by remember { mutableStateOf(60) }
    LaunchedEffect(reload) {
        batches = withContext(Dispatchers.IO) { dao.allBatchesOnce().filter { it.remaining > 0 } }
            .sortedWith(compareBy({ it.productName.lowercase() }, { it.expiryDate ?: Long.MAX_VALUE }))
    }
    val cutoff = System.currentTimeMillis() + window * 24L * 60 * 60 * 1000
    val expired = batches.count { it.hasExpired }
    val expiring = batches.count { b -> val e = b.expiryDate; e != null && !b.hasExpired && e <= cutoff }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PaneTitle(word(W.inspector_title), word(W.inspector_subtitle)) }
            FillButton(word(W.ledger_pdf_button), c.gaveFill, enabled = batches.isNotEmpty()) {
                val rows = batches.map { b ->
                    listOf(
                        b.productName + (b.batchNumber?.let { "  ·  " + words.text(W.pdf_reg_batch, it) } ?: ""),
                        b.expiryDate?.let { Format.dateOnly(it) } ?: "–",
                        Format.qty(b.remaining, b.unit)
                    )
                }
                StatementPrint.show(
                    TablePrint(rtl, shopName ?: words.text(W.app_name), words.text(W.inspector_title) + "  ·  " + Format.dateOnly(System.currentTimeMillis()),
                        listOf(words.text(W.inspector_col_product), words.text(W.inspector_col_expiry), words.text(W.inspector_col_left)), rows
                    ) { p, of -> words.text(W.pdf_stmt_page, p, of) },
                    words.text(W.inspector_title)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(word(W.inspector_window_title), color = c.textMuted, fontSize = 12.sp)
            listOf(30 to W.inspector_window_30, 60 to W.inspector_window_60, 90 to W.inspector_window_90).forEach { (d, label) ->
                if (d == window) FillButton(word(label), c.brandGreen) { window = d }
                else TextButton({ window = d }) { Text(word(label), color = c.brandGreenText) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TotalCard(word(W.inspector_count_batches), batches.size.toString(), c.ink, Modifier.weight(1f))
            TotalCard(word(W.inspector_count_expired), expired.toString(), c.owedToMe, Modifier.weight(1f))
            TotalCard(word(W.inspector_count_expiring), expiring.toString(), c.brandGreenText, Modifier.weight(1f))
        }
        if (batches.isEmpty()) {
            Text(word(W.inspector_empty), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().card()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(word(W.inspector_col_product), Modifier.weight(1f), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(word(W.inspector_col_expiry), Modifier.width(120.dp), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(word(W.inspector_col_left), Modifier.width(110.dp), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                }
                items(batches, key = { it.itemId }) { b ->
                    val e = b.expiryDate
                    val soon = e != null && !b.hasExpired && e <= cutoff
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.productName, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(b.batchNumber?.let { word(W.pdf_reg_batch, it) }, b.company, b.partyName).joinToString("  ·  "),
                                color = c.textMuted, fontSize = 12.sp)
                        }
                        Text(e?.let { Format.dateOnly(it) } ?: "–", Modifier.width(120.dp),
                            color = when { b.hasExpired -> c.owedToMe; soon -> c.brandGreenText; else -> c.ink },
                            fontSize = 14.sp, fontWeight = if (b.hasExpired || soon) FontWeight.Bold else FontWeight.Normal)
                        Text(Format.qty(b.remaining, b.unit), Modifier.width(110.dp), color = c.ink, fontSize = 14.sp, textAlign = TextAlign.End)
                    }
                    Rule()
                }
            }
        }
    }
}
