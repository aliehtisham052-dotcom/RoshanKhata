package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.data.Digits

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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
            showPermissionCard(false)
            loadContacts()
        } else {
            // Refused with "don't ask again" (or refused twice): Android will not ask any more, so the card's
            // button opens the phone's settings for this app instead of doing nothing.
            refusedForGood = !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
            showPermissionCard(true)
        }
    }

    /** Set once Android will no longer show its own question; the card then leads to the phone's settings. */
    private var refusedForGood = false
    private lateinit var permissionCard: View

    /**
     * The question about the contacts, as a card on the page (10 Nov 2026): it was a bare dialog over an
     * empty grey page. Nothing to choose or search while it shows.
     */
    private fun showPermissionCard(show: Boolean) {
        permissionCard.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.selectRow).visibility = if (show) View.GONE else View.VISIBLE
        etSearch.visibility = if (show) View.GONE else View.VISIBLE
        if (!show) return
        hideStatus()
        findViewById<View>(R.id.permPoints).visibility = if (refusedForGood) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tvPermDenied).visibility = if (refusedForGood) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnAllow)
            .setText(if (refusedForGood) R.string.contacts_open_settings else R.string.contacts_allow_access)
    }

    override fun onResume() {
        super.onResume()
        // Back from the phone's settings with the contacts allowed: straight to the list.
        if (::permissionCard.isInitialized && permissionCard.visibility == View.VISIBLE && Build.VERSION.SDK_INT < 37 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        ) {
            showPermissionCard(false)
            loadContacts()
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
        permissionCard = findViewById(R.id.permissionCard)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnByHand).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAllow).setOnClickListener {
            if (refusedForGood) {
                runCatching {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.fromParts("package", packageName, null))
                    )
                }
            } else {
                requestPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }

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

        showPermissionCard(true)
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

    /** Every contact's name prepared for search once, the way the Khata list does (P5, 7 Oct). */
    private var contactKeys: Map<String, NameSearch.Key> = emptyMap()
    private var filterJob: kotlinx.coroutines.Job? = null

    /**
     * Same rule as the ledger's search, from the same place: a name that
     * begins with what was typed first, then a name with a word that does,
     * then a match buried mid-word. That last rank is why "ali" used to
     * return three men called Wali and nothing else.
     *
     * Off the main thread since 7 Oct (P5): a phone book of several thousand
     * folded on every keystroke held the keyboard, the same way the Khata
     * list did before P1.
     */
    private fun applyFilter() {
        val q = NameSearch.Query(etSearch.text.toString())
        val all = allContacts
        if (contactKeys.size != all.size) {
            contactKeys = all.associate { it.phone to NameSearch.Key(it.name, it.phone) }
        }
        val keys = contactKeys
        filterJob?.cancel()
        filterJob = lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                all.filter { c -> keys[c.phone]?.let { NameSearch.matches(it, q) } ?: NameSearch.matches(c.name, c.phone, q.text) }
                    .let { list ->
                        if (q.isEmpty) list
                        else list.sortedWith(
                            compareBy<PhoneContact> { c -> keys[c.phone]?.let { NameSearch.rank(it, q) } ?: NameSearch.rank(c.name, q.text) }
                                .thenBy { it.name.lowercase() }
                        )
                    }
            }
            visible = filtered
            // Letter headings while the list is in name order; a search ranks by match, so none then.
            adapter.submit(filtered, selected, withSections = q.isEmpty)
            refreshSelectAllLabel()

            if (filtered.isEmpty() && all.isNotEmpty()) {
                showStatus(getString(R.string.no_matching_contacts))
            } else {
                hideStatus()
            }
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
        btnSelectAll.setText(if (allSelectableSelected) R.string.contacts_clear else R.string.contacts_select_all)
        btnSelectAll.isEnabled = selectable.isNotEmpty()
    }

    private fun updateSelectedCount() {
        tvSelectedCount.text = Digits.quantity(resources, R.plurals.n_selected, selected.size, selected.size)
        // The one action alone across the foot, only once someone is chosen (10 Nov 2026): a grey, faded button
        // stood there before anyone was. "Add 1,217 to khata" - not "customers": whether they are customers or
        // suppliers is asked next.
        findViewById<View>(R.id.importBar).visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        btnImport.isEnabled = selected.isNotEmpty()
        btnImport.text = Digits.quantity(resources, R.plurals.contacts_add_n, selected.size, selected.size)
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
        // One sheet (10 Nov 2026) in place of two plain dialogs: how many go in, a warning when that is a lot -
        // one slip must not fill the khata - and customer or supplier as two cards.
        val n = toImport.size
        val message = buildString {
            append(Digits.quantity(resources, R.plurals.import_as_message, n, n))
            if (n > BIG_BATCH) append("\n\n").append(Digits.quantity(resources, R.plurals.contacts_import_many_body, n, n))
        }
        com.innovation313.roshankhata.ui.ChoiceSheet.show(
            this,
            title = getString(R.string.import_as_title),
            message = message,
            options = listOf(
                com.innovation313.roshankhata.ui.ChoiceSheet.Option(getString(R.string.customer), icon = R.drawable.ic_person),
                com.innovation313.roshankhata.ui.ChoiceSheet.Option(getString(R.string.supplier), icon = R.drawable.ic_truck)
            )
        ) { which -> saveAll(toImport, isCustomer = which == 0) }
    }

    private fun saveAll(toImport: List<PhoneContact>, isCustomer: Boolean) {
        // AppScope, not lifecycleScope - a quick Back press right after the choice must not be able to cancel this
        // import partway through, the same as everywhere else creation writes happen in this app.
        AppScope.launch {
            dao.insertParties(toImport.map { c -> Party(name = c.name, phone = c.phone, isCustomer = isCustomer) })
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this@ImportContactsActivity,
                        Digits.quantity(resources, R.plurals.imported_count, toImport.size, toImport.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }

    private companion object {
        /** More than this many at once is confirmed first. */
        const val BIG_BATCH = 50
    }

    private fun showStatus(text: String) {
        tvStatus.text = text
        tvStatus.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        tvStatus.visibility = View.GONE
    }
}
