#!/usr/bin/env python3
"""Every Room column is carried by the backup, sync names real columns, and the
owner's private columns stay out of a helper's copy (secrets = false).

Drift protection, not proof (lesson 125): it reads names, it does not run a backup.
  1. each @Entity column is written by BackupCodec as put("<name>") (a column the
     backup never names is lost by a restore) -- DERIVED lists what is rebuilt on
     restore instead;
  2. every column SyncSchema names (localOnly, natural, fks) exists on its table,
     every table a pointer (fks) points at is a synced table with a uid, every
     Room table with a uid is in SyncSchema, and every "...Id" column of a synced
     table is a pointer in fks or in NOT_POINTER with its reason (a new pointer
     column left out of fks would travel as this device's own row number);
  3. each PRIVATE column is written on a line that reads `secrets`, so a helper's
     copy and a Waris snapshot cannot carry it. A new private column goes here.
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import gen_hudhud_catalog as g

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data')
DERIVED = {('products', 'nameKey'), ('products', 'normalisedName')}   # rebuilt on restore
# (table, column): the expression the backup writes, which must carry `takeIf { secrets }`
PRIVATE = {('parties', 'note'): 'p.note', ('products', 'costPrice'): 'p.costPrice',
           ('sale_items', 'costRate'): 'i.costRate', ('staff', 'pinHash'): 'st.pinHash',
           ('staff', 'pinSalt'): 'st.pinSalt'}

ents = {e['table']: e for e in g.entities()}
bc = open(os.path.join(DATA, 'BackupCodec.kt')).read().split('\n')
sy = open(os.path.join(DATA, 'SyncSchema.kt')).read()
fail = []

for t, e in ents.items():
    for c in e['cols']:
        if (t, c['col']) in DERIVED:
            continue
        if not any(('"%s"' % c['col']) in l or ('"%s"' % c['prop']) in l for l in bc):
            fail.append('backup never names %s.%s' % (t, c['col']))

def sync_tables(src):
    """Each Table(...) entry of SyncSchema.tables, one or several lines, by balanced parentheses."""
    body = src[src.index('val tables'):]
    body = body[body.index('listOf(') + len('listOf('):]
    out, i = [], 0
    while True:
        j = body.find('Table(', i)
        if j < 0:
            break
        depth, k = 0, j + len('Table')
        while True:
            ch = body[k]
            depth += ch == '('
            depth -= ch == ')'
            k += 1
            if depth == 0:
                break
        out.append(body[j + len('Table('):k - 1])
        i = k
    return out

synced = {}
for entry in sync_tables(sy):
    m = re.match(r'\s*"(\w+)"', entry)
    if not m:
        if entry.strip() != 'SETTINGS':
            fail.append('SyncSchema entry not understood: Table(%s)' % entry.strip()[:60])
        continue
    t = m.group(1)
    groups = {}
    for name in ('localOnly', 'natural'):
        g2 = re.search(name + r'\s*=\s*(?:setOf|listOf)\(([^)]*)\)', entry)
        groups[name] = re.findall(r'"(\w+)"', g2.group(1)) if g2 else []
    f2 = re.search(r'fks\s*=\s*mapOf\(([^)]*)\)', entry)
    groups['fks'] = re.findall(r'"(\w+)"\s*to\s*"(\w+)"', f2.group(1)) if f2 else []
    synced[t] = groups

ENTITY_COLS = {t: {c['col'] for c in e['cols']} for t, e in ents.items()}
# (table, column) named ...Id that is NOT a pointer at another row, with the reason.
NOT_POINTER = {('invoices', 'templateId'): "the invoice design's number, not a row"}

for t, groups in synced.items():
    if t not in ents:
        fail.append('SyncSchema names a table with no entity: ' + t)
        continue
    cols = ENTITY_COLS[t]
    for name in ('localOnly', 'natural'):
        for w in groups[name]:
            if w not in cols:
                fail.append('SyncSchema %s.%s names a column that is not there: %s' % (t, name, w))
    for col, target in groups['fks']:
        if col not in cols:
            fail.append('SyncSchema %s.fks names a column that is not there: %s' % (t, col))
        if target not in ents:
            fail.append('SyncSchema %s.fks %s points at a table with no entity: %s' % (t, col, target))
        elif target not in synced:
            fail.append('SyncSchema %s.fks %s points at a table that does not sync: %s' % (t, col, target))
        elif 'uid' not in ENTITY_COLS[target]:
            fail.append('SyncSchema %s.fks %s points at a table with no uid: %s' % (t, col, target))
    pointers = {c for c, _ in groups['fks']}
    for c in sorted(cols):
        if (c.endswith('Id') and c not in pointers and (t, c) not in NOT_POINTER
                and c not in groups['localOnly']):
            fail.append('%s.%s looks like a pointer but SyncSchema has no fks entry for it '
                        '(add it to fks, or to NOT_POINTER with the reason)' % (t, c))
for (t, c) in NOT_POINTER:
    if c not in ENTITY_COLS.get(t, set()):
        fail.append('NOT_POINTER lists a column that is gone: %s.%s' % (t, c))
for t, cols in ENTITY_COLS.items():
    if 'uid' in cols and t not in synced:
        fail.append('table %s has a uid but SyncSchema does not list it (it would never sync)' % t)

text = '\n'.join(bc)
for (t, col), expr in sorted(PRIVATE.items()):
    if t not in ents or col not in {c['col'] for c in ents[t]['cols']}:
        fail.append('PRIVATE lists a column that is gone: %s.%s' % (t, col))
        continue
    if ('%s?.takeIf { secrets }' % expr) not in text:
        fail.append('private column %s.%s is not held back from a helper copy' % (t, col))

if fail:
    print('\n'.join(fail)); sys.exit(1)
print('book columns ok: %d tables (%d synced, %d pointers), %d private columns held back from a helper copy'
      % (len(ents), len(synced), sum(len(v['fks']) for v in synced.values()), len(PRIVATE)))
