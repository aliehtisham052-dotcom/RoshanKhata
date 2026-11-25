package com.innovation313.roshankhata.data

import java.io.File
import java.security.MessageDigest

/**
 * Hudhud's BASELINE before any retrain (stage 8, plan item H0, 24 Nov 2026): the figures of every labelled set as counts
 * (lesson 144: "N of M", never a feeling), per language, with the entity metrics, the commonest confusions, how much the
 * figures move when the sense is learnt again from the very same bank with other seeds, and how fast he reads.
 *
 * A program, not a test: it asserts nothing and writes nothing (the shipped asset is only read; the other seeds are learnt
 * in memory and dropped). Run it with scripts/hudhud_baseline.sh. It reads the sets exactly as their own tests do
 * (HudhudSenseTest, HudhudOwnerSetTest, HudhudReviewSetTest, HudhudReviewOpusSetTest, HudhudEntityMetricsTest) and the entity
 * scoring below is that test's `score`, copied (its Tally is private); if that test changes, this copy is measured again.
 * Names and goods are the invented ones of those tests.
 */
object HudhudBaseline {

    // --- the books the set tests use (copied from them: one Latin spelling for the owner's and review sets, the nine-script
    // --- spellings for the held-out set)
    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee")
        .mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
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
    private val peopleWritten: Map<String, Long> = names.associate { it.name to it.id } +
        listOf("علی رضا", "علي رضا", "अली रज़ा", "আলী রাজা").associateWith { 1L }
    private val goodsWritten: Map<String, Long> = goods.associate { it.name to it.id } +
        listOf("یوریا", "يوريا", "यूरिया", "ইউরিয়া").associateWith { 100L }

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

    private fun heldLabel(a: Hudhud.Ask): String? = when (a) {
        is Hudhud.Ask.Guessed -> heldLabel(a.ask)
        is Hudhud.Ask.NeedName -> "needs_name"
        else -> Hudhud.labelOf(a)
    }

    /** HudhudSenseTest.close: readings that answer a held-out sentence as well as its own label would. */
    private fun close(expected: String, got: String?): Boolean = expected == got ||
        (expected == "help_howto" && got == "abilities") ||
        (expected == "who_are_you" && got == "abilities") ||
        (expected == "hello" && (got == "address" || got == "salaam")) ||
        (expected == "address" && got == "hello") ||
        (expected == "today_summary" && got == "activity") ||
        (expected == "sales_today" && got == "sales_period") ||
        (expected == "party_details" && got == "party_balance")

    private val heldSide = mapOf(
        "entry_gave" to HudhudEntities.Side.OUT, "entry_got" to HudhudEntities.Side.IN, "collected" to HudhudEntities.Side.IN,
        "to_get" to HudhudEntities.Side.RECEIVE, "who_owes_most" to HudhudEntities.Side.RECEIVE,
        "not_paid" to HudhudEntities.Side.RECEIVE, "to_give" to HudhudEntities.Side.PAY,
    )

