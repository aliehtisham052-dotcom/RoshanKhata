package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.RateList
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word

/**
 * The price list on the computer (KMP Phase 7k): every product with a cash
 * or credit price, narrowed by company, on the phone's shared RateList rules
 * (unpriced products left off and counted, a column with no price at all
 * dropped). Printed, or saved as PDF, through Windows' print dialog.
 */
@Composable
fun RateListPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?) {
    val c = LocalRoshanColors.current
    val products by remember { dao.observeProducts() }.collectAsState(emptyList())
    var company by remember { mutableStateOf<String?>(null) }
    val companies = RateList.companies(products)
    val rows = RateList.rows(products, company, emptySet())
    val cols = RateList.columns(rows, wantCash = true, wantCredit = true)
    val shown = RateList.visible(rows, cols)
    val unpriced = RateList.unpricedCount(products)

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PaneTitle(word(W.rate_list_title), word(W.rate_list_heading_default)) }
            FillButton(word(W.ledger_pdf_button), c.gaveFill, enabled = shown.isNotEmpty()) {
                val headers = listOfNotNull(
                    words.text(W.name),
                    if (cols.cash) words.text(W.sale_price_hint) else null,
                    if (cols.credit) words.text(W.credit_price_hint) else null
                )
                val table = shown.map { r ->
                    listOfNotNull(
                        r.name + (r.unit?.let { " ($it)" } ?: ""),
                        if (cols.cash) r.cash?.let { Format.money(it) } ?: "–" else null,
                        if (cols.credit) r.credit?.let { Format.money(it) } ?: "–" else null
                    )
                }
                StatementPrint.show(
                    TablePrint(rtl, shopName ?: words.text(W.app_name), words.text(W.rate_list_heading_default) +
                        (company?.let { "  ·  $it" } ?: ""), headers, table) { p, of -> words.text(W.pdf_stmt_page, p, of) },
                    words.text(W.rate_list_title)
                )
            }
        }
        if (companies.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf<String?>(null) + companies) { co ->
                    val on = co == company
                    Text(
                        co ?: word(W.filter_type_all),
                        Modifier.background(if (on) c.brandGreen else c.surface, RoundedCornerShape(16.dp))
                            .clickable { company = co }.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = if (on) Color.White else c.ink, fontSize = 13.sp
                    )
                }
            }
        }
        if (unpriced > 0) Text(word(W.rate_list_unpriced, unpriced), color = c.textMuted, fontSize = 12.sp)
        if (shown.isEmpty()) {
            Text(word(W.rate_list_none), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(14.dp))) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(word(W.name), Modifier.weight(1f), color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (cols.cash) Text(word(W.sale_price_hint), Modifier.width(130.dp), color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        if (cols.credit) Text(word(W.credit_price_hint), Modifier.width(130.dp), color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = c.pageBg)
                }
                items(shown, key = { it.productId }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            val sub = listOfNotNull(r.company, r.unit)
                            if (sub.isNotEmpty()) Text(sub.joinToString("  ·  "), color = c.textMuted, fontSize = 12.sp)
                        }
                        if (cols.cash) Text(r.cash?.let { Format.money(it) } ?: "–", Modifier.width(130.dp), color = c.ink, fontSize = 15.sp, textAlign = TextAlign.End)
                        if (cols.credit) Text(r.credit?.let { Format.money(it) } ?: "–", Modifier.width(130.dp), color = c.owedToMe, fontSize = 15.sp, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = c.pageBg)
                }
            }
        }
    }
}
