package com.innovation313.roshankhata

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.innovation313.roshankhata.data.HomeAlerts
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The bell's screen: today's alerts, each a row that opens where it is handled. */
class AlertsActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)
        ScreenInsets.on(this)
        val list = findViewById<LinearLayout>(R.id.alertList)
        lifecycleScope.launch {
            val alerts = withContext(Dispatchers.IO) { HomeAlerts.compute(this@AlertsActivity, dao) { Format.ltr(Format.money(it)) } }
            list.removeAllViews()
            if (alerts.isEmpty()) {
                val v = layoutInflater.inflate(R.layout.item_cashflow_row, list, false)
                v.findViewById<TextView>(R.id.tvCfTitle).text = getString(R.string.na_none)
                v.findViewById<View>(R.id.tvCfSub).visibility = View.GONE
                list.addView(v)
                return@launch
            }
            alerts.forEach { a ->
                val v = layoutInflater.inflate(R.layout.item_drawer_row, list, false)
                com.innovation313.roshankhata.ui.TextFit.relax(v)
                val (icon, tint, open) = when (a.kind) {
                    HomeAlerts.Kind.CHEQUE -> Triple(R.drawable.ic_tile_cheques, R.color.tile_cheques_bg, ChequesActivity::class.java)
                    HomeAlerts.Kind.PLAN -> Triple(R.drawable.ic_tile_plans, R.color.tile_plans_bg, PlansActivity::class.java)
                    HomeAlerts.Kind.BILL -> Triple(R.drawable.ic_tile_bills, R.color.tile_bills_bg, BillsActivity::class.java)
                    HomeAlerts.Kind.EXPIRING -> Triple(R.drawable.ic_tile_expiring, R.color.tile_stock_bg, ExpiringActivity::class.java)
                    HomeAlerts.Kind.BACKUP -> Triple(R.drawable.ic_tile_settings, R.color.tile_settings_bg, BackupActivity::class.java)
                    HomeAlerts.Kind.CASHFLOW -> Triple(R.drawable.ic_tile_insights, R.color.tile_insights_bg, CashFlowActivity::class.java)
                }
                v.findViewById<ImageView>(R.id.ivDrawerIcon).setImageResource(icon)
                v.findViewById<FrameLayout>(R.id.drawerIconDisc).backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this@AlertsActivity, tint))
                v.findViewById<TextView>(R.id.tvDrawerLabel).text =
                    if (a.sub.isEmpty()) a.title else a.title + "\n" + a.sub
                v.setOnClickListener { startActivity(Intent(this@AlertsActivity, open)) }
                list.addView(v)
            }
        }
    }
}
