package com.innovation313.roshankhata.data

/**
 * One switch for the whole Google Drive backup feature.
 *
 * ON since the Cloud Console setup was completed: Drive API enabled, consent
 * screen branded and published In production, and an Android OAuth client
 * carrying the release keystore's SHA-1 for com.innovation313.roshankhata.
 *
 * The comment this replaces said verification had to come first. That was
 * wrong, and it cost a day. The app requests exactly one scope —
 * drive.appdata, a private folder only this app can see — and Google's own
 * documentation classes that as non-sensitive: "If your app utilizes only
 * non-sensitive scopes, it is not mandatory for your app to complete the app
 * verification process." No review, no waiting, no unverified-app warning.
 *
 * The narrow scope is what buys that. drive.appdata cannot read, list or touch
 * anything else in the owner's Drive — not one file they did not create here.
 * That was chosen for the owner's privacy first; the exemption is a
 * consequence of it, not the reason for it.
 */
object DriveFeature {
    const val ENABLED = true
}
