package com.innovation313.roshankhata

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.AppLock
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.IncomingPayment
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.PaymentNotice
import com.innovation313.roshankhata.data.PaymentRefUse
import com.innovation313.roshankhata.data.PaymentTicket
import com.innovation313.roshankhata.data.PaymentTickets
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
 *    in a short list of 40 so the owner can tick it later. Nothing of its words. An app seen only with a person's
 *    notices — a chat, an email, a social post, a call — is never listed (7 Nov 2026, the review's P2), and the list
 *    is let go of when the feature is turned off (P6).
 *  - SMS apps (the phone's default and the known ones), phone/dialer apps and call notifications are never read
 *    and never offered ([neverRead]); nor is any chat, email or social notice, even from a ticked app.
 *  - From a ticked app, the title and text are read on the phone, parsed ([PaymentNotice]) and forgotten; only what
 *    the owner saves becomes an entry. Nothing is sent anywhere; no network call is made, and only a voice that is
 *    on the phone speaks (P6).
 *  - The notice carries a one-time ticket, never the payment ([openTicket], P3).
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

    /** The Play Store as an installer: its name now, and the one it had on phones of before 2014. */
    private val PLAY_INSTALLERS = setOf("com.android.vending", "com.google.android.feedback")

    private const val PREFS = "paisa_voice"
    private const val KEY_ON = "on"
    private const val KEY_CHOSEN = "chosen"
    private const val KEY_SEEN = "seen"
    private const val KEY_VOICE = "voice"
    /** The one fixed door key of 5 Nov 2026, let go of: a notice now carries a one-time ticket (P3). */
    private const val KEY_DOOR = "door"
    /** Set once the list kept before chats stopped being listed (7 Nov 2026) was let go of: it may hold a chat app. */
    private const val KEY_SEEN_V2 = "seen_v2"
    private const val KEY_NEXT_ID = "next_notice"
    private const val SEEN_MAX = 40
    private const val SEP = '\u001F'

    /** The notices' one-time tickets: this phone's own file, never the book's, never in a backup. */
    private const val TICKETS = "paisa_tickets"

    /** How far back a payment's reference is looked for in the book when its notice is opened. */
    private const val REF_LOOKBACK_MS = 180L * 24 * 60 * 60_000L

    /** The ticket MainActivity's door asks of a payment notice ([openTicket]). */
    const val ARG_TICKET = "paisa_ticket"

    enum class Voice { URDU, APP, CHIME }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** On only on a phone that writes the book: a helper's read-only phone could not add the payment anyway. */
    fun isOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, false) && !ViewerMode.isOn(ctx)

    /**
     * Turned off (7 Nov 2026, the review's P6), what was noted of other apps goes, and so do the notices' tickets and
     * the notices themselves: a payment notice left behind could not be opened anyway.
     */
    fun setOn(ctx: Context, on: Boolean) {
        if (on) {
            prefs(ctx).edit().putBoolean(KEY_ON, true).apply()
            return
        }
        prefs(ctx).edit().putBoolean(KEY_ON, false).remove(KEY_SEEN).apply()
        synchronized(this) { noted.clear() }
        val gone = runCatching { tickets(ctx).clear() }.getOrDefault(emptyList())
        val manager = NotificationManagerCompat.from(ctx)
        for (t in gone) runCatching { manager.cancel(t.noticeId) }
    }

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

    /**
     * True for a notice never read nor noted (7 Nov 2026, the review's P2 and P8): one still going on — Android 7 to 11
     * send them despite the manifest's filter — or a person's words: a chat, an email, a social post, a call, by its
     * category or its messaging style ([PaymentNotice.passedOver]). A customer could type "JazzCash: You have received
     * Rs 50,000" into a chat; no chat is ever read, even from an app the owner ticked.
     */
    fun passedOver(n: Notification): Boolean {
        val extras = n.extras
        val template = runCatching { extras?.getString(Notification.EXTRA_TEMPLATE) }.getOrNull()
        val keys = runCatching { extras?.keySet()?.toSet() }.getOrNull().orEmpty()
        return PaymentNotice.passedOver(n.flags and Notification.FLAG_ONGOING_EVENT != 0, n.category, template, keys)
    }

    /** The ticked apps; before the owner ever chose, the two wallets as [settleDefaults] would tick them. */
    fun chosen(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_CHOSEN, null)?.toSet() ?: firstTicks(ctx)

    fun isChosen(ctx: Context, pkg: String): Boolean = pkg in chosen(ctx)

    fun setChosen(ctx: Context, pkg: String, on: Boolean) {
        val now = chosen(ctx).toMutableSet()
        if (on) now += pkg else now -= pkg
        prefs(ctx).edit().putStringSet(KEY_CHOSEN, now).apply()
    }

    /**
     * The first time access is given, the two wallets are ticked if this phone has them from the Play Store, and
     * nothing else (7 Nov 2026, the review's P2: an app put on the phone another way can carry a wallet's package
     * name where the wallet is not installed). A wallet not installed, or installed another way, is shown unticked,
     * and the second is marked so ([fromPlay]).
     */
    fun settleDefaults(ctx: Context) {
        if (prefs(ctx).contains(KEY_CHOSEN)) return
        prefs(ctx).edit().putStringSet(KEY_CHOSEN, firstTicks(ctx)).apply()
    }

    private fun firstTicks(ctx: Context): Set<String> =
        KNOWN.map { it.first }.filter { installed(ctx, it) && fromPlay(ctx, it) }.toSet()

    /** Named in the manifest's queries for exactly these two, so Android 11+ answers. */
    fun installed(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** Put on this phone by the Play Store (P2). Only asked of the two wallets, which the manifest's queries name. */
    fun fromPlay(ctx: Context, pkg: String): Boolean =
        runCatching { installerOf(ctx.packageManager, pkg) in PLAY_INSTALLERS }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, pkg: String): String? =
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else pm.getInstallerPackageName(pkg)

    data class SeenApp(val pkg: String, val label: String, val at: Long)

    /** Apps that showed a notification since the feature was turned on, newest first, the two wallets aside. */
    fun seen(ctx: Context): List<SeenApp> {
        settleSeen(ctx)
        return (prefs(ctx).getStringSet(KEY_SEEN, null) ?: emptySet()).mapNotNull { row ->
            val parts = row.split(SEP)
            if (parts.size != 3) null else SeenApp(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
        }.filter { s -> KNOWN.none { it.first == s.pkg } && !neverRead(ctx, s.pkg) }.sortedByDescending { it.at }
    }

    /**
     * Once (7 Nov 2026): the list kept before chats stopped being listed may hold a chat app, so it is let go of and
     * fills again from what is seen now; the fixed door key of 5 Nov goes with it.
     */
    private fun settleSeen(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(KEY_SEEN_V2, false)) return
        p.edit().remove(KEY_SEEN).remove(KEY_DOOR).putBoolean(KEY_SEEN_V2, true).apply()
    }

    /** When each package was last written to the seen list in this process, so a chatty app writes once an hour. */
    private val noted = HashMap<String, Long>()

    /** Keeps [pkg] and its [label] (from the notification's "android.appInfo" only) in the 40 newest seen. */
    @Synchronized
    fun noteSeen(ctx: Context, pkg: String, label: String?) {
        val now = System.currentTimeMillis()
        if ((noted[pkg] ?: 0L) > now - 60 * 60_000L) return
        noted[pkg] = now
        settleSeen(ctx)
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

    private var ticketBook: PaymentTickets? = null

    /** The notices' one-time tickets, in [TICKETS]. One instance, so its own lock covers every caller. */
    @Synchronized
    private fun tickets(ctx: Context): PaymentTickets = ticketBook ?: run {
        val file = ctx.applicationContext.getSharedPreferences(TICKETS, Context.MODE_PRIVATE)
        PaymentTickets(object : PaymentTickets.Store {
            override fun all(): Map<String, String> =
                file.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

            override fun save(put: Map<String, String>, drop: Collection<String>) {
                val edit = file.edit()
                for (k in drop) edit.remove(k)
                for ((k, v) in put) edit.putString(k, v)
                edit.commit()
            }
        }).also { ticketBook = it }
    }

    private val random = SecureRandom()

    /** A ticket's key: 16 random bytes, as hex. */
    private fun newKey(): String =
        ByteArray(16).also { random.nextBytes(it) }.joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    /**
     * [args] without a payment notice's ticket, for a door opened from the recent-apps list (7 Nov 2026, the review's
     * P3): Android hands a task its first intent again there, extras and all. Null when the bundle cannot be read.
     */
    fun withoutTicket(args: Bundle): Bundle? = runCatching { Bundle(args).apply { remove(ARG_TICKET) } }.getOrNull()

    /**
     * A payment notice's tap, at MainActivity's door (7 Nov 2026, the review's P3 and P7). The notice carries only a
     * ticket ([ARG_TICKET]); its payment is kept inside the app and given out once, within a day, while the feature is
     * on, for the shop it was matched in — and the notice is taken away as its form opens. Then the book is asked
     * ([PaymentNotice.opening]): a reference already in it is said instead of a second form; the matched customer
     * opens only while he is still in the book as he was, else the Khata list to choose from.
     */
    fun openTicket(activity: AppCompatActivity, args: Bundle?) {
        val key = runCatching { args?.getString(ARG_TICKET) }.getOrNull() ?: return
        val ticket = redeem(activity, key)
        if (ticket == null) {
            Toast.makeText(activity, R.string.paisa_notice_gone, Toast.LENGTH_LONG).show()
            return
        }
        val app = activity.applicationContext
        activity.lifecycleScope.launch {
            // The book could not be read: the owner chooses, as for a notice that matched nobody.
            val opening = runCatching { withContext(Dispatchers.IO) { openingOf(app, ticket) } }.getOrNull()
                ?: PaymentNotice.Opening.Choose(ticket.sender)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            when (opening) {
                is PaymentNotice.Opening.Already -> MaterialAlertDialogBuilder(activity)
                    .setMessage(
                        activity.getString(
                            R.string.paisa_already_added,
                            ticket.reference.orEmpty(),
                            opening.partyName?.let { Format.isolate(it) } ?: activity.getString(R.string.nav_cashbook),
                            Format.dateOnly(opening.at)
                        )
                    )
                    .setPositiveButton(R.string.ok, null)
                    .show()
                is PaymentNotice.Opening.Customer -> activity.startActivity(customerForm(activity, ticket, opening.partyId))
                is PaymentNotice.Opening.Choose -> activity.startActivity(chooser(activity, ticket, opening.search))
            }
        }
    }

    /** The ticket behind [key], once; refused while the feature is off, past its day, or for another shop than the open one. */
    private fun redeem(ctx: Context, key: String): PaymentTicket? {
        if (!isOn(ctx)) return null
        val ticket = runCatching { tickets(ctx).redeem(key, System.currentTimeMillis()) }.getOrNull() ?: return null
        // Its form is opening, or it is refused: either way the notice is done with (its Add action does not take it away).
        runCatching { NotificationManagerCompat.from(ctx).cancel(ticket.noticeId) }
        return ticket.takeIf { it.business == Businesses.active(ctx).id }
    }

    /** The book's answer for [ticket]: its reference looked for in the last [REF_LOOKBACK_MS], its customer read again. */
    private suspend fun openingOf(ctx: Context, ticket: PaymentTicket): PaymentNotice.Opening {
        val dao = KhataDatabase.get(ctx).khataDao()
        val since = System.currentTimeMillis() - REF_LOOKBACK_MS
        val earlier = ticket.reference?.trim()?.takeIf { it.isNotEmpty() }?.let { ref ->
            dao.entryWithPaymentRef(ref, since) ?: dao.cashWithPaymentRef(ref, since)?.let { PaymentRefUse(it, null) }
        }
        val party = ticket.partyId?.let { dao.getParty(it) }?.let { PaymentNotice.PartyNow(it.uid, it.name, it.isDeleted) }
        return PaymentNotice.opening(ticket, party, earlier)
    }

    /** The matched customer's "I got" form, filled in from [t]. */
    private fun customerForm(ctx: Context, t: PaymentTicket, partyId: Long): Intent =
        Intent(ctx, PartyDetailActivity::class.java)
            .putExtra(PartyDetailActivity.EXTRA_PARTY_ID, partyId)
            .putExtra(PartyDetailActivity.EXTRA_VOICE_AMOUNT, t.amount)
            .putExtra(PartyDetailActivity.EXTRA_VOICE_IS_GIVEN, false)
            .putExtra(PartyDetailActivity.EXTRA_PREFILL_NOTE, t.note)
            .putExtra(PartyDetailActivity.EXTRA_PREFILL_METHOD, t.method)
            .apply { t.reference?.let { putExtra(PartyDetailActivity.EXTRA_PREFILL_REF, it) } }

    /** The Khata list with [t]'s payment carried to the customer the owner taps, its search set to [search]. */
    private fun chooser(ctx: Context, t: PaymentTicket, search: String?): Intent =
        Intent(ctx, KhataActivity::class.java)
            .putExtra(KhataActivity.EXTRA_CARRY_AMOUNT, t.amount)
            .putExtra(KhataActivity.EXTRA_CARRY_GOT, true)
            .putExtra(KhataActivity.EXTRA_CARRY_NOTE, t.note)
            .putExtra(KhataActivity.EXTRA_CARRY_METHOD, t.method)
            .apply {
                t.reference?.let { putExtra(KhataActivity.EXTRA_CARRY_REF, it) }
                search?.let { putExtra(KhataActivity.EXTRA_CARRY_SEARCH, it) }
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

    /** How often payments are heard (P5): three a minute, then one chime, then quiet. In memory only. */
    private val PACE = PaymentNotice.Pace()

    /**
     * Says [payment] — or chimes, or keeps quiet ([PACE]) — then finds its customer in the open shop's book, keeps the
     * notice's ticket and posts the notice with Add to khata. Runs on the main thread; the book is read on IO.
     */
    fun handle(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, speaker: PaymentSpeaker, pkg: String, appName: String, payment: IncomingPayment) {
        val app = com.innovation313.roshankhata.ui.AppLanguage.context(ctx.applicationContext)
        Currency.refresh(app)
        when (PACE.next(System.currentTimeMillis(), aloud = !namesStayQuiet(app))) {
            PaymentNotice.Pace.Say.VOICE -> speaker.announce(voice(app)) { words -> spoken(words, payment, appName) }
            PaymentNotice.Pace.Say.CHIME -> speaker.chime()
            PaymentNotice.Pace.Say.QUIET -> Unit
        }
        scope.launch {
            val match = runCatching {
                withContext(Dispatchers.IO) {
                    val parties = KhataDatabase.get(app).khataDao().partiesWithBalanceOnce()
                    PaymentNotice.match(parties, payment, { it.name }, { it.phone })
                }
            }.getOrNull()
            // The ticket is kept before the notice shows, so its tap always finds it.
            val (key, ticket) = runCatching { withContext(Dispatchers.IO) { issue(app, pkg, appName, payment, match) } }
                .getOrNull() ?: return@launch
            post(app, appName, payment, match?.one?.name, key, ticket)
        }
    }

    /**
     * The phone is locked, or opening the app would ask for App Lock (7 Nov 2026, the review's P6): then only the
     * chime plays. The notice keeps a name and a figure off the lock screen; the voice keeps them out of the room.
     */
    private fun namesStayQuiet(ctx: Context): Boolean {
        if (runCatching { AppLock.required(ctx) }.getOrDefault(true)) return true
        val keyguard = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return runCatching { keyguard?.isDeviceLocked }.getOrNull() ?: true
    }

    /** The notice's ticket, kept under a new key (P3), its customer named by id, uid and name (P7). */
    private suspend fun issue(
        ctx: Context, pkg: String, appName: String, payment: IncomingPayment, match: PaymentNotice.Match<PartyWithBalance>?
    ): Pair<String, PaymentTicket> {
        val one = match?.one
        val party = one?.let { KhataDatabase.get(ctx).khataDao().getParty(it.id) }
        val ref = payment.reference
        val ticket = PaymentTicket(
            noticeId = nextNoticeId(ctx),
            issuedAt = System.currentTimeMillis(),
            business = Businesses.active(ctx).id,
            amount = payment.amount,
            note = if (ref != null) ctx.getString(R.string.paisa_entry_note_ref, appName, ref)
                else ctx.getString(R.string.paisa_entry_note, appName),
            method = methodOf(pkg),
            reference = ref,
            sender = payment.sender,
            narrow = (match?.candidates?.size ?: 0) > 1,
            partyId = one?.id,
            partyUid = party?.uid,
            partyName = party?.name ?: one?.name
        )
        val key = newKey()
        tickets(ctx).issue(key, ticket)
        return key to ticket
    }

    private const val CHANNEL_ID = "payments"
    /** Clear of ReminderWorker's (1001-4999) and AutomationWorker's (6001+) ids. */
    private const val ID_BASE = 7001
    private const val ID_COUNT = 100

    /**
     * The next notice's id, kept across restarts (7 Nov 2026, the review's P3): counted in memory, it began again at
     * the first after every restart and took the place of a notice still showing.
     */
    @Synchronized
    private fun nextNoticeId(ctx: Context): Int {
        val p = prefs(ctx)
        val n = (p.getInt(KEY_NEXT_ID, -1) + 1).mod(ID_COUNT)
        p.edit().putInt(KEY_NEXT_ID, n).commit()
        return ID_BASE + n
    }

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
     * The notice "Ahmed Ali se Rs 5,000 aaye (JazzCash)" with Add to khata. Its tap carries only the ticket's [key]
     * ([openTicket] decides there what opens); the customer the book matched ([matched]) is named only while App Lock
     * is off (P6) — with it on, the book's names stay behind the lock.
     */
    private fun post(ctx: Context, appName: String, payment: IncomingPayment, matched: String?, key: String, ticket: PaymentTicket) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannel(ctx)
        val money = Format.money(payment.amount)
        val title = payment.sender?.let { ctx.getString(R.string.paisa_notif_title, Format.isolate(it), money, appName) }
            ?: ctx.getString(R.string.paisa_notif_title_noname, money, appName)
        val body = when {
            matched == null -> ctx.getString(R.string.paisa_notif_pick)
            runCatching { AppLock.required(ctx) }.getOrDefault(true) -> ctx.getString(R.string.paisa_notif_match_locked)
            else -> ctx.getString(R.string.paisa_notif_match, Format.isolate(matched))
        }
        val target = if (ticket.partyId != null) PartyDetailActivity::class.java else KhataActivity::class.java
        // Through the gate, as every notice goes: App Lock first, Home under it, the form on top.
        val intent = Intent(ctx, GateActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, target.name)
            .putExtra(MainActivity.EXTRA_OPEN_ARGS, Bundle().apply { putString(ARG_TICKET, key) })
            .putExtra(MainActivity.EXTRA_OPEN_BUSINESS, ticket.business)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val id = ticket.noticeId
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
            // A day's payment notice is done with by the next day, as its ticket is (PaymentTickets.TTL_MS).
            .setTimeoutAfter(PaymentTickets.TTL_MS)
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
 * app's language, else a short chime (the phone's notification sound). Only a voice that is on the phone speaks
 * (7 Nov 2026, the review's P6): one that needs the network would send the name and the amount to the engine's
 * server. Played as a notification (USAGE_NOTIFICATION), so a phone on silent or Do Not Disturb stays quiet. Let
 * go a minute after the last word.
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

    /** The voice of [locale] this engine has on the phone, never one needing the network ([PaymentNotice.offlineVoice]). */
    private fun offlineVoice(t: TextToSpeech, locale: Locale): android.speech.tts.Voice? {
        val voices = runCatching { t.voices }.getOrNull() ?: return null
        return runCatching {
            PaymentNotice.offlineVoice(voices, locale.language, locale.country) { v ->
                PaymentNotice.VoiceFacts(
                    language = v.locale.language,
                    country = v.locale.country,
                    needsNetwork = v.isNetworkConnectionRequired,
                    notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in v.features.orEmpty(),
                    quality = v.quality
                )
            }
        }.getOrNull()
    }

    /** The Urdu voice this phone has, or null. */
    private fun urdu(t: TextToSpeech): android.speech.tts.Voice? = offlineVoice(t, Locale("ur", "PK"))

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
            val speakIn: Locale = when (voice) {
                PaymentVoice.Voice.URDU -> Locale("ur", "PK")
                else -> if (appLocale.language == "ur" && appLocale.script == "Latn") Locale.ENGLISH else appLocale
            }
            val chosen = offlineVoice(t, speakIn)
            val usable = chosen != null && runCatching { t.setVoice(chosen) == TextToSpeech.SUCCESS }.getOrDefault(false)
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
