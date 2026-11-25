package com.innovation313.roshankhata.data

import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hudhud's LEARNERS compared (plan v1, Marhala 5 item 2, 26 Nov 2026): on the SAME features ([HudhudSense.features], the same
 * kind weights, rarity and length normalisation as [HudhudSense.trainEnsemble]) and the SAME paraphrase-family folds as the
 * calibration (scripts/hudhud_calib_folds.py), three learners:
 *
 *  1. the shipped averaged-perceptron ensemble ([HudhudSense.trainEnsemble], 15 seeds, prune 200) — the baseline;
 *  2. multinomial logistic regression (softmax, L2, plain SGD with a decaying step, the mean of the epoch-end weights of the
 *     second half, a fixed shuffle, StrictMath.exp);
 *  3. a linear SVM, one meaning against the rest (hinge, Pegasos steps, the same epoch-end mean).
 *
 * Every learner's weights are written into the app's own model ([HudhudSense.Model]: the same features, the same sixteen-bit
 * file, no bias term) and READ through [HudhudSense.rank], so the certainties below are the ones decide would compare. The app
 * reads a model as softmax(scores / [HudhudSense.TEMPERATURE]); a challenger's weights are multiplied by TEMPERATURE / T so the
 * app gives softmax(scores / T): T = 1 for logistic regression (its own probabilities), T fitted by likelihood for the SVM, and
 * for both a second T that gives the same share of rows at 0.95 or more as the perceptron (decide's gates were measured on the
 * perceptron's sharpness, lesson 165). Each challenger is pruned (whole-number steps, as SHIPPED_PRUNE) to the shipped file's size.
 *
 * End to end: the model learnt from the whole bank is put in place of sense.bin with [HudhudSense.use] (nothing of the
 * repository is written) and [HudhudBaseline.pass] reads every labelled set through the real pipeline (Hudhud.understand,
 * decide unchanged). A program, not a test. Run it with scripts/hudhud_learners.sh.
 */
object HudhudLearners {

    // ------------------------------------------------------------------------------------------ the features, as trainEnsemble makes them
    class Ex(val label: Int, val ids: IntArray, val x: DoubleArray)
    class Data(val labels: List<String>, val features: Array<String>, val df: IntArray, val n: Int, val ex: List<Ex>, val ids: HashMap<String, Int>, val idf: DoubleArray)

    private fun placeholders(text: String) = text.replace("{NAME}", " ${HudhudSense.NAME} ").replace("{ITEM}", " ${HudhudSense.ITEM} ")
    private fun idfOf(examples: Int, df: Int): Double = StrictMath.log((examples + 1.0) / (df + 1.0)) + 1.0
    private fun kindWeight(f: String): Double = when (f[0]) {
        'c' -> HudhudSense.W_CONCEPT; 'w' -> HudhudSense.W_WORD; 'b' -> HudhudSense.W_PAIR; 's' -> HudhudSense.W_SOUND
        else -> HudhudSense.W_PIECE
    }

    /** The bank's lines as trainEnsemble reads them (checked against it in [main]). */
    fun vectorize(lines: List<String>): Data {
        val labelIds = LinkedHashMap<String, Int>()
        val rows = ArrayList<Pair<Int, List<String>>>()
        for (line in lines) {
            val tab = line.indexOf('\t'); if (tab <= 0) continue
            val label = line.substring(0, tab).trim(); val text = line.substring(tab + 1).trim()
            if (label.isEmpty() || text.isEmpty()) continue
            rows += labelIds.getOrPut(label) { labelIds.size } to HudhudSense.features(placeholders(text)).toList()
        }
        val counted = HashMap<String, Int>()
        for ((_, fs) in rows) for (f in fs) counted[f] = (counted[f] ?: 0) + 1
        val features = counted.filterValues { it >= HudhudSense.MIN_DF }.keys.sorted().toTypedArray()
        val ids = HashMap<String, Int>(features.size * 2).also { m -> features.forEachIndexed { i, f -> m[f] = i } }
        val df = IntArray(features.size) { counted.getValue(features[it]) }
        val n = rows.size
        val idf = DoubleArray(features.size) { idfOf(n, df[it]) }
        val ex = rows.map { (label, all) ->
            val fs = all.filter { it in ids }
            val own = fs.map { ids.getValue(it) }.toIntArray()
            val raw = DoubleArray(own.size) { k -> kindWeight(fs[k]) * idf[own[k]] }
            val norm = sqrt(raw.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
            // trainEnsemble keeps x as a Float; so does this, for the same numbers.
            Ex(label, own, DoubleArray(own.size) { (raw[it] / norm).toFloat().toDouble() })
        }
        return Data(labelIds.keys.toList(), features, df, n, ex, ids, idf)
    }

    // ------------------------------------------------------------------------------------------ learners
    /** Multinomial logistic regression, L2 [lambda], SGD (Bottou's step eta0/(1+eta0*lambda*t)), mean of the epoch-end weights of the second half. */
    fun trainLR(d: Data, lambda: Double, epochs: Int, seed: Long, eta0: Double = 0.5): DoubleArray {
        val L = d.labels.size; val F = d.features.size
        val v = DoubleArray(F * L); var s = 1.0
        val mean = DoubleArray(F * L); var means = 0
        val order = d.ex.indices.toMutableList(); val rnd = java.util.Random(seed)
        val z = DoubleArray(L)
        var t = 0L
        for (ep in 0 until epochs) {
            java.util.Collections.shuffle(order, rnd)
            for (i in order) {
                val e = d.ex[i]
                val eta = eta0 / (1.0 + eta0 * lambda * t)
                java.util.Arrays.fill(z, 0.0)
                for (k in e.ids.indices) { val b = e.ids[k] * L; val xv = e.x[k]; for (l in 0 until L) z[l] += v[b + l] * xv }
                var top = Double.NEGATIVE_INFINITY
                for (l in 0 until L) { z[l] *= s; if (z[l] > top) top = z[l] }
                var tot = 0.0
                for (l in 0 until L) { z[l] = StrictMath.exp(z[l] - top); tot += z[l] }
                for (l in 0 until L) z[l] /= tot
                z[e.label] -= 1.0
                s *= (1.0 - eta * lambda)
                val c = eta / s
                for (k in e.ids.indices) { val b = e.ids[k] * L; val xv = c * e.x[k]; for (l in 0 until L) v[b + l] -= z[l] * xv }
                if (s < 1e-6) { for (j in v.indices) v[j] *= s; s = 1.0 }
                t++
            }
            if (ep >= epochs / 2) { for (j in v.indices) mean[j] += v[j] * s; means++ }
        }
        for (j in mean.indices) mean[j] /= means
        return mean
    }

    /**
     * Linear SVM, one meaning against the rest (no bias, as the app's model has none). Pegasos: with u the sum of (1/lambda) y x over
     * the margins broken so far, the weights after t steps are u / t. Mean of the epoch-end weights of the second half.
     */
    fun trainSVM(d: Data, lambda: Double, epochs: Int, seed: Long): DoubleArray {
        val L = d.labels.size; val F = d.features.size
        val u = DoubleArray(F * L)
        val mean = DoubleArray(F * L); var means = 0
        val order = d.ex.indices.toMutableList(); val rnd = java.util.Random(seed)
        val z = DoubleArray(L)
        var t = 1L
        val inv = 1.0 / lambda
        for (ep in 0 until epochs) {
            java.util.Collections.shuffle(order, rnd)
            for (i in order) {
                val e = d.ex[i]
                java.util.Arrays.fill(z, 0.0)
                if (t > 1) {
                    for (k in e.ids.indices) { val b = e.ids[k] * L; val xv = e.x[k]; for (l in 0 until L) z[l] += u[b + l] * xv }
                    val w = 1.0 / (t - 1); for (l in 0 until L) z[l] *= w
                }
                for (l in 0 until L) {
                    val y = if (l == e.label) 1.0 else -1.0
                    if (y * z[l] < 1.0) for (k in e.ids.indices) u[e.ids[k] * L + l] += inv * y * e.x[k]
                }
                t++
            }
            if (ep >= epochs / 2) { val w = 1.0 / (t - 1); for (j in u.indices) mean[j] += u[j] * w; means++ }
        }
        for (j in mean.indices) mean[j] /= means
        return mean
    }

    // ------------------------------------------------------------------------------------------ into the app's model
    /** Weights [w] (feature-major, F x L) times [mult], as sixteen-bit whole numbers of the largest, steps under [prune] left out. */
    fun toModel(d: Data, w: DoubleArray, mult: Double, prune: Int): HudhudSense.Model {
        val L = d.labels.size
        var largest = 0.0
        for (v in w) largest = maxOf(largest, abs(v * mult))
        val scale = if (largest > 0) (largest / Short.MAX_VALUE).toFloat() else 1f
        val start = IntArray(d.features.size + 1)
        val pl = ArrayList<Byte>(); val pw = ArrayList<Short>()
        for (f in d.features.indices) {
            start[f] = pl.size
            for (l in 0 until L) {
                val q = (w[f * L + l] * mult / scale).roundToInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
                if (q != 0 && abs(q) >= prune) { pl += l.toByte(); pw += q.toShort() }
            }
        }
        start[d.features.size] = pl.size
        return HudhudSense.Model(d.labels, d.features, d.df, d.n, scale, start, pl.toByteArray(), pw.toShortArray())
    }

    /** The smallest prune whose file is no larger than [budget] bytes (and the size): a binary search over the steps. */
    fun pruneFor(d: Data, w: DoubleArray, mult: Double, budget: Int): Pair<Int, Int> {
        var lo = 0; var hi = Short.MAX_VALUE.toInt()
        val at0 = HudhudSense.encode(toModel(d, w, mult, 0)).size
        if (at0 <= budget) return 0 to at0
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (HudhudSense.encode(toModel(d, w, mult, mid)).size <= budget) hi = mid else lo = mid
        }
        return hi to HudhudSense.encode(toModel(d, w, mult, hi)).size
    }

    // ------------------------------------------------------------------------------------------ reading
    class Pred(val lang: String, val truth: String, val top: String, val p: Double) { val right get() = truth == top }

    private fun predict(lang: String, truth: String, text: String): Pred {
        val g = HudhudSense.rank(placeholders(text), top = 1)
        return Pred(lang, truth, g.getOrNull(0)?.label ?: "(none)", g.getOrNull(0)?.score ?: 0.0)
    }

    /** Float scores W.x of a sentence by [d]'s features (for fitting T): null labels when nothing is known. */
    private fun rawScores(d: Data, w: DoubleArray, text: String): DoubleArray {
        val L = d.labels.size; val out = DoubleArray(L)
        val ids = ArrayList<Int>(); val raw = ArrayList<Double>()
        for (f in HudhudSense.features(placeholders(text))) { val i = d.ids[f] ?: continue; ids += i; raw += kindWeight(f) * d.idf[i] }
        if (ids.isEmpty()) return out
        val norm = sqrt(raw.sumOf { it * it })
        for (k in ids.indices) { val q = raw[k] / norm; val b = ids[k] * L; for (l in 0 until L) out[l] += w[b + l] * q }
        return out
    }

    private fun softmaxTop(s: DoubleArray, t: Double): Pair<Int, DoubleArray> {
        val top = s.maxOrNull() ?: 0.0
        val p = DoubleArray(s.size) { StrictMath.exp((s[it] - top) / t) }
        val tot = p.sum(); for (i in p.indices) p[i] /= tot
        var best = 0; for (i in p.indices) if (p[i] > p[best]) best = i
        return best to p
    }

    /** NLL of the OOF raw scores at temperature t ([truth] is the label index in each fold's label list). */
    private fun nll(scores: List<Pair<DoubleArray, Int>>, t: Double): Double = scores.sumOf { (s, y) ->
        if (y < 0) 0.0 else { val (_, p) = softmaxTop(s, t); -StrictMath.log(maxOf(p[y], 1e-12)) }
    }

    private fun shareAt(scores: List<Pair<DoubleArray, Int>>, t: Double, cut: Double) =
        scores.count { (s, _) -> softmaxTop(s, t).second.maxOrNull()!! >= cut }.toDouble() / scores.size

    // ------------------------------------------------------------------------------------------ measures
    private fun pct(r: Int, n: Int) = if (n == 0) "-" else "%.1f%%".format(100.0 * r / n)
    private val EQUAL15 = DoubleArray(16) { if (it == 15) 1.0000001 else it / 15.0 }
    private fun ece(ps: List<Pred>): Double {
        var e = 0.0
        for (b in 0 until 15) {
            val inBin = ps.filter { it.p >= EQUAL15[b] && it.p < EQUAL15[b + 1] }; if (inBin.isEmpty()) continue
            e += abs(inBin.count { it.right }.toDouble() / inBin.size - inBin.sumOf { it.p } / inBin.size) * inBin.size / ps.size
        }
        return e
    }
    private fun band(ps: List<Pred>, lo: Double, hi: Double = 2.0): String {
        val s = ps.filter { it.p >= lo && it.p < hi }
        return "${pct(s.count { it.right }, s.size)} of ${s.size}"
    }
    private val MONEY = listOf("to_get", "to_give", "collected", "entry_gave", "entry_got", "who_owes_most", "not_paid")
    private val SIDE = mapOf("to_get" to "R", "who_owes_most" to "R", "not_paid" to "R", "to_give" to "P", "collected" to "I", "entry_got" to "I", "entry_gave" to "O")
    private val OPP = mapOf("R" to "P", "P" to "R", "I" to "O", "O" to "I")
    private fun opposite(a: Pred) = SIDE[a.truth]?.let { s -> SIDE[a.top] == OPP[s] } ?: false

    // ------------------------------------------------------------------------------------------ the owner's lists, row by row
    private val names = listOf("Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders").mapIndexed { i, n -> Hudhud.Name(i + 1L, n) }
    private val goods = listOf("Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee").mapIndexed { i, n -> Hudhud.Name(100L + i, n) }
    private class OwnerRow(val set: String, val said: String, val ok: List<String>)
    private val ownerRows: List<OwnerRow> by lazy {
        listOf("owner_set", "owner_1000").flatMap { s ->
            (HudhudLearners::class.java.getResourceAsStream("/hudhud/$s.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines().orEmpty())
                .filter { it.isNotBlank() && !it.startsWith("#") }.map { val r = it.split('\t'); OwnerRow(s, r[1], r.last().split('|')) }
        }
    }
    private val ownerTopics by lazy { HudhudRealPages.topics(listOf("values-b+ur+Latn", "values", "values-ur")) }
    private fun ownerRight(): BooleanArray = BooleanArray(ownerRows.size) { i ->
        val shown = HudhudShown.of(Hudhud.understand(ownerRows[i].said, names, ownerTopics, null, goods)); ownerRows[i].ok.any { shown.startsWith(it) }
    }

    // ------------------------------------------------------------------------------------------ the run
    private class BankRow(val fold: Int, val lang: String, val label: String, val text: String)
    private class E2E(val name: String, val fig: HudhudBaseline.Figures, val owner: BooleanArray, val seconds: Double)
    private class Oof(val name: String, val preds: List<Pred>, val trainSeconds: Double)

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun e2e(name: String, m: HudhudSense.Model): E2E {
        HudhudSense.use(m)
        val t0 = System.nanoTime()
        System.err.println("e2e $name")
        val fig = HudhudBaseline.pass(timing = false, collectConfusion = false)
        val own = ownerRight()
        return E2E(name, fig, own, (System.nanoTime() - t0) / 1e9)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        fun arg(n: String) = args.indexOf(n).takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }
        val folds = File(arg("--folds") ?: error("--folds FILE")).readLines().filter { it.isNotBlank() }
            .map { val c = it.split('\t', limit = 4); BankRow(c[0].toInt(), c[1], c[2], c[3]) }
        val seeds = (arg("--seeds") ?: "313,1000,2000").split(',').map { it.trim().toLong() }
        val lrEpochs = arg("--lr-epochs")?.toInt() ?: 20
        val svmEpochs = arg("--svm-epochs")?.toInt() ?: 20
        val lrLambdas = (arg("--lr-lambdas") ?: "1e-6,1e-5,1e-4").split(',').map { it.toDouble() }
        val svmLambdas = (arg("--svm-lambdas") ?: "1e-6,1e-5,1e-4").split(',').map { it.toDouble() }
        val lrEta = arg("--lr-eta")?.toDouble() ?: 0.5
        val skipE2E = args.contains("--no-e2e")
        val shippedBytes = HudhudModelTest.asset().readBytes()
        val budget = shippedBytes.size
        val bank = HudhudModelTest.bank()
        require(folds.map { "${it.label}\t${it.text}" } == bank) { "folds file is not bank.tsv" }
        val foldIds = folds.map { it.fold }.distinct().sorted()
        println("Run: ${java.time.LocalDateTime.now()}; bank ${bank.size} lines; sense.bin ${budget} bytes sha256 ${sha(shippedBytes)}; " +
            "processors ${Runtime.getRuntime().availableProcessors()}; max heap ${Runtime.getRuntime().maxMemory() shr 20} MB")

        // 0. The features are trainEnsemble's.
        val whole = vectorize(bank)
        val ref = HudhudSense.trainEnsemble(bank, listOf(HudhudSense.SEED))
        require(whole.features.contentEquals(ref.features) && whole.df.contentEquals(ref.df) && whole.labels == ref.labels) {
            "vectorize does not give trainEnsemble's features"
        }
        val kinds = whole.features.groupingBy { it[0] }.eachCount().toSortedMap()
        println("Features check: vectorize(bank) = trainEnsemble's features, df and labels. ${whole.features.size} features " +
            "($kinds; g = three-letter pieces of words of 4+ letters), ${whole.labels.size} meanings, " +
            "mean ${"%.1f".format(whole.ex.sumOf { it.ids.size }.toDouble() / whole.n)} features a sentence.")
        println()

        val foldData = foldIds.associateWith { f -> vectorize(folds.filter { it.fold != f }.map { "${it.label}\t${it.text}" }) }
        val oofs = ArrayList<Oof>()

        // 1. The perceptron ensemble, three ensembles of 15 (the calibration's), OOF via rank.
        val percSeeds = seeds.map { s -> List(15) { s + it } }
        for (ps in percSeeds) {
            val preds = arrayOfNulls<Pred>(folds.size); var tt = 0.0
            for (f in foldIds) {
                val t0 = System.nanoTime()
                val m = HudhudSense.trainEnsemble(folds.filter { it.fold != f }.map { "${it.label}\t${it.text}" }, ps, HudhudSense.SHIPPED_PRUNE)
                tt += (System.nanoTime() - t0) / 1e9
                HudhudSense.use(m)
                folds.forEachIndexed { i, r -> if (r.fold == f) preds[i] = predict(r.lang, r.label, r.text) }
            }
            System.err.println("perceptron ${ps.first()}: OOF ${preds.count { it!!.right }} (learn %.1f s)".format(tt))
            oofs += Oof("perceptron x15 seeds ${ps.first()}..${ps.last()}", preds.map { it!! }, tt)
        }
        val percShare95 = oofs.first().preds.count { it.p >= 0.95 }.toDouble() / folds.size

        // 2/3. Challengers: lambda chosen by OOF accuracy (float scores) on the first seed; then every seed at that lambda.
        class Challenger(val kind: String, val lambda: Double, val seed: Long, val foldW: Map<Int, DoubleArray>, val raw: List<Pair<DoubleArray, Int>>, val trainSeconds: Double)
        fun runChallenger(kind: String, lambda: Double, seed: Long): Challenger {
            val ws = HashMap<Int, DoubleArray>(); var tt = 0.0
            val raw = arrayOfNulls<Pair<DoubleArray, Int>>(folds.size)
            for (f in foldIds) {
                val d = foldData.getValue(f)
                val t0 = System.nanoTime()
                val w = if (kind == "LR") trainLR(d, lambda, lrEpochs, seed, lrEta) else trainSVM(d, lambda, svmEpochs, seed)
                tt += (System.nanoTime() - t0) / 1e9
                ws[f] = w
                folds.forEachIndexed { i, r -> if (r.fold == f) raw[i] = rawScores(d, w, r.text) to d.labels.indexOf(r.label) }
            }
            val ch = Challenger(kind, lambda, seed, ws, raw.map { it!! }, tt)
            val acc = folds.indices.count { i -> val (s, y) = ch.raw[i]; s.any { it != 0.0 } && softmaxTop(s, 1.0).first == y }
            System.err.println("$kind lambda $lambda seed $seed: OOF float ${acc} of ${folds.size} (learn %.1f s)".format(tt))
            return ch
        }
        fun floatRight(c: Challenger) = folds.indices.count { i -> val (s, y) = c.raw[i]; s.any { it != 0.0 } && softmaxTop(s, 1.0).first == y }

        println("### Choosing lambda (out of fold, unquantised scores, first seed ${seeds.first()})")
        println()
        println("| learner | lambda | OOF right (float) | learn s (5 folds) |")
        println("|---|---|---|---|")
        val chosen = HashMap<String, Challenger>()
        for ((kind, lams) in listOf("LR" to lrLambdas, "SVM" to svmLambdas)) {
            var best: Challenger? = null
            for (lam in lams) {
                val c = runChallenger(kind, lam, seeds.first())
                val r = floatRight(c)
                println("| $kind | $lam | $r of ${folds.size} ${pct(r, folds.size)} | ${"%.1f".format(c.trainSeconds)} |")
                if (best == null || r > floatRight(best)) best = c
            }
            chosen[kind] = best!!
        }
        println()
        System.gc()

        // Temperatures, sizes, OOF via the app's model for each challenger x seed.
        class Final(val label: String, val kind: String, val lambda: Double, val seed: Long, val t: Double, val prune: Int, val size: Int, val size0: Int, val wholeW: DoubleArray, val wholeSeconds: Double)
        val finals = ArrayList<Final>()
        val tRows = ArrayList<String>()
        for (kind in listOf("LR", "SVM")) {
            val lam = chosen.getValue(kind).lambda
            for (seed in seeds) {
                val c = if (seed == seeds.first()) chosen.getValue(kind) else runChallenger(kind, lam, seed)
                // T: LR's own (1), the SVM's by likelihood; and the T that matches the perceptron's share at 0.95.
                val grid = generateSequence(0.005) { it * 1.05 }.takeWhile { it < 50.0 }.toList()
                val tNll = if (kind == "LR") 1.0 else grid.minByOrNull { nll(c.raw, it) }!!
                var lo = 0.0005; var hi = 50.0
                repeat(40) { val mid = sqrt(lo * hi); if (shareAt(c.raw, mid, 0.95) > percShare95) lo = mid else hi = mid }
                val tMatch = sqrt(lo * hi)
                tRows += "| $kind | $lam | $seed | ${"%.4f".format(tNll)} | ${"%.4f".format(nll(c.raw, tNll) / folds.size)} | ${"%.4f".format(tMatch)} | " +
                    "${"%.4f".format(nll(c.raw, tMatch) / folds.size)} |"
                // The model learnt from the whole bank, its size and prune.
                val t0 = System.nanoTime()
                val wholeW = if (kind == "LR") trainLR(whole, lam, lrEpochs, seed, lrEta) else trainSVM(whole, lam, svmEpochs, seed)
                val wholeSec = (System.nanoTime() - t0) / 1e9
                for ((tag, t) in listOf("own T" to tNll, "matched T" to tMatch)) {
                    val mult = HudhudSense.TEMPERATURE / t
                    val size0 = HudhudSense.encode(toModel(whole, wholeW, mult, 0)).size
                    val (prune, size) = pruneFor(whole, wholeW, mult, budget)
                    val preds = arrayOfNulls<Pred>(folds.size)
                    for (f in foldIds) {
                        HudhudSense.use(toModel(foldData.getValue(f), c.foldW.getValue(f), mult, prune))
                        folds.forEachIndexed { i, r -> if (r.fold == f) preds[i] = predict(r.lang, r.label, r.text) }
                    }
                    val name = "$kind (lambda $lam) seed $seed, $tag ${"%.3f".format(t)}"
                    oofs += Oof(name, preds.map { it!! }, c.trainSeconds)
                    finals += Final(name, kind, lam, seed, t, prune, size, size0, wholeW, wholeSec)
                    System.err.println("$name: OOF ${preds.count { it!!.right }}, prune $prune, size $size (unpruned $size0)")
                }
            }
        }

        println("### Temperatures (fitted on the out-of-fold scores; per-row mean NLL)")
        println()
        println("| learner | lambda | seed | own T (LR: 1; SVM: max likelihood) | NLL | matched T (same share >= 0.95 as the perceptron, ${"%.1f%%".format(100 * percShare95)}) | NLL |")
        println("|---|---|---|---|---|---|---|")
        tRows.forEach { println(it) }
        println()

        // 4. The OOF table.
        println("### Out of fold (5 family folds, 13,374 rows), read through the app's model and HudhudSense.rank")
        println()
        println("| learner | right | share | ECE | >= 0.95: right of rows | >= 0.99 | 0.95-0.99 | money read: opposite side | learn s (5 folds) |")
        println("|---|---|---|---|---|---|---|---|---|")
        for (o in oofs) {
            val ps = o.preds; val r = ps.count { it.right }
            println("| ${o.name} | $r | ${pct(r, ps.size)} | ${"%.4f".format(ece(ps))} | ${band(ps, 0.95)} | ${band(ps, 0.99)} | ${band(ps, 0.95, 0.99)} | " +
                "${ps.count { opposite(it) }} (at >= 0.99: ${ps.count { opposite(it) && it.p >= 0.99 }}) | ${"%.1f".format(o.trainSeconds)} |")
        }
        println()
        val langs = folds.map { it.lang }.distinct()
        println("Per language, OOF right of rows (ECE):")
        println()
        println("| learner | " + langs.joinToString(" | ") + " |")
        println("|---|" + "---|".repeat(langs.size))
        for (o in oofs) println("| ${o.name} | " + langs.joinToString(" | ") { l ->
            val s = o.preds.filter { it.lang == l }; "${s.count { it.right }}/${s.size} (${"%.3f".format(ece(s))})"
        } + " |")
        println()

        println("### Model files learnt from the whole bank (sense.bin today: $budget bytes)")
        println()
        println("| learner | learn s (whole bank) | prune (steps) | file bytes | unpruned file bytes |")
        println("|---|---|---|---|---|")
        for (fn in finals) println("| ${fn.label} | ${"%.1f".format(fn.wholeSeconds)} | ${fn.prune} | ${fn.size} | ${fn.size0} |")
        println()

        if (skipE2E) return
        // 5. End to end: the whole-bank model in sense.bin's place.
        val shipped = HudhudModelTest.shippedModel() ?: error("sense.bin missing")
        val runs = ArrayList<E2E>()
        try {
            runs += e2e("perceptron x15 313..327 (sense.bin)", shipped)
            for (ps in percSeeds.drop(1)) {
                val t0 = System.nanoTime()
                val m = HudhudSense.trainEnsemble(bank, ps, HudhudSense.SHIPPED_PRUNE)
                System.err.println("whole-bank perceptron ${ps.first()}: %.1f s, %d bytes".format((System.nanoTime() - t0) / 1e9, HudhudSense.encode(m).size))
                runs += e2e("perceptron x15 ${ps.first()}..${ps.last()}", m)
            }
            for (fn in finals) runs += e2e(fn.label, toModel(whole, fn.wholeW, HudhudSense.TEMPERATURE / fn.t, fn.prune))
        } finally {
            HudhudSense.use(shipped)
        }
        val series = listOf("heldout strict", "heldout lenient", "owner_set", "owner_1000", "review_d", "review_o")
        val base = runs.first()
        println("### End to end (Hudhud.understand, decide unchanged; the learner's whole-bank model in sense.bin's place)")
        println()
        println("| learner | " + series.joinToString(" | ") + " | owner lists together | wrong side | by set (held/os/o1k/D/O) | wrong amount | wrong person | wrong product | owner rows lost / won vs sense.bin | s |")
        println("|---|" + "---|".repeat(series.size + 8))
        for (r in runs) {
            val lost = r.owner.indices.count { base.owner[it] && !r.owner[it] }
            val won = r.owner.indices.count { !base.owner[it] && r.owner[it] }
            fun w(e: HudhudBaseline.Ent) = r.fig.entTotal(null, e, HudhudBaseline.Out.WRONG)
            println("| ${r.name} | " + series.joinToString(" | ") { r.fig.total(it).first.toString() } +
                " | ${r.fig.total("owner_set").first + r.fig.total("owner_1000").first} | ${w(HudhudBaseline.Ent.SIDE)} | " +
                listOf("heldout", "owner_set", "owner_1000", "review_d", "review_o").joinToString("/") { s ->
                    r.fig.entTotal(s, HudhudBaseline.Ent.SIDE, HudhudBaseline.Out.WRONG).toString() } +
                " | ${w(HudhudBaseline.Ent.AMOUNT)} | " +
                "${w(HudhudBaseline.Ent.PERSON)} | ${w(HudhudBaseline.Ent.PRODUCT)} | $lost / $won | ${"%.0f".format(r.seconds)} |")
        }
        println("| totals | " + series.joinToString(" | ") { base.fig.total(it).second.toString() } + " | ${base.fig.total("owner_set").second + base.fig.total("owner_1000").second} | | | | | | | |")
        println()
    }
}
