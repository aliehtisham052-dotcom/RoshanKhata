package com.innovation313.roshankhata.data

import java.util.Locale

/**
 * The units of measure, shown in the app's language and SAVED in one (4 Oct 2026).
 *
 * The unit list used to be English in every language: a Hindi screen offered
 * "bag, kg, bottle". It was left that way on purpose, because the unit is
 * saved with every goods line and the app groups a product's lines, its
 * stock and its last rate by item AND unit. A translated word saved on one
 * entry and "bag" on the next would split one product in two.
 *
 * So the two are separated. What is SAVED is always the same key ([KEYS],
 * the old English list, exactly what every existing book already holds).
 * What is SHOWN is that key's word in the app's language ([label]). What is
 * TYPED or picked goes back to the key before anything is saved or compared
 * ([canonical]) — from any of the app's languages, so a helper typing "بوری"
 * on a phone set to English still saves "bag".
 *
 * A unit that is not one of these (the owner's own word: "tin", "تھیلا") is
 * left exactly as typed, shown as typed, in every language. It always was.
 *
 * Words the owner typed by hand BEFORE this, where they are one of these
 * labels ("بوری" typed under the old English list), are turned into the key
 * once as the book opens ([normalizeStored]); otherwise the same product
 * would sit under "بوری" (old) and "bag" (new) while both read "بوری".
 */
object UnitWords {

    /** What is saved. Order is the order of the list the owner picks from. */
    val KEYS: List<String> = listOf(
        "bag", "kg", "maund", "seer", "litre", "ml", "packet", "bottle", "piece", "box", "ton",
        // For the trades added 11 Oct: cloth by the metre, shoes by the pair,
        // eggs and bread by the dozen, a restaurant's plates, gas cylinders.
        "metre", "pair", "dozen", "plate", "cylinder"
    )

    /** One array per language, in [KEYS] order. English shows the keys themselves. */
    private val LABELS: Map<String, Array<String>> = mapOf(
        "ur-latn" to arrayOf(
            "bori", "kg", "mann", "ser", "litre", "ml", "packet", "botal", "adad", "dabba", "ton",
            "meter", "jora", "darjan", "plate", "cylinder"
        ),
        "ur" to arrayOf(
            "بوری", "کلو", "من", "سیر", "لیٹر", "ملی لیٹر", "پیکٹ", "بوتل", "عدد", "ڈبہ", "ٹن",
            "میٹر", "جوڑا", "درجن", "پلیٹ", "سلنڈر"
        ),
        "sd" to arrayOf(
            "ٻوري", "ڪلو", "مڻ", "سير", "ليٽر", "ملي ليٽر", "پيڪيٽ", "بوتل", "عدد", "دٻو", "ٽن",
            "ميٽر", "جوڙو", "درجن", "پليٽ", "سلنڊر"
        ),
        "fa" to arrayOf(
            "کیسه", "کیلو", "من", "سیر", "لیتر", "میلی‌لیتر", "بسته", "بطری", "عدد", "جعبه", "تن",
            "متر", "جفت", "دوجین", "بشقاب", "کپسول"
        ),
        "ar" to arrayOf(
            "كيس", "كغ", "مَنّ", "سير", "لتر", "مل", "عبوة", "زجاجة", "قطعة", "صندوق", "طن",
            "متر", "زوج", "دزينة", "طبق", "أسطوانة"
        ),
        "hi" to arrayOf(
            "बोरी", "किलो", "मन", "सेर", "लीटर", "मिली", "पैकेट", "बोतल", "नग", "डिब्बा", "टन",
            "मीटर", "जोड़ी", "दर्जन", "प्लेट", "सिलेंडर"
        ),
        "bn" to arrayOf(
            "বস্তা", "কেজি", "মণ", "সের", "লিটার", "মিলি", "প্যাকেট", "বোতল", "পিস", "বাক্স", "টন",
            "মিটার", "জোড়া", "ডজন", "প্লেট", "সিলিন্ডার"
        ),
        "id" to arrayOf(
            "karung", "kg", "maund", "seer", "liter", "ml", "bungkus", "botol", "buah", "kotak", "ton",
            "meter", "pasang", "lusin", "piring", "tabung"
        )
    )

    /** Every label of every language, and every key, to its key. Lower-cased. */
    val TO_KEY: Map<String, String> = buildMap {
        for ((i, key) in KEYS.withIndex()) {
            put(key, key)
            for (labels in LABELS.values) put(fold(labels[i]), key)
        }
    }

    private fun fold(text: String): String = text.trim().lowercase(Locale.ROOT)

    fun tableOf(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        return when {
            language == "ur" && locale.script.equals("Latn", ignoreCase = true) -> "ur-latn"
            language == "in" -> "id"
            else -> language
        }
    }

    /**
     * The word to SHOW for a saved unit: its name in the app's language when
     * it is one of [KEYS] (in any letter case), otherwise the text as saved.
     * Blank for none.
     */
    fun label(stored: String?, locale: Locale = DateWords.appLocale()): String {
        val text = stored?.trim().orEmpty()
        if (text.isEmpty()) return ""
        val index = KEYS.indexOf(text.lowercase(Locale.ROOT))
        if (index < 0) return text
        return LABELS[tableOf(locale)]?.get(index) ?: text
    }

    /** The list the owner picks from, in the app's language. */
    fun choices(locale: Locale = DateWords.appLocale()): List<String> =
        KEYS.map { label(it, locale) }

    // label(context, key), choices(context) and normalizeStored(db) need the
    // phone (TradeVocab, SQLite): app data/UnitWordsAndroid.kt.

