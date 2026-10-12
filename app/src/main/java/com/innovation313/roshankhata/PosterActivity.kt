package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.TextureView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.PosterFields
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.data.TradeVocab
import com.innovation313.roshankhata.ui.LocaleRefresh
import com.innovation313.roshankhata.ui.PosterDesigns
import com.innovation313.roshankhata.ui.PosterImage
import com.innovation313.roshankhata.ui.PosterKit
import com.innovation313.roshankhata.ui.PosterMotionView
import com.innovation313.roshankhata.ui.PosterVideo
import com.innovation313.roshankhata.ui.PosterWords
import com.innovation313.roshankhata.ui.ScreenInsets
import com.innovation313.roshankhata.ui.TextFit
import com.innovation313.roshankhata.ui.poster3d.CoinFace
import com.innovation313.roshankhata.ui.poster3d.Hero
import com.innovation313.roshankhata.ui.poster3d.HeroStage
import com.innovation313.roshankhata.ui.poster3d.SealFace
import com.innovation313.roshankhata.ui.poster3d.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Poster & Status (12 Oct, the approved screen mockup).
 *
 * Choose a design from its picture and the poster's language (which may
 * differ from the app's: an English-speaking helper can make an Urdu poster);
 * the preview is the real picture, its 3D hero moving live; under it go the
 * big line, this design's own fields (what arrived, why closed and when it
 * opens, the timings, the Ramazan hours, the Eid holidays) and the last line;
 * then what the poster shows. A fixed bar puts it on WhatsApp Status (the
 * share goes to WhatsApp, where "My status" is the first choice) or shares it
 * anywhere.
 *
 * Reads Profile, writes nothing to the ledger, so it opens on the helper's
 * phone too. Everything chosen here is remembered per shop (PosterFields,
 * and the owner's own wording per design and language).
 */
class PosterActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val shopKey by lazy { Businesses.active(this).id.toString() }

    private lateinit var preview: ImageView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var tilMessage: TextInputLayout
    private lateinit var frame: FrameLayout
    private lateinit var heroView: TextureView
    private lateinit var motionView: PosterMotionView
    private lateinit var designRow: LinearLayout
    private lateinit var showGrid: GridLayout
    private lateinit var itemsList: LinearLayout
    private lateinit var suggestGrid: GridLayout
    private lateinit var chipReasons: ChipGroup
    private lateinit var chipHolidays: ChipGroup
    private lateinit var chipEidDays: ChipGroup
    private lateinit var chipOfferPercent: ChipGroup
    private lateinit var chipOfferDays: ChipGroup
    private lateinit var etItem: EditText
    private lateinit var etReason: TextInputEditText
    private lateinit var tilReason: TextInputLayout
    private lateinit var btnPhoto: View
    private lateinit var btnStatus: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var noPhoneNote: View
    private lateinit var toggleSendAs: MaterialButtonToggleGroup
    private lateinit var toggleLang: MaterialButtonToggleGroup
    private lateinit var etShopName: TextInputEditText
    private lateinit var tilShopName: TextInputLayout
    private lateinit var previewHost: LinearLayout
    private lateinit var btnStepBack: MaterialButton
    private lateinit var btnStepNext: MaterialButton

    /** 1 Design, 2 Words, 3 Send: the step on screen. */
    private var step = 1

    // The designs as sliding pages (Design step). Each page is a finished still;
    // the live poster lies over the page in view and is hidden while one slides.
    private lateinit var pager: androidx.viewpager2.widget.ViewPager2
    private lateinit var stageBox: FrameLayout
    private val pageAdapter = PageAdapter()
    /** Finished pages at the stage's size, a few at a time (each ~2 MB). */
    private val pages = object : android.util.LruCache<PosterOccasion, Bitmap>(5) {}
    private val pageJobs = mutableMapOf<PosterOccasion, Job>()
    /** The live poster as it last looked, standing in for its page while it slides away. */
    private var leaving: Pair<PosterOccasion, Bitmap>? = null
    private var pageW = 0
    private var pageH = 0
    /** The live picture for the chosen design is drawn, and the pages are at rest: the live layer may show. */
    private var liveDrawn = false
    private var pagerIdle = true
    /** Something on every page changed (the name, a choice) since the pages were drawn. */
    private var pagesDirty = false
    /** True while the language switch is being set in code, so it is not taken as the owner's choice. */
    private var syncingLang = false
    private var shopNameJob: Job? = null
    /** Send a moving video (the default: the motion is the point) or the still picture. */
    private var asVideo = true
    private var videoJob: Job? = null
    /** True when the owner cancelled the last video, so nothing is sent in its place. */
    private var videoCancelled = false

    /** The live 3D hero, while the chosen design has one and the phone can draw it. */
    private var stage: HeroStage? = null
    /** What [stage] was built for, to know when it must be rebuilt (another hero, or the seal's mark changed). */
    private var stageKey: String? = null
    private var stageHero: Hero? = null
    private var isStarted = false

    private var occasion = PosterOccasion.NEW_STOCK
    private var lang = "ur"
    private var fields = PosterFields()
    private var logo: Bitmap? = null
    private var photo: Bitmap? = null
    private var shown: Bitmap? = null
    private var renderJob: Job? = null
    private var thumbsJob: Job? = null
    private val thumbs = mutableMapOf<PosterOccasion, Bitmap>()
    /** True while the fields are being filled in code, so that is not saved as the owner's wording. */
    private var filling = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) takePhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_poster)
        ScreenInsets.on(this)

        preview = findViewById(R.id.ivPoster)
        etTitle = findViewById(R.id.etPosterTitle)
        etMessage = findViewById(R.id.etPosterMessage)
        tilMessage = findViewById(R.id.tilMessage)
        frame = findViewById(R.id.posterFrame)
        heroView = findViewById(R.id.heroView)
        motionView = findViewById(R.id.motionView)
        designRow = findViewById(R.id.designRow)
        showGrid = findViewById(R.id.showGrid)
        itemsList = findViewById(R.id.itemsList)
        suggestGrid = findViewById(R.id.suggestGrid)
        chipReasons = findViewById(R.id.chipReasons)
        chipHolidays = findViewById(R.id.chipHolidays)
        chipEidDays = findViewById(R.id.chipEidDays)
        chipOfferPercent = findViewById(R.id.chipOfferPercent)
        chipOfferDays = findViewById(R.id.chipOfferDays)
        etItem = findViewById(R.id.etItem)
        etReason = findViewById(R.id.etReason)
        tilReason = findViewById(R.id.tilReason)
        btnPhoto = findViewById(R.id.btnPhoto)
        btnStatus = findViewById(R.id.btnStatus)
        btnShare = findViewById(R.id.btnSharePoster)
        noPhoneNote = findViewById(R.id.noPhoneNote)
        toggleSendAs = findViewById(R.id.toggleSendAs)
        toggleLang = findViewById(R.id.toggleLang)
        etShopName = findViewById(R.id.etShopName)
        tilShopName = findViewById(R.id.tilShopName)
        previewHost = findViewById(R.id.previewHost)
        btnStepBack = findViewById(R.id.btnStepBack)
        btnStepNext = findViewById(R.id.btnStepNext)
        pager = findViewById(R.id.designPager)
        stageBox = findViewById(R.id.previewStage)
        frame.post { placeHero() }

        occasion = PosterOccasion.byKey(prefs.getString(KEY_OCCASION, null))
        lang = prefs.getString(KEY_LANG + shopKey, null)?.takeIf { it in LANGS } ?: defaultLanguage()
        fields = PosterFields.load(this, shopKey)

        buildDesignRow()
        buildLanguageToggle()
        buildShowGrid()
        buildStockFields()
        buildClosedFields()
        buildHoursFields()
        buildRamazanFields()
        buildEidFields()
        buildOfferFields()
        asVideo = prefs.getBoolean(KEY_VIDEO, true)
        buildSendAs()

        etTitle.doAfterTextChanged {
            if (!filling) { save(KEY_TITLE, it?.toString(), occasion.title); render() }
            if (!it.isNullOrBlank()) findViewById<TextInputLayout>(R.id.tilTitle).error = null
        }
        etMessage.doAfterTextChanged { if (!filling) { save(KEY_MSG, it?.toString(), occasion.message); render() } }
        buildShopName()
        buildSteps()
        findViewById<View>(R.id.btnTitleOriginal).setOnClickListener {
            if (occasion.title != 0) etTitle.setText(posterContext().getString(occasion.title))
        }
        findViewById<View>(R.id.btnMessageOriginal).setOnClickListener {
            if (occasion.message != 0) etMessage.setText(posterContext().getString(occasion.message))
        }
        btnStatus.setOnClickListener { sharePoster(toWhatsApp = true) }
        btnShare.setOnClickListener { sharePoster(toWhatsApp = false) }
        findViewById<MaterialButton>(R.id.btnAddPhone).setOnClickListener {
            startActivity(Intent(this, BusinessSettingsActivity::class.java)
                .putExtra(BusinessSettingsActivity.EXTRA_FOCUS, BusinessSettingsActivity.FOCUS_PHONE))
        }

        showFieldsFor()
        fill()
        showStep(1)
        lifecycleScope.launch {
            logo = withContext(Dispatchers.IO) { BusinessProfile.loadLogo(this@PosterActivity) }
            photo = withContext(Dispatchers.IO) { loadPhoto() }
            updateHero()
            render()
            renderThumbs()
        }
    }

    override fun onResume() {
        super.onResume()
        // The profile may have changed (a phone number added, a name, a logo).
        noPhoneNote.visibility = if (BusinessProfile.businessPhone(this).isNullOrBlank()) View.VISIBLE else View.GONE
        if (shown != null) {
            lifecycleScope.launch {
                logo = withContext(Dispatchers.IO) { BusinessProfile.loadLogo(this@PosterActivity) }
                buildShowGrid()
                updateHero()
                render()
            }
        }
    }

    // ------------------------------------------------------------------ language

    /** The app's language when it is one a poster speaks, else English. */
    private fun defaultLanguage(): String {
        val app = LocaleRefresh.tag(this)
        return LANGS.firstOrNull { it.equals(app, ignoreCase = true) }
            ?: LANGS.firstOrNull { Locale.forLanguageTag(it).language == Locale.forLanguageTag(app).language && !it.contains("-") }
            ?: "en"
    }

    /** A context speaking the poster's language: its words, its direction, its defaults. */
    private fun posterContext(): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        return createConfigurationContext(config)
    }

    private fun posterUrdu() = PosterKit.urduScript(Locale.forLanguageTag(lang))

    /** Urdu | English | Other ▾ (12 Oct): one control on one row; Other opens the full list. */
    private fun buildLanguageToggle() {
        val ur = findViewById<MaterialButton>(R.id.btnLangUr)
        val en = findViewById<MaterialButton>(R.id.btnLangEn)
        val other = findViewById<MaterialButton>(R.id.btnLangOther)
        ur.text = NAMES.getValue("ur")
        en.text = NAMES.getValue("en")
        if (toggleLang.tag == null) {
            toggleLang.tag = true
            toggleLang.addOnButtonCheckedListener { _, id, checked ->
                if (syncingLang || !checked) return@addOnButtonCheckedListener
                when (id) {
                    R.id.btnLangUr -> setLanguage("ur")
                    R.id.btnLangEn -> setLanguage("en")
                    R.id.btnLangOther -> showLanguageSheet()
                }
            }
            // Already showing another language: a second tap still opens the list.
            other.setOnClickListener { if (lang != "ur" && lang != "en") showLanguageSheet() }
        }
        syncingLang = true
        other.text = if (lang == "ur" || lang == "en") getString(R.string.poster_lang_other) else NAMES.getValue(lang)
        toggleLang.check(when (lang) { "ur" -> R.id.btnLangUr; "en" -> R.id.btnLangEn; else -> R.id.btnLangOther })
        syncingLang = false
        TextFit.relax(toggleLang)
    }

    /** Every language a poster speaks, the usual three first, each in its own script. */
    private fun showLanguageSheet() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_poster_language, null)
        val appLocale = resources.configuration.locales[0]
        var chosen = false
        fun addRows(list: LinearLayout, tags: List<String>) {
            for (tag in tags) {
                val row = layoutInflater.inflate(R.layout.item_poster_language, list, false)
                row.findViewById<TextView>(R.id.langName).apply {
                    text = NAMES.getValue(tag)
                    textDirection = if (tag in RTL_TAGS) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
                }
                val local = Locale.forLanguageTag(tag).getDisplayName(appLocale)
                row.findViewById<TextView>(R.id.langLocal).text = if (local.equals(NAMES.getValue(tag), true)) "" else local
                row.findViewById<android.widget.RadioButton>(R.id.langRadio).isChecked = tag == lang
                row.isSelected = tag == lang
                row.setOnClickListener { chosen = true; sheet.dismiss(); setLanguage(tag) }
                list.addView(row)
            }
        }
        addRows(view.findViewById(R.id.langMost), MOST_USED)
        addRows(view.findViewById(R.id.langMore), LANGS.filter { it !in MOST_USED })
        TextFit.relax(view)
        sheet.setContentView(view)
        // Closed without a choice: the switch shows the language still in use.
        sheet.setOnDismissListener { if (!chosen) buildLanguageToggle() }
        sheet.show()
    }

    private fun setLanguage(tag: String) {
        if (tag == lang) { buildLanguageToggle(); return }
        lang = tag
        prefs.edit().putString(KEY_LANG + shopKey, tag).apply()
        buildLanguageToggle()
        buildShopName()
        buildStockFields()
        updateHero()
        fill()
        renderThumbs()
    }

    // ------------------------------------------------------------------ designs

    private fun buildDesignRow() {
        designRow.removeAllViews()
        for (o in PosterOccasion.values()) {
            val item = layoutInflater.inflate(R.layout.item_poster_design, designRow, false)
            item.tag = o.key
            item.findViewById<TextView>(R.id.designName).text =
                if (o == PosterOccasion.CUSTOM) getString(R.string.poster_custom) else designName(o)
            item.contentDescription = item.findViewById<TextView>(R.id.designName).text
            item.setOnClickListener { chooseDesign(o) }
            designRow.addView(item)
        }
        TextFit.relax(designRow)
        markDesign()
    }

    /** The design's short name in the app's language. */
    private fun designName(o: PosterOccasion): String = getString(o.title).trimEnd('!', '!', '۔').let {
        if (it.length > 22) it.take(20) + "…" else it
    }

    private fun markDesign() {
        for (i in 0 until designRow.childCount) {
            val item = designRow.getChildAt(i)
            val on = item.tag == occasion.key
            item.findViewById<MaterialCardView>(R.id.designCard).apply {
                strokeWidth = (if (on) 3 else 1) * resources.displayMetrics.density.toInt().coerceAtLeast(1)
                strokeColor = getColor(if (on) R.color.brand_gold else R.color.hairline)
            }
            item.findViewById<View>(R.id.designTick).visibility = if (on) View.VISIBLE else View.GONE
            item.isSelected = on
        }
    }

    /**
     * Another design, from the strip, the arrows or a slide of the pages. The
     * live layer goes out at once (the leaving design stands on its page as it
     * last looked), the pages slide, and the new live poster shows only when it
     * is drawn and the pages are at rest: nothing of one design shows through
     * the next.
     */
    private fun chooseDesign(o: PosterOccasion, fromPager: Boolean = false) {
        if (o == occasion) return
        if (step == 1) hideLive()
        occasion = o
        prefs.edit().putString(KEY_OCCASION, o.key).apply()
        markDesign()
        showFieldsFor()
        updateHero()
        fill()
        updateDesignInfo()
        val i = PosterOccasion.values().indexOf(o)
        if (!fromPager && pager.currentItem != i) pager.setCurrentItem(i, true)
    }

    /** Take the live layer off the stage; the leaving design's page shows it as it last looked. */
    private fun hideLive() {
        if (frame.visibility == View.VISIBLE) {
            snapshotLive()?.let { shot ->
                // Not recycled: a page may still be showing the last one; it is let go to the collector.
                leaving = occasion to shot
                pageAdapter.notifyItemChanged(PosterOccasion.values().indexOf(occasion))
            }
        }
        frame.visibility = View.INVISIBLE
        liveDrawn = false
    }

    /** The live poster now, hero and all, at the stage's size. */
    private fun snapshotLive(): Bitmap? {
        val base = shown?.takeIf { !it.isRecycled } ?: return null
        val w = frame.width
        val h = frame.height
        if (w <= 0 || h <= 0) return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawBitmap(base, null, android.graphics.Rect(0, 0, w, h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        if (heroView.visibility == View.VISIBLE && heroView.isAvailable) {
            heroView.bitmap?.let { hb -> c.drawBitmap(hb, heroView.left.toFloat(), heroView.top.toFloat(), null); hb.recycle() }
        }
        return out
    }

    /** Show the live layer once its picture is drawn and, on Design, the pages are at rest on it. */
    private fun maybeShowLive() {
        if (!liveDrawn) return
        if (step == 1 && (!pagerIdle || pager.currentItem != PosterOccasion.values().indexOf(occasion))) return
        if (frame.visibility == View.VISIBLE) return
        // Two frames on, so a new 3D hero has drawn into its view before it is seen.
        frame.postOnAnimation {
            frame.postOnAnimation {
                if (!liveDrawn) return@postOnAnimation
                frame.alpha = 0f
                frame.visibility = View.VISIBLE
                frame.animate().alpha(1f).setDuration(120).withEndAction {
                    val gone = leaving?.first ?: return@withEndAction
                    leaving = null
                    pageAdapter.notifyItemChanged(PosterOccasion.values().indexOf(gone))
                }.start()
            }
        }
    }

    // ------------------------------------------------------------------ pages

    private fun buildPager() {
        pager.adapter = pageAdapter
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(PosterOccasion.values().indexOf(occasion), false)
        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                pagerIdle = state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE
                if (!pagerIdle) hideLive() else maybeShowLive()
            }

            override fun onPageSelected(position: Int) {
                chooseDesign(PosterOccasion.values()[position], fromPager = true)
            }
        })
    }

    /** A finished still of one design at the stage's size, drawn once and kept for a while. */
    private fun requestPage(o: PosterOccasion) {
        if (pageW <= 0 || pages.get(o) != null || pageJobs[o]?.isActive == true) return
        val ctx = posterContext()
        val w = pageW
        val h = pageH
        val inp = pageInput(ctx, o)
        pageJobs[o] = lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                val (full, _) = PosterImage.draw(ctx, inp)
                Bitmap.createScaledBitmap(full, w, h, true).also { if (it !== full) full.recycle() }
            }
            if (w != pageW || h != pageH) { bmp.recycle(); return@launch }
            pages.put(o, bmp)
            pageAdapter.notifyItemChanged(PosterOccasion.values().indexOf(o))
        }
    }

    /** What a page shows: the owner's own wording on the chosen design, each design's own on the rest. */
    private fun pageInput(ctx: Context, o: PosterOccasion): PosterImage.Input =
        if (o == occasion) input(animated = false, placeholder = true).copy(heroOverlay = false)
        else PosterImage.Input(o,
            if (o.title != 0) ctx.getString(o.title) else ctx.getString(R.string.poster_custom_title_hint),
            if (o.message != 0) ctx.getString(o.message) else ctx.getString(R.string.poster_custom_msg_hint),
            shopName(),
            if (fields.showPhone) BusinessProfile.businessPhone(this)?.trim() else null,
            if (fields.showAddress) BusinessProfile.businessAddress(this)?.trim() else null,
            if (fields.showLogo) logo else null,
            if (fields.showMark) ctx.getString(R.string.made_with_app) else null,
            fields = fields,
            email = if (fields.showEmail) BusinessProfile.businessEmail(this)?.trim() else null,
            whatsapp = fields.onWhatsapp)

    /** Every page is drawn again: the language, name, logo or a choice changed. */
    private fun refreshPages() {
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        pages.evictAll()
        pageAdapter.notifyDataSetChanged()
    }

    private inner class PageAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<PageAdapter.Holder>() {
        inner class Holder(val image: ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(image)

        override fun getItemCount() = PosterOccasion.values().size

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val iv = ImageView(parent.context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_XY
                setOnClickListener { showFull() }
            }
            return Holder(iv)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val o = PosterOccasion.values()[position]
            val shot = leaving?.takeIf { it.first == o }?.second
            val page = pages.get(o)
            holder.image.setImageBitmap(shot ?: page ?: thumbs[o])
            holder.image.contentDescription = if (o == PosterOccasion.CUSTOM) getString(R.string.poster_custom) else designName(o)
            if (page == null) requestPage(o)
        }
    }

    /** The design's name and place under the big picture, and on the pinned strip. */
    private fun updateDesignInfo() {
        val all = PosterOccasion.values()
        val i = all.indexOf(occasion)
        val name = if (occasion == PosterOccasion.CUSTOM) getString(R.string.poster_custom) else designName(occasion)
        findViewById<TextView>(R.id.designName).text = name
        findViewById<TextView>(R.id.designCount).text =
            com.innovation313.roshankhata.data.Digits.string(resources, R.string.poster_design_count, i + 1, all.size)
        findViewById<TextView>(R.id.previewWhat).text = "$name · ${NAMES.getValue(lang)}"
        // Bring the chosen thumbnail into view.
        designRow.findViewWithTag<View>(occasion.key)?.let { v ->
            findViewById<android.widget.HorizontalScrollView>(R.id.designScroll).post {
                findViewById<android.widget.HorizontalScrollView>(R.id.designScroll)
                    .smoothScrollTo((v.left - 40).coerceAtLeast(0), 0)
            }
        }
    }

    /** The next or previous design, from the arrows or a swipe on the picture. */
    private fun moveDesign(by: Int) {
        val all = PosterOccasion.values()
        chooseDesign(all[(all.indexOf(occasion) + by + all.size) % all.size])
    }

    /** Small pictures of every design, drawn as the real poster (flat heroes), in the poster's language. */
    private fun renderThumbs() {
        thumbsJob?.cancel()
        refreshPages()
        val ctx = posterContext()
        val w = (88 * resources.displayMetrics.density).toInt().coerceAtLeast(60)
        val h = w * PosterImage.HEIGHT / PosterImage.WIDTH
        val name = shopName()
        val phone = BusinessProfile.businessPhone(this)?.trim()
        val lg = logo
        thumbsJob = lifecycleScope.launch {
            for (o in PosterOccasion.values()) {
                val small = withContext(Dispatchers.Default) {
                    val title = if (o.title != 0) ctx.getString(o.title) else ctx.getString(R.string.poster_custom)
                    val message = if (o.message != 0) ctx.getString(o.message) else ""
                    val (full, _) = PosterImage.draw(ctx, PosterImage.Input(o, title, message, name, phone, null, lg, null,
                        fields = fields, whatsapp = fields.onWhatsapp))
                    Bitmap.createScaledBitmap(full, w, h, true).also { full.recycle() }
                }
                // The new picture goes up before the old one is let go.
                designRow.findViewWithTag<View>(o.key)?.findViewById<ImageView>(R.id.designThumb)?.setImageBitmap(small)
                thumbs.put(o, small)?.recycle()
            }
        }
    }

    /** Show the cards that belong to this design, and the last line where it has one. */
    private fun showFieldsFor() {
        fun show(id: Int, on: Boolean) { findViewById<View>(id).visibility = if (on) View.VISIBLE else View.GONE }
        show(R.id.fieldsStock, occasion == PosterOccasion.NEW_STOCK)
        show(R.id.fieldsClosed, occasion == PosterOccasion.CLOSED)
        show(R.id.fieldsHours, occasion == PosterOccasion.HOURS)
        show(R.id.fieldsRamazan, occasion == PosterOccasion.RAMAZAN)
        show(R.id.fieldsEid, occasion == PosterOccasion.EID_FITR)
        show(R.id.fieldsOffer, occasion == PosterOccasion.OFFER)
        // Designs without fields of their own have no card for them.
        show(R.id.designFields, occasion in listOf(PosterOccasion.NEW_STOCK, PosterOccasion.CLOSED, PosterOccasion.HOURS,
            PosterOccasion.RAMAZAN, PosterOccasion.EID_FITR, PosterOccasion.OFFER))
        // Closed and Hours print rows chosen above, not a free line.
        show(R.id.cardMessage, occasion != PosterOccasion.CLOSED && occasion != PosterOccasion.HOURS)
        findViewById<View>(R.id.btnTitleOriginal).visibility = if (occasion.title != 0) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.btnMessageOriginal).visibility = if (occasion.message != 0) View.VISIBLE else View.INVISIBLE
    }

    // ------------------------------------------------------------------ fields

    private fun setFields(f: PosterFields) {
        fields = f
        pagesDirty = true
        PosterFields.save(this, shopKey, f)
        render()
    }

    private fun choiceChip(group: ChipGroup, text: String): Chip {
        val chip = layoutInflater.inflate(R.layout.item_poster_chip, group, false) as Chip
        chip.id = View.generateViewId()
        chip.text = text
        group.addView(chip)
        return chip
    }

    private fun buildSendAs() {
        toggleSendAs.check(if (asVideo) R.id.btnAsVideo else R.id.btnAsPicture)
        toggleSendAs.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            asVideo = id == R.id.btnAsVideo
            prefs.edit().putBoolean(KEY_VIDEO, asVideo).apply()
        }
        TextFit.relax(toggleSendAs)
    }

    /**
     * What the poster shows, two to a row (12 Oct): a tick for each detail the
     * shop has, and for one it has not yet given, a tile that asks for it right
     * here (it is saved to the shop's profile, like the name).
     */
    private fun buildShowGrid() {
        showGrid.removeAllViews()
        fun place(v: View) {
            v.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(0, 0, dp(8), dp(8))
            }
            showGrid.addView(v)
        }
        fun tick(text: String, on: Boolean, isLogo: Boolean = false, set: (PosterFields, Boolean) -> PosterFields) {
            val box = layoutInflater.inflate(R.layout.item_poster_show, showGrid, false) as android.widget.CheckBox
            box.text = text
            box.isChecked = on
            box.setOnCheckedChangeListener { _, checked -> setFields(set(fields, checked)); if (isLogo) updateHero() }
            place(box)
        }
        fun ask(label: String, inputType: Int, save: (String) -> Unit) {
            val b = layoutInflater.inflate(R.layout.item_poster_suggest, showGrid, false) as MaterialButton
            b.text = label
            b.setOnClickListener {
                askValue(label, inputType) { value ->
                    save(value)
                    pagesDirty = true
                    buildShowGrid()
                    noPhoneNote.visibility = if (BusinessProfile.businessPhone(this).isNullOrBlank()) View.VISIBLE else View.GONE
                    render()
                }
            }
            place(b)
        }
        val phone = getString(R.string.poster_chip_phone)
        val email = getString(R.string.poster_chip_email)
        val address = getString(R.string.poster_chip_address)
        if (BusinessProfile.businessPhone(this).isNullOrBlank()) {
            ask(phone, android.text.InputType.TYPE_CLASS_PHONE) { BusinessProfile.setBusinessPhone(this, it); setFields(fields.copy(showPhone = true)) }
        } else {
            tick(phone, fields.showPhone) { f, v -> f.copy(showPhone = v) }
            tick(getString(R.string.poster_chip_whatsapp), fields.onWhatsapp) { f, v -> f.copy(onWhatsapp = v) }
        }
        if (BusinessProfile.businessEmail(this).isNullOrBlank()) {
            ask(email, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) {
                BusinessProfile.setBusinessEmail(this, it); setFields(fields.copy(showEmail = true))
            }
        } else {
            tick(email, fields.showEmail) { f, v -> f.copy(showEmail = v) }
        }
        if (BusinessProfile.businessAddress(this).isNullOrBlank()) {
            ask(address, android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS) {
                BusinessProfile.setBusinessAddress(this, it); setFields(fields.copy(showAddress = true))
            }
        } else {
            tick(address, fields.showAddress) { f, v -> f.copy(showAddress = v) }
        }
        if (BusinessProfile.hasLogo(this)) tick(getString(R.string.poster_chip_logo), fields.showLogo, isLogo = true) { f, v -> f.copy(showLogo = v) }
        tick("“" + getString(R.string.made_with_app) + "”", fields.showMark) { f, v -> f.copy(showMark = v) }
        TextFit.relax(showGrid)
    }

    /** One detail typed in a small window; [onSave] gets it trimmed, never blank. */
    private fun askValue(label: String, inputType: Int, onSave: (String) -> Unit) {
        val box = layoutInflater.inflate(R.layout.dialog_poster_value, null)
        val til = box.findViewById<TextInputLayout>(R.id.tilValue)
        val et = box.findViewById<TextInputEditText>(R.id.etValue)
        til.hint = label
        et.inputType = inputType
        // A number or an email reads left to right, whatever the app's language.
        val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        if (cls == android.text.InputType.TYPE_CLASS_PHONE || variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            et.textDirection = View.TEXT_DIRECTION_LTR
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.poster_add_named, label))
            .setMessage(R.string.poster_saved_to_profile)
            .setView(box)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val v = et.text?.toString()?.trim().orEmpty()
            if (v.isEmpty()) { til.error = getString(R.string.poster_value_needed); return@setOnClickListener }
            onSave(v)
            dialog.dismiss()
        }
        et.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        TextFit.relax(box)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /**
     * New stock (12 Oct): what arrived as a numbered list, each with its own
     * remove; a line to type the next; the trade's usual things three to a
     * row; and the photo, which takes the 3D crate's place.
     */
    private fun buildStockFields() {
        itemsList.removeAllViews()
        for ((i, item) in fields.items.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_poster_stock_row, itemsList, false)
            row.findViewById<TextView>(R.id.itemNumber).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.itemName).text = item
            row.findViewById<View>(R.id.itemRemove).apply {
                contentDescription = getString(R.string.poster_remove_item, item)
                setOnClickListener {
                    setFields(fields.copy(items = fields.items - item))
                    buildStockFields()
                }
            }
            itemsList.addView(row)
        }
        itemsList.visibility = if (fields.items.isEmpty()) View.GONE else View.VISIBLE
        val full = fields.items.size >= PosterFields.MAX_ITEMS
        findViewById<View>(R.id.rowAddItem).visibility = if (full) View.GONE else View.VISIBLE

        // Suggestions from the shop's trade, in the poster's language.
        suggestGrid.removeAllViews()
        val ideas = if (full) emptyList() else TradeVocab.items(posterContext()).filter { it !in fields.items }.take(6)
        for (idea in ideas) {
            val b = layoutInflater.inflate(R.layout.item_poster_suggest, suggestGrid, false) as MaterialButton
            b.text = idea
            b.contentDescription = getString(R.string.poster_add_named, idea)
            b.setOnClickListener { addItem(idea) }
            b.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(0, 0, dp(8), dp(8))
            }
            suggestGrid.addView(b)
        }
        val showIdeas = ideas.isNotEmpty()
        suggestGrid.visibility = if (showIdeas) View.VISIBLE else View.GONE
        findViewById<View>(R.id.labelQuickAdd).visibility = if (showIdeas) View.VISIBLE else View.GONE

        val hasPhoto = fields.usePhoto && photo != null
        findViewById<TextView>(R.id.photoTitle).setText(if (hasPhoto) R.string.poster_remove_photo else R.string.poster_add_photo)
        findViewById<ImageView>(R.id.photoIcon).setImageResource(if (hasPhoto) R.drawable.ic_close else R.drawable.ic_camera)
        TextFit.relax(itemsList)
        TextFit.relax(suggestGrid)
        if (btnPhoto.tag == null) {
            btnPhoto.tag = true
            findViewById<View>(R.id.btnAddItem).setOnClickListener { addItem(etItem.text?.toString().orEmpty()) }
            etItem.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { addItem(etItem.text?.toString().orEmpty()); true } else false
            }
            btnPhoto.setOnClickListener {
                if (fields.usePhoto && photo != null) {
                    // Not recycled: a picture being drawn in the background may still hold it.
                    photo = null
                    photoFile().delete()
                    setFields(fields.copy(usePhoto = false))
                    buildStockFields(); updateHero()
                } else {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
    }

    // ------------------------------------------------------------------ shop name

    /**
     * The shop's name in the poster's language: every poster's heading (12 Oct,
     * the owner: the heading is the shop, never its products). Written here,
     * it is the profile's name too, so the statements and cards agree.
     */
    private fun buildShopName() {
        val urdu = posterUrdu()
        tilShopName.hint = getString(if (urdu) R.string.poster_shop_in_ur else R.string.poster_shop_in_en)
        filling = true
        etShopName.setText((if (urdu) BusinessProfile.businessNameUrdu(this) else BusinessProfile.businessName(this)).orEmpty())
        etShopName.textDirection = if (urdu) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        filling = false
        tilShopName.error = null
        if (etShopName.tag == null) {
            etShopName.tag = true
            etShopName.doAfterTextChanged { e ->
                if (filling) return@doAfterTextChanged
                val name = e?.toString().orEmpty()
                if (posterUrdu()) BusinessProfile.setBusinessNameUrdu(this, name) else BusinessProfile.setBusinessName(this, name)
                if (name.isNotBlank()) tilShopName.error = null
                pagesDirty = true
                render()
                // The seal (Thank you) carries the name: rebuilt once the owner pauses.
                shopNameJob?.cancel()
                shopNameJob = lifecycleScope.launch { delay(700); updateHero() }
            }
        }
    }

    // ------------------------------------------------------------------ steps

    private fun buildSteps() {
        findViewById<View>(R.id.step1).setOnClickListener { showStep(1) }
        findViewById<View>(R.id.step2).setOnClickListener { showStep(2) }
        findViewById<View>(R.id.step3).setOnClickListener { showStep(3) }
        btnStepNext.setOnClickListener { showStep(step + 1) }
        btnStepBack.setOnClickListener { showStep(step - 1) }
        findViewById<View>(R.id.btnPrevDesign).setOnClickListener { moveDesign(-1) }
        findViewById<View>(R.id.btnNextDesign).setOnClickListener { moveDesign(1) }
        findViewById<View>(R.id.btnViewFull).setOnClickListener { showFull() }
        buildPager()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step > 1) showStep(step - 1) else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    /**
     * Show one step. The picture stays above them all: large on Design and
     * Send, a small pinned strip on Words. The Send step asks for the shop's
     * name first, since it is the poster's heading.
     */
    private fun showStep(n: Int) {
        var target = n.coerceIn(1, 3)
        if (target == 3 && etShopName.text.isNullOrBlank() && shopName().isBlank()) {
            target = 2
            tilShopName.error = getString(R.string.poster_shop_needed)
            etShopName.requestFocus()
        }
        step = target
        fun vis(id: Int, on: Boolean) { findViewById<View>(id).visibility = if (on) View.VISIBLE else View.GONE }
        vis(R.id.pageDesign, step == 1)
        vis(R.id.pageWords, step == 2)
        vis(R.id.pageSend, step == 3)
        vis(R.id.btnPrevDesign, step == 1)
        vis(R.id.btnNextDesign, step == 1)
        vis(R.id.previewInfo, step == 2)
        vis(R.id.btnViewFull, step == 2)
        btnStepBack.visibility = if (step == 2) View.VISIBLE else View.GONE
        btnStepNext.visibility = if (step < 3) View.VISIBLE else View.GONE
        btnStepNext.setText(if (step == 1) R.string.poster_next_words else R.string.poster_next_send)
        btnStatus.visibility = if (step == 3) View.VISIBLE else View.GONE
        btnShare.visibility = if (step == 3) View.VISIBLE else View.GONE

        // The picture: big, or the pinned strip. Big is what the screen can spare
        // with the step's own rows in view: title and steps (~100dp), the design's
        // name and strip (~150dp), the language (~90dp), the bar (~84dp) and the
        // system bars (~50dp). It never runs wider than the screen.
        val d = resources.displayMetrics.density
        val screenDp = resources.displayMetrics.heightPixels / d
        val widthDp = resources.displayMetrics.widthPixels / d - 32f - 120f
        val bigH = (screenDp - 474f).coerceIn(220f, 460f).coerceAtMost(widthDp * 16f / 9f)
        val h = if (step == 2) 112f else bigH
        stageBox.layoutParams = (stageBox.layoutParams as LinearLayout.LayoutParams).apply {
            height = (h * d).toInt()
            width = (h * 9f / 16f * d).toInt()
        }
        setPageSize((bigH * d).toInt())
        // Then, measured: the Design step's last row (the language) must clear the bar.
        if (step == 1) {
            val root = window.decorView.viewTreeObserver
            root.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    fitDesignStep()
                }
            })
        }
        // On Design the pages take the touches (slide, tap); elsewhere the live poster is tapped.
        pager.visibility = if (step == 1) View.VISIBLE else View.INVISIBLE
        if (step == 1) {
            frame.setOnClickListener(null)
            frame.isClickable = false
            val i = PosterOccasion.values().indexOf(occasion)
            if (pager.currentItem != i) pager.setCurrentItem(i, false)
        } else {
            frame.setOnClickListener { showFull() }
            if (liveDrawn) { frame.visibility = View.VISIBLE; frame.alpha = 1f }
        }
        previewHost.background = if (step == 2) getDrawable(R.drawable.bg_profile_card) else null
        val pad = if (step == 2) dp(10) else 0
        previewHost.setPadding(pad, pad, pad, pad)
        previewHost.gravity = if (step == 2) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER
        frame.post { placeHero() }

        styleStepper()
        updateDesignInfo()
        findViewById<android.widget.ScrollView>(R.id.posterScroll).scrollTo(0, 0)
        TextFit.relax(btnStepNext)
    }

    private fun setPageSize(heightPx: Int) {
        val pw = heightPx * 9 / 16
        if (pw != pageW || heightPx != pageH || (step == 1 && pagesDirty)) {
            pageW = pw; pageH = heightPx; pagesDirty = false
            refreshPages()
        }
    }

    /**
     * The estimate above, corrected by what the screen really shows: if the
     * language switch ends under the bar the poster gives up that much height,
     * and if there is room to spare it takes it, between 200 dp and 460 dp and
     * never wider than the screen.
     */
    private fun fitDesignStep() {
        if (step != 1) return
        val lang = toggleLang
        val bar = btnStepNext
        if (lang.height == 0 || bar.height == 0) return
        val l = IntArray(2); lang.getLocationOnScreen(l)
        val b = IntArray(2); bar.getLocationOnScreen(b)
        val gap = dp(12)
        val spare = b[1] - gap - (l[1] + lang.height)
        val d = resources.displayMetrics.density
        // The width left beside the two arrows (48 dp each, 12 dp from the poster).
        val maxByWidth = ((resources.displayMetrics.widthPixels / d - 32f - 120f) * 16f / 9f * d).toInt()
        val h = (stageBox.height + spare).coerceIn(dp(200), minOf(dp(460), maxByWidth))
        if (kotlin.math.abs(h - stageBox.height) < dp(4)) return
        stageBox.layoutParams = (stageBox.layoutParams as LinearLayout.LayoutParams).apply {
            height = h
            width = h * 9 / 16
        }
        setPageSize(h)
        frame.post { placeHero() }
    }

    /** The stepper: the step on screen filled green, done ones ticked, the rest outlined. */
    private fun styleStepper() {
        val d = resources.displayMetrics.density
        val green = getColor(R.color.brand_green)
        listOf(R.id.step1, R.id.step2, R.id.step3).forEachIndexed { i, id ->
            val tv = findViewById<TextView>(id)
            val n = i + 1
            val current = n == step
            val done = n < step
            tv.background = if (current) android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 18 * d; setColor(green)
            } else null
            tv.setTextColor(getColor(when {
                current -> R.color.white
                done -> R.color.brand_green_text
                else -> R.color.ink_soft
            }))
            val dot = StepDot(n, current, done, d, getColor(R.color.brand_green), getColor(R.color.ink_soft),
                getColor(if (current) R.color.brand_green else R.color.white))
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
            tv.isSelected = current
            tv.contentDescription = tv.text
        }
        findViewById<View>(R.id.stepLine1).setBackgroundColor(getColor(if (step > 1) R.color.brand_green else R.color.page_line))
        findViewById<View>(R.id.stepLine2).setBackgroundColor(getColor(if (step > 2) R.color.brand_green else R.color.page_line))
    }

    /** The round number (or tick) before each step's name. */
    private class StepDot(
        private val n: Int, private val current: Boolean, private val done: Boolean, d: Float,
        private val green: Int, private val grey: Int, private val numberColour: Int
    ) : android.graphics.drawable.Drawable() {
        private val size = (18 * d).toInt()
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val dd = d
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun draw(c: android.graphics.Canvas) {
            val r = bounds
            val cx = r.exactCenterX(); val cy = r.exactCenterY(); val rad = r.width() / 2f
            when {
                current -> { p.style = android.graphics.Paint.Style.FILL; p.color = android.graphics.Color.WHITE; c.drawCircle(cx, cy, rad, p) }
                done -> { p.style = android.graphics.Paint.Style.FILL; p.color = green; c.drawCircle(cx, cy, rad, p) }
                else -> { p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 1.5f * dd; p.color = grey; c.drawCircle(cx, cy, rad - dd, p) }
            }
            if (done) {
                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 2.4f * dd; p.color = android.graphics.Color.WHITE
                p.strokeCap = android.graphics.Paint.Cap.ROUND
                val path = android.graphics.Path().apply {
                    moveTo(cx - rad * 0.42f, cy + rad * 0.02f); lineTo(cx - rad * 0.08f, cy + rad * 0.36f); lineTo(cx + rad * 0.45f, cy - rad * 0.32f)
                }
                c.drawPath(path, p)
            } else {
                p.style = android.graphics.Paint.Style.FILL
                p.color = if (current) numberColour else grey
                p.textSize = 11f * dd; p.textAlign = android.graphics.Paint.Align.CENTER; p.isFakeBoldText = true
                c.drawText(n.toString(), cx, cy - (p.descent() + p.ascent()) / 2f, p)
            }
        }
        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** The poster at full size, its hero still, in a window over the screen. */
    private fun showFull() {
        lifecycleScope.launch {
            val bmp = stillPicture(posterContext()) ?: return@launch
            val iv = ImageView(this@PosterActivity).apply {
                adjustViewBounds = true
                setImageBitmap(bmp)
                contentDescription = getString(R.string.poster_title)
            }
            MaterialAlertDialogBuilder(this@PosterActivity)
                .setView(iv)
                .setPositiveButton(R.string.close, null)
                .setOnDismissListener { bmp.recycle() }
                .show()
        }
    }

    private fun addItem(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t in fields.items || fields.items.size >= PosterFields.MAX_ITEMS) return
        etItem.setText("")
        setFields(fields.copy(items = fields.items + t))
        buildStockFields()
    }

    private fun buildClosedFields() {
        chipReasons.removeAllViews()
        val reasons = PosterFields.Reason.values().map { it.key to PosterWords.reason(this, it.key)!! } +
            (PosterFields.CUSTOM_REASON to getString(R.string.poster_write_own))
        for ((key, label) in reasons) {
            val chip = choiceChip(chipReasons, label)
            chip.tag = key
            chip.isChecked = fields.reason == key
        }
        chipReasons.setOnCheckedStateChangeListener { g, ids ->
            val key = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? String } ?: ""
            tilReason.visibility = if (key == PosterFields.CUSTOM_REASON) View.VISIBLE else View.GONE
            setFields(fields.copy(reason = key))
        }
        tilReason.visibility = if (fields.reason == PosterFields.CUSTOM_REASON) View.VISIBLE else View.GONE
        etReason.setText(fields.reasonText)
        etReason.doAfterTextChanged { setFields(fields.copy(reasonText = it?.toString().orEmpty())) }

        val dayBtn = findViewById<MaterialButton>(R.id.btnReopenDay)
        val timeBtn = findViewById<MaterialButton>(R.id.btnReopenTime)
        fun label() {
            dayBtn.text = PosterWords.reopenDay(this, fields.reopen) ?: getString(R.string.poster_not_said)
            timeBtn.text = PosterWords.time(this, fields.reopenAt)
            timeBtn.isEnabled = fields.reopen.isNotEmpty()
        }
        label()
        dayBtn.setOnClickListener {
            val keys = PosterFields.Reopen.values().map { it.key } + ""
            val names = keys.map { PosterWords.reopenDay(this, it) ?: getString(R.string.poster_not_said) }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.poster_opens_again)
                .setItems(names.toTypedArray()) { _, i -> setFields(fields.copy(reopen = keys[i])); label() }
                .show()
        }
        timeBtn.setOnClickListener { pickTime(fields.reopenAt) { setFields(fields.copy(reopenAt = it)); label() } }
        val urgent = findViewById<SwitchMaterial>(R.id.switchUrgent)
        urgent.isChecked = fields.urgentPhone
        urgent.setOnCheckedChangeListener { _, on -> setFields(fields.copy(urgentPhone = on)) }
    }

    private fun buildHoursFields() {
        val opens = findViewById<MaterialButton>(R.id.btnOpens)
        val closes = findViewById<MaterialButton>(R.id.btnCloses)
        val from = findViewById<MaterialButton>(R.id.btnBreakFrom)
        val to = findViewById<MaterialButton>(R.id.btnBreakTo)
        val rowBreak = findViewById<View>(R.id.rowBreak)
        fun label() {
            opens.text = PosterWords.time(this, fields.opens)
            closes.text = PosterWords.time(this, fields.closes)
            from.text = PosterWords.time(this, fields.breakFrom)
            to.text = PosterWords.time(this, fields.breakTo)
            rowBreak.visibility = if (fields.prayerBreak) View.VISIBLE else View.GONE
        }
        label()
        opens.setOnClickListener { pickTime(fields.opens) { setFields(fields.copy(opens = it)); label() } }
        closes.setOnClickListener { pickTime(fields.closes) { setFields(fields.copy(closes = it)); label() } }
        from.setOnClickListener { pickTime(fields.breakFrom) { setFields(fields.copy(breakFrom = it)); label() } }
        to.setOnClickListener { pickTime(fields.breakTo) { setFields(fields.copy(breakTo = it)); label() } }
        val sw = findViewById<SwitchMaterial>(R.id.switchBreak)
        sw.isChecked = fields.prayerBreak
        sw.setOnCheckedChangeListener { _, on -> setFields(fields.copy(prayerBreak = on)); label() }

        chipHolidays.removeAllViews()
        for (d in PosterWords.WEEK) {
            val chip = choiceChip(chipHolidays, PosterWords.day(this, d, short = true))
            chip.isChecked = d in fields.holidays
            chip.setOnCheckedChangeListener { _, on ->
                // Not every day can be a holiday: one stays open.
                val next = if (on) fields.holidays + d else fields.holidays - d
                if (next.size >= 7) { chip.isChecked = false; return@setOnCheckedChangeListener }
                setFields(fields.copy(holidays = next))
            }
        }
        TextFit.relax(chipHolidays)
    }

    private fun buildRamazanFields() {
        val times = findViewById<View>(R.id.ramazanTimes)
        val dayFrom = findViewById<MaterialButton>(R.id.btnDayFrom)
        val dayTo = findViewById<MaterialButton>(R.id.btnDayTo)
        val nightFrom = findViewById<MaterialButton>(R.id.btnNightFrom)
        val nightTo = findViewById<MaterialButton>(R.id.btnNightTo)
        fun label() {
            times.visibility = if (fields.ramazanHours) View.VISIBLE else View.GONE
            dayFrom.text = PosterWords.time(this, fields.dayFrom)
            dayTo.text = PosterWords.time(this, fields.dayTo)
            nightFrom.text = if (fields.afterTaraweeh) getString(R.string.poster_after_taraweeh) else PosterWords.time(this, fields.nightFrom)
            nightTo.text = PosterWords.time(this, fields.nightTo)
        }
        label()
        val sw = findViewById<SwitchMaterial>(R.id.switchRamazan)
        sw.isChecked = fields.ramazanHours
        sw.setOnCheckedChangeListener { _, on -> setFields(fields.copy(ramazanHours = on)); label() }
        dayFrom.setOnClickListener { pickTime(fields.dayFrom) { setFields(fields.copy(dayFrom = it)); label() } }
        dayTo.setOnClickListener { pickTime(fields.dayTo) { setFields(fields.copy(dayTo = it)); label() } }
        nightTo.setOnClickListener { pickTime(fields.nightTo) { setFields(fields.copy(nightTo = it)); label() } }
        nightFrom.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setItems(arrayOf(getString(R.string.poster_after_taraweeh), getString(R.string.pick_time))) { _, i ->
                    if (i == 0) { setFields(fields.copy(afterTaraweeh = true)); label() }
                    else pickTime(fields.nightFrom) { setFields(fields.copy(afterTaraweeh = false, nightFrom = it)); label() }
                }
                .show()
        }
    }

    private fun buildEidFields() {
        val sw = findViewById<SwitchMaterial>(R.id.switchEid)
        chipEidDays.removeAllViews()
        for (n in 1..5) {
            val chip = choiceChip(chipEidDays, n.toString())
            chip.tag = n
            chip.isChecked = fields.eidDays == n
        }
        chipEidDays.visibility = if (fields.eidDays > 0) View.VISIBLE else View.GONE
        sw.isChecked = fields.eidDays > 0
        sw.setOnCheckedChangeListener { _, on ->
            val days = if (on) 3 else 0
            setFields(fields.copy(eidDays = days))
            chipEidDays.visibility = if (on) View.VISIBLE else View.GONE
            for (i in 0 until chipEidDays.childCount) (chipEidDays.getChildAt(i) as Chip).let { it.isChecked = it.tag == days }
        }
        chipEidDays.setOnCheckedStateChangeListener { g, ids ->
            val n = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? Int } ?: return@setOnCheckedStateChangeListener
            if (fields.eidDays > 0) setFields(fields.copy(eidDays = n))
        }
        TextFit.relax(chipEidDays)
    }

    private fun buildOfferFields() {
        chipOfferPercent.removeAllViews()
        // The usual steps, and the owner's own figure if he saved another.
        val steps = (OFFER_STEPS + fields.offerPercent).distinct().sorted()
        for (n in steps) {
            val chip = choiceChip(chipOfferPercent, "$n%")
            chip.tag = n
            chip.isChecked = fields.offerPercent == n
        }
        chipOfferPercent.setOnCheckedStateChangeListener { g, ids ->
            val n = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? Int } ?: return@setOnCheckedStateChangeListener
            if (n == fields.offerPercent) return@setOnCheckedStateChangeListener
            setFields(fields.copy(offerPercent = n))
            updateHero()
        }
        val sw = findViewById<SwitchMaterial>(R.id.switchOfferDays)
        chipOfferDays.removeAllViews()
        for (n in OFFER_DAYS) {
            val chip = choiceChip(chipOfferDays, n.toString())
            chip.tag = n
            chip.isChecked = fields.offerDays == n
        }
        chipOfferDays.visibility = if (fields.offerDays > 0) View.VISIBLE else View.GONE
        sw.isChecked = fields.offerDays > 0
        sw.setOnCheckedChangeListener { _, on ->
            val days = if (on) 3 else 0
            setFields(fields.copy(offerDays = days))
            chipOfferDays.visibility = if (on) View.VISIBLE else View.GONE
            for (i in 0 until chipOfferDays.childCount) (chipOfferDays.getChildAt(i) as Chip).let { it.isChecked = it.tag == days }
        }
        chipOfferDays.setOnCheckedStateChangeListener { g, ids ->
            val n = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? Int } ?: return@setOnCheckedStateChangeListener
            if (fields.offerDays > 0) setFields(fields.copy(offerDays = n))
        }
        TextFit.relax(chipOfferPercent)
        TextFit.relax(chipOfferDays)
    }

    private fun pickTime(minutes: Int, onPicked: (Int) -> Unit) {
        android.app.TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) },
            minutes / 60, minutes % 60, DateFormat.is24HourFormat(this)).show()
    }

    // ------------------------------------------------------------------ photo

    private fun photoFile() = File(File(filesDir, "poster").apply { mkdirs() }, "stock_$shopKey.jpg")

    private fun loadPhoto(): Bitmap? = photoFile().takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Read the chosen picture at no more than 1600 px, upright, and keep a copy for this shop. */
    private fun takePhoto(uri: Uri) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                    val raw = contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@withContext null
                    val turn = contentResolver.openInputStream(uri)?.use {
                        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    } ?: 0f
                    val upright = if (turn == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                        Matrix().apply { postRotate(turn) }, true).also { if (it !== raw) raw.recycle() }
                    FileOutputStream(photoFile()).use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    upright
                } catch (e: Exception) {
                    null
                }
            }
            if (bmp == null) {
                Toast.makeText(this@PosterActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            photo = bmp
            setFields(fields.copy(usePhoto = true))
            buildStockFields()
            updateHero()
        }
    }

    // ------------------------------------------------------------------ wording

    private fun key(kind: String) = "${kind}${occasion.key}_${lang}_$shopKey"
    /** Before the poster had its own language (11 Oct) the wording was kept per design only. */
    private fun oldKey(kind: String) = "${kind}${occasion.key}_$shopKey"

    /** The owner's wording if he changed it, else the design's own in the poster's language. */
    private fun fill() {
        filling = true
        val ctx = posterContext()
        val appLang = lang == defaultLanguage()
        fun stored(kind: String) = prefs.getString(key(kind), null) ?: if (appLang) prefs.getString(oldKey(kind), null) else null
        etTitle.setText(stored(KEY_TITLE) ?: occasion.title.takeIf { it != 0 }?.let { ctx.getString(it) } ?: "")
        etMessage.setText(stored(KEY_MSG) ?: occasion.message.takeIf { it != 0 }?.let { ctx.getString(it) } ?: "")
        val rtl = PosterKit.urduScript(Locale.forLanguageTag(lang)) || Locale.forLanguageTag(lang).language in listOf("ar", "fa", "sd")
        val dir = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        etTitle.textDirection = dir
        etMessage.textDirection = dir
        filling = false
        render()
    }

    private fun save(kind: String, text: String?, default: Int) {
        val t = text?.trim().orEmpty()
        val e = prefs.edit()
        if (default != 0 && t == posterContext().getString(default)) e.remove(key(kind)) else e.putString(key(kind), t)
        e.apply()
    }

    /** The shop's name as this poster prints it: in Urdu script on an Urdu poster, if the owner gave one. */
    private fun shopName(): String = BusinessProfile.nameFor(this, posterUrdu())?.trim().orEmpty()

    private fun usingPhoto() = occasion == PosterOccasion.NEW_STOCK && fields.usePhoto && photo != null

    /** [animated]: the moving layer (preview, video) draws the confetti, flag and fireworks; a still picture has them drawn in. */
    private fun input(animated: Boolean = true, placeholder: Boolean = true) = PosterImage.Input(
        occasion = occasion,
        // An empty Announcement shows where its words go, in the preview only.
        title = etTitle.text?.toString()?.trim().orEmpty().ifEmpty {
            if (placeholder && occasion == PosterOccasion.CUSTOM) posterContext().getString(R.string.poster_custom_title_hint) else ""
        },
        message = etMessage.text?.toString()?.trim().orEmpty().ifEmpty {
            if (placeholder && occasion == PosterOccasion.CUSTOM) posterContext().getString(R.string.poster_custom_msg_hint) else ""
        },
        shopName = shopName(),
        phone = if (fields.showPhone) BusinessProfile.businessPhone(this)?.trim() else null,
        address = if (fields.showAddress) BusinessProfile.businessAddress(this)?.trim() else null,
        logo = if (fields.showLogo) logo else null,
        mark = if (fields.showMark) posterContext().getString(R.string.made_with_app) else null,
        heroOverlay = stage != null,
        fields = fields,
        photo = if (usingPhoto()) photo else null,
        animated = animated,
        email = if (fields.showEmail) BusinessProfile.businessEmail(this)?.trim() else null,
        whatsapp = fields.onWhatsapp
    )

    // ------------------------------------------------------------------ 3D hero

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
     * 3D keeps the flat drawing, and New stock with the owner's photo shows the
     * photo instead. The Thank-you seal is rebuilt when its mark changes (the
     * logo, or the name in the poster's language).
     */
    private fun updateHero() {
        val hero = PosterDesigns.hero(occasion)?.takeIf { !usingPhoto() && Studio.available(this) }
        val markLogo = if (fields.showLogo) logo else null
        val wantKey = hero?.let {
            when (it) {
                Hero.THANKS -> "${it.name}|${shopName()}|${markLogo != null}"
                Hero.OFFER -> "${it.name}|${fields.offerPercent}|$lang"
                else -> it.name
            }
        }
        if (stage != null && stageKey != wantKey) {
            stage?.destroy()
            stage = null
            stageHero = null
            stageKey = null
        }
        if (hero != null && stage == null) {
            stage = try {
                val face = when {
                    hero.face == null -> null
                    hero == Hero.OFFER -> CoinFace.draw(posterContext(), fields.offerPercent)
                    else -> SealFace.draw(this, shopName(), markLogo)
                }
                HeroStage(this, hero, face = face).also {
                    face?.recycle()
                    stageHero = hero
                    stageKey = wantKey
                    if (isStarted) it.attach(heroView)
                }
            } catch (e: Exception) {
                null
            }
        }
        heroView.visibility = if (stage != null) View.VISIBLE else View.GONE
        placeHero()
        render()
    }

    // ------------------------------------------------------------------ picture

    /** Redraw after the owner stops typing; the old picture is let go only once the new one is up. */
    private fun render() {
        renderJob?.cancel()
        val inp = input()
        val ctx = posterContext()
        // While the owner types, wait for a pause; a new design is drawn at once.
        val wait = if (frame.visibility == View.VISIBLE) 150L else 0L
        renderJob = lifecycleScope.launch {
            delay(wait)
            val (bmp, fit) = withContext(Dispatchers.Default) { PosterImage.draw(ctx, inp) }
            val old = shown
            preview.setImageBitmap(bmp)
            motionView.show(fit.motion)
            shown = bmp
            old?.recycle()
            liveDrawn = true
            maybeShowLive()
        }
    }

    private fun sharePoster(toWhatsApp: Boolean) {
        if (shopName().isBlank()) { showStep(3); return }
        if (occasion == PosterOccasion.CUSTOM && etTitle.text.isNullOrBlank()) {
            showStep(2)
            findViewById<TextInputLayout>(R.id.tilTitle).error = getString(R.string.poster_heading_needed)
            etTitle.requestFocus()
            return
        }
        btnStatus.isEnabled = false
        btnShare.isEnabled = false
        val ctx = posterContext()
        lifecycleScope.launch {
            val file: File? = if (asVideo) makeVideo(ctx) else null
            if (asVideo && videoCancelled) {
                // The owner stopped it: nothing is sent.
                btnStatus.isEnabled = true
                btnShare.isEnabled = true
                return@launch
            }
            val (shared, type) = when {
                file != null -> file to "video/mp4"
                else -> {
                    if (asVideo) Toast.makeText(this@PosterActivity, R.string.poster_video_failed, Toast.LENGTH_LONG).show()
                    makePicture(ctx) to "image/png"
                }
            }
            btnStatus.isEnabled = true
            btnShare.isEnabled = true
            if (shared == null) {
                Toast.makeText(this@PosterActivity, R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            send(shared, type, toWhatsApp)
        }
    }

    /** The still picture, with the hero itself drawn still off-screen at full size. */
    private suspend fun makePicture(ctx: Context): File? {
        val bmp = stillPicture(ctx) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val dir = File(cacheDir, "posters").apply { mkdirs() }
                val f = File(dir, "poster.png")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f
            } catch (e: Exception) {
                null
            } finally {
                bmp.recycle()
            }
        }
    }

    /** The whole poster as one still picture: the hero drawn still at full size, no moving layer. */
    private suspend fun stillPicture(ctx: Context): Bitmap? {
        val hero = stage?.let { st ->
            try {
                val r = stageHero!!.rect
                st.still(r.width().toInt(), r.height().toInt())
            } catch (e: Exception) {
                null
            }
        }
        val inp = input(animated = false, placeholder = false).copy(hero = hero, heroOverlay = false)
        return withContext(Dispatchers.Default) {
            try {
                PosterImage.draw(ctx, inp).first
            } catch (e: Exception) {
                null
            } finally {
                hero?.recycle()
            }
        }
    }

    /**
     * The moving poster as an MP4 (PosterVideo), with a progress window the
     * owner can cancel. Null if it could not be made (the caller then sends
     * the picture) or was cancelled.
     */
    private suspend fun makeVideo(ctx: Context): File? {
        val text = TextView(this).apply {
            setTextColor(getColor(R.color.ink)); textSize = 15f
            text = com.innovation313.roshankhata.data.Digits.string(resources, R.string.poster_making_video, 0)
        }
        val bar = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply {
            max = 100; isIndeterminate = false
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad / 2)
            addView(text)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = pad / 2 })
        }
        TextFit.relax(box)
        var cancelled = false
        videoCancelled = false
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true; videoCancelled = true; videoJob?.cancel() }
            .show()
        return try {
            val inp = input(placeholder = false).copy(hero = null, heroOverlay = stage != null)
            val (base, fit) = withContext(Dispatchers.Default) { PosterImage.draw(ctx, inp) }
            val out = File(File(cacheDir, "posters").apply { mkdirs() }, "poster.mp4")
            var made = false
            val job = lifecycleScope.launch {
                // Caught here: an encoder that refuses must not take the screen down
                // (an exception in a launched job would); the picture is sent instead.
                try {
                    PosterVideo.make(base, stage, stageHero?.rect, fit.motion, VIDEO_SECONDS, out) { p ->
                        bar.setProgressCompat(p, true)
                        text.text = com.innovation313.roshankhata.data.Digits.string(resources, R.string.poster_making_video, p)
                    }
                    made = true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("PosterVideo", "The video could not be made", e)
                }
            }
            videoJob = job
            job.join()
            base.recycle()
            if (cancelled || !made) null else out.takeIf { it.length() > 0 }
        } catch (e: Exception) {
            null
        } finally {
            videoJob = null
            dialog.dismiss()
        }
    }

    private fun send(file: File, type: String, toWhatsApp: Boolean) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // WhatsApp, or WhatsApp Business; its share screen offers "My status" first.
            val whatsapp = if (toWhatsApp) WHATSAPP.firstOrNull { pkg ->
                send.setPackage(pkg)
                val found = send.resolveActivity(packageManager) != null
                send.setPackage(null)
                found
            } else null
            if (toWhatsApp && whatsapp == null) {
                Toast.makeText(this, R.string.poster_whatsapp_missing, Toast.LENGTH_LONG).show()
            }
            if (whatsapp != null) startActivity(send.setPackage(whatsapp))
            else startActivity(Intent.createChooser(send, getString(R.string.share)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
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
        videoJob?.cancel()
        stage?.destroy()
        stage = null
        thumbsJob?.cancel()
        super.onDestroy()
        preview.setImageDrawable(null)
        shown?.recycle()
        shown = null
        for (i in 0 until designRow.childCount) designRow.getChildAt(i).findViewById<ImageView>(R.id.designThumb)?.setImageDrawable(null)
        thumbs.values.forEach { it.recycle() }
        thumbs.clear()
        photo = null
    }

    companion object {
        private const val PREFS = "poster"
        private const val KEY_OCCASION = "occasion"
        private const val KEY_LANG = "lang_"
        private const val KEY_TITLE = "title_"
        private const val KEY_MSG = "msg_"
        private const val KEY_VIDEO = "as_video"
        /** Long enough for every motion to show (the slowest sway is 11 s; most loop in 3-8). */
        const val VIDEO_SECONDS = 8f
        private val OFFER_STEPS = listOf(5, 10, 15, 20, 25, 30, 40, 50)
        private val OFFER_DAYS = listOf(1, 2, 3, 5, 7, 10, 15)

        private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
        /** First in the language list: what most shops here write in. */
        private val MOST_USED = listOf("ur", "en", "ur-Latn")
        private val RTL_TAGS = setOf("ur", "sd", "ar", "fa")

        /** The languages a poster speaks, the app's own nine. */
        val LANGS = listOf("ur", "en", "ur-Latn", "sd", "ar", "fa", "hi", "bn", "in")

        /** Each language by its own name. */
        private val NAMES = mapOf(
            "ur" to "اردو", "en" to "English", "ur-Latn" to "Roman Urdu", "sd" to "سنڌي", "ar" to "العربية",
            "fa" to "فارسی", "hi" to "हिन्दी", "bn" to "বাংলা", "in" to "Bahasa Indonesia"
        )
    }
}
