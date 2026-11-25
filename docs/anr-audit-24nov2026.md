# Main-thread / ANR audit of app/src/main (24 Nov 2026)

Stage 6, plan item B5 (code part). Method: grep of `app/src/main` for runBlocking, allowMainThreadQueries,
Thread.sleep / SystemClock.sleep, `.commit()`, Dispatchers.Main / lifecycleScope work, BroadcastReceiver.onReceive,
Service callbacks, Workers, Application.onCreate, first-screen startup, bitmap / PNG / PDF / JSON / network calls, and a
read of every hit's caller. Labels: VERIFIED = read in the code; INFERRED = reasoned from the code and Android's
documented behaviour; NOT TESTED = nothing was compiled or run on a device (Android files cannot be built here; CI is
the first build). Severity: CERTAIN (blocks main in a normal use) / PLAUSIBLE (blocks main on some paths, or only
when something else holds a lock) / SAFE (off the main thread, or tiny and bounded).

Result: 2 CERTAIN (both fixed), 10 PLAUSIBLE rows (6 in section 5, 4 commit() groups in section 3; reported, not changed), the rest SAFE.
25 Nov 2026 (round 3): item 5.6 fixed with near-linear grouping (proved equal to the old on random books); the commit()
callers on main moved (PaymentVoice tickets, Businesses create/rename, ViewerMode.leave) or kept with a reason; a
`commits_listed` rule in the guard; binder calls scanned (section 8) and the Payment voice screen's moved.

## 1. runBlocking (5 code hits, 2 import lines) -- all SAFE (VERIFIED callers)

| Where | Severity | Why |
|---|---|---|
| data/Businesses.kt:275 `delete` | SAFE | `delete` is launched on Dispatchers.IO by its caller; waits there |
| data/Businesses.kt:361 `endSharing` | SAFE | IO delete/erase paths; own 20 s bound |
| data/Businesses.kt:383 `leaveRelays` | SAFE | same callers; withTimeoutOrNull(5 s) |
| data/PushWake.kt:274 `within` | SAFE | runs on the helper's own executor thread; the caller stops waiting at the deadline |
| data/LiveRunner.kt:196 `start` (news-wait lambda) | SAFE | under runInterruptible on Dispatchers.IO |

(Lines 7 and 16 are imports.) `allowMainThreadQueries`: none (VERIFIED). A non-suspend DAO call on main would crash
(Room), not stall: a crash class, outside this audit.

## 2. Thread.sleep / SystemClock.sleep (3 hits) -- all SAFE

- ui/HudhudChirpPlayer.kt:69 `hold`: the bird call's worker executor.
- ui/ReceiptPrinter.kt:80, :82 `send`: Bluetooth pacing, documented off the main thread, called on the print thread.

## 3. SharedPreferences commit() (20 hits before the fixes, 18 after)

`commit()` writes and fsyncs on the calling thread; on main it is a stall (worst on slow eMMC, storage under load).

