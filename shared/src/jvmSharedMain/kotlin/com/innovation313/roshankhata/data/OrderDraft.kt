package com.innovation313.roshankhata.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/** The order as a message to the supplier. */
fun orderText(words: Words, shopName: String?, o: PurchaseOrderWithLines, partyName: String): String {
    val b = StringBuilder()
    b.append(words.text(W.po_message_head, o.order.orderNumber, shopName ?: words.text(W.app_name))).append('\n')
    b.append(Format.dateOnly(o.order.timestamp)).append('\n').append(Format.name(partyName)).append("\n\n")
    o.items.forEachIndexed { i, l ->
        b.append(i + 1).append(". ").append(l.itemName).append(" — ").append(Calc.trim(l.quantity)).append(l.unit?.let { " $it" } ?: "")
        l.rate?.let { b.append(" @ ").append(Format.money(it)) }
        b.append('\n')
    }
    o.order.note?.let { b.append('\n').append(it).append('\n') }
    return b.toString()
}

/** The order being written: a supplier and its lines. */
class OrderDraft(val existing: PurchaseOrderWithLines?, val products: List<Product>) {
    class Line(val productId: Long?, name: String, qty: String, val unit: String?, rate: String) {
        var name by mutableStateOf(name)
        var qty by mutableStateOf(qty)
        var rate by mutableStateOf(rate)
    }
    var partyId by mutableStateOf(existing?.order?.partyId)
    var note by mutableStateOf(existing?.order?.note.orEmpty())
    val lines = mutableStateListOf<Line>().apply {
        existing?.items?.forEach { add(Line(it.productId, it.itemName, Calc.trim(it.quantity), it.unit, it.rate?.let { r -> Calc.trim(r) }.orEmpty())) }
    }
    fun add(p: Product, qty: Double = 1.0) {
        lines.firstOrNull { it.productId == p.id }?.let { it.qty = Calc.trim((Calc.evalAmount(it.qty) ?: 0.0) + qty); return }
        lines += Line(p.id, p.name, Calc.trim(qty), p.defaultUnit, p.costPrice?.let { Calc.trim(it) }.orEmpty())
    }
    fun toItems(): List<PurchaseOrderItem> = lines.map {
        PurchaseOrderItem(orderId = 0, productId = it.productId, itemName = it.name.trim(), quantity = Calc.evalAmount(it.qty) ?: 0.0, unit = it.unit, rate = Calc.evalAmount(it.rate))
    }.filter { it.itemName.isNotEmpty() && it.quantity > 0.0 }
    val valid: Boolean get() = partyId != null && toItems().isNotEmpty()
}

