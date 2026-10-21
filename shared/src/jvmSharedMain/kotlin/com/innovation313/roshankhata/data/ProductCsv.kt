package com.innovation313.roshankhata.data

/**
 * The products list as a spreadsheet (POS Phase 2, 21 Oct 2026): out to
 * Excel to fix five hundred prices at once, and back in. The columns are
 * fixed and named in English on both legs, so a file written on an Urdu
 * phone opens on an English laptop and comes back the same.
 *
 * Importing never deletes: a row whose name matches a product (by
 * [ProductName.key]) updates the fields the row fills in and leaves blank
 * cells alone; a new name becomes a new product. The owner sees the counts
 * before anything is written ([Plan]).
 */
object ProductCsv {

    val HEADERS = listOf("name", "category", "company", "unit", "sale price", "credit price", "cost price", "barcode", "low stock", "type")

    fun text(products: List<Product>): String {
        val sb = StringBuilder()
        sb.append('﻿')
        row(sb, HEADERS)
        products.sortedBy { it.name.lowercase() }.forEach { p ->
            row(
                sb,
                listOf(
                    p.name, p.category.orEmpty(), p.company.orEmpty(), p.defaultUnit.orEmpty(),
                    p.salePrice?.let(LedgerCsv::plain).orEmpty(), p.creditPrice?.let(LedgerCsv::plain).orEmpty(),
                    p.costPrice?.let(LedgerCsv::plain).orEmpty(), p.barcode?.let(::asText).orEmpty(),
                    p.lowStockLevel?.let(LedgerCsv::plain).orEmpty(), p.productType.orEmpty()
                )
            )
        }
        return sb.toString()
    }

