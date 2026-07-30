package com.innovation313.roshankhata.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.drive.DriveScopes
import com.innovation313.roshankhata.R

/**
 * Who has connected Drive, and how to connect them.
 *
 * The legacy Google Sign-In API did authentication and authorization in one
 * call; Google deprecated it and split the two. Roshan Khata now uses BOTH
 * halves, in order, for one purpose -- connecting the backup folder while
 * showing the owner which account it is:
 *
 *   1. Sign in with Google (Credential Manager) -- ONLY to learn the account
 *      email, so the backup screen can show "connected as name@gmail.com". It
 *      grants no data access by itself.
 *   2. AuthorizationClient -- the actual permission to the app's own Drive
 *      appdata folder. This returns a token, not an identity, which is why
 *      step 1 exists at all.
 *
 * There is no app-wide login: the app runs fine with nobody signed in, and this
 * whole two-step only ever runs when the owner taps "connect Drive". Connecting
 * Drive stays the single place a Google account is involved.
 *
 * The ONLY Drive scope requested is the app-data folder (drive.appdata) -- a
 * private space this app can see, nothing else in the owner's Drive. Narrow and
 * non-sensitive, so it needs no special Google verification. The sign-in asks
 * only for basic identity (the email to show). The consent screens say exactly
 * this.
 *
 * The connected email is remembered locally so the screen can show it between
 * visits; it is only a label. The real reach into Drive always goes back
 * through authorize(), which re-checks the grant is still in place.
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
     * Sign in with Google via Credential Manager, only to learn WHICH account
     * the owner is connecting -- so the backup screen can show that email.
     *
     * This is the piece that brings the email back. The Drive authorization
     * step (authorize(), below) returns only a token, deliberately not the
     * account identity; Google split those apart so an app asking merely for
     * storage cannot also read who you are. But the owner has a real need to
     * SEE which Gmail their books back up to -- a backup to the wrong account
     * must be visible -- so a Sign in with Google step is added purely for that
     * label. It grants no data access on its own; the Drive permission is still
     * the separate authorize() call.
     *
     * Returns the account email, or null if the owner dismissed the sheet or no
     * account came back. Must be called from a coroutine (getCredential
     * suspends). The serverClientId is the Web OAuth client id -- Credential
     * Manager verifies the returned ID token against it.
     */
    suspend fun signIn(context: Context): String? {
        val option = GetSignInWithGoogleOption.Builder(
            context.getString(R.string.google_web_client_id)
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            return google.id.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * Remember the connected account's email so the screen can show it. The
     * email comes from the Credential Manager sign-in above -- the one place it
     * is reliably available under the new APIs.
     */
    fun rememberAccount(context: Context, email: String) {
        if (email.isNotBlank()) {
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
