package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * The Zakat calculator on the computer (KMP Phase 7j), the phone's
 * ZakatActivity line for line: the same shared Zakat rules (per-customer
 * receivables and payables, nisab from the owner's own silver or gold rate,
 * per tola or per gram), the working shown, the same disclaimer. Silver is
 * the default, as on the phone. A calculator, not a ruling: the screen says
 * so, and a scholar has the last word.
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
        Column(
            Modifier.widthIn(max = 760.dp).card().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaneTitle(word(W.zakat_calculator), word(W.zakat_subtitle))

            ZakatHeading(word(W.nisab_section))
            // The silver standard's own words, said only while silver is the standard chosen.
            if (!gold) Text(word(W.nisab_help), color = c.textMuted, fontSize = 12.sp)
            TwoWay(word(W.nisab_silver), word(W.nisab_gold), !gold) { silver -> gold = !silver }
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
                color = c.ink, fontSize = 14.sp
            )
            Text(word(if (gold) W.nisab_gold_note else W.nisab_silver_note), color = c.textMuted, fontSize = 12.sp)

            ZakatHeading(word(W.from_your_ledger))
            Text(word(W.certain_receivables, Format.money(inputs.certainReceivables)), color = c.ink, fontSize = 14.sp)
            Text(word(W.qarze_hasna_given, Format.money(inputs.qarzeHasnaGiven)), color = c.ink, fontSize = 14.sp)
            Text(word(W.doubtful_receivables, Format.money(inputs.doubtfulReceivables)), color = c.ink, fontSize = 14.sp)
            Text(word(W.you_owe_others, Format.money(inputs.payables)), color = c.ink, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = doubtful, onCheckedChange = { doubtful = it })
                Text(word(W.include_doubtful), color = c.ink, fontSize = 14.sp)
            }
            Text(word(W.include_doubtful_help), color = c.textMuted, fontSize = 12.sp)

            ZakatHeading(word(W.cash_stock_section))
            Outlined(
                value = stockText, onValueChange = { stockText = it }, singleLine = true,
                label = { Text(word(W.cash_stock_hint)) }, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            Text(word(W.cash_stock_help), color = c.textMuted, fontSize = 12.sp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TotalCard(word(W.zakatable_wealth), Format.money(wealth), c.ink, Modifier.weight(1f))
                TotalCard(word(W.zakat_due_label), Format.money(due), c.iOwe, Modifier.weight(1f))
            }
            Text(
                when {
                    nisab <= 0.0 -> word(W.nisab_needed_first)
                    Zakat.meetsNisab(wealth, nisab) -> word(W.above_nisab)
                    else -> word(W.below_nisab)
                },
                color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text(word(W.zakat_disclaimer), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.zakat_footer_note), color = c.textMuted, fontSize = 12.sp)
        }
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
