package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hudhud measured on sentences he has never seen (13 Nov 2026, the owner: "high-end result, accuracy ke saath"): the
 * held-out set (scripts/gen_hudhud_bank.py keeps two of every meaning's ten sentences in each language out of the
 * bank) is asked of the whole of [Hudhud.understand] — rules and sense together — over a small made-up book, and
 * what he took each sentence for is compared with what it means. The figures are printed per language; the test
 * fails below the floor, so a change that makes him understand less cannot go in unseen.
 */
class HudhudSenseTest {

    // understand() reads with the sense the app ships (the rules alone: byRules).
    init { HudhudSense.use(HudhudModelTest.shippedModel()) }

    /** A made-up book: the same person and product in each script a question may name them in. */
    private val names = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(3, "علي رضا"), Hudhud.Name(4, "अली रज़ा"),
        Hudhud.Name(5, "আলী রাজা"), Hudhud.Name(6, "Bashir Ahmad"), Hudhud.Name(7, "Tariq Butt"), Hudhud.Name(8, "Naeem Khan"),
        Hudhud.Name(9, "Saleem Bhai")
    )
    private val goods = listOf(
        Hudhud.Name(101, "Urea"), Hudhud.Name(102, "یوریا"), Hudhud.Name(103, "يوريا"), Hudhud.Name(104, "यूरिया"),
        Hudhud.Name(105, "ইউরিয়া"), Hudhud.Name(106, "DAP"), Hudhud.Name(107, "Sugar")
    )
    private val topics = listOf(
        Hudhud.Topic("faq_backup", "Backup بیک اپ بیک‌اپ बैकअप ব্যাকআপ نسخة احتياطية پشتیبان cadangan", "Take a backup of the book"),
        Hudhud.Topic("faq_invoice", "Invoice انوائس اِنوائس इनवॉइस বিল ইনভয়েস فاتورة فاکتور faktur bill", "Make an invoice"),
        Hudhud.Topic("faq_customer", "Customer گاہک گراهڪ ग्राहक গ্রাহক عميل مشتری pelanggan", "Add a customer")
    )

    private fun filled(lang: String, text: String): String {
        val (name, item) = when (lang) {
            "ur", "sd", "fa" -> "علی رضا" to "یوریا"
            "ar" -> "علي رضا" to "يوريا"
            "hi" -> "अली रज़ा" to "यूरिया"
            "bn" -> "আলী রাজা" to "ইউরিয়া"
            else -> "Ali Raza" to "Urea"
        }
        return text.replace("{NAME}", name).replace("{ITEM}", item)
    }

    /** What a reading counts as: a guess counts as what it guessed; a help page with nothing found is the list. */
    private fun labelOf(a: Hudhud.Ask): String? = when (a) {
        is Hudhud.Ask.Guessed -> labelOf(a.ask)
        is Hudhud.Ask.NeedName -> "needs_name"
        else -> Hudhud.labelOf(a)
    }

    /** Readings that answer the question just as well as its own label would. */
    private fun close(expected: String, got: String?): Boolean = expected == got ||
        // A help question with no page for it gets the list of what he knows; a name's facts for a name.
        (expected == "help_howto" && got == "abilities") ||
        (expected == "who_are_you" && got == "abilities") ||
        (expected == "hello" && (got == "address" || got == "salaam")) ||
        (expected == "address" && got == "hello") ||
        (expected == "today_summary" && got == "activity") ||
        (expected == "sales_today" && got == "sales_period") ||
        (expected == "party_details" && got == "party_balance")

    @Test fun `he understands sentences he has never seen`() {
        val held = javaClass.getResourceAsStream("/hudhud/heldout.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() }
        if (held.isEmpty()) return
        val byLang = LinkedHashMap<String, IntArray>()
        val misses = ArrayList<String>()
        for (line in held) {
            val (lang, label, text) = line.split('\t', limit = 3)
            val sentence = filled(lang, text)
            val got = Hudhud.understand(sentence, names, topics, lang, goods)
            val ok = close(label, labelOf(got))
            val tally = byLang.getOrPut(lang) { IntArray(2) }
            tally[1]++
            if (ok) tally[0]++ else misses += "$lang  $label  <- $sentence  => $got"
        }
        val total = byLang.values.sumOf { it[1] }
        val right = byLang.values.sumOf { it[0] }
        println("Hudhud on held-out sentences: $right of $total right (${right * 100 / total}%)")
        for ((lang, t) in byLang) println("  $lang: ${t[0]} of ${t[1]} (${t[0] * 100 / t[1]}%)")
        misses.forEach { println("  miss: $it") }
        assertTrue("held-out accuracy ${right * 100 / total}% is below the floor", right * 100 >= FLOOR * total)
    }

    private companion object {
        /** The least share of the held-out set he must read right, in percent. */
        const val FLOOR = 88
    }
}