| Where | Severity | Note |
|---|---|---|
| PaymentVoice.kt:484 `nextNoticeId` | CERTAIN -> FIXED | ran on the notification listener's main thread for every notice; result unread. Now `apply()` (in-memory value is current at once; the @Synchronized counter still never repeats in-process) |
| data/BusinessProfile.kt:355 `copyRegion` | CERTAIN -> FIXED | called on main from BusinessSwitchActivity when a shop is added; result unread. Now `apply()` |
| PaymentVoice.kt `tickets`/`save` (tickets) | SAFE by design -> MOVED (25 Nov) | a consumed ticket must be durable (lesson 82), so it stays `commit()`; but its callers ran on main: `openTicket` (MainActivity.onCreate) redeemed it there, and the switch's tap (`setOn(false)`) cleared every ticket there with a notice cancel (binder) per ticket. Since 25 Nov: `openTicket` redeems on Dispatchers.IO in lifecycleScope and says "gone" / opens the form on main after; `setOn(false)` clears on AppScope (IO). Safe because `redeem` reads the switch (off at once, in memory) before the ticket. Known edge, accepted: off, on again and a payment notice within the few ms the clear takes, and that one fresh ticket is cleared (its tap then says the notice is gone). NOT TESTED |
| data/Businesses.kt `create`, `switchTo`, `delete`, `save` | PLAUSIBLE -> MOVED (25 Nov) | registry, must be durable, so `commit()` stays. `switchTo` is off main since BC-246. `create` and `rename` (-> `save`) ran in BusinessSwitchActivity's clicks: now on AppScope (IO), the switch / list redraw on main after; a second shop answer while one is being made does nothing (`creating`). BackupActivity's `create`/`ensure` and `delete` were already on IO. NOT TESTED |
| data/SetupProgress.kt:59, :63 | SAFE by design | on main, once per install, bytes; `markStarted` must be on disk before the first language is saved (else an update takes the setup for an old one and skips it) and a lost `markDone` sends the owner through the setup again. Kept `commit()` |
| data/ViewerMode.kt `switchOn`, `recordCopy`, `leave` | PLAUSIBLE -> MOVED (25 Nov) | `switchOn`/`recordCopy` run in ViewerSync on IO. `leave` ran in ViewerActivity's Stop button on main -- not only a commit: Room's close under the lock, `deleteDatabase` of the helper's whole book, its photos folder and four preference files. Now `ViewerActivity.leaveNow` runs it on AppScope (IO) and restarts through the gate on main when it is done; a second tap does nothing. NOT TESTED |
| ui/FeatureIntro.kt `markAllSeen`, `forget` | SAFE (was listed PLAUSIBLE) | test helpers only (screen proofs, store pictures); the app's own `markSeen` already uses `apply()` |
| data/BookRecovery.kt:60, :80 | SAFE | in the recovery path, already under the DB lock and rare |
| data/Snapshots.kt:467 | SAFE | daily-copy bookkeeping on the copy's own thread |
| data/DriveBackup.kt:133 | SAFE | IO |
| data/Backup.kt:362 | SAFE | result is read (a failure throws); IO export path |
| data/WarisAndroid.kt:325 | SAFE | Waris worker / IO |

## 4. Startup (VERIFIED)

- RoshanKhataApp.onCreate: preferences, a lazily read sense model, `ImageReattach.recover` on its own Thread. SAFE.
- BackgroundWatcher lifecycle callbacks only enqueue or cancel WorkManager jobs. SAFE.
- BaseActivity.onStart/onResume: preferences only. GateActivity: `InstallSource.checkOnce` is asynchronous. SAFE.
- No ContentProvider of the app's own. SAFE.

## 5. Plausible risks (reported, not changed -- each needs a design choice or touches a file this stage may not edit)

1. **MOVED (24 Nov 2026, round 2, NOT TESTED) -- PaymentListenerService.onNotificationPosted -> PaymentVoice.neverRead.**
   `neverRead`, `labelOf` and `nameOf` now run on Dispatchers.Default inside the service's coroutine; the privacy rule is
   unchanged and asked afresh each notice (no cache: a cached "not an SMS app" would go stale when an SMS app is installed
   or made the default, which weakens the rule). `PaymentVoice.Blockers` asks the PackageManager once per call, so
   `seen()` (the app list on the Payment voice screen, up to 40 rows) asks once, not 40 x 3 times. Original finding:
   Notification-listener callbacks run on the service's main thread; each notice asks PackageManager (binder) up to
   three times. Bounded and fast normally, but a busy system_server stretches it, and a burst of notices queues. A
   short cache would fix it, but `neverRead` is the privacy rule that keeps SMS apps unread: not relaxed here.
