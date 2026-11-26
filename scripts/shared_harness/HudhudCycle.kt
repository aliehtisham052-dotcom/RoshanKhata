package com.innovation313.roshankhata.data

import java.io.File

/**
 * The training cycle's measuring step (plan v1, Marhala 8, steps 6, 7 and 9; scripts/hudhud_cycle.sh, 26 Nov 2026).
 *
 * A program, not a test: it learns the CANDIDATE sense from a candidate bank (the shipped recipe, [HudhudSense.trainShipped],
 * seeds 313..327, pruned) and writes it to the build folder; learns the base bank and the candidate bank again under two other
 * 15-seed ensembles (lesson 163: a gain or a loss that one shuffle makes is noise); reads every labelled set with each of the
 * six senses through [HudhudBaseline.pass] (the sets' own tests' reading); and writes, for scripts/hudhud_cycle.py to judge:
 *
 *   figures.tsv          model, ensemble, metric, value  (right counts per set, totals, the entity WRONG counts per set)
 *   rows-M-E.tsv         set, row key, right (1/0), reading shown — every row, sorted (the miss lists are taken from these)
 *   sense-candidate.bin  the candidate sense, byte for byte what HudhudModelTest.main writes from the same bank
 *
 * It never writes the repository: the shipped sense.bin is only read (--base-sense), the banks are files in build/.
 *
 *   --base-bank F --cand-bank F --base-sense F --out DIR [--others 1000,2000]
 */
object HudhudCycle {
    private val SETS = listOf("heldout", "owner_set", "owner_1000", "review_d", "review_o")
    private val SERIES = listOf("heldout strict", "heldout lenient", "owner_set", "owner_1000", "review_d", "review_o")

    private fun ensemble(first: Long) = (first until first + 15L).toList()

    @JvmStatic
    fun main(args: Array<String>) {
        fun arg(n: String) = args.indexOf(n).takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }
        val baseLines = File(arg("--base-bank") ?: error("--base-bank FILE")).readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        val candLines = File(arg("--cand-bank") ?: error("--cand-bank FILE")).readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        val baseSense = File(arg("--base-sense") ?: error("--base-sense FILE (the shipped sense.bin)"))
        val out = File(arg("--out") ?: error("--out DIR")).also { it.mkdirs() }
        val others = (arg("--others") ?: "1000,2000").split(',').filter { it.isNotBlank() }.map { it.trim().toLong() }
        val started = System.nanoTime()
        fun took() = "%.0f s".format((System.nanoTime() - started) / 1e9)

        if (candLines == baseLines) {
            File(out, "figures.tsv").writeText("# no change: the candidate bank is the base bank line for line\n")
            println("no change: the candidate bank is the base bank line for line; nothing learnt")
            return
        }
        val shipped = HudhudSense.decode(baseSense.readBytes()) ?: error("${baseSense.path} is not a sense")

        val figures = StringBuilder("model\tensemble\tmetric\tvalue\n")
        fun measure(model: String, tag: String, m: HudhudSense.Model) {
            System.err.println("[${took()}] reading every set with the $model sense, ensemble $tag")
            HudhudSense.use(m)
            val rows = ArrayList<String>()
            val f = HudhudBaseline.pass(timing = false, collectConfusion = false) { set, key, shown, right ->
                rows += "$set\t$key\t${if (right) 1 else 0}\t$shown"
            }
            for (s in SERIES) {
                val (r, t) = f.total(s)
                figures.append("$model\t$tag\t$s\t$r\n$model\t$tag\t$s total\t$t\n")
            }
            for (set in SETS) for (e in listOf(HudhudBaseline.Ent.SIDE, HudhudBaseline.Ent.AMOUNT, HudhudBaseline.Ent.PERSON, HudhudBaseline.Ent.PRODUCT)) {
                figures.append("$model\t$tag\twrong ${e.name.lowercase()} $set\t${f.entTotal(set, e, HudhudBaseline.Out.WRONG)}\n")
            }
            File(out, "rows-$model-$tag.tsv").writeText(rows.sorted().joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        }

        try {
            // The candidate, by the shipped recipe: this is the file --apply would ship (HudhudModelTest.main writes the same bytes).
            System.err.println("[${took()}] learning the candidate sense (seeds ${HudhudSense.SHIPPED_SEEDS.first()}..${HudhudSense.SHIPPED_SEEDS.last()}, prune ${HudhudSense.SHIPPED_PRUNE})")
            val cand = HudhudSense.trainShipped(candLines)
            val bytes = HudhudSense.encode(cand)
            File(out, "sense-candidate.bin").writeBytes(bytes)
            figures.append("candidate\t313\tsense bytes\t${bytes.size}\nbase\t313\tsense bytes\t${baseSense.length()}\n")
            measure("base", "313", shipped)
            measure("candidate", "313", cand)
            for (first in others) {
                System.err.println("[${took()}] learning both banks under the ensemble $first..${first + 14}")
                measure("base", "$first", HudhudSense.trainEnsemble(baseLines, ensemble(first), HudhudSense.SHIPPED_PRUNE))
                measure("candidate", "$first", HudhudSense.trainEnsemble(candLines, ensemble(first), HudhudSense.SHIPPED_PRUNE))
            }
        } finally {
            HudhudSense.use(shipped)
        }
        File(out, "figures.tsv").writeText(figures.toString(), Charsets.UTF_8)
        println("measured: base and candidate under ensembles 313${others.joinToString("") { ", $it" }}; ${candLines.size - baseLines.size} more bank lines; whole run ${took()}")
    }
}
