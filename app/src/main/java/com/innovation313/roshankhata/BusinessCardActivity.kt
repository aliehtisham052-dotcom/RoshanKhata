package com.innovation313.roshankhata

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.innovation313.roshankhata.data.BusinessProfile
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
class BusinessCardActivity : AppCompatActivity() {

    private lateinit var preview: ImageView
    private lateinit var etBizName: EditText
    private lateinit var etType: EditText
    private lateinit var etOwner: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
    private lateinit var tplButtons: List<Button>

    private var template = TPL_CLASSIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_card)

        preview = findViewById(R.id.ivCardPreview)
        etBizName = findViewById(R.id.etBizName)
        etType = findViewById(R.id.etType)
        etOwner = findViewById(R.id.etOwner)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)

        tplButtons = listOf(
            findViewById(R.id.btnTplClassic),
            findViewById(R.id.btnTplGold),
            findViewById(R.id.btnTplGreen)
        )
        tplButtons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                template = index
                refreshTemplateButtons()
                render()
            }
        }

        // Prefill from what the app already knows, then whatever was last typed
        // here. The business name is shared with statements via BusinessProfile.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        etBizName.setText(BusinessProfile.businessName(this) ?: "")
        etType.setText(prefs.getString(KEY_TYPE, ""))
        etOwner.setText(prefs.getString(KEY_OWNER, ""))
        etPhone.setText(prefs.getString(KEY_PHONE, ""))
        etAddress.setText(prefs.getString(KEY_ADDRESS, ""))
        template = prefs.getInt(KEY_TEMPLATE, TPL_CLASSIC)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = render()
        }
        listOf(etBizName, etType, etOwner, etPhone, etAddress)
            .forEach { it.addTextChangedListener(watcher) }

        findViewById<Button>(R.id.btnShareCard).apply {
            backgroundTintList = ColorStateList.valueOf(BRAND_GREEN)
            setTextColor(Color.WHITE)
            setOnClickListener { shareCard() }
        }

        refreshTemplateButtons()
        render()
    }

    /** Selected template shows in brand green; the rest stay quiet. */
    private fun refreshTemplateButtons() {
        tplButtons.forEachIndexed { index, btn ->
            val selected = index == template
            btn.backgroundTintList =
                ColorStateList.valueOf(if (selected) BRAND_GREEN else Color.WHITE)
            btn.setTextColor(if (selected) Color.WHITE else INK)
        }
    }

    // ---------- Drawing ----------

    private fun drawCard(): Bitmap {
        val bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val bg: Int; val nameCol: Int; val textCol: Int; val accent: Int
        when (template) {
            TPL_GOLD -> { bg = INK; nameCol = GOLD_BRIGHT; textCol = Color.WHITE; accent = GOLD_BRIGHT }
            TPL_GREEN -> { bg = BRAND_GREEN; nameCol = Color.WHITE; textCol = Color.WHITE; accent = GOLD_PALE }
            else -> { bg = PAPER; nameCol = INK; textCol = INK; accent = BRAND_GREEN }
        }

        c.drawColor(bg)
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        c.drawRect(0f, 0f, CARD_W.toFloat(), 26f, band)
        c.drawRect(0f, (CARD_H - 26).toFloat(), CARD_W.toFloat(), CARD_H.toFloat(), band)

        val cx = CARD_W / 2f
        var y = 170f

        fun paint(size: Float, color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

        val name = etBizName.text.toString().trim()
            .ifEmpty { getString(R.string.biz_card_name_hint) }
        c.drawText(name, cx, y, paint(86f, nameCol, true))
        y += 66f

        val type = etType.text.toString().trim()
        if (type.isNotEmpty()) {
            c.drawText(type, cx, y, paint(44f, textCol, false)); y += 46f
        }

        y += 18f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = 4f }
        c.drawLine(cx - 200f, y, cx + 200f, y, line)
        y += 74f

        val owner = etOwner.text.toString().trim()
        if (owner.isNotEmpty()) {
            c.drawText(owner, cx, y, paint(52f, textCol, true)); y += 58f
        }
        val phone = etPhone.text.toString().trim()
        if (phone.isNotEmpty()) {
            c.drawText(phone, cx, y, paint(56f, textCol, true)); y += 60f
        }
        val address = etAddress.text.toString().trim()
        if (address.isNotEmpty()) {
            c.drawText(address, cx, y, paint(42f, textCol, false)); y += 48f
        }

        c.drawText(
            getString(R.string.powered_by), cx, (CARD_H - 58).toFloat(),
            paint(32f, accent, false)
        )
        return bmp
    }

    private fun render() {
        preview.setImageBitmap(drawCard())
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
        BusinessProfile.setBusinessName(this, etBizName.text.toString())
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_TYPE, etType.text.toString().trim())
            .putString(KEY_OWNER, etOwner.text.toString().trim())
            .putString(KEY_PHONE, etPhone.text.toString().trim())
            .putString(KEY_ADDRESS, etAddress.text.toString().trim())
            .putInt(KEY_TEMPLATE, template)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    companion object {
        private const val CARD_W = 1200
        private const val CARD_H = 700

        private const val TPL_CLASSIC = 0
        private const val TPL_GOLD = 1
        private const val TPL_GREEN = 2

        private const val PREFS = "biz_card"
        private const val KEY_TYPE = "type"
        private const val KEY_OWNER = "owner"
        private const val KEY_PHONE = "phone"
        private const val KEY_ADDRESS = "address"
        private const val KEY_TEMPLATE = "template"

        private val INK = Color.parseColor("#1A1A18")
        private val PAPER = Color.parseColor("#F7F6F2")
        private val BRAND_GREEN = Color.parseColor("#1B5E3A")
        private val GOLD_BRIGHT = Color.parseColor("#D9B44A")
        private val GOLD_PALE = Color.parseColor("#F2D27C")
    }
}
