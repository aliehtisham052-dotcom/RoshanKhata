package com.innovation313.roshankhata.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A weighing scale's label (21 Oct 2026, the critical review): the scale
 * prints an EAN-13 of its own — a two-digit prefix ("21" unless the book says
 * otherwise), the item's five-digit scale code, five digits of weight in
 * grams (or of price, in paisa/cents, on a scale set to print prices), and
 * the check digit. The product it names is the one whose barcode is that
 * scale code. A code that is some product's own barcode is that product
 * first; only an unknown code is read as a scale label.
 */
object ScaleCode {

    /** How this shop's scale prints: its prefix, and whether the five value digits are a price instead of grams. */
    data class Rule(val prefix: String = DEFAULT_PREFIX, val price: Boolean = false)

    /** What a label says: the item's scale code, and its weight in kilograms or its price. */
    data class Label(val code: String, val kilograms: Double?, val price: Double?)

    /** One weighed line: how much, at what rate (the shelf's, or the label's price spread over the weight). */
    data class Line(val product: Product, val quantity: Double, val rate: Double)

    const val DEFAULT_PREFIX = "21"

    fun rule(prefix: String?, price: String?): Rule =
        Rule(prefix?.trim()?.takeIf { it.length == 2 && it.all(Char::isDigit) && it.startsWith("2") } ?: DEFAULT_PREFIX, BookSettings.isOn(price))

    suspend fun rule(dao: KhataDao): Rule = rule(dao.settingValue(BookSettings.SCALE_PREFIX), dao.settingValue(BookSettings.SCALE_PRICE))

    /** The label, when [code] is one: 13 digits, this scale's prefix, its check digit right. */
    fun read(code: String, rule: Rule): Label? {
        val c = code.trim()
        if (c.length != 13 || !c.all(Char::isDigit) || !c.startsWith(rule.prefix) || !Barcodes.isValidEan(c)) return null
        val item = c.substring(2, 7)
        val value = c.substring(7, 12).toInt()
        return if (rule.price) Label(item, null, value / 100.0) else Label(item, value / 1000.0, null)
    }

    /** The product whose barcode is the label's scale code, with or without its leading zeros. */
    fun productOf(products: List<Product>, item: String): Product? {
        val bare = item.trimStart('0')
        return products.firstOrNull { p -> !p.isDeleted && p.barcode != null && (p.barcode == item || p.barcode.trimStart('0') == bare) }
    }

    /**
     * The whole reading: the product and how much of it. A weight label is
     * that many kilograms at the shelf's [rateOf]; a price label is the
     * weight that price buys (to the gram), at the rate that makes the line
     * come to the label's price exactly. Null when the code is not a label
     * of a product this book has.
     */
    fun line(code: String, rule: Rule, products: List<Product>, rateOf: (Product) -> Double): Line? {
        val label = read(code, rule) ?: return null
        val product = productOf(products, label.code) ?: return null
        val shelf = rateOf(product)
        label.kilograms?.let { kg -> return if (kg > 0.0) Line(product, kg, shelf) else null }
        val price = label.price ?: return null
        if (price <= 0.0) return null
        if (shelf <= 0.0) return Line(product, 1.0, price)
        val kg = BigDecimal.valueOf(price / shelf).setScale(3, RoundingMode.HALF_UP).toDouble().coerceAtLeast(0.001)
        return Line(product, kg, BigDecimal.valueOf(price / kg).setScale(2, RoundingMode.HALF_UP).toDouble())
    }
}
