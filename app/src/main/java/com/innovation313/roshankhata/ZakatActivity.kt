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
import kotlinx.coroutines.flow.combine
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
    private var useGold = false

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Latest ledger figures, refreshed reactively. */
    private var inputs = ZakatInputs(0.0, 0.0, 0.0, 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zakat)

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
            combine(
                dao.observeCertainReceivables(),
                dao.observeDoubtfulReceivables(),
                dao.observeQarzeHasnaGiven()
            ) { certain, doubtful, qarz ->
                Triple(certain, doubtful, qarz)
            }.collectLatest { (certain, doubtful, qarz) ->

                // A negative "receivable" means I actually owe that party.
                // Those flip over into payables and are deducted, not added.
                val certainPos = certain.coerceAtLeast(0.0)
                val doubtfulPos = doubtful.coerceAtLeast(0.0)
                val qarzPos = qarz.coerceAtLeast(0.0)

                val payables = listOf(certain, doubtful, qarz)
                    .filter { it < 0 }
                    .sumOf { -it }

                inputs = ZakatInputs(
                    certainReceivables = certainPos,
                    doubtfulReceivables = doubtfulPos,
                    qarzeHasnaGiven = qarzPos,
                    payables = payables
                )

                tvCertain.text = getString(R.string.certain_receivables, Format.money(certainPos))
                tvQarzeHasna.text = getString(R.string.qarze_hasna_given, Format.money(qarzPos))
                tvDoubtful.text = getString(R.string.doubtful_receivables, Format.money(doubtfulPos))
                tvPayables.text = getString(R.string.you_owe_others, Format.money(payables))

                recalculate()
            }
        }
    }

    private fun recalculate() {
        // evalPad, not toDoubleOrNull. Every other amount field in the app
        // totals a sum typed into it — "2500+1200" or "300*40" — and this one
        // silently read the whole thing as zero, so a shopkeeper adding up
        // their stock in the box got no answer and no reason why.
        val pricePerGram = Calc.evalPad(etSilverPrice.text.toString()) ?: 0.0
        val cashStock = Calc.evalPad(etCashStock.text.toString()) ?: 0.0

        val nisab = if (useGold) {
            Zakat.nisabFromGoldPrice(pricePerGram)
        } else {
            Zakat.nisabFromSilverPrice(pricePerGram)
        }

        tvNisabValue.text = if (nisab > 0) {
            getString(R.string.nisab_value, Format.money(nisab))
        } else {
            getString(R.string.nisab_enter_price)
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
