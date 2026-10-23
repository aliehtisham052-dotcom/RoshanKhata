package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.CsvImport
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
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
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(word(W.import_csv)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word(W.import_csv_found, rows.size, Format.money(rows.sumOf { it.balance })), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                        Text(
                            Format.money(kotlin.math.abs(r.balance)),
                            Modifier.padding(start = 12.dp),
                            color = if (r.balance >= 0) c.owedToMe else c.iOwe, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (rows.size > 6) Text("…", color = c.textMuted)
            }
        },
        confirmButton = { TextButton({ onImport(rows) }, enabled = rows.isNotEmpty()) { Text(word(W.import_csv_go), color = c.brandGreenText) } },
        dismissButton = { FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD) }
    )
}
