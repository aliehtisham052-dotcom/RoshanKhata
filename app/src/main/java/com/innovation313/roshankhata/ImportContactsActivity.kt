package com.innovation313.roshankhata

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Contacts
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PhoneContact
import com.innovation313.roshankhata.ui.ContactAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import customers from the phone's contact list.
 *
 * The contacts permission is asked for here and nowhere else — the app does
 * not touch contacts until the owner deliberately opens this screen. Contacts
 * are read into memory, shown to pick from, and dropped when the screen
 * closes. Only the ones actually chosen are saved, and only their name and
 * number. Anyone who declines the permission can still add parties by hand;
 * nothing in the app depends on this.
 */
class ImportContactsActivity : AppCompatActivity() {

    private lateinit var adapter: ContactAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnImport: MaterialButton
    private lateinit var etSearch: EditText

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var allContacts: List<PhoneContact> = emptyList()
    private val selected = linkedSetOf<String>()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadContacts()
        } else {
            showStatus(getString(R.string.contacts_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_contacts)

        tvStatus = findViewById(R.id.tvStatus)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnImport = findViewById(R.id.btnImport)
        etSearch = findViewById(R.id.etSearch)

        adapter = ContactAdapter { contact -> toggle(contact) }
        val rv: RecyclerView = findViewById(R.id.rvContacts)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })

        btnImport.setOnClickListener { importSelected() }

        updateSelectedCount()
        ensurePermission()
    }

    /** Explain before asking. A bare system prompt with no reason is not fair to the user. */
    private fun ensurePermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            loadContacts()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.contacts_permission_title)
            .setMessage(R.string.contacts_permission_rationale)
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(R.string.continue_action) { _, _ ->
                requestPermission.launch(Manifest.permission.READ_CONTACTS)
            }
            .setCancelable(false)
            .show()
    }

    private fun loadContacts() {
        showStatus(getString(R.string.loading_contacts))

        lifecycleScope.launch {
            val existing = dao.existingPhones()

            // Reading the whole contact list can be slow on a phone with
            // thousands of entries — keep it off the main thread.
            val loaded = withContext(Dispatchers.IO) {
                Contacts.load(this@ImportContactsActivity, existing)
            }

            allContacts = loaded

            if (loaded.isEmpty()) {
                showStatus(getString(R.string.no_contacts_found))
            } else {
                hideStatus()
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val query = etSearch.text.toString().trim().lowercase()

        val filtered = if (query.isEmpty()) {
            allContacts
        } else {
            allContacts.filter {
                it.name.lowercase().contains(query) ||
                    it.phone.filter { ch -> ch.isDigit() }.contains(query.filter { ch -> ch.isDigit() })
            }
        }

        adapter.submit(filtered, selected)

        if (filtered.isEmpty() && allContacts.isNotEmpty()) {
            showStatus(getString(R.string.no_matching_contacts))
        } else {
            hideStatus()
        }
    }

    private fun toggle(contact: PhoneContact) {
        if (contact.phone in selected) selected.remove(contact.phone)
        else selected.add(contact.phone)

        updateSelectedCount()
        applyFilter()
    }

    private fun updateSelectedCount() {
        tvSelectedCount.text = getString(R.string.selected_count, selected.size)
        btnImport.isEnabled = selected.isNotEmpty()
    }

    private fun importSelected() {
        val toImport = allContacts.filter { it.phone in selected }
        if (toImport.isEmpty()) return

        lifecycleScope.launch {
            dao.insertParties(
                toImport.map { c ->
                    Party(
                        name = c.name,
                        phone = c.phone,
                        isCustomer = true
                    )
                }
            )
            Toast.makeText(
                this@ImportContactsActivity,
                getString(R.string.imported_count, toImport.size),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun showStatus(text: String) {
        tvStatus.text = text
        tvStatus.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        tvStatus.visibility = View.GONE
    }
}
