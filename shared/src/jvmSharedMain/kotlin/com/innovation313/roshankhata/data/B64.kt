package com.innovation313.roshankhata.data

/**
 * Standard Base64 (RFC 4648, with padding), written out (22 Oct 2026):
 * java.util.Base64 exists on Android only from 8.0 (API 26) and the app
 * runs from 7.0 (API 24), where a PIN or a relay parcel would have crashed.
 * B64Test holds it to the JDK's own encoder, byte for byte.
 */
object B64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val VALUES = IntArray(128) { -1 }.also { v -> ALPHABET.forEachIndexed { i, c -> v[c.code] = i } }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
            out.append(if (b1 < 0) '=' else ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 < 0) 0 else b2 shr 6)])
            out.append(if (b2 < 0) '=' else ALPHABET[b2 and 0x3F])
            i += 3
        }
        return out.toString()
    }

    /**
     * The bytes of [text], read as the JDK's own decoder reads it (22 Oct
     * 2026, the review: this took anything after the first "=" and a lone
     * last character): the alphabet only; "=" only at the end, and only as
     * much as the last group needs, or none; never one character left over.
     * Spaces and line breaks are skipped. Throws IllegalArgumentException
     * otherwise. B64Test holds it to the JDK on random text.
     */
    fun decode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var count = 0
        var pad = 0
        for (c in text) {
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue
            if (c == '=') { pad++; continue }
            require(pad == 0) { "not Base64: data after padding" }
            val v = if (c.code < 128) VALUES[c.code] else -1
            require(v >= 0) { "not Base64: $c" }
            buffer = (buffer shl 6) or v
            bits += 6
            count++
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        val rest = count % 4
        require(rest != 1) { "not Base64: one character left over" }
        if (pad > 0) require((rest == 2 && pad == 2) || (rest == 3 && pad == 1)) { "not Base64: wrong padding" }
        return out.toByteArray()
    }
}
