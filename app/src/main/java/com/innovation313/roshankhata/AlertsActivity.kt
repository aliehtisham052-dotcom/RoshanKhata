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
import com.innovation313.roshankhata.data.AlertNotes
import com.innovation313.roshankhata.data.HomeAlerts
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The bell's screen: everything the owner is told, in one list (7 Oct) —
 * the ledger's own alerts, the backup's state, and once per update that the
 * app is new. Each row opens where it is handled. The phone notifications
 * the daily worker posts land here too, so there is one place to look.
 */
class AlertsActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)
        ScreenInsets.on(this)
    }

    override fun onResume() {
        super.onResume()
        // Rebuilt on every return: a backup taken from the row's own screen
        // must take its row away the moment the owner is back.
        fill()
    }

    private fun fill() {
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
                val v = layoutInflater.inflate(R.layout.item_alert_row, list, false)
                com.innovation313.roshankhata.ui.TextFit.relax(v)
                val (icon, tint, open) = when (a.kind) {
                    HomeAlerts.Kind.CHEQUE -> Triple(R.drawable.ic_tile_cheques, R.color.tile_cheques_bg, ChequesActivity::class.java)
                    HomeAlerts.Kind.PLAN -> Triple(R.drawable.ic_tile_plans, R.color.tile_plans_bg, PlansActivity::class.java)
                    HomeAlerts.Kind.BILL -> Triple(R.drawable.ic_tile_bills, R.color.tile_bills_bg, BillsActivity::class.java)
                    HomeAlerts.Kind.EXPIRING -> Triple(R.drawable.ic_tile_expiring, R.color.tile_stock_bg, ExpiringActivity::class.java)
                    HomeAlerts.Kind.BACKUP,
                    HomeAlerts.Kind.BACKUP_FAILED,
                    HomeAlerts.Kind.BACKUP_OTHER_PHONE -> Triple(R.drawable.ic_alert_backup, R.color.tile_backup_bg, BackupActivity::class.java)
                    HomeAlerts.Kind.CASHFLOW -> Triple(R.drawable.ic_tile_insights, R.color.tile_insights_bg, CashFlowActivity::class.java)
                    HomeAlerts.Kind.UPDATED -> Triple(R.drawable.ic_alert_update, R.color.tile_bills_bg, AboutActivity::class.java)
                }
                v.findViewById<ImageView>(R.id.ivAlertIcon).setImageResource(icon)
                v.findViewById<FrameLayout>(R.id.alertIconDisc).backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this@AlertsActivity, tint))
                v.findViewById<TextView>(R.id.tvAlertTitle).text = a.title
                v.findViewById<TextView>(R.id.tvAlertSub).apply {
                    text = a.sub
                    visibility = if (a.sub.isEmpty()) View.GONE else View.VISIBLE
                }
                v.findViewById<TextView>(R.id.tvAlertTag).apply {
                    text = a.tag ?: ""
                    visibility = if (a.tag.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
                v.setOnClickListener {
                    // Opening About is the owner reading what is new; the row has done its job.
                    if (a.kind == HomeAlerts.Kind.UPDATED) AlertNotes.updateSeen(this@AlertsActivity)
                    startActivity(Intent(this@AlertsActivity, open))
                }
                list.addView(v)
            }
        }
    }
}
