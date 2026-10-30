package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.CounterDraft
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ScaleCode
import com.innovation313.roshankhata.data.SaleMath
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The counter on the phone (POS Phase 1): the item box with the scan button,
 * the ticket's lines, and a bottom band with the total and Pay. Pay turns
 * the page into the payment step — who, the discount, the payments — with
 * Complete at its foot, as the phone's entry dialog goes in two steps.
 *
 * Draws a [CounterDraft], the same state the computer's counter draws; the
 * phone's activity does the database work and the camera, handed in as
 * callbacks, so this screen knows no Android.
 */
@Composable
fun CounterSaleScreen(
    draft: CounterDraft,
    products: List<Product>,
    parties: List<PartyWithBalance>,
    onHand: Map<Long, Double?>,
    heldCount: Int,
    message: String?,
    onScan: (() -> Unit)?,
    onHold: () -> Unit,
    onShowHeld: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    /** How this shop's weighing scale prints its labels ([ScaleCode]). */
    scaleRule: ScaleCode.Rule = ScaleCode.Rule(),
    /** A customer's loyalty points, read when they are chosen (null = the screen shows none). */
    loyaltyBalanceOf: (suspend (Long) -> Double)? = null,
    /**
     * Raised by the host each time a ticket is written: the payment step
     * closes then, not when Complete is pressed (23 Oct 2026, the review: a
     * PIN request or a refusal dropped the cashier back on the item list).
     */
    postedTick: Int = 0,
    /**
     * The big-shop tools (BookSettings.counterAdvanced, 24 Oct 2026): off,
     * the payment step has no tax box unless one is typed already.
     */
    advanced: Boolean = true,
    /** "When will he pay?" for what goes to the chosen customer's account (null: not offered). */
    onChooseDue: (() -> Unit)? = null,
    /** The recent tickets (their return, void and invoice), offered on an empty till (null: not offered). */
    onShowRecent: (() -> Unit)? = null
) {
    val c = LocalRoshanColors.current
    var paying by remember { mutableStateOf(false) }
    LaunchedEffect(postedTick) { if (postedTick > 0) paying = false }
    var query by remember { mutableStateOf("") }
    var choosingParty by remember { mutableStateOf(false) }
    // The item grid (21 Oct 2026): tiles to tap instead of the ticket's lines.
    var grid by remember { mutableStateOf(false) }
    val matches = remember(query, products) { if (query.isBlank()) emptyList() else CounterDraft.search(products, query, 8) }
    LaunchedEffect(draft.partyId) {
        val id = draft.partyId
        draft.pointsBalance = if (id != null && draft.loyalty != null) loyaltyBalanceOf?.invoke(id) ?: 0.0 else 0.0
    }

    fun ring(p: Product) { draft.add(p, onHand[p.id]); query = "" }

    Column(modifier.fillMaxSize().background(c.pageBg)) {
        if (!paying) {
            // ---- Step 1: the ticket ----
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f).testTag("posSearch"),
                    placeholder = { Text(word(W.pos_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val q = query.trim()
                        val exact = products.firstOrNull { it.barcode == q }
                        // A weighing scale's label (21 Oct 2026): the item and its weight in one code.
                        val weighed = if (exact == null) ScaleCode.line(q, scaleRule, products) { draft.unitRate(it) } else null
                        when {
                            exact != null -> ring(exact)
                            weighed != null -> { draft.addWeighed(weighed, onHand[weighed.product.id]); query = "" }
                            matches.isNotEmpty() -> ring(matches[0])
                            q.isNotEmpty() -> { draft.addFree(q); query = "" }
                        }
                    })
                )
                if (onScan != null) {
                    Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen), modifier = Modifier.testTag("posScan")) {
                        Text(word(W.pos_scan), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (matches.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(c.surface, RoundedCornerShape(12.dp))) {
                    matches.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().clickable { ring(p) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val under = listOfNotNull(p.company, onHand[p.id]?.let { "${Calc.trim(it)} ${com.innovation313.roshankhata.data.UnitWords.label(p.defaultUnit)}".trim() })
                                if (under.isNotEmpty()) Text(under.joinToString(" · "), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                            }
                            (if (draft.hasParty) p.creditPrice ?: p.salePrice else p.salePrice)?.let {
                                Text(Format.money(it), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (i < matches.lastIndex) HorizontalDivider(color = c.pageBg)
                    }
                }
            }
            if (products.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !grid, onClick = { grid = false }, label = { Text(word(W.pos_ticket) + if (draft.lines.isNotEmpty()) " (" + draft.lines.size + ")" else "", fontSize = 12.sp) })
                FilterChip(selected = grid, onClick = { grid = true }, label = { Text(word(W.pos_items), fontSize = 12.sp) })
            }
            if (grid) {
                ItemGrid(
                    products, onHand, onTicket = { id -> draft.lines.filter { it.productId == id }.sumOf { it.qtyValue } },
                    hasParty = draft.hasParty, onPick = { ring(it) }, modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else if (draft.lines.isEmpty()) {
                // An empty till says how the counter is used (24 Oct 2026, the owner could not tell).
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CounterHowTo(Modifier.padding(24.dp))
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(draft.lines, key = { _, l -> System.identityHashCode(l) }) { _, line ->
                        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (line.productId == null) {
                                    OutlinedTextField(line.name, { line.name = it }, Modifier.weight(1f), placeholder = { Text(word(W.item_name_hint)) }, singleLine = true)
                                } else {
                                    Column(Modifier.weight(1f)) {
                                        Text(line.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        line.shortBy?.let { Text(word(W.pos_stock_low, Calc.trim(line.onHand ?: 0.0)), color = c.owedToMe, fontSize = 12.sp) }
                                        // "Tax %: 33" — the word already carries its % (it read "Tax % 33%", 30 Oct 2026).
                                        line.taxRate?.takeIf { it > 0.0 }?.let { Text(word(W.pos_tax) + ": " + Calc.trim(it), color = c.textMuted, fontSize = 12.sp) }
                                        // A product also sold by the pack (schema 36): the line switches between the two.
                                        if (line.canSwitchUnit) {
                                            TextButton(onClick = { line.product?.let { line.switchUnit(draft.unitRate(it)) } }) {
                                                Text(word(W.pos_sell_by, line.otherUnit.orEmpty()), color = c.brandGreenText, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                                Text(Format.money(line.total), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { draft.remove(line) }) { Text("✕", color = c.textMuted) }
                            }
                            // Quantity and rate share a row, the discount has its own (30 Oct 2026, the phone audit: three
                            // across left each box ~28 dp of text — "Discount" read "Di" and a rate of 1,250 was hidden).
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CalcField(line.qty, { line.qty = it }, Modifier.weight(1f).testTag("posQty"), kind = CalcKind.QUANTITY, label = word(W.pos_col_qty) + (com.innovation313.roshankhata.data.UnitWords.label(line.unit).let { u -> if (u.isEmpty()) "" else " ($u)" }))
                                CalcField(line.rate, { line.rate = it }, Modifier.weight(1f), label = word(W.pos_col_rate))
                            }
                            CalcField(line.discount, { line.discount = it }, Modifier.fillMaxWidth(), label = word(W.pos_discount))
                        }
                    }
                }
            }
            message?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = c.inkSoft, fontSize = 13.sp) }
            // The band: the total on its own line, then held/hold/recent and Pay sharing a row (30 Oct 2026, the
            // owner's phone: in one Row the two buttons were measured first and the weighted total was left a
            // letter wide — "Tot / al / R"). Each button's words stay on one line and end in "…" if they must.
            Column(Modifier.fillMaxWidth().background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(word(W.pos_grand_total), Modifier.weight(1f), color = c.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Format.money(draft.totals.grandTotal), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val side = Modifier.weight(1f)
                    if (heldCount > 0) OutlinedButton(onClick = onShowHeld, modifier = side) { Text(word(W.pos_held_tickets, heldCount), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    else if (draft.lines.isNotEmpty()) OutlinedButton(onClick = onHold, modifier = side) { Text(word(W.pos_hold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    // Recent sales in sight (24 Oct 2026: they were only in the top bar's menu, and never found).
                    else if (onShowRecent != null) OutlinedButton(onClick = onShowRecent, modifier = side.testTag("posRecent")) { Text(word(W.pos_recent), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Button(
                        onClick = { paying = true }, enabled = draft.lines.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen), modifier = Modifier.weight(1f).testTag("posPay")
                    // Disabled, the fill fades to grey: white words on it could not be read (lesson 10).
                    ) { Text(word(W.pos_next_pay), color = if (draft.lines.isNotEmpty()) Color.White else c.textMuted, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        } else {
            // ---- Step 2: who and how paid ----
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(word(W.customer), color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                // The chips scroll sideways rather than squeeze (30 Oct 2026, the phone audit: a long name or a larger
                // text size pressed the second chip into a column).
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !draft.hasParty, onClick = { draft.setParty(null, null); choosingParty = false }, label = { Text(word(W.pos_walk_in), maxLines = 1) })
                    FilterChip(selected = draft.hasParty, onClick = { choosingParty = true }, label = { Text(draft.partyName?.let { Format.name(it) } ?: word(W.pos_choose_customer), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) }, modifier = Modifier.testTag("posCustomer"))
                }
                if (choosingParty) {
                    var q by remember { mutableStateOf("") }
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text(word(W.search_parties)) }, singleLine = true)
                    // With nothing typed, the customers dealt with most recently come first; a search finds anyone.
                    // The list scrolls inside its own box (30 Oct 2026, the owner's phone: it stood 260 dp tall
                    // with no scroll of its own, so the names below its foot could not be reached).
                    val needle = q.trim().lowercase()
                    val shown = parties.filter { it.isCustomer && NameSearch.matches(it.name, it.phone, needle) }
                        .let { found -> if (needle.isEmpty()) found.sortedByDescending { it.lastActivity } else found }
                        .take(50)
                    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).background(c.surface, RoundedCornerShape(12.dp)).verticalScroll(rememberScrollState())) {
                        shown.forEachIndexed { i, p ->
                            Row(Modifier.fillMaxWidth().clickable { draft.setParty(p.id, p.name); choosingParty = false }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(Format.name(p.name), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // A settled account says nothing; one that owes says how much, in the khata's colour.
                                if (com.innovation313.roshankhata.data.Money.isPositive(p.balance) || com.innovation313.roshankhata.data.Money.isNegative(p.balance))
                                    Text(Format.customerBalance(p.balance), color = if (p.balance > 0) c.owedToMe else c.iOwe, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                            if (i < shown.lastIndex) HorizontalDivider(color = c.pageBg)
                        }
                    }
                }
                // Loyalty (21 Oct 2026): the customer's points, and spending them on this ticket.
                val rule = draft.loyalty
                if (draft.hasParty && rule != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(word(W.loyalty_points, Calc.trim(draft.pointsBalance), Format.money(rule.value(draft.pointsBalance))), Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp)
                        FilterChip(selected = draft.pointsUsed > 0.0, onClick = { draft.usePoints(draft.pointsUsed <= 0.0) }, enabled = draft.pointsUsed > 0.0 || draft.pointsMax > 0.0,
                            label = { Text(word(W.loyalty_use), fontSize = 12.sp) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcField(draft.discount, { draft.discount = it }, Modifier.weight(1f), label = word(W.pos_discount))
                    // Tax is a big-shop tool; a figure already typed (a held ticket) stays in sight either way.
                    if (advanced || draft.taxPercent.isNotBlank()) CalcField(draft.taxPercent, { draft.taxPercent = it }, Modifier.weight(1f), kind = CalcKind.PERCENT, label = word(W.pos_tax))
                }
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Figure(word(W.pos_subtotal), Format.money(draft.totals.subtotal))
                    if (draft.handDiscount > 0.0) Figure(word(W.pos_discount), "−" + Format.money(draft.handDiscount))
                    if (draft.pointsValue > 0.0) Figure(word(W.loyalty_used, Calc.trim(draft.pointsUsed), Format.money(draft.pointsValue)), "−" + Format.money(draft.pointsValue))
                    if (draft.totals.tax > 0.0) Figure(word(W.pos_tax), Format.money(draft.totals.tax))
                    if (draft.totals.taxIncluded > 0.0) Figure(word(W.pos_tax_included), Format.money(draft.totals.taxIncluded))
                    // Settled to whole rupees, as on the computer (23 Oct 2026, the review): the paisa no drawer holds.
                    // A switch, with what the rounding moved beside it (30 Oct 2026: a chip reading "0" did not
                    // look like something to press, and said nothing of whether rounding was on).
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(word(W.pos_round_off), Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp)
                        if (draft.roundToWhole && draft.totals.roundOff != 0.0) Text(Format.money(draft.totals.roundOff), color = c.ink, fontSize = 13.sp, maxLines = 1, softWrap = false)
                        androidx.compose.material3.Switch(checked = draft.roundToWhole, onCheckedChange = { draft.roundToWhole = it }, modifier = Modifier.testTag("posRoundOff"))
                    }
                    HorizontalDivider(color = c.pageBg)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(word(W.pos_grand_total), Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(Format.money(draft.totals.grandTotal), color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(word(W.pos_paid), color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                draft.payments.forEach { p ->
                    Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The five ways of paying scroll sideways (30 Oct 2026, the phone audit: in one plain Row they
                        // needed ~445 dp of a phone's ~304 and "Online" / "Mobile wallet" were pressed into columns).
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaymentMethod.ALL.forEach { m ->
                                // The chosen way wears the same fill as every other chosen chip here (30 Oct 2026: it wore
                                // "I gave"'s red, which says money went out — the wrong word for a payment coming in).
                                FilterChip(selected = p.method == m, onClick = { p.method = m }, label = { Text(methodWord(m), fontSize = 12.sp, maxLines = 1, softWrap = false) })
                            }
                        }
                        // The amount with its ✕, and the reference under it at full width (beside the amount each box
                        // kept ~47 dp of text and the long label wrapped to four lines inside the box).
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CalcField(p.amount, { p.amount = it }, Modifier.weight(1f).testTag("posPayAmount"), label = word(W.amount))
                            TextButton(onClick = { draft.removePayment(p) }) { Text("✕", color = c.textMuted) }
                        }
                        if (p.method != PaymentMethod.CASH) OutlinedTextField(p.reference, { p.reference = it }, Modifier.fillMaxWidth(), label = { Text(word(W.pos_reference_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Half the row each, the words allowed two lines: in Urdu or Sindhi on a small phone the
                    // second button was pushed off the edge (21 Oct 2026).
                    OutlinedButton(onClick = { draft.payRestInCash() }, enabled = draft.remaining > 0.005, modifier = Modifier.weight(1f).testTag("posAllCash")) { Text(word(W.pos_pay_full_cash), color = c.ink, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    OutlinedButton(onClick = { draft.addPayment(PaymentMethod.CASH) }, modifier = Modifier.weight(1f)) { Text(word(W.pos_add_payment), color = c.ink, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
                val rest = draft.remaining
                // When the rest will be paid, as a khata entry asks (24 Oct 2026): it becomes the customer's promised day.
                if (rest > 0.005 && draft.hasParty && onChooseDue != null) {
                    TextButton(onClick = onChooseDue, modifier = Modifier.testTag("posDue")) {
                        Text(draft.due?.let { word(W.entry_due_set, Format.dateOnly(it)) } ?: word(W.entry_due_none), color = c.brandGreenText, fontSize = 14.sp)
                    }
                }
                when {
                    rest > 0.005 && draft.hasParty -> Figure(word(W.pos_to_account), Format.money(rest), c.owedToMe)
                    rest > 0.005 -> Text(word(W.pos_err_walk_in_unpaid), color = c.owedToMe, fontSize = 13.sp)
                    rest < -0.005 -> Figure(word(W.pos_change), Format.money(-rest), c.iOwe)
                }
                message?.let { Text(it, color = c.inkSoft, fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
            }
            val problem = draft.problem
            Row(Modifier.fillMaxWidth().background(c.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Shared by weight, the main button twice the other (30 Oct 2026, the phone audit: in Roman Urdu or
                // Indonesian, or English at 150%, "Complete sale" wrapped oddly beside a free-sized Cancel).
                OutlinedButton(onClick = { paying = false }, modifier = Modifier.weight(1f)) { Text(word(W.cancel), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Button(
                    onClick = { onComplete() }, enabled = problem == null && !draft.posting,
                    colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen), modifier = Modifier.weight(2f).testTag("posComplete")
                ) { Text(word(W.pos_post), color = if (problem == null && !draft.posting) Color.White else c.textMuted, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }
}

/**
 * How the counter is used, in three lines (24 Oct 2026, the owner: "na mujhe
 * functions ka pata hai"): drawn on an empty till on both platforms.
 */
@Composable
fun CounterHowTo(modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(word(W.pos_lines_empty), color = c.textMuted, fontSize = 14.sp)
        listOf(W.pos_how_1, W.pos_how_2, W.pos_how_3).forEach { Text(word(it), color = c.inkSoft, fontSize = 14.sp) }
    }
}

@Composable
private fun Figure(label: String, figure: String, color: Color? = null) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp)
        Text(figure, color = color ?: c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun methodWord(m: String): String = PaymentMethod.label(LocalWords.current, m)

/** The words for a problem the checks found, for either screen. */
fun problemWord(p: SaleMath.Problem): String = when (p) {
    SaleMath.Problem.NO_LINES -> W.pos_err_no_lines
    SaleMath.Problem.BAD_QUANTITY -> W.pos_err_quantity
    SaleMath.Problem.BAD_RATE -> W.pos_err_rate
    SaleMath.Problem.BAD_DISCOUNT -> W.pos_err_discount
    SaleMath.Problem.BAD_PAYMENT, SaleMath.Problem.BAD_METHOD -> W.pos_err_payment
    SaleMath.Problem.OVERPAID -> W.pos_err_overpaid
    SaleMath.Problem.WALK_IN_UNPAID -> W.pos_err_walk_in_unpaid
    SaleMath.Problem.EMPTY_NAME -> W.pos_err_empty_name
    SaleMath.Problem.RATE_MISSING -> W.pos_err_rate_missing
    SaleMath.Problem.UNREADABLE_DISCOUNT -> W.pos_err_discount_unread
    SaleMath.Problem.BAD_TAX -> W.pos_err_tax
    SaleMath.Problem.UNREADABLE_PAYMENT -> W.pos_err_payment_unread
}
