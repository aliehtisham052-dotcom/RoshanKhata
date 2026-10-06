package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.innovation313.roshankhata.data.CashFlow
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Cash flow — money promised in and due out over the next 30, 60 or 90 days,
 * week by week, with the first week that runs short called out (see CashFlow).
 * Reads only, so it opens on the helper's phone too.
 */
class CashFlowActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }
    private var days = 30
    private var all: List<CashFlow.Item> = emptyList()
    private var undated = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cash_flow)
        ScreenInsets.on(this)

        val group = findViewById<ChipGroup>(R.id.chipCfDays)
        listOf(30, 60, 90).forEach { d ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = getString(R.string.cf_days, d.toString())
            chip.tag = d
            chip.isChecked = d == days
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val c = ids.firstOrNull()?.let { g.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            days = c.tag as Int
            render()
        }

        lifecycleScope.launch {
            val (items, n) = withContext(Dispatchers.IO) {
                val parties = dao.partiesWithBalanceOnce()
                val names = parties.associate { it.id to it.name }
                val balance = parties.associate { it.id to it.balance }
                val plans = dao.openPlansOnce().map {
                    CashFlow.Plan(it.partyId, it.partyName, it.totalAmount - it.paidSoFar, it.installmentAmount, it.nextDueDate)
                }
                val cheques = dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && names.containsKey(it.partyId) }.map {
                    CashFlow.Cheque(it.partyId, names.getValue(it.partyId), it.amount, it.dueDate, it.isReceived)
                }
                val harvests = dao.cashFlowHarvestsOnce().map { it.copy(balance = balance[it.partyId] ?: 0.0) }
                // Negative balance = the shop owes that party (Entities.PartyWithBalance).
                val owed = parties.filter { it.balance < 0 }.associate { it.id to -it.balance }
                CashFlow.items(plans, cheques, harvests, dao.cashFlowBillsOnce(), owed)
            }
            all = items
            undated = n
            render()
        }
    }

    private fun today(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun money(v: Double) = Format.ltr(Format.money(v))

    private fun kind(k: CashFlow.Kind) = getString(when (k) {
        CashFlow.Kind.PLAN -> R.string.cf_kind_plan
        CashFlow.Kind.CHEQUE_IN -> R.string.cf_kind_cheque_in
        CashFlow.Kind.HARVEST -> R.string.cf_kind_harvest
        CashFlow.Kind.BILL -> R.string.cf_kind_bill
        CashFlow.Kind.CHEQUE_OUT -> R.string.cf_kind_cheque_out
    })

    private fun row(parent: LinearLayout, title: String, sub: String?, amount: String?, colour: Int?) {
        val v = layoutInflater.inflate(R.layout.item_cashflow_row, parent, false)
        com.innovation313.roshankhata.ui.TextFit.relax(v)
        v.findViewById<TextView>(R.id.tvCfTitle).text = title
        v.findViewById<TextView>(R.id.tvCfSub).apply { text = sub.orEmpty(); visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE }
        v.findViewById<TextView>(R.id.tvCfAmount).apply {
            text = amount.orEmpty()
            if (colour != null) setTextColor(ContextCompat.getColor(this@CashFlowActivity, colour))
        }
        parent.addView(v)
    }

    private fun heading(parent: LinearLayout, text: String) {
        val v = layoutInflater.inflate(R.layout.item_cashflow_row, parent, false)
        v.findViewById<TextView>(R.id.tvCfTitle).apply { this.text = text; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        v.findViewById<View>(R.id.tvCfSub).visibility = View.GONE
        parent.addView(v)
    }

    private fun render() {
        val o = CashFlow.outlook(all, undated, today(), days)
        findViewById<TextView>(R.id.tvCfIn).text = money(o.coming)
        findViewById<TextView>(R.id.tvCfOut).text = money(o.going)
        findViewById<TextView>(R.id.tvCfWarning).text = o.gapWeek?.let {
            getString(R.string.cf_gap, DateWords.format("d MMM", it.from), money(o.gap))
        } ?: getString(R.string.cf_no_gap)

        val weeks = findViewById<LinearLayout>(R.id.cfWeeks)
        weeks.removeAllViews()
        o.weeks.forEach { w ->
            val label = getString(R.string.cf_week, DateWords.format("d MMM", w.from), DateWords.format("d MMM", w.to - 1))
            row(weeks, label, getString(R.string.cf_in) + " " + money(w.coming), getString(R.string.cf_out) + " " + money(w.going),
                if (w == o.gapWeek) R.color.bal_owed_to_me else null)
        }

        val list = findViewById<LinearLayout>(R.id.cfItems)
        list.removeAllViews()
        fun item(i: CashFlow.Item) = row(list, i.name, kind(i.kind) + " \u00b7 " + DateWords.format("d MMM yyyy", i.date),
            (if (i.kind.isIn) "+" else "\u2212") + money(i.amount), if (i.kind.isIn) R.color.bal_i_owe else R.color.bal_owed_to_me)
        if (o.overdue.isNotEmpty()) { heading(list, getString(R.string.cf_overdue)); o.overdue.forEach(::item) }
        if (o.items.isNotEmpty()) { heading(list, getString(R.string.cf_ahead)); o.items.forEach(::item) }
        if (all.isEmpty()) row(list, getString(R.string.cf_empty), null, null, null)

        val note = getString(R.string.cf_note) +
            if (o.undatedBills > 0) "\n" + getString(R.string.cf_undated, o.undatedBills.toString()) else ""
        findViewById<TextView>(R.id.tvCfNote).text = note
    }
}
