#!/usr/bin/env python3
"""Every DAO write, held to a gate and an audit row: a REPORT, not a proof (L008).

The question: when a write of KhataDao runs, did somebody check who may make it
(Access.requireAllowed in the DAO, AccessUi.* on the phone, Gates.* on the
computer, a screen gated by AccessUi's SCREENS table) or does it leave a row in
Who did what (insertAudit / log / audit / AuditAction / *Audited)?

How it reads (by names, as gen_viewer_dao.py and check_hudhud_readonly.py do):
  1. the writes are gen_viewer_dao.py's own rule (check_hudhud_readonly.dao_writes);
  2. a write whose own DAO body checks Access.requireAllowed or writes an audit row is
     covered wherever it is called (followed through other DAO methods) -- "DAO";
  3. every call of a write outside KhataDao is judged ON ITS PATH: the open blocks' heads
     around it and the statements before it in its function (a gate around a sibling block
     is not a gate around this call), plus the rest of its own block for an audit row written
     just after; a screen in AccessUi's SCREENS table is gated as a whole, and a Compose state
     flag set inside a gate's block (`Gates.edit { bin = true }`) gates code under `if (bin)`;
     with nothing on the path, the callers of the function are judged the same way, up to
     DEPTH levels, by name (a name defined in two files only inside its own file);
  4. a write never called outside the DAO is INTERNAL (reached only through another write);
  5. EXEMPT names writes with their reason, EXEMPT_CALLERS whole callers (their one entry is
     checked, so they cannot quietly grow); what is left is UNCOVERED.

What it cannot see (lesson 125): a write in a callback registered elsewhere (an activity
result launcher, a menu's dispatch, a lambda kept in state and run by a dialog), a computer
page gated by its rail Section, and whether a gate is the RIGHT one or an audit row the right
action. COVERED means "some gate or audit row is on a path", never "correctly gated" -- and
a write may be gated without being audited (only `--why=` shows which).

Not a Strings step (16 Nov 2026): 18 writes stay UNCOVERED, and most of them are real choices
for the owner, not drift -- the phone's Cheques, Plans and Schemes pages write with no gate
where the computer asks Gates.edit; a cheque cleared or a plan payment writes a khata row
with no audit row on either platform; adding a customer, product or bill leaves no row by
design (anyone at the counter may). Exempting them to make CI green would hide exactly what
this report exists to show. Once those are decided and fixed, `--check` can become the step.

    python3 scripts/check_dao_gates.py            # the report, exit 0
    python3 scripts/check_dao_gates.py --check    # exit 1 on an UNCOVERED write
    python3 scripts/check_dao_gates.py --list     # every write with its verdict
    python3 scripts/check_dao_gates.py --why=clearCheque   # each call site, gate/audit, and why
"""
import os, re, sys, pathlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_hudhud_readonly as ro  # noqa: E402  (its dao_writes and code_only)

ROOT = pathlib.Path(__file__).resolve().parent.parent
DAO = ROOT / "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/KhataDao.kt"
ACCESS_UI = ROOT / "app/src/main/java/com/innovation313/roshankhata/ui/AccessUi.kt"
SOURCES = ["app/src/main", "desktop/src/main", "shared/src/jvmSharedMain",
           "shared/src/androidMain", "shared/src/desktopMain"]
SKIP = {"KhataDao.kt", "ViewerDao.kt"}
DEPTH = 3
# called by the platform, never followed up to "callers"
FRAMEWORK = {"onCreate", "onResume", "onStart", "onStop", "onPause", "onDestroy", "onViewCreated",
             "onCreateView", "onActivityResult", "onRequestPermissionsResult", "onNewIntent", "onClick",
             "onBindViewHolder", "onReceive", "doWork", "invoke", "main", "onSaveInstanceState"}

GATE = re.compile(r"\bAccess\.requireAllowed\b|\bAccessUi\.\w+|\bGates\.\w+|\bneedExport\b"
                  r"|\bAccess\.can\s*\(|\bAccess\.ownerOnly\b|\bownerFirst\b|\brequireOwnerPin\b")
AUDIT = re.compile(r"\binsertAudit\s*\(|\binsertAuditRow\s*\(|\bAuditAction\.|\bAuditActs\.|\.audit\s*\("
                   r"|\bdao\w*\.log\s*\(|\bAudited\s*\(|\w+Audited\s*\(")

