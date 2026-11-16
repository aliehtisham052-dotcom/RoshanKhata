package com.innovation313.roshankhata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudBook
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.VoiceQuestion
import com.innovation313.roshankhata.data.get
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "How much money came in today" is one figure on Hudhud's card and on Insights (16 Nov 2026, audit T3 / D-01, the owner:
 * money received means customers' real payments). A customer's payment and a walk-in's payment count; a customer's goods
 * return and the owner's own money put in the cashbook do not. Before, Hudhud counted all four (Rs 1,000 + 300 read as
 * Rs 3,800). Made-up names (lesson 80).
 */
@RunWith(AndroidJUnit4::class)
class HudhudMoneyInOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun moneyInIsInsightsFigureAndLeavesReturnsAndOwnMoneyOut() = runBlocking {
        val dao = KhataDatabase.get(context).khataDao()
        val (from, to) = Hudhud.span(VoiceQuestion.Window.TODAY)
        val before = dao.customerReceivedBetween(from, to)

        val party = dao.insertParty(Party(name = "Money In Test Customer"))
        val now = System.currentTimeMillis()
        val paid = dao.insertEntryWithItems(LedgerEntry(partyId = party, amount = 1000.0, isGiven = false, entryNumber = "", timestamp = now), emptyList())
        val returned = dao.insertEntryWithItems(
            LedgerEntry(partyId = party, amount = 500.0, isGiven = false, entryNumber = "", timestamp = now),
            listOf(EntryItem(entryId = 0, itemName = "Test goods", quantity = 1.0))
        )
        val walkIn = dao.insertCashEntry(CashEntry(amount = 300.0, isIncome = true, category = "Sale", timestamp = now))
        val ownMoney = dao.insertCashEntry(CashEntry(amount = 2000.0, isIncome = true, category = "Other", timestamp = now))
        try {
            val after = dao.customerReceivedBetween(from, to)
            assertEquals("Insights' rule: the payment and the walk-in only", 1300.0, after - before, 0.001)
            val card = HudhudBook.answer(context, dao, Hudhud.Ask.Collected(VoiceQuestion.Window.TODAY), dao.partiesWithBalanceOnce())
            assertEquals("Hudhud's figure is Insights' figure", HudhudBook.money(after), card.figure)
        } finally {
            dao.softDeleteEntry(paid); dao.softDeleteEntry(returned)
            dao.softDeleteCashEntry(walkIn); dao.softDeleteCashEntry(ownMoney)
            dao.softDeleteParty(party)
        }
    }
}
