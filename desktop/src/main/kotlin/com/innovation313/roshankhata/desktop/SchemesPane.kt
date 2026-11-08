package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Scheme
import com.innovation313.roshankhata.data.SchemeMath
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
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

    var loaded by remember { mutableStateOf(false) }
    val words = LocalWords.current
    // A write that fails is said (Marhala 4: a claim, an unclaim, a delete or a save failed in silence).
    var told by remember { mutableStateOf<String?>(null) }
    fun write(job: suspend () -> Unit) {
        scope.launch { runCatching { withContext(Dispatchers.IO) { job() } }.onFailure { told = failText(words, it) } }
    }

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
        loaded = true
    }

    val now = System.currentTimeMillis()
    val due = rows.count { SchemeMath.status(it.scheme, it.progress, now) == SchemeMath.Status.CLAIM_DUE }
    // In the page frame (Marhala 4): Add in the head, the claims due as its line, the schemes as cards.
    FluentPage(word(W.schemes_title), if (due > 0) plural(WP.schemes_claims_due, due, due) else word(W.schemes_subtitle), scroll = false, actions = {
        FluentButton(word(W.schemes_add), { adding = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
    }) {
        if (rows.isEmpty()) {
            if (loaded) EmptyState("ic_tile_bills", word(W.schemes_empty), word(W.schemes_subtitle), Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.scheme.id }) { r -> SchemeCard(r, now) { acting = r.scheme } }
            }
        }
    }

    acting?.let { s ->
        // A change to what is written asks a cashier for a manager's PIN, as the phone does
        // (25 Oct 2026, the audit: anyone at the till could edit, claim or delete a scheme).
        val actions = listOf<Pair<String, () -> Unit>>(
            word(W.scheme_edit) to { Gates.edit { form = s } },
            (if (s.claimedAmount == null) word(W.scheme_mark_claimed) else word(W.scheme_unmark_claimed)) to {
                Gates.edit {
                    if (s.claimedAmount == null) claiming = s
                    else write { dao.setSchemeClaim(s.id, null, null) }
                }
            },
            word(W.scheme_delete) to { Gates.edit { deleting = s } }
        )
        PickDialog(s.name, actions.map { it.first }, onPick = { i -> acting = null; actions[i].second() }, onDismiss = { acting = null })
    }
    claiming?.let { s ->
        var amount by remember(s) { mutableStateOf("") }
        // A claim of nothing is a claim too (the phone allows 0): the scheme stops reading "Claim due". Read as the
        // phone's CalcPad.figure reads it: the pad's sum as a size, never a sign.
        val value = Calc.evalPad(amount)?.let { kotlin.math.abs(it) }?.takeIf { !it.isNaN() && !it.isInfinite() }
        val close = { claiming = null }
        val save = {
            if (value != null) {
                val at = System.currentTimeMillis()
                write { dao.setSchemeClaim(s.id, value, at) }
                claiming = null
            }
        }
        val title = word(W.scheme_mark_claimed)
        AppDialog(onCloseRequest = close, title = title, state = rememberDialogState(width = fitW(460.dp), height = fitH(300.dp))) {
            DialogFrame(title, subtitle = s.name, onClose = close, footer = {
                FluentButton(word(W.cancel), close, kind = ButtonKind.STANDARD)
                FluentButton(word(W.save), save, kind = ButtonKind.ACCENT, enabled = value != null)
            }) {
                FluentField(amount, { amount = it }, Modifier.fillMaxWidth(), label = word(W.scheme_claim_hint), big = true,
                    calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            }
        }
    }
    deleting?.let { s ->
        AskDialog(
            word(W.scheme_delete), word(W.scheme_delete_confirm, s.name), word(W.scheme_delete), danger = true,
            onDismiss = { deleting = null },
            onConfirm = { deleting = null; write { dao.deleteScheme(s.id) } }
        )
    }
    if (adding || form != null) {
        val existing = form
        val close = { adding = false; form = null }
        val title = word(if (existing == null) W.schemes_add else W.scheme_edit)
        AppDialog(onCloseRequest = close, title = title, state = rememberDialogState(width = fitW(600.dp), height = fitH(820.dp))) {
            SchemeForm(title, existing, suppliers, onSave = { s -> close(); write { dao.saveScheme(s) } }, onCancel = close)
        }
    }
    told?.let { TellDialog(word(W.schemes_title), it, { told = null }) }
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
private fun SchemeForm(title: String, existing: Scheme?, suppliers: List<Party>, onSave: (Scheme) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    val day = 24L * 60 * 60 * 1000
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var partyId by remember { mutableStateOf(existing?.partyId) }
    var company by remember { mutableStateOf(existing?.company.orEmpty()) }
    var start by remember { mutableStateOf(existing?.startDate ?: com.innovation313.roshankhata.data.Payroll.dayOf(System.currentTimeMillis())) }
    // The last day's whole day, by the calendar (dayAfter: never 24 h).
    var end by remember { mutableStateOf(existing?.endDate ?: (dayAfter(com.innovation313.roshankhata.data.Payroll.dayOf(System.currentTimeMillis() + 90 * day)) - 1)) }
    var byQty by remember { mutableStateOf(existing?.measure == SchemeMath.BY_QTY) }
    var unit by remember { mutableStateOf(UnitWords.label(existing?.unit)) }
    var kind by remember { mutableStateOf(existing?.rewardKind ?: SchemeMath.PERCENT) }
    val targets = remember { mutableStateOf(listOf(existing?.target1, existing?.target2, existing?.target3).map { it?.let(Calc::trim).orEmpty() }) }
    val rewards = remember { mutableStateOf(listOf(existing?.reward1, existing?.reward2, existing?.reward3).map { it?.let(Calc::trim).orEmpty() }) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    var pickingSupplier by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needName = word(W.scheme_need_name)
    val needSlab = word(W.scheme_need_slab)
    val badDates = word(W.scheme_bad_dates)

    fun save() {
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

    // In the app's frame (Marhala 4): boxes with their labels above, the supplier and the days as choice boxes,
    // what is wrong said beside Save, where it is seen without scrolling.
    DialogFrame(title, onClose = onCancel, footerStart = {
        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }, footer = {
        FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
        FluentButton(word(W.save), { save() }, kind = ButtonKind.ACCENT)
    }) {
        FluentField(name, { name = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.scheme_name_hint))
        // Any supplier, or one of the book's: chosen from a list, not laid out whole in the form.
        ChoiceBox(word(W.supplier), suppliers.firstOrNull { it.id == partyId }?.let { Format.name(it.name) }, word(W.scheme_any_supplier),
            { pickingSupplier = true }, Modifier.fillMaxWidth())
        FluentField(company, { company = it }, Modifier.fillMaxWidth(), label = word(W.scheme_company_hint))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateBox(word(W.report_start_date), Format.dateOnly(start), "", { picking = true }, Modifier.weight(1f))
            DateBox(word(W.report_end_date), Format.dateOnly(end), "", { picking = false }, Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SchemeHead(word(W.scheme_measure))
            TwoWay(word(W.scheme_by_value), word(W.scheme_by_qty), !byQty) { value -> byQty = !value; if (!value && kind == SchemeMath.PERCENT) kind = SchemeMath.AMOUNT }
        }
        if (byQty) FluentField(unit, { unit = it }, Modifier.fillMaxWidth(), label = word(W.scheme_unit_hint))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SchemeHead(word(W.scheme_reward))
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
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SchemeHead(word(W.scheme_slabs))
            (0..2).forEach { i ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluentField(targets.value[i], { v -> targets.value = targets.value.toMutableList().also { it[i] = v }; problem = null }, Modifier.weight(1f),
                        label = word(W.scheme_target_hint), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                    FluentField(rewards.value[i], { v -> rewards.value = rewards.value.toMutableList().also { it[i] = v }; problem = null }, Modifier.weight(1f),
                        label = word(W.scheme_reward_hint), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                }
            }
        }
        FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.scheme_note_hint), singleLine = false)
    }
    picking?.let { isStart ->
        DayPicker(if (isStart) start else end, onPick = { d -> if (isStart) start = d else end = dayAfter(d) - 1; picking = null }, onDismiss = { picking = null })
    }
    if (pickingSupplier) {
        val labels = listOf(word(W.scheme_any_supplier)) + suppliers.map { Format.name(it.name) }
        val chosen = if (partyId == null) 0 else suppliers.indexOfFirst { it.id == partyId } + 1
        PickDialog(word(W.supplier), labels, chosen, onPick = { i -> partyId = if (i == 0) null else suppliers[i - 1].id; pickingSupplier = false },
            onDismiss = { pickingSupplier = false })
    }
}

/** A small head over a group of choices in the scheme form, as a box's label reads. */
@Composable
private fun SchemeHead(text: String) {
    Text(text, color = LocalRoshanColors.current.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}
