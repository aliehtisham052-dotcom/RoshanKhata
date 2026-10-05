package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.ui.PosterImage
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Poster & Status — a 9:16 picture for WhatsApp Status with the shop's name,
 * phone and logo: new stock, Eid, Ramazan, closed today, timings, thanks, or
 * the owner's own words.
 *
 * Reads Profile, writes nothing to the ledger, so it opens on the helper's
 * phone too. The owner's own wording for an occasion (his timings, say) is
 * remembered per shop; the app's default wording is not stored, so it
 * follows the app language.
 */
class PosterActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val shopKey by lazy { Businesses.active(this).id.toString() }

    private lateinit var preview: ImageView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var logoSwitch: SwitchMaterial
    private lateinit var markSwitch: SwitchMaterial
    private lateinit var share: MaterialButton

    private var occasion = PosterOccasion.NEW_STOCK
    private var logo: Bitmap? = null
    private var shown: Bitmap? = null
    private var renderJob: Job? = null
    /** True while the fields are being filled in code, so that is not saved as the owner's wording. */
    private var filling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poster)
        ScreenInsets.on(this)

        preview = findViewById(R.id.ivPoster)
        etTitle = findViewById(R.id.etPosterTitle)
        etMessage = findViewById(R.id.etPosterMessage)
        logoSwitch = findViewById(R.id.switchPosterLogo)
        markSwitch = findViewById(R.id.switchPosterMark)
        share = findViewById(R.id.btnSharePoster)

        occasion = PosterOccasion.byKey(prefs.getString(KEY_OCCASION, null))
        markSwitch.isChecked = prefs.getBoolean(KEY_MARK, true)
        logoSwitch.isChecked = prefs.getBoolean(KEY_LOGO, true)
        logoSwitch.visibility = if (BusinessProfile.hasLogo(this)) View.VISIBLE else View.GONE

        val group = findViewById<ChipGroup>(R.id.chipOccasions)
        PosterOccasion.values().forEach { o ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = if (o == PosterOccasion.CUSTOM) getString(R.string.poster_custom) else getString(o.title)
            chip.tag = o.key
            chip.isChecked = o == occasion
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val chip = ids.firstOrNull()?.let { g.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            occasion = PosterOccasion.byKey(chip.tag as String)
            prefs.edit().putString(KEY_OCCASION, occasion.key).apply()
            fill()
        }

        fill()
        etTitle.doAfterTextChanged { if (!filling) { save(KEY_TITLE, it?.toString(), occasion.title); render() } }
        etMessage.doAfterTextChanged { if (!filling) { save(KEY_MSG, it?.toString(), occasion.message); render() } }
        markSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(KEY_MARK, on).apply(); render() }
        logoSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(KEY_LOGO, on).apply(); render() }
        share.setOnClickListener { sharePoster() }

        lifecycleScope.launch {
            logo = withContext(Dispatchers.IO) { BusinessProfile.loadLogo(this@PosterActivity) }
            render()
        }
    }

    private fun key(kind: String) = "${kind}${occasion.key}_$shopKey"

    /** The owner's wording if he changed it, else the occasion's own in the app language. */
    private fun fill() {
        filling = true
        etTitle.setText(prefs.getString(key(KEY_TITLE), null) ?: occasion.title.takeIf { it != 0 }?.let { getString(it) } ?: "")
        etMessage.setText(prefs.getString(key(KEY_MSG), null) ?: occasion.message.takeIf { it != 0 }?.let { getString(it) } ?: "")
        filling = false
        render()
    }

    private fun save(kind: String, text: String?, default: Int) {
        val t = text?.trim().orEmpty()
        val e = prefs.edit()
        if (default != 0 && t == getString(default)) e.remove(key(kind)) else e.putString(key(kind), t)
        e.apply()
    }

    private fun input() = PosterImage.Input(
        occasion = occasion,
        title = etTitle.text?.toString()?.trim().orEmpty(),
        message = etMessage.text?.toString()?.trim().orEmpty(),
        shopName = BusinessProfile.businessName(this)?.trim().orEmpty(),
        phone = BusinessProfile.businessPhone(this)?.trim(),
        address = BusinessProfile.businessAddress(this)?.trim(),
        logo = if (logoSwitch.isChecked) logo else null,
        mark = if (markSwitch.isChecked) getString(R.string.made_with_app) else null
    )

    /** Redraw after the owner stops typing; the old picture is let go only once the new one is up. */
    private fun render() {
        renderJob?.cancel()
        val inp = input()
        renderJob = lifecycleScope.launch {
            delay(150)
            val bmp = withContext(Dispatchers.Default) { PosterImage.draw(this@PosterActivity, inp).first }
            val old = shown
            preview.setImageBitmap(bmp)
            shown = bmp
            old?.recycle()
        }
    }

    private fun sharePoster() {
        val inp = input()
        share.isEnabled = false
        lifecycleScope.launch {
            val file = withContext(Dispatchers.Default) {
                try {
                    val dir = File(cacheDir, "posters").apply { mkdirs() }
                    val f = File(dir, "poster.png")
                    val bmp = PosterImage.draw(this@PosterActivity, inp).first
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    f
                } catch (e: Exception) {
                    null
                }
            }
            share.isEnabled = true
            if (file == null) {
                Toast.makeText(this@PosterActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val uri = FileProvider.getUriForFile(this@PosterActivity, "$packageName.fileprovider", file)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, getString(R.string.share)))
            } catch (e: Exception) {
                Toast.makeText(this@PosterActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preview.setImageDrawable(null)
        shown?.recycle()
        shown = null
    }

    companion object {
        private const val PREFS = "poster"
        private const val KEY_OCCASION = "occasion"
        private const val KEY_MARK = "mark"
        private const val KEY_LOGO = "logo"
        private const val KEY_TITLE = "title_"
        private const val KEY_MSG = "msg_"
    }
}
