#!/usr/bin/env python3
"""
Dark mode guard: text coloured with a colour that turns LIGHT at night,
drawn on a background that stays WHITE at night, disappears.

That is how the number keys of the entry keypad went blank in dark mode
(26 Sep 2026): style CalcPadKey had backgroundTint @color/white (fixed) and
textColor @color/ink (light at night). The on-device contrast check opens
screens, not the dialogs they open, so it never saw that keypad; this check
reads every layout and style instead, dialogs included.

Fails when one element or style has a fixed-white background (white, #FFF,
#FFFFFF) together with a textColor that values-night repaints.
"""
import glob, re, sys, xml.etree.ElementTree as ET

RES = "app/src/main/res"
A = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"
night = set(re.findall(r'<color name="([^"]+)"', open(f"{RES}/values-night/colors.xml").read()))
WHITE = {"@color/white", "#fff", "#ffffff", "#ffffffff"}
BG_KEYS = ["backgroundTint", "background", "cardBackgroundColor"]

def turns_light(c):
    return c and c.startswith("@color/") and c[7:] in night

problems = []

# styles (with inheritance inside our own themes files)
styles = {}
for f in glob.glob(f"{RES}/values/*.xml"):
    for st in ET.parse(f).getroot().iter("style"):
        items = {i.get("name").replace("android:", ""): (i.text or "").strip() for i in st.iter("item")}
        styles[st.get("name")] = (st.get("parent"), items, f)
def resolved(name, seen=()):
    if name not in styles or name in seen: return {}
    parent, items, _ = styles[name]
    base = resolved(parent, seen + (name,)) if parent else {}
    if not parent and "." in name: base = resolved(name.rsplit(".", 1)[0], seen + (name,))
    return {**base, **items}
for name in styles:
    it = resolved(name)
    bg = next((it[k] for k in BG_KEYS if k in it), None)
    if bg and bg.lower() in WHITE and turns_light(it.get("textColor")):
        problems.append(f"style {name}: white background + {it['textColor']} text")

# layouts
for f in glob.glob(f"{RES}/layout*/*.xml"):
    for el in ET.parse(f).getroot().iter():
        g = lambda k: el.get(A + k) or el.get(APP + k)
        bg = next((g(k) for k in BG_KEYS if g(k)), None)
        if bg and bg.lower() in WHITE and turns_light(g("textColor")):
            problems.append(f"{f.split('/')[-1]} {g('id') or el.tag}: white background + {g('textColor')} text")

# Kotlin: a FILL colour (dark at night, because it is a header or button
# fill) used as TEXT colour. Found 10 such calls on 26 Sep 2026 — amounts,
# due dates and expiry days in dark red/green on a dark page. The *_text
# colours are the same by day and light at night.
FILLS = r"R\.color\.(red_gave|green_got|brand_green|section_[a-z]+)\b(?!_)"
ALLOWED = ["tvDirection"]  # the I Gave / I Got pill is white in both modes
for f in glob.glob("app/src/main/java/**/*.kt", recursive=True):
    lines = open(f, encoding="utf-8").read().split("\n")
    for i, l in enumerate(lines):
        if "setTextColor" not in l or any(a in l for a in ALLOWED):
            continue
        j, depth, started = i, 0, False
        while j < len(lines):
            depth += lines[j].count("(") - lines[j].count(")")
            started = started or "(" in lines[j]
            if started and depth <= 0: break
            j += 1
        stmt = " ".join(lines[i:j + 1])
        for m in re.finditer(FILLS, stmt):
            problems.append(f"{f.split('/')[-1]}:{i + 1}: text coloured with fill {m.group(0)} (use {m.group(0)}_text)")

if problems:
    print("Text that vanishes in dark mode (use @color/surface for the background):")
    print("\n".join("  " + p for p in problems))
    sys.exit(1)
print(f"dark pairs OK: {len(styles)} styles, every layout, and every Kotlin setTextColor checked")
