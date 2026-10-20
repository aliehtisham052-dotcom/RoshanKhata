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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.innovation313.roshankhata.data.ShopCalendar
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
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
fun ShopDetailsPane(settings: ShopSettings, images: ProfileImages, onSaved: () -> Unit) {
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
        Column(
            Modifier.widthIn(max = 720.dp).card().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaneTitle(word(W.business_profile), word(W.business_settings_subtitle))
            fields.forEach { (label, pair) ->
                val hint = pair.second
                Outlined(
                    value = values.value[label].orEmpty(),
                    onValueChange = { v -> values.value = values.value + (label to v); saved = false },
                    label = { Text(word(label)) },
                    placeholder = hint?.let { h -> { Text(word(h)) } },
                    singleLine = label != W.invoice_terms && label != W.business_address,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Outlined(
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
        Spacer(Modifier.padding(top = 14.dp))
        ProfilePictures(images)
    }
}

/**
 * The shop's four pictures for its invoices (Phase 10): logo, signature,
 * stamp and payment QR. The text backup carries no pictures (the owner's
 * own rule), so the computer keeps its own, one set per business.
 */
@Composable
private fun ProfilePictures(images: ProfileImages) {
    val c = LocalRoshanColors.current
    var version by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var unreadable by remember { mutableStateOf(false) }
    // A chosen picture waiting for the owner's crop (the phone's ImageCropActivity).
    var cropping by remember { mutableStateOf<Pair<ProfileImages.Kind, java.awt.image.BufferedImage>?>(null) }
    val chooseTitle = word(W.bp_add)
    cropping?.let { (kind, img) ->
        androidx.compose.ui.window.DialogWindow(
            onCloseRequest = { cropping = null },
            onPreviewKeyEvent = escCloses { cropping = null },
            title = word(W.adjust_crop_title),
            state = androidx.compose.ui.window.rememberDialogState(width = 760.dp, height = 720.dp)
        ) {
            // A logo is kept whole to start; a signature, stamp or QR starts at its ink.
            val start = remember(img) { if (kind == ProfileImages.Kind.LOGO) java.awt.Rectangle(0, 0, img.width, img.height) else InkBounds.of(img) }
            CropWindowContent(img, start, onUse = { cut ->
                cropping = null
                failed = runCatching {
                    val tmp = java.io.File.createTempFile("rk-crop", ".png")
                    try {
                        javax.imageio.ImageIO.write(cut, "png", tmp) && images.set(kind, tmp)
                    } finally { tmp.delete() }
                }.getOrDefault(false).not()
                version++
            }, onCancel = { cropping = null })
        }
    }
    Column(
        Modifier.widthIn(max = 720.dp).card().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(word(W.bp_seals_help), color = c.textMuted, fontSize = 12.sp)
        listOf(
            ProfileImages.Kind.LOGO to (W.bp_logo to W.bp_logo_help), ProfileImages.Kind.SIGNATURE to (W.signature to W.no_signature_yet),
            ProfileImages.Kind.STAMP to (W.stamp to W.no_stamp_yet), ProfileImages.Kind.QR to (W.bp_qr to W.bp_qr_help)
        ).forEach { (kind, texts) ->
            val picture = remember(version, kind) { images.load(kind)?.image?.toComposeImageBitmap() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(Look.subtle, Look.ctl), contentAlignment = Alignment.Center) {
                    picture?.let { Image(it, contentDescription = word(texts.first), modifier = Modifier.size(60.dp)) }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(word(texts.first), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(word(texts.second), color = c.textMuted, fontSize = 12.sp)
                }
                TextButton({
                    val dialog = java.awt.FileDialog(null as java.awt.Frame?, chooseTitle, java.awt.FileDialog.LOAD)
                    startIn(dialog, "Pictures")
                    dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".bmp") || it.endsWith(".gif") } }
                    dialog.file = "*.png;*.jpg;*.jpeg"
                    dialog.isVisible = true
                    val name = dialog.file
                    if (name != null) {
                        val img = runCatching { javax.imageio.ImageIO.read(java.io.File(dialog.directory, name)) }.getOrNull()
                        unreadable = img == null
                        failed = false
                        if (img != null) cropping = kind to img
                    }
                }) { Text(word(if (picture == null) W.bp_add else W.bp_change), color = c.brandGreenText) }
                if (picture != null) FluentButton(word(W.delete), { images.remove(kind); version++ }, kind = ButtonKind.DANGER)
            }
        }
        if (failed) Text(word(W.crop_save_failed), color = c.owedToMe, fontSize = 13.sp)
        if (unreadable) Text(word(W.crop_load_failed), color = c.owedToMe, fontSize = 13.sp)
    }
}
