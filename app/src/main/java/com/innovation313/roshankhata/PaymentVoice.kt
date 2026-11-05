package com.innovation313.roshankhata

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.IncomingPayment
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.PaymentNotice
import com.innovation313.roshankhata.data.ViewerMode
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.refresh
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Locale

/**
 * "Paisa aaya ki awaaz" (5 Nov 2026): when money arrives in a wallet or bank app the owner ticked, this phone says
 * it ("Ahmed se 5000 rupay aa gaye") and offers, in one tap, to put it on the customer's khata. It never writes an
 * entry itself: the tap opens the usual "I got" form filled in, and the owner presses Save.
 *
 * What is read, and what never is (Google Play's SMS and Call Log policy, the owner's privacy rule):
 *  - The feature is off until the owner turns it on, per phone; a helper's read-only phone never runs it.
 *  - From an app NOT ticked, only its package name and its label (the notification's "android.appInfo") are kept,
 *    in a short list of 40 so the owner can tick it later. Nothing of its words.
 *  - SMS apps (the phone's default and the known ones), phone/dialer apps and call notifications are never read
 *    and never offered ([neverRead]).
 *  - From a ticked app, the title and text are read on the phone, parsed ([PaymentNotice]) and forgotten; only what
 *    the owner saves becomes an entry. Nothing is sent anywhere; no network call is made.
 *
 * Settings are this phone's (prefs "paisa_voice"), never the book's: what a phone listens to is its own.
 */
object PaymentVoice {

    const val JAZZCASH = "com.techlogix.mobilinkcustomer"
    const val EASYPAISA = "pk.com.telenor.phoenix"

    /** The two wallets offered first, with their own names (brands, never translated). */
    val KNOWN: List<Pair<String, String>> = listOf(JAZZCASH to "JazzCash", EASYPAISA to "Easypaisa")

