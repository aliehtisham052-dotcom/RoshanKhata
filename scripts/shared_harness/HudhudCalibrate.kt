package com.innovation313.roshankhata.data

import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The certainty gates of [Hudhud.decide], made settable per meaning for a MEASUREMENT only (calibration, plan Marhala 3).
 * scripts/shared_harness/patch_gates.py writes a copy of Hudhud.kt into the build folder in which each `p >= <gate>` of decide
 * reads `p >= HudhudCalibGate.at(top.label, "<GATE>", <gate>)`. With no [rules] the value comes back unchanged, so the copy
 * reads exactly as the app does. The app never compiles this file.
 */
object HudhudCalibGate {
    /** [turn]: replaces a gate of 0.90 or more (the gates that let the sense TURN a reading); [floor]: no gate under it. */
    class Rule(val floor: Double? = null, val turn: Double? = null)

    const val TURN_GATE = 0.90

    @Volatile
    var rules: Map<String, Rule> = emptyMap()

    @JvmStatic
    fun at(label: String, gate: String, today: Double): Double {
        val r = rules[label] ?: return today
        var t = today
        if (r.turn != null && today >= TURN_GATE) t = r.turn
        if (r.floor != null) t = maxOf(t, r.floor)
        return t
    }
}

/**
 * Hudhud's CALIBRATION (plan Marhala 3 and 12, lessons 107, 163, 165): how often the learnt sense is right at each certainty,
 * measured on sentences it did not learn from, and the lowest certainty at which each meaning reaches a target precision.
 * A program, not a test: it asserts nothing and writes nothing of the repository (senses are learnt in memory; sense.bin is
 * only read). Run it with scripts/hudhud_calibrate.sh, which writes the folds and the gate copy first.
 *
 * 1. Out-of-fold (OOF): the bank's 13,374 lines in five folds by paraphrase family (scripts/hudhud_calib_folds.py: every form
 *    of one sentence, in every script, in one fold). Each fold is read by a sense learnt from the other four with the SHIPPED
 *    recipe ([HudhudSense.trainEnsemble] with [HudhudSense.SHIPPED_SEEDS] and [HudhudSense.SHIPPED_PRUNE]) and, on request,
 *    with other 15-seed ensembles (lesson 163: two more seeds, so the shuffle's part is seen).
 * 2. Held-out (dev data, lesson 107): the 1,170 held-out sentences read by the shipped sense.bin.
 * 3. A reliability table (certainty bins aligned with decide's gates), the expected calibration error, and per meaning the
 *    lowest certainty from which the share right stays at or above the target (0.98 for a money side, 0.95 otherwise).
 * 4. Policies, measured end to end through the gate copy on every labelled set (counts, wrong sides, rows that change).
 *
 * What the sense reads here is its own reading of the sentence with the bank's placeholders ({NAME} -> qqname, {ITEM} ->
 * qqitem), the reading decide gets when the rules found the name: the sense's calibration, not the whole pipeline's.
 */
object HudhudCalibrate {

    // ------------------------------------------------------------------------------------------ the meanings that move money
    val MONEY = listOf("to_get", "to_give", "collected", "entry_gave", "entry_got", "who_owes_most", "not_paid")
    private val SIDE = mapOf(
        "to_get" to "RECEIVE", "who_owes_most" to "RECEIVE", "not_paid" to "RECEIVE", "to_give" to "PAY",
        "collected" to "IN", "entry_got" to "IN", "entry_gave" to "OUT",
    )
    private val OPPOSITE = mapOf("RECEIVE" to "PAY", "PAY" to "RECEIVE", "IN" to "OUT", "OUT" to "IN")
    private fun oppositeSide(truth: String, read: String): Boolean {
        val a = SIDE[truth] ?: return false
        val b = SIDE[read] ?: return false
        return OPPOSITE[a] == b
    }

