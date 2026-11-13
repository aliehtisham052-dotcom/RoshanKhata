package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The sense the app ships (app/src/main/assets/hudhud/sense.bin) is the one the bank teaches (13 Nov 2026): it is
 * learnt again here from the bank (resources/hudhud/bank.tsv, scripts/gen_hudhud_bank.py) and the two are compared
 * field by field, weight by weight. A bank changed without the model, or a change to how sentences are read
 * ([HudhudSense.features], [HudhudMind], [VoiceQuestion.normalise]) without learning again, fails here — the phone
 * would otherwise read questions with a sense the tests never measured.
 *
 * To learn it again after such a change: run [main] below with the repository as the working directory (it writes the
 * asset), then run the tests.
 */
class HudhudModelTest {

    @Test fun `the shipped sense is the one the bank teaches`() {
        val shipped = shippedModel()
        assertNotNull("the app's asset hudhud/sense.bin is missing or unreadable", shipped)
        val learnt = HudhudSense.train(bank())
        val m = shipped!!
        assertEquals("meanings", learnt.labels, m.labels)
        assertEquals("examples", learnt.examples, m.examples)
        assertEquals("pieces", learnt.features.size, m.features.size)
        assertTrue("pieces differ: learn the sense again (HudhudModelTest.main)", learnt.features.contentEquals(m.features))
        assertTrue("how rare each piece is differs", learnt.df.contentEquals(m.df))
        assertEquals("scale", learnt.scale.toDouble(), m.scale.toDouble(), 0.0)
        assertTrue("weights differ: learn the sense again (HudhudModelTest.main)",
            learnt.start.contentEquals(m.start) && learnt.pairLabel.contentEquals(m.pairLabel) && learnt.pairWeight.contentEquals(m.pairWeight))
    }

    @Test fun `the file reads back as it was written`() {
        val m = HudhudSense.train(bank().take(800))
        val again = HudhudSense.decode(HudhudSense.encode(m))!!
        assertEquals(m.labels, again.labels)
        assertTrue(m.features.contentEquals(again.features))
        assertTrue(m.pairWeight.contentEquals(again.pairWeight))
        assertTrue(m.pairLabel.contentEquals(again.pairLabel))
        assertTrue(m.start.contentEquals(again.start))
    }

    @Test fun `a file that is not a model is no model`() {
        assertEquals(null, HudhudSense.decode(ByteArray(10) { it.toByte() }))
        assertEquals(null, HudhudSense.decode(ByteArray(0)))
    }

    companion object {
        /** The bank the tests learn from. */
        fun bank(): List<String> =
            HudhudModelTest::class.java.getResourceAsStream("/hudhud/bank.tsv")?.bufferedReader(Charsets.UTF_8)?.readLines()
                ?.filter { it.isNotBlank() }.orEmpty()

        /** The repository's root: the tests run in the module's folder, a program wherever it is started. */
        private fun root(): File {
            var dir: File? = File("").absoluteFile
            while (dir != null) {
                if (File(dir, "settings.gradle.kts").isFile && File(dir, "app").isDirectory) return dir
                dir = dir.parentFile
            }
            return File("").absoluteFile
        }

        fun asset(): File = File(root(), "app/src/main/assets/hudhud/sense.bin")

        /** The model the app ships, or null. */
        fun shippedModel(): HudhudSense.Model? = asset().takeIf { it.isFile }?.let { HudhudSense.decode(it.readBytes()) }

        /** Learns the sense from the bank and writes the app's asset. */
        @JvmStatic
        fun main(args: Array<String>) {
            val lines = if (args.isNotEmpty()) File(args[0]).readLines().filter { it.isNotBlank() } else bank()
            val m = HudhudSense.train(lines)
            val bytes = HudhudSense.encode(m)
            val out = if (args.size > 1) File(args[1]) else asset()
            out.parentFile.mkdirs()
            out.writeBytes(bytes)
            println("Wrote ${out.path}: ${m.labels.size} meanings, ${m.features.size} pieces, ${m.weights} weights, ${bytes.size} bytes")
        }
    }
}
