package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.ReturnMath
import com.innovation313.roshankhata.data.SaleReturn
import com.innovation313.roshankhata.data.SaleReturnItem
import com.innovation313.roshankhata.data.SaleWithLines
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * Goods back against a sale (POS Phase 3): each sold line with how many
 * are coming back (what was sold and what already came back under it), the
 * reason, and how the value goes back — money in a method, or credit left
 * on the account. The same form on the phone and inside the computer's
 * dialog frame; the host posts it ([ReturnDraft.toReturn]).
 */
class ReturnDraft(val sale: SaleWithLines, returnedSoFar: Map<Long, Double>) {
    class Line(val item: com.innovation313.roshankhata.data.SaleItem, val left: Double) {
        var qty by mutableStateOf("")
        val qtyValue: Double get() = Calc.evalAmount(qty) ?: 0.0
        /** Per unit as sold, the line's discount spread over it. */
        val rate: Double get() = if (item.quantity > 0.0) com.innovation313.roshankhata.data.LineMath.round(item.lineTotal / item.quantity) else item.rate
        val total: Double get() = com.innovation313.roshankhata.data.SaleMath.lineTotal(qtyValue, rate)
    }

    val lines: SnapshotStateList<Line> = mutableStateListOf<Line>().apply {
        sale.items.forEach { add(Line(it, it.quantity - (returnedSoFar[it.id] ?: 0.0))) }
    }
    var reason by mutableStateOf("")
    var refundMethod by mutableStateOf(if (sale.sale.partyId != null) SaleReturn.ACCOUNT else PaymentMethod.CASH)
    var refundText by mutableStateOf("")

    val hasParty: Boolean get() = sale.sale.partyId != null
    val total: Double get() = ReturnMath.total(toItems())
    val refund: Double get() = if (refundMethod == SaleReturn.ACCOUNT) 0.0 else (Calc.evalAmount(refundText) ?: total)

    fun toItems(): List<SaleReturnItem> = lines.filter { it.qtyValue > 0.0 }.map { l ->
        SaleReturnItem(
            returnId = 0, saleItemId = l.item.id, productId = l.item.productId, itemName = l.item.itemName,
            quantity = l.qtyValue, unit = l.item.unit, rate = l.rate, billItemId = l.item.billItemId
        )
    }

    val problem: ReturnMath.Problem?
        get() = ReturnMath.check(toItems(), refundMethod, refund, total, hasParty, reason, lines.associate { it.item.id to it.left })

    fun toReturn(createdBy: String? = null) = SaleReturn(
        saleId = sale.sale.id, partyId = sale.sale.partyId, customerName = sale.sale.customerName,
        total = total, refundMethod = refundMethod, refundAmount = refund, reason = reason.trim(), createdBy = createdBy
    )
}

fun returnProblemWord(p: ReturnMath.Problem): String = when (p) {
    ReturnMath.Problem.NO_LINES -> W.pos_err_no_lines
    ReturnMath.Problem.BAD_QUANTITY -> W.pos_err_quantity
    ReturnMath.Problem.OVER_SOLD -> W.pos_err_over_sold
    ReturnMath.Problem.BAD_REFUND -> W.pos_err_refund
    ReturnMath.Problem.WALK_IN_NEEDS_CASH -> W.pos_err_walk_in_cash
    ReturnMath.Problem.NO_REASON -> W.pos_err_reason
    ReturnMath.Problem.BAD_METHOD -> W.pos_err_payment
}

@Composable
fun ReturnForm(draft: ReturnDraft, onPost: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, message: String? = null) {
    val c = LocalRoshanColors.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(word(W.pos_return_of, draft.sale.sale.saleNumber) + " · " + (draft.sale.sale.customerName?.let { Format.name(it) } ?: word(W.pos_walk_in)), color = c.inkSoft, fontSize = 13.sp)
        draft.lines.forEach { l ->
            Row(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(l.item.itemName, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(word(W.pos_return_qty_help, Calc.trim(l.item.quantity), Calc.trim(l.item.quantity - l.left)) + " · " + Format.money(l.rate), color = c.textMuted, fontSize = 12.sp)
                }
                OutlinedTextField(l.qty, { l.qty = it }, Modifier.width(96.dp), label = { Text(word(W.pos_col_qty)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = l.left > 0.0)
            }
        }
        OutlinedTextField(draft.reason, { draft.reason = it }, Modifier.fillMaxWidth(), label = { Text(word(W.pos_return_reason)) }, singleLine = true)
        Text(word(W.pos_return_refund) + " · " + Format.money(draft.total), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (draft.hasParty) FilterChip(selected = draft.refundMethod == SaleReturn.ACCOUNT, onClick = { draft.refundMethod = SaleReturn.ACCOUNT }, label = { Text(word(W.pos_refund_account), fontSize = 12.sp) })
            PaymentMethod.ALL.forEach { m ->
                FilterChip(selected = draft.refundMethod == m, onClick = { draft.refundMethod = m }, label = { Text(methodWord(m), fontSize = 12.sp) })
            }
        }
        if (draft.refundMethod != SaleReturn.ACCOUNT) {
            OutlinedTextField(draft.refundText, { draft.refundText = it }, Modifier.fillMaxWidth(), label = { Text(word(W.amount)) }, placeholder = { Text(Format.money(draft.total)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        message?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        val problem = draft.problem
        if (problem != null && draft.toItems().isNotEmpty()) Text(word(returnProblemWord(problem)), color = c.textMuted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(word(W.cancel), color = c.ink) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onPost, enabled = problem == null, colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen)) {
                Text(word(W.pos_return), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun methodWord(m: String): String = word(
    when (m) {
        PaymentMethod.CASH -> W.payment_method_cash
        PaymentMethod.BANK -> W.payment_method_bank
        PaymentMethod.CHEQUE -> W.payment_method_cheque
        else -> W.payment_method_online
    }
)
