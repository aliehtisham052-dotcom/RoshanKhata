package com.innovation313.roshankhata

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.innovation313.roshankhata.data.PaymentNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The phone's notifications, as Android hands them to an app the owner gave notification access
 * ([PaymentVoice], 5 Nov 2026). Bound by Android only after the owner turns access on in the phone's settings.
 *
 * Reads nothing while the feature is off or on a helper's read-only phone. A notice still going on, or a person's
 * words — a chat, an email, a social post, a call — is passed over whole, from any app (7 Nov 2026, the review's P2
 * and P8). For an app the owner has not ticked, only its package name and label are noted (to offer it later); SMS
 * and phone apps not even that. For a ticked app, the title and text are parsed on the phone, off the main thread,
 * and when they are clearly a payment that came in, said and offered as a notice — nothing is written to the book
 * and nothing leaves the phone.
 */
class PaymentListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speaker: PaymentSpeaker? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Access was just given, or the phone restarted: the wallets' first ticks, by the Play Store rule (P2).
        runCatching { PaymentVoice.settleDefaults(applicationContext) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val ctx = applicationContext
        if (!PaymentVoice.isOn(ctx)) return
        val pkg = n.packageName ?: return
        if (PaymentVoice.neverRead(ctx, pkg)) return
        val notification = n.notification ?: return
        // Music, a download, a call in progress, a chat, an email: never a payment, and not a reason to list the app.
        if (PaymentVoice.passedOver(notification)) return
        if (!PaymentVoice.isChosen(ctx, pkg)) {
            // Not ticked: its name only, so the owner can tick it later. Not a word of what it says.
            PaymentVoice.noteSeen(ctx, pkg, PaymentVoice.labelOf(ctx, n))
            return
        }
        // A group's summary repeats its children.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras ?: return
        val title = runCatching { extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() }.getOrNull()
        val text = runCatching { extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() }.getOrNull()
        val big = runCatching { extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() }.getOrNull()
        val label = PaymentVoice.labelOf(ctx, n)
        scope.launch {
            // Off the main thread (P5): the text is cut and tidied before any pattern sees it, but reading is still work,
            // and an app that floods long notices must not hold up the phone's screen.
            val payment = withContext(Dispatchers.Default) {
                runCatching { PaymentNotice.parse(title, text, big) }.getOrNull()
            } ?: return@launch
            val words = listOfNotNull(title, text, big).joinToString("\n")
            if (REPEATS.isRepeat(pkg, payment.amount, payment.reference, words, System.currentTimeMillis())) return@launch
            // Turned off while it was being read.
            if (!PaymentVoice.isOn(ctx)) return@launch
            val voice = speaker ?: PaymentSpeaker(ctx).also { speaker = it }
            PaymentVoice.handle(ctx, scope, voice, pkg, PaymentVoice.nameOf(ctx, pkg, label), payment)
        }
    }

    override fun onListenerDisconnected() {
        speaker?.shutdown()
        speaker = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        speaker?.shutdown()
        speaker = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** The same payment posted or updated twice within ten minutes is said once. In memory only. */
        val REPEATS = PaymentNotice.Repeats()
    }
}
