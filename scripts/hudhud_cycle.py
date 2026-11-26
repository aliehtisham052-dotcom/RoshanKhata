#!/usr/bin/env python3
"""The Python half of Hudhud's training cycle (plan v1, "the training cycle (ten steps)", Marhala 8; 26 Nov 2026).

scripts/hudhud_cycle.sh is the one command; it calls this file for the steps that need no Java:

    python3 -I scripts/hudhud_cycle.py gate --additions F --out DIR [--next-key] [--sealed DIR] [--allow-word W ...]
        steps 3-4: the quality gate on the NEW bank sentences, then the candidate tree and bank in DIR (never the repository).
        Exit 0 ready to learn, 3 no change (nothing to learn), 1 refused (the reasons are printed and in DIR/gate.txt).
    python3 -I scripts/hudhud_cycle.py verdict --out DIR
        step 10's decision from what the Java steps wrote: SHIP (exit 0) or NO-SHIP (exit 1), every reason said; DIR/verdict.md.
    python3 -I scripts/hudhud_cycle.py apply --out DIR
        --apply's repository half: the additions into shared/hudhud/more/<lang>.tsv, their provenance into
        docs/hudhud-bank-additions.tsv, recipe 3 made the default when the run was --next-key. Refused unless DIR's verdict is SHIP
        and the repository's bank is still the one the run measured. hudhud_cycle.sh then writes bank.tsv, sense.bin, the
        overlap list and the manifest.
    python3 -I scripts/hudhud_cycle.py ledger-check
        every row of docs/hudhud-bank-additions.tsv is a line of its language's more/ file (Strings: --self-test runs it).

THE ADDITIONS FILE (one sentence a line, `#` lines skipped):  language<TAB>meaning<TAB>sentence<TAB>source<TAB>class
  language  one of the nine (en ur ur-Latn hi bn ar fa sd id; Android's "in" is read as id)
  meaning   a label of Hudhud.kt's meaning table (the bank's own labels; hudhud_sets.py reads them from the code)
  sentence  names and goods ONLY as {NAME} and {ITEM} (the bank's rule, lesson 80); amounts may be figures
  source    checklog-class (written by Claude from the CLASS of a check-log mistake: never the owner's row, plan "Never" 5),
            synthetic (written by Claude for a class), or pair (scripts/hudhud_pairs.py's one-word-changed partner)
  class     what the sentence teaches: a BC id or a short class name; required for checklog-class and pair

THE GATE refuses (nothing is learnt until it passes):
  columns, language, meaning, source, class; an empty sentence or one with no letter of its language's script;
  a phone-shaped number that is not the invented family (hudhud_sets.phone_problem);
  a name-shaped word: a Latin word of three letters or more with a capital first letter, anywhere in the sentence (the bank
  is written in small letters; ALLOWED_WORDS, the bank's own capital words, and --allow-word pass), or any of the test books' invented names or goods in any script (they must be {NAME}/{ITEM}: a literal
  one would teach the sense the test books' own answers);
  a sentence already in the bank (it would be dropped: nothing learnt) or there with another meaning (a label conflict);
  two additions that are one sentence;
  LEAKAGE: an addition that is a held-out, owner, review (or, with --sealed, sealed-set) sentence by the bank's key, or a
  NEAR-DUPLICATE of one (hudhud_sets.near_key, Jaccard of 3-grams >= 0.80 with names, goods and numbers masked): the same
  sentence family may not sit on both sides (step 4); for a sealed sentence only its file and line are said (plan "Never" 9).
Then the candidate tree (DIR/src: the first-round files and more/ with the additions appended), its bank and held-out
(DIR/res/hudhud/, gen_hudhud_bank.read with the candidate's sources), and hudhud_sets.py's whole gate run on the candidate:
every addition must be in the candidate bank and the held-out set must be the repository's, byte for byte.

THE SHIP RULE (verdict): every one of these, or NO-SHIP with each failed one said:
  1. the gate passed and the candidate bank differs from the repository's (else "no change");
  2. hard zeros: wrong money side, wrong amount, wrong person, wrong product = 0 on every set, for the candidate under the
     shipped ensemble AND both other ensembles (plan v1 hard-zero gates; lesson 163);
  3. the gain is above the seed noise under EVERY ensemble, each against the base bank learnt the same way: the owner's two
     lists together +12 rows, or held-out strict +6 (plan v1: "no retrain when the gain is inside the noise");
  4. no loss past the noise under any ensemble: held-out strict -6, each owner list -12, each review set's held rows -12
     (every row lost is listed in DIR/compare for a row-by-row decision before the floors move);
  5. the floors the tests hold today (HudhudOwnerSetTest, HudhudSenseTest), for the candidate under the shipped ensemble;
  6. the seed-safe guard tests and the byte-for-byte model test pass against the candidate bank and sense (DIR/junit.txt);
  7. the certainty table and the thresholds were derived again on the candidate (DIR/calibration.md, lesson 165). They are
     a REPORT: a changed threshold in Hudhud.decide is a code change for a person, never made by this cycle (lesson 167).
"""
import hashlib
import importlib.util
import json
import re
import shutil
import sys
import unicodedata
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEDGER = ROOT / "docs/hudhud-bank-additions.tsv"
LEDGER_HEAD = ["date", "cycle", "language", "meaning", "source", "class", "sentence"]
SOURCES = {"checklog-class", "synthetic", "pair"}
NEEDS_CLASS = {"checklog-class", "pair"}
# Words that are written with a capital inside a sentence and are no person's name. A word not here and not passed with
# --allow-word stops the gate: a capital word in the middle of a bank sentence is most often a name, and names go in as {NAME}.
ALLOWED_WORDS = {"WhatsApp", "Excel", "PDF", "SMS", "Google", "Drive", "Android", "Windows", "Rs", "Zakat", "Qarz", "Hasna",
                 "Ramzan", "Ramazan", "Eid", "Jumma", "Muharram", "January", "February", "March", "April", "May", "June",
                 "July", "August", "September", "October", "November", "December", "Monday", "Tuesday", "Wednesday",
                 "Thursday", "Friday", "Saturday", "Sunday", "I", "OK", "QR", "PIN", "GST", "VAT", "IBAN", "Kharif", "Rabi",
                 # the bank's own capital words (26 Nov 2026): the app, the assistant, forms of address, languages, Indonesian days
                 "Hudhud", "Hood", "Roshan", "Khata", "Bang", "Pak", "Kak", "Anda", "Mas", "Bro", "Bos", "Urdu", "English", "Hindi",
                 "Bengali", "Arabic", "Arab", "Sindhi", "Farsi", "Persian", "Indonesia", "Inggris", "Jawa", "Islamic", "Senin",
                 "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"}
