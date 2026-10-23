package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Scheme
import com.innovation313.roshankhata.data.SchemeMath
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Company schemes on the computer (KMP Phase 7q), the phone's
 * SchemesActivity: each scheme's purchases counted from the supplier bills
 * in its window (KhataDao.schemePurchased), the slab reached and the next,
 * what was earned (shared SchemeMath), and its status — running, claim due,
 * claimed, missed. Add, edit, mark the claim received, delete.
 */
@Composable
fun SchemesPane(dao: KhataDao, reload: Int) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val schemes by remember { dao.observeSchemes() }.collectAsState(emptyList())
    var rows by remember { mutableStateOf<List<SchemeRow>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<Party>>(emptyList()) }
    var form by remember { mutableStateOf<Scheme?>(null) }
    var adding by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<Scheme?>(null) }
    var claiming by remember { mutableStateOf<Scheme?>(null) }
    var deleting by remember { mutableStateOf<Scheme?>(null) }

    LaunchedEffect(schemes, reload) {
        withContext(Dispatchers.IO) {
            val sup = dao.suppliersOnce()
            val names = sup.associate { it.id to it.name }
            suppliers = sup
            rows = schemes.map { s ->
                val byValue = s.measure == SchemeMath.BY_VALUE
                val bought = dao.schemePurchased(byValue, s.startDate, s.endDate, s.partyId, s.company, s.unit)
                val unpriced = if (byValue) dao.schemeUnpricedLines(s.startDate, s.endDate, s.partyId, s.company) else 0
                SchemeRow(s, SchemeMath.progress(s, bought), unpriced, s.partyId?.let { names[it] })
            }
        }
    }

    val now = System.currentTimeMillis()
    val due = rows.count { SchemeMath.status(it.scheme, it.progress, now) == SchemeMath.Status.CLAIM_DUE }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                PaneTitle(word(W.schemes_title), if (due > 0) plural(WP.schemes_claims_due, due, due) else word(W.schemes_subtitle))
            }
            FillButton(word(W.schemes_add), c.brandGreen) { adding = true }
        }
        if (rows.isEmpty()) {
            Text(word(W.schemes_empty), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.scheme.id }) { r -> SchemeCard(r, now) { acting = r.scheme } }
            }
        }
    }

    acting?.let { s ->
        AlertDialog(
            onDismissRequest = { acting = null },
            title = { Text(s.name) },
            text = {
                Column {
                    listOf<Pair<String, () -> Unit>>(
                        word(W.scheme_edit) to { form = s },
                        (if (s.claimedAmount == null) word(W.scheme_mark_claimed) else word(W.scheme_unmark_claimed)) to {
                            if (s.claimedAmount == null) claiming = s
                            else scope.launch { withContext(Dispatchers.IO) { dao.setSchemeClaim(s.id, null, null) } }
                        },
                        word(W.scheme_delete) to { deleting = s }
                    ).forEach { (label, go) ->
                        Text(label, Modifier.fillMaxWidth().press { acting = null; go() }.padding(vertical = 12.dp), color = c.ink, fontSize = 16.sp)
                    }
                }
            },
            confirmButton = { TextButton({ acting = null }) { Text(word(W.cancel)) } }
        )
    }
    claiming?.let { s ->
        var amount by remember(s) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { claiming = null },
            title = { Text(word(W.scheme_mark_claimed)) },
            text = { Outlined(amount, { amount = it }, label = { Text(word(W.scheme_claim_hint)) }, singleLine = true, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY) },
            confirmButton = {
                TextButton(enabled = Calc.evalAmount(amount) != null, onClick = {
                    val v = Calc.evalAmount(amount) ?: return@TextButton
                    scope.launch { withContext(Dispatchers.IO) { dao.setSchemeClaim(s.id, v, System.currentTimeMillis()) } }
                    claiming = null
                }) { Text(word(W.save)) }
            },
            dismissButton = { TextButton({ claiming = null }) { Text(word(W.cancel)) } }
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(word(W.scheme_delete_confirm, s.name)) },
            confirmButton = {
                TextButton({
                    scope.launch { withContext(Dispatchers.IO) { dao.deleteScheme(s.id) } }
                    deleting = null
                }) { Text(word(W.scheme_delete), color = c.owedToMe) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(word(W.cancel)) } }
        )
    }
    if (adding || form != null) {
        val existing = form
        AppDialog(
            onCloseRequest = { adding = false; form = null },
            onPreviewKeyEvent = escCloses { adding = false; form = null },
            title = word(if (existing == null) W.schemes_add else W.scheme_edit),
            state = rememberDialogState(width = fitW(560.dp), height = fitH(820.dp))
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                SchemeForm(existing, suppliers, onSave = { s ->
                    adding = false; form = null
                    scope.launch { withContext(Dispatchers.IO) { dao.saveScheme(s) } }
                }, onCancel = { adding = false; form = null })
            }
        }
    }
}

