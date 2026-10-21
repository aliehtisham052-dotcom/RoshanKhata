package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Aging
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The four age buckets' names, in [Aging]'s order. */
val AGING_WORDS = listOf(W.aging_0_30, W.aging_31_60, W.aging_61_90, W.aging_90)

/**
 * A bucket's colour, from fresh to old: the brand green, amber, orange, the
 * ledger's red. Fills only — every figure beside them is in ink.
 */
@Composable
fun agingColor(bucket: Int): Color {
    val c = LocalRoshanColors.current
    return when (bucket) {
        0 -> c.brandGreen
        1 -> Color(0xFFF2A900)
        2 -> Color(0xFFE8710A)
        else -> c.owedToMe
    }
}

/**
 * One party's balance as a bar split by age: each bucket's share of the
 * width in its colour, so the old money is seen before it is read.
 */
@Composable
fun AgingBar(buckets: List<Double>, modifier: Modifier = Modifier) {
    val total = buckets.sum()
    Row(modifier.height(8.dp).clip(RoundedCornerShape(4.dp))) {
        if (total <= 0.0) return@Row
        buckets.forEachIndexed { i, b ->
            if (b > 0.0) Box(Modifier.weight((b / total).toFloat()).fillMaxSize().background(agingColor(i)))
        }
    }
}

/**
 * Dues by age on the phone (21 Oct 2026): who owes me — or whom I owe —
 * split into 0–30, 31–60, 61–90 and over 90 days ([Aging]); the totals per
 * bucket on top, then each party with the oldest money first. A tap opens
 * the party ([onParty]).
 */
@Composable
fun AgingScreen(dao: KhataDao, modifier: Modifier = Modifier, reload: Int = 0, onParty: ((Long) -> Unit)? = null) {
    val c = LocalRoshanColors.current
    var report by remember { mutableStateOf<Aging.Report?>(null) }
    var owedToMe by remember { mutableStateOf(true) }
    LaunchedEffect(reload) { report = withContext(Dispatchers.IO) { runCatching { Aging.compute(dao) }.getOrNull() } }
    Column(modifier.fillMaxSize().background(c.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = owedToMe, onClick = { owedToMe = true }, label = { Text(word(W.i_have_to_get), fontSize = 12.sp) })
            FilterChip(selected = !owedToMe, onClick = { owedToMe = false }, label = { Text(word(W.i_have_to_give), fontSize = 12.sp) })
        }
        Text(word(W.aging_help), Modifier.padding(horizontal = 16.dp), color = c.inkSoft, fontSize = 12.sp)
        val r = report
        if (r == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…", color = c.textMuted) }; return }
        val rows = if (owedToMe) r.receivable else r.payable
        val totals = if (owedToMe) r.receivableTotals else r.payableTotals
        if (rows.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(word(W.aging_none), color = c.textMuted) }; return }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "totals") {
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(word(W.aging_total), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(Format.money(totals.sum()), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    AgingBar(totals, Modifier.fillMaxWidth())
                    totals.forEachIndexed { i, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.padding(end = 8.dp).size(10.dp).background(agingColor(i), RoundedCornerShape(2.dp)))
                            Text(word(AGING_WORDS[i]), Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp)
                            Text(Format.money(t), color = if (i == Aging.BUCKETS - 1 && t > 0.0) c.owedToMe else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            items(rows, key = { it.partyId }) { row ->
                Column(
                    Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp))
                        .then(if (onParty != null) Modifier.clickable { onParty(row.partyId) } else Modifier).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Format.name(row.name), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            row.oldest?.let { Text(word(W.aging_oldest, Format.dateOnly(it)), color = c.textMuted, fontSize = 12.sp) }
                        }
                        Text(Format.money(row.balance), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    AgingBar(row.buckets, Modifier.fillMaxWidth())
                    row.buckets.forEachIndexed { i, b ->
                        if (b > 0.0) Row {
                            Text(word(AGING_WORDS[i]), Modifier.weight(1f), color = c.inkSoft, fontSize = 12.sp)
                            Text(Format.money(b), color = if (i == Aging.BUCKETS - 1) c.owedToMe else c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