    /** SMS apps, never read and never offered (Play: SMS data only for the default SMS handler). */
    private val SMS_APPS = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging"
    )

    /** Phone apps: a missed call's notification is Call Log data (Play's Call Log rule). */
    private val DIALER_APPS = setOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer")

    private const val PREFS = "paisa_voice"
    private const val KEY_ON = "on"
    private const val KEY_CHOSEN = "chosen"
    private const val KEY_SEEN = "seen"
    private const val KEY_VOICE = "voice"
    private const val KEY_DOOR = "door"
    private const val SEEN_MAX = 40
    private const val SEP = '\u001F'

    /** The key MainActivity's door asks of a payment's words ([doorOpen]). */
    const val ARG_KEY = "paisa_key"

    enum class Voice { URDU, APP, CHIME }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** On only on a phone that writes the book: a helper's read-only phone could not add the payment anyway. */
    fun isOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, false) && !ViewerMode.isOn(ctx)
    fun setOn(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_ON, on).apply() }

    fun voice(ctx: Context): Voice =
        runCatching { Voice.valueOf(prefs(ctx).getString(KEY_VOICE, null) ?: Voice.URDU.name) }.getOrDefault(Voice.URDU)
    fun setVoice(ctx: Context, v: Voice) { prefs(ctx).edit().putString(KEY_VOICE, v.name).apply() }

    // ------------------------------------------------------------------------------- which apps

    /** True for a package this app never reads a word of, nor lists: SMS, phone, and itself. */
    fun neverRead(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg in SMS_APPS || pkg in DIALER_APPS) return true
        val sms = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()
        if (sms != null && sms == pkg) return true
        val dialer = runCatching {
            (ctx.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage
        }.getOrNull()
        return dialer != null && dialer == pkg
    }

    /** The ticked apps; before the owner ever chose, JazzCash and Easypaisa. */
    fun chosen(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_CHOSEN, null)?.toSet() ?: KNOWN.map { it.first }.toSet()

    fun isChosen(ctx: Context, pkg: String): Boolean = pkg in chosen(ctx)

    fun setChosen(ctx: Context, pkg: String, on: Boolean) {
        val now = chosen(ctx).toMutableSet()
        if (on) now += pkg else now -= pkg
        prefs(ctx).edit().putStringSet(KEY_CHOSEN, now).apply()
    }

    /**
     * The first time access is given, the two wallets are ticked if this phone has them, and nothing else
     * (a wallet not installed is shown, unticked, so the list says what it would listen to).
     */
    fun settleDefaults(ctx: Context) {
        if (prefs(ctx).contains(KEY_CHOSEN)) return
        prefs(ctx).edit().putStringSet(KEY_CHOSEN, KNOWN.map { it.first }.filter { installed(ctx, it) }.toSet()).apply()
    }

    /** Named in the manifest's queries for exactly these two, so Android 11+ answers. */
    fun installed(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    data class SeenApp(val pkg: String, val label: String, val at: Long)

    /** Apps that showed a notification since the feature was turned on, newest first, the two wallets aside. */
    fun seen(ctx: Context): List<SeenApp> =
        (prefs(ctx).getStringSet(KEY_SEEN, null) ?: emptySet()).mapNotNull { row ->
            val parts = row.split(SEP)
            if (parts.size != 3) null else SeenApp(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
        }.filter { s -> KNOWN.none { it.first == s.pkg } && !neverRead(ctx, s.pkg) }.sortedByDescending { it.at }

    /** When each package was last written to the seen list in this process, so a chatty app writes once an hour. */
    private val noted = HashMap<String, Long>()

    /** Keeps [pkg] and its [label] (from the notification's "android.appInfo" only) in the 40 newest seen. */
    @Synchronized
    fun noteSeen(ctx: Context, pkg: String, label: String?) {
        val now = System.currentTimeMillis()
        if ((noted[pkg] ?: 0L) > now - 60 * 60_000L) return
        noted[pkg] = now
        val rows = (prefs(ctx).getStringSet(KEY_SEEN, null) ?: emptySet())
            .filterNot { it.substringBefore(SEP) == pkg }.toMutableList()
        val name = (label ?: pkg).replace(SEP, ' ').take(60)
        rows += "$pkg$SEP$name$SEP$now"
        val kept = rows.sortedByDescending { it.substringAfterLast(SEP).toLongOrNull() ?: 0L }.take(SEEN_MAX)
        prefs(ctx).edit().putStringSet(KEY_SEEN, kept.toSet()).apply()
    }

    /** The posting app's name, from the notification's own "android.appInfo" (no package query needed). */
    fun labelOf(ctx: Context, sbn: StatusBarNotification): String? = runCatching {
        val info = BundleCompat.getParcelable(sbn.notification.extras, "android.appInfo", ApplicationInfo::class.java)
            ?: return@runCatching null
        ctx.packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** A ticked app's name for the voice and the notice: the wallets' own, else as it was seen, else the package. */
    fun nameOf(ctx: Context, pkg: String, fromNotice: String?): String =
        KNOWN.firstOrNull { it.first == pkg }?.second ?: fromNotice
            ?: seen(ctx).firstOrNull { it.pkg == pkg }?.label ?: pkg

    // ------------------------------------------------------------------------------- access

    /** Whether the owner gave this app notification access (Android's own list of listeners). */
    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Android 11+: straight to this app's own switch; before, the list of apps. */
    fun accessIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(ctx, PaymentListenerService::class.java).flattenToString()
            )
        else Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    // ------------------------------------------------------------------------------- the door

    /** This phone's random key, made once; only the app's own payment notice carries it. */
    fun doorKey(ctx: Context): String {
        prefs(ctx).getString(KEY_DOOR, null)?.let { return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = bytes.joinToString("") { "%02x".format(it) }
        prefs(ctx).edit().putString(KEY_DOOR, key).commit()
        return key
    }

    fun doorOpen(ctx: Context, given: String?): Boolean {
        val mine = prefs(ctx).getString(KEY_DOOR, null) ?: return false
        return given != null && java.security.MessageDigest.isEqual(given.toByteArray(), mine.toByteArray())
    }

    // ------------------------------------------------------------------------------- a payment

    /** The way of paying the form starts on: the two wallets are a wallet; any other app, online. */
    fun methodOf(pkg: String): String = if (KNOWN.any { it.first == pkg }) PaymentMethod.WALLET else PaymentMethod.ONLINE

    /** What is said: "Ahmed se 5000 rupay aa gaye", in [words]'s language; the amount in plain digits. */
    fun spoken(words: Context, payment: IncomingPayment, appName: String): String {
        val digits = Format.plain(payment.amount)
        // The book's own sign: "rupay" for Rs, else the sign as written (AED, $…).
        val amount = if (Currency.symbol == Currency.DEFAULT) words.getString(R.string.paisa_rupees, digits) else "$digits ${Currency.symbol}"
        val who = payment.sender
        return if (who != null) words.getString(R.string.paisa_said, who, amount)
        else words.getString(R.string.paisa_said_noname, amount, appName)
    }

    /**
     * Says [payment], then finds its customer in the open shop's book and posts the notice with Add to khata.
     * Runs on the main thread; the book is read on IO.
     */
    fun handle(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, speaker: PaymentSpeaker, pkg: String, appName: String, payment: IncomingPayment) {
        val app = com.innovation313.roshankhata.ui.AppLanguage.context(ctx.applicationContext)
        Currency.refresh(app)
        speaker.announce(voice(app)) { words -> spoken(words, payment, appName) }
        scope.launch {
            val match = runCatching {
                withContext(Dispatchers.IO) {
                    val parties = KhataDatabase.get(app).khataDao().partiesWithBalanceOnce()
                    PaymentNotice.match(parties, payment, { it.name }, { it.phone })
                }
            }.getOrNull()
            post(app, pkg, appName, payment, match)
        }
    }

    private const val CHANNEL_ID = "payments"
    /** Clear of ReminderWorker's (1001-4999) and AutomationWorker's (6001+) ids. */
    private const val ID_BASE = 7001
    private var nextId = 0

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.paisa_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = ctx.getString(R.string.paisa_channel_desc)
                    // The voice (or the chime) was the sound; the notice itself only shows.
                    setSound(null, null)
                }
            )
        }
    }

    /**
     * The notice "Ahmed Ali se Rs 5,000 aaye (JazzCash)" with Add to khata: one customer clearly matched opens his
     * "I got" form filled in; none or several open Khata with the amount carried, narrowed to the name when several.
     */
    fun post(ctx: Context, pkg: String, appName: String, payment: IncomingPayment, match: PaymentNotice.Match<PartyWithBalance>?) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannel(ctx)
        val money = Format.money(payment.amount)
        val title = payment.sender?.let { ctx.getString(R.string.paisa_notif_title, Format.isolate(it), money, appName) }
            ?: ctx.getString(R.string.paisa_notif_title_noname, money, appName)
        val one = match?.one
        val body = if (one != null) ctx.getString(R.string.paisa_notif_match, Format.isolate(one.name))
            else ctx.getString(R.string.paisa_notif_pick)
        val ref = payment.reference
        val note = if (ref != null) ctx.getString(R.string.paisa_entry_note_ref, appName, ref)
            else ctx.getString(R.string.paisa_entry_note, appName)
        val method = methodOf(pkg)
        val args = Bundle().apply {
            putString(ARG_KEY, doorKey(ctx))
            if (one != null) {
                putLong(PartyDetailActivity.EXTRA_PARTY_ID, one.id)
                putDouble(PartyDetailActivity.EXTRA_VOICE_AMOUNT, payment.amount)
                putBoolean(PartyDetailActivity.EXTRA_VOICE_IS_GIVEN, false)
                putString(PartyDetailActivity.EXTRA_PREFILL_NOTE, note)
                putString(PartyDetailActivity.EXTRA_PREFILL_METHOD, method)
                ref?.let { putString(PartyDetailActivity.EXTRA_PREFILL_REF, it) }
            } else {
                putDouble(KhataActivity.EXTRA_CARRY_AMOUNT, payment.amount)
                putBoolean(KhataActivity.EXTRA_CARRY_GOT, true)
                putString(KhataActivity.EXTRA_CARRY_NOTE, note)
                putString(KhataActivity.EXTRA_CARRY_METHOD, method)
                ref?.let { putString(KhataActivity.EXTRA_CARRY_REF, it) }
                if ((match?.candidates?.size ?: 0) > 1) payment.sender?.let { putString(KhataActivity.EXTRA_CARRY_SEARCH, it) }
            }
        }
        val target = if (one != null) PartyDetailActivity::class.java else KhataActivity::class.java
        // Through the gate, as every notice goes: App Lock first, Home under it, the form on top.
        val intent = Intent(ctx, GateActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, target.name)
            .putExtra(MainActivity.EXTRA_OPEN_ARGS, args)
            .putExtra(MainActivity.EXTRA_OPEN_BUSINESS, Businesses.active(ctx).id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val id = ID_BASE + (synchronized(this) { nextId = (nextId + 1) % 100; nextId })
        val pending = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(ctx, R.color.brand_green))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pending)
            .addAction(0, ctx.getString(R.string.paisa_add), pending)
            .setAutoCancel(true)
            .setSilent(true)
            // A name and a figure stay off the lock screen: its public face says only that money came.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_notify)
                    .setColor(ContextCompat.getColor(ctx, R.color.brand_green))
                    .setContentTitle(ctx.getString(R.string.paisa_notif_public)).build()
            )
            // A day's payment notice is done with by the next day.
            .setTimeoutAfter(24 * 60 * 60_000L)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications refused between the check and the post.
        }
    }
}

