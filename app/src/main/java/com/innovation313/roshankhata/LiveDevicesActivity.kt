package com.innovation313.roshankhata

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.BidiFormatter
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LiveForget
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.SyncSql
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.string
import com.innovation313.roshankhata.data.syncSql
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import com.innovation313.roshankhata.ui.TextFit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The phone's side of the computer's Live sync window (23 Oct 2026): the
 * book's other devices, with when each was last heard and a Remove for one
 * that is gone for good (a lost phone, a sold computer), and the rows that
 * were changed on two devices at once — each field as it is now and as the
 * other version had it — to be read and cleared.
 *
 * Removing forgets the device on this phone only ([LiveForget]): its own
 * book stays as it is, and the other devices keep their own list.
 */
class LiveDevicesActivity : BaseActivity() {

    private lateinit var devices: LinearLayout
    private lateinit var conflicts: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_devices)
        ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        devices = findViewById(R.id.devicesList)
        conflicts = findViewById(R.id.conflictsList)
        refresh()
    }

    private fun sql(): SyncSql = KhataDatabase.get(this).syncSql()

    private fun refresh() {
        lifecycleScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val s = sql()
                    LiveSync.state(s) to LiveSync.conflictViews(s)
                }.getOrNull()
            }
            if (isFinishing || isDestroyed) return@launch
            showDevices(read?.first)
            showConflicts(read?.second.orEmpty())
        }
    }

    private fun showDevices(state: LiveSync.State?) {
        devices.removeAllViews()
        val peers = if (state?.on == true) state.peers else emptyList()
        findViewById<TextView>(R.id.tvDevicesEmpty).apply {
            // Off: said as the Backup screen says it, not as "no device".
            setText(if (state?.on == true) R.string.live_devices_none else R.string.live_status_off)
            visibility = if (peers.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
        val bidi = BidiFormatter.getInstance()
        val inflater = LayoutInflater.from(this)
        for (p in peers) {
            val row = inflater.inflate(R.layout.item_live_device, devices, false)
            val name = p.name?.takeIf { it.isNotBlank() } ?: "—"
            row.findViewById<TextView>(R.id.tvDeviceName).text = name
            row.findViewById<TextView>(R.id.tvDeviceSeen).text = p.seen
                ?.let { getString(R.string.live_device_seen, bidi.unicodeWrap(Digits.toLatin(Format.dateTime(it)))) }
                ?: getString(R.string.live_device_never)
            row.findViewById<MaterialButton>(R.id.btnDeviceRemove).setOnClickListener { confirmRemove(p.device, name) }
            TextFit.relax(row)
            devices.addView(row)
        }
    }

    private fun confirmRemove(device: String, name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.live_remove)
            .setMessage(getString(R.string.live_remove_confirm_phone, BidiFormatter.getInstance().unicodeWrap(name)))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.live_remove) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { runCatching { LiveForget.device(sql(), device) } }
                    refresh()
                }
            }
            .show()
    }

    private fun showConflicts(views: List<LiveSync.ConflictView>) {
        conflicts.removeAllViews()
        findViewById<TextView>(R.id.tvConflictsHead).text = Digits.string(resources, R.string.live_conflicts, views.size)
        findViewById<TextView>(R.id.tvConflictsEmpty).visibility =
            if (views.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        val inflater = LayoutInflater.from(this)
        val muted = ContextCompat.getColor(this, R.color.text_muted)
        val ink = ContextCompat.getColor(this, R.color.ink)
        val saved = ContextCompat.getColor(this, R.color.red_gave_text)
        for (v in views) {
            val card = inflater.inflate(R.layout.item_live_conflict, conflicts, false)
            card.findViewById<TextView>(R.id.tvConflictTitle).text =
                tableWord(v.table) + "  ·  " + Digits.toLatin(Format.dateTime(v.at))
            val fields = card.findViewById<LinearLayout>(R.id.conflictFields)
            for ((field, now, other) in v.fields.take(8)) {
                fields.addView(line(field, muted, 12f))
                fields.addView(line(getString(R.string.live_conflict_now, now ?: "—"), ink, 14f))
                fields.addView(line(getString(R.string.live_conflict_saved, other ?: "—"), saved, 14f))
            }
            card.findViewById<MaterialButton>(R.id.btnConflictClear).setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { runCatching { LiveSync.clearConflict(sql(), v.id) } }
                    refresh()
                }
            }
            TextFit.relax(card)
            conflicts.addView(card)
        }
    }

    /** One line of a conflict card, full width (a vertical LinearLayout's default params). */
    private fun line(text: String, color: Int, sp: Float) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = sp
        setPadding(0, (2 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    /** A synced table in the owner's words where the app has them (the computer's window says the same). */
    private fun tableWord(table: String): String = when (table) {
        "parties" -> getString(R.string.customer)
        "transactions" -> getString(R.string.nav_khata)
        "cashbook" -> getString(R.string.cashbook)
        "cheques" -> getString(R.string.cheques)
        "invoices" -> getString(R.string.invoices)
        "supplier_bills" -> getString(R.string.supplier_bills)
        "products" -> getString(R.string.tile_products)
        "staff" -> getString(R.string.staff_title)
        else -> table
    }
}
