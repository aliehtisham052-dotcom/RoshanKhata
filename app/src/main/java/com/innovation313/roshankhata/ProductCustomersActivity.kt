package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.ui.ChoiceSheet
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.SeasonWindow
import com.innovation313.roshankhata.ui.PromoAdapter
import com.innovation313.roshankhata.ui.Reminder
import com.innovation313.roshankhata.ui.ScreenInsets
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.launch

/**
 * Who bought this product, and telling them it is back in.
 *
 * This is the whole of season targeting. There is no season to configure —
 * [SeasonWindow] turns "the same time last year" into a stretch of dates and
 * the book answers directly, so a shop selling into two sowings a year gets
 * both without anyone describing either.
 *
 * ONE MESSAGE AT A TIME, ON PURPOSE. There is no send-to-all button and there
 * should not be one. A hundred identical messages leaving a single number
 * within a minute is what gets that number restricted, and the owner's
 * WhatsApp is their shop. The list is the work; pressing send stays theirs.
 *
 * The message names what they actually bought and when. A shopkeeper who
 * writes "the urea you took last October is in again" is doing business; the
 * same shopkeeper sending "GREAT OFFER" to eleven hundred people is doing
 * something else, and the app should not make the second one easier.
 */
class ProductCustomersActivity : BaseActivity() {

    companion object {
        const val EXTRA_PRODUCT_ID = "productId"
        const val EXTRA_PRODUCT_NAME = "productName"
        /**
         * The days to open on, [from, to) in millis (17 Nov 2026, audit L020): Hudhud's "who bought Urea this month" opens
         * on that month, offered as a fourth window beside the screen's own three.
         */
        const val EXTRA_FROM = "fromMillis"
        const val EXTRA_TO = "toMillis"
        private const val KEY_CHOICE = "choice"
        /** The index of the asked days among the windows (after the screen's own three). */
        private const val ASKED = 3
    }

    private lateinit var adapter: PromoAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var btnWindow: MaterialButton

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var productId: Long = 0
    private var productName: String = ""

    /** Which of the windows is being asked about: the screen's three, then the days it was opened on. */
    private var choice = 0

    /** The days the screen was opened on ([EXTRA_FROM], [EXTRA_TO]), or null. */
    private var asked: SeasonWindow.Window? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_customers)
        ScreenInsets.on(this)

        productId = intent.getLongExtra(EXTRA_PRODUCT_ID, 0)
        productName = intent.getStringExtra(EXTRA_PRODUCT_NAME).orEmpty()
        val from = intent.getLongExtra(EXTRA_FROM, 0L)
        val to = intent.getLongExtra(EXTRA_TO, 0L)
        if (to > from && from > 0L) asked = SeasonWindow.Window(from, to)
        choice = savedInstanceState?.getInt(KEY_CHOICE, -1)?.takeIf { it in windowLabels.indices }
            ?: if (asked != null) ASKED else 0

        findViewById<TextView>(R.id.tvPromoTitle).text = productName
        findViewById<TextView>(R.id.tvPromoSubtitle).setText(R.string.promo_subtitle)

        tvEmpty = findViewById(R.id.tvNoPromo)
        btnWindow = findViewById(R.id.btnWindow)
        btnWindow.setOnClickListener { pickWindow() }

        adapter = PromoAdapter { party -> send(party) }
        val rv: RecyclerView = findViewById(R.id.rvPromo)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CHOICE, choice)
    }

    private val windowLabels
        get() = listOfNotNull(
            getString(R.string.window_same_season),
            getString(R.string.window_last_year),
            getString(R.string.window_ever),
            asked?.let { daysLabel(it) }
        ).toTypedArray()

    /** The asked days in words: one day, or the first and the last day ([from, to) ends the moment before [to]). */
    private fun daysLabel(w: SeasonWindow.Window): String {
        val first = Format.dateOnly(w.from)
        val last = Format.dateOnly(w.to - 1)
        return if (first == last) Format.isolate(first) else Format.isolate(first) + " \u2013 " + Format.isolate(last)
    }

    private fun pickWindow() {
        val icons = intArrayOf(R.drawable.ic_tile_season, R.drawable.ic_calendar, R.drawable.ic_list_all)
        val choices = windowLabels.mapIndexed { i, label ->
            // The season tile is a duotone (own colours); the other two take the tint.
            if (i == 0) ChoiceSheet.Option(label, icon = icons[i], iconTint = 0, tile = R.color.tile_plans_bg)
            else if (i == ASKED) ChoiceSheet.Option(label, icon = R.drawable.ic_calendar,
                iconTint = R.color.tile_insights_fg, tile = R.color.tile_insights_bg)
            else ChoiceSheet.Option(label, icon = icons[i.coerceAtMost(icons.size - 1)],
                iconTint = R.color.tile_insights_fg, tile = R.color.tile_insights_bg)
        }
        ChoiceSheet.show(this, getString(R.string.promo_window), choices, choice) { which ->
            choice = which
            load()
        }
    }

    private fun load() {
        btnWindow.text = windowLabels[choice]

        val now = System.currentTimeMillis()
        val window = when (choice) {
            0 -> SeasonWindow.sameSeasonLastYear(now)
            1 -> SeasonWindow.lastTwelveMonths(now)
            ASKED -> asked ?: SeasonWindow.everything(now)
            else -> SeasonWindow.everything(now)
        }

        lifecycleScope.launch {
            val list = dao.customersWhoBought(productId, window.from, window.to)
            adapter.submitList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * Hands WhatsApp a message about this customer's own last purchase.
     *
     * [Reminder.sendViaWhatsApp] normalises the number and opens the chat with
     * the text already in the box — the owner reads it and presses send, or
     * edits it, or backs out. Nothing leaves the phone by itself.
     */
    private fun send(party: PartyWithBalance) {
        val message = getString(R.string.promo_message, party.name, productName)
        Reminder.sendViaWhatsApp(this, party.phone, message)
    }
}
