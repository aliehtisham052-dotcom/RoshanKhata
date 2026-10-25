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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.CardLanguage
import com.innovation313.roshankhata.data.ShopSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
 * gfx stand-in), in any of the app's nine languages (21 Oct 2026; Urdu, Sindhi,
 * Arabic and Farsi drawn in the design's mirror), the shop's logo when it has one.
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
        // Swapped in one step (21 Oct 2026): Windows will not rename over a file, and the old way deleted
        // the old one first — a power cut in between left neither.
        replaceAtomically(tmp, file)
    }
}

@Composable
fun BusinessCardPane(
    settings: ShopSettings, images: ProfileImages, home: File, businessId: Long, words: Words,
    /** The shop's own name changed here: the window's header reads it again. */
    onShopName: () -> Unit = {}
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val prefs = remember(businessId) { CardPrefs(File(home, "card_b$businessId.properties")) }
    var lang by remember(businessId) { mutableStateOf(CardLanguage.of(prefs.get("lang", "")) ?: CardLanguage.forApp(Locale.getDefault())) }
    fun k(key: String) = lang.key(key)
    // The card's own words, in the card's language (not the app's).
    val cardWords = remember(lang) { desktopWords(lang.locale) }
    // English and Urdu are the shop's own two names (statements use them too);
    // the other languages keep theirs with the card, a Latin one starting from the English.
    fun storedName(): String = when (lang) {
        CardLanguage.ENGLISH -> settings.businessName().orEmpty()
        CardLanguage.URDU -> settings.businessNameUrdu().orEmpty()
        else -> prefs.get(k("name"), if (lang.latin) settings.businessName().orEmpty() else "")
    }
    remember(businessId) { CardTemplates.init(Context(words, settings, images, false)); 0 }

    var name by remember(lang) { mutableStateOf(storedName()) }
    var type by remember(lang) { mutableStateOf(prefs.get(k("type"))) }
    var owner by remember(lang) { mutableStateOf(prefs.get(k("owner"))) }
    var address by remember(lang) { mutableStateOf(prefs.get(k("address"), if (lang == CardLanguage.ENGLISH) settings.businessAddress().orEmpty() else "")) }
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
        // The app's name stays off cards for now (Branding: the owner, 12 Oct 2026); the phone's rule here too.
        footer = if (showMark && com.innovation313.roshankhata.data.Branding.ON_POSTERS_AND_CARDS) cardWords.text(W.made_with_app) else "",
        whatsapp = if (waSame) phone.trim() else whatsapp.trim(),
        email = email.trim(), web = web.trim(), tagline = tagline.trim(), qrMessage = qrMessage.trim(),
        logo = logo, rtl = lang.rtl, nastaliq = lang.nastaliq
    )

    fun save() {
        // The shop's own two names live in Business Profile (owner-only SETTINGS): the card writes them only
        // for someone allowed to change them, and never a blank one (25 Oct 2026, the audit: a box left
        // empty was saved on a language or design click, and the shop's name was erased).
        val typed = name.trim()
        val mayRename = typed.isNotEmpty() && com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.SETTINGS)
        val own = when (lang) {
            CardLanguage.ENGLISH -> {
                if (mayRename && typed != settings.businessName()) { settings.setBusinessName(typed); onShopName() }
                emptyMap()
            }
            CardLanguage.URDU -> {
                if (mayRename && typed != settings.businessNameUrdu()) settings.setBusinessNameUrdu(typed)
                emptyMap()
            }
            else -> mapOf(k("name") to typed)
        }
        prefs.set(
            own + mapOf(
                "lang" to lang.code, k("type") to type.trim(), k("owner") to owner.trim(), k("address") to address.trim(),
                k("tagline") to tagline.trim(), k("qr") to qrMessage.trim(), "phone" to phone.trim(), "whatsapp" to whatsapp.trim(),
                "wa_same" to waSame.toString(), "email" to email.trim(), "web" to web.trim(), "mark" to showMark.toString(),
                "logo" to showLogo.toString(), "template" to template.toString()
            )
        )
    }

    // What was typed is kept when the owner leaves the page (Marhala 4, part 8 — the audit: it was saved only on a
    // language or design click, a picture or a print, so leaving lost it). The newest save, with the newest boxes.
    val saveOnLeave = androidx.compose.runtime.rememberUpdatedState { if (name.isNotBlank()) save() }
    androidx.compose.runtime.DisposableEffect(businessId) { onDispose { runCatching { saveOnLeave.value() } } }

    LaunchedEffect(template, lang, name, type, owner, address, tagline, qrMessage, phone, whatsapp, waSame, email, web, showMark, logo) {
        delay(250)
        val d = data()
        val t = CardTemplates.byId(template)
        picture = withContext(Dispatchers.Default) { runCatching { CardDraw.image(t, d, 1800, 1050).toComposeImageBitmap() }.getOrNull() }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.42f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaneTitle(word(W.biz_card), word(W.biz_card_sub))
            Text(word(W.biz_card_language), color = c.ink, fontWeight = FontWeight.Bold)
            // One pill per language, each in its own script, in a row that scrolls.
            PillRow(Modifier.fillMaxWidth()) {
                CardLanguage.entries.forEach { l ->
                    FluentPill(l.native, l == lang) { if (l != lang) { save(); lang = l } }
                }
            }
            Text(word(W.biz_card_language_note), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.biz_card_sec_own), color = c.ink, fontWeight = FontWeight.Bold)
            // This language's own lines are typed in its direction, as on the phone.
            CompositionLocalProvider(LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(name, { name = it }, cardWords.text(W.biz_card_l_name), cardWords.text(W.biz_card_ex_name))
                    Field(type, { type = it }, cardWords.text(W.biz_card_l_type), cardWords.text(W.biz_card_ex_type))
                    Field(owner, { owner = it }, cardWords.text(W.biz_card_l_owner), cardWords.text(W.biz_card_ex_owner))
                    Field(address, { address = it }, cardWords.text(W.biz_card_l_address), cardWords.text(W.biz_card_ex_address))
                    Field(tagline, { tagline = it }, cardWords.text(W.biz_card_l_tagline), cardWords.text(W.biz_card_ex_tagline))
                    Field(qrMessage, { qrMessage = it }, cardWords.text(W.biz_card_l_qr), cardWords.text(W.biz_card_ex_qr))
                }
            }
            Text(word(W.biz_card_sec_shared), color = c.ink, fontWeight = FontWeight.Bold)
            Field(phone, { phone = it }, word(W.biz_card_l_call), word(W.biz_card_ex_phone))
            Row(Modifier.clip(Look.ctl).press(Look.ctl) { waSame = !waSame }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(waSame, { waSame = it })
                Text(word(W.biz_card_l_wa_same), Modifier.padding(end = 8.dp), color = c.ink, fontSize = 14.sp)
            }
            if (!waSame) Field(whatsapp, { whatsapp = it }, word(W.biz_card_l_whatsapp), word(W.biz_card_ex_phone))
            Field(email, { email = it }, word(W.biz_card_l_email), word(W.biz_card_ex_email))
            Field(web, { web = it }, word(W.biz_card_l_web), word(W.biz_card_ex_web))
            if (com.innovation313.roshankhata.data.Branding.ON_POSTERS_AND_CARDS) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showMark, { showMark = it })
                Text(word(W.biz_card_show_mark), color = c.ink, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(showLogo, { showLogo = it }, enabled = images.has(ProfileImages.Kind.LOGO))
                Text(word(if (images.has(ProfileImages.Kind.LOGO)) W.biz_card_show_logo else W.biz_card_logo_none), color = c.ink, fontSize = 14.sp)
            }
            Text(word(W.biz_card_note_pc), color = c.textMuted, fontSize = 12.sp)
        }
        Column(Modifier.weight(0.58f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            picture?.let {
                // Drawn at twice the size it is shown, and only ever scaled down (21 Oct 2026: 900 px stretched over a
                // 760 dp card on a 125% screen looked soft).
                Image(it, contentDescription = word(W.biz_card), modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().aspectRatio(CardTemplates.W.toFloat() / CardTemplates.H),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val saveTitle = word(W.biz_card_save_picture)
                val enterName = word(W.biz_card_enter_name)
                FluentButton(saveTitle, kind = ButtonKind.ACCENT, icon = "ic_download", onClick = save@{
                    if (name.isBlank()) { told = enterName; return@save }
                    save()
                    val dialog = FileDialog(null as Frame?, saveTitle, FileDialog.SAVE)
                    startIn(dialog, "Pictures")
                    dialog.file = "RoshanKhata_Card.png"
                    dialog.isVisible = true
                    val file = dialog.file?.let { File(dialog.directory, if (it.endsWith(".png", true)) it else "$it.png") } ?: return@save
                    val d = data()
                    val t = CardTemplates.byId(template)
                    scope.launch {
                        told = withContext(Dispatchers.IO) {
                            runCatching { ImageIO.write(CardDraw.image(t, d), "png", file) }
                                .map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { failText(words, it) }
                        }
                    }
                })
                FluentButton(word(W.print_short), kind = ButtonKind.STANDARD, icon = "ic_print", onClick = print@{
                    if (name.isBlank()) { told = enterName; return@print }
                    save()
                    val d = data()
                    val t = CardTemplates.byId(template)
                    scope.launch {
                        // Drawn at print sharpness (1225 x 700 is 350 dots an inch at 3.5 x 2 inches).
                        val card = withContext(Dispatchers.Default) { CardDraw.image(t, d, 2100, 1200) }
                        StatementPrint.show(CardSheet(card), words.text(W.biz_card))
                    }
                })
            }
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
    told?.let { text -> TellDialog(word(W.biz_card), text, { told = null }) }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, hint: String) {
    Outlined(
        value, onChange, label = { Text(label) }, placeholder = { Text(hint) }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
