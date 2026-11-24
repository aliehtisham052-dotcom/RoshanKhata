# Main-thread / ANR audit of app/src/main (24 Nov 2026)

Stage 6, plan item B5 (code part). Method: grep of `app/src/main` for runBlocking, allowMainThreadQueries,
Thread.sleep / SystemClock.sleep, `.commit()`, Dispatchers.Main / lifecycleScope work, BroadcastReceiver.onReceive,
Service callbacks, Workers, Application.onCreate, first-screen startup, bitmap / PNG / PDF / JSON / network calls, and a
read of every hit's caller. Labels: VERIFIED = read in the code; INFERRED = reasoned from the code and Android's
documented behaviour; NOT TESTED = nothing was compiled or run on a device (Android files cannot be built here; CI is
the first build). Severity: CERTAIN (blocks main in a normal use) / PLAUSIBLE (blocks main on some paths, or only
when something else holds a lock) / SAFE (off the main thread, or tiny and bounded).

Result: 2 CERTAIN (both fixed), 10 PLAUSIBLE rows (6 in section 5, 4 commit() groups in section 3; reported, not changed), the rest SAFE.

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
| PaymentVoice.kt:293 (tickets) | SAFE by design | a consumed ticket must be durable (lesson 82); a few bytes. Left on purpose |
| data/Businesses.kt:138, :181, :323, :410 | PLAUSIBLE | registry; :181 is `switchTo`, which runs on main (see 5.2). Business* is not editable in this stage |
| data/SetupProgress.kt:59, :63 | PLAUSIBLE (low) | once per install, bytes; "done" must survive a kill, so commit is right |
| data/ViewerMode.kt:95, :116, :131 | PLAUSIBLE (low) | helper-phone switch, rare, must be durable |
| ui/FeatureIntro.kt:113, :118 | PLAUSIBLE (low) | once per feature, on a sheet's first show |
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
2. **NOT CHANGED (round 2) -- Businesses.switchTo on main (data/Businesses.kt:172-195; callers BusinessSwitchActivity:226, BackupActivity:2224,
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
6. **DuplicateDetector over the whole book on main:** DuplicateCustomersActivity.kt:70-89, KhataActivity.kt:740-760 and
   :887-897. Quadratic in customers on a big book; the 10,000-customer book of the speed work is the case to measure.
   NOT TESTED.

## 6. Checked and SAFE (VERIFIED)

PDF builders (statement, invoice, register, reports) and share paths: IO. Backup export/restore, Drive, network
(Drive, relays, LiveControl, TSAs): IO or the worker's dispatcher. Workers (AutomationWorker, ReminderWorker,
WarisWorker, AutoBackup.Worker/LeftShopWorker): CoroutineWorker on Default. BroadcastReceivers and WakeService: enqueue or
hand to AppScope (Dispatchers.IO). The 3 Nov speed work's slices (LedgerLines) hold the Khata list reads.

## 7. Guard

`scripts/check_main_thread.py` fails on a `runBlocking`, `Thread.sleep` or `SystemClock.sleep` in `app/src/main` unless
its file:function is in the script's allow-list with a count and a reason (8 code hits, 7 entries today: runBlocking x5, Thread.sleep x3). Run once clean (exit 0);
broken once on purpose with a temporary copy holding both patterns in a new function: exit 1, the line named.
Not covered by the guard: commit() on main (needs the caller's thread, which a line scan cannot know) and binder calls.
