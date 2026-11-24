#!/usr/bin/env python3
"""Nothing in app/src/main waits on the main thread's behalf with runBlocking or a sleep (24 Nov 2026, the ANR audit).

  no_blocking_waits (BC-NEW): `runBlocking`, `Thread.sleep` and `SystemClock.sleep` in app/src/main
      are refused unless the file:function is in ALLOWED below, each with the reason it is safe and
      the most hits it may hold. A call that blocks the thread it runs on freezes the screen when that
      thread is the main one (Play Console's ANR: input not answered for 5 s). The justified ones all
      run off the main thread: on Dispatchers.IO, on a thread of their own, or on a worker executor.
      A new one must either go off the main thread without blocking (suspend, withContext) or be
      added here with its reason, so a person reads it once.

  A comment line, an import line and a string that only mentions the word are not hits. The function is
  the nearest `fun` above the hit; a hit before any is "<top>".

    python3 scripts/check_main_thread.py         # exit 1 and a line per fault
    python3 scripts/check_main_thread.py --list  # every hit, allowed or not
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = "app/src/main"

HIT = re.compile(r"\brunBlocking\b|\bThread\.sleep\b|\bSystemClock\.sleep\b")
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


def code_hits(path):
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
        if HIT.search(code):
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


def main():
    listing = "--list" in sys.argv
    faults = no_blocking_waits(listing=listing)
    for f in faults:
        print("FAIL " + f)
    if faults:
        sys.exit(1)
    print("main thread: every runBlocking and sleep in app/src/main is an allowed one, off the main thread")


if __name__ == "__main__":
    main()
