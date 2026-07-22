package com.innovation313.roshankhata

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.innovation313.roshankhata.data.BalancePrivacy
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PartyAdapter
import com.innovation313.roshankhata.ui.PartySuggestionAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Roshan Khata — Innovation-313
 * Home: customers and suppliers with their outstanding balances.
 */
class KhataActivity : AppCompatActivity() {

    private lateinit var adapter: PartyAdapter
    private lateinit var etSearch: AutoCompleteTextView
    private lateinit var suggestions: PartySuggestionAdapter

    /** Everything from the DB. The list on screen is a view onto this. */
    private var allParties: List<PartyWithBalance> = emptyList()
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

        supportActionBar?.setDisplayShowTitleEnabled(false)

        tvNetBalance = findViewById(R.id.tvNetBalance)
        tvTotalGet = findViewById(R.id.tvTotalGet)
        tvTotalGive = findViewById(R.id.tvTotalGive)
        tvPartySummary = findViewById(R.id.tvPartySummary)
        tvEmpty = findViewById(R.id.tvEmpty)

        val rv: RecyclerView = findViewById(R.id.rvParties)
        adapter = PartyAdapter(
            onClick = { party -> openParty(party) },
            onLongClick = { party -> confirmDeleteParty(party) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddParty).setOnClickListener {
            showAddPartyChoice()
        }

        etSearch = findViewById(R.id.etSearchParties)

        // Tapping a suggestion goes STRAIGHT to the account. That is the whole
        // point — filtering the list still leaves the owner hunting for a row.
        suggestions = PartySuggestionAdapter(this) { party ->
            openParty(party)
        }
        etSearch.setAdapter(suggestions)

        etSearch.setOnItemClickListener { _, _, position, _ ->
            suggestions.getItem(position)?.let { party ->
                // Clear the box before leaving. Coming back to a stale query and
                // a filtered list — with no memory of having typed it — is a
                // small bewilderment the owner does not need.
                etSearch.setText("")
                openParty(party)
            }
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = render()
        })

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
                suggestions.setSource(list)

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
                    // One name, one ledger. Nothing stopped the same customer
                    // being added twice, and a second "Bilal" got a second
                    // page — so a single account appeared as two, with the
                    // balance split between them and neither one right.
                    val existing = dao.findPartyByName(name)
                    if (existing != null) {
                        Toast.makeText(
                            this@KhataActivity,
                            getString(R.string.party_exists, existing.name),
                            Toast.LENGTH_LONG
                        ).show()
                        // Open the one they already have rather than making
                        // them find it: this is almost always the account they
                        // were reaching for.
                        openParty(
                            PartyWithBalance(
                                id = existing.id,
                                name = existing.name,
                                phone = existing.phone,
                                isCustomer = existing.isCustomer,
                                photoPath = existing.photoPath,
                                creditLimit = existing.creditLimit,
                                balance = 0.0,
                                lastActivity = 0L
                            )
                        )
                        return@launch
                    }

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
                    Toast.makeText(this@KhataActivity, R.string.moved_to_bin, Toast.LENGTH_SHORT).show()
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
            val queryDigits = query.filter { it.isDigit() }
            allParties.filter { p ->
                p.name.lowercase().contains(query) ||
                    (queryDigits.isNotEmpty() &&
                        p.phone?.filter { it.isDigit() }?.contains(queryDigits) == true)
            }
        }

        // Then the side, if one is chosen. Settled accounts fall out of both:
        // someone at zero is neither owed nor owing.
        val bySide = when (sideFilter) {
            SideFilter.ALL -> filtered
            SideFilter.TO_GET -> filtered.filter { it.balance > 0 }
            SideFilter.TO_GIVE -> filtered.filter { it.balance < 0 }
        }

        val sorted = when (sortMode) {
            SortMode.NAME_AZ -> bySide.sortedBy { it.name.lowercase() }
            SortMode.NAME_ZA -> bySide.sortedByDescending { it.name.lowercase() }
            // "Owes me most" means the largest positive balance at the top.
            SortMode.OWES_MOST -> bySide.sortedByDescending { it.balance }
            // "I owe most" is the mirror: the most negative balance first.
            SortMode.I_OWE_MOST -> bySide.sortedBy { it.balance }
            SortMode.RECENT -> bySide.sortedByDescending { it.lastActivity }
        }

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
     * One way into a party's ledger, used by the list AND the suggestions.
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
}
