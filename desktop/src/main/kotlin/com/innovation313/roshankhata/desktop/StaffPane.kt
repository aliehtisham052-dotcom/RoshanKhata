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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Payroll
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.StaffAttendance
import com.innovation313.roshankhata.data.StaffPayment
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
 * Staff & salary on the computer (KMP Phase 7l; Marhala 4, part 9 — the page frame), the phone's StaffActivity:
 * a month at a time, each person's absent / half / leave days, what they earned (shared Payroll.month), advances
 * and salary paid, and what is still to pay. Mark a day (within the open month: a day picked outside it was
 * written where this page never shows it), hand over an advance or salary, add, edit, remove (kept in the
 * backup, as on the phone). The month's arrows point the reader's way; the counter-mode help is said once, in its
 * own window; the dialogs are the app's.
 */
@Composable
fun StaffPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var monthAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val (from, to, days) = Payroll.monthRange(monthAt)
    val staff by remember { dao.observeStaff() }.collectAsState(emptyList())
    val marks by remember(from) { dao.observeAttendanceBetween(from, to) }.collectAsState(emptyList())
    val pays by remember(from) { dao.observeStaffPaymentsBetween(from, to) }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<Staff?>(null) }
    var adding by remember { mutableStateOf(false) }
    var marking by remember { mutableStateOf<Staff?>(null) }
    var handing by remember { mutableStateOf<Pair<Staff, Int>?>(null) }
    var removing by remember { mutableStateOf<Staff?>(null) }
    // The owner's own controls (21 Oct 2026): the book's owner PIN and this computer's counter mode.
    var ownerPinOpen by remember { mutableStateOf(false) }
    var counterOpen by remember { mutableStateOf(false) }
    var hasOwnerPin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(ownerPinOpen, counterOpen) {
        hasOwnerPin = withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.Access.ownerPin(dao) != null }
    }
    if (ownerPinOpen) OwnerPinDialog(dao, hasOwnerPin == true) { ownerPinOpen = false }
    if (counterOpen) CounterModeDialog(dao, !com.innovation313.roshankhata.data.Access.counterMode) { counterOpen = false }

    fun shift(by: Int) {
        monthAt = Calendar.getInstance().apply { timeInMillis = from; add(Calendar.MONTH, by) }.timeInMillis
    }

    FluentPage(word(W.staff_title), word(W.staff_subtitle), scroll = false, actions = {
        FluentButton(word(W.staff_add), { adding = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
    }) {
        // The counter's two locks in a row of their own (Marhala 4 — the proof: in Urdu the head held three buttons
        // and the third, Add staff, was cut off at the window's edge).
        Row(
            Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Glyph("ic_shield_check", Look.accent, 18)
            Text(word(W.access_who), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            FluentButton(word(if (hasOwnerPin == true) W.access_owner_pin_change else W.access_owner_pin_set), { ownerPinOpen = true }, kind = ButtonKind.STANDARD)
            FluentButton(
                word(if (com.innovation313.roshankhata.data.Access.counterMode) W.access_counter_on_label else W.access_counter_off_label),
                { counterOpen = true }, kind = ButtonKind.STANDARD
            )
        }
        Row(Modifier.fillMaxWidth().card().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MonthStep(word(W.staff_prev_month), back = true) { shift(-1) }
            Text(DateWords.format("MMMM yyyy", from), Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            MonthStep(word(W.staff_next_month), back = false) { shift(1) }
        }
        if (staff.isEmpty()) {
            EmptyState("ic_people", word(W.staff_none), null, Modifier.weight(1f)) {
                FluentButton(word(W.staff_add), { adding = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(staff, key = { it.id }) { person ->
                    val mine = marks.filter { it.staffId == person.id }
                    val m = Payroll.month(person.monthlySalary, days, mine.map { it.status }, pays.filter { it.staffId == person.id })
                    Row(Modifier.fillMaxWidth().card().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(person.name, 40, id = person.id)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(person.name, Modifier.weight(1f, fill = false), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (person.role != null) Tag(roleWord(person.role), Look.accent, Look.accentTint)
                            }
                            Text(word(W.staff_salary_line, Format.money(person.monthlySalary)), color = c.inkSoft, fontSize = 13.sp)
                            Text(word(W.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString()), color = c.inkSoft, fontSize = 13.sp)
                            Text(word(W.staff_money, Format.money(m.earned), Format.money(m.advances), Format.money(m.paid)), color = c.inkSoft, fontSize = 13.sp)
                            Text(
                                when {
                                    m.due > 0.004 -> word(W.staff_due, Format.money(m.due))
                                    m.due < -0.004 -> word(W.staff_overpaid, Format.money(-m.due))
                                    else -> word(W.staff_settled)
                                },
                                color = when { m.due > 0.004 -> c.owedToMe; m.due < -0.004 -> Look.accent; else -> c.ink },
                                fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                            PillRow(Modifier.fillMaxWidth()) {
                                FluentButton(word(W.staff_attendance), { marking = person }, kind = ButtonKind.STANDARD, icon = "ic_calendar")
                                FluentButton(word(W.staff_advance), { handing = person to Payroll.ADVANCE }, kind = ButtonKind.STANDARD)
                                FluentButton(word(W.staff_pay), { handing = person to Payroll.SALARY }, kind = ButtonKind.STANDARD)
                                FluentButton(word(W.staff_edit), { editing = person }, kind = ButtonKind.SUBTLE, icon = "ic_edit")
                                FluentButton(word(W.staff_remove), { removing = person }, kind = ButtonKind.DANGER)
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val person = editing
        var name by remember(person) { mutableStateOf(person?.name.orEmpty()) }
        var phone by remember(person) { mutableStateOf(person?.phone.orEmpty()) }
        var salary by remember(person) { mutableStateOf(person?.monthlySalary?.let { Calc.trim(it) }.orEmpty()) }
        var bad by remember(person) { mutableStateOf(false) }
        // Counter login (POS Phase 5): a role and a PIN; only the owner sets them.
        var role by remember(person) { mutableStateOf(person?.role) }
        var pin by remember(person) { mutableStateOf("") }
        var badPin by remember(person) { mutableStateOf(false) }
        // Two staff of one name would share a shift and a drawer (23 Oct 2026, the review).
        var nameTaken by remember(person) { mutableStateOf(false) }
        val mayEditLogin = com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.STAFF)
        val close = { adding = false; editing = null }
        val title = word(if (person == null) W.staff_add else W.staff_edit)
        fun save() {
            val n = name.trim()
            val s = Calc.evalAmount(salary)
            if (n.isEmpty() || s == null) { bad = true; return }
            val p = phone.trim().ifEmpty { null }
            // A role needs a PIN the first time; a blank PIN later keeps the old one.
            // A staff login without an owner PIN would let anyone sign in as the owner: set it first.
            if (mayEditLogin && role != null && hasOwnerPin != true) { ownerPinOpen = true; return }
            val needsPin = mayEditLogin && role != null && (person?.pinHash == null || pin.isNotEmpty())
            if (needsPin && !com.innovation313.roshankhata.data.PinHash.valid(pin)) { badPin = true; return }
            val login: (Staff) -> Staff = { st ->
                if (!mayEditLogin) st
                else if (role == null) st.copy(role = null, pinHash = null, pinSalt = null)
                else if (pin.isNotEmpty()) { val salt = com.innovation313.roshankhata.data.PinHash.salt(); st.copy(role = role, pinSalt = salt, pinHash = com.innovation313.roshankhata.data.PinHash.hash(pin, salt)) }
                else st.copy(role = role)
            }
            scope.launch {
                val taken = withContext(Dispatchers.IO) { dao.staffNameTaken(n, person?.id ?: 0L) }
                if (taken) { nameTaken = true; return@launch }
                withContext(Dispatchers.IO) {
                    if (person == null) dao.insertStaff(login(Staff(name = n, phone = p, monthlySalary = s)))
                    else dao.updateStaff(login(person.copy(name = n, phone = p, monthlySalary = s)))
                }
                close()
            }
        }
        AppDialog(onCloseRequest = close, onPreviewKeyEvent = escCloses(close), title = title,
            state = androidx.compose.ui.window.rememberDialogState(width = fitW(520.dp), height = fitH(if (mayEditLogin) 620.dp else 420.dp))) {
            DialogFrame(title, onClose = close, footer = {
                FluentButton(word(W.cancel), close, kind = ButtonKind.STANDARD)
                FluentButton(word(W.save), { save() }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FluentField(name, { name = it; nameTaken = false }, Modifier.fillMaxWidth(), label = word(W.staff_name))
                    if (nameTaken) Text(word(W.staff_name_taken), color = c.owedToMe, fontSize = 13.sp)
                    FluentField(phone, { phone = it }, Modifier.fillMaxWidth(), label = word(W.staff_phone), ltr = true)
                    Outlined(salary, { salary = it }, label = { Text(word(W.staff_salary)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                    if (bad) Text(word(W.staff_invalid), color = c.owedToMe, fontSize = 13.sp)
                    if (mayEditLogin) {
                        Text(word(W.staff_role), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        PillRow(Modifier.fillMaxWidth()) {
                            FluentPill(word(W.staff_role_none), role == null) { role = null }
                            FluentPill(word(W.staff_role_manager), role == com.innovation313.roshankhata.data.Role.MANAGER) { role = com.innovation313.roshankhata.data.Role.MANAGER }
                            FluentPill(word(W.staff_role_cashier), role == com.innovation313.roshankhata.data.Role.CASHIER) { role = com.innovation313.roshankhata.data.Role.CASHIER }
                        }
                        if (role != null) {
                            FluentField(pin, { pin = it.filter { ch -> ch.isDigit() }.take(8); badPin = false }, Modifier.fillMaxWidth(), label = word(W.staff_pin), password = true, ltr = true)
                            if (person?.pinHash != null) Text(word(W.staff_pin_keep), color = c.textMuted, fontSize = 12.sp)
                            if (badPin) Text(word(W.staff_pin_invalid), color = c.owedToMe, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    handing?.let { (person, kind) ->
        var amount by remember(person, kind) { mutableStateOf("") }
        var note by remember(person, kind) { mutableStateOf("") }
        var bad by remember(person, kind) { mutableStateOf(false) }
        val close = { handing = null }
        val title = word(if (kind == Payroll.ADVANCE) W.staff_advance else W.staff_pay)
        AppDialog(onCloseRequest = close, onPreviewKeyEvent = escCloses(close), title = title,
            state = androidx.compose.ui.window.rememberDialogState(width = fitW(460.dp), height = fitH(380.dp))) {
            DialogFrame(title, subtitle = person.name, onClose = close, footer = {
                FluentButton(word(W.cancel), close, kind = ButtonKind.STANDARD)
                FluentButton(word(W.save), {
                    val v = Calc.evalAmount(amount)
                    if (v == null) bad = true
                    else {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                dao.insertStaffPayment(StaffPayment(staffId = person.id, amount = v, kind = kind, note = note.trim().ifEmpty { null }))
                            }
                        }
                        close()
                    }
                }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Outlined(amount, { amount = it; bad = false }, label = { Text(word(W.staff_amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                    FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.staff_note))
                    if (bad) Text(word(W.staff_invalid), color = c.owedToMe, fontSize = 13.sp)
                }
            }
        }
    }

    marking?.let { person ->
        // Today, or the 1st when another month is open — then the day can be changed, within that month.
        val today = Payroll.dayOf(System.currentTimeMillis())
        var day by remember(person, from) { mutableStateOf(if (today in from until to) today else from) }
        var picking by remember(person) { mutableStateOf(false) }
        val current = marks.firstOrNull { it.staffId == person.id && it.day == day }?.status ?: Payroll.PRESENT
        val close = { marking = null }
        val title = word(W.staff_day_for, person.name + " \u00b7 " + DateWords.format("d MMM", day))
        AppDialog(onCloseRequest = close, onPreviewKeyEvent = escCloses(close), title = title,
            state = androidx.compose.ui.window.rememberDialogState(width = fitW(440.dp), height = fitH(420.dp))) {
            DialogFrame(title, onClose = close, footer = {
                FluentButton(word(W.staff_change_date), { picking = true }, kind = ButtonKind.STANDARD, icon = "ic_calendar")
                FluentButton(word(W.cancel), close, kind = ButtonKind.STANDARD)
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Payroll.PRESENT to W.staff_present, Payroll.ABSENT to W.staff_absent,
                        Payroll.HALF to W.staff_half, Payroll.LEAVE to W.staff_leave
                    ).forEach { (status, label) ->
                        val on = status == current
                        // A day with no row is a day present: the owner marks only the exceptions.
                        Text(
                            word(label),
                            Modifier.fillMaxWidth().clip(Look.ctl)
                                .background(if (on) Look.pickedFill else Look.subtle, Look.ctl)
                                .press(Look.ctl) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (status == Payroll.PRESENT) dao.clearAttendance(person.id, day)
                                            else dao.markAttendance(StaffAttendance(staffId = person.id, day = day, status = status))
                                        }
                                    }
                                    close()
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            color = if (on) Look.onPicked else c.ink, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        if (picking) {
            DayPicker(day, onPick = { picked -> day = picked; picking = false }, onDismiss = { picking = false }, latest = to - 1, earliest = from)
        }
    }

    removing?.let { person ->
        AskDialog(word(W.staff_remove), word(W.staff_remove_confirm, person.name), word(W.staff_remove),
            onConfirm = {
                scope.launch { withContext(Dispatchers.IO) { dao.updateStaff(person.copy(isActive = false)) } }
                removing = null
            }, onDismiss = { removing = null }, danger = true)
    }
}

/** The month's step back or on, its arrow pointing the reader's way (a chevron that turns in a right-to-left language). */
@Composable
private fun MonthStep(label: String, back: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(Look.ctl).press(Look.ctl, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (back) Box(Modifier.scale(-1f, 1f)) { Glyph("ic_chevron_right", Look.accent, 16) }
        Text(label, color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (!back) Glyph("ic_chevron_right", Look.accent, 16)
    }
}
