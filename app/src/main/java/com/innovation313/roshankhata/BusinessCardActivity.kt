package com.innovation313.roshankhata

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.ui.CardPreviewView
import com.innovation313.roshankhata.ui.CardTemplates
import java.io.File
import java.io.FileOutputStream

/**
 * The Dukan Card: a visiting card for the shop, drawn entirely on the phone
 * and shared as an image. The competitor offers decorated template cards; ours
 * stay in the brand's own colours and, because every line of text is drawn
 * centred, the same composition reads correctly in Urdu, Sindhi, Arabic and
 * Persian as in English — no mirrored-layout bugs to chase.
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
        etBizName.setText(BusinessProfile.businessName(this) ?: "")
        etType.setText(prefs.getString(KEY_TYPE, ""))
        etOwner.setText(prefs.getString(KEY_OWNER, ""))
        etPhone.setText(prefs.getString(KEY_PHONE, ""))
        etAddress.setText(prefs.getString(KEY_ADDRESS, ""))
        etWhatsapp.setText(prefs.getString(KEY_WHATSAPP, ""))
        etEmail.setText(prefs.getString(KEY_EMAIL, ""))
        etWeb.setText(prefs.getString(KEY_WEB, ""))
        etTagline.setText(prefs.getString(KEY_TAGLINE, ""))
        // Pre-filled until the owner changes it; an emptied box stays empty.
        etQrMessage.setText(prefs.getString(KEY_QR_MESSAGE, getString(R.string.biz_card_qr_message_default)))
        // A design that was removed (ids 0-7) resolves to the first card, and the
        // picker highlights that card rather than nothing.
        template = CardTemplates.byId(prefs.getInt(KEY_TEMPLATE, TPL_DEFAULT)).id
        // On unless the owner has said otherwise.
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
            override fun afterTextChanged(s: Editable?) = render()
        }
        listOf(etBizName, etType, etOwner, etPhone, etAddress, etWhatsapp, etEmail, etWeb, etTagline, etQrMessage)
            .forEach { it.addTextChangedListener(watcher) }

        findViewById<Button>(R.id.btnShareCard).apply {
            backgroundTintList = ColorStateList.valueOf(BRAND_GREEN)
            setTextColor(Color.WHITE)
            setOnClickListener { shareCard() }
        }
        findViewById<Button>(R.id.btnCardPdf).setOnClickListener { sharePdf() }

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
            .ifEmpty { getString(R.string.biz_card_name_hint) },
        type = etType.text.toString().trim(),
        owner = etOwner.text.toString().trim(),
        phone = etPhone.text.toString().trim(),
        address = etAddress.text.toString().trim(),
        // Empty when the owner has switched the mark off — CardTemplates
        // draws nothing for an empty footer, so no template needs to know
        // about the setting.
        footer = if (markSwitch.isChecked) getString(R.string.made_with_app) else "",
        whatsapp = etWhatsapp.text.toString().trim(),
        email = etEmail.text.toString().trim(),
        web = etWeb.text.toString().trim(),
        tagline = etTagline.text.toString().trim(),
        qrMessage = etQrMessage.text.toString().trim(),
        logo = if (logoSwitch.isChecked) logo else null
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

    /** The card as a print-ready PDF (3.5 x 2 in + bleed), for a print shop. */
    private fun sharePdf() {
        if (etBizName.text.toString().isBlank()) {
            Toast.makeText(this, R.string.biz_card_enter_name, Toast.LENGTH_SHORT).show()
            return
        }
        save()
        try {
            val file = com.innovation313.roshankhata.data.CardPdf.write(
                this, CardTemplates.byId(template), cardData()
            )
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getString(R.string.biz_card_print_pdf)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun save() {
        BusinessProfile.setBusinessName(this, etBizName.text.toString())
        getSharedPreferences(PREFS + Businesses.suffix(this), MODE_PRIVATE).edit()
            .putString(KEY_TYPE, etType.text.toString().trim())
            .putString(KEY_OWNER, etOwner.text.toString().trim())
            .putString(KEY_PHONE, etPhone.text.toString().trim())
            .putString(KEY_ADDRESS, etAddress.text.toString().trim())
            .putString(KEY_WHATSAPP, etWhatsapp.text.toString().trim())
            .putString(KEY_EMAIL, etEmail.text.toString().trim())
            .putString(KEY_WEB, etWeb.text.toString().trim())
            .putString(KEY_TAGLINE, etTagline.text.toString().trim())
            .putString(KEY_QR_MESSAGE, etQrMessage.text.toString().trim())
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
        private const val KEY_TYPE = "type"
        private const val KEY_OWNER = "owner"
        private const val KEY_PHONE = "phone"
        private const val KEY_ADDRESS = "address"
        private const val KEY_WHATSAPP = "whatsapp"
        private const val KEY_EMAIL = "email"
        private const val KEY_WEB = "web"
        private const val KEY_TAGLINE = "tagline"
        private const val KEY_QR_MESSAGE = "qr_message"
        private const val KEY_TEMPLATE = "template"
        private const val KEY_MARK = "show_mark"
        private const val KEY_LOGO = "show_logo"

        private val INK = Color.parseColor("#1A1A18")
        private val BRAND_GREEN = Color.parseColor("#1B5E3A")
    }
}
