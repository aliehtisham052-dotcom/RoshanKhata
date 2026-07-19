package com.innovation313.roshankhata

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
class CalculatorActivity : AppCompatActivity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView

    /** What the owner has typed, in evaluator syntax (* and / rather than × and ÷). */
    private var expression = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)

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
        expression = Calc.trim(value)
        render()
    }

    /** The digits typed since the last operator — what a percent key acts on. */
    private fun currentNumber(): String =
        expression.takeLastWhile { it.isDigit() || it == '.' }

    private fun render() {
        tvExpression.text = display(expression)
        val value = Calc.evalPad(expression)
        tvResult.text = if (value == null) "" else Format.money(value)
    }

    /** Evaluator syntax in, keypad symbols out — nobody types an asterisk. */
    private fun display(text: String): String =
        text.replace("*", " × ").replace("/", " ÷ ")
            .replace("+", " + ").replace("-", " − ")

}