# Writes left without a gate or an audit row ON PURPOSE, each with its reason. A write goes here
# only after its callers were read; the reason says why no shopkeeper has to answer for it.
EXEMPT = {
    "setSetting": "book_settings plumbing written by features themselves (live sync marks, Waris keys, "
                  "customer-link counters, seal switch's own audited path); the settings a person changes "
                  "are gated at their screens (SETTINGS/BACKUP) — the key, not the write, decides",
    "insertSeal": "the daily seal (Kitab ki Muhar) written by the app itself from the daily copy, never by a "
                  "person; its switch is audited (seal_on/seal_off)",
    "purgeOldEntries": "the bin's own 30-day sweep, run on opening by the app (both platforms), not a person",
    "purgeBinKinds": "the bin's own 30-day sweep of cashbook, cheques and bills (since 17 Nov 2026 the only caller), not a person",
    "purgeOldParties": "the bin's own 30-day sweep, run on opening by the app, not a person",
    "setCashPhotoPath": "a cashbook row's picture: no money, never synced; the owner decided 17 Nov 2026 that a "
                        "picture asks no gate",
    "setChequePhotoPath": "a cheque's picture: the same rule (owner, 17 Nov 2026)",
    "setPartyPhotoPath": "a customer's face: the same rule (owner, 17 Nov 2026); one column, so no other field of "
                         "the row is written back from an old copy",
    "setPartyQrToken": "a customer's card token, made once when the card is first drawn; no money, nobody's word",
    "linkSaleInvoice": "SaleInvoice.of: a posted ticket shown as an A4 invoice; the ticket itself was gated and "
                       "audited by postSale",
}

# Whole callers whose writes are judged where the caller is entered. Each is (the file stem,
# the one call that enters it, the files that may make that call, the reason). The entry is
# checked: a new caller of it elsewhere fails, so the exemption cannot quietly grow (tests may
# call anything: they are not shipped).
EXEMPT_CALLERS = [
    ("BookMerge", r"\bBookMerge\.apply\s*\(", {"KhataDao.kt"},
     "runs only inside KhataDao.mergeBook (one transaction, audited BOOK_MERGED), reached from the restore "
     "question behind BACKUP"),
    ("BigBookSeed", r"\bBigBookSeed\.(?:seed|clear)\s*\(", {"BigBook.kt"},
     "the speed measurement's seed: the phone reaches it only through About's DEV_TOOLS door (no door in a Play "
     "build), refused for a book that syncs, every row marked and cleared"),
    ("BigBookProbe", r"\bBigBookProbe\.\w+\s*\(", set(),
     "the speed measurement's probe, called from tests only"),
    ("BackupImages", r"\bBackupImages\.\w+\s*\(", {"DriveBackup.kt", "BackupActivity.kt", "ViewerSync.kt",
                                                     "ViewerActivity.kt"},
     "re-points photo paths after a restore, a helper copy or a picture archive (Backup and the helper's-phone "
     "screen, both BACKUP in AccessUi's SCREENS, and the Drive worker); the rows came in through that restore"),
]

def kt_files():
    for base in SOURCES:
        for p in sorted((ROOT / base).rglob("*.kt")):
            if p.name != "ViewerDao.kt":
                yield p


