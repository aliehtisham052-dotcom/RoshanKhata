package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.TimeZone

/**
 * The roles matrix over the phone (plan B7, 24 Nov 2026, private data): every kind of question of [HudhudGoldenAsks] (the
 * golden test's own list, so a new kind is asked here too) asked through [HudhudPhoneEnv] as the owner, a manager, a cashier
 * under counter mode and a locked counter, over [HudhudGoldenBook]'s invented book. One distinct invented value is put in
 * each class of private data (a product's cost price, a salary, the tax number, the bank account, the email, the
 * country); a role whose gate does not hold the class must find the value in no card, the owner must find every one (the
 * control: a leak test that cannot see the data proves nothing). Asking writes nothing (the book's fingerprint and every
 * table's row count are the same afterwards) and a private number is on its card, never in what is said aloud.
 *
 * The helper's read-only copy and the shared cards are HudhudRolesMatrixTest (:shared); the computer's panel is
 * HudhudRolesDeskTest.
 *
 * NOT TESTED where it was written: Robolectric needs Gradle, which that machine does not have. CI's first run decides;
 * a leak it names is a role, an ask and a marker: read the card's code before blaming the test. Invented names only.
 */
@RunWith(AndroidJUnit4::class)
class HudhudRolesAnswersTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao
    private lateinit var keptZone: TimeZone
    private lateinit var keptLocale: Locale
    private lateinit var keptSign: String

    private companion object {
        const val COST = "7731"
        const val SALARY = "48617"
        const val TAX = "RLTAX-5520"
        const val IBAN = "RLBANK-0419"
        const val EMAIL = "roles.shop.5520@example.invalid"
    }

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

    private enum class Who { OWNER, MANAGER, CASHIER, LOCKED }

    private fun become(who: Who, ids: HudhudGoldenBook.Ids) {
        Access.resetForTest()
        when (who) {
            Who.OWNER -> Unit
            Who.LOCKED -> Access.switchCounterMode(true)
            Who.MANAGER -> { Access.switchCounterMode(true); Access.signIn(Staff(id = ids.staff + 100, name = "Imran Shah", monthlySalary = 1.0, role = Role.MANAGER)) }
            Who.CASHIER -> { Access.switchCounterMode(true); Access.signIn(Staff(id = ids.staff, name = "Hamid Khan", monthlySalary = 30_000.0, role = Role.CASHIER)) }
        }
    }

    /** The golden book with one distinct value in each class of private data. */
    private suspend fun seedMarked(now: Long): HudhudGoldenBook.Ids {
        val ids = HudhudGoldenBook.seed(dao, now)
        dao.updateProduct(dao.productById(ids.urea)!!.copy(costPrice = COST.toDouble()))
        val staff = dao.allStaffForBackup().first { it.id == ids.staff }
        dao.updateStaff(staff.copy(monthlySalary = SALARY.toDouble()))
        BusinessProfile.setStrn(context, TAX)
        BusinessProfile.setBankIban(context, IBAN)
        BusinessProfile.setBusinessEmail(context, EMAIL)
        BusinessProfile.setCountry(context, ShopCountry.FIJI)
        return ids
    }

    private fun textOf(card: AnswerCard): String = buildList {
        card.label?.let { add(it) }
        card.figure?.let { add(it) }
        addAll(card.lines)
        card.rows.forEach { r -> add(r.name); add(r.figure); r.sub?.let { add(it) } }
        addAll(card.optionLabels.values)
    }.joinToString("\n")

    private fun flat(s: String) = s.replace(",", "").replace(" ", "").replace(" ", "")

    private suspend fun askAll(env: AnswerEnv, list: List<Hudhud.Ask>): List<Pair<Hudhud.Ask, AnswerCard>> =
        list.map { it to AnswerBook.answer(env, dao, it, dao.partiesWithBalanceOnce()) }

    @Test
    fun eachRoleFindsOnlyThePrivateDataItsGateHoldsAndTheOwnerFindsAllOfIt() {
        runBlocking {
            val now = System.currentTimeMillis()
            assumeTrue("too close to midnight", HudhudGoldenBook.clockIsSafe(now))
            val ids = seedMarked(now)
            val list = HudhudGoldenAsks.all(ids)
            val env = HudhudPhoneEnv(context) { now }
            val held = mapOf(
                COST to setOf(Who.OWNER, Who.MANAGER), SALARY to setOf(Who.OWNER),
                TAX to setOf(Who.OWNER), IBAN to setOf(Who.OWNER), EMAIL to setOf(Who.OWNER), "Fiji" to setOf(Who.OWNER)
            )
            val leaks = ArrayList<String>()
            for (who in Who.values()) {
                become(who, ids)
                val answered = askAll(env, list)
                for ((marker, roles) in held) {
                    val found = answered.filter { flat(textOf(it.second)).contains(flat(marker)) }
                    if (who == Who.OWNER) assertTrue("control: the owner never found $marker", found.isNotEmpty())
                    else if (who !in roles) found.forEach { leaks += "$who is told $marker by ${it.first}" }
                }
            }
            assertEquals("the phone's answers show more than the role's gate allows", emptyList<String>(), leaks)
        }
    }

    @Test
    fun askingEveryKindOfQuestionWritesNothing() {
        runBlocking {
            val now = System.currentTimeMillis()
            assumeTrue("too close to midnight", HudhudGoldenBook.clockIsSafe(now))
            val ids = seedMarked(now)
            val list = HudhudGoldenAsks.all(ids)
            val env = HudhudPhoneEnv(context) { now }
            suspend fun counts() = (SyncSchema.tables.map { it.name } + listOf("audit_log", "book_seals")).associateWith { t ->
                db.syncSql().read { rows("SELECT COUNT(*) FROM `$t`", types = "I").first()[0] as Long }
            }
            for (who in Who.values()) {
                become(who, ids)
                val fingerprint = LiveSync.fingerprint(db.syncSql())
                val before = counts()
                val first = askAll(env, list).map { textOf(it.second) }
                val second = askAll(env, list).map { textOf(it.second) }
                assertEquals("$who: asking changed the book's fingerprint", fingerprint, LiveSync.fingerprint(db.syncSql()))
                assertEquals("$who: asking changed a table's row count", before, counts())
                assertEquals("$who: the same questions answered differently the second time", first, second)
            }
        }
    }

    @Test
    fun aPrivateNumberIsOnTheCardAndNeverSaidAloud() {
        runBlocking {
            val now = System.currentTimeMillis()
            assumeTrue("too close to midnight", HudhudGoldenBook.clockIsSafe(now))
            val ids = seedMarked(now)
            val env = HudhudPhoneEnv(context) { now }
            val phone = AnswerBook.answer(env, dao, Hudhud.Ask.Person(listOf(ids.ali), Hudhud.Facet.PHONE), dao.partiesWithBalanceOnce())
            assertTrue("the number is on the card", flat(textOf(phone)).isNotEmpty() && phone.secrets.isNotEmpty())
            val digits = flat(phone.secrets.first())
            val said = flat(AnswerBook.speech(env, phone, hideMoney = false)?.text.orEmpty())
            assertTrue("the number is said aloud: $said", !said.contains(digits))
        }
    }
}
