package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.Digits

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.BackupReminder
import com.innovation313.roshankhata.data.BusinessDeleteSummary
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cupboard door: every ledger this phone holds, and which one is open.
 *
 * Tapping a business does not "load" it into the running screens — it tears
 * the whole task down and starts fresh. That is the point, not a shortcut:
 * screens cache their DAO handles, and a screen still holding the old shop's
 * handle after a switch would write an entry into the wrong book. There is
 * no way to half-switch, so there is no way to half-switch wrongly.
 *
 * Long-press offers Rename and, where it is safe, Delete — see
 * [Businesses.canDelete] for exactly when Delete is offered at all, and
 * [Businesses.delete] for what removing a shop actually does.
 */
class BusinessSwitchActivity : BaseActivity() {

    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_switch)
        ScreenInsets.on(this)

        adapter = Adapter()
        val rv: RecyclerView = findViewById(R.id.rvBusinesses)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnAddBusiness).setOnClickListener { promptCreate() }

        refresh()
    }

    private fun refresh() {
        adapter.submit(Businesses.list(this), Businesses.active(this).id)
    }

    private fun promptCreate() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_business_name, null)
        val et: EditText = view.findViewById(R.id.etBusinessName)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.business_add)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = et.text.toString().trim()
                // An empty name used to close the dialog and add nothing,
                // silently (13 Oct); the dialog now stays and says why.
                if (name.isEmpty()) {
                    et.error = getString(R.string.business_name_needed)
                    return@setOnClickListener
                }
                dialog.dismiss()
                // The new book's trade, asked before it exists (9 Oct): a
                // second shop is often a different trade from the first, and
                // every hint, unit and feature reads it. Nothing is created
                // until it is chosen.
                com.innovation313.roshankhata.ui.TradePicker.show(this, null, required = false, onClosed = {
                    android.widget.Toast.makeText(this, R.string.business_not_added, android.widget.Toast.LENGTH_LONG).show()
                }) { trade ->
                    val fresh = Businesses.create(this, name)
                    com.innovation313.roshankhata.data.BusinessProfile.setTradeOf(this, fresh.id, trade)
                    // Its country, sign and calendar from the shop open now (23 Oct 2026): never a silent Pakistan.
                    com.innovation313.roshankhata.data.BusinessProfile.copyRegion(this, Businesses.active(this).id, fresh.id)
                    // Created to be used: open it straight away. The restart
                    // below is also what makes the new empty book visibly real.
                    switchAndRestart(fresh.id)
                }
            }
        }
        dialog.show()
        com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
        et.requestFocus()
    }

    private fun promptRename(id: Long, current: String?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_business_name, null)
        val et: EditText = view.findViewById(R.id.etBusinessName)
        et.setText(current.orEmpty())
        et.setSelection(et.text.length)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.business_rename)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                Businesses.rename(this, id, name)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
        et.requestFocus()
    }

    /**
     * What long-press opens: always Rename, and Delete too where
     * [Businesses.canDelete] allows it — Business 1 and the currently open
     * business never offer it at all, rather than offering it and then
     * explaining why it refused.
     */
    private fun showBusinessOptions(b: Businesses.Business) {
        val display = Businesses.displayName(this, b) ?: getString(R.string.app_name)
        val choices = listOfNotNull(
            ChoiceSheet.edit(getString(R.string.business_rename)),
            if (Businesses.canDelete(this, b.id)) ChoiceSheet.delete(getString(R.string.business_delete)) else null
        )
        ChoiceSheet.show(this, display, choices) { which ->
            if (which == 0) promptRename(b.id, display) else promptDelete(b, display)
        }
    }

    /**
     * Reads the shop's own numbers before showing anything — the owner
     * should see what a delete costs before being asked to confirm it, not
     * after.
     */
    private fun promptDelete(business: Businesses.Business, display: String) {
        lifecycleScope.launch {
            val summary = Businesses.summaryOf(this@BusinessSwitchActivity, business)
            showDeleteConfirm(business, display, summary)
        }
    }

    /**
     * The guarded delete dialog: shows what is about to be lost, and keeps
     * its positive button disabled until the owner has typed the shop's
     * name exactly — a bare Yes/No is not enough for something this
     * permanent. See [Businesses.delete] for what actually happens once
     * this button is pressed.
     */
    private fun showDeleteConfirm(
        business: Businesses.Business,
        display: String,
        summary: BusinessDeleteSummary
    ) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_business_delete, null)
        val tvSummary: TextView = view.findViewById(R.id.tvDeleteSummary)
        val etConfirm: EditText = view.findViewById(R.id.etDeleteConfirmName)
        tvSummary.text = Digits.string(resources, R.string.business_delete_confirm_body,
            summary.partyCount,
            // In that shop's own sign, not the open shop's (23 Oct 2026, the review).
            Format.money(summary.netBalance, com.innovation313.roshankhata.data.BusinessProfile.currencyOf(this, business.id))
        )
        etConfirm.hint = getString(R.string.business_delete_type_hint, display)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.business_delete_confirm_title, display))
            .setView(view)
            .setPositiveButton(R.string.delete_forever, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positive.isEnabled = false
            positive.setOnClickListener {
                // Disabled at once: file deletion is disk work done off the
                // main thread below, and a second tap mid-delete must not
                // start a second one.
                positive.isEnabled = false
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        Businesses.delete(this@BusinessSwitchActivity, business.id)
                    }
                    Toast.makeText(
                        this@BusinessSwitchActivity,
                        getString(R.string.business_delete_done, display),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    refresh()
                }
            }
            etConfirm.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    positive.isEnabled = s?.toString()?.trim() == display
                }
            })
        }
        dialog.show()
        com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
        etConfirm.requestFocus()
    }

    /**
     * The only correct way to change shops: switch, then rebuild the world.
     *
     * CLEAR_TASK removes every activity — and with them every cached DAO
     * still pointing at the old file — before the home screen opens against
     * the newly active business's own database.
     */
    private fun switchAndRestart(id: Long) = switchAndRestart(this, id)

    companion object {
        /**
         * The one way to change shops, from here or the shop sheet (11 Oct):
         * switch, then rebuild every screen against the new shop's book.
         */
        fun switchAndRestart(activity: android.app.Activity, id: Long) {
            if (switching) return
            switching = true
            val app = activity.applicationContext
            com.innovation313.roshankhata.data.AppScope.launch {
                // The shop being left is protected first, while it is still the open book (23 Oct 2026,
                // the review): today's daily copy written again, so the work done in it before the
                // switch is in a copy, and a copy still waiting after a sale is not written into the
                // next shop. Best effort: a copy that fails never stops the switch.
                runCatching { com.innovation313.roshankhata.data.Snapshots.beforeLeaving(app) }
                withContext(Dispatchers.Main) {
                    try {
                        // Whoever was signed in is signed out by the switch itself (KhataDatabase.closeActive).
                        Businesses.switchTo(app, id)
                        val alive = !activity.isFinishing && !activity.isDestroyed
                        val fresh = Intent(if (alive) activity else app, KhataActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        if (alive) activity.startActivity(fresh) else app.startActivity(fresh)
                    } finally {
                        switching = false
                    }
                }
            }
        }

        /** A switch is on its way (the copy first): a second tap does not start another. */
        @Volatile private var switching = false
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        private var items: List<Businesses.Business> = emptyList()
        private var activeId: Long = 1L

        fun submit(list: List<Businesses.Business>, active: Long) {
            items = list
            activeId = active
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvBizName)
            val hint: TextView = v.findViewById(R.id.tvBizHint)
            val open: TextView = v.findViewById(R.id.tvBizOpen)
            val switch: View = v.findViewById(R.id.btnBizSwitch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_business, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val b = items[position]
            val ctx = holder.itemView.context
            val display = Businesses.displayName(ctx, b)
                ?: ctx.getString(R.string.app_name)
            holder.name.text = display

            // When each shop was last protected, on the one screen that shows
            // every shop at once. Backup only ever covers the open business,
            // so without this the owner has to open each shop in turn to learn
            // whether the others are safe — and the honest answer to "are both
            // my books backed up" should take one glance, not a tour.
            val last = BackupReminder.lastBackupAtOf(ctx, b.id)
            holder.hint.text = if (last == 0L) {
                ctx.getString(R.string.business_never_backed_up)
            } else {
                ctx.getString(R.string.business_last_backup, Format.dateOnly(last))
            }

            val isOpen = b.id == activeId
            holder.open.visibility = if (isOpen) View.VISIBLE else View.GONE
            holder.switch.visibility = if (isOpen) View.GONE else View.VISIBLE
            holder.switch.contentDescription = ctx.getString(R.string.business_switch_open) + " " + display
            holder.switch.setOnClickListener { switchAndRestart(b.id) }
            holder.itemView.setOnClickListener {
                // The open shop: no switch is needed, so go straight to its
                // khata (13 Oct). Doing nothing read as "it will not open".
                if (isOpen) {
                    startActivity(Intent(this@BusinessSwitchActivity, KhataActivity::class.java))
                    finish()
                } else {
                    switchAndRestart(b.id)
                }
            }
            holder.itemView.setOnLongClickListener {
                showBusinessOptions(b)
                true
            }
        }
    }
}
