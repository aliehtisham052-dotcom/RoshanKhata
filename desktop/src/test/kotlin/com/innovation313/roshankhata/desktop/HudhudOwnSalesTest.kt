package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AnswerBook
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.Role
import com.innovation313.roshankhata.data.Sale
import com.innovation313.roshankhata.data.SaleItem
import com.innovation313.roshankhata.data.SalePayment
import com.innovation313.roshankhata.data.ScreenId
import com.innovation313.roshankhata.data.Staff
import com.innovation313.roshankhata.data.VoiceQuestion
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * The owner's T5 (17 Nov 2026): a cashier signed in at the counter who asks Hudhud "today's sale" is told the total of the
 * tickets HE rang up today, never the shop's; the owner (counter mode off) still hears the shop's whole sale; and a cashier
 * asking any other figure is still told only the owner or a manager sees it. Names are invented.
 */
class HudhudOwnSalesTest {

    private lateinit var dir: File
    private lateinit var keptLocale: Locale

    @Before
    fun open() {
        keptLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-hudhud-own").toFile()
    }

    @After
    fun close() {
        Access.resetForTest()
        Locale.setDefault(keptLocale)
        dir.deleteRecursively()
    }

    private fun env(): HudhudDeskEnv = HudhudDeskEnv(
        desktopWords(Locale.US), Locale.US, FileShopSettings(File(dir, "shop.properties")),
        AppPrefs(File(dir, "app.properties")), 1L, ReminderStore(File(dir, "reminders_b1.properties"))
    )

    private suspend fun ticket(dao: com.innovation313.roshankhata.data.KhataDao, by: String?, rate: Double) {
        dao.postSale(
            Sale(customerName = null, timestamp = System.currentTimeMillis() - 60_000, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, createdBy = by),
            listOf(SaleItem(saleId = 0, itemName = "Item", quantity = 1.0, unit = "piece", rate = rate)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = rate))
        )
    }

    @Test
    fun `a cashier is told only his own tickets of today`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("khata.db"))
        try {
            val dao = db.khataDao()
            ticket(dao, "Tariq Butt", 300.0)
            ticket(dao, "Tariq Butt", 200.0)
            ticket(dao, "Bashir Ahmad", 1000.0)
            ticket(dao, null, 4000.0)
            val env = env()
            val today = Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY)

            // The owner (counter mode off): the shop's whole sale of today, 5,500.
            val shop = AnswerBook.answer(env, dao, today, emptyList())
            assertEquals(AnswerBook.money(5500.0), shop.figure)

            // Tariq at the counter: his two tickets, 500, and the count; never the shop's 5,500.
            Access.switchCounterMode(true)
            Access.signIn(Staff(id = 7, name = "Tariq Butt", monthlySalary = 1.0, role = Role.CASHIER))
            val his = AnswerBook.answer(env, dao, today, emptyList())
            assertEquals(AnswerBook.money(500.0), his.figure)
            assertEquals(env.words.text(W.hudhud_own_sales_label), his.label)
            assertTrue(his.lines.any { it.contains("2") })
            assertEquals(ScreenId.SHIFT, his.open)

            // This month's sale stays the owner's or a manager's.
            val month = AnswerBook.answer(env, dao, Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_MONTH), emptyList())
            assertEquals(null, month.figure)
            assertEquals(listOf(env.words.text(W.hudhud_owner_only)), month.lines)
        } finally {
            db.close()
        }
    }
}
