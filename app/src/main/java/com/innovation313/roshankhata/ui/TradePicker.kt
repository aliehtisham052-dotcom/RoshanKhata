package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.OtherTrade
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.data.TradeVocab

/**
 * "What does your shop sell?" - the one place the trade is chosen (9 Oct).
 *
 * The trade decides the examples in every hint, the units offered first,
 * the product types, a new book's starter goods, and which trade-only
 * features show (crop seasons, spray advice, inspector reports). It is
 * asked where a book begins - the first run and "Add business" - and
 * Profile -> Shop type opens the same page.
 *
 * A full page rather than a radio list: the list read like a system
 * dialog at the one moment the app first asks the owner about his
 * business. Each trade is a card with its own icon and what such a shop
 * sells ("Milk · yogurt"), so the owner finds his trade at a glance. A tap
 * only marks a card; Continue decides, so a stray tap while scrolling
 * cannot set the wrong trade.
 *
 * Not cancelable when [required]: a new book must have a trade, and
 * "General shop" is always there for one that fits none.
 */
object TradePicker {

    fun show(
        activity: Activity,
        current: Trade?,
        required: Boolean,
        /** Which first-run step this is (2 of 2), or null anywhere else. */
        step: Int? = null,
        onPicked: (Trade) -> Unit
    ) {
        val dialog = Dialog(activity, R.style.Theme_RoshanKhata)
        val root = LayoutInflater.from(activity).inflate(R.layout.dialog_trade_picker, null)
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setCancelable(!required)
        bind(activity, dialog.window, root, current, required, step,
            onClose = { dialog.dismiss() },
            onPicked = { dialog.dismiss(); onPicked(it) })
        dialog.show()
    }

    /**
     * The same page as an Activity's own content (ShopTypeActivity, first
     * run): drawn in that window's first frame, so no blank sheet shows
     * between the language screen and this one - which is what a dialog
     * opened from onCreate left on screen for a moment (9 Oct).
     */
    fun bindActivity(
        activity: Activity,
        root: View,
        step: Int,
        onPicked: (Trade) -> Unit
    ) {
        bind(activity, activity.window, root, null, true, step, onClose = {}, onPicked = onPicked)
    }

