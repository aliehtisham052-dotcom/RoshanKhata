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
