package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.Locale

/**
 * The shop's country (21 Oct 2026, the nine-language review).
 *
 * The app had a language and no country, so it took every shop for a
 * Pakistani one: WhatsApp links got +92, invoices "Rs", registers
 * "NTN/STRN", posters "Pakistan Zindabad". Microsoft's globalization
 * guideline says it plainly: never infer the region from the language. So
 * the country is its own setting, kept with the shop's profile (and so in
 * every backup), and everything that differs by country reads this one
 * table instead of guessing.
 *
 * What a country decides: the calling code, the currency and its decimals,
 * what the tax number is called, which day the week starts and which is the
 * weekly off, the calendar most shops there date by, the clock, the wallet
 * services a hint may name, the crop seasons, and whether Pakistan's own
 * occasions apply. Nothing here changes a stored figure.
 *
 * @property code ISO 3166-1 alpha-2, the key kept in the profile.
 * @property dialing The calling code, digits only.
 * @property currencySign What [Currency] prints; one of [Currency.CHOICES].
 * @property currencyDecimals How many decimals the currency's smallest unit has.
 * @property taxIdName What the shop's tax registration is called on a sheet.
 * @property weekStart [Calendar.MONDAY] and friends.
 * @property weeklyOff The usual closed day, for the hours poster.
 * @property calendar The calendar most shops there date by.
 * @property clock24 Whether the clock is written 15:30 rather than 3:30 PM.
 * @property wallets Mobile-wallet services, for hints only.
 * @property seasons Whether Rabi/Kharif crop seasons apply (the agri trade's).
 * @property pakistan Whether Pakistan's own content (the 14 August poster) applies.
 */
