package com.innovation313.roshankhata.data

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Cropping an uploaded photo down to just the part that matters, so a
 * "payment QR" saved here is the code itself rather than a screenshot of a
 * whole payment app around it, and a signature or stamp photographed on a
 * sheet of paper is the ink rather than the sheet.
 *
 * Cropping IS the compression the owner asked for, without any quality
 * loss: the saved file is still written lossless (PNG, unchanged), but a
 * tight crop of just the ink or the code is a small fraction of the pixels
 * a whole photographed page was, so the file shrinks for free.
 *
 * Both crops are best-effort. If nothing is confidently detected, the
 * original image comes back unchanged rather than risking a bad crop that
 * cuts off part of what the owner meant to save — a photo the owner
 * already framed reasonably well is a safe fallback; a wrong guess is not.
 */
object ImageAutoCrop {

    /**
     * Finds the QR code using the same ZXing decoder this app already uses
     * to generate its own codes, and returns just that region with a
     * safety margin. The margin matters as much as the crop itself: a QR
     * needs a plain "quiet zone" border around it to scan reliably, and the
     * finder-pattern centres ZXing reports sit inside the code's true
     * corners, not on them — a tight crop right to those points would cut
     * into the code itself.
     */
    fun cropToQr(bitmap: Bitmap): Bitmap {
        val bounds = detectQrBounds(bitmap) ?: return bitmap
        return cropWithPadding(bitmap, bounds, paddingFraction = 0.35f)
    }

    /**
     * The same detection [cropToQr] uses, but returned as a suggested
     * rectangle rather than already cropped — for the interactive cropper,
     * which shows this as a starting point the person can confirm or drag
     * to fix, rather than a guess that gets trusted outright.
     */
    fun suggestQrRect(bitmap: Bitmap): Rect? =
        detectQrBounds(bitmap)?.let { padded(it, bitmap.width, bitmap.height, 0.35f) }

    private fun detectQrBounds(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return try {
            val source = RGBLuminanceSource(width, height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val result = QRCodeReader().decode(binary)
            val points = result.resultPoints
            if (points.isNullOrEmpty()) return null

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (p in points) {
                if (p == null) continue
                minX = minOf(minX, p.x); minY = minOf(minY, p.y)
                maxX = maxOf(maxX, p.x); maxY = maxOf(maxY, p.y)
            }
            if (minX > maxX || minY > maxY) null
            else Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
        } catch (e: Exception) {
            // Covers ZXing's NotFoundException/FormatException/ChecksumException
            // and anything else a real-world photo could trigger — a QR that
            // could not be confidently located is not an error, it is a photo
            // this method cannot improve on, so the original is kept.
            null
        }
    }

    /**
     * Finds the ink on a photographed sheet of signature or stamp paper and
     * returns just that region. Works on any paper shade or lighting
     * because the dark/light split is chosen from THIS photo's own
     * brightness histogram (Otsu's method) rather than one fixed number
     * that would suit some paper and not others.
     */
    fun cropToInk(bitmap: Bitmap): Bitmap {
        val bounds = detectInkBounds(bitmap) ?: return bitmap
        return cropWithPadding(bitmap, bounds, paddingFraction = 0.08f)
    }

    /** Same reasoning as [suggestQrRect] — a starting rectangle for the person to confirm, not a final answer. */
    fun suggestInkRect(bitmap: Bitmap): Rect? =
        detectInkBounds(bitmap)?.let { padded(it, bitmap.width, bitmap.height, 0.08f) }

    private fun detectInkBounds(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val luminance = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val p = pixels[i]
            val l = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            luminance[i] = l
            histogram[l]++
        }

        val threshold = otsuThreshold(histogram, pixels.size)

        // A grid of blocks, not raw rows/columns. The first version of this
        // required a whole row or column to carry a meaningful share of dark
        // pixels — which a real but THIN cursive signature stroke never
        // does; it only crosses a couple of pixels of any one row, and the
        // whole photo was returned uncropped every time as a result. A block
        // catches the stroke because it fills several pixels of at least one
        // block it passes through, however thin the line — and noise
        // rejection moves from "enough pixels in this line" to "does this
        // block sit next to another filled block", which is what actually
        // tells real handwriting (a cluster of nearby marks) apart from a
        // stray dark speck (dust, a paper crease, compression noise) sitting
        // alone with nothing filled around it.
        val blockSize = maxOf(8, minOf(width, height) / 80)
        val cols = (width + blockSize - 1) / blockSize
        val rows = (height + blockSize - 1) / blockSize
        val darkCount = Array(rows) { IntArray(cols) }

        for (y in 0 until height) {
            val by = y / blockSize
            val base = y * width
            for (x in 0 until width) {
                // <= , not < : Otsu's threshold can land EXACTLY on the ink's
                // own luminance value when a photo is sharply two-toned
                // (plain paper + a fairly uniform ink colour, which a
                // signature or stamp usually is) — a strict less-than then
                // excludes the ink itself from ever counting as dark, and
                // nothing is ever detected. Caught by testing this against a
                // synthetic thin stroke before trusting it on a real photo.
                if (luminance[base + x] <= threshold) darkCount[by][x / blockSize]++
            }
        }

        val minDarkPixelsInBlock = 3
        val filled = Array(rows) { by -> BooleanArray(cols) { bx -> darkCount[by][bx] >= minDarkPixelsInBlock } }

        fun hasFilledNeighbour(by: Int, bx: Int): Boolean {
            for (dy in -1..1) for (dx in -1..1) {
                if (dy == 0 && dx == 0) continue
                val ny = by + dy
                val nx = bx + dx
                if (ny in 0 until rows && nx in 0 until cols && filled[ny][nx]) return true
            }
            return false
        }

        var top = -1
        var bottom = -1
        var left = -1
        var right = -1
        for (by in 0 until rows) {
            for (bx in 0 until cols) {
                if (!filled[by][bx] || !hasFilledNeighbour(by, bx)) continue
                val y0 = by * blockSize
                val y1 = minOf(height, y0 + blockSize) - 1
                val x0 = bx * blockSize
                val x1 = minOf(width, x0 + blockSize) - 1
                if (top == -1 || y0 < top) top = y0
                if (bottom == -1 || y1 > bottom) bottom = y1
                if (left == -1 || x0 < left) left = x0
                if (right == -1 || x1 > right) right = x1
            }
        }
        if (top == -1) return null

        return Rect(left, top, right, bottom)
    }

