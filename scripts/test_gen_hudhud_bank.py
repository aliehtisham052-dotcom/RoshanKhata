#!/usr/bin/env python3
"""Tests of gen_hudhud_bank.py's two recipes (25 Nov 2026, plan H1, the owed retrain L017):  python3 -I scripts/test_gen_hudhud_bank.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("g", Path(__file__).resolve().parent / "gen_hudhud_bank.py")
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)


class Recipe(unittest.TestCase):
    def test_a_vowel_sign_is_part_of_the_letter_in_recipe_2_only(self):
        for a, b in [("कि", "के"), ("বাড়ি", "বাড়")]:
            self.assertEqual(g.key(a), g.key(b))            # recipe 1 folds them: the fault
            self.assertNotEqual(g.key_indic(a), g.key_indic(b))

    def test_other_marks_and_punctuation_still_fold(self):
        self.assertEqual(g.key_indic("كَتَبَ"), g.key_indic("كتب"))
        self.assertEqual(g.key_indic("Café, WHAT?"), g.key_indic("cafe what"))

    def test_recipe_2_recovers_sentences_and_leaves_out_held_copies(self):
        b1, h1 = g.read(1)
        b2, h2 = g.read(2)
        self.assertEqual(h1, h2)
        self.assertGreaterEqual(len(set(b2) - set(b1)), 43)
        held = {g.key_indic(t) for _, _, t in h2}
        self.assertFalse([b for b in b2 if g.key_indic(b[2]) in held])
        self.assertTrue([b for b in b1 if g.key_indic(b[2]) in held], "recipe 1 is the one that teaches held-out copies")

    def test_coverage_check_is_clean(self):
        self.assertEqual(g.coverage_problems()[0], [])

    def test_the_shipped_default_is_recipe_2_since_h4(self):
        # H4 (25 Nov 2026) flipped it with the new sense.bin; this test still said 1 and failed (BC-393). Recipe 3 is the next
        # retrain's and only scripts/hudhud_cycle.sh --next-key --apply may flip it, with the sense learnt again.
        self.assertEqual(g.DEFAULT_RECIPE, 2)


class Recipe3(unittest.TestCase):
    def test_recipe_3_teaches_no_measured_sentence_and_only_loses_those(self):
        self.assertEqual(g.recipe3_problems()[0], [])

    def test_recipe_3_keeps_the_held_out_set_and_leaves_out_the_listed_overlap(self):
        b2, h2 = g.read(2)
        b3, h3 = g.read(3)
        self.assertEqual(h2, h3)
        gone = {g.key_indic(t) for b in b2 if b not in set(b3) for t in [b[2]]}
        listed = {line.split("\t", 1)[1] for line in (g.ROOT / "scripts/hudhud_sets_overlap.tsv").read_text(encoding="utf-8").splitlines()
                  if line.strip() and not line.startswith("#")}
        # Every listed overlap is a sentence recipe 3 no longer teaches (the list is compared by the shipped key, key_indic).
        self.assertTrue(listed <= gone | {g.key_indic(t) for _, _, t in h2}, sorted(listed - gone)[:5])

    def test_next_key_is_recipe_3_and_writes_only_where_asked(self):
        import subprocess, sys, tempfile
        before = g.OUT.read_bytes()
        with tempfile.TemporaryDirectory() as d:
            r = subprocess.run([sys.executable, "-I", str(g.ROOT / "scripts/gen_hudhud_bank.py"), "--next-key", "--out-dir", d],
                               capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertIn("recipe 3", r.stdout)
            self.assertEqual(Path(d, "bank.tsv").read_text(encoding="utf-8"), g.bank_text(g.read(3)[0]))
        self.assertEqual(g.OUT.read_bytes(), before, "--out-dir must leave the shipped bank alone")

    def test_a_measured_set_missing_is_refused_not_ignored(self):
        with tempfile_dir() as d:
            with self.assertRaises(SystemExit):
                g.measured_keys(d)


def tempfile_dir():
    import tempfile
    return tempfile.TemporaryDirectory()


if __name__ == "__main__":
    unittest.main()
