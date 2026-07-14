package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.AppLock
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PartyAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Roshan Khata — Innovation-313
 * Home: customers and suppliers with their outstanding balances.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * Set by the gate. MainActivity is not exported and cannot be launched
         * from outside the app, so this is a routing hint rather than a
         * security boundary — the real boundary is that the gate never starts
         * this activity until the lock has been cleared.
         */
        const val EXTRA_UNLOCKED = "unlocked"
    }

    private lateinit var adapter: PartyAdapter
    private lateinit var tvNetBalance: TextView
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)

        tvNetBalance = findViewById(R.id.tvNetBalance)
        tvEmpty = findViewById(R.id.tvEmpty)

        val rv: RecyclerView = findViewById(R.id.rvParties)
        adapter = PartyAdapter(
            onClick = { party ->
                startActivity(
                    Intent(this, PartyDetailActivity::class.java)
                        .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, party.id)
                )
            },
            onLongClick = { party -> confirmDeleteParty(party) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddParty).setOnClickListener {
            showAddPartyChoice()
        }

        observeData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_zakat -> {
                startActivity(Intent(this, ZakatActivity::class.java))
                true
            }
            R.id.action_business_settings -> {
                startActivity(Intent(this, BusinessSettingsActivity::class.java))
                true
            }
            R.id.action_app_lock -> {
                showAppLockSettings()
                true
            }
            R.id.action_recycle_bin -> {
                startActivity(Intent(this, RecycleBinActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest { list ->
                adapter.submitList(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            dao.observeNetBalance().collectLatest { net ->
                tvNetBalance.text = Format.money(net)
            }
        }
    }

    /**
     * Two ways in: pull from the phone's contacts, or type it in.
     * Manual entry is listed second but works identically — nobody is forced
     * to hand over their contact list to use the app.
     */
    private fun showAddPartyChoice() {
        val options = arrayOf(
            getString(R.string.import_contacts),
            getString(R.string.add_manually)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_party)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, ImportContactsActivity::class.java))
                    1 -> showAddPartyDialog()
                }
            }
            .show()
    }

    private fun showAddPartyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_party, null)
        val etName: EditText = view.findViewById(R.id.etName)
        val etPhone: EditText = view.findViewById(R.id.etPhone)
        val rbCustomer: RadioButton = view.findViewById(R.id.rbCustomer)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_party)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.enter_name, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val phone = etPhone.text.toString().trim().ifEmpty { null }
                lifecycleScope.launch {
                    dao.insertParty(
                        Party(
                            name = name,
                            phone = phone,
                            isCustomer = rbCustomer.isChecked
                        )
                    )
                }
            }
            .show()
    }

    /** Deleting a party is never destructive — it goes to the Recycle Bin, entries and all. */
    private fun confirmDeleteParty(party: PartyWithBalance) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_party_title)
            .setMessage(getString(R.string.delete_party_confirm, party.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    val now = System.currentTimeMillis()
                    // Same timestamp for both, so a restore can reunite them exactly.
                    dao.softDeleteEntriesOfParty(party.id, now)
                    dao.softDeleteParty(party.id, now)
                    Toast.makeText(this@MainActivity, R.string.moved_to_bin, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /**
     * App Lock settings.
     *
     * If the phone has no screen lock at all there is nothing to authenticate
     * against, so we say so plainly instead of offering a switch that would do
     * nothing — a lock that only looks like a lock is worse than none.
     */
    private fun showAppLockSettings() {
        if (AppLock.noneEnrolled(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_lock)
                .setMessage(R.string.app_lock_no_screen_lock)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        if (!AppLock.isAvailable(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_lock)
                .setMessage(R.string.app_lock_unavailable)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val enabled = AppLock.isEnabled(this)

        val status = getString(
            if (enabled) R.string.app_lock_enabled else R.string.app_lock_disabled
        )
        val message = status + "\n\n" + getString(R.string.app_lock_explain)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_lock)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(
                if (enabled) R.string.app_lock_turn_off else R.string.app_lock_turn_on
            ) { _, _ ->
                AppLock.setEnabled(this, !enabled)
                Toast.makeText(
                    this,
                    if (!enabled) R.string.app_lock_enabled else R.string.app_lock_disabled,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }
}
