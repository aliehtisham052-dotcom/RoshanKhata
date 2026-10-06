package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.SupplierRates
import com.innovation313.roshankhata.data.UnitWords
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * "Where is it cheaper?" — the purchase rates each supplier charged, product by
 * product, with rises and cheaper offers called out (see SupplierRates).
 * Reads supplier bills only; opens on the helper's phone too.
 */
class SupplierRatesActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }
    private var rows: List<SupplierRates.Row> = emptyList()
    private var watchOnly = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_supplier_rates)
        ScreenInsets.on(this)

        val group = findViewById<ChipGroup>(R.id.chipSrFilter)
        listOf(true to R.string.sr_watch, false to R.string.filter_type_all).forEach { (watch, label) ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = getString(label)
            chip.tag = watch
            chip.isChecked = watch == watchOnly
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val c = ids.firstOrNull()?.let { g.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            watchOnly = c.tag as Boolean
            render()
        }

        lifecycleScope.launch {
            rows = withContext(Dispatchers.IO) { SupplierRates.rows(dao.supplierRateLinesOnce(), System.currentTimeMillis()) }
            render()
        }
    }

    private fun money(v: Double) = Format.ltr(Format.money(v))

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.srList)
        list.removeAllViews()
        val shown = if (watchOnly) rows.filter { it.needsLook } else rows
        if (shown.isEmpty()) {
            val t = layoutInflater.inflate(R.layout.item_cashflow_row, list, false)
            t.findViewById<TextView>(R.id.tvCfTitle).text =
                getString(if (rows.isEmpty()) R.string.sr_none else R.string.sr_nothing_to_watch)
            t.findViewById<View>(R.id.tvCfSub).visibility = View.GONE
            list.addView(t)
            return
        }
        shown.forEach { r ->
            val card = layoutInflater.inflate(R.layout.item_supplier_rate, list, false)
            com.innovation313.roshankhata.ui.TextFit.relax(card)
            val unit = UnitWords.label(r.unit).takeIf { it.isNotEmpty() }
            card.findViewById<TextView>(R.id.tvSrName).text = listOfNotNull(r.name, unit).joinToString(" \u00b7 ")
            card.findViewById<TextView>(R.id.tvSrLast).text =
                getString(R.string.sr_last, money(r.last.rate), r.last.supplierName, DateWords.format("d MMM yyyy", r.last.date))
            val change = card.findViewById<TextView>(R.id.tvSrChange)
            val pct = String.format(Locale.US, "%.1f%%", kotlin.math.abs(r.percent))
            when {
                r.previous == null -> change.visibility = View.GONE
                r.change > 0.004 -> {
                    change.text = getString(R.string.sr_up, money(r.change), pct)
                    change.setTextColor(ContextCompat.getColor(this, R.color.bal_owed_to_me))
                }
                r.change < -0.004 -> {
                    change.text = getString(R.string.sr_down, money(-r.change), pct)
                    change.setTextColor(ContextCompat.getColor(this, R.color.bal_i_owe))
                }
                else -> change.visibility = View.GONE
            }
            val cheaper = card.findViewById<TextView>(R.id.tvSrCheaper)
            val c = r.cheaperElsewhere
            if (c == null) cheaper.visibility = View.GONE
            else cheaper.text = getString(R.string.sr_cheaper, c.supplierName, money(c.rate), DateWords.format("d MMM yyyy", c.date))
            list.addView(card)
        }
    }
}
