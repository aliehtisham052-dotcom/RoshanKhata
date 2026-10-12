package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The poster screen itself (12 Oct), photographed for a person to look at:
 * the top (designs, language, the live preview) and, for Closed, Timings and
 * Ramazan, that design's own fields. Pictures go to Download/rtl-proof/.
 */
class PosterScreenProofTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun the_screen_opens_with_each_designs_fields() {
        for (key in listOf("new_stock", "closed", "hours", "ramazan", "offer", "azadi")) {
            app.getSharedPreferences("poster", Context.MODE_PRIVATE).edit().putString("occasion", key).commit()
            ActivityScenario.launch(PosterActivity::class.java).use { scenario ->
                SystemClock.sleep(2500)
                if (key == "new_stock") save("screen_top.png")
                scenario.onActivity { a ->
                    val scroll = a.findViewById<ScrollView>(R.id.posterScroll)
                    val card = a.findViewById<android.view.View>(R.id.etPosterTitle)
                    var y = 0
                    var v: android.view.View? = card
                    while (v != null && v !== scroll) { y += v.top; v = v.parent as? android.view.View }
                    scroll.scrollTo(0, (y - 120).coerceAtLeast(0))
                }
                SystemClock.sleep(1200)
                save("screen_fields_$key.png")
            }
        }
        assertTrue(true)
    }

    private fun save(name: String) {
        val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        if (Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/rtl-proof")
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        app.contentResolver.openOutputStream(uri)?.use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shot.recycle()
    }
}
