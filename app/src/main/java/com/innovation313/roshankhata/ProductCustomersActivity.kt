package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
class ProductCustomersActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCT_ID = "productId"
        const val EXTRA_PRODUCT_NAME = "productName"
    }

    private lateinit var adapter: PromoAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var btnWindow: MaterialButton

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var productId: Long = 0
    private var productName: String = ""

    /** Which of the three windows is being asked about. */
    private var choice = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_customers)
        ScreenInsets.on(this)

        productId = intent.getLongExtra(EXTRA_PRODUCT_ID, 0)
        productName = intent.getStringExtra(EXTRA_PRODUCT_NAME).orEmpty()

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

    private val windowLabels
        get() = arrayOf(
            getString(R.string.window_same_season),
            getString(R.string.window_last_year),
            getString(R.string.window_ever)
        )

    private fun pickWindow() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.promo_window)
            .setSingleChoiceItems(windowLabels, choice) { dialog, which ->
                choice = which
                dialog.dismiss()
                load()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun load() {
        btnWindow.text = windowLabels[choice]

        val now = System.currentTimeMillis()
        val window = when (choice) {
            0 -> SeasonWindow.sameSeasonLastYear(now)
            1 -> SeasonWindow.lastTwelveMonths(now)
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
