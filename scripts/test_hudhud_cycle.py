#!/usr/bin/env python3
"""Tests of scripts/hudhud_cycle.py (Marhala 8, 26 Nov 2026): the gate refuses each kind of bad addition, the verdict's SHIP rule
(driven with made-up figures, no training), --apply refuses without a SHIP, the ledger check.   python3 -I scripts/test_hudhud_cycle.py

Everything is written under build/hudhud-cycle-test (git-ignored); the repository's bank, sense.bin and ledger are compared
before and after and must not move. Invented sentences only ({NAME}/{ITEM}, lesson 80)."""
import importlib.util
import io
import json
import shutil
import unittest
from contextlib import redirect_stdout
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build/hudhud-cycle-test"


def _load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


C = _load("hudhud_cycle")
WATCHED = [ROOT / "shared/src/jvmSharedTest/resources/hudhud/bank.tsv", ROOT / "app/src/main/assets/hudhud/sense.bin",
           ROOT / "docs/hudhud-bank-additions.tsv", ROOT / "scripts/gen_hudhud_bank.py"] + \
          sorted((ROOT / "shared/hudhud/more").glob("*.tsv"))


def run_gate(lines, *extra):
    OUT.mkdir(parents=True, exist_ok=True)
    f = OUT / "additions.tsv"
    f.write_text("".join(l + "\n" for l in lines), encoding="utf-8")
    buf = io.StringIO()
    with redirect_stdout(buf):
        rc = _load("hudhud_cycle").gate(["--additions", str(f), "--out", str(OUT / "run"), *extra])
    return rc, buf.getvalue()


def figs(base, cand, hard=0):
    """figures.tsv rows: base and candidate right counts per series for the three ensembles; [hard] wrong sides for the candidate."""
    fig = {}
    for e in ("313", "1000", "2000"):
        for s, b in base.items():
            fig[("base", e, s)] = b
            fig[("candidate", e, s)] = cand.get(s, b)
            fig[("base", e, s + " total")] = fig[("candidate", e, s + " total")] = 1170 if s.startswith("heldout") else 1000
        fig[("candidate", e, "wrong side owner_1000")] = hard
    return fig


BASE = {"heldout strict": 1097, "heldout lenient": 1110, "owner_set": 159, "owner_1000": 813, "review_d": 1040, "review_o": 1070}


class Gate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = {p: p.read_bytes() for p in WATCHED}

    @classmethod
    def tearDownClass(cls):
        for p, b in cls.before.items():
            assert p.read_bytes() == b, f"the cycle's tests changed {p}"
        shutil.rmtree(OUT, ignore_errors=True)

    def test_an_empty_addition_is_no_change(self):
        rc, said = run_gate([])
        self.assertEqual(rc, 3, said)
        self.assertIn("no change", said)

    def test_a_clean_addition_builds_a_candidate_with_it(self):
        rc, said = run_gate(["ur-Latn\tentry_got\t{NAME} ne parson 735 jama karwaye the shaam ko\tsynthetic\tcycle-test"])
        self.assertEqual(rc, 0, said)
        bank = (OUT / "run/res/hudhud/bank.tsv").read_text(encoding="utf-8")
        self.assertIn("entry_got\t{NAME} ne parson 735 jama karwaye the shaam ko\n", bank)
        state = json.loads((OUT / "run/gate.json").read_text(encoding="utf-8"))
        self.assertTrue(state["changed"])
        self.assertEqual(state["candidate_lines"], state["base_lines"] + 1)

    def test_each_kind_of_bad_line_is_refused(self):
        held = (ROOT / "shared/src/jvmSharedTest/resources/hudhud/heldout.tsv").read_text(encoding="utf-8").splitlines()[0].split("\t")
        bank = (ROOT / "shared/src/jvmSharedTest/resources/hudhud/bank.tsv").read_text(encoding="utf-8").splitlines()[0].split("\t")
        owner = (ROOT / "shared/src/jvmSharedTest/resources/hudhud/owner_1000.tsv").read_text(encoding="utf-8").splitlines()
        owner_text = next(l.split("\t")[1] for l in owner if l.strip() and not l.startswith("#"))
        cases = {
            "columns": "ur-Latn\tentry_got\t{NAME} ne 500 diye",
            "language": "xx\tentry_got\t{NAME} ne 500 diye kal\tsynthetic\tc",
            "meaning": "ur-Latn\tno_such_meaning\t{NAME} ne 500 diye kal\tsynthetic\tc",
            "source": "ur-Latn\tentry_got\t{NAME} ne 500 diye kal\twritten\tc",
            "class": "ur-Latn\tentry_got\t{NAME} ne 500 diye kal raat\tchecklog-class\t",
            "phone": "ur-Latn\tparty_phone\t{NAME} ka number 0345 1234567 hai kya\tsynthetic\tc",
            "name": "ur-Latn\tentry_got\tKareem ne 500 diye kal raat\tsynthetic\tc",
            "test-book": "ur-Latn\tentry_got\tsaleem ne 500 diye kal raat\tsynthetic\tc",
            "held-out": f"{held[0]}\t{held[1]}\t{held[2]}\tsynthetic\tc",
            "bank": f"en\t{bank[0]}\t{bank[1]}\tsynthetic\tc",
            "owner": f"ur-Latn\tto_get\t{owner_text}\tsynthetic\tc",
            "script": "ur\tentry_got\t{NAME} ne 500 diye kal raat\tsynthetic\tc",
        }
        for what, line in cases.items():
            rc, said = run_gate([line])
            self.assertEqual(rc, 1, f"{what} was not refused:\n{said}")
            self.assertIn("GATE REFUSED", said, what)

    def test_two_additions_that_are_one_sentence_are_refused(self):
        rc, said = run_gate(["ur-Latn\tentry_got\t{NAME} ne parson 735 jama karwaye\tsynthetic\tc",
                             "ur-Latn\tentry_got\t{NAME} ne parson 735 jama karwaye!\tsynthetic\tc"])
        self.assertEqual(rc, 1, said)
        self.assertIn("the same sentence", said)

    def test_a_near_duplicate_of_a_measured_sentence_is_refused(self):
        held = [l.split("\t") for l in (ROOT / "shared/src/jvmSharedTest/resources/hudhud/heldout.tsv").read_text(encoding="utf-8").splitlines()]
        lang, meaning, text = next(h for h in held if h[0] == "ur-Latn" and len(h[2].split()) >= 6)
        rc, said = run_gate([f"{lang}\t{meaning}\t{text} na\tsynthetic\tc"])
        self.assertEqual(rc, 1, said)
        self.assertIn("near-duplicate", said)

    def test_out_must_be_under_build(self):
        f = OUT / "additions.tsv"
        OUT.mkdir(parents=True, exist_ok=True)
        f.write_text("", encoding="utf-8")
        with redirect_stdout(io.StringIO()):
            self.assertEqual(C.gate(["--additions", str(f), "--out", str(ROOT / "docs")]), 2)


