package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.CsvImport
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word

/**
 * Customers from another ledger app (Phase 9d), shown before anything is
 * written: how many the file holds, how many are already in the book (left
 * alone, never doubled), the first few as they will come in, and which way
 * the file's sign runs — apps disagree, and a wrong guess would turn every
 * debt into a credit, so the owner sees the result and chooses.
 */
@Composable
fun ImportPreviewDialog(
    fresh: List<CsvImport.Row>,
    skipped: Int,
    onCancel: () -> Unit,
    onImport: (List<CsvImport.Row>) -> Unit
) {
    val c = LocalRoshanColors.current
    var owesMe by remember { mutableStateOf(true) }
    val rows = if (owesMe) fresh else fresh.map { it.copy(balance = -it.balance) }
    val title = word(W.import_csv)
    // In the app's frame (Marhala 4): the figures and the sign's choice first, Import the one accent button.
    AppDialog(onCloseRequest = onCancel, title = title, state = rememberDialogState(width = fitW(560.dp), height = fitH(600.dp))) {
        DialogFrame(title, onClose = onCancel, footer = {
            FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
            FluentButton(word(W.import_csv_go), { onImport(rows) }, kind = ButtonKind.ACCENT, enabled = rows.isNotEmpty())
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Two figures, never one net (25 Oct 2026, the audit): what comes in to collect and what
                // comes in owed out, each with its side, so flipping the file's sign shows on screen.
                Text(plural(WP.customer_count, rows.size, rows.size), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(word(W.you_will_get) + "  " + Format.money(rows.filter { it.balance > 0 }.sumOf { it.balance }), color = c.owedToMe, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(word(W.you_will_give) + "  " + Format.money(rows.filter { it.balance < 0 }.sumOf { -it.balance }), color = c.iOwe, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (skipped > 0) Text(word(W.import_csv_skipped, skipped), color = c.textMuted, fontSize = 13.sp)
                Text(word(W.import_csv_sign), color = c.ink, fontSize = 13.sp)
                TwoWay(word(W.import_csv_owes_me), word(W.import_csv_i_owe), owesMe) { owesMe = it }
                Rule()
                rows.take(6).forEach { r ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(Format.name(r.name), color = c.ink, fontSize = 14.sp)
                            r.phone?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 12.sp) }
                        }
                        Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                            Text(
                                Format.money(kotlin.math.abs(r.balance)),
                                color = if (r.balance >= 0) c.owedToMe else c.iOwe, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                            // Its side in words too, not by colour alone.
                            Text(word(com.innovation313.roshankhata.screens.balanceSideWord(r.balance)), color = c.textMuted, fontSize = 11.sp)
                        }
                    }
                }
                if (rows.size > 6) Text("…", color = c.textMuted)
            }
        }
    }
}
