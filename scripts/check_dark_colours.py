#!/usr/bin/env python3
"""
Dark-mode colour guard (26 Sep 2026).

A colour repainted in values-night must keep one job. The owner found two
ways this broke on a phone:
  - a colour used as header TEXT (section_*_soft subtitles) was repainted
    dark at night and vanished into the dark green header;
  - ink, a TEXT colour that turns light at night, was also the FILL of the
    dark "Add Party / Add Entry" pills, so the pill turned light and its
    white label vanished.

This reads every layout, drawable and theme, classifies each @color use as
text/icon or fill by the attribute it sits in, and fails if a colour that is
light at night is used as a fill, or one that is dark at night is used as
text. Colour-state lists in res/color are judged by how they are used.
"""
import glob, re, sys

RES = "app/src/main/res"
night = dict(re.findall(r'<color name="([^"]+)">([^<]+)</color>', open(f"{RES}/values-night/colors.xml").read()))

def lum(h):
    h = h.lstrip("#")[-6:]
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)

TEXT = {"android:textColor", "android:textColorHint", "app:tint", "android:tint", "app:iconTint",
        "app:drawableTint", "android:drawableTint", "app:titleTextColor", "app:subtitleTextColor",
        "app:navigationIconTint", "app:hintTextColor", "android:textColorLink", "app:chipIconTint",
        "app:buttonTint", "android:buttonTint", "app:itemTextColor", "app:itemIconTint"}
FILL = {"android:background", "app:backgroundTint", "android:backgroundTint", "app:cardBackgroundColor",
        "android:popupBackground", "app:chipBackgroundColor", "app:boxBackgroundColor",
        "android:fillColor", "android:drawable", "android:startColor", "android:endColor", "android:centerColor"}
THEME_FILL = {"colorPrimary", "colorPrimaryVariant", "colorSecondary", "colorSecondaryVariant",
              "colorSurface", "android:colorBackground", "android:windowBackground",
              "android:statusBarColor", "android:navigationBarColor", "backgroundTint",
              "windowSplashScreenBackground", "android:windowSplashScreenBackground"}
# Outlines are neither text nor fill: a subtle stroke is the intent in both modes.
STROKE = {"strokeColor", "app:strokeColor", "app:boxStrokeColor"}

uses = []  # (colour, kind, where)
csl = {}   # res/color list -> colours inside it
for f in glob.glob(f"{RES}/color/*.xml"):
    csl[f.split("/")[-1][:-4]] = re.findall(r'android:color="@color/(\w+)"', open(f).read())

for f in glob.glob(f"{RES}/layout/*.xml") + glob.glob(f"{RES}/drawable*/*.xml"):
    t = open(f, encoding="utf-8").read()
    for a, kind_src, c in re.findall(r'([\w:]+)="@(color)/(\w+)"', t):
        where = f"{f.split('/')[-1]} {a}"
        kind = "text" if a in TEXT else "fill" if (a in FILL or (a == "android:color" and "/drawable" in f)) else None
        if not kind:
            continue
        for col in (csl[c] if c in csl else [c]):
            uses.append((col, kind, where + (f" (via {c})" if c in csl else "")))
for m in re.finditer(r'<item name="([^"]+)"[^>]*>@color/(\w+)<', open(f"{RES}/values/themes.xml").read()):
    a, c = m.groups()
    if a in STROKE:
        continue
    uses.append((c, "fill" if a in THEME_FILL else "text", f"themes.xml {a}"))

bad = []
for col, kind, where in uses:
    if col not in night:
        continue
    # Translucent overlays (hairlines, scrims) take the colour of what is
    # under them; their luminance alone says nothing.
    if len(night[col].lstrip("#")) == 8 and int(night[col].lstrip("#")[:2], 16) < 0x80:
        continue
    L = lum(night[col])
    if kind == "fill" and L > 0.35:
        bad.append(f"{col} is light at night ({night[col]}) but is a FILL in {where}")
    if kind == "text" and L < 0.2:
        bad.append(f"{col} is dark at night ({night[col]}) but is TEXT in {where}")

if bad:
    print("Dark-mode colour conflicts:")
    print("\n".join("  " + b for b in sorted(set(bad))))
    sys.exit(1)
print(f"dark colours OK: {len(uses)} colour uses checked against {len(night)} night colours, no text/fill conflicts")
