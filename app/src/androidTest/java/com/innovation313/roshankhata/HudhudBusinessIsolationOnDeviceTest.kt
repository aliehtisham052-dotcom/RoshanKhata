package com.innovation313.roshankhata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudBook
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PartyTotals
import com.innovation313.roshankhata.data.VoiceQuestion
import com.innovation313.roshankhata.data.get
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One shop's book never answers another's question (16 Nov 2026, audit T13a). Two businesses on one phone, each with a
 * customer of its own; the same questions asked of Hudhud's answer layer while each is open are answered from that book
 * alone — its own customers, its own figure — never the other's. Party ids start again at 1 in every book, so the same
 * customer id names a different person in each: the ask is the same, the answer is the open book's.
 *
 * Everything this test makes is taken away again: the second business is deleted (Businesses.delete) and the first
 * book's customer and entries are binned; the business that was open before is open again. Made-up names (lesson 80).
 */
@RunWith(AndroidJUnit4::class)
class HudhudBusinessIsolationOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class Book(val customer: Long, val name: String, val owes: Double)

    private suspend fun seed(name: String, owes: Double): Book {
        val dao = KhataDatabase.get(context).khataDao()
        val id = dao.insertParty(Party(name = name))
        dao.insertEntryNumbered(LedgerEntry(partyId = id, amount = owes, isGiven = true, entryNumber = ""))
        return Book(id, name, owes)
    }

    /** The same three questions, asked of the book open now. */
    private suspend fun ask(customer: Long): Triple<String?, List<String>, String?> {
        val dao = KhataDatabase.get(context).khataDao()
        val parties = dao.partiesWithBalanceOnce()
        val most = HudhudBook.answer(context, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.WHO_OWES_MOST), parties)
        val toGet = HudhudBook.answer(context, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), parties)
        val his = HudhudBook.answer(context, dao, Hudhud.Ask.Parties(listOf(customer)), parties)
        return Triple(toGet.figure, most.rows.map { it.name }, his.label ?: his.rows.firstOrNull()?.name)
    }

    @Test
    fun eachBusinessAnswersFromItsOwnBook() = runBlocking {
        val first = Businesses.active(context).id
        val firstDao = KhataDatabase.get(context).khataDao()
        val toGetBefore = PartyTotals.of(firstDao.partiesWithBalanceOnce().map { it.balance }).get
        val a = seed("Isolation Shop A Customer", 1234.0)
        val second = Businesses.create(context, "Isolation Test Shop B")
        try {
            val (aFigure, _, aWho) = ask(a.customer)
            assertEquals(HudhudBook.money(toGetBefore + a.owes), aFigure)
            assertEquals("A's own customer by A's id", a.name, aWho)

            Businesses.switchTo(context, second.id)
            val b = seed("Isolation Shop B Customer", 250.0)
            val (bFigure, bRows, bWho) = ask(b.customer)
            assertEquals("B's figure is B's book alone", HudhudBook.money(250.0), bFigure)
            assertEquals("B's book names only B's customer", listOf(b.name), bRows)
            assertTrue("A's customer is not in B's answer", a.name !in bRows)
            // The same ask (a customer id) means B's customer while B is open.
            val (_, _, sameIdInB) = ask(a.customer)
            assertTrue("customer id ${a.customer} in B is never A's customer (got $sameIdInB)", sameIdInB != a.name)
            assertEquals("B's own customer by B's id", b.name, bWho)

            Businesses.switchTo(context, first)
            val (aAgain, aRowsAgain, aWhoAgain) = ask(a.customer)
            assertEquals("back on A, A's figure again", aFigure, aAgain)
            assertEquals(a.name, aWhoAgain)
            assertTrue("B's customer never reaches A: $aRowsAgain", b.name !in aRowsAgain)
        } finally {
            if (Businesses.active(context).id != first) Businesses.switchTo(context, first)
            runCatching { Businesses.delete(context, second.id) }
            val dao = KhataDatabase.get(context).khataDao()
            dao.softDeleteEntriesOfParty(a.customer)
            dao.softDeleteParty(a.customer)
        }
    }
}
