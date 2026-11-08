package com.innovation313.roshankhata.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

/**
 * Where this install came from, asked of Google Play once (Google's free Install Referrer library).
 *
 * The shop's khata page links to the Play listing with `referrer=khata%3D1`. A customer who installs from there and
 * opens the app lands on My Khata (its words say to open the shop's link again), not on a shop setup he never asked
 * for (8 Nov 2026 review, M95). Only that flag travels: the khata key stays out of Play's URL (it would let Google
 * read the page). Nothing is stored but one yes/no on this phone; a sideloaded build answers "no".
 */
object InstallSource {
    private const val PREFS = "install_source"
    private const val CHECKED = "checked"
    private const val KHATA = "khata_page"
    private const val WAIT_MS = 2000L

    /** Pure and tested: does Play's referrer text carry the khata page's flag? */
    fun isKhataReferrer(referrer: String?): Boolean =
        referrer.orEmpty().split('&').any { it.trim() == "khata=1" }

    fun fromKhataPage(context: Context): Boolean =
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KHATA, false) }.getOrDefault(false)

    /** Cleared once the owner starts his own setup, so the flag never pulls him back to My Khata. */
    fun clear(context: Context) {
        runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KHATA, false).apply() }
    }

    /** Asks Play only on a start where the setup is not done and it was never asked; `then` runs once, within two seconds. */
    fun checkOnce(context: Context, then: () -> Unit) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val needed = runCatching { !prefs.getBoolean(CHECKED, false) && SetupProgress.next(context) != SetupProgress.Step.DONE }.getOrDefault(false)
        if (!needed) { then(); return }
        var done = false
        val main = Handler(Looper.getMainLooper())
        fun finish() { if (!done) { done = true; then() } }
        main.postDelayed({ finish() }, WAIT_MS)
        runCatching {
            val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    runCatching {
                        if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                            val flag = isKhataReferrer(client.installReferrer.installReferrer)
                            prefs.edit().putBoolean(CHECKED, true).putBoolean(KHATA, flag).apply()
                        } else if (code == InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED) {
                            prefs.edit().putBoolean(CHECKED, true).apply()
                        }
                        client.endConnection()
                    }
                    main.post { finish() }
                }
                override fun onInstallReferrerServiceDisconnected() { main.post { finish() } }
            })
        }.onFailure { finish() }
    }
}
