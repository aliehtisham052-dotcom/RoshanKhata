package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.innovation313.roshankhata.CashFlowActivity
import com.innovation313.roshankhata.PosterActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.RateListActivity
import com.innovation313.roshankhata.SupplierRatesActivity

/**
 * The Home slider (6 Oct): four cards, each a feature worth a look, turning
 * on their own every few seconds and stopping while a finger is on them.
 * Nothing here is an advertisement: every card opens a screen of this app.
 * With phone animations off the cards wait for a swipe.
 */
object HomeSlides {

    private const val TURN_MS = 3000L

    /** Loop: the pager holds a very long run of the four banners and starts in the middle, so there is always a next and a previous. */
    private const val LOOP = 4000

    /** [ground] is the banner's gradient, [label] the colour of the pill's text — the deep tone of the same hue. */
    private class Slide(val icon: Int, val tint: Int, val title: Int, val sub: Int, val open: Class<*>, val ground: Int, val label: Int)

    private val slides = listOf(
        Slide(R.drawable.ic_tile_insights, R.color.tile_insights_bg, R.string.cf_title, R.string.home_slide_cf, CashFlowActivity::class.java, R.drawable.bg_banner_blue, R.color.fi_blue_b),
        Slide(R.drawable.ic_tile_products, R.color.tile_plans_bg, R.string.rate_list_title, R.string.home_slide_rates, RateListActivity::class.java, R.drawable.bg_banner_green, R.color.fi_green_b),
        Slide(R.drawable.ic_tile_bills, R.color.tile_bills_bg, R.string.sr_title, R.string.home_slide_cheaper, SupplierRatesActivity::class.java, R.drawable.bg_banner_gold, R.color.fi_gold_b),
        Slide(R.drawable.ic_tile_card, R.color.tile_card_bg, R.string.poster_title, R.string.home_slide_poster, PosterActivity::class.java, R.drawable.bg_banner_violet, R.color.fi_violet_b)
    )

    private class Holder(v: View) : RecyclerView.ViewHolder(v)

    private class Adapter(private val ctx: Context) : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = slides.size * LOOP
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(ctx).inflate(R.layout.item_home_slide, parent, false))
        override fun onBindViewHolder(h: Holder, i: Int) {
            val s = slides[i % slides.size]
            TextFit.relax(h.itemView)
            h.itemView.findViewById<View>(R.id.slideRoot).background = ContextCompat.getDrawable(ctx, s.ground)
            h.itemView.findViewById<ImageView>(R.id.ivSlideIcon).setImageResource(s.icon)
            h.itemView.findViewById<TextView>(R.id.tvSlideSub).setText(s.sub)
            h.itemView.findViewById<TextView>(R.id.tvSlideTitle).apply {
                text = ctx.getString(s.title) + "  \u2192"
                // The pill's label is the feature's own deep tone by day and
                // its light tone by night (fi_*_b) — but the pill itself is
                // white in both, so by night the light tone would vanish on
                // it. Day colours only, read from the default resources.
                setTextColor(dayColour(ctx, s.label))
            }
            h.itemView.setOnClickListener { ctx.startActivity(Intent(ctx, s.open)) }
        }

        /** A colour as values/ (not values-night/) defines it: the banner is a poster, the same in both modes. */
        private fun dayColour(ctx: Context, res: Int): Int {
            val conf = android.content.res.Configuration(ctx.resources.configuration)
            conf.uiMode = (conf.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                android.content.res.Configuration.UI_MODE_NIGHT_NO
            return ContextCompat.getColor(ctx.createConfigurationContext(conf), res)
        }
    }

    /** Wire [pager]; call once from the Home screen's onCreate. No dots (7 Oct): the banners loop, and a row of dots read as "how many more?". */
    fun attach(pager: ViewPager2) {
        val ctx = pager.context
        pager.adapter = Adapter(ctx)
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(slides.size * (LOOP / 2), false)

        val turn = object : Runnable {
            override fun run() {
                if (!pager.isAttachedToWindow) return
                pager.setCurrentItem(pager.currentItem + 1, true)
                pager.postDelayed(this, TURN_MS)
            }
        }
        val auto = Motion.enabled(ctx)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (!auto) return
                pager.removeCallbacks(turn)
                if (state == ViewPager2.SCROLL_STATE_IDLE) pager.postDelayed(turn, TURN_MS)
            }
        })
        if (auto) pager.postDelayed(turn, TURN_MS)
    }
}
