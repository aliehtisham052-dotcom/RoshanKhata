package com.innovation313.roshankhata.data

/**
 * Which backup is locked with the owner's password, and when (25 Nov 2026, the owner's decision D14, hybrid):
 *
 *  - The Google Drive backup has NO password: the owner's Google account protects the app's own Drive folder, and a
 *    helper's phone, a new phone's restore and the automatic run all need it open.
 *  - Every backup FILE that leaves the device is protected BY DEFAULT: phone — Save to Downloads, Share (WhatsApp),
 *    Send to the computer, the helper's copy file; computer — Back up now, the outside folder, a copy for a phone. The
 *    first one asks the owner for a password (and a hint he chooses); later ones use it without asking.
 *  - A switch turns file protection off, with a plain warning; screens and saved-file notices say plainly which it is.
 *  - What stays inside the app's own folder (the daily copies, the phone's internal saved copy) is not a file that
 *    leaves the device: it follows the file it was made from, or stays plain as before.
 *
 * Pure rules, no Android and no file: the platforms ask [plan] and do what it says.
 */
object BackupProtection {
    /** Where a backup goes. [leavesDevice]: it reaches a place the owner no longer controls. [protectedByDefault]: D14's table. */
    enum class Way(val leavesDevice: Boolean, val protectedByDefault: Boolean) {
        /** The automatic and the manual Google Drive backup, photo archive included: no password (D14.1). */
        DRIVE(true, false),
        /** Phone: Save to Downloads. */
        DOWNLOADS(true, true),
        /** Phone: the share sheet (WhatsApp, mail, a file manager). */
        SHARE(true, true),
        /** Phone: Send to the computer over WiFi. */
        TO_COMPUTER(true, true),
        /**
         * Phone: the helper's copy file (More → helper). Open on purpose: it is made with secrets = false (no PIN hashes,
         * server passes, private notes or costs), and locking it with the owner's own password would mean telling the
         * helper that password, which opens every other backup of the book.
         */
        HELPER_FILE(true, false),
        /** Computer: Back up now, to the folder or stick the owner chose. */
        BACK_UP_NOW(true, true),
        /** Computer: today's copy in the outside folder (a USB stick, Google Drive for Desktop). */
        OUTSIDE_FOLDER(true, true),
        /** Computer: the book offered to a phone that asks for it. */
        FOR_PHONE(true, true),
        /** The computer's own daily copies and the phone's own safety copies: inside the app's folder. */
        DAILY_COPY(false, false)
    }

    /** What a platform does with one backup. */
    sealed class Plan {
        /** Written open, as it always was. */
        object Plain : Plan()
        /** Locked with [password] (and [hint] shown before it is asked, null for none). */
        class Seal(val password: String, val hint: String?) : Plan()
        /** Protection is on and no password is set yet: ask the owner for one now, then plan again. Nothing is written meanwhile. */
        object NeedFirstPassword : Plan()
        /** Protection is on, a password is set and cannot be read: nothing is written — never an open file instead. */
        class Unavailable(val why: String) : Plan()
    }

    /** Whether [way] is locked when file protection is [filesProtected]. */
    fun wanted(way: Way, filesProtected: Boolean): Boolean = way.leavesDevice && way.protectedByDefault && filesProtected

    fun plan(way: Way, filesProtected: Boolean, state: BackupPassword.State, hint: String?): Plan {
        if (!wanted(way, filesProtected)) return Plan.Plain
        return when (state) {
            BackupPassword.State.None -> Plan.NeedFirstPassword
            is BackupPassword.State.Set -> Plan.Seal(state.password, BackupSeal.cleanHint(hint))
            is BackupPassword.State.Unreadable -> Plan.Unavailable(state.why)
        }
    }

    /** A backup could not be made because it is to be locked and no password is set; the screen should have asked first. */
    class NeedsPassword : IllegalStateException("backup password needed")

    /** What a screen says about the files that leave the device. */
    enum class Status {
        /** On, with a password: "Protected with your password". */
        PROTECTED,
        /** On, no password yet: the next file asks for one. */
        NEEDS_PASSWORD,
        /** On, a password is set and cannot be read: no file is made until it is set again. */
        UNREADABLE,
        /** Off: "Open: anyone with this file can read your book". */
        OPEN
    }

    fun status(filesProtected: Boolean, state: BackupPassword.State): Status = when {
        !filesProtected -> Status.OPEN
        state is BackupPassword.State.Set -> Status.PROTECTED
        state is BackupPassword.State.Unreadable -> Status.UNREADABLE
        else -> Status.NEEDS_PASSWORD
    }

    /** A small key-value place the platform supplies for the two non-secret choices (the switch, the hint). */
    interface Store {
        fun read(key: String): String?
        /** null removes. Must be written before it returns (a backup made the next moment reads it). */
        fun write(key: String, value: String?)
    }

    private const val OPEN_KEY = "files_open"
    private const val HINT_KEY = "hint"

    /** File protection is ON unless the owner turned it off. An unreadable or unknown store means on. */
    fun filesProtected(store: Store): Boolean = try { store.read(OPEN_KEY) != "1" } catch (e: Exception) { true }

    fun setFilesProtected(store: Store, on: Boolean) = store.write(OPEN_KEY, if (on) null else "1")

    /** The hint kept with the password; null for none. Plain: it goes into every locked file (see [BackupSeal]). */
    fun hint(store: Store): String? = try { BackupSeal.cleanHint(store.read(HINT_KEY)) } catch (e: Exception) { null }

    fun setHint(store: Store, hint: String?) = store.write(HINT_KEY, BackupSeal.cleanHint(hint))

    /** Whether a typed hint may be kept next to [password]: it must not hold the password. */
    fun hintAllowed(hint: String?, password: String): Boolean = !BackupSeal.hintGivesAway(hint, password)

    /** Where the choices stand now, for [way]. [state] is read by the caller (the Keystore or DPAPI: off the main thread). */
    fun planFor(way: Way, store: Store, state: BackupPassword.State): Plan = plan(way, filesProtected(store), state, hint(store))
}