    private fun bind(
        activity: Activity,
        window: android.view.Window?,
        root: View,
        current: Trade?,
        required: Boolean,
        step: Int?,
        onClose: () -> Unit,
        onPicked: (Trade) -> Unit
    ) {
        val kicker = root.findViewById<TextView>(R.id.tvTradeKicker)
        if (step != null) {
            StepDots.show(root.findViewById(R.id.tradeStepDots), kicker, step)
        } else {
            kicker.setText(if (current == null) R.string.trade_kicker_new else R.string.trade_kicker_change)
        }
        val close = root.findViewById<View>(R.id.btnTradeClose)
        close.visibility = if (required) View.GONE else View.VISIBLE
        close.setOnClickListener { onClose() }

        val go = root.findViewById<MaterialButton>(R.id.btnTradeContinue)
        if (current != null) go.setText(R.string.save)

        var chosen: Trade? = current
        go.isEnabled = chosen != null
        // Other's words while the page is open: the owner's saved ones when
        // he is changing an Other shop, else none until he writes them.
        var otherWords: OtherTrade.Words? = if (current == Trade.OTHER)
            OtherTrade.Words(OtherTrade.name(activity), OtherTrade.items(activity)) else null
        lateinit var adapter: Cards
        adapter = Cards(Trade.entries, chosen, { otherWords }) { picked ->
            if (picked == Trade.OTHER) {
                askOther(activity, otherWords) { words ->
                    otherWords = words
                    chosen = Trade.OTHER
                    adapter.choose(Trade.OTHER)
                    go.isEnabled = true
                }
            } else {
                chosen = picked
                adapter.choose(picked)
                go.isEnabled = true
            }
        }
        val list = root.findViewById<RecyclerView>(R.id.rvTrades).apply {
            layoutManager = GridLayoutManager(activity, 2)
            this.adapter = adapter
            itemAnimator = null
            // The owner's current trade, when changing it, is in view.
            current?.let { scrollToPosition(Trade.entries.indexOf(it)) }
        }

        // Search (11 Oct): a few letters of the trade's name, its examples or
        // its English name. Other is always kept, last, for a shop not found.
        val noMatch = root.findViewById<TextView>(R.id.tvTradeNoMatch)
        root.findViewById<android.widget.EditText>(R.id.etTradeSearch)?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) {
                val q = e?.toString()?.trim()?.lowercase().orEmpty()
                val shown = if (q.isEmpty()) Trade.entries else {
                    val hits = Trade.entries.filter { it != Trade.OTHER && matches(activity, it, q) }
                    hits + Trade.OTHER
                }
                adapter.submit(shown)
                noMatch.visibility = if (q.isNotEmpty() && shown.size == 1) View.VISIBLE else View.GONE
                list.scrollToPosition(0)
            }
        })

        go.setOnClickListener {
            val picked = chosen ?: return@setOnClickListener
            // The words go with the trade: setTrade / setTradeOf store them.
            if (picked == Trade.OTHER) OtherTrade.pending = otherWords ?: OtherTrade.Words(null, emptyList())
            onPicked(picked)
        }

        if (window != null) fitBars(window, root, activity)
        TextFit.relax(root)
    }

    /**
     * The page runs behind the status and navigation bars as every screen
     * does from Android 15, so the header takes the status bar on top of its
     * own padding and the footer takes the gesture bar - added to the
     * designed padding, never replacing it (the lesson in [ScreenInsets]).
     */
    private fun fitBars(window: android.view.Window, root: View, activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Below Android 15 the theme paints the status bar the Khata green;
        // the light header has to show through it instead. (Deprecated from
        // 15 only because the bars are always transparent there.)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = ContextCompat.getColor(activity, R.color.surface)
        val night = (activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val header = root.findViewById<View>(R.id.tradeHeader)
        val footer = root.findViewById<View>(R.id.tradeFooter)
        val h = intArrayOf(header.paddingStart, header.paddingTop, header.paddingEnd, header.paddingBottom)
        val f = intArrayOf(footer.paddingStart, footer.paddingTop, footer.paddingEnd, footer.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val rtl = root.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val start = if (rtl) bars.right else bars.left
            val end = if (rtl) bars.left else bars.right
            header.setPaddingRelative(h[0] + start, h[1] + bars.top, h[2] + end, h[3])
            footer.setPaddingRelative(f[0] + start, f[1], f[2] + end, f[3] + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** Whether [q] is in the trade's name, label, example line, example goods or English name. */
    private fun matches(activity: Activity, trade: Trade, q: String): Boolean {
        val words = listOf(
            activity.getString(trade.nameRes), activity.getString(trade.labelRes),
            activity.getString(trade.exampleRes), trade.name
        ) + TradeVocab.items(activity, trade) + TradeVocab.types(activity, trade)
        return words.any { it.lowercase().contains(q) }
    }

    /**
     * Other's two questions (11 Oct): what the shop is, and up to three things
     * it sells. OK hands them back; Cancel leaves the choice as it was.
     */
    private fun askOther(activity: Activity, now: OtherTrade.Words?, onDone: (OtherTrade.Words) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_other_trade, null)
        val etName = view.findViewById<android.widget.EditText>(R.id.etOtherName)
        val etItems = view.findViewById<android.widget.EditText>(R.id.etOtherItems)
        now?.name?.let { etName.setText(it) }
        now?.items?.takeIf { it.isNotEmpty() }?.let { etItems.setText(it.joinToString(", ")) }
        TextFit.relax(view)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.trade_other_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                onDone(OtherTrade.Words(etName.text.toString().trim().ifEmpty { null }, OtherTrade.split(etItems.text.toString())))
            }
            .show()
        etName.requestFocus()
    }

    private class Cards(
        private var trades: List<Trade>,
        private var chosen: Trade?,
        /** Other's words as written so far on this page, for its card. */
        private val other: () -> OtherTrade.Words?,
        private val onChoose: (Trade) -> Unit
    ) : RecyclerView.Adapter<Cards.Holder>() {

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Trade>) {
            trades = list
            notifyDataSetChanged()
        }

        /** Mark [trade] chosen (Other only once its words are written). */
        fun choose(trade: Trade) {
            val was = chosen
            chosen = trade
            was?.let { i -> trades.indexOf(i).takeIf { it >= 0 }?.let { notifyItemChanged(it) } }
            trades.indexOf(trade).takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val card: View = view.findViewById(R.id.tradeCard)
            val icon: ImageView = view.findViewById(R.id.ivTradeIcon)
            val name: TextView = view.findViewById(R.id.tvTradeName)
            val example: TextView = view.findViewById(R.id.tvTradeExample)
            val check: View = view.findViewById(R.id.ivTradeCheck)
        }

        override fun getItemCount() = trades.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trade_card, parent, false)
            TextFit.relax(view)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val trade = trades[position]
            val ctx = holder.itemView.context
            holder.icon.setImageResource(trade.iconRes)
            holder.icon.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, trade.chipRes))
            holder.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, trade.inkRes))
            // Other wears the owner's own words once written (11 Oct).
            val words = if (trade == Trade.OTHER) other() else null
            val name = words?.name ?: ctx.getString(trade.nameRes)
            val example = words?.items?.takeIf { it.isNotEmpty() }?.joinToString(" \u00b7 ") ?: ctx.getString(trade.exampleRes)
            holder.name.text = name
            holder.example.text = example

            val on = trade == chosen
            holder.card.isSelected = on
            holder.check.visibility = if (on) View.VISIBLE else View.GONE
            // Read as one control: "Dairy, Milk · yogurt, selected".
            holder.card.contentDescription = "$name, $example"
            ViewCompat.setStateDescription(
                holder.card,
                if (on) ctx.getString(R.string.trade_card_selected) else null
            )

            holder.card.setOnClickListener {
                // Other opens its questions even when chosen, so its words can be changed.
                if (chosen == trade && trade != Trade.OTHER) return@setOnClickListener
                onChoose(trade)
            }
        }
    }
}
