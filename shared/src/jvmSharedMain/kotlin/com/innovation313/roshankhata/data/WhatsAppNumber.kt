package com.innovation313.roshankhata.data

/**
 * A phone number as WhatsApp's wa.me link wants it: bare international
 * digits, no plus, no separators. Shared by the phone's reminders and the
 * computer's (KMP Phase 7o).
 *
 * Pakistani numbers arrive in many shapes: 03001234567, +923001234567,
 * 0092..., or with spaces and dashes.
 */
object WhatsAppNumber {
    fun of(raw: String, defaultCountryCode: String = "92"): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return when {
            // 00 92 300 1234567
            digits.startsWith("00") && digits.length > 4 -> digits.removePrefix("00")
            // 92 300 1234567  (already international)
            digits.startsWith(defaultCountryCode) && digits.length >= 12 -> digits
            // 0300 1234567  (local, leading zero)
            digits.startsWith("0") -> defaultCountryCode + digits.drop(1)
            // 300 1234567  (local, no leading zero)
            digits.length in 9..10 -> defaultCountryCode + digits
            else -> digits
        }
    }
}
