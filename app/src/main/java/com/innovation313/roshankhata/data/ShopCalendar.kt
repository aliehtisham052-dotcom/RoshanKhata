package com.innovation313.roshankhata.data

import android.content.Context
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Which calendar the shop reads its dates in: Gregorian (the default),
 * Hijri, or Solar Hijri (Jalali, the calendar of Iran and Afghanistan).
 *
 * DISPLAY ONLY. Every date is still stored as epoch milliseconds, file names
 * and CSV stay yyyy-MM-dd, and the date pickers stay the phone's own. Switching
 * calendars changes how a date is printed, never what is saved.
 *
 * Hijri comes from Android's Umm al-Qura tables (android.icu, API 24 = this
 * app's minSdk). It is a calculation, not a sighting: in Pakistan the month
 * starts with the moon, so a day's difference is normal. The setting says so,
 * and nothing in the app (Zakat included) takes a religious date from it.
 *
 * Jalali is worked out in plain Kotlin (the Borkowski / jalaali method), because
 * Android does not expose a Persian calendar class; ShopCalendarTest checks it
 * against the jdatetime library, Nowruz on both sides of the year included.
 */
object ShopCalendar {

    enum class Kind(val key: String) {
        GREGORIAN("gregorian"), HIJRI("hijri"), JALALI("jalali");

        companion object {
            fun of(key: String?): Kind = values().firstOrNull { it.key == key } ?: GREGORIAN
        }
    }

    @Volatile
    var current: Kind = Kind.GREGORIAN
        private set

    /** Re-read the active shop's choice (same places Currency is refreshed). */
    fun refresh(context: Context) {
        current = BusinessProfile.calendar(context)
    }

    fun set(context: Context, kind: Kind) {
        BusinessProfile.setCalendar(context, kind)
        refresh(context)
    }

    /** Tests only: print in [kind] without a shop behind it. */
    internal fun useForTest(kind: Kind) {
        current = kind
    }

    /** Year, month (1-12) and day of [millis] in [kind], on the phone's local day. */
    fun ymd(kind: Kind, millis: Long, zone: TimeZone = TimeZone.getDefault()): Triple<Int, Int, Int> {
        if (kind == Kind.HIJRI) return hijri(millis, zone)
        val c = Calendar.getInstance(zone).apply { timeInMillis = millis }
        val g = Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return if (kind == Kind.JALALI) jalali(g.first, g.second, g.third) else g
    }

    private fun hijri(millis: Long, zone: TimeZone): Triple<Int, Int, Int> {
        val c = android.icu.util.IslamicCalendar(
            android.icu.util.TimeZone.getTimeZone(zone.id), android.icu.util.ULocale.ENGLISH
        )
        c.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        c.timeInMillis = millis
        return Triple(
            c.get(android.icu.util.Calendar.YEAR),
            c.get(android.icu.util.Calendar.MONTH) + 1,
            c.get(android.icu.util.Calendar.DAY_OF_MONTH)
        )
    }