2. **FIXED (25 Nov 2026, BC-246, CI first) -- the three callers now call `Businesses.switchOffMain` (switchTo on Dispatchers.IO, every step in its order; only startActivity on main; a double tap guarded). Original finding: Businesses.switchTo on main (data/Businesses.kt:172-195; callers BusinessSwitchActivity:226, BackupActivity:2224,
   :2259).** Does `commit()`, `closeActive` (Room close under the global DB lock) and `cancelAll()` on main. If a
   daily copy or live-sync round holds the lock, the switch waits on main. Not moved inside `switchTo`: the order
   commit -> afterLeaving -> closeActive -> bookChanged -> dropCaches -> cancelAll is what every caller relies on (each
   calls `KhataDatabase.get` right after), a late `cancelAll` could cancel the new shop's first notice, and `apply()` in
   place of `commit()` would let a killed process wake in the old shop. **The safe fix is in the three callers**, not
   owned in this round: all three already run inside `AppScope` (IO) and hop to `Dispatchers.Main` only for the switch --
   BusinessSwitchActivity:226 (run `switchTo` before its `withContext(Main)`, keep `startActivity` on main) and
   BackupActivity:2224 and :2259 (drop the `withContext(Dispatchers.Main)` around `switchTo`). Read: `switchTo`'s
   callees (`AutoBackup.afterLeaving`, `closeActive` + `Access.signOut`, `LiveRunner.bookChanged`, `PartyPhoto.dropCaches`,
   `NotificationManagerCompat.cancelAll`) use no view and no main-only API. NOT TESTED; androidTest
   HudhudBusinessIsolationOnDeviceTest calls it from the test thread already.
3. **KhataDatabase.get() lock (data/KhataDatabaseAndroid.kt:25-165).** `synchronized(KhataDatabaseHolder)` is also
   taken by `closeActive` and `whileClosed` (snapshot restore, erase swaps). A first `get()` from main during a swap
   waits for it; `BookRecovery.recoverIfNeeded` (BookRecovery.kt:68-81) can rebuild a book from a snapshot under that
   lock. Rare. INFERRED.
4. **MOVED (24 Nov 2026, round 2, NOT TESTED) -- PNG compress + file write on main in a tap:** BusinessCardActivity.shareCard (the
   views are read on main, the 1200x700 draw, PNG compress and file write run in `withContext(Dispatchers.IO)`),
   ImageCropActivity.confirmCrop (rectangle read on main; crop, compress, write on IO; the result is set on main),
   EntryDetailActivity.shareReceipt (the card view is drawn into its bitmap on main, as a view must be; the seal's stamp
   decode, PNG compress and file write on IO). Each has a flag so a second tap does nothing while it runs, and each runs in
   lifecycleScope so a screen that is gone cancels it. Original finding:
   Tens to a few hundred ms on a low-end phone. INFERRED, not measured.
5. **MOVED (24 Nov 2026, round 2, NOT TESTED) -- Bitmap decode on main:** BusinessCardActivity.loadLogo (decode on IO in
   lifecycleScope; the card is drawn without the logo for a moment, then again; a share waits for it), PartyDetailActivity.viewPhotoFullSize
   (a photo already in memory opens at once, a cold one is read on IO). Both files are already small: the logo is saved at
   most 1000 px (`BusinessProfile.MAX_EDGE`) and the party photo at 400 px (`PartyPhoto.EDGE`), so no inSampleSize was
   added -- the decode was a disk read, not a full-size camera decode.
6. **FIXED (24-25 Nov 2026, BC-243, BC-303) -- DuplicateDetector over the whole book.** 24 Nov (ce4d604): the duplicates
   screen grouped on Dispatchers.Default and the add-party form built `DuplicateDetector.Index` once off main (its check
   per pause in typing is two hash lookups). 25 Nov: (a) `DuplicateDetector.find` was quadratic -- each group's members
   were found by reading the whole book again (`all.filter { it.partyId in ids }`); now the book's places by party id
   are read once and each group takes its members from them, sorted back into the book's order, every record of an id
   included: the same groups, order, reasons and dismissals. (b) `caution` (run by the card's bind, on main) compared
   every pair of a group's numbers -- 4.5 million for 3,000 parties under one number; now each distinct number is filed
   once per place with that place covered, and a file holding a second number is "one digit apart": the same answer.
   (c) DuplicateCustomersActivity: the candidates are made on Default too, one look at a time (a newer look cancels the
   older, so a late one cannot show a group a merge removed), and onCreate no longer starts a second look beside
   onResume's. **Proof (local, shared harness compiler):** `DuplicateDetectorBucketTest` runs the old code (copied in
   the test) against the new on 400 random invented books (names folding alike, numbers typed four ways, one digit off,
   in Arabic-Indic digits, short, blank; a party id listed twice; dismissed groups): every group, its order, reason and
   caution equal, every Caution and Reason reached, over 2,000 groups compared; the one-digit rule against the pairwise
   rule on 5,000 random lists. Broken on purpose six ways (no sort, first record of an id only, no covered marker, no
   de-duplication, a size guard off by one, the first place skipped): each failed. **Measured** on a 10,400-party
   invented book (2,230 groups, 400 parties under one number), this machine, best of 3: find 259 ms old -> 31 ms new
   (a second run under load: 676 -> 91); caution of every group 25 -> 11 ms (38 -> 24). The test asserts only "not
   slower" (+50 ms noise); the figures are printed. Android code (the screen): NOT TESTED, CI first. Windows'
   `DuplicatesPane` still groups inside `remember` on the window's thread and `PartyForm` calls `matchExisting` (all
   names normalised) at every recomposition -- not changed (not this stage's files; see section 8).

## 6. Checked and SAFE (VERIFIED)

PDF builders (statement, invoice, register, reports) and share paths: IO. Backup export/restore, Drive, network
(Drive, relays, LiveControl, TSAs): IO or the worker's dispatcher. Workers (AutomationWorker, ReminderWorker,
WarisWorker, AutoBackup.Worker/LeftShopWorker): CoroutineWorker on Default. BroadcastReceivers and WakeService: enqueue or
hand to AppScope (Dispatchers.IO). The 3 Nov speed work's slices (LedgerLines) hold the Khata list reads.