enum class ShopCountry(
    val code: String,
    val dialing: String,
    val currencySign: String,
    val currencyDecimals: Int,
    val taxIdName: String,
    val weekStart: Int,
    val weeklyOff: Int,
    val calendar: ShopCalendar.Kind,
    val clock24: Boolean,
    val wallets: String,
    val seasons: Boolean = false,
    val pakistan: Boolean = false
) {
    PAKISTAN("PK", "92", "Rs", 2, "NTN / STRN", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "JazzCash, EasyPaisa", seasons = true, pakistan = true),
    INDIA("IN", "91", "₹", 2, "GSTIN", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "UPI, PhonePe, Paytm", seasons = true),
    BANGLADESH("BD", "880", "৳", 2, "BIN", Calendar.SUNDAY, Calendar.FRIDAY, ShopCalendar.Kind.GREGORIAN, false, "bKash, Nagad, Rocket"),
    SAUDI_ARABIA("SA", "966", "SAR", 2, "VAT No.", Calendar.SUNDAY, Calendar.FRIDAY, ShopCalendar.Kind.HIJRI, false, "STC Pay, mada"),
    UAE("AE", "971", "AED", 2, "TRN", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "e& money, Payit"),
    QATAR("QA", "974", "QAR", 2, "TIN", Calendar.SUNDAY, Calendar.FRIDAY, ShopCalendar.Kind.GREGORIAN, false, "Ooredoo Money"),
    OMAN("OM", "968", "OMR", 3, "VATIN", Calendar.SUNDAY, Calendar.FRIDAY, ShopCalendar.Kind.GREGORIAN, false, "Thawani"),
    KUWAIT("KW", "965", "KWD", 3, "Tax No.", Calendar.SUNDAY, Calendar.FRIDAY, ShopCalendar.Kind.GREGORIAN, false, "KNET"),
    BAHRAIN("BH", "973", "BHD", 3, "VAT No.", Calendar.SUNDAY, Calendar.FRIDAY, ShopCalendar.Kind.GREGORIAN, false, "BenefitPay"),
    EGYPT("EG", "20", "EGP", 2, "Tax ID", Calendar.SATURDAY, Calendar.FRIDAY, ShopCalendar.Kind.GREGORIAN, false, "Vodafone Cash, InstaPay"),
    IRAN("IR", "98", "IRR", 0, "Economic Code", Calendar.SATURDAY, Calendar.FRIDAY, ShopCalendar.Kind.JALALI, true, "Shetab card"),
    AFGHANISTAN("AF", "93", "AFN", 2, "TIN", Calendar.SATURDAY, Calendar.FRIDAY, ShopCalendar.Kind.JALALI, false, "HesabPay, M-Paisa"),
    INDONESIA("ID", "62", "Rp", 0, "NPWP", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, true, "GoPay, OVO, DANA, QRIS"),
    MALAYSIA("MY", "60", "RM", 2, "SST No.", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "Touch 'n Go, DuitNow"),
    UNITED_KINGDOM("GB", "44", "£", 2, "VAT No.", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, true, "PayPal"),
    UNITED_STATES("US", "1", "$", 2, "EIN", Calendar.SUNDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "Zelle, Venmo, Cash App"),
    CANADA("CA", "1", "CA$", 2, "GST/HST No.", Calendar.SUNDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "Interac e-Transfer"),
    AUSTRALIA("AU", "61", "A$", 2, "ABN", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "PayID"),
    NIGERIA("NG", "234", "₦", 2, "TIN", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "OPay, PalmPay"),
    KENYA("KE", "254", "KSh", 2, "KRA PIN", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "M-Pesa"),
    SOUTH_AFRICA("ZA", "27", "R", 2, "VAT No.", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "SnapScan"),
    TURKEY("TR", "90", "₺", 2, "VKN", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, true, "Papara"),
    /** Anywhere else: no calling code assumed, so a number is used as typed. */
    OTHER("", "", "Rs", 2, "Tax ID", Calendar.MONDAY, Calendar.SUNDAY, ShopCalendar.Kind.GREGORIAN, false, "");

    /** Gold and silver are quoted by the tola in Pakistan, India and Bangladesh; by the gram elsewhere. */
    val tola: Boolean get() = this == PAKISTAN || this == INDIA || this == BANGLADESH || this == AFGHANISTAN

    /** The country's own name, in the app's language, from the JDK's locale data. */
    fun displayName(locale: Locale): String =
        if (code.isEmpty()) "" else Locale("", code).getDisplayCountry(locale).ifBlank { code }

    companion object {
        /** The shop's country now, read with the profile ([refresh] on the phone, FileShopSettings on Windows). */
        @Volatile
        var current: ShopCountry = PAKISTAN

        /** The country a stored key names; Pakistan for a book from before the setting existed. */
        fun of(key: String?): ShopCountry = entries.firstOrNull { it.code == key?.uppercase(Locale.ROOT) } ?: PAKISTAN

        /**
         * The country a new shop starts in. The device's own region when it is
         * one we know (a phone bought in Dhaka says BD); else the first country
         * that prints the shop's chosen currency; else what the language
         * suggests (Hindi → India, Bengali → Bangladesh, Farsi → Iran,
         * Indonesian → Indonesia, Arabic → Saudi Arabia); else Pakistan.
         */
        fun guess(deviceRegion: String?, currency: String?, language: String?): ShopCountry {
            deviceRegion?.uppercase(Locale.ROOT)?.let { r -> entries.firstOrNull { it.code == r }?.let { return it } }
            if (currency != null && currency != Currency.DEFAULT) {
                entries.firstOrNull { it.currencySign == currency }?.let { return it }
            }
            return when (language?.lowercase(Locale.ROOT)?.substringBefore('-')) {
                "hi" -> INDIA
                "bn" -> BANGLADESH
                "fa" -> IRAN
                "id", "in" -> INDONESIA
                "ar" -> SAUDI_ARABIA
                else -> PAKISTAN
            }
        }

        /** The choices a picker offers: the shop's region first, then the rest by name in [locale]. */
        fun choices(locale: Locale): List<ShopCountry> {
            val named = entries.filter { it != OTHER }
            val first = listOf(PAKISTAN, INDIA, BANGLADESH, SAUDI_ARABIA, UAE, IRAN, AFGHANISTAN, INDONESIA)
            return first + named.filter { it !in first }.sortedBy { it.displayName(locale) } + OTHER
        }
    }
}
