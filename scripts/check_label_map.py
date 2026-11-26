#!/usr/bin/env python3
"""Hudhud's label map (docs/hudhud-label-map.tsv) is held to the code, the bank and the Formula Book (Marhala 6, 26 Nov 2026).

Fails when:
  - a label is in the bank (shared/hudhud/*.tsv, more/*.tsv) or the code (Hudhud.askFor's `"label" ->` cases, Hudhud.labelOf's
    answers) and not in the map — an orphan meaning nobody described;
  - a label is in the map and in none of those — a row for a meaning that no longer exists;
  - the map is stale: its in_bank or rules_ask column no longer says what the bank and askFor say (`--write` refreshes those two
    columns; the reading columns are HudhudLabelMapTest's, which compares them with what askFor really makes);
  - a figure label (figure = yes) has no Formula Book id, or names an id the Formula Book does not have; a figure written
    GAP:<why> is allowed only for the labels in KNOWN_GAPS (frozen: a new gap fails until the Formula Book has its id);
  - an action, a figure flag or a screen is not one of the allowed values (screens: AnswerCard.kt's ScreenId).

Drift protection, not proof (lesson 125): it cannot say that a task or a formula is the right one, only that each is there and
real. Usage: scripts/check_label_map.py [--write]
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAP = ROOT / "docs/hudhud-label-map.tsv"
BOOK = ROOT / "docs/formula-book.tsv"
DATA = ROOT / "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data"
BANK = [ROOT / "shared/hudhud", ROOT / "shared/hudhud/more"]

HEAD = ["label", "in_bank", "intent", "task", "facet", "action", "figure", "formula", "screen", "rules_ask", "reading", "reads_back"]
ACTIONS = {"read", "draft", "refuse", "help", "reply", "act"}
# Figures whose rule has no Formula Book id yet (frozen; each line is a gap the owner knows of).
KNOWN_GAPS = {"staff_salary"}


def bank_labels():
    labels = set()
    for d in BANK:
        for f in sorted(d.glob("*.tsv")):
            for line in f.read_text(encoding="utf-8").splitlines():
                if line.strip() and not line.startswith("#") and "\t" in line:
                    labels.add(line.split("\t", 1)[0].strip())
    return labels


def code_cases():
    """askFor's cases: label -> the first line of what it makes, and labelOf's answers."""
    src = (DATA / "Hudhud.kt").read_text(encoding="utf-8")
    i = src.index("    internal fun askFor(")
    j = src.index("    internal fun byRules(", i)
    m = re.search(r"return when \(label\) \{\n(.*?)\n            else -> null", src[i:j], re.S)
    if not m:
        sys.exit("check_label_map: Hudhud.askFor's `when (label)` was not found")
    cases = {}
    for line in m.group(1).split("\n"):
        mm = re.match(r"\s{12}((?:\"[a-z_]+\"(?:, )?)+) -> (.*)$", line)
        if mm:
            for label in re.findall(r'"([a-z_]+)"', mm.group(1)):
                cases[label] = re.sub(r"\s+", " ", mm.group(2).strip())
    k = src.index("    internal fun labelOf(a: Ask)")
    end = src.index("    internal fun askFor(", k)
    back = set(re.findall(r'"([a-z_]+)"', src[k:end]))
    return cases, back


def screens():
    src = (DATA / "AnswerCard.kt").read_text(encoding="utf-8")
    m = re.search(r"enum class ScreenId \{(.*?)\}", src, re.S)
    return {s.strip() for s in m.group(1).split(",") if s.strip()}


def formulas():
    ids = set()
    for line in BOOK.read_text(encoding="utf-8").splitlines()[1:]:
        if line.strip():
            ids.add(line.split("\t", 1)[0].strip())
    return ids


def read_map():
    rows, comments = [], []
    lines = MAP.read_text(encoding="utf-8").splitlines()
    head = None
    for n, line in enumerate(lines, 1):
        if line.startswith("#"):
            comments.append(line)
            continue
        if not line.strip():
            continue
        cells = line.split("\t")
        if head is None:
            head = cells
            if head != HEAD:
                sys.exit("check_label_map: the map's header is %s, expected %s" % (head, HEAD))
            continue
        if len(cells) != len(HEAD):
            sys.exit("check_label_map: line %d has %d columns, expected %d" % (n, len(cells), len(HEAD)))
        rows.append(dict(zip(HEAD, cells)))
    return comments, rows


def main():
    write = "--write" in sys.argv[1:]
    comments, rows = read_map()
    bank = bank_labels()
    cases, back = code_cases()
    code = set(cases) | back
    known = bank | code
    fails = []

    mapped = [r["label"] for r in rows]
    for label in sorted({x for x in mapped if mapped.count(x) > 1}):
        fails.append("%s: in the map more than once" % label)
    for label in sorted(known - set(mapped)):
        fails.append("%s: in the %s but not in the map (an orphan label)" % (label, "bank" if label in bank else "code"))
    for label in sorted(set(mapped) - known):
        fails.append("%s: in the map but in neither the bank nor the code" % label)

    ids, names = formulas(), screens()
    for r in rows:
        label = r["label"]
        if write:
            r["in_bank"] = "yes" if label in bank else "no"
            r["rules_ask"] = cases.get(label, "-")
        else:
            want_bank = "yes" if label in bank else "no"
            if r["in_bank"] != want_bank:
                fails.append("%s: in_bank says %s, the bank says %s (stale: --write)" % (label, r["in_bank"], want_bank))
            if r["rules_ask"] != cases.get(label, "-"):
                fails.append("%s: rules_ask says '%s', askFor makes '%s' (stale: --write)" % (label, r["rules_ask"], cases.get(label, "-")))
        if r["action"] not in ACTIONS:
            fails.append("%s: action '%s' is not one of %s" % (label, r["action"], sorted(ACTIONS)))
        if r["figure"] not in ("yes", "no"):
            fails.append("%s: figure must be yes or no" % label)
        f = r["formula"]
        if r["figure"] == "yes":
            if f.startswith("GAP:"):
                if label not in KNOWN_GAPS:
                    fails.append("%s: a figure with no Formula Book id (%s); add the id, a new gap is not allowed" % (label, f))
            elif not re.fullmatch(r"F\d{2,}", f):
                fails.append("%s: a figure label with no Formula Book id ('%s')" % (label, f))
            elif f not in ids:
                fails.append("%s: %s is not in docs/formula-book.tsv" % (label, f))
        elif f != "-":
            fails.append("%s: formula '%s' on a label that is no figure (figure = yes, or '-')" % (label, f))
        if r["screen"] != "-" and r["screen"] not in names:
            fails.append("%s: screen '%s' is no ScreenId" % (label, r["screen"]))
        for col in ("reading", "reads_back"):
            if not r[col] or (not write and r[col] == "?"):
                fails.append("%s: %s is empty (HudhudLabelMapTest with LABEL_MAP_WRITE=1 fills it)" % (label, col))
    for gap in sorted(KNOWN_GAPS - {r["label"] for r in rows if r["formula"].startswith("GAP:")}):
        fails.append("%s: in KNOWN_GAPS but its figure has a Formula Book id now: take it out of KNOWN_GAPS" % gap)

    if write:
        MAP.write_text("\n".join(comments + ["\t".join(HEAD)] + ["\t".join(r[c] for c in HEAD) for r in rows]) + "\n", encoding="utf-8")
        fails = [x for x in fails if "stale" not in x and "is empty" not in x]
    if fails:
        print("check_label_map: %d problem(s)" % len(fails))
        for x in fails:
            print("  " + x)
        sys.exit(1)
    print("check_label_map: %d labels (%d in the bank, %d in the code), every one mapped, figures with Formula Book ids"
          % (len(rows), len(bank), len(code)))


if __name__ == "__main__":
    main()
