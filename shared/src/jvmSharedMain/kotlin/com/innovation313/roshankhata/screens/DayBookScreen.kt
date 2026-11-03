package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.DayBook
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.ZReport
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One line of the day book (3 Nov 2026): who, then when, what and how, and the amount in the ledger's own
 * colours — money that came in green, money that went out (a khata "I gave" included) red. A row of name +
 * figure that fits the phone at 360 dp and 150% text (lesson 58): the name column takes the room the figure
 * leaves and wraps to two lines; the figure keeps to one. The computer's day report draws the same rows.
 */
@Composable
fun DayBookRow(r: DayBook.Row, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val time = DateWords.formatter("h:mm a").format(java.util.Date(r.at))
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Format.isolate(DayBook.whoText(words, r)), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(time, DayBook.kindText(words, r), r.detail?.let { Format.isolate(it) }).joinToString("  ·  "),
                color = c.textMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Text(Format.money(r.amount), color = if (r.isIn) c.iOwe else c.owedToMe, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false)
    }
}

/**
 * The day book on the phone (3 Nov 2026): the day, stepped back and on (never past today); the day report's
 * figures ([ZReport.lines], the computer's day report too); then every money movement of the day in the order it
 * happened ([DayBook.rows]). [onLoaded] hands the platform what it shares and prints.
 */
@Composable
fun DayBookScreen(
    dao: KhataDao,
    day: Long,
    onDay: (Long) -> Unit,
    onLoaded: (ZReport?, List<DayBook.Row>) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    var report by remember(day) { mutableStateOf<ZReport?>(null) }
    var rows by remember(day) { mutableStateOf<List<DayBook.Row>?>(null) }
    LaunchedEffect(day) {
        val from = DayCloseMath.startOf(day)
        val to = DayCloseMath.startOf(day + 1)
        val z = withContext(Dispatchers.IO) { runCatching { ZReport.compute(dao, from, to) }.getOrNull() }
        val list = withContext(Dispatchers.IO) { runCatching { DayBook.rows(dao, from, to) }.getOrDefault(emptyList()) }
        report = z
        rows = list
        onLoaded(z, list)
    }
    val today = DayCloseMath.dayOf(System.currentTimeMillis())
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LazyColumn(modifier.fillMaxSize().background(c.pageBg), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item(key = "day") {
            // The day between its two steps; each arrow points the way its day lies in the reader's direction.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDay(day - 1) }) { Text(if (rtl) "›" else "‹", color = c.brandGreenText, fontSize = 22.sp) }
                Text(dayWords(day, today, words), Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2)
                TextButton(onClick = { if (day < today) onDay(day + 1) }, enabled = day < today) {
                    Text(if (rtl) "‹" else "›", color = if (day < today) c.brandGreenText else c.textMuted, fontSize = 22.sp)
                }
            }
        }
        item(key = "figures") {
            val r = report
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (r == null) Text("…", color = c.textMuted)
                else {
                    r.lines(words) { Format.money(it) }.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(k, Modifier.weight(1f), color = if (k.startsWith("  ")) c.inkSoft else c.ink, fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(v, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                    }
                    if (r.counted == null) Text(word(W.z_no_close), Modifier.padding(top = 6.dp), color = c.textMuted, fontSize = 12.sp)
                }
            }
        }
        val list = rows
        item(key = "head") {
            Text(if (list == null) "…" else plural(WP.bin_party_entries, list.size, list.size), Modifier.padding(top = 10.dp, bottom = 2.dp),
                color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (list != null && list.isEmpty()) item(key = "none") {
            Text(word(W.party_no_entries), Modifier.fillMaxWidth().padding(vertical = 24.dp), color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        if (list != null) items(list, key = { "${it.kind}-${it.ref}" }) { r ->
            Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp)) {
                DayBookRow(r)
                HorizontalDivider(color = c.textMuted.copy(alpha = 0.18f))
            }
        }
    }
}

/** "Today · 3 Nov 2026", "Yesterday · 2 Nov 2026", else the day itself. */
@Composable
private fun dayWords(day: Long, today: Long, words: com.innovation313.roshankhata.words.Words): String {
    val noon = DayCloseMath.startOf(day) + 12L * 60 * 60 * 1000
    val date = Format.dateOnly(noon)
    return when (day) {
        today -> words.text(W.range_today) + " · " + date
        today - 1 -> words.text(W.range_yesterday) + " · " + date
        else -> date
    }
}
