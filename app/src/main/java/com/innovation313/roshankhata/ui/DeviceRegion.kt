package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.res.Resources
import android.telephony.TelephonyManager
import com.innovation313.roshankhata.data.ShopCountry
import java.util.Locale

/**
 * Where the phone is, never the app's language (23 Oct 2026, the review; moved here from the
 * shop-name page on 5 Nov 2026 so Home can ask an older book's country with the same rule).
 *
 * The app's locale is the owner's language choice and carries no region ("ur", "ar"), so a UAE
 * shop in Arabic read as Saudi Arabia and an English one anywhere as Pakistan. The SIM's country
 * first (no permission needed), then the network's, then the phone's own system languages, then
 * the app's; the first one that names a country the app knows, else null.
 */
object DeviceRegion {

    fun of(context: Context): String? {
        val known = ShopCountry.entries.map { it.code }.filter { it.isNotBlank() }.toSet()
        val tm = runCatching { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }.getOrNull()
        val system = Resources.getSystem().configuration.locales
        val candidates = buildList {
            tm?.let { t ->
                runCatching { t.simCountryIso }.getOrNull()?.let { add(it) }
                runCatching { t.networkCountryIso }.getOrNull()?.let { add(it) }
            }
            for (i in 0 until system.size()) add(system[i].country)
            add(context.resources.configuration.locales[0].country)
            add(Locale.getDefault().country)
        }
        return candidates.map { it.trim().uppercase(Locale.ROOT) }.firstOrNull { it in known }
    }

    /**
     * The SIM's country, else the network's, when the app knows it; never the phone's languages.
     * For asking an older book's country (Home): a phone's language region is a weak sign there -
     * many phones in Pakistan say "English (United States)" - and a wrong question to every
     * such owner would be worse than none.
     */
    fun ofTelephony(context: Context): String? {
        val known = ShopCountry.entries.map { it.code }.filter { it.isNotBlank() }.toSet()
        val tm = runCatching { context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager }.getOrNull() ?: return null
        return listOfNotNull(
            runCatching { tm.simCountryIso }.getOrNull(),
            runCatching { tm.networkCountryIso }.getOrNull()
        ).map { it.trim().uppercase(Locale.ROOT) }.firstOrNull { it in known }
    }

    /** The flag of [country] as the phone's own emoji (two regional letters); a globe for Other. */
    fun flag(country: ShopCountry): String {
        val code = country.code.uppercase(Locale.ROOT)
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return "🌐"
        val sb = StringBuilder()
        for (c in code) sb.appendCodePoint(0x1F1E6 + (c - 'A'))
        return sb.toString()
    }
}
