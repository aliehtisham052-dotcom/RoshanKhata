package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The label map's reading columns (Marhala 6, 26 Nov 2026; BC-380): for every label of docs/hudhud-label-map.tsv, what
 * [Hudhud.askFor] makes of it for one sentence that names a customer, an item and an amount — spelt as [HudhudShown] spells a
 * reading, the spelling the owner's lists and review sets are labelled in — and what [Hudhud.labelOf] gives back for that
 * reading. A change to askFor or labelOf that moves a label's reading fails here until the map says so;
 * scripts/check_label_map.py holds the rest of the map (orphans, the Formula Book, the screens).
 *
 * To write the two columns after a change that is meant: LABEL_MAP_WRITE=1 scripts/shared_harness/run.sh, then read the diff.
 */
class HudhudLabelMapTest {

    private val names = listOf(Hudhud.Name(1L, "Ali Raza"))
    private val goods = listOf(Hudhud.Name(7L, "Urea"))
    /** One customer, one item and an amount, and no word of a span or a side: each label's plainest reading. */
    private val sentence = "Ali Raza Urea 500"

    private fun mapFile(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "docs/hudhud-label-map.tsv")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        throw AssertionError("docs/hudhud-label-map.tsv not found from " + File(".").absolutePath)
    }

    private fun readingOf(label: String): Pair<String, String> {
        val ask = Hudhud.askFor(label, sentence, names, emptyList(), "ur-Latn", goods) ?: return "-" to "-"
        return HudhudShown.of(ask) to (Hudhud.labelOf(ask) ?: "-")
    }

    @Test
    fun everyLabelsReadingIsAsTheMapSays() {
        val file = mapFile()
        val lines = file.readLines()
        val head = lines.first { !it.startsWith("#") && it.isNotBlank() }.split("\t")
        val label = head.indexOf("label")
        val reading = head.indexOf("reading")
        val back = head.indexOf("reads_back")
        assertTrue("the map's columns: $head", label >= 0 && reading >= 0 && back >= 0)
        val write = System.getenv("LABEL_MAP_WRITE") == "1"
        val out = ArrayList<String>()
        val wrong = ArrayList<String>()
        var seenHead = false
        for (line in lines) {
            if (line.startsWith("#") || line.isBlank() || !seenHead) {
                if (!line.startsWith("#") && line.isNotBlank()) seenHead = true
                out += line
                continue
            }
            val cells = line.split("\t").toMutableList()
            val (r, b) = readingOf(cells[label])
            if (cells[reading] != r || cells[back] != b) {
                wrong += cells[label] + ": the map says " + cells[reading] + " / " + cells[back] + ", askFor makes " + r + " / " + b
            }
            cells[reading] = r
            cells[back] = b
            out += cells.joinToString("\t")
        }
        if (write) {
            file.writeText(out.joinToString("\n") + "\n")
            return
        }
        assertEquals("label map readings (LABEL_MAP_WRITE=1 rewrites them)", emptyList<String>(), wrong)
    }

    /**
     * Every label reads back as itself but one: help with no page found is the abilities list. A new exchange fails here.
     */
    @Test
    fun aLabelReadsBackAsItselfOrIsAKnownExchange() {
        val lines = mapFile().readLines().filter { !it.startsWith("#") && it.isNotBlank() }
        val head = lines.first().split("\t")
        // No help page in the test's topics: the abilities list (Hudhud.askFor's own fallback).
        val known = setOf("help_howto")
        val odd = lines.drop(1).map { it.split("\t") }
            .filter { it[head.indexOf("reads_back")] != it[head.indexOf("label")] }
            .map { it[head.indexOf("label")] }
            .filter { it !in known }
        assertEquals("labels whose reading gives back another label", emptyList<String>(), odd)
    }
}
