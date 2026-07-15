package com.innovation313.roshankhata

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.innovation313.roshankhata.data.CustomerStat
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.ProductStat
import com.innovation313.roshankhata.data.Insights
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
class InsightsActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insights)
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { Insights.thisMonth(dao) }
            render(data)
        }
    }

    private fun render(data: SaleInsights) {
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

        renderProducts(data.topProducts)
        renderCustomers(data.topCustomers)
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
                setTextColor(ContextCompat.getColor(this@InsightsActivity, R.color.section_insights))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            row.addView(name)
            row.addView(amount)
            list.addView(row)
        }
    }
}
