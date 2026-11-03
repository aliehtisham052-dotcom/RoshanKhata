# Lessons for the next app

What the November 2026 audit of Roshan Khata (Android + Windows, Kotlin Multiplatform, live on Google Play) taught,
written so the next app starts with them. Each line is a rule, then the mistake that taught it. The project's own
running list is in CLAUDE.md ("Conventions, learned the hard way"); this file is the part worth carrying to any app.

## Data safety comes first

- **Rows that must agree are written, deleted, restored and edited together.** A cash sale and the payment taken
  with it were two ledger rows pointing at each other. Deleting, restoring or editing one alone left the customer
  owing money he had paid. Write both in one transaction, delete and restore them together, ask when one is edited,
  and clear the link when one is purged for good.
- **A backup reader must accept what the app itself can leave behind.** After one half of such a pair was purged,
  the other still pointed at it, and the restore of that backup failed on the dangling link. Every reader repairs or
  drops links to rows that are not in the file; a test restores a backup made in exactly that state.
- **Every write that is more than one statement is one database transaction, started from an app-wide scope.** A
  screen's scope is cancelled by a rotation or a Back press between two writes; the book is then half written.
- **Open the store build's real database file in a test and migrate it to the newest version.** Count every row,
  sum and balance before and after. "The migration compiles" is not "no row was lost".
- **A device lock that is not answering is not a lock that refuses.** Treat "cannot ask now" and "the answer was
  no" differently; never let either wipe a pairing or open the book.
- **Test the lock screen on the oldest Android the app supports, with a PIN-only phone.** `noHistory` on the lock
  activity closed it while Android 7-10 showed its own PIN screen, so the answer never came back and the owner was
  locked out of his own book.

## Money

- **Never compare a money Double with 0.** Use one epsilon, tied to the currency's smallest coin (2 decimals for
  Rs, 3 for KWD, 0 for Rp). 1,000.10 − 1,000.00 − 0.10 is not zero in a Double.
- **Round before deciding whether a figure is whole,** and never print E-notation: 11,111,111.01 became
  "1.111111101E7" and the amount box refused its own figure.
- **One rule for one decision, on every platform.** The phone and Windows each decided "is this sale paid now?"
  with their own conditions; the phone forgot unpriced goods and wrote no payment. Put the rule in shared code
  (`RateType.forEntry`) and call it from both.
- **A sign or a side is part of the figure.** A round-off of −0.40 printed as "Rs 0.40", and a balance without
  "you will pay / you will receive" lost which way it ran. Print the minus and the side's words.

## Input and screens

- **Android's `numberDecimal` filter ignores the language** unless the box names its keyboard's locale: it passes
  only 0-9 and ".", so an Indonesian "2,5" became 25 without a word. Widen the filter to the language's decimal mark
  and every keyboard's digits, and read every typed number through one locale-aware parser.
- **A fixed-height panel and large text do not fit together.** A 330 dp keypad on a 720 dp phone at 150% text hid
  the balance preview. Size panels to the screen with a floor that keeps 48 dp touch targets, and show a fade
  where more of the form is below.
- **A table column of fixed width wraps; it never cuts a date or a unit.**
- **What a cashier sees is decided by role, not by screen.** A customer's payment habits are the owner's.
- **Two counts of the same thing must be one rule.** Home said 4 while the bell said 3, because one counted the
  late payers' row and the other did not.
- **Names sort by their first letter,** past any bracket, mark or emoji written before it.

## Notifications and background work

- Anything with money or a name is `VISIBILITY_PRIVATE` with a public version for the lock screen.
- Check that notifications are allowed before promising one, and say so in the setting when they are not.
- Periodic work is unique and replaced with `UPDATE`, never cancelled mid-run by a new schedule.
- A notice is for the shop that was open when it was due; a switch of shop cancels the old shop's notices.

## Logs, privacy and the store

- **A log that can leave the device is cleaned before it is written.** Exception messages carry what was typed
  (a phone number, an amount, a name in a path); "Report a problem" mailed them whole.
- **Test data in a public repository is made up.** Real shop numbers and balances in tests stay in git history.
- **The store's Data safety form lists what the SDKs collect, not only what the app's own code sends.** ML Kit
  sends device and performance data to Google; read each SDK's data-disclosure page before answering the form, and
  keep the store listing's words true to it.
- **Check 16 KB page alignment of native libraries on every build** (`scripts/check_16kb.py`); a new library or a
  new version of one can break it without a sound.

## Working without a local build

- When CI fails, read the check-run annotations through the GitHub API; do not guess.
- Gradle stops at the first failing task: one failing test hides every later failure. Fix it, push, and read the
  next run before calling anything green.
- A test that asserts the old behaviour is changed in the same commit as the behaviour, with the reason written in it.
- Look at the proof pictures after any change to a screen. Passing tests have shown blank screens.
- Verify every audit finding in the code before fixing it, and research platform behaviour in the official
  documentation before deciding. Write down what was not tested on a real device.
