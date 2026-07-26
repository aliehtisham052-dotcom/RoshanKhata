package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR payload as a bitmap, ready for a screen or a shared PNG.
 *
 * Plain black on white and nothing decorative on the code itself — a QR at a
 * shop counter is scanned off paper that has lived in a pocket, or off a
 * cracked screen at whatever brightness is left by evening. Error correction
 * M gives a scuffed print a fighting chance without bloating the module
 * count the way H would.
 */
object QrImage {

    fun of(payload: String, sizePx: Int = 800): Bitmap {
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                // The quiet border scanners rely on. Kept in the bitmap itself
                // so a shared PNG scans even when something crops it tight.
                EncodeHintType.MARGIN to 2
            )
        )
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val row = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }
}
