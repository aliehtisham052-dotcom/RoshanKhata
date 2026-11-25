package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.XmlWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The logic that differs by language and trade, walked over the pairwise covering array (plan B4, 24 Nov 2026).
 *
 * `docs/covering-array.tsv` (written by `scripts/gen_covering_array.py`) holds a few hundred looks in which every pair of
 * language, trade, platform, text size and theme meets at least once, and the money trades' triples too. A device cannot
 * draw them all; this walks each row on the parts that need no device:
 *
 *  - the row's trade has its words in the row's language, read from that language's OWN strings.xml (no English fallback:
 *    a missing array in Sindhi shows as English on a phone and passes a Robolectric test, so the files are read with the
 *    fallback taken away): items, types, starters, units, non-blank, in the language's own script, a hint that is one line;
 *  - the trade's units are real keys and have a label in the language;
 *  - the register style and the trade switches that the shared module owns hold for the trade (the phone's TradeFeatures
 *    are held in the app's CoveringArrayTradeTest, the same rows);
 *  - the row's country has its currency, the currency its decimals, and a figure printed in it carries 0-9 only, the
 *    language's marks and the currency's decimals, and a figure typed in the language's own digits is read back.
 *
 * Which looks the CI proofs DRAW is not tested here: the generator lists them (`--report`), see docs/covering-array.tsv.
 */
class CoveringArrayLogicTest {

    private data class Row(
        val id: String, val language: String, val trade: String, val platform: String, val size: Int, val theme: String,
        val country: String, val currency: String, val decimals: Int, val money: Boolean
    )

    private val languages = listOf("en", "ur", "ur-Latn", "sd", "fa", "ar", "hi", "bn", "in")
    private val platforms = listOf("phone", "windows")
    private val sizes = listOf(85, 100, 150)
    private val themes = listOf("day", "night")
    private val moneyTrades = listOf("AGRI", "MEDICAL", "KIRYANA", "WHOLESALE")

    private val rows: List<Row> by lazy {
        val file = listOf(File("../docs/covering-array.tsv"), File("docs/covering-array.tsv")).firstOrNull { it.isFile }
            ?: throw AssertionError("docs/covering-array.tsv not found from ${File(".").absoluteFile}")
        val lines = file.readLines().filter { it.isNotBlank() }
        val head = lines.first().split('\t')
        for (col in listOf("row", "language", "trade", "platform", "text_size", "theme", "country", "currency", "decimals", "money")) {
            assertTrue("column $col is in the header", col in head)
        }
        lines.drop(1).map { line ->
            val cells = line.split('\t')
            assertEquals("cells of $line", head.size, cells.size)
            val m = head.zip(cells).toMap()
            Row(
                m.getValue("row"), m.getValue("language"), m.getValue("trade"), m.getValue("platform"),
                m.getValue("text_size").toInt(), m.getValue("theme"), m.getValue("country"), m.getValue("currency"),
                m.getValue("decimals").toInt(), m.getValue("money") == "Y"
            )
        }
    }

    // ---------- the array itself, checked in Kotlin as the script checks it in Python ----------

    /** A language's file ALONE: the folder of its own, never the default one it falls back to. */
    private fun own(tag: String): XmlWords {
        val locale = Locale.forLanguageTag(tag)
        val folder = if (tag == "en") "values" else XmlWords.folders(locale).first()
        return XmlWords(locale) { f, file -> if (f == folder) XmlWords::class.java.getResourceAsStream("/words/$f/$file") else null }
    }

    private val ownCache = HashMap<String, XmlWords>()
    private fun ownOf(tag: String) = ownCache.getOrPut(tag) { own(tag) }

    /** The 25 trades, as the English file names them: one vocab_<trade>_items array each. */
    private val allTrades: List<String> by lazy {
        // Names come from the array's own list, not from a guess: every trade of the phone's enum has an items array.
        ownOf("en").listNames().filter { it.startsWith("vocab_") && it.endsWith("_items") }
            .map { it.removePrefix("vocab_").removeSuffix("_items").uppercase(Locale.ROOT) }.sorted()
    }

    @Test fun `the array holds 25 trades, nine languages, and every pair of its five factors`() {
        assertEquals("the trades the strings name", 25, allTrades.size)
        assertEquals("trades in the array", allTrades, rows.map { it.trade }.distinct().sorted())
        assertEquals(languages.sorted(), rows.map { it.language }.distinct().sorted())
        assertEquals(platforms.sorted(), rows.map { it.platform }.distinct().sorted())
        assertEquals(sizes, rows.map { it.size }.distinct().sorted())
        assertEquals(themes.sorted(), rows.map { it.theme }.distinct().sorted())
        assertEquals("no look twice", rows.size, rows.map { listOf(it.language, it.trade, it.platform, it.size, it.theme) }.distinct().size)

        fun value(r: Row, f: Int): Any = when (f) { 0 -> r.language; 1 -> r.trade; 2 -> r.platform; 3 -> r.size; else -> r.theme }
        val domains: List<List<Any>> = listOf(languages, allTrades, platforms, sizes, themes)
        val missing = ArrayList<String>()
        for (a in 0 until 5) for (b in a + 1 until 5) for (va in domains[a]) for (vb in domains[b]) {
            if (rows.none { value(it, a) == va && value(it, b) == vb }) missing += "factor$a=$va x factor$b=$vb"
        }
        assertEquals("pairs no row covers: ${missing.take(5)}", 0, missing.size)
    }

    @Test fun `the money trades meet every language with every platform, text size and theme`() {
        val missing = ArrayList<String>()
        for (lang in languages) for (t in moneyTrades) {
            val mine = rows.filter { it.language == lang && it.trade == t }
            for (p in platforms) if (mine.none { it.platform == p }) missing += "$lang/$t/$p"
            for (s in sizes) if (mine.none { it.size == s }) missing += "$lang/$t/$s"
            for (th in themes) if (mine.none { it.theme == th }) missing += "$lang/$t/$th"
        }
        assertEquals("money triples no row covers: ${missing.take(5)}", 0, missing.size)
        assertTrue("only the four money trades are marked money", rows.all { it.money == (it.trade in moneyTrades) })
    }

    @Test fun `every country and each of the 0, 2 and 3 decimal currencies is met`() {
        assertEquals("countries", ShopCountry.entries.map { it.name }.sorted(), rows.map { it.country }.distinct().sorted())
        for (d in listOf(0, 2, 3)) assertTrue("a $d-decimal currency", rows.any { it.decimals == d })
        // Per language too: each language meets at least one two-decimal currency and, where it can, the others it lists.
        for (lang in languages) assertTrue("$lang has a country", rows.any { it.language == lang })
    }

    // ---------- the trade's words, in the row's language, from the language's own file ----------

    private fun scriptOk(lang: String, raw: String): Boolean {
        // Acronyms a language writes in Latin letters inside its own words (LED, PVC, DC, LPG) are allowed.
        val word = raw.replace(Regex("\\b[A-Z]{2,5}\\b"), " ").trim()
        if (word.isEmpty()) return true
        val wanted = when (lang) {
            "ur", "sd", "fa", "ar" -> Character.UnicodeScript.ARABIC
            "hi" -> Character.UnicodeScript.DEVANAGARI
            "bn" -> Character.UnicodeScript.BENGALI
            else -> Character.UnicodeScript.LATIN
        }
        val letters = word.codePoints().filter { Character.isLetter(it) }.toArray()
        if (letters.isEmpty()) return true
        val scripts = letters.map { Character.UnicodeScript.of(it) }.toSet()
        if (wanted == Character.UnicodeScript.LATIN) return scripts == setOf(Character.UnicodeScript.LATIN)
        // An acronym a language writes in Latin letters (LPG, LED) is allowed whole; mixed words are not.
        return wanted in scripts && scripts.all { it == wanted } || Regex("[A-Z0-9]{2,5}").matches(word)
    }

    private val seen = HashSet<String>()

    @Test fun `every row's trade has its items, types and starters in the row's own language file`() {
        val faults = ArrayList<String>()
        for (r in rows) {
            if (!seen.add("${r.language}/${r.trade}")) continue
            val t = r.trade.lowercase(Locale.ROOT)
            val words = ownOf(r.language)
            fun list(part: String): List<String> = try { words.list("vocab_${t}_$part") } catch (e: NoSuchElementException) {
                faults += "${r.language}/${r.trade}: no $part in the language's own file (it would read English)"; emptyList()
            }
            val items = list("items"); val types = list("types"); val starters = list("starters")
            if (items.isNotEmpty() && items.size != 3) faults += "${r.language}/${r.trade}: ${items.size} items, not 3"
            if (types.isNotEmpty() && types.size !in 3..4) faults += "${r.language}/${r.trade}: ${types.size} types, not 3..4"
            if (starters.isNotEmpty() && starters.size < 7) faults += "${r.language}/${r.trade}: ${starters.size} starters, under 7"
            for ((part, l) in listOf("items" to items, "types" to types, "starters" to starters)) {
                if (l.any { it.isBlank() }) faults += "${r.language}/${r.trade}: a blank $part word"
                if (l.map { it.trim().lowercase(Locale.ROOT) }.toSet().size != l.size) faults += "${r.language}/${r.trade}: a repeated $part word"
                for (w in l) if (!scriptOk(r.language, w)) faults += "${r.language}/${r.trade}: $part word \"$w\" is not in ${r.language}'s script"
            }
            // A language that is not English never shows the English list under its own name (Indonesian and the Arabic
            // scripts share no words with it; Roman Urdu keeps loanwords, so it is exempt).
            if (r.language != "en" && r.language != "ur-Latn" && items.isNotEmpty() && items == ownOf("en").list("vocab_${t}_items")) {
                faults += "${r.language}/${r.trade}: items are the English ones"
            }
        }
        assertEquals(faults.joinToString("\n"), 0, faults.size)
    }

    @Test fun `the hints built from a trade's words stay short enough for one line`() {
        // Lesson 59: a hint is one line. HintFit wraps one that overruns, but a hint that grows beyond today's longest
        // (43 characters, Indonesian "Barang (Ayam, Daging kambing, Daging sapi…)") is a hint to look at in a screen proof.
        val limit = 52
        val longest = ArrayList<String>()
        val done = HashSet<String>()
        for (r in rows) {
            if (!done.add("${r.language}/${r.trade}")) continue
            val t = r.trade.lowercase(Locale.ROOT)
            val words = ownOf(r.language)
            val items = words.list("vocab_${t}_items")
            val hint = words.text("vocab_item_hint", items.joinToString(", "))
            if (hint.length > limit) longest += "${r.language}/${r.trade}: ${hint.length} characters: $hint"
            val types = words.list("vocab_${t}_types").take(3)
            val typeHint = words.text("vocab_type_hint", types.joinToString(", "))
            if (typeHint.length > limit) longest += "${r.language}/${r.trade}: type hint ${typeHint.length}: $typeHint"
            assertTrue("${r.language}: the hint names its list", hint.contains(items.first()))
        }
        assertEquals(longest.joinToString("\n"), 0, longest.size)
    }

    @Test fun `every trade's units are unit keys with a label in every language`() {
        val en = ownOf("en")
        val faults = ArrayList<String>()
        val done = HashSet<String>()
        for (r in rows) {
            val t = r.trade.lowercase(Locale.ROOT)
            val keys = try { en.list("vocab_${t}_units") } catch (e: NoSuchElementException) { faults += "${r.trade}: no units array"; continue }
            if (keys.isEmpty()) faults += "${r.trade}: no units"
            if (keys.toSet().size != keys.size) faults += "${r.trade}: a repeated unit"
            if (!done.add("${r.language}/${r.trade}")) continue
            val locale = Locale.forLanguageTag(r.language)
            for (k in keys) {
                if (k !in UnitWords.KEYS) faults += "${r.trade}: unit '$k' is not a UnitWords key"
                val label = UnitWords.label(k, locale)
                if (label.isBlank()) faults += "${r.language}/${r.trade}: unit '$k' has no label"
                else if (!scriptOk(r.language, label)) faults += "${r.language}/${r.trade}: unit '$k' label \"$label\" is not in ${r.language}'s script"
            }
        }
        assertEquals(faults.joinToString("\n"), 0, faults.size)
    }

    @Test fun `a language writes every unit key in its own words, not the English key, except the shared abbreviations`() {
        // kg, ml, ton, litre, plate and similar are the same word in several languages; the rest must differ from the key.
        val shared = setOf("kg", "ml", "ton", "litre", "plate", "cylinder", "packet", "metre", "meter", "liter", "pair", "maund", "seer")
        val faults = ArrayList<String>()
        for (lang in listOf("ur", "sd", "fa", "ar", "hi", "bn")) {
            for (k in UnitWords.KEYS) {
                val label = UnitWords.label(k, Locale.forLanguageTag(lang))
                if (label.lowercase(Locale.ROOT) == k && k !in shared) faults += "$lang/$k is shown as the English key"
            }
        }
        assertEquals(faults.joinToString("\n"), 0, faults.size)
    }

    // ---------- the switches the shared module owns ----------

    @Test fun `the register style follows the trade, agri and medical are inspected and every other shop is plain`() {
        for (r in rows) {
            val want = when (r.trade) { "AGRI" -> RegisterStyle.AGRI; "MEDICAL" -> RegisterStyle.MEDICAL; else -> RegisterStyle.PLAIN }
            assertEquals("${r.id} ${r.trade}", want, RegisterStyle.of(r.trade))
            assertEquals("${r.id} compliance only for the two inspected trades", r.trade == "AGRI" || r.trade == "MEDICAL", RegisterStyle.of(r.trade).compliance)
            // The stored key is read in any letter case and with spaces, as the computer's TradeKit.key and the phone's Trade.name.
            assertEquals(want, RegisterStyle.of(" " + r.trade.lowercase(Locale.ROOT) + " "))
        }
    }

    @Test fun `crop seasons belong to agri shops in the countries that farm by Rabi and Kharif, nowhere else`() {
        // The rule the phone's TradeFeatures.seasons and the computer's TradeKit.seasons both state: trade AGRI and country.seasons.
        for (r in rows) {
            val country = ShopCountry.valueOf(r.country)
            val seasons = r.trade == "AGRI" && country.seasons
            if (r.trade != "AGRI") assertTrue("${r.id}: no seasons for ${r.trade}", !seasons)
        }
        assertEquals(setOf(ShopCountry.PAKISTAN, ShopCountry.INDIA), ShopCountry.entries.filter { it.seasons }.toSet())
    }

    // ---------- the row's country, currency and figures ----------

    @Test fun `every country's currency is one the app offers and its decimals are the currency's own`() {
        val choices = Currency.CHOICES.map { it.first }.toSet()
        val faults = ArrayList<String>()
        for (c in ShopCountry.entries) {
            if (c.currencySign !in choices) faults += "${c.name}: sign ${c.currencySign} is not in Currency.CHOICES"
            if (Currency.decimals(c.currencySign) != c.currencyDecimals) {
                faults += "${c.name}: ShopCountry says ${c.currencyDecimals} decimals, Currency.decimals(${c.currencySign}) says ${Currency.decimals(c.currencySign)}"
            }
        }
        assertEquals(faults.joinToString("\n"), 0, faults.size)
        for (r in rows) {
            val c = ShopCountry.valueOf(r.country)
            assertEquals("${r.id} sign", c.currencySign, r.currency)
            assertEquals("${r.id} decimals", c.currencyDecimals, r.decimals)
        }
    }

    private val figures = listOf(0.0, 5.0, 999.0, 1250.5, 1234567.891, 25000000.0, 0.004)

    private fun <T> inRow(r: Row, body: () -> T): T {
        val before = Locale.getDefault(); val country = ShopCountry.current; val sign = Currency.symbol; val native = Digits.nativeOnPrint
        try {
            Locale.setDefault(Locale.forLanguageTag(r.language))
            ShopCountry.current = ShopCountry.valueOf(r.country)
            Currency.symbol = r.currency
            Digits.nativeOnPrint = false
            return body()
        } finally {
            Locale.setDefault(before); ShopCountry.current = country; Currency.symbol = sign; Digits.nativeOnPrint = native
        }
    }

    @Test fun `a figure printed in the row's currency and language has only 0-9, the language's marks and the currency's decimals`() {
        val faults = ArrayList<String>()
        val done = HashSet<String>()
        for (r in rows) {
            if (!done.add("${r.language}/${r.currency}/${r.country}")) continue
            inRow(r) {
                // Indonesian groups with "." and marks the decimal with ",": the language, or an Indonesian shop in any
                // language, which types its figures so and prints them as typed (BC-275).
                val dot = r.language == "in" || r.country == "INDONESIA"
                val group = if (dot) '.' else ','
                val mark = if (dot) ',' else '.'
                for (v in figures) {
                    val text = Format.money(v, r.currency)
                    if (!text.startsWith(r.currency + " ")) { faults += "${r.id} $v: \"$text\" does not start with the sign"; continue }
                    val figure = text.removePrefix(r.currency + " ")
                    val odd = figure.filter { !(it in '0'..'9' || it == group || it == mark) }
                    if (odd.isNotEmpty()) faults += "${r.id} $v: \"$text\" holds ${odd.map { "U+%04X".format(it.code) }}"
                    if (figure.any { it.isDigit() && it !in '0'..'9' }) faults += "${r.id} $v: \"$text\" has non-0-9 digits"
                    val fraction = if (mark in figure) figure.substringAfter(mark) else ""
                    if (fraction.isNotEmpty() && fraction.length != r.decimals) faults += "${r.id} $v: \"$text\" has ${fraction.length} decimals, the currency has ${r.decimals}"
                    if (r.decimals == 0 && fraction.isNotEmpty()) faults += "${r.id} $v: \"$text\" has a fraction in a no-decimal currency"
                    // Groups of three, from the right, in the whole part.
                    val whole = figure.substringBefore(mark)
                    if (whole.length > 3 && !Regex("\\d{1,3}(\\$group\\d{3})+").matches(whole)) faults += "${r.id} $v: \"$text\" is not grouped by three"
                }
            }
        }
        assertEquals(faults.joinToString("\n"), 0, faults.size)
    }

    @Test fun `a figure typed in the language's own digits is read back as written`() {
        val zero = mapOf("ar" to '٠', "sd" to '٠', "fa" to '۰', "bn" to '০', "hi" to '०')
        val faults = ArrayList<String>()
        val done = HashSet<String>()
        for (r in rows) {
            if (!done.add(r.language + "/" + (r.country == "INDONESIA"))) continue
            inRow(r) {
                val comma = Digits.decimalComma()
                val typed = if (comma) "1250,5" else "1250.5"
                val own = typed.map { c -> if (c in '0'..'9' && r.language in zero) zero.getValue(r.language) + (c - '0') else c }.joinToString("")
                for (text in listOf(typed, own)) {
                    val got = Digits.parse(text)
                    if (got == null || Math.abs(got - 1250.5) > 1e-9) faults += "${r.id} typed \"$text\" read as $got"
                }
            }
        }
        assertEquals(faults.joinToString("\n"), 0, faults.size)
    }
}
