package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.innovation313.roshankhata.data.RateList
import com.innovation313.roshankhata.ui.RateListImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The rate-list picture, drawn on a real Android in all nine languages.
 *
 * Checked by numbers, not by eye: the page is 1080 wide and no taller than
 * a page may be; the name column sits on the reading side (right in Urdu,
 * Sindhi, Persian, Arabic; left otherwise); a long list goes onto more
 * pages instead of one endless picture. Each first page is also saved to
 * Download/rtl-proof/ so the CI report carries the pictures for a person.
 */
class RateListOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    private val names = listOf(
        "Urea 50 kg", "ڈی اے پی کھاد", "سلفر 90 فیصد دانے دار", "कीटनाशक स्प्रे प्रीमियम 500 मिलीलीटर",
        "জৈব সার বিশেষ মিশ্রণ এক কেজি প্যাকেট", "مبيد حشري مركز عالي الجودة", "Pupuk NPK Mutiara 16-16-16"
    )

    private fun rows(n: Int) = (0 until n).map { i ->
        RateList.Row(
            productId = i.toLong(), name = names[i % names.size] + if (i >= names.size) " ${i + 1}" else "",
            company = if (i % 2 == 0) "Engro" else "FFC", unit = if (i % 3 == 0) "bag" else "kg",
            cash = 1000.0 + i * 37, credit = if (i % 4 == 0) null else 1100.0 + i * 41
        )
    }

    private fun input(rows: List<RateList.Row>) = RateListImage.Input(
        shopName = "Ali Traders Pasrur", contact = "0340 7026467  \u00b7  Main Bazaar", logo = null,
        heading = "This week", subLine = "5 Oct 2026", rows = rows,
        columns = RateList.Columns(cash = true, credit = true), companyPerRow = true, mark = "Roshan Khata"
    )

    @Test
    fun every_language_draws_a_page_with_the_names_on_the_reading_side() {
        val languages = listOf("en", "ur-Latn", "ur", "sd", "fa", "ar", "in", "hi", "bn")
        val rtl = setOf("ur", "sd", "fa", "ar")
        for (tag in languages) {
            val plan = RateListImage.plan(inLanguage(tag), input(rows(7)))
            assertEquals("$tag: seven rows fit one page", 1, plan.pages.size)
            val bmp = RateListImage.draw(plan, 0)
            assertEquals("$tag width", RateListImage.WIDTH, bmp.width)
            assertTrue("$tag height ${bmp.height}", bmp.height <= RateListImage.MAX_HEIGHT)
            if (tag in rtl) {
                assertTrue("$tag: names on the right", plan.nameLeft + plan.nameWidth > RateListImage.WIDTH - 60)
            } else {
                assertTrue("$tag: names on the left", plan.nameLeft < 60f)
            }
            save("ratelist_$tag.png", bmp)
            bmp.recycle()
        }
    }

    @Test
    fun a_long_list_goes_onto_more_pages_each_within_the_limit() {
        val plan = RateListImage.plan(inLanguage("bn"), input(rows(60)))
        assertTrue("60 rows need more than one page", plan.pages.size > 1)
        assertEquals("every row on exactly one page", 60, plan.pages.sumOf { it.count() })
        plan.pages.indices.forEach { i ->
            val bmp = RateListImage.draw(plan, i)
            assertTrue("page ${i + 1} height ${bmp.height}", bmp.height <= RateListImage.MAX_HEIGHT)
            if (i == 1) save("ratelist_bn_page2.png", bmp)
            bmp.recycle()
        }
    }

    private fun save(name: String, bmp: Bitmap) {
        if (Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/rtl-proof")
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        app.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
