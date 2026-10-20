package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.EntryWithItems
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.DocumentPrint
import com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.words.desktopWords
import org.apache.pdfbox.Loader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Date
import java.util.Locale

/**
 * The PDFs the preview keeps (1.8.4): a real PDF that opens, one page per
 * sheet at the sheet's own size, a name Windows accepts, never written over
 * an earlier file. The files go to build/pdf-proof, and desktop.yml puts them
 * on desktop-test, so they are opened and looked at, not only counted.
 */
class PdfFilesTest {

    private val proof = File("build/pdf-proof").apply { mkdirs() }

    private fun statement(rows: Int): StatementPrint {
        val entries = (1..rows).map { i ->
            EntryWithItems(
                LedgerEntry(
                    id = i.toLong(), partyId = 7, amount = 1000.0 + i * 37, isGiven = i % 3 != 0,
                    entryNumber = "RK-%06d".format(i), timestamp = 1_760_000_000_000L + i * 86_400_000L
                ),
                emptyList()
            )
        }
        return StatementPrint(desktopWords(Locale.ENGLISH), false, "Al-Noor Agri Store", "Sample Customer", "0300 0000000", entries)
    }

    @Test
    fun `a long statement becomes a PDF with every page, each A4`() {
        val sheet = statement(90)
        val sheets = Sheets.of(sheet)
        assertTrue("90 rows should run past one page: ${sheets.count}", sheets.count > 1)
        val file = File(proof, "statement-sample.pdf")
        PdfFiles.write(sheets, file)
        Loader.loadPDF(file).use { pdf ->
            assertEquals(sheets.count, pdf.numberOfPages)
            for (p in pdf.pages) {
                assertEquals(595f, p.mediaBox.width, 1f)
                assertEquals(842f, p.mediaBox.height, 1f)
            }
        }
        assertFalse("no half-written file is left behind", File(proof, "statement-sample.pdf.part").exists())
    }

    @Test
    fun `an invoice keeps its design's own page size, the thermal roll too`() {
        val dir = Files.createTempDirectory("rk-pdf").toFile()
        val settings = FileShopSettings(File(dir, "shop.properties")).apply { setBusinessName("Al-Noor Agri Store") }
        val ctx = Context(desktopWords(Locale.ENGLISH), settings, ProfileImages(File(dir, "pictures")), rtl = false)
        val items = listOf(InvoiceItem(invoiceId = 1, itemName = "Urea", quantity = 10.0, unit = "bag", rate = 4650.0))
        for (design in listOf(1, 10)) {
            val inv = Invoice(id = 1, invoiceNumber = "INV-0007", customerName = "Sample Customer", invoiceDate = 1_760_000_000_000L, templateId = design)
            val doc = InvoiceDesigns.pages(ctx, inv, items)!!
            val sheets = Sheets.of(DocumentPrint(doc))
            assertEquals(doc.finished.size, sheets.count)
            val file = File(proof, "invoice-sample-$design.pdf")
            PdfFiles.write(sheets, file)
            Loader.loadPDF(file).use { pdf ->
                assertEquals(doc.finished.size, pdf.numberOfPages)
                assertEquals(doc.finished[0].info.pageWidth.toFloat(), pdf.getPage(0).mediaBox.width, 1f)
                assertEquals(doc.finished[0].info.pageHeight.toFloat(), pdf.getPage(0).mediaBox.height, 1f)
            }
        }
    }

    @Test
    fun `file names are safe for Windows and never overwrite`() {
        val dir = Files.createTempDirectory("rk-names").toFile()
        val day = Date(1_760_000_000_000L)
        val first = PdfFiles.next("Statement: Ali / Raza?", dir, day)
        assertFalse(first.name.any { it in "\\/:*?\"<>|" })
        assertTrue(first.name.endsWith(".pdf"))
        first.writeText("x")
        val second = PdfFiles.next("Statement: Ali / Raza?", dir, day)
        assertTrue(second.name, second.name.endsWith("(2).pdf"))
        assertTrue(PdfFiles.next("", dir, day).name.startsWith("Roshan Khata_"))
    }
}
