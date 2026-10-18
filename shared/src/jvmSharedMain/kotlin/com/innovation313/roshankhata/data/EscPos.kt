package com.innovation313.roshankhata.data

/**
 * The bytes a Bluetooth thermal receipt printer understands (ESC/POS), for a
 * picture: the receipt card already drawn on screen, so Urdu, Arabic, Hindi
 * and Bengali print exactly as they read in the app — no printer font is
 * asked to shape a script it has never seen.
 *
 * Plain Kotlin over ARGB pixels, so every byte is unit-tested (EscPosTest).
 */
object EscPos {

    /** Dots across a 58 mm roll at 203 dpi. An 80 mm printer prints it too, a little narrower. */
    const val WIDTH_58MM = 384

    /** Rows per raster block: small printers drop a picture sent as one huge block. */
    const val BAND = 256

    private fun luma(argb: Int): Int {
        val a = argb ushr 24 and 0xFF
        if (a < 128) return 255 // transparent prints as paper
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    /**
     * Initialise, the picture in bands of [BAND] rows (GS v 0), feed four
     * lines and cut where there is a cutter (printers without one ignore it).
     *
     * A picture that is mostly dark — the receipt card in the app's dark
     * theme — is inverted first: thermal paper prints black on white, and a
     * dark card printed as it is would come out a solid black strip.
     */
    fun raster(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(pixels.size >= width * height) { "pixels too short" }
        var total = 0L
        for (i in 0 until width * height) total += luma(pixels[i])
        val invert = width * height > 0 && total / (width * height) < 128
        val rowBytes = (width + 7) / 8
        val out = java.io.ByteArrayOutputStream(rowBytes * height + 64)
        out.write(byteArrayOf(0x1B, 0x40))
        var top = 0
        while (top < height) {
            val rows = minOf(BAND, height - top)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
                (rowBytes and 0xFF).toByte(), (rowBytes shr 8 and 0xFF).toByte(),
                (rows and 0xFF).toByte(), (rows shr 8 and 0xFF).toByte()))
            for (y in top until top + rows) {
                for (bx in 0 until rowBytes) {
                    var byte = 0
                    for (bit in 0 until 8) {
                        val x = bx * 8 + bit
                        if (x >= width) continue
                        val dark = luma(pixels[y * width + x]) < 128
                        if (dark != invert) byte = byte or (0x80 ushr bit)
                    }
                    out.write(byte)
                }
            }
            top += rows
        }
        out.write(byteArrayOf(0x1B, 0x64, 0x04))
        out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        return out.toByteArray()
    }
}
