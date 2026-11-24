#!/usr/bin/env python3
"""
Right-to-left PDF guard.

Every PDF the app prints (invoice, ledger, statement, business, register,
inspector) is mirrored for Urdu, Sindhi, Farsi and Arabic by
data/PdfRtl.kt: the page is flipped, and each piece of text and each
picture is flipped back so it reads the right way round.

Check 1. That only works if every page is started, and every text and bitmap
drawn, through PdfRtl. One raw canvas.drawText(...) in a PDF file prints as
mirror writing in those four languages, and nothing else would catch it:
English and Roman Urdu look perfect. So this fails on any direct drawText,
drawBitmap or startPage in the PDF code (the data/ package), outside
PdfRtl.kt itself.

Check 2 (24 Nov 2026, plan C5; lessons 28 and 77 and the 8 Nov M57 sweep). A
money figure that is joined into a SENTENCE of a printed sheet or a receipt
must be isolated (Format.ltr / Format.moneyLtr / Format.isolate): in an
Urdu, Sindhi, Arabic or Farsi line "Rs 1,250" or "- Rs 1,250" otherwise lands
as "1,250 Rs" or splits around the words beside it, and in English nothing
shows. A figure drawn on its own (PdfRtl.drawText(c, Format.money(x), ...)) is
not a sentence and is left alone. Flagged, in the print and receipt files only:

  * Format.money(...) (also signedIfNegative, signedTotal, customerBalance*)
    as a direct argument of getString( / words.text( / Digits.string( /
    String.format( / .format( - the translated sentence has words around it;
  * the same call joined with + to a string literal that holds a letter, or
    to a words.text(...) / getString(...) operand;
  * the same call inside "...${ }..." whose literal text holds a letter.

A deliberate exception takes "// rtl-ok: <reason>" on its line.
"""
import glob, os, re, sys

ROOTS = ["app/src/main/java/com/innovation313/roshankhata/data",
         "shared/src"]
RAW = re.compile(r'(?<!PdfRtl)\.(drawText|drawBitmap|startPage|drawTextOnPath|drawTextRun|drawPosText)\(')

# ---- check 2 ----
MONEY = re.compile(r'Format\.(?:money|signedIfNegative|signedTotal|customerBalanceBare|customerBalance)\(')
MONEY_ROOTS = ["app/src/main/java", "desktop/src/main", "shared/src/jvmSharedMain", "shared/src/androidMain", "shared/src/desktopMain"]
# A file that prints: a PDF, a sheet, a report, a statement, a receipt, a label, the thermal roll.
PRINT_NAME = re.compile(r'(Pdf|Print|Report|Statement|Receipt|Thermal|Label|Register|Inspector|InvoiceTemplate)')
SENTENCE_CALLS = {"getString", "string", "format"}
WRAPPERS = {"ltr", "isolate", "auto", "name", "moneyLtr"}
SENTENCE_QUALIFIED = {"words.text", "W.text", "w.text"}
LETTER = r'[^\W\d_]'


def strip_comments(s):
    """Blank out // and /* */ comments, keeping every offset and newline where it was."""
    out = []
    i, n, instr = 0, len(s), False
    while i < n:
        c = s[i]
        if instr:
            out.append(c)
            if c == '\\' and i + 1 < n:
                out.append(s[i + 1]); i += 2; continue
            if c == '"':
                instr = False
            i += 1
            continue
        if c == '"':
            instr = True; out.append(c); i += 1; continue
        if s.startswith('//', i):
            j = s.find('\n', i); j = n if j < 0 else j
            out.append(' ' * (j - i)); i = j; continue
        if s.startswith('/*', i):
            j = s.find('*/', i); j = n if j < 0 else j + 2
            out.append(''.join(ch if ch == '\n' else ' ' for ch in s[i:j])); i = j; continue
        out.append(c); i += 1
    return ''.join(out)


def enclosing_call(s, pos):
    """The call whose parentheses directly hold the expression at [pos]: its dotted name, else None."""
    depth, i = 0, pos - 1
    while i >= 0:
        c = s[i]
        if c == ')':
            depth += 1
        elif c == '(':
            if depth == 0:
                k = i - 1
                while k >= 0 and (s[k].isalnum() or s[k] in '_.'):
                    k -= 1
                return s[k + 1:i]
            depth -= 1
        elif c in '{}' and depth == 0:
            return None  # a lambda or block boundary: the expression is a statement, not an argument
        i -= 1
    return None