private class SchemeRow(val scheme: Scheme, val progress: SchemeMath.Progress, val unpriced: Int, val supplier: String?)

private fun amountText(s: Scheme, v: Double) = if (s.measure == SchemeMath.BY_VALUE) Format.money(v) else Format.qty(v, s.unit)

@Composable
private fun rewardText(s: Scheme, slab: SchemeMath.Slab): String = when (s.rewardKind) {
    SchemeMath.PERCENT -> word(W.scheme_reward_pct_value, Format.plain(slab.reward))
    SchemeMath.AMOUNT -> Format.money(slab.reward)
    else -> word(W.scheme_reward_units_value, Format.plain(slab.reward))
}

@Composable
private fun SchemeCard(r: SchemeRow, now: Long, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    val s = r.scheme
    val p = r.progress
    Column(Modifier.fillMaxWidth().card().press(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.name, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(r.supplier, s.company, word(W.scheme_period, Format.dateOnly(s.startDate), Format.dateOnly(s.endDate)))
            .joinToString("  ·  "), color = c.textMuted, fontSize = 13.sp)
        Text(word(W.scheme_bought, amountText(s, p.purchased)), color = c.ink, fontSize = 14.sp)
        // A bill line with no rate adds nothing to a value scheme: say so.
        if (r.unpriced > 0) Text(plural(WP.scheme_unpriced, r.unpriced, r.unpriced), color = c.textMuted, fontSize = 12.sp)
        val next = p.next
        Text(
            if (next != null) word(W.scheme_next, amountText(s, next.target), rewardText(s, next), amountText(s, p.toNext ?: 0.0))
            else word(W.scheme_top_reached),
            color = c.inkSoft, fontSize = 13.sp
        )
        val achieved = p.achieved
        Text(
            when {
                achieved == null -> word(W.scheme_nothing_yet)
                s.rewardKind == SchemeMath.UNITS -> word(W.scheme_earned, rewardText(s, achieved))
                else -> word(W.scheme_earned, p.earned?.let { Format.money(it) } ?: rewardText(s, achieved))
            },
            color = c.iOwe, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        val status = SchemeMath.status(s, p, now)
        Text(
            when (status) {
                SchemeMath.Status.RUNNING -> word(W.scheme_status_running)
                SchemeMath.Status.CLAIM_DUE -> word(W.scheme_status_claim_due)
                SchemeMath.Status.CLAIMED -> word(W.scheme_status_claimed, Format.money(s.claimedAmount ?: 0.0))
                SchemeMath.Status.MISSED -> word(W.scheme_status_missed)
            },
            color = if (status == SchemeMath.Status.CLAIM_DUE) c.owedToMe else c.textMuted, fontSize = 13.sp,
            fontWeight = if (status == SchemeMath.Status.CLAIM_DUE) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SchemeForm(existing: Scheme?, suppliers: List<Party>, onSave: (Scheme) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    val day = 24L * 60 * 60 * 1000
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var partyId by remember { mutableStateOf(existing?.partyId) }
    var company by remember { mutableStateOf(existing?.company.orEmpty()) }
    var start by remember { mutableStateOf(existing?.startDate ?: com.innovation313.roshankhata.data.Payroll.dayOf(System.currentTimeMillis())) }
    var end by remember { mutableStateOf(existing?.endDate ?: (com.innovation313.roshankhata.data.Payroll.dayOf(System.currentTimeMillis() + 90 * day) + day - 1)) }
    var byQty by remember { mutableStateOf(existing?.measure == SchemeMath.BY_QTY) }
    var unit by remember { mutableStateOf(existing?.unit.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.rewardKind ?: SchemeMath.PERCENT) }
    val targets = remember { mutableStateOf(listOf(existing?.target1, existing?.target2, existing?.target3).map { it?.let(Calc::trim).orEmpty() }) }
    val rewards = remember { mutableStateOf(listOf(existing?.reward1, existing?.reward2, existing?.reward3).map { it?.let(Calc::trim).orEmpty() }) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needName = word(W.scheme_need_name)
    val needSlab = word(W.scheme_need_slab)
    val badDates = word(W.scheme_bad_dates)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Outlined(name, { name = it }, label = { Text(word(W.scheme_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(word(W.supplier), color = c.ink, fontWeight = FontWeight.Bold)
        // Any supplier, or one: chosen from the suppliers in the book.
        Column(Modifier.fillMaxWidth().background(Look.subtle, Look.ctl).padding(6.dp)) {
            (listOf<Party?>(null) + suppliers).forEach { sp ->
                val on = sp?.id == partyId
                Text(
                    sp?.let { Format.name(it.name) } ?: word(W.scheme_any_supplier),
                    Modifier.fillMaxWidth().background(if (on) c.brandGreen else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                        .press { partyId = sp?.id }.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = if (on) androidx.compose.ui.graphics.Color.White else c.ink, fontSize = 14.sp
                )
            }
        }
        Outlined(company, { company = it }, label = { Text(word(W.scheme_company_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ picking = true }) { Text(word(W.scheme_from, Format.dateOnly(start)), color = c.brandGreenText) }
            TextButton({ picking = false }) { Text(word(W.scheme_to, Format.dateOnly(end)), color = c.brandGreenText) }
        }
        Text(word(W.scheme_measure), color = c.ink, fontWeight = FontWeight.Bold)
        TwoWay(word(W.scheme_by_value), word(W.scheme_by_qty), !byQty) { value -> byQty = !value; if (!value && kind == SchemeMath.PERCENT) kind = SchemeMath.AMOUNT }
        if (byQty) Outlined(unit, { unit = it }, label = { Text(word(W.scheme_unit_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(word(W.scheme_reward), color = c.ink, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // A percentage needs a rupee base: only on a value scheme, as on the phone.
            listOfNotNull(
                if (!byQty) SchemeMath.PERCENT to W.scheme_reward_percent else null,
                SchemeMath.AMOUNT to W.scheme_reward_amount,
                SchemeMath.UNITS to W.scheme_reward_units
            ).forEach { (k, label) ->
                FluentPill(word(label), k == kind) { kind = k }
            }
        }
        Text(word(W.scheme_slabs), color = c.ink, fontWeight = FontWeight.Bold)
        (0..2).forEach { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Outlined(targets.value[i], { v -> targets.value = targets.value.toMutableList().also { it[i] = v } },
                    label = { Text(word(W.scheme_target_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                Outlined(rewards.value[i], { v -> rewards.value = rewards.value.toMutableList().also { it[i] = v } },
                    label = { Text(word(W.scheme_reward_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
            }
        }
        Outlined(note, { note = it }, label = { Text(word(W.scheme_note_hint)) }, modifier = Modifier.fillMaxWidth())
        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
            FillButton(word(W.save), c.brandGreen) {
                val slabs = (0..2).mapNotNull { i ->
                    val t = Calc.evalAmount(targets.value[i]); val r = Calc.evalAmount(rewards.value[i])
                    if (t != null && r != null) t to r else null
                }.sortedBy { it.first }
                when {
                    name.isBlank() -> problem = needName
                    slabs.isEmpty() -> problem = needSlab
                    end < start -> problem = badDates
                    else -> onSave(
                        Scheme(
                            id = existing?.id ?: 0,
                            name = name.trim(),
                            partyId = partyId,
                            company = company.trim().ifEmpty { null },
                            startDate = start,
                            endDate = end,
                            measure = if (byQty) SchemeMath.BY_QTY else SchemeMath.BY_VALUE,
                            unit = if (byQty) UnitWords.canonical(unit) else null,
                            rewardKind = kind,
                            target1 = slabs[0].first, reward1 = slabs[0].second,
                            target2 = slabs.getOrNull(1)?.first, reward2 = slabs.getOrNull(1)?.second,
                            target3 = slabs.getOrNull(2)?.first, reward3 = slabs.getOrNull(2)?.second,
                            claimedAmount = existing?.claimedAmount,
                            claimedAt = existing?.claimedAt,
                            note = note.trim().ifEmpty { null },
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
    picking?.let { isStart ->
        DayPicker(if (isStart) start else end, onPick = { d -> if (isStart) start = d else end = d + day - 1; picking = null }, onDismiss = { picking = null })
    }
}
