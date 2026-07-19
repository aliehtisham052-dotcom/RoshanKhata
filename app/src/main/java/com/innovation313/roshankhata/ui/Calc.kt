package com.innovation313.roshankhata.ui

/**
 * A tiny, safe arithmetic evaluator for amount fields, so the owner can type
 * "500+300" or "1200-150" or "12*40" straight into the box and have it total
 * itself — the sum they'd otherwise do on a separate calculator first.
 *
 * It handles + - * / with correct precedence and decimals, and nothing else:
 * no variables, no functions, no code. Anything it can't parse returns null,
 * and the caller falls back to a plain number. This is deliberately not a
 * general expression engine — a ledger only ever needs four operators.
 */
object Calc {

    /**
     * Evaluate an expression that may contain percentages and the pad's own
     * symbols, and is otherwise [eval]'s business.
     *
     * This is what every amount field and the calculator screen should call.
     * [eval] itself is left alone: it is the same four-operator evaluator it
     * always was, and percentages are rewritten into plain arithmetic before
     * it ever sees them.
     */
    fun evalPad(input: String): Double? = eval(resolvePercent(normalize(input)))

    /** The pad prints × ÷ − for looks; the evaluator wants * / -. */
    fun normalize(text: String): String =
        text.replace('\u00d7', '*').replace('\u00f7', '/').replace('\u2212', '-')

    /**
     * Rewrite percentages into plain arithmetic.
     *
     * What a percentage resolves to depends on the operator in front of it,
     * the way it is read aloud behind a counter:
     *
     *   500*32%   the 32% of 500                 160
     *   3500-32%  3500 less 32% of itself       2380
     *   3500+32%  3500 plus 32% of itself       4620
     *
     * After * and / it is simply a hundredth. After + and - it is taken of the
     * running total to its left, which is what makes a discount come out the
     * way it does on paper.
     */
    fun resolvePercent(input: String): String {
        var out = input
        while (true) {
            val at = out.indexOf('%')
            if (at < 0) return out

            val numStart = out.lastIndexOfAny(charArrayOf('+', '-', '*', '/'), at - 1) + 1
            val number = out.substring(numStart, at).toDoubleOrNull() ?: return out

            val opIndex = numStart - 1
            val op = if (opIndex >= 0) out[opIndex] else ' '
            val base = if (opIndex > 0) eval(out.substring(0, opIndex)) else null

            val replacement = when {
                (op == '+' || op == '-') && base != null -> trim(base * number / 100.0)
                else -> trim(number / 100.0)
            }
            out = out.substring(0, numStart) + replacement + out.substring(at + 1)
        }
    }

    /** Drop a trailing .0 so a whole number reads as one. */
    fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else value.toString()

    /**
     * Evaluate a plain-number or arithmetic string. "500" -> 500.0,
     * "500+300" -> 800.0, "1200-150" -> 1050.0. Returns null if the text isn't
     * a clean number-or-expression, so callers can keep their existing
     * "invalid amount" handling.
     */
    fun eval(input: String): Double? {
        val text = input.trim().replace(" ", "")
        if (text.isEmpty()) return null

        // Fast path: a plain number (possibly decimal). No math to do.
        text.toDoubleOrNull()?.let { return it }

        // Only digits, decimal points and the four operators are allowed.
        if (!text.all { it.isDigit() || it == '.' || it in "+-*/" }) return null

        return try {
            Parser(text).parse().takeIf { it.isFinite() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A minimal recursive-descent parser: expr = term (('+'|'-') term)*,
     * term = factor (('*'|'/') factor)*, factor = number | '(' expr ')'.
     * Parentheses aren't offered in the UI but cost nothing to support.
     */
    private class Parser(val s: String) {
        var pos = 0

        fun parse(): Double {
            val v = expr()
            if (pos != s.length) throw IllegalArgumentException("trailing input")
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = factor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val r = factor()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun factor(): Double {
            if (pos < s.length && s[pos] == '(') {
                pos++ // '('
                val v = expr()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("missing )")
                pos++ // ')'
                return v
            }
            // Allow a leading minus for negative numbers.
            val start = pos
            if (pos < s.length && s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            val token = s.substring(start, pos)
            return token.toDoubleOrNull() ?: throw IllegalArgumentException("bad number '$token'")
        }
    }
}
