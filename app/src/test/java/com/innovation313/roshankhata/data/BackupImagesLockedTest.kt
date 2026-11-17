package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The Drive photo archive locked with the backup password (17 Nov 2026, BC-165): with its password it unpacks as a
 * plain one does; without, with a wrong one, or changed on the way, it writes no picture at all; every archive made
 * before (plain) still restores. Made-up pictures only.
 */
@RunWith(AndroidJUnit4::class)
class BackupImagesLockedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private val pw = "Ali Raza 2026"
    private val picture = ByteArray(5000) { (it * 7).toByte() }
    private val bill get() = File(File(context.filesDir, "bills"), "bill_42.jpg")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        bill.delete()
    }

    @After fun close() { db.close(); bill.delete() }

    private fun zip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("bills/bill_42.jpg")); z.write(picture); z.closeEntry() }
        return out.toByteArray()
    }

    private fun locked(): ByteArray {
        val out = ByteArrayOutputStream()
        BackupSeal.sealing(out, pw, BackupSeal.MIN_ITERATIONS).use { it.write(zip()) }
        return out.toByteArray()
    }

    @Test fun `a locked archive unpacks with its password`() = runBlocking {
        assertEquals(1, BackupImages.restore(context, db.khataDao(), locked(), listOf("not it at all", pw)))
        assertArrayEquals(picture, bill.readBytes())
    }

    @Test fun `without its password, or with a wrong one, no picture is written`() = runBlocking {
        for ((given, wrong) in listOf(emptyList<String>() to false, listOf("Tariq Butt 300") to true)) {
            try {
                BackupImages.restore(context, db.khataDao(), locked(), given)
                fail("unpacked a locked archive with $given")
            } catch (e: BackupImages.Locked) {
                assertEquals(wrong, e.wrong)
            }
            assertFalse(bill.exists())
        }
    }

    @Test fun `a locked archive changed on the way writes nothing`() = runBlocking {
        val bad = locked().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        try {
            BackupImages.restore(context, db.khataDao(), bad, listOf(pw))
            fail("unpacked a changed archive")
        } catch (e: BackupSeal.Damaged) {
        }
        assertFalse(bill.exists())
        assertTrue("no unlocked copy is left in the cache", context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("rk-photos-") })
    }

    @Test fun `an old plain archive restores as it always did, whatever passwords are given`() = runBlocking {
        assertEquals(1, BackupImages.restore(context, db.khataDao(), zip(), listOf(pw)))
        assertArrayEquals(picture, bill.readBytes())
    }
}
