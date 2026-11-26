#!/usr/bin/env python3
"""Hard negatives and reversed pairs for Hudhud's training cycle (plan v1, Marhala 8; 26 Nov 2026).

A sense learnt from a bank of sentences reads a sentence by its words; two sentences that differ by ONE word and mean
opposite things are what it gets wrong (the wrong money side, BC-240, lessons 163 and 166). This generator takes labelled
sentences and writes each one's one-word-changed partners, with the partner's own label:

  side      who gave: "{NAME} ko 500 diye" (I gave, entry_gave) <-> "{NAME} ne 500 diye" (he gave, entry_got);
            "{NAME} se 500 liye" (entry_got) -> "{NAME} ko 500 diye" (entry_gave); English "gave {NAME} 500" <-> "{NAME} gave me 500"
  topic     what the figure is: sale / munafa / wusooli (sales_today, profit, collected) and English sales / profit / payments received
  not       money that did NOT move: "nahi diye", "did not give"            -> NO bank label: a RULE matter (lesson 166)
  future    money that WILL move: "dega", "dunga", "will give"                -> NO bank label: a RULE matter (lesson 166)

A partner whose meaning is a bank label goes to the ADDITIONS file in scripts/hudhud_cycle.py's format (source "pair", class
"pair:<rule>"), ready for the cycle's gate. A partner with no bank label ("not", "future": nothing moved, so no entry) never goes
into the bank: what must never move is said by the rules (plan v1 step 5, lesson 163), so it goes to the RULES file
(language, sentence, what the rules must answer, rule) for a test the lead writes beside the rule that holds it.

    python3 -I scripts/hudhud_pairs.py --in FILE --additions OUT.tsv --rules OUT_RULES.tsv
    python3 -I scripts/hudhud_pairs.py --from-bank --meanings entry_gave,entry_got --langs ur-Latn,en --additions OUT --rules OUT_R
    python3 -I scripts/hudhud_pairs.py --self-test

--in FILE: language<TAB>meaning<TAB>sentence (or the cycle's five-column additions format). --from-bank: the repository's own
bank rows of the given meanings and languages. Names and goods only as {NAME}/{ITEM}: a sentence with a name-shaped word is
refused, never rewritten (hudhud_cycle.name_problem). A partner already in the bank, or a sentence of a measured set (held-out,
owner, review), is left out and counted, so the additions file passes the cycle's exact-leakage check; near-duplicates are
the cycle's gate's to judge. Which languages: Roman Urdu, Urdu script and English only, where the rules here could be checked;
Hindi, Bengali, Arabic, Farsi, Sindhi and Indonesian partners wait for a native speaker's rule (D17), and are left out, not guessed.

OUT-OF-SCOPE SENTENCES (MASSIVE, CC-BY-4.0): a hook, not built. `--massive DIR` would read MASSIVE's JSONL (locale, utt,
intent) for the locales ur-PK, hi-IN, bn-BD, ar-SA, fa-IR, id-ID, en-US, keep only intents no ledger question could be
(weather, music, alarm, iot, play, ...) and write them as `rules` rows expecting "not the book" (Hudhud's OffTopic), never as
bank sentences (Hudhud has no "off topic" meaning to learn). On 26 Nov 2026 it could not be fetched from this session (see
docs/hudhud-training-cycle.md); with the data in an owner's folder outside the repository, the reader is the only work left.
"""
import importlib.util
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANGS = ("ur-Latn", "ur", "en")


def _load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _sub(pattern, repl, text):
    """re.sub on whole words; None when nothing changed."""
    new = re.sub(pattern, repl, text, count=1)
    return new if new != text else None


