package com.innovation313.roshankhata.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money side holds under every shuffle of the sense (25 Nov 2026, BC-240 and lesson 163; the rest of plan A1).
 *
 * HudhudSeedSafeTest holds a short list under five other seeds. This holds EVERY money sentence the repository keeps — the opposite pairs
 * of [HudhudMoneySidePairsTest], [HudhudMoneySideTest]'s pairs, the Arabic payables of BC-256, the money rows of review sets D and O
 * (customer figures, supplier figures, entries, with the sentence before them where they have one) and ten sentences of the classes this
 * round found — under the app's sense and five senses learnt from the same bank with seeds 314 to 318 (in memory). A reading's money SIDE
 * (the customer's: to get, who owes most or least, not paid, when he will pay; the supplier's: to give; an entry gave or got; two entries)
 * must be the side the row says, and the same under every seed; an order to the machine is never a write under any. A first probe of
 * 480 such readings found ten that moved: a supplier's money read as a count of suppliers or a help page (Persian, Indonesian,
 * Bengali, Urdu "payable"), Persian "how much must I get" as a name question, an entry "Ali Raza ne paanch sau diye" as a promised day,
 * and "my money stuck outside" as the cashbook; the rules now say each (VoiceQuestion.payableAsked, TO_GET, OTHERS_HOLD; Hudhud's
 * sideShaped and the guards in decide). Invented names only; the wording of sd, bn, fa, ar, in, hi rows wants a native read.
 */
class HudhudSeedSafeMoneyTest {

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
    private val commandBook = names + listOf(Hudhud.Name(50L, "Ignore Previous Instructions"), Hudhud.Name(51L, "System Admin"))

    private class Item(val lang: String, val said: String, val prev: String?, val expect: String?, val src: String)

    private fun unwrap(a: Hudhud.Ask): Hudhud.Ask = when (a) {
        is Hudhud.Ask.Guessed -> unwrap(a.ask)
        is Hudhud.Ask.Now -> unwrap(a.ask)
        else -> a
    }

    /** The money side a reading is on: CUST, SUPP, GAVE, GOT, a pair of entries, or "-" for none. */
    private fun side(a: Hudhud.Ask): String {
        val x = unwrap(a)
        return when {
            x is Hudhud.Ask.Figure && x.kind in setOf(VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.WHO_OWES_MOST,
                VoiceQuestion.Kind.WHO_OWES_LEAST, VoiceQuestion.Kind.NOT_PAID) -> "CUST"
            // "Who will pay me" is the customers' side too.
            x is Hudhud.Ask.WhenPays -> "CUST"
            x is Hudhud.Ask.Figure && x.kind == VoiceQuestion.Kind.TO_GIVE -> "SUPP"
            x is Hudhud.Ask.Entry -> if (x.isGiven) "GAVE" else "GOT"
            x is Hudhud.Ask.Entries -> "ENTRIES" + x.entries.map { if (it.isGiven) "G" else "R" }
            else -> "-"
        }
    }

    private fun items(): List<Item> {
        val out = ArrayList<Item>()
        for (r in HudhudMoneySidePairsTest.ROWS) {
            val e = when (r.side) {
                HudhudMoneySidePairsTest.Side.CUSTOMER -> "CUST"
                HudhudMoneySidePairsTest.Side.SUPPLIER -> "SUPP"
                HudhudMoneySidePairsTest.Side.GAVE -> "GAVE"
                HudhudMoneySidePairsTest.Side.GOT -> "GOT"
                HudhudMoneySidePairsTest.Side.GOT_THEN_GAVE -> "ENTRIES[R, G]"
                else -> null // a count of customers or suppliers: no money side
            }
            out += Item(r.lang, r.said, null, e, "pairs")
        }
        for ((l, s, k) in HudhudMoneySideTest.PAIRS) out += Item(l, s, null, if (k == VoiceQuestion.Kind.TO_GET) "CUST" else "SUPP", "side")
        for (s in listOf("أي الموردين لم أدفع لهم", "أي الموردين لم أدفع لهم بعد", "من لم أدفع له من الموردين", "أي مورد لم أسدد له", "الموردين الذين لم أسدد لهم"))
            out += Item("ar", s, null, "SUPP", "arabic")
        for (s in listOf("من يدين لي بأكبر مبلغ", "من مدين لي بأكثر", "من هو أكبر مدين لي", "من يدين لي بأعلى مبلغ"))
            out += Item("ar", s, null, "CUST", "arabic")
        // The classes this round found, said again in other words of the same shape.
        for ((l, s, e) in listOf(
            Triple("fa", "چقدر باید به تامین کننده ها بدهم", "SUPP"), Triple("fa", "چقدر به تامین‌کننده بدهکارم", "SUPP"),
            Triple("bn", "কোন সাপ্লায়ারকে টাকা দেওয়া হয়নি", "SUPP"), Triple("in", "pemasok mana yang belum saya bayar", "SUPP"),
            Triple("ur", "سپلائر پے ایبل کتنا ہے", "SUPP"), Triple("fa", "چقدر باید بگیرم", "CUST"),
            Triple("ur-Latn", "Ali Raza ne dhai hazaar diye", "GOT"), Triple("ur-Latn", "Ali Raza ne paanch sau diye", "GOT"),
            Triple("ur-Latn", "mera kitna paisa bahar phansa hua hai", "CUST"), Triple("ur", "میرا کتنا پیسہ باہر پھنسا ہوا ہے", "CUST"),
            Triple("hi", "मेरा कितना पैसा बाहर फंसा हुआ है", "CUST")
        )) out += Item(l, s, null, e, "classes")
        for (f in listOf("review_d", "review_o")) {
            val lines = javaClass.getResourceAsStream("/hudhud/$f.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            for (r in lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.filter { it.size >= 5 && it[1] == "OK" }) {
                val e = when (r[4]) {
                    "Figure:TO_GET", "Figure:WHO_OWES_MOST", "Figure:WHO_OWES_LEAST", "Figure:NOT_PAID" -> "CUST"
                    "Figure:TO_GIVE" -> "SUPP"
                    "Entry" -> "ENTRY"
                    else -> null
                }
                if (e != null) out += Item(r[0], r[2], r[3].ifBlank { null }, e, f)
            }
        }
        return out
    }

