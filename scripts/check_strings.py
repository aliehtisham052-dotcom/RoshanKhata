#!/usr/bin/env python3
"""The two string failures the Android build does NOT catch.

aapt already fails a build on malformed XML, duplicate keys, and a
translation with no default. What it happily ships:

1. A key present in values/ but MISSING from a locale — the phone falls
   back to English silently, and a Sindhi-speaking shopkeeper meets an
   English sentence in the middle of their own language.
2. A locale translation whose FORMAT PLACEHOLDERS disagree with the
   default (%1$s dropped, %2$d retyped as %2$s) — that one is worse than
   silent: getString(key, args) CRASHES at runtime, only in that
   language, only on that screen.
3. A <plurals> missing a form its language actually uses. Arabic inflects
   a noun six ways by count; leave out "few" and every count from 3 to 10
   silently borrows "other", so "3 شيكات" comes out as "3 شيك" — wrong to
   an Arabic reader in the same way "3 cheque" is wrong to an English
   one. Missing "other" is worse still: that one throws.

Every push runs this so no session — human or Claude — has to remember
to check by hand. Zero dependencies; stdlib only.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
DEFAULT = RES / "values" / "strings.xml"
LOCALES = ["values-b+ur+Latn", "values-ur", "values-sd", "values-fa", "values-ar", "values-in",
           "values-hi", "values-bn"]

# Positional (%1$s, %2$02d) and plain (%d, %s) placeholders alike: 18 plurals
# use a bare %d, and season_rabi a %2$02d, which the old r"%\d+\$[sd]" never
# saw (23 Oct 2026 review). "%%" is a literal percent and is removed first.
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdf]")

# The cardinal plural forms each language actually uses (Unicode CLDR).
# Urdu, Roman Urdu, Sindhi and Persian split at one; Arabic does not.
# Indonesian (values-in, Android's legacy code for "id") has no plural
# inflection at all: one form, "other", for every count.
QUANTITIES = {
    "values": {"one", "other"},
    "values-b+ur+Latn": {"one", "other"},
    "values-ur": {"one", "other"},
    "values-sd": {"one", "other"},
    "values-fa": {"one", "other"},
    "values-ar": {"zero", "one", "two", "few", "many", "other"},
    "values-in": {"other"},
    # Hindi and Bengali: "one" covers 0 and 1, "other" the rest.
    "values-hi": {"one", "other"},
    "values-bn": {"one", "other"},
}


def load(path: Path):
    """name -> (text, translatable) for every <string> in the file."""
    out = {}
    root = ET.parse(path).getroot()
    for s in root.iter("string"):
        name = s.get("name")
        translatable = s.get("translatable", "true") != "false"
        # itertext so placeholders inside nested tags still count
        out[name] = ("".join(s.itertext()), translatable)
    return out


def load_plurals(path: Path):
    """name -> {quantity: text} for every <plurals> in the file."""
    out = {}
    root = ET.parse(path).getroot()
    for p in root.iter("plurals"):
        out[p.get("name")] = {
            i.get("quantity"): "".join(i.itertext()) for i in p.iter("item")
        }
    return out


def shapes(text: str):
    """The SET of placeholder identities, not their repeat counts.

    Repeating a positional placeholder (%2$s used twice in English, once
    in Urdu) is legal in both directions and crashes nothing — the first
    run of this guard flagged exactly that and taught the rule. What DOES
    crash getString at runtime is a locale asking for an index the code
    never supplies, or the same index with a different type (%2$s vs
    %2$d) — and set comparison catches precisely those two.
    """
    return sorted(set(PLACEHOLDER.findall(text.replace("%%", ""))))


# --- What aapt2 and XmlWords do to the text (23 Oct 2026 review) ---
# Outside double quotes, every run of white space (a real line break too)
# becomes one space and the ends are trimmed; an unescaped " opens or closes
# a quoted run and is not shown. So a typed line break, a space meant to
# stand at either end, and a bare " around a name all vanish in silence.

NATIVE_DIGITS = re.compile("[\u0660-\u0669\u06f0-\u06f9\u0966-\u096f\u09e6-\u09ef\u066b]")

# Letters of a neighbouring script typed into a language that does not use
# them (Urdu keyboards in Sindhi, Arabic and Persian files): ھ for ۽ or ۾ in
# Sindhi, Urdu's ہ ھ ے in Arabic and Persian, Arabic's ي ك in Urdu.
WRONG_LETTERS = {
    "values-ur": "\u064a\u0643",                       # ي ك  (Urdu: ی ک)
    "values-sd": "\u06cc",                              # ی    (Sindhi: ي)
    "values-ar": "\u06c1\u06be\u06d2\u06cc\u06a9",  # ہ ھ ے ی ک
    "values-fa": "\u06c1\u06be\u06d2\u064a\u0643",  # ہ ھ ے ي ك  (Persian: ه ی ک)
}
# Where another script is shown on purpose: an Urdu shop name as an example,
# a help line that shows the digits it offers, and the privacy screen's
# strings, which are the owner's to change (review finding R69, out of scope).
SCRIPT_EXEMPT = {"business_name_urdu_hint", "print_native_digits_help"}
SCRIPT_EXEMPT_PREFIX = ("screen_privacy_",)

# The app's own name in each right-to-left language, as app_name spells it;
# Sindhi's oblique (روشن کاتي ۾) is right where the grammar asks for it.
BRAND_FORMS = {"values-sd": {"کاتي"}}
BRAND_LIKE = re.compile(r"روشن\s+((?:[\u06a9\u0643][\u06be\u0647]?ات[\u0627\u06c1\u0647\u0629\u0648\u064a])|(?:خات[\u0627\u06c1\u0647]))(?![\u0600-\u06ff])")

# Roman Urdu: one spelling per word (the file's majority, the review's list).
# A warning, not a failure: a variant is untidy, not broken.
ROMAN_GLOSSARY = {
    "dukan": "dukaan", "mojood": "maujood", "mojooda": "maujooda", "indraj": "indiraaj",
    "wapis": "wapas", "aik": "ek", "dabayen": "dabayein", "grahak": "gahak",
    "dihani": "dehani", "intebah": "intibah",
}


def exempt(name: str) -> bool:
    return name in SCRIPT_EXEMPT or name.startswith(SCRIPT_EXEMPT_PREFIX)


def raw_values(path: Path):
    """(kind, name, text, element) for every <string> and plural <item>, as written."""
    root = ET.parse(path).getroot()
    for el in root:
        if el.tag == "string":
            yield "string", el.get("name"), "".join(el.itertext()), el
        elif el.tag == "plurals":
            for item in el.iter("item"):
                yield "item", f'{el.get("name")}[{item.get("quantity")}]', "".join(item.itertext()), el


def text_failures(folder: str, path: Path, unformatted_defaults: set):
    out = []
    for kind, name, text, el in raw_values(path):
        base = name.split("[")[0]
        whole = len(text) >= 2 and text.startswith('"') and text.endswith('"')
        if not whole and "\n" in text.strip():
            out.append(f"{folder}: REAL LINE BREAK in {name} (aapt2 makes it a space; write \\n)")
        if not whole and text != text.strip():
            out.append(f"{folder}: SPACE AT AN END of {name} is trimmed (wrap the value in double quotes)")
        if not whole and re.search(r'(?<!\\)"', text):
            out.append(f"{folder}: BARE \" in {name} is dropped (use \\\" or the language's own quote marks)")
        if kind == "string" and base in unformatted_defaults and "%%" in text:
            out.append(f"{folder}: %% in {name}, which is never formatted, shows as %% (write one %)")
        if not exempt(base) and NATIVE_DIGITS.search(text):
            out.append(f"{folder}: NATIVE DIGITS in {name} (screens stay 0-9; see Digits)")
        letters = WRONG_LETTERS.get(folder)
        if letters and not exempt(base):
            bad = sorted(set(re.findall("[" + letters + "]", text)))
            if bad:
                out.append(f"{folder}: WRONG-SCRIPT LETTERS {bad} in {name}")
        if folder == "values-sd" and not exempt(base) and re.search(r"(?<![\u0600-\u06ff])\u06be(?![\u0600-\u06ff])", text):
            out.append(f"{folder}: STRAY ھ in {name} (Sindhi writes ۽ for and, ۾ for in)")
    return out


def brand_failures(folder: str, path: Path):
    root = ET.parse(path).getroot()
    app = next(("".join(e.itertext()) for e in root.iter("string") if e.get("name") == "app_name"), None)
    if not app or "روشن" not in app:
        return []
    own = {app.split()[-1]} | BRAND_FORMS.get(folder, set())
    out = []
    for kind, name, text, el in raw_values(path):
        if exempt(name.split("[")[0]):
            continue
        for word in BRAND_LIKE.findall(text):
            if word not in own:
                out.append(f"{folder}: APP NAME spelled \"روشن {word}\" in {name}; app_name is \"{app}\"")
    return out


def glossary_warnings(path: Path):
    out = []
    for kind, name, text, el in raw_values(path):
        plain = text.replace("\\n", " ").replace("\\t", " ")
        for wrong, right in ROMAN_GLOSSARY.items():
            if re.search(r"(?<![A-Za-z])" + wrong + r"(?![A-Za-z])", plain, re.I):
                out.append(f"values-b+ur+Latn: {name} writes \"{wrong}\"; the app writes \"{right}\"")
    return out


def main() -> int:
    default = load(DEFAULT)
    translatable = {k: v[0] for k, v in default.items() if v[1]}

    failures = []

    # The same name twice in ONE file is a build failure (Android's resource
    # merger refuses it: "Found item String/x more than one time"), yet it
    # passes every comparison below, because each locale then has the key.
    # Push 3 (7721d84) shipped exactly that: a new rate_hint beside an old
    # one. Checked first, in the default file and every locale.
    for folder in ["values"] + list(LOCALES):
        path = RES / folder / "strings.xml"
        seen = set()
        for el in ET.parse(path).getroot():
            if el.tag not in ("string", "plurals", "string-array"):
                continue
            key = (el.tag, el.get("name"))
            if key in seen:
                failures.append(f"{folder}: DUPLICATE <{el.tag} name=\"{key[1]}\">")
            seen.add(key)

    # An apostrophe must be written \\' in Android strings (unless the whole
    # text is in double quotes). A bare ' passes XML parsing and every check
    # above, then fails aapt2 at build time ("Can not extract resource").
    # Push 6 (b480b5f) shipped two: "Last time's items", "%1$s's".
    for folder in ["values"] + list(LOCALES):
        path = RES / folder / "strings.xml"
        for el in ET.parse(path).getroot().iter("string"):
            text = "".join(el.itertext())
            if len(text) >= 2 and text.startswith('"') and text.endswith('"'):
                continue
            if re.search(r"(?<!\\)'", text):
                failures.append(f"{folder}: UNESCAPED APOSTROPHE in <string name=\"{el.get('name')}\">")

    # What the resource compiler would silently change (23 Oct 2026 review):
    # line breaks, edge spaces, bare quotes, %% in plain text, native digits,
    # letters of the wrong script, and the app's own name spelled otherwise.
    unformatted = {k for k, v in default.items() if not shapes(v[0])}
    for folder in ["values"] + list(LOCALES):
        path = RES / folder / "strings.xml"
        failures.extend(text_failures(folder, path, unformatted))
        failures.extend(brand_failures(folder, path))
    warnings = glossary_warnings(RES / "values-b+ur+Latn" / "strings.xml")

    for folder in LOCALES:
        path = RES / folder / "strings.xml"
        locale = load(path)
        for key, text in sorted(translatable.items()):
            if key not in locale:
                failures.append(f"{folder}: MISSING <string name=\"{key}\">")
                continue
            want, got = shapes(text), shapes(locale[key][0])
            if want != got:
                failures.append(
                    f"{folder}: PLACEHOLDER MISMATCH {key}: default {want} vs {got}"
                )

    # --- string-arrays (values*/arrays.xml) ---
    # Suggestion lists live in arrays.xml, which nothing above reads. Three
    # languages were added (Indonesian, Hindi, Bengali, 4 Oct) with every
    # string translated and no arrays.xml at all, so their Cashbook offered
    # "Cash Sale" and "Rent" in English. The rule: an array that ANY locale
    # translates must be in EVERY locale, with the default's number of items.
    # An array no locale translates (units, kept as saved keys on purpose) is
    # left alone.
    def arrays(path):
        if not path.exists():
            return {}
        return {
            el.get("name"): len(el.findall("item"))
            for el in ET.parse(path).getroot()
            if el.tag == "string-array"
        }

    default_arrays = arrays(RES / "values" / "arrays.xml")
    per_locale = {folder: arrays(RES / folder / "arrays.xml") for folder in LOCALES}
    translated = {name for found in per_locale.values() for name in found}
    for name in sorted(translated):
        if name not in default_arrays:
            failures.append(f"arrays: <string-array name=\"{name}\"> has no default in values/arrays.xml")
            continue
        for folder in LOCALES:
            count = per_locale[folder].get(name)
            if count is None:
                failures.append(f"{folder}: MISSING <string-array name=\"{name}\">")
            elif count != default_arrays[name]:
                failures.append(
                    f"{folder}: <string-array name=\"{name}\"> has {count} items, default has {default_arrays[name]}"
                )

    # --- plurals ---
    # A form may use FEWER placeholders than the default, never more or
    # different. Arabic's "one" and "two" name the count in the word itself
    # ("شيك واحد", "شيكان") and correctly leave %1$d out; asking for an index
    # the code does not supply is what crashes.
    default_plurals = load_plurals(DEFAULT)
    for folder in ["values"] + LOCALES:
        path = RES / folder / "strings.xml"
        got_plurals = load_plurals(path)
        for key, forms in sorted(default_plurals.items()):
            allowed = set(shapes(forms.get("other", "")))
            if key not in got_plurals:
                failures.append(f'{folder}: MISSING <plurals name="{key}">')
                continue
            missing = QUANTITIES[folder] - set(got_plurals[key])
            if missing:
                failures.append(
                    f"{folder}: {key} has no {sorted(missing)} form(s) — "
                    f"this language uses {sorted(QUANTITIES[folder])}"
                )
            for quantity, text in sorted(got_plurals[key].items()):
                extra = set(shapes(text)) - allowed
                if extra:
                    failures.append(
                        f"{folder}: PLACEHOLDER {key}[{quantity}] asks for "
                        f"{sorted(extra)}, which the code does not supply"
                    )

    for w in warnings:
        print("  warning -", w)

    if failures:
        print(f"{len(failures)} string parity failure(s):")
        for f in failures:
            print("  -", f)
        return 1

    print(
        f"strings OK: {len(translatable)} translatable keys and "
        f"{len(default_plurals)} plurals present in all {len(LOCALES)} "
        f"locales, with matching placeholders and every plural form "
        f"each language uses"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
