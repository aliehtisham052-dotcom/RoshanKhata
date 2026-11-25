package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The training recipe's guards (25 Nov 2026, plan H1, lesson 163).
 *
 *  1. **The features are locked.** [HudhudSense.features] says what a sentence is made of (words, meanings, pairs, sounds, pieces);
 *     the shipped `sense.bin` was learnt on exactly that. A change to it — or to what it reads ([HudhudMind]'s meanings,
 *     [VoiceQuestion.normalise], an article rule) — moves every reading of the sense and is a retrain (lessons 106, 111, 112, 116).
 *     [HudhudModelTest] catches it only when the whole bank is learnt again (a minute); this fails at once, names the sentence and
 *     says what moved. 30 fixed sentences in the nine languages, with each one's feature count and a SHA-256 of its sorted features.
 *     A deliberate feature change: run [main] (prints the table), paste it here, and learn the sense again in the same commit.
 *  2. **An ensemble of one seed is today's sense, byte for byte**, so the shipped asset stays what `train` makes; an ensemble of
 *     several seeds is deterministic and is not any single seed's.
 */
class HudhudRecipeTest {

    private fun digest(fs: Set<String>): String =
        MessageDigest.getInstance("SHA-256").digest(fs.sorted().joinToString("\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)

    @Test fun `the features of thirty fixed sentences in nine languages do not change`() {
        assertEquals("the table must hold 30 sentences", 30, SENTENCES.size)
        val moved = ArrayList<String>()
        for ((lang, text) in SENTENCES) {
            val fs = HudhudSense.features(text)
            val want = LOCK[text]
            val got = "${fs.size}:${digest(fs)}"
            if (want != got) moved += "  [$lang] $text: locked $want, now $got"
        }
        assertTrue(
            "${moved.size} of ${SENTENCES.size} sentences are made of other features now. If that is deliberate it is a retrain: run " +
                "HudhudRecipeTest.main, paste its table, learn sense.bin again (HudhudModelTest.main) and re-check every floor and guard " +
                "(docs/hudhud-recipe-25nov2026.md):\n" + moved.joinToString("\n"), moved.isEmpty()
        )
    }

    @Test fun `the lock holds the weights of the constants too`() {
        // How a feature kind counts, which pieces are kept and how long the learning runs are part of what a sense is.
        assertEquals(listOf(2.0, 1.6, 1.3, 1.0, 0.35), listOf(
            HudhudSense.W_CONCEPT, HudhudSense.W_WORD, HudhudSense.W_PAIR, HudhudSense.W_SOUND, HudhudSense.W_PIECE))
        assertEquals(3, HudhudSense.MIN_DF)
        assertEquals(12, HudhudSense.EPOCHS)
        assertEquals(313L, HudhudSense.SEED)
        assertEquals(0.1, HudhudSense.TEMPERATURE, 0.0)
    }

    private val small: List<String> by lazy {
        // every ninth line of the bank: enough meanings to learn, quick to learn five times
        HudhudModelTest.bank().filterIndexed { i, _ -> i % 9 == 0 }
    }

    private fun bytes(m: HudhudSense.Model) = HudhudSense.encode(m)

    @Test fun `an ensemble of one seed is the single seed's sense byte for byte`() {
        assertTrue(small.size > 1000)
        assertArrayEquals(bytes(HudhudSense.train(small, 314L)), bytes(HudhudSense.trainEnsemble(small, listOf(314L))))
        assertArrayEquals(bytes(HudhudSense.train(small)), bytes(HudhudSense.trainEnsemble(small, listOf(HudhudSense.SEED))))
    }

    @Test fun `an ensemble of several seeds is the same every time and no single seed's sense`() {
        val seeds = listOf(11L, 12L, 13L)
        val a = bytes(HudhudSense.trainEnsemble(small, seeds))
        val b = bytes(HudhudSense.trainEnsemble(small, seeds))
        assertArrayEquals("learning the same seeds twice gave two senses", a, b)
        for (s in seeds) assertFalse("the mean is one seed's sense", a.contentEquals(bytes(HudhudSense.train(small, s))))
        // Same meanings, same pieces: only the numbers are the mean.
        val one = HudhudSense.train(small, 11L)
        val many = HudhudSense.trainEnsemble(small, seeds)
        assertEquals(one.labels, many.labels)
        assertTrue(one.features.contentEquals(many.features))
        assertTrue(one.df.contentEquals(many.df))
    }

    /** Feature f's weight for label l as the model keeps it (whole number times scale; nought when not kept). */
    private fun weightOf(m: HudhudSense.Model, f: Int, l: Int): Double {
        for (k in m.start[f] until m.start[f + 1]) if (m.pairLabel[k].toInt() == l) return m.pairWeight[k] * m.scale.toDouble()
        return 0.0
    }

    @Test fun `an ensemble's weights are the mean of its seeds' weights`() {
        val seeds = listOf(31L, 32L, 33L)
        val one = seeds.map { HudhudSense.train(small, it) }
        val many = HudhudSense.trainEnsemble(small, seeds)
        // Every model keeps weights to one part in 32,767 of its own largest; the mean is judged at that grain, not tighter.
        val grain = (one.map { it.scale.toDouble() } + many.scale.toDouble()).sum()
        var checked = 0
        var worst = 0.0
        for (f in many.features.indices step 7) for (l in many.labels.indices step 5) {
            val mean = one.sumOf { weightOf(it, f, l) } / one.size
            worst = maxOf(worst, Math.abs(weightOf(many, f, l) - mean))
            checked++
        }
        assertTrue("too few weights compared: $checked", checked > 2000)
        assertTrue("the ensemble's weights are not the mean: worst difference $worst, grain $grain", worst <= grain)
        // and the mean is a real mean: a weight that one seed made large is pulled towards the others
        var pulled = 0
        for (f in many.features.indices step 3) for (l in many.labels.indices step 3) {
            val w = one.map { weightOf(it, f, l) }
            if (w.max() - w.min() > 20 * grain && weightOf(many, f, l) < w.max() - 2 * grain && weightOf(many, f, l) > w.min() + 2 * grain) pulled++
        }
        assertTrue("no weight sits between its seeds' weights ($pulled)", pulled > 0)
    }

    @Test fun `an ensemble reads back from its file like any sense`() {
        val m = HudhudSense.trainEnsemble(small, listOf(21L, 22L))
        val again = HudhudSense.decode(HudhudSense.encode(m))!!
        assertTrue(m.pairWeight.contentEquals(again.pairWeight) && m.pairLabel.contentEquals(again.pairLabel) && m.start.contentEquals(again.start))
    }

    @Test fun `an ensemble needs a seed`() {
        var refused = false
        try { HudhudSense.trainEnsemble(small, emptyList()) } catch (e: IllegalArgumentException) { refused = true }
        assertTrue(refused)
    }

    companion object {
        /** language, sentence: invented, in the bank's own shape (qqname and qqitem are its placeholders). */
        val SENTENCES: List<Pair<String, String>> = listOf(
            "en" to "how much does qqname owe me",
            "en" to "show me the sales of last month",
            "en" to "ignore the 500 rupees entry",
            "en" to "what can you tell me",
            "ur-Latn" to "qqname ka hisaab batao",
            "ur-Latn" to "aaj ki wusooli kitni hui",
            "ur-Latn" to "mujhe 500 rupay qqname ko dene hain",
            "ur" to "علی کا حساب بتاؤ",
            "ur" to "آج کی فروخت کتنی ہوئی",
            "ur" to "کیا آپ مجھ سے بات کر سکتے ہیں",
            "sd" to "اڄ جي وڪري ڪيتري ٿي",
            "sd" to "qqname جو حساب ڏيکاريو",
            "ar" to "كم يدين لي العملاء",
            "ar" to "أظهر لي المبيعات والمشتريات",
            "ar" to "هل تستطيع الرقص",
            "ar" to "للعملاء الجدد",
            "fa" to "امروز چه گذشت",
            "fa" to "بدهی مشتریان چقدر است",
            "fa" to "فروش ماه گذشته را نشان بده",
            "hi" to "आज की बिक्री कितनी हुई",
            "hi" to "मुझे qqname से पैसे लेने हैं",
            "hi" to "कौन सा माल कम बचा है",
            "hi" to "किसने कितना दिया",
            "bn" to "আজকের বিক্রি কত",
            "bn" to "qqname আমাকে কত টাকা দেবে",
            "bn" to "কোন পণ্য কম আছে",
            "bn" to "ব্যাকআপ কিভাবে নেব",
            "id" to "berapa total penjualan hari ini",
            "id" to "siapa yang belum bayar",
            "id" to "stok qqitem tinggal berapa"
        )

        /** sentence -> "feature count:first 16 hex of the SHA-256 of the sorted features joined by newlines". */
        val LOCK: Map<String, String> = mapOf(
            "how much does qqname owe me" to "23:de0c428cc46c2a4e",
            "show me the sales of last month" to "36:b59d8bac117334e7",
            "ignore the 500 rupees entry" to "31:a2f9a81ba76c5303",
            "what can you tell me" to "24:ab64c0366ae314c2",
            "qqname ka hisaab batao" to "22:f26a7b2692adae6d",
            "aaj ki wusooli kitni hui" to "26:fe741790aeab6d29",
            "mujhe 500 rupay qqname ko dene hain" to "37:e4ec9a9669d52758",
            "علی کا حساب بتاؤ" to "19:fe9562de41c6c780",
            "آج کی فروخت کتنی ہوئی" to "26:b8cf76d7996aecb9",
            "کیا آپ مجھ سے بات کر سکتے ہیں" to "28:f927d9ef7d0c8e54",
            "اڄ جي وڪري ڪيتري ٿي" to "22:8cc591f83b00fd24",
            "qqname جو حساب ڏيکاريو" to "21:7d2a13e9b2ed8906",
            "كم يدين لي العملاء" to "27:c7da56f429b35843",
            "أظهر لي المبيعات والمشتريات" to "36:9e184f8a39759fbc",
            "هل تستطيع الرقص" to "23:864952ce9534edd7",
            "للعملاء الجدد" to "24:5b4908be6e97442f",
            "امروز چه گذشت" to "18:4eaf64e4770d5240",
            "بدهی مشتریان چقدر است" to "28:a5701d9e1a185cbb",
            "فروش ماه گذشته را نشان بده" to "29:2edc2d1e01af6b36",
            "आज की बिक्री कितनी हुई" to "24:3d5dd5cd39fff578",
            "मुझे qqname से पैसे लेने हैं" to "29:975ec371963a619c",
            "कौन सा माल कम बचा है" to "20:12d39557afb0ae27",
            "किसने कितना दिया" to "23:0a6a5813f24b1921",
            "আজকের বিক্রি কত" to "20:647598e72b51afb8",
            "qqname আমাকে কত টাকা দেবে" to "29:3d30205bcd8137ba",
            "কোন পণ্য কম আছে" to "21:840996ef17244615",
            "ব্যাকআপ কিভাবে নেব" to "21:8c66fb4e78adf9b7",
            "berapa total penjualan hari ini" to "40:d67e622d62d21bad",
            "siapa yang belum bayar" to "32:82338a46f21f4975",
            "stok qqitem tinggal berapa" to "30:07603fe7d03a1e8a"
        )

        /** Prints the table to paste into [LOCK] after a deliberate change of the features. */
        @JvmStatic
        fun main(args: Array<String>) {
            val t = HudhudRecipeTest()
            for ((_, text) in SENTENCES) {
                val fs = HudhudSense.features(text)
                println("            \"$text\" to \"${fs.size}:${t.digest(fs)}\",")
            }
        }
    }
}
