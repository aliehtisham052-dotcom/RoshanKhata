package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.ReminderLadder
import com.innovation313.roshankhata.data.ReminderText
import com.innovation313.roshankhata.data.WhatsAppNumber
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.io.File
import java.util.Properties

/**
 * Where the computer keeps the reminder ladder ([ReminderLadder], Marhala 4, 25 Oct 2026): one small file per
 * business beside the books, one line per customer in the ladder's own stored form — this computer's count of
 * reminders opened, as the phone keeps its own. Never in the book or a backup.
 */
class ReminderStore(private val file: File) {
    private val props = Properties().apply { runCatching { if (file.exists()) file.inputStream().use { load(it) } } }

    @Synchronized
    fun state(partyId: Long): ReminderLadder.State? = ReminderLadder.decode(props.getProperty(partyId.toString()))

    /** One more reminder opened for [partyId] at [balanceNow]. A failed write is passed over: the next one counts again. */
    @Synchronized
    fun record(partyId: Long, balanceNow: Double, now: Long = System.currentTimeMillis()) {
        props.setProperty(partyId.toString(), ReminderLadder.encode(ReminderLadder.next(state(partyId), balanceNow, now)))
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().use { props.store(it, "Roshan Khata: reminders opened on this computer") }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    companion object {
        fun forBusiness(home: File, businessId: Long) = ReminderStore(File(home, "reminders_b$businessId.properties"))
    }
}

/**
 * A payment reminder read over before it goes (Marhala 4; the phone's reminder preview): the ladder's next step
 * chosen (Gentle, Plain, Serious — the owner can pick another), the words in a box he can change, then WhatsApp
 * opens with them; the owner presses send himself. A customer with no number, or a computer that cannot open
 * WhatsApp, is told so here (25 Oct 2026, the audit: the button did nothing at all, silently).
 */
@Composable
fun ReminderDialog(
    partyId: Long,
    name: String,
    phone: String?,
    balance: Double,
    /** The day the customer promised, if any: the gentle step names it. */
    promised: Long?,
    shopName: String?,
    store: ReminderStore,
    onClose: () -> Unit
) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val owes = Money.isPositive(balance)
    val state = remember(partyId) { store.state(partyId) }
    var step by remember(partyId) { mutableStateOf(if (owes) ReminderLadder.step(state, balance, System.currentTimeMillis()) else ReminderLadder.GENTLE) }
    fun fill(s: Int) = ReminderText.signed(ReminderText.body(words, name, balance, promised, s, state?.firstAt), shopName)
    var text by remember(partyId) { mutableStateOf(fill(step)) }
    var trouble by remember { mutableStateOf<String?>(null) }
    val number = phone?.let { WhatsAppNumber.of(it) }
    AppDialog(onCloseRequest = onClose, title = word(W.send_reminder), state = rememberDialogState(width = fitW(560.dp), height = fitH(600.dp))) {
        DialogFrame(
            title = word(W.send_reminder),
            subtitle = name,
            onClose = onClose,
            footer = {
                FluentButton(word(W.cancel), onClose, kind = ButtonKind.SUBTLE)
                FluentButton(word(W.open_whatsapp), {
                    if (number == null) { trouble = words.text(W.no_phone_number); return@FluentButton }
                    val opened = runCatching {
                        java.awt.Desktop.getDesktop().browse(java.net.URI("https://wa.me/$number?text=" +
                            java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")))
                    }
                    opened.onSuccess {
                        if (owes) store.record(partyId, balance)
                        onClose()
                    }.onFailure { trouble = failText(words, it) }
                }, kind = ButtonKind.ACCENT, icon = "ic_action_whatsapp")
            }
        ) {
            if (owes) {
                // The ladder's next step, chosen; another refills the words (as the phone's chips do).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ReminderLadder.GENTLE to W.reminder_step_gentle, ReminderLadder.PLAIN to W.reminder_step_plain,
                        ReminderLadder.SERIOUS to W.reminder_step_serious).forEach { (s, label) ->
                        FluentPill(word(label), s == step) { if (s != step) { step = s; text = fill(s) } }
                    }
                }
            }
            Outlined(
                value = text, onValueChange = { text = it; trouble = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                singleLine = false
            )
            Text(word(W.reminder_preview_help), color = c.textMuted, fontSize = 12.sp)
            if (number == null) Text(word(W.no_phone_number), color = c.owedToMe, fontSize = 13.sp)
            trouble?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        }
    }
}
