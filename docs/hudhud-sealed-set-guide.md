# Sealed set v2: how to write it (D18)

The sealed set is the one clean exam of Hudhud. It is written by people who never tuned him, **kept in the owner's own
folder, never in this repository** (D12), hashed once and opened once, at the release gate (Marhala 4). Claude never
writes, sees or edits its sentences.

## Who writes what

- 400 sentences by the owner: 300 Roman Urdu, 100 English.
- 100 by a second writer who has never used the test build (a friend, a shop helper): any of the two languages.
- Written WITHOUT asking Hudhud first, the way a shopkeeper would really ask, with invented names only.

## The file (tab-separated, UTF-8), one sentence a line

```
id	writer	language	sentence	meaning	person	amount	side
S0001	owner	ur-Latn	Bashir ka kitna baqi hai	customer balance	Bashir	-	to_get
S0002	owner	en	how much did I sell today	sales today	-	-	-
S0003	friend	ur-Latn	Kal Ali ko 500 dunga	future payment (no entry)	Ali	500	to_give
```

- `meaning`: in plain words what the right answer is (the labelling guide's words if you know them; plain words are fine).
- `person`, `amount`, `side` (to_get / to_give / -): only when the sentence names them; `-` otherwise.
- About ten sentences for each kind of question you really ask (balances, sales, stock, cheques, entries, how-to...).

## After writing

1. Keep the file in your own folder (not in the app, not on GitHub).
2. Claude runs `python3 scripts/hudhud_sets.py --check --sealed <your folder>`: it prints counts only, never a sentence,
   and reports exact and near duplicates against everything Hudhud learnt from.
3. The file's SHA-256 is written in the decision log; from then on nobody changes it.
4. At the release gate it is opened once; the result per language and the hard-zero gates go into the model card.
