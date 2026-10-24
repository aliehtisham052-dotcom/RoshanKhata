package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.innovation313.roshankhata.data.Branding
import com.innovation313.roshankhata.data.CardLanguage
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.ui.CardPreviewView
import com.innovation313.roshankhata.ui.CardTemplates
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * The Dukan Card: a visiting card for the shop, drawn entirely on the phone
 * and shared as an image. The competitor offers decorated template cards; ours
 * stay in the brand's own colours. A card is made in any of the app's nine
 * languages (21 Oct 2026); Urdu, Sindhi, Arabic and Farsi cards are drawn in
 * the design's mirror, so they read right to left.
 *
 * Nothing here touches the network. The card is rendered to a bitmap, cached,
 * and handed to the share sheet via FileProvider, the same road the receipt
 * image already travels.
 */
class BusinessCardActivity : BaseActivity() {

    private lateinit var preview: CardPreviewView
    private lateinit var etBizName: EditText
    private lateinit var etType: EditText
    private lateinit var etOwner: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
    private lateinit var etWhatsapp: EditText
    private lateinit var etEmail: EditText
    private lateinit var etWeb: EditText
    private lateinit var etTagline: EditText
    private lateinit var etQrMessage: EditText
    private lateinit var markSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var logoSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var logoHint: android.widget.TextView

    /** The shop's logo from its Profile; null when it has none. Reloaded on return. */
    private var logo: Bitmap? = null
    private lateinit var tplButtons: List<Button>

    private var template = TPL_DEFAULT

