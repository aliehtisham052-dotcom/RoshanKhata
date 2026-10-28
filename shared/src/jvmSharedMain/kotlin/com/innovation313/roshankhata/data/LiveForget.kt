package com.innovation313.roshankhata.data

/**
 * Removing a device from this book's live sync (the owner's Remove, the
 * computer's Live sync window and the phone's Devices screen alike, 23 Oct
 * 2026). Only this device forgets it: the other devices keep their own
 * list, and the removed device's own book stays as it is.
 *
 * What goes with it, so a device that never comes back holds nothing up:
 * - what [LiveCrew] held or announced for it (acks toward the others held at
 *   a copy's line until they heard of it — with it gone for ever they would
 *   be held for ever, and every relay kept each parcel the full seven days);
 * - which relay its parcels came by ([LiveCloud.forgetDevice]) — else this
 *   device kept sending to that relay, and kept the HTTP clock, for nobody;
 * - on a phone, the WiFi road to it when it was this phone's computer
 *   ([LiveRound.setTarget]) — else the next WiFi round added it straight back;
 * - the device itself ([LiveSync.removePeer]), last: with no reader left the
 *   log is let go there.
 *
 * Nothing is said to the relays: a "leave" on its behalf would also delete
 * the parcels it left there, which the book's other devices may not have
 * read yet. The relays let a device go after a month unused and every parcel
 * after seven days on their own.
 */
object LiveForget {

    suspend fun device(sql: SyncSql, device: String) {
        LiveCrew.forget(sql, device)
        LiveCloud.forgetDevice(sql, device)
        if (LiveRound.target(sql)?.device == device) LiveRound.setTarget(sql, null)
        LiveSync.removePeer(sql, device)
    }
}
