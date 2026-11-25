package com.innovation313.roshankhata

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudBook
import com.innovation313.roshankhata.data.HudhudSense
import com.innovation313.roshankhata.data.HudhudSenseState
import java.util.Locale
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long Hudhud takes to read a question on a real Android (Marhala 2 of docs/hudhud-master-plan-v1.md: the phone
 * speed table, p50/p95/p99, and memory). REPORT ONLY: no budget, nothing fails for being slow. It fails only on an
 * error — the sample missing, the learnt sense not loading (a rules-only reading would be timed as if it were Hudhud),
 * or a question that throws.
 *
 * What is timed is the very call his sheet makes for a question (HudhudSheet.question): `Hudhud.understand(candidates,
 * names, topics, language, goods)` — the rules and the sense, off the screen's thread — with the app's real help pages
 * (`HudhudBook.topics`). The book's reads and the card (`HudhudBook.answer`) are not in it; BigBookPerfTest times those.
 * The sentences are the held-out set's sample (`androidTest/assets/hudhud/speed_sample.tsv`, written by
 * scripts/hudhud_speed_sample.py; the test APK only, never the app), asked against two made-up books (lesson 80): the
 * held-out test's own (nine names in their scripts, seven goods) and a shop's size (1,000 invented customers).
 *
 * The figures are logged under [TAG], one line each, and instrumented.yml puts them in the run's annotations. **On CI
 * they are the emulator's, not the owner's phone** (an Oppo Reno 11F): an x86_64 emulator without the phone's big
 * cores, so they say how the cost moves from build to build, not what the owner feels. His phone's own figures come
 * from running this class on it over USB (docs/hudhud-field-test-guide.md).
 */
