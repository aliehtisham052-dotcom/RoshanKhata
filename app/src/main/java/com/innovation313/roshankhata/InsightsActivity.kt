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
import com.innovation313.roshankhata.data.Profit
import com.innovation313.roshankhata.data.VoiceQuestion
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
        findViewById<View>(R.id.btnAskVoice).setOnClickListener { askByVoice() }

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { Insights.thisMonth(dao) }
            render(data)
            // Profit reads the same tables twice more (sale lines, bill lines);
            // it comes after the sale figures so the first screenful is not
            // held up by it.
            val profit = withContext(Dispatchers.IO) { Profit.thisMonth(dao) to Profit.lastMonth(dao) }
            renderProfit(profit.first, profit.second)
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

    // ---------- Ask by voice (5 Oct) ----------
    //
    // The phone's own recogniser listens (RecognizerIntent, as voice entry
    // does), so this app needs no microphone permission and never holds the
    // audio. Eight fixed questions (VoiceQuestion); the answer box always
    // says what was understood, and every answer only reads the ledger.

    private val askVoice = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (heard.isEmpty()) return@registerForActivityResult
        val hit = com.innovation313.roshankhata.data.VoiceQuestion.classify(heard)
        if (hit == null) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.vq_heard, heard.first()))
                .setMessage(R.string.vq_not_understood)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } else {
            answer(hit.first, hit.second)
        }
    }

    private fun askByVoice() {
        val tag = com.innovation313.roshankhata.data.VoiceLanguage
            .preferred(java.util.Locale.getDefault().toLanguageTag()).firstOrNull()
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            tag?.let { putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, it) }
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.vq_prompt))
        }
        try {
            askVoice.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, R.string.vq_no_recognizer, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun answer(heard: String, kind: com.innovation313.roshankhata.data.VoiceQuestion.Kind) {
        lifecycleScope.launch {
            val (label, body, open) = withContext(Dispatchers.IO) { compute(kind) }
            val box = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@InsightsActivity)
                .setTitle(getString(R.string.vq_understood, label))
                .setMessage(getString(R.string.vq_heard, heard) + "\n\n" + body)
                .setPositiveButton(android.R.string.ok, null)
            if (open != null) box.setNeutralButton(R.string.vq_open) { _, _ ->
                startActivity(android.content.Intent(this@InsightsActivity, open))
            }
            box.show()
        }
    }

    /** "To get" and "to give" are separate sums over separate people, never netted. */
    private suspend fun compute(kind: com.innovation313.roshankhata.data.VoiceQuestion.Kind): Triple<String, String, Class<*>?> {
        fun money(v: Double) = (if (v < 0) "\u2212" else "") + Format.ltr(Format.money(v))
        val c = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = c.timeInMillis
        val dayEnd = (c.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        c.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val monthStart = c.timeInMillis
        val monthEnd = (c.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 1) }.timeInMillis
        return when (kind) {
            VoiceQuestion.Kind.PROFIT_MONTH -> {
                val r = Profit.thisMonth(dao)
                Triple(getString(R.string.vq_profit_month),
                    if (r.isEmpty) getString(R.string.insights_profit_none)
                    else money(r.grossProfit) + (r.coveragePercent?.let { "\n" + getString(R.string.insights_profit_coverage, it) } ?: ""),
                    null)
            }
            VoiceQuestion.Kind.SALES_TODAY -> Triple(getString(R.string.vq_sales_today), money(dao.salesTotalBetween(dayStart, dayEnd)), null)
            VoiceQuestion.Kind.SALES_MONTH -> Triple(getString(R.string.vq_sales_month), money(dao.salesTotalBetween(monthStart, monthEnd)), null)
            VoiceQuestion.Kind.TO_GET -> Triple(getString(R.string.vq_to_get),
                money(dao.partiesWithBalanceOnce().filter { it.balance > 0 }.sumOf { it.balance }), null)
            VoiceQuestion.Kind.TO_GIVE -> Triple(getString(R.string.vq_to_give),
                money(dao.partiesWithBalanceOnce().filter { it.balance < 0 }.sumOf { -it.balance }), null)
            VoiceQuestion.Kind.WHO_OWES_MOST -> {
                val top = dao.partiesWithBalanceOnce().filter { it.balance > 0 }.sortedByDescending { it.balance }.take(5)
                Triple(getString(R.string.vq_who_owes_most),
                    if (top.isEmpty()) getString(R.string.vq_nobody)
                    else top.mapIndexed { i, p -> "${i + 1}. ${p.name} \u2014 ${money(p.balance)}" }.joinToString("\n"),
                    null)
            }
            VoiceQuestion.Kind.NOT_PAID -> Triple(getString(R.string.vq_not_paid), getString(R.string.vq_not_paid_body), FollowUpActivity::class.java)
            VoiceQuestion.Kind.EXPIRING -> Triple(getString(R.string.vq_expiring), getString(R.string.vq_expiring_body), ExpiringActivity::class.java)
        }
    }

    /**
     * Profit — sale rate minus purchase rate, line by line (see [Profit]).
     *
     * A loss is written with a minus sign in red, not hidden behind [Format.money]'s
     * absolute value. When nothing could be costed the card says why, in words,
     * instead of showing a zero the owner might take for "no profit".
     */
    private fun renderProfit(now: Profit.ProfitReport, last: Profit.ProfitReport) {
        val figure = findViewById<TextView>(R.id.tvProfit)
        val net = findViewById<TextView>(R.id.tvProfitNet)
        val lastMonth = findViewById<TextView>(R.id.tvProfitLastMonth)
        val coverage = findViewById<TextView>(R.id.tvProfitCoverage)

        fun signed(value: Double): String =
            if (value < 0) "\u2212" + Format.money(value) else Format.money(value)

        if (now.isEmpty) {
            figure.text = "\u2014"
            figure.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            net.visibility = View.GONE
            coverage.text = getString(R.string.insights_profit_none)
        } else {
            figure.text = signed(now.grossProfit)
            figure.setTextColor(ContextCompat.getColor(this,
                if (now.grossProfit < 0) R.color.bal_owed_to_me else R.color.bal_i_owe))
            net.visibility = View.VISIBLE
            net.text = getString(R.string.insights_profit_after_expenses,
                signed(now.netAfterExpenses), Format.money(now.expenses))
            val pct = now.coveragePercent
            coverage.text = if (pct == null) getString(R.string.insights_profit_none)
                else getString(R.string.insights_profit_coverage, pct)
        }

        if (last.isEmpty) {
            lastMonth.visibility = View.GONE
        } else {
            lastMonth.visibility = View.VISIBLE
            lastMonth.text = getString(R.string.insights_profit_last_month, signed(last.grossProfit))
        }

        val list = findViewById<LinearLayout>(R.id.profitList)
        val empty = findViewById<TextView>(R.id.tvNoProfitProducts)
        list.removeAllViews()
        val top = now.products.filter { it.profit > 0.0 }.take(5)
        if (top.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        val max = top.first().profit.coerceAtLeast(1.0)
        top.forEachIndexed { i, p ->
            val row = layoutInflater.inflate(R.layout.item_insight_product, list, false)
            com.innovation313.roshankhata.ui.TextFit.relax(row)
            row.findViewById<TextView>(R.id.tvRank).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.tvProductName).text = p.name
            row.findViewById<TextView>(R.id.tvProductQty).text = Format.money(p.profit)

            val share = (p.profit / max).toFloat().coerceIn(0f, 1f)
            val bar = row.findViewById<View>(R.id.bar)
            val lp = bar.layoutParams as LinearLayout.LayoutParams
            lp.weight = share
            bar.layoutParams = lp
            val spacer = row.findViewById<View>(R.id.barSpacer)
            val slp = spacer.layoutParams as LinearLayout.LayoutParams
            slp.weight = 1f - share
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
