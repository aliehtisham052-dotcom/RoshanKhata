package com.innovation313.roshankhata.data

/**
 * A phone number as WhatsApp's wa.me link wants it: bare international
 * digits, no plus, no separators. Shared by the phone's reminders and the
 * computer's (KMP Phase 7o).
 *
 * Numbers arrive in many shapes: 03001234567, +923001234567, 0092..., or
 * with spaces and dashes. A local number takes the SHOP'S country's calling
 * code ([ShopCountry.current], 21 Oct 2026): until then every shop's was
 * +92, and a Bangladeshi customer's 01712345678 became a Pakistani number.
 * A "+" or "00" number is already international and is used as it is. A
 * shop in a country the app does not know (OTHER) has no code to add, so a
 * local number there gives no link rather than a wrong one.
 */
object WhatsAppNumber {
    fun of(raw: String, defaultCountryCode: String = ShopCountry.current.dialing): String? {
        // Digits the keyboard may have typed in another script count too.
        val typed = Digits.toLatin(raw).trim()
        val digits = typed.filter { it in '0'..'9' }
        if (digits.isEmpty()) return null
        val international = typed.startsWith("+") || digits.startsWith("00")
        return when {
            // +92 300 1234567 / 00 92 300 1234567
            international -> digits.removePrefix("00").takeIf { it.length >= 8 }
            defaultCountryCode.isEmpty() -> null
            // 92 300 1234567  (already international, no plus)
            digits.startsWith(defaultCountryCode) && digits.length >= defaultCountryCode.length + 9 -> digits
            // 0300 1234567  (local, leading zero)
            digits.startsWith("0") -> defaultCountryCode + digits.drop(1)
            // 300 1234567  (local, no leading zero)
            digits.length in 8..10 -> defaultCountryCode + digits
            else -> digits
        }
    }
}