@RunWith(AndroidJUnit4::class)
class HudhudSpeedOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class Row(val language: String, val meaning: String, val sentence: String)

    private class Pass(val label: String, val timesMs: DoubleArray, val asked: Int, val stoppedEarly: Boolean)

    @Test
    fun hudhudsReadingIsTimed() {
        val rows = sample()
        assertTrue("the speed sample (androidTest assets $SAMPLE) is missing or empty", rows.isNotEmpty())
        Log.i(TAG, where())
        val topics = HudhudBook.topics(context)
        val small = smallBook()
        val shop = shopBook()

        // The model as the app installs it (RoshanKhataApp.onCreate), read again from its asset so its load is timed cold
        // even when another test of this process asked Hudhud first. The same bytes, the same reader: nothing changes in
        // how he reads.
        gc()
        val heapBefore = usedHeapMb()
        HudhudSense.useModel(HudhudSenseState.reading(MODEL) { context.assets.open(MODEL).use { it.readBytes() } })
        val loadStart = SystemClock.elapsedRealtimeNanos()
        HudhudSense.warmUp()
        val loadMs = msSince(loadStart)
        val mode = HudhudSenseState.check()
        gc()
        val heapWithModel = usedHeapMb()
        Log.i(
            TAG,
            "sense $mode; model load (read $MODEL + decode, cold) ${f(loadMs)} ms; Java heap ${f(heapBefore)} -> " +
                "${f(heapWithModel)} MB (+${f(heapWithModel - heapBefore)})"
        )
        assertEquals("Hudhud's learnt sense did not load (${HudhudSenseState.reason}): the timings would be of the rules alone",
            HudhudSenseState.Mode.WITH_SENSE, mode)

        val errors = ArrayList<String>()
        val firstStart = SystemClock.elapsedRealtimeNanos()
        ask(rows.first(), small, topics, errors)
        Log.i(TAG, "first question after the load: ${f(msSince(firstStart))} ms (not in the table)")

        var peak = usedHeapMb()
        val passes = ArrayList<Pass>()
        for ((label, book) in listOf("small book (9 names, 7 goods)" to small, "shop book (1,000 names, 7 goods)" to shop)) {
            // Warm-up: the first sentences again, untimed, so the table is of a reader the JIT has seen.
            rows.take(WARM_UP).forEach { ask(it, book, topics, errors) }
            val times = DoubleArray(rows.size)
            var n = 0
            val passStart = SystemClock.elapsedRealtime()
            for (r in rows) {
                if (SystemClock.elapsedRealtime() - passStart > PASS_CAP_MS) break
                val t = SystemClock.elapsedRealtimeNanos()
                ask(r, book, topics, errors)
                times[n++] = msSince(t)
                if (n % 16 == 0) peak = maxOf(peak, usedHeapMb())
            }
            peak = maxOf(peak, usedHeapMb())
            passes += Pass(label, times.copyOf(n), n, n < rows.size)
        }

        for (p in passes) {
            val sorted = p.timesMs.sortedArray()
            val line = "${p.label}: n=${p.asked} p50 ${f(pct(sorted, 50.0))} ms, p95 ${f(pct(sorted, 95.0))} ms, " +
                "p99 ${f(pct(sorted, 99.0))} ms, max ${f(sorted.lastOrNull() ?: 0.0)} ms, " +
                "mean ${f(if (sorted.isEmpty()) 0.0 else sorted.average())} ms" +
                if (p.stoppedEarly) " (stopped at the ${PASS_CAP_MS / 1000} s cap: the first ${p.asked} of ${rows.size})" else ""
            Log.i(TAG, line)
        }
        val rt = Runtime.getRuntime()
        Log.i(
            TAG,
            "memory: Java heap peak ${f(peak)} MB sampled during the passes, the app's limit ${f(rt.maxMemory() / MB)} MB; " +
                "native heap ${f(Debug.getNativeHeapAllocatedSize() / MB)} MB"
        )
        Log.i(TAG, "errors: ${errors.size}" + if (errors.isEmpty()) "" else " — " + errors.take(3).joinToString("; "))
        assertTrue("${errors.size} questions threw while being read: ${errors.take(3)}", errors.isEmpty())
    }

    /** One question as the sheet asks it: the recogniser's (here the only) candidate, the book, the pages, the language. */
    private fun ask(r: Row, book: Book, topics: List<Hudhud.Topic>, errors: MutableList<String>) {
        try {
            Hudhud.understand(listOf(filled(r.language, r.sentence)), book.names, topics, r.language, book.goods)
        } catch (e: Exception) {
            if (errors.size < 50) errors += "${r.language}/${r.meaning}: ${e.javaClass.simpleName}"
        }
    }

    private class Book(val names: List<Hudhud.Name>, val goods: List<Hudhud.Name>)

    /** The held-out test's made-up book (HudhudSenseTest): the same person and product in each script a question may use. */
    private fun smallBook() = Book(
        listOf(
            Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(3, "علي رضا"), Hudhud.Name(4, "अली रज़ा"),
            Hudhud.Name(5, "আলী রাজা"), Hudhud.Name(6, "Bashir Ahmad"), Hudhud.Name(7, "Tariq Butt"), Hudhud.Name(8, "Naeem Khan"),
            Hudhud.Name(9, "Saleem Bhai")
        ),
        GOODS
    )

    /** A shop's size: the small book's nine and 991 more invented customers, first names by second names in order. */
    private fun shopBook(): Book {
        val small = smallBook().names
        val more = ArrayList<Hudhud.Name>()
        var id = 1000L
        outer@ for (first in FIRST) for (second in SECOND) {
            if (small.size + more.size >= SHOP_SIZE) break@outer
            more += Hudhud.Name(id++, "$first $second")
        }
        return Book(small + more, GOODS)
    }

    /** The held-out test's own filling of the placeholders (HudhudSenseTest.filled). */
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

    /** The sample from the test APK's own assets (not the app's). */
    private fun sample(): List<Row> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val lines = runCatching { assets.open(SAMPLE).bufferedReader(Charsets.UTF_8).use { it.readLines() } }.getOrDefault(emptyList())
        return lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3) Row(parts[0], parts[1], parts[2]) else null
        }
    }

    /** Which Android this is, said first so nobody reads an emulator's figures as the owner's phone. */
    private fun where(): String {
        val emulator = Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.PRODUCT.contains("sdk")
        val what = "${Build.MANUFACTURER} ${Build.MODEL}, Android API ${Build.VERSION.SDK_INT}, ${Build.SUPPORTED_ABIS.firstOrNull()}, " +
            "${Runtime.getRuntime().availableProcessors()} cores"
        return if (emulator) "EMULATOR figures, NOT the owner's phone (Oppo Reno 11F): $what"
        else "real device: $what (the owner's phone only if this is his Oppo Reno 11F)"
    }

    private fun pct(sorted: DoubleArray, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun msSince(startNanos: Long): Double = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0

    private fun usedHeapMb(): Double = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / MB }

    private fun gc() {
        Runtime.getRuntime().gc()
        SystemClock.sleep(100)
    }

    private fun f(x: Double): String = String.format(Locale.US, "%.1f", x)

    private companion object {
        /** The log tag instrumented.yml reads ("Hudhud speed" annotation). Not used in any message text. */
        const val TAG = "HudhudSpeed"
        const val SAMPLE = "hudhud/speed_sample.tsv"
        const val MODEL = "hudhud/sense.bin"
        const val WARM_UP = 30
        const val SHOP_SIZE = 1000

        /** A pass stops here and says so: a slow emulator must not take the whole on-device run with it. */
        const val PASS_CAP_MS = 120_000L
        const val MB = 1024.0 * 1024.0

        val GOODS = listOf(
            Hudhud.Name(101, "Urea"), Hudhud.Name(102, "یوریا"), Hudhud.Name(103, "يوريا"), Hudhud.Name(104, "यूरिया"),
            Hudhud.Name(105, "ইউরিয়া"), Hudhud.Name(106, "DAP"), Hudhud.Name(107, "Sugar")
        )

        /** Invented customers for the shop-sized book: 40 x 25 made-up pairs (lesson 80), Latin letters as most books type them. */
        val FIRST = listOf(
            "Zarnosh", "Qadeer", "Waleed", "Shahzeb", "Arsalan", "Danish", "Fahad", "Haris", "Ibad", "Junaid",
            "Kashif", "Luqman", "Moiz", "Nabeel", "Owais", "Rameez", "Sarmad", "Taimoor", "Umair", "Wajid",
            "Yasir", "Zeeshan", "Adeel", "Basit", "Daniyal", "Ehsan", "Faraz", "Ghazanfar", "Hammad", "Irfan",
            "Jawad", "Kamil", "Laeeq", "Mudassar", "Nauman", "Obaid", "Qasim", "Rizwan", "Sohaib", "Talha"
        )
        val SECOND = listOf(
            "Store", "Kiryana", "Sons", "Brothers", "Mart", "Agency", "Centre", "Depot", "House", "Point",
            "Sahab", "Bhai", "Chaudhry", "Malik", "Sheikh", "Mirza", "Qureshi", "Siddiqui", "Ansari", "Farooqi",
            "Hashmi", "Jafri", "Naqvi", "Rizvi", "Zaidi"
        )
    }
}
