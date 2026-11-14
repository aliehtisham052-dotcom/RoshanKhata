#!/usr/bin/env python3
"""Hudhud only READS. This check fails when any of his files calls a write.

The owner's rule (14 Nov 2026): Hudhud brings the information and puts the
thing in front of him; saving, sending, deleting or changing is the owner's
own tap, on the screen that already has its checks (PIN gates, "are you sure",
the form's own validation). Hudhud never writes a row, never sends a message
and never changes a setting of the book on his own.

What is enforced, in every Kotlin file whose name starts with "Hudhud" (the
phone's and the shared module's), comments and strings left out:

  1. No call to a write method of KhataDao. A write is the DAO's own rule
     (scripts/gen_viewer_dao.py: @Insert/@Update/@Delete/@Upsert, a @Query
     that starts with UPDATE/DELETE/INSERT/REPLACE, or a default method that
     calls one, followed through).
  2. No raw SQL write: execSQL, SyncSql writes, openHelper.writableDatabase,
     runInTransaction, beginTransaction.
  3. No send: SmsManager, sendTextMessage, sendBroadcast of anything but the
     app's own, startActivity of ACTION_SEND / ACTION_SENDTO / wa.me /
     whatsapp: links, and no direct HTTP (HttpURLConnection, OkHttp): the
     voice and the book never leave the phone through him.

A real exception is written down below, by file, with its reason, and is read
in review: it is never a way to let him save. Run from the repository root:

    python3 scripts/check_hudhud_readonly.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# What the rule allows, by file and name, with the reason. Empty is the goal.
ALLOWED = {
    # The owner's own check of a test build: his tap on Share hands the list of his questions and Hudhud's answers
    # to Android's share sheet, and HE picks where it goes. Nothing is sent without that tap; nothing of the book.
    ("HudhudCheck.kt", "sending something to another app"):
        "More -> Hudhud's voice -> Share: the owner's own tap, through the share sheet",
}

# Names that are the DAO's and also a maths function's: a call on one of these receivers is the maths.
MATH_RECEIVERS = ("StrictMath.", "Math.", "kotlin.math.")

SEND_AND_RAW = [
    (r"\bexecSQL\s*\(", "raw SQL write"),
    (r"\bwritableDatabase\b", "writable database"),
    (r"\brunInTransaction\s*\(", "a transaction of his own"),
    (r"\bbeginTransaction\s*\(", "a transaction of his own"),
    (r"\bSyncSql\b", "the sync door's raw SQL"),
    (r"\bSmsManager\b", "sending a text"),
    (r"\bsendTextMessage\s*\(", "sending a text"),
    (r"\bACTION_SEND(?:TO|_MULTIPLE)?\b", "sending something to another app"),
    (r"wa\.me/", "a WhatsApp link"),
    (r"whatsapp://", "a WhatsApp link"),
    (r"\bHttpURLConnection\b", "his own network call"),
    (r"\bOkHttpClient\b", "his own network call"),
    (r"\bHttpClient\b", "his own network call"),
]


def dao_writes():
    """The DAO's own write rule, loaded from gen_viewer_dao.py without running its generation."""
    text = (ROOT / "scripts/gen_viewer_dao.py").read_text()
    cut = text.index("_all, _names = write_names(src)")
    ns = {"__name__": "gen_viewer_dao_rules", "__file__": str(ROOT / "scripts/gen_viewer_dao.py")}
    exec(compile(text[:cut], "gen_viewer_dao.py", "exec"), ns)
    _, writes = ns["write_names"](ns["src"])
    return writes


def code_only(text):
    """The file with comments and string literals blanked, line numbers kept."""
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(re.sub(r"[^\n]", " ", text[i:j]))
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(re.sub(r"[^\n]", " ", text[i:j]))
            i = j
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(re.sub(r"[^\n]", " ", text[i:j]))
            i = j
        elif text[i] == "'" and i + 2 < n and (text[i + 2] == "'" or text[i + 1] == "\\"):
            j = text.find("'", i + 2 if text[i + 1] != "\\" else i + 3)
            j = i + 1 if j < 0 else j + 1
            out.append(" " * (j - i))
            i = j
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


# Files that are Hudhud's though their names do not say so: what he counts and how he hears a figure question.
ALSO = ("UsageLog.kt", "VoiceQuestion.kt")


def hudhud_files():
    found = []
    for base in ("app/src/main", "shared/src/jvmSharedMain", "shared/src/androidMain", "shared/src/desktopMain"):
        found += sorted((ROOT / base).rglob("Hudhud*.kt"))
        for name in ALSO:
            found += sorted((ROOT / base).rglob(name))
    return found


def main():
    writes = dao_writes()
    if len(writes) < 50:
        sys.exit(f"only {len(writes)} DAO writes found: the rule in gen_viewer_dao.py has changed, read it")
    problems = []
    files = hudhud_files()
    if len(files) < 15:
        sys.exit(f"only {len(files)} Hudhud files found: the folders moved, update this check")
    for path in files:
        rel = path.relative_to(ROOT)
        code = code_only(path.read_text())
        lines = code.split("\n")
        for name in sorted(writes):
            pat = re.compile(r"(?<![\w.])(?:\w+\.)*" + re.escape(name) + r"\s*\(")
            for no, line in enumerate(lines, 1):
                if pat.search(line) and re.search(r"[.\s(]" + re.escape(name) + r"\s*\(", " " + line):
                    # a function Hudhud DEFINES with the same name is not a call to the DAO
                    if re.search(r"\bfun\s+(?:\w+\.)?" + re.escape(name) + r"\s*\(", line):
                        continue
                    if (path.name, name) in ALLOWED:
                        continue
                    if any(re.search(re.escape(m) + re.escape(name) + r"\s*\(", line) for m in MATH_RECEIVERS):
                        continue
                    problems.append(f"{rel}:{no}: calls the DAO write {name}()")
        for pat, why in SEND_AND_RAW:
            for no, line in enumerate(lines, 1):
                if re.search(pat, line) and (path.name, why) not in ALLOWED:
                    problems.append(f"{rel}:{no}: {why}")
    if problems:
        print("Hudhud may only read. These lines write, send or reach out:")
        for p in problems:
            print("  " + p)
        print("\nHe brings the answer and opens the screen; the owner's own tap saves. "
              "See scripts/check_hudhud_readonly.py.")
        sys.exit(1)
    print(f"{len(files)} Hudhud files checked against {len(writes)} DAO writes: none writes, sends or reaches out")


if __name__ == "__main__":
    main()