    /** decide's gates today (Hudhud.kt, c870984). */
    private val TODAY = listOf(
        "SENSE_MAYBE" to Hudhud.SENSE_MAYBE, "HALF (inline)" to 0.5, "SENSE_SURE" to Hudhud.SENSE_SURE,
        "SENSE_CERTAIN" to Hudhud.SENSE_CERTAIN, "SENSE_OVERRULE" to Hudhud.SENSE_OVERRULE, "SENSE_FIRM_TURN" to Hudhud.SENSE_FIRM_TURN,
    )

    /** Bins whose edges are decide's own gates (and the "68% at 0.95-0.97" band of lesson 165). */
    private val EDGES = doubleArrayOf(0.0, 0.30, 0.50, 0.60, 0.80, 0.90, 0.92, 0.95, 0.97, 0.98, 0.99, 0.995, 1.0000001)

    class Pred(val lang: String, val truth: String, val top: String, val p: Double, val second: Double) {
        val right get() = truth == top
    }

    private fun placeholders(text: String) = text.replace("{NAME}", " ${HudhudSense.NAME} ").replace("{ITEM}", " ${HudhudSense.ITEM} ")

    private fun predict(lang: String, truth: String, text: String): Pred {
        val g = HudhudSense.rank(placeholders(text), top = 2)
        return Pred(lang, truth, g.getOrNull(0)?.label ?: "(none)", g.getOrNull(0)?.score ?: 0.0, g.getOrNull(1)?.score ?: 0.0)
    }

    // ------------------------------------------------------------------------------------------ the measures
    private fun pct(r: Int, n: Int) = if (n == 0) "-" else "%.1f%%".format(100.0 * r / n)

    private fun wilsonLow(r: Int, n: Int, z: Double = 1.96): Double {
        if (n == 0) return 0.0
        val ph = r.toDouble() / n
        val d = 1 + z * z / n
        val c = ph + z * z / (2 * n)
        val s = z * sqrt(ph * (1 - ph) / n + z * z / (4.0 * n * n))
        return (c - s) / d
    }

    /** Expected calibration error over [edges]: sum over bins of |share right - mean certainty| x share of rows. */
    private fun ece(ps: List<Pred>, edges: DoubleArray): Double {
        if (ps.isEmpty()) return 0.0
        var e = 0.0
        for (b in 0 until edges.size - 1) {
            val inBin = ps.filter { it.p >= edges[b] && it.p < edges[b + 1] }
            if (inBin.isEmpty()) continue
            val acc = inBin.count { it.right }.toDouble() / inBin.size
            val conf = inBin.sumOf { it.p } / inBin.size
            e += abs(acc - conf) * inBin.size / ps.size
        }
        return e
    }

    private val EQUAL15 = DoubleArray(16) { if (it == 15) 1.0000001 else it / 15.0 }

    private fun reliability(title: String, ps: List<Pred>) {
        println("#### $title")
        println()
        println("| certainty | rows | right | share right | mean certainty | gap | money-side rows | money right | opposite side |")
        println("|---|---|---|---|---|---|---|---|---|")
        for (b in 0 until EDGES.size - 1) {
            val inBin = ps.filter { it.p >= EDGES[b] && it.p < EDGES[b + 1] }
            val r = inBin.count { it.right }
            val conf = if (inBin.isEmpty()) 0.0 else inBin.sumOf { it.p } / inBin.size
            val money = inBin.filter { it.top in MONEY }
            val hi = if (EDGES[b + 1] > 1.0) "1.000" else "%.3f".format(EDGES[b + 1])
            println("| %.3f-%s | %d | %d | %s | %s | %s | %d | %s | %d |".format(
                EDGES[b], hi, inBin.size, r, pct(r, inBin.size), if (inBin.isEmpty()) "-" else "%.3f".format(conf),
                if (inBin.isEmpty()) "-" else "%+.3f".format(r.toDouble() / inBin.size - conf),
                money.size, pct(money.count { it.right }, money.size), money.count { oppositeSide(it.truth, it.top) }))
        }
        val r = ps.count { it.right }
        println("| all | ${ps.size} | $r | ${pct(r, ps.size)} | ${"%.3f".format(ps.sumOf { it.p } / ps.size)} | | ${ps.count { it.top in MONEY }} | " +
            "${pct(ps.count { it.top in MONEY && it.right }, ps.count { it.top in MONEY })} | ${ps.count { it.top in MONEY && oppositeSide(it.truth, it.top) }} |")
        println()
        println("ECE (15 equal bins): %.4f; ECE (the gate bins above): %.4f. Gap = share right - mean certainty (negative: over-sure).".format(
            ece(ps, EQUAL15), ece(ps, EDGES)))
        println()
    }

