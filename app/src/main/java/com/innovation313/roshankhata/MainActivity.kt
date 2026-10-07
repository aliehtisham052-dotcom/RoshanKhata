package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PaymentHabit
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.ui.CoachMarkController
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Home: the net balance at the top, every feature the app has below it.
 *
 * The features used to sit behind a More menu, which in practice meant most
 * shopkeepers never learned the app had a bills book or a Zakat calculator at
 * all. Each one now has a tile on this screen and its own destination; the
 * customer ledger keeps its own screen, one tap away in the bar.
 */
class MainActivity : BaseActivity() {

    private lateinit var tvNetBalance: TextView
    private lateinit var tvHomeLate: TextView
    private var lateJob: kotlinx.coroutines.Job? = null
    private lateinit var ivEye: ImageView

    private var netBalance = 0.0
    private var totalGet = 0.0
    private var totalGive = 0.0

    /** The totals collector, so a stale one is cancelled before a new one starts. */
    private var totalsJob: Job? = null

    /** Which book the figures on screen belong to. Null until the first read. */
    private var totalsBusinessId: Long? = null

    /** Tile views by label resource — the walkthrough needs them by name. */
    private val featureViews = mutableMapOf<Int, View>()

    /**
     * The language this Home was built in. Changing it from More → Language
     * leaves this screen sitting in the back stack with its old wording, so
     * it checks on the way back and rebuilds itself if the answer moved.
     */
    private var builtInLocale: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lightHeaderBars()

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        // A reminder's screen, now that the gate (and the lock) are behind us.
        // Only the screens reminders open; anything else is ignored.
        if (savedInstanceState == null) {
            OPENABLE.firstOrNull { it.name == intent.getStringExtra(EXTRA_OPEN) }
                ?.let { startActivity(Intent(this, it)) }
        }

        tvNetBalance = findViewById(R.id.tvNetBalance)
        tvHomeLate = findViewById(R.id.tvHomeLate)
        tvHomeLate.setOnClickListener {
            startActivity(android.content.Intent(this, FollowUpActivity::class.java))
        }
        ivEye = findViewById(R.id.ivEye)

        findViewById<View>(R.id.balanceRow).setOnClickListener {
            BalancePrivacy.toggle(this)
            renderBalance()
        }

        buildFeatureGrid()
        sizeGridTail()
        maybeShowCoachMarks()