# (language, the source meaning, rule, pattern, replacement, the partner's meaning or None for a rule matter, what the rules must say)
RULES = [
    # --- side: who gave. Only plain "diye/diya" sentences without udhaar (with udhaar "ne ... diye" can be either side).
    ("ur-Latn", "entry_gave", "side", r"\{NAME\} ko (\d[\d,]*) (?:rupay |rupaye )?(diye|diya|de diye|de diya)\b", r"{NAME} ne \1 \2", "entry_got", None),
    ("ur-Latn", "entry_got", "side", r"\{NAME\} ne (\d[\d,]*) (?:rupay |rupaye )?(diye|diya|de diye|de diya)\b", r"{NAME} ko \1 \2", "entry_gave", None),
    ("ur-Latn", "entry_got", "side", r"\{NAME\} se (\d[\d,]*) (?:rupay |rupaye )?(liye|liya|le liye)\b", r"{NAME} ko \1 diye", "entry_gave", None),
    ("ur", "entry_gave", "side", r"\{NAME\} کو (\d[\d,]*) (دیے|دیئے|دیا)", r"{NAME} نے \1 \2", "entry_got", None),
    ("ur", "entry_got", "side", r"\{NAME\} نے (\d[\d,]*) (دیے|دیئے|دیا)", r"{NAME} کو \1 \2", "entry_gave", None),
    ("en", "entry_gave", "side", r"^(?:I )?gave \{NAME\} (\d[\d,]*)\b", r"{NAME} gave me \1", "entry_got", None),
    ("en", "entry_got", "side", r"^\{NAME\} gave me (\d[\d,]*)\b", r"gave {NAME} \1", "entry_gave", None),
    # --- not: the money did not move (no entry at all).
    ("ur-Latn", "entry_got", "not", r"\{NAME\} ne (\d[\d,]*) (diye|diya)\b", r"{NAME} ne \1 nahi \2", None, "no entry: nothing moved"),
    ("ur-Latn", "entry_gave", "not", r"\{NAME\} ko (\d[\d,]*) (diye|diya)\b", r"{NAME} ko \1 nahi \2", None, "no entry: nothing moved"),
    ("ur", "entry_got", "not", r"\{NAME\} نے (\d[\d,]*) (دیے|دیئے|دیا)", r"{NAME} نے \1 نہیں \2", None, "no entry: nothing moved"),
    ("en", "entry_got", "not", r"^\{NAME\} gave me (\d[\d,]*)\b", r"{NAME} did not give me \1", None, "no entry: nothing moved"),
    # --- future: the money will move (a promise, no entry).
    ("ur-Latn", "entry_got", "future", r"\{NAME\} ne (\d[\d,]*) (?:de )?(diye|diya)\b", r"{NAME} \1 dega", None, "no entry: a promise"),
    ("ur-Latn", "entry_gave", "future", r"\{NAME\} ko (\d[\d,]*) (diye|diya)\b", r"{NAME} ko \1 dunga", None, "no entry: a promise"),
    ("en", "entry_got", "future", r"^\{NAME\} gave me (\d[\d,]*)\b", r"{NAME} will give me \1", None, "no entry: a promise"),
    # --- topic: which figure.
    ("ur-Latn", "sales_today", "topic", r"\bsale\b", "munafa", "profit", None),
    ("ur-Latn", "sales_today", "topic", r"\bsale\b", "wusooli", "collected", None),
    ("ur-Latn", "profit", "topic", r"\bmunafa\b", "sale", "sales_period", None),
    ("ur-Latn", "collected", "topic", r"\bwusooli\b", "sale", "sales_period", None),
    ("en", "sales_today", "topic", r"\bsales\b", "profit", "profit", None),
    ("en", "sales_today", "topic", r"\bsales\b", "payments received", "collected", None),
    ("en", "profit", "topic", r"\bprofit\b", "sales", "sales_period", None),
]
# A sales figure for today stays today's: "aaj ka munafa" is profit (Hudhud's profit meaning covers any span), but "aaj ki sale"
# from a profit sentence is today's sales only when the sentence says today.
TODAY = re.compile(r"\b(aaj|today)\b|آج", re.I)


def partners(lang, meaning, text):
    """Every (rule, partner sentence, partner meaning or None, what the rules must say or None) of one sentence."""
    out = []
    for r_lang, r_meaning, rule, pattern, repl, to, expect in RULES:
        if r_lang != lang or r_meaning != meaning:
            continue
        new = _sub(pattern, repl, text)
        if not new:
            continue
        if to == "sales_period" and TODAY.search(new):
            to = "sales_today"
        out.append((rule, " ".join(new.split()), to, expect))
    return out


def generate(rows, cycle, sets, bankmod):
    """(additions, rules, counts) for (language, meaning, sentence) rows."""
    key = bankmod.key_indic if bankmod.DEFAULT_RECIPE >= 2 else bankmod.key
    bank, held = bankmod.read()
    taught = {key(t) for _, _, t in bank}
    measured = {key(t) for _, _, t in held}
    res = bankmod.OUT.parent
    for name, col in bankmod.MEASURED.items():
        for raw in (res / name).read_text(encoding="utf-8").splitlines():
            cols = raw.split("\t")
            if raw.strip() and not raw.startswith("#") and len(cols) > col:
                measured.add(key(cols[col]))
    adds, rules, counts, seen = [], [], {"refused": 0, "in_bank": 0, "measured": 0, "no_rule": 0}, set()
    for lang, meaning, text in rows:
        if cycle.name_problem(text, sets, cycle.ALLOWED_WORDS):
            counts["refused"] += 1
            continue
        found = partners(lang, meaning, text) if lang in LANGS else []
        if not found:
            counts["no_rule"] += 1
        for rule, new, to, expect in found:
            k = key(new)
            if k in seen:
                continue
            seen.add(k)
            if k in measured:
                counts["measured"] += 1
                continue
            if to is None:
                rules.append((lang, new, expect, f"pair:{rule}"))
            elif k in taught:
                counts["in_bank"] += 1
            else:
                adds.append((lang, to, new, "pair", f"pair:{rule}"))
    return adds, rules, counts


