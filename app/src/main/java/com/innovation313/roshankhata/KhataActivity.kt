package com.innovation313.roshankhata

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.AppLock
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.innovation313.roshankhata.data.QrTag
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.DateRangeFilter
import com.innovation313.roshankhata.data.VoiceEntry
import com.innovation313.roshankhata.data.VoiceLanguage
import com.innovation313.roshankhata.data.VoiceLog
import androidx.core.content.FileProvider
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.ui.PartyAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Roshan Khata — Innovation-313
 * Home: customers and suppliers with their outstanding balances.
 */
class KhataActivity : AppCompatActivity() {

    private lateinit var adapter: PartyAdapter
    private lateinit var etSearch: EditText

    /** Everything from the DB. The list on screen is a view onto this. */
    private var allParties: List<PartyWithBalance> = emptyList()

    /**
     * What is actually on screen after searching, filtering and sorting.
     *
     * Kept because "Select all" has to mean the rows the owner can see. A shop
     * with a thousand customers who has searched down to four expects all to
     * be four.
     */
    private var shownParties: List<PartyWithBalance> = emptyList()

    /** Customers picked for deleting. Empty means nothing is being picked. */
    private val selectedIds = mutableSetOf<Long>()

    private lateinit var selectionBar: View
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnSelectAll: com.google.android.material.button.MaterialButton

    // Newest dealing first. Sorting A-Z buried the customer just served
    // somewhere in the middle of the alphabet.
    private var sortMode = SortMode.RECENT

    private lateinit var ivEye: ImageView

    /** The real figure. The view may be showing a mask over it. */
    private var netBalance: Double = 0.0

    private enum class SortMode { NAME_AZ, NAME_ZA, OWES_MOST, I_OWE_MOST, RECENT }

    /**
     * Which side of the ledger the list is showing.
     *
     * ALL is the default and shows everyone, newest dealing first — the order
     * the shop actually works in. The other two come from tapping the summary
     * boxes above the list: a shopkeeper looking at "I have to get" wants the
     * people behind that figure, not a total.
     */
    private enum class SideFilter { ALL, TO_GET, TO_GIVE }
    private var sideFilter = SideFilter.ALL

