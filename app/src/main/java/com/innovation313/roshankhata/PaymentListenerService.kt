package com.innovation313.roshankhata

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.innovation313.roshankhata.data.PaymentNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The phone's notifications, as Android hands them to an app the owner gave notification access
 * ([PaymentVoice], 5 Nov 2026). Bound by Android only after the owner turns access on in the phone's settings.
 *
 * Reads nothing while the feature is off or on a helper's read-only phone. For an app the owner has not ticked,
 * only its package name and label are noted (to offer it later); SMS and phone apps not even that. For a ticked
 * app, the title and text are parsed on the phone and, when they are clearly a payment that came in, said and
 * offered as a notice — nothing is written to the book and nothing leaves the phone.
 */
class PaymentListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speaker: PaymentSpeaker? = null

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val ctx = applicationContext
        if (!PaymentVoice.isOn(ctx)) return
        val pkg = n.packageName ?: return
        if (PaymentVoice.neverRead(ctx, pkg)) return
        if (!PaymentVoice.isChosen(ctx, pkg)) {
            // Not ticked: its name only, so the owner can tick it later. Not a word of what it says.
            PaymentVoice.noteSeen(ctx, pkg, PaymentVoice.labelOf(ctx, n))
            return
        }
        val notification = n.notification ?: return
        // A group's summary repeats its children; a call's notice is Call Log data.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.category == Notification.CATEGORY_CALL || notification.category == "missed_call") return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val payment = PaymentNotice.parse(title, text, big) ?: return
        val words = listOfNotNull(title, text, big).joinToString("\n")
        if (REPEATS.isRepeat(pkg, payment.amount, payment.reference, words, System.currentTimeMillis())) return
        val name = PaymentVoice.nameOf(ctx, pkg, PaymentVoice.labelOf(ctx, n))
        val voice = speaker ?: PaymentSpeaker(ctx).also { speaker = it }
        PaymentVoice.handle(ctx, scope, voice, pkg, name, payment)
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
