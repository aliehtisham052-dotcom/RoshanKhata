package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.TimeZone

/**
 * 24 Nov 2026, the catalog's last GAPs (plan H6): what two cards now say, asked of a real (in-memory) book through the phone's
 * own environment, as the owner and as a cashier under counter mode. A cashbook row's line says who typed it only when the
 * book holds a name (the owner's rows and the older ones carry none and are not guessed), behind REPORTS; the backup answer
 * carries the newest seal's day and entry count for the owner only, and never for a book with no seal. Invented names only.
 *
 * NOT TESTED where it was written: Robolectric needs Gradle, which that machine does not have. CI's first run decides.
 */
@RunWith(AndroidJUnit4::class)
class HudhudCatalogLastGapsOnPhoneTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private lateinit var keptZone: TimeZone
    private lateinit var keptLocale: Locale
    private lateinit var keptSign: String
    private val now = System.currentTimeMillis()

    @Before
    fun open() {
        keptZone = TimeZone.getDefault()
        keptLocale = Locale.getDefault()
        keptSign = Currency.symbol
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        Currency.symbol = Currency.DEFAULT
        Access.resetForTest()
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).allowMainThreadQueries().build()
        dao = db.ledgerDao()
    }

    @After
    fun close() {
        Access.resetForTest()
        db.close()
        TimeZone.setDefault(keptZone)
        Locale.setDefault(keptLocale)
        Currency.symbol = keptSign
    }

    private fun env() = HudhudPhoneEnv(context) { now }

    private suspend fun answer(ask: Hudhud.Ask): String {
        val c = AnswerBook.answer(env(), dao, ask, dao.partiesWithBalanceOnce())
        return (listOfNotNull(c.label, c.figure) + c.lines + c.rows.flatMap { listOfNotNull(it.name, it.figure, it.sub) }).joinToString("\n")
    }

    private fun asCashier() {
        Access.resetForTest()
        Access.switchCounterMode(true)
        Access.signIn(Staff(id = 7, name = "Tariq Butt", monthlySalary = 1.0, role = Role.CASHIER))
    }

    private suspend fun seedCash() {
        dao.insertCashEntry(CashEntry(amount = 120.0, isIncome = true, category = "Other", note = "Typed at the counter", timestamp = now - 60_000,
            paymentMethod = PaymentMethod.CASH, createdBy = "Hamid Khan"))
        dao.insertCashEntry(CashEntry(amount = 80.0, isIncome = false, category = "Rent", note = "Owner's own row", timestamp = now - 120_000,
            paymentMethod = PaymentMethod.CASH))
    }

    @Test
    fun aCashbookRowSaysWhoTypedItOnlyWhenTheBookHoldsAName() = runBlocking {
        seedCash()
        val text = answer(Hudhud.Ask.Cashbook(VoiceQuestion.Window.TODAY))
        val typed = text.lines().first { it.contains("Typed at the counter") }
        val owners = text.lines().first { it.contains("Owner's own row") }
        assertTrue("the named writer is on his row: $typed", typed.contains("Hamid Khan"))
        assertFalse("the owner's row is not given a writer: $owners", owners.contains("By"))
    }

    @Test
    fun aCashierIsToldNeitherTheRowsNorTheirWriter() = runBlocking {
        seedCash()
        asCashier()
        assertFalse(answer(Hudhud.Ask.Cashbook(VoiceQuestion.Window.TODAY)).contains("Hamid Khan"))
    }

    private suspend fun seal(entries: Int) {
        dao.insertSeal(BookSeal(sealedAt = now - 3_600_000, fromId = 0, toId = entries.toLong(), leafCount = entries, digest = "d1"))
    }

    @Test
    fun theBackupAnswerCarriesTheNewestSealForTheOwnerOnly() = runBlocking {
        seal(7)
        val text = answer(Hudhud.Ask.BackupStatus)
        assertTrue("the seal's line is under the backup's: $text", text.lines().any { it.contains("7") && it.contains("entries sealed") })
        asCashier()
        assertFalse(answer(Hudhud.Ask.BackupStatus).contains("entries sealed"))
    }

    @Test
    fun aBookNeverSealedSaysNothingOfASeal() = runBlocking {
        assertFalse(answer(Hudhud.Ask.BackupStatus).contains("sealed"))
    }

    @Test
    fun theSealLineNeverCarriesTheProof() = runBlocking {
        dao.insertSeal(BookSeal(sealedAt = now - 1_000, fromId = 0, toId = 1, leafCount = 1, digest = "SECRETDIGEST-5520",
            tokens = "{\"FreeTSA\":{\"token\":\"TOKEN-5520\"}}"))
        val text = answer(Hudhud.Ask.BackupStatus)
        assertFalse(text.contains("SECRETDIGEST-5520") || text.contains("TOKEN-5520"))
    }
}
