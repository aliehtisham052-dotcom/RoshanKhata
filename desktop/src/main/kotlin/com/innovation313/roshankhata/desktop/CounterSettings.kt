package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    var perHundred by remember { mutableStateOf("") }
    var pointValue by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf(com.innovation313.roshankhata.data.ScaleCode.DEFAULT_PREFIX) }
    var pricedLabel by remember { mutableStateOf(false) }
    var decimals by remember { mutableStateOf(com.innovation313.roshankhata.data.ScaleCode.DEFAULT_DECIMALS) }
    var loaded by remember { mutableStateOf(false) }
    var wide by remember { mutableStateOf(printer.wide) }
    var logo by remember { mutableStateOf(printer.logo) }
    var drawer by remember { mutableStateOf(printer.drawer) }
    // The receipt printer a ticket goes to without asking (23 Oct 2026, the review); Windows' list is read once.
    val printerNames = remember { runCatching { ThermalReceipt.printers() }.getOrDefault(emptyList()) }
    var receiptPrinter by remember { mutableStateOf(ThermalReceipt.lastPrinter) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            taxInside = BookSettings.isOn(dao.settingValue(BookSettings.PRICES_INCLUDE_TAX))
            perHundred = dao.settingValue(BookSettings.LOYALTY_POINTS_PER_100).orEmpty()
            pointValue = dao.settingValue(BookSettings.LOYALTY_POINT_VALUE).orEmpty()
            prefix = dao.settingValue(BookSettings.SCALE_PREFIX) ?: com.innovation313.roshankhata.data.ScaleCode.DEFAULT_PREFIX
            pricedLabel = BookSettings.isOn(dao.settingValue(BookSettings.SCALE_PRICE))
            decimals = com.innovation313.roshankhata.data.ScaleCode.decimalsOf(dao.settingValue(BookSettings.SCALE_PRICE_DECIMALS))
        }
        loaded = true
    }
    val title = word(W.counter_settings)
    AppDialog(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = fitW(600.dp), height = fitH(720.dp))) {
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        dao.setSetting(BookSettings.PRICES_INCLUDE_TAX, if (taxInside) "1" else null)
                        // An empty or nothing box keeps loyalty off; a prefix outside 20–29 falls back to 21.
                        fun positive(t: String) = t.trim().takeIf { (com.innovation313.roshankhata.data.Digits.parse(it) ?: 0.0) > 0.0 }
                        dao.setSetting(BookSettings.LOYALTY_POINTS_PER_100, positive(perHundred))
                        dao.setSetting(BookSettings.LOYALTY_POINT_VALUE, positive(pointValue))
                        val p = com.innovation313.roshankhata.data.Digits.toLatin(prefix.trim())
                        dao.setSetting(BookSettings.SCALE_PREFIX, p.takeIf { it.length == 2 && it.startsWith("2") && it.all(Char::isDigit) })
                        dao.setSetting(BookSettings.SCALE_PRICE, if (pricedLabel) "1" else null)
                        dao.setSetting(BookSettings.SCALE_PRICE_DECIMALS, decimals.takeIf { it != com.innovation313.roshankhata.data.ScaleCode.DEFAULT_DECIMALS }?.toString())
                    }
                    onPrinter(ThermalReceipt.Settings(wide = wide, logo = logo, drawer = drawer))
                    ThermalReceipt.lastPrinter = receiptPrinter
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
                if (printerNames.isNotEmpty()) {
                    Text(word(W.print_choose), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        printerNames.forEach { n -> FluentPill(n, receiptPrinter == n) { receiptPrinter = n } }
                    }
                }
            }
            Rule()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(word(W.loyalty_section), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FluentField(perHundred, { perHundred = it }, Modifier.weight(1f), label = word(W.loyalty_per_100), placeholder = "0")
                    FluentField(pointValue, { pointValue = it }, Modifier.weight(1f), label = word(W.loyalty_value), placeholder = "0", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                }
                Text(word(W.loyalty_help), color = c.textMuted, fontSize = 12.sp)
            }
            Rule()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(word(W.scale_section), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                FluentField(prefix, { prefix = it.take(2) }, Modifier.width(220.dp), label = word(W.scale_prefix))
                Tick(pricedLabel, word(W.scale_price)) { pricedLabel = it }
                // The label's price in the shop's own currency digits (21 Oct 2026), with what 01250 then reads as.
                if (pricedLabel) Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(word(W.scale_price_decimals), color = c.ink, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..3).forEach { d -> FluentPill(d.toString(), d == decimals, onClick = { decimals = d }) }
                    }
                    Text(word(W.scale_price_example, com.innovation313.roshankhata.ui.Format.money(com.innovation313.roshankhata.data.ScaleCode.example(decimals))), color = c.textMuted, fontSize = 12.sp)
                }
                Text(word(W.scale_help), color = c.textMuted, fontSize = 12.sp)
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
