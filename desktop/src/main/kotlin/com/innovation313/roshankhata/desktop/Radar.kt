package com.innovation313.roshankhata.desktop

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.EntryWithItems
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PaymentRadar
import com.innovation313.roshankhata.data.LedgerPoint
import com.innovation313.roshankhata.data.PromiseLog
import com.innovation313.roshankhata.data.ReminderLadder
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Payment Radar on the computer (2 Nov 2026), the phone's RadarUi in the window's own controls: a chip on
 * the customer's account with the dot and the likely days of the next payment, its reasons on a click, and
 * the switch under More (kept in app.properties: each device of a shop says its own). The reading is the
 * shared [PaymentRadar]; the judgement is never on the chip itself.
 */
object RadarDesk {
    private const val KEY = "radar_on"
    private const val SHOP_TTL_MS = 10L * 60 * 1000
    private var prefs: AppPrefs? = null

    /** On unless the owner turned it off on this computer. Compose reads it, so a switch repaints at once. */
    var on by mutableStateOf(true)
        private set

    fun attach(p: AppPrefs) {
        prefs = p
        on = p.prop(KEY)?.toBooleanStrictOrNull() ?: true
    }

    fun set(value: Boolean) {
        prefs?.setProp(KEY, value.toString())
        on = value
    }

    private var shopFor = 0
    private var shopAt = 0L
    private var shopDays: Int? = null

    /** This shop's normal, read again after ten minutes or for another book. */
    suspend fun shopDays(dao: KhataDao): Int? {
        val key = System.identityHashCode(dao)
        val now = System.currentTimeMillis()
        if (key == shopFor && now - shopAt < SHOP_TTL_MS) return shopDays
        val days = withContext(Dispatchers.IO) { runCatching { PaymentRadar.shopTypicalDays(dao.radarRows()) }.getOrNull() }
        shopFor = key; shopAt = now; shopDays = days
        return days
    }
}

/** A customer's reading from the entries his account already shows; null when off, a supplier, or nothing yet. */
@Composable
fun rememberRadar(dao: KhataDao, partyId: Long, isCustomer: Boolean, entries: List<EntryWithItems>, promise: Long?): PaymentRadar.Reading? {
    var reading by remember(partyId) { mutableStateOf<PaymentRadar.Reading?>(null) }
    val on = RadarDesk.on && isCustomer
    LaunchedEffect(partyId, entries, promise, on) {
        reading = if (!on) null else runCatching {
            val shop = RadarDesk.shopDays(dao)
            // The promised days written on this computer (PromiseLog), for promises kept and broken.
            val promises = withContext(Dispatchers.IO) { runCatching { PromiseLog.of(dao.promiseLog(partyId)) }.getOrDefault(emptyList()) }
            withContext(Dispatchers.Default) {
                PaymentRadar.read(
                    // With their pairing and loan mark (3 Nov 2026 audit, M26/M34), as the phone reads them.
                    entries.map { LedgerPoint(partyId, it.entry.timestamp, it.entry.amount, it.entry.isGiven, id = it.entry.id, pairedEntryId = it.entry.pairedEntryId, isQarzeHasna = it.entry.isQarzeHasna) },
                    System.currentTimeMillis(), shop, promise, promises
                )
            }
        }.getOrNull()
    }
    return if (on) reading else null
}

@Composable
private fun dotColour(r: PaymentRadar.Reading): Color? = when (r.light) {
    PaymentRadar.Light.GREEN -> Look.leaf
    PaymentRadar.Light.AMBER -> Look.warn
    PaymentRadar.Light.RED -> LocalRoshanColors.current.owedToMe
    PaymentRadar.Light.NONE -> null
}

private fun short(t: Long) = DateWords.format("d MMM", t)

