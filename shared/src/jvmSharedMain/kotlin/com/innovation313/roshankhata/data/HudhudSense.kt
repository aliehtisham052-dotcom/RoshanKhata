package com.innovation313.roshankhata.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hudhud's sense of what a sentence means (13 Nov 2026, the owner: "jo technique Claude use karta hai, wohi Hudhud
 * mein chalao — jawab phir bhi app ke andar se"). The rules in [Hudhud] read a question word by word; this reads it
 * whole, the way a language model's understanding works, without carrying a model of that size:
 *
 *  - **Pieces of words, not whole words.** Every word is also taken as its three-letter pieces ("#ka", "kal", "alk"…)
 *    and as its sound in any script ([HudhudMind.skeleton]): "اپ" and "آپ", "کیلکولیٹر" and "calculator", a word one
 *    letter off, a word with an ending another language joins to it — each still shares most of its pieces.
 *  - **Meaning from examples.** The bank (shared/hudhud: thirteen thousand sentences a shopkeeper says, in the app's
 *    nine languages, each with what it means — written by Claude, Fable, on the owner's yes, 13 Nov 2026) teaches
 *    which pieces speak for which meaning (an averaged perceptron, [train]). It is measured against a second set it
 *    has never seen (HudhudSenseTest).
 *  - **Context.** Words side by side count as a pair too ("ka pata" is an address, "pata hai" is knowing; "baat kar"
 *    is talking, "Butt" is a name), and every word also carries what [HudhudMind] knows it means, so a question in
 *    Bengali meets an example in Urdu through the meaning both share.
 *
 * What the phone carries is only what was learnt ([Model]: the pieces, how rare each is, and a small whole number per
 * piece and meaning), read once from the app's assets ([useModel]) — not the bank, and nothing is learnt on the phone:
 * a few hundred kilobytes and a few milliseconds, nothing downloaded, nothing sent anywhere. The tests learn it again
 * from the bank and hold the shipped file to it (HudhudModelTest), so the two cannot drift apart.
 *
 * Names and products are never in the bank: a name in an example is the placeholder [NAME], a product [ITEM], an
 * amount [NUM]; [rank] puts the same placeholders where the book's own names stand. What comes out is only a meaning
 * (a label); [Hudhud] turns it into a question about the book, and the answer is read from the book as before.
 */
object HudhudSense {

    /** A meaning and how sure the sense is of it, as its share of all meanings (0 to 1). */
    data class Guess(val label: String, val score: Double)

    /** The placeholders the bank writes for a customer's or supplier's name, a product's name and an amount. */
    const val NAME = "qqname"
    const val ITEM = "qqitem"
    const val NUM = "qqnum"

    /** Each kind of feature's weight beside its rarity: a meaning counts most, a three-letter piece least. */
    internal const val W_CONCEPT = 2.0
    internal const val W_WORD = 1.6
    internal const val W_PAIR = 1.3
    internal const val W_SOUND = 1.0
    internal const val W_PIECE = 0.35

    /** A feature fewer examples have teaches nothing another sentence can use, and is left out. */
    internal const val MIN_DF = 3
    /** How many times the bank is read while learning. */
    internal const val EPOCHS = 12
    /** How sharply the scores are made shares (a softmax's temperature): low, so a clear meaning stands out. */
    internal const val TEMPERATURE = 0.1

    /**
     * What was learnt: the meanings ([labels]), the pieces ([features], sorted, so the file never depends on a hash
     * order), how many examples had each ([df], of [examples]), and each piece's weights for the meanings it speaks
     * for — whole numbers times [scale], only those that are not nought ([start] into [pairLabel] and [pairWeight]).
     */
    class Model internal constructor(
        internal val labels: List<String>,
        internal val features: Array<String>,
        internal val df: IntArray,
        internal val examples: Int,
        internal val scale: Float,
        internal val start: IntArray,
        internal val pairLabel: ByteArray,
        internal val pairWeight: ShortArray
    ) {
        internal val vocabulary: HashMap<String, Int> = HashMap<String, Int>(features.size * 2).also { m ->
            features.forEachIndexed { i, f -> m[f] = i }
        }
        internal val idf = DoubleArray(features.size) { idfOf(examples, df[it]) }

        /** Tests: how many weights are kept. */
        internal val weights: Int get() = pairLabel.size

        internal fun isEmpty() = labels.isEmpty() || features.isEmpty()
    }

    @Volatile
    private var model: Model? = null

    @Volatile
    private var tried = false

    /** Where the model comes from: the app's asset ([useModel]), a test's bank ([useBank]); none — no sense at all. */
    @Volatile
    private var loader: (() -> Model?)? = null

    /** The app's shipped model: [read] gives its bytes (the asset), read once, on the first question. */
    fun useModel(read: () -> ByteArray?) {
        synchronized(this) {
            loader = { read()?.let { decode(it) } }
            model = null
            tried = false
        }
    }

    /** Tests: learn from [lines] instead (`label<TAB>sentence`). */
    internal fun useBank(lines: List<String>) {
        synchronized(this) {
            loader = { train(lines) }
            model = null
            tried = false
        }
    }

    /** Tests: this model, or none. */
    internal fun use(m: Model?) {
        synchronized(this) {
            loader = { m }
            model = m?.takeIf { !it.isEmpty() }
            tried = true
        }
    }

    /** Reads the model now, off the screen's thread (the app warms it up when Home opens). */
    fun warmUp() {
        current()
    }

    private fun current(): Model? = model ?: synchronized(this) {
        model ?: if (tried) null else {
            tried = true
            runCatching { loader?.invoke() }.getOrNull()?.takeIf { !it.isEmpty() }?.also { model = it }
        }
    }

    /** Whether there is a sense at all (the phone's asset read, a test's bank). */
    val ready: Boolean get() = current() != null

    /** The meanings it knows. */
    fun labels(): List<String> = current()?.labels.orEmpty()

    /** Tests: how many pieces it knows. */
    internal fun featureCount(): Int = current()?.features?.size ?: 0

    // ------------------------------------------------------------------------------------------------ learning

    private class Example(val label: Int, val ids: IntArray, val weights: FloatArray)

    /**
     * Learns the sense from the bank's [lines]: every example is read in turn, and when the meanings' scores put another
     * meaning above its own, its features are given to its own and taken from the other (an averaged perceptron:
     * averaging the weights over every step keeps one late example from tipping them). A fixed order and a fixed
     * shuffle, so the same bank always learns the same weights, on any computer. The weights are then made whole numbers
     * (a sixteen-bit share of the largest), as the phone keeps them.
     */
    internal fun train(lines: List<String>): Model {
        val labelIds = LinkedHashMap<String, Int>()
        val rows = ArrayList<Pair<Int, List<String>>>()
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val label = line.substring(0, tab).trim()
            val text = line.substring(tab + 1).trim()
            if (label.isEmpty() || text.isEmpty()) continue
            val id = labelIds.getOrPut(label) { labelIds.size }
            rows += id to features(placeholders(text)).toList()
        }
        val counted = HashMap<String, Int>()
        for ((_, fs) in rows) for (f in fs) counted[f] = (counted[f] ?: 0) + 1
        val features = counted.filterValues { it >= MIN_DF }.keys.sorted().toTypedArray()
        val ids = HashMap<String, Int>(features.size * 2).also { m -> features.forEachIndexed { i, f -> m[f] = i } }
        val df = IntArray(features.size) { counted.getValue(features[it]) }
        val n = rows.size
        val idf = DoubleArray(features.size) { idfOf(n, df[it]) }
        val examples = rows.map { (label, all) ->
            val fs = all.filter { it in ids }
            val own = fs.map { ids.getValue(it) }.toIntArray()
            val raw = DoubleArray(own.size) { k -> kindWeight(fs[k]) * idf[own[k]] }
            val norm = sqrt(raw.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
            Example(label, own, FloatArray(own.size) { (raw[it] / norm).toFloat() })
        }
        val labels = labelIds.size
        val w = learn(examples, labels, features.size)
        // Sixteen-bit whole numbers, a share of the largest weight; noughts are not kept.
        var largest = 0f
        for (v in w) largest = maxOf(largest, abs(v))
        val scale = if (largest > 0f) largest / Short.MAX_VALUE else 1f
        val start = IntArray(features.size + 1)
        val pl = ArrayList<Byte>()
        val pw = ArrayList<Short>()
        for (f in features.indices) {
            start[f] = pl.size
            for (l in 0 until labels) {
                val q = (w[f * labels + l] / scale).roundToInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
                if (q != 0) {
                    pl += l.toByte()
                    pw += q.toShort()
                }
            }
        }
        start[features.size] = pl.size
        return Model(labelIds.keys.toList(), features, df, n, scale, start, pl.toByteArray(), pw.toShortArray())
    }

    private fun learn(examples: List<Example>, labels: Int, features: Int): FloatArray {
        val w = FloatArray(features * labels)
        val sum = FloatArray(features * labels)
        if (examples.isEmpty() || labels == 0) return w
        val order = examples.indices.toMutableList()
        val random = java.util.Random(313)
        var step = 1f
        repeat(EPOCHS) {
            java.util.Collections.shuffle(order, random)
            for (x in order) {
                val e = examples[x]
                // Its own meaning's score, and the best of the others': a tie is a mistake too, so learning starts.
                var own = 0f
                var best = -1
                var bestScore = Float.NEGATIVE_INFINITY
                for (l in 0 until labels) {
                    var sc = 0f
                    for (k in e.ids.indices) sc += w[e.ids[k] * labels + l] * e.weights[k]
                    if (l == e.label) own = sc else if (sc > bestScore) { bestScore = sc; best = l }
                }
                if (best >= 0 && bestScore >= own) {
                    for (k in e.ids.indices) {
                        val f = e.ids[k] * labels
                        val v = e.weights[k]
                        w[f + e.label] += v
                        sum[f + e.label] += step * v
                        w[f + best] -= v
                        sum[f + best] -= step * v
                    }
                }
                step += 1f
            }
        }
        for (i in w.indices) w[i] = w[i] - sum[i] / step
        return w
    }

    // ------------------------------------------------------------------------------------------------ the file

    private const val MAGIC = 0x524B4853 // "RKHS"
    private const val VERSION = 1

    /** The model as the app's asset holds it: gzip of a small binary of the fields above. */
    internal fun encode(m: Model): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeInt(MAGIC)
            out.writeShort(VERSION)
            out.writeInt(m.examples)
            out.writeFloat(m.scale)
            out.writeShort(m.labels.size)
            m.labels.forEach { out.writeUTF(it) }
            out.writeInt(m.features.size)
            m.features.forEach { out.writeUTF(it) }
            m.df.forEach { out.writeShort(it) }
            for (f in m.features.indices) {
                val from = m.start[f]
                val to = m.start[f + 1]
                out.writeByte(to - from)
                for (k in from until to) {
                    out.writeByte(m.pairLabel[k].toInt())
                    out.writeShort(m.pairWeight[k].toInt())
                }
            }
        }
        return bytes.toByteArray()
    }

    /** The model from its file; null for a file that is not one, or of another version. */
    internal fun decode(file: ByteArray): Model? = runCatching {
        DataInputStream(GZIPInputStream(ByteArrayInputStream(file)).buffered()).use { inp ->
            if (inp.readInt() != MAGIC || inp.readShort().toInt() != VERSION) return null
            val examples = inp.readInt()
            val scale = inp.readFloat()
            val labels = List(inp.readUnsignedShort()) { inp.readUTF() }
            val features = Array(inp.readInt()) { inp.readUTF() }
            val df = IntArray(features.size) { inp.readUnsignedShort() }
            val start = IntArray(features.size + 1)
            val pl = ArrayList<Byte>()
            val pw = ArrayList<Short>()
            for (f in features.indices) {
                start[f] = pl.size
                repeat(inp.readUnsignedByte()) {
                    pl += inp.readByte()
                    pw += inp.readShort()
                }
            }
            start[features.size] = pl.size
            Model(labels, features, df, examples, scale, start, pl.toByteArray(), pw.toShortArray())
        }
    }.getOrNull()

    // ------------------------------------------------------------------------------------------------ reading

    /**
     * How rare a feature is. StrictMath, not Math: Math.log may differ in its last bit from one computer to another, and
     * the learning must come out the same on the owner's CI as where the model was written (HudhudModelTest).
     */
    private fun idfOf(examples: Int, df: Int): Double = StrictMath.log((examples + 1.0) / (df + 1.0)) + 1.0

    private fun kindWeight(feature: String): Double = when (feature[0]) {
        'c' -> W_CONCEPT
        'w' -> W_WORD
        'b' -> W_PAIR
        's' -> W_SOUND
        else -> W_PIECE
    }

    /** The bank's {NAME}, {ITEM} and amounts as the placeholders a question gets. */
    private fun placeholders(text: String): String =
        text.replace("{NAME}", " $NAME ").replace("{ITEM}", " $ITEM ")

    /**
     * What [text] is made of, for comparing: its words, the meanings [HudhudMind] knows for them, each pair of words
     * side by side, each word's sound and its three-letter pieces. Placeholders and amounts are words of their own.
     */
    internal fun features(text: String): Set<String> {
        val said = VoiceQuestion.normalise(text).split(' ').filter { it.isNotEmpty() }
            .map { if (it.all { c -> c.isDigit() }) NUM else it }
        if (said.isEmpty()) return emptySet()
        val plain = said.map { if (it == NAME || it == ITEM || it == NUM) "" else it }
        val m = HudhudMind.read(plain)
        val arabic = HudhudMind.arabicOnly(plain)
        val out = LinkedHashSet<String>()
        said.forEachIndexed { i, w ->
            out += "w:$w"
            if (w == NAME || w == ITEM || w == NUM) return@forEachIndexed
            for (c in m.at(i)) out += "c:" + c.name
            val k = HudhudMind.skeleton(w)
            if (k.length >= 2) out += "s:$k"
            if (w.length >= 4) {
                val b = "#$w#"
                for (j in 0..b.length - 3) out += "g:" + b.substring(j, j + 3)
            }
            // Arabic writes "the" joined to its word ("الرقص", "والمبيعات", "للعملاء"): the word without it is the word
            // too, as HudhudMind reads it (13 Nov 2026, Arabic held out at 83%: "هل تستطيع الرقص" was read as flying
            // because the bank's only "هل تستطيع ال…" was "الطيران"). Only in an Arabic sentence, never Urdu's "الگ".
            if (arabic) articleless(w)?.let { stem ->
                out += "w:$stem"
                val ks = HudhudMind.skeleton(stem)
                if (ks.length >= 2) out += "s:$ks"
                val b = "#$stem#"
                for (j in 0..b.length - 3) out += "g:" + b.substring(j, j + 3)
            }
        }
        for (i in 0 until said.size - 1) out += "b:" + said[i] + "_" + said[i + 1]
        return out
    }

    /** Arabic's article joined in front ("ال", "وال", "بال", "فال", "كال", "لل"), taken off; null when none, or under three letters are left. */
    private fun articleless(w: String): String? {
        val front = ARTICLES.firstOrNull { w.startsWith(it) } ?: return null
        return w.substring(front.length).takeIf { it.length >= 3 }
    }

    /** Longest first, after [VoiceQuestion.normalise] (which writes ك as ک). */
    private val ARTICLES = listOf("وال", "بال", "فال", "کال", "ال", "لل")

    /**
     * The meanings nearest to [heard], best first, at most [top]. [isName] and [isItem] say which words are one of
     * the book's names or products: the question is read twice, as said and with those words as placeholders, and
     * each meaning keeps the surer of the two ("بات" may sound like a customer called Butt, but "بات کر سکتے ہیں" is
     * talking).
     */
    fun rank(heard: String, isName: (String) -> Boolean = { false }, isItem: (String) -> Boolean = { false }, top: Int = 5): List<Guess> {
        val m = current() ?: return emptyList()
        val said = VoiceQuestion.normalise(heard).split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty()) return emptyList()
        val asSaid = scores(m, features(said.joinToString(" ")))
        val swapped = said.map { w -> if (isName(w)) NAME else if (isItem(w)) ITEM else w }
        val best = if (swapped == said) asSaid else {
            val other = scores(m, features(swapped.joinToString(" ")))
            DoubleArray(asSaid.size) { maxOf(asSaid[it], other[it]) }
        }
        return best.indices.sortedByDescending { best[it] }.take(top)
            .filter { best[it] > 0.0 }
            .map { Guess(m.labels[it], best[it]) }
    }

    /** The learnt weights' scores, as shares of one (a softmax): how sure each meaning is beside the others. */
    private fun scores(m: Model, fs: Set<String>): DoubleArray {
        val labels = m.labels.size
        val out = DoubleArray(labels)
        val ids = ArrayList<Int>()
        val raw = ArrayList<Double>()
        for (f in fs) {
            val i = m.vocabulary[f] ?: continue
            ids += i
            raw += kindWeight(f) * m.idf[i]
        }
        if (ids.isEmpty()) return out
        val norm = sqrt(raw.sumOf { it * it })
        for (k in ids.indices) {
            val q = raw[k] / norm
            val f = ids[k]
            for (j in m.start[f] until m.start[f + 1]) out[m.pairLabel[j].toInt()] += m.pairWeight[j] * m.scale * q
        }
        val top = out.maxOrNull() ?: 0.0
        var total = 0.0
        for (l in 0 until labels) { out[l] = exp((out[l] - top) / TEMPERATURE); total += out[l] }
        for (l in 0 until labels) out[l] /= total
        return out
    }
}
