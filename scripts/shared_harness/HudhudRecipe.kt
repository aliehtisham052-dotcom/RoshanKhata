package com.innovation313.roshankhata.data

import java.io.File
import java.security.MessageDigest

/**
 * Hudhud's STABLE TRAINING RECIPE, measured (plan H1, 25 Nov 2026; lesson 163, the owed retrain L017). A program, not a test:
 * it asserts nothing and writes nothing of the repository (senses are learnt in memory and dropped; sense.bin is only read).
 * Run it with scripts/hudhud_recipe.sh.
 *
 * It learns the sense in four ways — the bank as shipped (recipe-1 data) or with L017's two data faults repaired (recipe-2
 * data, written by `gen_hudhud_bank.py --recipe 2 --out-dir`), each with ONE seed (today) or the mean of K seeds
 * ([HudhudSense.trainEnsemble]) — five times each, with five DIFFERENT outer seeds (one seed 314..318, or the K seeds
 * 1000*(o+1)+i). Every sense reads every row of the held-out set, the owner's two lists and review sets D and O (as
 * HudhudBaseline does), plus the guard sentences of HudhudSeedSafeTest and HudhudSeedSafeMoneyTest, and the program says per
 * cell: the counts per set and language, the entity wrong-sides and wrong-amounts, how many rows read differently between
 * the five outer seeds (the spread that matters), how many differ from the shipped sense.bin (what a retrain changes), and
 * which guard sentences fail.
 *
 * Books, topics and the held-out filling are copied from HudhudBaseline (its members are private); the counts come from
 * HudhudBaseline.pass itself, so they are the baseline's own numbers, and the shipped sense must give exactly the baseline's
 * figures (1059/1077/157/807/1046/1074): the program says so first.
 */