/** The chip: the dot and the likely days of the next payment (else the feature's name); nothing when there is nothing to say. */
@Composable
fun RadarChip(r: PaymentRadar.Reading, onClick: () -> Unit) {
    val dot = dotColour(r) ?: return
    val c = LocalRoshanColors.current
    val shape = RoundedCornerShape(8.dp)
    val from = r.nextFrom
    val to = r.nextTo
    val text = if (from != null && to != null) word(W.radar_next_short, short(from), short(to)) else word(W.radar_title)
    HoverTip(word(W.radar_title)) {
        Row(
            Modifier.height(26.dp).clip(shape).background(Look.subtle).press(shape, onClick = onClick).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // A shape by light, not colour alone (3 Nov 2026 audit, M45): circle, diamond, triangle — and the light
            // in words for a screen reader. Still no word on the face for a customer to read.
            val mark = when (r.light) { PaymentRadar.Light.AMBER -> "\u25C6"; PaymentRadar.Light.RED -> "\u25B2"; else -> "\u25CF" }
            val said = when (r.light) {
                PaymentRadar.Light.GREEN -> word(W.radar_on_time)
                PaymentRadar.Light.AMBER -> word(W.radar_late)
                PaymentRadar.Light.RED -> word(W.radar_very_late)
                PaymentRadar.Light.NONE -> ""
            }
            Text(mark, color = dot, fontSize = 11.sp, modifier = Modifier.semantics { contentDescription = said })
            Text(text, color = c.inkSoft, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

/** Every reason, one line each, in the owner's language (the phone's RadarUi.reasons); [tone] the step that worked. */
@Composable
fun radarReasons(r: PaymentRadar.Reading, promise: Long?, tone: Int = 0): String {
    val lines = mutableListOf<String>()
    lines += word(when (r.light) {
        PaymentRadar.Light.GREEN -> W.radar_on_time
        PaymentRadar.Light.AMBER -> W.radar_late
        PaymentRadar.Light.RED -> W.radar_very_late
        PaymentRadar.Light.NONE -> W.radar_new
    })
    r.typicalDays?.let { lines += word(W.radar_typical, plural(WP.radar_days, it, it)) }
    r.shopDays?.let { lines += word(W.radar_shop_typical, plural(WP.radar_days, it, it)) }
    r.oldestOpenDays?.takeIf { it > 0 }?.let { lines += word(W.radar_oldest, plural(WP.radar_days, it, it)) }
    r.payDay?.let { lines += word(W.radar_pay_day, it.toString()) }
    val from = r.nextFrom
    val to = r.nextTo
    if (from != null && to != null) lines += word(W.radar_next, short(from), short(to))
    if (r.promiseOverdue && promise != null) lines += word(W.radar_promise_passed, Format.dateOnly(promise))
    if (r.behindHabit) lines += word(W.radar_behind)
    val promised = r.promisesKept + r.promisesBroken
    if (promised > 0) lines += word(W.radar_promises, r.promisesKept.toString(), promised.toString())
    when (tone) {
        ReminderLadder.GENTLE -> W.reminder_step_gentle
        ReminderLadder.PLAIN -> W.reminder_step_plain
        ReminderLadder.SERIOUS -> W.reminder_step_serious
        else -> null
    }?.let { lines += word(W.radar_tone, word(it)) }
    return lines.joinToString("\n") { "• $it" } + "\n\n" + word(W.radar_private)
}

/** The reasons, with the switch to turn the Radar off on this computer. */
@Composable
fun RadarDialog(r: PaymentRadar.Reading, promise: Long?, tone: Int = 0, onClose: () -> Unit) {
    AskDialog(
        title = word(W.radar_title),
        text = radarReasons(r, promise, tone),
        confirm = word(W.radar_turn_off),
        onConfirm = { RadarDesk.set(false); onClose() },
        onDismiss = onClose,
        height = 420
    )
}

/** More → Payment Radar: what it is, and the one switch. */
@Composable
fun RadarSettingsDialog(onClose: () -> Unit) {
    val isOn = RadarDesk.on
    AskDialog(
        title = word(W.radar_title),
        text = word(W.radar_switch_help) + "\n\n" + word(W.radar_private),
        confirm = word(if (isOn) W.radar_turn_off else W.radar_turn_on),
        onConfirm = { RadarDesk.set(!isOn); onClose() },
        onDismiss = onClose,
        height = 340
    )
}