    /** Share right among rows of [label] read at [t] or more: (right, n). */
    private fun at(ps: List<Pred>, label: String, t: Double): Pair<Int, Int> {
        val s = ps.filter { it.top == label && it.p >= t }
        return s.count { it.right } to s.size
    }

    /**
     * The lowest certainty t (one of the rows' own certainties, from SENSE_MAYBE up) such that, for t and EVERY higher cut that still
     * holds at least [minN] rows of [label], the share right is at least [target]. Null when no cut of [minN] rows reaches it.
     */
    fun lowestCertainty(ps: List<Pred>, label: String, target: Double, minN: Int): Double? {
        val s = ps.filter { it.top == label && it.p >= Hudhud.SENSE_MAYBE }.sortedByDescending { it.p }
        if (s.size < minN) return null
        // Walk from the surest down: cumulative right / n at each cut (ties kept together).
        var best: Double? = null
        var right = 0
        var i = 0
        var everBelow = false
        while (i < s.size) {
            val p = s[i].p
            var j = i
            while (j < s.size && s[j].p == p) { if (s[j].right) right++; j++ }
            val n = j
            if (n >= minN) {
                if (right.toDouble() / n >= target && !everBelow) best = p else everBelow = true
            }
            i = j
        }
        return best
    }

    class Proposal(val label: String, val target: Double, val t: Double?, val n: Int, val right: Int)

    private fun fmt(rn: Pair<Int, Int>) = if (rn.second == 0) "-" else "${pct(rn.first, rn.second)} (${rn.second})"

    private fun classTable(oof: List<Pred>, held: List<Pred>, minN: Int, moneyTarget: Double, otherTarget: Double): List<Proposal> {
        val labels = (MONEY + oof.map { it.truth }.distinct().sorted().filter { it !in MONEY })
        val out = ArrayList<Proposal>()
        println("| meaning | target | OOF read as it | OOF right | @0.60 | @0.92 | @0.95 | @0.99 | **proposed t** | OOF @t | Wilson low @t | opposite side @t (OOF) | held-out @t | held-out @0.95 | held-out @0.99 | opposite side @t (held-out) |")
        println("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (l in labels) {
            val target = if (l in MONEY) moneyTarget else otherTarget
            val all = oof.filter { it.top == l }
            val t = lowestCertainty(oof, l, target, minN)
            val atT = t?.let { at(oof, l, it) } ?: (0 to 0)
            val opp = t?.let { tt -> oof.count { it.top == l && it.p >= tt && oppositeSide(it.truth, it.top) } }
            val hOpp = t?.let { tt -> held.count { it.top == l && it.p >= tt && oppositeSide(it.truth, it.top) } }
            println("| ${if (l in MONEY) "**$l**" else l} | $target | ${all.size} | ${pct(all.count { it.right }, all.size)} | ${fmt(at(oof, l, 0.60))} | " +
                "${fmt(at(oof, l, 0.92))} | ${fmt(at(oof, l, 0.95))} | ${fmt(at(oof, l, 0.99))} | ${t?.let { "%.4f".format(it) } ?: "not reached"} | " +
                "${fmt(atT)} | ${if (atT.second > 0) "%.3f".format(wilsonLow(atT.first, atT.second)) else "-"} | ${opp ?: "-"} | " +
                "${t?.let { fmt(at(held, l, it)) } ?: "-"} | ${fmt(at(held, l, 0.95))} | ${fmt(at(held, l, 0.99))} | ${hOpp ?: "-"} |")
            out += Proposal(l, target, t, atT.second, atT.first)
        }
        println()
        return out
    }