    private fun matches(item: Item, s: String) = when (item.expect) {
        null -> true
        "ENTRY" -> s == "GAVE" || s == "GOT"
        else -> s == item.expect
    }

    @Test fun `every money sentence keeps its side under every shuffle of the same bank`() {
        val all = items()
        assertTrue("fewer money sentences than expected: ${all.size}", all.size >= 450)
        val bank = HudhudModelTest.bank()
        val pagesOf = HashMap<String, List<Hudhud.Topic>>()
        val bySeed = LinkedHashMap<Long, List<String>>()
        try {
            for (seed in listOf(HudhudSense.SEED, 314L, 315L, 316L, 317L, 318L)) {
                HudhudSense.use(if (seed == HudhudSense.SEED) HudhudModelTest.shippedModel() else HudhudSense.train(bank, seed))
                bySeed[seed] = all.map { it ->
                    val pages = pagesOf.getOrPut(it.lang) { HudhudRealPages.forApp(it.lang) }
                    val before = it.prev?.let { p -> Hudhud.understand(p, names, pages, it.lang, goods, null) }
                    side(Hudhud.understand(it.said, names, pages, it.lang, goods, before))
                }
            }
        } finally {
            HudhudSense.use(HudhudModelTest.shippedModel())
        }
        val problems = ArrayList<String>()
        for ((i, it) in all.withIndex()) {
            val sides = bySeed.values.map { s -> s[i] }
            if (sides.toSet().size > 1 || sides.any { s -> !matches(it, s) })
                problems += "  [${it.src}][${it.lang}] ${it.said} (expect ${it.expect}): " + bySeed.keys.zip(sides).joinToString { (k, s) -> "$k=$s" }
        }
        assertTrue(problems.joinToString("\n", "${problems.size} of ${all.size} money readings hang on the shuffle:\n"), problems.isEmpty())
    }

    @Test fun `an order to the machine is never a write under any shuffle`() {
        val bank = HudhudModelTest.bank()
        val orders = HudhudInjectionTest.INJECTIONS + HudhudMoneySidePairsTest.ORDERS.filter { !it.second.startsWith("<script>") }
        val problems = ArrayList<String>()
        try {
            for (seed in listOf(HudhudSense.SEED, 314L, 315L, 316L, 317L, 318L)) {
                HudhudSense.use(if (seed == HudhudSense.SEED) HudhudModelTest.shippedModel() else HudhudSense.train(bank, seed))
                for (book in listOf(names, commandBook)) for ((lang, said) in orders) {
                    val a = Hudhud.understand(said, book, HudhudRealPages.forApp(lang), lang, goods, null)
                    if (HudhudInjectionTest.dangerous(a)) problems += "  seed $seed [$lang] $said => $a"
                }
            }
        } finally {
            HudhudSense.use(HudhudModelTest.shippedModel())
        }
        assertTrue(problems.joinToString("\n", "${problems.size} orders read as a write:\n"), problems.isEmpty())
    }

    // --- one test per rule, through understand with the sense, where only that rule can refuse (lessons 129, 145) ---

    private fun read(lang: String, said: String) =
        unwrap(Hudhud.understand(said, names, HudhudRealPages.forApp(lang), lang, goods, null))

    @Test fun `a supplier's money asked outright is the payables and a bare count stays a count`() {
        for ((l, s) in listOf("fa" to "چقدر باید به تامین کننده ها بدهم", "bn" to "কোন সাপ্লায়ারকে টাকা দেওয়া হয়নি",
            "in" to "pemasok mana yang belum saya bayar", "ur" to "سپلائر پے ایبل کتنا ہے")) {
            assertTrue(s, VoiceQuestion.payableAsked(s))
            assertTrue("$s -> ${read(l, s)}", read(l, s) == Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE))
        }
        assertTrue(!VoiceQuestion.payableAsked("kitne suppliers hain"))
        assertTrue(read("ur-Latn", "kitne suppliers hain") == Hudhud.Ask.Count(Hudhud.Thing.SUPPLIERS))
    }

    @Test fun `my money stuck outside is the shop's to get`() {
        for (s in listOf("mera kitna paisa bahar phansa hua hai", "میرا کتنا پیسہ باہر پھنسا ہوا ہے", "मेरा कितना पैसा बाहर फंसा हुआ है"))
            assertTrue(s, VoiceQuestion.classify(s) == VoiceQuestion.Kind.TO_GET)
        // Theirs with me is still the shop's own debt.
        assertTrue(VoiceQuestion.classify("kis kis ka paisa mere paas hai") == VoiceQuestion.Kind.TO_GIVE)
    }

    @Test fun `Persian how much must I get is the shop's to get`() {
        assertTrue(VoiceQuestion.classify("چقدر باید بگیرم") == VoiceQuestion.Kind.TO_GET)
    }

    @Test fun `an entry said as a fact is never turned by the sense`() {
        for (s in listOf("Ali Raza ne dhai hazaar diye", "Ali Raza ne paanch sau diye")) {
            val a = read("ur-Latn", s)
            assertTrue("$s -> $a", a is Hudhud.Ask.Entry && !a.isGiven)
        }
    }
}
