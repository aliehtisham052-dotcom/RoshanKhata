package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.BookSettings
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The counter's own settings (21 Oct 2026). Two kinds, said apart on the
 * page: the book's (BookSettings — whether the products' prices already hold
 * their tax, so the phone's counter follows the same rule) and this
 * computer's receipt printer (app.properties — an 80 mm roll, the shop's logo
 * at the top, the cash drawer opened on a cash sale). The owner's to change
 * (the caller asks).
 */
@Composable
fun CounterSettingsDialog(
    dao: KhataDao,
    printer: ThermalReceipt.Settings,
    hasLogo: Boolean,
    onPrinter: (ThermalReceipt.Settings) -> Unit,
    onClose: () -> Unit
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var taxInside by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var wide by remember { mutableStateOf(printer.wide) }
    var logo by remember { mutableStateOf(printer.logo) }
    var drawer by remember { mutableStateOf(printer.drawer) }
    LaunchedEffect(Unit) {
        taxInside = withContext(Dispatchers.IO) { BookSettings.isOn(dao.settingValue(BookSettings.PRICES_INCLUDE_TAX)) }
        loaded = true
    }
    val title = word(W.counter_settings)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 560.dp, height = 600.dp)) {
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setSetting(BookSettings.PRICES_INCLUDE_TAX, if (taxInside) "1" else null) }
                    onPrinter(ThermalReceipt.Settings(wide = wide, logo = logo, drawer = drawer))
                    onClose()
                }
            }, kind = ButtonKind.ACCENT, enabled = loaded)
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tick(taxInside, word(W.counter_prices_include_tax)) { taxInside = it }
                Text(word(W.counter_prices_include_tax_help), color = c.textMuted, fontSize = 12.sp)
            }
            Rule()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(word(W.printer_section), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Tick(wide, word(W.printer_width_80)) { wide = it }
                Tick(logo, word(if (hasLogo) W.printer_logo else W.biz_card_logo_none), enabled = hasLogo) { logo = it }
                Tick(drawer, word(W.printer_drawer)) { drawer = it }
                Text(word(W.printer_drawer_help), Modifier.padding(start = 12.dp), color = c.textMuted, fontSize = 12.sp)
            }
        }
    }
}

/** A tick box with its words; the whole row answers the click. */
@Composable
private fun Tick(on: Boolean, label: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalRoshanColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = if (enabled) Modifier.press { onChange(!on) } else Modifier) {
        Checkbox(on && enabled, onCheckedChange = { onChange(it) }, enabled = enabled)
        Text(label, color = if (enabled) c.ink else c.textMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
