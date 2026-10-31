package com.innovation313.roshankhata.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Stock
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * A stock count (POS Phase 6): every product with what the book says, and a
 * box for what was found. Only boxes filled in are counted; a blank box is
 * "not counted today", never zero. The same form on the phone and inside
 * the computer's dialog; the host writes it ([KhataDao.recordCount]).
 */
class CountDraft(stock: List<Stock.ProductStock>) {
    class Line(val stock: Stock.ProductStock) {
        var counted by mutableStateOf("")
        // A shelf found empty is counted as 0 (21 Oct 2026: evalAmount turns 0 into "nothing typed",
        // so an empty shelf was skipped and the book kept its old figure). Never below zero.
        // Not where the book cannot say what it holds (units that do not convert, 22 Oct 2026, the review):
        // a count there was written as if the book held 0.
        val value: Double? get() = if (stock.onHand == null) null
            else counted.takeIf { it.isNotBlank() }?.let { Calc.evalPad(it) }?.takeIf { it >= 0.0 && it.isFinite() }
    }
    val lines = com.innovation313.roshankhata.data.NameOrder.sortedBy(stock) { it.name }.map { Line(it) }
    var note by mutableStateOf("")
    fun counts(): Map<Long, Double> = lines.mapNotNull { l -> l.value?.let { l.stock.productId to it } }.toMap()
}

@Composable
fun StockCountForm(draft: CountDraft, onSave: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) draft.lines else draft.lines.filter { NameSearch.matches(it.stock.name, null, q) }
    Column(modifier.fillMaxSize()) {
        Text(word(W.stock_count_help), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = c.inkSoft, fontSize = 13.sp)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text(word(W.tile_products)) }, singleLine = true)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(shown, key = { it.stock.productId }) { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(0.6f)) {
                        Text(l.stock.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(word(W.po_book) + ": " + (l.stock.onHand?.let { Calc.trim(it) } ?: "?") + (com.innovation313.roshankhata.data.UnitWords.label(l.stock.unit).let { u -> if (u.isEmpty()) "" else " $u" }), color = c.textMuted, fontSize = 12.sp)
                    }
                    // A share of the row, not 124 dp (30 Oct 2026, the phone audit: "Dihitung" and the Farsi label were clipped).
                    if (l.stock.onHand == null) Text(word(W.stock_units_differ), Modifier.weight(0.4f), color = c.textMuted, fontSize = 12.sp)
                    else CalcField(l.counted, { l.counted = it }, Modifier.weight(0.4f), kind = CalcKind.QUANTITY, label = word(W.stock_counted_hint))
                }
                HorizontalDivider(color = c.pageBg)
            }
        }
        OutlinedTextField(draft.note, { draft.note = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text(word(W.note_optional)) }, singleLine = true)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(word(W.cancel), color = c.ink) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, enabled = draft.counts().isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen, contentColor = Color.White)) {
                Text(word(W.save), fontWeight = FontWeight.Bold) // greyed with the button when disabled (31 Oct 2026)
            }
        }
    }
}
