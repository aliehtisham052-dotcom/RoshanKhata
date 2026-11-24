#!/usr/bin/env python3
"""Every place the app sends anything off the device is in docs/data-flows.tsv (plan B3, 24 Nov 2026).

docs/data-flows.tsv has one row per flow: the host or library or intent that carries it, what is sent, whether it is sealed,
the feature that turns it on, whether it is on by default, the privacy-policy sections that name it and the Play Data safety
answer. The columns are tab-separated, see the header line. This check fails when:
  1. a host written in the production code (app/src/main, shared/src without its tests, desktop/src/main: a URL, an
     android:host or a network-security domain) is in no row's `hosts` (the IGNORE list below holds XML namespaces and
     the like, each with its reason);
  2. a row's host, library or marker text is no longer in the code (so a row cannot outlive its flow);
  3. a row names a policy section (`policy_sections`) that is missing from docs/privacy-policy.html or
     docs/privacy-policy-ur.html, or the section's English text does not hold the row's `policy_term`;
  4. a row has no policy section (NONE) and its note does not start with POLICY GAP, POLICY MISMATCH or DORMANT HOST
     (a flow the policy does not name must be written down as a gap, never left looking fine);
  5. (LOCAL in `policy_sections` is for a lan row that never leaves the shop, e.g. a Bluetooth printer)
     a column is empty, an id repeats, or `ds_basis` is not CLAUDE.md, UNSEEN or N/A.
It prints the counts: rows, gaps, rows whose Play Data safety answer is UNSEEN (the Play Console answers are the owner's and
are not in the repository, so UNSEEN is honest, never a guess). A new flow adds a row in the same commit as the code.
Drift protection, not proof: it reads names and words, it cannot say that what a row says is sent is what is sent (lesson 125).
Run from the repository root; exit 0 when every flow is written down.
"""
import os, re, sys

TSV = "docs/data-flows.tsv"
POLICIES = ["docs/privacy-policy.html", "docs/privacy-policy-ur.html"]
ENGLISH = POLICIES[0]
COLS = ["id", "kind", "hosts", "libs", "markers", "platform", "what_is_sent", "sealed", "feature", "default",
        "policy_sections", "policy_term", "play_data_safety", "ds_basis", "note"]
OPTIONAL = {"hosts", "libs", "markers", "note"}  # an intent has no host, a link no library
BASES = {"CLAUDE.md", "UNSEEN", "N/A"}
GAP_WORDS = ("POLICY GAP", "POLICY MISMATCH", "DORMANT HOST")
KINDS = {"network", "sdk", "intent", "link", "lan"}

ROOTS = ["app/src/main", "shared/src/jvmSharedMain", "shared/src/androidMain", "shared/src/desktopMain", "desktop/src/main"]
EXTS = (".kt", ".java", ".kts", ".xml", ".json", ".properties")
LIBS_FILE = "app/build.gradle.kts"

# hosts that are not a flow, with the reason
IGNORE = {
    "schemas.android.com": "XML namespace",
    "www.w3.org": "XML/SVG namespace",
    "apache.org": "licence header",
    "www.apache.org": "licence header",
    "localhost": "this device",
}
IGNORE_SUFFIX = (".test", ".invalid", ".example", ".local")


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def walk():
    for root in ROOTS:
        for d, _, files in os.walk(root):
            for n in files:
                if n.endswith(EXTS):
                    yield os.path.join(d, n)


HOST_RES = [
    re.compile(r"https?://([A-Za-z0-9][A-Za-z0-9._-]*\.[A-Za-z]{2,})"),
    re.compile(r'android:host="([A-Za-z0-9][A-Za-z0-9._-]*\.[A-Za-z]{2,})"'),
    re.compile(r"<domain[^>]*>([A-Za-z0-9][A-Za-z0-9._-]*\.[A-Za-z]{2,})</domain>"),
]


def ignored(h):
    return h in IGNORE or h.endswith(IGNORE_SUFFIX) or h.startswith("example.")


def section_texts(html):
    """{number: text} of a policy file: each <h2>N. ...</h2> up to the next <h2>."""
    parts = re.split(r"(?=<h2>\s*\d+\.)", html)
    out = {}
    for p in parts:
        m = re.match(r"<h2>\s*(\d+)\.", p)
        if m:
            out[int(m.group(1))] = re.sub(r"<[^>]+>", " ", p)
    return out