    // ---------- Jalali (jalaali-js, Behrooz Borkowski's breaks) ----------

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCal {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += jump / 33 * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += n / 33 * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 + (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
        d = d - ((gy + 100100 + (gm - 8) / 6) / 100 * 3) / 4 + 752
        return d
    }

    /** Gregorian date to Jalali (year, month 1-12, day). */
    fun jalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val jdn = g2d(gy, gm, gd)
        var jy = gy - 621
        val r = jalCal(jy)
        val first = g2d(gy, 3, r.march)
        var k = jdn - first
        if (k >= 0) {
            if (k <= 185) return Triple(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return Triple(jy, 7 + k / 30, k % 30 + 1)
    }

    // ---------- Month names, by DateWords' language key ----------

    private val HIJRI_MONTHS: Map<String, Array<String>> = mapOf(
        "en" to arrayOf("Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Shaban", "Ramazan", "Shawwal", "Zul-Qadah", "Zul-Hijjah"),
        "ur" to arrayOf("محرم", "صفر", "ربیع الاول", "ربیع الثانی", "جمادی الاول", "جمادی الثانی",
            "رجب", "شعبان", "رمضان", "شوال", "ذوالقعدہ", "ذوالحجہ"),
        "sd" to arrayOf("محرم", "صفر", "ربيع الاول", "ربيع الثاني", "جمادي الاول", "جمادي الثاني",
            "رجب", "شعبان", "رمضان", "شوال", "ذوالقعد", "ذوالحج"),
        "fa" to arrayOf("محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
            "رجب", "شعبان", "رمضان", "شوال", "ذیقعده", "ذیحجه"),
        "ar" to arrayOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"),
        "id" to arrayOf("Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jumadilawal", "Jumadilakhir",
            "Rajab", "Syakban", "Ramadan", "Syawal", "Zulkaidah", "Zulhijah"),
        "hi" to arrayOf("मुहर्रम", "सफ़र", "रबी-उल-अव्वल", "रबी-उस-सानी", "जमादी-उल-अव्वल", "जमादी-उस-सानी",
            "रजब", "शाबान", "रमज़ान", "शव्वाल", "ज़िल-क़ादा", "ज़िल-हिज्जा"),
        "bn" to arrayOf("মহররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
            "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ")
    )

    private val JALALI_MONTHS: Map<String, Array<String>> = mapOf(
        "en" to arrayOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"),
        "ur" to arrayOf("فروردین", "اردیبہشت", "خرداد", "تیر", "مرداد", "شہریور",
            "مہر", "آبان", "آذر", "دی", "بہمن", "اسفند"),
        "sd" to arrayOf("فروردين", "ارديبهشت", "خرداد", "تير", "مرداد", "شهريور",
            "مهر", "آبان", "آذر", "دي", "بهمن", "اسفند"),
        "fa" to arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"),
        "ar" to arrayOf("فروردين", "أرديبهشت", "خرداد", "تير", "مرداد", "شهريور",
            "مهر", "آبان", "آذر", "دي", "بهمن", "اسفند"),
        "id" to arrayOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"),
        "hi" to arrayOf("फ़रवरदीन", "उर्दिबहिश्त", "ख़ुरदाद", "तीर", "मुरदाद", "शहरीवर",
            "मेहर", "आबान", "आज़र", "दे", "बहमन", "इस्फ़ंद"),
        "bn" to arrayOf("ফারভারদিন", "অর্দিবেহেশত", "খোরদাদ", "তির", "মোরদাদ", "শাহরিভার",
            "মেহর", "আবান", "আজার", "দে", "বাহমান", "এসফান্দ")
    )

    fun monthNames(kind: Kind, languageKey: String): Array<String> = when (kind) {
        Kind.HIJRI -> HIJRI_MONTHS[languageKey] ?: HIJRI_MONTHS.getValue("en")
        Kind.JALALI -> JALALI_MONTHS[languageKey] ?: JALALI_MONTHS.getValue("en")
        Kind.GREGORIAN -> DateWords.months(Locale(languageKey))
    }
}

/**
 * A date pattern printed in a non-Gregorian calendar. Day, month and year come
 * from [ShopCalendar]; every other letter (hours, minutes, am/pm) is handed to
 * [base], the same Gregorian formatter DateWords would have used, so times
 * read exactly as before. Figures stay 0-9 like every other date in the app.
 */
internal class CalendarDateFormat(
    private val datePattern: String,
    private val base: SimpleDateFormat,
    private val kind: ShopCalendar.Kind,
    private val monthNames: Array<String>
) : SimpleDateFormat(datePattern, Locale.ENGLISH) {

    override fun format(date: Date, toAppendTo: StringBuffer, pos: FieldPosition): StringBuffer {
        val (y, m, d) = ShopCalendar.ymd(kind, date.time)
        val p = datePattern
        var i = 0
        while (i < p.length) {
            val ch = p[i]
            if (ch == '\'') {
                if (i + 1 < p.length && p[i + 1] == '\'') { toAppendTo.append('\''); i += 2; continue }
                val end = p.indexOf('\'', i + 1).let { if (it < 0) p.length else it }
                toAppendTo.append(p, i + 1, end)
                i = end + 1
                continue
            }
            if (!ch.isLetter()) { toAppendTo.append(ch); i++; continue }
            var j = i
            while (j < p.length && p[j] == ch) j++
            val n = j - i
            when (ch) {
                'd' -> toAppendTo.append(d.toString().padStart(n, '0'))
                'M', 'L' -> toAppendTo.append(if (n >= 3) monthNames[m - 1] else m.toString().padStart(n, '0'))
                'y' -> toAppendTo.append(if (n == 2) (y % 100).toString().padStart(2, '0') else y.toString())
                else -> {
                    base.applyPattern(p.substring(i, j))
                    toAppendTo.append(base.format(date))
                }
            }
            i = j
        }
        return toAppendTo
    }
}