    /**
     * The brightness level that best splits the image into a dark group and
     * a light group — found by testing every possible split and keeping the
     * one where the two groups are most different from each other, relative
     * to how spread out each group is on its own. Standard technique
     * (Otsu, 1979); reimplemented here in plain Kotlin rather than adding an
     * image-processing library for one histogram scan.
     */
    private fun otsuThreshold(histogram: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in histogram.indices) sum += i.toDouble() * histogram[i]

        var sumDark = 0.0
        var countDark = 0
        var bestVariance = 0.0
        var threshold = 128

        for (i in histogram.indices) {
            countDark += histogram[i]
            if (countDark == 0) continue
            val countLight = total - countDark
            if (countLight == 0) break

            sumDark += i.toDouble() * histogram[i]
            val meanDark = sumDark / countDark
            val meanLight = (sum - sumDark) / countLight
            val diff = meanDark - meanLight

            val variance = countDark.toDouble() * countLight.toDouble() * diff * diff
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = i
            }
        }
        return threshold
    }

    private fun padded(box: Rect, bitmapWidth: Int, bitmapHeight: Int, paddingFraction: Float): Rect {
        val padX = (box.width() * paddingFraction).toInt().coerceAtLeast(4)
        val padY = (box.height() * paddingFraction).toInt().coerceAtLeast(4)
        return Rect(
            (box.left - padX).coerceAtLeast(0),
            (box.top - padY).coerceAtLeast(0),
            (box.right + padX).coerceAtMost(bitmapWidth),
            (box.bottom + padY).coerceAtMost(bitmapHeight)
        )
    }

    private fun cropWithPadding(bitmap: Bitmap, box: Rect, paddingFraction: Float): Bitmap {
        val p = padded(box, bitmap.width, bitmap.height, paddingFraction)
        val w = p.width()
        val h = p.height()
        if (w <= 0 || h <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, p.left, p.top, w, h)
    }
}
