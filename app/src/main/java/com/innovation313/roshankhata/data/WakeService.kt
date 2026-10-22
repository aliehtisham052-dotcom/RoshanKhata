package com.innovation313.roshankhata.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase's door into the app ([PushWake]): a new token is kept for the
 * relay, and the relay's one word, "rk-wake", runs a few relay rounds. Any
 * other message is ignored — nothing shown, nothing written.
 */
class WakeService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushWake.saveToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["t"] == "rk-wake") PushWake.roundNow(applicationContext)
    }
}
