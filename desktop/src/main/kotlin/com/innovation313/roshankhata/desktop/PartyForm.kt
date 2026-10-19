package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.DuplicateDetector
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * A customer or supplier, new or edited (KMP Phase 8b), the phone's add and
 * edit dialogs: name, phone, father's name, village, customer or supplier,
 * and (when editing) the credit limit. While typing, a similar party
 * already in the book is named (DuplicateDetector.matchExisting) — it
 * informs and never blocks, as on the phone.
 */
@Composable
fun PartyForm(existing: Party?, book: List<PartyWithBalance>, onSave: (Party) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var father by remember { mutableStateOf(existing?.fatherName.orEmpty()) }
    var village by remember { mutableStateOf(existing?.village.orEmpty()) }
    var customer by remember { mutableStateOf(existing?.isCustomer ?: true) }
    var limit by remember { mutableStateOf(existing?.creditLimit?.let { Calc.trim(it) }.orEmpty()) }
    val others = remember(book, existing) {
        book.filter { it.id != existing?.id }.map { DuplicateDetector.Candidate(it.id, it.name, it.phone, it.isCustomer, it.balance) }
    }
    val similar = DuplicateDetector.matchExisting(name.trim(), phone.trim().ifEmpty { null }, others)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(if (existing == null) W.add_party else W.edit_party), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        TwoWay(word(W.customer), word(W.supplier), customer) { customer = it }
        OutlinedTextField(name, { name = it }, label = { Text(word(W.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text(word(W.phone_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        similar?.let { m ->
            Text(
                m.phone?.let { word(W.add_party_duplicate_with_phone, m.name, Format.ltr(it)) } ?: word(W.add_party_duplicate_name_only, m.name),
                color = c.owedToMe, fontSize = 13.sp
            )
        }
        OutlinedTextField(father, { father = it }, label = { Text(word(W.father_name_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(village, { village = it }, label = { Text(word(W.village_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (existing != null) {
            Text(word(W.credit_limit), color = c.ink, fontWeight = FontWeight.Bold)
            OutlinedTextField(limit, { limit = it }, label = { Text(word(W.credit_limit_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(word(W.credit_limit_help), color = c.textMuted, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TextButton(onCancel) { Text(word(W.cancel)) }
            FillButton(word(W.save), c.brandGreen, enabled = name.isNotBlank()) {
                fun typed(v: String) = v.trim().ifEmpty { null }
                val base = existing ?: Party(name = name.trim(), isCustomer = customer)
                onSave(
                    base.copy(
                        name = name.trim(), phone = typed(phone), fatherName = typed(father), village = typed(village),
                        isCustomer = customer,
                        // A cleared limit is "no limit", never zero.
                        creditLimit = if (existing == null) null else Calc.evalAmount(limit)
                    )
                )
            }
        }
    }
}
