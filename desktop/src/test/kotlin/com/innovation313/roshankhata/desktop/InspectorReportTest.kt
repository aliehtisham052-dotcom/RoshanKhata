package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.InspectorReport
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.words.desktopWords
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The phone's inspector document drawn on the computer (the copy gen_desktop_inspector.py makes), from a
 * small made-up book: an expired batch, one expiring, one in order, a product with no registration number.
 * Its first page goes to build/screen-proof (inspector-en, inspector-ur) for the desktop-test release — to be
 * looked at (lesson 16).
 */
class InspectorReportTest {

    @Test
    fun `the inspector document draws its pages in English and Urdu`() {
        val home = Files.createTempDirectory("rk-insp").toFile()
        val db = DesktopDatabase.open(File(home, "khata.db"))
        val dao = db.khataDao()
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        try {
            runBlocking {
                val sup = dao.insertParty(Party(name = "Sample Seeds Co.", phone = "03000000004", isCustomer = false))
                val conf = dao.insertProduct(Product(name = "Confidor", nameKey = "confidor", normalisedName = "confidor", category = "Pesticide", company = "Bayer", defaultUnit = "bottle", salePrice = 875.0, technicalName = "Imidacloprid 200 SL", registrationNumber = "PK-1234"))
                val dap = dao.insertProduct(Product(name = "DAP", nameKey = "dap", normalisedName = "dap", category = "Fertilizer", company = "Engro", defaultUnit = "bag", salePrice = 3500.0))
                dao.insertSupplierBill(
                    null, SupplierBill(partyId = sup, billNumber = "SB-1042", billDate = now - 200 * day, totalAmount = 0.0),
                    listOf(
                        BillItem(billId = 0, productName = "Confidor", batchNumber = "CF-2401", expiryDate = now - 5 * day, quantity = 6.0, unit = "bottle", rate = 700.0, productId = conf),
                        BillItem(billId = 0, productName = "Confidor", batchNumber = "CF-2410", expiryDate = now + 18 * day, quantity = 24.0, unit = "bottle", rate = 700.0, productId = conf),
                        BillItem(billId = 0, productName = "DAP", batchNumber = "DP-77", expiryDate = now + 400 * day, quantity = 40.0, unit = "bag", rate = 3200.0, productId = dap)
                    )
                )
            }
            val dir = File("build/screen-proof").apply { mkdirs() }
            val before = Locale.getDefault()
            for (ur in listOf(false, true)) {
                val locale = Locale.forLanguageTag(if (ur) "ur" else "en")
                // As the app runs: the document's dates read the default locale (the Urdu proof said "Oct 2026").
                Locale.setDefault(locale)
                val settings = FileShopSettings(File(home, "shop-$locale.properties")).apply {
                    setBusinessName(if (ur) "النور ایگری اسٹور" else "Al-Noor Agri Store"); setBusinessAddress("Main Bazaar, Sample Town"); setStrn("1234567-8")
                }
                val ctx = Context(desktopWords(locale), settings, ProfileImages(File(home, "pics")), ur, locale)
                val doc = runBlocking { InspectorReport.build(ctx, dao, 60) }
                assertTrue("the document was not built", doc != null)
                val sheets = Sheets.of(doc!!)
                assertTrue("no pages", sheets.count >= 1)
                // Landscape A4, the phone's.
                assertEquals(842f, sheets.width(0), 0.5f)
                val img = sheets.picture(0, 1.5f)
                val f = File(dir, "inspector-" + (if (ur) "ur" else "en") + ".png")
                ImageIO.write(img, "png", f)
                assertTrue("${f.name} looks empty", f.length() > 20_000)
            }
            Locale.setDefault(before)
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }
}
