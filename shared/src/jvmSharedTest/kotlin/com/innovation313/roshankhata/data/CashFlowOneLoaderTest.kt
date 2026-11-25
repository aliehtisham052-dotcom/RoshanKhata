package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * One cash-flow loader (25 Nov 2026, BC-285): the phone's and the computer's Cash flow page, the bell's cash gap and the
 * assistant's "next 30 days" each built the plans, cheques, promised days and bills by hand, four copies of one rule.
 */
class CashFlowOneLoaderTest {
    private fun root(): File = listOf(File(".."), File(".")).first { File(it, "app/src/main").isDirectory }

    private val readers = listOf(
        "app/src/main/java/com/innovation313/roshankhata/CashFlowActivity.kt",
        "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/CashFlowPane.kt",
        "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/LedgerAlerts.kt",
        "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/AnswerAdvisor.kt"
    )

    @Test fun `every cash-flow reader calls the one loader`() {
        for (path in readers) {
            val text = File(root(), path).readText().replace("\r\n", "\n")
            assertTrue("$path does not use CashFlow.load", text.contains("CashFlow.load("))
        }
    }

    @Test fun `no file but the loader builds the plans for the cash flow itself`() {
        val copies = root().resolve("app/src/main").walkTopDown()
            .plus(root().resolve("desktop/src/main").walkTopDown())
            .plus(root().resolve("shared/src/jvmSharedMain").walkTopDown())
            .filter { it.isFile && it.extension == "kt" && it.name != "CashFlow.kt" }
            .filter { it.readText().contains("CashFlow.Plan(") }
            .map { it.name }.toList()
        assertEquals("a second copy of the cash-flow loader", emptyList<String>(), copies)
    }
}
