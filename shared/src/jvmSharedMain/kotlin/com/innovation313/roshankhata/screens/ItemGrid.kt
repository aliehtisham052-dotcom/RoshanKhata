package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.ItemGroups
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The counter's item grid on the phone (21 Oct 2026): a tile a product, a
 * product's variants behind its one tile ([ItemGroups]), the categories as
 * chips above. A tap rings it up; a tile with variants asks which one. How
 * many are on the ticket already shows on the tile.
 */
@Composable
fun ItemGrid(
    products: List<Product>,
    onHand: Map<Long, Double?>,
    onTicket: (Long) -> Double,
    hasParty: Boolean,
    onPick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    var category by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf<ItemGroups.Group?>(null) }
    val categories = remember(products) { ItemGroups.categories(products) }
    val groups = remember(products, category) { ItemGroups.of(products, category) }
    fun price(p: Product) = (if (hasParty) p.creditPrice ?: p.salePrice else p.salePrice ?: p.creditPrice)
    Column(modifier) {
        if (categories.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text(word(W.pos_all), fontSize = 12.sp) })
            categories.forEach { k -> FilterChip(selected = category == k, onClick = { category = k }, label = { Text(k, fontSize = 12.sp, maxLines = 1) }) }
        }
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(word(W.no_items_yet), color = c.textMuted) }
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(108.dp), Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups, key = { it.main.id }) { g ->
                val inTicket = (listOf(g.main) + g.variants).sumOf { onTicket(it.id) }
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 84.dp).background(if (inTicket > 0.0) c.brandGreenText.copy(alpha = 0.18f) else c.surface, RoundedCornerShape(12.dp))
                        .clickable { if (g.choices.size == 1) onPick(g.choices[0]) else choosing = g }.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(g.main.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val p = if (g.choices.size == 1) price(g.choices[0]) else null
                    Text(
                        when {
                            g.choices.size > 1 -> "+" + g.variants.size
                            p != null -> Format.money(p)
                            else -> "–"
                        },
                        color = c.inkSoft, fontSize = 13.sp, maxLines = 1
                    )
                    val stock = if (g.choices.size == 1) onHand[g.choices[0].id] else null
                    if (inTicket > 0.0) Text("× " + Calc.trim(inTicket), color = c.brandGreenText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    else stock?.let { Text(Calc.trim(it) + (com.innovation313.roshankhata.data.UnitWords.label(g.main.defaultUnit).let { u -> if (u.isEmpty()) "" else " $u" }), color = if (it <= 0.0) c.owedToMe else c.textMuted, fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
    }
    choosing?.let { g ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(word(W.pos_choose_variant, g.main.name)) },
            text = {
                Column {
                    g.choices.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(p); choosing = null }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (p.id == g.main.id) p.name else ItemGroups.label(p), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            price(p)?.let { Text(Format.money(it), color = c.inkSoft, fontSize = 14.sp) }
                        }
                        if (i < g.choices.lastIndex) HorizontalDivider(color = c.pageBg)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = null }) { Text(word(W.cancel)) } }
        )
    }
}
