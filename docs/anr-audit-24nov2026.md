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

1. **PaymentListenerService.onNotificationPosted -> PaymentVoice.neverRead (PaymentVoice.kt:142-165, ~237-250).**
   Notification-listener callbacks run on the service's main thread; each notice asks PackageManager (binder) up to
   three times. Bounded and fast normally, but a busy system_server stretches it, and a burst of notices queues. A
   short cache would fix it, but `neverRead` is the privacy rule that keeps SMS apps unread: not relaxed here.
2. **Businesses.switchTo on main (data/Businesses.kt:172-195; callers BusinessSwitchActivity:226, BackupActivity:2224,
   :2259).** Does `commit()`, `closeActive` (Room close under the global DB lock) and `cancelAll()` on main. If a
   daily copy or live-sync round holds the lock, the switch waits on main. File is off-limits here.
3. **KhataDatabase.get() lock (data/KhataDatabaseAndroid.kt:25-165).** `synchronized(KhataDatabaseHolder)` is also
   taken by `closeActive` and `whileClosed` (snapshot restore, erase swaps). A first `get()` from main during a swap
   waits for it; `BookRecovery.recoverIfNeeded` (BookRecovery.kt:68-81) can rebuild a book from a snapshot under that
   lock. Rare. INFERRED.
4. **PNG compress + file write on main in a tap:** BusinessCardActivity.kt:396, :409-425 (a 1200x700 card draw and
   compress), ImageCropActivity.kt:156-186 (crop + compress), EntryDetailActivity.kt:1040-1056 (receipt PNG).
   Tens to a few hundred ms on a low-end phone. INFERRED, not measured.
5. **Bitmap decode on main:** BusinessCardActivity.kt:320 (logo), PartyDetailActivity.kt:3119 (full-size photo on tap).
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