def main():
    errors = []
    if not os.path.exists(TSV):
        print("MISSING", TSV)
        return 1
    lines = [l.rstrip("\n") for l in read(TSV).split("\n") if l.strip()]
    if lines[0].split("\t") != COLS:
        print("HEADER differs from", COLS)
        return 1
    rows = []
    for n, l in enumerate(lines[1:], 2):
        c = l.split("\t")
        if len(c) != len(COLS):
            errors.append(f"{TSV}:{n}: {len(c)} columns, expected {len(COLS)}")
            continue
        rows.append(dict(zip(COLS, c)))

    ids = set()
    for r in rows:
        i = r["id"]
        if i in ids:
            errors.append(f"{i}: repeated id")
        ids.add(i)
        for k in COLS:
            if k not in OPTIONAL and not r[k].strip():
                errors.append(f"{i}: column {k} is empty")
        if r["kind"] not in KINDS:
            errors.append(f"{i}: kind {r['kind']!r} is not one of {sorted(KINDS)}")
        if r["ds_basis"] not in BASES:
            errors.append(f"{i}: ds_basis {r['ds_basis']!r} is not one of {sorted(BASES)}")
        if r["kind"] == "network" and not r["hosts"]:
            errors.append(f"{i}: a network flow names its hosts")

    # 1 and 2: hosts, libraries, markers against the code
    files = list(walk())
    code = {p: read(p) for p in files}
    alltext = "\n".join(code.values())
    found = {}
    for p, t in code.items():
        for rx in HOST_RES:
            for h in rx.findall(t):
                h = h.lower().rstrip(".")
                if not ignored(h):
                    found.setdefault(h, p)
    listed = {}
    for r in rows:
        for h in filter(None, (x.strip().lower() for x in r["hosts"].split(";"))):
            listed.setdefault(h, r["id"])
    for h, p in sorted(found.items()):
        if h not in listed:
            errors.append(f"host {h} is in {p} but in no row of {TSV}")
    lowered = alltext.lower()
    for h, i in sorted(listed.items()):
        if h not in lowered:
            errors.append(f"{i}: host {h} is no longer anywhere in the code")
    libs_text = read(LIBS_FILE) if os.path.exists(LIBS_FILE) else ""
    for r in rows:
        for lib in filter(None, (x.strip() for x in r["libs"].split(";"))):
            if lib not in libs_text:
                errors.append(f"{r['id']}: library {lib} is not in {LIBS_FILE}")
        for m in filter(None, (x.strip() for x in r["markers"].split(";"))):
            if m not in alltext:
                errors.append(f"{r['id']}: marker {m!r} is no longer in the code")
        if not r["hosts"] and not r["libs"] and not r["markers"]:
            errors.append(f"{r['id']}: no host, library or marker: nothing ties the row to the code")

    # 3 and 4: the policy
    pol = {}
    for p in POLICIES:
        if not os.path.exists(p):
            errors.append(f"missing {p}")
            continue
        pol[p] = section_texts(read(p))
    gaps = 0
    for r in rows:
        i = r["id"]
        secs = r["policy_sections"].strip()
        if secs.upper() == "LOCAL":
            # a link that never leaves the shop (a Bluetooth printer): no policy section is owed
            if r["kind"] != "lan" or r["ds_basis"] != "N/A":
                errors.append(f"{i}: LOCAL is only for a lan flow with ds_basis N/A")
            continue
        if secs.upper() == "NONE":
            gaps += 1
            if not r["note"].startswith(GAP_WORDS):
                errors.append(f"{i}: no policy section, so its note must start with one of {GAP_WORDS}")
            continue
        if r["note"].startswith(GAP_WORDS):
            gaps += 1
        nums = []
        for s in secs.split(";"):
            if not s.strip().isdigit():
                errors.append(f"{i}: policy section {s!r} is not a number")
                continue
            nums.append(int(s))
        for p, texts in pol.items():
            for s in nums:
                if s not in texts:
                    errors.append(f"{i}: section {s} is missing from {p}")
        eng = pol.get(ENGLISH, {})
        term = r["policy_term"].strip().lower()
        if nums and term and not any(term in re.sub(r"\s+", " ", eng.get(s, "")).lower() for s in nums):
            errors.append(f"{i}: sections {nums} of {ENGLISH} do not hold the words {r['policy_term']!r}")

    unseen = sum(1 for r in rows if r["ds_basis"] == "UNSEEN")
    for e in errors:
        print("FAIL", e)
    print(f"flows: {len(rows)} rows, {len(found)} hosts in code, {gaps} written down as a policy gap, "
          f"{unseen} with a Play Data safety answer not in the repository (UNSEEN)")
    if errors:
        print(f"{len(errors)} problem(s)")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
