package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
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
import com.innovation313.roshankhata.data.ShopCountry
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * A Business Profile being typed and not yet saved, kept while the app is open (Marhala 4, part 8 — the audit:
 * leaving the page lost what was typed; the phone asks before it lets typed changes go). Coming back finds it as
 * left, said as unsaved, with Save and Discard. One per business: keyed by that business's settings.
 */
private data class ProfileDraft(val values: Map<String, String>, val currency: String, val country: ShopCountry, val calendar: ShopCalendar.Kind)

private val profileDrafts = java.util.WeakHashMap<ShopSettings, ProfileDraft>()

/**
 * The shop's own details on the computer (KMP Phase 7d; Marhala 4, part 8 — the phone's sections): what is
 * printed on statements, invoices and reports, with the same keys as the phone's Business Profile, so a backup
 * carries them across both ways. The phone's four parts, each with its own help: Identity (names, phone, email,
 * address, the logo, country, currency, calendar), Getting paid (bank, wallet, the payment QR), Invoice &
 * statement (tax number, terms) and Stamp & signature. Saved together with one button; what is typed and not
 * saved waits ([ProfileDraft]). A picture is deleted only after the owner is told it cannot come back.
 */
@Composable
fun ShopDetailsPane(settings: ShopSettings, images: ProfileImages, onSaved: () -> Unit) {
    val c = LocalRoshanColors.current
    // One "version" per save, so the fields re-read what was actually stored (trimmed, blanks to null).
    var version by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }

    val hints = mapOf(
        W.business_name to W.business_name_hint, W.business_name_urdu to W.business_name_urdu_hint,
        W.business_address to W.business_address_hint, W.bp_phone to W.bp_phone_hint,
        W.bp_strn to W.invoice_strn_hint, W.invoice_terms to W.invoice_terms_hint
    )
    val stored = remember(version) {
        ProfileDraft(
            linkedMapOf(
                W.business_name to settings.businessName().orEmpty(),
                W.business_name_urdu to settings.businessNameUrdu().orEmpty(),
                W.business_address to settings.businessAddress().orEmpty(),
                W.bp_phone to settings.businessPhone().orEmpty(),
                W.bp_email to settings.businessEmail().orEmpty(),
                W.invoice_bank_name_hint to settings.bankName().orEmpty(),
                W.invoice_bank_title_hint to settings.bankAccountTitle().orEmpty(),
                W.invoice_bank_iban_hint to settings.bankIban().orEmpty(),
                W.invoice_bank_jazzcash_hint to settings.bankJazzCash().orEmpty(),
                W.bp_strn to settings.strn().orEmpty(),
                W.invoice_terms to settings.termsAndConditions().orEmpty()
            ),
            settings.currency(), settings.country(),
            // All three of the phone's calendars (25 Oct 2026, the audit: a Gregorian/Hijri switch turned a
            // Solar Hijri shop Gregorian on its next Save).
            settings.calendar()
        )
    }
    var draft by remember(version) { mutableStateOf(profileDrafts[settings] ?: stored) }
    fun edit(next: ProfileDraft) {
        draft = next
        saved = false
        if (next == stored) profileDrafts.remove(settings) else profileDrafts[settings] = next
    }
    val unsaved = draft != stored

    fun save() {
        val v = draft.values
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
        settings.setCurrency(draft.currency)
        settings.setCountry(draft.country)
        settings.setCalendar(draft.calendar)
        // Money signs and dates on screen and on paper follow at once.
        settings.refreshDisplay()
        profileDrafts.remove(settings)
        version++
        saved = true
        onSaved()
    }
    @Composable
    fun field(label: String, modifier: Modifier, lines: Boolean = true, help: String? = null) {
        // The wallet field names the shop's country's services (21 Oct 2026).
        val shown = if (label == W.invoice_bank_jazzcash_hint && draft.country.wallets.isNotBlank()) word(W.wallet_hint_country, draft.country.wallets) else word(label)
        FluentField(
            draft.values[label].orEmpty(), { v -> edit(draft.copy(values = draft.values + (label to v))) }, modifier,
            label = shown, placeholder = hints[label]?.let { word(it) }, help = help?.let { word(it) }, singleLine = lines,
            // A phone or wallet number stays left to right in an RTL window (R15, 23 Oct 2026).
            ltr = label == W.bp_phone || label == W.invoice_bank_jazzcash_hint
        )
    }
    val pictures = rememberPictureState(images)
    FluentPage(word(W.business_profile), word(W.business_settings_subtitle), actions = {
        if (unsaved) {
            Text(word(W.bp_unsaved), color = Look.warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            FluentButton(word(W.bp_leave_discard), { profileDrafts.remove(settings); draft = stored }, kind = ButtonKind.SUBTLE)
        } else if (saved) Text(word(W.profile_saved), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        FluentButton(word(W.save), { save() }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
    }) {
        // Identity: who the shop is, as every sheet prints it.
        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardTitle(word(W.bp_section_identity))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(W.business_name, Modifier.weight(1f))
                field(W.business_name_urdu, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(W.bp_phone, Modifier.weight(1f), help = W.bp_phone_help)
                // The phone's own advice: a shop email is better than a personal one.
                field(W.bp_email, Modifier.weight(1f), help = W.bp_email_help)
            }
            field(W.business_address, Modifier.fillMaxWidth(), lines = false)
            PictureRow(pictures, ProfileImages.Kind.LOGO, W.bp_logo, W.bp_logo_help)
            // The shop's country (21 Oct 2026): one pill per country in a row that scrolls; choosing one
            // also offers its currency when the shop still prints the default sign.
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(word(W.bp_country), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val locale = java.util.Locale.getDefault()
                PillRow(Modifier.fillMaxWidth()) {
                    ShopCountry.choices(locale).forEach { ctry ->
                        val name = if (ctry == ShopCountry.OTHER) word(W.country_other) else ctry.displayName(locale)
                        FluentPill(name, ctry == draft.country) {
                            if (ctry != draft.country) {
                                var next = draft.copy(country = ctry)
                                if (next.currency == com.innovation313.roshankhata.data.Currency.DEFAULT && ctry != ShopCountry.OTHER) next = next.copy(currency = ctry.currencySign)
                                // The phone's rule: a shop still on the default calendar takes the country's own.
                                if (next.calendar == ShopCalendar.Kind.GREGORIAN && ctry.calendar != ShopCalendar.Kind.GREGORIAN) next = next.copy(calendar = ctry.calendar)
                                edit(next)
                            }
                        }
                    }
                }
                Text(word(W.bp_country_help), color = c.textMuted, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FluentField(draft.currency, { edit(draft.copy(currency = it)) }, Modifier.weight(1f), label = word(W.bp_currency), help = word(W.bp_currency_help))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(word(W.bp_calendar), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    PillRow(Modifier.fillMaxWidth()) {
                        ShopCalendar.Kind.values().forEach { k ->
                            FluentPill(word(calendarWord(k)), draft.calendar == k) { edit(draft.copy(calendar = k)) }
                        }
                    }
                    Text(word(W.bp_calendar_help), color = c.textMuted, fontSize = 12.sp)
                }
            }
        }
        // Getting paid: the payment box on invoices and statements, and the QR.
        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardTitle(word(W.bp_section_paid))
            Text(word(W.bp_paid_help), color = c.textMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(W.invoice_bank_name_hint, Modifier.weight(1f))
                field(W.invoice_bank_title_hint, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                field(W.invoice_bank_iban_hint, Modifier.weight(1f))
                field(W.invoice_bank_jazzcash_hint, Modifier.weight(1f))
            }
            PictureRow(pictures, ProfileImages.Kind.QR, W.bp_qr, W.bp_qr_help)
        }
        // Invoice & statement: what every sheet carries at its foot.
        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardTitle(word(W.bp_section_docs))
            field(W.bp_strn, Modifier.fillMaxWidth(0.5f), help = W.bp_strn_help)
            field(W.invoice_terms, Modifier.fillMaxWidth(), lines = false, help = W.bp_terms_help)
        }
        // Stamp & signature, as the phone files them.
        Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(word(W.bp_section_seals))
            Text(word(W.bp_seals_help), color = c.textMuted, fontSize = 12.sp)
            PictureRow(pictures, ProfileImages.Kind.SIGNATURE, W.signature, W.no_signature_yet)
            PictureRow(pictures, ProfileImages.Kind.STAMP, W.stamp, W.no_stamp_yet)
        }
        if (pictures.failed) Text(word(W.crop_save_failed), color = c.owedToMe, fontSize = 13.sp)
        if (pictures.unreadable) Text(word(W.crop_load_failed), color = c.owedToMe, fontSize = 13.sp)
    }
    PictureDialogs(pictures)
}