## 7. Guard

`scripts/check_main_thread.py` fails on a `runBlocking`, `Thread.sleep` or `SystemClock.sleep` in `app/src/main` unless
its file:function is in the script's allow-list with a count and a reason (8 code hits, 7 entries today: runBlocking x5, Thread.sleep x3). Run once clean (exit 0);
broken once on purpose with a temporary copy holding both patterns in a new function: exit 1, the line named.
Since 25 Nov 2026 it also lists every `.commit()` in `app/src/main` (rule `commits_listed`, BC-304): 19 hits in 18
file:function entries, each with the most it may hold and why it is safe (off main by its callers, or rare and needing the
disk before the next step). A line scan cannot know the caller's thread, so the list is where a person reads each new one
once. Run clean (exit 0); broken once on purpose -- a new file with a `commit()` in a click-like function and a second
`commit()` in an allowed function: exit 1, both lines named; restored, exit 0.
Not covered by the guard: binder calls (section 8: no line scan can tell a cheap one, a rare one and an off-main one apart).

## 8. Binder calls on the main path (25 Nov 2026, scan of app/src/main)

Scanned for PackageManager (`getPackageInfo`, `queryIntentActivities`, `resolveActivity`, `getInstallSourceInfo`,
`getApplicationLabel`), `contentResolver.query`, AccountManager and the notification-listener list, each caller read.

| Where | Label | Note |
|---|---|---|
| PaymentVoiceActivity.onResume / renderApps | MOVED (BC-305) | about ten binder calls in onResume and on the switch's tap: notification access (Settings), the two wallets' `getPackageInfo` and installer, the default SMS app, every SMS-capable app (`queryIntentActivities`), the default dialer, this app's installer. Now asked on Dispatchers.IO in lifecycleScope (one look at a time); only the views are made on main, with the screen's own words. NOT TESTED (EveryScreenOpensTest opens the screen on CI) |
| PaymentVoice.openTicket / setOn(false) | MOVED | the notice cancels (NotificationManager) go with the ticket writes, section 3 |
| PaymentListenerService (neverRead, labelOf) | SAFE | moved 24 Nov (5.1) |
| ui/Reminder.kt `whatsAppIntents` | NOT CHANGED (low) | one `queryIntentActivities` on a tap, only on Android 7-10; the answer decides which intent opens next, so it stays |
| ui/SignInProblem.kt `signingSha1` | NOT CHANGED (low) | one `getPackageInfo` when a Drive sign-in failure dialog opens; rare |
| data/HudhudPhoneEnv.kt `appVersion` | NOT CHANGED | `getPackageInfo` for the app's own version when Hudhud answers "which version"; a Hudhud file, not this stage's |
| SalesReportActivity.writeCsv, data/Contacts.kt | SAFE | `contentResolver.query` inside withContext(Dispatchers.IO) |
| AccountManager | none | the app has no AccountManager call |
