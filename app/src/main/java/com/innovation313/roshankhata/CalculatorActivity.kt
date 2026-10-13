package com.innovation313.roshankhata

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format

/**
 * A four-function calculator with a percent key, for the sums a shopkeeper
 * does beside the ledger rather than inside it.
 *
 * The arithmetic is [Calc.eval] — the same evaluator the amount fields use, so
 * a sum worked out here and a sum typed into an entry can never disagree.
 * Percent is resolved here instead, by rewriting the expression before it is
 * handed over: the evaluator is load-bearing for five screens where money is
 * entered, and a calculator key is not worth the risk of changing it.
 */
class CalculatorActivity : BaseActivity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvHistory: TextView

    /** Sums settled with "=" on this visit, oldest first; three are shown. */
    private val history = ArrayList<String>()

    /** What the owner has typed, in evaluator syntax (* and / rather than × and ÷). */
    private var expression = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)
        tvHistory = findViewById(R.id.tvHistory)
        savedInstanceState?.let {
            expression = it.getString(STATE_EXPRESSION).orEmpty()
            it.getStringArrayList(STATE_HISTORY)?.let { h -> history.addAll(h) }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCalcToKhata)
            .setOnClickListener { answer()?.let { toKhata(it) } }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCalcCopy)
            .setOnClickListener { answer()?.let { copy(it) } }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCalcShare)
            .setOnClickListener { answer()?.let { share(it) } }

        digit(R.id.btn0, "0"); digit(R.id.btn1, "1"); digit(R.id.btn2, "2")
        digit(R.id.btn3, "3"); digit(R.id.btn4, "4"); digit(R.id.btn5, "5")
        digit(R.id.btn6, "6"); digit(R.id.btn7, "7"); digit(R.id.btn8, "8")
        digit(R.id.btn9, "9"); digit(R.id.btnDot, ".")
        digit(R.id.btn00, "00")

        operator(R.id.btnPlus, "+")
        operator(R.id.btnMinus, "-")
        operator(R.id.btnMultiply, "*")
        operator(R.id.btnDivide, "/")

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            expression = ""
            render()
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            if (expression.isNotEmpty()) expression = expression.dropLast(1)
            render()
        }
        findViewById<Button>(R.id.btnPercent).setOnClickListener { appendPercent() }
        findViewById<Button>(R.id.btnEquals).setOnClickListener { settle() }

        render()
    }

    private fun digit(id: Int, text: String) {
        findViewById<Button>(id).setOnClickListener {
            // One decimal point per number, or the evaluator rejects the lot.
            if (text == "." && currentNumber().contains(".")) return@setOnClickListener
            // A digit after "32%" would read as part of that number when the
            // percentage is resolved; the % has to be closed off first.
            if (expression.endsWith("%")) return@setOnClickListener
            expression += text
            render()
        }
    }

    private fun operator(id: Int, symbol: String) {
        findViewById<Button>(id).setOnClickListener {
            if (expression.isEmpty()) {
                // A leading minus is a negative number; the others have
                // nothing to act on.
                if (symbol == "-") {
                    expression = symbol
                    render()
                }
                return@setOnClickListener
            }
            // Replace a trailing operator rather than stacking a second one.
            expression = if (expression.last() in "+-*/") {
                expression.dropLast(1) + symbol
            } else {
                expression + symbol
            }
            render()
        }
    }

    /**
     * Mark the number being typed as a percentage. The % stays in the
     * expression and on the display — replacing it with a decimal the moment
     * it was pressed turned "32%" into "0.32" on screen, which is not what
     * anybody typed and not what they meant.
     *
     * What it resolves to depends on the operator in front of it, the way a
     * shopkeeper reads it aloud:
     *
     *   500 × 32%   the 32% of 500                    160
     *   3500 − 32%  3500 less 32% of itself          2380
     *   3500 + 32%  3500 plus 32% of itself          4620
     *
     * A bare "32%" with nothing before it is 0.32.
     */
    private fun appendPercent() {
        if (currentNumber().isEmpty()) return
        if (expression.endsWith("%")) return
        expression += "%"
        render()
    }

    /** Replace the expression with its result, so the next sum can build on it. */
    private fun settle() {
        val value = Calc.evalPad(expression) ?: return
        val typed = display(expression)
        val answer = Calc.trim(value)
        // Only a real sum goes in the history, not "3400 = 3400".
        if (typed.trim() != answer) {
            history += "$typed = ${Format.signedIfNegative(value)}"
            while (history.size > 3) history.removeAt(0)
        }
        expression = answer
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_EXPRESSION, expression)
        outState.putStringArrayList(STATE_HISTORY, history)
    }

    /** The answer on screen, or null (with nothing done) when there is none to use. */
    private fun answer(): Double? = Calc.evalPad(expression)?.takeIf { !it.isNaN() && !it.isInfinite() }

    /**
     * The answer onto a customer's khata (13 Oct): the ledger opens to pick
     * who, and that customer's entry form opens filled in with it. Nothing is
     * saved until the owner checks it there and taps Save, as with a spoken
     * entry. A khata amount is never negative or zero, so those do nothing.
     */
    private fun toKhata(value: Double) {
        if (value <= 0.0) {
            android.widget.Toast.makeText(this, R.string.calc_khata_positive, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            android.content.Intent(this, KhataActivity::class.java)
                .putExtra(KhataActivity.EXTRA_CARRY_AMOUNT, value)
        )
    }

    private fun copy(value: Double) {
        val clip = getSystemService(android.content.ClipboardManager::class.java) ?: return
        clip.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.calculator), Calc.trim(value)))
        // Android 13 and later show their own "copied" confirmation.
        if (android.os.Build.VERSION.SDK_INT < 33) {
            android.widget.Toast.makeText(this, R.string.copied, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(value: Double) {
        // A sum still on screen is sent whole; a settled answer goes with
        // the sum that made it, when that sum is the last one in the history.
        val shown = Format.signedIfNegative(value)
        val pending = expression.drop(1).any { it in "+-*/%" }
        val line = when {
            pending -> "${display(expression)} = $shown"
            history.lastOrNull()?.endsWith("= $shown") == true -> history.last()
            else -> shown
        }
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, line)
        startActivity(android.content.Intent.createChooser(send, getString(R.string.share)))
    }

    /** The digits typed since the last operator — what a percent key acts on. */
    private fun currentNumber(): String =
        expression.takeLastWhile { it.isDigit() || it == '.' }

    private fun render() {
        tvExpression.text = display(expression)
        val value = Calc.evalPad(expression)
        // signedIfNegative, not money: money() strips the sign for ledger
        // rows that state their direction in words, and this screen states
        // nothing. 75 − 80 read back as "Rs 5" — the right digits with the
        // meaning reversed, which on a shop counter is worse than no answer.
        tvResult.text = if (value == null) "" else Format.signedIfNegative(value)
        tvHistory.text = history.joinToString("\n")
        tvHistory.visibility = if (history.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        val usable = value != null && !value.isNaN() && !value.isInfinite()
        listOf(R.id.btnCalcToKhata, R.id.btnCalcCopy, R.id.btnCalcShare).forEach {
            findViewById<android.view.View>(it).isEnabled = usable
        }
    }

    /** Evaluator syntax in, keypad symbols out — nobody types an asterisk. */
    private fun display(text: String): String =
        text.replace("*", " × ").replace("/", " ÷ ")
            .replace("+", " + ").replace("-", " − ")

    private companion object {
        const val STATE_EXPRESSION = "calc_expression"
        const val STATE_HISTORY = "calc_history"
    }
}