/**
 * The shop's four pictures for its sheets (Phase 10): logo, signature, stamp and payment QR. The text backup
 * carries no pictures (the owner's own rule), so the computer keeps its own, one set per business — which is
 * why deleting one asks first.
 */
private class PictureState(val images: ProfileImages) {
    var version by mutableStateOf(0)
    var failed by mutableStateOf(false)
    var unreadable by mutableStateOf(false)
    /** A chosen picture waiting for the owner's crop (the phone's ImageCropActivity). */
    var cropping by mutableStateOf<Pair<ProfileImages.Kind, java.awt.image.BufferedImage>?>(null)
    /** A picture the owner asked to delete, and its name, waiting for his yes. */
    var deleting by mutableStateOf<Pair<ProfileImages.Kind, String>?>(null)
}

@Composable
private fun rememberPictureState(images: ProfileImages) = remember(images) { PictureState(images) }

/** One picture: what it is, its help, and Add / Change / Delete. */
@Composable
private fun PictureRow(state: PictureState, kind: ProfileImages.Kind, title: String, help: String) {
    val c = LocalRoshanColors.current
    val picture = remember(state.version, kind) { state.images.load(kind)?.image?.toComposeImageBitmap() }
    val chooseTitle = word(W.bp_add)
    val name = word(title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).background(Look.subtle, Look.ctl), contentAlignment = Alignment.Center) {
            picture?.let { Image(it, contentDescription = name, modifier = Modifier.size(60.dp)) }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(word(help), color = c.textMuted, fontSize = 12.sp)
        }
        FluentButton(word(if (picture == null) W.bp_add else W.bp_change), icon = "ic_edit", onClick = {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, chooseTitle, java.awt.FileDialog.LOAD)
            startIn(dialog, "Pictures")
            dialog.setFilenameFilter { _, n -> n.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".bmp") || it.endsWith(".gif") } }
            dialog.file = "*.png;*.jpg;*.jpeg"
            dialog.isVisible = true
            val file = dialog.file
            if (file != null) {
                val img = runCatching { javax.imageio.ImageIO.read(java.io.File(dialog.directory, file)) }.getOrNull()
                state.unreadable = img == null
                state.failed = false
                if (img != null) state.cropping = kind to img
            }
        })
        Spacer(Modifier.width(8.dp))
        if (picture != null) FluentButton(word(W.delete), { state.deleting = kind to name }, kind = ButtonKind.DANGER)
    }
}

