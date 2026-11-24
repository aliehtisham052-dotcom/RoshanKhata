package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.Hudhud.Ask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 24 Nov 2026, the catalog's last GAPs (plan H6, importance 6). The catalog now says three things Hudhud tells through an
 * ask that already existed; each is held here two ways (lesson 145): the question that reaches it is read by the whole
 * pipeline with the shipped sense, and the reader's own source still holds the gate and the column the catalog names.
 * What the cards then say is the phone's Robolectric test (HudhudCatalogLastGapsOnPhoneTest). Invented names only.
 */
class HudhudCatalogLastGapsTest {

    private val names = listOf(Hudhud.Name(2, "Bashir Test"))
    private fun ask(s: String, lang: String? = null): Ask {
        HudhudSense.use(HudhudModelTest.shippedModel())
        return Hudhud.understand(s, names, emptyList(), lang)
    }

    private val dir = "src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/"
    private fun source(name: String): String {
        val f = listOf(File(dir + name), File("shared/" + dir + name)).first { it.exists() }
        return f.readText()
    }

    @Test fun `the cashbook's writer rides on the cashbook question`() {
        assertTrue(ask("cashbook mein aaj kya aaya kya gaya", "ur-Latn") is Ask.Cashbook)
        assertTrue(ask("what did the cashbook take in today", "en") is Ask.Cashbook)
    }

    @Test fun `the seal's day rides on the backup question`() {
        assertEquals(Ask.BackupStatus, ask("backup kab hua", "ur-Latn"))
        assertEquals(Ask.BackupStatus, ask("when was the last backup", "en"))
    }

    @Test fun `the radar's state rides on the when-will-he-pay question`() {
        assertEquals(Ask.WhenPays(listOf(2L)), ask("Bashir Test kab dega", "ur-Latn"))
        assertEquals(Ask.WhenPays(listOf(2L)), ask("when will Bashir Test pay", "en"))
    }

    // The readers the catalog names must still name the column and ask the gate (the script checks the same, this keeps the
    // pair from drifting if the script's rule is ever loosened).
    @Test fun `the cashbook reader names the writer and asks the reports gate`() {
        val body = source("AnswerFacts.kt").substringAfter("suspend fun cashbook(").substringBefore("suspend fun returns(")
        assertTrue(body.contains("createdBy"))
        assertTrue(body.contains("reports()"))
    }

    @Test fun `the seal reader asks the owner gate and reads only the head`() {
        val body = source("AnswerBook.kt").substringAfter("private suspend fun sealNote(").substringBefore("private suspend fun drawer(")
        assertTrue(body.contains("AnswerGate.settings()"))
        assertTrue(body.contains("latestSealHead()"))
        assertTrue(body.contains("sealedAt") && body.contains("leafCount"))
        assertTrue("never the leaves or the signed tokens", !body.contains("latestSeal()") && !body.contains(".tokens") && !body.contains(".leaves"))
    }
}