    private fun row(sb: StringBuilder, cells: List<String>) {
        cells.joinTo(sb, ",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        sb.append("\r\n")
    }

    /** One row as read: every field optional but the name. */
    data class Row(
        val name: String,
        val category: String? = null,
        val company: String? = null,
        val unit: String? = null,
        val salePrice: Double? = null,
        val creditPrice: Double? = null,
        val costPrice: Double? = null,
        val barcode: String? = null,
        val lowStock: Double? = null,
        val type: String? = null
    )

    /**
     * Reads a file this wrote, or any sheet whose header names its columns in
     * English (name / product / item, price / rate, barcode / code, cost,
     * unit, company / brand, category). A file with no header is read as
     * name, price, barcode. Rows without a name are dropped.
     */
    fun parse(text: String): List<Row> {
        val table = CsvImport.cells(text.removePrefix("﻿")).filter { r -> r.any { it.isNotBlank() } }
        if (table.isEmpty()) return emptyList()
        val first = table.first().map { it.trim().lowercase() }
        val hasHeader = first.any { h -> h.isNotEmpty() && HEADER_WORDS.values.any { ws -> ws.any { h.contains(it) } } } && first.none { CsvImport.amount(it) != null }
        val body = if (hasHeader) table.drop(1) else table
        fun col(key: String): Int? = if (!hasHeader) DEFAULT_COLS[key] else first.indices.firstOrNull { i -> HEADER_WORDS.getValue(key).any { first[i].contains(it) } && EXCLUDE[key]?.none { first[i].contains(it) } ?: true }
        val cols = HEADER_WORDS.keys.associateWith { col(it) }
        fun cell(r: List<String>, key: String): String? = cols[key]?.let { r.getOrNull(it)?.trim()?.ifEmpty { null } }
        return body.mapNotNull { r ->
            val name = cell(r, "name") ?: return@mapNotNull null
            Row(
                name = name, category = cell(r, "category"), company = cell(r, "company"), unit = cell(r, "unit"),
                salePrice = cell(r, "sale")?.let(CsvImport::amount), creditPrice = cell(r, "credit")?.let(CsvImport::amount),
                costPrice = cell(r, "cost")?.let(CsvImport::amount), barcode = cell(r, "barcode")?.let(::barcodeOf),
                lowStock = cell(r, "low")?.let(CsvImport::amount), type = cell(r, "type")
            )
        }
    }

    private val HEADER_WORDS = linkedMapOf(
        "name" to listOf("name", "product", "item"),
        "category" to listOf("category"),
        "company" to listOf("company", "brand", "manufacturer"),
        "unit" to listOf("unit"),
        "sale" to listOf("sale price", "price", "rate", "mrp"),
        "credit" to listOf("credit price", "udhar", "credit"),
        "cost" to listOf("cost", "purchase"),
        "barcode" to listOf("barcode", "code", "ean", "sku"),
        "low" to listOf("low stock", "min", "reorder"),
        "type" to listOf("type")
    )
    private val EXCLUDE = mapOf("sale" to listOf("cost", "credit", "purchase"), "name" to listOf("company", "brand"), "unit" to listOf("price"))
    /**
     * A barcode written so Excel keeps it as text (21 Oct 2026, the critical
     * review): a bare 13-digit code opened in Excel became 8.96E+12, and saved
     * back it WAS 8.96E+12. ="8964000000123" shows as the code and stays one.
     */
    fun asText(code: String): String = if (code.all(Char::isDigit) && code.length > 11) "=\"$code\"" else code

    /** A barcode read back: Excel's ="..." undone; a code Excel already turned into 8.96E+12 is lost, so nothing. */
    fun barcodeOf(cell: String): String? {
        val t = cell.trim().removePrefix("=").trim('"').trim()
        if (t.isEmpty()) return null
        if (Regex("^\\d+(\\.\\d+)?[eE][+-]?\\d+$").matches(t)) return null
        return t
    }

    private val DEFAULT_COLS = mapOf("name" to 0, "sale" to 1, "barcode" to 2)

    /** What an import would do, for the owner to confirm. */
    data class Plan(val toAdd: List<Row>, val toUpdate: List<Pair<Product, Row>>, val barcodeClashes: Int)

    fun plan(rows: List<Row>, existing: List<Product>): Plan {
        val byKey = existing.filter { !it.isDeleted }.associateBy { it.nameKey }
        val codes = existing.filter { !it.isDeleted && it.barcode != null }.associateBy { it.barcode!! }
        val add = ArrayList<Row>(); val update = ArrayList<Pair<Product, Row>>(); var clashes = 0
        val seen = HashSet<String>()
        for (r in rows) {
            val key = ProductName.key(r.name)
            if (!seen.add(key)) continue
            val have = byKey[key]
            r.barcode?.let { b -> codes[b]?.let { owner -> if (owner.nameKey != key) clashes++ } }
            if (have == null) add += r else update += have to r
        }
        return Plan(add, update, clashes)
    }

    /** The product a row updates: filled cells replace, blank cells leave the old value. */
    fun apply(p: Product, r: Row): Product = p.copy(
        category = r.category ?: p.category, company = r.company ?: p.company,
        defaultUnit = r.unit?.let { UnitWords.canonical(it) } ?: p.defaultUnit,
        salePrice = r.salePrice ?: p.salePrice, creditPrice = r.creditPrice ?: p.creditPrice,
        costPrice = r.costPrice ?: p.costPrice, barcode = r.barcode ?: p.barcode,
        lowStockLevel = r.lowStock ?: p.lowStockLevel, productType = r.type ?: p.productType
    )

    fun newProduct(r: Row): Product = Product(
        name = r.name.trim(), nameKey = ProductName.key(r.name), normalisedName = ProductName.normalised(r.name),
        category = r.category, company = r.company, defaultUnit = r.unit?.let { UnitWords.canonical(it) },
        salePrice = r.salePrice, creditPrice = r.creditPrice, costPrice = r.costPrice, barcode = r.barcode,
        lowStockLevel = r.lowStock, productType = r.type
    )
}

/**
 * Barcodes the shop prints itself (POS Phase 2): an EAN-13 in the
 * store-internal range (first digit 2, as supermarkets label their own
 * goods), built from the product's id so it never repeats, with the
 * standard check digit so any scanner accepts it.
 */
object Barcodes {
    fun internalEan13(productId: Long): String {
        val body = "2" + productId.toString().padStart(11, '0').takeLast(11)
        return body + checkDigit(body)
    }

    /** The EAN/UPC check digit of a 12-digit (or 7-digit) body. */
    fun checkDigit(body: String): Int {
        var sum = 0
        body.reversed().forEachIndexed { i, ch -> sum += (ch - '0') * if (i % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10
    }

    /** True for a 13- or 8-digit code whose check digit holds. */
    fun isValidEan(code: String): Boolean =
        (code.length == 13 || code.length == 8) && code.all { it.isDigit() } &&
            checkDigit(code.dropLast(1)) == code.last() - '0'
}