    // ------------------------------------------------------------------------------------------ end to end, through the gate copy
    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders")
        .mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
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
        HudhudCalibrate::class.java.getResourceAsStream(resource)?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }

    /** A labelled row of the five sets, read as HudhudRecipe reads them; [right] judges a reading, [sides] the sides it expects. */
    private class Row(val set: String, val lang: String, val said: String, val prev: String?, val right: (String) -> Boolean, val sides: Set<String>)

    private fun sideOfShown(s: String): String? = HudhudEntities.sideOfShown(s)?.name

    private val rows: List<Row> by lazy {
        val out = ArrayList<Row>()
        val heldSide = mapOf("entry_gave" to "OUT", "entry_got" to "IN", "collected" to "IN", "to_get" to "RECEIVE",
            "who_owes_most" to "RECEIVE", "not_paid" to "RECEIVE", "to_give" to "PAY")
        for (l in lines("/hudhud/heldout.tsv")) {
            val (lang, label, t) = l.split('\t', limit = 3)
            out += Row("heldout", lang, heldFilled(lang, t), null, { it == label }, setOfNotNull(heldSide[label]))
        }
        for (f in listOf("owner_set", "owner_1000")) for (l in lines("/hudhud/$f.tsv")) {
            val r = l.split('\t'); val ok = r.last().split('|')
            val s = ok.mapNotNull { sideOfShown(it) }.toSet().let { s -> if (s.any { OPPOSITE[it] in s }) emptySet() else s }
            out += Row(f, "owner", r[1], null, { shown -> ok.any { shown.startsWith(it) } }, s)
        }
        for (f in listOf("review_d", "review_o")) for (l in lines("/hudhud/$f.tsv")) {
            val r = l.split('\t'); val ok = r[1] == "OK"; val held = r[4]
            out += Row(f, r[0], r[2], r.getOrNull(3)?.takeIf { it.isNotBlank() }, { shown -> ok && shown == held },
                if (ok) setOfNotNull(sideOfShown(held)) else emptySet())
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

    private fun readRows(): List<String> = rows.map { r ->
        when (r.set) {
            "heldout" -> shownOf(Hudhud.understand(r.said, heldNames, heldTopics, r.lang, heldGoods))
            "owner_set", "owner_1000" -> HudhudShown.of(Hudhud.understand(r.said, names, ownerTopics, null, goods))
            else -> {
                val tp = pages(r.lang)
                val before = r.prev?.let { Hudhud.understand(it, names, tp, r.lang, goods, null) }
                HudhudShown.of(Hudhud.understand(r.said, names, tp, r.lang, goods, before))
            }
        }
    }

    /** A reading on the other side of the money from what the row expects (HudhudEntities' sides; held-out's own map). */
    private fun wrongSide(r: Row, shown: String): Boolean {
        if (r.sides.isEmpty()) return false
        val s = if (r.set == "heldout") SIDE[shown] ?: sideOfShown(shown) else sideOfShown(shown)
        return s != null && r.sides.any { OPPOSITE[it] == s }
    }

    private class Run(val name: String, val fig: HudhudBaseline.Figures, val readings: List<String>, val seconds: Double)

    private val SETS = listOf("heldout", "owner_set", "owner_1000", "review_d", "review_o")
    private val SERIES = listOf("heldout strict", "heldout lenient", "owner_set", "owner_1000", "review_d", "review_o")

    private fun runPolicy(name: String, rules: Map<String, HudhudCalibGate.Rule>): Run {
        HudhudCalibGate.rules = rules
        val t0 = System.nanoTime()
        try {
            System.err.println("policy $name: figures")
            val fig = HudhudBaseline.pass(timing = false, collectConfusion = false)
            System.err.println("policy $name: rows")
            return Run(name, fig, readRows(), (System.nanoTime() - t0) / 1e9)
        } finally {
            HudhudCalibGate.rules = emptyMap()
        }
    }

    private fun policies(props: List<Proposal>, shipped: HudhudSense.Model) {
        HudhudSense.use(shipped)
        val never = 1.01
        val byLabel = props.associateBy { it.label }
        val p1 = MONEY.associateWith { HudhudCalibGate.Rule(floor = byLabel[it]?.t ?: never) }
        val p2 = props.associate { p ->
            p.label to HudhudCalibGate.Rule(turn = p.t ?: never, floor = if (p.label in MONEY) (p.t ?: never) else null)
        }
        val runs = listOf(
            runPolicy("P0 today (no rule: must equal the baseline)", emptyMap()),
            runPolicy("P1 money floor: every gate >= the money meaning's t", p1),
            runPolicy("P2 per-meaning turn: gates >= 0.90 become t; money floors as P1", p2),
        )
        println("| policy | " + SERIES.joinToString(" | ") + " | wrong side (all) | by set (held/os/o1k/D/O) | wrong amount | rows changed vs P0 | right->wrong | wrong->right | s |")
        println("|---|" + "---|".repeat(SERIES.size + 7))
        val base = runs[0]
        for (r in runs) {
            val changed = rows.indices.filter { r.readings[it] != base.readings[it] }
            val lost = changed.count { rows[it].right(base.readings[it]) && !rows[it].right(r.readings[it]) }
            val won = changed.count { !rows[it].right(base.readings[it]) && rows[it].right(r.readings[it]) }
            val bySet = SETS.joinToString("/") { s -> r.fig.entTotal(s, HudhudBaseline.Ent.SIDE, HudhudBaseline.Out.WRONG).toString() }
            println("| ${r.name} | " + SERIES.joinToString(" | ") { r.fig.total(it).first.toString() } +
                " | ${r.fig.entTotal(null, HudhudBaseline.Ent.SIDE, HudhudBaseline.Out.WRONG)} | $bySet | " +
                "${r.fig.entTotal(null, HudhudBaseline.Ent.AMOUNT, HudhudBaseline.Out.WRONG)} | ${changed.size} | $lost | $won | ${"%.0f".format(r.seconds)} |")
        }
        println("| totals | " + SERIES.joinToString(" | ") { base.fig.total(it).second.toString() } + " | | | | | | | |")
        println()
        for (r in runs.drop(1)) {
            println("#### ${r.name}: rows that read differently from P0")
            println()
            for (s in SETS) {
                val ch = rows.indices.filter { rows[it].set == s && r.readings[it] != base.readings[it] }
                val lost = ch.count { rows[it].right(base.readings[it]) && !rows[it].right(r.readings[it]) }
                val won = ch.count { !rows[it].right(base.readings[it]) && rows[it].right(r.readings[it]) }
                val sideFix = ch.count { wrongSide(rows[it], base.readings[it]) && !wrongSide(rows[it], r.readings[it]) }
                val sideNew = ch.count { !wrongSide(rows[it], base.readings[it]) && wrongSide(rows[it], r.readings[it]) }
                println("- $s: ${ch.size} changed; right->wrong $lost, wrong->right $won; wrong money side removed $sideFix, added $sideNew")
            }
            println()
            val listed = rows.indices.filter { r.readings[it] != base.readings[it] }
                .sortedBy { SETS.indexOf(rows[it].set) }
            for (i in listed.filter { rows[it].set.startsWith("owner") }.take(80) + listed.filter { !rows[it].set.startsWith("owner") }.take(40)) {
                val was = rows[i].right(base.readings[i]); val now = rows[i].right(r.readings[i])
                val mark = when { was && !now -> "LOST"; !was && now -> "WON"; was && now -> "still right"; else -> "still wrong" }
                println("- [${rows[i].set}][${rows[i].lang}] ${rows[i].said}  | P0 ${base.readings[i]} -> ${r.readings[i]} ($mark" +
                    (if (wrongSide(rows[i], base.readings[i]) != wrongSide(rows[i], r.readings[i])) ", money side ${if (wrongSide(rows[i], r.readings[i])) "now WRONG" else "now fixed"}" else "") + ")")
            }
            if (listed.size > 120) println("- … ${listed.size - 120} more (owner rows are listed first, all of them up to 80)")
            println()
        }
        // The wrong-side rows of P0 on the owner's lists, and whether each policy fixes them.
        println("#### The wrong money sides at P0 on the owner's lists and review sets (entity metrics' rows), per policy")
        println()
        for (i in rows.indices.filter { rows[it].set != "heldout" && wrongSide(rows[it], base.readings[it]) })
            println("- [${rows[i].set}] ${rows[i].said} | P0 ${base.readings[i]}; " + runs.drop(1).joinToString("; ") { "${it.name.substringBefore(' ')} ${it.readings[i]}" })
        println()
    }

    // ------------------------------------------------------------------------------------------ main
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private class BankRow(val fold: Int, val lang: String, val label: String, val text: String)

    private fun readFolds(f: File): List<BankRow> = f.readLines().filter { it.isNotBlank() }.map {
        val c = it.split('\t', limit = 4); BankRow(c[0].toInt(), c[1], c[2], c[3])
    }

    private fun oof(bank: List<BankRow>, seeds: List<Long>, tag: String): List<Pred> {
        val out = arrayOfNulls<Pred>(bank.size)
        for (f in bank.map { it.fold }.distinct().sorted()) {
            val train = bank.filter { it.fold != f }.map { "${it.label}\t${it.text}" }
            val t0 = System.nanoTime()
            val m = HudhudSense.trainEnsemble(train, seeds, HudhudSense.SHIPPED_PRUNE)
            val tTrain = (System.nanoTime() - t0) / 1e9
            HudhudSense.use(m)
            val t1 = System.nanoTime()
            bank.forEachIndexed { i, r -> if (r.fold == f) out[i] = predict(r.lang, r.label, r.text) }
            System.err.println("OOF $tag fold $f: learnt from ${train.size} lines in %.1f s, read ${bank.count { it.fold == f }} in %.1f s".format(tTrain, (System.nanoTime() - t1) / 1e9))
            timings += "OOF $tag fold $f: learn %.1f s, read %.1f s".format(tTrain, (System.nanoTime() - t1) / 1e9)
        }
        return out.map { it!! }
    }

    private val timings = ArrayList<String>()

    private fun writeTsv(file: File, ps: List<Pred>) {
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("language\tmeaning\tread_as\tcertainty\tsecond\n")
            for (p in ps) w.write("${p.lang}\t${p.truth}\t${p.top}\t${"%.6f".format(p.p)}\t${"%.6f".format(p.second)}\n")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        fun arg(n: String) = args.indexOf(n).takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }
        val foldsFile = File(arg("--folds") ?: error("--folds FILE (scripts/hudhud_calib_folds.py --out FILE)"))
        val naiveFile = arg("--naive-folds")?.let { File(it) }
        val extra = arg("--extra-seeds")?.split(',')?.filter { it.isNotBlank() }?.map { it.trim().toLong() }.orEmpty()
        val outDir = arg("--out")?.let { File(it) }
        val minN = arg("--min-n")?.toInt() ?: 20
        val moneyTarget = arg("--money-target")?.toDouble() ?: 0.98
        val otherTarget = arg("--other-target")?.toDouble() ?: 0.95
        val doPolicies = !args.contains("--no-policies")
        val started = System.nanoTime()

        val bank = readFolds(foldsFile)
        val shippedLines = HudhudModelTest.bank()
        require(bank.map { "${it.label}\t${it.text}" } == shippedLines) { "the folds file is not bank.tsv line for line, in order" }
        val shipped = HudhudModelTest.shippedModel() ?: error("sense.bin is missing")
        println("Run: " + java.time.LocalDateTime.now() + "; bank ${bank.size} lines (= bank.tsv, in order); sense.bin sha256 " +
            sha(HudhudModelTest.asset().readBytes()) + "; folds file sha256 " + sha(foldsFile.readBytes()) + "; processors " +
            Runtime.getRuntime().availableProcessors() + "; max heap ${Runtime.getRuntime().maxMemory() / (1 shl 20)} MB")
        println("Fold sizes: " + bank.groupingBy { it.fold }.eachCount().toSortedMap() + "; recipe: trainEnsemble(seeds, prune ${HudhudSense.SHIPPED_PRUNE}); " +
            "targets: money ${moneyTarget}, other ${otherTarget}; a cut needs at least $minN rows")
        println()

        try {
            // 0. The recipe the folds are learnt with IS the shipped one: learnt from the whole bank it must give sense.bin byte for byte.
            val t0 = System.nanoTime()
            val whole = HudhudSense.encode(HudhudSense.trainEnsemble(shippedLines, HudhudSense.SHIPPED_SEEDS, HudhudSense.SHIPPED_PRUNE))
            val same = whole.contentEquals(HudhudModelTest.asset().readBytes())
            println("Recipe check: trainEnsemble(whole bank, seeds ${HudhudSense.SHIPPED_SEEDS.first()}..${HudhudSense.SHIPPED_SEEDS.last()}, prune ${HudhudSense.SHIPPED_PRUNE}) " +
                (if (same) "equals sense.bin byte for byte" else "DIFFERS from sense.bin: the folds below are NOT the shipped recipe") +
                " (%.1f s)".format((System.nanoTime() - t0) / 1e9))
            println()
            // 1. OOF with the shipped seeds, then the other ensembles.
            val main = oof(bank, HudhudSense.SHIPPED_SEEDS, "seeds ${HudhudSense.SHIPPED_SEEDS.first()}..${HudhudSense.SHIPPED_SEEDS.last()}")
            val others = extra.map { base -> base to oof(bank, List(15) { base + it }, "seeds $base..${base + 14}") }
            val naive = naiveFile?.let { f ->
                val nb = readFolds(f)
                require(nb.map { "${it.label}\t${it.text}" } == shippedLines) { "the naive folds file is not bank.tsv" }
                oof(nb, HudhudSense.SHIPPED_SEEDS, "naive text-only folds")
            }
            // 2. Held-out with the shipped sense.
            HudhudSense.use(shipped)
            val held = lines("/hudhud/heldout.tsv").map { val (l, m, t) = it.split('\t', limit = 3); predict(l, m, t) }

            outDir?.let { d ->
                writeTsv(File(d, "oof-${HudhudSense.SHIPPED_SEEDS.first()}.tsv"), main)
                others.forEach { (b, ps) -> writeTsv(File(d, "oof-$b.tsv"), ps) }
                naive?.let { writeTsv(File(d, "oof-naive.tsv"), it) }
                writeTsv(File(d, "heldout-shipped.tsv"), held)
            }

            println("### 1. Reliability: certainty -> share right")
            println()
            reliability("Out-of-fold, bank, 5 folds by paraphrase family, shipped recipe (seeds 313..327, prune 200)", main)
            reliability("Held-out (dev data), the shipped sense.bin", held)
            for ((b, ps) in others) reliability("Out-of-fold, seeds $b..${b + 14} (lesson 163: another ensemble)", ps)
            naive?.let { reliability("Out-of-fold, NAIVE folds (text key only: one sentence's forms in other scripts may sit in other folds)", it) }

            println("Per language (OOF, shipped seeds / held-out): rows right, ECE")
            println()
            println("| lang | OOF right | OOF ECE | held-out right | held-out ECE |")
            println("|---|---|---|---|---|")
            for (l in main.map { it.lang }.distinct()) {
                val a = main.filter { it.lang == l }; val h = held.filter { it.lang == l || (l == "id" && it.lang == "in") }
                println("| $l | ${a.count { it.right }}/${a.size} ${pct(a.count { it.right }, a.size)} | ${"%.4f".format(ece(a, EQUAL15))} | " +
                    "${h.count { it.right }}/${h.size} ${pct(h.count { it.right }, h.size)} | ${"%.4f".format(ece(h, EQUAL15))} |")
            }
            println()

            println("### 2. Per meaning: the lowest certainty that reaches the target (from OOF), checked on held-out")
            println()
            println("Today decide's gates are global: " + TODAY.joinToString(", ") { "${it.first} ${it.second}" } +
                " (and `p < OWN_PAGE_SENSE` 0.80 keeps a page). A cell is share right (rows read as that meaning at that certainty or more).")
            println()
            val props = classTable(main, held, minN, moneyTarget, otherTarget)
            // Stability of t across the other ensembles (lesson 163). With them, the proposal used below is the CONSERVATIVE t: the
            // highest of the ensembles' (a meaning that misses its target under any ensemble is "not reached").
            var used = props
            if (others.isNotEmpty()) {
                println("Proposed t under the other ensembles (lesson 163; '-' = not reached); the conservative t (the highest; '-' when any")
                println("ensemble does not reach the target) is the one the policies below use:")
                println()
                println("| meaning | seeds 313.. | " + others.joinToString(" | ") { "seeds ${it.first}.." } + " | conservative t |")
                println("|---|---|" + "---|".repeat(others.size + 1))
                used = props.map { p ->
                    val ts = others.map { (_, ps) -> lowestCertainty(ps, p.label, p.target, minN) }
                    val all = listOf(p.t) + ts
                    val c = if (all.any { it == null }) null else all.maxOf { it!! }
                    println("| ${p.label} | ${p.t?.let { "%.4f".format(it) } ?: "-"} | " + ts.joinToString(" | ") { t -> t?.let { "%.4f".format(it) } ?: "-" } +
                        " | ${c?.let { "%.4f".format(it) } ?: "-"} |")
                    val atC = c?.let { at(main, p.label, it) } ?: (0 to 0)
                    Proposal(p.label, p.target, c, atC.second, atC.first)
                }
                println()
            }
            val reached = used.filter { it.t != null }
            println("Summary (${if (others.isEmpty()) "shipped seeds" else "conservative t"}): ${reached.size} of ${used.size} meanings reach their target at some certainty; " +
                "${reached.count { it.t!! < Hudhud.SENSE_FIRM_TURN }} below today's FIRM_TURN 0.99, " +
                "${reached.count { it.t!! > Hudhud.SENSE_OVERRULE }} above today's OVERRULE 0.95; money meanings: " +
                used.filter { it.label in MONEY }.joinToString(", ") { "${it.label} ${it.t?.let { t -> "%.4f".format(t) } ?: "not reached"}" })
            println()
            outDir?.let { d ->
                File(d, "proposals.tsv").bufferedWriter(Charsets.UTF_8).use { w ->
                    w.write("meaning\tmoney_side\ttarget\tproposed_t\toof_rows_at_t\toof_right_at_t\n")
                    for (p in used) w.write("${p.label}\t${p.label in MONEY}\t${p.target}\t${p.t?.let { "%.6f".format(it) } ?: "none"}\t${p.n}\t${p.right}\n")
                }
            }

            if (doPolicies) {
                println("### 3. What the proposals would change, end to end (a gate copy of Hudhud.kt in the build folder; report only)")
                println()
                policies(used, shipped)
            }
        } finally {
            HudhudSense.use(shipped)
            HudhudCalibGate.rules = emptyMap()
        }
        println("Timings: " + timings.joinToString("; ") + "; whole run %.0f s".format((System.nanoTime() - started) / 1e9))
    }
}
