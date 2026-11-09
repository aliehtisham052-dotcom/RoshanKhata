package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * The business card as a moving card (9 Nov 2026): the card's own animation (the phase the live
 * preview loops through) recorded into a silent 4-second H.264 MP4 at the card's picture size
 * (1200 x 700), 30 frames a second. One full turn of the phase over the clip, so it loops without a
 * jump when WhatsApp plays it as a GIF.
 *
 * The encoder, muxer and YUV conversion follow [PosterVideo] (the phone's own MediaCodec, no library).
 */
object CardVideo {

    const val FPS = 30
    const val SECONDS = 4
    /** Frames in the clip: fps * seconds, never fewer. */
    const val FRAMES = FPS * SECONDS
    private const val BITRATE = 4_000_000

    /** True when [template] changes with the phase (or carries the shine band): a still card has nothing to record. */
    fun moves(template: CardTemplates.Template, data: CardTemplates.CardData): Boolean {
        if (template.shine) return true
        val w = 300
        val h = w * CardTemplates.H / CardTemplates.W
        val a = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return try {
            for ((bmp, t) in listOf(a to 0.25f, b to 0.5f)) {
                val c = Canvas(bmp)
                val s = w.toFloat() / CardTemplates.W
                c.scale(s, s)
                template.draw(c, data, CardTemplates.W, CardTemplates.H, t)
            }
            !a.sameAs(b)
        } catch (e: Exception) {
            false
        } finally {
            a.recycle()
            b.recycle()
        }
    }

    /** Make the clip into [out]. [onProgress] gets 0..100 on the main thread. */
    suspend fun make(
        template: CardTemplates.Template,
        data: CardTemplates.CardData,
        out: File,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.Default) {
        val width = CardTemplates.W
        val height = CardTemplates.H
        require(width % 2 == 0 && height % 2 == 0)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        out.delete()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        val info = MediaCodec.BufferInfo()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pixels = IntArray(width * height)

        fun drain(endOfStream: Boolean) {
            var waits = 0
            while (true) {
                val i = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++waits > 500) return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    i >= 0 -> {
                        val buf = codec.getOutputBuffer(i)
                        if (buf != null && info.size > 0 && muxing && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                    else -> if (!endOfStream) return
                }
            }
        }

        try {
            for (n in 0 until FRAMES) {
                coroutineContext.ensureActive()
                // One full turn of the preview's phase over the clip: the last frame leads into the first.
                val phase = n / FRAMES.toFloat()
                bmp.eraseColor(0)
                canvas.save()
                template.draw(canvas, data, width, height, phase)
                if (template.shine) CardPreviewView.drawShine(canvas, phase)
                canvas.restore()
                bmp.getPixels(pixels, 0, width, 0, 0, width, height)
                var index = codec.dequeueInputBuffer(10_000L)
                while (index < 0) {
                    drain(false)
                    index = codec.dequeueInputBuffer(10_000L)
                }
                val image = codec.getInputImage(index) ?: error("The encoder gave no input image")
                toYuv(pixels, width, height, image)
                codec.queueInputBuffer(index, 0, width * height * 3 / 2, n * 1_000_000L / FPS, 0)
                drain(false)
                withContext(Dispatchers.Main) { onProgress((n + 1) * 100 / FRAMES) }
            }
            var index = codec.dequeueInputBuffer(10_000L)
            while (index < 0) { drain(false); index = codec.dequeueInputBuffer(10_000L) }
            codec.queueInputBuffer(index, 0, 0, FRAMES * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { if (muxing) muxer.stop() } catch (_: Exception) {}
            muxer.release()
            bmp.recycle()
        }
        out
    }

    /** ARGB into the encoder's YUV 4:2:0 image (BT.601, studio range), any plane layout. */
    private fun toYuv(argb: IntArray, width: Int, height: Int, image: Image) {
        val w = image.width.coerceAtMost(width)
        val h = image.height.coerceAtMost(height)
        val y = image.planes[0]
        val u = image.planes[1]
        val v = image.planes[2]
        val yBuf = y.buffer
        val uBuf = u.buffer
        val vBuf = v.buffer
        val yRow = ByteArray(w)
        for (row in 0 until h) {
            val base = row * width
            for (x in 0 until w) {
                val p = argb[base + x]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                yRow[x] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
            }
            if (y.pixelStride == 1) {
                yBuf.position(row * y.rowStride)
                yBuf.put(yRow, 0, w)
            } else {
                for (x in 0 until w) yBuf.put(row * y.rowStride + x * y.pixelStride, yRow[x])
            }
        }
        for (row in 0 until h / 2) {
            for (x in 0 until w / 2) {
                var r = 0; var g = 0; var b = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val p = argb[(row * 2 + dy) * width + x * 2 + dx]
                    r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                }
                r /= 4; g /= 4; b /= 4
                val cb = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
                val cr = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
                uBuf.put(row * u.rowStride + x * u.pixelStride, cb)
                vBuf.put(row * v.rowStride + x * v.pixelStride, cr)
            }
        }
    }
}