def functions(code):
    """[(name, start, end)] for every `fun name(...)` with a block or `=` body, in blanked code."""
    out, n = [], len(code)
    for m in re.finditer(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(", code):
        i, d = m.end(), 1
        while i < n and d:
            d += (code[i] == "(") - (code[i] == ")")
            i += 1
        # past an optional return type: the first `{` or `=` outside (), <> and [] starts the body;
        # a newline-then-declaration or `}` first means no body (an abstract interface method)
        j, depth = i, 0
        while j < n:
            ch = code[j]
            if ch == "-" and code[j + 1:j + 2] == ">":
                j += 2
                continue
            if ch in "(<[":
                depth += 1
            elif ch in ")>]" and depth:
                depth -= 1
            elif depth == 0 and ch in "{=}":
                break
            elif depth == 0 and ch == "\n" and re.match(r"\s*(?:@|fun\b|val\b|var\b|override\b|suspend\b|private\b|"
                                                        r"internal\b|public\b|class\b|object\b|$)", code[j + 1:j + 40]) \
                    and not re.match(r"\s*[:{=]", code[j + 1:j + 40]):
                ch = None
                break
            j += 1
        if j >= n or ch is None or ch not in "{=":
            continue
        if ch == "{":
            d, e = 1, j + 1
            while e < n and d:
                d += (code[e] == "{") - (code[e] == "}")
                e += 1
            out.append((m.group(1), m.start(), e))
            continue
        # an expression body: it runs to the first line that comes back to the fun's own
        # indent (a closing bracket at that indent still belongs to it) or below it
        line_start = code.rfind("\n", 0, m.start()) + 1
        indent = len(code[line_start:m.start()]) - len(code[line_start:m.start()].lstrip(" "))
        e = code.find("\n", j)
        while 0 <= e < n:
            nl = code.find("\n", e + 1)
            line = code[e + 1:nl if nl >= 0 else n]
            body = line.lstrip(" ")
            ind = len(line) - len(body)
            if body and ind < indent:
                break
            if body and ind == indent:
                if body[0] in ")]}":
                    e = nl if nl >= 0 else n
                break
            if nl < 0:
                e = n
                break
            e = nl
        out.append((m.group(1), m.start(), e if e >= 0 else n))
    return out


def dao_covered(writes):
    """Writes whose DAO body checks a gate or writes an audit row, followed through DAO calls."""
    text = DAO.read_text()
    code = ro.code_only(text)
    funcs = functions(code)
    body = {}
    for name, s, e in funcs:
        body.setdefault(name, code[s:e])
    covered = {n for n, b in body.items() if GATE.search(b) or AUDIT.search(b)}
    # insertAudit / log / insertAuditRow ARE the audit row
    covered |= {"insertAudit", "insertAuditRow", "log"} & writes
    changed = True
    while changed:
        changed = False
        for n, b in body.items():
            if n in covered:
                continue
            if any(re.search(r"\b" + re.escape(c) + r"\s*\(", b) for c in covered
                   if c in {"insertAudit", "insertAuditRow", "log"}):
                covered.add(n)
                changed = True
    return covered & writes, body


def screen_gated():
    """Android screens gated as a whole (AccessUi's SCREENS table)."""
    try:
        text = ACCESS_UI.read_text()
    except OSError:
        return set()
    m = re.search(r"SCREENS\s*=\s*mapOf\((.*?)\n\s*\)", text, re.S)
    return set(re.findall(r'"(\w+Activity)"\s*to', m.group(1))) if m else set()


def on_the_path(code, start, pos):
    """The code of a function from its start to `pos` that can run before `pos`: every block
    already closed before `pos` is taken out with its statement's head (a gate around a sibling
    is not a gate around this call), so what stays is the open blocks' heads and the statements
    before the call in each. And the rest of the innermost open block, where an audit row
    written just after the write sits."""
    out, stack = [], []
    for i in range(start, pos):
        ch = code[i]
        if ch == "{":
            stack.append(len(out))
        elif ch == "}" and stack:
            b = min(stack.pop(), len(out))
            # back from the `{` over its head to where the statement began (never past the open block)
            floor = min(stack[-1] + 1, b) if stack else 0
            k, depth = b, 0
            while k > floor:
                c = out[k - 1]
                if c == ")":
                    depth += 1
                elif c == "(":
                    depth -= 1
                elif c in "\n;{}" and depth <= 0:
                    break
                k -= 1
            del out[k:]
            continue
        out.append(ch)
    before = "".join(out)
    # the rest of the innermost open block
    d, e, n = 0, pos, len(code)
    while e < n:
        if code[e] == "{":
            d += 1
        elif code[e] == "}":
            if d == 0:
                break
            d -= 1
        e += 1
    return before, code[pos:e]


# The ratchet (BC-194, BC-223; 17 Nov 2026): the writes that still have a bare path, each frozen at its
# count of bare call sites. They wait for the owner's decision (who may add a customer, product, bill or
# plan; whether a photo or the plan's next day needs a gate), so they are not exempted. `--ratchet` fails
# on any OTHER uncovered write, on a new bare path of one of these, and on one that became covered and
# was not taken out -- so gates only ever tighten. A Strings step.
WAITING = {
    "linkGoodsToProducts": (1, "the tie button: the owner's call"),
}


def main():
    writes = ro.dao_writes()
    in_dao, _ = dao_covered(writes)
    gated_screens = screen_gated()

    files = []          # (path, code, funcs sorted by start, [(start, end)])
    calls = {}          # name -> [(file index, position)] of `name(` / `name {` not after `fun`
    for p in kt_files():
        code = ro.code_only(p.read_text())
        funcs = sorted(functions(code), key=lambda f: f[1])
        k = len(files)
        files.append((p, code, funcs))
        for m in re.finditer(r"(?<![\w])(\w+)\s*[({]", code):
            if re.search(r"\bfun\s*(?:<[^>]*>\s*)?(?:[\w.]+\.)?$", code[max(0, m.start() - 40):m.start()]):
                continue
            calls.setdefault(m.group(1), []).append((k, m.start()))

    # Compose's way: a gate sets a state flag (`Gates.edit { bin = true }`) and the dialog under
    # `if (bin)` writes later. A flag set inside a gate's block is a gate for code under it.
    flag_set = re.compile(r"(?:\bGates\.\w+|\bAccessUi\.\w+)\s*(?:\([^{}]*?\))?\s*(?:\w+@)?\s*\{\s*(\w+)\s*=(?!=)")
    gate_flags = []
    for _, code, _ in files:
        names = set(flag_set.findall(code))
        gate_flags.append(re.compile(r"\bif\s*\([^)]*\b(?:%s)\b|\b(?:%s)\s*\?\.let\b" % ("|".join(names), "|".join(names)))
                          if names else None)

    defined_in = {}
    for k, (_, _, funcs) in enumerate(files):
        for f in funcs:
            defined_in.setdefault(f[0], set()).add(k)

    def enclosing_at(k, pos):
        best = None
        for f in files[k][2]:
            if f[1] > pos:
                break
            if pos < f[2]:
                best = f
        return best

    ev_cache = {}

    def evidence(k, f, pos):
        key = (k, pos)
        if key not in ev_cache:
            before, after = on_the_path(files[k][1], f[1], pos)
            flags = gate_flags[k]
            ev_cache[key] = (bool(GATE.search(before) or (flags and flags.search(before))),
                             bool(AUDIT.search(before) or AUDIT.search(after)))
        return ev_cache[key]

    climb_cache = {}

    def climb(name, home, depth, seen):
        """Gate/audit evidence in the callers of function `name` (defined in file `home`), up to depth
        levels. A name defined in more than one file is followed only inside its own file: a call of
        `save(` elsewhere is somebody else's `save`."""
        if depth == 0 or name in seen or len(name) < 4 or name in FRAMEWORK:
            return False, False, None
        key = (name, home if len(defined_in.get(name, ())) > 1 else None, depth)
        if key in climb_cache:
            return climb_cache[key]
        climb_cache[key] = (False, False, None)   # a cycle reads as nothing found
        seen = seen | {name}
        g = a = False
        why = None
        for k, pos in calls.get(name, []):
            if key[1] is not None and k != home:
                continue
            p = files[k][0]
            if p.stem in gated_screens:
                g, why = True, why or "screen " + p.stem
                continue
            f = enclosing_at(k, pos)
            if f is None:
                continue
            eg, ea = evidence(k, f, pos)
            if eg or ea:
                g, a = g or eg, a or ea
                why = why or "%s.%s" % (p.stem, f[0])
            else:
                cg, ca, cw = climb(f[0], k, depth - 1, seen)
                g, a = g or cg, a or ca
                why = why or cw
        climb_cache[key] = (g, a, why)
        return g, a, why

    exempt_stems = {stem for stem, _, _, _ in EXEMPT_CALLERS}
    caller_problems = []
    every = [q for base in SOURCES + ["app/src/test", "app/src/androidTest", "desktop/src/test",
                                       "shared/src/desktopTest", "shared/src/jvmSharedTest"]
             for q in sorted((ROOT / base).rglob("*.kt"))]
    for stem, entry, allowed, _ in EXEMPT_CALLERS:
        found_stem = any(f[0].stem == stem for f in files)
        if not found_stem:
            caller_problems.append("EXEMPT_CALLERS names a file that is gone: " + stem)
        for q in every:
            if q.stem == stem or "test" in q.relative_to(ROOT).parts[2].lower():
                continue
            if re.search(entry, ro.code_only(q.read_text())) and q.name not in allowed:
                caller_problems.append("%s is entered from %s, which EXEMPT_CALLERS does not allow"
                                       % (stem, q.relative_to(ROOT)))

    report = {}
    for w in sorted(writes):
        sites = []
        for k, pos in calls.get(w, []):
            p, code, _ = files[k]
            if p.name in SKIP:
                continue   # a DAO method calling another: judged by the outer write
            if code[pos + len(w):].lstrip()[:1] != "(":
                continue
            before = code[max(0, pos - 12):pos]
            if w == "log" and not re.search(r"dao\w*\.\s*$|\)\.\s*$", before):
                continue
            where = "%s:%d" % (p.relative_to(ROOT), code.count("\n", 0, pos) + 1)
            if p.stem in exempt_stems:
                sites.append((where, True, True, "exempt caller " + p.stem))
                continue
            if p.stem in gated_screens:
                sites.append((where, True, False, "screen " + p.stem))
                continue
            f = enclosing_at(k, pos)
            if f is None:
                sites.append((where, False, False, None))
                continue
            g, a = evidence(k, f, pos)
            why = "%s.%s" % (p.stem, f[0]) if (g or a) else None
            if not (g or a):
                g, a, why = climb(f[0], k, DEPTH, frozenset())
            sites.append((where, g, a, why))
        if w in in_dao:
            verdict = "DAO"
        elif not sites:
            verdict = "INTERNAL"
        elif all(g or a for _, g, a, _ in sites):
            verdict = "COVERED"
        elif w in EXEMPT:
            verdict = "EXEMPT"
        else:
            verdict = "UNCOVERED"
        report[w] = (verdict, sites)

    counts = {}
    for v, _ in report.values():
        counts[v] = counts.get(v, 0) + 1
    listing = "--list" in sys.argv
    for w, (v, sites) in sorted(report.items(), key=lambda kv: (kv[1][0], kv[0])):
        if v == "UNCOVERED" or listing:
            bare = [s for s, g, a, _ in sites if not (g or a)]
            reason = ("  [" + EXEMPT[w] + "]") if v == "EXEMPT" else ""
            print("%-9s %-30s %d call(s)%s%s" % (v, w, len(sites), reason,
                  ("; bare: " + ", ".join(bare[:4]) + (" ..." if len(bare) > 4 else "")) if bare else ""))
    for w in [a.split("=", 1)[1] for a in sys.argv if a.startswith("--why=")]:
        v, sites = report.get(w, ("?", []))
        print("%s %s" % (v, w))
        for where, g, a, why in sites:
            print("   %-70s gate=%-5s audit=%-5s %s" % (where, g, a, why or ""))
    stale = sorted(set(EXEMPT) - set(writes))
    for s_ in stale:
        print("EXEMPT lists a write that is gone: " + s_)
    for c in caller_problems:
        print(c)
    stale = stale + caller_problems
    print("dao gates: %d writes — %s" % (len(writes), ", ".join("%s %d" % kv for kv in sorted(counts.items()))))
    if "--check" in sys.argv and (counts.get("UNCOVERED") or stale):
        sys.exit(1)
    if "--ratchet" in sys.argv:
        bad = list(stale)
        for w, (v, sites) in sorted(report.items()):
            bare = sum(1 for _, g, a, _ in sites if not (g or a))
            if v != "UNCOVERED":
                if w in WAITING:
                    bad.append("%s is covered now: take it out of WAITING so it cannot slip back" % w)
                continue
            if w not in WAITING:
                bad.append("%s writes with no gate and no audit row on %d path(s): gate it, audit it, or "
                           "ask the owner and add it to WAITING with the reason" % (w, bare))
            elif bare > WAITING[w][0]:
                bad.append("%s has %d bare call(s), WAITING allows %d: a new ungated path" % (w, bare, WAITING[w][0]))
        for w in sorted(set(WAITING) - set(report)):
            bad.append("WAITING lists a write that is gone: " + w)
        for b in bad:
            print("RATCHET: " + b)
        if bad:
            sys.exit(1)
        print("dao gates ratchet: no new ungated write (%d wait for the owner's decision)" % len(WAITING))


if __name__ == "__main__":
    main()
