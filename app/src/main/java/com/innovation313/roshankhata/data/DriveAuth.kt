package com.innovation313.roshankhata.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

/**
 * Who is signed in, and how to sign them in.
 *
 * A thin wrapper over Google Sign-In so the rest of the app can ask one plain
 * question — "is someone signed in, and what is their account name?" — without
 * knowing anything about the sign-in machinery.
 *
 * The ONLY scope requested is the app-data Drive folder. Not the user's email
 * beyond identifying the account, not their contacts, not their full Drive.
 * The narrowest door that opens the room. When the consent screen appears, it
 * will say precisely this: that Roshan Khata may see and manage only its own
 * backup data. That honesty is the point — a ledger app asking for the run of
 * someone's Drive would deserve to be refused.
 */
object DriveAuth {

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()

    fun client(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(context, signInOptions())

    fun signInIntent(context: Context): Intent =
        client(context).signInIntent

    /** The signed-in account, or null if nobody is signed in yet. */
    fun account(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /** The account's email, used both to show who's signed in and to reach Drive. */
    fun accountName(context: Context): String? =
        account(context)?.email

    /**
     * Whether the Drive-appdata permission was actually granted.
     *
     * Signing in and granting the Drive scope are two separate consents — a
     * user can do the first and decline the second. Checking the scope, not
     * just the sign-in, keeps the app from trying to reach a Drive it was never
     * allowed into.
     */
    fun hasDrivePermission(context: Context): Boolean {
        val acct = account(context) ?: return false
        return GoogleSignIn.hasPermissions(acct, Scope(DriveScopes.DRIVE_APPDATA))
    }

    fun signOut(context: Context, onDone: () -> Unit) {
        client(context).signOut().addOnCompleteListener { onDone() }
    }
}
