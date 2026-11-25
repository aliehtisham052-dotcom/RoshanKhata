package com.innovation313.roshankhata.data

import java.io.File
import kotlin.math.ceil

/**
 * Hudhud's reading timed on the computer (26 Nov 2026, BC-353): `Hudhud.understand` with the shipped sense and the app's own help
 * pages, over the books of HudhudSpeedOnDeviceTest (the held-out test's nine names; a shop of 1,000 invented names) and larger
 * ones. Run by scripts/shared_harness/speed.sh. REPORT ONLY: the computer is not the phone (CI's emulator was about 20 to 35
 * times slower), and other work on the machine moves the figures; compare two builds in one sitting, never across days.
 * With a fourth argument it writes every reading, so two builds can be held to the same answers.
 */
object HudhudSpeed {
    class Row(val language: String, val meaning: String, val sentence: String)
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
    val GOODS = listOf(
        Hudhud.Name(101, "Urea"), Hudhud.Name(102, "یوریا"), Hudhud.Name(103, "يوريا"), Hudhud.Name(104, "यूरिया"),
        Hudhud.Name(105, "ইউরিয়া"), Hudhud.Name(106, "DAP"), Hudhud.Name(107, "Sugar")
    )
    val small = listOf(
        Hudhud.Name(1, "Ali Raza"), Hudhud.Name(2, "علی رضا"), Hudhud.Name(3, "علي رضا"), Hudhud.Name(4, "अली रज़ा"),
        Hudhud.Name(5, "আলী রাজা"), Hudhud.Name(6, "Bashir Ahmad"), Hudhud.Name(7, "Tariq Butt"), Hudhud.Name(8, "Naeem Khan"),
        Hudhud.Name(9, "Saleem Bhai")
    )
    fun shop(size: Int): List<Hudhud.Name> {
        val more = ArrayList<Hudhud.Name>(); var id = 1000L
        val thirds = listOf("", " Traders", " Co", " Ltd", " Wala", " Shop", " Bazar", " Gali", " Road", " Chowk")
        outer@ for (t in thirds) for (first in FIRST) for (second in SECOND) {
            if (small.size + more.size >= size) break@outer
            more += Hudhud.Name(id++, "$first $second$t")
        }
        return small + more
    }
    fun filled(lang: String, text: String): String {
        val (name, item) = when (lang) {
            "ur", "sd", "fa" -> "علی رضا" to "یوریا"; "ar" -> "علي رضا" to "يوريا"
            "hi" -> "अली रज़ा" to "यूरिया"; "bn" -> "আলী রাজা" to "ইউরিয়া"; else -> "Ali Raza" to "Urea"
        }
        return text.replace("{NAME}", name).replace("{ITEM}", item)
    }
    fun pct(s: DoubleArray, p: Double) = if (s.isEmpty()) 0.0 else s[(ceil(p / 100.0 * s.size).toInt().coerceIn(1, s.size)) - 1]

    @JvmStatic fun main(args: Array<String>) {
        val rows = File(args[0]).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull {
            val p = it.split('\t', limit = 3); if (p.size == 3) Row(p[0], p[1], p[2]) else null }
        HudhudSense.use(HudhudModelTest.shippedModel() ?: error("no sense"))
        val sizes = (args.getOrNull(1) ?: "9,1000").split(',').map { it.toInt() }
        val reps = (args.getOrNull(2) ?: "1").toInt()
        val pages = HashMap<String, List<Hudhud.Topic>>()
        val dump = args.getOrNull(3)
        val out = if (dump != null) File(dump).printWriter() else null
        for (size in sizes) {
            val names = if (size == 9) small else shop(size)
            rows.take(30).forEach { Hudhud.understand(listOf(filled(it.language, it.sentence)), names, pages.getOrPut(it.language) { HudhudRealPages.forApp(it.language) }, it.language, GOODS) }
            val times = ArrayList<Double>()
            repeat(reps) {
                for (r in rows) {
                    val tp = pages.getOrPut(r.language) { HudhudRealPages.forApp(r.language) }
                    val t = System.nanoTime()
                    val got = Hudhud.understand(listOf(filled(r.language, r.sentence)), names, tp, r.language, GOODS)
                    times += (System.nanoTime() - t) / 1e6
                    if (it == 0) out?.println("$size\t${r.language}\t${r.sentence}\t" + got.second.toString().replace(Regex("@[0-9a-f]+"), ""))
                }
            }
            val s = times.toDoubleArray().sortedArray()
            val rt0 = Runtime.getRuntime(); rt0.gc(); Thread.sleep(200); rt0.gc()
            println("  name memos (forms, words) " + Hudhud.nameMemoSizes() + "; heap used %.1f MB".format((rt0.totalMemory() - rt0.freeMemory()) / 1048576.0))
            println("names=${names.size}: n=${s.size} p50 %.1f ms, p95 %.1f ms, p99 %.1f, max %.1f ms, mean %.1f".format(pct(s,50.0), pct(s,95.0), pct(s,99.0), s.last(), s.average()))
        }
        out?.close()
        val rt = Runtime.getRuntime(); rt.gc()
        println("heap used %.1f MB".format((rt.totalMemory() - rt.freeMemory()) / 1048576.0))
    }
}
