package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Reading the file an owner sends to a helper's phone ([BackupImages.readCopy]):
 * the plain text every earlier version sends, and the zip that also carries photos.
 */
class HelperCopyTest {

    private val book = "{\"format\":\"RoshanKhata\",\"businessName\":\"علی ٹریڈرز\"}"

    private fun opener(bytes: ByteArray): () -> InputStream? = { ByteArrayInputStream(bytes) }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `a plain text backup still opens, with no photos`() {
        val copy = BackupImages.readCopy(opener(book.toByteArray(Charsets.UTF_8)))
        assertNotNull(copy)
        assertEquals(book, copy!!.text)
        assertFalse(copy.hasImages)
    }

    @Test
    fun `a zip gives back the same text, Urdu intact, and says photos came`() {
        val zip = zipOf(
            "party_photos/party_7.jpg" to byteArrayOf(1, 2, 3),
            BackupImages.COPY_TEXT to book.toByteArray(Charsets.UTF_8),
            "bills/bill_1.jpg" to byteArrayOf(4, 5)
        )
        val copy = BackupImages.readCopy(opener(zip))
        assertNotNull(copy)
        assertEquals(book, copy!!.text)
        assertTrue(copy.hasImages)
    }

    @Test
    fun `a zip holding only the book has no photos`() {
        val copy = BackupImages.readCopy(opener(zipOf(BackupImages.COPY_TEXT to book.toByteArray(Charsets.UTF_8))))
        assertNotNull(copy)
        assertFalse(copy!!.hasImages)
    }

    @Test
    fun `a zip with no book in it is not a copy`() {
        assertNull(BackupImages.readCopy(opener(zipOf("party_photos/party_7.jpg" to byteArrayOf(1)))))
    }

    @Test
    fun `a file that cannot be opened is not a copy`() {
        assertNull(BackupImages.readCopy { null })
    }

    @Test
    fun `a file shorter than a zip header is read as text`() {
        val copy = BackupImages.readCopy(opener("{}".toByteArray(Charsets.UTF_8)))
        assertEquals("{}", copy!!.text)
    }
}
