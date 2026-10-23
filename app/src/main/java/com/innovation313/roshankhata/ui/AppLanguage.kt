package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * Words said from outside a screen — the daily notifications, their
 * channel's name, the read-only toast — in the APP'S language, not the
 * phone's.
 *
 * On Android 13+ the system applies the owner's choice to every context
 * the app has. On Android 12 and lower AppCompat's per-app language
 * reaches only its activities (Google's per-app language guide: "the
 * backward compatible APIs work with the AppCompatActivity context, not
 * the application context, for Android 12 (API level 32) and earlier").
 * So a notification built from the application context spoke English to
 * an owner who had picked Urdu (23 Oct review). Every screen notes the
 * chosen tags here ([remember], from BaseActivity), and [context] builds a
 * context in that language when a worker runs in a process where no
 * screen has opened yet.
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY_TAGS = "tags"

    /** Note the app's language as AppCompat holds it now. Call after super.onCreate. */
    fun remember(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) return
        runCatching {
            val chosen = AppCompatDelegate.getApplicationLocales()
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val tags = if (chosen.isEmpty) null else chosen.toLanguageTags()
            if (prefs.getString(KEY_TAGS, null) != tags) {
                val edit = prefs.edit()
                if (tags == null) edit.remove(KEY_TAGS) else edit.putString(KEY_TAGS, tags)
                edit.apply()
            }
        }
    }

    /**
     * [base] in the app's language. Unchanged on Android 13+ (the system
     * already did it) and when no language was ever chosen.
     */
    fun context(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        return runCatching {
            val chosen = AppCompatDelegate.getApplicationLocales()
            val tags = if (!chosen.isEmpty) chosen.toLanguageTags()
            else base.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAGS, null)
            if (tags.isNullOrEmpty()) return base
            val config = Configuration(base.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(tags))
            base.createConfigurationContext(config)
        }.getOrDefault(base)
    }
}
