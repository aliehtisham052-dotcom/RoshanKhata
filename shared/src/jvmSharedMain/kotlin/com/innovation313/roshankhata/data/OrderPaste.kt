package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch

/**
 * A customer's order message, pasted from WhatsApp, read into a sales order's lines (3 Nov 2026, the competitor
 * study: orders arrive on WhatsApp, and every line was typed again by hand). "2 bori DAP, 1 Confidor" becomes two
 * lines: the shop's own product where the words name one, the quantity, the unit, and the product's price. Nothing
 * is posted: the lines open in the document form, where the owner reads each one, corrects it, picks the customer
 * and saves. A line whose product is not found keeps the customer's words as its name, so nothing he asked for
 * is lost from the order.
 *
 * Read on the phone, from the shop's own products only; nothing is sent anywhere.
 */
object OrderPaste {

    data class Line(
        /** The words the line was read from. */
        val raw: String,
        val product: Product?,
        val name: String,
        val quantity: Double,
        val unit: String?,
        /** The product's cash price, else its credit price, else nothing (0, for the owner to type). */
        val rate: Double
    )

    /** The longest message read: an order, not a chat history. */
    const val MAX_CHARS = 4_000

    /** A line per line of the message, and per comma, semicolon, plus or "and" within one. */
    fun segments(text: String): List<String> =
        text.take(MAX_CHARS)
            .split(Regex("[\\n\\r;؛+]|[,،](?!\\d)|\\s(?:and|aur|or|اور)\\s", RegexOption.IGNORE_CASE))
            .map { it.trim().trim('-', '•', '*', '.', '·', ':', ')', '(').trim() }
            .filter { it.isNotBlank() }

    fun parse(text: String, products: List<Product>): List<Line> {
        val live = products.filter { !it.isDeleted }
        return segments(text).mapNotNull { line(it, live) }
    }

    /** A number in any script ("2", "2.5", "۲"), never a word. */
    private fun digits(token: String): Double? = Digits.toLatin(token).replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it < 1_000_000 }

    private fun unitKey(token: String): String? = UnitWords.canonical(token)?.takeIf { it in UnitWords.KEYS }

    private fun tokens(text: String): List<String> =
        Regex("\\p{Nd}+(?:[.,]\\p{Nd}+)?|[\\p{L}\\p{M}\\p{Nd}]+").findAll(text).map { it.value }.toList()

    private fun same(a: String, b: String): Boolean {
        val x = NameSearch.fold(a)
        val y = NameSearch.fold(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        // "confidor" for "Confidor 250", "dap" for "D.A.P": a word typed short or long, from four letters.
        return minOf(x.length, y.length) >= 4 && (x.startsWith(y) || y.startsWith(x))
    }

    /**
     * The product the words name: the one whose name words (not its figures or units) are most found among
     * [words], at least half of them; between equals, the one more of whose figures are there too ("DAP 25 kg"
     * over "DAP 50 kg" when 25 was written), then the shorter name.
     */
    fun bestProduct(words: List<String>, products: List<Product>): Pair<Product, Set<Int>>? {
        var best: Pair<Product, Set<Int>>? = null
        var bestScore = 0.0
        var bestFigures = -1
        for (p in products) {
            val name = tokens(p.name)
            val nameWords = name.filter { digits(it) == null && unitKey(it) == null }
            if (nameWords.isEmpty()) continue
            val used = HashSet<Int>()
            var found = 0
            for (w in nameWords) {
                val at = words.indices.firstOrNull { it !in used && same(words[it], w) } ?: continue
                used += at
                found++
            }
            if (found == 0) continue
            val score = found.toDouble() / nameWords.size
            if (score < 0.5) continue
            var figures = 0
            for (f in name.filter { digits(it) != null || unitKey(it) != null }) {
                val at = words.indices.firstOrNull { it !in used && words[it].equals(f, ignoreCase = true) } ?: continue
                used += at
                figures++
            }
            val better = score > bestScore || (score == bestScore && (figures > bestFigures ||
                (figures == bestFigures && best != null && p.name.length < best.first.name.length)))
            if (best == null || better) {
                best = p to used
                bestScore = score
                bestFigures = figures
            }
        }
        return best
    }

    private fun line(segment: String, products: List<Product>): Line? {
        val words = tokens(segment)
        if (words.isEmpty()) return null
        val match = bestProduct(words, products)
        val rest = words.filterIndexed { i, _ -> match == null || i !in match.second }
        // A written figure first; a count in words ("do bori") only where there is none.
        val qtyAt = rest.indexOfFirst { digits(it) != null }.takeIf { it >= 0 }
            ?: rest.indexOfFirst { VoiceEntry.countWord(it) != null }.takeIf { it >= 0 }
        // Words that name no product and carry no count are the message's greeting or chat, not an order line.
        if (match == null && qtyAt == null) return null
        val quantity = qtyAt?.let { digits(rest[it]) ?: VoiceEntry.countWord(rest[it]) } ?: 1.0
        val unitAt = rest.indices.firstOrNull { it != qtyAt && unitKey(rest[it]) != null }
        val unit = unitAt?.let { unitKey(rest[it]) }
        val product = match?.first
        val name = product?.name ?: rest.filterIndexed { i, _ -> i != qtyAt && i != unitAt }.joinToString(" ").trim()
        if (name.isEmpty()) return null
        return Line(segment, product, name, quantity, unit ?: product?.defaultUnit, product?.salePrice ?: product?.creditPrice ?: 0.0)
    }
}
