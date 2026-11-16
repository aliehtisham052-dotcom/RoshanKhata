package com.innovation313.roshankhata.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase's door into the app ([PushWake]): a new token is kept for the
 * relay; "rk-wake" runs a few relay rounds; "rk-parcel" carries a small sealed
 * parcel itself, written in without asking the relay (23 Oct 2026). When
 * Google dropped messages it was holding (a phone off too long), a round
 * fetches what they carried. Any other message is ignored — nothing shown,
 * nothing written.
 */
class WakeService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushWake.saveToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        when (d["t"]) {
            // "k": the book the relay keeps the parcel for (a relay that does not say it yet leaves it out).
            "rk-wake" -> PushWake.roundNow(applicationContext, d["k"])
            "rk-parcel" -> PushWake.takeCarried(applicationContext, d["f"].orEmpty(), d["b"].orEmpty(), d["k"])
        }
    }

    override fun onDeletedMessages() {
        PushWake.roundNow(applicationContext)
    }
}
