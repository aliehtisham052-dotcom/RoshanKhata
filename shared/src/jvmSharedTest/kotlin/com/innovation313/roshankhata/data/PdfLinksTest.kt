package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture is a real two-page PDF written by Skia, the engine behind
 * Android's PdfDocument, so this exercises the shape the app actually produces.
 */
class PdfLinksTest {

    private val url = "https://play.google.com/store/apps/details?id=com.innovation313.roshankhata"

    private fun fixture(): ByteArray =
        javaClass.getResourceAsStream("/skia_two_page.pdf")!!.readBytes()

    @Test
    fun original_bytes_are_kept_and_a_link_is_appended() {
        val original = fixture()
        val updated = PdfLinks.append(original, listOf(PdfLinks.Link(0, 40f, 700f, 555f, 780f, url)))
        assertNotNull(updated)
        updated!!
        // An incremental update never rewrites what was already there.
        assertArrayEquals(original, updated.copyOfRange(0, original.size))
        val tail = String(updated, original.size, updated.size - original.size, Charsets.ISO_8859_1)
        assertTrue(tail.contains("/Subtype /Link"))
        assertTrue(tail.contains("/URI ($url)"))
        assertTrue(tail.contains("/Annots ["))
        assertTrue(tail.trimEnd().endsWith("%%EOF"))
    }

    @Test
    fun rectangle_is_flipped_from_top_left_to_pdf_bottom_left() {
        val updated = PdfLinks.append(fixture(), listOf(PdfLinks.Link(0, 40f, 700f, 555f, 780f, url)))!!
        // Page is 842 high: top 700 -> 142, bottom 780 -> 62.
        assertTrue(String(updated, Charsets.ISO_8859_1).contains("/Rect [40.00 62.00 555.00 142.00]"))
    }

    @Test
    fun new_table_points_back_at_the_old_one_and_at_its_own_start() {
        val original = fixture()
        val updated = PdfLinks.append(original, listOf(PdfLinks.Link(1, 10f, 10f, 20f, 20f, url)))!!
        val text = String(updated, Charsets.ISO_8859_1)
        assertTrue(text.contains("/Prev 817"))
        val start = Regex("""startxref\s+(\d+)\s+%%EOF\s*$""").find(text)!!.groupValues[1].toInt()
        assertTrue(text.startsWith("xref", start))
        // Size grew by exactly the one annotation.
        assertTrue(text.contains("/Size 10"))
    }

    @Test
    fun url_with_brackets_is_escaped() {
        val updated = PdfLinks.append(fixture(), listOf(PdfLinks.Link(0, 0f, 0f, 1f, 1f, "https://x.test/a(b)")))!!
        assertTrue(String(updated, Charsets.ISO_8859_1).contains("/URI (https://x.test/a\\(b\\))"))
    }

    @Test
    fun a_link_for_a_page_that_does_not_exist_is_refused() {
        assertNull(PdfLinks.append(fixture(), listOf(PdfLinks.Link(5, 0f, 0f, 1f, 1f, url))))
    }

    @Test
    fun anything_that_is_not_the_expected_shape_is_refused_not_mangled() {
        assertNull(PdfLinks.append("not a pdf".toByteArray(), listOf(PdfLinks.Link(0, 0f, 0f, 1f, 1f, url))))
        assertNull(PdfLinks.append(ByteArray(0), listOf(PdfLinks.Link(0, 0f, 0f, 1f, 1f, url))))
        val truncated = fixture().copyOf(300)
        assertNull(PdfLinks.append(truncated, listOf(PdfLinks.Link(0, 0f, 0f, 1f, 1f, url))))
    }

    @Test
    fun no_links_means_no_change() {
        assertEquals(false, PdfLinks.addLinks(java.io.File.createTempFile("links", ".pdf"), emptyList()))
    }
}