    // --- the data
    private fun lines(resource: String): List<String> =
        HudhudBaseline::class.java.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }

    private class Held(val lang: String, val label: String, val text: String)
    private class Owner(val set: String, val id: String, val said: String, val ok: List<String>)
    private class Review(val set: String, val lang: String, val status: String, val said: String, val prev: String?, val held: String)

    private val heldRows: List<Held> by lazy {
        lines("/hudhud/heldout.tsv").map { val (l, m, t) = it.split('\t', limit = 3); Held(l, m, t) }
    }
    private val ownerRows: List<Owner> by lazy {
        lines("/hudhud/owner_set.tsv").map { val r = it.split('\t'); Owner("owner_set", r[0], r[1], r.last().split('|')) } +
            lines("/hudhud/owner_1000.tsv").map { val r = it.split('\t'); Owner("owner_1000", r[0], r[1], r.last().split('|')) }
    }
    private val reviewRows: List<Review> by lazy {
        listOf("review_d", "review_o").flatMap { s ->
            lines("/hudhud/$s.tsv").map { it.split('\t') }
                .map { Review(s, it[0], it[1], it[2], it.getOrNull(3)?.takeIf { p -> p.isNotBlank() }, it[4]) }
        }
    }

    private val ownerTopics: List<Hudhud.Topic> by lazy { HudhudRealPages.topics(listOf("values-b+ur+Latn", "values", "values-ur")) }
    private val pagesByLang = HashMap<String, List<Hudhud.Topic>>()
    private fun pagesFor(lang: String) = pagesByLang.getOrPut(lang) { HudhudRealPages.forApp(lang) }

    private fun disp(lang: String) = if (lang == "in") "id" else lang

    // --- the tallies
    class Rate { var right = 0; var total = 0; fun add(ok: Boolean) { total++; if (ok) right++ } }
    enum class Ent { SIDE, AMOUNT, PERSON, PRODUCT, DAYS }
    enum class Out { CORRECT, WRONG, CHOICE, MISSING, OTHERWISE }

    /** One full pass over every set with whatever sense is in use. */
    class Figures {
        val rates = LinkedHashMap<String, LinkedHashMap<String, Rate>>()          // series -> language -> rate
        val ent = LinkedHashMap<Triple<String, String, Ent>, IntArray>()           // (set, lang, entity) -> counts by Out
        val confusion = HashMap<Pair<String, String>, Int>()                       // expected -> read (owner and review sets)
        val heldConfusion = HashMap<Pair<String, String>, Int>()                   // held-out labels
        val millis = LinkedHashMap<String, ArrayList<Double>>()                    // set -> ms per question
        val reviewOk = HashMap<String, IntArray>()                                 // set -> [ok rows, ok rows now read the same]
        fun rate(series: String, lang: String) = rates.getOrPut(series) { LinkedHashMap() }.getOrPut(lang) { Rate() }
        fun total(series: String): Pair<Int, Int> {
            val m = rates[series] ?: return 0 to 0
            return m.values.sumOf { it.right } to m.values.sumOf { it.total }
        }
        fun entTotal(set: String?, e: Ent, o: Out): Int =
            ent.filterKeys { (set == null || it.first == set) && it.third == e }.values.sumOf { it[o.ordinal] }
    }

    private class Expect(
        val sides: Set<HudhudEntities.Side> = emptySet(), val amount: Double? = null,
        val people: Set<Long> = emptySet(), val goods: Set<Long> = emptySet(), val window: VoiceQuestion.Window? = null,
    )

    /** HudhudEntityMetricsTest.score, copied: each entity compared alone. */
    private fun score(f: Figures, set: String, lang: String, got: Hudhud.Ask, accepted: Boolean, x: Expect) {
        val c = HudhudEntities.of(got)
        fun none() = if (accepted) Out.OTHERWISE else Out.MISSING
        fun add(e: Ent, o: Out) { f.ent.getOrPut(Triple(set, disp(lang), e)) { IntArray(Out.values().size) }[o.ordinal]++ }
        if (x.sides.isNotEmpty()) add(Ent.SIDE, when {
            c.sideAsked -> Out.CHOICE
            x.sides.any { it.opposite in c.sides } -> Out.WRONG
            x.sides.any { it in c.sides } -> Out.CORRECT
            else -> none()
        })
        x.amount?.let { want ->
            add(Ent.AMOUNT, when {
                c.amounts.isEmpty() -> none()
                c.amounts.any { Math.abs(it - want) < 0.005 } -> Out.CORRECT
                else -> Out.WRONG
            })
        }
        fun ids(e: Ent, want: Set<Long>, have: Set<Long>) {
            if (want.isEmpty()) return
            add(e, when {
                have.isEmpty() -> none()
                want.containsAll(have) -> Out.CORRECT
                have.any { it in want } -> Out.CHOICE
                else -> Out.WRONG
            })
        }
        ids(Ent.PERSON, x.people, c.people)
        ids(Ent.PRODUCT, x.goods, c.goods)
        x.window?.let { want ->
            add(Ent.DAYS, when {
                c.windows.isEmpty() -> none()
                want in c.windows -> Out.CORRECT
                else -> Out.WRONG
            })
        }
    }

    private fun expectWritten(said: String, sides: Set<HudhudEntities.Side>, amount: Double?, window: VoiceQuestion.Window?) = Expect(
        sides = sides, amount = amount, window = window,
        people = HudhudEntities.written(said, peopleWritten).let { if (it.size == 1) it else emptySet() },
        goods = HudhudEntities.written(said, goodsWritten).let { if (it.size == 1) it else emptySet() },
    )

    private fun sidesOf(readings: List<String>): Set<HudhudEntities.Side> {
        val s = readings.mapNotNull { HudhudEntities.sideOfShown(it) }.toSet()
        return if (s.any { it.opposite in s }) emptySet() else s
    }

    private fun windowOf(readings: List<String>): VoiceQuestion.Window? =
        readings.mapNotNull { HudhudEntities.windowOfShown(it) }.toSet().singleOrNull()

    /** Times [block] in milliseconds into the set's list when [f] collects times. */
    private inline fun <T> timed(f: Figures, timing: Boolean, set: String, block: () -> T): T {
        if (!timing) return block()
        val t0 = System.nanoTime()
        val r = block()
        f.millis.getOrPut(set) { ArrayList() }.add((System.nanoTime() - t0) / 1e6)
        return r
    }

    fun pass(timing: Boolean, collectConfusion: Boolean): Figures {
        val f = Figures()
        // held-out
        for (h in heldRows) {
            val said = heldFilled(h.lang, h.text)
            val got = timed(f, timing, "heldout") { Hudhud.understand(said, heldNames, heldTopics, h.lang, heldGoods) }
            val label = heldLabel(got)
            f.rate("heldout strict", h.lang).add(label == h.label)
            f.rate("heldout lenient", h.lang).add(close(h.label, label))
            if (collectConfusion && label != h.label) {
                val k = h.label to (label ?: "(none)")
                f.heldConfusion[k] = (f.heldConfusion[k] ?: 0) + 1
            }
            score(f, "heldout", h.lang, got, label == h.label, Expect(
                sides = setOfNotNull(heldSide[h.label]),
                amount = if (h.label.startsWith("entry_")) HudhudEntities.digitAmount(said) else null,
                people = if ("{NAME}" in h.text) (1L..5L).toSet() else emptySet(),
                goods = if ("{ITEM}" in h.text) (101L..105L).toSet() else emptySet(),
            ))
        }
        // the owner's lists (no language column)
        for (o in ownerRows) {
            val got = timed(f, timing, o.set) { Hudhud.understand(o.said, names, ownerTopics, null, goods) }
            val shown = HudhudShown.of(got)
            val right = o.ok.any { shown.startsWith(it) }
            f.rate(o.set, "owner").add(right)
            if (collectConfusion && !right) {
                val k = o.ok.joinToString("|") to shown
                f.confusion[k] = (f.confusion[k] ?: 0) + 1
            }
            score(f, o.set, "owner", got, right, expectWritten(o.said, sidesOf(o.ok), null, windowOf(o.ok)))
        }
        // the review sets: right = judged OK and still read exactly as held
        for (r in reviewRows) {
            val topics = pagesFor(r.lang)
            val before = r.prev?.let { Hudhud.understand(it, names, topics, r.lang, goods, null) }
            val got = timed(f, timing, r.set) { Hudhud.understand(r.said, names, topics, r.lang, goods, before) }
            val shown = HudhudShown.of(got)
            val ok = r.status == "OK"
            val same = ok && shown == r.held
            f.rate(r.set, r.lang).add(same)
            val c = f.reviewOk.getOrPut(r.set) { IntArray(2) }
            if (ok) { c[0]++; if (same) c[1]++ }
            if (collectConfusion && ok && !same) {
                val k = r.held to shown
                f.confusion[k] = (f.confusion[k] ?: 0) + 1
            }
            score(f, r.set, r.lang, got, same, expectWritten(
                r.said,
                sides = if (ok) sidesOf(listOf(r.held)) else emptySet(),
                amount = if (ok && r.held == "Entry") HudhudEntities.digitAmount(r.said) else null,
                window = if (ok) windowOf(listOf(r.held)) else null,
            ))
        }
        return f
    }

    // --- the report
    private fun pct(r: Int, t: Int) = if (t == 0) "-" else String.format("%.1f%%", 100.0 * r / t)
    private fun cell(r: Rate?) = if (r == null || r.total == 0) "-" else "${r.right}/${r.total} ${pct(r.right, r.total)}"
    private val LANGS = listOf("en", "ur-Latn", "ur", "sd", "ar", "fa", "hi", "bn", "id")

    private fun langRate(f: Figures, series: String, lang: String): Rate? {
        val m = f.rates[series] ?: return null
        return m[lang] ?: if (lang == "id") m["in"] else null
    }

    private fun printAccuracy(f: Figures) {
        println("### Accuracy per set and language (right/total, shipped sense, seed ${HudhudSense.SEED})")
        println()
        println("| set | " + LANGS.joinToString(" | ") + " | ALL |")
        println("|---|" + "---|".repeat(LANGS.size + 1))
        val series = listOf("heldout strict", "heldout lenient", "owner_set", "owner_1000", "review_d", "review_o")
        for (s in series) {
            val (r, t) = f.total(s)
            val owner = s.startsWith("owner")
            val cells = LANGS.joinToString(" | ") { l -> if (owner) (if (l == "ur-Latn") cell(f.rates[s]?.get("owner")) else "-") else cell(langRate(f, s, l)) }
            println("| $s | $cells | $r/$t ${pct(r, t)} |")
        }
        println()
        for (s in listOf("review_d", "review_o")) {
            val c = f.reviewOk[s] ?: continue
            val (r, t) = f.total(s)
            println("- $s: $t rows; ${c[0]} judged OK by the reviewer, ${c[1]} of those still read exactly as held; the other ${t - c[0]} are MISS/GUESS/NOW rows (counted as not right here, even if the reading changed).")
        }
    }

    private fun printEntities(f: Figures) {
        println("### Entity metrics (correct/wrong/choice/missing/otherwise; - = not labelled)")
        println()
        val sets = listOf("heldout", "owner_set", "owner_1000", "review_d", "review_o")
        println("| set | side | amount | person | product | days |")
        println("|---|---|---|---|---|---|")
        for (s in sets) {
            val row = Ent.values().joinToString(" | ") { e ->
                val a = IntArray(Out.values().size)
                f.ent.filterKeys { it.first == s && it.third == e }.values.forEach { v -> for (i in a.indices) a[i] += v[i] }
                if (a.sum() == 0) "-" else a.joinToString("/")
            }
            println("| $s | $row |")
        }
        println()
        println("Per language, all sets pooled (correct/wrong/choice/missing/otherwise):")
        println()
        println("| lang | side | amount | person | product | days |")
        println("|---|---|---|---|---|---|")
        for (l in LANGS + "owner") {
            val row = Ent.values().joinToString(" | ") { e ->
                val a = IntArray(Out.values().size)
                f.ent.filterKeys { it.second == l && it.third == e }.values.forEach { v -> for (i in a.indices) a[i] += v[i] }
                if (a.sum() == 0) "-" else a.joinToString("/")
            }
            println("| $l | $row |")
        }
    }

    private fun printConfusion(f: Figures) {
        println("### Confusions, top 20 (owner lists + review OK rows: expected reading -> read; held-out in its own table)")
        println()
        println("| n | expected | read |")
        println("|---|---|---|")
        f.confusion.entries.sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { it.key.first + it.key.second })
            .take(20).forEach { (k, n) -> println("| $n | ${k.first.replace("|", "\\|")} | ${k.second} |") }
        println("(distinct pairs: ${f.confusion.size}; misses pooled: ${f.confusion.values.sum()})")
        println()
        println("Held-out, label -> label read, top 12:")
        println()
        println("| n | expected | read |")
        println("|---|---|---|")
        f.heldConfusion.entries.sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { it.key.first + it.key.second })
            .take(12).forEach { (k, n) -> println("| $n | ${k.first} | ${k.second} |") }
        println("(distinct pairs: ${f.heldConfusion.size}; strict misses: ${f.heldConfusion.values.sum()})")
    }

    private fun printSpeed(f: Figures) {
        println("### Reading speed (ms per Hudhud.understand call, shipped sense, after a warm-up of 600 questions)")
        println()
        println("| set | n | median | p95 | max |")
        println("|---|---|---|---|---|")
        fun row(name: String, v: List<Double>) {
            val s = v.sorted()
            fun q(p: Double) = s[minOf(s.size - 1, (p * s.size).toInt())]
            println(String.format("| %s | %d | %.2f | %.2f | %.1f |", name, s.size, q(0.5), q(0.95), s.last()))
        }
        f.millis.forEach { (k, v) -> row(k, v) }
        row("ALL", f.millis.values.flatten())
    }

    private fun printSeeds(rows: List<Pair<Long, Figures>>) {
        println("### Seed noise: the sense learnt again from the same bank (in memory only), right/total")
        println()
        val series = listOf("heldout strict", "heldout lenient", "owner_set", "owner_1000", "review_d", "review_o")
        println("| seed | " + series.joinToString(" | ") + " | wrong side | wrong amount |")
        println("|---|" + "---|".repeat(series.size + 2))
        for ((seed, f) in rows) {
            val cells = series.joinToString(" | ") { s -> f.total(s).first.toString() }
            println("| $seed${if (seed == HudhudSense.SEED) " (shipped)" else ""} | $cells | ${f.entTotal(null, Ent.SIDE, Out.WRONG)} | ${f.entTotal(null, Ent.AMOUNT, Out.WRONG)} |")
        }
        println("| min..max | " + series.joinToString(" | ") { s ->
            val v = rows.map { it.second.total(s).first }; "${v.min()}..${v.max()}"
        } + " | " + rows.map { it.second.entTotal(null, Ent.SIDE, Out.WRONG) }.let { "${it.min()}..${it.max()}" } + " | " +
            rows.map { it.second.entTotal(null, Ent.AMOUNT, Out.WRONG) }.let { "${it.min()}..${it.max()}" } + " |")
        println("| totals | " + series.joinToString(" | ") { s -> rows.first().second.total(s).second.toString() } + " | | |")
        println()
        println("Per language, held-out strict (right of 130) across the seeds:")
        println()
        println("| lang | " + rows.joinToString(" | ") { it.first.toString() } + " | spread |")
        println("|---|" + "---|".repeat(rows.size + 1))
        for (l in LANGS) {
            val v = rows.map { langRate(it.second, "heldout strict", l)?.right ?: 0 }
            println("| $l | ${v.joinToString(" | ")} | ${v.min()}..${v.max()} |")
        }
        // Rows that change reading between the shipped sense and another seed, in the review and owner sets: counted via the
        // pass itself (the figures above); the per-row diff is not kept.
    }

    private fun sha(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @JvmStatic
    fun main(args: Array<String>) {
        val seedsOnly = args.contains("--no-seeds")
        System.err.println("load: " + File("/proc/loadavg").takeIf { it.exists() }?.readText()?.trim())
        val shipped = HudhudModelTest.shippedModel() ?: error("sense.bin is missing")
        HudhudSense.use(shipped)
        val bank = HudhudModelTest.bank()
        println("Run: " + java.time.LocalDateTime.now() + "; bank ${bank.size} lines; sense.bin sha256 " + sha(HudhudModelTest.asset()))
        println("Load average at start: " + File("/proc/loadavg").takeIf { it.exists() }?.readText()?.trim() + "; processors ${Runtime.getRuntime().availableProcessors()}")
        println()

        // warm-up, then the measured pass with the shipped sense
        System.err.println("warm-up")
        heldRows.take(600).forEach { Hudhud.understand(heldFilled(it.lang, it.text), heldNames, heldTopics, it.lang, heldGoods) }
        System.err.println("shipped pass")
        val shippedFigures = pass(timing = true, collectConfusion = true)
        printAccuracy(shippedFigures)
        println()
        printEntities(shippedFigures)
        println()
        printConfusion(shippedFigures)
        println()
        printSpeed(shippedFigures)
        println()

        if (!seedsOnly) {
            val rows = ArrayList<Pair<Long, Figures>>()
            rows += HudhudSense.SEED to shippedFigures
            try {
                for (seed in listOf(314L, 315L, 316L, 317L, 318L)) {
                    System.err.println("seed $seed: learning")
                    HudhudSense.use(HudhudSense.train(bank, seed))
                    rows += seed to pass(timing = false, collectConfusion = false)
                }
            } finally {
                HudhudSense.use(shipped)
            }
            printSeeds(rows)
        }
    }
}
