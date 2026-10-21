package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.CardLanguage
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.desktop.poster.PosterDraw
import com.innovation313.roshankhata.desktop.poster.PosterFields
import com.innovation313.roshankhata.desktop.poster.PosterInput
import com.innovation313.roshankhata.desktop.poster.PosterOccasion
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.desktopWords
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale
import java.util.Properties
import javax.imageio.ImageIO

/** What the poster form keeps, per business: the language, the design, each design's words and choices. */
private class PosterPrefs(private val file: File) {
    private val p = Properties().apply { if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) } }
    fun get(key: String): String? = p.getProperty(key)
    fun set(values: Map<String, String?>) {
        values.forEach { (k, v) -> if (v == null) p.remove(k) else p.setProperty(k, v) }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { p.store(it, "Roshan Khata poster") }
        // Swapped in one step (21 Oct 2026): Windows will not rename over a file, and the old way deleted
        // the old one first — a power cut in between left neither.
        replaceAtomically(tmp, file)
    }
}

/** "9:30" or "21:30" to minutes after midnight; null when it is not a time. */
internal fun posterMinutes(text: String): Int? {
    val m = Regex("""^\s*(\d{1,2})(?::(\d{2}))?\s*$""").find(com.innovation313.roshankhata.data.Digits.toLatin(text)) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}

internal fun posterClock(minutes: Int): String = "%d:%02d".format(Locale.ROOT, minutes / 60, minutes % 60)

/**
 * The phone's Poster & Status on the computer: the same eleven designs
 * (generated from the phone's files, drawn on the gfx stand-in), in any of
 * the app's nine languages as on the phone (21 Oct 2026), the shop's name, phone and logo from its profile. The heroes are
 * drawn flat here (the phone's are 3D). Saved as a 1080 x 1920 picture to
 * put on WhatsApp Status. Drawn on this computer; nothing is uploaded.
 */
