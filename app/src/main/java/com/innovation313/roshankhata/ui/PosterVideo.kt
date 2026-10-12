package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.innovation313.roshankhata.ui.poster3d.HeroStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * A poster as a short Status video (12 Oct): the still poster with its 3D
 * hero moving and its 2D motion (stars, glint, neon, aurora) on top, as in
 * the live preview — H.264 in an MP4, 720 × 1280, 30 frames a second, no
 * sound. WhatsApp plays it on Status as it is.
 *
 * Made with the phone's own encoder (MediaCodec) and muxer, no extra
 * library. Each frame is drawn in full, turned into YUV and given its own
 * time stamp, so the video plays at the right speed however long the phone
 * takes to draw it.
 */
object PosterVideo {

    const val WIDTH = 720
    const val HEIGHT = 1280
    const val FPS = 30
    private const val BITRATE = 5_000_000
    private const val TAG = "PosterVideo"

    /**
     * Make the video into [out].
     *
     * [base] is the poster with the hero's place left empty when there is a
     * [stage] (its hero is drawn into [heroRect] frame by frame), or the whole
     * poster when there is none. [motion] is the 2D layer. [onProgress] gets
     * 0..100 on the caller's thread.
     */
    suspend fun make(
        base: Bitmap,
        stage: HeroStage?,
        heroRect: RectF?,
        motion: PosterMotion.Spec,
        seconds: Float,
        out: File,
        onProgress: (Int) -> Unit = {}
    ): File {
        val frames = (seconds * FPS).toInt().coerceAtLeast(1)
        val clockStart = System.currentTimeMillis()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
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
        val canvasBmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val pixels = IntArray(WIDTH * HEIGHT)
        val cache = PosterMotion.Cache()
        val scale = WIDTH / PosterKit.W
        val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val heroW = heroRect?.width()?.times(scale)?.toInt() ?: 0
        val heroH = heroRect?.height()?.times(scale)?.toInt() ?: 0

        fun drain(endOfStream: Boolean) {
            // At the end, wait for the encoder's last buffer — but not forever (5 s).
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
            for (n in 0 until frames) {
                coroutineContext.ensureActive()
                val t = n / FPS.toFloat()
                // The hero at this moment, from the GPU (main thread).
                val hero = if (stage != null && heroRect != null) withContext(Dispatchers.Main) {
                    try {
                        stage.frameAt(heroW, heroH, t, clockStart + (t * 1000).toLong())
                    } catch (e: Exception) {
                        Log.w(TAG, "frame $n: no hero", e)
                        null
                    }
                } else null
                withContext(Dispatchers.Default) {
                    canvas.save()
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(base, null, RectF(0f, 0f, PosterKit.W, PosterKit.H), filter)
                    if (hero != null && heroRect != null) canvas.drawBitmap(hero, null, heroRect, filter)
                    PosterMotion.draw(canvas, motion, t, cache)
                    canvas.restore()
                    hero?.recycle()
                    canvasBmp.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                    var index = codec.dequeueInputBuffer(10_000L)
                    while (index < 0) {
                        drain(false)
                        index = codec.dequeueInputBuffer(10_000L)
                    }
                    val image = codec.getInputImage(index) ?: error("The encoder gave no input image")
                    toYuv(pixels, image)
                    codec.queueInputBuffer(index, 0, WIDTH * HEIGHT * 3 / 2, n * 1_000_000L / FPS, 0)
                    drain(false)
                }
                withContext(Dispatchers.Main) { onProgress((n + 1) * 100 / frames) }
            }
            withContext(Dispatchers.Default) {
                var index = codec.dequeueInputBuffer(10_000L)
                while (index < 0) { drain(false); index = codec.dequeueInputBuffer(10_000L) }
                codec.queueInputBuffer(index, 0, 0, frames * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { if (muxing) muxer.stop() } catch (_: Exception) {}
            muxer.release()
            canvasBmp.recycle()
            cache.clear()
        }
        return out
    }

    /**
     * ARGB pixels into the encoder's YUV 4:2:0 image (BT.601, studio range),
     * whatever its plane layout: planar or semi-planar, any row and pixel stride.
     */
    private fun toYuv(argb: IntArray, image: android.media.Image) {
        val w = image.width.coerceAtMost(WIDTH)
        val h = image.height.coerceAtMost(HEIGHT)
        val y = image.planes[0]
        val u = image.planes[1]
        val v = image.planes[2]
        val yBuf = y.buffer
        val uBuf = u.buffer
        val vBuf = v.buffer
        val yRow = ByteArray(w)
        for (row in 0 until h) {
            val base = row * WIDTH
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
                // The average of the 2 × 2 block.
                var r = 0; var g = 0; var b = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val p = argb[(row * 2 + dy) * WIDTH + x * 2 + dx]
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