    /** The card's own language, independent of the app's (12 Oct; nine languages 21 Oct). */
    private var cardLang = CardLanguage.ENGLISH
    private lateinit var langButtons: List<Button>
    private lateinit var waSame: com.google.android.material.switchmaterial.SwitchMaterial
    /** Set while a language's fields are being filled in, so they are not saved or drawn one by one. */
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_card)
        CardTemplates.init(this)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        preview = findViewById(R.id.ivCardPreview)
        etBizName = findViewById(R.id.etBizName)
        etType = findViewById(R.id.etType)
        etOwner = findViewById(R.id.etOwner)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
        etWhatsapp = findViewById(R.id.etWhatsapp)
        etEmail = findViewById(R.id.etEmail)
        etWeb = findViewById(R.id.etWeb)
        etTagline = findViewById(R.id.etTagline)
        etQrMessage = findViewById(R.id.etQrMessage)

        markSwitch = findViewById(R.id.switchCardWatermark)
        logoSwitch = findViewById(R.id.switchCardLogo)
        logoHint = findViewById(R.id.tvCardLogoHint)

        buildTemplateRow()

        // Prefill from what the app already knows, then whatever was last typed
        // here. The business name is shared with statements via BusinessProfile.
        val prefs = getSharedPreferences(PREFS + Businesses.suffix(this), MODE_PRIVATE)
        cardLang = CardLanguage.of(prefs.getString(KEY_LANG, null)) ?: defaultLanguage()
        loading = true
        etPhone.setText(prefs.getString(KEY_PHONE, ""))
        etWhatsapp.setText(prefs.getString(KEY_WHATSAPP, ""))
        // WhatsApp on the call number (13 Oct): on when it was saved so, or
        // when the two boxes already held the same number.
        waSame = findViewById(R.id.switchWhatsappSame)
        waSame.isChecked = prefs.getBoolean(KEY_WA_SAME,
            etWhatsapp.text.isNotBlank() && etWhatsapp.text.toString().filter { it.isDigit() } == etPhone.text.toString().filter { it.isDigit() })
        etWhatsapp.visibility = if (waSame.isChecked) android.view.View.GONE else android.view.View.VISIBLE
        waSame.setOnCheckedChangeListener { _, on ->
            etWhatsapp.visibility = if (on) android.view.View.GONE else android.view.View.VISIBLE
            render()
        }
        // The card's own email, else the shop's (Profile, 12 Oct).
        etEmail.setText(prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() } ?: BusinessProfile.businessEmail(this).orEmpty())
        etWeb.setText(prefs.getString(KEY_WEB, ""))
        fillLanguageFields()
        loading = false
        buildLanguageRow()
        // A design that was removed (ids 0-7) resolves to the first card, and the
        // picker highlights that card rather than nothing.
        template = CardTemplates.byId(prefs.getInt(KEY_TEMPLATE, TPL_DEFAULT)).id
        // On unless the owner has said otherwise. Not offered while the app's
        // mark is off on cards (Branding); the saved choice is kept for later.
        markSwitch.visibility = if (Branding.ON_POSTERS_AND_CARDS) android.view.View.VISIBLE else android.view.View.GONE
        markSwitch.isChecked = prefs.getBoolean(KEY_MARK, true)
        markSwitch.setOnCheckedChangeListener { _, on ->
            getSharedPreferences(PREFS + Businesses.suffix(this), MODE_PRIVATE)
                .edit().putBoolean(KEY_MARK, on).apply()
            render()
        }

        // On unless the owner has said otherwise; only offered when a logo exists.
        logoSwitch.isChecked = prefs.getBoolean(KEY_LOGO, true)
        logoSwitch.setOnCheckedChangeListener { _, on ->
            getSharedPreferences(PREFS + Businesses.suffix(this), MODE_PRIVATE)
                .edit().putBoolean(KEY_LOGO, on).apply()
            render()
        }
        // No logo yet: point to where one is added (Profile), rather than a dead switch.
        logoHint.setOnClickListener {
            startActivity(Intent(this, BusinessSettingsActivity::class.java))
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (!loading) render() }
        }
        listOf(etBizName, etType, etOwner, etPhone, etAddress, etWhatsapp, etEmail, etWeb, etTagline, etQrMessage)
            .forEach { it.addTextChangedListener(watcher) }

        findViewById<Button>(R.id.btnShareCard).apply {
            backgroundTintList = ColorStateList.valueOf(BRAND_GREEN)
            setTextColor(Color.WHITE)
            setOnClickListener { shareCard() }
        }

        refreshTemplateButtons()
        loadLogo()
        render()
    }

    override fun onResume() {
        super.onResume()
        // The owner may have just added or changed the logo in Profile.
        if (::preview.isInitialized) {
            loadLogo()
            render()
        }
    }

    // ---------- Language ----------

    /** The app's language when cards speak it, else English. */
    private fun defaultLanguage(): CardLanguage =
        CardLanguage.forApp(Locale.forLanguageTag(com.innovation313.roshankhata.ui.LocaleRefresh.tag(this)))

    /** A context speaking the card's language: its default lines and the app's mark. */
    private fun cardContext(): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(cardLang.locale)
        return createConfigurationContext(config)
    }

    /** The key a language-own field is kept under: English and Urdu keep the keys they always had. */
    private fun k(key: String) = cardLang.key(key)

    /** One pill per language, each in its own script, in a row that scrolls. */
    private fun buildLanguageRow() {
        val row = findViewById<android.widget.LinearLayout>(R.id.cardLangRow)
        row.removeAllViews()
        langButtons = CardLanguage.entries.map { lang ->
            Button(this).apply {
                text = lang.native
                isAllCaps = false
                textSize = 14f
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(16), 0, dp(16), 0)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
                ).apply { marginEnd = dp(8) }
                contentDescription = lang.native
                setOnClickListener {
                    if (lang == cardLang) return@setOnClickListener
                    save()
                    cardLang = lang
                    getSharedPreferences(PREFS + Businesses.suffix(this@BusinessCardActivity), MODE_PRIVATE)
                        .edit().putString(KEY_LANG, lang.code).apply()
                    loading = true
                    fillLanguageFields()
                    loading = false
                    refreshLanguageButtons()
                    render()
                }
                row.addView(this)
                com.innovation313.roshankhata.ui.TextFit.relax(this)
            }
        }
        refreshLanguageButtons()
    }

    private fun refreshLanguageButtons() {
        langButtons.forEachIndexed { index, btn ->
            val selected = CardLanguage.entries[index] == cardLang
            btn.backgroundTintList = ColorStateList.valueOf(if (selected) BRAND_GREEN else Color.WHITE)
            btn.setTextColor(if (selected) Color.WHITE else INK)
        }
    }

    /**
     * The shop's name on this language's card. English and Urdu are the
     * Profile's own two names (statements use them too); the other languages
     * keep theirs with the card, a Latin-letter one starting from the English.
     */
    private fun storedName(): String = when (cardLang) {
        CardLanguage.ENGLISH -> BusinessProfile.businessName(this).orEmpty()
        CardLanguage.URDU -> BusinessProfile.businessNameUrdu(this).orEmpty()
        else -> getSharedPreferences(PREFS + Businesses.suffix(this), MODE_PRIVATE).getString(k(KEY_NAME), null)
            ?: if (cardLang.latin) BusinessProfile.businessName(this).orEmpty() else ""
    }

    /** The fields each language keeps for itself, and the way they are typed. */
    private fun fillLanguageFields() {
        val prefs = getSharedPreferences(PREFS + Businesses.suffix(this), MODE_PRIVATE)
        etBizName.setText(storedName())
        etType.setText(prefs.getString(k(KEY_TYPE), ""))
        etOwner.setText(prefs.getString(k(KEY_OWNER), ""))
        etAddress.setText(prefs.getString(k(KEY_ADDRESS), ""))
        etTagline.setText(prefs.getString(k(KEY_TAGLINE), ""))
        // Pre-filled until the owner changes it; an emptied box stays empty.
        etQrMessage.setText(prefs.getString(k(KEY_QR_MESSAGE), cardContext().getString(R.string.biz_card_qr_message_default)))
        applyCardLanguage()
    }

    /**
     * The form speaks the card's language (13 Oct; every language 21 Oct). A
     * card is filled in wholly in its own language: labels, examples and, for
     * Urdu, Sindhi, Arabic and Farsi, typing right to left, with no English
     * hint sitting inside an Urdu box. Its whole section is laid out
     * right to left, not only its text, so each box keeps one fixed side for
     * its text and nothing moves while the owner types. Numbers, email and
     * website stay left to right on both cards.
     */
    private fun applyCardLanguage() {
        val cc = cardContext()
        val rtl = cardLang.rtl
        fun label(id: Int, res: Int) { findViewById<TextView>(id).text = cc.getString(res) }
        label(R.id.tvCardOwnTitle, R.string.biz_card_sec_own)
        findViewById<TextView>(R.id.tvCardOwnTag).text = cardLang.tag
        label(R.id.tvCardOwnSub, R.string.biz_card_sec_own_sub)
        label(R.id.tvCardSharedTitle, R.string.biz_card_sec_shared)
        label(R.id.lblCardName, R.string.biz_card_l_name)
        label(R.id.lblCardType, R.string.biz_card_l_type)
        label(R.id.lblCardOwner, R.string.biz_card_l_owner)
        label(R.id.lblCardAddress, R.string.biz_card_l_address)
        label(R.id.lblCardTagline, R.string.biz_card_l_tagline)
        label(R.id.lblCardPhone, R.string.biz_card_l_call)
        label(R.id.lblCardWhatsapp, R.string.biz_card_l_whatsapp)
        label(R.id.lblCardEmail, R.string.biz_card_l_email)
        label(R.id.lblCardWeb, R.string.biz_card_l_web)
        label(R.id.lblCardQr, R.string.biz_card_l_qr)
        waSame.text = cc.getString(R.string.biz_card_l_wa_same)
        etBizName.hint = cc.getString(R.string.biz_card_ex_name)
        etType.hint = cc.getString(R.string.biz_card_ex_type)
        etOwner.hint = cc.getString(R.string.biz_card_ex_owner)
        etAddress.hint = cc.getString(R.string.biz_card_ex_address)
        etTagline.hint = cc.getString(R.string.biz_card_ex_tagline)
        etPhone.hint = cc.getString(R.string.biz_card_ex_phone)
        etWhatsapp.hint = cc.getString(R.string.biz_card_ex_phone)
        etEmail.hint = cc.getString(R.string.biz_card_ex_email)
        etWeb.hint = cc.getString(R.string.biz_card_ex_web)
        etQrMessage.hint = cc.getString(R.string.biz_card_ex_qr)
        val layoutDir = if (rtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
        findViewById<android.view.View>(R.id.cardOwnSection).layoutDirection = layoutDir
        findViewById<android.view.View>(R.id.cardSharedSection).layoutDirection = layoutDir
        val textDir = if (rtl) android.view.View.TEXT_DIRECTION_RTL else android.view.View.TEXT_DIRECTION_LTR
        listOf(etBizName, etType, etOwner, etAddress, etTagline, etQrMessage).forEach {
            it.layoutDirection = layoutDir
            it.textDirection = textDir
        }
    }

    /** Read the Profile logo, and show the switch or the "add one" hint accordingly. */
    private fun loadLogo() {
        logo = BusinessProfile.loadLogo(this)
        val has = logo != null
        logoSwitch.visibility = if (has) android.view.View.VISIBLE else android.view.View.GONE
        logoHint.visibility = if (has) android.view.View.GONE else android.view.View.VISIBLE
        // A read-only phone cannot open Profile, so it is not offered the way there.
        com.innovation313.roshankhata.data.ViewerMode.hide(this, logoHint)
    }

    /**
     * One chip per design, in a row that scrolls. Built from the template list
     * rather than the layout, so a thirteenth design is a line in
     * CardTemplates and nothing here.
     */
    private fun buildTemplateRow() {
        val row = findViewById<android.widget.LinearLayout>(R.id.templateRow)
        row.removeAllViews()
        tplButtons = CardTemplates.all.map { tpl ->
            Button(this).apply {
                text = getString(tpl.labelRes)
                isAllCaps = false
                textSize = 13f
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(18), 0, dp(18), 0)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    template = tpl.id
                    refreshTemplateButtons()
                    render()
                }
                row.addView(this)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Selected template shows in brand green; the rest stay quiet. */
    private fun refreshTemplateButtons() {
        tplButtons.forEachIndexed { index, btn ->
            val selected = CardTemplates.all[index].id == template
            btn.backgroundTintList =
                ColorStateList.valueOf(if (selected) BRAND_GREEN else Color.WHITE)
            btn.setTextColor(if (selected) Color.WHITE else INK)
        }
    }

    // ---------- Drawing ----------

    private fun cardData() = CardTemplates.CardData(
        name = etBizName.text.toString().trim()
            .ifEmpty { cardContext().getString(R.string.biz_card_name_hint) },
        type = etType.text.toString().trim(),
        owner = etOwner.text.toString().trim(),
        phone = etPhone.text.toString().trim(),
        address = etAddress.text.toString().trim(),
        // Empty when the owner has switched the mark off — CardTemplates
        // draws nothing for an empty footer, so no template needs to know
        // about the setting.
        footer = if (markSwitch.isChecked && Branding.ON_POSTERS_AND_CARDS) cardContext().getString(R.string.made_with_app) else "",
        whatsapp = if (waSame.isChecked) etPhone.text.toString().trim() else etWhatsapp.text.toString().trim(),
        email = etEmail.text.toString().trim(),
        web = etWeb.text.toString().trim(),
        tagline = etTagline.text.toString().trim(),
        qrMessage = etQrMessage.text.toString().trim(),
        logo = if (logoSwitch.isChecked) logo else null,
        rtl = cardLang.rtl,
        nastaliq = cardLang.nastaliq
    )

    /** The still card that is shared: every moving part at rest. */
    private fun drawCard(): Bitmap {
        val bmp = Bitmap.createBitmap(CardTemplates.W, CardTemplates.H, Bitmap.Config.ARGB_8888)
        CardTemplates.byId(template).draw(
            Canvas(bmp), cardData(), CardTemplates.W, CardTemplates.H, -1f
        )
        return bmp
    }

    private fun render() {
        preview.show(CardTemplates.byId(template), cardData())
    }

    // ---------- Share ----------

    private fun shareCard() {
        if (etBizName.text.toString().isBlank()) {
            Toast.makeText(this, R.string.biz_card_enter_name, Toast.LENGTH_SHORT).show()
            return
        }
        save()
        try {
            val dir = File(cacheDir, "cards").apply { mkdirs() }
            val file = File(dir, "dukan-card.png")
            FileOutputStream(file).use { drawCard().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getString(R.string.biz_card_share)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        val prefs = getSharedPreferences(PREFS + Businesses.suffix(this), MODE_PRIVATE)
        when (cardLang) {
            CardLanguage.ENGLISH -> BusinessProfile.setBusinessName(this, etBizName.text.toString())
            CardLanguage.URDU -> BusinessProfile.setBusinessNameUrdu(this, etBizName.text.toString())
            else -> prefs.edit().putString(k(KEY_NAME), etBizName.text.toString().trim()).apply()
        }
        prefs.edit()
            .putString(k(KEY_TYPE), etType.text.toString().trim())
            .putString(k(KEY_OWNER), etOwner.text.toString().trim())
            .putString(KEY_PHONE, etPhone.text.toString().trim())
            .putString(k(KEY_ADDRESS), etAddress.text.toString().trim())
            .putString(KEY_WHATSAPP, etWhatsapp.text.toString().trim())
            .putBoolean(KEY_WA_SAME, waSame.isChecked)
            .putString(KEY_EMAIL, etEmail.text.toString().trim())
            .putString(KEY_WEB, etWeb.text.toString().trim())
            .putString(k(KEY_TAGLINE), etTagline.text.toString().trim())
            .putString(k(KEY_QR_MESSAGE), etQrMessage.text.toString().trim())
            .putInt(KEY_TEMPLATE, template)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    companion object {

        /**
         * The design a card starts on, if none was ever chosen.
         *
         * The first one in the picker, not the plainest. A shop opening this
         * screen for the first time should find the strongest design already
         * selected, not have to hunt for it past eleven others.
         */
        /** Editorial. An id saved from a removed design also lands here. */
        private const val TPL_DEFAULT = 101

        private const val PREFS = "biz_card"
        private const val KEY_NAME = "name"
        private const val KEY_TYPE = "type"
        private const val KEY_OWNER = "owner"
        private const val KEY_PHONE = "phone"
        private const val KEY_ADDRESS = "address"
        private const val KEY_WHATSAPP = "whatsapp"
        private const val KEY_WA_SAME = "whatsapp_same"
        private const val KEY_EMAIL = "email"
        private const val KEY_WEB = "web"
        private const val KEY_TAGLINE = "tagline"
        private const val KEY_QR_MESSAGE = "qr_message"
        private const val KEY_TEMPLATE = "template"
        private const val KEY_MARK = "show_mark"
        private const val KEY_LOGO = "show_logo"
        private const val KEY_LANG = "card_lang"

        private val INK = Color.parseColor("#1A1A18")
        private val BRAND_GREEN = Color.parseColor("#1B5E3A")
    }
}
