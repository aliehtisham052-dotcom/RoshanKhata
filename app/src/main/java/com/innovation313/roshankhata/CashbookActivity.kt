package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.audit
import com.innovation313.roshankhata.data.auditAmount
import com.innovation313.roshankhata.data.AuditAction as AuditActs

import com.innovation313.roshankhata.data.get

import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.DateTimeField

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.ReminderLog
import com.innovation313.roshankhata.data.LineMath
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.PaymentRef
import com.innovation313.roshankhata.data.labelOf
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.DayClose
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.CashAdapter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.customerBalanceColour
import com.innovation313.roshankhata.ui.signedTotalColourOnDark
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cashbook: business money with no customer attached.
 *
 * Kept firmly apart from the party ledger. Rent paid, wages paid, a walk-in
 * cash sale — none of it changes what any customer owes, and none of it should
 * ever touch a balance. Mixing the two would either invent a phantom party or
 * quietly corrupt a real one's account.
 */
class CashbookActivity : BaseActivity() {

    private lateinit var adapter: CashAdapter
    private lateinit var tvIn: TextView
    private lateinit var tvOut: TextView
    private lateinit var tvNet: TextView
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cashbook)
        // Owner only, like the screen itself: a helper's phone shows no staff list.
        findViewById<android.view.View>(R.id.btnStaff).apply {
            visibility = if (com.innovation313.roshankhata.data.ViewerMode.isOn(this@CashbookActivity))
                android.view.View.GONE else android.view.View.VISIBLE
            setOnClickListener { startActivity(android.content.Intent(this@CashbookActivity, StaffActivity::class.java)) }
        }

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        tvIn = findViewById(R.id.tvCashIn)
        tvOut = findViewById(R.id.tvCashOut)
        tvNet = findViewById(R.id.tvCashNet)
        tvEmpty = findViewById(R.id.tvNoCash)

        adapter = CashAdapter { entry -> confirmDelete(entry) }
        val rv: RecyclerView = findViewById(R.id.rvCash)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        // The newest page first, more as the owner scrolls (23 Oct 2026, the review: every row was read).
        com.innovation313.roshankhata.ui.GrowingList.attach(rv, listLimit) { listShown }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddCash).setOnClickListener {
            showDirectionChoice()
        }

        findViewById<View>(R.id.btnCloseDay).setOnClickListener { showDayClose() }
        // The day's figures (POS Phase 4): a long press on Close the day shows the Z report.
        findViewById<View>(R.id.btnCloseDay).setOnLongClickListener { showDayReport(); true }
        findViewById<View>(R.id.btnDayReport).setOnClickListener { showDayReport() }
        val tvLastClose = findViewById<TextView>(R.id.tvLastClose)
        lifecycleScope.launch {
            dao.observeLastDayClose().collectLatest { c ->
                tvLastClose.text = if (c == null) getString(R.string.dayclose_never) else getString(
                    R.string.dayclose_last, Format.dateOnly(c.closedAt), Format.signedTotal(c.difference)
                )
            }
        }

        observe()
    }

    /**
     * Galla milan (2 Oct): what the drawer should hold since the last count —
     * the last counted cash, plus cash in (cashbook income and khata receipts
     * marked Cash), minus cash out (expenses and khata payments marked Cash) —
     * against what the owner counts now. Entries with no payment method are
     * not guessed at; the help line says so.
     */
    private fun showDayClose() {
        lifecycleScope.launch {
            // The figures by the one shared rule (DayCloseMath.drawerByTheBooks, 29 Oct 2026): the computer's Home,
            // Cashbook and Close the day read the same, so no two screens can disagree on the drawer.
            val figures = DayCloseMath.drawerByTheBooks(dao)
            val last = figures.last
            val now = figures.now
            val cbIn = figures.cbIn
            val cbOut = figures.cbOut
            val kIn = figures.kIn
            val kOut = figures.kOut
            val cashIn = figures.cashIn
            val cashOut = figures.cashOut
            if (isFinishing || isDestroyed) return@launch

            val view = layoutInflater.inflate(R.layout.dialog_day_close, null)
            val etOpening = view.findViewById<EditText>(R.id.etOpening)
            val etCounted = view.findViewById<EditText>(R.id.etCounted)
            val etNote = view.findViewById<EditText>(R.id.etCloseNote)
            val tvBreak = view.findViewById<TextView>(R.id.tvBreakdown)
            val tvDiff = view.findViewById<TextView>(R.id.tvDifference)
            com.innovation313.roshankhata.ui.TextFit.relax(view)
            last?.let { etOpening.setText(Format.plain(it.counted)) }

            // Both figures on the app's calculator pad (21 Oct): it types
            // into whichever of the two has the focus, so a drawer can be
            // counted note by note — "50×12+100×7" — and the note box keeps
            // the phone's keyboard. Read back with CalcPad.figure, which keeps
            // a counted zero (an empty drawer is a real count).
            com.innovation313.roshankhata.ui.CalcPad.bindAll(
                root = view,
                pad = view.findViewById(R.id.closeKeypad),
                fields = listOf(etOpening, etCounted),
                results = mapOf(
                    etOpening to view.findViewById<TextView>(R.id.tvOpeningResult),
                    etCounted to view.findViewById<TextView>(R.id.tvCountedResult)
                )
            )

            fun refresh() {
                val opening = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0
                val expected = DayCloseMath.expected(opening, cashIn, cashOut)
                tvBreak.text = getString(
                    R.string.dayclose_breakdown,
                    Format.money(cbIn), Format.money(kIn), Format.money(cbOut), Format.money(kOut), Format.money(expected)
                )
                val counted = com.innovation313.roshankhata.ui.CalcPad.figure(etCounted.text)
                tvDiff.text = when {
                    counted == null -> ""
                    else -> {
                        val d = DayCloseMath.difference(counted, expected)
                        // A difference needs its reason: the note box says so before Save is pressed (R155).
                        etNote.hint = getString(if (kotlin.math.abs(d) >= 0.005) R.string.z_variance_reason else R.string.dayclose_note_hint)
                        when {
                            Money.isPositive(d) -> getString(R.string.dayclose_over, Format.money(d))
                            Money.isPositive(-d) -> getString(R.string.dayclose_short, Format.money(-d))
                            else -> getString(R.string.dayclose_match)
                        }
                    }
                }
            }
            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) = refresh()
            }
            etOpening.addTextChangedListener(watcher)
            etCounted.addTextChangedListener(watcher)
            refresh()

            MaterialAlertDialogBuilder(this@CashbookActivity)
                .setTitle(R.string.dayclose_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                // Save is wired after show() (23 Oct 2026, review R155): a builder's button closes the
                // dialog whatever its listener returns, so a refused save threw away both counted figures.
                .setPositiveButton(R.string.save, null)
                .show().also { dialog ->
                    com.innovation313.roshankhata.ui.CalcPad.dock(dialog, view, view.findViewById(R.id.closeKeypad))
                    com.innovation313.roshankhata.ui.KeyboardFollow.dialog(dialog)
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                        val counted = com.innovation313.roshankhata.ui.CalcPad.figure(etCounted.text)
                        if (counted == null) {
                            Toast.makeText(this@CashbookActivity, R.string.dayclose_need_count, Toast.LENGTH_SHORT).show()
                            etCounted.requestFocus()
                            return@setOnClickListener
                        }
                        // A drawer that does not match the books needs its reason written down (POS Phase 4):
                        // a figure with no story is one nobody can answer for next week.
                        val openingNow = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0
                        val expectedNow = DayCloseMath.expected(openingNow, cashIn, cashOut)
                        if (kotlin.math.abs(DayCloseMath.difference(counted, expectedNow)) >= 0.005 && etNote.text.toString().isBlank()) {
                            Toast.makeText(this@CashbookActivity, R.string.z_variance_reason, Toast.LENGTH_LONG).show()
                            // The box it wants sits below the whole pad: brought on screen and given the focus.
                            etNote.requestFocus()
                            (view as? android.widget.ScrollView)?.let { sv -> sv.post { sv.smoothScrollTo(0, etNote.top) } }
                            return@setOnClickListener
                        }
                        val close = DayClose(
                            // The working day: a drawer counted after midnight is the evening before's (23 Oct 2026).
                            day = DayCloseMath.businessDayOf(now),
                            opening = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0,
                            cashIn = LineMath.round(cashIn),
                            cashOut = LineMath.round(cashOut),
                            counted = counted,
                            note = etNote.text.toString().trim().ifEmpty { null },
                            closedAt = now
                        )
                        AppScope.launch { dao.saveDayClose(close) }
                        dialog.dismiss()
                    }
                }
        }
    }

    /** The Z report for today: every figure summed from the rows, shown and shareable. */
    private fun showDayReport() {
        // The day's profit, expected cash and the till's difference are the shop's figures (23 Oct 2026, the
        // review): a cashier needs a manager's PIN, as for every other report.
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao, com.innovation313.roshankhata.data.Access.Permission.REPORTS) { showDayReport() }) return
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val day = DayCloseMath.dayOf(now)
            val from = DayCloseMath.startOf(day)
            val report = withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.ZReport.compute(dao, from, from + 24L * 60 * 60 * 1000) }
            val words = com.innovation313.roshankhata.data.AndroidWords(this@CashbookActivity)
            val lines: List<Pair<String, String>> = report.lines(words) { v: Double -> Format.money(v) }
            val text = Format.dateOnly(now) + "\n" +
                lines.joinToString("\n") { (k, v) -> "$k: $v" } +
                (if (report.counted == null) "\n\n" + getString(R.string.z_no_close) else "")
            MaterialAlertDialogBuilder(this@CashbookActivity)
                .setTitle(R.string.z_title)
                .setMessage(text)
                .setPositiveButton(R.string.share) { _, _ ->
                    startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.z_title) + "\n" + text), getString(R.string.z_title)))
                }
                .setNeutralButton(R.string.print_receipt) { _, _ -> com.innovation313.roshankhata.ui.TextReceipt.print(this@CashbookActivity, lifecycleScope, getString(R.string.z_title) + "\n" + text) }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private val listLimit = kotlinx.coroutines.flow.MutableStateFlow(com.innovation313.roshankhata.data.ListWindow.PAGE)
    private var listShown = 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observe() {
        lifecycleScope.launch {
            listLimit.flatMapLatest { dao.observeCashPage(it) }.collectLatest { list ->
                listShown = list.size
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            combine(
                dao.observeCashIncome(),
                dao.observeCashExpense()
            ) { income, expense -> income to expense }
                .collectLatest { (income, expense) ->
                    tvIn.text = Format.money(income)
                    tvOut.text = Format.money(expense)

                    // Net carried no sign at all: Format.money() takes abs()
                    // on purpose, because everywhere else the direction is
                    // already said by a label or a + / − beside it. Here it
                    // was the whole meaning, so Rs 50,000 short read as
                    // Rs 50,000 in hand. Sign and colour both now say which
                    // way the month went.
                    val net = income - expense
                    tvNet.text = Format.signedTotal(net)
                    tvNet.setTextColor(
                        ContextCompat.getColor(this@CashbookActivity, Format.signedTotalColourOnDark(net))
                    )
                }
        }
    }

    /** In or out — asked first, because it changes what the entry means. */
    private fun showDirectionChoice() {
        // In on the "got" green, out on the "gave" red, with the same arrows
        // as the khata (10 Oct).
        val options = listOf(
            ChoiceSheet.Option(getString(R.string.cash_income), icon = R.drawable.ic_arrow_got,
                iconTint = R.color.bal_i_owe, tile = R.color.summary_give_bg),
            ChoiceSheet.Option(getString(R.string.cash_expense), icon = R.drawable.ic_arrow_gave,
                iconTint = R.color.bal_owed_to_me, tile = R.color.summary_get_bg)
        )
        ChoiceSheet.show(this, getString(R.string.add_cash_entry), options) { which ->
            showAddDialog(isIncome = which == 0)
        }
    }

    private fun showAddDialog(isIncome: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_add_cash, null)
        com.innovation313.roshankhata.ui.TextFit.relax(view)
        val etAmount: EditText = view.findViewById(R.id.etCashAmount)
        val etCategory: AutoCompleteTextView = view.findViewById(R.id.etCashCategory)
        val etNote: EditText = view.findViewById(R.id.etCashNote)

        // How the money moved and its reference (29 Oct 2026): the add-entry form's chips, one per
        // PaymentMethod.ALL, Cash checked first; the reference box only beside a way that has one.
        val cgMethod: com.google.android.material.chip.ChipGroup = view.findViewById(R.id.cgCashMethod)
        val etRef: EditText = view.findViewById(R.id.etCashRef)
        val chipKeys = HashMap<Int, String>()
        PaymentMethod.ALL.forEach { key ->
            val chip = layoutInflater.inflate(R.layout.item_rate_list_chip, cgMethod, false) as com.google.android.material.chip.Chip
            chip.id = View.generateViewId()
            chip.text = PaymentMethod.labelOf(this, key)
            chipKeys[chip.id] = key
            cgMethod.addView(chip)
            if (key == PaymentMethod.CASH) cgMethod.check(chip.id)
        }
        fun chosenMethod(): String? = chipKeys[cgMethod.checkedChipId]
        fun showRefBox() { etRef.visibility = if (PaymentRef.applies(chosenMethod())) View.VISIBLE else View.GONE }
        // After the first check (lesson 4): the listener only follows the chip.
        cgMethod.setOnCheckedStateChangeListener { _, _ -> showRefBox() }
        showRefBox()

        // The app's own calculator pad for the amount (21 Oct), as on the
        // add-entry form; the category and note keep the phone's keyboard.
        com.innovation313.roshankhata.ui.CalcPad.bindAll(
            root = view,
            pad = view.findViewById(R.id.cashKeypad),
            fields = listOf(etAmount),
            results = mapOf(etAmount to view.findViewById<TextView>(R.id.tvCashAmountResult))
        )

        // The owner's own categories first, then our starting suggestions.
        // Their words for their business beat ours.
        lifecycleScope.launch {
            val used = dao.cashCategories()
            val defaults = resources.getStringArray(R.array.cash_categories).toList()
            val merged = (used + defaults).distinct()

            etCategory.setAdapter(
                ArrayAdapter(
                    this@CashbookActivity,
                    R.layout.item_dropdown_row,
                    merged
                )
            )
        }

        // When the cash actually moved. Defaults to now, which is right most of
        // the time, but the day's takings are often written up at closing.
        var chosenTime = System.currentTimeMillis()
        DateTimeField.attach(
            activity = this,
            button = view.findViewById(R.id.btnCashDate),
            initial = chosenTime
        ) { chosenTime = it }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isIncome) R.string.cash_income else R.string.cash_expense)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = Calc.evalAmount(etAmount.text.toString())
                if (amount == null) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val category = etCategory.text.toString().trim()
                if (category.isEmpty()) {
                    Toast.makeText(this, R.string.enter_category, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // AppScope, not lifecycleScope — see AppScope's own comment.
                // This dialog closes the instant Save is tapped and shows no
                // progress, so a quick Back press right after could cancel the
                // write before it landed, taking the typed amount, category,
                // note and date with it.
                AppScope.launch {
                    dao.insertCashEntry(
                        CashEntry(
                            amount = amount,
                            isIncome = isIncome,
                            category = category,
                            note = etNote.text.toString().trim().ifEmpty { null },
                            timestamp = chosenTime,
                            paymentMethod = chosenMethod(),
                            paymentRef = PaymentRef.clean(chosenMethod(), etRef.text.toString())
                        )
                    )
                }
            }
            .show().also { com.innovation313.roshankhata.ui.CalcPad.dock(it, view, view.findViewById(R.id.cashKeypad)); com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
    }

    private fun confirmDelete(entry: CashEntry) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmDelete(entry) }) return
        lifecycleScope.launch {
            // A line a counter ticket or a return wrote is changed from there (22 Oct 2026, the review: deleting
            // it here left the drawer expecting less while the ticket still read as paid).
            val owner = withContext(Dispatchers.IO) { runCatching { dao.cashOwner(entry.id) }.getOrNull() }
            if (isFinishing || isDestroyed) return@launch
            if (owner != null) {
                MaterialAlertDialogBuilder(this@CashbookActivity)
                    .setTitle(R.string.entry_linked_title)
                    .setMessage(if (owner == "return") R.string.entry_linked_return else R.string.entry_linked_sale)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@CashbookActivity)
                .setTitle(R.string.delete_cash_entry)
                .setMessage(R.string.delete_cash_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        // Checked again inside the delete's own transaction, with its audit row.
                        val done = AppScope.async { runCatching { dao.softDeleteOwnCashEntry(entry.id, auditAmount(entry.amount)) }.getOrDefault(false) }.await()
                        Toast.makeText(
                            this@CashbookActivity,
                            if (done) R.string.moved_to_bin else R.string.entry_linked_sale,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .show()
        }
    }
}
