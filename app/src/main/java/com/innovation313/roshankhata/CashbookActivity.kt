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
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.DayClose
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.CashAdapter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.customerBalanceColour
import com.innovation313.roshankhata.ui.signedTotalColourOnDark
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
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
            val last = dao.lastDayClose()
            val now = System.currentTimeMillis()
            val from = last?.closedAt ?: 0L
            val cbIn = dao.cashbookCashBetween(true, from, now)
            val cbOut = dao.cashbookCashBetween(false, from, now)
            val kIn = dao.khataCashBetween(false, from, now)
            val kOut = dao.khataCashBetween(true, from, now)
            val cashIn = cbIn + kIn
            val cashOut = cbOut + kOut
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
                .setPositiveButton(R.string.save) { _, _ ->
                    val counted = com.innovation313.roshankhata.ui.CalcPad.figure(etCounted.text)
                    if (counted == null) {
                        Toast.makeText(this@CashbookActivity, R.string.dayclose_need_count, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    // A drawer that does not match the books needs its reason written down (POS Phase 4):
                    // a figure with no story is one nobody can answer for next week.
                    val openingNow = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0
                    val expectedNow = DayCloseMath.expected(openingNow, cashIn, cashOut)
                    if (kotlin.math.abs(DayCloseMath.difference(counted, expectedNow)) >= 0.005 && etNote.text.toString().isBlank()) {
                        Toast.makeText(this@CashbookActivity, R.string.z_variance_reason, Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                    val close = DayClose(
                        day = ReminderLog.dayOf(now),
                        opening = com.innovation313.roshankhata.ui.CalcPad.figure(etOpening.text) ?: 0.0,
                        cashIn = LineMath.round(cashIn),
                        cashOut = LineMath.round(cashOut),
                        counted = counted,
                        note = etNote.text.toString().trim().ifEmpty { null },
                        closedAt = now
                    )
                    AppScope.launch { dao.saveDayClose(close); dao.audit(AuditActs.DAY_CLOSED, "day_close", null, auditAmount(close.counted)) }
                }
                .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
        }
    }

    /** The Z report for today: every figure summed from the rows, shown and shareable. */
    private fun showDayReport() {
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

    private fun observe() {
        lifecycleScope.launch {
            dao.observeCashEntries().collectLatest { list ->
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
                            timestamp = chosenTime
                        )
                    )
                }
            }
            .show().also { com.innovation313.roshankhata.ui.KeyboardFollow.dialog(it) }
    }

    private fun confirmDelete(entry: CashEntry) {
        if (!com.innovation313.roshankhata.ui.AccessUi.may(this, dao) { confirmDelete(entry) }) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_cash_entry)
            .setMessage(R.string.delete_cash_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    AppScope.launch { dao.softDeleteCashEntry(entry.id); dao.audit(AuditActs.CASH_DELETED, "cashbook", entry.id, auditAmount(entry.amount)) }.join()
                    Toast.makeText(
                        this@CashbookActivity,
                        R.string.moved_to_bin,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }
}
