package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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

    private const val TURN_MS = 4500L

    private class Slide(val icon: Int, val tint: Int, val title: Int, val sub: Int, val open: Class<*>)

    private val slides = listOf(
        Slide(R.drawable.ic_tile_insights, R.color.tile_insights_bg, R.string.cf_title, R.string.home_slide_cf, CashFlowActivity::class.java),
        Slide(R.drawable.ic_tile_products, R.color.tile_plans_bg, R.string.rate_list_title, R.string.home_slide_rates, RateListActivity::class.java),
        Slide(R.drawable.ic_tile_bills, R.color.tile_bills_bg, R.string.sr_title, R.string.home_slide_cheaper, SupplierRatesActivity::class.java),
        Slide(R.drawable.ic_tile_card, R.color.tile_card_bg, R.string.poster_title, R.string.home_slide_poster, PosterActivity::class.java)
    )

    private class Holder(v: View) : RecyclerView.ViewHolder(v)

    private class Adapter(private val ctx: Context) : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = slides.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(ctx).inflate(R.layout.item_home_slide, parent, false))
        override fun onBindViewHolder(h: Holder, i: Int) {
            val s = slides[i]
            TextFit.relax(h.itemView)
            h.itemView.findViewById<ImageView>(R.id.ivSlideIcon).setImageResource(s.icon)
            h.itemView.findViewById<FrameLayout>(R.id.slideIconDisc).backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, s.tint))
            h.itemView.findViewById<TextView>(R.id.tvSlideTitle).setText(s.title)
            h.itemView.findViewById<TextView>(R.id.tvSlideSub).setText(s.sub)
            h.itemView.setOnClickListener { ctx.startActivity(Intent(ctx, s.open)) }
        }
    }

    /** Wire [pager] and its [dots]; call once from the Home screen's onCreate. */
    /** The same four doors as drawer rows (7 Oct), so a slide that has turned past is never the only way in. */
    fun drawerEntries(activity: android.app.Activity): List<HomeDrawer.Entry> = slides.map { s ->
        HomeDrawer.Entry(s.icon, s.tint, s.title) { activity.startActivity(android.content.Intent(activity, s.open)) }
    }

    fun attach(pager: ViewPager2, dots: LinearLayout) {
        val ctx = pager.context
        pager.adapter = Adapter(ctx)
        pager.offscreenPageLimit = 1
        val dp = ctx.resources.displayMetrics.density
        dots.removeAllViews()
        repeat(slides.size) {
            dots.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((6 * dp).toInt(), (6 * dp).toInt()).also { lp ->
                    lp.marginStart = (3 * dp).toInt(); lp.marginEnd = (3 * dp).toInt()
                }
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_slide_dot)
            })
        }
        fun mark(active: Int) {
            for (i in 0 until dots.childCount) {
                val d = dots.getChildAt(i)
                val on = i == active
                d.layoutParams = (d.layoutParams as LinearLayout.LayoutParams).also { lp -> lp.width = ((if (on) 18 else 6) * dp).toInt() }
                d.alpha = if (on) 1f else 0.45f
            }
        }
        mark(0)

        val turn = object : Runnable {
            override fun run() {
                if (!pager.isAttachedToWindow) return
                pager.setCurrentItem((pager.currentItem + 1) % slides.size, true)
                pager.postDelayed(this, TURN_MS)
            }
        }
        val auto = Motion.enabled(ctx)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = mark(position)
            override fun onPageScrollStateChanged(state: Int) {
                if (!auto) return
                pager.removeCallbacks(turn)
                if (state == ViewPager2.SCROLL_STATE_IDLE) pager.postDelayed(turn, TURN_MS)
            }
        })
        if (auto) pager.postDelayed(turn, TURN_MS)
    }
}
