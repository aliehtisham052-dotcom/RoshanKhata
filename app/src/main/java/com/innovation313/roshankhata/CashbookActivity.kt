package com.innovation313.roshankhata

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
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.DayClose
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.CashAdapter
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
            val cbIn = dao.cashbookTotalBetween(true, from, now)
            val cbOut = dao.cashbookTotalBetween(false, from, now)
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
            last?.let { etOpening.setText(Format.plain(it.counted)) }

            fun refresh() {
                val opening = Digits.parse(etOpening.text.toString()) ?: 0.0
                val expected = DayCloseMath.expected(opening, cashIn, cashOut)
                tvBreak.text = getString(
                    R.string.dayclose_breakdown,
                    Format.money(cbIn), Format.money(kIn), Format.money(cbOut), Format.money(kOut), Format.money(expected)
                )
                val counted = Digits.parse(etCounted.text.toString())
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
                    val counted = Digits.parse(etCounted.text.toString())
                    if (counted == null) {
                        Toast.makeText(this@CashbookActivity, R.string.dayclose_need_count, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val close = DayClose(
                        day = ReminderLog.dayOf(now),
                        opening = Digits.parse(etOpening.text.toString()) ?: 0.0,
                        cashIn = LineMath.round(cashIn),
                        cashOut = LineMath.round(cashOut),
                        counted = counted,
                        note = etNote.text.toString().trim().ifEmpty { null },
                        closedAt = now
                    )
                    AppScope.launch { dao.saveDayClose(close) }
                }
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
        val options = arrayOf(
            getString(R.string.cash_income),
            getString(R.string.cash_expense)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_cash_entry)
            .setItems(options) { _, which ->
                showAddDialog(isIncome = which == 0)
            }
            .show()
    }

    private fun showAddDialog(isIncome: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_add_cash, null)
        val etAmount: EditText = view.findViewById(R.id.etCashAmount)
        val etCategory: AutoCompleteTextView = view.findViewById(R.id.etCashCategory)
        val etNote: EditText = view.findViewById(R.id.etCashNote)

        // The owner's own categories first, then our starting suggestions.
        // Their words for their business beat ours.
        lifecycleScope.launch {
            val used = dao.cashCategories()
            val defaults = resources.getStringArray(R.array.cash_categories).toList()
            val merged = (used + defaults).distinct()

            etCategory.setAdapter(
                ArrayAdapter(
                    this@CashbookActivity,
                    android.R.layout.simple_dropdown_item_1line,
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
            .show()
    }

    private fun confirmDelete(entry: CashEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_cash_entry)
            .setMessage(R.string.delete_cash_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    AppScope.launch { dao.softDeleteCashEntry(entry.id) }.join()
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