    /**
     * The UPDATEs that turn units saved as one of the app's own labels into
     * their key, one per table in [STORED_IN], touching only rows whose unit
     * is exactly such a label; a key, an empty unit and the owner's own words
     * are not rows they match. Run once per opening of a book, each on its
     * own and never fatal — by the phone (normalizeStored) and, since 23 Oct
     * 2026, by the computer's DesktopDatabase too, which until then never
     * repaired a book whose Windows forms had saved "بوری" for "bag".
     *
     * English keys are deliberately not in the list: "bag" is already right,
     * and "Bag" typed by hand is left as the owner wrote it (the app has
     * always compared units without regard to letter case).
     *
     * Run them through [repairStored], never one by one: the tables are
     * synced live, and only there are the UPDATEs kept out of the sync log.
     */
    fun normalizeStatements(): List<String> {
        val pairs = TO_KEY.filter { (label, key) -> label != key }
        if (pairs.isEmpty()) return emptyList()
        val quoted = { s: String -> "'" + s.replace("'", "''") + "'" }
        val cases = pairs.entries.joinToString(" ") { (label, key) -> "WHEN ${quoted(label)} THEN ${quoted(key)}" }
        val labels = pairs.keys.joinToString(", ") { quoted(it) }
        return STORED_IN.map { (table, column) ->
            "UPDATE $table SET $column = CASE lower(trim($column)) $cases ELSE $column END " +
                "WHERE $column IS NOT NULL AND lower(trim($column)) IN ($labels)"
        }
    }

    /**
     * What to SAVE or COMPARE for a unit the owner typed or picked: the key
     * when the text is a key or any language's word for one, otherwise the
     * text itself, trimmed. Null for nothing typed.
     */
    fun canonical(typed: String?): String? {
        val text = typed?.trim().orEmpty()
        if (text.isEmpty()) return null
        return TO_KEY[fold(text)] ?: text
    }

    /**
     * Every table and column a unit is saved in. The counter's tables were
     * missing until 23 Oct 2026 (phase 2 of the review report): a sale line
     * saved as "بوری" did not meet the product's "bag" in stock or returns,
     * and a pack ("بوری" against a line's "bag") was never found as a pack.
     */
    val STORED_IN = listOf(
        "transactions" to "unit",
        "entry_items" to "unit",
        "bill_items" to "unit",
        "invoice_items" to "unit",
        "products" to "defaultUnit",
        "products" to "packUnit",
        "schemes" to "unit",
        "sale_items" to "unit",
        "sale_return_items" to "unit",
        "purchase_order_items" to "unit",
        "stock_adjustments" to "unit"
    )

    /**
     * One read that says whether any row of [STORED_IN] holds a label (1) or
     * none does (0), so a book already in order is not written to at all as
     * it opens. Null when there is nothing to repair.
     */
    fun repairNeededQuery(): String? {
        val labels = labelsToRepair()
        if (labels.isEmpty()) return null
        val list = labels.joinToString(", ") { quote(it) }
        return "SELECT " + STORED_IN.joinToString(" OR ") { (table, column) ->
            "EXISTS (SELECT 1 FROM $table WHERE $column IS NOT NULL AND lower(trim($column)) IN ($list))"
        }
    }

    /**
     * Live sync's own switch that keeps a write out of its log (LiveSync's
     * triggers note nothing while sync_mute has a row; a parcel is written
     * under it). The table exists only in a book that was ever synced: in
     * any other, [MUTE] fails, and there are no triggers to keep quiet.
     */
    const val MUTE = "INSERT INTO sync_mute (one) VALUES (1)"
    const val UNMUTE = "DELETE FROM sync_mute"

    /**
     * The repair as a book opens, on both platforms (the phone's
     * normalizeStored, DesktopDatabase's onOpen): [repairNeededQuery] first,
     * through [needed], and nothing more when it says no; else the UPDATEs of
     * [normalizeStatements] in ONE transaction ([inTransaction]), under
     * [MUTE], each on its own and never fatal.
     *
     * Why muted (23 Oct 2026): every one of these tables is synced live. Not
     * muted, the repair of a book with years of hand-typed "bori" sent every
     * such row to the other devices at once (a relay's daily byte cap could
     * be spent on it), and as each device repairs its own copy as it opens,
     * the same row arrived changed on both sides — a conflict in a money
     * table, said in the bell, over two identical rows. Muted, each device
     * repairs its own copy and the copies end the same (both run this build:
     * a pairing already needs phone and computer on the same one). A row
     * that comes back from another device still as a label is repaired on
     * the next open, again in silence.
     *
     * The mute is lifted inside the same transaction, so a crash between the
     * two leaves no muted book behind: the whole transaction is undone. A
     * failure of the lift itself undoes the repair instead of keeping a mute.
     */
    fun repairStored(needed: (String) -> Boolean, inTransaction: (() -> Unit) -> Unit, exec: (String) -> Unit) {
        val updates = normalizeStatements()
        if (updates.isEmpty()) return
        val ask = repairNeededQuery() ?: return
        // A read that fails (it should not) is no reason to skip the repair: each UPDATE is tried on its own.
        if (!runCatching { needed(ask) }.getOrDefault(true)) return
        runCatching {
            inTransaction {
                val muted = runCatching { exec(MUTE) }.isSuccess
                for (sql in updates) runCatching { exec(sql) }
                if (muted) exec(UNMUTE)
            }
        }
    }

    private fun labelsToRepair(): List<String> = TO_KEY.filter { (label, key) -> label != key }.keys.toList()

    private fun quote(s: String): String = "'" + s.replace("'", "''") + "'"
}