        builtInLocale = com.innovation313.roshankhata.ui.LocaleRefresh.tag(this)
    }

    /**
     * One tile in the grid: which screen it opens, and how it is labelled.
     * Kept as data so the rows below read as a list of features rather than
     * a wall of view plumbing.
     */
    private data class Feature(
        val iconRes: Int,
        val labelRes: Int,
        val destination: Class<*>,
        /** The tile's own tint, and the colour its icon is drawn in. */
        val tintRes: Int,
        val iconColorRes: Int
    )

    /**
     * Build the grid three tiles to a row.
     *
     * The tiles are inflated here rather than <include>d in the layout. An
     * <include> keeps the ids of the layout it pulls in, so twelve copies
     * would have shared one ivFeatureIcon between them — every lookup after
     * the first came back null, which is what crashed Home on open.
     */
    private fun buildFeatureGrid() {
        // Every day (7 Oct): the six a shopkeeper opens daily, in full-size
        // tiles. Everything else is a smaller tile in Business or Tools, four
        // to a row, so the screen is not a wall of sixteen equal buttons.
        val daily = listOf(
            Feature(R.drawable.ic_tile_khata, R.string.nav_khata, KhataActivity::class.java,
                R.color.tile_khata_bg, R.color.section_khata),
            Feature(R.drawable.ic_tile_cashbook, R.string.nav_cashbook, CashbookActivity::class.java,
                R.color.tile_cashbook_bg, R.color.section_cashbook),
            Feature(R.drawable.ic_tile_cheques, R.string.nav_cheques, ChequesActivity::class.java,
                R.color.tile_cheques_bg, R.color.section_cheques),
            Feature(R.drawable.ic_tile_bills, R.string.tile_bills, BillsActivity::class.java,
                R.color.tile_bills_bg, R.color.section_bills),
            Feature(R.drawable.ic_tile_plans, R.string.nav_plans, PlansActivity::class.java,
                R.color.tile_plans_bg, R.color.section_plans),
            Feature(R.drawable.ic_tile_expiring, R.string.tile_expiry, ExpiringActivity::class.java,
                R.color.tile_stock_bg, R.color.tile_stock_fg)
        )
        val business = listOf(
            // A product is born the first time its name appears on a bill, so
            // Products keeps Supplier Bills' colour.
            Feature(R.drawable.ic_tile_products, R.string.tile_products, ProductsActivity::class.java,
                R.color.tile_bills_bg, R.color.section_bills),
            Feature(R.drawable.ic_tile_invoice, R.string.nav_invoice, InvoicesActivity::class.java,
                R.color.tile_invoice_bg, R.color.tile_invoice_fg),
            Feature(R.drawable.ic_tile_insights, R.string.tile_insights, InsightsActivity::class.java,
                R.color.tile_insights_bg, R.color.tile_insights_fg),
            Feature(R.drawable.ic_tile_followup, R.string.followup_title, FollowUpActivity::class.java,
                R.color.tile_followup_bg, R.color.tile_followup_fg),
            Feature(R.drawable.ic_tile_card, R.string.tile_card, BusinessCardActivity::class.java,
                R.color.tile_card_bg, R.color.tile_card_fg),
            // Seasons are an agri shop's; another trade's grid goes without (4 Oct).
            Feature(R.drawable.ic_tile_season, R.string.tile_seasons, SeasonsActivity::class.java,
                R.color.tile_plans_bg, R.color.section_plans),
            Feature(R.drawable.ic_tile_zakat, R.string.tile_zakat, ZakatActivity::class.java,
                R.color.tile_zakat_bg, R.color.gold_accent),
            Feature(R.drawable.ic_tile_settings, R.string.tile_settings, BusinessSettingsActivity::class.java,
                R.color.tile_settings_bg, R.color.tile_settings_fg)
        ).filter {
            it.destination != SeasonsActivity::class.java ||
                com.innovation313.roshankhata.data.TradeFeatures.seasons(this)
        }
        // Tools: the calculator, the four doors the banners turn through, the bin.
        val tools = listOf(
            Feature(R.drawable.ic_tile_calc, R.string.calculator, CalculatorActivity::class.java,
                R.color.tile_calc_bg, R.color.tile_calc_fg),
            Feature(R.drawable.ic_tile_insights, R.string.cf_title, CashFlowActivity::class.java,
                R.color.tile_insights_bg, R.color.tile_insights_fg),
            Feature(R.drawable.ic_tile_products, R.string.rate_list_title, RateListActivity::class.java,
                R.color.tile_plans_bg, R.color.section_plans),
            Feature(R.drawable.ic_tile_bills, R.string.tile_rates, SupplierRatesActivity::class.java,
                R.color.tile_bills_bg, R.color.section_bills),
            Feature(R.drawable.ic_tile_card, R.string.poster_title, PosterActivity::class.java,
                R.color.tile_card_bg, R.color.tile_card_fg),
            Feature(R.drawable.ic_tile_bin, R.string.tile_bin, RecycleBinActivity::class.java,
                R.color.tile_bin_bg, R.color.tile_bin_fg)
        )

        // A read-only phone shows no tile for a screen it may not open.
        val viewer = com.innovation313.roshankhata.data.ViewerMode.isOn(this)
        fun shown(list: List<Feature>) =
            if (!viewer) list
            else list.filterNot { com.innovation313.roshankhata.data.ViewerMode.ownerOnly(it.destination) }

        featureViews.clear()
        com.innovation313.roshankhata.ui.HomeSlides.attach(findViewById(R.id.homeSlides), findViewById(R.id.homeSlideDots))
        run {
            val drawer = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
            fun entry(f: Feature) = com.innovation313.roshankhata.ui.HomeDrawer.Entry(f.iconRes, f.tintRes, f.labelRes) {
                startActivity(Intent(this, f.destination))
            }
            com.innovation313.roshankhata.ui.HomeDrawer.fill(
                this, drawer, findViewById(R.id.drawerContent),
                shown(daily).map(::entry), shown(business).map(::entry), shown(tools).map(::entry))
            findViewById<View>(R.id.tvSeeAll).setOnClickListener { drawer.openDrawer(androidx.core.view.GravityCompat.START) }
            findViewById<View>(R.id.todayStrip).setOnClickListener { startActivity(Intent(this, AlertsActivity::class.java)) }
            findViewById<TextView>(R.id.tvHomeFooter).text =
                getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + " \u00b7 " + getString(R.string.app_tagline)
            findViewById<View>(R.id.btnDrawer).setOnClickListener { drawer.openDrawer(androidx.core.view.GravityCompat.START) }
            findViewById<View>(R.id.btnAlerts).setOnClickListener { startActivity(Intent(this, AlertsActivity::class.java)) }
            // The quiet line under the summary cards (7 Oct): backup age opens
            // Backup, the open business's name opens the switcher.
            findViewById<View>(R.id.homeBackupTap).setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }
            // The ⋮ menu beside the bell, and the business card in the drawer (7 Oct).
            findViewById<View>(R.id.btnMore).setOnClickListener { com.innovation313.roshankhata.ui.MoreSheet.showMenu(this, it) }
            findViewById<View>(R.id.drawerBusinessTap).setOnClickListener {
                drawer.closeDrawers()
                startActivity(Intent(this, BusinessSwitchActivity::class.java))
            }
            // The title is the shop's name (7 Oct); tapping it switches shops too.
            findViewById<View>(R.id.tvHomeTitle).setOnClickListener { startActivity(Intent(this, BusinessSwitchActivity::class.java)) }
            onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(false) {
                override fun handleOnBackPressed() = drawer.closeDrawers()
            }.also { cb ->
                drawer.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerOpened(drawerView: View) { cb.isEnabled = true }
                    override fun onDrawerClosed(drawerView: View) { cb.isEnabled = false }
                })
            })
        }
        fillGrid(findViewById(R.id.gridDaily), shown(daily), 3, compact = false)
        fillGrid(findViewById(R.id.gridBusiness), shown(business), 4, compact = true)
        fillGrid(findViewById(R.id.gridTools), shown(tools), 4, compact = true)
        equalizeTileHeights()
    }

    /**
     * One height for every tile in both grids: the tallest tile's own.
     *
     * Each tile measures to fit its label (see createTile), so in a script
     * with tall lines a tile holding a two-line name comes out taller than
     * one holding a single word. A grid of uneven tiles reads as a mistake,
     * so just before the first frame is drawn every tile is set to the
     * tallest height and that frame is skipped — the owner only ever sees the
     * finished, even grid. In English at normal size the tallest tile is the
     * 72dp minimum, so the screen is exactly as it was before.
     */
    private fun equalizeTileHeights() {
        // Two sizes of tile since 7 Oct (full in Every day, compact in
        // Business and Tools): each size is evened out among its own kind.
        val groups = featureViews.values.groupBy { it.getTag(R.id.tile_compact) == true }.values
        if (groups.isEmpty()) return
        val anchor = findViewById<View>(R.id.gridDaily)
        anchor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                anchor.viewTreeObserver.removeOnPreDrawListener(this)
                var changed = false
                groups.forEach { tiles ->
                    val tallest = tiles.maxOf { it.height }
                    if (tallest <= 0) return@forEach
                    tiles.filter { it.layoutParams.height != tallest }.forEach { tile ->
                        tile.layoutParams = tile.layoutParams.apply { height = tallest }
                        changed = true
                    }
                }
                // Skip this frame; the next layout pass draws the even grid.
                return !changed
            }
        })
    }

    private fun fillGrid(container: LinearLayout, features: List<Feature>, columns: Int, compact: Boolean) {
        container.removeAllViews()
        features.chunked(columns).forEach { rowFeatures ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }
            rowFeatures.forEach { feature -> row.addView(createTile(feature, compact)) }

            // Pad a short last row with empty weight, so three tiles and two
            // tiles come out the same width instead of the pair stretching.
            //
            // The filler carries the same side margins as a tile. Without
            // them it was 2 x TILE_GAP_DP narrower than the slot it stands
            // in for, LinearLayout shared that space out among the real
            // tiles, and the last row came out wider than the rows above and
            // shifted out of line with them (seen on the Calculator tile, and
            // on the last two business tiles in right-to-left languages).
            val gap = (TILE_GAP_DP * resources.displayMetrics.density).toInt()
            repeat(columns - rowFeatures.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                    marginStart = gap
                    marginEnd = gap
                })
            }
            container.addView(row)
        }
    }

    private fun createTile(feature: Feature, compact: Boolean): View {
        val tile = layoutInflater.inflate(R.layout.item_home_feature, null)
        tile.setTag(R.id.tile_compact, compact)

        // Inflating against a null parent throws away every layout_* attribute
        // in the file — the margin and the height included — and replacing
        // layoutParams here finishes the job. That is why widening the gap in
        // XML twice over changed nothing on screen: the value was being
        // discarded before it was ever measured. Set here instead, where it
        // survives.
        //
        // The height is a MINIMUM now, not a fixed value. 72dp was measured
        // against Latin text: two lines of 10sp fit with room to spare. Arabic,
        // Farsi, Sindhi and Urdu are drawn in a fallback font whose lines are
        // about half as tall again, so the second line of "Business Settings"
        // or "Products & Stock" in those languages was sliced off at the
        // bottom of the card — at normal text size too, not only when enlarged.
        // Each tile now measures its own content; equalizeTileHeights() then
        // gives every tile the tallest one's height, so the grid stays even.
        val gap = (TILE_GAP_DP * resources.displayMetrics.density).toInt()
        tile.minimumHeight = ((if (compact) TILE_HEIGHT_COMPACT_DP else TILE_HEIGHT_DP) * resources.displayMetrics.density).toInt()
        if (compact) {
            // Business / Tools tiles: four to a row, a smaller disc and glyph, a smaller label.
            val d = resources.displayMetrics.density
            tile.findViewById<android.widget.FrameLayout>(R.id.featureIconDisc).layoutParams =
                tile.findViewById<android.widget.FrameLayout>(R.id.featureIconDisc).layoutParams.apply { width = (38 * d).toInt(); height = (38 * d).toInt() }
            tile.findViewById<ImageView>(R.id.ivFeatureIcon).layoutParams =
                tile.findViewById<ImageView>(R.id.ivFeatureIcon).layoutParams.apply { width = (22 * d).toInt(); height = (22 * d).toInt() }
            tile.findViewById<TextView>(R.id.tvFeatureLabel).apply {
                textSize = 10f
                setPadding((4 * d).toInt(), paddingTop, (4 * d).toInt(), paddingBottom)
            }
        }
        tile.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = gap
            marginEnd = gap
            topMargin = gap
            bottomMargin = gap
        }
        // Every tile is the same brand-green card now (set in the layout),
        // with the header's bright gold for the icon — the owner's choice, to
        // match the Khata tools. The icon set is drawn white, so it is tinted
        // here rather than thirteen files being redrawn.
        // Two-tone icon in its own colours (6 Oct) on a rounded square in the
        // tile's tint — no colour filter, the drawable carries day and night.
        tile.findViewById<ImageView>(R.id.ivFeatureIcon).setImageResource(feature.iconRes)
        tile.findViewById<android.widget.FrameLayout>(R.id.featureIconDisc).backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this@MainActivity, feature.tintRes))
        tile.findViewById<TextView>(R.id.tvFeatureLabel).setText(feature.labelRes)
        tile.setOnClickListener { startActivity(Intent(this, feature.destination)) }
        featureViews[feature.labelRes] = tile
        return tile
    }


    /**
     * The figures are re-read every time this screen comes forward, not once
     * when it is built.
     *
     * Each business is its own database FILE, and switching closes one and
     * opens another. A collector started in onCreate is bound to whichever
     * file was open then; after a switch it is listening to a shut book and
     * simply goes quiet, leaving whatever number it last read frozen on
     * screen. Clearing the task on every switch is the first guard and the
     * stronger one, but Home should not depend on someone else remembering
     * to do that — it can check for itself, and this is the screen where a
     * wrong figure is most believed.
     */
    override fun onStart() {
        super.onStart()

        // A language picked while this screen waited in the back stack does
        // not reach it by itself. Check first: if it has changed, this
        // instance is about to be replaced and there is no sense reading the
        // ledger for a screen that will not be shown.
        val before = builtInLocale
        builtInLocale = com.innovation313.roshankhata.ui.LocaleRefresh
            .refresh(this, before)
        if (before != null && before != builtInLocale) return

        observeTotals()
    }

    override fun onResume() {
        super.onResume()
        // The bell's count, from the ledger on this phone (HomeAlerts).
        lifecycleScope.launch {
            val alerts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.innovation313.roshankhata.data.HomeAlerts.compute(
                        this@MainActivity,
                        com.innovation313.roshankhata.data.KhataDatabase.get(this@MainActivity).khataDao()
                    ) { Format.money(it) }
                } catch (e: Exception) { emptyList() }
            }
            val n = alerts.size
            findViewById<TextView>(R.id.tvAlertBadge)?.apply {
                visibility = if (n > 0) View.VISIBLE else View.GONE
                text = if (n > 9) "9+" else n.toString()
            }
            paintToday(alerts)
        }
        paintHeaderLine()
        // Returning from another screen, the bar must point at Home again.
        showViewerNote()
    }

    /**
     * The light header (P2, 7 Oct) needs a light status bar with dark icons by
     * day, and the reverse by night; the theme's green bar belongs to the
     * green-headed screens.
     */
    private fun lightHeaderBars() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.header_light_top)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !night
    }

    /**
     * The Today strip (7 Oct): one chip per kind of alert, with its count —
     * "Cheques · 2", "Late · 1" — the bell's list at a glance. The "updated"
     * notice is not today's work and stays in the bell alone.
     */
    private fun paintToday(alerts: List<com.innovation313.roshankhata.data.HomeAlerts.Alert>) {
        val strip = findViewById<View>(R.id.todayStrip) ?: return
        val chips = findViewById<LinearLayout>(R.id.todayChips)
        while (chips.childCount > 1) chips.removeViewAt(chips.childCount - 1)
        data class Chip(val label: Int, val bg: Int, val fg: Int)
        // An enum is not a value: its constants must be named in full.
        val look = mapOf(
            com.innovation313.roshankhata.data.HomeAlerts.Kind.CHEQUE to Chip(R.string.nav_cheques, R.color.tile_cheques_bg, R.color.fi_teal_b),
            com.innovation313.roshankhata.data.HomeAlerts.Kind.PLAN to Chip(R.string.na_tag_late, R.color.tile_bills_bg, R.color.fi_gold_b),
            com.innovation313.roshankhata.data.HomeAlerts.Kind.BILL to Chip(R.string.tile_bills, R.color.tile_stock_bg, R.color.fi_orange_b),
            com.innovation313.roshankhata.data.HomeAlerts.Kind.EXPIRING to Chip(R.string.tile_expiry, R.color.tile_bin_bg, R.color.fi_red_b),
            com.innovation313.roshankhata.data.HomeAlerts.Kind.BACKUP to Chip(R.string.today_backup, R.color.tile_backup_bg, R.color.fi_teal_b),
            com.innovation313.roshankhata.data.HomeAlerts.Kind.BACKUP_FAILED to Chip(R.string.today_backup, R.color.tile_bin_bg, R.color.fi_red_b),
            com.innovation313.roshankhata.data.HomeAlerts.Kind.BACKUP_OTHER_PHONE to Chip(R.string.today_backup, R.color.tile_bin_bg, R.color.fi_red_b),
            com.innovation313.roshankhata.data.HomeAlerts.Kind.CASHFLOW to Chip(R.string.today_gap, R.color.tile_insights_bg, R.color.fi_blue_b)
        )
        val d = resources.displayMetrics.density
        alerts.groupBy { it.kind }.forEach { (kind, list) ->
            val c = look[kind] ?: return@forEach
            chips.addView(TextView(this).apply {
                // "Cheques · 2" when there are two; a single item is just its
                // name — "Backup · 1" read as a count of nothing (the owner's
                // note, 7 Oct).
                text = if (list.size > 1) getString(R.string.today_chip, getString(c.label), Format.ltr(list.size.toString()))
                    else getString(c.label)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, c.fg))
                background = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_today_chip)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this@MainActivity, c.bg))
                setPadding((8 * d).toInt(), (3 * d).toInt(), (8 * d).toInt(), (3 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = (6 * d).toInt() }
            })
        }
        strip.visibility = if (chips.childCount > 1) View.VISIBLE else View.GONE
    }

    /**
     * The backup age under the summary cards, and the drawer's business card
     * (7 Oct). Painted on every resume: a backup taken one screen away, or a
     * business switched, must show the moment Home is back, not after a
     * restart.
     */
    private fun paintHeaderLine() {
        val line = com.innovation313.roshankhata.data.BackupAge.line(this)
        findViewById<TextView>(R.id.tvHomeBackupAge)?.apply {
            text = line.text
            // On the light header the words are the deep gold whether stale
            // or fresh; stale is bold, fresh is not.
            setTypeface(null, if (line.stale) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        // The drawer's business card: the open shop's name, and its place
        // among the books when there is more than one.
        val saved = com.innovation313.roshankhata.data.BusinessProfile.businessName(this@MainActivity)
        val shop = if (saved.isNullOrBlank()) getString(R.string.app_name) else saved
        findViewById<TextView>(R.id.tvHomeTitle)?.text = shop
        findViewById<TextView>(R.id.tvDrawerBusiness)?.text = shop
        findViewById<TextView>(R.id.tvDrawerBusinessSub)?.apply {
            val all = Businesses.list(this@MainActivity)
            val at = all.indexOfFirst { it.id == Businesses.active(this@MainActivity).id } + 1
            text = if (all.size > 1 && at > 0)
                getString(R.string.drawer_active_business_n, Format.ltr(at.toString()), Format.ltr(all.size.toString()))
            else getString(R.string.drawer_active_business)
        }
    }

    /** On a helper's read-only phone: whose copy, how old, tap for a fresh one. */
    private fun showViewerNote() {
        val note = findViewById<TextView>(R.id.tvHomeViewer) ?: return
        val viewer = com.innovation313.roshankhata.data.ViewerMode
        if (!viewer.isOn(this)) {
            note.visibility = View.GONE
            return
        }
        val at = viewer.dataAt(this)
        note.text = getString(
            R.string.viewer_banner,
            if (at > 0L) com.innovation313.roshankhata.ui.Format.dateTime(at)
            else getString(R.string.viewer_unknown_time)
        )
        note.visibility = View.VISIBLE
        note.setOnClickListener { startActivity(Intent(this, ViewerActivity::class.java)) }
    }

    /**
     * The same figures the ledger screen shows, read straight from the
     * database so the two can never drift apart.
     *
     * This is also the app's FIRST touch of the database after launch — so
     * it is where a ledger that will not open (corruption, a failed
     * migration) surfaces. Until now that surfaced as a crash, and a crash
     * on launch made the daily copies unreachable in the exact case they
     * were built for. Caught here instead: the owner is told plainly and
     * offered the copies screen, which deliberately never needs the live
     * database to run (see SnapshotsActivity — it works on the files).
     */
    /**
     * "N customers are paying later than usual", from each debtor's own
     * payment habit — the same rule and the same order as the Follow-up
     * screen the note opens. A count only, never an amount, so it shows the
     * same with balances hidden. Started and replaced with the totals (same
     * open book); a failure here only hides the note, since the totals
     * collector is the one that reports a ledger that will not open.
     */
    private fun observeLate() {
        lateJob?.cancel()
        tvHomeLate.visibility = View.GONE
        lateJob = lifecycleScope.launch {
            try {
                val dao = KhataDatabase.get(this@MainActivity).khataDao()
                dao.observePartiesWithBalance()
                    .combine(dao.observeLedgerPoints()) { parties, points -> parties to points }
                    .combine(dao.observePromises()) { (parties, points), promiseRows ->
                        val owing = parties.filter { Money.isPositive(it.balance) }.map { it.id }.toSet()
                        val habits = PaymentHabit.forAll(points)
                        val promises = promiseRows.associate { it.partyId to it.due }
                        val now = System.currentTimeMillis()
                        val tz = java.util.TimeZone.getDefault()
                        // A customer who named a day still ahead ("after the
                        // harvest") is not counted late — same rule as Follow-up.
                        habits.keys.count { id ->
                            id in owing && com.innovation313.roshankhata.data.FollowUpRank.lateByHabit(id, habits, promises, now, tz)
                        }
                    }
                    .flowOn(Dispatchers.Default)
                    .collectLatest { late ->
                        tvHomeLate.visibility = if (late > 0) View.VISIBLE else View.GONE
                        if (late > 0) {
                            tvHomeLate.text = resources.getQuantityString(R.plurals.home_paying_late, late, late)
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                tvHomeLate.visibility = View.GONE
            }
        }
    }

    private fun observeTotals() {
        // One collector at a time. Without this, every return to Home would
        // add another, each holding its own database handle.
        totalsJob?.cancel()
        observeLate()

        // If the open book has CHANGED, clear the figures before re-reading.
        // A brief blank is honest; another shop's balance sitting there until
        // the first row arrives is not, and on this screen it would be read
        // as this shop's money.
        val openBusiness = try {
            Businesses.active(this).id
        } catch (e: Exception) {
            null
        }
        if (openBusiness != null && totalsBusinessId != null && openBusiness != totalsBusinessId) {
            totalGet = 0.0
            totalGive = 0.0
            netBalance = 0.0
            renderBalance()
        }
        totalsBusinessId = openBusiness

        totalsJob = lifecycleScope.launch {
            try {
                val dao = KhataDatabase.get(this@MainActivity).khataDao()
                dao.observePartiesWithBalance().collectLatest { parties ->
                    totalGet = parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
                    totalGive = parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
                    netBalance = totalGet - totalGive
                    renderBalance()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancellation is this screen replacing its own collector, not
                // a ledger that will not open. It is an Exception like any
                // other, so without this it would fall into the branch below
                // and accuse a perfectly healthy database — a recovery dialog
                // every time the owner came back to Home.
                throw e
            } catch (e: Exception) {
                offerRecovery()
            }
        }
    }

    /**
     * The door that must exist precisely when nothing else works. Restoring
     * a daily copy closes and replaces the database FILE — no Room open is
     * needed on the broken ledger, and today's ledger is kept first, so
     * trying a copy costs nothing even if the copy turns out damaged.
     */
    private fun offerRecovery() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.db_broken_title)
            .setMessage(R.string.db_broken_body)
            .setCancelable(false)
            .setPositiveButton(R.string.daily_copies) { _, _ ->
                startActivity(Intent(this, SnapshotsActivity::class.java))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** What each figure last showed, so a change counts from there (2 Oct). Null = not shown yet. */
    private var shownNet: Double? = null
    private var shownGet: Double? = null
    private var shownGive: Double? = null

    private fun renderBalance() {
        val hidden = BalancePrivacy.isHidden(this)
        if (hidden) {
            // Hidden figures never animate: a change chip would leak the size.
            tvNetBalance.text = BalancePrivacy.MASK
            shownNet = null; shownGet = null; shownGive = null
        } else {
            // Figures go straight to their value (5 Oct, the owner's call: a
            // count-up showed amounts that were never true). What CHANGED since
            // the last showing rises beside "to get" and "to give" as a chip —
            // each on its own figure, never a net across different people.
            // The two halves (to get / to give) and their change chips moved
            // to the Khata screen with the cards (7 Oct); Home shows the net.
            tvNetBalance.text = Format.money(netBalance)
            shownNet = netBalance; shownGet = totalGet; shownGive = totalGive
        }
        ivEye.setImageResource(
            if (hidden) R.drawable.ic_eye_closed else R.drawable.ic_eye_open
        )
    }

    /**
     * First run only: a short tour of this screen, spotlighting real tiles one
     * at a time. Never a full-screen slide — every step points at the actual
     * card the owner will tap later.
     */
    private fun maybeShowCoachMarks() {
        if (CoachMarkController.hasRun(this)) return

        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
            .getChildAt(0) as? android.view.ViewGroup ?: return

        val steps = listOfNotNull(
            // The header first — the net figure — and then every tile. One
            // card and one count for the whole tour.
            findViewById<View>(R.id.balanceRow)?.let { row ->
                CoachMarkController.Step(
                    target = row,
                    titleRes = R.string.net_balance,
                    descRes = R.string.coach_desc_balance,
                    cornerRadiusDp = 12f,
                    // Tight. "NET BALANCE" sits directly above this row, and a
                    // wider ring lights the caption along with the figure.
                    paddingDp = 3f
                )
            },
            tileStep(R.string.nav_khata, R.string.coach_title_nav_khata, R.string.coach_desc_nav_khata),
            tileStep(R.string.nav_cashbook, R.string.coach_title_nav_cashbook, R.string.coach_desc_nav_cashbook),
            tileStep(R.string.nav_cheques, R.string.coach_title_nav_cheques, R.string.coach_desc_nav_cheques),
            tileStep(R.string.tile_bills, R.string.coach_title_bills, R.string.coach_desc_bills),
            tileStep(R.string.nav_plans, R.string.coach_title_nav_plans, R.string.coach_desc_nav_plans),
            tileStep(R.string.tile_expiry, R.string.coach_title_stock, R.string.coach_desc_stock),
            tileStep(R.string.calculator, R.string.coach_title_calc, R.string.coach_desc_calc),
            tileStep(R.string.tile_insights, R.string.coach_title_insights, R.string.coach_desc_insights),
            tileStep(R.string.followup_title, R.string.coach_title_followup, R.string.coach_desc_followup),
            tileStep(R.string.tile_zakat, R.string.coach_title_zakat, R.string.coach_desc_zakat),
            tileStep(R.string.tile_card, R.string.coach_title_bizcard, R.string.coach_desc_bizcard),
            tileStep(R.string.tile_settings, R.string.coach_title_settings, R.string.coach_desc_settings),
            tileStep(R.string.tile_bin, R.string.coach_title_applock, R.string.coach_desc_applock),
            tileStep(R.string.nav_invoice, R.string.coach_title_invoice, R.string.coach_desc_invoice),
            tileStep(R.string.tile_products, R.string.coach_title_products, R.string.coach_desc_products)
        )

        if (steps.isEmpty()) return

        // The tour is a nicety, not the app. If anything in it goes wrong on a
        // device we have not seen, mark it done and carry on — a shopkeeper
        // locked out of their own ledger by a broken tutorial is far worse
        // than one who never sees the tutorial.
        root.post {
            try {
                CoachMarkController(this, root, steps, onFinished = { sizeGridTail() }).start()
            } catch (e: Exception) {
                android.util.Log.e("Home", "walkthrough failed", e)
                CoachMarkController.markRun(this)
            }
        }
    }

    private fun tileStep(labelRes: Int, titleRes: Int, descRes: Int): CoachMarkController.Step? =
        featureViews[labelRes]?.let { tile ->
            // The whole tile, icon and label together. Highlighting the icon
            // alone left its own name outside the lit area, which read as
            // pointing at half a thing.
            CoachMarkController.Step(
                target = tile,
                titleRes = titleRes,
                descRes = descRes,
                // The tile's own 18dp corner (item_home_feature). The old
                // default rounded fully, which fitted the pill tiles but would
                // leave ~8dp of each corner of a rounded-rect tile under the
                // dim.
                cornerRadiusDp = 18f,
                //
                // No padding either. The default 10dp ringed an oval that is
                // already most of a third of the screen wide, which pushed the
                // left column's spotlight off the edge. Lit at its own size,
                // the hole is the tile.
                paddingDp = 0f
            )
        }

    /**
     * Leave a screenful of room under the last row so the walkthrough can
     * scroll any tile — including the bottom ones — to the top, where there is
     * space for its card underneath.
     */
    private fun sizeGridTail() {
        val tail = findViewById<View>(R.id.gridTailSpace) ?: return
        val scroll = findViewById<View>(R.id.featureScroll) ?: return
        scroll.post {
            val params = tail.layoutParams
            // The room is the tour's alone (7 Oct). It was left in place after
            // the tour had run, so every Home ever since scrolled on past the
            // version line into most of a screen of nothing. Once the tour is
            // done — on this launch or any earlier one — the grid ends where
            // the grid ends.
            if (CoachMarkController.hasRun(this)) {
                if (params.height != 0) {
                    params.height = 0
                    tail.layoutParams = params
                }
                return@post
            }
            // Nearly a full viewport. At 0.55 the last row could only rise to
            // about the middle of the screen, so the walkthrough card — which
            // sits below its tile — had to climb over the tile to fit, hiding
            // the very thing the step was pointing at. With room to scroll the
            // whole way, the row reaches the top and the card has the rest of
            // the screen beneath it.
            params.height = (scroll.height * 0.9f).toInt().coerceAtLeast(0)
            tail.layoutParams = params
        }
    }

    companion object {
        /** Tiles per row in the feature grid. */
        private const val TILE_HEIGHT_COMPACT_DP = 62f

        /** Tile height, in dp. Set here because the layout file's value is
         *  discarded when a tile is inflated against a null parent. */
        private const val TILE_HEIGHT_DP = 72f

        /** Space around each tile, in dp. Neighbours end up twice this apart. */
        private const val TILE_GAP_DP = 5f

        /**
         * Set by the gate. MainActivity is not exported and cannot be launched
         * from outside the app, so this is a routing hint rather than a
         * security boundary — the real boundary is that the gate never starts
         * this activity until the lock has been cleared.
         */
        const val EXTRA_UNLOCKED = "unlocked"

        /** The screen a reminder notification asked for; see ReminderWorker. */
        const val EXTRA_OPEN = "open_screen"

        /** The only screens EXTRA_OPEN may name: the ones reminders open. */
        private val OPENABLE = listOf(
            AlertsActivity::class.java,
            BackupActivity::class.java,
            ChequesActivity::class.java,
            PlansActivity::class.java,
            ExpiringActivity::class.java,
            BusinessSwitchActivity::class.java
        )
    }
}
