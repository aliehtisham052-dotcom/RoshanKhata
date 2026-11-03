package com.innovation313.roshankhata.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * A restore's steps on the computer (3 Nov 2026, the phone's RestoreSteps and the owner's mockup "Restore
 * steps"): the backup checked, this book kept in Daily copies, replaced, and for a phone's pairing live sync
 * joined. Each step is moved by the restore itself as its work finishes — never by a clock. Safe to move from
 * the restore's own thread: these are Compose state, read by the dialog.
 */
class RestoreRun(val labels: List<String>, val details: List<String?>, val sub: String?) {
    enum class State { WAIT, RUN, DONE, FAILED }

    val states = mutableStateListOf<State>().apply { repeat(labels.size) { add(State.WAIT) } }

    /** Null while it runs; then "Restored" or "Not restored". */
    var heading by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun start(i: Int) { if (i in states.indices) states[i] = State.RUN }
    fun done(i: Int) { if (i in states.indices) states[i] = State.DONE }
    fun failed(i: Int) { if (i in states.indices) states[i] = State.FAILED }
    /** Whichever step was running failed: what a thrown error reaches. */
    fun failRunning() { val i = states.indexOf(State.RUN); if (i >= 0) states[i] = State.FAILED }
    fun finish(heading: String, message: String?) { this.message = message; this.heading = heading }
}

/** What a copy holds, as its question counted it: "1240 entries · 86 customers · 12 suppliers" (the phone's line). */
fun restoreCounts(data: com.innovation313.roshankhata.data.BackupCodec.ParsedBackup, words: com.innovation313.roshankhata.words.Words): String {
    val customers = data.parties.count { it.isCustomer }
    return restoreCounts(data.entries.size, customers, data.parties.size - customers, words)
}

/** [restoreCounts] from the three figures: the one line the restore and its proof picture both say. */
fun restoreCounts(entries: Int, customers: Int, suppliers: Int, words: com.innovation313.roshankhata.words.Words): String = listOfNotNull(
    words.plural(com.innovation313.roshankhata.words.WP.bin_party_entries, entries, entries),
    if (customers > 0) words.plural(com.innovation313.roshankhata.words.WP.customer_count, customers, customers) else null,
    if (suppliers > 0) words.plural(com.innovation313.roshankhata.words.WP.supplier_count, suppliers, suppliers) else null
).joinToString(" \u00B7 ")

/** The dialog for [run]. It cannot be closed while the restore runs; at the end, OK closes it. */
@Composable
fun RestoreStepsDialog(run: RestoreRun, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val heading = run.heading
    val close = { if (run.heading != null) onClose() }
    val title = heading ?: word(W.restoring)
    AppDialog(
        onCloseRequest = close, title = title, closable = heading != null,
        state = rememberDialogState(width = fitW(500.dp), height = fitH(if (run.labels.size > 3) 470.dp else 420.dp))
    ) {
        DialogFrame(
            title, subtitle = run.sub, onClose = close,
            footer = {
                if (heading != null) FluentButton(word(W.ok), close, kind = ButtonKind.ACCENT)
                else Text(word(W.restore_keep_open), color = c.textMuted, fontSize = 12.sp)
            }
        ) { RestoreStepsBody(run) }
    }
}

/** The steps and, at the end, what to know: the dialog's body (drawn on its own by the proof pictures too). */
@Composable
fun RestoreStepsBody(run: RestoreRun) {
    val c = LocalRoshanColors.current
    Column {
        run.labels.forEachIndexed { i, label ->
            StepRow(label, run.details[i], run.states[i], last = i == run.labels.lastIndex)
        }
    }
    run.message?.let { Text(it, color = c.ink, fontSize = 14.sp) }
}

@Composable
private fun StepRow(label: String, detail: String?, state: RestoreRun.State, last: Boolean) {
    val c = LocalRoshanColors.current
    val filled by animateFloatAsState(
        if (state == RestoreRun.State.DONE) 1f else 0f, tween(260, delayMillis = 120, easing = DeskMotion.Standard), label = "stepLine"
    )
    val lineBase = Look.line
    val accent = Look.accent
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            StepMarkDesk(state, Modifier.align(Alignment.TopCenter).size(24.dp))
            if (!last) Canvas(Modifier.align(Alignment.TopCenter).padding(top = 28.dp).width(2.dp).fillMaxHeight()) {
                drawRect(lineBase)
                if (filled > 0f) drawRect(accent, size = size.copy(height = size.height * filled))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = 14.dp)) {
            Text(
                label,
                color = when (state) {
                    RestoreRun.State.WAIT -> c.textMuted
                    RestoreRun.State.FAILED -> c.owedToMe
                    else -> c.ink
                },
                fontSize = 14.sp,
                fontWeight = if (state == RestoreRun.State.RUN) FontWeight.SemiBold else FontWeight.Normal
            )
            detail?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
        }
    }
}

/** A step's mark: a ring waiting, the window's own progress ring running, a disc with a check or a cross. */
@Composable
private fun StepMarkDesk(state: RestoreRun.State, modifier: Modifier) {
    val c = LocalRoshanColors.current
    when (state) {
        RestoreRun.State.WAIT -> {
            val ring = c.textMuted.copy(alpha = 0.55f)
            Canvas(modifier) {
                val sw = 2.dp.toPx()
                drawCircle(ring, radius = size.minDimension / 2f - 2.dp.toPx(), style = Stroke(width = sw))
            }
        }
        RestoreRun.State.RUN -> Box(modifier, contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = Look.accent, strokeWidth = 2.5.dp)
        }
        RestoreRun.State.DONE -> DrawnCheck(key = state, ring = Look.accent, check = Look.onAccent, modifier = modifier, fill = Look.accent, size = 24.dp)
        RestoreRun.State.FAILED -> {
            val red = c.owedToMe
            val mark = Look.onAccent
            val p = remember { Animatable(0f) }
            LaunchedEffect(Unit) { p.animateTo(1f, tween(300, easing = DeskMotion.Standard)) }
            Canvas(modifier) {
                val w = size.minDimension
                val k = motionSeg(p.value, 0f, 0.6f)
                drawCircle(red, radius = w / 2f * (0.7f + 0.3f * k), alpha = k)
                val q = motionSeg(p.value, 0.2f, 1f)
                val s = w * 0.17f
                val cx = w / 2f
                val sw = 2.5.dp.toPx()
                val a = (q * 2f).coerceAtMost(1f)
                if (a > 0f) drawLine(mark, Offset(cx - s, cx - s), Offset(cx - s + 2 * s * a, cx - s + 2 * s * a), strokeWidth = sw, cap = StrokeCap.Round)
                if (q > 0.5f) {
                    val b = (q - 0.5f) * 2f
                    drawLine(mark, Offset(cx + s, cx - s), Offset(cx + s - 2 * s * b, cx - s + 2 * s * b), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
        }
    }
}
