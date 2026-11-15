#!/usr/bin/env python3
"""Every Room column is carried by the backup, sync names real columns, and the
owner's private columns stay out of a helper's copy (secrets = false).

Drift protection, not proof (lesson 125): it reads names, it does not run a backup.
  1. each @Entity column is written by BackupCodec as put("<name>") (a column the
     backup never names is lost by a restore) -- DERIVED lists what is rebuilt on
     restore instead;
  2. every column SyncSchema names (localOnly, natural) exists on its table;
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

for m in re.finditer(r'Table\("(\w+)"([^\n]*)\)', sy):
    t, rest = m.group(1), m.group(2)
    if t not in ents:
        fail.append('SyncSchema names a table with no entity: ' + t)
        continue
    cols = {c['col'] for c in ents[t]['cols']}
    for grp in re.finditer(r'(localOnly|natural)\s*=\s*(?:setOf|listOf)\(([^)]*)\)', rest):
        for w in re.findall(r'"(\w+)"', grp.group(2)):
            if w not in cols:
                fail.append('SyncSchema %s.%s names a column that is not there: %s' % (t, grp.group(1), w))

text = '\n'.join(bc)
for (t, col), expr in sorted(PRIVATE.items()):
    if t not in ents or col not in {c['col'] for c in ents[t]['cols']}:
        fail.append('PRIVATE lists a column that is gone: %s.%s' % (t, col))
        continue
    if ('%s?.takeIf { secrets }' % expr) not in text:
        fail.append('private column %s.%s is not held back from a helper copy' % (t, col))

if fail:
    print('\n'.join(fail)); sys.exit(1)
print('book columns ok: %d tables, %d private columns held back from a helper copy' % (len(ents), len(PRIVATE)))
