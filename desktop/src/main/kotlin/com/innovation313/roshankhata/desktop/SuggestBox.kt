package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.ProductName
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.SmartSuggest
import com.innovation313.roshankhata.ui.withStarters
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The shop's products for the boxes that suggest them (25 Oct 2026, the audit: the computer's invoice and
 * bill lines suggested nothing, the phone's do): read once when a form opens and folded once
 * ([SmartSuggest.Shelf], the phone's own rule, now in :shared), with the trade's starter goods while the
 * shop has fewer than five products of its own (the phone's withStarters).
 */
class ProductShelf(val products: List<Product>, starters: List<String>, starterLabel: String) {
    val shelf = SmartSuggest.Shelf(products.withStarters(starters, starterLabel))

    /** The product a picked row names, by the name rule products use; null for a starter. */
    fun product(name: String): Product? {
        val key = ProductName.key(name)
        return products.firstOrNull { ProductName.key(it.name) == key }
    }
}

/** The open book's [ProductShelf], read when the form is first drawn; null until then (the box is a plain box). */
@Composable
fun rememberProductShelf(dao: KhataDao): ProductShelf? {
    val trade = LocalTrade.current
    val starters = remember(trade) { trade.starters() }
    val label = word(W.vocab_suggested)
    var shelf by remember(dao) { mutableStateOf<ProductShelf?>(null) }
    LaunchedEffect(dao, starters, label) {
        shelf = withContext(Dispatchers.IO) { ProductShelf(dao.productsOnce(), starters, label) }
    }
    return shelf
}

/**
 * A box that offers the shop's products as their names are typed: the best [rows] by the phone's rule,
 * each with its company · unit (a starter says it is suggested for the shop), under the box; a click fills
 * it ([onPick]). An offer, never a decision — a product the book does not know is typed and kept like any
 * other, and the list closes once the box holds a name it offers. A pick, a click beside the list and Esc
 * close it until the next letter; Esc closes the list, not the dialog ([EscFirst]).
 */
@Composable
fun SuggestBox(
    value: String,
    onValueChange: (String) -> Unit,
    shelf: ProductShelf?,
    label: String,
    modifier: Modifier = Modifier,
    rows: Int = 6,
    onPick: (SmartSuggest.Item) -> Unit = { onValueChange(it.value) }
) {
    val c = LocalRoshanColors.current
    var focused by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    val typed = value.trim()
    val hits = remember(typed, shelf) {
        if (shelf == null || typed.isEmpty()) emptyList()
        else shelf.shelf.rank(typed, rows).filterNot { it.value.trim().equals(typed, ignoreCase = true) }
    }
    val open = focused && !closed && hits.isNotEmpty()
    DisposableEffect(open) {
        val close = { closed = true }
        if (open) EscFirst.push(close)
        onDispose { EscFirst.pop(close) }
    }
    Box(modifier) {
        Outlined(
            value, { onValueChange(it); closed = false },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            label = { Text(label) },
            singleLine = true
        )
        // Never takes the keys: the owner keeps typing in the box while the list follows him.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { closed = true },
            properties = PopupProperties(focusable = false),
            containerColor = Look.layer
        ) {
            hits.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.value, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.subtitle?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    },
                    onClick = { closed = true; onPick(item) }
                )
            }
        }
    }
}
