package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.innovation313.roshankhata.data.ShopCalendar
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The shop's own details on the computer (KMP Phase 7d): what is printed on
 * statements, invoices and reports. The same keys as the phone's Business
 * Profile, so a backup carries them across both ways. Saved together with
 * one button, as on the phone.
 */
@Composable
fun ShopDetailsPane(settings: ShopSettings, onSaved: () -> Unit) {
    val c = LocalRoshanColors.current
    // One "version" per save, so the fields re-read what was actually stored (trimmed, blanks to null).
    var version by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }

    val fields = remember(version) {
        linkedMapOf(
            W.business_name to (settings.businessName().orEmpty() to W.business_name_hint),
            W.business_name_urdu to (settings.businessNameUrdu().orEmpty() to W.business_name_urdu_hint),
            W.business_address to (settings.businessAddress().orEmpty() to W.business_address_hint),
            W.bp_phone to (settings.businessPhone().orEmpty() to W.bp_phone_hint),
            W.bp_email to (settings.businessEmail().orEmpty() to null),
            W.invoice_bank_name_hint to (settings.bankName().orEmpty() to null),
            W.invoice_bank_title_hint to (settings.bankAccountTitle().orEmpty() to null),
            W.invoice_bank_iban_hint to (settings.bankIban().orEmpty() to null),
            W.invoice_bank_jazzcash_hint to (settings.bankJazzCash().orEmpty() to null),
            W.bp_strn to (settings.strn().orEmpty() to W.invoice_strn_hint),
            W.invoice_terms to (settings.termsAndConditions().orEmpty() to W.invoice_terms_hint)
        )
    }
    val values = remember(version) { mutableStateOf(fields.mapValues { it.value.first }) }
    var currency by remember(version) { mutableStateOf(settings.currency()) }
    var hijri by remember(version) { mutableStateOf(settings.calendar() == ShopCalendar.Kind.HIJRI) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Column(
            Modifier.widthIn(max = 720.dp).background(c.surface, RoundedCornerShape(14.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaneTitle(word(W.business_profile), word(W.business_settings_subtitle))
            fields.forEach { (label, pair) ->
                val hint = pair.second
                OutlinedTextField(
                    value = values.value[label].orEmpty(),
                    onValueChange = { v -> values.value = values.value + (label to v); saved = false },
                    label = { Text(word(label)) },
                    placeholder = hint?.let { h -> { Text(word(h)) } },
                    singleLine = label != W.invoice_terms && label != W.business_address,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it; saved = false },
                label = { Text(word(W.bp_currency)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(word(W.bp_currency_help), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.bp_calendar), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            TwoWay(word(W.calendar_gregorian), word(W.calendar_hijri), !hijri) { gregorian -> hijri = !gregorian; saved = false }
            Text(word(W.bp_calendar_help), color = c.textMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (saved) Text(word(W.profile_saved), color = c.iOwe, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                FillButton(word(W.save), c.brandGreen) {
                    val v = values.value
                    settings.setBusinessName(v[W.business_name])
                    settings.setBusinessNameUrdu(v[W.business_name_urdu])
                    settings.setBusinessAddress(v[W.business_address])
                    settings.setBusinessPhone(v[W.bp_phone])
                    settings.setBusinessEmail(v[W.bp_email])
                    settings.setBankName(v[W.invoice_bank_name_hint])
                    settings.setBankAccountTitle(v[W.invoice_bank_title_hint])
                    settings.setBankIban(v[W.invoice_bank_iban_hint])
                    settings.setBankJazzCash(v[W.invoice_bank_jazzcash_hint])
                    settings.setStrn(v[W.bp_strn])
                    settings.setTermsAndConditions(v[W.invoice_terms])
                    settings.setCurrency(currency)
                    settings.setCalendar(if (hijri) ShopCalendar.Kind.HIJRI else ShopCalendar.Kind.GREGORIAN)
                    // Money signs and dates on screen and on paper follow at once.
                    settings.refreshDisplay()
                    version++
                    saved = true
                    onSaved()
                }
            }
        }
    }
}
