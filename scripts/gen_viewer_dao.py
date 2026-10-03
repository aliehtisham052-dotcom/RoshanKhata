#!/usr/bin/env python3
"""Regenerate ViewerDao.kt from KhataDao.kt.

Every DAO method that changes the database gets an override that refuses.
A method counts as a write when it carries @Insert/@Update/@Delete/@Upsert,
or a @Query that starts with UPDATE/DELETE/INSERT/REPLACE, or when it is a
default method whose body calls any write (followed through, so a
transaction that calls a transaction is caught too). A @Transaction that only
reads is NOT a write — blocking it would break a screen on the helper's phone. The unit test ViewerDaoCoverageTest uses
the same rule, so adding a write to KhataDao without re-running this script
fails the build instead of quietly giving viewers a way to write.

    python3 scripts/gen_viewer_dao.py
"""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "app/src/main/java/com/innovation313/roshankhata/data"
src = (DATA / "KhataDao.kt").read_text()


def methods(text):
    """Yield (name, annotations, signature_text) for every fun in the interface."""
    i = 0
    pending = []
    n = len(text)
    while i < n:
        # skip comments
        if text.startswith("/*", i):
            i = text.index("*/", i) + 2
            continue
        if text.startswith("//", i):
            i = text.index("\n", i)
            continue
        if text[i] == "@":
            m = re.match(r"@(\w+)", text[i:])
            name = m.group(1)
            j = i + m.end()
            body = ""
            if j < n and text[j] == "(":
                depth, k, in_str = 0, j, None
                while True:
                    c = text[k]
                    if in_str:
                        if text.startswith(in_str, k):
                            k += len(in_str) - 1
                            in_str = None
                    elif text.startswith('"""', k):
                        in_str = '"""'; k += 2
                    elif c == '"':
                        in_str = '"'
                    elif c == "(":
                        depth += 1
                    elif c == ")":
                        depth -= 1
                        if depth == 0:
                            break
                    k += 1
                body = text[j + 1:k]
                j = k + 1
            pending.append((name, body))
            i = j
            continue
        m = re.match(r"(suspend\s+)?fun\s+(\w+)\s*\(", text[i:])
        if m and (i == 0 or not (text[i - 1].isalnum() or text[i - 1] == "_")):
            start = i
            k = i + m.end() - 1
            depth = 0
            while True:
                c = text[k]
                if c == "(": depth += 1
                elif c == ")":
                    depth -= 1
                    if depth == 0: break
                k += 1
            params = text[i + m.end():k]
            rest = text[k + 1:]
            rm = re.match(r"\s*:\s*([^\n={]+)", rest)
            ret = rm.group(1).strip() if rm else None
            yield m.group(2), pending, bool(m.group(1)), params, ret
            pending = []
            i = k + 1
            continue
        if not text[i].isspace() and text[i] not in "}":
            # any other token (val, interface, braces) ends an annotation run
            if not text.startswith("fun", i):
                pass
        i += 1


def is_direct_write(anns):
    names = {a for a, _ in anns}
    if names & {"Insert", "Update", "Delete", "Upsert"}:
        return True
    for a, body in anns:
        if a == "Query":
            first = re.sub(r'["+\s]', " ", body).split()
            return bool(first) and first[0].upper() in ("UPDATE", "DELETE", "INSERT", "REPLACE")
    return False


def bodies(text):
    """name -> body text for every method that has one (an interface default)."""
    clean = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    clean = re.sub(r"//[^\n]*", "", clean)
    starts = list(re.finditer(r"\bfun\s+(\w+)\s*\(", clean))
    out = {}
    for k, m in enumerate(starts):
        i, d = m.end(), 1
        while d:
            d += (clean[i] == "(") - (clean[i] == ")")
            i += 1
        end = starts[k + 1].start() if k + 1 < len(starts) else len(clean)
        tail = clean[i:end]
        if re.match(r"\s*(:\s*[\w<>?,\s.\[\]]+?)?\s*[{=]", tail):
            out[m.group(1)] = tail
    return out


def write_names(text):
    """A write is a direct write, or a default method that calls one. Transitive."""
    all_methods = list(methods(text))
    writes = {n for n, a, _, _, _ in all_methods if is_direct_write(a)}
    body = bodies(text)
    changed = True
    while changed:
        changed = False
        for n, b in body.items():
            if n not in writes and any(re.search(r"\b" + re.escape(w) + r"\s*\(", b) for w in writes):
                writes.add(n)
                changed = True
    return all_methods, writes


def strip_defaults(params):
    # Comments inside a parameter list (KhataDao.restoreAll has several)
    # must go first: joined onto one line, a // would swallow the rest.
    params = re.sub(r"/\*.*?\*/", "", params, flags=re.S)
    params = re.sub(r"//[^\n]*", "", params)
    out, depth, cur = [], 0, ""
    for c in params:
        if c in "(<[": depth += 1
        if c in ")>]": depth -= 1
        if c == "," and depth == 0:
            out.append(cur); cur = ""
        else:
            cur += c
    if cur.strip(): out.append(cur)
    cleaned = []
    for p in out:
        d, part = 0, ""
        for c in p:
            if c in "(<[": d += 1
            if c in ")>]": d -= 1
            if c == "=" and d == 0: break
            part += c
        cleaned.append(" ".join(part.split()))
    return ", ".join(cleaned)


_all, _names = write_names(src)
writes = [(n, s, p, r) for n, a, s, p, r in _all if n in _names]
if any(not s for _, s, _, _ in writes):
    sys.exit("a write method is not suspend; ViewerDao would need a different refusal")

imports = [l for l in src.splitlines() if l.startswith("import ") and "androidx.room" not in l]
lines = [
    "package com.innovation313.roshankhata.data",
    "",
    *imports,
    "import kotlin.coroutines.cancellation.CancellationException",
    "",
    "// GENERATED by scripts/gen_viewer_dao.py from KhataDao.kt — do not edit by hand.",
    "// Re-run the script after adding a write to KhataDao; ViewerDaoCoverageTest fails until you do.",
    "",
    "/**",
    " * The DAO a viewer phone gets (see [ViewerMode]). Reads pass straight through;",
    " * every write refuses by cancelling the coroutine that asked, so the screen",
    " * stops where it is, nothing reaches the database, and the app does not crash.",
    " */",
    "class ViewerDao(private val real: KhataDao) : KhataDao by real {",
    "",
    "    private fun refused(): Nothing {",
    "        ViewerMode.writeRefused()",
    "        throw CancellationException(\"Read-only phone: this copy cannot be changed here\")",
    "    }",
    "",
]
for name, _, params, ret in writes:
    sig = f"    override suspend fun {name}({strip_defaults(params)})"
    sig += f": {ret or 'Unit'}"
    lines.append(sig + " = refused()")
lines.append("}")
lines.append("")
(DATA / "ViewerDao.kt").write_text("\n".join(lines))
print(f"{len(writes)} write methods overridden")
