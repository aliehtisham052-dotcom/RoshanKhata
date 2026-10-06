package com.innovation313.roshankhata

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.Contacts
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PhoneContact
import com.innovation313.roshankhata.ui.NameSearch
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
class ImportContactsActivity : BaseActivity() {

    private lateinit var adapter: ContactAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnImport: MaterialButton
    private lateinit var etSearch: EditText

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private var allContacts: List<PhoneContact> = emptyList()
    private val selected = linkedSetOf<String>()
    private var visible = listOf<PhoneContact>()
    private lateinit var btnSelectAll: MaterialButton

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

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnImport = findViewById(R.id.btnImport)
        btnSelectAll = findViewById(R.id.btnSelectAll)
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
        btnSelectAll.setOnClickListener { toggleSelectAll() }

        updateSelectedCount()
        ensurePermission()
    }

    // ---------- Android 17+: the system Contact Picker (5 Oct 2026) ----------
    //
    // Play's Contacts Permissions policy (in force 27 Jan 2027 for apps that
    // target Android 17) asks apps that do not need the whole address book to
    // use the system picker instead. On Android 17 the owner picks the
    // customers in the phone's own list (up to 100 at a time, the picker's
    // ceiling) and the app reads only those — READ_CONTACTS is not even
    // declared there (manifest: maxSdkVersion 36). Older phones keep the
    // full list below, because the picker exists only on Android 17.
    // Constants are written out because this app compiles against API 36;
    // their values are from developer.android.com's
    // ContactsPickerSessionContract reference.

    private val pickContacts = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val session = result.data?.data
        if (result.resultCode != RESULT_OK || session == null) {
            finish()
            return@registerForActivityResult
        }
        showStatus(getString(R.string.loading_contacts))
        lifecycleScope.launch {
            val existing = dao.existingPhones()
            val binned = dao.binnedPhones()
            val picked = withContext(Dispatchers.IO) {
                try {
                    Contacts.fromPicker(this@ImportContactsActivity, session, existing, binned)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            allContacts = picked
            // What the owner just chose is what he wants: ticked already.
            picked.filter { !it.alreadyAdded && !it.inRecycleBin }.forEach { selected.add(it.phone) }
            updateSelectedCount()
            if (picked.isEmpty()) showStatus(getString(R.string.no_contacts_found)) else { hideStatus(); applyFilter() }
        }
    }

    private fun openSystemPicker() {
        val intent = Intent("android.provider.action.PICK_CONTACTS")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .putExtra("android.provider.extra.PICK_CONTACTS_SELECTION_LIMIT", 100)
            .putExtra("android.provider.extra.PICK_CONTACTS_MATCH_ALL_DATA_FIELDS", false)
            .putStringArrayListExtra(
                "android.provider.extra.PICK_CONTACTS_REQUESTED_DATA_FIELDS",
                arrayListOf(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            )
        try {
            pickContacts.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            showStatus(getString(R.string.contacts_picker_missing))
        }
    }

    /** Explain before asking. A bare system prompt with no reason is not fair to the user. */
    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 37) { // Android 17
            openSystemPicker()
            return
        }
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
            val binned = dao.binnedPhones()

            // Reading the whole contact list can be slow on a phone with
            // thousands of entries — keep it off the main thread.
            val loaded = withContext(Dispatchers.IO) {
                Contacts.load(this@ImportContactsActivity, existing, binned)
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

        // Same rule as the ledger's search, from the same place: a name that
        // begins with what was typed first, then a name with a word that
        // does, then a match buried mid-word.
        //
        // That last rank is why "ali" used to return three men called Wali
        // and nothing else — every one of them was a real match, and every
        // one was the wrong answer.
        val filtered = NameSearch.sort(
            allContacts.filter { NameSearch.matches(it.name, it.phone, query) },
            query
        ) { it.name }

        visible = filtered
        adapter.submit(filtered, selected)
        refreshSelectAllLabel()

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

    /**
     * Select (or clear) everyone currently visible. Works on the filtered list,
     * so searching first and then "Select all" imports just that subset — the
     * natural way to grab, say, every contact with "Mandi" in the name.
     */
    private fun toggleSelectAll() {
        // Only the importable rows — never the already-added ones, which are
        // shown greyed and cannot be picked individually either.
        val selectable = visible.filter { !it.alreadyAdded && !it.inRecycleBin }
        val allSelectableSelected = selectable.isNotEmpty() && selectable.all { it.phone in selected }
        if (allSelectableSelected) {
            selectable.forEach { selected.remove(it.phone) }
        } else {
            selectable.forEach { selected.add(it.phone) }
        }
        updateSelectedCount()
        applyFilter()
    }

    private fun refreshSelectAllLabel() {
        val selectable = visible.filter { !it.alreadyAdded && !it.inRecycleBin }
        val allSelectableSelected = selectable.isNotEmpty() && selectable.all { it.phone in selected }
        btnSelectAll.setText(if (allSelectableSelected) R.string.clear_all else R.string.select_all)
        btnSelectAll.isEnabled = selectable.isNotEmpty()
    }

    private fun updateSelectedCount() {
        tvSelectedCount.text = getString(R.string.selected_count, selected.size)
        btnImport.isEnabled = selected.isNotEmpty()
    }

    /**
     * Asks once, for the whole batch, rather than importing silently as a
     * customer. That used to be the only outcome this screen could produce —
     * every contact, whatever the shop actually buys from or sells to them,
     * became a customer with no way to say otherwise here. A supplier
     * imported this way was wrong from the moment it was saved, and stayed
     * wrong until someone opened that party and corrected it by hand.
     *
     * One choice for the batch, not a toggle per row: mixing customers and
     * suppliers in a single import is the rare case, and a shop that does
     * import a mixed batch can still fix the odd one out afterwards from the
     * party's own screen, the same as before this existed.
     */
    private fun importSelected() {
        val toImport = allContacts.filter { it.phone in selected }
        if (toImport.isEmpty()) return

        val view = layoutInflater.inflate(R.layout.dialog_import_contact_type, null)
        view.findViewById<TextView>(R.id.tvImportTypeMessage).text =
            resources.getQuantityString(
                R.plurals.import_as_message, toImport.size, toImport.size
            )
        val rbCustomer: RadioButton = view.findViewById(R.id.rbImportCustomer)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_as_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.import_selected) { _, _ ->
                val isCustomer = rbCustomer.isChecked
                // AppScope, not lifecycleScope — a quick Back press right
                // after Save must not be able to cancel this import partway
                // through, the same fix as everywhere else creation writes
                // happen in this app. See AppScope's own comment.
                AppScope.launch {
                    dao.insertParties(
                        toImport.map { c ->
                            Party(name = c.name, phone = c.phone, isCustomer = isCustomer)
                        }
                    )
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(
                                this@ImportContactsActivity,
                                resources.getQuantityString(
                                    R.plurals.imported_count, toImport.size, toImport.size
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    }
                }
            }
            .show()
    }

    private fun showStatus(text: String) {
        tvStatus.text = text
        tvStatus.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        tvStatus.visibility = View.GONE
    }
}
