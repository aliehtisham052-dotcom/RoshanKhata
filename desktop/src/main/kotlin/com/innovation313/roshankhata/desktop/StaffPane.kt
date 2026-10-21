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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Staff & salary on the computer (KMP Phase 7l), the phone's StaffActivity:
 * a month at a time, each person's absent / half / leave days, what they
 * earned (shared Payroll.month), advances and salary paid, and what is still
 * to pay. Mark a day, hand over an advance or salary, add, edit, remove
 * (kept in the backup, as on the phone).
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

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PaneTitle(word(W.staff_title), word(W.staff_subtitle)) }
            FluentButton(word(if (hasOwnerPin == true) W.access_owner_pin_change else W.access_owner_pin_set), { ownerPinOpen = true }, kind = ButtonKind.STANDARD)
            FluentButton(
                word(if (com.innovation313.roshankhata.data.Access.counterMode) W.access_counter_on_label else W.access_counter_off_label),
                { counterOpen = true }, kind = ButtonKind.STANDARD
            )
            FillButton(word(W.staff_add), c.brandGreen) { adding = true }
        }
        Text(word(W.access_counter_mode_help), color = c.textMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ shift(-1) }) { Text("‹ " + word(W.staff_prev_month), color = c.brandGreenText) }
            Text(DateWords.format("MMMM yyyy", from), Modifier.weight(1f), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton({ shift(1) }) { Text(word(W.staff_next_month) + " ›", color = c.brandGreenText) }
        }
        if (staff.isEmpty()) {
            Text(word(W.staff_none), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(staff, key = { it.id }) { person ->
                    val mine = marks.filter { it.staffId == person.id }
                    val m = Payroll.month(person.monthlySalary, days, mine.map { it.status }, pays.filter { it.staffId == person.id })
                    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(person.name, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(word(W.staff_salary_line, Format.money(person.monthlySalary)), color = c.inkSoft, fontSize = 13.sp)
                        Text(word(W.staff_counts, m.absent.toString(), m.half.toString(), m.leave.toString()), color = c.inkSoft, fontSize = 13.sp)
                        Text(word(W.staff_money, Format.money(m.earned), Format.money(m.advances), Format.money(m.paid)), color = c.inkSoft, fontSize = 13.sp)
                        Text(
                            when {
                                m.due > 0.004 -> word(W.staff_due, Format.money(m.due))
                                m.due < -0.004 -> word(W.staff_overpaid, Format.money(-m.due))
                                else -> word(W.staff_settled)
                            },
                            color = when { m.due > 0.004 -> c.owedToMe; m.due < -0.004 -> c.iOwe; else -> c.ink },
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FluentButton(word(W.staff_attendance), { marking = person }, kind = ButtonKind.SUBTLE)
                            FluentButton(word(W.staff_advance), { handing = person to Payroll.ADVANCE }, kind = ButtonKind.SUBTLE)
                            FluentButton(word(W.staff_pay), { handing = person to Payroll.SALARY }, kind = ButtonKind.SUBTLE)
                            TextButton({ editing = person }) { Text(word(W.staff_edit), color = c.ink) }
                            FluentButton(word(W.staff_remove), { removing = person }, kind = ButtonKind.DANGER)
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
        val mayEditLogin = com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.STAFF)
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(word(if (person == null) W.staff_add else W.staff_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Outlined(name, { name = it }, label = { Text(word(W.staff_name)) }, singleLine = true)
                    Outlined(phone, { phone = it }, label = { Text(word(W.staff_phone)) }, singleLine = true)
                    Outlined(salary, { salary = it }, label = { Text(word(W.staff_salary)) }, singleLine = true)
                    if (bad) Text(word(W.staff_invalid), color = c.owedToMe, fontSize = 13.sp)
                    if (mayEditLogin) {
                        Text(word(W.staff_role), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FluentPill(word(W.staff_role_none), role == null) { role = null }
                            FluentPill(word(W.staff_role_manager), role == com.innovation313.roshankhata.data.Role.MANAGER) { role = com.innovation313.roshankhata.data.Role.MANAGER }
                            FluentPill(word(W.staff_role_cashier), role == com.innovation313.roshankhata.data.Role.CASHIER) { role = com.innovation313.roshankhata.data.Role.CASHIER }
                        }
                        if (role != null) {
                            Outlined(pin, { pin = it.filter { ch -> ch.isDigit() }.take(8); badPin = false }, label = { Text(word(W.staff_pin)) }, singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            if (person?.pinHash != null) Text(word(W.staff_pin_keep), color = c.textMuted, fontSize = 12.sp)
                            if (badPin) Text(word(W.staff_pin_invalid), color = c.owedToMe, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val n = name.trim()
                    val s = Calc.evalAmount(salary)
                    if (n.isEmpty() || s == null) { bad = true; return@TextButton }
                    val p = phone.trim().ifEmpty { null }
                    // A role needs a PIN the first time; a blank PIN later keeps the old one.
                    // A staff login without an owner PIN would let anyone sign in as the owner: set it first.
                    if (mayEditLogin && role != null && hasOwnerPin != true) { ownerPinOpen = true; return@TextButton }
                    val needsPin = mayEditLogin && role != null && (person?.pinHash == null || pin.isNotEmpty())
                    if (needsPin && !com.innovation313.roshankhata.data.PinHash.valid(pin)) { badPin = true; return@TextButton }
                    val login: (Staff) -> Staff = { st ->
                        if (!mayEditLogin) st
                        else if (role == null) st.copy(role = null, pinHash = null, pinSalt = null)
                        else if (pin.isNotEmpty()) { val salt = com.innovation313.roshankhata.data.PinHash.salt(); st.copy(role = role, pinSalt = salt, pinHash = com.innovation313.roshankhata.data.PinHash.hash(pin, salt)) }
                        else st.copy(role = role)
                    }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (person == null) dao.insertStaff(login(Staff(name = n, phone = p, monthlySalary = s)))
                            else dao.updateStaff(login(person.copy(name = n, phone = p, monthlySalary = s)))
                        }
                    }
                    adding = false; editing = null
                }) { Text(word(W.save)) }
            },
            dismissButton = { TextButton({ adding = false; editing = null }) { Text(word(W.cancel)) } }
        )
    }

    handing?.let { (person, kind) ->
        var amount by remember(person, kind) { mutableStateOf("") }
        var note by remember(person, kind) { mutableStateOf("") }
        var bad by remember(person, kind) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { handing = null },
            title = { Text(person.name + " · " + word(if (kind == Payroll.ADVANCE) W.staff_advance else W.staff_pay)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Outlined(amount, { amount = it }, label = { Text(word(W.staff_amount)) }, singleLine = true)
                    Outlined(note, { note = it }, label = { Text(word(W.staff_note)) }, singleLine = true)
                    if (bad) Text(word(W.staff_invalid), color = c.owedToMe, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton({
                    val v = Calc.evalAmount(amount)
                    if (v == null) { bad = true; return@TextButton }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.insertStaffPayment(StaffPayment(staffId = person.id, amount = v, kind = kind, note = note.trim().ifEmpty { null }))
                        }
                    }
                    handing = null
                }) { Text(word(W.save)) }
            },
            dismissButton = { TextButton({ handing = null }) { Text(word(W.cancel)) } }
        )
    }

    marking?.let { person ->
        // Today, or the 1st when another month is open — then the day can be changed.
        val today = Payroll.dayOf(System.currentTimeMillis())
        var day by remember(person, from) { mutableStateOf(if (today in from until to) today else from) }
        var picking by remember(person) { mutableStateOf(false) }
        val current = marks.firstOrNull { it.staffId == person.id && it.day == day }?.status ?: Payroll.PRESENT
        AlertDialog(
            onDismissRequest = { marking = null },
            title = { Text(word(W.staff_day_for, person.name + " · " + DateWords.format("d MMM", day))) },
            text = {
                Column {
                    listOf(
                        Payroll.PRESENT to W.staff_present, Payroll.ABSENT to W.staff_absent,
                        Payroll.HALF to W.staff_half, Payroll.LEAVE to W.staff_leave
                    ).forEach { (status, label) ->
                        val on = status == current
                        // A day with no row is a day present: the owner marks only the exceptions.
                        Text(
                            word(label),
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .background(if (on) c.brandGreen else c.pageBg, RoundedCornerShape(10.dp))
                                .press {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (status == Payroll.PRESENT) dao.clearAttendance(person.id, day)
                                            else dao.markAttendance(StaffAttendance(staffId = person.id, day = day, status = status))
                                        }
                                    }
                                    marking = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            color = if (on) Color.White else c.ink, fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = { TextButton({ picking = true }) { Text(word(W.staff_change_date)) } },
            dismissButton = { TextButton({ marking = null }) { Text(word(W.cancel)) } }
        )
        if (picking) {
            DayPicker(day, onPick = { picked -> day = picked; picking = false }, onDismiss = { picking = false })
        }
    }

    removing?.let { person ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(word(W.staff_remove_confirm, person.name)) },
            confirmButton = {
                TextButton({
                    scope.launch { withContext(Dispatchers.IO) { dao.updateStaff(person.copy(isActive = false)) } }
                    removing = null
                }) { Text(word(W.staff_remove), color = c.owedToMe) }
            },
            dismissButton = { TextButton({ removing = null }) { Text(word(W.cancel)) } }
        )
    }
}
