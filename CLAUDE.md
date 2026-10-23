# Roshan Khata — project instructions

A Kotlin-native Android ledger for a Pakistani shopkeeper, specifically an
agri-chemical dealer (pesticides, seeds, feed). Brand: Innovation-313.

No account, no server, no ads. Everything stays on the phone.

> This repository is **public**. Never commit tokens, keystores, API keys,
> customer data, phone numbers or personal email addresses to it — including
> into this file.

---

## Shape of the project

- Three modules (Kotlin Multiplatform since 18 Oct 2026):
  - `:app` — the Android app, ~194 Kotlin files, ~47,000 lines
  - `:shared` — everything that does not need Android, ~14,500 lines with
    `:desktop`: sums, voice parsing, search, dates, the Room database, the
    backup format, the shared Compose screens. Android and Windows both run it.
  - `:desktop` — Roshan Khata for Windows (Compose Desktop, MSI)
- About 2,450 strings × 9 locales — English, Urdu, Roman Urdu (`b+ur+Latn`), Sindhi,
  Arabic, Farsi, Hindi, Bengali, Indonesian (`in`)
- Room database, version 38 (schema JSON checked in up to 31; CI writes the rest);
  backup format 15 (an entry's bill photo path, 22 Oct 2026)
- CI builds the **dev** APK on every push to `main` and publishes it to the
  rolling `latest` release: the release code with R8 and resource shrinking,
  ARM only, package `.dev` ("Roshan Khata TEST"). The on-device workflow
  monkeys the same build on the emulator (x86_64 kept with
  `-PdevAllAbis=true`) and checks in the APK that the trade goods lists and
  all nine languages survived the shrinker. `kmp-*` branches build too; their
  APK goes to the `kmp-test` pre-release, never `latest`.
- `desktop.yml` builds the Windows MSI on GitHub's Windows runner and puts it
  on the `desktop-test` pre-release (not signed yet).
- The Windows app (`desktop/src/main/kotlin/.../desktop/`) opens maximised on
  **Home** (1.8.0, 20 Oct redesign, `HomePane.kt`): the phone's Home made wide —
  Net balance card with the eye, get/give, late payers (MainActivity.observeLate's
  rule), today's chips, the four slides, the three tile grids in the phone's
  order. The header (`Shell.kt`, `AppHeader`) is the phone's: shop button,
  Back up now, From phone, bell, More (Data / Display / Help groups, with a
  Theme choice kept in app.properties), and one green Add Party. The rail is
  grouped as the phone's Home (Every day, Business, Tools) with the computer's
  own pages folded under More; colours beyond the shared palette live in
  `Look` (Panes.kt) and dark is read from the theme (`isDark()`), never only
  from Windows. Invoices are list + page: a click shows the invoice in its own
  design beside the list. Khata has the phone's tabs (All / Customers /
  Suppliers), side pills, its five orders and a several-at-once delete
  (`KhataControls.kt`, KhataActivity.rank's rule); the open customer has the
  WhatsApp reminder; More → Text size has the phone's four steps (a font
  scale over Windows' own). The bell and Home's chips come from
  `LedgerAlerts` (:shared — cheques, plans, bills, expiry, cash gap; the
  phone's HomeAlerts calls it and adds its backup and update rows around it);
  the backup chip shows the newest daily copy's time; the open customer shows
  the promised day and season record and has the phone's entry search and
  newest/oldest order; Ctrl+F jumps to the Khata search. Khata also has the
  phone's date filter (today, yesterday, this week, this month — by last
  dealing); invoices have the due date and the phone's Invoice settings
  switches (kept in app.properties; a field already filled always shows);
  a nameless shop is asked its name on opening (ShopNameActivity's question). The shop's trade
  (`TradeKit.kt`) is required on Windows (the owner, 20 Oct): a book with none
  gets the phone's 25-card picker before anything else (More → Shop type to
  change it); `LocalTrade` gives every form the phone's TradeVocab words from
  strings.xml — item and unit hints, starter goods and unit pills, product
  types — and TradeFeatures' switches: Seasons only for agri, Stock register
  and Register only for agri/medical, harvest-day choices only for agri. App Lock on Windows
  (`AppLock.kt`, More → Security): the owner's 4-8 digit PIN, kept only as a
  salted PBKDF2 hash in app.properties; nothing of the book is composed until
  it is typed; five misses wait 30 s. Unlike the phone (Android's own screen
  lock) it is the app's PIN, because a JVM cannot reach Windows Hello; it
  hides the ledger, it does not encrypt the file — the screen says so. A customer's
  statement dialog shows the phone's Report figures first (gave, got, net in
  the window; the whole account's closing balance). A first-run walkthrough
  (`Tour.kt`, the phone's coach words) shows once after trade and name. A shop picture
  (logo, signature, stamp, QR) is cropped before it is kept (`ImageCrop.kt`,
  the phone's Adjust crop: corners resize, inside moves; signature, stamp and
  QR start at their ink). Esc closes every dialog window (`escCloses`). Laptop fit (1.8.2, the owner's photos of 1366x768 at 125%, about 1093 dp): no label may wrap letter by letter — pill and chip rows scroll sideways (`horizontalScroll`, `maxLines = 1, softWrap = false`), the header below 1180 dp keeps only icons with hover tips, Home and the rail are tighter; the Home banner turns every 3 s (the phone's TURN_MS) with a cross-fade; an invoice's ten designs are stepped through with ‹ › (`DesignStepper`, Apply keeps one via `updateInvoice`). ScreenProofTest also draws the laptop size. Motion and contrast (1.8.3, `Motion.kt`): every click target uses `Modifier.press` (hand pointer, a hover wash `Look.hover`, ripple) instead of bare `clickable`; chosen colours ease over 160 ms (`eased`), pages cross-fade over 200 ms, Home rises once; nothing bounces. Control edges are `Look.edge` (3:1 on page, card and rail — `Look.line` is only for dividers and card edges) and chosen pills `Look.pickedFill`/`onPicked` (light green by night); `LookContrastTest` measures them. Proof pictures render a second in so animations have ended. PDFs on the computer (1.8.4, `PdfPreview.kt`): every PDF / Statement / Print button (`StatementPrint.show`) opens the sheet in the app's own preview first (pages, zoom), with Save PDF, Print (Windows' dialog, `printDialog`) and Open folder; `Sheets` reads a Printable or an invoice design's own page sizes; `PdfFiles` writes Documents\Roshan Khata\<title>_<date>.pdf with Apache PDFBox 3.0.4, each page a 2.5 px/pt JPEG (looks exactly as printed, Urdu safe; words not selectable), then opens it in Windows' viewer. PdfFilesTest's PDFs go to desktop-test. The MSI adds java.logging for PDFBox's commons-logging — not exercised by the Gradle tests (full JDK), so check a saved PDF on the laptop. MSI size (1.8.1): ICU4J is
  gone from the MSI (14.7 MB, used only for plural rules — the computer's own
  `pluralCategory` covers the nine languages; PluralRulesDesktopTest holds it
  to ICU4J for 0..10,000, ICU4J is test-only); on the Windows runner a Gradle
  artifact transform drops sqlite-bundled's Linux/macOS natives (4.7 MB). No
  ProGuard yet: Room, Compose and reflection would need a run-time smoke test
  first (lesson 17).
  `ScreenProofTest`
  draws the window (Home, English/Urdu, light/dark, made-up shop) to
  build/screen-proof, put on desktop-test — look at it after any UI change.
  The scrolling section rail (each section wears the
  phone's own tile icon, synced from `app/src/main/res` at build time — plus
  the header glyphs ic_storefront, ic_bell, ic_eye_*, ic_phone, ic_search;
  `TileIconsTest` reads them all) holds: Khata (master-detail, newest dealing first, statement for the whole
  ledger or a period), Follow-up (WhatsApp reminders via wa.me), Cashbook,
  Cheques, Plans, Staff, Seasons, Schemes, Supplier Bills (new bills via
  `insertSupplierBill`), Expiry, Stock register, Products (edit, who bought),
  Purchase rates, Price list, Invoices (new + print, one plain design), Khata
  Report, Register, Cash flow, Insights, Zakat, Calculator, Duplicates, Daily
  copies, Help, Business Profile; Businesses on the header (one book per
  shop: `khata.db` / `khata_b<id>.db`, Business 1 never deleted).
  Money-moving writes that the phone did step by step are DAO transactions
  here (`clearCheque`, `recordPlanPayment`); any new DAO write needs
  `scripts/gen_viewer_dao.py` or `ViewerDaoTest` fails.
  Bump `packageVersion` in `desktop/build.gradle.kts` for every MSI the owner
  installs, or Windows will not upgrade it; the MSIX takes its version from
  the same line. Material's own colours come from `RoshanTheme` (dark mode
  once hid typed text). Entries carry date, goods, payment method, Qarz-e-Hasna and recovery; the bin purges after 30 days. Counter shortcuts: F1 Khata, F2/F3 gave/got,
  Ctrl+N, Ctrl+P. The Khata page exports every party to Excel (CSV), prints
  every statement as one job, and imports customers from another app's CSV
  (`CsvImport`: name, phone, balance -> one opening entry each, in one
  `importOpeningBalances` transaction; the owner picks the file's sign before
  anything is written). Daily copies can also go to a chosen folder (USB,
  Google Drive for Desktop), rewritten on open and on close. Receipts print on
  a Windows-installed thermal printer as an ESC/POS picture (`ThermalReceipt`,
  raw through the spooler; not yet tried on a real printer). Bills and invoices
  are edited (`editSupplierBill`, `saveBillLine`, `updateInvoiceWithItems`);
  sales carry rate type, paid-now pairing (`recordCashForSale`), a batch and
  the payment day (Party.harvestPromise); Expiring traces a batch to its buyers.
  A customer card's QR typed by a USB scanner opens that customer.
  **The phone's printed designs run unchanged on the computer**: invoices
  (InvoicePdfExport, InvoiceTemplateKit), business cards (CardTemplates,
  CardGlyphs, CardLogo) and posters (PosterKit, PosterDesigns, PosterFlat,
  PosterGlyphs, PosterNewStock, PosterWords, PosterMotion, poster3d's Hero,
  CoinFace, SealFace, data's Poster and PosterFields) are COPIED by
  `scripts/gen_desktop_invoice.py`, `gen_desktop_cards.py` and
  `gen_desktop_poster.py` (Strings CI fails if stale — re-run after
  touching the phone's files) and drawn on `desktop/.../gfx`, a small
  android.graphics stand-in over Java2D (StaticLayout, shadows and blur,
  Path.op, SweepGradient, PathParser, drawBitmapMesh, Canvas(bitmap) since
  20 Oct). Posters are drawn flat on the computer (the 3D heroes are
  Filament's); proof pictures go to desktop-test (PosterDesignsTest).
  **Phone to computer over WiFi** (`data/WifiSync`, shared): the computer's
  "From phone" window shows a QR (its LAN addresses, a port, a fresh secret);
  the phone's Backup screen scans it and sends its backup sealed with
  AES-GCM over a plain socket (no HTTP: Android blocks clear text); the
  computer hands it to the usual restore confirmation. The other way (later
  20 Oct) is a pull: the phone's Backup screen asks the computer ("RKG1"
  frame) and gets its book sealed the same way, then shows its own restore
  confirmation — a whole copy each way, never a merge.
  **1.9.0 (20 Oct, the shortfall list):** `BookHealth` runs SQLite's
  quick_check before Room opens the book; a bad file is kept as
  `khata.db.corrupt-<time>` and the book is rebuilt from the newest daily
  copy that parses (own folder, then the outside one), told once in a
  dialog. `Reminders` says the bell's rows and a week-old backup once a day
  as a Windows tray notification (the phone's ReminderWorker). `UpdateCheck`
  reads `version.json` from the desktop-test release once a day (desktop.yml
  writes it) and puts a newer version in the bell. Today's daily copy is
  rewritten every hour while the app is open. A receipt can be saved as a
  PNG from the printer choice. More → Report a problem (`ReportProblem.kt`,
  with `CrashLog` writing uncaught exceptions to `logs/errors.log`) opens
  the mail program or copies the report. Proof pictures of every design go
  to the desktop-test release. Shop pictures (logo, signature, stamp, QR) and
  photos live per computer (the text backup carries none). The MSI's Java
  runtime has only the modules in desktop/build.gradle.kts (no java.prefs).
  **1.10.0 (20 Oct, the Fluent face):** the window is drawn as Windows 11
  draws its own, after the owner's Figma mockups (file "Roshan Khata Windows —
  Fluent redesign", 11 screens, the colourful Home chosen). `Fluent.kt` holds
  the controls every pane now reaches for: `FluentButton` (32 dp, 4 dp
  corners; ACCENT / STANDARD / SUBTLE / DANGER), `IconAction`, `FluentPill`,
  `FluentField` (a label above a 32 dp box, the accent line along its foot
  while focused; `big` for an amount), `FluentCard`, `PageHeader` (26 sp
  title, its line, the page's actions on the end side), `StatCard` (the
  figure cards with a soft gradient of their tint), `Avatar`, `TableHead`,
  `MonoTile` (a tile glyph in one colour). `Look` (Panes.kt) gained the
  surfaces — base, layer, layerAlt, subtle, control, strokeCard — the accent
  (#1A7F50 by day: Figma's #1F8F5C fell short of 4.5:1 for white words;
  #5FD9A3 by night), the four tints and `ctl`/`card` corners; the edge and
  chosen colours still pass LookContrastTest. The rail is a NavigationView
  (224 dp, 36 dp rows, a 3 dp accent bar on the open one, glyphs in ink), the
  header one flat band on `Look.base` with the shop, 32 dp buttons, the bell's
  badge and the one accent button; the content area sits on `Look.layerAlt`
  with its top start corner rounded. Home is the mockup: the day as the title,
  three figure cards (will get, will give, cash in hand — the cashbook's two
  sums, `homeFigures`), today's entries as a table (`entriesInRange`; the
  recent customers when the day has none), the bell's rows and six quick
  actions beside it; the slides and the tile grids are gone (the rail holds
  every section). Khata got a page head and card rows (avatar, name, phone ·
  last dealing, the balance and which way it runs); Add Party and the entry
  editor use FluentField (the amount big with the currency sign, the day as a
  date box). Three strings were added in all nine languages
  (home_cash_in_hand, home_quick_actions, home_col_time). Phase 2 (the same
  evening, the owner: one theme on every Windows screen, no generic page):
  Fluent.kt also holds the frames — `FluentPage` (head + actions, 24/18 dp),
  `ListCard`/`ListHead`, `EmptyState` (tile on a disc, a line, the one
  action), `DialogFrame` (a ContentDialog inside a DialogWindow: title,
  scrolling body, footer band), `FigureCard`, `DateBox`, `PartyPicker`,
  `Tag`, `Modifier.card()`, and `Outlined` — Material's OutlinedTextField in
  Fluent's colours and corners for the forms not yet on FluentField (every
  pane's `OutlinedTextField(` became `Outlined(`). Cashbook and Cheques were
  rebuilt (figure cards, tables, pills, the forms in DialogFrame with the
  party picker and the date box); RecordsPanes' `RecordList` (Bills,
  Products, Expiry, Plans, Purchase rates) is a FluentPage with an empty
  state; Invoice is list + page on cards with the accent Print; Khata Report
  has date boxes, period pills, three figure cards and a table; Business
  Profile is three cards of paired fields. A mechanical pass put every other
  pane on `.card()`, `Rule()`, 14 sp semibold rows, Fluent pills for its
  choices and subtle/danger buttons for its text buttons. ScreenProofTest
  also draws Cashbook, Cheques and the Report from a small temporary book.
  Still Material-shaped: the AlertDialogs (confirmations, small forms) and
  the DialogWindows not yet on DialogFrame (bill, scheme, staff, product).
  Not on Windows: the posters' 3D heroes and video, voice entry
  (no free offline Urdu recognition), Google Drive sign-in (the backup
  folder can be a Google Drive for Desktop folder instead).

### The counter (POS, 21 Oct 2026 — Phases 1–9, branch kmp-desktop-14)

Built in one night to the owner's plan ("POS system ka plan do with all
check and balance"), for every trade, both platforms, all nine languages.
The rules every piece keeps: **money and stock are movements, never
stored totals** (every figure is summed from rows); **delete nothing,
reverse it** (a void bins what it posted, a return is a credit note); every
document is **numbered from the highest ever used** (S-, R-, PO-) and
**immutable once posted**; every write that a shopkeeper would answer for
leaves an **audit row** with the signed-in name.

- **Schema 32→35, backup format 8→11.** `Schema32`/`33`/`34`/`35` are the one
  SQL list each the phone's `MIGRATION_n` and the computer's
  (`DesktopDatabase.MIGRATIONS` — the laptop's 1.10.0 book must open, never
  be rebuilt) both run. `SchemaRewind` (app tests) takes a file back a
  version so `SalesMigrationTest`, `ReturnsDaoTest`, `InventoryDaoTest` and
  the old `EntryItemsMigrationTest` open real older files through the chain.
  **Any new column or table: add it to the newest Schema list, bump the
  version, and extend SchemaRewind.** `app/schemas/35.json` is written by
  CI's KSP run; the checked-in folder still holds 31 (no local Gradle).
- **Sale** (`Sale.kt`): a document over the ledger. A customer's ticket is a
  khata "I gave" with the goods as entry_items (stock and statements see it
  as any sale) and an "I got" per payment (cash/bank/cheque/online — a
  split is several); a walk-in's payments are cashbook income with their
  method, and its lines are counted in stock by `walkInSoldPerProduct`
  (`Stock.of` is the one call every stock figure makes). `postSale`
  recomputes totals, runs `SaleMath.check` again and writes it all in one
  transaction; `voidSale` bins exactly the rows the sale remembers.
  `CounterDraft` is the ticket state both counters draw; `Counter.post`.
- **Products** gained barcode (unique among live products, checked in the
  DAO), costPrice, lowStockLevel. `ProductCsv` both ways (import never
  deletes). `Barcodes.internalEan13` for labels; `LabelSheet` (Windows,
  zxing) prints 50×25 mm labels. Low stock is a bell row (Kind.STOCK).
- **Returns** (`SaleReturn.kt`): a customer's is a khata "I got" with goods
  lines (+ an "I gave" refund in its method); a walk-in's a cashbook refund
  (category Return) with its lines counted back. A line may not come back
  more than it was sold (`returnedOfSale`). `ReturnForm` is shared Compose.
- **Day report** (`ZReport`): sales by method, on account, discounts, tax,
  voids, returns, cash in/out, the drawer (DayClose) and profit. Close the
  day refuses an unexplained difference. Kind.DRAWER says when yesterday
  was never counted. `DayCloseMath.dayOf/startOf` is the shared day rule.
- **Access** (`Access.kt`): owner (null), manager, cashier — `Staff.role`,
  PIN as PBKDF2 (`PinHash`, now shared with App Lock). A cashier's void,
  return, discount past 5%, price change, export, import, stock adjustment
  and credit-limit breach wait for a manager's PIN (`OverrideDialog` /
  `AccessUi.allowed`), logged as MANAGER_OVERRIDE. Who did what: `AuditPane`
  (Windows) and `AuditActivity` over the shared `AuditScreen`.
- **Inventory** (`Inventory.kt`): `StockAdjustment` (signed, reason
  required; counts write only the differences — `recordCount`),
  `Valuation` (cost price, else last purchase rate, nothing guessed),
  `PurchaseOrder` → `receiveOrder` makes a supplier bill through
  `insertSupplierBill`. Windows: Stock on hand and Purchase orders pages;
  phone: Count stock, Stock value, Adjust stock and Purchase orders
  (`OrdersActivity` over the shared `OrdersScreen`; `OrderDraft` and
  `orderText` are shared) from Products' more button.
- **Sales report** (`SalesReport`, `SoldLine`): by day/item/category/
  customer/cashier/hour, tax, P&L (cost known for how much of revenue is
  said), dead stock (90 days). `reportRows` gives both platforms the same
  rows; Windows page + phone tile.
- **Printing on the phone:** `TextReceipt` draws text as a picture the
  roll's width for the paired Bluetooth ESC/POS printer (the entry receipt's
  path).

### The counter's second night (21 Oct 2026 — the owner's critical review, "implement all these one by one")

- **Schema 36/37, backup format 12/13.** Every money/stock row gained a
  `uid` (random, kept by every backup and restore — `Uid.next()`; the
  migration fills old rows with `randomblob`); `book_settings` (settings
  that travel with the book: owner PIN hash, prices-include-tax, loyalty,
  scale labels), `loyalty`, `shifts`; products' packs, tax rate, variant
  (`parentId`, `variant`); sale lines' own tax; the audit log's seal;
  stock_adjustments.billItemId (37). **A schema version once pushed — even
  in a test build — never changes again: a fix is the next version.**
- **Security** (`Access.kt`, `AccessUi` on the phone, `Gate.kt`/`Gates` on
  Windows): the owner's counter PIN lives in the book as a PBKDF2 hash with
  a recovery code (hash only); counter mode per device (a locked counter
  can do nothing until someone signs in); PIN waits 30 s doubling to 15 min
  after five misses, kept across restarts; screens and buttons gated by
  permission (VOID, RETURN, DISCOUNT_ANY, PRICE_EDIT, EXPORT, STOCK_ADJUST,
  SETTINGS, BACKUP, STAFF, CREDIT_OVER, EDIT_BOOK, REPORTS; owner-only:
  SETTINGS, BACKUP, STAFF). The audit log is a SHA-256 chain (`AuditChain`)
  — it shows an edited row; it cannot stop a whole file being rewritten.
- **Restore safety** (`RestoreCheck`): before a copy replaces the book, what
  would be lost is counted (by uid, else by content), said in figures and
  ticked; the book is kept first (phone `Snapshots.keepNow`, Windows a
  "before-restore" daily copy). **Merge** (`BookMerge`, "Add what is new"
  beside Replace): only rows this book lacks, by uid, with what they point
  at (customers by name+phone, products by name, lines, payments, a
  ticket's khata/cashbook rows); a used number takes the next free one and
  keeps the old in the note; rows both have stay as here (counted as
  changed); the copy's bin and held tickets are left out; plans, seasons,
  schemes, attendance and day closes are not merged; a copy with no shared
  row or no uids is refused. One transaction (`mergeBook`).
- **FEFO and packs** (`Fefo`, `Units`, `Cost`): a line is written once per
  batch, soonest expiry first, money split exactly; an expired lot is
  refused (`Counter.ExpiredStock`) until written off (Expiry page / batch
  dialog). Packs convert to the product's own unit (mixed units → no figure
  rather than a wrong one). Cost: cost price, else weighted average
  purchase, else last rate. Line tax inside or on top (`SaleMath.lineTaxes`).
- **Counter extras**: a ticket as an A4 invoice (`SaleInvoice`); shifts and
  the time clock (`ShiftMath`, shared `ShiftScreen`); receipt printer per
  device — 80 mm, logo dithered (`EscPos.dither`), cash drawer kick
  (`EscPos.DRAWER_KICK`); loyalty points (`Loyalty`, spent as a discount,
  checked in `postSale`, undone by void/return); weighing-scale labels
  (`ScaleCode`, prefix 21 by default, weight or price); the item grid with
  variants (`ItemGroups`); the customer's screen (Windows second window;
  the shop's QR carries no amount — said on screen); stock between the
  owner's shops (`StockTransfer`, Windows); today's copy rewritten a minute
  after the last sale (both). **`Counter.post` now records the signed-in
  name** (it passed null until 21 Oct: every ticket read as the owner's).
- **Reports**: the sales report's own dates and a chart (`SalesReport.bars`:
  hours / days ≤ 62 / months; time runs from the reading side in RTL);
  dues by age (`Aging`: 0–30/31–60/61–90/90+, payments clear the oldest
  first) — phone `AgingActivity` (Follow-up, report menu), Windows page.
- **Not built (as of 21 Oct):** live multi-device sync (built 22 Oct, below),
  country tax packs (FBR/ZATCA e-invoicing), a dynamic payment QR with the
  amount (needs the bank's merchant service), device-own number series
  (merge renumbers instead), customer display and stock transfer on the
  phone. Nothing above was tested on a real device, printer, drawer, scale
  or second monitor — CI only.

### Live sync (22 Oct 2026, branch kmp-desktop-15 — the owner: "implement karna hai")

The phone and the computer keep one book together, change by change. Nothing
of it was tried on two real devices yet — CI only.

- **Schema 38, backup format 14** (`Schema38`): a `uid` on the 15 tables that
  had none (parties, products, plans, instalments, bill/invoice/entry items,
  day_close, schemes, staff, attendance, sale lines and payments, return and
  order lines) and an index on every uid table (27). Frozen once pushed.
- **The door to SQLite** (`SyncSql`, expect/actual): the phone through
  `runInTransaction` + the open helper, the computer through
  `useWriterConnection { immediateTransaction }`. Raw SQL, types 'I'/'R'/'T'.
- **The engine** (`LiveSync`, `SyncSchema`): triggers (`rksync_ins/upd/del_<t>`)
  write `sync_log` in the same transaction; `sync_mute` stops echoes while a
  parcel is written. Rows travel by uid with their links as uids; photo paths
  never travel; natural keys (product nameKey, party qrToken, day_close day,
  attendance staff+day) meet by the lower uid and an alias. A row both sides
  changed is decided alike on both (higher device id wins, the other kept in
  `sync_conflict`, a delete wins). A gap asks again; `fingerprint` (SHA-256 of
  canonical rows) says whether the two books are the same. `SyncSchema` must
  list every uid table (LiveSyncTest holds it to Schema38).
- **WiFi road** (`LiveWire`, `LiveRound`): port 47313, QR `rklive:1?`.
  Pairing (RKA1, sealed with the code's secret): the phone sends its sync
  identity and whole book; the computer shows the usual restore
  confirmation (no merge for a live pairing), and on yes disables, restores,
  `LiveDesk.join` (enable(adopt) + the phone as peer at its seq). Rounds
  (RKL1, sealed with the book key, AES-256-GCM): request → answer → take.
  Phone: `LiveRunner` (a round every 4 s while the app is on screen, a
  fingerprint every 30 min), a card on Backup. Computer: `LiveDesk`
  (listens while the book syncs), More → Live sync window; a replace
  restore of a syncing book turns live sync off first.
- **Relay road** (`LiveCloud`, `cloud/worker`): a Cloudflare Worker with one
  SQLite Durable Object per book; it carries the same sealed messages and can
  open none. Pass = HMAC-SHA256(book key, "rk-relay-v1:" + book), set by the
  first hello. Parcels go once the others acked them, or after 7 days.
  Optional FCM wake-up (data only). **Closed until `LiveCloud.base` is set**;
  `cloud-worker.yml` type-checks and smoke tests it on every push and
  deploys only by hand with `CLOUDFLARE_API_TOKEN`/`CLOUDFLARE_ACCOUNT_ID`.
- **Kill switch** (`LiveControl`): `live.json` on the `live-control`
  release, `{"live":false}` halts everyone; a 404 means live. Read every 6 h.
- **Document numbers per device** (`NumberMark`): while a book syncs, each
  device's numbers carry its letter after the number — the book's maker A,
  a joiner the letter the phone gave it in the offer (else one from its id,
  never A): RK-000013-B, S-000013-A, INV-, R-, PO- alike. After, not before:
  every "highest" query casts the digits after the prefix and SQLite stops
  at the "-", so the series runs on across devices and older versions.
  Off: numbers exactly as before. Set when a book opens (the phone's
  onOpen callback, the computer's LiveDesk.attach) and on join/stop.
- Pairing copies are the same book: the backup carries each customer's card
  QR (`qrToken`); LivePairingTest compares a restored copy row by row.
- The phone finds a computer whose address changed (UDP call on 47313 with a
  tag from the book key); the computer keeps 47313 across restarts
  (exclusive bind off, the phone closes first); the window lists conflicts.
- **Wake-up (FCM, 22 Oct 2026):** the owner registered both packages in the
  Firebase project `roshan-khata` (Spark, free) and keeps google-services.json
  as the secret `GOOGLE_SERVICES_JSON`; `scripts/google_services.py` writes it
  in build.yml, instrumented.yml and aab.yml only when it names both packages,
  and `app/build.gradle.kts` applies the google-services plugin (4.5.0) only
  when the file is there — without it the app builds as before, no wake-up.
  Firebase BoM 35.0.2 (messaging only, no Analytics). Messaging's auto-init is
  off in the manifest: `PushWake.address` asks for a token only from a round
  that found the relay on, `LiveCloud.tellWake` leaves it at the relay once per
  token and book (sync_meta "cloud_wake", reset by every hello), and turning
  the relay off on the phone deletes the token (`PushWake.off`). `WakeService`
  takes "rk-wake" and runs up to five relay rounds within 15 s for the open
  book (`PushWake.roundNow`; nothing while `LiveRunner.running`). **Data
  safety:** Firebase Installations sends Google an installation ID, the app's
  version and device basics — declare "Device or other IDs" (app
  functionality), not shared; the privacy policy says so (section 17).
  Not tried on a real phone yet. Only the open business's book is woken.
- **Three relays (22 Oct 2026, the owner's plan: never rely on one):**
  Cloudflare (`cloud/worker`), Supabase (`supabase/functions/daakiya`, project
  `tcztkeyliletxshhkhnz`, Deno + Postgres in schema `rk_relay` with RLS on and
  no policy, JWT check off — the book's pass is the check) and the owner's
  hosting (`web/relay`, PHP + one SQLite per book in `rk-relay-data` beside
  public_html, uploaded by hand to `public_html/r`). All three speak the same
  paths and answers; `scripts/test_relay.py` holds each to the same 32 checks
  (relays.yml for PHP and Supabase, cloud-worker.yml for the Worker). The
  token goes as `Authorization: RK` and `X-RK-Pass` (hostings drop the first).
  `LiveCloud.roundAll`: the first relay that answers takes this device's
  changes and gives the others'; later relays are read every 8th round
  (`OTHERS_EVERY`) and also sent to while a parcel came from them within 24 h
  (`USED_MS`) — a phone that cannot reach Cloudflare still meets a computer
  that can. Cursors per relay in sync_meta (`cloud_<tag>_after:<book>`;
  Cloudflare's tag is "" so old books keep their place). A relay answering
  503 is passed over; the kill switch still stops all. Supabase pauses a free
  project after 7 quiet days: the Worker's daily cron (`KEEP_ALIVE`, 04:23
  UTC) calls its health, which runs a query and lets idle books go. Deploy
  Supabase with relays.yml (deploy = true, `SUPABASE_ACCESS_TOKEN`: Edge
  Functions read-write on that one project, expires 21 Oct 2027). Upstash was
  dropped (22 Oct): a phone could only use it with its master key or another
  server to mint per-book keys, so it could never be an independent road.
- Not yet: the audit chain stays per device; the wake-up for a business that
  is not the open one; the wake-up through Supabase or the hosting (only
  Cloudflare sends FCM).

### The review (22 Oct 2026, kmp-desktop-15 — the owner: "critical analysis karo, drawbacks fix karo")

Two days' work (the counter, live sync, the customer page) read by six
reviewers and fixed in six parts, each point read in the code first, each
part with its test class (LiveSyncReviewTest, CounterReviewTest,
CustomerLinkTest + scripts/test_khata_web.sh, PosReviewTest,
SecurityReviewTest, SnapshotsOnDeviceTest). CI only — nothing on two real
devices, a printer or a second monitor.

- **Live sync (part 1):** every replace-restore pauses sync
  (`LiveSync.pause`/`resume`) and ends it once through; a book file put back
  from an older copy is caught on opening (`LogMarks`, outside the file);
  each change carries its log line and pointers move in the parcel's own
  transaction (`applyFrom`); a change the book cannot take waits in its own
  savepoint (`sync_held`); removals win; photo-path-only updates are no
  change; REPLACE upserts keep the uid; a pairing waits for the computer's
  yes (pair id, given up after 15 min); WiFi frames capped at 16 MB, a thread
  per connection, a 5 s hello; discovery is a challenge (RKS2); the relay is
  off until the owner turns it on (`BookSettings.LIVE_RELAY`); the computer
  honours the kill switch.
- **Counter (parts 2, 4):** merging a customer moves his tickets, returns,
  points, orders and schemes; rows a void took back stay out of the bin;
  FEFO shares batches across a ticket's lines; ticket/return cashbook rows
  change only from their owner (`cashOwner`); a customer with posted tickets
  is not binned or purged, and the bin's purges keep a kept customer's
  entries; a count of stock the book cannot state is not written; profit
  takes returns off as their sale was counted (`ReturnedLine`); inside tax
  comes off after the discount; a cost is never a rate in another unit;
  Indonesian figures read as written ("1.250,50", "50.000"); walk-in tickets
  are in the register; a scale label off the shelf price is a price change;
  audit notes are written inside `postSale`; a ticket is a snapshot
  (`Counter.Ticket`, `clearAfter`).
- **Customer page (part 3, `web/khata`, `CustomerLink`) — built, NOT deployed,
  NOT on any screen yet.** api.php keeps who owns which page beside the web
  root (`RK_KHATA_DATA`, else `../rk-khata-data`), one lock, whole writes,
  global and per-shop caps, one writer per shop (another copy claims),
  tombstones, 180-day expiry; `view.js` speaks the nine languages under a
  strict CSP; the page text is the same on both devices; what a device sent
  is its own note (`Place`, outside the book and backups); while the book
  syncs, the shop pass and links come from the book key. Deploy: README in
  web/khata, then check `/khata/data/` and the store answer 403.
- **Security (parts 5, 6):** a restore keeps this book's owner PIN, recovery
  code and counter keys where the copy has none (`restoreAll(keepAccess)`,
  false only for a live pairing); a device keeps the owner's PIN keys each
  time the PIN is typed right on it (`Access.rememberOwner`) and a book on a
  counter without an owner PIN takes them (`Access.ownerPin` →
  `adoptOwnerPin`) — a counter can no longer lock out its owner; an owner's
  or manager's counter session ends after 5 idle minutes (`touch`,
  `expireIfIdle`); a shop switch signs out (the phone in `closeActive`);
  counter mode on locks at once; PIN waits never pass 15 minutes; CSV
  import, bill lines and bill deletes are gated and audited; the computer's
  device id is Windows' MachineGuid as a hash (`MachineId`,
  `Access.machineId`), never app.properties; the helper's copy carries no
  PIN hashes (`exportTo(secrets = false)`); the phone's bin keeps a bill
  photo another shop's book points at; the computer's copies and backups
  are written on its own disk, then copied out; the outside folder is per
  computer ("Business-1 (SHOP-PC)"); the phone's safety copies are taken
  under a write transaction with the log, then checked (`copyWhole`).
- **After the review (the owner's yes, 22 Oct):** the owner's PIN is 6 to 8
  digits (`PinHash.validOwner`; staff PINs stay 4 to 8) — its hash is in
  every backup, and four digits fall offline in minutes; an older short one
  still opens and is asked to be changed each time it is typed
  (`Access.ownerPinShort`). The relay Worker with the review's fixes is
  deployed (cloud-worker.yml, deploy = true).
  Then: a pairing shows four digits on both screens (`LiveWire.pairCode`,
  from the code's secret and the pair id); the cashbook and the counter's
  tables go out and come back a page at a time (`*PageForBackup`,
  `BackupCodec.Streamed`); a day closed again writes its audit row inside
  `saveDayClose`, with what the first close counted.
- **Left, with the reason:** goods lines edited on both devices at once can
  double (E8: needs line-set versions); a device with the book's key can
  name itself as any device of the book (insider only; the relay's own
  filing of "from" is held to the sealed message since 22 Oct); the kill switch fails open by
  design; a lone Indonesian "1.125" stays one and an eighth.

### The live-sync review (22 Oct 2026, kmp-desktop-15 — the owner: "in tamam recommendations ko fix karo")

Three reviewers read the whole live-sync system (engine, WiFi, three relays,
wake-up); every point was read in the code first. CI only — nothing on two
real devices.

- **Keys.** The book's key at rest is sealed for the device (`KeyVault`,
  expect/actual: Android Keystore AES-GCM; Windows DPAPI through JNA 5.17,
  `KeyAtRest` "w1:" in sync_meta; plain where there is no lock — Linux
  tests). A key sealed elsewhere turns live sync off on opening
  (`OFF_RESTORED`); a plain key from an older build is sealed on the next
  open. Each road seals with its own HKDF-SHA256 subkey and names itself in
  the AAD (`LiveWire.Road`: wifi-request, wifi-reply, relay; "RKL2"); a
  request played back as a reply, or a relay parcel on the WiFi, does not
  open; a device's own words are refused (`echo`). **A build before this
  cannot open this build's rounds — update both devices.** Each relay has its
  own pass (`LiveCloud.token(relay)`: v1 for Cloudflare, v2 with the host's
  name for the rest). The owner's PIN hash and the access keys
  (`BookSettings.ACCESS_KEYS`) no longer travel in sync.
- **Honesty on screen.** The phone's Status has `RelayDown(why)` and backs
  off doubling to 6 × ROUND_MS × 64; the computer's window shows the relay's
  last answer or why it is down (`LiveDesk.relayState`). A bell row
  (`Kind.SYNC`) counts conflicts in money tables (`LiveSync.MONEY_TABLES`,
  `moneyConflicts`) on both platforms; pairing a second phone warns that the
  first is replaced (`live_pair_other_phone`). A relay stop says goodbye
  (`leave`); relay-health.yml opens/closes a `relay-down` issue hourly.
- **Engine.** A Gap's `need` lowers the sender's pointer (`lowerSent`) on
  both roads, so a book put back from an older copy catches up instead of
  resending for ever; resends back off 2 min doubling to 1 h.
- **Capacity.** One `round` call does hello + send + take + ack (all three
  relays; a 404 falls back to the old three); a quiet round writes nothing
  at the relay (Cloudflare counts rows written: 100k/day free). Caps:
  NEW_BOOKS_DAY 300, MAX_BOOKS (CF 20,000 via a "!ledger" DO, Supabase 5,000,
  PHP 2,000); Supabase's health sweep needs `X-RK-Keep` (secret
  `RK_KEEP_SECRET`, both deploys). Supabase stays deployed but is out of the
  default road list (`others`: the hosting only) — three roads with FCM only
  from Cloudflare was more surface than reach; add it back in one line when
  the hosting goes.
- **Not done:** sharding books across relays (Cloudflare stays first for
  FCM); the audit chain across devices; E8 line-set versions.
- **The second pass (23 Oct, both variants against WiFi + the three relays +
  Firebase, two reviewers):** the device's lock **not answering**
  (`KeyLockUnavailable`: Keystore after an OS update, JNA quarantined, DPAPI
  reset) decides nothing — `LiveSync.lockTrouble`, identity waits, the phone
  says `KeyBusy`, the window `live_status_key_busy`; only a key that is **not
  this device's** (AEADBadTag / DPAPI refusal) turns sync off; Windows never
  falls back to a plain key. A pairing after an off is a new book id (the
  relay's old box is never read into it). WiFi `Lost` → Rejoin like the relay
  road; a schema refusal is `UpdateNeeded` / "update both", not "Live"; a
  relay's 503 is never "halted for everyone"; one relay round at a time per
  book (`Mutex`); `tellWake` re-hellos once and the phone backs it off to an
  hour; `leaveAll` before every `disable` (restore, LOST, erase, a deleted
  business) and `PushWake.off` on Stop/erase; the phone drops a round's answer
  once the book was closed under it (`KhataDatabase.isOpen`); the computer
  detaches live sync before `db.close()`, clears NumberMark on LOST, answers
  phones from the cached kill switch, does not lose a pairing's question to a
  click outside; `Kind.SYNC` bell row on both while `OFF_RESTORED`
  (`na_sync_off`). Still open, with the reason: a pairing frame is gathered
  in RAM before the secret is checked (256 MB cap; stream to a file next); no
  conflicts screen on the phone (the computer's window has it); `join()` is
  three transactions; swallowed sync failures are not written to errors.log.

### The live line, Firebase's parcels, several devices (23 Oct 2026, kmp-desktop-15 — the owner: "WebSocket bhi banate hain… teen mobile ek ID")

Why: the build polled the relay (a request every 15 s whether or not anything
changed), so Cloudflare's free 100,000 requests a day carried far fewer shops
than the plan said; and the engine knew only two devices. CI only — nothing on
real devices yet.

- **The live line** (`LiveLines`, Worker `live`): one hibernating WebSocket per
  open app, device and book, to the first relay only. A round is one message
  (`{"id","round"}` → `{"id","status",...}`, the Worker's `roundOf`, the same
  as the HTTP round); Cloudflare bills incoming messages 20 to a request and
  nothing for outgoing ones, pings or a sleeping line. A parcel nudges the
  other open lines (`{"t":"new"}`); runners wait on `LiveLines.waitForNews`
  (a nudge, or this device's own log growing) with a 5-minute round only to be
  sure. No line (404/426: `NoLineHere`, an hour; a drop: 30 s doubling) and the
  round goes over HTTP unchanged. Sockets: `OkLines` (OkHttp 4.12.0, phone,
  opened while on screen) and `JdkLines` (java.net.http, the MSI gains that
  module). test_relay.py speaks a stdlib WebSocket client to the Worker;
  JdkLinesTest runs the JDK's socket against a loopback server.
- **Firebase carries the parcel** (Worker `wakeOthers`): a parcel of at most
  3,400 base64 characters rides in the FCM data message ("rk-parcel": number,
  sender, the sealed body — Google carries ciphertext), ≤60 a minute to a
  device, only to devices without an open line; larger ones still "rk-wake".
  `LiveCloud.takeCarried` writes it in without a relay call and without moving
  the relay cursor (the relay stays the record); a gap asks for a round;
  `onDeletedMessages` runs a round. The privacy policy says so (section 17).
- **Several devices** (`LiveCrew`): an offer carries the sender's other devices
  and how far it had read each (`offerPeers`); the joiner starts there
  (`LiveCrew.join`). The sender holds back its acks toward each of them at the
  copy's line (`holdForCopy`/`ackFor`) until that device names the joiner in
  its own acks/knows (`heard`), so nobody prunes what the joiner needs; joins
  are announced in relay messages and WiFi requests/answers (`joins`,
  `knows`) and taken with `takeJoins`. A phone joiner says its pairing
  (`pair`) through the relay and the sender adds it there
  (`confirmFromRelay`). `LiveRound.confirm` no longer drops the book's other
  devices, only the computer it replaces. The phone runs relay rounds with no
  computer at all, and beside the WiFi round when the book has more than one
  peer (`sideDue`: on news or once a minute); the computer keeps its relay
  rounds while the phone is on its WiFi if the book has other devices.
- **A phone takes a book** (`LiveReceive`, Backup → "Take a shop's book on
  this phone"): it shows a live code (`t=phone`) and serves one pairing frame
  on the WiFi; the confirmation shows the code and what this phone would lose;
  `Snapshots.keepNow` first, then `Backup.restore`, then `LiveCrew.join`. The
  sender turns the relay on for the book before the copy (phones meet only
  through it). The computer still cannot be a sender (no camera to scan).
- Tests: LiveCloudTest (rounds over a line, nudges, carried parcels, three
  devices through a phone joiner), JdkLinesTest, test_relay.py's live line.
- **Not done:** removing a stale device from the phone's screen (the
  computer's window has it); the hosting relay has no live line (rounds only).

### The 23 Oct review report (kmp-desktop-15)

- **Relays (R06-R08, R36-R39, R84; all three relays, test_relay.py `--caps`/`--idle`):**
  a Worker object makes its tables only on a hello (`haveTables`), and an emptied
  book is forgotten by the `!ledger` too (`forget`: ledger DELETE, deleteAlarm, deleteAll);
  the ledger keeps its total in a row and indexes `at`. Per book: BOOK_BYTES 16 MiB
  stored (507 `book-full`), DAY_BYTES 32 MiB a day (429 `day-bytes`); the hosting
  also has MAX_STORE_BYTES 1 GiB over its files; Worker NEW_BOOKS_DAY 100. A round
  whose parcel meets a limit keeps its ack and still answers the page under the
  refusal's status; a whole page carries `through` (the cursor passes own parcels).
  A device unseen for 30 days goes with its parcels (`DEVICE_IDLE_MS`). The hosting
  counts new books by marks in `new/` and sweeps idle books from health (6 h).
  Client (`LiveCloud`): a hello refused 507/429/409 waits an hour (`refused_until`);
  resends ignore peers silent past `STALE_MS` and are one page (the next at once
  while the peer acks); a daily heartbeat; capacity refusals take the page and fall
  over to the next relay.
- **Live-sync engine:** letters — every device's letter is kept (`mark:<device>`,
  `markgiven:<L>`, `LiveSync.knownMarks`), `nextJoinerMark` takes the lowest free one,
  the offer carries `marks` and each peer's `mark`, relay messages carry `mark` and
  `absorb` settles it (R05). After a parcel, a voided ticket with a live return and a
  line returned past its quantity are kept as `sync_conflict` rows (kept "both",
  `counterChecks`) for the bell's money row (R31). Pairing is "RKA2" and the pull answer
  "RKC2": `SealedChunks` (64 KB AES-GCM records, a small head opened first, the book
  streamed to a file) — a stranger can no longer make a device hold a big frame, and
  "RKA1" is refused unread: **update phone and computer together**. The phone's pull
  writes to the cache (`Pulled.Got(file)`, `Pulled.TooBig`, `wifi_take_too_big`) and
  catches OutOfMemoryError (R46). `LiveCrew.join` is one transaction
  (`LiveSync.enableWith`). The computer writes live-sync failures it passed over to
  logs/errors.log (`LiveTrouble`, ten minutes per kind, 20 an hour); the phone has no
  such log (its CrashLog records crashes only), so nothing is written there.
- **Live-sync screens and the runner (R45, R48, R71, R83, the handover's phone Devices screen):**
  Backup → "Devices and conflicts" (`LiveDevicesActivity`, gated BACKUP, owner-only on a helper's phone) lists
  the book's devices with when each was last heard, removes one after a confirmation, and lists/clears
  conflicts (`LiveSync.conflictViews`). Both platforms remove through `LiveForget.device`: LiveCrew's holds,
  waits and announcement for it (`LiveCrew.forget` — else the others' acks stayed held for ever), its `via_`
  road mark, the phone's WiFi target when it was that computer, then `removePeer`; nothing is said to the
  relays (a "leave" for it would delete its unread parcels). `LiveRunner.bookChanged` (from
  `Businesses.switchTo`) stops, resets every status and back-off, and starts again; the news wait ends once
  its book is closed and NumberMark is written only while the round's book is open (`markFrom`/`clearMark`).
  The later relays are read by time (`LiveCloud.othersDue`, `OTHERS_MS` 2 min) and a peer whose parcels came
  by the hosting keeps the HTTP clock (`peerOnLinelessRoad`) on the phone and the computer. Known reason codes
  read in the owner's language (`live_why_*`); an exception is `WHY_ERROR`. Tests: LiveDevicesTest.
- **Several shops and Drive (phone):** a Drive restore, one shop or all, is
  read and weighed first (`planDrive`/`planFor`: download, parse, RestoreCheck
  against that shop's own book via `openOther`, live sync) and says per shop
  whether it replaces, comes in new or could not be read, with figures and the
  tick; the open shop alone goes through the single restore's question.
  Business 1 under another name comes in as a new shop unless replacing it
  loses nothing. The loop runs on AppScope; photos by the Drive shop's number.
  DriveBackup's names, keys and records are by business id; an automatic
  backup stops if the shop changes under it. A switch writes the left shop's
  daily copy first (`Snapshots.beforeLeaving`); afterSale is bound to its
  shop. Own saved backups are per shop (`backups_b<id>`); bell backup notes
  per shop; delete/erase say goodbye to the relays for closed books and take
  bill photos, signatures and reminder notes; a new shop takes the open
  shop's country, sign and calendar (`copyRegion`); the shop sheet asks
  SETTINGS; PIN counts are by staff uid. Not done: a Drive backup of the shop
  being left (the export reads the open shop's profile).
- **Windows app:** every dialog window is `AppDialog` (Shell.kt; same arguments as DialogWindow), which
  carries the app's direction and text size into the window (`KeepAppLook`, also on the customer's
  screen) — a window of its own took Swing's. Business ids are never reused (`highest` in
  businesses.properties, `leftoverMax`), and a delete removes the shop's daily copies, pictures,
  `party_photos_b<id>`, card/poster settings and set-aside books, refuses while Windows holds the book,
  and runs off the window's thread. Customer photos are per business (`Photos.partyDir`). A switch
  writes the closing copies first and is saved only once the new book opened; a book that cannot open
  offers the previous business and logs why (errors.log). A live offer of a book another business keeps
  (`Businesses.book/holderOf`) opens that business instead of joining again. Invoice field switches are
  per business (`_b<id>` keys in app.properties). Phone boxes are LTR (`FluentField(ltr = true)`), printed
  sheets and receipts fall back per character (`PrintFonts.attributed`). CI only.
- **Counter core and Android crash-safety:** the phone posts and holds a
  ticket on `AppScope` and clears the till the moment it is written (a
  rebuilt screen no longer posts it twice); `postSale` refuses a held ticket
  already rung up or dropped (`CounterRefused.HELD_GONE`); refusals carry
  their reason (`CounterRefused`, `SaleMath.Refused`, `ReturnMath.Refused`)
  and both counters word them (`counterFailWord`, `voidRefusalWord`), never
  the English message or backup_failed; voids, returns, counts, adjustments,
  write-offs and transfers check `Access.requireAllowed(p, allowedBy)` in
  the DAO (screens pass the PIN prompt's `by`; Windows `Gates.needBy`); a
  sale of a closed shift is not voided (`VoidRefusal.SHIFT_CLOSED`) and a
  shift's drawer counts its own device's tickets (`Shift.device`,
  `ShiftMath.markOf`); a day close is filed by working day
  (`DayCloseMath.businessDayOf`, before 04:00 = the day before); Insights'
  profit costs walk-in tickets and does not count walk-in refunds as
  expenses; `postReturn` takes rate, unit, product, customer and lines from
  the ticket (`ReturnMath.paidRate`); the bin's purges keep a void's khata
  rows and numbering goes past `BookSettings.ENTRY_NUMBER_HIGH`; a stock
  transfer's new product gets its own uid; a bill line's productId wins over
  its name. `AppScope` has a handler (`AppFailures`: CrashLog + "not saved",
  storage full said as such); AccessUi checks `isDestroyed` too; the
  add-entry form is dismissed with its screen and a bill photo that comes
  back to a rebuilt screen is kept for the next form (the form itself is NOT
  restored yet). Tests: CounterReviewTest (9 new), ProfitTest, DayCloseTest.
- **Languages and units:** units are saved as keys on Windows too
  (`UnitWords.canonical` in the entry, bill and invoice forms; labels shown
  through `UnitWords.label` everywhere a unit is printed) and a book's
  label-units are repaired on every open on both platforms
  (`UnitWords.normalizeStatements`, run by the phone's normalizeStored and
  DesktopDatabase's onOpen). Quantities group like money (`Format.qty/goods`
  through `groupedForReader`); CSV figures read the decimal mark from the
  figure (`CsvImport.amount(raw, groupDots)`); WhatsApp numbers on the phone
  take the shop's country (Reminder no longer passes "92"); notifications use
  the app's language on Android 7-12 (`ui/AppLanguage`). `check_strings.py`
  now fails real line breaks, edge spaces, bare `"`, `%%` in unformatted
  text, native digits, wrong-script letters and a misspelt app name in the
  RTL files (privacy strings exempt), and warns on Roman Urdu glossary
  variants (dukaan, maujood, ek, gahak, wapas…).
- **Line sets (E8, LiveSync rule 7):** an entry's goods, an invoice's items and a draft
  order's lines are one set (`LiveSync.LINE_SETS`): every editor rewrites the whole set with
  new uids, so two devices editing the same parent before a round both kept both sets (the
  goods doubled, the fingerprints agreed). Now, when a parcel changes a parent's lines that
  this device also changed (`lineSetRules`/`decideSet`), the device the parent's own rule
  picks (bin, else higher device id — `setKeeper`) keeps its whole set: the receiver either
  takes theirs (its own lines out, kept in sync_conflict, noted "D") or keeps its own
  (`keepOwnLine`: their lines not written, kept aside, noted "D"/"U" so they follow). No schema
  change. bill_items stay row-by-row (saveBillLine edits in place). Tests: LineSetSyncTest
  (both orders and crossing parcels; entries, invoices, orders; uids kept in place).
- **The computer as a sender (LiveGive, phase 2):** More → Live sync → "Give this book to a phone" shows a
  give code (`rklive:1?…&t=give`, a one-time secret, the computer's addresses and port; `phone`+`give` together
  is refused). The phone (Backup → Take a shop's book → "Scan the computer's code", `LiveReceive.pull`) asks
  with "RKT2": one ≤1 KB `SealedChunks` record under the code's secret, opened before anything is made (a
  stranger gets BAD, nothing buffered); the computer (`LiveDesk.give`, the code spent at once) runs
  `LiveGive.prepare` — the phone sender's steps: enable, `nextJoinerMark`, LIVE_RELAY on before the copy,
  `offerPeers` + `holdForCopy`, the book to `to-phone/`, `setPending(phone = true)` — and streams the offer
  ("RKT2|offer"); the phone answers OK/BAD once it parsed, BAD undoes it (`LiveGive.settle/undo`). Same
  confirmation and take path (`live_receive_offer_pc`); the phone keeps the computer as its WiFi target. The
  computer adds the joiner on its first WiFi round (`LiveRound.answer` → `confirmFromRelay`) or its relay
  "pair"; `confirmFromRelay` now also sets `pair:<joiner>` (WiFi rounds name it); `LiveCloud.round` runs with
  no peers while a phone pairing waits (a sender that synced with nobody never heard its phone's yes). A give
  not answered in 15 min is given up (`LiveGive.expire`, the computer's relay loop). Tests: LiveGiveTest. CI only.
- **Phone, phase 2 (R21, R41, sync notes; CI only):** a switch asks for the left shop's Drive
  backup (`AutoBackup.afterLeaving` from `Businesses.switchTo`; unique job `backup_after_change_b<id>`,
  the id as input, delayed to when its interval is due): `DriveBackup.autoBackupLeftShop` opens that
  shop's own file (`openOther`), exports with its own profile (`Backup.exportToCacheFor`,
  `BusinessProfile.settingsOf`, read-only) and keeps the open shop's rules (auto on, signed in,
  `DriveFeature.ENABLED` — true since the Cloud setup — entries, interval, changed, two-phone guard);
  a shop open again is skipped (its own run covers it), and one `Mutex` runs every automatic backup
  one at a time. Photos are not sent for a closed shop. The add-entry form is kept in
  onSaveInstanceState (`entry_form` Bundle, lines via `ui/EntryFormLines`) and reopened after the
  customer is read (`reopenSavedForm`; an edit through openEditor's checks again); not kept: the
  signature drawn and the two product-offer ticks; a rebuilt screen no longer reopens the intent's
  voice or edit form. Live-sync failures passed over are written to `files/synclog/sync-notes.txt`
  (`SyncLog`, LiveTrouble's sink, 32 KB cap, messages cleaned of quoted text, long numbers and codes)
  and Report a Problem adds the newest 15 under `report_sync_notes_included`, not as a crash.
- **Windows, phase 2 (From phone, relay reasons, labels):** the phone's send to the "From phone" window is
  "RKB2": `SealedChunks` under `WifiSync.chunkKey` (label "RKB2"), an 8-byte head (the book's length) opened
  first within 5 s, the computer's `GO` (or BAD / TOO_BIG: over MAX_BYTES or no disk room), then the book onto
  `from-phone/rk-phone-*.part` and moved in by `saveIncomingFile`; `WifiSync.receive` returns that file.
  "RKS1" is refused unread (update phone and computer together); a pull's question is read only up to 256
  bytes. Reason codes on both platforms go through `LiveWhy` (shared; the phone's `liveWhy` and the Live sync
  window), "day-bytes" and "book-full" included; the kill switch shows `live_status_halted` there. Barcode
  labels draw name and price through `PrintFonts.width/draw` and cut whole code points (`LabelSheet.fitted`).
  Tests: WifiSyncTest, LiveWhyTest, LabelSheetTest. CI only.

### The shared module (KMP)

```
shared/src/
  jvmSharedMain/   Kotlin that may use the JDK; Android and the computer share it
    data/          KhataDatabase (Room, @ConstructedBy), KhataDao, entities,
                   BackupCodec (the backup format), ShopSettings, Format...
    screens/       Shared Compose screens: About, PartyLedger, EntryForm, RoshanTheme
    words/         Words: the app's strings for shared screens (W / WP / WA names)
  androidMain/     The phone's side of each door (android.icu, JsonReader, BidiFormatter)
  desktopMain/     The computer's side (java.time Hijri, Gson, BidiPort, FileShopSettings,
                   DesktopDatabase, XmlWords from the copied strings.xml)
  desktopTest/     Run with `gradle :shared:desktopTest`
```

Rules learned moving code there:
- Android-only calls stay in `app` as **extensions with the same call shape**
  (`ShopCalendar.refresh(context)`, `Digits.quantity(res, ...)`,
  `UnitWords.choices(context)`): callers outside the `data` package import them.
- Across modules there are no smart casts on public `var`s and `internal` is
  not visible: copy to a local `val`, make test-used members public.
- A platform difference is a **door**: an `expect` in jvmSharedMain, one
  `actual` per platform, and a test holding the computer's side to the phone's
  (BidiPortTest, XmlWordsVsAndroidTest, HijriDesktopTest).
- Shared screens name words as `W.x`; the phone reads them through its own
  resources (generated `WordIds`, which also keeps them through shrinking),
  the computer from the same strings.xml (XmlWords). Compose resources are
  not used: Roman Urdu's script folder needs Compose 1.12, which needs
  compileSdk 37 and AGP 9.
- Compose Multiplatform is **1.11.1**; 1.12 waits for the AGP 9 move.
- `.gitattributes` keeps binaries binary: the Windows runner otherwise
  rewrote a PDF fixture's line endings.

### Key files

```
app/src/main/java/com/innovation313/roshankhata/
  MainActivity.kt           Home — 13 coloured tiles, walkthrough
  KhataActivity.kt          Ledger list — search, voice entry, filters
  PartyDetailActivity.kt    One customer — two-step full-screen entry dialog
  EntryDetailActivity.kt    Receipt + edit
  BusinessCardActivity.kt   Card picker
  BusinessSettingsActivity  QR, signature, photo-on-statement switch
  RecycleBinActivity.kt     Bin + bulk delete
  BaseActivity.kt           Every screen extends this (not AppCompatActivity): text size
  data/TextSize.kt          Owner's text size (Home ⋮) x phone scale, 85%-150% bounds
  ui/TextFit.kt             Fixed-height buttons may grow ONLY when text > 100%;
                            call TextFit.relax(view) after inflating any dialog/row in code
  data/
    VoiceEntry.kt           Spoken sentence -> party, amount, direction
    BusinessProfile.kt      QR, signature, photo-on-statement setting
    PdfExport.kt            Statement PDF — signature, optional photo
    DriveFeature.kt         ENABLED = false — the one line to flip
    KhataDatabaseAndroid.kt get(context): migrations, the phone's file (DB itself in :shared)
  ui/
    CardTemplates.kt        12 business card designs
    NameSearch.kt           One search rule for every screen
    Calc.kt                 evalPad, resolvePercent, trim
    DateRangeFilter.kt      Shared date filter
    DateTimeField.kt        Shared date + time picker

app/src/test/java/.../data/VoiceEntryTest.kt   What the microphone may conclude
```

VoiceEntry, DriveFeature, Calc and NameSearch now live in `:shared`
(`shared/src/jvmSharedMain/...`, same packages); their tests are in
`shared/src/jvmSharedTest`.

---

## Verifying a change

CI runs `testDebugUnitTest` before it packages anything. A build that
compiles is not a build that works.

```bash
# Unit tests
gradle testDebugUnitTest

# Every locale has every key, and no duplicates
python3 -c "
import re
base = set(re.findall(r'name=\"([^\"]+)\"', open('app/src/main/res/values/strings.xml').read()))
for loc in ['ar','sd','fa','ur','b+ur+Latn']:
    o = set(re.findall(r'name=\"([^\"]+)\"', open(f'app/src/main/res/values-{loc}/strings.xml').read()))
    print(loc, len(o), 'complete' if o==base else 'DIFF')"

# Every XML parses, every constrained id exists
python3 -c "
import re, glob, xml.etree.ElementTree as ET
for f in glob.glob('app/src/main/res/**/*.xml', recursive=True):
    ET.parse(f)
    src = open(f).read()
    d = set(re.findall(r'android:id=\"@\+id/(\w+)\"', src))
    u = set(re.findall(r'app:layout_constraint\w+=\"@id/(\w+)\"', src))
    if u - d: print('DANGLING', f, u - d)"
```

When a build does fail, read the **check-run annotations through the GitHub
API** rather than guessing. Log blobs are often unavailable; annotations come
back reliably. The workflow emits one per error line for exactly this reason.

A build takes roughly four minutes.

---

## Conventions, learned the hard way

Each of these cost a build or a bug.

1. **Verify before asserting.** A claim that ML Kit read handwritten Urdu at
   "20-30%" was invented; it does not support Arabic script at all. The owner
   made decisions on that number. Search first, then answer.
2. **On-device Urdu OCR is not possible today.** ML Kit covers Latin, Chinese,
   Devanagari, Japanese and Korean only. Do not re-attempt.
3. `inflate(layout, null)` discards every `layout_*` attribute — set height
   and margin in code.
4. `selectedItemId` fires its listener — set it before attaching one.
5. `MaterialButton` ignores `android:background` — use `backgroundTint`.
6. The table is `transactions`, not `entries`.
7. **Check for an existing string key before adding one.** Duplicates break
   the resource merger, and prefixes collide: `tpl_classic` matched
   `biz_tpl_classic` and silently skipped a whole write.
8. `Calc.eval()` rejects `%` and parentheses — handle it in the caller.
9. **Validate every anchor before writing any of them.** A script that applied
   two of three edits and exited left a layout the next script corrupted.
10. A disabled Material button fades its own colours — a faded label on a
    near-black fill is invisible.
11. **Removing a view breaks whatever was constrained to it.** Check
    `layout_constraint*="@id/..."` after any deletion.
12. Quotes in a commit message break the shell. Use `git commit -F file`.
13. **Colour is not decoration.** Text on the dark section headers needs its
    own `*_on_dark` variant; the ledger's plain red and green measure barely
    above 1:1 there. Anything carrying a figure must clear 4.5:1.

14. **3D posters (Filament): the CI emulator must use `-gpu swangle_indirect`.**
    The old `swiftshader_indirect` translator renders every material white,
    aborts on FXAA, and shadow maps kill the emulator (the run then hangs).
15. **A Filament read-back finishes only on `Renderer` frames.** `readPixels`
    waits on a fence that only `driver.tick()` (beginFrame/endFrame) checks;
    `Engine.flush()` then delivers the callback. `HeroStage.still` pumps both.
    Its rows come back top first — do not flip them.
16. **Look at the pictures, not only the numbers.** Upside-down 3D heroes and
    a reversed phone number passed every assertion; the "Poster proof"
    workflow puts the CI pictures on the `poster-proof` pre-release.
17. **R8 and the shrinker break at run time, not at build time.** Anything
    found by NAME must be kept: `getIdentifier` resources go in
    `res/raw/keep.xml`, classes compared by name in `proguard-rules.pro`.
    Enum names stored in preferences are safe (R8 keeps `name()`), but check
    any new reflection before shipping.
18. **A scrolling body never goes inside a scrolling frame.** Windows'
    `DialogFrame` scrolls its body; a shared form with its own
    `verticalScroll` (ReturnForm) throws there, and a weighted `LazyColumn`
    (StockCountForm) gets no height. Pass `DialogFrame(scroll = false)`;
    ScreenProofTest draws the shared forms in the frame.
19. **A property's setter and a `setX` function clash on the JVM.** `var
    counterMode` with a private setter generates `setCounterMode(Z)V`; a
    `fun setCounterMode` beside it failed both builds. Name the function
    otherwise (`switchCounterMode`).
20. **A pushed schema version is frozen.** Anyone may have installed the test
    build; changing what version N creates crashes their identity hash. Add
    version N+1 instead (37 for billItemId).
21. **Defaults that name "who" must be the signed-in user.** `Counter.post`
    had `createdBy = null`, so every ticket read as the owner's; reports and
    shifts silently lied. Default to `Access.userName`, and test it.
22. **No safety copy, no replace.** A restore or merge runs only after the
    book is on disk as a copy (`DailyCopies.makeToday` returns the file or
    null; the phone's `Snapshots.keepNow` returns Boolean). A copy is named so
    it can never overwrite the copy being restored.
23. **Only damage is damage.** `BookHealth.classify`: SQLite codes 11/26 (or
    "malformed"/"not a database") replace a book; a locked, busy or missing
    library leaves it untouched (`CannotOpen`).
24. **Wrong-PIN counts are per account** (`Access.account`, `tryPin(account=)`).
    One shared count let a cashier's own right PIN clear the misses made
    guessing the owner's.
25. **Money boxes are calc boxes.** Shared screens use `CalcField`
    (screens/CalcBox.kt); Windows passes `calc = CalcKind.X` to FluentField /
    Outlined. A percent box is read with `Calc.evalPercent`, a discount with
    `Calc.evalDiscount`; a lone "10%" is never settled into 0.1.
26. **Counter mode is the book's word too** (`counter_used`,
    `owner_devices`, `Access.applyBook` on the computer): app.properties is a
    file anyone at the till can edit.
27. **Nine languages means nine.** A picker of languages is `CardLanguage`
    (shared: code, own-script name, RTL, Nastaliq only for Urdu), never an
    English/Urdu toggle — cards, posters and both platforms use it. Printed
    sheets on Windows use `PrintFonts.family` and mirror for RTL; new
    number/scale words go in for every language (VoiceEntry, NumberWords,
    DateWords' weekdays). A word not verified from a source is not shipped:
    Sindhi's 0-99 came from the Sindhi Language Authority's grammar.
28. **Digits: screens 0-9, printed sheets by choice.** Storage, typing, CSV,
    codes and screens stay 0-9 (`Digits`). Arabic/Sindhi/Farsi/Bengali sheets
    may print their own digits (`Digits.nativeOnPrint`, off by default),
    applied only at the one text call each sheet uses (`Digits.forPrint` in
    PdfRtl.drawText, the print helpers, receipts) so a sheet never mixes
    two digit sets. Indonesian money is `Rp 1.250,50` (`Format.money`).
29. **The shop has a country, never inferred from its language**
    (`ShopCountry`, kept with the profile and in backups; Pakistan for old
    books; asked once with the shop's name). Anything that differs by
    region reads it: WhatsApp calling code, currency and decimals, tax ID
    name, week start and weekly off, calendar, 24-hour clock, wallet
    hints, Rabi/Kharif, 14 August. Never key a region rule on `Currency ==
    "Rs"` again.
30. **Names sort in the reader's alphabet** (`NameOrder`), never by
    `lowercase()`: the JDK's Collator has no Urdu/Sindhi/Persian
    tailoring on Windows, so the order is the app's own. Counts are
    `<plurals>`; direction icons on Windows go through `Glyph`/`MonoTile`
    (flipped in RTL); names and figures in a sentence are
    `Format.isolate`/`Format.ltr`.
31. **A row with a random uid never equals its copy.** Tests that compare
    entities read back from a backup or another book compare
    `copy(uid = null)` (BackupCodecDesktopTest failed on schema 38).
32. **The on-device migration test runs the newest Schema's lines too.**
    `StaffMigrationOnDeviceTest` compares migrated tables with Room's own;
    any new column or index on those tables goes into its list of
    statements, or the on-device run fails (55e79c5).
33. **On Android a statement starting "ROLLBACK" ends Android's own
    transaction.** A savepoint is rolled back with `";ROLLBACK TO x"`
    (LiveSync.tryOne), never `"ROLLBACK TO x"`.
34. **A replace-restore of a syncing book pauses live sync first** and ends it
    once through (resumes it if the restore failed): with the triggers live,
    the restore reached the other device as deletions.
35. **`INSERT OR REPLACE` gives a row a new uid.** Saving a row again keeps
    its uid (upsert by key), or the other device gets a duplicate.
36. **Any change to a DAO write's signature re-runs `scripts/gen_viewer_dao.py`**,
    a new parameter included, or ViewerDaoTest fails.
37. **A getter that builds a new object is never `===` to itself.** A test
    that compared one (LiveSyncReviewTest) passed only by chance.
38. **No `: . ; [ ] / < > \` in a backtick test name.** The JVM refuses them
    in a method name and the build fails at compile time.
39. **What a device sent is its own note, outside the book** (CustomerLink's
    `Place`): kept in the book, a restore or a second device believed pages
    were sent that never were.
40. **A counter never stays without the owner's PIN.** A book opened on a
    counter without one takes the PIN keys the device kept when the owner's
    PIN was last typed right there (`Access.ownerPin`); a restore keeps the
    access keys a copy says nothing of.
41. **Nothing that decides who someone is comes from a file anyone can edit.**
    The computer's device id is its MachineGuid (hashed); counter mode is
    the book's word too (lesson 26).
42. **A backup is written on the computer's own disk, then copied out.**
    Exported straight to a USB stick or Drive folder, it held the book's
    transaction for as long as the stick took, and the counter waited.
43. **One conversation, many relays.** A relay is added only when it passes
    `scripts/test_relay.py` like the others; a change to the protocol goes to
    all three in the same commit.
44. **Research before deciding, then decide once** (the owner, 22 Oct): "PHP
    only on Hostinger" was said from memory and was wrong (Business has
    Node.js, on demand); Upstash was planned before anyone checked that a phone
    cannot hold its key. Search first, give one decision, and say what changed
    if a verified fact changes it.
45. **Tell before doing** (the owner, 22 Oct): every build, push, deploy or
    new file is said first, in one line, then done. A tool call made in
    silence was refused.
46. **A key is a key for one use.** The book's key is the root only; each road
    gets an HKDF subkey and an AAD naming the road (`LiveWire.Road`), and the
    key never sits in plain in a file that is copied whole (`KeyAtRest`).
47. **A relay that counts writes is spent by quiet rounds.** A round with
    nothing to say must write nothing (no `seen` on every take): Cloudflare's
    free DO counts rows written, 100k a day, and 20 devices polling every 4 s
    would have spent it by noon.
48. **A lock that does not answer is not a lock that refuses.** `KeyVault`
    returns null only for "not this device's key" and throws
    `KeyLockUnavailable` for everything else; a null un-pairs, a throw waits.
    `runCatching { … }.getOrNull()` around a security primitive turned a
    Keystore hiccup into a wiped pairing and, on Windows, a plain key.
49. **A round's answer is for the book that asked.** The phone checks
    `KhataDatabase.isOpen(db)` before writing a round in; Room reopens a
    closed database on the next write, so a stale handle would have reopened
    the old file beside the restored one.

---

## Working with the owner

- **Reply in Roman Urdu** unless English is asked for.
- **Confirmation first.** Present the plan, wait for approval, then build.
  Push only after an explicit yes.
- **Never hide facts**, good or bad. No false guarantees. Say plainly what was
  verified and what was not — in particular, code checked by reasoning is not
  code tested on a real device.
- **Root-cause debugging.** Find the real reason; do not guess.
- **Ask before spending** any paid credits.
- **Flag privacy concerns unprompted**, and Shariah concerns too — while
  noting you are not a mufti and pointing to a qualified scholar.
- **Professional standard.** No rough or quick fixes.
- Content and store copy are planned bilingually: Urdu for local trust,
  English for reach.

---

## Where the project stands

**Play Store publishing is the blocker.** Nothing else matters as much.
Screenshots, bilingual listing, content rating, data safety form (no data
collected until live sync's wake-up reaches Play: then "Device or other IDs"
for app functionality, see Live sync), feature graphic, signed AAB.

Use dummy data for store screenshots. Real customer names and numbers must not
appear in a public listing.

**Google Drive backup** asks for one Drive scope, `drive.appdata`, which
Google classes as **non-sensitive** (checked 9 Oct 2026 on Google's "Choose
Google Drive API scopes" page): no sensitive-scope verification, no 100-user
cap, no security audit. Basic brand verification (homepage, privacy policy,
domain ownership) is still worth doing so the consent screen shows the app's
name and logo. Do not add a sensitive Drive scope without re-checking this.

Sign-in needs an **Android OAuth client per package + SHA-1 pair** in the
`roshan-khata` Cloud project, beside the Web client the app passes as
serverClientId. The CI test APK is `com.innovation313.roshankhata.dev`
(client added 9 Oct); the Play build is `com.innovation313.roshankhata`
signed by Play's app-signing key, whose SHA-1 is in Play Console. A missing
pair shows as "cannot sign in"; the Backup screen's sign-in dialog prints the
running build's package and SHA-1 for exactly this.

**Data import from other ledger apps** is agreed but unbuilt. Balance-only
first: name, phone, current balance as one opening entry.

Smaller and deferred: merging duplicate parties, cashbook and cheque
`createdAt` editing, a WhatsApp number on the Help screen.
