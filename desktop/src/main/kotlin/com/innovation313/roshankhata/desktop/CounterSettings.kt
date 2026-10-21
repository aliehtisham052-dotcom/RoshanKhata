package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * The counter's own settings (21 Oct 2026), kept in the book (BookSettings)
 * so the phone's counter follows the same rules: whether the products'
 * prices already hold their tax. The owner's to change (the caller asks).
 */
@Composable
fun CounterSettingsDialog(dao: KhataDao, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var taxInside by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        taxInside = withContext(Dispatchers.IO) { BookSettings.isOn(dao.settingValue(BookSettings.PRICES_INCLUDE_TAX)) }
        loaded = true
    }
    val title = word(W.counter_settings)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = 520.dp, height = 420.dp)) {
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.setSetting(BookSettings.PRICES_INCLUDE_TAX, if (taxInside) "1" else null) }
                    onClose()
                }
            }, kind = ButtonKind.ACCENT, enabled = loaded)
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.press { taxInside = !taxInside }) {
                    Checkbox(taxInside, onCheckedChange = { taxInside = it })
                    Text(word(W.counter_prices_include_tax), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(word(W.counter_prices_include_tax_help), color = c.textMuted, fontSize = 12.sp)
            }
        }
    }
}
