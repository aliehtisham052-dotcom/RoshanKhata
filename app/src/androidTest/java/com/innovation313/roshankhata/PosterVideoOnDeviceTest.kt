package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.PosterFields
import com.innovation313.roshankhata.data.PosterOccasion
import com.innovation313.roshankhata.ui.PosterDesigns
import com.innovation313.roshankhata.ui.PosterImage
import com.innovation313.roshankhata.ui.PosterVideo
import com.innovation313.roshankhata.ui.poster3d.HeroStage
import com.innovation313.roshankhata.ui.poster3d.Studio
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The Status video (12 Oct): made on a real Android with the phone's own
 * encoder, then read back — H.264, 720 × 1280, the length asked for, 30
 * frames a second — and a frame from its middle saved for a person to look
 * at, with the MP4 itself, in Download/rtl-proof/.
 *
 * Eid ul-Fitr carries the 3D hero (when the GPU can draw it); Closed is 2D
 * only (the neon), so both paths are made.
 */
class PosterVideoOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    @Test
    fun a_status_video_is_made_and_reads_back() {
        var gpu = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { gpu = Studio.engineReady(app) && !Studio.isLegacySwiftShader }
        for ((o, tag) in listOf(PosterOccasion.EID_FITR to "ur", PosterOccasion.CLOSED to "en")) {
            val ctx = inLanguage(tag)
            val hero = PosterDesigns.hero(o)?.takeIf { gpu }
            var stage: HeroStage? = null
            if (hero != null) InstrumentationRegistry.getInstrumentation().runOnMainSync { stage = HeroStage(app, hero) }
            val fields = if (o == PosterOccasion.CLOSED) PosterFields(reason = PosterFields.Reason.WEDDING.key) else PosterFields(eidDays = 3)
            val (base, fit) = PosterImage.draw(ctx, PosterImage.Input(
                occasion = o, title = ctx.getString(o.title), message = ctx.getString(o.message),
                shopName = if (tag == "ur") "علی ٹریڈرز" else "Ali Traders", phone = "0300 0000000",
                address = "Main Bazaar", logo = null, mark = ctx.getString(R.string.made_with_app),
                heroOverlay = stage != null, fields = fields
            ))
            val out = File(app.cacheDir, "test_${o.key}.mp4")
            val started = System.currentTimeMillis()
            runBlocking { withTimeout(240_000L) { PosterVideo.make(base, stage, hero?.rect, fit.motion, 2f, out) } }
            Log.i("PosterVideo", "${o.key}: ${out.length()} bytes in ${System.currentTimeMillis() - started} ms (3D: ${stage != null})")
            base.recycle()
            stage?.let { s -> InstrumentationRegistry.getInstrumentation().runOnMainSync { s.destroy() } }

            assertTrue("${o.key}: empty video", out.length() > 10_000)
            val ex = MediaExtractor()
            ex.setDataSource(out.path)
            assertEquals(1, ex.trackCount)
            val f = ex.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, f.getString(MediaFormat.KEY_MIME))
            assertEquals(PosterVideo.WIDTH, f.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(PosterVideo.HEIGHT, f.getInteger(MediaFormat.KEY_HEIGHT))
            ex.selectTrack(0)
            var samples = 0
            while (ex.advance()) samples++
            ex.release()
            Log.i("PosterVideo", "${o.key}: ${samples + 1} frames, duration ${f.getLong(MediaFormat.KEY_DURATION)} us")
            assertTrue("${o.key}: ${samples + 1} frames, want about 60", samples + 1 in 55..62)
            val durationUs = f.getLong(MediaFormat.KEY_DURATION)
            assertTrue("${o.key}: lasts $durationUs us, want about 2 s", durationUs in 1_800_000L..2_200_000L)

            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(out.path)
            val frame = mmr.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST)
            mmr.release()
            assertTrue("${o.key}: no frame at 1 s", frame != null)
            save("video_${o.key}_frame.png", "image/png") { frame!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            save("video_${o.key}.mp4", "video/mp4") { s -> out.inputStream().use { it.copyTo(s) } }
            frame?.recycle()
        }
    }

    private fun save(name: String, mime: String, write: (java.io.OutputStream) -> Unit) {
        if (Build.VERSION.SDK_INT < 29) return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/rtl-proof")
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        app.contentResolver.openOutputStream(uri)?.use(write)
    }
}
