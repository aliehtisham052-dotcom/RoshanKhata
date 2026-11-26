# The owner's decisions, 25 November 2026

The owner accepted the professional recommendation for each (25 Nov 2026, 07:12).

| Id | Decision |
|---|---|
| D14 | Backup protection, hybrid. The automatic Google Drive backup has no password (the Google account protects the app's own Drive folder). A backup FILE that leaves the phone (Downloads, WhatsApp, a USB stick or the computer's outside folder) is password-protected by default; the owner can turn it off; a hint helps him remember. Every backup screen says plainly whether a copy is protected or open. |
| D10 | The assistant's voice, first step: on the phone only by default where the phone has the language's pack; where it has none, ask once whether to use the online recogniser, saying the voice then goes to Google. |
| D13 | Money stays a Double under the money contract (Money.round, TicketSpread, the formula book). Moving to whole minor units (paisa) is done only if field testing finds a real rounding fault, not before January 2027. |
| D8 | Cashier totals: a cashier sees only his own sales and counts the drawer blind (as today). The tie button (linking goods to products) needs EDIT_BOOK and is written in Who did what. Windows App Lock says plainly that it hides the book and does not encrypt it (done). T39 (the master plan's item 39): the assistant reading a customer's private note aloud at the counter. Professional choice, accepted with the rest: the note is written, never spoken, while counter mode is on or a cashier is signed in; the owner's own phone keeps the voice. |

## The rest, 25 November 2026 (08:20, the owner's answers)

| Id | Decision |
|---|---|
| D1 | When the assistant is unsure it answers and writes "Taken as: …" over the card, with the other readings as chips; it asks back only to choose between named people or products. |
| D2 | A customer's private note is written in full; long numbers in it are shown, never spoken (and with D8, not spoken at all at a counter). |
| D3 | The assistant changes only the LOOK settings (text size, theme), each with Undo; everything else it opens or says where it is. |
| D4 | No large on-device language model; rules plus the learnt sense, figures only from the book (H9 stays optional, on evidence only). |
| D5 | The same nine languages. |
| D6 | The same 25 trades. |
| D7 | The assistant writes nothing itself; it fills a form and the owner saves. |
| D11 | The Windows app goes to the Microsoft Store as MSIX with the final release (21-27 Dec); it is sent only after the owner's yes on that day. |
| D12 | The sealed test set stays in the owner's own folder, outside the repository (as v1 already is); no new account. |
| D9 | The owner's devices for the real-device runs (B5, B6, H7): an Oppo Reno 11F phone and an HP EliteBook 830 G7 laptop (25 Nov 08:19; Android and Windows versions to be read on the devices). |

## Hudhud's training master plan v1, 25 November 2026 (evening, the owner's answers at 20:10)

The owner read the Hudhud Training Master Plan v1 (`docs/hudhud-master-plan-v1.md`) and accepted the professional
recommendation for each of D15–D21 ("Sab haan").

| Id | Decision |
|---|---|
| D15 | D1 changes in one place: when the money's side (to get / to give), the amount or the person is not certain, Hudhud asks one question first ("lene hain ya dene?") instead of "Taken as: …". Everywhere else D1 stays as it is. Built in Marhala 1. |
| D16 | Business knowledge questions (margin, markup, cash flow, stock) are allowed after the release, in eight modules only, every answer with its source and date. The 16 Nov refusal of cricket, jokes and general knowledge stays. Marhala 9. |
| D17 | Sindhi, Bengali, Farsi and Arabic are called "beta" in the app and in every figure until a native speaker has read them. Marhala 4 (release) and 11. |
| D18 | Sealed set v2: 500 new sentences (400 Roman Urdu, 100 English), written without looking at Hudhud's answers — 400 by the owner and 100 by a second writer who has never tuned Hudhud, measured and reported apart (the owner's lists were the dev set, so his writing alone would say how Hudhud reads him, not other shopkeepers). The other seven languages' sealed figures are "not certified" in H8. Kept in the owner's folder (D12). Marhala 3. |
| D19 | Concentration: first only the share of all credit one party holds is shown; an alert and its threshold come later, set by the owner or by measurement. Marhala 10. |
| D20 | The master plan v1 is the base. A new plan is fitted into its phases (what exists, what is new, what goes against a decision); a v2 only when new evidence changes a decision, saying what changed, why and the source. |
| D21 | The repository stays public: GitHub's standard runners are free for public repositories, while private ones draw on a monthly allowance (the owner's rule: free routes only). Hudhud's name and training bank are therefore public; outside AI tools get only the name-free context pack. |

### 26 November 2026 (03:44): the timeline, not the limits

The owner: no phase is kept for later; every phase is worked now, without stopping. So D16's "after the release" no
longer holds: business knowledge (Marhala 9) is built now, with D16's limits unchanged (eight modules, every answer
with its source and date; cricket, jokes and general knowledge still refused). Marhalas 5-10 start now in the plan's
order; the plan's rules stay (each piece with its tests, no big refactor, figures only from the book). Marhala 8's
retrain still needs the owner's own check-log mistakes (decided 25 Nov: no synthetic retrain). The owner will use the
test build for two days (about 70 real questions a day, marking wrong answers) instead of seven (Marhala 2), and will
look at the sealed set (D18) later; Claude never writes its sentences.

### 26 November 2026 (morning): two questions answered

| Id | Decision |
|---|---|
| D22 | Business-knowledge sources: OpenStax is replaced ("Source badal do"). Its books are CC BY 4.0, but each atom must point at a source the owner can open and check in one step; the atoms of Marhala 9 take their source from the rights register's allowed hosts, and an OpenStax link in an atom fails `check_knowledge_atoms.py`. |
| D23 | Zakat stays as it is ("Yahi rakho"): "on whom is Zakat due" opens the Zakat page and its calculator; Hudhud gives no ruling of his own, and rulings are sent to a qualified Hanafi scholar. The rate's source is the comment above `Zakat.RATE`. |
