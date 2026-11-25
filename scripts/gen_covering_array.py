#!/usr/bin/env python3
"""The pairwise covering array of languages, trades, platforms, text sizes and themes (plan B4, 24 Nov 2026).

Nine languages x 25 trades x 2 platforms x 3 text sizes x 2 themes is 2,700 looks: nobody can draw them all. This writes
docs/covering-array.tsv, a short list of rows (about 250) in which

  * every PAIR of values of two different factors appears in at least one row (language x trade, language x platform,
    trade x text size, trade x theme, size x theme, ...); and
  * the money trades (AGRI, MEDICAL, KIRYANA, WHOLESALE: the shops whose books carry credit, batches and stock value) are
    weighted: every (language, money trade) is also met with every platform, every text size and every theme, as TRIPLES.

Each row also carries a country (one of the ShopCountry values, chosen by the least used first among the countries a
language is plausibly used in, so every country, and each of the 0, 2 and 3 decimal currencies, is met) and says which of
the repository's CI proofs already DRAW the row's look (a screenshot), or only MEASURE or OPEN it, or nothing does
(PROOFS below; each entry's needle is checked against its file by --check, so a renamed test shows up here).

The generator is deterministic (no random numbers, no set order): the same sources give the same file.

  scripts/gen_covering_array.py            write docs/covering-array.tsv
  scripts/gen_covering_array.py --check    fail when the file is not what the sources give, when a pair or triple is not
                                           covered, when the trades or countries drifted from Trade.kt, TradeKit.kt or
                                           ShopCountry.kt, or when a proof's file or needle is gone (Strings CI may run it)
  scripts/gen_covering_array.py --report   print the coverage by proof (what is drawn, what is not)

Run from the repository root. Drift protection, not proof (lesson 125): it holds the list to the code, it cannot say that a
named test really draws what its entry says.
"""
import itertools
import os
import re
import sys

TSV = "docs/covering-array.tsv"
TRADE_KT = "app/src/main/java/com/innovation313/roshankhata/data/Trade.kt"
TRADE_KIT = "desktop/src/main/kotlin/com/innovation313/roshankhata/desktop/TradeKit.kt"
COUNTRY_KT = "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/ShopCountry.kt"

LANGS = ["en", "ur", "ur-Latn", "sd", "fa", "ar", "hi", "bn", "in"]
PLATFORMS = ["phone", "windows"]
SIZES = [85, 100, 150]
THEMES = ["day", "night"]
MONEY_TRADES = ["AGRI", "MEDICAL", "KIRYANA", "WHOLESALE"]
FACTORS = ["language", "trade", "platform", "size", "theme"]

# The countries a language is plausibly used in (a row's country is taken from its language's list). The union must be every
# ShopCountry; --check fails otherwise. English and Urdu shops exist in many countries, Sindhi, Hindi and Bengali in few.
COUNTRIES = {
    "en": ["PK", "IN", "BD", "US", "GB", "CA", "AU", "NG", "KE", "ZA", "AE", "MY", "NP", "FJ", "TR", "SA", "QA", "KW", "OM", "BH", "", "ID", "AF"],
    "ur": ["PK", "IN", "AE", "SA", "GB", "AF", "QA", "OM", "KW", "BH"],
    "ur-Latn": ["PK", "IN", "AE", "SA", "GB", "BD", "NP", "OM", "KW"],
    "sd": ["PK", "IN", "AE", "SA"],
    "fa": ["IR", "AF", "TJ", "PK"],
    "ar": ["SA", "AE", "QA", "OM", "KW", "BH", "EG", "IQ", "JO", "LB", "SY", "PS", "YE", "MA", "DZ", "TN", "LY", "SD", "MR"],
    "hi": ["IN", "NP", "FJ", "PK", "AE"],
    "bn": ["BD", "IN", "AE", "MY"],
    "in": ["ID", "MY"],
}

# The CI proofs. kind: PICTURE (a screenshot is made), MEASURED (the layout is laid out and measured, no picture) or OPENS
# (the screen is opened and checked not to crash or show an unfilled word). A look matches an entry when its platform,
# language, size and theme are in the entry's sets and its trade is in the entry's trades (ANY = the tests set no trade,
# which reads as AGRI, Trade.DEFAULT, so it is listed as AGRI). `needle` must stand in `file`.
PH = "app/src/androidTest/java/com/innovation313/roshankhata/"
WD = "desktop/src/test/kotlin/com/innovation313/roshankhata/desktop/"
PROOFS = [
    # id, kind, platform, languages, sizes, themes, trades, file, needle
    ("PH-LOOK1", "PICTURE", "phone", {"en"}, {150}, {"day"}, {"KIRYANA"}, PH + "ScreenProofPhoneTest.kt", "en-150-day-kiryana"),
    ("PH-LOOK2", "PICTURE", "phone", {"ur"}, {150}, {"night"}, {"AGRI"}, PH + "ScreenProofPhoneTest.kt", "ur-150-night-agri"),
    ("PH-LOOK3", "PICTURE", "phone", {"bn"}, {100}, {"night"}, {"CLOTHING"}, PH + "ScreenProofPhoneTest.kt", "bn-100-night-clothing"),
    ("PH-LOOK4", "PICTURE", "phone", {"in"}, {85}, {"day"}, {"MEDICAL"}, PH + "ScreenProofPhoneTest.kt", "in-85-day-medical"),
    # ScreenProofLangTest (R9-B4): the nine languages x 85/100/150% text, day and night alternating, one trade a look (the
    # picker's order); i = 3 * language + size, night when i is odd, trade i % 25. Six screens a look, 162 pictures.
    ("PH-LANG01", "PICTURE", "phone", {"en"}, {85}, {"day"}, {"GENERAL"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG02", "PICTURE", "phone", {"en"}, {100}, {"night"}, {"KIRYANA"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG03", "PICTURE", "phone", {"en"}, {150}, {"day"}, {"WHOLESALE"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG04", "PICTURE", "phone", {"ur"}, {85}, {"night"}, {"MEDICAL"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG05", "PICTURE", "phone", {"ur"}, {100}, {"day"}, {"MOBILE"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG06", "PICTURE", "phone", {"ur"}, {150}, {"night"}, {"CLOTHING"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG07", "PICTURE", "phone", {"ur-Latn"}, {85}, {"day"}, {"ELECTRONICS"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG08", "PICTURE", "phone", {"ur-Latn"}, {100}, {"night"}, {"HARDWARE"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG09", "PICTURE", "phone", {"ur-Latn"}, {150}, {"day"}, {"AGRI"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG10", "PICTURE", "phone", {"sd"}, {85}, {"night"}, {"DAIRY"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG11", "PICTURE", "phone", {"sd"}, {100}, {"day"}, {"BAKERY"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG12", "PICTURE", "phone", {"sd"}, {150}, {"night"}, {"MEAT"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG13", "PICTURE", "phone", {"fa"}, {85}, {"day"}, {"PRODUCE"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG14", "PICTURE", "phone", {"fa"}, {100}, {"night"}, {"RESTAURANT"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG15", "PICTURE", "phone", {"fa"}, {150}, {"day"}, {"COSMETICS"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG16", "PICTURE", "phone", {"ar"}, {85}, {"night"}, {"FOOTWEAR"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG17", "PICTURE", "phone", {"ar"}, {100}, {"day"}, {"STATIONERY"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG18", "PICTURE", "phone", {"ar"}, {150}, {"night"}, {"TAILOR"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG19", "PICTURE", "phone", {"hi"}, {85}, {"day"}, {"SALON"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG20", "PICTURE", "phone", {"hi"}, {100}, {"night"}, {"AUTO"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG21", "PICTURE", "phone", {"hi"}, {150}, {"day"}, {"FURNITURE"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG22", "PICTURE", "phone", {"bn"}, {85}, {"night"}, {"BUILDING"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG23", "PICTURE", "phone", {"bn"}, {100}, {"day"}, {"SOLAR"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG24", "PICTURE", "phone", {"bn"}, {150}, {"night"}, {"FUEL"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG25", "PICTURE", "phone", {"in"}, {85}, {"day"}, {"OTHER"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG26", "PICTURE", "phone", {"in"}, {100}, {"night"}, {"GENERAL"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-LANG27", "PICTURE", "phone", {"in"}, {150}, {"day"}, {"KIRYANA"}, PH + "ScreenProofLangTest.kt", "fun looks()"),
    ("PH-STORE", "PICTURE", "phone", {"en", "ur", "ar", "fa", "hi", "bn", "in"}, {100}, {"day"}, {"AGRI"}, PH + "StoreScreenshotsTest.kt",
     "fun languages()"),
    ("PH-HOMEFIT", "MEASURED", "phone", set(LANGS), {100, 150}, {"day"}, {"AGRI"}, PH + "HomeTilesFitTest.kt", "TextSize.LARGEST"),
    ("PH-PARTYFIT", "MEASURED", "phone", set(LANGS), {100, 150}, {"day"}, {"AGRI"}, PH + "PartyHeaderFitTest.kt", "TextSize.LARGEST"),
    ("PH-OPENS-EN", "OPENS", "phone", {"en"}, {100, 150}, {"day"}, {"AGRI"}, PH + "EveryScreenOpensTest.kt", "@ largest text"),
    ("PH-OPENS-DARK", "OPENS", "phone", {"en"}, {100}, {"night"}, {"AGRI"}, PH + "EveryScreenOpensTest.kt", "@ dark"),
    ("PH-OPENS-LANGS", "OPENS", "phone", {"ur", "ur-Latn", "sd", "fa", "ar"}, {100}, {"day"}, {"AGRI"}, PH + "EveryScreenOpensTest.kt",
     'LANGUAGES = listOf("ur", "ur-Latn", "sd", "fa", "ar")'),
    ("PH-BROKEN", "MEASURED", "phone", {"en", "ur"}, {150}, {"day"}, {"AGRI"}, PH + "NoBrokenWordsTest.kt", "fun urduLargest()"),
    ("WD-HOME-EN", "PICTURE", "windows", {"en", "ur"}, {100}, {"day", "night"}, {"AGRI"}, WD + "ScreenProofTest.kt",
     "home draws in English and Urdu, light and dark"),
    ("WD-HOME-4", "PICTURE", "windows", {"ar", "hi", "bn", "in"}, {100}, {"day"}, {"AGRI"}, WD + "ScreenProofTest.kt",
     "home draws in Arabic, Hindi, Bengali and Indonesian"),
]

SOURCE_FILES = [TRADE_KT, TRADE_KIT, COUNTRY_KT]


def die(msg):
    print("covering-array: " + msg)
    sys.exit(1)


def read(path):
    if not os.path.exists(path):
        die("missing file " + path + " (run from the repository root)")
    with open(path, encoding="utf-8") as f:
        return f.read()


def trades_from_code():
    """The phone's picker order, from Trade.kt, and the computer's keys, from TradeKit.kt: they must be the same 25."""
    src = read(TRADE_KT)
    m = re.search(r"val PICKER: List<Trade> = listOf\((.*?)\)", src, re.S)
    if not m:
        die("Trade.PICKER not found in " + TRADE_KT)
    picker = re.findall(r"[A-Z]+", m.group(1))
    enum = re.findall(r"^    ([A-Z]+)\(R\.string", src, re.M)
    if sorted(picker) != sorted(enum):
        die("Trade.PICKER %s is not the enum's trades %s" % (sorted(picker), sorted(enum)))
    kit = read(TRADE_KIT)
    k = re.search(r"val KEYS = listOf\((.*?)\)", kit, re.S)
    if not k:
        die("Trades.KEYS not found in " + TRADE_KIT)
    keys = re.findall(r'"([A-Z]+)"', k.group(1))
    if keys != picker:
        die("the computer's Trades.KEYS differs from the phone's Trade.PICKER: %s vs %s" % (keys, picker))
    if len(picker) != 25:
        die("expected 25 trades, found %d" % len(picker))
    return picker


def countries_from_code():
    """code -> (enum name, calling code, currency sign, decimals), in the enum's order."""
    src = read(COUNTRY_KT)
    out = []
    for m in re.finditer(r'^    ([A-Z_]+)\("([A-Z]*)", "(\d*)", "([^"]+)", (\d),', src, re.M):
        out.append((m.group(2), m.group(1), m.group(3), m.group(4), int(m.group(5))))
    if len(out) < 38:
        die("only %d countries parsed from ShopCountry.kt" % len(out))
    return out


def required_tuples(trades):
    """Every pair of two factors' values, then the money trades' triples. A tuple is a tuple of (factor index, value)."""
    values = [LANGS, trades, PLATFORMS, SIZES, THEMES]
    pairs = []
    for a, b in itertools.combinations(range(5), 2):
        for va in values[a]:
            for vb in values[b]:
                pairs.append(((a, va), (b, vb)))
    triples = []
    for lang in LANGS:
        for t in MONEY_TRADES:
            for other in (2, 3, 4):
                for v in values[other]:
                    triples.append(((0, lang), (1, t), (other, v)))
    return pairs + triples


def build_rows(trades):
    values = [LANGS, trades, PLATFORMS, SIZES, THEMES]
    todo = required_tuples(trades)
    uncovered = list(todo)  # in order: the first one is the next seed
    used = [dict((v, 0) for v in vs) for vs in values]
    rows = []
    fill_order = [1, 0, 3, 4, 2]  # trade, language, size, theme, platform: the wide factors first

    def covered_by(row, tup):
        return all(row.get(f) == v for f, v in tup)

    while uncovered:
        seed = uncovered[0]
        row = {f: v for f, v in seed}
        for f in fill_order:
            if f in row:
                continue
            best, best_score = None, None
            for v in values[f]:
                trial = dict(row)
                trial[f] = v
                # tuples this choice completes: the ones fully inside the trial row and touching factor f
                gain = sum(1 for tup in uncovered if any(ff == f for ff, _ in tup) and covered_by(trial, tup))
                # a look that no tuple touches yet is scored by how many tuples it could still serve (pairs with what is set)
                partial = sum(1 for tup in uncovered
                              if any(ff == f and vv == v for ff, vv in tup)
                              and all(ff == f or row.get(ff) == vv for ff, vv in tup))
                score = (gain, partial, -used[f][v], -values[f].index(v))
                if best_score is None or score > best_score:
                    best, best_score = v, score
            row[f] = best
        rows.append(dict(row))
        for f in range(5):
            used[f][row[f]] += 1
        uncovered = [tup for tup in uncovered if not covered_by(row, tup)]
    return rows, todo


def assign_countries(rows, countries):
    """The least used plausible country of the row's language; ties go to the language's own list order."""
    by_code = {c[0]: c for c in countries}
    count = {c[0]: 0 for c in countries}
    for lang, codes in COUNTRIES.items():
        for c in codes:
            if c not in by_code:
                die("COUNTRIES names %r, which ShopCountry does not have" % c)
    union = set(c for codes in COUNTRIES.values() for c in codes)
    missing = [c[1] for c in countries if c[0] not in union]
    if missing:
        die("no language lists these countries: " + ", ".join(missing))
    for row in rows:
        codes = COUNTRIES[row[0]]
        pick = min(codes, key=lambda c: (count[c], codes.index(c)))
        count[pick] += 1
        row["country"] = pick
    return rows


def proofs_for(row):
    plat, lang, size, theme, trade = row[2], row[0], row[3], row[4], row[1]
    pic, other = [], []
    for pid, kind, pplat, langs, sizes, themes, ptrades, _f, _n in PROOFS:
        if pplat == plat and lang in langs and size in sizes and theme in themes and trade in ptrades:
            (pic if kind == "PICTURE" else other).append(pid)
    return pic, other


def render(rows, countries, trades):
    by_code = {c[0]: c for c in countries}
    lines = ["row\tlanguage\ttrade\tplatform\ttext_size\ttheme\tcountry\tcurrency\tdecimals\tmoney\tdrawn\tmeasured\tcoverage"]
    for i, row in enumerate(rows, 1):
        c = by_code[row["country"]]
        pic, other = proofs_for(row)
        cov = "PICTURE" if pic else ("MEASURED" if other else "NONE")
        lines.append("\t".join([
            "R%03d" % i, row[0], row[1], row[2], str(row[3]), row[4], c[1], c[3], str(c[4]),
            "Y" if row[1] in MONEY_TRADES else "N", ";".join(pic) or "-", ";".join(other) or "-", cov]))
    return "\n".join(lines) + "\n"


def parse_file(text):
    lines = text.rstrip("\n").split("\n")
    head = lines[0].split("\t")
    out = []
    for ln in lines[1:]:
        cells = ln.split("\t")
        if len(cells) != len(head):
            die("a row has %d cells, the header %d: %r" % (len(cells), len(head), ln))
        out.append(dict(zip(head, cells)))
    return out


def verify(rows_text, trades, countries):
    """Coverage checked from the file's own text, independent of the generator."""
    rows = parse_file(rows_text)
    values = [LANGS, trades, PLATFORMS, SIZES, THEMES]
    keys = ["language", "trade", "platform", "text_size", "theme"]
    seen = set()
    for r in rows:
        look = (r["language"], r["trade"], r["platform"], int(r["text_size"]), r["theme"])
        for a, b in itertools.combinations(range(5), 2):
            seen.add(((a, look[a]), (b, look[b])))
        if r["trade"] in MONEY_TRADES:
            for other in (2, 3, 4):
                seen.add(((0, look[0]), (1, look[1]), (other, look[other])))
    problems = [t for t in required_tuples(trades) if t not in seen]
    if problems:
        die("%d required pairs or triples are not covered, first: %r" % (len(problems), problems[0]))
    looks = [(r["language"], r["trade"], r["platform"], r["text_size"], r["theme"]) for r in rows]
    if len(set(looks)) != len(looks):
        die("a look appears twice")
    for f, vals in zip(keys, values):
        have = set(r[f] if f != "text_size" else int(r[f]) for r in rows)
        if have != set(vals):
            die("factor %s: values %s are not exactly %s" % (f, sorted(have, key=str), sorted(vals, key=str)))
    named = {c[1] for c in countries}
    got = {r["country"] for r in rows}
    if got != named:
        die("countries not met: %s" % sorted(named - got))
    for r in rows:
        if r["country"] not in {c[1] for c in countries if c[0] in COUNTRIES[r["language"]]}:
            die("row %s: country %s is not one of %s's" % (r["row"], r["country"], r["language"]))
    for dec in ("0", "2", "3"):
        if not any(r["decimals"] == dec for r in rows):
            die("no row with a %s-decimal currency" % dec)
    return rows


def check_proofs():
    for pid, _k, _p, _l, _s, _t, _tr, path, needle in PROOFS:
        text = read(path)
        if needle not in text:
            die("proof %s: %r is no longer in %s" % (pid, needle, path))


def report(rows):
    n = len(rows)
    by = {"PICTURE": 0, "MEASURED": 0, "NONE": 0}
    for r in rows:
        by[r["coverage"]] += 1
    print("rows: %d   drawn (picture): %d   measured or opened only: %d   not drawn anywhere: %d" % (
        n, by["PICTURE"], by["MEASURED"], by["NONE"]))
    for pid, kind, *_rest in PROOFS:
        c = sum(1 for r in rows if pid in r["drawn"].split(";") or pid in r["measured"].split(";"))
        print("  %-15s %-8s covers %3d rows" % (pid, kind, c))
    for factor in ("language", "trade", "platform", "text_size", "theme"):
        tally = {}
        for r in rows:
            t = tally.setdefault(r[factor], [0, 0, 0])
            t[{"PICTURE": 0, "MEASURED": 1, "NONE": 2}[r["coverage"]]] += 1
        print("  by %s: %s" % (factor, ", ".join("%s %d/%d/%d" % (k, *v) for k, v in sorted(tally.items(), key=lambda kv: kv[0]))))
    print("  (picture / measured-only / none)")


def proof_pairs(trades, kinds):
    """The required pairs that some proof of one of [kinds] covers by itself (its own looks, expanded)."""
    seen = set()
    for _pid, kind, plat, langs, sizes, themes, ptrades, _f, _n in PROOFS:
        if kind not in kinds:
            continue
        for look in itertools.product(sorted(langs), sorted(ptrades), [plat], sorted(sizes), sorted(themes)):
            for a, b in itertools.combinations(range(5), 2):
                seen.add(((a, look[a]), (b, look[b])))
    return seen


def report_pairs(trades):
    pairs = [t for t in required_tuples(trades) if len(t) == 2]
    for label, kinds in (("drawn by a picture", {"PICTURE"}), ("drawn, measured or opened", {"PICTURE", "MEASURED", "OPENS"})):
        seen = proof_pairs(trades, kinds)
        hit = [t for t in pairs if t in seen]
        print("pairs %s by CI: %d of %d" % (label, len(hit), len(pairs)))
        for a, b in itertools.combinations(range(5), 2):
            group = [t for t in pairs if t[0][0] == a and t[1][0] == b]
            got = sum(1 for t in group if t in seen)
            print("    %-9s x %-9s %3d of %3d" % (FACTORS[a], FACTORS[b], got, len(group)))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else ""
    if mode not in ("", "--check", "--report"):
        die("usage: gen_covering_array.py [--check|--report]")
    trades = trades_from_code()
    countries = countries_from_code()
    rows, _ = build_rows(trades)
    assign_countries(rows, countries)
    text = render(rows, countries, trades)
    if mode == "--check":
        check_proofs()
        have = read(TSV)
        if have != text:
            die("%s is not what the sources give: run scripts/gen_covering_array.py and commit it" % TSV)
        parsed = verify(have, trades, countries)
        print("covering array OK: %d rows, every pair and every money-trade triple covered, all %d countries met" % (
            len(parsed), len(countries)))
        return
    if mode == "--report":
        report(parse_file(text))
        report_pairs(trades)
        return
    with open(TSV, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    verify(text, trades, countries)
    print("wrote %s: %d rows" % (TSV, len(rows)))


if __name__ == "__main__":
    main()