/**
 * The phone's own speech (TextToSpeech), for [PaymentVoice]: Urdu when this phone has an Urdu voice, else the
 * app's language, else a short chime (the phone's notification sound). Played as a notification
 * (USAGE_NOTIFICATION), so a phone on silent or Do Not Disturb stays quiet. Let go a minute after the last word.
 */
class PaymentSpeaker(context: Context) {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val waiting = ArrayList<(TextToSpeech?) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val release = Runnable { shutdown() }

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Runs [then] with the engine once it is up, or with null when the phone has none. Main thread. */
    private fun withEngine(then: (TextToSpeech?) -> Unit) {
        main.removeCallbacks(release)
        main.postDelayed(release, 60_000L)
        if (failed) { then(null); return }
        if (ready) { then(tts); return }
        waiting += then
        if (tts != null) return
        tts = runCatching {
            TextToSpeech(app) { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    failed = !ready
                    if (ready) runCatching { tts?.setAudioAttributes(attributes) }
                    val all = ArrayList(waiting); waiting.clear()
                    all.forEach { it(if (ready) tts else null) }
                }
            }
        }.getOrNull()
        if (tts == null) { failed = true; val all = ArrayList(waiting); waiting.clear(); all.forEach { it(null) } }
    }

    /** The Urdu locale this engine can speak, or null. */
    private fun urdu(t: TextToSpeech): Locale? = listOf(Locale("ur", "PK"), Locale("ur")).firstOrNull {
        runCatching { t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
    }

    /** Answers on the main thread whether this phone has an Urdu voice (null: no speech engine at all). */
    fun checkUrdu(answer: (Boolean?) -> Unit) = withEngine { t -> answer(t?.let { urdu(it) != null }) }

    /**
     * Says what [textIn] writes in the voice's language, or chimes. [textIn] gets a context in that language so
     * the words come from its strings.xml.
     */
    fun announce(voice: PaymentVoice.Voice, textIn: (Context) -> String) {
        if (voice == PaymentVoice.Voice.CHIME) { chime(); return }
        withEngine { t ->
            if (t == null) { chime(); return@withEngine }
            val appLocale = com.innovation313.roshankhata.ui.AppLanguage.context(app).resources.configuration.locales[0]
            // Roman Urdu is Latin letters: an Urdu voice cannot read them, an English one reads them as written.
            val speakIn: Locale? = when (voice) {
                PaymentVoice.Voice.URDU -> urdu(t)
                else -> if (appLocale.language == "ur" && appLocale.script == "Latn") Locale.ENGLISH else appLocale
            }
            val usable = speakIn != null && runCatching { t.setLanguage(speakIn) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
            if (!usable) { chime(); return@withEngine }
            // The words' language: Urdu script for the Urdu voice; otherwise the app's own (Roman Urdu included).
            val words = if (voice == PaymentVoice.Voice.URDU) localized(Locale("ur")) else com.innovation313.roshankhata.ui.AppLanguage.context(app)
            val text = runCatching { textIn(words) }.getOrNull() ?: return@withEngine
            t.speak(text, TextToSpeech.QUEUE_ADD, null, "paisa-" + System.nanoTime())
        }
    }

    private fun localized(locale: Locale): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(locale)
        return app.createConfigurationContext(config)
    }

    /** The phone's own notification sound, once. */
    fun chime() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            RingtoneManager.getRingtone(app, uri)?.apply { audioAttributes = attributes }?.play()
        }
    }

    fun shutdown() {
        main.removeCallbacks(release)
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null; ready = false; failed = false
        // Words still waiting for an engine that is going are dropped, not chimed.
        waiting.clear()
    }
}