# Noise of the seed (docs/hudhud-baseline-24nov2026.md, plan v1): the gain must pass it, a loss must stay inside it.
GAIN_OWNER, GAIN_HELD = 12, 6
LOSS = {"heldout strict": 6, "owner_set": 12, "owner_1000": 12, "review_d": 12, "review_o": 12}
HARD = ("wrong side", "wrong amount", "wrong person", "wrong product")
SETS = ("heldout", "owner_set", "owner_1000", "review_d", "review_o")


def _load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _arg(argv, name, default=None):
    return argv[argv.index(name) + 1] if name in argv and argv.index(name) + 1 < len(argv) else default


def _args(argv, name):
    return [argv[i + 1] for i, a in enumerate(argv) if a == name and i + 1 < len(argv)]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


# ---------------------------------------------------------------------------------------------------------------
# Step 3-4: the gate.

def read_additions(path, problems):
    """(line, language, meaning, sentence, source, class) of every addition; malformed lines go to [problems]."""
    rows = []
    sets = _load("hudhud_sets")
    for n, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        cols = raw.split("\t")
        where = f"additions:{n}"
        if len(cols) != 5:
            problems.append(f"{where}: {len(cols)} columns, expected 5 (language, meaning, sentence, source, class)")
            continue
        lang, meaning, text, source, cls = (c.strip() for c in cols)
        lang = sets.ALIASES.get(lang, lang)
        text = " ".join(text.split())
        if lang not in sets.LANGUAGES:
            problems.append(f"{where}: language '{lang}' is not one of the nine")
        if source not in SOURCES:
            problems.append(f"{where}: source '{source}' is not one of {sorted(SOURCES)}")
        if source in NEEDS_CLASS and not cls:
            problems.append(f"{where}: a {source} sentence names the class it teaches (a BC id or a class name)")
        rows.append((n, lang, meaning, text, source, cls))
    return rows


