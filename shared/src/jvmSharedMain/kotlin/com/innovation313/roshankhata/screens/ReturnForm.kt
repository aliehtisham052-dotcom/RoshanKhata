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
    /**
     * What each rupee of the sold lines came to in the end (21 Oct 2026): the
     * ticket's discount (with any points spent), the tax added on top and the
     * round-off, spread over the lines by their worth — what the customer
     * actually paid for each. A return gives back that, not the shelf price:
     * two bottles at 1,250 with 250 off the ticket come back at 1,125 each,
     * and 10,000 of goods with 17% tax on top at 11,700. Until then the line
     * price came back, so a discounted walk-in was refunded more than he paid.
     */
    val share: Double = sale.sale.subtotal.takeIf { it > 0.0 }?.let { sale.sale.grandTotal / it } ?: 1.0

    class Line(val item: com.innovation313.roshankhata.data.SaleItem, val left: Double, private val sale: com.innovation313.roshankhata.data.Sale) {
        var qty by mutableStateOf("")
        val qtyValue: Double get() = Calc.evalAmount(qty) ?: 0.0
        /** Per unit as paid for: the line's own discount and its share of the ticket's spread over it (the DAO's own rule). */
        val rate: Double get() = ReturnMath.paidRate(sale, item)
        val total: Double get() = com.innovation313.roshankhata.data.SaleMath.lineTotal(qtyValue, rate)
    }

    val lines: SnapshotStateList<Line> = mutableStateListOf<Line>().apply {
        sale.items.forEach { add(Line(it, it.quantity - (returnedSoFar[it.id] ?: 0.0), sale.sale)) }
    }
    var reason by mutableStateOf("")
    /** Being posted: the button waits, so a double tap never writes the return twice (21 Oct 2026). */
    var posting by mutableStateOf(false)
    var refundMethod by mutableStateOf(if (sale.sale.partyId != null) SaleReturn.ACCOUNT else PaymentMethod.CASH)
    var refundText by mutableStateOf("")

    val hasParty: Boolean get() = sale.sale.partyId != null
    val total: Double get() = ReturnMath.total(toItems())
    /**
     * The money handed back: what is typed, or the whole return when the box is
     * empty. Something typed that is not a figure is refused below, not taken
     * as the whole (21 Oct 2026: "1,2o0" refunded the full total unseen).
     */
    val refund: Double get() = if (refundMethod == SaleReturn.ACCOUNT) 0.0 else if (refundText.isBlank()) total else (Calc.evalAmount(refundText) ?: -1.0)

    fun toItems(): List<SaleReturnItem> = lines.filter { it.qtyValue > 0.0 }.map { l ->
        SaleReturnItem(
            returnId = 0, saleItemId = l.item.id, productId = l.item.productId, itemName = l.item.itemName,
            quantity = l.qtyValue, unit = l.item.unit, rate = l.rate, billItemId = l.item.billItemId
        )
    }

    val problem: ReturnMath.Problem?
        get() = ReturnMath.check(toItems(), refundMethod, refund, total, hasParty, reason, lines.associate { it.item.id to it.left })

    fun toReturn(createdBy: String? = com.innovation313.roshankhata.data.Access.userName) = SaleReturn(
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

/**
 * A failed counter write (a sale, a hold, a void, a return) in words, as a W
 * name (23 Oct 2026, the review): the reason where it is one the counter
 * knows, else [fallback] — never the exception's English message or another
 * screen's sentence. Both counters use it.
 */
fun counterFailWord(e: Throwable, fallback: String): String = when (e) {
    is com.innovation313.roshankhata.data.SaleMath.Refused -> problemWord(e.problem)
    is ReturnMath.Refused -> returnProblemWord(e.problem)
    is com.innovation313.roshankhata.data.CounterRefused -> when (e.why) {
        com.innovation313.roshankhata.data.CounterRefused.Why.CUSTOMER_BINNED -> W.pos_customer_binned
        com.innovation313.roshankhata.data.CounterRefused.Why.HELD_GONE -> W.pos_held_gone
        com.innovation313.roshankhata.data.CounterRefused.Why.NOT_POSTED -> W.pos_ticket_not_posted
        com.innovation313.roshankhata.data.CounterRefused.Why.NOT_THIS_TICKET -> fallback
        com.innovation313.roshankhata.data.CounterRefused.Why.NOT_ALLOWED -> W.access_denied
    }
    else -> fallback
}

/** A refused void in words (W names), on both counters; [VoidRefusal.NOT_DONE] and anything else is [fallback]. */
fun voidRefusalWord(r: com.innovation313.roshankhata.data.SaleMath.VoidRefusal?, fallback: String): String = when (r) {
    com.innovation313.roshankhata.data.SaleMath.VoidRefusal.HAS_RETURNS -> W.pos_void_has_returns
    com.innovation313.roshankhata.data.SaleMath.VoidRefusal.DAY_CLOSED -> W.pos_void_day_closed
    com.innovation313.roshankhata.data.SaleMath.VoidRefusal.SHIFT_CLOSED -> W.pos_void_shift_closed
    else -> fallback
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
                CalcField(l.qty, { l.qty = it }, Modifier.width(112.dp), kind = CalcKind.QUANTITY, label = word(W.pos_col_qty), enabled = l.left > 0.0)
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
            CalcField(draft.refundText, { draft.refundText = it }, Modifier.fillMaxWidth(), label = word(W.amount), placeholder = Format.money(draft.total))
        }
        message?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        val problem = draft.problem
        if (problem != null && draft.toItems().isNotEmpty()) Text(word(returnProblemWord(problem)), color = c.textMuted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(word(W.cancel), color = c.ink) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (!draft.posting) onPost() }, enabled = problem == null && !draft.posting, colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen)) {
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
