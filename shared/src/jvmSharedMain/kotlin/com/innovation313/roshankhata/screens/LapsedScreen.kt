package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Lapsed
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A customer who stopped coming (3 Nov 2026, [Lapsed]): his name, when he last bought and how long ago, how often he
 * used to, and what he bought in all. Fits the phone at 360 dp and 150% text: the name and the lines wrap; nothing
 * sits beside them but the chevron's room. The computer's Sale Insights card draws the same rows.
 */
@Composable
fun LapsedRow(c: Lapsed.Customer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colours = LocalRoshanColors.current
    val words = LocalWords.current
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Format.isolate(Format.name(c.habit.name)), color = colours.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(words.text(W.lapsed_last, Format.dateOnly(c.habit.lastAt), words.plural(WP.radar_days, c.sinceDays, c.sinceDays)),
                color = colours.owedToMe, fontSize = 13.sp)
            Text(words.text(W.lapsed_usual, words.plural(WP.radar_days, c.usualGapDays, c.usualGapDays), Format.money(c.habit.total)),
                color = colours.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        // Forward points the reading way: left in Urdu, Sindhi, Arabic and Persian — the text mirrors › itself in a
        // right-to-left page (Bidi_Mirrored), so it is never swapped here.
        Text("›", color = colours.textMuted, fontSize = 20.sp)
    }
}

/**
 * The phone's list of customers who stopped coming (3 Nov 2026): what the list means first (the rule, in a line),
 * then the customers, biggest buyers first; a tap opens the customer, where the owner calls or sends a WhatsApp.
 */
@Composable
fun LapsedScreen(dao: KhataDao, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    var list by remember { mutableStateOf<List<Lapsed.Customer>?>(null) }
    LaunchedEffect(Unit) {
        list = withContext(Dispatchers.IO) { runCatching { Lapsed.list(dao) }.getOrDefault(emptyList()) }
    }
    LazyColumn(modifier.fillMaxSize().background(c.pageBg), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        item(key = "help") {
            Text(word(W.lapsed_help), Modifier.padding(bottom = 10.dp), color = c.inkSoft, fontSize = 13.sp)
        }
        val shown = list
        if (shown == null) item(key = "wait") { Text("…", color = c.textMuted) }
        else if (shown.isEmpty()) item(key = "none") {
            Text(word(W.lapsed_none), Modifier.fillMaxWidth().padding(vertical = 24.dp), color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        else items(shown, key = { it.habit.partyId }) { row ->
            Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp)) {
                LapsedRow(row, onClick = { onOpen(row.habit.partyId) })
                HorizontalDivider(color = c.textMuted.copy(alpha = 0.18f))
            }
        }
    }
}
