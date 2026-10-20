package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The shop's trade on the computer, as the phone's Trade, TradeVocab and
 * TradeFeatures (20 Oct): the same twenty-five keys (stored as Trade.name, so
 * a phone backup carries it across), the same words from strings.xml in
 * every language, and the same switches — crop seasons for agri only, the
 * inspector's registers for agri and medical. The core is the same for all.
 */
object Trades {
    /** The phone's Trade entries, in its picker's order. */
    val KEYS = listOf(
        "AGRI", "MEDICAL", "MOBILE", "KIRYANA", "HARDWARE", "DAIRY", "TAILOR", "AUTO", "FURNITURE", "SALON", "SOLAR",
        "CLOTHING", "FOOTWEAR", "ELECTRONICS", "RESTAURANT", "BAKERY", "MEAT", "PRODUCE", "STATIONERY", "COSMETICS",
        "BUILDING", "FUEL", "WHOLESALE", "GENERAL", "OTHER"
    )

    /** A stored key back to one of [KEYS]; unknown or blank is null (never chosen). */
    fun of(stored: String?): String? = stored?.trim()?.uppercase()?.takeIf { it in KEYS }
}

/** The open shop's trade and, for an Other shop, the goods the owner wrote. */
data class TradeKit(val key: String = "AGRI", val ownItems: List<String> = emptyList()) {
    private val low get() = key.lowercase()

    /** Crop seasons, the season row on a sale, the season choices for a promised day (TradeFeatures.seasons). */
    val seasons: Boolean get() = key == "AGRI"

    /** The inspector's stock report and in/out register (TradeFeatures.inspectorReports). */
    val inspector: Boolean get() = key == "AGRI" || key == "MEDICAL"

    /** Whether a section belongs in this trade's rail and Home. */
    fun shows(s: Section): Boolean = when (s) {
        Section.SEASONS -> seasons
        Section.STOCK, Section.REGISTER -> inspector
        else -> true
    }

    @Composable
    private fun list(part: String): List<String> {
        val words = LocalWords.current
        fun of(k: String) = runCatching { words.list("vocab_${k}_$part") }.getOrDefault(emptyList())
        return of(low).ifEmpty { of("agri") }
    }

    /** Three example goods (TradeVocab.items), the owner's own first for an Other shop. */
    @Composable
    fun items(): List<String> {
        val base = list("items")
        return if (ownItems.isEmpty()) base else (ownItems + base).distinct().take(3)
    }

    /** The goods a book of this trade is offered before it has its own (TradeVocab.starters). */
    @Composable
    fun starters(): List<String> = (ownItems + list("starters")).distinct()

    /** The rate list's product types (TradeVocab.types). */
    @Composable
    fun types(): List<String> = list("types")

    /** The trade's units in the owner's language, its own first (TradeVocab.unitLabels). */
    @Composable
    fun units(): List<String> = list("units").map { UnitWords.label(it) }.filter { it.isNotBlank() }

    /** "Item (Milk, Yogurt, Ghee…)" */
    @Composable
    fun itemHint(): String = word(W.vocab_item_hint, items().joinToString(", "))

    /** "Unit (litre, kg, packet…)" */
    @Composable
    fun unitHint(): String = word(W.vocab_unit_hint, units().take(3).joinToString(", "))

    /** The goods paragraph, with this trade's own example ("5 litre Milk, 2 kg Yogurt"). */
    @Composable
    fun goodsHelp(): String {
        val items = items()
        val units = units()
        val example = buildString {
            if (items.isNotEmpty()) append("5 ").append(units.getOrNull(0) ?: "").append(' ').append(items[0])
            if (items.size > 1) append(", 2 ").append(units.getOrNull(1) ?: units.getOrNull(0) ?: "").append(' ').append(items[1])
        }.replace("  ", " ").trim()
        return word(W.vocab_goods_help, example)
    }

    companion object {
        /** From the shop's settings; a book that never chose is AGRI, as on the phone. */
        fun of(stored: String?, otherItems: String?): TradeKit = TradeKit(
            key = Trades.of(stored) ?: "AGRI",
            ownItems = if (Trades.of(stored) == "OTHER") otherItems.orEmpty()
                .split(',', '،', ';', '\n', '·').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
            else emptyList()
        )
    }
}

/** The open shop's trade for every screen below the window. */
val LocalTrade = staticCompositionLocalOf { TradeKit() }

/** Suggestion pills under a field (the trade's units or starter goods); a click fills the field. */
@Composable
fun SuggestionRow(choices: List<String>, current: String, onPick: (String) -> Unit) {
    if (choices.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.take(6).forEach { s -> FilterPill(s, s.equals(current.trim(), ignoreCase = true)) { onPick(s) } }
    }
}

/**
 * The phone's shop-type picker (ShopTypeActivity / TradePicker): every trade
 * as a card with its own icon, name and what it sells; Other asks for the
 * shop's own name and three goods. [mandatory]: the first opening, with no
 * way out but a choice, as the owner asked (20 Oct).
 */
@Composable
fun TradePicker(
    current: String?,
    otherName: String?,
    otherItems: String?,
    mandatory: Boolean,
    onPick: (key: String, otherName: String?, otherItems: String?) -> Unit,
    onCancel: () -> Unit
) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    var chosen by remember { mutableStateOf(Trades.of(current)) }
    var name by remember { mutableStateOf(otherName.orEmpty()) }
    var items by remember { mutableStateOf(otherItems.orEmpty()) }
    Column(Modifier.fillMaxSize().background(c.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(word(if (current == null) W.trade_kicker_new else W.trade_kicker_change), color = c.brandGreenText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(word(W.trade_pick_title), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val perRow = maxOf(2, ((maxWidth.value + 10) / 160).toInt())
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Trades.KEYS.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            val on = key == chosen
                            val low = key.lowercase()
                            Column(
                                Modifier.weight(1f).heightIn(min = 118.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (on) Look.chosen else c.surface)
                                    .border(if (on) 2.dp else 1.dp, if (on) c.brandGreen else Look.line, RoundedCornerShape(16.dp))
                                    .clickable { chosen = key }.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier.size(44.dp).background(TileIcons.color("trade_${low}_bg", isDark()) ?: c.pageBg, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) { Glyph("ic_trade_$low", TileIcons.color("trade_${low}_fg", isDark()) ?: c.ink, 24) }
                                Text(words.text("trade_name_$low"), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                                Text(words.text("trade_eg_$low"), color = c.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (chosen == "OTHER") {
            Text(word(W.trade_other_title), color = c.ink, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text(word(W.trade_other_name_hint)) }, modifier = Modifier.weight(1f))
                OutlinedTextField(items, { items = it }, singleLine = true, placeholder = { Text(word(W.trade_other_items_hint)) }, modifier = Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (!mandatory) androidx.compose.material3.TextButton(onCancel) { Text(word(W.cancel)) }
            val pick = chosen
            FillButton(word(W.save), c.brandGreen, enabled = pick != null && (pick != "OTHER" || name.isNotBlank())) {
                if (pick != null) onPick(pick, name.trim().ifEmpty { null }, items.trim().ifEmpty { null })
            }
        }
    }
}
