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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.AppLock
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.ThemeMode
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.ui.CoachMarkController
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenPrivacyDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
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
    private lateinit var tvTotalGet: TextView
    private lateinit var tvTotalGive: TextView
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

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvNetBalance = findViewById(R.id.tvNetBalance)
        tvTotalGet = findViewById(R.id.tvTotalGet)
        tvTotalGive = findViewById(R.id.tvTotalGive)
        ivEye = findViewById(R.id.ivEye)

        findViewById<View>(R.id.balanceRow).setOnClickListener {
            BalancePrivacy.toggle(this)
            renderBalance()
        }

        buildFeatureGrid()
        sizeGridTail()
        setupBottomNav()
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
        val daily = listOf(
            Feature(R.drawable.ic_nav_khata, R.string.nav_khata, KhataActivity::class.java,
                R.color.tile_khata_bg, R.color.section_khata),
            Feature(R.drawable.ic_nav_cashbook, R.string.nav_cashbook, CashbookActivity::class.java,
                R.color.tile_cashbook_bg, R.color.section_cashbook),
            Feature(R.drawable.ic_nav_cheques, R.string.nav_cheques, ChequesActivity::class.java,
                R.color.tile_cheques_bg, R.color.section_cheques),
            Feature(R.drawable.ic_feature_bills, R.string.supplier_bills, BillsActivity::class.java,
                R.color.tile_bills_bg, R.color.section_bills),
            Feature(R.drawable.ic_nav_plans, R.string.nav_plans, PlansActivity::class.java,
                R.color.tile_plans_bg, R.color.section_plans),
            Feature(R.drawable.ic_feature_stock, R.string.expiring_stock, ExpiringActivity::class.java,
                R.color.tile_stock_bg, R.color.tile_stock_fg),
            Feature(R.drawable.ic_feature_calc, R.string.calculator, CalculatorActivity::class.java,
                R.color.tile_calc_bg, R.color.tile_calc_fg)
        )
        val business = listOf(
            Feature(R.drawable.ic_feature_insights, R.string.insights_title, InsightsActivity::class.java,
                R.color.tile_insights_bg, R.color.tile_insights_fg),
            Feature(R.drawable.ic_feature_followup, R.string.followup_title, FollowUpActivity::class.java,
                R.color.tile_followup_bg, R.color.tile_followup_fg),
            Feature(R.drawable.ic_feature_zakat, R.string.zakat_calculator, ZakatActivity::class.java,
                R.color.tile_zakat_bg, R.color.gold_accent),
            Feature(R.drawable.ic_feature_card, R.string.biz_card, BusinessCardActivity::class.java,
                R.color.tile_card_bg, R.color.tile_card_fg),
            Feature(R.drawable.ic_feature_settings, R.string.business_settings, BusinessSettingsActivity::class.java,
                R.color.tile_settings_bg, R.color.tile_settings_fg),
            Feature(R.drawable.ic_feature_lock, R.string.recycle_bin, RecycleBinActivity::class.java,
                R.color.tile_bin_bg, R.color.tile_bin_fg),
            Feature(R.drawable.ic_feature_invoice, R.string.nav_invoice, InvoicesActivity::class.java,
                R.color.tile_invoice_bg, R.color.tile_invoice_fg),
            // Moved off the More sheet 18 Sep 2026: once Inspector Mode gave this
            // screen its own registers (stock, batches, expiry, compliance), it
            // stopped being a settings-style afterthought. It keeps Supplier
            // Bills' own section colour because that is where its data comes
            // from — a product is born the first time its name appears on a bill.
            Feature(R.drawable.ic_feature_products, R.string.products_stock, ProductsActivity::class.java,
                R.color.tile_bills_bg, R.color.section_bills)
        )

        featureViews.clear()
        fillGrid(findViewById(R.id.gridDaily), daily)
        fillGrid(findViewById(R.id.gridBusiness), business)
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
        val tiles = featureViews.values.toList()
        if (tiles.isEmpty()) return
        val anchor = findViewById<View>(R.id.gridDaily)
        anchor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                anchor.viewTreeObserver.removeOnPreDrawListener(this)
                val tallest = tiles.maxOf { it.height }
                if (tallest <= 0) return true
                val uneven = tiles.filter { it.layoutParams.height != tallest }
                if (uneven.isEmpty()) return true
                uneven.forEach { tile ->
                    tile.layoutParams = tile.layoutParams.apply { height = tallest }
                }
                // Skip this frame; the next layout pass draws the even grid.
                return false
            }
        })
    }

    private fun fillGrid(container: LinearLayout, features: List<Feature>) {
        container.removeAllViews()
        features.chunked(COLUMNS).forEach { rowFeatures ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }
            rowFeatures.forEach { feature -> row.addView(createTile(feature)) }

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
            repeat(COLUMNS - rowFeatures.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                    marginStart = gap
                    marginEnd = gap
                })
            }
            container.addView(row)
        }
    }

    private fun createTile(feature: Feature): View {
        val tile = layoutInflater.inflate(R.layout.item_home_feature, null)

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
        tile.minimumHeight = (TILE_HEIGHT_DP * resources.displayMetrics.density).toInt()
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
        tile.findViewById<ImageView>(R.id.ivFeatureIcon).apply {
            setImageResource(feature.iconRes)
            setColorFilter(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.gold_on_dark))
        }
        tile.findViewById<TextView>(R.id.tvFeatureLabel).setText(feature.labelRes)
        tile.setOnClickListener { startActivity(Intent(this, feature.destination)) }
        featureViews[feature.labelRes] = tile
        return tile
    }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_home
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true // already here
                R.id.nav_more -> {
                    showMoreSheet()
                    false
                }
                else -> false
            }
        }
    }

    /**
     * What is left after every real feature moved onto the grid: the things
     * set once and then forgotten. Too few to earn tiles, too useful to drop.
     */
    private fun showMoreSheet() {
        // Products & stock moved onto the grid (18 Sep 2026) — it earned a tile,
        // not a line in a settings list. What is left here is genuinely the
        // set-once-and-forget kind.
        val options = arrayOf(
            getString(R.string.app_lock),
            getString(R.string.screen_privacy),
            getString(R.string.duplicate_customers),
            getString(R.string.language),
            getString(R.string.text_size),
            getString(R.string.theme),
            getString(R.string.help_support),
            getString(R.string.about_us)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.more_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppLockSettings()
                    1 -> ScreenPrivacyDialog.show(this)
                    2 -> startActivity(Intent(this, DuplicateCustomersActivity::class.java))
                    3 -> startActivity(Intent(this, LanguageActivity::class.java))
                    4 -> showTextSizeSettings()
                    5 -> showThemeSettings()
                    // Reporting a problem lives inside Help now, so there is
                    // one door marked "something is wrong" rather than two.
                    6 -> startActivity(Intent(this, HelpActivity::class.java))
                    7 -> startActivity(Intent(this, AboutActivity::class.java))
                }
            }
            .show()
    }

    /**
     * The owner's own text size (see [TextSize]). Home is the bottom of the
     * back stack — every other screen is opened from it — so rebuilding Home
     * is enough: each screen opened afterwards is built at the new size.
     */
    private fun showTextSizeSettings() {
        val labels = arrayOf(
            getString(R.string.text_size_small),
            getString(R.string.text_size_normal),
            getString(R.string.text_size_large),
            getString(R.string.text_size_largest)
        )
        val current = TextSize.level(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.text_size)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which != current) {
                    TextSize.setLevel(this, which)
                    recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Light, dark, or the phone's own setting (see [ThemeMode]). No recreate()
     * here: setDefaultNightMode rebuilds every open screen by itself.
     */
    private fun showThemeSettings() {
        val labels = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )
        val current = ThemeMode.get(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which != current) ThemeMode.set(this, which)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Mirrors the ledger screen's own dialog, so App Lock reads the same from either side. */
    private fun showAppLockSettings() {
        if (AppLock.noneEnrolled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_lock)
                .setMessage(R.string.app_lock_no_screen_lock)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        if (!AppLock.isAvailable(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_lock)
                .setMessage(R.string.app_lock_unavailable)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val enabled = AppLock.isEnabled(this)
        val status = getString(
            if (enabled) R.string.app_lock_enabled else R.string.app_lock_disabled
        )
        val message = status + "\n\n" + getString(R.string.app_lock_explain)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_lock)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(
                if (enabled) R.string.app_lock_turn_off else R.string.app_lock_turn_on
            ) { _, _ ->
                AppLock.setEnabled(this, !enabled)
                Toast.makeText(
                    this,
                    if (!enabled) R.string.app_lock_enabled else R.string.app_lock_disabled,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
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
        // Returning from another screen, the bar must point at Home again.
        findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.nav_home
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
    private fun observeTotals() {
        // One collector at a time. Without this, every return to Home would
        // add another, each holding its own database handle.
        totalsJob?.cancel()

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

    private fun renderBalance() {
        val hidden = BalancePrivacy.isHidden(this)
        tvNetBalance.text = if (hidden) BalancePrivacy.MASK else Format.money(netBalance)
        tvTotalGet.text = if (hidden) BalancePrivacy.MASK else Format.money(totalGet)
        tvTotalGive.text = if (hidden) BalancePrivacy.MASK else Format.money(totalGive)
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
            // The header first, in three short steps — the net figure, then
            // each of its two halves — and then every tile. One card and one
            // count ("4 / 18") for the whole tour.
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
            findViewById<View>(R.id.homeCardGet)?.let { card ->
                CoachMarkController.Step(
                    target = card,
                    titleRes = R.string.i_have_to_get,
                    descRes = R.string.coach_desc_get,
                    cornerRadiusDp = 14f,
                    paddingDp = 3f
                )
            },
            findViewById<View>(R.id.homeCardGive)?.let { card ->
                CoachMarkController.Step(
                    target = card,
                    titleRes = R.string.i_have_to_give,
                    descRes = R.string.coach_desc_give,
                    cornerRadiusDp = 14f,
                    paddingDp = 3f
                )
            },
            tileStep(R.string.nav_khata, R.string.coach_title_nav_khata, R.string.coach_desc_nav_khata),
            tileStep(R.string.nav_cashbook, R.string.coach_title_nav_cashbook, R.string.coach_desc_nav_cashbook),
            tileStep(R.string.nav_cheques, R.string.coach_title_nav_cheques, R.string.coach_desc_nav_cheques),
            tileStep(R.string.supplier_bills, R.string.coach_title_bills, R.string.coach_desc_bills),
            tileStep(R.string.nav_plans, R.string.coach_title_nav_plans, R.string.coach_desc_nav_plans),
            tileStep(R.string.expiring_stock, R.string.coach_title_stock, R.string.coach_desc_stock),
            tileStep(R.string.calculator, R.string.coach_title_calc, R.string.coach_desc_calc),
            tileStep(R.string.insights_title, R.string.coach_title_insights, R.string.coach_desc_insights),
            tileStep(R.string.followup_title, R.string.coach_title_followup, R.string.coach_desc_followup),
            tileStep(R.string.zakat_calculator, R.string.coach_title_zakat, R.string.coach_desc_zakat),
            tileStep(R.string.biz_card, R.string.coach_title_bizcard, R.string.coach_desc_bizcard),
            tileStep(R.string.business_settings, R.string.coach_title_settings, R.string.coach_desc_settings),
            tileStep(R.string.recycle_bin, R.string.coach_title_applock, R.string.coach_desc_applock),
            tileStep(R.string.nav_invoice, R.string.coach_title_invoice, R.string.coach_desc_invoice),
            tileStep(R.string.products_stock, R.string.coach_title_products, R.string.coach_desc_products)
        )

        if (steps.isEmpty()) return

        // The tour is a nicety, not the app. If anything in it goes wrong on a
        // device we have not seen, mark it done and carry on — a shopkeeper
        // locked out of their own ledger by a broken tutorial is far worse
        // than one who never sees the tutorial.
        root.post {
            try {
                CoachMarkController(this, root, steps).start()
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
        private const val COLUMNS = 3

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
    }
}
