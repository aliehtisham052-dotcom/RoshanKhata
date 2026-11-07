package com.innovation313.roshankhata.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The phone's side of [CustomerLink] (7 Nov 2026, "Khata link bhejein"): the shop's customers who were sent a link
 * see their balance on khata.ehtisham.shop, or in Roshan Khata ([com.innovation313.roshankhata.MeraKhataActivity]).
 *
 * Light on the server, as the owner asked ("chand log saare credits khatam na kar dein"):
 *  - only customers the owner sent a link to are ever sent, only when their balance changed (CustomerLink keeps
 *    a hash per page and sends nothing when nothing changed — not even a request);
 *  - at most one send every [GAP_MIN] minutes: the app leaving the screen asks for one, and asks made while one
 *    waits are the same one (KEEP);
 *  - it goes to the owner's hosting as plain files, never through live sync's relays;
 *  - the hosting's own caps hold everyone (web/khata/api.php: new pages and requests per shop a day, new shops per
 *    address a day and an hour in all, shops and files per server, a share of the files kept for older shops).
 * A helper's read-only phone never sends (the book is the owner's). When another copy of the book writes the pages,
 * the background send leaves a note ([otherCopy]) and the link menu offers to take over (7 Nov 2026).
 */
object CustomerLinkSync {

    const val GAP_MIN = 10L
    private const val WORK = "customer_link_flush"
    /** Set the first time this phone sends a customer his link. */
    private const val USED = "clink_used"

    /** This phone's own notes for the pages it sent: preferences, outside the book and every backup. */
    class Prefs(context: Context) : CustomerLink.Place {
        private val p = context.applicationContext.getSharedPreferences("customer_link", Context.MODE_PRIVATE)
        override fun get(key: String): String? = p.getString(key, null)
        override fun put(key: String, value: String?) {
            if (value == null) p.edit().remove(key).apply() else p.edit().putString(key, value).apply()
        }
    }

    /** How the shop's pages look where the book has not said: the profile's name, the shop's sign, the app's language. */
    fun look(context: Context): CustomerLink.Look {
        val lang = runCatching {
            val chosen = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            val tag = if (!chosen.isEmpty) chosen[0] else context.resources.configuration.locales[0]
            when {
                tag == null -> "en"
                tag.language == "ur" && tag.script == "Latn" -> "ur-Latn"
                else -> tag.language
            }
        }.getOrDefault("en")
        return CustomerLink.Look(BusinessProfile.businessName(context), BusinessProfile.currency(context), lang)
    }

    /** The book's live sync key while it syncs: the phone and the computer then make the same links. */
    suspend fun bookKey(context: Context): String? =
        runCatching { LiveSync.identity(KhataDatabase.get(context).syncSql())?.key }.getOrNull()

    /** Which book these notes are for: the open business's file. */
    fun scope(context: Context): String = Businesses.activeDbFile(context)

    /** Asks for a send in [GAP_MIN] minutes, with the network; a send already waiting is kept, not doubled. */
    fun soon(context: Context) {
        if (CustomerLink.base == null || ViewerMode.isOn(context)) return
        // A phone that never sent a link asks for nothing at all.
        if (Prefs(context).get(USED) == null) return
        runCatching {
            val req = OneTimeWorkRequestBuilder<Worker>()
                .setInitialDelay(GAP_MIN, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, req)
        }
    }

    /**
     * Sends what changed now (after a link was taken back, so it stops opening at once). [claim]: the owner's word
     * that this copy of the book writes the pages from now on (another copy held them).
     */
    suspend fun flushNow(context: Context, claim: Boolean = false): CustomerLink.Result {
        if (ViewerMode.isOn(context)) return CustomerLink.Result.Off
        val dao = KhataDatabase.get(context).khataDao()
        return runCatching {
            CustomerLink.flush(dao, Prefs(context), scope(context), look(context), bookKey(context), claim)
        }.getOrDefault(CustomerLink.Result.Unreachable)
    }

    /** Another copy of this book writes its customers' pages (the last send was refused for it). */
    fun otherCopy(context: Context): Boolean = CustomerLink.otherCopy(Prefs(context), scope(context))

    /** The customer's link taken back, noted on this phone so it is never made again here ([CustomerLink.remove]). */
    suspend fun remove(context: Context, partyUid: String): CustomerLink.Link? =
        CustomerLink.remove(KhataDatabase.get(context).khataDao(), partyUid, Prefs(context))

    /** The customer's link, ready to send, his page on the server first ([CustomerLink.share]). */
    suspend fun share(context: Context, partyUid: String, claim: Boolean = false): CustomerLink.Shared {
        Prefs(context).put(USED, "1")
        val dao = KhataDatabase.get(context).khataDao()
        return runCatching {
            CustomerLink.share(dao, Prefs(context), scope(context), look(context), partyUid, bookKey(context), claim)
        }.getOrElse { CustomerLink.Shared.NotYet(CustomerLink.Result.Unreachable) }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            // Unreachable is tried again on the next leave of the app, not in a loop here.
            flushNow(applicationContext)
            return Result.success()
        }
    }
}
