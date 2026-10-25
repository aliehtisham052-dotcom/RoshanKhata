package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The computer's bin and its pictures (Marhala 4, part 10 — the audit: a customer deleted forever left his face and
 * his bill photos on disk). A purge takes the pictures it left with no row pointing at them, the phone's
 * BillPhoto.purgingOrphans rule; never one another business's book points at, nothing when another book cannot be
 * read, and never a file outside the app's own folders, whatever path a backup brought in.
 */
class BinPhotosTest {

    private class Book(val home: File) {
        val db = DesktopDatabase.open(File(home, "khata.db"))
        val dao = db.khataDao()
        fun picture(rel: String): File = File(home, rel).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
    }

    /** A customer with a face and a bill photo, binned (customer and entry together), ready to purge. */
    private fun binned(book: Book, outside: File?): Triple<Long, File, File> = runBlocking {
        val face = book.picture("party_photos/party_7.jpg")
        val bill = book.picture("bills/bill_1.jpg")
        val id = book.dao.insertParty(Party(name = "Sample Customer", phone = "03000000009", isCustomer = true, photoPath = face.absolutePath))
        book.dao.insertEntry(LedgerEntry(partyId = id, amount = 500.0, isGiven = true, entryNumber = "E-1", billPhotoPath = bill.absolutePath))
        if (outside != null) book.dao.insertEntry(LedgerEntry(partyId = id, amount = 100.0, isGiven = true, entryNumber = "E-2", billPhotoPath = outside.absolutePath))
        assertTrue(book.dao.softDeleteParties(listOf(id)).isEmpty())
        Triple(id, face, bill)
    }

    @Test
    fun `a purge takes the pictures it left unowned, never a file outside the app's folders`() {
        val home = Files.createTempDirectory("rk-binphotos").toFile()
        val elsewhere = Files.createTempDirectory("rk-elsewhere").toFile()
        val book = Book(home)
        try {
            // A path a backup brought in may point anywhere: that file is never the bin's to delete.
            val outside = File(elsewhere, "owners-own.jpg").apply { writeBytes(byteArrayOf(9)) }
            val (id, face, bill) = binned(book, outside)
            val purged = runBlocking { Photos.purgingOrphans(book.dao, home, { emptySet() }) { book.dao.purgeParty(id) } }
            assertEquals(1, purged)
            assertFalse("the face went with him", face.exists())
            assertFalse("the bill photo went with his entry", bill.exists())
            assertTrue("a file outside the app's folders stays", outside.exists())
        } finally {
            book.db.close()
            home.deleteRecursively()
            elsewhere.deleteRecursively()
        }
    }

    @Test
    fun `a picture another book points at stays, and nothing goes when another book cannot be read`() {
        val home = Files.createTempDirectory("rk-binphotos2").toFile()
        val book = Book(home)
        try {
            val (id, face, bill) = binned(book, null)
            // Another business's book still points at the bill photo (the bills folder is every business's).
            runBlocking { Photos.purgingOrphans(book.dao, home, { setOf(bill.absolutePath) }) { book.dao.purgeParty(id) } }
            assertFalse(face.exists())
            assertTrue("another book's bill photo stays", bill.exists())

            val (id2, face2, bill2) = binned(book, null)
            runBlocking { Photos.purgingOrphans(book.dao, home, { null }) { book.dao.purgeParty(id2) } }
            assertTrue("no other book read, no picture removed", face2.exists())
            assertTrue(bill2.exists())
        } finally {
            book.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `only the bills folder and the faces folders are the app's`() {
        val home = Files.createTempDirectory("rk-ours").toFile()
        try {
            assertTrue(Photos.ours(home, File(home, "bills/bill_1.jpg")))
            assertTrue(Photos.ours(home, File(home, "party_photos/party_1.jpg")))
            assertTrue(Photos.ours(home, File(home, "party_photos_b3/party_1.jpg")))
            assertFalse(Photos.ours(home, File(home, "khata.db")))
            assertFalse(Photos.ours(home, File(home, "logs/errors.log")))
            assertFalse(Photos.ours(home, File(home, "bills/../khata.db")))
            assertFalse(Photos.ours(home, File(home.parentFile, "bills/bill_1.jpg")))
        } finally {
            home.deleteRecursively()
        }
    }
}
