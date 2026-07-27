package com.innovation313.roshankhata

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.Zakat
import com.innovation313.roshankhata.data.ZakatInputs
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Zakat reference calculator.
 *
 * This screen is a reference aid, never a ruling. The disclaimer is shown
 * at the top of the screen — not buried below the result — because the number
 * this produces could otherwise be mistaken for a fatwa.
 */
class ZakatActivity : AppCompatActivity() {

    private lateinit var etSilverPrice: EditText
    private lateinit var etCashStock: EditText
    private lateinit var cbIncludeDoubtful: MaterialCheckBox

    private lateinit var tvNisabValue: TextView
    private lateinit var tvCertain: TextView
    private lateinit var tvQarzeHasna: TextView
    private lateinit var tvDoubtful: TextView
    private lateinit var tvPayables: TextView
    private lateinit var tvWealth: TextView
    private lateinit var tvZakatDue: TextView
    private lateinit var tvNisabStatus: TextView
    private lateinit var toggleNisab: com.google.android.material.button.MaterialButtonToggleGroup
    private lateinit var tvNisabStandardNote: TextView
    private lateinit var toggleUnit: com.google.android.material.button.MaterialButtonToggleGroup
    private lateinit var tvPriceWarning: TextView
    private var useGold = false

    /**
     * Tola by default. It is the only unit a sarafa bazaar quotes, so it is
     * the only unit the owner will have a number for without doing arithmetic
     * first — and doing that arithmetic in his head is how 66 was entered for
     * silver.
     */
    private var perTola = true

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Latest ledger figures, refreshed reactively. */
    private var inputs = ZakatInputs(0.0, 0.0, 0.0, 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zakat)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        etSilverPrice = findViewById(R.id.etSilverPrice)
        toggleNisab = findViewById(R.id.toggleNisab)
        tvNisabStandardNote = findViewById(R.id.tvNisabStandardNote)

        // Default to silver (lower nisab). Selecting gold swaps the standard and
        // the field hint; the note under the result explains what each means.
        toggleNisab.check(R.id.btnSilver)
        tvNisabStandardNote.setText(R.string.nisab_silver_note)
        toggleNisab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            useGold = checkedId == R.id.btnGold
            etSilverPrice.setHint(
                if (useGold) R.string.gold_price_hint else R.string.silver_price_hint
            )
            tvNisabStandardNote.setText(
                if (useGold) R.string.nisab_gold_note else R.string.nisab_silver_note
            )
            recalculate()
        }

        toggleUnit = findViewById(R.id.toggleUnit)
        tvPriceWarning = findViewById(R.id.tvPriceWarning)
        toggleUnit.check(R.id.btnPerTola)
        toggleUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            perTola = checkedId == R.id.btnPerTola
            recalculate()
        }

        etCashStock = findViewById(R.id.etCashStock)
        cbIncludeDoubtful = findViewById(R.id.cbIncludeDoubtful)

        tvNisabValue = findViewById(R.id.tvNisabValue)
        tvCertain = findViewById(R.id.tvCertain)
        tvQarzeHasna = findViewById(R.id.tvQarzeHasna)
        tvDoubtful = findViewById(R.id.tvDoubtful)
        tvPayables = findViewById(R.id.tvPayables)
        tvWealth = findViewById(R.id.tvWealth)
        tvZakatDue = findViewById(R.id.tvZakatDue)
        tvNisabStatus = findViewById(R.id.tvNisabStatus)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = recalculate()
        }
        etSilverPrice.addTextChangedListener(watcher)
        etCashStock.addTextChangedListener(watcher)
        cbIncludeDoubtful.setOnCheckedChangeListener { _, _ -> recalculate() }

        observeLedger()
    }

    private fun observeLedger() {
        lifecycleScope.launch {
            dao.observeZakatBalancesByParty().collectLatest { rows ->

                // Per customer, so money owed to the shop and money the shop
                // owes stay two different things all the way to the screen.
                inputs = Zakat.fromParties(rows)

                tvCertain.text =
                    getString(R.string.certain_receivables, Format.money(inputs.certainReceivables))
                tvQarzeHasna.text =
                    getString(R.string.qarze_hasna_given, Format.money(inputs.qarzeHasnaGiven))
                tvDoubtful.text =
                    getString(R.string.doubtful_receivables, Format.money(inputs.doubtfulReceivables))
                tvPayables.text =
                    getString(R.string.you_owe_others, Format.money(inputs.payables))

                recalculate()
            }
        }
    }

    private fun recalculate() {
        // evalPad, not toDoubleOrNull. Every other amount field in the app
        // totals a sum typed into it — "2500+1200" or "300*40" — and this one
        // silently read the whole thing as zero, so a shopkeeper adding up
        // their stock in the box got no answer and no reason why.
        val price = Calc.evalPad(etSilverPrice.text.toString()) ?: 0.0
        val cashStock = Calc.evalPad(etCashStock.text.toString()) ?: 0.0

        val nisab = Zakat.nisab(price, gold = useGold, perTola = perTola)

        // The working, not just the answer. The owner asked to be able to
        // trust this figure himself, and a number he has to take on faith is
        // not one he can check. Spelled out, "52.5 tola of silver x your rate"
        // also shows him which unit he is being asked for.
        tvNisabValue.text = if (nisab > 0) {
            getString(
                R.string.nisab_working,
                Calc.trim(Zakat.nisabWeight(useGold, perTola)) + " " +
                    getString(if (perTola) R.string.unit_tola else R.string.unit_gram),
                getString(if (useGold) R.string.metal_gold else R.string.metal_silver),
                Format.money(price),
                Format.money(nisab)
            )
        } else {
            getString(R.string.nisab_enter_price)
        }

        // A rate out by an order of magnitude is almost always the other unit.
        // Said once, quietly, and never in the way of the answer.
        tvPriceWarning.visibility =
            if (Zakat.priceLooksOff(price, gold = useGold, perTola = perTola)) {
                tvPriceWarning.text = getString(R.string.price_looks_off)
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

        val wealth = Zakat.zakatableWealth(
            inputs = inputs,
            cashAndStock = cashStock,
            includeDoubtful = cbIncludeDoubtful.isChecked
        )
        tvWealth.text = Format.money(wealth)

        val due = Zakat.zakatDue(wealth, nisab)
        tvZakatDue.text = Format.money(due)

        tvNisabStatus.text = when {
            nisab <= 0.0 -> getString(R.string.nisab_needed_first)
            Zakat.meetsNisab(wealth, nisab) -> getString(R.string.above_nisab)
            else -> getString(R.string.below_nisab)
        }
    }
}
