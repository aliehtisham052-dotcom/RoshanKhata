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
import com.innovation313.roshankhata.data.Trade

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
        onPicked: (Trade) -> Unit
    ) {
        val dialog = Dialog(activity, R.style.Theme_RoshanKhata)
        val root = LayoutInflater.from(activity).inflate(R.layout.dialog_trade_picker, null)
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setCancelable(!required)

        root.findViewById<TextView>(R.id.tvTradeKicker).setText(
            if (current == null) R.string.trade_kicker_new else R.string.trade_kicker_change
        )
        val close = root.findViewById<View>(R.id.btnTradeClose)
        close.visibility = if (required) View.GONE else View.VISIBLE
        close.setOnClickListener { dialog.dismiss() }

        val go = root.findViewById<MaterialButton>(R.id.btnTradeContinue)
        if (current != null) go.setText(R.string.save)

        var chosen: Trade? = current
        go.isEnabled = chosen != null
        val adapter = Cards(Trade.entries, chosen) { picked ->
            chosen = picked
            go.isEnabled = true
        }
        root.findViewById<RecyclerView>(R.id.rvTrades).apply {
            layoutManager = GridLayoutManager(activity, 2)
            this.adapter = adapter
            itemAnimator = null
            // The owner's current trade, when changing it, is in view.
            current?.let { scrollToPosition(Trade.entries.indexOf(it)) }
        }

        go.setOnClickListener {
            val picked = chosen ?: return@setOnClickListener
            dialog.dismiss()
            onPicked(picked)
        }

        fitBars(dialog, root, activity)
        TextFit.relax(root)
        dialog.show()
    }

    /**
     * The page runs behind the status and navigation bars as every screen
     * does from Android 15, so the header takes the status bar on top of its
     * own padding and the footer takes the gesture bar - added to the
     * designed padding, never replacing it (the lesson in [ScreenInsets]).
     */
    private fun fitBars(dialog: Dialog, root: View, activity: Activity) {
        val window = dialog.window ?: return
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

    private class Cards(
        private val trades: List<Trade>,
        private var chosen: Trade?,
        private val onChoose: (Trade) -> Unit
    ) : RecyclerView.Adapter<Cards.Holder>() {

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
            holder.name.setText(trade.nameRes)
            holder.example.setText(trade.exampleRes)

            val on = trade == chosen
            holder.card.isSelected = on
            holder.check.visibility = if (on) View.VISIBLE else View.GONE
            // Read as one control: "Dairy, Milk · yogurt, selected".
            holder.card.contentDescription = ctx.getString(trade.nameRes) + ", " + ctx.getString(trade.exampleRes)
            ViewCompat.setStateDescription(
                holder.card,
                if (on) ctx.getString(R.string.trade_card_selected) else null
            )

            holder.card.setOnClickListener {
                val was = chosen
                if (was == trade) return@setOnClickListener
                chosen = trade
                was?.let { notifyItemChanged(trades.indexOf(it)) }
                notifyItemChanged(holder.bindingAdapterPosition)
                onChoose(trade)
            }
        }
    }
}