def name_problem(text, sets, allowed):
    """Why [text] seems to carry a name or a good that should be {NAME}/{ITEM}; None when it does not."""
    bare = sets.PLACEHOLDER.sub(" ", text)
    words = re.findall(r"[^\W\d_]+", bare)
    folded = {sets.key(w) for w in words}
    for w in sets._NAME_WORDS + sets._GOODS_WORDS:
        if sets.key(w) in folded:
            return f"'{w}' is a test book's invented name or good: write {{NAME}} or {{ITEM}}"
    for p in sets._NAME_PHRASES:
        if f" {sets.key(p)} " in f" {sets.key(bare)} ":
            return f"'{p}' is a test book's invented name: write {{NAME}}"
    for w in words:
        latin = all("LATIN" in unicodedata.name(c, "") for c in w)
        if latin and w[0].isupper() and len(w) >= 3 and not w.isupper() and w not in allowed:
            return f"'{w}' looks like a name (a capital word; the bank is written in small letters): write {{NAME}} or {{ITEM}}, or --allow-word {w}"
    return None


def gate(argv):
    additions = _arg(argv, "--additions")
    out = Path(_arg(argv, "--out", str(ROOT / "build/hudhud-cycle"))).resolve()
    if not additions:
        print("gate: --additions FILE (an empty file is allowed: it means no change)")
        return 2
    next_key = "--next-key" in argv
    allowed = ALLOWED_WORDS | set(_args(argv, "--allow-word"))
    sealed = _arg(argv, "--sealed")
    if out.resolve() == ROOT or ROOT not in out.resolve().parents or "build" not in out.resolve().relative_to(ROOT).parts:
        print(f"gate: --out must be a folder under {ROOT / 'build'} (the cycle never writes the repository before --apply)")
        return 2
    sets = _load("hudhud_sets")
    bankmod = sets.BANK
    base_recipe = bankmod.DEFAULT_RECIPE
    recipe = 3 if next_key else base_recipe
    engine = sets.Engine()
    problems = []
    rows = read_additions(additions, problems)
    key = bankmod.key_indic if recipe >= 2 else bankmod.key

    # The sets as they stand (records of the repository), for keys and near-duplicates.
    recs = sets.records(engine, problems)
    bank_meaning = {}
    for r in recs:
        if r["split"] == "train":
            bank_meaning.setdefault(key(r["text"]), r["meaning"])
    measured = {key(r["text"]): r for r in recs if r["split"] in ("heldout", "owner", "review")}
    seen = {}
    measured3 = bankmod.measured_keys() if recipe == 3 else set()
    for n, lang, meaning, text, source, cls in rows:
        where = f"additions:{n}"
        if not text:
            problems.append(f"{where}: empty sentence")
            continue
        if meaning not in engine.meanings:
            problems.append(f"{where}: meaning '{meaning}' is not in Hudhud.kt's meaning table")
        if lang in sets.SCRIPT_OF and sets.SCRIPT_OF[lang] not in sets.scripts_of(sets.PLACEHOLDER.sub("", text)):
            problems.append(f"{where}: a {lang} sentence with no {sets.SCRIPT_OF[lang]} letter")
        why = sets.phone_problem(text)
        if why:
            problems.append(f"{where}: {why}")
        why = name_problem(text, sets, allowed)
        if why:
            problems.append(f"{where}: {why}")
        k = key(text)
        if k in seen:
            problems.append(f"{where}: the same sentence as additions:{seen[k]}")
        seen.setdefault(k, n)
        if k in measured:
            m = measured[k]
            problems.append(f"{where}: LEAKAGE: it is a {m['split']} sentence ({m['source']}): a set measured is never learnt")
        elif k in bank_meaning:
            if bank_meaning[k] != meaning:
                problems.append(f"{where}: label conflict: the bank already has it as '{bank_meaning[k]}'")
            else:
                problems.append(f"{where}: already in the bank (it would be dropped: nothing learnt)")
        if k in measured3 and k not in measured:
            problems.append(f"{where}: LEAKAGE: a measured-set sentence (recipe 3 would leave it out)")

    # Near-duplicates of a measured sentence (the additions as the only train family).
    add_recs = [{"id": f"add-{n}", "text": t, "split": "train", "source": f"additions:{n}"}
                for n, _, _, t, _, _ in rows if t]
    others = [r for r in recs if r["split"] in ("heldout", "owner", "review")]
    if sealed:
        others += sets.sealed_records(sealed)
    near = 0
    for a, b, jac in sets.near_duplicates(add_recs + others):
        fam = {sets._family(a), sets._family(b)}
        if "train" not in fam:
            continue
        add, other = (a, b) if sets._family(a) == "train" else (b, a)
        near += 1
        shown = f"sealed {other['source']} (not shown: plan Never 9)" if sets._family(other) == "sealed" else \
            f"{other['source']} '{other['text']}'"
        problems.append(f"{add['source']}: LEAKAGE: near-duplicate ({jac:.2f}) of {shown}: one sentence family, one set (step 4)")

    out.mkdir(parents=True, exist_ok=True)
    # Nothing of an earlier run may be read as this one's (lesson 131, BC-273's class).
    for p in [out / n for n in ("figures.tsv", "verdict.md", "junit.txt", "calibration.md", "calibration-summary.txt", "gate.json",
                                "sense-candidate.bin", "cycle-manifest.tsv", "compare", "res", "root")] + list(out.glob("rows-*.tsv")):
        if p.is_dir():
            shutil.rmtree(p)
        elif p.exists():
            p.unlink()
    lines = [f"additions: {len(rows)} sentences from {additions}; recipe {recipe} (base {base_recipe}); "
             f"near-duplicates of measured sentences: {near}"]
    if problems:
        lines += problems + [f"GATE REFUSED: {len(problems)} problem(s)"]
        (out / "gate.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print("\n".join(lines))
        return 1

    # The candidate tree, its bank and held-out (gen_hudhud_bank's own reader, its sources moved).
    src = out / "src"
    if src.exists():
        shutil.rmtree(src)
    (src / "more").mkdir(parents=True)
    for lang in bankmod.LANGS:
        for folder, into in ((bankmod.SRC, src), (bankmod.MORE, src / "more")):
            f = folder / f"{lang}.tsv"
            if f.exists():
                shutil.copyfile(f, into / f.name)
    for _, lang, meaning, text, _, _ in rows:
        f = src / "more" / f"{lang}.tsv"
        body = f.read_text(encoding="utf-8") if f.exists() else ""
        if body and not body.endswith("\n"):
            body += "\n"
        f.write_text(body + f"{meaning}\t{text}\n", encoding="utf-8")
    bankmod.SRC, bankmod.MORE = src, src / "more"
    bank, held = bankmod.read(recipe)
    res = out / "res/hudhud"
    res.mkdir(parents=True, exist_ok=True)
    (res / "bank.tsv").write_text(bankmod.bank_text(bank), encoding="utf-8")
    (res / "heldout.tsv").write_text(bankmod.held_text(held), encoding="utf-8")
    after = []
    if (res / "heldout.tsv").read_bytes() != bankmod.HELD.read_bytes():
        after.append("the candidate's held-out set is not the repository's (an addition reached the first round?)")
    in_bank = {(l, m, t) for l, m, t in bank}
    for n, lang, meaning, text, _, _ in rows:
        if (lang, meaning, text) not in in_bank:
            after.append(f"additions:{n}: not in the candidate bank (dropped by the bank's own reader)")
    # hudhud_sets.py's whole gate on the candidate (its own module copy, sources moved). Under recipe 3 the overlap list shrinks
    # by design: "no longer overlaps" lines are what --apply's --write-overlap settles, not a fault.
    sets.BANK.SRC, sets.BANK.MORE, sets.BANK.DEFAULT_RECIPE = src, src / "more", recipe
    cprobs = []
    crecs = sets.records(engine, cprobs)
    sets.check(engine, crecs, cprobs)
    shrink = [p for p in cprobs if "no longer overlaps" in p]
    after += [p for p in cprobs if "no longer overlaps" not in p]
    if shrink and recipe != 3:
        after += shrink
    changed = (res / "bank.tsv").read_bytes() != bankmod.OUT.read_bytes()
    state = {"recipe": recipe, "base_recipe": base_recipe, "additions": len(rows), "additions_file": str(Path(additions).resolve()),
             "additions_sha": sha(additions), "changed": changed, "overlap_shrinks": len(shrink),
             "candidate_lines": len(bank), "base_lines": len(bankmod.OUT.read_text(encoding="utf-8").splitlines()),
             "base_bank_sha": sha(bankmod.OUT), "candidate_bank_sha": sha(res / "bank.tsv"),
             "rows": [[n, l, m, t, s, c] for n, l, m, t, s, c in rows]}
    (out / "gate.json").write_text(json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8")
    if after:
        lines += after + [f"GATE REFUSED after building the candidate: {len(after)} problem(s)"]
        (out / "gate.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print("\n".join(lines))
        return 1
    lines.append(f"candidate bank: {state['candidate_lines']} lines (base {state['base_lines']}); held-out identical; "
                 f"hudhud_sets gate clean{f'; {len(shrink)} listed overlaps leave the bank (recipe 3)' if shrink else ''}")
    if not changed:
        lines.append("no change: the candidate bank is the repository's bank byte for byte")
    (out / "gate.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return 0 if changed else 3


# ---------------------------------------------------------------------------------------------------------------
# Step 10: the verdict.

def read_figures(path):
    fig = {}
    if not path.exists():
        return fig
    for raw in path.read_text(encoding="utf-8").splitlines()[1:]:
        if raw.startswith("#") or not raw.strip():
            continue
        model, ens, metric, value = raw.split("\t")
        fig[(model, ens, metric)] = int(value)
    return fig


def floors():
    tests = ROOT / "shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data"
    owner = (tests / "HudhudOwnerSetTest.kt").read_text(encoding="utf-8")
    sense = (tests / "HudhudSenseTest.kt").read_text(encoding="utf-8")
    f = {"owner_set": int(re.search(r"SHORT_FLOOR = (\d+)", owner).group(1)),
         "owner_1000": int(re.search(r"THOUSAND_FLOOR = (\d+)", owner).group(1)),
         "heldout_pct": int(re.search(r"const val FLOOR = (\d+)", sense).group(1))}
    return f


def decide(fig, gate_state, junit_ok, calibrated, compare=None):
    """(ship, reasons, table lines). Pure: the self-test drives it with made-up figures."""
    reasons, table = [], []
    if gate_state is None:
        return False, ["the gate did not pass (DIR/gate.txt)"], table
    if not gate_state.get("changed"):
        return False, ["no change: the candidate bank is the repository's bank, nothing to learn"], table
    ens = sorted({e for (m, e, _) in fig if m == "candidate"}, key=lambda e: (e != "313", e))
    if "313" not in ens or len(ens) < 3:
        reasons.append(f"the candidate was measured under {len(ens)} ensemble(s), not the shipped one and two others (lesson 163)")
    series = ["heldout strict", "heldout lenient", "owner_set", "owner_1000", "review_d", "review_o"]
    table.append("| ensemble | " + " | ".join(series) + " | wrong side | wrong amount | wrong person | wrong product |")
    table.append("|---|" + "---|" * (len(series) + 4))
    for e in ens:
        def v(model, s):
            return fig.get((model, e, s), 0)
        hard = {h: sum(v("candidate", f"{h} {s}") for s in SETS) for h in HARD}
        hard_base = {h: sum(v("base", f"{h} {s}") for s in SETS) for h in HARD}
        table.append(f"| {e} | " + " | ".join(f"{v('base', s)} -> {v('candidate', s)} ({v('candidate', s) - v('base', s):+d})" for s in series)
                     + " | " + " | ".join(f"{hard_base[h]} -> {hard[h]}" for h in HARD) + " |")
        for h in HARD:
            if hard[h]:
                where = ", ".join(f"{s} {v('candidate', f'{h} {s}')}" for s in SETS if v("candidate", f"{h} {s}"))
                reasons.append(f"hard zero: {h} is {hard[h]} under ensemble {e} ({where}; base {hard_base[h]})")
        d_owner = (v("candidate", "owner_set") + v("candidate", "owner_1000")) - (v("base", "owner_set") + v("base", "owner_1000"))
        d_held = v("candidate", "heldout strict") - v("base", "heldout strict")
        if d_owner < GAIN_OWNER and d_held < GAIN_HELD:
            reasons.append(f"gain inside the noise under ensemble {e}: owner lists {d_owner:+d} (needs +{GAIN_OWNER}), "
                           f"held-out strict {d_held:+d} (needs +{GAIN_HELD})")
        for s, n in LOSS.items():
            d = v("candidate", s) - v("base", s)
            if d < -n:
                reasons.append(f"loss past the noise under ensemble {e}: {s} {d:+d} (allowed -{n}; rows in compare/)")
    if "313" in ens:
        fl = floors()
        for s in ("owner_set", "owner_1000"):
            if fig.get(("candidate", "313", s), 0) < fl[s]:
                reasons.append(f"floor: {s} {fig.get(('candidate', '313', s), 0)} under the test's floor {fl[s]}")
        r, t = fig.get(("candidate", "313", "heldout strict"), 0), fig.get(("candidate", "313", "heldout strict total"), 0)
        if t and r * 100 < fl["heldout_pct"] * t:
            reasons.append(f"floor: held-out {r}/{t} under {fl['heldout_pct']}%")
    if not junit_ok:
        reasons.append("the seed-safe guard tests or the byte-for-byte model test failed against the candidate (DIR/junit.txt)")
    if not calibrated:
        reasons.append("the certainty table and thresholds were not derived again on the candidate (lesson 165; run without --no-calibrate)")
    return not reasons, reasons, table


def verdict(argv):
    out = Path(_arg(argv, "--out", str(ROOT / "build/hudhud-cycle"))).resolve()
    gate_state = json.loads((out / "gate.json").read_text(encoding="utf-8")) if (out / "gate.json").exists() and \
        (out / "gate.txt").exists() and "GATE REFUSED" not in (out / "gate.txt").read_text(encoding="utf-8") else None
    fig = read_figures(out / "figures.tsv")
    junit = out / "junit.txt"
    junit_ok = junit.exists() and any(l.startswith("OK") for l in junit.read_text(encoding="utf-8").splitlines())
    calib = out / "calibration.md"
    calibrated = calib.exists() and "Reliability" in calib.read_text(encoding="utf-8") and \
        "equals sense.bin byte for byte" in calib.read_text(encoding="utf-8")
    ship, reasons, table = decide(fig, gate_state, junit_ok, calibrated)
    lines = [f"# Hudhud training cycle: {'SHIP' if ship else 'NO-SHIP'}", ""]
    if gate_state:
        lines.append(f"Additions: {gate_state['additions']} ({gate_state['additions_file']}, sha256 {gate_state['additions_sha'][:16]}); "
                     f"recipe {gate_state['recipe']} (shipped {gate_state['base_recipe']}); bank {gate_state['base_lines']} -> "
                     f"{gate_state['candidate_lines']} lines.")
        lines.append("")
    if table:
        lines += ["Base bank -> candidate bank, each learnt the same way (313 = the shipped sense.bin against the candidate's):", ""] + table + [""]
    comp = out / "compare"
    if comp.is_dir():
        lines.append("Miss lists compared sentence by sentence (comm; DIR/compare):")
        for f in sorted(comp.glob("*.tsv")):
            n = sum(1 for l in f.read_text(encoding="utf-8").splitlines() if l.strip())
            lines.append(f"- {f.name}: {n}")
        lines.append("")
    calib_note = out / "calibration-summary.txt"
    if calib_note.exists():
        lines += ["Thresholds (report only, lesson 167):", "", calib_note.read_text(encoding="utf-8").strip(), ""]
    if ship:
        lines.append("SHIP: every gate holds. `scripts/hudhud_cycle.sh ... --apply` writes it; then run the harness, set the floors "
                     "from the figures above and decide every row in compare/*-lost.tsv (promote or re-class) before the push.")
    else:
        lines.append("NO-SHIP:")
        lines += [f"- {r}" for r in reasons]
    text = "\n".join(lines) + "\n"
    (out / "verdict.md").write_text(text, encoding="utf-8")
    print(text)
    return 0 if ship else 1


# ---------------------------------------------------------------------------------------------------------------
# --apply's repository half and the ledger.

def read_ledger():
    if not LEDGER.exists():
        return []
    rows = []
    for raw in LEDGER.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.startswith("#") or raw.split("\t")[0] == "date":
            continue
        rows.append(raw.split("\t"))
    return rows


def ledger_check(argv=None):
    bankmod = _load("gen_hudhud_bank")
    problems = []
    if not LEDGER.exists():
        problems.append(f"{LEDGER.relative_to(ROOT)} is missing")
    else:
        head = [l for l in LEDGER.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")][:1]
        if head != ["\t".join(LEDGER_HEAD)]:
            problems.append(f"{LEDGER.name}: the first line must be the header {LEDGER_HEAD}")
    cache = {}
    for i, row in enumerate(read_ledger(), 1):
        if len(row) != len(LEDGER_HEAD):
            problems.append(f"{LEDGER.name} row {i}: {len(row)} columns")
            continue
        _, _, lang, meaning, source, cls, text = row
        if source not in SOURCES:
            problems.append(f"{LEDGER.name} row {i}: source '{source}'")
        if lang not in cache:
            f = bankmod.MORE / f"{lang}.tsv"
            cache[lang] = set(bankmod.lines_of(f)) if f.exists() else set()
        if (meaning, text) not in cache[lang]:
            problems.append(f"{LEDGER.name} row {i}: [{lang}] {meaning}: '{text}' is not a line of shared/hudhud/more/{lang}.tsv")
    if problems:
        print("\n".join(problems))
        return 1
    print(f"bank additions ledger OK: {len(read_ledger())} rows, each a line of its more/ file")
    return 0


def apply(argv):
    out = Path(_arg(argv, "--out", str(ROOT / "build/hudhud-cycle"))).resolve()
    v = out / "verdict.md"
    if not v.exists() or not v.read_text(encoding="utf-8").startswith("# Hudhud training cycle: SHIP"):
        print("apply: refused: the run's verdict is not SHIP (DIR/verdict.md)")
        return 1
    state = json.loads((out / "gate.json").read_text(encoding="utf-8"))
    bankmod = _load("gen_hudhud_bank")
    if sha(bankmod.OUT) != state["base_bank_sha"]:
        print("apply: refused: the repository's bank.tsv is not the one this run measured (it moved: run the cycle again)")
        return 1
    if sha(state["additions_file"]) != state["additions_sha"]:
        print("apply: refused: the additions file changed after the run (run the cycle again)")
        return 1
    today = date.today().isoformat()
    cycle = state["additions_sha"][:12]
    for n, lang, meaning, text, source, cls in state["rows"]:
        f = bankmod.MORE / f"{lang}.tsv"
        body = f.read_text(encoding="utf-8") if f.exists() else ""
        if body and not body.endswith("\n"):
            body += "\n"
        f.write_text(body + f"{meaning}\t{text}\n", encoding="utf-8")
    if not LEDGER.exists():
        LEDGER.write_text(ledger_header(), encoding="utf-8")
    with LEDGER.open("a", encoding="utf-8") as fh:
        for n, lang, meaning, text, source, cls in state["rows"]:
            fh.write("\t".join([today, cycle, lang, meaning, source, cls, text]) + "\n")
    if state["recipe"] != state["base_recipe"]:
        g = (ROOT / "scripts/gen_hudhud_bank.py")
        s = g.read_text(encoding="utf-8")
        s2 = re.sub(r"^DEFAULT_RECIPE = \d+$", f"DEFAULT_RECIPE = {state['recipe']}", s, count=1, flags=re.M)
        if s2 == s:
            print("apply: DEFAULT_RECIPE not found in gen_hudhud_bank.py")
            return 1
        g.write_text(s2, encoding="utf-8")
    print(f"apply: {len(state['rows'])} sentences added to shared/hudhud/more/ and {LEDGER.relative_to(ROOT)}"
          + (f"; DEFAULT_RECIPE is now {state['recipe']}" if state["recipe"] != state["base_recipe"] else ""))
    return 0


def ledger_header():
    return ("# Where every sentence the training cycle added to shared/hudhud/more/ came from (scripts/hudhud_cycle.sh --apply).\n"
            "# Written only by the cycle; hudhud_cycle.py ledger-check holds each row to a line of its more/ file. Sources:\n"
            "# checklog-class (from the CLASS of a check-log mistake, names invented), synthetic, pair (hudhud_pairs.py).\n"
            + "\t".join(LEDGER_HEAD) + "\n")


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    cmd, rest = argv[0], argv[1:]
    return {"gate": gate, "verdict": verdict, "apply": apply, "ledger-check": ledger_check}.get(
        cmd, lambda _: (print(f"unknown command {cmd}"), 2)[1])(rest)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
