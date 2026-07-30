package com.innovation313.roshankhata.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.drive.DriveScopes

/**
 * Who has authorized Drive access, and how to ask for it.
 *
 * This used to wrap the legacy Google Sign-In API, which handled both "who is
 * signed in" and "what may the app touch" in one call. Google deprecated that
 * API and split the two apart: sign-in via Credential Manager, and
 * AUTHORIZATION (the app's own Drive appdata folder) via AuthorizationClient.
 * Roshan Khata never needed a sign-in identity for its own sake -- it only ever
 * needed permission to reach the backup folder -- so it now uses ONLY the
 * authorization half. There is no app-wide Google login; connecting Drive is
 * the one and only place a Google account is involved, exactly as before.
 *
 * The ONLY scope requested is the app-data Drive folder (drive.appdata) -- a
 * private space this app can see and nothing else in the owner's Drive. The
 * narrowest door that opens the room, and non-sensitive, so it needs no special
 * Google verification. The consent screen says precisely that.
 *
 * "Connected" is remembered locally: AuthorizationClient does not keep a
 * getLastSignedInAccount()-style handle the way the old API did, so once the
 * owner authorizes, their account email is stored here and the backup screen
 * reads it to know it may show the backup controls. The email is only an
 * account label -- the actual reach into Drive always goes back through
 * authorize(), which re-checks the grant is still in place.
 */
object DriveAuth {

    private const val PREFS = "roshan_khata_prefs"
    private const val KEY_ACCOUNT = "drive_account_name"

    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The authorization request for exactly the appdata scope. No offline
     * access is requested: this app reaches Drive only from the device, never
     * from a server, so it needs no refresh token -- a short-lived on-device
     * access token, refreshed silently by authorize() on each session, is the
     * whole requirement.
     */
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(driveScope))
            .build()

    /**
     * Ask for authorization. The result either already has the grant (returns a
     * usable [AuthorizationResult]) or carries a PendingIntent the caller must
     * launch to show the consent screen. The caller handles both -- see
     * BackupActivity.
     */
    fun authorize(context: Context): Task<AuthorizationResult> =
        Identity.getAuthorizationClient(context).authorize(authorizationRequest())

    /**
     * Pull the result back out of the intent the consent screen returned. Used
     * by the activity-result callback after the owner grants (or declines).
     */
    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /**
     * Remember which account authorized, so the screen can show it next time.
     *
     * The email is read via toGoogleSignInAccount() -- the one accessor the
     * AuthorizationResult reliably exposes for the account behind the grant.
     * (GoogleSignInAccount is itself a deprecated type, but it is still what
     * this result hands back, and only its email field is read -- no sign-in
     * behaviour depends on it.)
     */
    fun rememberAccount(context: Context, result: AuthorizationResult) {
        val email = result.toGoogleSignInAccount()?.email
        if (!email.isNullOrBlank()) {
            prefs(context).edit().putString(KEY_ACCOUNT, email).apply()
        }
    }

    /** The remembered account email, or null if Drive was never connected. */
    fun accountName(context: Context): String? =
        prefs(context).getString(KEY_ACCOUNT, null)?.takeIf { it.isNotBlank() }

    /**
     * The Android Account the Drive client authenticates as. Built from the
     * remembered email -- GoogleAccountCredential only needs the account, and
     * the actual token is fetched by authorize() at backup time.
     */
    fun account(context: Context): Account? =
        accountName(context)?.let { Account(it, "com.google") }

    /** Whether Drive has been connected (an account is remembered). */
    fun isConnected(context: Context): Boolean = accountName(context) != null

    /**
     * Forget the connection. AuthorizationClient has no "sign out" of its own --
     * disconnecting here just drops the remembered account so the screen goes
     * back to its not-connected state; the OAuth grant itself lives in the
     * owner's Google account and can be removed from their Google settings.
     */
    fun disconnect(context: Context, onDone: () -> Unit) {
        prefs(context).edit().remove(KEY_ACCOUNT).apply()
        onDone()
    }
}
