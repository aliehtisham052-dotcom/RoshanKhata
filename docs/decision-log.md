# Decision log (index)

One row for every owner's decision D1–D21 (plan v1, Marhala 12). This file only points: the decision's words are in the file
named in "Recorded in", which stays the record and is not changed here. "Phase" is the Hudhud master plan's phase
(`docs/hudhud-master-plan-v1.md`) the decision governs: *(stated)* where the decision or the plan names it, *(read)* where
it is read from what the decision governs and the plan's phase table. `python3 scripts/hudhud_manifest.py --check` fails when a
D-decision is in a `docs/decisions-*.md` and not here, or here and in none, or its row does not name its file.

Not in this namespace: the Formula Book's discrepancies "D1"–"D12" (`docs/formula-book.md`, e.g. "D2 Sales total against sales
report revenue") are a separate list that happens to share the letter; they are not owner's decisions.

| Id | Date (the owner's answer) | Decision, in one line | Recorded in | Phase it affects |
|---|---|---|---|---|
| D1 | 25 Nov 2026, 08:20 | When unsure, Hudhud answers with "Taken as: …" and the other readings as chips; asks back only to choose between named people or products (changed for money side, amount and person by D15). | docs/decisions-25nov2026.md | 6 (SAFE_GUESS outcome) *(stated)*; 1 through D15 |
| D2 | 25 Nov 2026, 08:20 | A customer's private note is written in full; long numbers in it are shown, never spoken. | docs/decisions-25nov2026.md | 4 (hard-zero gate "private note spoken at the counter"), 11 voice *(read)* |
| D3 | 25 Nov 2026, 08:20 | Hudhud changes only the look settings (text size, theme), each with Undo; anything else it opens or points to. | docs/decisions-25nov2026.md | all: plan "Never" 3; 4 (gate "a write by Hudhud") *(stated)* |
| D4 | 25 Nov 2026, 08:20 | No large on-device language model: rules plus the learnt sense, figures only from the book (H9 optional, on evidence). | docs/decisions-25nov2026.md | all: plan "Never" 1; 5 (the model decision) *(stated)* |
| D5 | 25 Nov 2026, 08:20 | The same nine languages. | docs/decisions-25nov2026.md | 11 (languages and voice) *(read)* |
| D6 | 25 Nov 2026, 08:20 | The same 25 trades. | docs/decisions-25nov2026.md | app-wide; 9 (business knowledge per trade) *(read)* |
| D7 | 25 Nov 2026, 08:20 | Hudhud writes nothing itself; it fills a form and the owner saves. | docs/decisions-25nov2026.md | all: plan "Never" 3; 4 (gate); 6 (DRAFT outcome) *(stated)* |
| D8 | 25 Nov 2026, 07:12 | A cashier sees only his own sales and counts the drawer blind; the tie button needs EDIT_BOOK; App Lock says it hides, not encrypts; a private note is written, never spoken, at a counter (T39). | docs/decisions-25nov2026.md | 4 (gates: owner's figure to a cashier, note at the counter); 10 *(stated)* |
| D9 | 25 Nov 2026, 08:19 | The owner's devices for the real-device runs (B5, B6, H7): an Oppo Reno 11F phone and an HP EliteBook 830 G7 laptop. | docs/decisions-25nov2026.md | 2 (real use, phone speed table) *(stated: phase 2 names the phone and B5, B6, H7)* |
| D10 | 25 Nov 2026, 07:12 | Voice on the phone only by default where the phone has the language's pack; else ask once before using the online recogniser (the voice then goes to Google). | docs/decisions-25nov2026.md | 11 (voice) *(read)*; app-wide privacy |
| D11 | 25 Nov 2026, 08:20 | The Windows app goes to the Microsoft Store as MSIX with the final release (21–27 Dec), sent only after the owner's yes that day. | docs/decisions-25nov2026.md | 4 (release gate, Android/Windows parity) *(read)* |
| D12 | 25 Nov 2026, 08:20 | The sealed test set stays in the owner's own folder outside the repository; no new account. | docs/decisions-25nov2026.md | 3 (clean exam) *(read)*; 12 (never in the manifest) |
| D13 | 25 Nov 2026, 07:12 | Money stays a Double under the money contract; whole minor units only if field testing finds a real rounding fault, not before January 2027. | docs/decisions-25nov2026.md | app-wide (money contract); not a Hudhud phase *(read)* |
| D14 | 25 Nov 2026, 07:12 | Backup protection, hybrid: Drive's automatic backup has no password; a backup file that leaves the phone is password-protected by default; every screen says protected or open. | docs/decisions-25nov2026.md | app-wide (backup); not a Hudhud phase *(read)* |
| D15 | 25 Nov 2026, 20:10 | When the money's side, the amount or the person is not certain, Hudhud asks one question first ("lene hain ya dene?") instead of "Taken as: …". | docs/decisions-25nov2026.md | 1 *(stated)*; 6 (CLARIFY outcome) *(stated)* |
| D16 | 25 Nov 2026, 20:10 | Business-knowledge questions allowed after the release, in eight modules only, each answer with its source and date; cricket, jokes and general knowledge still refused. | docs/decisions-25nov2026.md | 9 *(stated)* |
| D17 | 25 Nov 2026, 20:10 | Sindhi, Bengali, Farsi and Arabic are "beta" in the app and in every figure until a native speaker has read them. | docs/decisions-25nov2026.md | 4 and 11 *(stated)*; 12 (the manifest's native_read column) |
| D18 | 25 Nov 2026, 20:10 | Sealed set v2: 500 new sentences (400 Roman Urdu, 100 English; 400 by the owner, 100 by a second writer), written blind, reported apart; other languages "not certified". | docs/decisions-25nov2026.md | 3 *(stated)* |
| D19 | 25 Nov 2026, 20:10 | Concentration: first only the share of all credit one party holds; an alert and its threshold later, by the owner or by measurement. | docs/decisions-25nov2026.md | 10 *(stated)* |
| D20 | 25 Nov 2026, 20:10 | Master plan v1 is the base; a new plan is fitted into its phases; a v2 only when new evidence changes a decision, saying what, why and the source. | docs/decisions-25nov2026.md | all *(stated)* |
| D21 | 25 Nov 2026, 20:10 | The repository stays public (free GitHub runners); Hudhud's name and bank are therefore public; outside AI tools get only the name-free context pack. | docs/decisions-25nov2026.md | all; 12 (rights register: the bank is public) *(read)* |
| D22 | 26 Nov 2026, morning | Business-knowledge atoms no longer cite OpenStax; each atom names a source from the rights register's allowed hosts. | docs/decisions-25nov2026.md | 9 *(stated)* |
| D23 | 26 Nov 2026, morning | Zakat unchanged: "kis par farz" opens the Zakat page; no ruling of Hudhud's own, rulings to a qualified Hanafi scholar. | docs/decisions-25nov2026.md | 9 *(stated)* |
