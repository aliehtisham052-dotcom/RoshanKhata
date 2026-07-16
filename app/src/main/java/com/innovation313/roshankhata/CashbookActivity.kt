package com.innovation313.roshankhata

import com.innovation313.roshankhata.ui.Calc

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.innovation313.roshankhata.data.CashEntry
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
class CashbookActivity : AppCompatActivity() {

    private lateinit var adapter: CashAdapter
    private lateinit var tvIn: TextView
    private lateinit var tvOut: TextView
    private lateinit var tvNet: TextView
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cashbook)

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

        observe()
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
                    tvNet.text = Format.money(income - expense)
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

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isIncome) R.string.cash_income else R.string.cash_expense)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val amount = Calc.eval(etAmount.text.toString())
                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this, R.string.enter_valid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val category = etCategory.text.toString().trim()
                if (category.isEmpty()) {
                    Toast.makeText(this, R.string.enter_category, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    dao.insertCashEntry(
                        CashEntry(
                            amount = amount,
                            isIncome = isIncome,
                            category = category,
                            note = etNote.text.toString().trim().ifEmpty { null }
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
                    dao.softDeleteCashEntry(entry.id)
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
