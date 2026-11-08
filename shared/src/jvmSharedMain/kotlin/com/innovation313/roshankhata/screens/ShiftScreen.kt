package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LineMath
import com.innovation313.roshankhata.data.Shift
import com.innovation313.roshankhata.data.ShiftMath
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shifts at the counter (21 Oct 2026, the critical review), the same on the
 * phone and the computer. The person signed in opens their shift with the
 * float counted into the drawer and closes it with the cash counted out; what
 * should be there ([ShiftMath.expected]) is the float plus their own cash
 * sales less their own cash refunds, and a difference needs its reason, as
 * the day's close does ([KhataDao.closeShift] refuses it otherwise). The owner
 * and a manager see every open shift and may close one left open; the hours
 * on the clock come from the same rows (the time clock).
 */
@Composable
fun ShiftScreen(dao: KhataDao, modifier: Modifier = Modifier, onChanged: () -> Unit = {}) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    val me = Access.userName
    val seeAll = Access.can(Access.Permission.REPORTS)
    val open by remember { dao.observeOpenShifts() }.collectAsState(emptyList())
    val recent by remember { dao.observeShifts(60) }.collectAsState(emptyList())
    val mine = open.firstOrNull { it.user == me }
    var chosen by remember { mutableStateOf<Long?>(null) }
    val closing: Shift? = open.firstOrNull { it.id == chosen } ?: mine
    var floatText by remember { mutableStateOf("") }
    var countedText by remember(closing?.id) { mutableStateOf("") }
    var reason by remember(closing?.id) { mutableStateOf("") }
    var told by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The live figures of the shift being closed: summed from the tickets each time the screen looks.
    var taken by remember { mutableStateOf(0.0) }
    var back by remember { mutableStateOf(0.0) }
    LaunchedEffect(closing?.id, open.size, recent.size) {
        val s = closing ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            taken = com.innovation313.roshankhata.data.ShiftMath.taken(dao, s, now)
            back = com.innovation313.roshankhata.data.ShiftMath.back(dao, s, now)
        }
    }
    fun who(user: String?) = user?.let { Format.name(it) } ?: words.text(W.access_owner)
    // Sums as every money box takes them (21 Oct 2026: "5000+1200" here was refused).
    fun amount(text: String): Double? = com.innovation313.roshankhata.ui.Calc.evalPad(text)?.takeIf { it >= 0.0 }

    Column(
        modifier.fillMaxWidth().background(c.pageBg).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        told?.let { Text(it, color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

        // ---- The shift on the counter now: open one, or close it ----
        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(words.text(W.shift_of, who(closing?.user ?: me)), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (closing == null) {
                Text(word(W.shift_none), color = c.textMuted, fontSize = 13.sp)
                Text(word(W.shift_float_help), color = c.inkSoft, fontSize = 13.sp)
                CalcField(floatText, { floatText = it }, Modifier.fillMaxWidth(), label = word(W.dc_opening))
                val float = amount(floatText)
                Button(
                    onClick = {
                        float?.let { f ->
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { dao.openShift(Shift(user = me, device = com.innovation313.roshankhata.data.NumberMark.mark, openingFloat = LineMath.round(f))) } }
                                told = r.fold({ words.text(W.audit_shift_opened) }, { words.text(W.shift_already_open) })
                                floatText = ""; busy = false; onChanged()
                            }
                        }
                    },
                    enabled = float != null && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen)
                ) { Text(word(W.shift_open), color = Color.White, fontWeight = FontWeight.Bold) }
            } else {
                val expected = LineMath.round(closing.openingFloat + taken - back)
                Text(words.text(W.shift_opened_at, Format.dateTime(closing.openedAt)), color = c.textMuted, fontSize = 13.sp)
                Figure(word(W.dc_opening), Format.money(closing.openingFloat))
                Figure(word(W.shift_cash_sales), Format.money(taken))
                Figure(word(W.shift_cash_refunds), if (back > 0.0) "−" + Format.money(back) else Format.money(0.0))
                HorizontalDivider(color = c.pageBg)
                Figure(word(W.z_expected), Format.money(expected), bold = true)
                CalcField(countedText, { countedText = it }, Modifier.fillMaxWidth(), label = word(W.dc_counted))
                val counted = amount(countedText)
                val difference = counted?.let { LineMath.round(it - expected) }
                if (difference != null && kotlin.math.abs(difference) > 0.005) {
                    Figure(word(W.z_variance), signed(difference), color = if (difference < 0) c.owedToMe else c.iOwe, bold = true)
                    OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text(word(W.z_variance_reason)) })
                }
                val needsReason = counted != null && ShiftMath.needsReason(expected, counted, reason)
                Button(
                    onClick = {
                        counted?.let { n ->
                            busy = true
                            val id = closing.id
                            scope.launch {
                                val r = withContext(Dispatchers.IO) { runCatching { dao.closeShift(id, n, reason) } }
                                told = r.fold({ s -> words.text(W.shift_closed) + " · " + words.text(W.z_variance) + ": " + signed(s.difference ?: 0.0) }, { it.message })
                                chosen = null; busy = false; onChanged()
                            }
                        }
                    },
                    enabled = counted != null && !needsReason && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen)
                ) { Text(word(W.shift_close), color = Color.White, fontWeight = FontWeight.Bold) }
                if (chosen != null) OutlinedButton(onClick = { chosen = null }) { Text(word(W.cancel), color = c.ink) }
            }
        }

        // ---- Everyone's open shifts: the owner and a manager may close one left open ----
        val others = open.filter { it.id != closing?.id }
        if (seeAll && others.isNotEmpty()) {
            Section(word(W.shift_open_now))
            others.forEach { s ->
                Row(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(who(s.user), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(words.text(W.shift_opened_at, Format.dateTime(s.openedAt)) + " · " + Format.money(s.openingFloat), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(onClick = { chosen = s.id }) { Text(word(W.shift_close), color = c.ink) }
                }
            }
        }

        // ---- The time clock: hours per person over the last seven days ----
        val shown = if (seeAll) recent else recent.filter { it.user == me }
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val hours = ShiftMath.hours(shown.filter { it.openedAt >= weekAgo })
        if (hours.isNotEmpty()) {
            Section(word(W.shift_week_hours))
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hours.entries.sortedByDescending { it.value }.forEach { (user, h) -> Figure(who(user), words.text(W.shift_hours, Calc.trim(h))) }
            }
        }

        // ---- Recent shifts: each with its float, what should have been there, what was, and the difference ----
        val closed = shown.filter { !it.isOpen }
        if (closed.isNotEmpty()) {
            Section(word(W.shift_recent))
            closed.forEach { s ->
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(who(s.user), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(words.text(W.shift_hours, Calc.trim(s.hours ?: 0.0)), color = c.inkSoft, fontSize = 13.sp)
                    }
                    Text(Format.dateTime(s.openedAt) + " – " + Format.dateTime(s.closedAt ?: s.openedAt), color = c.textMuted, fontSize = 12.sp)
                    Figure(word(W.z_expected), Format.money(s.expected ?: 0.0))
                    Figure(word(W.dc_counted), Format.money(s.counted ?: 0.0))
                    val d = s.difference ?: 0.0
                    if (kotlin.math.abs(d) > 0.005) Figure(word(W.z_variance), signed(d), color = if (d < 0) c.owedToMe else c.iOwe, bold = true)
                    s.note?.let { Text(it, color = c.inkSoft, fontSize = 12.sp) }
                }
            }
        }
    }
}

/** A difference with its sign: short is "−", over is "+". */
private fun signed(d: Double): String = (if (d < 0) "−" else if (d > 0) "+" else "") + Format.money(kotlin.math.abs(d))

@Composable
private fun Section(title: String) {
    val c = LocalRoshanColors.current
    Text(title, Modifier.padding(top = 6.dp), color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Figure(label: String, figure: String, color: Color? = null, bold: Boolean = false) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp)
        Text(figure, color = color ?: c.ink, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}
