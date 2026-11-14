#!/usr/bin/env python3
"""
Hudhud's catalog (14 Nov 2026, the owner: "complete access, from every angle ... self-determining").

The app's own code is the map: every Room table and column, the shop's profile fields, every screen, every Home
tile, every More entry. shared/hudhud/catalog/decisions.tsv says what Hudhud does with each of them, and this script
refuses a map that has

  * an item with no decision (a new column, screen, tile or menu entry nobody has thought about for Hudhud),
  * a decision for something that is gone,
  * a reader that does not exist, or that does not apply the gate its decision claims,
  * a secret (a PIN hash, a salt, a token, an IBAN ...) that is told or merely left undecided,
  * a gate looser than the screen's own: Hudhud must not tell a cashier what the cashier's own screens would hide
    (the owner's choice A, 14 Nov 2026). A gate stricter than the screen's needs a written reason.

  python3 scripts/gen_hudhud_catalog.py             write shared/hudhud/catalog/catalog.tsv
  python3 scripts/gen_hudhud_catalog.py --check     the same, failing on any problem or on a catalog that is stale
  python3 scripts/gen_hudhud_catalog.py --report    counts, and the honest backlog (what Hudhud could tell and does not yet)
  python3 scripts/gen_hudhud_catalog.py --explain   where each column's name appears in Hudhud's code (to write decisions)

Plain Python 3, no packages. It only READS the app's source; the catalog it writes is a record, the app never loads it.
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, 'app/src/main/java/com/innovation313/roshankhata')
DECISIONS = os.path.join(ROOT, 'shared/hudhud/catalog/decisions.tsv')
CATALOG = os.path.join(ROOT, 'shared/hudhud/catalog/catalog.tsv')

problems = []


def fail(msg):
    problems.append(msg)


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def rel(path):
    return os.path.relpath(path, ROOT)


_files = None


def kotlin_files():
    """The app's and the shared module's own Kotlin (not tests, not the computer's copies)."""
    global _files
    if _files is None:
        found = glob.glob(ROOT + '/app/src/main/**/*.kt', recursive=True)
        for sub in ('jvmSharedMain', 'androidMain', 'desktopMain', 'commonMain'):
            found += glob.glob(ROOT + '/shared/src/' + sub + '/**/*.kt', recursive=True)
        _files = sorted(found)
    return _files


def file_named(cls):
    """The one Kotlin file called <cls>.kt in the app or the shared module (the app's copy wins)."""
    hits = [f for f in kotlin_files() if os.path.basename(f) == cls + '.kt']
    return hits[0] if hits else None


# ------------------------------------------------------------------------------------------------ Kotlin reading

def strip(src):
    """Blank comments and triple-quoted text, keep everything else, offsets and newlines unchanged."""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if src.startswith('/*', i):
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            out.append(''.join('\n' if ch == '\n' else ' ' for ch in src[i:j]))
            i = j
        elif src.startswith('//', i):
            j = src.find('\n', i)
            j = n if j < 0 else j
            out.append(' ' * (j - i))
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(''.join('\n' if ch == '\n' else ' ' for ch in src[i:j]))
            i = j
        elif c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == '\\':
                    j += 1
                j += 1
            j += 1
            out.append(src[i:j])
            i = j
        elif c == "'" and i + 2 < n and (src[i + 2] == "'" or src[i + 1] == '\\'):
            j = src.find("'", i + 2 if src[i + 1] != '\\' else i + 3)
            j = n if j < 0 else j + 1
            out.append(src[i:j])
            i = j
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def balanced(s, start, open_c='(', close_c=')'):
    """s[start] == open_c; the index just past the matching close."""
    depth = 0
    i = start
    n = len(s)
    while i < n:
        c = s[i]
        if c == '"':
            j = i + 1
            while j < n and s[j] != '"':
                if s[j] == '\\':
                    j += 1
                j += 1
            i = j + 1
            continue
        if c == open_c:
            depth += 1
        elif c == close_c:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError('unbalanced')


def split_top(s):
    parts = []
    depth = 0
    ang = 0
    cur = []
    i = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c == '"':
            j = i + 1
            while j < n and s[j] != '"':
                if s[j] == '\\':
                    j += 1
                j += 1
            cur.append(s[i:j + 1])
            i = j + 1
            continue
        if c in '([{':
            depth += 1
        elif c in ')]}':
            depth -= 1
        elif c == '<' and i > 0 and (s[i - 1].isalnum() or s[i - 1] in '?_'):
            ang += 1
        elif c == '>' and ang > 0 and s[i - 1] != '-':
            ang -= 1
        if c == ',' and depth == 0 and ang == 0:
            parts.append(''.join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    if ''.join(cur).strip():
        parts.append(''.join(cur))
    return parts


def entities():
    """Every Room @Entity: table, class, file and its stored columns (an @Ignore field is not stored)."""
    res = []
    for f in kotlin_files():
        raw = read(f)
        if '@Entity' not in raw:
            continue
        s = strip(raw)
        for m in re.finditer(r'^@Entity\b', s, re.M):
            p = m.end()
            ann = ''
            if s[p:p + 1] == '(':
                e = balanced(s, p)
                ann = s[p:e]
                p = e
            tm = re.search(r'tableName\s*=\s*"([^"]+)"', ann)
            cm = re.compile(r'\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:data\s+)?class\s+(\w+)\s*\(').match(s, p)
            if not cm:
                fail('an @Entity in %s has no class after it' % rel(f))
                continue
            cls = cm.group(1)
            o = cm.end() - 1
            body = s[o + 1:balanced(s, o) - 1]
            cols = []
            for part in split_top(body):
                part = part.strip()
                if not part:
                    continue
                pm = re.search(r'\b(val|var)\s+(\w+)\s*:\s*([^=]+?)\s*(?:=|$)', part, re.S)
                if not pm:
                    fail('%s: a constructor part of %s could not be read: %r' % (rel(f), cls, part[:60]))
                    continue
                if '@Ignore' in part:
                    continue
                prop = pm.group(2)
                col = prop
                cn = re.search(r'@ColumnInfo\(\s*name\s*=\s*"([^"]+)"', part)
                if cn:
                    col = cn.group(1)
                cols.append({'col': col, 'prop': prop, 'type': re.sub(r'\s+', '', pm.group(3))})
            res.append({'table': tm.group(1) if tm else cls, 'cls': cls, 'file': rel(f), 'cols': cols})
    return sorted(res, key=lambda e: e['table'])


_fn_cache = {}


def functions(path):
    """name -> the text of that member function (4-space members of an object, and the 8-space members of a class nested
    in it: HudhudSheet's Session), overloads together. A 4-space function runs to the next 4-space one (its nested
    members are inside it); an 8-space one runs to the next member of either depth."""
    if path in _fn_cache:
        return _fn_cache[path]
    src = strip(read(path))
    pat = re.compile(r'^( {4}| {8})(?:(?:private|internal|public|suspend|inline|override|tailrec)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(', re.M)
    ms = list(pat.finditer(src))
    out = {}
    for i, m in enumerate(ms):
        depth = len(m.group(1))
        end = len(src)
        for later in ms[i + 1:]:
            if len(later.group(1)) <= depth:
                end = later.start()
                break
        out[m.group(2)] = out.get(m.group(2), '') + src[m.start():end]
    _fn_cache[path] = out
    return out


def reader(sym, ctx):
    """'HudhudFacts.person' -> (path, body) or None (and a problem written)."""
    if '.' not in sym:
        fail('%s: reader %r is not Class.function' % (ctx, sym))
        return None
    cls, fn = sym.split('.', 1)
    path = file_named(cls)
    if not path:
        fail('%s: no file %s.kt for reader %s' % (ctx, cls, sym))
        return None
    body = functions(path).get(fn)
    if body is None:
        fail('%s: %s has no function %s' % (ctx, cls, fn))
        return None
    return path, body


def via_check(sym, ctx):
    """via=Class.function: the class, object or interface exists somewhere in the app or the shared module and
    declares that function (any indentation: a companion's, a DAO's, a data class's). Existence only."""
    if '.' not in sym:
        fail('%s: via=%r is not Class.function' % (ctx, sym))
        return
    cls, fn = sym.split('.', 1)
    pat = re.compile(r'\b(?:class|object|interface)\s+%s\b' % re.escape(cls))
    path = file_named(cls)
    if not path or not pat.search(read(path)):
        path = next((f for f in kotlin_files() if pat.search(read(f))), None)
    if not path:
        fail('%s: via=%s: no class, object or interface %s in the app or the shared module' % (ctx, sym, cls))
        return
    if not re.search(r'\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?%s\s*\(' % re.escape(fn), strip(read(path))):
        fail('%s: via=%s: %s declares no function %s' % (ctx, sym, rel(path), fn))


def also_check(value, ctx):
    """also=A.f+B.g: more readers of the same column (each a Hudhud function that really exists)."""
    for sym in value.split('+'):
        if sym.strip():
            reader(sym.strip(), ctx)


def camel(perm):
    parts = perm.lower().split('_')
    return parts[0] + ''.join(p.title() for p in parts[1:])


# ------------------------------------------------------------------------------------------------ the app's own lists

def manifest():
    src = re.sub(r'<!--.*?-->', '', read(os.path.join(ROOT, 'app/src/main/AndroidManifest.xml')), flags=re.S)
    acts = {}
    for m in re.finditer(r'<activity\b([^>]*?)(?:/>|>)', src, flags=re.S):
        a = m.group(1)
        n = re.search(r'android:name="\.?([\w.]+)"', a).group(1).split('.')[-1]
        p = re.search(r'android:parentActivityName="\.?([\w.]+)"', a)
        acts[n] = p.group(1).split('.')[-1] if p else None
    return acts


def access_screens():
    """AccessUi's own table: the permission each screen asks of whoever opens it."""
    src = strip(read(os.path.join(APP, 'ui/AccessUi.kt')))
    m = re.search(r'val SCREENS\s*=\s*mapOf\(', src)
    if not m:
        fail('AccessUi.SCREENS not found')
        return {}
    body = src[m.end() - 1:balanced(src, m.end() - 1)]
    return dict(re.findall(r'"(\w+)"\s+to\s+Access\.Permission\.(\w+)', body))


def permissions():
    """(all permissions, the owner-only ones) from Access.kt."""
    src = strip(read(file_named('Access')))
    m = re.search(r'enum class Permission\s*\{(.*?)\n    \}', src, re.S)
    allp = re.findall(r'\b([A-Z][A-Z_]+)\b', m.group(1)) if m else []
    o = re.search(r'OWNER_ONLY\s*=\s*setOf\(([^)]*)\)', src)
    owner = re.findall(r'Permission\.(\w+)', o.group(1)) if o else []
    return allp, owner


def tiles():
    src = strip(read(os.path.join(APP, 'MainActivity.kt')))
    return [{'icon': i, 'label': l, 'screen': s} for i, l, s in
            re.findall(r'Feature\(\s*R\.drawable\.(\w+),\s*R\.string\.(\w+),\s*(\w+)::class\.java', src)]


def more_entries():
    src = strip(read(os.path.join(APP, 'ui/MoreSheet.kt')))
    ms = list(re.finditer(r'\be\(\s*R\.drawable\.(\w+),\s*R\.color\.(\w+),\s*R\.string\.(\w+),\s*(\w+)', src))
    out = []
    for i, m in enumerate(ms):
        end = ms[i + 1].start() if i + 1 < len(ms) else len(src)
        span = src[m.end():end]
        cl = re.search(r'(\w+)::class\.java', span)
        call = re.search(r'\b([A-Z]\w+\.\w+)\(activity|\b([a-z]\w+)\(activity\)', span)
        target = cl.group(1) if cl else ((call.group(1) or call.group(2)) if call else '?')
        out.append({'label': m.group(3), 'group': m.group(4), 'target': target, 'screen': bool(cl)})
    return out


def pages():
    """Hudhud's help pages: key -> the screen it opens (or None), from HudhudBook.PAGES and screenOf."""
    src = strip(read(os.path.join(APP, 'data/HudhudBook.kt')))
    out = {}
    for key, tail in re.findall(r'Page\("(\w+)",\s*R\.string\.\w+,\s*R\.string\.\w+,\s*([\w.]+(?:::class\.java)?)\)', src):
        out[key] = tail.split('::')[0].split('.')[-1] if tail != 'null' else None
    for topic, screen in re.findall(r'FeatureIntro\.Topic\.(\w+)\s*->\s*(\w+)::class\.java', src):
        out['feature_' + topic.lower()] = screen
    return out


def enums():
    """Every enum in Hudhud.kt and VoiceQuestion.kt: 'Facet' -> {PHONE, ...}."""
    out = {}
    for name in ('Hudhud', 'VoiceQuestion'):
        src = strip(read(file_named(name)))
        for m in re.finditer(r'enum class (\w+)(?:\([^)]*\))?\s*\{', src):
            end = balanced(src, m.end() - 1, '{', '}')
            out[m.group(1)] = set(re.findall(r'\b([A-Z][A-Z0-9_]+)\b(?=\s*[,}\n(;])', src[m.end():end]))
    return out


def asks():
    src = strip(read(file_named('Hudhud')))
    return set(re.findall(r'(?:data class|object|class)\s+(\w+)\s*(?:\(|:)', src[src.find('sealed class Ask'):]))


def profile_getters():
    """BusinessProfile's plain value getters: fun name(context: Context): Type (no setters, loaders or files)."""
    src = strip(read(file_named('BusinessProfile')))
    out = []
    for name, typ in re.findall(r'^    fun (\w+)\(context: Context\)\s*:\s*([\w.?<>]+)', src, re.M):
        if re.match(r'(set|has|load|save|remove|copy|header|settings)', name) or name.endswith(('File', 'Of')):
            continue
        if typ in ('File', 'Bitmap', 'Bitmap?', 'ShopSettings'):
            continue
        out.append((name, typ))
    return out


# ------------------------------------------------------------------------------------------------ decisions

KINDS = {'column', 'profile', 'screen', 'tile', 'more'}
DECISIONS_OF = {
    'column': {'READ', 'GAP', 'WITHHELD', 'STRUCT'},
    'profile': {'READ', 'GAP', 'WITHHELD'},
    'screen': {'OPEN', 'OPENED', 'CHILD', 'INFRA', 'NOT-ASKED', 'GAP'},
    'tile': {'EXEMPT'},
    'more': {'OPEN', 'ANSWERED', 'NOT-ASKED', 'GAP'},
}
STRUCT_PATTERNS = [r'^(id|uid)$', r'^isDeleted$', r'^deletedAt$', r'Id$', r'^lineNo$', r'^(nameKey|normalisedName)$']
SECRET_WORDS = {'pin', 'hash', 'salt', 'secret', 'token', 'password', 'passwd'}
# A number the owner may be told on the card but that must never be SPOKEN: the reader has to say so (`secrets =`).
NUMBER_WORDS = {'iban', 'jazzcash', 'phone', 'email', 'strn'}
# what a decision's line in catalog.tsv carries, in this order (the values never hold a ';' or a tab)
NOTE_KEYS = ('reader', 'facet', 'ask', 'via', 'also', 'gate', 'screen', 'screengate', 'alsoseen', 'stricter', 'why', 'q')
TIERS = {'LOOK', 'OPEN', 'NEVER'}
ALLOWED_KEYS = {'cols', 'of', 'by', 'tier'} | set(NOTE_KEYS)


def words_of(name):
    return {w.lower() for w in re.findall(r'[A-Z]?[a-z0-9]+|[A-Z]+(?![a-z])', name)}


def parse_decisions():
    rows = []
    if not os.path.exists(DECISIONS):
        fail('%s is missing' % rel(DECISIONS))
        return rows
    for n, line in enumerate(read(DECISIONS).splitlines(), 1):
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        parts = line.split('\t')
        ctx = 'decisions.tsv:%d' % n
        if len(parts) < 3:
            fail('%s: needs kind, subject and decision' % ctx)
            continue
        kind, subject, decision = parts[0].strip(), parts[1].strip(), parts[2].strip()
        if kind not in KINDS:
            fail('%s: unknown kind %r' % (ctx, kind))
            continue
        if decision not in DECISIONS_OF[kind]:
            fail('%s: %s decision must be one of %s, not %r' % (ctx, kind, sorted(DECISIONS_OF[kind]), decision))
            continue
        kv = {}
        for p in parts[3:]:
            if not p.strip():
                continue
            k, sep, v = p.partition('=')
            if not sep:
                fail('%s: %r is not key=value' % (ctx, p))
                continue
            k = k.strip()
            if k not in ALLOWED_KEYS:
                fail('%s: unknown key %r (a typo?)' % (ctx, k))
            if k in kv:
                fail('%s: key %s twice' % (ctx, k))
            if ';' in v:
                fail('%s: the value of %s holds a ";" (the catalog keeps its notes apart by it); use a comma' % (ctx, k))
            kv[k] = v.strip()
        rows.append({'n': n, 'ctx': ctx, 'kind': kind, 'subject': subject, 'decision': decision, 'kv': kv})
    return rows


def level(perm, owner_only):
    if perm in (None, 'NONE'):
        return 0
    return 2 if perm in owner_only else 1


class Map:
    pass


def build():
    m = Map()
    m.ents = entities()
    m.acts = manifest()
    m.screens = access_screens()
    m.perms, m.owner_only = permissions()
    m.tiles = tiles()
    m.more = more_entries()
    m.pages = pages()
    m.enums = enums()
    m.asks = asks()
    m.profile = profile_getters()
    m.rows = parse_decisions()
    m.lines = []          # the catalog, line by line
    m.stat = {}           # counters for --report
    m.gap_columns = []
    m.gap_screens = []
    m.stricter = []
    m.indirect = []
    check_gates(m)
    check_columns(m, 'column')
    check_profile(m)
    check_screens(m)
    check_tiles(m)
    check_more(m)
    return m


def gate_ok(m, d, perm, screen, ctx, tokens=()):
    """Hudhud's gate (perm) against the screen's own. Writes problems; returns the screen's gate.

    `alsoseen=A,B` names other screens that show the same data to whoever opens them (a receipt shows the shop's name
    to a cashier although Business Profile is the owner's): the lowest gate among the screen and these is the one
    Hudhud is held to. Each of them must use every token (the column's or field's name) in its source."""
    if perm != 'NONE' and perm not in m.perms:
        fail('%s: gate %s is not a Permission' % (ctx, perm))
        return None
    if screen not in m.acts:
        fail('%s: screen %s is not an activity in the manifest' % (ctx, screen))
        return None
    own = m.screens.get(screen)
    extra = d['kv'].get('screengate')
    if own and extra:
        fail('%s: %s is already in AccessUi.SCREENS (%s); screengate must not be given' % (ctx, screen, own))
    sg = own or extra or 'NONE'
    if extra:
        path = file_named(screen)
        if not path or 'Permission.' + extra not in read(path):
            fail('%s: screengate=%s but %s.kt never mentions Permission.%s' % (ctx, extra, screen, extra))
    seen = [x.strip() for x in d['kv'].get('alsoseen', '').split(',') if x.strip()]
    sources = []
    for x in seen:
        if x not in m.acts:
            fail('%s: alsoseen=%s is not an activity in the manifest' % (ctx, x))
            continue
        path = file_named(x)
        sources.append(strip(read(path)) if path else '')
        xg = m.screens.get(x, 'NONE')
        if level(xg, m.owner_only) < level(sg, m.owner_only):
            sg = xg
    if sources:
        for t in tokens:
            if not any(re.search(r'\b%s\b' % re.escape(t), src) for src in sources):
                fail('%s: alsoseen=%s: none of them ever uses %s' % (ctx, ','.join(seen), t))
    lp, ls = level(perm, m.owner_only), level(sg, m.owner_only)
    if lp < ls:
        fail('%s: LEAK: Hudhud tells it with gate %s but %s asks %s (a cashier would be told what his own screen hides)' % (ctx, perm, screen, sg))
    elif lp > ls:
        if not d['kv'].get('stricter'):
            fail('%s: gate %s is stricter than %s (%s); say why in stricter=' % (ctx, perm, screen, sg))
        else:
            m.stricter.append((ctx, perm, screen, sg, d['kv']['stricter']))
    elif perm != sg and d['kv'].get('stricter'):
        fail('%s: stricter= given but the gates are the same level' % ctx)
    return sg


def check_gates(m):
    """HudhudGate is the one place Hudhud's files ask Access (so a gate is always one the catalog knows)."""
    path = file_named('HudhudGate')
    if not path:
        fail('HudhudGate.kt is missing: Hudhud\'s gates all live there')
        return
    for f in kotlin_files():
        base = os.path.basename(f)
        if base in ('HudhudGate.kt',) or not base.startswith('Hudhud'):
            continue
        for i, line in enumerate(strip(read(f)).splitlines(), 1):
            if re.search(r'\bAccess\.(can|ownerOnly|isOwner)\b', line):
                fail('%s:%d: Hudhud asks Access directly; use HudhudGate (so the catalog can hold the gate to the screen\'s)' % (rel(f), i))
    src = strip(read(path))
    for p in re.findall(r'fun (\w+)\(\)\s*(?::\s*Boolean\s*)?=\s*Access\.can\(Access\.Permission\.(\w+)\)', src):
        if camel(p[1]) != p[0]:
            fail('HudhudGate.%s() reads Permission.%s: name it %s()' % (p[0], p[1], camel(p[1])))


def check_columns(m, kind):
    decided = {}
    by_col = {}
    for e in m.ents:
        for c in e['cols']:
            by_col[(e['table'], c['col'])] = c
    for d in m.rows:
        if d['kind'] != 'column':
            continue
        ctx = d['ctx']
        cols = [c for c in d['kv'].get('cols', '').split(',') if c]
        if not cols:
            fail('%s: cols= is empty' % ctx)
            continue
        if d['subject'] not in {e['table'] for e in m.ents}:
            fail('%s: there is no table %s (stale decision)' % (ctx, d['subject']))
            continue
        dec = d['decision']
        if dec in ('WITHHELD', 'STRUCT') and not d['kv'].get('why'):
            fail('%s: %s needs a why=' % (ctx, dec))
        rd = None
        if dec == 'READ':
            for k in ('reader', 'gate', 'screen'):
                if not d['kv'].get(k):
                    fail('%s: READ needs %s=' % (ctx, k))
            if d['kv'].get('reader') and d['kv'].get('gate') and d['kv'].get('screen'):
                rd = reader(d['kv']['reader'], ctx)
                gate_ok(m, d, d['kv']['gate'], d['kv']['screen'], ctx,
                        tokens=[by_col[(d['subject'], c)]['prop'] for c in cols if (d['subject'], c) in by_col])
                if rd and d['kv']['gate'] != 'NONE':
                    tok = r'\b(?:HudhudGate\.)?%s\(\)' % camel(d['kv']['gate'])
                    if not re.search(tok, rd[1]):
                        fail('%s: reader %s never applies the gate %s (no %s() in its body)' % (ctx, d['kv']['reader'], d['kv']['gate'], camel(d['kv']['gate'])))
                for key in ('facet',):
                    if d['kv'].get(key):
                        en, _, val = d['kv'][key].partition('.')
                        if en not in m.enums or val not in m.enums[en]:
                            fail('%s: facet %s is not an enum value of Hudhud.kt / VoiceQuestion.kt' % (ctx, d['kv'][key]))
                if d['kv'].get('ask') and d['kv']['ask'] not in m.asks:
                    fail('%s: ask=%s is not one of Hudhud.Ask' % (ctx, d['kv']['ask']))
                if d['kv'].get('via'):
                    via_check(d['kv']['via'], ctx)
                if d['kv'].get('also'):
                    also_check(d['kv']['also'], ctx)
        for c in cols:
            key = (d['subject'], c)
            if key not in by_col:
                fail('%s: %s.%s is not a column (stale decision)' % (ctx, d['subject'], c))
                continue
            if key in decided:
                fail('%s: %s.%s is decided twice (also at %s)' % (ctx, d['subject'], c, decided[key]['ctx']))
                continue
            decided[key] = d
            prop = by_col[key]['prop']
            if SECRET_WORDS & (words_of(c) | words_of(prop)) and dec != 'WITHHELD':
                fail('%s: %s.%s looks like a secret; it must be WITHHELD, not %s' % (ctx, d['subject'], c, dec))
            if dec == 'READ' and rd and NUMBER_WORDS & (words_of(c) | words_of(prop)) and not re.search(r'\bsecrets\b', rd[1]):
                fail('%s: %s.%s is a number the card may show but never say; reader %s never sets secrets' % (ctx, d['subject'], c, d['kv']['reader']))
            if dec == 'READ' and rd:
                if not re.search(r'\b%s\b' % re.escape(prop), rd[1]):
                    if d['kv'].get('via'):
                        m.indirect.append((d['subject'], c, d['kv']['via']))
                    else:
                        fail('%s: %s.%s: the reader %s never names %s; if the reading is indirect say via=Class.function' % (ctx, d['subject'], c, d['kv']['reader'], prop))
    for e in m.ents:
        for c in e['cols']:
            key = (e['table'], c['col'])
            d = decided.get(key)
            if d:
                kv = d['kv']
                note = ';'.join('%s=%s' % (k, kv[k]) for k in NOTE_KEYS if kv.get(k))
                m.lines.append(('column', '%s.%s' % (e['table'], c['col']), c['type'], d['decision'], note))
                if d['decision'] == 'GAP':
                    m.gap_columns.append((e['table'], c['col']))
            elif any(re.search(p, c['col']) or re.search(p, c['prop']) for p in STRUCT_PATTERNS) and not (SECRET_WORDS & (words_of(c['col']) | words_of(c['prop']))):
                m.lines.append(('column', '%s.%s' % (e['table'], c['col']), c['type'], 'STRUCT', 'pattern'))
            else:
                fail('%s.%s (%s, %s) has no decision for Hudhud: add it to decisions.tsv' % (e['table'], c['col'], c['type'], e['file']))


def check_profile(m):
    getters = dict(m.profile)
    decided = {}
    for d in m.rows:
        if d['kind'] != 'profile':
            continue
        ctx = d['ctx']
        if d['subject'] != 'BusinessProfile':
            fail('%s: profile decisions are about BusinessProfile' % ctx)
            continue
        dec = d['decision']
        if dec == 'WITHHELD' and not d['kv'].get('why'):
            fail('%s: WITHHELD needs a why=' % ctx)
        if dec == 'READ':
            for k in ('reader', 'gate', 'screen'):
                if not d['kv'].get(k):
                    fail('%s: READ needs %s=' % (ctx, k))
            if all(d['kv'].get(k) for k in ('reader', 'gate', 'screen')):
                rd = reader(d['kv']['reader'], ctx)
                gate_ok(m, d, d['kv']['gate'], d['kv']['screen'], ctx, tokens=[g for g in d['kv'].get('cols', '').split(',') if g])
                if rd and d['kv']['gate'] != 'NONE' and not re.search(r'\b(?:HudhudGate\.)?%s\(\)' % camel(d['kv']['gate']), rd[1]):
                    fail('%s: reader %s never applies the gate %s' % (ctx, d['kv']['reader'], d['kv']['gate']))
                if d['kv'].get('facet'):
                    en, _, val = d['kv']['facet'].partition('.')
                    if en not in m.enums or val not in m.enums[en]:
                        fail('%s: facet %s is not an enum value' % (ctx, d['kv']['facet']))
                if d['kv'].get('also'):
                    also_check(d['kv']['also'], ctx)
        for g in [c for c in d['kv'].get('cols', '').split(',') if c]:
            if g not in getters:
                fail('%s: BusinessProfile has no value getter %s (stale decision)' % (ctx, g))
            elif g in decided:
                fail('%s: %s decided twice' % (ctx, g))
            else:
                decided[g] = d
                if SECRET_WORDS & words_of(g) and dec != 'WITHHELD':
                    fail('%s: %s looks like a secret' % (ctx, g))
                if dec == 'READ' and d['kv'].get('reader') and NUMBER_WORDS & words_of(g):
                    rd2 = reader(d['kv']['reader'], ctx)
                    if rd2 and not re.search(r'\bsecrets\b', rd2[1]):
                        fail('%s: %s is a number the card may show but never say; reader %s never sets secrets' % (ctx, g, d['kv']['reader']))
                if dec == 'READ' and d['kv'].get('reader'):
                    rd = reader(d['kv']['reader'], ctx)
                    if rd and not re.search(r'BusinessProfile\.%s\(' % g, rd[1]):
                        fail('%s: reader %s never calls BusinessProfile.%s' % (ctx, d['kv']['reader'], g))
    for g, typ in m.profile:
        d = decided.get(g)
        if not d:
            fail('BusinessProfile.%s (%s) has no decision for Hudhud: add it to decisions.tsv' % (g, typ))
            continue
        kv = d['kv']
        note = ';'.join('%s=%s' % (k, kv[k]) for k in NOTE_KEYS if kv.get(k))
        m.lines.append(('profile', g, typ, d['decision'], note))


def check_screens(m):
    decided = {}
    page_screens = {s for s in m.pages.values() if s}
    for d in m.rows:
        if d['kind'] != 'screen':
            continue
        ctx, s, dec, kv = d['ctx'], d['subject'], d['decision'], d['kv']
        if s not in m.acts:
            fail('%s: %s is not an activity in the manifest (stale decision)' % (ctx, s))
            continue
        if s in decided:
            fail('%s: %s decided twice' % (ctx, s))
            continue
        decided[s] = d
        if dec in ('INFRA', 'NOT-ASKED', 'GAP') and not kv.get('why'):
            fail('%s: %s needs a why=' % (ctx, dec))
        if dec == 'OPEN' and s not in page_screens:
            fail('%s: %s is OPEN but no Hudhud page opens it' % (ctx, s))
        if dec != 'OPEN' and s in page_screens:
            fail('%s: %s has a Hudhud page, so it is OPEN, not %s' % (ctx, s, dec))
        if dec == 'CHILD':
            if kv.get('of') not in m.acts:
                fail('%s: CHILD needs of=<an activity>' % ctx)
        if dec == 'OPENED':
            rd = reader(kv['by'], ctx) if kv.get('by') else fail('%s: OPENED needs by=Class.function' % ctx)
            if rd and not re.search(r'\b%s::class\.java' % s, rd[1]):
                fail('%s: %s never opens %s' % (ctx, kv['by'], s))
    for s in sorted(m.acts):
        d = decided.get(s)
        if not d:
            fail('screen %s has no decision for Hudhud: add it to decisions.tsv' % s)
            continue
        kv = d['kv']
        note = ';'.join('%s=%s' % (k, kv[k]) for k in ('of', 'by', 'why', 'q') if kv.get(k))
        page = sorted(k for k, v in m.pages.items() if v == s)
        if page:
            note = (note + ';' if note else '') + 'pages=' + ','.join(page)
        gate = m.screens.get(s, 'NONE')
        parent = m.acts[s] or '-'
        m.lines.append(('screen', s, 'parent=%s;gate=%s' % (parent, gate), d['decision'], note))
        if d['decision'] == 'GAP':
            m.gap_screens.append(s)


def check_tiles(m):
    page_screens = {s for s in m.pages.values() if s}
    exempt = {}
    for d in m.rows:
        if d['kind'] == 'tile':
            if not d['kv'].get('why'):
                fail('%s: EXEMPT needs a why=' % d['ctx'])
            exempt[d['subject']] = d
    seen = set()
    for t in m.tiles:
        seen.add(t['screen'])
        if t['screen'] in page_screens:
            m.lines.append(('tile', t['label'], t['screen'], 'OPEN', 'pages=' + ','.join(sorted(k for k, v in m.pages.items() if v == t['screen']))))
        elif t['screen'] in exempt:
            m.lines.append(('tile', t['label'], t['screen'], 'EXEMPT', 'why=' + exempt[t['screen']]['kv'].get('why', '')))
        else:
            fail('Home tile %s (%s) has no Hudhud page: write one, or give it a tile EXEMPT decision with a why=' % (t['label'], t['screen']))
    for s in exempt:
        if s not in seen:
            fail('tile decision for %s but no Home tile opens it (stale)' % s)


def check_more(m):
    decided = {}
    page_screens = {s for s in m.pages.values() if s}
    for d in m.rows:
        if d['kind'] != 'more':
            continue
        if d['subject'] in decided:
            fail('%s: %s decided twice' % (d['ctx'], d['subject']))
        decided[d['subject']] = d
        if d['decision'] in ('NOT-ASKED', 'GAP') and not d['kv'].get('why'):
            fail('%s: %s needs a why=' % (d['ctx'], d['decision']))
        if d['decision'] == 'ANSWERED':
            if d['kv'].get('ask') not in m.asks:
                fail('%s: ANSWERED needs ask=<one of Hudhud.Ask>, not %r (the case that tells it in words)' % (d['ctx'], d['kv'].get('ask')))
            if not d['kv'].get('why'):
                fail('%s: ANSWERED needs a why= (what he says about it)' % d['ctx'])
        tier = d['kv'].get('tier')
        if tier not in TIERS:
            fail('%s: every More decision needs tier=LOOK, OPEN or NEVER (what Hudhud may do with it), not %r' % (d['ctx'], tier))
        elif d['decision'] == 'OPEN' and tier != 'OPEN':
            fail('%s: an OPEN More entry has tier=OPEN, not %s' % (d['ctx'], tier))
        elif d['decision'] == 'NOT-ASKED' and tier != 'NEVER':
            fail('%s: a NOT-ASKED More entry has tier=NEVER, not %s' % (d['ctx'], tier))
    seen = set()
    for e in m.more:
        seen.add(e['label'])
        d = decided.get(e['label'])
        if not d:
            fail('More entry %s (%s) has no decision for Hudhud: add it to decisions.tsv' % (e['label'], e['target']))
            continue
        if d['decision'] == 'OPEN' and not (e['screen'] and e['target'] in page_screens):
            fail('%s: More entry %s is OPEN but %s has no Hudhud page' % (d['ctx'], e['label'], e['target']))
        note = ';'.join('%s=%s' % (k, d['kv'][k]) for k in ('tier', 'ask', 'why', 'q') if d['kv'].get(k))
        m.lines.append(('more', e['label'], 'group=%s;target=%s' % (e['group'], e['target']), d['decision'], note))
    for k, d in decided.items():
        if k not in seen:
            fail('%s: More entry %s no longer exists (stale decision)' % (d['ctx'], k))


# ------------------------------------------------------------------------------------------------ output

def render(m):
    out = ['# GENERATED by scripts/gen_hudhud_catalog.py: the app\'s own map and what Hudhud does with each item.',
           '# Do not edit; change shared/hudhud/catalog/decisions.tsv and run the script. kind, subject, detail, decision, notes.']
    for e in m.ents:
        out.append('\t'.join(('table', e['table'], 'class=%s;file=%s;columns=%d' % (e['cls'], e['file'], len(e['cols'])), '-', '')))
    for ln in sorted(m.lines, key=lambda t: (t[0] != 'column', t[0] != 'profile', t[0], t[1])):
        out.append('\t'.join(ln))
    return '\n'.join(out) + '\n'


def report(m):
    def count(kind, dec=None):
        return sum(1 for l in m.lines if l[0] == kind and (dec is None or l[3] == dec))
    cols = [l for l in m.lines if l[0] == 'column']
    print('Tables %d, stored columns %d' % (len(m.ents), len(cols)))
    for dec in ('READ', 'GAP', 'WITHHELD', 'STRUCT'):
        print('  %-9s %4d' % (dec, count('column', dec)))
    print('Per table (READ / GAP / WITHHELD / STRUCT):')
    for e in m.ents:
        t = [l for l in cols if l[1].startswith(e['table'] + '.')]
        print('  %-24s %3d / %3d / %3d / %3d' % (e['table'], *(sum(1 for l in t if l[3] == d) for d in ('READ', 'GAP', 'WITHHELD', 'STRUCT'))))
    print('Profile fields %d: READ %d, GAP %d, WITHHELD %d' % (len(m.profile), count('profile', 'READ'), count('profile', 'GAP'), count('profile', 'WITHHELD')))
    print('Screens %d:' % len(m.acts), ', '.join('%s %d' % (d, count('screen', d)) for d in sorted(DECISIONS_OF['screen'])))
    print('Home tiles %d (all with a Hudhud page, or exempt: %d)' % (len(m.tiles), count('tile', 'EXEMPT')))
    print('More entries %d:' % len(m.more), ', '.join('%s %d' % (d, count('more', d)) for d in sorted(DECISIONS_OF['more'])))
    print('Help pages %d' % len(m.pages))
    print('READ stricter than their screen (%d):' % len(m.stricter))
    for ctx, perm, screen, sg, why in m.stricter:
        print('  %s gate %s, screen %s asks %s: %s' % (ctx, perm, screen, sg, why))
    print('Columns read indirectly (via=): %d' % len(m.indirect))
    print('Honest backlog: GAP columns %d, GAP screens %d (%s)' % (len(m.gap_columns), len(m.gap_screens), ', '.join(m.gap_screens)))


def explain(m):
    hudhud = [f for f in kotlin_files() if os.path.basename(f) in ('HudhudFacts.kt', 'HudhudBook.kt', 'Hudhud.kt', 'HudhudSheet.kt')]
    bodies = {}
    for f in hudhud:
        for fn, body in functions(f).items():
            bodies[os.path.splitext(os.path.basename(f))[0] + '.' + fn] = body
    for e in m.ents:
        print('== %s (%s)' % (e['table'], e['cls']))
        for c in e['cols']:
            where = sorted(k for k, b in bodies.items() if re.search(r'\b%s\b' % re.escape(c['prop']), b))
            print('   %-22s %-14s %s' % (c['col'], c['type'][:14], ' '.join(where)))


def main():
    args = sys.argv[1:]
    m = build()
    if '--explain' in args:
        explain(m)
        return 0
    text = render(m)
    if '--report' in args:
        report(m)
    if problems:
        print('Hudhud catalog: %d problem(s)' % len(problems), file=sys.stderr)
        for p in problems:
            print('  ' + p, file=sys.stderr)
        return 1
    if '--check' in args:
        have = read(CATALOG) if os.path.exists(CATALOG) else ''
        if have != text:
            print('Hudhud catalog: %s is stale; run python3 scripts/gen_hudhud_catalog.py and commit it' % rel(CATALOG), file=sys.stderr)
            return 1
        print('Hudhud catalog ok: %d tables, %d columns, %d screens, %d tiles, %d more entries, %d profile fields' % (
            len(m.ents), sum(len(e['cols']) for e in m.ents), len(m.acts), len(m.tiles), len(m.more), len(m.profile)))
        return 0
    if '--report' not in args:
        os.makedirs(os.path.dirname(CATALOG), exist_ok=True)
        with open(CATALOG, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
        print('wrote %s (%d lines)' % (rel(CATALOG), text.count('\n')))
    return 0


if __name__ == '__main__':
    sys.exit(main())
