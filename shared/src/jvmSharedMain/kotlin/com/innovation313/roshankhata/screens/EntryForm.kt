package com.innovation313.roshankhata.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * A new entry: which way ([isGiven]), how much, an optional note. The amount
 * takes what the phone's pad takes — sums like 1200+350, Urdu or Persian
 * digits — through Calc.evalAmount, and the figure it will save is shown
 * under the box before Save, as on the phone. Save stays off until there
 * is an amount above zero.
 */
@Composable
fun EntryForm(
    partyName: String,
    isGiven: Boolean,
    onSave: (amount: Double, note: String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Editing: the entry's amount and note to start from. */
    initialAmount: Double? = null,
    initialNote: String? = null,
    /** Editing: moves the entry to the Recycle Bin (the caller confirms). */
    onDelete: (() -> Unit)? = null
) {
    val c = LocalRoshanColors.current
    var amountText by remember { mutableStateOf(initialAmount?.let { Calc.trim(it) } ?: "") }
    var note by remember { mutableStateOf(initialNote.orEmpty()) }
    val amount = Calc.evalAmount(amountText)
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            word(if (isGiven) W.you_gave else W.you_got),
            color = if (isGiven) c.owedToMe else c.iOwe, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Text(Format.name(partyName), color = c.ink, fontSize = 15.sp)
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(word(W.amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("entryAmount")
        )
        Text(
            amount?.let { Format.money(it) } ?: "",
            Modifier.testTag("entryPreview"),
            color = c.textMuted, fontSize = 14.sp
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(word(W.note_optional)) },
            modifier = Modifier.fillMaxWidth().testTag("entryNote")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.testTag("entryDelete")) {
                    Text(word(W.delete), color = c.owedToMe)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onCancel) { Text(word(W.cancel)) }
            Button(
                onClick = { amount?.let { onSave(it, note.trim().ifEmpty { null }) } },
                enabled = amount != null,
                modifier = Modifier.testTag("entrySave"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGiven) c.gaveFill else c.brandGreen,
                    contentColor = Color.White
                )
            ) { Text(word(W.save), fontWeight = FontWeight.Bold) }
        }
    }
}

/** A new customer or supplier: a name (required), a phone (optional). */
@Composable
fun AddPartyForm(
    onSave: (name: String, phone: String?, isCustomer: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isCustomer by remember { mutableStateOf(true) }
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(word(W.add_party), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(word(W.customer), isCustomer) { isCustomer = true }
            Choice(word(W.supplier), !isCustomer) { isCustomer = false }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text(word(W.name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("partyName")
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it }, label = { Text(word(W.phone_optional)) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().testTag("partyPhone")
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End)) {
            TextButton(onClick = onCancel) { Text(word(W.cancel)) }
            Button(
                onClick = { onSave(name.trim(), phone.trim().ifEmpty { null }, isCustomer) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen, contentColor = Color.White)
            ) { Text(word(W.save), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    if (selected) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen, contentColor = Color.White)) { Text(text) }
    } else {
        TextButton(onClick = onClick) { Text(text, color = c.brandGreenText) }
    }
}