def self_test():
    ok = True

    def check(cond, what):
        nonlocal ok
        if not cond:
            ok = False
            print("FAIL: " + what)
    p = {(r, s, t) for r, s, t, _ in partners("ur-Latn", "entry_gave", "{NAME} ko 500 diye")}
    check(("side", "{NAME} ne 500 diye", "entry_got") in p, "ko -> ne turns the side")
    check(("not", "{NAME} ko 500 nahi diye", None) in p, "nahi is a rule matter, never a bank label")
    check(("future", "{NAME} ko 500 dunga", None) in p, "dunga is a promise")
    check(not partners("ur-Latn", "entry_gave", "{NAME} ko 500 udhaar diye") or
          all(t != "entry_got" for _, _, t, _ in partners("ur-Latn", "entry_gave", "{NAME} ko 500 udhaar diye")),
          "with udhaar the side is not turned (either side)")
    q = {(r, s, t) for r, s, t, _ in partners("ur-Latn", "sales_today", "aaj kitni sale hui")}
    check(("topic", "aaj kitni munafa hui", "profit") in q and ("topic", "aaj kitni wusooli hui", "collected") in q, "sale -> munafa, wusooli")
    r = {(s, t) for _, s, t, _ in partners("ur-Latn", "profit", "aaj ka munafa kitna hai")}
    check(("aaj ka sale kitna hai", "sales_today") in r, "a profit sentence with today becomes today's sales, not the period's")
    e = {(s, t) for _, s, t, _ in partners("en", "entry_got", "{NAME} gave me 500")}
    check(("gave {NAME} 500", "entry_gave") in e and ("{NAME} did not give me 500", None) in e, "English side and negation")
    check(partners("hi", "entry_gave", "{NAME} को 500 दिए") == [], "Hindi waits for a native speaker's rule")
    check(partners("ur", "entry_gave", "{NAME} کو 500 دیے")[0][1] == "{NAME} نے 500 دیے", "Urdu script ko -> ne")
    cycle, sets = _load("hudhud_cycle"), _load("hudhud_sets")
    check(cycle.name_problem("Saleem ko 500 diye", sets, cycle.ALLOWED_WORDS) is not None, "a test-book name is refused")
    check(cycle.name_problem("aaj Kareem ko 500 diye", sets, cycle.ALLOWED_WORDS) is not None, "a capital word inside is refused")
    check(cycle.name_problem("aaj {NAME} ko WhatsApp par 500 bheje", sets, cycle.ALLOWED_WORDS) is None, "WhatsApp passes")
    check(cycle.name_problem("Kareem gave me 500", sets, cycle.ALLOWED_WORDS) is not None, "a name as the first word is refused too")
    adds, rules, counts = generate([("ur-Latn", "entry_gave", "{NAME} ko 500 diye"), ("en", "x", "Kareem gave me 5")], cycle, sets, sets.BANK)
    check(counts["refused"] == 1, "a name-shaped input is refused, never rewritten")
    check(all(a[3] == "pair" and a[4].startswith("pair:") for a in adds), "additions are source pair, class pair:<rule>")
    check(all(r[2].startswith("no entry") for r in rules), "rules rows say what the rules must answer")
    print("hudhud_pairs self-test " + ("OK" if ok else "FAILED"))
    return 0 if ok else 1


def main(argv):
    if "--self-test" in argv:
        return self_test()
    cycle, sets = _load("hudhud_cycle"), _load("hudhud_sets")
    bankmod = sets.BANK

    def arg(n):
        return argv[argv.index(n) + 1] if n in argv and argv.index(n) + 1 < len(argv) else None
    if "--massive" in argv:
        print("hudhud_pairs: --massive is a documented hook, not built (docs/hudhud-training-cycle.md): no MASSIVE data in reach")
        return 2
    rows = []
    if arg("--in"):
        for raw in Path(arg("--in")).read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.startswith("#"):
                continue
            c = raw.split("\t")
            if len(c) >= 3:
                rows.append((sets.ALIASES.get(c[0], c[0]), c[1], " ".join(c[2].split())))
    elif "--from-bank" in argv:
        meanings = set((arg("--meanings") or "").split(",")) - {""}
        langs = set((arg("--langs") or ",".join(LANGS)).split(","))
        bank, _ = bankmod.read()
        rows = [(l, m, t) for l, m, t in bank if l in langs and (not meanings or m in meanings)]
    else:
        print(__doc__)
        return 2
    adds, rules, counts = generate(rows, cycle, sets, bankmod)
    if arg("--additions"):
        Path(arg("--additions")).write_text(
            "# hudhud_pairs.py: language<TAB>meaning<TAB>sentence<TAB>source<TAB>class (scripts/hudhud_cycle.py's additions format)\n"
            + "".join("\t".join(a) + "\n" for a in adds), encoding="utf-8")
    if arg("--rules"):
        Path(arg("--rules")).write_text(
            "# hudhud_pairs.py: language<TAB>sentence<TAB>what the RULES must answer<TAB>rule (never bank sentences: lesson 166)\n"
            + "".join("\t".join(r) + "\n" for r in rules), encoding="utf-8")
    print(f"{len(rows)} sentences in: {len(adds)} bank partners, {len(rules)} rule partners; left out: {counts['in_bank']} already "
          f"taught, {counts['measured']} measured-set sentences, {counts['refused']} name-shaped inputs refused, "
          f"{counts['no_rule']} with no rule for their language or meaning")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
