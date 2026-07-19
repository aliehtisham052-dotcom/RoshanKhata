package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.AppLock
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.ui.CoachMarkController
import com.innovation313.roshankhata.ui.Format
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
class MainActivity : AppCompatActivity() {

    private lateinit var tvNetBalance: TextView
    private lateinit var tvTotalGet: TextView
    private lateinit var tvTotalGive: TextView
    private lateinit var ivEye: ImageView

    private var netBalance = 0.0
    private var totalGet = 0.0
    private var totalGive = 0.0

    /** Tile views by label resource — the walkthrough needs them by name. */
    private val featureViews = mutableMapOf<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
        observeTotals()
        maybeShowCoachMarks()
    }

    /**
     * One tile in the grid: which screen it opens, and how it is labelled.
     * Kept as data so the rows below read as a list of features rather than
     * a wall of view plumbing.
     */
    private data class Feature(
        val iconRes: Int,
        val labelRes: Int,
        val destination: Class<*>
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
            Feature(R.drawable.ic_nav_khata, R.string.nav_khata, KhataActivity::class.java),
            Feature(R.drawable.ic_nav_cashbook, R.string.nav_cashbook, CashbookActivity::class.java),
            Feature(R.drawable.ic_nav_cheques, R.string.nav_cheques, ChequesActivity::class.java),
            Feature(R.drawable.ic_feature_bills, R.string.supplier_bills, BillsActivity::class.java),
            Feature(R.drawable.ic_nav_plans, R.string.nav_plans, PlansActivity::class.java),
            Feature(R.drawable.ic_feature_stock, R.string.expiring_stock, ExpiringActivity::class.java)
        )
        val business = listOf(
            Feature(R.drawable.ic_feature_insights, R.string.insights_title, InsightsActivity::class.java),
            Feature(R.drawable.ic_feature_zakat, R.string.zakat_calculator, ZakatActivity::class.java),
            Feature(R.drawable.ic_feature_card, R.string.biz_card, BusinessCardActivity::class.java),
            Feature(R.drawable.ic_feature_backup, R.string.backup_restore, BackupActivity::class.java),
            Feature(R.drawable.ic_feature_settings, R.string.business_settings, BusinessSettingsActivity::class.java),
            Feature(R.drawable.ic_feature_lock, R.string.recycle_bin, RecycleBinActivity::class.java)
        )

        featureViews.clear()
        fillGrid(findViewById(R.id.gridDaily), daily)
        fillGrid(findViewById(R.id.gridBusiness), business)
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
            repeat(COLUMNS - rowFeatures.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
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
        val gap = (TILE_GAP_DP * resources.displayMetrics.density).toInt()
        tile.layoutParams = LinearLayout.LayoutParams(
            0,
            (TILE_HEIGHT_DP * resources.displayMetrics.density).toInt(),
            1f
        ).apply {
            marginStart = gap
            marginEnd = gap
            topMargin = gap
            bottomMargin = gap
        }
        tile.findViewById<ImageView>(R.id.ivFeatureIcon).apply {
            setImageResource(feature.iconRes)
            // The icon set was drawn white for the old dark nav bar. On a white
            // tile that is white on white — the icons were there all along and
            // simply could not be seen. Tint them to the brand green here
            // rather than redrawing twelve files.
            setColorFilter(
                androidx.core.content.ContextCompat.getColor(
                    this@MainActivity, R.color.brand_green
                )
            )
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
        val options = arrayOf(
            getString(R.string.app_lock),
            getString(R.string.language),
            getString(R.string.report_problem)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.more_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppLockSettings()
                    1 -> startActivity(Intent(this, LanguageActivity::class.java))
                    2 -> startActivity(Intent(this, ReportProblemActivity::class.java))
                }
            }
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

    override fun onResume() {
        super.onResume()
        // Returning from another screen, the bar must point at Home again.
        findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.nav_home
    }

    /**
     * The same figures the ledger screen shows, read straight from the
     * database so the two can never drift apart.
     */
    private fun observeTotals() {
        val dao = KhataDatabase.get(this).khataDao()
        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest { parties ->
                totalGet = parties.filter { it.balance > 0 }.sumOf { it.balance }
                totalGive = parties.filter { it.balance < 0 }.sumOf { -it.balance }
                netBalance = totalGet - totalGive
                renderBalance()
            }
        }
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
            findViewById<View>(R.id.balanceRow)?.let { row ->
                CoachMarkController.Step(
                    target = row,
                    titleRes = R.string.coach_title_balance,
                    descRes = R.string.coach_desc_balance,
                    // Tight. "Net balance" sits directly above this row, and a
                    // wider ring lights the caption along with the figure.
                    paddingDp = 2f
                )
            },
            tileStep(R.string.nav_khata, R.string.coach_title_nav_khata, R.string.coach_desc_nav_khata),
            tileStep(R.string.nav_cashbook, R.string.coach_title_nav_cashbook, R.string.coach_desc_nav_cashbook),
            tileStep(R.string.nav_cheques, R.string.coach_title_nav_cheques, R.string.coach_desc_nav_cheques),
            tileStep(R.string.supplier_bills, R.string.coach_title_bills, R.string.coach_desc_bills),
            tileStep(R.string.nav_plans, R.string.coach_title_nav_plans, R.string.coach_desc_nav_plans),
            tileStep(R.string.expiring_stock, R.string.coach_title_stock, R.string.coach_desc_stock),
            tileStep(R.string.insights_title, R.string.coach_title_insights, R.string.coach_desc_insights),
            tileStep(R.string.zakat_calculator, R.string.coach_title_zakat, R.string.coach_desc_zakat),
            tileStep(R.string.biz_card, R.string.coach_title_bizcard, R.string.coach_desc_bizcard),
            tileStep(R.string.backup_restore, R.string.coach_title_backup, R.string.coach_desc_backup),
            tileStep(R.string.recycle_bin, R.string.coach_title_applock, R.string.coach_desc_applock)
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
                // No radius given: the default rounds fully, and the overlay
                // clamps it to half the shorter side — so the lit shape
                // follows the tile, which is now a round one.
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
            params.height = (scroll.height * 0.55f).toInt().coerceAtLeast(0)
            tail.layoutParams = params
        }
    }

    companion object {
        /** Tiles per row in the feature grid. */
        private const val COLUMNS = 3

        /** Tile height, in dp. Set here because the layout file's value is
         *  discarded when a tile is inflated against a null parent. */
        private const val TILE_HEIGHT_DP = 86f

        /** Space around each tile, in dp. Neighbours end up twice this apart. */
        private const val TILE_GAP_DP = 8f

        /**
         * Set by the gate. MainActivity is not exported and cannot be launched
         * from outside the app, so this is a routing hint rather than a
         * security boundary — the real boundary is that the gate never starts
         * this activity until the lock has been cleared.
         */
        const val EXTRA_UNLOCKED = "unlocked"
    }
}
