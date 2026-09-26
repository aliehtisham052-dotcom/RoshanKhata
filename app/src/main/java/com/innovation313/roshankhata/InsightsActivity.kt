package com.innovation313.roshankhata

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.innovation313.roshankhata.data.CustomerStat
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.ProductStat
import com.innovation313.roshankhata.data.Insights
import com.innovation313.roshankhata.data.MonthSale
import com.innovation313.roshankhata.data.SaleInsights
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sale Insights — what's moving in the shop, worked out from the owner's own
 * ledger, entirely on the device.
 *
 * Nothing here leaves the phone and nothing costs anything: it is the month's
 * sales summed, ranked, and compared to last month. The screen is deliberately
 * read-only — a place to glance at, not another thing to maintain.
 */
class InsightsActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insights)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { Insights.thisMonth(dao) }
            render(data)
        }
    }

    private fun render(data: SaleInsights) {
        // Today's activity — received (green), given out (red), and entry count.
        findViewById<TextView>(R.id.tvTodayReceived).text = Format.money(data.todayReceived)
        findViewById<TextView>(R.id.tvTodayGiven).text = Format.money(data.todayGiven)
        findViewById<TextView>(R.id.tvTodayCount).text =
            resources.getQuantityString(R.plurals.entries_today, data.todayCount, data.todayCount)

        findViewById<TextView>(R.id.tvTotalSale).text = Format.money(data.thisMonthTotal)

        // Trend line: green up / red down / neutral when there's no prior month.
        val trend = findViewById<TextView>(R.id.tvTrend)
        val pct = data.changePercent
        when {
            pct == null -> {
                trend.text = getString(R.string.insights_no_compare)
                trend.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
            pct >= 0 -> {
                trend.text = getString(R.string.insights_up, pct, Format.money(data.lastMonthTotal))
                trend.setTextColor(ContextCompat.getColor(this, R.color.bal_i_owe))
            }
            else -> {
                trend.text = getString(R.string.insights_down, -pct, Format.money(data.lastMonthTotal))
                trend.setTextColor(ContextCompat.getColor(this, R.color.bal_owed_to_me))
            }
        }

        renderMonths(data.monthlySales)
        renderProducts(data.topProducts)
        renderCustomers(data.topCustomers)
    }

    /**
     * The last twelve months as twelve bars.
     *
     * Twelve amounts cannot be printed side by side on a phone — each column is
     * about thirty density points wide — so the chart carries shape only, and
     * the figure for one month at a time is written underneath. It opens on the
     * best month, because that is the one the owner came to find; tapping any
     * bar moves the figure to that month.
     */
    private fun renderMonths(months: List<MonthSale>) {
        val chart = findViewById<LinearLayout>(R.id.monthChart)
        val pick = findViewById<TextView>(R.id.tvMonthPick)
        val hint = findViewById<TextView>(R.id.tvMonthHint)
        val empty = findViewById<TextView>(R.id.tvNoMonthly)
        chart.removeAllViews()

        val best = months.maxByOrNull { it.total }
        // A year of zeros is not a chart. Say so in words instead of drawing
        // twelve empty columns that look like a fault in the screen.
        if (best == null || best.total <= 0.0) {
            chart.visibility = View.GONE
            pick.visibility = View.GONE
            hint.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }
        chart.visibility = View.VISIBLE
        pick.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        empty.visibility = View.GONE

        val bars = ArrayList<View>(months.size)

        fun select(index: Int) {
            val m = months[index]
            pick.text = getString(R.string.insights_monthly_pick, m.fullLabel, Format.money(m.total))
            bars.forEachIndexed { i, bar ->
                bar.setBackgroundResource(
                    if (i == index) R.drawable.bg_month_bar_selected else R.drawable.bg_month_bar
                )
            }
        }

        months.forEachIndexed { i, m ->
            val col = layoutInflater.inflate(R.layout.item_insight_month, chart, false)
            val colLp = col.layoutParams as LinearLayout.LayoutParams
            colLp.weight = 1f
            col.layoutParams = colLp

            col.findViewById<TextView>(R.id.tvMonth).text = m.shortLabel

            // A month with no sales still gets a sliver, so the reader can see
            // the month was there and was empty, rather than see nothing at all.
            val share = (m.total / best.total).toFloat().coerceAtLeast(0.015f)

            val bar = col.findViewById<View>(R.id.bar)
            val barLp = bar.layoutParams as LinearLayout.LayoutParams
            barLp.weight = share
            bar.layoutParams = barLp

            val spacer = col.findViewById<View>(R.id.barSpacer)
            val spacerLp = spacer.layoutParams as LinearLayout.LayoutParams
            spacerLp.weight = (1f - share).coerceAtLeast(0f)
            spacer.layoutParams = spacerLp

            col.setOnClickListener { select(i) }
            bars.add(bar)
            chart.addView(col)
        }

        select(months.indexOf(best))
    }

    private fun renderProducts(products: List<ProductStat>) {
        val list = findViewById<LinearLayout>(R.id.productList)
        val empty = findViewById<TextView>(R.id.tvNoProducts)
        list.removeAllViews()

        if (products.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        // The tallest bar is the top seller; the rest are drawn relative to it,
        // so the ranking is visible at a glance, not just readable.
        val max = products.first().qty.coerceAtLeast(1.0)

        products.forEachIndexed { i, p ->
            val row = layoutInflater.inflate(R.layout.item_insight_product, list, false)
            com.innovation313.roshankhata.ui.TextFit.relax(row)
            row.findViewById<TextView>(R.id.tvRank).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.tvProductName).text = p.name
            row.findViewById<TextView>(R.id.tvProductQty).text = Format.qty(p.qty, p.unit)

            val bar = row.findViewById<View>(R.id.bar)
            val lp = bar.layoutParams as LinearLayout.LayoutParams
            lp.weight = (p.qty / max).toFloat()
            bar.layoutParams = lp

            val spacer = row.findViewById<View>(R.id.barSpacer)
            val slp = spacer.layoutParams as LinearLayout.LayoutParams
            slp.weight = (1f - (p.qty / max).toFloat()).coerceAtLeast(0f)
            spacer.layoutParams = slp

            list.addView(row)
        }
    }

    private fun renderCustomers(customers: List<CustomerStat>) {
        val list = findViewById<LinearLayout>(R.id.customerList)
        val empty = findViewById<TextView>(R.id.tvNoCustomers)
        list.removeAllViews()

        if (customers.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        customers.forEach { c ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 14, 0, 14)
            }
            val name = TextView(this).apply {
                text = c.name
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@InsightsActivity, R.color.ink))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val amount = TextView(this).apply {
                text = Format.money(c.total)
                textSize = 14f
                gravity = Gravity.END
                setTextColor(ContextCompat.getColor(this@InsightsActivity, R.color.section_insights_text))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            row.addView(name)
            row.addView(amount)
            list.addView(row)
        }
    }
}
