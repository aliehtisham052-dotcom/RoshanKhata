package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
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
import com.innovation313.roshankhata.ui.PosterDesigns
import com.innovation313.roshankhata.ui.PosterImage
import com.innovation313.roshankhata.ui.PosterKit
import com.innovation313.roshankhata.ui.PosterMotionView
import com.innovation313.roshankhata.ui.poster3d.Hero
import com.innovation313.roshankhata.ui.poster3d.HeroStage
import com.innovation313.roshankhata.ui.poster3d.SealFace
import com.innovation313.roshankhata.ui.poster3d.Studio
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
    private lateinit var frame: FrameLayout
    private lateinit var heroView: TextureView
    private lateinit var motionView: PosterMotionView

    /** The live 3D hero, while the chosen design has one and the phone can draw it. */
    private var stage: HeroStage? = null
    /** Which hero [stage] shows, and whether its face carries the logo, to know when to rebuild it. */
    private var stageHero: Hero? = null
    private var stageHadLogo = false

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
        frame = findViewById(R.id.posterFrame)
        heroView = findViewById(R.id.heroView)
        motionView = findViewById(R.id.motionView)
        frame.post { placeHero() }

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
            updateHero()
            fill()
        }

        updateHero()
        fill()
        etTitle.doAfterTextChanged { if (!filling) { save(KEY_TITLE, it?.toString(), occasion.title); render() } }
        etMessage.doAfterTextChanged { if (!filling) { save(KEY_MSG, it?.toString(), occasion.message); render() } }
        markSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(KEY_MARK, on).apply(); render() }
        logoSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(KEY_LOGO, on).apply(); updateHero(); render() }
        share.setOnClickListener { sharePoster() }

        lifecycleScope.launch {
            logo = withContext(Dispatchers.IO) { BusinessProfile.loadLogo(this@PosterActivity) }
            updateHero()
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
        shopName = shopName(),
        phone = BusinessProfile.businessPhone(this)?.trim(),
        address = BusinessProfile.businessAddress(this)?.trim(),
        logo = shownLogo(),
        mark = if (markSwitch.isChecked) getString(R.string.made_with_app) else null,
        heroOverlay = stage != null
    )

    /** The shop's name as this language's poster prints it: in Urdu script on an Urdu poster, if the owner gave one. */
    private fun shopName(): String {
        val locale = resources.configuration.locales[0]
        return BusinessProfile.nameFor(this, locale != null && PosterKit.urduScript(locale))?.trim().orEmpty()
    }

    private fun shownLogo() = if (logoSwitch.isChecked) logo else null

    /** Lay the 3D view over the hero's place on the picture, at the frame's scale. */
    private fun placeHero() {
        val s = frame.width / PosterImage.WIDTH.toFloat()
        if (s <= 0f) return
        val r = (stageHero ?: return).rect
        heroView.layoutParams = FrameLayout.LayoutParams((r.width() * s).toInt(), (r.height() * s).toInt()).apply {
            leftMargin = (r.left * s).toInt()
            topMargin = (r.top * s).toInt()
        }
    }

    /**
     * Start, swap or stop the live hero for the chosen design; a phone without
     * 3D keeps the flat drawing. A hero with a face (the Thank-you seal) is
     * rebuilt when the logo arrives or is switched, so the seal shows the mark.
     */
    private fun updateHero() {
        val hero = PosterDesigns.hero(occasion)?.takeIf { Studio.available(this) }
        val withLogo = shownLogo() != null
        val stale = stage != null && (stageHero != hero || (hero?.face != null && stageHadLogo != withLogo))
        if (stale || (hero == null && stage != null)) {
            stage?.destroy()
            stage = null
            stageHero = null
        }
        if (hero != null && stage == null) {
            stage = try {
                val face = hero.face?.let { SealFace.draw(this, shopName(), shownLogo()) }
                HeroStage(this, hero, face = face).also {
                    face?.recycle()
                    stageHero = hero
                    stageHadLogo = withLogo
                    if (isStarted) it.attach(heroView)
                }
            } catch (e: Exception) {
                null
            }
        }
        heroView.visibility = if (stage != null) View.VISIBLE else View.GONE
        placeHero()
    }

    private var isStarted = false

    /** Redraw after the owner stops typing; the old picture is let go only once the new one is up. */
    private fun render() {
        renderJob?.cancel()
        val inp = input()
        renderJob = lifecycleScope.launch {
            delay(150)
            val (bmp, fit) = withContext(Dispatchers.Default) { PosterImage.draw(this@PosterActivity, inp) }
            val old = shown
            preview.setImageBitmap(bmp)
            motionView.show(fit.motion)
            shown = bmp
            old?.recycle()
        }
    }

    private fun sharePoster() {
        share.isEnabled = false
        lifecycleScope.launch {
            // The shared picture carries the hero itself, drawn still off-screen at full size.
            val hero = stage?.let { st ->
                try {
                    val r = stageHero!!.rect
                    st.still(r.width().toInt(), r.height().toInt())
                } catch (e: Exception) {
                    null
                }
            }
            val inp = input().copy(hero = hero, heroOverlay = false)
            val file = withContext(Dispatchers.Default) {
                try {
                    val dir = File(cacheDir, "posters").apply { mkdirs() }
                    val f = File(dir, "poster.png")
                    val bmp = PosterImage.draw(this@PosterActivity, inp).first
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    hero?.recycle()
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

    override fun onStart() {
        super.onStart()
        isStarted = true
        stage?.attach(heroView)
    }

    override fun onStop() {
        isStarted = false
        stage?.detach()
        super.onStop()
    }

    override fun onDestroy() {
        stage?.destroy()
        stage = null
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
