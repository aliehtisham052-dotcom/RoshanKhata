package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.desktop.card.CardTemplates
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
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
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.io.File
import java.util.Locale
import java.util.Properties
import javax.imageio.ImageIO

/**
 * The phone's Business Card on the computer (Phase 10): the same twenty-four
 * designs (CardTemplates, generated from the phone's file and drawn on the
 * gfx stand-in), an English or an Urdu card, the shop's logo when it has one.
 * Saved as a picture to send, or printed as a sheet of ten at true size
 * (3.5 x 2 inches) to cut. Drawn on this computer; nothing is uploaded.
 */
object CardDraw {
    /** The still card at [w] x [h] pixels, every moving part at rest. */
    fun image(template: CardTemplates.Template, data: CardTemplates.CardData, w: Int = CardTemplates.W, h: Int = CardTemplates.H): BufferedImage {
        val canvas = Canvas(w, h)
        template.draw(canvas, data, w, h, -1f)
        return canvas.picture(w, h)
    }
}

/** Ten cards at true size on one sheet, with thin cut marks, for the shop's own printer. */
class CardSheet(private val card: BufferedImage) : Printable {
    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        if (pageIndex > 0) return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        val cw = 3.5 * 72
        val ch = 2.0 * 72
        val cols = 2
        val rows = 5
        val left = (format.width - cols * cw) / 2
        val top = (format.height - rows * ch) / 2
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = left + c * cw
            val y = top + r * ch
            val at = java.awt.geom.AffineTransform()
            at.translate(x, y)
            at.scale(cw / card.width, ch / card.height)
            g.drawImage(card, at, null)
        }
        g.color = java.awt.Color(0, 0, 0, 90)
        g.stroke = java.awt.BasicStroke(0.3f)
        for (r in 0..rows) g.draw(java.awt.geom.Line2D.Double(left - 10, top + r * ch, left + cols * cw + 10, top + r * ch))
        for (c in 0..cols) g.draw(java.awt.geom.Line2D.Double(left + c * cw, top - 10, left + c * cw, top + rows * ch + 10))
        return Printable.PAGE_EXISTS
    }
}

