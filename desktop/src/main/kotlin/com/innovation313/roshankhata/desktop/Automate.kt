package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Automations
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.util.Calendar

/**
 * The automations on the computer (31 Oct 2026, [Automations]): the same five, said as the tray's Windows
 * notifications while the app is open, each opening its page on a click. Their switches, hours and marks are
 * this computer's (app.properties), as the phone keeps its own: two devices of one shop each say their own.
 */
class DesktopAutomationSettings(private val prefs: AppPrefs) : Automations.Settings {
    override fun isOn(kind: Automations.Kind) = prefs.prop("auto_on_${kind.key}")?.toBooleanStrictOrNull() ?: true
    override fun hour(kind: Automations.Kind) = prefs.prop("auto_hour_${kind.key}")?.toIntOrNull()?.coerceIn(0, 23) ?: kind.defaultHour
    override fun lastSaid(kind: Automations.Kind) = prefs.prop("auto_said_${kind.key}")?.toLongOrNull()
    override fun setSaid(kind: Automations.Kind, stamp: Long) = prefs.setProp("auto_said_${kind.key}", stamp.toString())
    fun setOn(kind: Automations.Kind, on: Boolean) = prefs.setProp("auto_on_${kind.key}", on.toString())
    fun setHour(kind: Automations.Kind, hour: Int) = prefs.setProp("auto_hour_${kind.key}", hour.coerceIn(0, 23).toString())
}

/** An hour in the shop's clock ("9:00 AM", or "09:00" where the country writes 24 hours). */
internal fun hourWord(hour: Int): String {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0) }
    return DateWords.format("h:mm a", c.timeInMillis)
}

/** More → Automations: each one's switch and, under its name, the hour it speaks at. */
@Composable
fun AutomationsDialog(settings: DesktopAutomationSettings, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    var tick by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf<Automations.Kind?>(null) }
    AppDialog(onCloseRequest = onClose, title = word(W.auto_title), state = rememberDialogState(width = fitW(520.dp), height = fitH(620.dp))) {
        DialogFrame(word(W.auto_title), onClose = onClose, footer = { FluentButton(word(W.ok), onClose, kind = ButtonKind.ACCENT) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(word(W.auto_help), color = c.textMuted, fontSize = 13.sp)
                key(tick) {
                    Automations.Kind.values().forEach { kind ->
                        val on = settings.isOn(kind)
                        Column(Modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(word(kind.word), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, maxLines = 2)
                                Switch(on, { settings.setOn(kind, it); tick++ },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Look.pickedFill, checkedThumbColor = Look.onPicked))
                            }
                            FluentButton(word(W.auto_at_time, hourWord(settings.hour(kind))), { if (on) picking = kind },
                                kind = ButtonKind.SUBTLE, enabled = on)
                        }
                    }
                }
            }
        }
    }
    picking?.let { kind ->
        PickDialog(word(W.auto_pick_hour), (0..23).map { hourWord(it) }, chosen = settings.hour(kind),
            onPick = { h -> settings.setHour(kind, h); picking = null; tick++ }, onDismiss = { picking = null })
    }
}

@Composable
private fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }
