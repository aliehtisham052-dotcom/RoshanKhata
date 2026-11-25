#!/usr/bin/env python3
"""Tests for scripts/hudhud_checklog.py (Marhala 2). Invented data only (lessons 80, 120): the names are made up
(Zarnosh, Qeemat, Parwaiz), the phone numbers are the invented family of check_invented_numbers.py (0300 1234567,
0300 0000001), and no row comes from the owner's log.

    python3 scripts/test_hudhud_checklog.py
"""
import contextlib
import hashlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("hudhud_checklog", HERE / "hudhud_checklog.py")
cl = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cl)

HEADER = "no\ttime\tlanguage\tquestion\ttaken_as\tanswer\tmark\tbusiness"

# A small vocabulary, so the masking tests do not move when the bank grows.
SENTENCES = [
    ("ur-Latn", "{NAME} ka kitna baqaya hai"), ("ur-Latn", "aaj ki sale kitni hui"),
    ("ur-Latn", "{NAME} ko 500 diye"), ("ur-Latn", "uska number kya hai"),
    ("en", "what is the balance of {NAME}"), ("en", "how much did I sell today"),
    ("en", "Ask Hudhud about Zakat"),  # "Zakat" is capitalised mid-sentence: a name to the bank, never vocabulary
    ("ur", "{NAME} کا کتنا بقایا ہے"), ("hi", "आज की बिक्री कितनी है"), ("id", "penjualan hari ini berapa"),
]
PER, VOCAB = cl.vocabulary(SENTENCES, names={"ali"})


def row(no, question, taken_as="person: BALANCE [12]", answer="Zarnosh Traders · Rs 4,321 · owes you", mark="wrong",
        language="ur-PK", business="1"):
    return "\t".join([str(no), "2026-11-27 10:00", language, question, taken_as, answer, mark, business])


def log_text(*rows, header=HEADER):
    return "﻿" + "\n".join([header, *rows]) + "\n"


class ReadingTheAppsFile(unittest.TestCase):

    def test_the_apps_own_export_is_read(self):
        raw = log_text(row(1, "Zarnosh ka kitna baqaya hai"), row(2, "aaj ki sale kitni hui", mark="right"),
                       row(3, "uska number kya hai", mark="")).encode("utf-8")
        text, quoted = cl.decode(raw)
        rows, malformed = cl.parse(text, quoted)
        self.assertFalse(quoted)
        self.assertEqual(0, malformed)
        self.assertEqual(["1", "2", "3"], [r["no"] for r in rows])
        self.assertEqual(["wrong", "right", ""], [r["mark"] for r in rows])
        self.assertEqual("", rows[0]["expected"])

    def test_an_older_row_of_seven_fields_has_no_business(self):
        old = "\t".join(["4", "2026-11-15 09:00", "ur", "aaj ki sale kitni hui", "figure: SALES", "Sales", "wrong"])
        rows, malformed = cl.parse(log_text(old))
        self.assertEqual(0, malformed)
        self.assertEqual("", rows[0]["business"])
        self.assertEqual("wrong", rows[0]["mark"])

    def test_a_line_that_is_not_a_row_is_counted_not_read(self):
        rows, malformed = cl.parse(log_text(row(1, "aaj ki sale kitni hui"), "half a line\tonly", "x\ty\tz\tw\tv\tu\tt"))
        self.assertEqual(1, len(rows))
        self.assertEqual(2, malformed)

    def test_a_quote_in_a_question_does_not_join_rows(self):
        rows, malformed = cl.parse(log_text(row(1, 'he said "baqaya'), row(2, "aaj ki sale kitni hui")))
        self.assertEqual(2, len(rows))
        self.assertEqual(0, malformed)

    def test_an_expected_column_the_owner_added_is_read(self):
        header = HEADER + "\texpected"
        rows, _ = cl.parse(log_text(row(1, "aaj ki sale kitni hui") + "\taaj ki sale", header=header))
        self.assertEqual("aaj ki sale", rows[0]["expected"])
        # A ninth column with no heading is read the same way.
        rows, _ = cl.parse(log_text(row(1, "aaj ki sale kitni hui") + "\tbalance"))
        self.assertEqual("balance", rows[0]["expected"])

    def test_a_spreadsheets_unicode_text_is_read(self):
        raw = (HEADER + "\n" + row(1, "Zarnosh ka kitna baqaya hai") + "\n").encode("utf-16")
        text, quoted = cl.decode(raw)
        rows, _ = cl.parse(text, quoted)
        self.assertTrue(quoted)
        self.assertEqual("Zarnosh ka kitna baqaya hai", rows[0]["question"])


class Masking(unittest.TestCase):

    def test_the_customer_the_card_named_is_a_name(self):
        card = cl.card_name("person: BALANCE [12]", "Zarnosh Traders · Rs 4,321 · owes you")
        self.assertEqual(("Zarnosh Traders", "NAME"), card)
        masked, look, _ = cl.mask("zarnosh ka kitna baqaya hai", VOCAB, card)
        self.assertEqual("{NAME1} ka kitna baqaya hai", masked)
        self.assertEqual(set(), look)

    def test_a_guess_or_now_still_names_its_customer(self):
        self.assertEqual(("Qeemat Brothers", "NAME"),
                         cl.card_name("guessed > now > person: DETAILS [3]", "Qeemat Brothers · 0300 1234567"))
        self.assertEqual(("Urea", "ITEM"), cl.card_name("goods: RATE [101]", "Urea · Rs 3,900"))
        self.assertIsNone(cl.card_name("figure: SALES", "Today's sales · Rs 900"))

    def test_a_word_hudhud_does_not_know_is_masked_and_flagged(self):
        masked, look, n = cl.mask("Parwaiz ka kitna baqaya hai", VOCAB)
        self.assertEqual("{X1} ka kitna baqaya hai", masked)
        self.assertIn("word not in Hudhud's vocabulary", look)
        self.assertEqual(1, n)

    def test_the_same_word_twice_is_one_placeholder(self):
        masked, _, _ = cl.mask("parwaiz parwaiz qeemat", VOCAB)
        self.assertEqual("{X1} {X1} {X2}", masked)

    def test_a_capitalised_word_inside_a_latin_sentence_is_masked(self):
        # "sale" is vocabulary, but written "Sale" mid-sentence it may be a name the recogniser capitalised.
        masked, look, _ = cl.mask("aaj ki Sale kitni hui", VOCAB)
        self.assertEqual("aaj ki {X1} kitni hui", masked)
        self.assertIn("capitalised word", look)

    def test_an_invented_test_name_is_never_vocabulary(self):
        self.assertNotIn("ali", VOCAB)
        self.assertNotIn("zakat", VOCAB)
        self.assertIn("hudhud", VOCAB)

    def test_phone_numbers_and_long_numbers_go_short_amounts_stay(self):
        masked, _, _ = cl.mask("uska number 0300 1234567 hai", VOCAB)
        self.assertEqual("uska number {PHONE} hai", masked)
        masked, _, _ = cl.mask("uska number +92-300-0000001 hai", VOCAB)
        self.assertEqual("uska number {PHONE} hai", masked)
        masked, _, _ = cl.mask("aaj ki sale 1,25,000 hui", VOCAB)
        self.assertEqual("aaj ki sale {NUM} hui", masked)
        masked, _, _ = cl.mask("ko 500 diye", VOCAB)
        self.assertEqual("ko 500 diye", masked)

    def test_urdu_and_hindi_digits_are_numbers_too(self):
        masked, _, _ = cl.mask("uska number ۰۳۰۰ ۱۲۳۴۵۶۷ hai", VOCAB)
        self.assertEqual("uska number {PHONE} hai", masked)
        masked, _, _ = cl.mask("आज की बिक्री १२३४५ है", VOCAB)
        self.assertEqual("आज की बिक्री {NUM} है", masked)

    def test_the_card_figure_is_always_masked(self):
        masked, _, _ = cl.mask("Zarnosh Traders · Rs 4,321", VOCAB, ("Zarnosh Traders", "NAME"), mask_all_numbers=True)
        self.assertEqual("{NAME1} {NAME2} · {X1} {NUM}", masked)

    def test_an_arabic_script_name_from_the_card(self):
        card = ("زرنوش ٹریڈرز", "NAME")
        masked, look, _ = cl.mask("زرنوش کا کتنا بقایا ہے", VOCAB, card)
        self.assertEqual("{NAME1} کا کتنا بقایا ہے", masked)
        self.assertEqual(set(), look)

    def test_the_owners_shop_is_masked_by_its_fingerprint(self):
        # The guard keeps fingerprints of two lower-case words; an invented shop stands in for the real one here.
        mark = hashlib.sha256("zarnosh brothers".encode()).hexdigest()[:16]
        masked, _, _ = cl.mask("zarnosh brothers ka kitna baqaya hai", VOCAB, shop_marks={mark})
        self.assertEqual("{SHOP} ka kitna baqaya hai", masked)

    def test_devanagari_vowel_signs_stay_inside_the_word(self):
        self.assertEqual(["आज", "की", "बिक्री"], ["आज की बिक्री"[a:b] for a, b in cl.tokens("आज की बिक्री")])


class Guesses(unittest.TestCase):

    def test_language_from_letters_and_words(self):
        self.assertEqual(("Latn", "ur-Latn"), cl.guess_language("aaj ki sale kitni hui", PER))
        self.assertEqual(("Latn", "en"), cl.guess_language("how much did I sell today", PER))
        self.assertEqual(("Latn", "id"), cl.guess_language("penjualan hari ini berapa", PER))
        self.assertEqual(("Arab", "ur"), cl.guess_language("کتنا بقایا ہے", PER))
        self.assertEqual(("Deva", "hi"), cl.guess_language("आज की बिक्री", PER))
        self.assertEqual(("Arab", "sd"), cl.guess_language("ڪيترو", PER))
        self.assertEqual(("none", "unknown"), cl.guess_language("500", PER))

    def test_mixed_script_is_said(self):
        script, _ = cl.guess_language("aaj کتنا بقایا", PER)
        self.assertTrue(script.startswith("mixed:"))

    def test_what_hudhud_read_loses_the_books_ids(self):
        self.assertEqual("person: BALANCE", cl.read_class("person: BALANCE [12, 40]"))
        self.assertEqual("guessed > balance:", cl.read_class("guessed > balance: [7]"))
        self.assertEqual("choices: person: BALANCE | goods: RATE",
                         cl.read_class("choices: person: BALANCE [1] | goods: RATE [101]"))
        self.assertEqual("(empty)", cl.read_class(""))


class Triage(unittest.TestCase):

    ROWS = [
        row(1, "Zarnosh ka kitna baqaya hai"),
        row(2, "Taimoorzai ka kitna baqaya hai", taken_as="person: BALANCE [12]", answer="Zarnosh Traders · Rs 9"),
        row(3, "aaj ki sale kitni hui", taken_as="figure: SALES", answer="Sales · Rs 900", mark="right"),
        row(4, "uska number 0300 1234567", taken_as="error", answer="", mark="wrong"),
        row(5, "parwaiz ko 500 diye", taken_as="talk: HELLO", answer="Hello", mark=""),
    ]

    def setUp(self):
        self.rows, _ = cl.parse(log_text(*self.ROWS))

    def test_only_wrong_rows_by_default_and_right_ones_when_asked(self):
        written, _ = cl.triage(self.rows, PER, VOCAB)
        self.assertEqual(["1", "2", "4"], [r["no"] for r in written])
        written, _ = cl.triage(self.rows, PER, VOCAB, all_marked=True)
        self.assertEqual(["1", "2", "3", "4"], [r["no"] for r in written])

    def test_one_reading_is_one_class(self):
        written, classes = cl.triage(self.rows, PER, VOCAB)
        top_key, top = next(iter(classes.items()))
        self.assertEqual(("-", "person: BALANCE"), top_key)
        self.assertEqual(2, top["count"])
        self.assertEqual(["1", "2"], top["rows"])
        # Row 2: Hudhud gave Zarnosh's card for a question about somebody else — the question's name is not on the
        # card, so it is an unknown word and the row asks for a look.
        self.assertEqual("{X1} ka kitna baqaya hai", written[1]["question_masked"])
        self.assertIn("vocabulary", written[1]["needs_look"])
        self.assertIn("the card's name is not in the question", written[1]["needs_look"])
        self.assertIn("error", written[2]["needs_look"])
        self.assertEqual("uska number {PHONE}", written[2]["question_masked"])

    def test_an_expected_meaning_splits_the_class(self):
        header = HEADER + "\texpected"
        rows, _ = cl.parse(log_text(row(1, "aaj ki sale kitni hui") + "\tsale", row(2, "aaj ki sale kitni hui") + "\t",
                                    header=header))
        _, classes = cl.triage(rows, PER, VOCAB)
        self.assertEqual({("sale", "person: BALANCE"), ("-", "person: BALANCE")}, set(classes))

    def test_the_real_question_is_written_only_when_asked(self):
        written, _ = cl.triage(self.rows, PER, VOCAB)
        self.assertNotIn("question_PRIVATE", written[0])
        written, _ = cl.triage(self.rows, PER, VOCAB, with_original=True)
        self.assertEqual("Zarnosh ka kitna baqaya hai", written[0]["question_PRIVATE"])


class TheCommand(unittest.TestCase):

    def run_main(self, argv):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = cl.main(argv)
        return code, out.getvalue()

    def test_a_folder_inside_the_repository_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            log = Path(d) / "hudhud_check_20261127_1000.tsv"
            log.write_text(log_text(row(1, "Zarnosh ka kitna baqaya hai")), encoding="utf-8")
            with self.assertRaises(SystemExit) as e:
                cl.main([str(log), "--out", str(cl.ROOT / "build" / "triage")])
            self.assertIn("OUTSIDE", str(e.exception))
            self.assertFalse((cl.ROOT / "build" / "triage").exists())

    def test_a_log_inside_the_repository_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(SystemExit) as e:
                cl.main([str(cl.ROOT / "scripts" / "hudhud_sets_overlap.tsv"), "--out", d])
            self.assertIn("must not be inside the repository", str(e.exception))

    def test_out_is_required(self):
        with self.assertRaises(SystemExit):
            cl.main(["log.tsv"])

    def test_the_summary_says_counts_and_never_a_sentence(self):
        with tempfile.TemporaryDirectory() as d:
            log = Path(d) / "in" / "hudhud_check_20261127_1000.tsv"
            log.parent.mkdir()
            log.write_text(log_text(*Triage.ROWS), encoding="utf-8")
            code, said = self.run_main([str(log), "--out", str(Path(d) / "out"), "--all-marked"])
            self.assertEqual(0, code)
            self.assertIn("5 answers read, 1 right, 3 wrong, 1 not marked", said)
            for secret in ("Zarnosh", "zarnosh", "Taimoorzai", "parwaiz", "kitna", "1234567", "Traders", "baqaya"):
                self.assertNotIn(secret, said)
            rows_file = Path(d) / "out" / "hudhud_check_20261127_1000.rows.tsv"
            classes_file = Path(d) / "out" / "hudhud_check_20261127_1000.classes.tsv"
            self.assertTrue(rows_file.is_file() and classes_file.is_file())
            written = rows_file.read_text(encoding="utf-8")
            self.assertNotIn("Zarnosh", written)
            self.assertNotIn("Taimoorzai", written)
            self.assertNotIn("1234567", written)
            self.assertEqual(5, len(written.strip().splitlines()))  # header + four marked rows

    def test_a_name_that_is_also_a_word_is_the_known_limit_and_is_flagged(self):
        # "qeemat" (price) is a word of the bank: as a customer's name it is not masked by the vocabulary. When Hudhud
        # answered with another customer's card, the row is still flagged, so Claude reads it in the owner's file.
        per, vocab = cl.vocabulary()
        rows, _ = cl.parse(log_text(row(1, "qeemat ka kitna baqaya hai", answer="Zarnosh Traders · Rs 9")))
        written, _ = cl.triage(rows, per, vocab)
        self.assertIn("the card's name is not in the question", written[0]["needs_look"])

    def test_the_real_bank_is_the_vocabulary(self):
        per, vocab = cl.vocabulary()
        self.assertEqual({"en", "ur", "ur-Latn", "hi", "bn", "ar", "fa", "sd", "id"}, set(per))
        for word in ("kitna", "baqaya", "hai", "کتنا", "है"):
            self.assertIn(cl.fold(word), vocab)
        for name in ("ali", "raza", "bashir", "علی"):
            self.assertNotIn(cl.fold(name), vocab)


if __name__ == "__main__":
    unittest.main(verbosity=1)
