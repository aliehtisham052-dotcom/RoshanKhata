package com.innovation313.roshankhata.desktop

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
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App Lock on the computer (20 Oct). The phone keeps no PIN of its own — it
 * asks Android's screen lock — but a desktop JVM cannot reach Windows Hello,
 * so here the owner sets a PIN. Only a salted PBKDF2 hash of it is kept (in
 * app.properties), never the PIN. It hides the ledger from other eyes at the
 * counter; it does not encrypt the book file, and the settings say so.
 */
object PinHash {
    private const val ITERATIONS = 120_000

    fun salt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256)
        return try {
            Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    /** Compared in constant time, so the time taken says nothing about how close a guess was. */
    fun matches(pin: String, salt: String, stored: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())

    /** 4 to 8 digits, as the form asks. */
    fun valid(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }
}

/** A PIN box: digits only, hidden as typed, Enter to go. */
@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit, onEnter: () -> Unit) {
    OutlinedTextField(
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
    var misses by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitUntil) { while (System.currentTimeMillis() < waitUntil) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    val waiting = now < waitUntil
    fun tryPin() {
        if (waiting || pin.isEmpty()) return
        if (prefs.pinMatches(pin)) { onUnlocked(); return }
        misses++; wrong = true; pin = ""
        if (misses >= 5) { waitUntil = System.currentTimeMillis() + 30_000; now = System.currentTimeMillis() }
    }
    Box(Modifier.fillMaxSize().background(c.pageBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource("roshan_khata_icon.png"), contentDescription = null, modifier = Modifier.size(84.dp))
            Text(word(W.lock_title), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(word(W.lock_subtitle), color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            PinField(pin, word(W.lock_pin_label), { pin = it; wrong = false }, ::tryPin)
            when {
                waiting -> Text(word(W.lock_pin_wait, ((waitUntil - now) / 1000 + 1).toInt()), color = c.owedToMe, fontSize = 13.sp)
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
            TextButton(onDone) { Text(word(W.cancel)) }
            FillButton(word(if (on) W.app_lock_turn_off else W.app_lock_turn_on), if (on) c.gaveFill else c.brandGreen) { go() }
        }
    }
}