/** The crop before a picture is kept, and the question before one is deleted. */
@Composable
private fun PictureDialogs(state: PictureState) {
    state.cropping?.let { (kind, img) ->
        AppDialog(
            onCloseRequest = { state.cropping = null },
            onPreviewKeyEvent = escCloses { state.cropping = null },
            title = word(W.adjust_crop_title),
            state = androidx.compose.ui.window.rememberDialogState(width = fitW(760.dp), height = fitH(720.dp))
        ) {
            // A logo is kept whole to start; a signature, stamp or QR starts at its ink.
            val start = remember(img) { if (kind == ProfileImages.Kind.LOGO) java.awt.Rectangle(0, 0, img.width, img.height) else InkBounds.of(img) }
            CropWindowContent(img, start, onUse = { cut ->
                state.cropping = null
                state.failed = runCatching {
                    val tmp = java.io.File.createTempFile("rk-crop", ".png")
                    try {
                        javax.imageio.ImageIO.write(cut, "png", tmp) && state.images.set(kind, tmp)
                    } finally { tmp.delete() }
                }.getOrDefault(false).not()
                state.version++
            }, onCancel = { state.cropping = null })
        }
    }
    state.deleting?.let { (kind, name) ->
        AskDialog(name, word(W.pic_delete_body_pc), word(W.delete), onConfirm = {
            state.images.remove(kind)
            state.deleting = null
            state.version++
        }, onDismiss = { state.deleting = null }, danger = true)
    }
}

/** A calendar's name, as the phone's Business Profile says it. */
internal fun calendarWord(kind: ShopCalendar.Kind): String = when (kind) {
    ShopCalendar.Kind.GREGORIAN -> W.calendar_gregorian
    ShopCalendar.Kind.HIJRI -> W.calendar_hijri
    ShopCalendar.Kind.JALALI -> W.calendar_jalali
}
