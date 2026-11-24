#!/usr/bin/env python3
"""Every screen, tile, menu entry and printed document is in docs/capability-matrix.tsv, with its proof (plan B8, 24 Nov 2026).

docs/capability-matrix.tsv has one row per capability: kind (phone-screen, phone-tile, phone-more, desktop-section,
desktop-more, desktop-window, document), platform, subject, gate (the permission its own screen asks for), trade (the
TradeFeatures switch that limits it), the languages it has and the ones a test renders it in, and the PROOF: a test class or
a screen-proof picture that holds it (`path#needle`; NONE is allowed and counted). The check fails when:
  1. a screen the code has is not in the matrix: every <activity> of the phone's manifest, every `Section` of the Windows
     rail, every Home tile and More entry of shared/hudhud/catalog/catalog.tsv, every Windows More-menu line in Main.kt, and
     every file that makes a phone PDF (`PdfDocument(`) or a Windows printable sheet (`Printable`, `PrinterJob`) is a row's
     subject or source; and a row whose subject is no longer in the code fails the other way;
  2. a row's proof or source names a file that does not exist, or a `path#needle` whose needle is not in the file
     (the screen-proof picture names, the test's `open<Screen>` calls);
  3. a gate is not the one the code asks (AccessUi.SCREENS for the phone, `Section.need()` for the rail), a gate or trade
     word is not a real permission or TradeFeatures switch, or the Windows trade filter (`TradeKit.shows`) disagrees;
  4. a column holds a word outside its list, or strength says NONE while a proof is named (or the reverse);
  5. the number of rows with NO proof grows past NONE_CEILING (a new screen with no test must say so by raising it).
Counts are printed (`--list` names every NONE). Labels: basis VERIFIED means the gate and proof were read in the code;
INFERRED means they were worked out from the screen's purpose. Drift protection, not proof: it holds the list to the code,
it cannot say that a named test really checks the screen it names (lesson 125). Run from the repository root.
"""
import os, re, sys

TSV = "docs/capability-matrix.tsv"
COLS = ["kind", "platform", "subject", "label", "gate", "trade", "strings", "rendered", "strength", "proof", "source",
        "basis", "note"]
KINDS = ["phone-screen", "phone-tile", "phone-more", "desktop-section", "desktop-more", "desktop-window", "document"]
PLATFORMS = {"phone", "windows", "both"}
STRENGTHS = {"NONE", "OPENS", "OPENS+PICTURE", "PICTURE", "UNIT", "UNIT+PICTURE", "PARTIAL", "REFERENCED"}
BASES = {"VERIFIED", "INFERRED"}
LANGS = {"en", "ur", "ur-Latn", "sd", "fa", "ar", "hi", "bn", "in", "none", "designs-only"}
SPECIAL_GATES = {"NONE", "OWNER_PIN"}

# the number of rows that say "no proof yet"; lower it as proofs are written, never raise it without a reason in the commit
NONE_CEILING = 33

MANIFEST = "app/src/main/AndroidManifest.xml"
ACCESS_UI = "app/src/main/java/com/innovation313/roshankhata/ui/AccessUi.kt"
TRADE_FEATURES = "app/src/main/java/com/innovation313/roshankhata/data/TradeFeatures.kt"
PANES = "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/Panes.kt"
GATE = "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/Gate.kt"
TRADEKIT = "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/TradeKit.kt"
MAIN = "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/Main.kt"
CATALOG = "shared/hudhud/catalog/catalog.tsv"
ACCESS = "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/Access.kt"
EVERY_SCREEN = "app/src/androidTest/java/com/innovation313/roshankhata/EveryScreenOpensTest.kt"
PHONE_ROOT = "app/src/main"
DESKTOP_ROOT = "desktop/src/main"

# files that draw a phone or Windows sheet but are not a document of their own, with the reason
PRODUCER_EXEMPT = {
    "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/PhoneSheet.kt": "the engine under every Windows document",
    "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/Documents.kt": "builds the credit/debit-note sheets of the invoice designs",
    "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/PdfPreview.kt": "the preview window every Windows PDF opens in",
}


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def files_under(root, exts):
    for d, _, names in os.walk(root):
        for n in names:
            if n.endswith(exts):
                yield os.path.join(d, n)


