package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.innovation313.roshankhata.data.CounterDraft
import com.innovation313.roshankhata.data.ItemGroups
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The counter's item grid on the computer (21 Oct 2026): the products as
 * tiles for a touch screen or a quick click, each product's variants behind
 * its one tile ([ItemGroups]), the categories as pills above. A tap rings it
 * up; a tile with variants asks which one; the ticket's count shows on it.
 */
@Composable
fun ItemGridPane(products: List<Product>, onHand: Map<Long, Double?>, draft: CounterDraft, onPick: (Product) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    var category by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf<ItemGroups.Group?>(null) }
    val categories = remember(products) { ItemGroups.categories(products) }
    val groups = remember(products, category) { ItemGroups.of(products, category) }
    fun price(p: Product) = if (draft.hasParty) p.creditPrice ?: p.salePrice else p.salePrice ?: p.creditPrice
    fun onTicket(id: Long) = draft.lines.filter { it.productId == id }.sumOf { it.qtyValue }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (categories.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FluentPill(word(W.pos_all), category == null) { category = null }
            categories.forEach { k -> FluentPill(k, category == k) { category = k } }
        }
        if (groups.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(word(W.no_items_yet), color = c.textMuted) }
        else LazyVerticalGrid(GridCells.Adaptive(132.dp), Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups, key = { it.main.id }) { g ->
                val inTicket = (listOf(g.main) + g.variants).sumOf { onTicket(it.id) }
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 86.dp).clip(Look.card)
                        .background(if (inTicket > 0.0) Look.chosen else Look.layer).border(1.dp, if (inTicket > 0.0) Look.accent else Look.strokeCard, Look.card)
                        .press(Look.card) { if (g.choices.size == 1) onPick(g.choices[0]) else choosing = g }.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(g.main.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val one = g.choices.singleOrNull()
                    Text(
                        when {
                            one == null -> "+" + g.variants.size
                            else -> price(one)?.let { Format.money(it) } ?: "–"
                        },
                        color = c.inkSoft, fontSize = 13.sp, maxLines = 1, softWrap = false
                    )
                    if (inTicket > 0.0) Text("× " + Calc.trim(inTicket), color = Look.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    else one?.let { onHand[it.id] }?.let { Text(Calc.trim(it) + (g.main.defaultUnit?.let { u -> " $u" } ?: ""), color = if (it <= 0.0) c.owedToMe else c.textMuted, fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
    }
    choosing?.let { g ->
        val title = word(W.pos_choose_variant, g.main.name)
        AppDialog(onCloseRequest = { choosing = null }, onPreviewKeyEvent = escCloses { choosing = null }, title = title, state = rememberDialogState(width = fitW(440.dp), height = fitH(480.dp))) {
            DialogFrame(title, onClose = { choosing = null }, footer = { FluentButton(word(W.cancel), { choosing = null }, kind = ButtonKind.STANDARD) }) {
                g.choices.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().press { onPick(p); choosing = null }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (p.id == g.main.id) p.name else ItemGroups.label(p), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        price(p)?.let { Text(Format.money(it), color = c.inkSoft, fontSize = 14.sp) }
                    }
                    if (i < g.choices.lastIndex) Rule()
                }
            }
        }
    }
}

/**
 * The customer's own screen (21 Oct 2026): a second window, meant for the
 * second monitor, that draws the ticket as it is rung up — the items, the
 * total, what was paid and the change — and the shop's payment QR (the one
 * on the Business Profile page) beside "Scan to pay". The QR is the shop's
 * own fixed one: it carries no amount, and the window says so; a bank's
 * dynamic QR with the amount needs the bank's own merchant service.
 */
@Composable
fun CustomerDisplay(draft: CounterDraft, shopName: String, paymentQr: java.io.File?, onClose: () -> Unit) {
    // On the second screen when there is one; else where Windows puts it, to be dragged.
    val screens = remember { runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds } }.getOrDefault(emptyList()) }
    val second = screens.getOrNull(1)
    val state = rememberWindowState(
        placement = if (second != null) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = second?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
        size = DpSize(900.dp, 640.dp)
    )
    val qr = remember(paymentQr) { paymentQr?.takeIf { it.isFile }?.let { f -> runCatching { javax.imageio.ImageIO.read(f)?.toComposeImageBitmap() }.getOrNull() } }
    // The app's direction and text size carried into the window of its own (R13, 23 Oct 2026).
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    Window(onCloseRequest = onClose, state = state, title = word(W.cd_title), onPreviewKeyEvent = escCloses(onClose)) {
        KeepAppLook(dir, fontScale) { CustomerScreen(draft, shopName, qr) }
    }
}

/** What the customer's window shows — apart, so the proof pictures can draw it. */
@Composable
fun CustomerScreen(draft: CounterDraft, shopName: String, qr: androidx.compose.ui.graphics.ImageBitmap?) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxSize().background(Look.base).padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(shopName, color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (draft.lines.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(word(W.cd_welcome), color = c.inkSoft, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f).clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)) {
                    itemsIndexed(draft.lines, key = { _, l -> System.identityHashCode(l) }) { i, line ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.name, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Calc.trim(line.qtyValue) + (line.unit?.let { " $it" } ?: "") + " × " + Format.money(line.rateValue), color = c.inkSoft, fontSize = 17.sp, maxLines = 1)
                            }
                            Text(Format.money(line.total), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                        if (i < draft.lines.lastIndex) Rule()
                    }
                }
            }
            val t = draft.totals
            if (t.discount > 0.0) Row(Modifier.fillMaxWidth()) {
                Text(word(W.pos_discount), Modifier.weight(1f), color = c.inkSoft, fontSize = 20.sp)
                Text("−" + Format.money(t.discount), color = c.ink, fontSize = 20.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(word(W.pos_grand_total), Modifier.weight(1f), color = c.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(Format.money(t.grandTotal), color = c.ink, fontSize = 48.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
            val rest = draft.remaining
            if (draft.paid > 0.0 && rest < -0.005) Text(word(W.cd_change, Format.money(-rest)), Modifier.fillMaxWidth(), color = Look.accent, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
        if (qr != null) Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(word(W.cd_scan), color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Image(qr, word(W.cd_scan), Modifier.size(280.dp).clip(Look.card).background(androidx.compose.ui.graphics.Color.White).padding(10.dp))
            Text(word(W.cd_qr_note), color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
