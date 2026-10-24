package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.PinHash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.delay

/**
 * App Lock on the computer (20 Oct). The phone keeps no PIN of its own — it
 * asks Android's screen lock — but a desktop JVM cannot reach Windows Hello,
 * so here the owner sets a PIN. Only a salted PBKDF2 hash of it is kept (in
 * app.properties), never the PIN. It hides the ledger from other eyes at the
 * counter; it does not encrypt the book file, and the settings say so.
 */
// PinHash moved to the shared module (data/PinHash.kt, POS Phase 5): staff PINs use it too.

/** A PIN box: digits only, hidden as typed, Enter to go. */
@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit, onEnter: () -> Unit) {
    Outlined(
        value = value,
        onValueChange = { typed -> onChange(typed.filter { it in '0'..'9' }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.width(280.dp).onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onEnter(); true } else false
        }
    )
}

/**
 * The gate before the book (the phone's LockActivity): nothing of the ledger
 * is composed until the PIN is right. Five wrong tries in a row wait 30
 * seconds, then each further miss waits again.
 */
@Composable
fun LockScreen(prefs: AppPrefs, onUnlocked: () -> Unit) {
    val c = LocalRoshanColors.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    // Wrong PINs counted in app.properties, not in memory (21 Oct 2026, the critical review): closing
    // and reopening the app cleared the count, so five guesses could be tried again and again.
    // Every fifth miss waits, each wait twice the last, up to fifteen minutes (the counter PIN's rule).
    val store = prefs.accessStore
    var misses by remember { mutableIntStateOf(store.get(KEY_LOCK_MISSES)?.toIntOrNull() ?: 0) }
    var waitUntil by remember { mutableLongStateOf(store.get(KEY_LOCK_WAIT)?.toLongOrNull() ?: 0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitUntil) { while (System.currentTimeMillis() < waitUntil) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    val waiting = now < waitUntil
    fun tryPin() {
        if (waiting || pin.isEmpty()) return
        if (prefs.pinMatches(pin)) {
            store.put(KEY_LOCK_MISSES, null); store.put(KEY_LOCK_WAIT, null)
            onUnlocked(); return
        }
        misses++; wrong = true; pin = ""
        store.put(KEY_LOCK_MISSES, misses.toString())
        if (misses % 5 == 0) {
            val doublings = (misses / 5 - 1).coerceIn(0, 10)
            waitUntil = System.currentTimeMillis() + (30_000L shl doublings).coerceAtMost(15 * 60_000L)
            store.put(KEY_LOCK_WAIT, waitUntil.toString())
            now = System.currentTimeMillis()
        }
    }
    Box(Modifier.fillMaxSize().background(c.pageBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource("roshan_khata_icon.png"), contentDescription = null, modifier = Modifier.size(84.dp))
            Text(word(W.lock_title), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(word(W.lock_subtitle), color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            PinField(pin, word(W.lock_pin_label), { pin = it; wrong = false }, ::tryPin)
            when {
                waiting -> Text(((waitUntil - now) / 1000 + 1).toInt().let { com.innovation313.roshankhata.words.plural(WP.n_wait_seconds, it, it) }, color = c.owedToMe, fontSize = 13.sp)
                wrong -> Text(word(W.lock_pin_wrong), color = c.owedToMe, fontSize = 13.sp)
            }
            FillButton(word(W.lock_unlock), c.brandGreen, enabled = !waiting && pin.isNotEmpty()) { tryPin() }
            Text(word(W.lock_tagline), Modifier.padding(top = 16.dp), color = c.textMuted, fontSize = 12.sp)
        }
    }
}

/**
 * More → App Lock: turn it on with a new PIN typed twice, or off with the
 * current one. Says plainly what the lock does and does not do.
 */
@Composable
fun AppLockSettings(prefs: AppPrefs, onDone: () -> Unit) {
    val c = LocalRoshanColors.current
    val on = prefs.hasPin()
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val wrongPin = word(W.lock_pin_wrong)
    val mismatch = word(W.lock_pin_mismatch)
    val tooShort = word(W.lock_pin_new)
    fun go() {
        if (on) {
            if (prefs.pinMatches(first)) { prefs.clearPin(); onDone() } else { problem = wrongPin; first = "" }
        } else when {
            !PinHash.valid(first) -> problem = tooShort
            first != second -> { problem = mismatch; second = "" }
            else -> { prefs.setPin(first); onDone() }
        }
    }
    Column(Modifier.fillMaxSize().background(c.surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(word(if (on) W.app_lock_enabled else W.app_lock_disabled), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(word(W.app_lock_pc_explain), color = c.textMuted, fontSize = 13.sp)
        if (on) {
            PinField(first, word(W.lock_pin_label), { first = it; problem = null }, ::go)
        } else {
            PinField(first, word(W.lock_pin_new), { first = it; problem = null }, ::go)
            PinField(second, word(W.lock_pin_again), { second = it; problem = null }, ::go)
        }
        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            FluentButton(word(W.cancel), onDone, kind = ButtonKind.STANDARD)
            FillButton(word(if (on) W.app_lock_turn_off else W.app_lock_turn_on), if (on) c.gaveFill else c.brandGreen) { go() }
        }
    }
}

private const val KEY_LOCK_MISSES = "app_lock_misses"
private const val KEY_LOCK_WAIT = "app_lock_wait_until"