def call_end(s, open_paren):
    d, j = 0, open_paren
    while j < len(s):
        if s[j] == '(':
            d += 1
        elif s[j] == ')':
            d -= 1
            if d == 0:
                return j
        j += 1
    return len(s) - 1


def operand_before(s, plus_pos):
    """The operand that ends just before the '+' at [plus_pos]: (its text, where it starts)."""
    j = plus_pos - 1
    while j >= 0 and s[j] in ' \t\n':
        j -= 1
    if j < 0:
        return "", 0
    if s[j] == '"':
        k = s.rfind('"', 0, j)
        return s[k:j + 1], k
    if s[j] == ')':
        d, k = 0, j
        while k >= 0:
            if s[k] == ')':
                d += 1
            elif s[k] == '(':
                d -= 1
                if d == 0:
                    break
            k -= 1
        m = k - 1
        while m >= 0 and (s[m].isalnum() or s[m] in '_.'):
            m -= 1
        return s[m + 1:j + 1], m + 1
    return "", 0


def sentence_context(s, m):
    """Why the money call at match [m] is part of a sentence, or None."""
    start = m.start()
    encl = enclosing_call(s, start)
    if encl:
        last = encl.split('.')[-1]
        if last in SENTENCE_CALLS or encl in SENTENCE_QUALIFIED or encl.endswith("Digits.string"):
            return "an argument of " + encl + "(...)"
        if last in WRAPPERS:
            return None  # isolated: Format.ltr(...), Format.isolate(...), BidiWrap.*
        # any other call (a drawing call, a Line(...) holder): decided by what the figure is joined to, below
    # no enclosing call: a statement or an expression - look at what it is joined to, along the whole "a + b + c" chain
    def wordy(op):
        if op.startswith('"'):
            return bool(re.search(LETTER, op))
        return bool(re.search(r'(^|\.)(text|getString|string|label)\(', op))
    before = s[:start].rstrip()
    pos = len(before)
    while before[:pos].rstrip().endswith('+'):
        plus = len(before[:pos].rstrip()) - 1
        op, op_start = operand_before(s, plus)
        if not op:
            break
        if wordy(op):
            return "joined with + to " + (op[:30] + "..." if len(op) > 30 else op)
        pos = op_start
    # b.append(words.text(W.x)).append(": ").append(Format.money(v)): the figure is one more link of a chain of appends
    cur = before
    if cur.endswith('.append('):
        cur = cur[:-len('.append(')].rstrip()
        while cur.endswith(')'):
            op, op_start = operand_before(s, len(cur))
            if not op or not op.split('(')[0].endswith('append'):
                break
            inner = op[op.index('(') + 1:-1]
            if wordy(inner):
                return "appended after " + inner[:30]
            cur = cur[:op_start].rstrip()
    end = call_end(s, m.end() - 1)
    tail = end + 1
    while True:
        am = re.match(r'\s*\+\s*("[^"\n]*"|[A-Za-z_][A-Za-z_.]*\()', s[tail:tail + 200])
        if not am:
            break
        if am.group(1).startswith('"'):
            if wordy(am.group(1)):
                return "joined with + to " + am.group(1)[:30]
            tail += am.end()
        else:
            op_start = tail + am.start(1)
            op_end = call_end(s, tail + am.end(1) - 1)
            if wordy(s[op_start:op_end + 1]):
                return "joined with + to " + am.group(1)[:30] + "...)"
            tail = op_end + 1
    # inside "...${ }..."
    d = before.rfind('${')
    if d >= 0 and before[d + 2:].strip() == "":
        q = s.rfind('"', 0, d)
        lit_before = s[q + 1:d] if q >= 0 else ""
        lit_after = s[end + 1:]
        lit_after = lit_after[:lit_after.find('"')] if '"' in lit_after else ""
        lit_after = lit_after[lit_after.find('}') + 1:] if '}' in lit_after else ""
        if re.search(LETTER, lit_before) or re.search(LETTER, lit_after):
            return "inside a string template with words around it"
    return None