@Composable
fun PosterPane(settings: ShopSettings, images: ProfileImages, home: File, businessId: Long, words: Words) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val prefs = remember(businessId) { PosterPrefs(File(home, "poster_b$businessId.properties")) }
    // The poster's own language, the app's when none was chosen (the phone's rule).
    var posterLang by remember(businessId) { mutableStateOf(CardLanguage.of(prefs.get("lang")) ?: CardLanguage.forApp(Locale.getDefault())) }
    val lang = posterLang.code
    val ur = posterLang == CardLanguage.URDU
    val posterWords = remember(lang) { desktopWords(posterLang.locale) }
    var occasion by remember(businessId) { mutableStateOf(PosterOccasion.byKey(prefs.get("occasion"))) }
    fun k(key: String) = "${occasion.key}.$lang.$key"
    fun original(id: String, hint: String) = posterWords.text(id.ifEmpty { hint })
    var title by remember(businessId, lang, occasion) { mutableStateOf(prefs.get(k("title")) ?: original(occasion.title, W.poster_custom_title_hint)) }
    var message by remember(businessId, lang, occasion) { mutableStateOf(prefs.get(k("message")) ?: original(occasion.message, W.poster_custom_msg_hint)) }

    val d = PosterFields()
    fun flag(key: String, def: Boolean) = prefs.get(key)?.toBooleanStrictOrNull() ?: def
    var items by remember(businessId) { mutableStateOf(prefs.get("items").orEmpty()) }
    var eidDays by remember(businessId) { mutableStateOf(prefs.get("eidDays") ?: "0") }
    var ramazanHours by remember(businessId) { mutableStateOf(flag("ramazanHours", d.ramazanHours)) }
    var urgentPhone by remember(businessId) { mutableStateOf(flag("urgentPhone", d.urgentPhone)) }
    var opens by remember(businessId) { mutableStateOf(prefs.get("opens") ?: posterClock(d.opens)) }
    var closes by remember(businessId) { mutableStateOf(prefs.get("closes") ?: posterClock(d.closes)) }
    var prayerBreak by remember(businessId) { mutableStateOf(flag("prayerBreak", d.prayerBreak)) }
    var offerPercent by remember(businessId) { mutableStateOf(prefs.get("offerPercent") ?: d.offerPercent.toString()) }
    var offerDays by remember(businessId) { mutableStateOf(prefs.get("offerDays") ?: d.offerDays.toString()) }
    var showLogo by remember(businessId) { mutableStateOf(flag("showLogo", d.showLogo)) }
    var showPhone by remember(businessId) { mutableStateOf(flag("showPhone", d.showPhone)) }
    var showAddress by remember(businessId) { mutableStateOf(flag("showAddress", d.showAddress)) }
    var showEmail by remember(businessId) { mutableStateOf(flag("showEmail", d.showEmail)) }
    var onWhatsapp by remember(businessId) { mutableStateOf(flag("onWhatsapp", d.onWhatsapp)) }
    var showMark by remember(businessId) { mutableStateOf(flag("showMark", d.showMark)) }

    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    var told by remember { mutableStateOf<String?>(null) }
    val hasLogo = images.has(ProfileImages.Kind.LOGO)
    val logo = remember(images, showLogo, hasLogo) { if (showLogo && hasLogo) images.load(ProfileImages.Kind.LOGO) else null }
    val shop = (if (ur) settings.businessNameUrdu()?.takeIf { it.isNotBlank() } else null) ?: settings.businessName().orEmpty()

    fun fields() = d.copy(
        items = items.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(PosterFields.MAX_ITEMS),
        eidDays = eidDays.trim().toIntOrNull()?.coerceIn(0, 10) ?: 0,
        ramazanHours = ramazanHours, urgentPhone = urgentPhone,
        opens = posterMinutes(opens) ?: d.opens, closes = posterMinutes(closes) ?: d.closes, prayerBreak = prayerBreak,
        offerPercent = offerPercent.trim().toIntOrNull()?.coerceIn(1, 90) ?: d.offerPercent,
        offerDays = offerDays.trim().toIntOrNull()?.coerceIn(0, 30) ?: 0,
        showLogo = showLogo, showPhone = showPhone, showAddress = showAddress, showEmail = showEmail,
        showMark = showMark, onWhatsapp = onWhatsapp
    )

    fun input(): PosterInput {
        val f = fields()
        return PosterInput(
            occasion = occasion, title = title.trim(), message = message.trim(), shopName = shop.trim(),
            phone = if (f.showPhone) settings.businessPhone()?.trim()?.takeIf { it.isNotEmpty() } else null,
            address = if (f.showAddress) settings.businessAddress()?.trim()?.takeIf { it.isNotEmpty() } else null,
            logo = logo, mark = if (f.showMark) posterWords.text(W.made_with_app) else null, fields = f,
            email = if (f.showEmail) settings.businessEmail()?.trim()?.takeIf { it.isNotEmpty() } else null,
            whatsapp = f.onWhatsapp
        )
    }

    fun context() = Context(posterWords, settings, images, posterLang.rtl, posterLang.locale)

    fun save() {
        val o = original(occasion.title, W.poster_custom_title_hint)
        val m = original(occasion.message, W.poster_custom_msg_hint)
        prefs.set(
            mapOf(
                "lang" to lang, "occasion" to occasion.key,
                // Only words the owner changed are kept: an untouched line follows the language.
                k("title") to title.takeIf { it != o }, k("message") to message.takeIf { it != m },
                "items" to items, "eidDays" to eidDays, "ramazanHours" to ramazanHours.toString(),
                "urgentPhone" to urgentPhone.toString(), "opens" to opens, "closes" to closes,
                "prayerBreak" to prayerBreak.toString(), "offerPercent" to offerPercent, "offerDays" to offerDays,
                "showLogo" to showLogo.toString(), "showPhone" to showPhone.toString(), "showAddress" to showAddress.toString(),
                "showEmail" to showEmail.toString(), "onWhatsapp" to onWhatsapp.toString(), "showMark" to showMark.toString()
            )
        )
    }

    LaunchedEffect(occasion, lang, title, message, items, eidDays, ramazanHours, urgentPhone, opens, closes, prayerBreak,
        offerPercent, offerDays, showLogo, showPhone, showAddress, showEmail, onWhatsapp, showMark, logo, shop) {
        delay(300)
        val ctx = context()
        val inp = input()
        picture = withContext(Dispatchers.Default) {
            runCatching { scaled(PosterDraw.image(ctx, inp), 540, 960).toComposeImageBitmap() }.getOrNull()
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.46f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaneTitle(word(W.poster_title), word(W.poster_subtitle))
            Text(word(W.poster_language), color = c.ink, fontWeight = FontWeight.Bold)
            // One pill per language, each in its own script, in a row that scrolls.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardLanguage.entries.forEach { l ->
                    FluentPill(l.native, l == posterLang) { if (l != posterLang) { save(); posterLang = l } }
                }
            }
            Text(word(W.poster_lang_note), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.poster_choose_design), color = c.ink, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PosterOccasion.offered().chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { o ->
                            val name = word(if (o.title.isEmpty()) W.poster_custom else o.title).trimEnd('!', '！', '۔')
                            FluentPill(name, o == occasion) { save(); occasion = o }
                        }
                    }
                }
            }
            if (shop.isBlank()) Text(word(W.poster_shop_needed), color = c.owedToMe, fontSize = 13.sp)
            PosterField(title, { title = it }, posterWords.text(W.poster_heading_hint))
            PosterField(message, { message = it }, posterWords.text(W.poster_message_hint), single = false)
            if (occasion.title.isNotEmpty()) TextButton({
                title = original(occasion.title, W.poster_custom_title_hint)
                message = original(occasion.message, W.poster_custom_msg_hint)
            }) { Text(word(W.poster_use_original), color = c.brandGreenText) }

            when (occasion) {
                PosterOccasion.NEW_STOCK -> PosterField(items, { items = it }, word(W.poster_items_label), posterWords.text(W.poster_item_hint))
                PosterOccasion.EID_FITR -> PosterField(eidDays, { eidDays = it.filter(Char::isDigit).take(2) }, word(W.poster_eid_days))
                PosterOccasion.RAMAZAN -> Tick(ramazanHours, word(W.poster_ramazan_toggle)) { ramazanHours = it }
                PosterOccasion.CLOSED -> Tick(urgentPhone, word(W.poster_urgent_phone)) { urgentPhone = it }
                PosterOccasion.HOURS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { PosterField(opens, { opens = it }, word(W.poster_opens), "9:00") }
                        Column(Modifier.weight(1f)) { PosterField(closes, { closes = it }, word(W.poster_closes), "20:00") }
                    }
                    Tick(prayerBreak, word(W.poster_prayer_break_toggle)) { prayerBreak = it }
                }
                PosterOccasion.OFFER -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { PosterField(offerPercent, { offerPercent = it.filter(Char::isDigit).take(2) }, word(W.poster_offer_percent) + " %") }
                    Column(Modifier.weight(1f)) { PosterField(offerDays, { offerDays = it.filter(Char::isDigit).take(2) }, word(W.poster_offer_days_toggle)) }
                }
                else -> {}
            }

            Text(word(W.poster_show_on), color = c.ink, fontWeight = FontWeight.Bold)
            Tick(showLogo && hasLogo, word(if (hasLogo) W.poster_chip_logo else W.biz_card_logo_none), enabled = hasLogo) { showLogo = it }
            Tick(showPhone, word(W.poster_chip_phone)) { showPhone = it }
            Tick(onWhatsapp, word(W.poster_chip_whatsapp)) { onWhatsapp = it }
            Tick(showAddress, word(W.poster_chip_address)) { showAddress = it }
            Tick(showEmail, word(W.poster_chip_email)) { showEmail = it }
            Tick(showMark, word(W.poster_show_mark)) { showMark = it }
            if (settings.businessPhone().isNullOrBlank()) Text(word(W.poster_no_phone), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.poster_desktop_flat), color = c.textMuted, fontSize = 12.sp)
        }
        Column(Modifier.weight(0.54f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            picture?.let {
                Image(it, contentDescription = word(W.poster_title),
                    modifier = Modifier.width(400.dp).heightIn(max = 712.dp).aspectRatio(PosterDraw.WIDTH.toFloat() / PosterDraw.HEIGHT))
            }
            val saveTitle = word(W.poster_save_picture)
            val needShop = word(W.poster_shop_needed)
            FillButton(saveTitle, c.brandGreen) {
                if (shop.isBlank()) { told = needShop; return@FillButton }
                save()
                val dialog = FileDialog(null as Frame?, saveTitle, FileDialog.SAVE)
                startIn(dialog, "Pictures")
                dialog.file = "RoshanKhata_Poster_${occasion.key}.png"
                dialog.isVisible = true
                val file = dialog.file?.let { File(dialog.directory, if (it.endsWith(".png", true)) it else "$it.png") } ?: return@FillButton
                val ctx = context()
                val inp = input()
                scope.launch {
                    told = withContext(Dispatchers.IO) {
                        runCatching { ImageIO.write(PosterDraw.image(ctx, inp), "png", file) }
                            .map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { it.message ?: it.toString() }
                    }
                }
            }
            told?.let { Text(it, color = c.textMuted, fontSize = 13.sp) }
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}

/** A finished poster shrunk for the screen, smoothly (the saved one is full size). */
private fun scaled(img: java.awt.image.BufferedImage, w: Int, h: Int): java.awt.image.BufferedImage {
    val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
    g.drawImage(img, 0, 0, w, h, null)
    g.dispose()
    return out
}

@Composable
private fun PosterField(value: String, onChange: (String) -> Unit, label: String, hint: String = "", single: Boolean = true) {
    Outlined(
        value, onChange, label = { Text(label) }, placeholder = { if (hint.isNotEmpty()) Text(hint) },
        singleLine = single, minLines = if (single) 1 else 2, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Tick(on: Boolean, label: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalRoshanColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, onChange, enabled = enabled)
        Text(label, color = c.ink, fontSize = 14.sp)
    }
}