def main():
    list_none = "--list" in sys.argv
    errors = []
    if not os.path.exists(TSV):
        print("MISSING", TSV)
        return 1
    lines = [l for l in read(TSV).split("\n") if l.strip()]
    if lines[0].split("\t") != COLS:
        print("HEADER differs from", COLS)
        return 1
    rows = []
    for n, l in enumerate(lines[1:], 2):
        c = l.rstrip("\n").split("\t")
        if len(c) != len(COLS):
            errors.append(f"{TSV}:{n}: {len(c)} columns, expected {len(COLS)}")
            continue
        r = dict(zip(COLS, c))
        r["line"] = n
        rows.append(r)

    perms = set(re.findall(r"\b([A-Z_]{3,})\b", re.search(r"enum class Permission \{(.*?)\n    \}", read(ACCESS), re.S).group(1)))
    perms = {p for p in perms if p.isupper()}
    trade_switches = set(re.findall(r"fun (\w+)\(context: Context\)", read(TRADE_FEATURES)))

    # ---- discovery from the code
    manifest_acts = set(re.findall(r'<activity[^>]*?android:name="\.?([A-Za-z0-9_.]+)"', read(MANIFEST), re.S))
    access_map = dict(re.findall(r'"(\w+Activity)" to Access\.Permission\.(\w+)', read(ACCESS_UI)))

    panes = read(PANES)
    body = panes[panes.index("enum class Section("):]
    sections = []
    for line in body.split("\n")[1:]:
        if re.match(r"^\s*;", line) or line.startswith("}"):
            break
        m = re.match(r"^\s{4}([A-Z][A-Z_0-9]*)\(", line)
        if m:
            sections.append(m.group(1))
    need = {}
    gate_src = read(GATE)
    nb = gate_src[gate_src.index("fun Section.need()"):]
    nb = nb[: nb.index("else -> null")]
    for grp, perm in re.findall(r"((?:Section\.\w+,?\s*(?://[^\n]*\n\s*)?)+)->\s*Access\.Permission\.(\w+)", nb):
        for s in re.findall(r"Section\.(\w+)", grp):
            need[s] = perm
    tk = read(TRADEKIT)
    sh = tk[tk.index("fun shows(s: Section)"):]
    sh = sh[: sh.index("}\n")]
    desk_trade = {s: t for s, t in re.findall(r"Section\.(\w+)\s*->\s*(\w+)", sh)}

    cat = [l.split("\t") for l in read(CATALOG).splitlines() if l and not l.startswith("#")]
    tiles = {c[1] for c in cat if c[0] == "tile"}
    mores = {c[1] for c in cat if c[0] == "more"}
    main_src = read(MAIN)
    menu_lines = []
    for ln in main_src.split("\n"):
        if "MenuLine(" in ln and ", null)" not in ln:
            ks = re.findall(r"word\(W\.(\w+)", ln)
            if ks:
                menu_lines.append(ks)

    producers = set()
    for p in files_under(PHONE_ROOT, (".kt",)):
        if "PdfDocument(" in read(p):
            producers.add(p)
    for p in files_under(DESKTOP_ROOT, (".kt",)):
        t = read(p)
        if re.search(r"PrinterJob|: Printable|\) : java\.awt\.print\.Printable|PDDocument\(", t):
            producers.add(p)

    by_kind = {k: [r for r in rows if r["kind"] == k] for k in KINDS}
    subj = {k: {r["subject"] for r in by_kind[k]} for k in KINDS}

    def compare(kind, code, what):
        for s in sorted(code - subj[kind]):
            errors.append(f"{what} {s} is in the code but has no {kind} row in {TSV}")
        for s in sorted(subj[kind] - code):
            errors.append(f"{kind} row {s} is no longer in the code ({what})")

    compare("phone-screen", manifest_acts, "activity")
    compare("desktop-section", set(sections), "Section")
    compare("phone-tile", tiles, "Home tile (catalog.tsv)")
    compare("phone-more", mores, "More entry (catalog.tsv)")
    for ks in menu_lines:
        if not any(k in subj["desktop-more"] for k in ks):
            errors.append(f"Windows More-menu line {ks} has no desktop-more row")
    allkeys = {k for ks in menu_lines for k in ks}
    for s in sorted(subj["desktop-more"] - allkeys):
        errors.append(f"desktop-more row {s} is no longer a More-menu line in Main.kt")
    doc_sources = set()
    for r in by_kind["document"]:
        doc_sources.update(x.strip() for x in r["source"].split(";") if x.strip())
    for p in sorted(producers):
        if p not in doc_sources and p not in PRODUCER_EXEMPT:
            errors.append(f"{p} makes a printed sheet but no document row names it as a source")
    for p in PRODUCER_EXEMPT:
        if p not in producers:
            errors.append(f"PRODUCER_EXEMPT lists {p}, which no longer makes a sheet")

    # ---- the rows themselves
    every_open = set(re.findall(r"open<(\w+)>", read(EVERY_SCREEN))) if os.path.exists(EVERY_SCREEN) else set()
    seen = set()
    none_rows = []
    counts = {"NONE": 0, "PARTIAL": 0, "REFERENCED": 0, "INFERRED": 0}
    for r in rows:
        who = f"{r['kind']} {r['subject']}"
        key = (r["kind"], r["subject"])
        if key in seen:
            errors.append(f"{who}: repeated")
        seen.add(key)
        if r["kind"] not in KINDS:
            errors.append(f"{TSV}:{r['line']}: kind {r['kind']!r} is not one of {KINDS}")
            continue
        for k in ("subject", "label", "gate", "trade", "strings", "rendered", "strength", "proof", "source", "basis"):
            if not r[k].strip():
                errors.append(f"{who}: column {k} is empty")
        if r["platform"] not in PLATFORMS:
            errors.append(f"{who}: platform {r['platform']!r}")
        if r["strength"] not in STRENGTHS:
            errors.append(f"{who}: strength {r['strength']!r} is not one of {sorted(STRENGTHS)}")
        if r["basis"] not in BASES:
            errors.append(f"{who}: basis {r['basis']!r} is not VERIFIED or INFERRED")
        else:
            counts["INFERRED"] += r["basis"] == "INFERRED"
        for g in r["gate"].split(";"):
            if g not in SPECIAL_GATES and g not in perms:
                errors.append(f"{who}: gate {g!r} is no Access.Permission")
        for t in r["trade"].split(";"):
            if t != "ALL" and t not in trade_switches:
                errors.append(f"{who}: trade {t!r} is no TradeFeatures switch")
        for lg in r["rendered"].split(","):
            if lg not in LANGS:
                errors.append(f"{who}: rendered language {lg!r}")
        if not (r["strings"] == "9" or r["strings"].isdigit()):
            errors.append(f"{who}: strings {r['strings']!r} is not a count of languages")

        # sources
        for s in filter(None, (x.strip() for x in r["source"].split(";"))):
            if not os.path.exists(s):
                errors.append(f"{who}: source {s} does not exist")
        # proofs
        proofs = [x.strip() for x in r["proof"].split(";") if x.strip()]
        if proofs == ["NONE"]:
            if r["strength"] != "NONE":
                errors.append(f"{who}: strength {r['strength']} but no proof named")
            counts["NONE"] += 1
            none_rows.append(who)
        else:
            if r["strength"] == "NONE":
                errors.append(f"{who}: a proof is named but strength says NONE")
            for pr in proofs:
                path, _, needle = pr.partition("#")
                if not os.path.exists(path):
                    errors.append(f"{who}: proof file {path} does not exist")
                    continue
                if needle and needle.strip('"') not in read(path):
                    errors.append(f"{who}: proof {path} does not hold {needle}")
        counts["PARTIAL"] += r["strength"] == "PARTIAL"
        counts["REFERENCED"] += r["strength"] == "REFERENCED"

        # gates and trades against the code
        if r["kind"] == "phone-screen":
            want = access_map.get(r["subject"], "NONE")
            if r["gate"] != want:
                errors.append(f"{who}: gate {r['gate']} but AccessUi.SCREENS says {want}")
            claims_open = "OPENS" in r["strength"]
            if claims_open and r["subject"] not in every_open:
                named = [x.partition("#")[0] for x in r["proof"].split(";")]
                if not any(os.path.exists(f) and r["subject"] in read(f) for f in named):
                    errors.append(f"{who}: strength {r['strength']} but no proof file opens {r['subject']}")
            if r["subject"] in every_open and not claims_open and EVERY_SCREEN not in r["proof"]:
                errors.append(f"{who}: EveryScreenOpensTest opens it but the row does not say so")
        if r["kind"] == "desktop-section":
            want = need.get(r["subject"], "NONE")
            if r["gate"] != want:
                errors.append(f"{who}: gate {r['gate']} but Section.need() says {want}")
            want_t = "seasons" if desk_trade.get(r["subject"]) == "seasons" else "ALL"
            if r["trade"] != want_t:
                errors.append(f"{who}: trade {r['trade']} but TradeKit.shows says {want_t}")

    if counts["NONE"] > NONE_CEILING:
        errors.append(f"{counts['NONE']} rows have no proof, past NONE_CEILING {NONE_CEILING}: write the proof or say why in the commit that raises it")
    for e in errors:
        print("FAIL", e)
    kinds = ", ".join(f"{k} {len(by_kind[k])}" for k in KINDS)
    print(f"matrix: {len(rows)} rows ({kinds})")
    print(f"proof: {counts['NONE']} with NONE (ceiling {NONE_CEILING}), {counts['PARTIAL']} PARTIAL, "
          f"{counts['REFERENCED']} only REFERENCED; {counts['INFERRED']} rows INFERRED rather than VERIFIED")
    if list_none:
        for w in none_rows:
            print("  NONE:", w)
    if errors:
        print(f"{len(errors)} problem(s)")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
