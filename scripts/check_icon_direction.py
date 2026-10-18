#!/usr/bin/env python3
"""
Icon direction guard, for all nine languages (13 Oct 2026).

The owner's rule: in every language the call, WhatsApp, mail and web marks
stay the right way round, and an icon never sits on one side while the
number it belongs to starts on the other. Three things break that, and
none of them shows in English:

1. An icon drawable marked autoMirrored. Android flips it in Urdu, Sindhi,
   Farsi and Arabic. Right for an arrow or a chevron (they point "forward"),
   wrong for a handset, a chat bubble, an envelope or a globe. Only the
   pointing shapes on the list below may carry it.
2. An input with an icon whose text is forced left-to-right (a phone number,
   an email, a website) but whose layout is not. In a right-to-left language
   the icon then goes right while the number starts left. Such a field must
   be layoutDirection="ltr" as well, so icon and number stay together.
3. A business card mark drawn without CardTemplates.glyph(), which turns each
   mark back the right way on an Urdu card.
"""
import glob, re, sys

RES = "app/src/main/res"
# Undo and redo turn with the reading direction too (Material's bidirectionality
# guidance mirrors them), so the signature pad's Undo may carry it.
POINTING = re.compile(r'^ic_(arrow_(back|forward)|chevron_(right|left|start|end)|undo|redo)$')
problems = []

for path in sorted(glob.glob(f"{RES}/drawable*/*.xml")):
    name = path.rsplit("/", 1)[1][:-4]
    if 'autoMirrored="true"' in open(path, encoding="utf-8").read() and not POINTING.match(name):
        problems.append(f"{path}: autoMirrored on an icon that is not an arrow — it would face backwards in right-to-left languages")

TAG = re.compile(r'<([\w.]+)((?:[^>"]|"[^"]*")*?)/?>')
for path in sorted(glob.glob(f"{RES}/layout*/*.xml")):
    src = open(path, encoding="utf-8").read()
    for m in TAG.finditer(src):
        tag, attrs = m.group(1).rsplit(".", 1)[-1], m.group(2)
        if tag not in ("EditText", "AutoCompleteTextView", "TextInputEditText"):
            continue
        if not re.search(r'drawableStart(Compat)?="', attrs):
            continue
        if 'textDirection="ltr"' in attrs and 'layoutDirection="ltr"' not in attrs:
            line = src.count("\n", 0, m.start()) + 1
            problems.append(f"{path}:{line}: left-to-right field with an icon but no layoutDirection=\"ltr\" — icon and number split in right-to-left languages")

cards = "app/src/main/java/com/innovation313/roshankhata/ui/CardTemplates.kt"
for n, line in enumerate(open(cards, encoding="utf-8"), 1):
    code = line.split("//", 1)[0]
    if "glyphShape(" in code and "fun glyphShape" not in code and "try { glyphShape" not in code and not re.match(r'\s*glyphShape\(c, ico, cx, cy, s, colour\)\s*$', code):
        problems.append(f"{cards}:{n}: card mark drawn around glyph() — it would be mirrored on an Urdu card")

if problems:
    print("\n".join(problems))
    sys.exit(1)
print("Icon direction: arrows only are mirrored, contact fields keep icon and number together, card marks go through glyph().")