def money_problems(path, raw):
    s = strip_comments(raw)
    lines = raw.split('\n')
    found = []
    for m in MONEY.finditer(s):
        why = sentence_context(s, m)
        if not why:
            continue
        line = s.count('\n', 0, m.start()) + 1
        if "rtl-ok" in lines[line - 1]:
            continue
        found.append(f"{path}:{line}: {m.group(0)[:-1]}(...) is {why}, with no Format.ltr / Format.moneyLtr / Format.isolate round it: "
                     f"in an Urdu, Sindhi, Arabic or Farsi sheet the figure lands out of order (lesson 77, M57)")
    return found


def is_print_file(path):
    base = os.path.basename(path)
    if base == "PdfRtl.kt" or base.endswith("Test.kt") or base.endswith(("Activity.kt", "Pane.kt", "Screen.kt", "Dialog.kt")):
        return False
    if "/test/" in path or "/androidTest/" in path or "Test/" in path:
        return False
    return bool(PRINT_NAME.search(base))


SELF_TESTS = [
    # (snippet, should be flagged)
    ('val l = context.getString(R.string.x, Format.dateOnly(d), Format.signedIfNegative(atEnd))', True),
    ('val l = context.getString(R.string.x, Format.dateOnly(d), Format.ltr(Format.signedIfNegative(atEnd)))', False),
    ('lines += words.text(W.receipt_items_total, Format.money(it))', True),
    ('lines += words.text(W.receipt_items_total, Format.moneyLtr(it))', False),
    ('out += words.text(W.pos_return_title) + ": " + Format.money(ret.total)', True),
    ('out += words.text(W.pos_return_title) + ": " + Format.ltr(Format.money(ret.total))', False),
    ('val t = "Total ${Format.money(x)} due"', True),
    ('val t = "${Format.ltr(Format.money(x))}"', False),
    ('PdfRtl.drawText(c, Format.money(e.amount), gaveEnd, y, figure)', False),
    ('add("@ ${Format.money(it)}")', False),
    ('val s = Format.money(a) + " × " + Format.money(b)', False),
    ('val s = "Balance: " + Format.money(a)', True),
    ('b.append(words.text(W.pos_grand_total)).append(": ").append(Format.money(sale.grandTotal)).append(\'\\n\')', True),
    ('b.append(words.text(W.pos_grand_total)).append(": ").append(Format.moneyLtr(sale.grandTotal))', False),
    ('b.append(Format.money(x)).append(\'\\n\')', False),
    ('// context.getString(R.string.x, Format.money(a))', False),
    ('val l = context.getString(R.string.x, Format.money(a)) // rtl-ok: test', False),
]


def self_test():
    bad = []
    for snippet, flagged in SELF_TESTS:
        got = bool(money_problems("<self-test>", snippet))
        if got != flagged:
            bad.append(f"self-test: {snippet!r} should {'be' if flagged else 'not be'} flagged")
    return bad


def main():
    problems = []

    # check 1
    for path in sorted(p for r in ROOTS for p in glob.glob(f"{r}/**/*.kt", recursive=True)):
        if path.endswith("/PdfRtl.kt"):
            continue
        for n, line in enumerate(open(encoding="utf-8", file=path), 1):
            code = line.split("//", 1)[0]
            for m in RAW.finditer(code):
                problems.append(f"{path}:{n}: direct .{m.group(1)}( — use PdfRtl.{m.group(1)}(...) so right-to-left PDFs stay readable")

    # check 2
    problems += self_test()
    seen = set()
    for path in sorted(p for r in MONEY_ROOTS for p in glob.glob(f"{r}/**/*.kt", recursive=True)):
        if path in seen or not is_print_file(path):
            continue
        seen.add(path)
        problems += money_problems(path, open(encoding="utf-8", file=path).read())

    if problems:
        print("\n".join(problems))
        print(f"\n{len(problems)} problem(s). See data/PdfRtl.kt (drawing) and Format.ltr / Format.moneyLtr (figures in sentences).")
        sys.exit(1)
    print("PDF RTL guard: every PDF page, text and bitmap goes through PdfRtl, and no money figure sits unisolated in a printed sentence.")


if __name__ == "__main__":
    main()
