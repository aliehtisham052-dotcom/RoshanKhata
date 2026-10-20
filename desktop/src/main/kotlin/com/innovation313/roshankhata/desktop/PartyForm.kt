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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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
fun PartyForm(existing: Party?, book: List<PartyWithBalance>, onSave: (Party) -> Unit, onCancel: () -> Unit, home: java.io.File? = null) {
    val c = LocalRoshanColors.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.phone.orEmpty()) }
    var father by remember { mutableStateOf(existing?.fatherName.orEmpty()) }
    var village by remember { mutableStateOf(existing?.village.orEmpty()) }
    var customer by remember { mutableStateOf(existing?.isCustomer ?: true) }
    var limit by remember { mutableStateOf(existing?.creditLimit?.let { Calc.trim(it) }.orEmpty()) }
    var photo by remember { mutableStateOf(existing?.photoPath) }
    var photoVersion by remember { mutableStateOf(0) }
    var photoFailed by remember { mutableStateOf(false) }
    val photoTitle = word(W.set_photo)
    val others = remember(book, existing) {
        book.filter { it.id != existing?.id }.map { DuplicateDetector.Candidate(it.id, it.name, it.phone, it.isCustomer, it.balance) }
    }
    val similar = DuplicateDetector.matchExisting(name.trim(), phone.trim().ifEmpty { null }, others)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(if (existing == null) W.add_party else W.edit_party), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        TwoWay(word(W.customer), word(W.supplier), customer) { customer = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FluentField(name, { name = it }, Modifier.weight(1f), label = word(W.name))
            FluentField(phone, { phone = it }, Modifier.weight(1f), label = word(W.phone_optional), leadingIcon = "ic_phone")
        }
        similar?.let { m ->
            Text(
                m.phone?.let { word(W.add_party_duplicate_with_phone, m.name, Format.ltr(it)) } ?: word(W.add_party_duplicate_name_only, m.name),
                color = c.owedToMe, fontSize = 13.sp
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FluentField(father, { father = it }, Modifier.weight(1f), label = word(W.father_name_optional))
            FluentField(village, { village = it }, Modifier.weight(1f), label = word(W.village_optional))
        }
        if (existing != null && home != null) {
            // A face to know the customer by (the phone's party photo): kept on this computer only.
            val bmp = remember(photo, photoVersion) { Photos.bitmap(photo, 240) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.pageBg),
                    contentAlignment = Alignment.Center
                ) {
                    bmp?.let { androidx.compose.foundation.Image(it, contentDescription = word(W.party_photo), modifier = Modifier.size(72.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                }
                TextButton({
                    Photos.pick(photoTitle)?.let { src ->
                        val target = Photos.partyFile(home, existing.id)
                        if (Photos.save(src, target, max = 600)) { photo = target.absolutePath; photoVersion++; photoFailed = false } else photoFailed = true
                    }
                }) { Text(word(if (bmp == null) W.set_photo else W.change_photo), color = c.brandGreenText) }
                if (photo != null) TextButton({ photo = null }) { Text(word(W.remove_photo), color = c.owedToMe) }
            }
            if (photoFailed) Text(word(W.photo_save_failed), color = c.owedToMe, fontSize = 12.sp)
            Text(word(W.photo_privacy_note), color = c.textMuted, fontSize = 11.sp)
        }
        if (existing != null) {
            FluentField(limit, { limit = it }, Modifier.fillMaxWidth(), label = word(W.credit_limit), placeholder = word(W.credit_limit_hint), help = word(W.credit_limit_help))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            FluentButton(word(W.cancel), onCancel)
            FluentButton(word(W.save), kind = ButtonKind.ACCENT, icon = "ic_check_plain", enabled = name.isNotBlank(), onClick = {
                fun typed(v: String) = v.trim().ifEmpty { null }
                val base = existing ?: Party(name = name.trim(), isCustomer = customer)
                onSave(
                    base.copy(
                        name = name.trim(), phone = typed(phone), fatherName = typed(father), village = typed(village),
                        isCustomer = customer,
                        photoPath = if (existing == null) null else photo,
                        // A cleared limit is "no limit", never zero.
                        creditLimit = if (existing == null) null else Calc.evalAmount(limit)
                    )
                )
            })
        }
    }
}
