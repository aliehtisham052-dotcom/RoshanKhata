package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The phone's first-run walkthrough (MainActivity's coach marks), as one
 * window of steps on the computer: each names a page in the phone's own
 * words and wears its tile. Shown once (app.properties), Skip any time;
 * More → Help keeps everything it says.
 */
private class Step(val section: Section, val title: String, val desc: String)

@Composable
fun TourWindowContent(onDone: () -> Unit) {
    val c = LocalRoshanColors.current
    val trade = LocalTrade.current
    val steps = listOf(
        Step(Section.KHATA, W.coach_title_nav_khata, W.coach_desc_nav_khata),
        Step(Section.CASHBOOK, W.coach_title_nav_cashbook, W.coach_desc_nav_cashbook),
        Step(Section.CHEQUES, W.coach_title_nav_cheques, W.coach_desc_nav_cheques),
        Step(Section.PLANS, W.coach_title_nav_plans, W.coach_desc_nav_plans),
        Step(Section.BILLS, W.coach_title_bills, W.coach_desc_bills),
        Step(Section.PRODUCTS, W.coach_title_products, W.coach_desc_products),
        Step(Section.INVOICES, W.coach_title_invoice, W.coach_desc_invoice),
        Step(Section.FOLLOWUP, W.coach_title_followup, W.coach_desc_followup),
        Step(Section.INSIGHTS, W.coach_title_insights, W.coach_desc_insights),
        Step(Section.CALC, W.coach_title_calc, W.coach_desc_calc),
        Step(Section.CARD, W.coach_title_bizcard, W.coach_desc_bizcard),
        Step(Section.DAILY, W.coach_title_backup, W.coach_desc_backup)
    ).filter { trade.shows(it.section) }
    var at by remember { mutableIntStateOf(0) }
    val s = steps[at]
    Column(Modifier.fillMaxSize().background(c.surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileBadge(s.section, 64, 38)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("${at + 1} / ${steps.size}", color = c.textMuted, fontSize = 12.sp)
                Text(word(s.title), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(word(s.desc), color = c.inkSoft, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            steps.indices.forEach { i ->
                Box(Modifier.size(width = if (i == at) 20.dp else 8.dp, height = 8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (i == at) c.brandGreen else Look.line))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (at < steps.lastIndex) FluentButton(word(W.coach_skip), onDone, kind = ButtonKind.STANDARD)
            FillButton(word(if (at < steps.lastIndex) W.coach_next else W.coach_done), c.brandGreen) {
                if (at < steps.lastIndex) at++ else onDone()
            }
        }
    }
}
