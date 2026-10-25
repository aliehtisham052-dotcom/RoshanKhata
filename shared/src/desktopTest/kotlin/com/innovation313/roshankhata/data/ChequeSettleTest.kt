package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A cheque marked by hand (25 Oct 2026, the audit): bounced or cancelled only
 * while pending, back to pending only when it never posted a payment. With
 * live sync a stale dialog marked a cheque the other device had cleared as
 * bounced, and reopening and clearing it posted the payment twice.
 */
class ChequeSettleTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-cheque-settle").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun cheque(): Long {
        val aslam = dao.insertParty(Party(name = "Aslam"))
        return dao.insertCheque(Cheque(partyId = aslam, amount = 7_500.0, isReceived = true, dueDate = System.currentTimeMillis()))
    }

    @Test
    fun `a pending cheque bounces and can go back to pending`() = runBlocking {
        val id = cheque()
        assertTrue(dao.settleCheque(id, ChequeStatus.BOUNCED))
        assertEquals(ChequeStatus.BOUNCED, dao.getCheque(id)!!.status)
        assertTrue(dao.settleCheque(id, null))
        assertEquals(ChequeStatus.PENDING, dao.getCheque(id)!!.status)
    }

    @Test
    fun `a cleared cheque is never marked bounced or reopened, so it cannot post twice`() = runBlocking {
        val id = cheque()
        assertNotNull(dao.clearCheque(id, "cleared on the phone"))
        assertFalse(dao.settleCheque(id, ChequeStatus.BOUNCED))
        assertFalse(dao.settleCheque(id, ChequeStatus.CANCELLED))
        assertFalse(dao.settleCheque(id, null))
        assertEquals(ChequeStatus.CLEARED, dao.getCheque(id)!!.status)
        assertEquals("one payment, not two", null, dao.clearCheque(id, "again"))
    }

    @Test
    fun `clearing is never done by hand and a pending cheque is not reopened`() = runBlocking {
        val id = cheque()
        assertFalse(dao.settleCheque(id, ChequeStatus.CLEARED))
        assertFalse(dao.settleCheque(id, null))
        assertEquals(ChequeStatus.PENDING, dao.getCheque(id)!!.status)
    }
}
