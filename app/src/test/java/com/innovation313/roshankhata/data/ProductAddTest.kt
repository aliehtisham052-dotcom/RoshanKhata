package com.innovation313.roshankhata.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Products screen's Add (30 Oct 2026): a product made by hand, its name and barcode checked in one transaction. */
@RunWith(AndroidJUnit4::class)
class ProductAddTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).allowMainThreadQueries().build()
        dao = db.khataDao()
    }

    @After
    fun close() = db.close()

    private fun typed(name: String, barcode: String? = null, price: Double? = null) =
        Product(name = name, nameKey = "", normalisedName = "", barcode = barcode, salePrice = price, defaultUnit = "bottle")

    @Test
    fun `a new product is written with its keys and what was typed`() = runBlocking {
        val r = dao.addProduct(typed("  Confidor ", barcode = " 8901234567890 ", price = 875.0))
        assertTrue(r is ProductAdd.Added)
        val p = dao.productById((r as ProductAdd.Added).id)!!
        assertEquals("Confidor", p.name)
        assertEquals(ProductName.key("Confidor"), p.nameKey)
        assertEquals("8901234567890", p.barcode)
        assertEquals(875.0, p.salePrice!!, 0.0)
        assertEquals("bottle", p.defaultUnit)
        assertFalse(p.uid.isNullOrBlank())
        // The counter finds it by name and by its code.
        assertEquals(p.id, dao.productByBarcode("8901234567890")!!.id)
    }

    @Test
    fun `a name a live product holds is refused and nothing is written`() = runBlocking {
        dao.addProduct(typed("DAP"))
        val r = dao.addProduct(typed("dap "))
        assertEquals(ProductAdd.NameTaken("DAP"), r)
        assertEquals(1, dao.productsOnce().size)
    }

    @Test
    fun `a barcode another product holds is refused`() = runBlocking {
        dao.addProduct(typed("DAP", barcode = "111"))
        val r = dao.addProduct(typed("Urea", barcode = "111"))
        assertEquals(ProductAdd.BarcodeTaken("DAP"), r)
        assertNull(dao.productByKey(ProductName.key("Urea")))
    }

    @Test
    fun `a deleted product of the same name comes back with what was typed, keeping its id and uid`() = runBlocking {
        val first = dao.addProduct(typed("Urea")) as ProductAdd.Added
        val old = dao.productById(first.id)!!
        dao.updateProduct(old.copy(isDeleted = true, deletedAt = 9L))
        val again = dao.addProduct(typed("urea", price = 4200.0))
        assertEquals(ProductAdd.Added(first.id), again)
        val back = dao.productById(first.id)!!
        assertFalse(back.isDeleted)
        assertNull(back.deletedAt)
        assertEquals(old.uid, back.uid)
        assertEquals(old.createdAt, back.createdAt)
        assertEquals(4200.0, back.salePrice!!, 0.0)
        assertEquals(1, dao.productsOnce().size)
    }
}