class Verdict(unittest.TestCase):
    STATE = {"changed": True}

    def test_a_gain_above_the_noise_with_hard_zeros_ships(self):
        ship, reasons, _ = C.decide(figs(BASE, {"owner_1000": 830}), self.STATE, True, True)
        self.assertTrue(ship, reasons)

    def test_no_change_never_ships(self):
        ship, reasons, _ = C.decide({}, {"changed": False}, True, True)
        self.assertFalse(ship)
        self.assertIn("no change", reasons[0])

    def test_a_gain_inside_the_noise_does_not_ship(self):
        ship, reasons, _ = C.decide(figs(BASE, {"owner_1000": 820, "heldout strict": 1101}), self.STATE, True, True)
        self.assertFalse(ship)
        self.assertTrue(any("inside the noise" in r for r in reasons), reasons)

    def test_a_wrong_money_side_does_not_ship_whatever_the_gain(self):
        ship, reasons, _ = C.decide(figs(BASE, {"owner_1000": 850}, hard=1), self.STATE, True, True)
        self.assertFalse(ship)
        self.assertTrue(any("hard zero: wrong side" in r for r in reasons), reasons)

    def test_a_loss_past_the_noise_does_not_ship(self):
        ship, reasons, _ = C.decide(figs(BASE, {"owner_1000": 850, "review_o": 1050}), self.STATE, True, True)
        self.assertFalse(ship)
        self.assertTrue(any("loss past the noise" in r and "review_o" in r for r in reasons), reasons)

    def test_the_gain_must_hold_under_every_ensemble(self):
        fig = figs(BASE, {"owner_1000": 830})
        fig[("candidate", "2000", "owner_1000")] = 815
        ship, reasons, _ = C.decide(fig, self.STATE, True, True)
        self.assertFalse(ship)
        self.assertTrue(any("ensemble 2000" in r for r in reasons), reasons)

    def test_one_ensemble_alone_does_not_ship(self):
        fig = {k: v for k, v in figs(BASE, {"owner_1000": 830}).items() if k[1] == "313"}
        ship, reasons, _ = C.decide(fig, self.STATE, True, True)
        self.assertFalse(ship)

    def test_failed_guards_or_no_calibration_do_not_ship(self):
        self.assertFalse(C.decide(figs(BASE, {"owner_1000": 830}), self.STATE, False, True)[0])
        self.assertFalse(C.decide(figs(BASE, {"owner_1000": 830}), self.STATE, True, False)[0])

    def test_the_floors_are_read_from_the_tests(self):
        f = C.floors()
        self.assertGreater(f["owner_set"], 100)
        self.assertGreater(f["owner_1000"], 700)
        self.assertGreater(f["heldout_pct"], 50)


class Apply(unittest.TestCase):
    def test_apply_refuses_without_a_ship_verdict(self):
        d = OUT / "noship"
        d.mkdir(parents=True, exist_ok=True)
        (d / "verdict.md").write_text("# Hudhud training cycle: NO-SHIP\n", encoding="utf-8")
        with redirect_stdout(io.StringIO()):
            self.assertEqual(C.apply(["--out", str(d)]), 1)

    def test_the_ledger_is_consistent(self):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(C.ledger_check([]), 0)


if __name__ == "__main__":
    unittest.main()
