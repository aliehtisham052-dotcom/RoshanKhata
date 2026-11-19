package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.string

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
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
import com.innovation313.roshankhata.data.Branding
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.PosterFields
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.data.TradeVocab
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.LocaleRefresh
import com.innovation313.roshankhata.ui.PlaceAt
import com.innovation313.roshankhata.ui.PosterContact
import com.innovation313.roshankhata.ui.PosterDesigns
import com.innovation313.roshankhata.ui.PosterImage
import com.innovation313.roshankhata.ui.PosterKit
import com.innovation313.roshankhata.ui.PosterMotionView
import com.innovation313.roshankhata.ui.PosterVideo
import com.innovation313.roshankhata.ui.PosterWords
import com.innovation313.roshankhata.ui.ScreenInsets
import com.innovation313.roshankhata.ui.TextFit
import com.innovation313.roshankhata.ui.poster3d.CoinFace
import com.innovation313.roshankhata.ui.poster3d.CrateFace
import com.innovation313.roshankhata.ui.poster3d.Hero
import com.innovation313.roshankhata.ui.poster3d.HeroStage
import com.innovation313.roshankhata.ui.poster3d.SealFace
import com.innovation313.roshankhata.ui.poster3d.Studio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** The live layer is on the stage and seen. */
    private var liveUp = false
    /** The live layer is laid out unseen while its 3D hero draws its first frame; [revealTurn] names this wait. */
    private var revealing = false
    private var revealTurn = 0
    private var pagerIdle = true
    /** How often the pages left their rest (for the on-device proof's message). */
    private var pagerMoves = 0
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
    /** Where the picture on show has its hero: the design may move or grow it with what the foot shows (PosterBody). */
    private var heroPlace: android.graphics.RectF? = null
    private var isStarted = false
    /** The logo and the owner's photo are read: pictures drawn before that would be drawn twice. */
    private var loaded = false
    /** What the logo looks like, for the seal's key: another logo is another seal. */
    private var logoMark = "-"
    /** What of the shop's profile the pictures show, as last drawn (onResume draws again only when it changed). */
    private var profileMark = ""

    /** A design's 3D hero as a picture, and when it was taken (a clock's grows old). Never recycled: views show them. */
    private class Shot(val picture: Bitmap, val at: Long)
    private val heroShots = object : LinkedHashMap<String, Shot>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Shot>?) = size > 16
    }
    /** One 3D stage at a time is built for a picture. */
    private val shotLock = Mutex()
    /** The GPU would not give a hero's picture: the designs are drawn flat while this screen is open, and nothing waits again. */
    private var shotsOff = false

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
        // Off the stage until its picture is drawn and its hero has a frame (maybeShowLive).
        frame.visibility = View.INVISIBLE
        frame.post { placeHero() }
        // Whenever the poster's size changes (a step, the fit to the screen), the 3D
        // view is laid over the hero's place again, at the size the poster really has.
        frame.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) frame.post { placeHero() }
        }
        // The Design step's last row (the language) must clear the bar whatever arrives after the first fit — the
        // design strip's pictures, a font, a larger text size: measured again whenever the row or the bar moves
        // (13 Nov 2026, the owner's photo: the row ended under the bar). Only at the page's top, where the fit
        // measures what the eye first sees; the fit itself stops once the change is under 4 dp.
        val refit = View.OnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob ->
            if (step == 1 && (t != ot || b != ob) &&
                findViewById<android.widget.ScrollView>(R.id.posterScroll).scrollY == 0
            ) frame.post { fitDesignStep() }
        }
        toggleLang.addOnLayoutChangeListener(refit)
        btnStepNext.addOnLayoutChangeListener(refit)

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
            if (!filling) { save(KEY_TITLE, it?.toString(), occasion.titleIn(posterContext(), tradeKey())); pagesDirty = true; render() }
            if (!it.isNullOrBlank()) findViewById<TextInputLayout>(R.id.tilTitle).error = null
        }
        etMessage.doAfterTextChanged {
            if (!filling) { save(KEY_MSG, it?.toString(), occasion.messageIn(posterContext(), tradeKey())); pagesDirty = true; render() }
        }
        buildShopName()
        buildSteps()
        findViewById<View>(R.id.btnTitleOriginal).setOnClickListener {
            if (occasion.hasWords) etTitle.setText(occasion.titleIn(posterContext(), tradeKey()))
        }
        findViewById<View>(R.id.btnMessageOriginal).setOnClickListener {
            if (occasion.hasWords) etMessage.setText(occasion.messageIn(posterContext(), tradeKey()))
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
            withContext(Dispatchers.IO) { sweepShots() }
            logoMark = markOf(logo)
            profileMark = profileNow()
            loaded = true
            updateHero()
            refreshPictures()
        }
    }

    override fun onResume() {
        super.onResume()
        // The profile may have changed (a phone number added, a name, a logo).
        noPhoneNote.visibility = if (BusinessProfile.businessPhone(this).isNullOrBlank()) View.VISIBLE else View.GONE
        if (shown != null) {
            lifecycleScope.launch {
                logo = withContext(Dispatchers.IO) { BusinessProfile.loadLogo(this@PosterActivity) }
                logoMark = markOf(logo)
                buildShowGrid()
                updateHero()
                // The pages and the small pictures show the profile too: drawn again only when it changed.
                val now = profileNow()
                if (now != profileMark) {
                    profileMark = now
                    if (step == 1) refreshPictures() else pagesDirty = true
                }
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
        refreshPictures()
    }

    // ------------------------------------------------------------------ designs

    private fun buildDesignRow() {
        designRow.removeAllViews()
        for (o in PosterOccasion.offered()) {
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

    /** The shop's trade ([TradeVocab.key]): New stock's words follow it ([NewStockWords]). */
    private fun tradeKey(): String = TradeVocab.key(this)

    /** The design's short name in the app's language. */
    private fun designName(o: PosterOccasion): String = o.titleIn(this, tradeKey()).trimEnd('!', '!', '۔').let {
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
        // The new design's picture is not drawn yet; only a change of design says so (see hideLive).
        liveDrawn = false
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
        if (liveUp) {
            snapshotLive()?.let { shot ->
                // A page that still shows an older one goes back to its own picture.
                leaving?.first?.takeIf { it != occasion }?.let { notifyPage(PosterOccasion.values().indexOf(it)) }
                // Not recycled: a page may still be showing the last one; it is let go to the collector.
                leaving = occasion to shot
                notifyPage(PosterOccasion.values().indexOf(occasion))
            }
        }
        cancelReveal()
        liveUp = false
        frame.animate().cancel()
        frame.visibility = View.INVISIBLE
        // liveDrawn is NOT forgotten here. The pages also move without a change of design (a slide begun and let
        // go, the pages settling again when the poster's size is fitted to the screen): the picture is still the
        // chosen design's, and the layer comes back when the pages rest. Forgotten here, as it was until 19 Nov
        // 2026, the layer never came back until something drew the poster again and the page stood in for it:
        // with a flat page, one more way a design showed two pictures.
    }

    /** A wait for the hero's first frame is over: the design or its hero changed under it. */
    private fun cancelReveal() {
        revealing = false
        revealTurn++
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

    /**
     * Show the live layer once its picture is drawn, on Design the pages are at rest on it, and its 3D hero has
     * drawn a frame at the size it now has. Until then the layer is laid out and drawn unseen (alpha 0), which is
     * what gives the 3D view its surface; the page under it already shows the same picture, hero and all
     * ([heroShot]), so the layer coming up changes nothing the eye can see. Shown on a guess of "two frames on",
     * the layer stood for a moment with a hole where the hero is, or with the last design's hero.
     */
    private fun maybeShowLive() {
        if (!liveDrawn || liveUp || revealing) return
        if (step == 1 && (!pagerIdle || pager.currentItem != PosterOccasion.values().indexOf(occasion))) return
        revealing = true
        val turn = ++revealTurn
        frame.animate().cancel()
        frame.alpha = 0f
        frame.visibility = View.VISIBLE
        val up = Runnable {
            if (turn != revealTurn || !revealing) return@Runnable
            revealing = false
            liveUp = true
            frame.animate().alpha(1f).setDuration(120).withEndAction {
                val gone = leaving?.first ?: return@withEndAction
                leaving = null
                notifyPage(PosterOccasion.values().indexOf(gone))
            }.start()
        }
        // Two frames on: the poster has its size and the 3D view has been told of it.
        frame.postOnAnimation {
            frame.postOnAnimation {
                if (turn != revealTurn) return@postOnAnimation
                val st = stage
                if (st == null || st.liveFrameUp) {
                    up.run()
                } else {
                    // The hero's frame is in its view one screen frame after the stage drew it.
                    st.onLiveFrame = { frame.postOnAnimation { frame.postOnAnimation(up) } }
                    // A GPU that never draws must not keep the poster off the stage.
                    frame.postDelayed(up, HERO_WAIT_MS)
                }
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
                if (!pagerIdle) { pagerMoves++; hideLive() } else maybeShowLive()
            }

            override fun onPageSelected(position: Int) {
                chooseDesign(PosterOccasion.values()[position], fromPager = true)
            }
        })
    }

    /** A finished still of one design at the stage's size, drawn once and kept for a while. */
    private fun requestPage(o: PosterOccasion) {
        if (!loaded || pageW <= 0 || pages.get(o) != null || pageJobs[o]?.isActive == true) return
        val ctx = posterContext()
        val w = pageW
        val h = pageH
        val inp = pageInput(ctx, o)
        pageJobs[o] = lifecycleScope.launch {
            val hero = heroShot(o)
            val bmp = withContext(Dispatchers.Default) {
                val (full, _) = PosterImage.draw(ctx, if (hero != null) inp.copy(hero = hero) else inp)
                Bitmap.createScaledBitmap(full, w, h, true).also { if (it !== full) full.recycle() }
            }
            if (w != pageW || h != pageH) { bmp.recycle(); return@launch }
            pages.put(o, bmp)
            notifyPage(PosterOccasion.values().indexOf(o))
        }
    }

    /**
     * What a design's page and small picture show: exactly what its live poster will show once it is chosen: the
     * owner's own wording where he changed it, the design's own where he did not, the shop's lines as ticked, his
     * photo on New stock. (The rest of the pages once showed each design's own words whatever the owner had
     * written, so a design changed as it was chosen.) The 3D hero is added by the caller ([heroShot]).
     */
    private fun pageInput(ctx: Context, o: PosterOccasion): PosterImage.Input =
        if (o == occasion) input(animated = false, placeholder = true).copy(heroOverlay = false)
        else PosterImage.Input(o,
            words(KEY_TITLE, o, ctx).trim().ifEmpty { if (o == PosterOccasion.CUSTOM) ctx.getString(R.string.poster_custom_title_hint) else "" },
            words(KEY_MSG, o, ctx).trim().ifEmpty { if (o == PosterOccasion.CUSTOM) ctx.getString(R.string.poster_custom_msg_hint) else "" },
            nameShown(),
            if (fields.showPhone) BusinessProfile.businessPhone(this)?.trim() else null,
            if (fields.showAddress) BusinessProfile.businessAddress(this)?.trim() else null,
            if (fields.showLogo) logo else null,
            if (fields.showMark && Branding.ON_POSTERS_AND_CARDS) ctx.getString(R.string.made_with_app) else null,
            fields = fields,
            photo = if (usingPhoto(o)) photo else null,
            email = if (fields.showEmail) BusinessProfile.businessEmail(this)?.trim() else null,
            whatsapp = fields.onWhatsapp)

    /** Every page is drawn again: the language, name, logo or a choice changed. */
    private fun refreshPages() {
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        pages.evictAll()
        pager.post { pageAdapter.notifyDataSetChanged() }
    }

    /** The pages and the small pictures are one picture of each design: both are drawn again together. */
    private fun refreshPictures() {
        refreshPages()
        renderThumbs()
    }

    /**
     * A page shows something new. Posted, never called at once: these come from
     * the pager's own scroll and selection callbacks, and RecyclerView refuses
     * (IllegalStateException) a change while it is laying out or scrolling.
     */
    private fun notifyPage(position: Int) {
        pager.post { pageAdapter.notifyItemChanged(position) }
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

    /**
     * Small pictures of every design: the design's own page ([pageInput]) made small, with the same 3D hero
     * ([heroShot]). They were drawn with the flat stand-in while the big picture showed the 3D hero, so one design
     * was two pictures on one screen (the owner, 19 Nov 2026). A design with a 3D hero gets its small picture once
     * the hero's is taken (kept on the phone after the first time), never the flat one first; the chosen design
     * comes first.
     */
    private fun renderThumbs() {
        thumbsJob?.cancel()
        if (!loaded) return
        val ctx = posterContext()
        val w = (88 * resources.displayMetrics.density).toInt().coerceAtLeast(60)
        val h = w * PosterImage.HEIGHT / PosterImage.WIDTH
        // The chosen design first, then those with no 3D hero (drawn at once), then the rest as their heroes come.
        val order = listOf(occasion) + PosterOccasion.values().filter { it != occasion }.sortedBy { PosterDesigns.hero(it) != null }
        thumbsJob = lifecycleScope.launch {
            for (o in order) {
                val inp = pageInput(ctx, o)
                val hero = heroShot(o)
                val small = withContext(Dispatchers.Default) {
                    val (full, _) = PosterImage.draw(ctx, if (hero != null) inp.copy(hero = hero) else inp)
                    Bitmap.createScaledBitmap(full, w, h, true).also { full.recycle() }
                }
                // The old picture is never recycled, only let go to the collector: a design page still
                // being drawn shows it as its placeholder (PageAdapter), and a recycled bitmap a view
                // still draws ends the app (Play, 4 Nov 2026, Android 16: Canvas: trying to use a
                // recycled bitmap; BC-235).
                designRow.findViewWithTag<View>(o.key)?.findViewById<ImageView>(R.id.designThumb)?.setImageBitmap(small)
                thumbs.put(o, small)
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
        findViewById<View>(R.id.btnTitleOriginal).visibility = if (occasion.hasWords) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.btnMessageOriginal).visibility = if (occasion.hasWords) View.VISIBLE else View.INVISIBLE
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
            // "Also on WhatsApp" belongs to the call number; with a WhatsApp number of its own it has nothing to say.
            if (fields.whatsappNumber.isBlank()) tick(getString(R.string.poster_chip_whatsapp), fields.onWhatsapp) { f, v -> f.copy(onWhatsapp = v) }
        }
        // A WhatsApp number that is not the call number (13 Nov 2026): kept with the poster's choices, not in the profile.
        run {
            val b = layoutInflater.inflate(R.layout.item_poster_suggest, showGrid, false) as MaterialButton
            val wa = fields.whatsappNumber
            b.text = if (wa.isBlank()) getString(R.string.poster_wa_other) else getString(R.string.poster_wa_number_is, Format.ltr(wa))
            b.setOnClickListener { askWhatsappNumber() }
            place(b)
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
        // The app's own mark: off for now (Branding), and so not offered.
        if (Branding.ON_POSTERS_AND_CARDS) tick("“" + getString(R.string.made_with_app) + "”", fields.showMark) { f, v -> f.copy(showMark = v) }
        TextFit.relax(showGrid)
    }

    /**
     * One detail typed in a small window; [onSave] gets it trimmed, never blank. The rest are for a detail that is
     * kept elsewhere than the shop profile (the WhatsApp number): [initial] is filled in, [message] says where it is
     * kept, [problem] may refuse a value with its reason, and [onRemove] adds a button that takes the detail away.
     */
    private fun askValue(label: String, inputType: Int, initial: String = "", message: Int = R.string.poster_saved_to_profile,
                         title: String = getString(R.string.poster_add_named, label), problem: (String) -> Int? = { null },
                         onRemove: (() -> Unit)? = null, onSave: (String) -> Unit) {
        val box = layoutInflater.inflate(R.layout.dialog_poster_value, null)
        val til = box.findViewById<TextInputLayout>(R.id.tilValue)
        val et = box.findViewById<TextInputEditText>(R.id.etValue)
        til.hint = label
        et.inputType = inputType
        if (initial.isNotEmpty()) { et.setText(initial); et.setSelection(initial.length) }
        // A number or an email reads left to right, whatever the app's language.
        val cls = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        if (cls == android.text.InputType.TYPE_CLASS_PHONE || variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            et.textDirection = View.TEXT_DIRECTION_LTR
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(box)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        if (onRemove != null) builder.setNeutralButton(R.string.poster_wa_remove, null)
        val dialog = builder.show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val v = et.text?.toString()?.trim().orEmpty()
            if (v.isEmpty()) { til.error = getString(R.string.poster_value_needed); return@setOnClickListener }
            val why = problem(v)
            if (why != null) { til.error = getString(why); return@setOnClickListener }
            onSave(v)
            dialog.dismiss()
        }
        if (onRemove != null) dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            onRemove()
            dialog.dismiss()
        }
        et.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        // After the line above, which sets the whole mode: keep the box above the keyboard (18 Oct).
        com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
        TextFit.relax(box)
    }

    /**
     * The WhatsApp number, when it is not the call number (13 Nov 2026). Typed here and kept with the poster's choices
     * (PosterFields.whatsappNumber), not in the shop profile, so invoices and statements are as they were; the same
     * window changes it or takes it away. PosterContact decides how it is shown.
     */
    private fun askWhatsappNumber() {
        val label = getString(R.string.poster_wa_other)
        fun keep(number: String) {
            setFields(fields.copy(whatsappNumber = number))
            buildShowGrid()
        }
        askValue(label, android.text.InputType.TYPE_CLASS_PHONE, initial = fields.whatsappNumber, message = R.string.poster_wa_other_help,
            title = label,
            problem = { typed -> if (PosterContact.kept(typed).isNotEmpty()) null else R.string.poster_wa_number_bad },
            onRemove = if (fields.whatsappNumber.isBlank()) null else ({ keep("") })
        ) { typed -> keep(PosterContact.kept(typed)) }
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
            if (liveDrawn) {
                cancelReveal()
                frame.animate().cancel()
                frame.visibility = View.VISIBLE
                frame.alpha = 1f
                liveUp = true
            }
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
        val stale = step == 1 && pagesDirty
        if (pw != pageW || heightPx != pageH || stale) {
            pageW = pw; pageH = heightPx; pagesDirty = false
            refreshPages()
            // What changed on the other steps (a tick, the words, the name) shows in the small pictures too.
            if (stale) renderThumbs()
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
        findViewById<View>(R.id.stepLine1).setBackgroundColor(getColor(if (step > 1) R.color.brand_green_text else R.color.page_line))
        findViewById<View>(R.id.stepLine2).setBackgroundColor(getColor(if (step > 2) R.color.brand_green_text else R.color.page_line))
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
                // Not recycled on dismiss: the dialog is still drawn while it fades out (BC-235).
                .setOnDismissListener { iv.setImageDrawable(null) }
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
            minutes / 60, minutes % 60, com.innovation313.roshankhata.data.ShopCountry.current.clock24).show()
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

    private fun key(kind: String, o: PosterOccasion = occasion) = "${kind}${o.key}_${lang}_$shopKey"
    /** Before the poster had its own language (11 Oct) the wording was kept per design only. */
    private fun oldKey(kind: String, o: PosterOccasion = occasion) = "${kind}${o.key}_$shopKey"

    /** A design's heading ([KEY_TITLE]) or line ([KEY_MSG]): the owner's if he changed it, else the design's own in the poster's language. */
    private fun words(kind: String, o: PosterOccasion, ctx: Context): String {
        val stored = prefs.getString(key(kind, o), null)
            ?: if (lang == defaultLanguage()) prefs.getString(oldKey(kind, o), null) else null
        return stored ?: if (kind == KEY_TITLE) o.titleIn(ctx, tradeKey()) else o.messageIn(ctx, tradeKey())
    }

    /** The owner's wording if he changed it, else the design's own in the poster's language. */
    private fun fill() {
        filling = true
        val ctx = posterContext()
        etTitle.setText(words(KEY_TITLE, occasion, ctx))
        etMessage.setText(words(KEY_MSG, occasion, ctx))
        val rtl = PosterKit.urduScript(Locale.forLanguageTag(lang)) || Locale.forLanguageTag(lang).language in listOf("ar", "fa", "sd")
        val dir = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
        etTitle.textDirection = dir
        etMessage.textDirection = dir
        filling = false
        render()
    }

    /** [default] is the design's own words for this shop ([PosterOccasion.titleIn]); text equal to it is no change of the owner's. */
    private fun save(kind: String, text: String?, default: String) {
        val t = text?.trim().orEmpty()
        val e = prefs.edit()
        if (default.isNotEmpty() && t == default) e.remove(key(kind)) else e.putString(key(kind), t)
        e.apply()
    }

    /** The shop's name as this poster prints it: in Urdu script on an Urdu poster, if the owner gave one. */
    private fun shopName(): String = BusinessProfile.nameFor(this, posterUrdu())?.trim().orEmpty()

    /** In a preview a shop with no name yet shows where its name will stand; a poster is never sent without one. */
    private fun nameShown(): String = shopName().ifBlank { posterContext().getString(R.string.shop_name_field) }

    private fun usingPhoto(o: PosterOccasion = occasion) = o == PosterOccasion.NEW_STOCK && fields.usePhoto && photo != null

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
        shopName = if (placeholder) nameShown() else shopName(),
        phone = if (fields.showPhone) BusinessProfile.businessPhone(this)?.trim() else null,
        address = if (fields.showAddress) BusinessProfile.businessAddress(this)?.trim() else null,
        logo = if (fields.showLogo) logo else null,
        mark = if (fields.showMark && Branding.ON_POSTERS_AND_CARDS) posterContext().getString(R.string.made_with_app) else null,
        heroOverlay = stage != null,
        fields = fields,
        photo = if (usingPhoto()) photo else null,
        animated = animated,
        email = if (fields.showEmail) BusinessProfile.businessEmail(this)?.trim() else null,
        whatsapp = fields.onWhatsapp
    )

    // ------------------------------------------------------------------ 3D hero

    /**
     * Lay the 3D view over the hero's place on the picture, at the frame's scale. By the picture's own left
     * ([PlaceAt]): hung from the frame's start, the view stood its own left margin too far to the right in an Urdu
     * window: the clock, the mosque and the crate beside their place (the owner's phone, 19 Nov 2026).
     */
    private fun placeHero() {
        val s = frame.width / PosterImage.WIDTH.toFloat()
        if (s <= 0f) return
        val hero = stageHero ?: return
        val r = heroPlace ?: hero.rect
        heroView.layoutParams = PlaceAt.params((r.left * s).toInt(), (r.top * s).toInt(), (r.width() * s).toInt(), (r.height() * s).toInt())
    }

    /**
     * A design's 3D hero and what its look depends on (the key); null where the design is drawn flat: no hero of
     * its own, a phone without 3D, New stock with the owner's photo. The seal carries the shop's name and logo,
     * the coin the percent in the poster's language, the crate the trade's emblem.
     */
    private fun heroOf(o: PosterOccasion): Pair<Hero, String>? {
        val hero = PosterDesigns.hero(o) ?: return null
        if (usingPhoto(o) || !Studio.available(this)) return null
        val key = when (hero) {
            Hero.THANKS -> "${hero.name}|${shopName()}|${if (fields.showLogo) logoMark else "-"}"
            Hero.OFFER -> "${hero.name}|${fields.offerPercent}|$lang"
            Hero.CRATE -> "${hero.name}|${tradeKey()}"
            else -> hero.name
        }
        return hero to key
    }

    /** A stage for [hero] wearing what this shop's poster puts on it. */
    private fun newStage(hero: Hero): HeroStage {
        val face = when {
            hero.face == null -> null
            hero == Hero.OFFER -> CoinFace.draw(posterContext(), fields.offerPercent)
            hero == Hero.CRATE -> CrateFace.draw(tradeKey())
            else -> SealFace.draw(this, shopName(), if (fields.showLogo) logo else null)
        }
        return try {
            HeroStage(this, hero, face = face)
        } finally {
            face?.recycle()
        }
    }

    /**
     * Start, swap or stop the live hero for the chosen design; a phone without
     * 3D keeps the flat drawing, and New stock with the owner's photo shows the
     * photo instead. The Thank-you seal is rebuilt when its mark changes (the
     * logo, or the name in the poster's language).
     */
    private fun updateHero() {
        val want = heroOf(occasion)
        val hero = want?.first
        val wantKey = want?.second
        if (stage != null && stageKey != wantKey) {
            stage?.destroy()
            stage = null
            stageHero = null
            stageKey = null
            heroPlace = null
            // A wait for the old hero's frame would never end: the new one is waited for afresh.
            if (revealing) cancelReveal()
        }
        if (hero != null && stage == null) {
            stage = try {
                newStage(hero).also {
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

    // ------------------------------------------------------------------ the hero as a picture

    /**
     * The design's 3D hero as its live view first shows it, for the design's page and its small picture, so that a
     * design is ONE picture wherever the screen shows it. Before, the pages and the small pictures were drawn with
     * the flat stand-in and only the live layer had the 3D hero: the strip showed one mosque and the big picture
     * another, and choosing a design showed the flat one first and then changed to the 3D one (the owner, 19 Nov
     * 2026).
     *
     * Taken once on a stage of its own (never the live one, which a change of design may end under it), one at a
     * time, and kept in the app's cache folder for the next time the screen opens. A clock's picture changes with
     * the hour, so it is not kept on disk and is taken again after [CLOCK_SHOT_MS]. Null where the design is flat
     * on this phone, or the GPU would not give the picture: the flat hero is drawn then, as before.
     * Never recycled: pages and small pictures are drawn from it (BC-235).
     */
    private suspend fun heroShot(o: PosterOccasion): Bitmap? {
        val (hero, key) = heroOf(o) ?: return null
        val moving = HeroStage.moves(this)
        val clock = hero.tellsTime && moving
        fun kept(): Bitmap? = heroShots[key]
            ?.takeIf { !clock || android.os.SystemClock.elapsedRealtime() - it.at < CLOCK_SHOT_MS }?.picture
        val have = kept()
        if (have != null) return have
        if (shotsOff) return null
        return shotLock.withLock {
            val since = kept()
            if (since != null) return@withLock since
            if (shotsOff) return@withLock null
            val scale = shotScale()
            val w = (hero.rect.width() * scale).toInt().coerceAtLeast(2)
            val h = (hero.rect.height() * scale).toInt().coerceAtLeast(2)
            val file = if (clock) null else shotFile(key, moving, w, h)
            var picture: Bitmap? = file?.let { f ->
                withContext(Dispatchers.IO) { try { if (f.exists()) BitmapFactory.decodeFile(f.path) else null } catch (e: Exception) { null } }
            }
            if (picture == null) {
                var temp: HeroStage? = null
                picture = try {
                    val made = newStage(hero)
                    temp = made
                    made.asLive(w, h)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One refusal may have cost the GPU's whole wait: the rest are not asked, so no design waits for it.
                    android.util.Log.w("Poster3D", "No picture of ${hero.name}; the designs are drawn flat", e)
                    shotsOff = true
                    null
                } finally {
                    temp?.destroy()
                }
                val taken = picture
                if (taken != null && file != null) withContext(Dispatchers.IO) {
                    try {
                        FileOutputStream(file).use { taken.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } catch (e: Exception) {
                        file.delete()
                    }
                    Unit
                }
            }
            picture?.also { heroShots[key] = Shot(it, android.os.SystemClock.elapsedRealtime()) }
        }
    }

    /** The size a hero's picture is taken at: the largest the Design step shows the poster (460 dp tall). */
    private fun shotScale(): Float = (dp(460) * 9f / 16f / PosterImage.WIDTH).coerceIn(0.35f, 1f)

    private fun shotDir() = File(cacheDir, "posters/hero")

    /** Where a hero's picture is kept: by this build (a new build may draw it differently), its key and its size. */
    private fun shotFile(key: String, moving: Boolean, w: Int, h: Int): File {
        val id = java.security.MessageDigest.getInstance("SHA-1").digest("$key|$moving|${w}x$h".toByteArray())
            .joinToString("") { Integer.toHexString((it.toInt() and 0xff) + 0x100).substring(1) }.take(24)
        return File(shotDir().apply { mkdirs() }, "v${BuildConfig.VERSION_CODE}_$id.png")
    }

    /** Pictures an older build kept are let go, and all but the newest [SHOTS_KEPT] of this one's. */
    private fun sweepShots() {
        try {
            val all = shotDir().listFiles() ?: return
            val (mine, old) = all.partition { it.name.startsWith("v${BuildConfig.VERSION_CODE}_") }
            old.forEach { it.delete() }
            mine.sortedByDescending { it.lastModified() }.drop(SHOTS_KEPT).forEach { it.delete() }
        } catch (e: Exception) {
            // The cache is a convenience: the pictures are taken again.
        }
    }

    /** A logo by its look (its size and 64 of its pixels): another logo is another seal, here and in the kept pictures. */
    private fun markOf(b: Bitmap?): String {
        if (b == null || b.isRecycled || b.width < 1 || b.height < 1) return "-"
        var h = 17
        try {
            for (i in 0 until 8) for (j in 0 until 8) {
                h = h * 31 + b.getPixel((b.width * (2 * i + 1) / 16).coerceAtMost(b.width - 1), (b.height * (2 * j + 1) / 16).coerceAtMost(b.height - 1))
            }
        } catch (e: Exception) {
            // A bitmap kept on the GPU gives no pixels: its size alone names it.
        }
        return "${b.width}x${b.height}:$h"
    }

    /** What of the shop's profile a poster shows, as one text: a change of it means the pictures are drawn again. */
    private fun profileNow(): String = listOf(
        BusinessProfile.businessName(this), BusinessProfile.businessNameUrdu(this), BusinessProfile.businessPhone(this),
        BusinessProfile.businessAddress(this), BusinessProfile.businessEmail(this), logoMark
    ).joinToString("\u0001") { it.orEmpty() }

    /**
     * For the on-device proof (PosterScreenProofTest): the chosen design's page and small picture as the screen now
     * holds them, the same poster drawn with the FLAT hero at the page's size, and where the hero is on the picture.
     */
    @androidx.annotation.VisibleForTesting
    internal fun proof(): Proof {
        val page = pages.get(occasion)
        val flat = if (pageW > 0 && pageH > 0) {
            val (full, _) = PosterImage.draw(posterContext(), pageInput(posterContext(), occasion))
            Bitmap.createScaledBitmap(full, pageW, pageH, true)
        } else null
        val state = "liveUp=$liveUp liveDrawn=$liveDrawn revealing=$revealing pagerIdle=$pagerIdle item=${pager.currentItem} " +
            "stage=${stage != null} heroFrame=${stage?.liveFrameUp} shotsOff=$shotsOff page=${page != null} " +
            "pageJob=${pageJobs[occasion]?.isActive} thumb=${thumbs[occasion] != null} size=${pageW}x$pageH pagerMoves=$pagerMoves"
        return Proof(page, thumbs[occasion], flat, heroPlace ?: stageHero?.rect, liveUp, state)
    }

    internal class Proof(
        val page: Bitmap?, val thumb: Bitmap?, val flat: Bitmap?, val hero: android.graphics.RectF?, val liveUp: Boolean,
        /** What the screen was waiting for, for the proof's message when something never comes. */
        val state: String
    )

    // ------------------------------------------------------------------ picture

    /** Redraw after the owner stops typing; the old picture is let go only once the new one is up. */
    private fun render() {
        renderJob?.cancel()
        val inp = input()
        val ctx = posterContext()
        // While the owner types, wait for a pause; a new design is drawn at once.
        val wait = if (liveUp) 150L else 0L
        renderJob = lifecycleScope.launch {
            delay(wait)
            val (bmp, fit) = withContext(Dispatchers.Default) { PosterImage.draw(ctx, inp) }
            // The old picture is let go to the collector, never recycled: the frame being drawn may
            // still hold it (BC-235).
            preview.setImageBitmap(bmp)
            motionView.show(fit.motion)
            if (fit.hero != heroPlace) { heroPlace = fit.hero; placeHero() }
            shown = bmp
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
                val r = heroPlace ?: stageHero!!.rect
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
                    PosterVideo.make(base, stage, fit.hero ?: stageHero?.rect, fit.motion, VIDEO_SECONDS, out) { p ->
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
            // WhatsApp, or WhatsApp Business; its share screen offers "My status" first. Asked by trying, not by
            // resolveActivity: on Android 11+ that sees only packages named in <queries> and answered "not installed"
            // with WhatsApp on the phone (8 Nov 2026 review, M60).
            if (toWhatsApp) {
                for (pkg in WHATSAPP) {
                    try {
                        startActivity(Intent(send).setPackage(pkg))
                        return
                    } catch (_: android.content.ActivityNotFoundException) {
                        // not this one
                    }
                }
                Toast.makeText(this, R.string.poster_whatsapp_missing, Toast.LENGTH_LONG).show()
            }
            startActivity(Intent.createChooser(send, getString(R.string.share)))
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
        // Every picture a view was given is let go to the collector, never recycled (BC-235).
        preview.setImageDrawable(null)
        shown = null
        for (i in 0 until designRow.childCount) designRow.getChildAt(i).findViewById<ImageView>(R.id.designThumb)?.setImageDrawable(null)
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
        /** How long the live layer waits for its 3D hero's first frame before it shows without it. */
        private const val HERO_WAIT_MS = 1500L
        /** A clock's picture is taken again once it is this old: the hour hand has not moved to the eye. */
        private const val CLOCK_SHOT_MS = 5 * 60_000L
        /** Hero pictures kept on the phone: 8 heroes, the coin at a few percents, the seal after a few names. */
        private const val SHOTS_KEPT = 40
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
