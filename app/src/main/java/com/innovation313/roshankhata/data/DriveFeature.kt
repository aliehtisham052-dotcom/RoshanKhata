package com.innovation313.roshankhata.data

/**
 * One switch for the whole Google Drive backup feature.
 *
 * The Drive code is complete and tested, but the feature stays OFF in the UI
 * until Google has verified the app for the sensitive Drive scope. Before that
 * verification, any real user who taps "Connect Google Drive" is shown Google's
 * own "this app isn't verified" warning — a frightening screen for someone
 * whose customer ledger is at stake, and exactly the wrong first impression.
 *
 * So the button simply is not shown yet. The day verification comes through,
 * this flips to true and the feature appears for everyone — no rebuild of the
 * logic, no migration, just one line.
 *
 * Keeping the code shipped-but-hidden (rather than removed) means the feature
 * that was already built and tested is ready to go live the moment it is
 * allowed to, instead of having to be reintroduced later.
 */
object DriveFeature {
    const val ENABLED = false
}
