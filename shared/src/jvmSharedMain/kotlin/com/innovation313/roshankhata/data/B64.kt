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

    /** The bytes of [text]; spaces and line breaks are skipped. Throws on a character outside the alphabet. */
    fun decode(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=' ) break
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') continue
            val v = if (c.code < 128) VALUES[c.code] else -1
            require(v >= 0) { "not Base64: $c" }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
