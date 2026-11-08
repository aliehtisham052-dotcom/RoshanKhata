package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Zakat
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The Zakat calculator on the computer (KMP Phase 7j; Marhala 4, part 7 — the page frame and the phone's order):
 * the same shared Zakat rules (per-customer receivables and payables, nisab from the owner's own silver or gold
 * rate, per tola or per gram), the working shown. The phone's order: the disclaimer above everything, never
 * buried at the foot (the audit); the nisab; cash and stock; what the ledger holds; then the result on the dark
 * card — beside the questions on a wide window, so it stays in sight while they are answered. Silver is the
 * default, as on the phone. A calculator, not a ruling: the page says so, and a scholar has the last word.
 */
@Composable
fun ZakatPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val rows by remember { dao.observeZakatBalancesByParty() }.collectAsState(emptyList())
    val inputs = remember(rows) { Zakat.fromParties(rows) }
    var gold by remember { mutableStateOf(false) }
    var perTola by remember { mutableStateOf(com.innovation313.roshankhata.data.ShopCountry.current.tola) }
    var priceText by remember { mutableStateOf("") }
    var stockText by remember { mutableStateOf("") }
    var doubtful by remember { mutableStateOf(false) }

    // A sum typed into a box is totalled, as in every other amount box (Calc.evalPad).
    val price = Calc.evalPad(priceText) ?: 0.0
    val stock = Calc.evalPad(stockText) ?: 0.0
    val nisab = Zakat.nisab(price, gold = gold, perTola = perTola)
    val wealth = Zakat.zakatableWealth(inputs = inputs, cashAndStock = stock, includeDoubtful = doubtful)
    val due = Zakat.zakatDue(wealth, nisab)

    FluentPage(word(W.zakat_calculator), word(W.zakat_subtitle)) {
        // The disclaimer sits above everything, as on the phone.
        Text(
            word(W.zakat_disclaimer), Modifier.fillMaxWidth().background(Look.warnTint, Look.card).padding(14.dp),
            color = Look.warn, fontSize = 13.sp
        )
        val questions: @Composable (Modifier) -> Unit = { m ->
            Column(m.card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZakatHeading(word(W.nisab_section))
                // The silver standard's own words, said only while silver is the standard chosen.
                if (!gold) Text(word(W.nisab_help), color = c.textMuted, fontSize = 12.sp)
                TwoWay(word(W.nisab_silver), word(W.nisab_gold), !gold) { silver -> gold = !silver }
                Text(word(if (gold) W.nisab_gold_note else W.nisab_silver_note), color = c.textMuted, fontSize = 12.sp)
                TwoWay(word(W.per_tola), word(W.per_gram), perTola) { perTola = it }
                Outlined(
                    value = priceText, onValueChange = { priceText = it }, singleLine = true,
                    label = { Text(word(zakatPriceLabel(gold, perTola))) },
                    modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                Text(word(W.price_help), color = c.textMuted, fontSize = 12.sp)
                if (Zakat.priceLooksOff(price, gold = gold, perTola = perTola)) {
                    Text(word(W.price_looks_off), color = c.owedToMe, fontSize = 13.sp)
                }
                // The working, not just the answer: "52.5 tola of silver x your rate".
                Text(
                    if (nisab > 0) word(
                        W.nisab_working,
                        Calc.trim(Zakat.nisabWeight(gold, perTola)) + " " + word(if (perTola) W.unit_tola else W.unit_gram),
                        word(if (gold) W.metal_gold else W.metal_silver),
                        Format.money(price),
                        Format.money(nisab)
                    ) else word(W.nisab_enter_price),
                    color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )

                ZakatHeading(word(W.cash_stock_section))
                Text(word(W.cash_stock_help), color = c.textMuted, fontSize = 12.sp)
                Outlined(
                    value = stockText, onValueChange = { stockText = it }, singleLine = true,
                    label = { Text(word(W.cash_stock_hint)) }, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)

                ZakatHeading(word(W.from_your_ledger))
                Text(word(W.certain_receivables, Format.money(inputs.certainReceivables)), color = c.ink, fontSize = 14.sp)
                Text(word(W.qarze_hasna_given, Format.money(inputs.qarzeHasnaGiven)), color = c.ink, fontSize = 14.sp)
                Text(word(W.doubtful_receivables, Format.money(inputs.doubtfulReceivables)), color = c.ink, fontSize = 14.sp)
                Text(word(W.you_owe_others, Format.money(inputs.payables)), color = c.ink, fontSize = 14.sp)
                Row(Modifier.clip(Look.ctl).press(Look.ctl) { doubtful = !doubtful }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = doubtful, onCheckedChange = { doubtful = it })
                    Text(word(W.include_doubtful), Modifier.padding(end = 8.dp), color = c.ink, fontSize = 14.sp)
                }
                Text(word(W.include_doubtful_help), color = c.textMuted, fontSize = 12.sp)
            }
        }
        // The phone's result card: the wealth, a gold rule, the Zakat due, and where it stands against the nisab.
        val result: @Composable (Modifier) -> Unit = { m ->
            Column(m.background(Look.hero, Look.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(word(W.zakatable_wealth), color = Look.heroLabel, fontSize = 12.sp)
                Text(Format.money(wealth), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Look.goldEdge))
                Text(word(W.zakat_due_label), color = Look.heroLabel, fontSize = 12.sp)
                Text(Format.money(due), color = Look.heroLabel, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(
                    when {
                        nisab <= 0.0 -> word(W.nisab_needed_first)
                        Zakat.meetsNisab(wealth, nisab) -> word(W.above_nisab)
                        else -> word(W.below_nisab)
                    },
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 860.dp) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                questions(Modifier.weight(1.5f))
                result(Modifier.weight(1f))
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                questions(Modifier.fillMaxWidth())
                result(Modifier.fillMaxWidth())
            }
        }
        Text(word(W.zakat_footer_note), color = c.textMuted, fontSize = 12.sp)
    }
}

/**
 * The price box's label: the metal and the unit the two choices name (25 Oct
 * 2026, the audit). It said "per gram" while "Per tola" was chosen — the
 * default in Pakistan — and a gram's price read as a tola's puts the nisab
 * 11.66 times too low, so Zakat looked due on wealth below it.
 */
internal fun zakatPriceLabel(gold: Boolean, perTola: Boolean): String = when {
    gold && perTola -> W.gold_price_hint_tola
    gold -> W.gold_price_hint
    perTola -> W.silver_price_hint_tola
    else -> W.silver_price_hint
}

@Composable
private fun ZakatHeading(text: String) {
    Text(text, Modifier.padding(top = 8.dp), color = LocalRoshanColors.current.brandGreenText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}
