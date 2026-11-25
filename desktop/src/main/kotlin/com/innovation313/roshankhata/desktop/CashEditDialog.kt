package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.CashEdit
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.PaymentRef
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * A written cashbook line corrected on the computer (the phone's Edit on a cashbook row, 17 Nov 2026, audit L023): the
 * amount, the category, the note, the day and the time, how it was paid and its reference. Never which way the money
 * went (a wrong way is a delete and a new line) and never its picture (the row's own camera button).
 *
 * The write is [KhataDao.editCashEntryAudited] — EDIT_BOOK checked there with [by] (who let a cashier through with a
 * manager's PIN), the line refused when a counter ticket or a return wrote it, CASH_EDITED written in the same
 * transaction. The dialog closes only once that call has returned: while it runs Save and the close are held, and a
 * failure says "not saved" and keeps what was typed. A refusal ([CashEdit]) closes the form through [onRefused], which
 * says where the line is changed instead.
 */
@Composable
fun CashEditDialog(
    dao: KhataDao,
    existing: CashEntry,
    /** Who let a cashier correct it (a manager's PIN), null when the signed-in user's own right was enough. */
    by: String?,
    onClose: () -> Unit,
    onRefused: (CashEdit) -> Unit
) {
    val c = LocalRoshanColors.current
    val words = com.innovation313.roshankhata.words.LocalWords.current
    val scope = rememberCoroutineScope()
    // The counter's own category words are kept as stored, never as this language's word for them (the phone's rule).
    val storedWord = cashCategoryWord(existing.category)
    var amountText by remember { mutableStateOf(Calc.trim(existing.amount)) }
    var category by remember { mutableStateOf(storedWord) }
    var note by remember { mutableStateOf(existing.note.orEmpty()) }
    var method by remember { mutableStateOf(existing.paymentMethod) }
    var paymentRef by remember { mutableStateOf(existing.paymentRef.orEmpty()) }
    var at by remember { mutableStateOf(existing.timestamp) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var used by remember { mutableStateOf<List<String>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = rememberFocusFirstEmpty(amountText.isNotBlank(), category.isNotBlank())
    LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { runCatching { dao.cashCategories() }.getOrDefault(emptyList()) } }
    val amount = Calc.evalAmount(amountText)
    val needAmount = word(W.enter_valid_amount)
    val needCategory = word(W.enter_category)
    val notSaved = word(W.save_failed_not_saved)

    // Esc and the ✕ ask first once anything differs from how the form opened; nothing is thrown away while the write runs.
    fun formNow(): List<Any?> = listOf(amountText, category, note, method, paymentRef, at)
    val opened = rememberOpened(formNow())
    val close = { if (!saving) onClose() }

    val title = word(W.edit) + " · " + word(if (existing.isIncome) W.cash_income else W.cash_expense)
    AppDialog(
        onCloseRequest = close,
        title = title,
        state = rememberDialogState(width = fitW(480.dp), height = fitH(620.dp))
    ) {
        Box(Modifier.fillMaxSize()) {
            // Said inside the dialog (its chrome is read from here), as the cheque form does.
            FormTyped { !saving && typedSince(opened, formNow()) }
            DialogFrame(
                title, word(W.cashbook),
                onClose = close,
                footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) } },
                footer = {
                    CancelButton(close, enabled = !saving)
                    FluentButton(word(W.save), kind = ButtonKind.ACCENT, icon = "ic_check_plain", enabled = !saving, onClick = {
                        when {
                            amount == null -> problem = needAmount
                            category.isBlank() -> problem = needCategory
                            else -> {
                                val corrected = correctedCash(existing, amount, category, storedWord, note, at, method, paymentRef)
                                saving = true
                                problem = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { dao.editCashEntryAudited(corrected, by) } }
                                    saving = false
                                    result
                                        .onSuccess { refused -> if (refused == null) onClose() else onRefused(refused) }
                                        .onFailure { problem = notSaved }
                                }
                            }
                        }
                    })
                }
            ) {
                FluentField(amountText, { amountText = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.amount), prefix = com.innovation313.roshankhata.data.Currency.symbol, big = true, help = amount?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY, focusRequester = focus[0])
                FluentField(category, { category = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.cash_category_hint), focusRequester = focus[1])
                // The owner's own earlier categories, one click each.
                if (used.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(used) { u -> FluentPill(u, u == category) { category = u; problem = null } }
                    }
                }
                // When the money moved: the day and the time of day, each its own box, as the entry form's.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateBox(word(W.date_label), Format.dateOnly(at), word(W.pick_date), { pickingDay = true }, Modifier.weight(1f), tall = true)
                    DateBox(
                        word(W.home_col_time),
                        remember(at) { com.innovation313.roshankhata.data.DateWords.formatter("h:mm a").format(java.util.Date(at)) },
                        word(W.pick_time), { pickingTime = true }, Modifier.weight(0.75f), tall = true
                    )
                }
                Text(word(W.payment_method_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethod.ALL.forEach { m ->
                        FluentPill(PaymentMethod.label(words, m), m == method) { method = if (m == method) null else m }
                    }
                }
                if (PaymentRef.applies(method)) {
                    FluentField(paymentRef, { paymentRef = it }, Modifier.fillMaxWidth(), label = word(W.pos_reference_hint), ltr = true)
                }
                FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.note_optional), singleLine = false)
            }
        }
    }
    if (pickingDay) {
        // The day changes; the time of day the line was written stays, and it is never put ahead of now.
        DayPicker(at, onPick = { day -> at = dayKeepingTime(day, at, System.currentTimeMillis()); pickingDay = false },
            onDismiss = { pickingDay = false }, latest = System.currentTimeMillis())
    }
    if (pickingTime) TimeOfDayDialog(at, onPick = { picked -> at = picked; pickingTime = false }, onDismiss = { pickingTime = false })
}

/**
 * The line as the form corrects it (the phone's own rule): amount, category, note, day and time, way of paying and its
 * reference change; the way the money went, the picture, the uid, the counter's link and the writer stay as stored. A
 * category typed as the language's word for the counter's own category keeps the stored key. The DAO still decides
 * whether it may be written ([KhataDao.editCashEntryAudited]).
 */
internal fun correctedCash(
    stored: CashEntry, amount: Double, category: String, storedCategoryWord: String,
    note: String, timestamp: Long, method: String?, reference: String
): CashEntry {
    val typed = category.trim()
    return stored.copy(
        amount = amount,
        category = if (typed == storedCategoryWord) stored.category else typed,
        note = note.trim().ifEmpty { null },
        timestamp = timestamp,
        paymentMethod = method,
        paymentRef = PaymentRef.clean(method, reference)
    )
}

/** [day] (a moment on the chosen day) with the clock time of [at], brought back to [now] when that is ahead of it. */
internal fun dayKeepingTime(day: Long, at: Long, now: Long): Long {
    val keep = Calendar.getInstance().apply { timeInMillis = at }
    return Calendar.getInstance().apply {
        timeInMillis = day
        set(Calendar.HOUR_OF_DAY, keep.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, keep.get(Calendar.MINUTE))
    }.timeInMillis.coerceAtMost(now)
}
