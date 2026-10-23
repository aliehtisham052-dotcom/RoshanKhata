package com.innovation313.roshankhata.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A restore's picture move is finished after a kill (23 Oct 2026, review R39): nothing is left as
 * "*.moving", nothing lands on a row that is not its own, nothing is overwritten.
 */
class ImageReattachTest {

    private lateinit var dir: File
    private val sig = Regex("^sig_(\\d+)\\.png$")

    @Before
    fun setUp() { dir = Files.createTempDirectory("reattach").toFile() }

    @After
    fun tearDown() { dir.deleteRecursively() }

    private fun put(name: String, body: String) = File(dir, name).writeText(body)
    private fun read(name: String): String? = File(dir, name).takeIf { it.isFile }?.readText()
    private fun movingLeft() = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".moving") }.toList()

    @Test
    fun `a whole move swaps two pictures without overwriting either`() {
        put("sig_1.png", "one")
        put("sig_2.png", "two")
        ImageReattach.move(dir, sig, "sig_%d.png") { old -> if (old == 1L) 2L else 1L }
        assertEquals("one", read("sig_2.png"))
        assertEquals("two", read("sig_1.png"))
        assertTrue(movingLeft().isEmpty())
        assertFalse(File(dir, "reattach.staged").exists())
    }

    @Test
    fun `a run killed in the first step is finished from its plan`() {
        // The plan was written, sig_1 was staged, then the process died.
        put("reattach.plan", "sig_1.png\tsig_2.png\nsig_2.png\tsig_1.png\n")
        put("sig_1.png.moving", "one")
        put("sig_2.png", "two")
        ImageReattach.finish(dir)
        assertEquals("one", read("sig_2.png"))
        assertEquals("two", read("sig_1.png"))
        assertTrue(movingLeft().isEmpty())
    }

    @Test
    fun `a run killed in the second step does not stage a picture already moved`() {
        // Everything was staged, sig_1 already went to its new name sig_2, then the process died.
        put("reattach.staged", "sig_1.png\tsig_2.png\nsig_2.png\tsig_1.png\n")
        put("sig_2.png", "one")
        put("sig_2.png.moving", "two")
        ImageReattach.finish(dir)
        assertEquals("one", read("sig_2.png"))
        assertEquals("two", read("sig_1.png"))
        assertTrue(movingLeft().isEmpty())
    }

    @Test
    fun `a picture left moving with no plan is set aside and kept`() {
        put("sig_9.png.moving", "nine")
        ImageReattach.finish(dir)
        assertTrue(movingLeft().isEmpty())
        val aside = dir.walkTopDown().filter { it.isFile && it.name == "sig_9.png" }.toList()
        assertEquals(1, aside.size)
        assertTrue(aside.single().parentFile!!.name.startsWith("unmatched-"))
        assertEquals("nine", aside.single().readText())
    }

    @Test
    fun `a picture whose row is gone is set aside`() {
        put("sig_3.png", "three")
        ImageReattach.move(dir, sig, "sig_%d.png") { null }
        assertEquals(null, read("sig_3.png"))
        assertEquals(1, dir.walkTopDown().count { it.isFile && it.name == "sig_3.png" && it.parentFile!!.name.startsWith("unmatched-") })
    }
}