    /** Which stretch of days the list is showing. All of them, until asked. */
    private var dateRange = DateRangeFilter.Range.ALL
    private lateinit var tvNetBalance: TextView
    private lateinit var tvTotalGet: TextView
    private lateinit var tvTotalGive: TextView
    private lateinit var tvPartySummary: TextView
    private var totalGet = 0.0
    private var totalGive = 0.0
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_khata)

        // The day's ledger snapshot, off the main thread, at most once a day.
        // Here rather than in the Application class so it runs when the ledger
        // is actually opened, and so the trigger is visible on the screen that
        // owns the data it protects.
        lifecycleScope.launch(Dispatchers.IO) {
            com.innovation313.roshankhata.data.Snapshots.maybeToday(applicationContext)
        }

        // Edge-to-edge, the mechanism proven on this very screen — now shared
        // by every screen from ScreenInsets so there is one implementation to
        // keep right instead of a copy per Activity.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Asked now rather than on the first tap, so the microphone opens at
        // once and in the right language.
        askPhoneWhatItSpeaks()

        tvNetBalance = findViewById(R.id.tvNetBalance)
        tvTotalGet = findViewById(R.id.tvTotalGet)
        tvTotalGive = findViewById(R.id.tvTotalGive)
        tvPartySummary = findViewById(R.id.tvPartySummary)
        tvEmpty = findViewById(R.id.tvEmpty)

        val rv: RecyclerView = findViewById(R.id.rvParties)
        adapter = PartyAdapter(
            onClick = { party ->
                // While picking, a tap adds or removes. Otherwise it opens the
                // customer as it always did.
                if (selectedIds.isEmpty()) openParty(party) else toggleSelected(party)
            },
            onLongClick = { party -> toggleSelected(party) },
            isSelected = { id -> id in selectedIds }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        selectionBar = findViewById(R.id.selectionBar)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)

        btnSelectAll.setOnClickListener {
            // What is on screen, not what is in the book. If the owner has
            // searched or filtered down to a handful, "all" means that handful
            // — selecting a thousand customers they cannot see would be a
            // trap, not a convenience.
            val visible = shownParties.map { it.id }
            if (selectedIds.containsAll(visible)) selectedIds.clear()
            else selectedIds.addAll(visible)
            renderSelection()
        }

        findViewById<View>(R.id.btnDeleteSelected).setOnClickListener {
            confirmDeleteSelected()
        }

        findViewById<View>(R.id.btnScanQr).setOnClickListener { scanCustomerCard() }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddParty).setOnClickListener {
            showAddPartyChoice()
        }

        etSearch = findViewById(R.id.etSearchParties)

        // No dropdown. It floated over the list while the list narrowed
        // underneath it, so the same customer appeared twice — once in a panel
        // and once in a row — and the owner had to work out which to tap. The
        // list itself is the answer; typing narrows it and the row is right
        // there.
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = render()
        })

        val btnDateFilter = findViewById<MaterialButton>(R.id.btnFilterPartyDate)
        btnDateFilter.setOnClickListener {
            DateRangeFilter.choose(this, dateRange) { picked ->
                dateRange = picked
                btnDateFilter.text = DateRangeFilter.label(this, picked)
                render()
            }
        }

        findViewById<MaterialButton>(R.id.btnVoiceEntry).apply {
            setOnClickListener { startListening() }
            // Diagnostic scaffolding, and deliberately unadvertised: a long
            // press hands over the voice record. It comes out with the rest of
            // VoiceLog once voice entry's fate is settled.
            setOnLongClickListener { shareVoiceLog(); true }
        }

        findViewById<MaterialButton>(R.id.btnSortParties).setOnClickListener { showSortDialog() }

        // The two summary boxes are the filter. Tapping "I have to get" shows
        // the people behind that figure; tapping it again puts everyone back.
        // A shopkeeper reading a total is usually about to ask who is in it.
        findViewById<View>(R.id.boxTotalGet).setOnClickListener {
            sideFilter = if (sideFilter == SideFilter.TO_GET) SideFilter.ALL else SideFilter.TO_GET
            render()
        }
        findViewById<View>(R.id.boxTotalGive).setOnClickListener {
            sideFilter = if (sideFilter == SideFilter.TO_GIVE) SideFilter.ALL else SideFilter.TO_GIVE
            render()
        }

        // Show which box is doing the filtering. Without this a shortened list
        // looks like customers have gone missing rather than been narrowed.
        renderFilterState()

        ivEye = findViewById(R.id.ivEye)

        findViewById<View>(R.id.balanceRow).setOnClickListener {
            BalancePrivacy.toggle(this)
            renderNetBalance()
        }

        ivEye = findViewById(R.id.ivEye)

        setupBottomNav()

        observeData()

    }

    private fun observeData() {
        lifecycleScope.launch {
            dao.observePartiesWithBalance().collectLatest { list ->
                allParties = list

                // The two box totals: everything owed TO the shop (positive
                // balances, money to collect) and everything the shop owes OUT
                // (negative balances). These are the parts the net figure above
                // nets together.
                totalGet = list.filter { it.balance > 0 }.sumOf { it.balance }
                totalGive = list.filter { it.balance < 0 }.sumOf { -it.balance }
                renderTotals()
                renderPartySummary(list)
                render()
            }
        }
        lifecycleScope.launch {
            dao.observeNetBalance().collectLatest { net ->
                netBalance = net
                renderNetBalance()
            }
        }

        setupReminders()
    }

    /**
     * Daily reminders (cheques due, instalments, expiring stock, backup nudge).
     * Scheduling is idempotent. On Android 13+ notifications need a runtime
     * permission — asked exactly once, and a "no" is remembered and respected.
     */
    private fun setupReminders() {
        ReminderWorker.ensureChannel(this)
        ReminderWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // No "asked once" flag: allowBackup restores SharedPreferences
                // across reinstalls, so a remembered flag silently blocked the
                // dialog forever (found on the owner's device). Android itself
                // stops showing the dialog after two denials, so requesting on
                // every launch until granted cannot become spam.
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 313
                )
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
                    // A match on the number or the name is worth raising, but
                    // it is not the app's decision.
                    //
                    // Three different things look identical from here: two
                    // different men both called Ahmad, one man whose shop and
                    // land the owner keeps as separate accounts, and the same
                    // customer entered twice by mistake. Only the shopkeeper
                    // knows which. Opening the existing account silently gets
                    // the first two wrong; creating a second one silently gets
                    // the third wrong. So it asks.
                    //
                    // The number is checked first: a man goes in as "Bilal"
                    // one week and "Bilal Bhai" the next, but the number he
                    // answers on does not change.
                    val existing = (phone?.let { dao.findPartyByPhone(it) })
                        ?: dao.findPartyByName(name)

                    if (existing == null) {
                        dao.insertParty(
                            Party(
                                name = name,
                                phone = phone,
                                isCustomer = rbCustomer.isChecked
                            )
                        )
                        return@launch
                    }

                    confirmDuplicate(existing) { openExisting ->
                        lifecycleScope.launch {
                            if (openExisting) {
                                openParty(existing.toRow())
                            } else {
                                dao.insertParty(
                                    Party(
                                        name = name,
                                        phone = phone,
                                        isCustomer = rbCustomer.isChecked
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .show()
    }

    /**
     * Ask whether a near-match is the same customer.
     *
     * Shows the number alongside the name, because that is what tells two
     * Ahmads apart at a glance — and the shopkeeper reading it already knows
     * which of theirs is which.
     */
    private fun confirmDuplicate(existing: Party, onChoice: (openExisting: Boolean) -> Unit) {
        val detail = existing.phone?.let { "${existing.name}\n$it" } ?: existing.name

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.party_exists_title)
            .setMessage(getString(R.string.party_exists_message, detail))
            .setPositiveButton(R.string.open_existing) { _, _ -> onChoice(true) }
            .setNegativeButton(R.string.create_new_anyway) { _, _ -> onChoice(false) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    /** The list row shape, for a party fetched on its own. */
    private fun Party.toRow() = PartyWithBalance(
        id = id,
        name = name,
        phone = phone,
        isCustomer = isCustomer,
        photoPath = photoPath,
        creditLimit = creditLimit,
        // Filled in by the detail screen from the ledger itself; these are
        // only here because the row type carries them.
        balance = 0.0,
        lastActivity = 0L
    )

    /**
     * Scan a customer's card and open their ledger.
     *
     * The scanner is Google Play services' own — its UI, its camera session,
     * its models — which is why this app still declares no camera permission.
     * The trade is stated in the failure message: a phone without Play
     * services cannot scan, and is told so instead of shown nothing.
     *
     * Three outcomes, each with its own words:
     * - a card of ours whose customer is on the books → their ledger opens.
     * - a QR that is not ours (a payment code, a link, another shop's card)
     *   → "not a customer card". QrTag decides this, and its tests hold the
     *   line: a near-miss on an identity is a miss.
     * - a card of ours with no living customer — deleted, or another phone's
     *   book → "no customer answers to it". Distinct from the second case,
     *   because the owner holding a card they themselves issued deserves to
     *   know the card is fine and the customer is what's missing.
     *
     * Cancelling the scanner is not an outcome and says nothing.
     */
    private fun scanCustomerCard() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode ->
                val token = QrTag.parse(barcode.rawValue)
                if (token == null) {
                    Toast.makeText(this, R.string.qr_not_ours, Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                lifecycleScope.launch {
                    val party = withContext(Dispatchers.IO) { dao.partyByQrToken(token) }
                    if (party == null) {
                        Toast.makeText(
                            this@KhataActivity, R.string.qr_no_customer, Toast.LENGTH_LONG
                        ).show()
                    } else {
                        startActivity(
                            Intent(this@KhataActivity, PartyDetailActivity::class.java)
                                .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, party.id)
                        )
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.qr_scan_failed, Toast.LENGTH_LONG).show()
            }
    }

    /** Add or drop one customer from the selection. */
    private fun toggleSelected(party: PartyWithBalance) {
        if (!selectedIds.remove(party.id)) selectedIds.add(party.id)
        renderSelection()
    }

    /** The bar, the count, and the rows, kept saying the same thing. */
    private fun renderSelection() {
        val picking = selectedIds.isNotEmpty()
        selectionBar.visibility = if (picking) View.VISIBLE else View.GONE
        if (picking) {
            tvSelectedCount.text = getString(R.string.selected_count, selectedIds.size)
            val visible = shownParties.map { it.id }
            btnSelectAll.setText(
                if (visible.isNotEmpty() && selectedIds.containsAll(visible)) R.string.clear_all
                else R.string.select_all
            )
        }
        adapter.notifyDataSetChanged()
    }

    /** Leave selection without deleting anything. */
    private fun clearSelection() {
        if (selectedIds.isEmpty()) return
        selectedIds.clear()
        renderSelection()
    }

    override fun onBackPressed() {
        // Back gets out of a selection before it leaves the screen. Someone who
        // has picked forty customers and wants out should not have to tap forty
        // times or risk the Delete button to escape.
        if (selectedIds.isNotEmpty()) clearSelection() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    /**
     * Deleting is never destructive — everything goes to the Recycle Bin,
     * entries and all, and can be restored for thirty days.
     *
     * The confirmation says how many, and says what they are worth. A ledger's
     * worst loss is not a deleted row, it is quietly losing sight of money
     * owed: remove a customer who owes eighty thousand and the shop's totals
     * simply drop by eighty thousand, with nothing on screen to say why. Both
     * directions are named separately, because money owed *to* someone is the
     * more serious of the two to forget — that one is somebody else's.
     */
    private fun confirmDeleteSelected() {
        val picked = allParties.filter { it.id in selectedIds }
        if (picked.isEmpty()) return

        val toCollect = picked.filter { it.balance > 0 }.sumOf { it.balance }
        val toPay = picked.filter { it.balance < 0 }.sumOf { -it.balance }
        val owing = picked.count { it.balance != 0.0 }

        val message = buildString {
            append(getString(R.string.delete_parties_confirm, picked.size))
            if (owing > 0) {
                append("\n\n")
                append(
                    getString(
                        R.string.delete_parties_owing,
                        owing,
                        Format.money(toCollect),
                        Format.money(toPay)
                    )
                )
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_parties_title, picked.size))
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    // All-or-nothing inside one transaction, under one
                    // timestamp: a crash partway leaves the ledger untouched,
                    // and a restore brings back exactly what one delete took.
                    dao.softDeleteParties(picked.map { it.id })
                    clearSelection()
                    Toast.makeText(this@KhataActivity, R.string.moved_to_bin, Toast.LENGTH_SHORT)
                        .show()
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

    /**
     * Filter, then sort. Search matches name or number — and the number match
     * strips separators from both sides, so "3001234" finds "0300-123 4567"
     * the way a person would expect it to.
     */
    private fun render() {
        val query = etSearch.text.toString().trim().lowercase()

        val filtered = if (query.isEmpty()) {
            allParties
        } else {
            allParties.filter { NameSearch.matches(it.name, it.phone, query) }
        }

        // Days first. A customer belongs in the window if their last dealing
        // falls inside it — "who did I deal with today" is the question, and
        // someone untouched for a month is not part of today's answer.
        val inRange = if (dateRange == DateRangeFilter.Range.ALL) {
            filtered
        } else {
            filtered.filter { dateRange.contains(it.lastActivity) }
        }

        // Then the side, if one is chosen. Settled accounts fall out of both:
        // someone at zero is neither owed nor owing.
        val bySide = when (sideFilter) {
            SideFilter.ALL -> inRange
            SideFilter.TO_GET -> inRange.filter { it.balance > 0 }
            SideFilter.TO_GIVE -> inRange.filter { it.balance < 0 }
        }

        val sorted = if (query.isNotEmpty()) {
            // While searching, the chosen sort steps aside for relevance —
            // by the same rule the contacts screen uses.
            NameSearch.sort(bySide, query) { it.name }
        } else when (sortMode) {
            SortMode.NAME_AZ -> bySide.sortedBy { it.name.lowercase() }
            SortMode.NAME_ZA -> bySide.sortedByDescending { it.name.lowercase() }
            // "Owes me most" means the largest positive balance at the top.
            SortMode.OWES_MOST -> bySide.sortedByDescending { it.balance }
            // "I owe most" is the mirror: the most negative balance first.
            SortMode.I_OWE_MOST -> bySide.sortedBy { it.balance }
            SortMode.RECENT -> bySide.sortedByDescending { it.lastActivity }
        }

        shownParties = sorted
        adapter.submitList(sorted)
        renderFilterState()

        tvEmpty.visibility = when {
            allParties.isEmpty() -> View.VISIBLE
            sorted.isEmpty() -> View.VISIBLE
            else -> View.GONE
        }
        tvEmpty.setText(
            if (allParties.isEmpty()) R.string.no_parties_yet
            else R.string.no_matching_parties
        )
    }

    /**
     * Dim whichever box is not filtering. Fading the other is quieter than
     * outlining the active one, and it reads at a glance: one box bright, the
     * list belongs to it.
     */
    private fun renderFilterState() {
        val get = findViewById<View>(R.id.boxTotalGet) ?: return
        val give = findViewById<View>(R.id.boxTotalGive) ?: return
        when (sideFilter) {
            SideFilter.ALL -> { get.alpha = 1f; give.alpha = 1f }
            SideFilter.TO_GET -> { get.alpha = 1f; give.alpha = 0.45f }
            SideFilter.TO_GIVE -> { get.alpha = 0.45f; give.alpha = 1f }
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_name_az),
            getString(R.string.sort_name_za),
            getString(R.string.sort_owes_most),
            getString(R.string.sort_i_owe_most),
            getString(R.string.sort_recent_activity)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, sortMode.ordinal) { dialog, which ->
                sortMode = SortMode.values()[which]
                render()
                dialog.dismiss()
            }
            .show()
    }

    /**
     * The main sections, visible instead of buried.
     *
     * They lived in an overflow menu until now, which in practice meant most
     * shopkeepers would never have discovered that a Cashbook or a Cheque
     * register existed at all. A feature nobody can find may as well not have
     * been built.
     */
    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Khata has no item of its own in the bar — it is one of the twelve
        // cards on Home. Home stays lit while its ledger is open.
        //
        // Selected BEFORE the listener is attached. Setting selectedItemId
        // fires the listener exactly as a tap would, and the Home branch
        // finishes this screen: with the listener already in place, opening
        // the ledger closed it again on the spot.
        nav.selectedItemId = R.id.nav_home

        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Home is below this screen in the stack — finish rather
                    // than stacking a second copy of it on top.
                    finish()
                    false
                }
                R.id.nav_more -> {
                    showMoreSheet()
                    false
                }
                else -> false
            }
        }
    }

    /** The set-once items, the same short list Home offers. */
    private fun showMoreSheet() {
        val options = arrayOf(
            getString(R.string.app_lock),
            getString(R.string.language),
            getString(R.string.help_support),
            getString(R.string.about_us)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.more_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppLockSettings()
                    1 -> startActivity(Intent(this, LanguageActivity::class.java))
                    // Reporting a problem lives inside Help now, so there is
                    // one door marked "something is wrong" rather than two.
                    2 -> startActivity(Intent(this, HelpActivity::class.java))
                    3 -> startActivity(Intent(this, AboutActivity::class.java))
                }
            }
            .show()
    }


    override fun onResume() {
        super.onResume()
        // Nothing to re-select here: Khata has no item in the bar, and
        // assigning selectedItemId would fire the listener and finish this
        // screen the moment it came back to the front.
    }

    /**
     * Show the net balance, or a mask over it.
     *
     * This runs on every update, not just on the tap — so a balance that
     * changes while hidden STAYS hidden. Revealing the figure the moment an
     * entry lands would defeat the whole point, and it would do so at the exact
     * moment the owner is holding the phone where someone can see it.
     */
    /**
     * Fill the two summary boxes. Called from BOTH the party-list stream (which
     * has just computed the totals) and renderNetBalance (for the privacy
     * toggle). The old code only filled them inside renderNetBalance, which runs
     * off the net-balance stream BEFORE the party list has set the totals — so
     * the boxes were stuck at zero even though the net figure was right.
     */
    /**
     * One line under the search: total customers, and how many with an
     * outstanding balance have gone quiet for 30+ days — the ones worth a
     * reminder. Both counts come straight from the live list, so they always
     * agree with what's on screen.
     */
    private fun renderPartySummary(list: List<com.innovation313.roshankhata.data.PartyWithBalance>) {
        val count = list.size
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val overdue = list.count { it.balance > 0 && it.lastActivity in 1 until cutoff }

        val countText = resources.getQuantityString(R.plurals.customer_count, count, count)
        val base = if (overdue > 0) {
            getString(R.string.summary_with_overdue, countText, overdue)
        } else {
            countText
        }

        // A quiet once-a-week nudge to back up, shown only when there's data to
        // protect. It rides on the same summary line so it costs no extra space.
        val backupDue = com.innovation313.roshankhata.data.BackupReminder
            .isReminderDue(this, hasData = count > 0)
        tvPartySummary.text = if (backupDue) {
            base + "  ·  " + getString(R.string.backup_reminder_hint)
        } else {
            base
        }
    }

    private fun renderTotals() {
        val hidden = BalancePrivacy.isHidden(this)
        tvTotalGet.text = if (hidden) BalancePrivacy.MASK else Format.money(totalGet)
        tvTotalGive.text = if (hidden) BalancePrivacy.MASK else Format.money(totalGive)
    }

    private fun renderNetBalance() {
        val hidden = BalancePrivacy.isHidden(this)

        tvNetBalance.text = if (hidden) {
            BalancePrivacy.MASK
        } else {
            Format.money(netBalance)
        }

        renderTotals()

        ivEye.setImageResource(
            if (hidden) R.drawable.ic_eye_closed else R.drawable.ic_eye_open
        )
        ivEye.contentDescription = getString(
            if (hidden) R.string.show_balance else R.string.hide_balance
        )
    }

    /**
     * One way into a party's ledger.
     *
     * Two paths to the same screen would eventually drift apart — one gaining a
     * check or an extra the other lacked — and the difference would show up as a
     * bug nobody could reproduce, because it would depend on how the owner got
     * there.
     */
    private fun openParty(party: PartyWithBalance) {
        startActivity(
            Intent(this, PartyDetailActivity::class.java)
                .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, party.id)
        )
    }

    // ---------- speaking an entry ----------

    /**
     * Ask the system to listen, in Urdu.
     *
     * RecognizerIntent rather than SpeechRecognizer: the system's own app does
     * the recording, so this app never needs the microphone permission and
     * never holds the audio. Urdu is requested; the recogniser falls back on
     * its own if the phone has no Urdu pack.
     */
    private val listen = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Every candidate, not just the winner. The app still acts on the
        // first — that behaviour is unchanged — but the rest are what tell us
        // whether a missed entry was misheard or merely mis-chosen, and until
        // now they were read and dropped in the same breath.
        val candidates = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val confidences = result.data
            ?.getFloatArrayExtra(android.speech.RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
            ?.toList()

        val heard = candidates.firstOrNull()

        if (heard.isNullOrEmpty()) {
            VoiceLog.nothingHeard(this, lastVoiceLanguage)
            Toast.makeText(this, R.string.voice_not_understood, Toast.LENGTH_SHORT).show()
        } else {
            handleSpoken(heard, candidates, confidences)
        }
    }

    /**
     * What this phone's recogniser can actually listen in, or null until it
     * has answered. Asked once per process — the answer does not change while
     * the app is open, and a shopkeeper tapping the microphone should not wait
     * on a broadcast.
     */
    private var speechLanguages: List<String>? = null

    /**
     * The language tag handed to the recogniser last time, or null when the
     * phone was left to its own setting. Kept only so the diagnostic record
     * can say what was asked for — a sentence heard in the wrong language
     * reads exactly like a sentence heard badly.
     */
    private var lastVoiceLanguage: String? = null

    /** The app's own language, as a BCP-47 tag: "ur", "ur-Latn", "en", … */
    private fun appLanguageTag(): String {
        val chosen = AppCompatDelegate.getApplicationLocales()
        if (!chosen.isEmpty) chosen[0]?.let { return it.toLanguageTag() }
        return resources.configuration.locales[0].toLanguageTag()
    }

    /**
     * Ask the phone which languages it can hear.
     *
     * Sent early and answered in the background, so the answer is usually
     * waiting by the time the microphone is tapped. If it is not, the intent
     * still goes out with the preferred tag — the same guess as before, but
     * now only a guess until the phone says otherwise.
     */
    private fun askPhoneWhatItSpeaks() {
        if (speechLanguages != null) return
        try {
            sendOrderedBroadcast(
                Intent(android.speech.RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS),
                null,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: Intent?) {
                        speechLanguages = getResultExtras(true)?.getStringArrayList(
                            android.speech.RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES
                        )
                    }
                },
                null, RESULT_OK, null, null
            )
        } catch (e: Exception) {
            // A phone with no recogniser at all cannot answer. startListening
            // already handles that case where the owner can see it.
        }
    }

    private fun startListening() {
        val choice = VoiceLanguage.choose(appLanguageTag(), speechLanguages)

        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Left off entirely when nothing suitable was found, so the phone
            // falls back to its own setting rather than to a language of this
            // app's choosing.
            choice.tag?.let {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, it)
            }
            // Ask for the runners-up as well. The app acts on the first either
            // way; the others exist so a missed entry can be told apart from a
            // misheard one instead of both looking the same from here.
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }

        lastVoiceLanguage = choice.tag

        // Listening in a language the owner did not pick is not something to
        // do quietly. Without this the app would appear to be broken, when in
        // fact the phone simply does not carry their language.
        if (!choice.exact) {
            Toast.makeText(
                this,
                getString(R.string.voice_language_missing, choice.tag ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }

        try {
            listen.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // Some phones ship without a recogniser at all. Say so rather than
            // leaving a button that does nothing.
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show what was understood and let the owner decide.
     *
     * Nothing is written here. The entry opens pre-filled on the party's own
     * screen, where every check the app already makes — the credit limit
     * warning above all — still runs before anything is saved. A microphone in
     * a busy shop is not a witness to trust with someone's money.
     */
    private fun handleSpoken(
        heard: String,
        candidates: List<String>,
        confidences: List<Float>?
    ) {
        // A new sentence replaces the last one's answer, whatever it was.
        hideVoiceStrip()

        val parsed = VoiceEntry.parse(heard, allParties.map { it.name })

        // Who was named, decided by the same measure that orders the picker.
        //
        // This used to come from the reader's own matcher, which keeps a
        // name's consonants and nothing else. To it "Ahsaan" and "Hassan" are
        // one name — h-s-n either way — so "Maine Ahsaan Munshi ko 5000 diya"
        // wrote five thousand rupees against Hassan without asking, in a book
        // that had no Ahsaan in it at all. Two matchers meant two answers, and
        // the weaker one was making the decision that mattered.
        //
        // Now there is one, and it only answers when a word was genuinely
        // recognised and the winner is clearly ahead. Anything short of that
        // falls through to the picker below, which is one tap.
        val spokenWords = VoiceEntry.nameWords(heard)
        val party = NameSearch.confidentMatch(allParties, spokenWords) { it.name }

        // Written down before the owner answers, so the record holds what the
        // app concluded rather than what it was corrected into. Nothing above
        // or below this call depends on it.
        VoiceLog.spoken(
            context = this,
            languageTag = lastVoiceLanguage,
            candidates = candidates,
            confidences = confidences,
            amount = parsed.amount,
            isGiven = parsed.isGiven,
            nameWords = spokenWords,
            topNames = NameSearch.scoreSpoken(allParties, spokenWords) { it.name }
                .take(5)
                .map { Triple(it.item.name, it.score, it.strongHits) },
            decision = when {
                parsed.amount == null || parsed.amount <= 0.0 -> "NO AMOUNT — refused"
                party != null -> "CONFIDENT — \"${party.name}\""
                else -> "NOT SURE — offered the picker"
            }
        )

        if (parsed.amount == null || parsed.amount <= 0.0) {
            VoiceLog.outcome(this, "shown the \"no amount\" message")
            showSpokenProblem(heard, getString(R.string.voice_no_amount))
            return
        }

        if (party == null) {
            // The figure was heard but the name was not. Offer the list rather
            // than the door: a shop with a hundred customers has said the hard
            // part already, and a dead end here would send them back to typing
            // the whole entry.
            pickPartyForSpoken(heard, parsed.amount, parsed.isGiven ?: true)
            return
        }

        val direction = when (parsed.isGiven) {
            true -> getString(R.string.i_gave)
            false -> getString(R.string.i_got)
            // Unsaid. The entry opens on "I Gave" but the owner picks before
            // saving; guessing here would put the money on the wrong side.
            null -> "—"
        }

        showVoiceStrip(
            heard = heard,
            summary = "${party.name}  ·  ${Format.money(parsed.amount)}  ·  $direction",
            // Waving the strip away is the clearest signal there is that the
            // confident match was the wrong customer: there is no way to
            // change the party once the entry has opened on their page, so an
            // owner who was handed a stranger has nothing else to do here.
            onDismiss = { VoiceLog.outcome(this, "DISMISSED the strip") },
            onOpen = {
                VoiceLog.outcome(this, "OPENED the entry for \"${party.name}\"")
                startActivity(
                    Intent(this, PartyDetailActivity::class.java)
                        .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, party.id)
                        .putExtra(PartyDetailActivity.EXTRA_VOICE_AMOUNT, parsed.amount)
                        .putExtra(
                            PartyDetailActivity.EXTRA_VOICE_IS_GIVEN,
                            parsed.isGiven ?: true
                        )
                )
            }
        )
    }

    /**
     * Show what was understood as one strip under the search box.
     *
     * It was a full dialog before — a title, three lines and two spread
     * buttons, laid over the very list it described. A shopkeeper confirming
     * a figure they just spoke needs to read one line, not dismiss a window.
     */
    private fun showVoiceStrip(
        heard: String,
        summary: String,
        onOpen: () -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        val strip = findViewById<View>(R.id.voiceStrip)
        findViewById<TextView>(R.id.tvVoiceSummary).text = summary
        findViewById<TextView>(R.id.tvVoiceHeard).text = getString(R.string.voice_heard, heard)

        findViewById<MaterialButton>(R.id.btnVoiceOpen).setOnClickListener {
            strip.visibility = View.GONE
            onOpen()
        }
        findViewById<MaterialButton>(R.id.btnVoiceDismiss).setOnClickListener {
            strip.visibility = View.GONE
            onDismiss()
        }
        strip.visibility = View.VISIBLE
    }

    /** Take the strip down — a new sentence replaces the last one's answer. */
    private fun hideVoiceStrip() {
        findViewById<View>(R.id.voiceStrip).visibility = View.GONE
    }

    /**
     * Which customer did they mean?
     *
     * Shown when the amount was understood and the name could not be pinned
     * down with enough confidence to write against someone's money.
     *
     * The whole book is here, every time. What changes is the order: the
     * names that answer what was spoken come first, judged by the same rule
     * the search box uses, and everything else follows. Ordering rather than
     * filtering is deliberate — a spoken name arrives spelled however the
     * recogniser chose, and a filter that guesses wrong does not show a worse
     * list, it shows a list without the customer in it and tells the owner
     * something untrue about their own book.
     */
    private fun pickPartyForSpoken(heard: String, amount: Double, isGiven: Boolean) {
        if (allParties.isEmpty()) {
            showSpokenProblem(heard, getString(R.string.voice_no_party))
            return
        }

        val spoken = VoiceEntry.nameWords(heard)
        val choices = NameSearch.rankSpoken(
            allParties.sortedByDescending { it.lastActivity },
            spoken
        ) { it.name }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.voice_which_party, Format.money(amount)))
            .setItems(choices.map { it.name }.toTypedArray()) { _, which ->
                val chosen = choices[which]
                // The rank is the measure of our own ordering. Position one
                // means the ranking was right and only the confidence rule
                // held back; position ninety means it was not close.
                VoiceLog.outcome(
                    this,
                    "PICKED \"${chosen.name}\" from the list — " +
                        "position ${which + 1} of ${choices.size}"
                )
                startActivity(
                    Intent(this, PartyDetailActivity::class.java)
                        .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, chosen.id)
                        .putExtra(PartyDetailActivity.EXTRA_VOICE_AMOUNT, amount)
                        .putExtra(PartyDetailActivity.EXTRA_VOICE_IS_GIVEN, isGiven)
                )
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                VoiceLog.outcome(this, "CANCELLED the picker")
            }
            .setOnCancelListener {
                VoiceLog.outcome(this, "CANCELLED the picker")
            }
            .show()
    }

    /**
     * Hand the voice record over, or start it again.
     *
     * DIAGNOSTIC SCAFFOLDING — goes out with [VoiceLog]. Its wording is left
     * in English on purpose: translating a temporary screen into six locales
     * would cost more than the screen is worth, and only this phone's owner
     * will ever reach it.
     */
    private fun shareVoiceLog() {
        val kept = VoiceLog.count(this)
        if (kept == 0) {
            Toast.makeText(this, "Voice log is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Voice log")
            .setMessage(
                "$kept voice attempt${if (kept == 1) "" else "s"} recorded.\n\n" +
                    "This file holds what was heard and which customer was " +
                    "matched, including their names. It has never left this " +
                    "phone. Sending it is your choice."
            )
            .setPositiveButton("Share") { _, _ ->
                val file = VoiceLog.export(this)
                if (file == null) {
                    Toast.makeText(this, "Could not prepare the file.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, "Send voice log"))
            }
            .setNegativeButton("Clear") { _, _ ->
                VoiceLog.clear(this)
                Toast.makeText(this, "Voice log cleared.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun showSpokenProblem(heard: String, problem: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.voice_confirm_title)
            .setMessage("$problem\n\n" + getString(R.string.voice_heard, heard))
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