/** What the card form keeps, per business, beside the shop's own settings. */
private class CardPrefs(private val file: File) {
    private val p = Properties().apply { if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) } }
    fun get(key: String, def: String = "") = p.getProperty(key) ?: def
    fun set(values: Map<String, String>) {
        values.forEach { (k, v) -> p.setProperty(k, v) }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { p.store(it, "Roshan Khata card") }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

@Composable
fun BusinessCardPane(settings: ShopSettings, images: ProfileImages, home: File, businessId: Long, words: Words) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val prefs = remember(businessId) { CardPrefs(File(home, "card_b$businessId.properties")) }
    var lang by remember(businessId) { mutableStateOf(prefs.get("lang", "en")) }
    val ur = lang == "ur"
    fun k(key: String) = if (ur) key + "_ur" else key
    // The card's own words, in the card's language (not the app's).
    val cardWords = remember(lang) { desktopWords(Locale.forLanguageTag(lang)) }
    remember(businessId) { CardTemplates.init(Context(words, settings, images, false)); 0 }

    var name by remember(lang) { mutableStateOf((if (ur) settings.businessNameUrdu() else settings.businessName()).orEmpty()) }
    var type by remember(lang) { mutableStateOf(prefs.get(k("type"))) }
    var owner by remember(lang) { mutableStateOf(prefs.get(k("owner"))) }
    var address by remember(lang) { mutableStateOf(prefs.get(k("address"), if (ur) "" else settings.businessAddress().orEmpty())) }
    var tagline by remember(lang) { mutableStateOf(prefs.get(k("tagline"))) }
    var qrMessage by remember(lang) { mutableStateOf(prefs.get(k("qr"), cardWords.text(W.biz_card_qr_message_default))) }
    var phone by remember { mutableStateOf(prefs.get("phone", settings.businessPhone().orEmpty())) }
    var whatsapp by remember { mutableStateOf(prefs.get("whatsapp")) }
    var waSame by remember { mutableStateOf(prefs.get("wa_same", "true") == "true") }
    var email by remember { mutableStateOf(prefs.get("email", settings.businessEmail().orEmpty())) }
    var web by remember { mutableStateOf(prefs.get("web")) }
    var showMark by remember { mutableStateOf(prefs.get("mark", "true") == "true") }
    var showLogo by remember { mutableStateOf(prefs.get("logo", "true") == "true") }
    var template by remember { mutableStateOf(prefs.get("template", CardTemplates.all.first().id.toString()).toIntOrNull() ?: CardTemplates.all.first().id) }
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    var told by remember { mutableStateOf<String?>(null) }
    val logo = remember(images, showLogo) { if (showLogo) images.load(ProfileImages.Kind.LOGO) else null }

    fun data() = CardTemplates.CardData(
        name = name.trim().ifEmpty { cardWords.text(W.biz_card_name_hint) },
        type = type.trim(), owner = owner.trim(), phone = phone.trim(), address = address.trim(),
        footer = if (showMark) cardWords.text(W.made_with_app) else "",
        whatsapp = if (waSame) phone.trim() else whatsapp.trim(),
        email = email.trim(), web = web.trim(), tagline = tagline.trim(), qrMessage = qrMessage.trim(),
        logo = logo, rtl = ur
    )

    fun save() {
        if (ur) settings.setBusinessNameUrdu(name) else settings.setBusinessName(name)
        prefs.set(
            mapOf(
                "lang" to lang, k("type") to type.trim(), k("owner") to owner.trim(), k("address") to address.trim(),
                k("tagline") to tagline.trim(), k("qr") to qrMessage.trim(), "phone" to phone.trim(), "whatsapp" to whatsapp.trim(),
                "wa_same" to waSame.toString(), "email" to email.trim(), "web" to web.trim(), "mark" to showMark.toString(),
                "logo" to showLogo.toString(), "template" to template.toString()
            )
        )
    }

    LaunchedEffect(template, lang, name, type, owner, address, tagline, qrMessage, phone, whatsapp, waSame, email, web, showMark, logo) {
        delay(250)
        val d = data()
        val t = CardTemplates.byId(template)
        picture = withContext(Dispatchers.Default) { runCatching { CardDraw.image(t, d, 900, 525).toComposeImageBitmap() }.getOrNull() }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.42f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaneTitle(word(W.biz_card), word(W.biz_card_sub))
            Text(word(W.biz_card_language), color = c.ink, fontWeight = FontWeight.Bold)
            TwoWay("English", "اردو", !ur) { english -> save(); lang = if (english) "en" else "ur" }
            Text(word(W.biz_card_language_note), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.biz_card_sec_own), color = c.ink, fontWeight = FontWeight.Bold)
            Field(name, { name = it }, cardWords.text(W.biz_card_l_name), cardWords.text(W.biz_card_ex_name))
            Field(type, { type = it }, cardWords.text(W.biz_card_l_type), cardWords.text(W.biz_card_ex_type))
            Field(owner, { owner = it }, cardWords.text(W.biz_card_l_owner), cardWords.text(W.biz_card_ex_owner))
            Field(address, { address = it }, cardWords.text(W.biz_card_l_address), cardWords.text(W.biz_card_ex_address))
            Field(tagline, { tagline = it }, cardWords.text(W.biz_card_l_tagline), cardWords.text(W.biz_card_ex_tagline))
            Field(qrMessage, { qrMessage = it }, cardWords.text(W.biz_card_l_qr), cardWords.text(W.biz_card_ex_qr))
            Text(word(W.biz_card_sec_shared), color = c.ink, fontWeight = FontWeight.Bold)
            Field(phone, { phone = it }, word(W.biz_card_l_call), word(W.biz_card_ex_phone))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(waSame, { waSame = it })
                Text(word(W.biz_card_l_wa_same), color = c.ink, fontSize = 14.sp)
            }
            if (!waSame) Field(whatsapp, { whatsapp = it }, word(W.biz_card_l_whatsapp), word(W.biz_card_ex_phone))
            Field(email, { email = it }, word(W.biz_card_l_email), word(W.biz_card_ex_email))
            Field(web, { web = it }, word(W.biz_card_l_web), word(W.biz_card_ex_web))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showMark, { showMark = it })
                Text(word(W.biz_card_show_mark), color = c.ink, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showLogo, { showLogo = it }, enabled = images.has(ProfileImages.Kind.LOGO))
                Text(word(if (images.has(ProfileImages.Kind.LOGO)) W.biz_card_show_logo else W.biz_card_logo_none), color = c.ink, fontSize = 14.sp)
            }
            Text(word(W.biz_card_note), color = c.textMuted, fontSize = 12.sp)
        }
        Column(Modifier.weight(0.58f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            picture?.let {
                Image(it, contentDescription = word(W.biz_card), modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().aspectRatio(CardTemplates.W.toFloat() / CardTemplates.H))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val saveTitle = word(W.biz_card_send_picture)
                val enterName = word(W.biz_card_enter_name)
                FillButton(saveTitle, c.brandGreen) {
                    if (name.isBlank()) { told = enterName; return@FillButton }
                    save()
                    val dialog = FileDialog(null as Frame?, saveTitle, FileDialog.SAVE)
                    startIn(dialog, "Pictures")
                    dialog.file = "RoshanKhata_Card.png"
                    dialog.isVisible = true
                    val file = dialog.file?.let { File(dialog.directory, if (it.endsWith(".png", true)) it else "$it.png") } ?: return@FillButton
                    val d = data()
                    val t = CardTemplates.byId(template)
                    scope.launch {
                        told = withContext(Dispatchers.IO) {
                            runCatching { ImageIO.write(CardDraw.image(t, d), "png", file) }
                                .map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { it.message ?: it.toString() }
                        }
                    }
                }
                TextButton({
                    if (name.isBlank()) { told = enterName; return@TextButton }
                    save()
                    val d = data()
                    val t = CardTemplates.byId(template)
                    scope.launch {
                        // Drawn at print sharpness (1225 x 700 is 350 dots an inch at 3.5 x 2 inches).
                        val card = withContext(Dispatchers.Default) { CardDraw.image(t, d, 1225, CardTemplates.H) }
                        StatementPrint.show(CardSheet(card), words.text(W.biz_card))
                    }
                }) { Text(word(W.print_short), color = c.brandGreenText) }
            }
            told?.let { Text(it, color = c.textMuted, fontSize = 13.sp) }
            Text(word(W.biz_card_template), color = c.ink, fontWeight = FontWeight.Bold)
            // The designs by name, the chosen one filled, as the phone's picker.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardTemplates.all.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { t ->
                            FluentPill(word(t.labelRes), t.id == template) { template = t.id; save() }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, hint: String) {
    Outlined(
        value, onChange, label = { Text(label) }, placeholder = { Text(hint) }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
