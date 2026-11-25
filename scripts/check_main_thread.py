#!/usr/bin/env python3
"""Nothing in app/src/main waits on the main thread's behalf with runBlocking or a sleep (24 Nov 2026, the ANR audit).

  no_blocking_waits (BC-NEW): `runBlocking`, `Thread.sleep` and `SystemClock.sleep` in app/src/main
      are refused unless the file:function is in ALLOWED below, each with the reason it is safe and
      the most hits it may hold. A call that blocks the thread it runs on freezes the screen when that
      thread is the main one (Play Console's ANR: input not answered for 5 s). The justified ones all
      run off the main thread: on Dispatchers.IO, on a thread of their own, or on a worker executor.
      A new one must either go off the main thread without blocking (suspend, withContext) or be
      added here with its reason, so a person reads it once.

  commits_listed (25 Nov 2026, BC-304): every `.commit()` in app/src/main (SharedPreferences.Editor's
      synchronous write, which waits for the disk on the thread it runs on) is in COMMITS below with the
      most it may hold and why it is safe: the callers run it off the main thread, or it must be on disk
      before the next step and runs rarely (once per install, a test helper). A line scan cannot see the
      caller's thread, so a person reads each new one once: either `apply()` (when a lost write costs
      nothing worse than asking again) or off the main thread, or listed here with its reason.

  A comment line, an import line and a string that only mentions the word are not hits. The function is
  the nearest `fun` above the hit; a hit before any is "<top>".

    python3 scripts/check_main_thread.py         # exit 1 and a line per fault
    python3 scripts/check_main_thread.py --list  # every hit, allowed or not
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = "app/src/main"

HIT = re.compile(r"\brunBlocking\b|\bThread\.sleep\b|\bSystemClock\.sleep\b")
COMMIT = re.compile(r"\.commit\(\s*\)")
FUN = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*[(<]")

P = "app/src/main/java/com/innovation313/roshankhata/"
# (file, function) -> (most hits, why it is safe)
ALLOWED = {
    (P + "data/Businesses.kt", "delete"):
        (1, "delete() runs on IO (BusinessSwitchActivity launches it on Dispatchers.IO); runBlocking waits there"),
    (P + "data/Businesses.kt", "endSharing"):
        (1, "called from the IO delete/erase paths; waits for the relays' goodbyes with its own 20 s bound"),
    (P + "data/Businesses.kt", "leaveRelays"):
        (1, "same callers as endSharing; withTimeoutOrNull(5 s) inside"),
    (P + "data/PushWake.kt", "within"):
        (1, "runBlocking on the helper's own executor thread; the caller stops waiting at the deadline"),
    (P + "data/LiveRunner.kt", "start"):
        (1, "the news wait's check lambda: runs under runInterruptible on Dispatchers.IO, never on main"),
    (P + "ui/HudhudChirpPlayer.kt", "hold"):
        (1, "the bird call's worker executor holds the track open until it has played; never the main thread"),
    (P + "ui/ReceiptPrinter.kt", "send"):
        (2, "Bluetooth writes paced for a slow printer; documented and called only from the print thread on IO"),
}

# (file, function) -> (most commit() hits, why it is safe there)
COMMITS = {
    (P + "PaymentVoice.kt", "save"):
        (1, "a payment notice's one-time ticket must be on disk before its form opens (lesson 82); issued on the "
            "listener's Default thread, redeemed on IO (openTicket), cleared on AppScope (setOn) since 25 Nov 2026"),
    (P + "data/Backup.kt", "write"):
        (2, "the backup password and file protection: the next backup must find them; written in BackupActivity's "
            "withContext(Dispatchers.IO) and its result is read (a failure throws)"),
    (P + "data/BookRecovery.kt", "keepAside"):
        (1, "Room's corruption callback, on the thread that opened the damaged book; the note must survive the kill "
            "that often follows; rare"),
    (P + "data/BookRecovery.kt", "recoverIfNeeded"):
        (1, "only after a damaged book was set aside, inside KhataDatabase.get's rebuild (audit 5.3); rare"),
    (P + "data/Businesses.kt", "create"):
        (1, "the highest id ever given; callers on AppScope (BusinessSwitchActivity since 25 Nov 2026, BackupActivity)"),
    (P + "data/Businesses.kt", "switchTo"):
        (1, "the open shop; a killed process must wake in the shop it switched to. Screens call switchOffMain (IO, BC-246)"),
    (P + "data/Businesses.kt", "delete"):
        (1, "the highest id after a delete; delete runs on Dispatchers.IO"),
    (P + "data/Businesses.kt", "save"):
        (1, "the registry, the map to every book; from create, ensure, rename and delete, all off the main thread "
            "since 25 Nov 2026 (rename on AppScope)"),
    (P + "data/DriveBackup.kt", "deviceId"):
        (1, "made once per install, inside the Drive upload's withContext(Dispatchers.IO)"),
    (P + "data/SetupProgress.kt", "markStarted"):
        (1, "on the main thread, once per install: it must be on disk before the first language is saved, or an "
            "update would take this setup for an old one and skip it"),
    (P + "data/SetupProgress.kt", "markDone"):
        (1, "on the main thread, once per install (Start my khata / Maybe later); a lost 'done' sends the owner back "
            "through the setup"),
    (P + "data/Snapshots.kt", "refreshToday"):
        (1, "from afterSale (AppScope) and beforeLeaving (BusinessSwitchActivity's AppScope); the copy that follows "
            "reads the marker"),
    (P + "data/ViewerMode.kt", "switchOn"):
        (1, "ViewerSync.load on IO; the next line opens the database and must see the role"),
    (P + "data/ViewerMode.kt", "recordCopy"):
        (1, "ViewerSync.load on IO"),
    (P + "data/ViewerMode.kt", "leave"):
        (1, "ViewerSync (IO) and ViewerActivity.leaveNow (AppScope, since 25 Nov 2026)"),
    (P + "data/WarisAndroid.kt", "markHeirCopy"):
        (1, "inside openHeirCopy's withContext(Dispatchers.IO)"),
    (P + "ui/FeatureIntro.kt", "markAllSeen"):
        (1, "a test helper (screen proofs, store pictures); the app's own markSeen uses apply()"),
    (P + "ui/FeatureIntro.kt", "forget"):
        (1, "a test helper (ScreenProofPhoneTest)"),
}


def code_hits(path, pattern=HIT):
    """(line number, function, text) for each hit that is code: not a comment, an import, or a string."""
    out, fn, in_block = [], "<top>", False
    for n, raw in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        line = raw.strip()
        if in_block:
            if "*/" in line:
                in_block = False
            continue
        if line.startswith("/*"):
            if "*/" not in line:
                in_block = True
            continue
        if line.startswith(("//", "*", "import ")):
            continue
        code = re.sub(r'"(?:[^"\\]|\\.)*"', '""', raw)       # strings out
        code = code.split("//")[0]                           # trailing comment out
        m = FUN.search(code)
        if m:
            fn = m.group(1)
        if pattern.search(code):
            out.append((n, fn, line))
    return out


def no_blocking_waits(root=ROOT, listing=False):
    faults, seen = [], {}
    for path in sorted((root / SRC).rglob("*.kt")):
        rel = path.relative_to(root).as_posix()
        for n, fn, text in code_hits(path):
            key = (rel, fn)
            seen[key] = seen.get(key, 0) + 1
            if listing:
                print(("allowed " if key in ALLOWED else "REFUSED ") + f"{rel}:{n} [{fn}] {text}")
            if key not in ALLOWED:
                faults.append(f"{rel}:{n} [{fn}] blocks the thread it runs on (runBlocking / sleep): {text}")
            elif seen[key] > ALLOWED[key][0]:
                faults.append(f"{rel}:{n} [{fn}] a further blocking wait beyond the {ALLOWED[key][0]} allowed: {text}")
    return faults


def commits_listed(root=ROOT, listing=False):
    faults, seen = [], {}
    for path in sorted((root / SRC).rglob("*.kt")):
        rel = path.relative_to(root).as_posix()
        for n, fn, text in code_hits(path, COMMIT):
            key = (rel, fn)
            seen[key] = seen.get(key, 0) + 1
            if listing:
                print(("allowed " if key in COMMITS else "REFUSED ") + f"{rel}:{n} [{fn}] {text}")
            if key not in COMMITS:
                faults.append(f"{rel}:{n} [{fn}] commit() waits for the disk on its thread; apply(), move it off the "
                              f"main thread, or list it in COMMITS with its reason: {text}")
            elif seen[key] > COMMITS[key][0]:
                faults.append(f"{rel}:{n} [{fn}] a further commit() beyond the {COMMITS[key][0]} allowed: {text}")
    return faults


def main():
    listing = "--list" in sys.argv
    faults = no_blocking_waits(listing=listing) + commits_listed(listing=listing)
    for f in faults:
        print("FAIL " + f)
    if faults:
        sys.exit(1)
    print("main thread: every runBlocking, sleep and commit() in app/src/main is an allowed one, with its reason")


if __name__ == "__main__":
    main()
