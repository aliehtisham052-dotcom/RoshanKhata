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

    def test_the_shipped_default_is_recipe_1_until_the_retrain(self):
        self.assertEqual(g.DEFAULT_RECIPE, 1)


if __name__ == "__main__":
    unittest.main()