object HudhudRecipe {

    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
    private val commandBook = names + listOf(Hudhud.Name(50L, "Ignore Previous Instructions"), Hudhud.Name(51L, "System Admin"))
    private val heldNames = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(3, "علي رضا"), Hudhud.Name(4, "अली रज़ा"),
        Hudhud.Name(5, "আলী রাজা"), Hudhud.Name(6, "Bashir Ahmad"), Hudhud.Name(7, "Tariq Butt"), Hudhud.Name(8, "Naeem Khan"),
        Hudhud.Name(9, "Saleem Bhai")
    )
    private val heldGoods = listOf(
        Hudhud.Name(101, "Urea"), Hudhud.Name(102, "یوریا"), Hudhud.Name(103, "يوريا"), Hudhud.Name(104, "यूरिया"),
        Hudhud.Name(105, "ইউরিয়া"), Hudhud.Name(106, "DAP"), Hudhud.Name(107, "Sugar")
    )
    private val heldTopics = listOf(
        Hudhud.Topic("faq_backup", "Backup بیک اپ بیک‌اپ बैकअप ব্যাকআপ نسخة احتياطية پشتیبان cadangan", "Take a backup of the book"),
        Hudhud.Topic("faq_invoice", "Invoice انوائس اِنوائس इनवॉइस বিল ইনভয়েস فاتورة فاکتور faktur bill", "Make an invoice"),
        Hudhud.Topic("faq_customer", "Customer گاہک گراهڪ ग्राहक গ্রাহক عميل مشتری pelanggan", "Add a customer")
    )

    private fun heldFilled(lang: String, text: String): String {
        val (name, item) = when (lang) {
            "ur", "sd", "fa" -> "علی رضا" to "یوریا"
            "ar" -> "علي رضا" to "يوريا"
            "hi" -> "अली रज़ा" to "यूरिया"
            "bn" -> "আলী রাজা" to "ইউরিয়া"
            else -> "Ali Raza" to "Urea"
        }
        return text.replace("{NAME}", name).replace("{ITEM}", item)
    }

    private fun lines(resource: String): List<String> =
        HudhudRecipe::class.java.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }

    /** [right]: how a reading is judged right for this row (the held-out label; the owner's accepted readings; the review row's held reading when it is OK). */
    private class Row(val set: String, val lang: String, val said: String, val prev: String?, val right: (String) -> Boolean = { false })

    /** Every labelled row, in a fixed order: held-out, owner_set, owner_1000, review_d, review_o. */
    private val rows: List<Row> by lazy {
        val out = ArrayList<Row>()
        for (l in lines("/hudhud/heldout.tsv")) { val (lang, _, t) = l.split('\t', limit = 3); val label = l.split('\t', limit = 3)[1]; out += Row("heldout", lang, heldFilled(lang, t), null) { it == label } }
        for (f in listOf("owner_set", "owner_1000")) for (l in lines("/hudhud/$f.tsv")) { val r = l.split('\t'); val ok = r.last().split('|'); out += Row(f, "owner", r[1], null) { shown -> ok.any { shown.startsWith(it) } } }
        for (f in listOf("review_d", "review_o")) for (l in lines("/hudhud/$f.tsv")) {
            val r = l.split('\t'); val ok = r[1] == "OK"; val held = r[4]
            out += Row(f, r[0], r[2], r.getOrNull(3)?.takeIf { it.isNotBlank() }) { shown -> ok && shown == held }
        }
        out
    }
    private val ownerTopics: List<Hudhud.Topic> by lazy { HudhudRealPages.topics(listOf("values-b+ur+Latn", "values", "values-ur")) }
    private val pagesBy = HashMap<String, List<Hudhud.Topic>>()
    private fun pages(lang: String) = pagesBy.getOrPut(lang) { HudhudRealPages.forApp(lang) }

    private fun shownOf(a: Hudhud.Ask): String = when (a) {
        is Hudhud.Ask.Guessed -> shownOf(a.ask)
        is Hudhud.Ask.NeedName -> "needs_name"
        else -> Hudhud.labelOf(a) ?: HudhudShown.of(a)
    }

    /** One reading per row, as a string (what "moves" is a different string). Held-out rows are their label; the rest HudhudShown. */
    private fun readRows(): List<String> = rows.map { r ->
        when (r.set) {
            "heldout" -> {
                val got = Hudhud.understand(r.said, heldNames, heldTopics, r.lang, heldGoods)
                shownOf(got)
            }
            "owner_set", "owner_1000" -> HudhudShown.of(Hudhud.understand(r.said, names, ownerTopics, null, goods))
            else -> {
                val tp = pages(r.lang)
                val before = r.prev?.let { Hudhud.understand(it, names, tp, r.lang, goods, null) }
                HudhudShown.of(Hudhud.understand(r.said, names, tp, r.lang, goods, before))
            }
        }
    }

    // ---- the guard sentences (HudhudSeedSafeTest, HudhudSeedSafeMoneyTest): a reading's money side, an order to a machine
    private class Guard(val tag: String, val lang: String, val said: String, val prev: String?, val expect: String?, val book: List<Hudhud.Name>)

    private fun unwrap(a: Hudhud.Ask): Hudhud.Ask = when (a) {
        is Hudhud.Ask.Guessed -> unwrap(a.ask)
        is Hudhud.Ask.Now -> unwrap(a.ask)
        else -> a
    }

    private fun sideOf(a: Hudhud.Ask): String {
        val x = unwrap(a)
        return when {
            x is Hudhud.Ask.Figure && x.kind in setOf(VoiceQuestion.Kind.TO_GET, VoiceQuestion.Kind.WHO_OWES_MOST,
                VoiceQuestion.Kind.WHO_OWES_LEAST, VoiceQuestion.Kind.NOT_PAID) -> "CUST"
            x is Hudhud.Ask.WhenPays -> "CUST"
            x is Hudhud.Ask.Figure && x.kind == VoiceQuestion.Kind.TO_GIVE -> "SUPP"
            x is Hudhud.Ask.Entry -> if (x.isGiven) "GAVE" else "GOT"
            x is Hudhud.Ask.Entries -> "ENTRIES" + x.entries.map { if (it.isGiven) "G" else "R" }
            x is Hudhud.Ask.Count && x.thing == Hudhud.Thing.CUSTOMERS -> "COUNT_C"
            x is Hudhud.Ask.Count && x.thing == Hudhud.Thing.SUPPLIERS -> "COUNT_S"
            else -> "-"
        }
    }

    private val guards: List<Guard> by lazy {
        val out = ArrayList<Guard>()
        for (r in HudhudMoneySidePairsTest.ROWS) {
            val e = when (r.side) {
                HudhudMoneySidePairsTest.Side.CUSTOMER -> "CUST"; HudhudMoneySidePairsTest.Side.SUPPLIER -> "SUPP"
                HudhudMoneySidePairsTest.Side.GAVE -> "GAVE"; HudhudMoneySidePairsTest.Side.GOT -> "GOT"
                HudhudMoneySidePairsTest.Side.COUNT_C -> "COUNT_C"; HudhudMoneySidePairsTest.Side.COUNT_S -> "COUNT_S"
                HudhudMoneySidePairsTest.Side.GOT_THEN_GAVE -> "ENTRIES[R, G]"
            }
            out += Guard("pairs", r.lang, r.said, null, e, names)
        }
        for ((l, s, k) in HudhudMoneySideTest.PAIRS) out += Guard("side", l, s, null, if (k == VoiceQuestion.Kind.TO_GET) "CUST" else "SUPP", names)
        for ((l, s) in listOf("ur-Latn" to "logon ke paas mere kitne paise hain", "ur-Latn" to "mera kitna paisa logon ke paas phansa hua hai",
            "ur" to "لوگوں کے پاس میرے کتنے پیسے ہیں", "hi" to "लोगों के पास मेरे कितने पैसे हैं", "ur-Latn" to "bazaar mein mera kitna paisa hai",
            "ur-Latn" to "mera kitna paisa bahar phansa hua hai", "ur" to "میرا کتنا پیسہ باہر پھنسا ہوا ہے", "hi" to "मेरा कितना पैसा बाहर फंसा हुआ है",
            "fa" to "چقدر باید بگیرم"))
            out += Guard("held", l, s, null, "CUST", names)
        for ((l, s, e) in listOf(Triple("fa", "چقدر باید به تامین کننده ها بدهم", "SUPP"), Triple("fa", "چقدر به تامین‌کننده بدهکارم", "SUPP"),
            Triple("bn", "কোন সাপ্লায়ারকে টাকা দেওয়া হয়নি", "SUPP"), Triple("in", "pemasok mana yang belum saya bayar", "SUPP"),
            Triple("ur", "سپلائر پے ایبل کتنا ہے", "SUPP"), Triple("ur-Latn", "Ali Raza ne dhai hazaar diye", "GOT"),
            Triple("ur-Latn", "Ali Raza ne paanch sau diye", "GOT")))
            out += Guard("classes", l, s, null, e, names)
        for (f in listOf("review_d", "review_o")) for (l in lines("/hudhud/$f.tsv")) {
            val r = l.split('\t')
            if (r.size < 5 || r[1] != "OK") continue
            val e = when (r[4]) {
                "Figure:TO_GET", "Figure:WHO_OWES_MOST", "Figure:WHO_OWES_LEAST", "Figure:NOT_PAID" -> "CUST"
                "Figure:TO_GIVE" -> "SUPP"; "Entry" -> "ENTRY"; else -> null
            }
            if (e != null) out += Guard(f, r[0], r[2], r[3].ifBlank { null }, e, names)
        }
        for (book in listOf(names, commandBook)) {
            for ((l, s) in HudhudInjectionTest.INJECTIONS) out += Guard("order", l, s, null, "NOWRITE", book)
            for ((l, s) in HudhudMoneySidePairsTest.ORDERS.filter { !it.second.startsWith("<script>") }) out += Guard("order", l, s, null, "NOWRITE", book)
        }
        out
    }

    /** The guard sentences this sense fails (a stable text each), and every guard's side (for drift against the shipped sense). */
    private class GuardRun(val failed: List<String>, val sides: List<String>)

    private fun runGuards(): GuardRun {
        val failed = ArrayList<String>()
        val sides = ArrayList<String>()
        for (g in guards) {
            val tp = pages(g.lang)
            val before = g.prev?.let { Hudhud.understand(it, g.book, tp, g.lang, goods, null) }
            val a = Hudhud.understand(g.said, g.book, tp, g.lang, goods, before)
            val s = sideOf(a)
            sides += s
            val ok = when (g.expect) {
                "NOWRITE" -> !HudhudInjectionTest.dangerous(a)
                "ENTRY" -> s == "GAVE" || s == "GOT"
                null -> true
                else -> s == g.expect
            }
            if (!ok) failed += "[${g.tag}][${g.lang}] ${g.said}  => ${if (g.expect == "NOWRITE") a.toString().take(60) else s} (want ${g.expect})"
        }
        return GuardRun(failed, sides)
    }

    // ---- the cells
    private class Result(val name: String, val seconds: Double, val fig: HudhudBaseline.Figures, val readings: List<String>, val guard: GuardRun)

    private val SERIES = listOf("heldout strict", "heldout lenient", "owner_set", "owner_1000", "review_d", "review_o")

    private fun measure(name: String, trainSeconds: Double): Result {
        val fig = HudhudBaseline.pass(timing = false, collectConfusion = false)
        return Result(name, trainSeconds, fig, readRows(), runGuards())
    }

    private fun rowsRange(set: String): IntRange {
        val first = rows.indexOfFirst { it.set == set }
        return first..rows.indexOfLast { it.set == set }
    }

    /** Rows whose money side is the opposite of the shipped sense's reading (a row's side only where the reading names one). */
    private fun flips(r: Result, shipped: Result): List<Int> = r.readings.indices.filter { i ->
        val a = HudhudEntities.sideOfShown(r.readings[i]); val b = HudhudEntities.sideOfShown(shipped.readings[i])
        a != null && b != null && a == b.opposite
    }

    private fun printCell(title: String, rs: List<Result>, shipped: Result) {
        println("### $title")
        println()
        println("| sense | " + SERIES.joinToString(" | ") + " | wrong side | by set (held/os/o1k/D/O) | wrong amount | guard fails | side flips vs shipped | rows differing from shipped | train s |")
        println("|---|" + "---|".repeat(SERIES.size + 7))
        for (r in rs + shipped) {
            val c = SERIES.joinToString(" | ") { r.fig.total(it).first.toString() }
            val diff = r.readings.indices.count { r.readings[it] != shipped.readings[it] }
            val bySet = listOf("heldout", "owner_set", "owner_1000", "review_d", "review_o").joinToString("/") { st -> r.fig.entTotal(st, HudhudBaseline.Ent.SIDE, HudhudBaseline.Out.WRONG).toString() }
            println("| ${r.name} | $c | ${r.fig.entTotal(null, HudhudBaseline.Ent.SIDE, HudhudBaseline.Out.WRONG)} | $bySet | " +
                "${r.fig.entTotal(null, HudhudBaseline.Ent.AMOUNT, HudhudBaseline.Out.WRONG)} | ${r.guard.failed.size} | ${flips(r, shipped).size} | $diff | ${"%.0f".format(r.seconds)} |")
        }
        if (rs.size > 1) {
            println("| min..max (outer) | " + SERIES.joinToString(" | ") { s -> rs.map { it.fig.total(s).first }.let { "${it.min()}..${it.max()}" } } +
                " | " + rs.map { it.fig.entTotal(null, HudhudBaseline.Ent.SIDE, HudhudBaseline.Out.WRONG) }.let { "${it.min()}..${it.max()}" } + " | " +
                " | " + rs.map { it.fig.entTotal(null, HudhudBaseline.Ent.AMOUNT, HudhudBaseline.Out.WRONG) }.let { "${it.min()}..${it.max()}" } +
                " | " + rs.map { it.guard.failed.size }.let { "${it.min()}..${it.max()}" } + " | | | |")
            println("| mean (outer) | " + SERIES.joinToString(" | ") { s -> "%.1f".format(rs.map { it.fig.total(s).first }.average()) } + " | | | | | | | |")
        }
        println()
        if (rs.size < 2) return
        // How many rows read differently between the outer seeds: any two differ, and the mean of the 10 pairs.
        println("Rows that move between the ${rs.size} outer seeds (not all ${rs.size} read alike) / mean differing rows over all pairs:")
        println()
        println("| set | rows | moved (any) | pair mean | moved among OK/held rows* |")
        println("|---|---|---|---|---|")
        var allMoved = 0
        for (set in listOf("heldout", "owner_set", "owner_1000", "review_d", "review_o")) {
            val rg = rowsRange(set)
            val moved = rg.count { i -> rs.map { it.readings[i] }.toSet().size > 1 }
            var pairSum = 0; var pairs = 0
            for (a in rs.indices) for (b in a + 1 until rs.size) { pairs++; pairSum += rg.count { rs[a].readings[it] != rs[b].readings[it] } }
            allMoved += moved
            // the rows the regression floors hold: for the review sets the OK rows read as held; others: all rows
            println("| $set | ${rg.count()} | $moved | ${"%.1f".format(pairSum.toDouble() / pairs)} | ${heldMoved(set, rs, rg)} |")
        }
        println("| ALL | ${rows.size} | $allMoved | | |")
        println()
        println("(*) for the review sets: moved rows among those the reviewer judged OK; for held-out and owner sets the same as 'moved'.")
        println()
        val failures = rs.flatMap { it.guard.failed }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        println("Guard sentences failing under at least one of the ${rs.size} outer seeds: ${failures.size} distinct (of ${guards.size} checked)")
        failures.take(40).forEach { println("- (${it.value}/${rs.size}) ${it.key}") }
        println()
        // What a retrain really changes: rows right under the shipped sense and not under most outer seeds (losses), and the reverse (gains).
        val need = rs.size / 2 + 1
        val lost = rows.indices.filter { i -> rows[i].right(shipped.readings[i]) && rs.count { !rows[i].right(it.readings[i]) } >= need }
        val gained = rows.indices.filter { i -> !rows[i].right(shipped.readings[i]) && rs.count { rows[i].right(it.readings[i]) } >= need }
        val flaky = rows.indices.count { i -> rows[i].right(shipped.readings[i]) && rs.count { !rows[i].right(it.readings[i]) } in 1 until need }
        println("Against the shipped sense (a row counts when at least $need of ${rs.size} outer seeds agree): LOST ${lost.size} rows it read right, GAINED ${gained.size} rows it read wrong; ${flaky} more right-at-shipped rows are lost under only some seeds.")
        for (set in listOf("heldout", "owner_set", "owner_1000", "review_d", "review_o"))
            println("- $set: lost ${lost.count { rows[it].set == set }}, gained ${gained.count { rows[it].set == set }}")
        println()
        println("Lost rows (the ones a retrain must re-promote or re-class), at most 60:")
        lost.filter { rows[it].set != "heldout" }.take(60).forEach { i -> println("- [${rows[i].set}][${rows[i].lang}] ${rows[i].said}  | shipped: ${shipped.readings[i]} | now: ${rs.map { it.readings[i] }.groupingBy { it }.eachCount().entries.joinToString(" ") { "${it.key}x${it.value}" }}") }
        println()
        println("Lost held-out sentences by language: " + lost.filter { rows[it].set == "heldout" }.groupingBy { rows[it].lang }.eachCount())
        println("Gained held-out sentences by language: " + gained.filter { rows[it].set == "heldout" }.groupingBy { rows[it].lang }.eachCount())
        println()
        val flipRows = rs.flatMap { flips(it, shipped) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        println("Rows whose money side is the opposite of the shipped sense's, under at least one outer seed: ${flipRows.size}")
        flipRows.take(30).forEach { (i, n) -> println("- ($n/${rs.size}) [${rows[i].set}][${rows[i].lang}] ${rows[i].said}  shipped: ${shipped.readings[i]}; here: ${rs.map { it.readings[i] }.toSet().joinToString(" | ")}") }
        println()
        val driftSides = guards.indices.count { i -> rs.map { it.guard.sides[i] }.toSet().size > 1 }
        println("Guard sentences whose money side differs between the outer seeds: $driftSides of ${guards.size}")
        println()
    }

    private val reviewStatus: Map<String, List<Boolean>> by lazy {
        listOf("review_d", "review_o").associateWith { f -> lines("/hudhud/$f.tsv").map { it.split('\t')[1] == "OK" } }
    }

    private fun heldMoved(set: String, rs: List<Result>, rg: IntRange): String {
        val ok = reviewStatus[set] ?: return "-"
        val first = rg.first
        return rg.count { i -> ok[i - first] && rs.map { it.readings[i] }.toSet().size > 1 }.toString()
    }

    private fun perLanguage(title: String, rs: List<Result>) {
        println("Held-out strict per language (right of 130), $title:")
        println()
        println("| lang | " + rs.joinToString(" | ") { it.name } + " | spread |")
        println("|---|" + "---|".repeat(rs.size + 1))
        for (l in listOf("en", "ur-Latn", "ur", "sd", "ar", "fa", "hi", "bn", "id")) {
            val v = rs.map { r -> r.fig.rates["heldout strict"]?.let { it[l] ?: if (l == "id") it["in"] else null }?.right ?: 0 }
            println("| $l | ${v.joinToString(" | ")} | ${v.min()}..${v.max()} |")
        }
        println()
    }

    private fun sha(f: File): String = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @JvmStatic
    fun main(args: Array<String>) {
        fun arg(n: String) = args.indexOf(n).takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }
        val bank2 = arg("--bank2")?.let { f -> File(f).readLines().filter { it.isNotBlank() } }
        val k = arg("--k")?.toInt() ?: 5
        val outers = arg("--outer")?.toInt() ?: 5
        val only = arg("--only")  // e.g. "1x1,2x5": data recipe x K
        val bank1 = HudhudModelTest.bank()
        println("Run: " + java.time.LocalDateTime.now() + "; bank1 ${bank1.size} lines" + (bank2?.let { "; bank2 ${it.size} lines" } ?: "") +
            "; sense.bin sha256 " + sha(HudhudModelTest.asset()) + "; K=$k; outer seeds $outers")
        println()
        val shippedModel = HudhudModelTest.shippedModel() ?: error("sense.bin is missing")
        HudhudSense.use(shippedModel)
        System.err.println("shipped pass")
        val shipped = measure("shipped sense.bin (seed 313)", 0.0)
        println("Shipped sense check (must equal the baseline: 1059/1077/157/807/1046/1074, wrong side 4, wrong amount 0): " +
            SERIES.joinToString("/") { shipped.fig.total(it).first.toString() } + ", wrong side " +
            shipped.fig.entTotal(null, HudhudBaseline.Ent.SIDE, HudhudBaseline.Out.WRONG) + ", wrong amount " +
            shipped.fig.entTotal(null, HudhudBaseline.Ent.AMOUNT, HudhudBaseline.Out.WRONG) + "; guard sentences failing under the shipped sense: " + shipped.guard.failed.size +
            " of ${guards.size}")
        shipped.guard.failed.forEach { println("  - $it") }
        println()
        val cells = listOf("1x1" to (bank1 to 1), "1x$k" to (bank1 to k), "2x1" to (bank2 to 1), "2x$k" to (bank2 to k))
        val done = LinkedHashMap<String, List<Result>>()
        try {
            for ((cell, spec) in cells) {
                val (bank, kk) = spec
                if (bank == null) continue
                if (only != null && cell !in only.split(',')) continue
                val rs = ArrayList<Result>()
                for (o in 0 until outers) {
                    val seeds = if (kk == 1) listOf(314L + o) else List(kk) { 1000L * (o + 1) + it }
                    System.err.println("cell $cell outer $o: learning ${seeds}")
                    val t0 = System.nanoTime()
                    val m = HudhudSense.trainEnsemble(bank, seeds)
                    val sec = (System.nanoTime() - t0) / 1e9
                    HudhudSense.use(m)
                    rs += measure("o$o (${seeds.joinToString(",")})", sec)
                }
                done[cell] = rs
                val title = "data recipe ${cell[0]}, K=${cell.substring(2)}: ${if (kk == 1) "one seed per sense (314..)" else "mean of $kk seeds per sense"}, $outers outer seeds"
                printCell(title, rs, shipped)
                perLanguage(title, rs)
                System.out.flush()
            }
        } finally {
            HudhudSense.use(shippedModel)
        }
        println("Summary (outer min..max of owner_1000 / review_d / review_o / moved rows any):")
        for ((c, rs) in done) {
            val mv = rows.indices.count { i -> rs.map { it.readings[i] }.toSet().size > 1 }
            println("- $c: " + listOf("owner_set", "owner_1000", "review_d", "review_o", "heldout strict").joinToString("; ") { s -> rs.map { it.fig.total(s).first }.let { "$s ${it.min()}..${it.max()}" } } +
                "; moved rows $mv of ${rows.size}; guard fails ${rs.map { it.guard.failed.size }.let { "${it.min()}..${it.max()}" }}")
        }
    }
}
