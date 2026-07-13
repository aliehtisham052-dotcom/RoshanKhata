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
            showAddPartyDialog()
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
}
