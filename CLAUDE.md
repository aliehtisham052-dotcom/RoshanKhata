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
  - `:app` — the Android app, 258 Kotlin files, ~64,700 lines (counted 8 Nov 2026, main only)
  - `:shared` — everything that does not need Android, ~37,800 lines (`:desktop` ~33,800
    more; tests not counted; counted 8 Nov 2026): sums, voice parsing, search, dates, the Room database, the
    backup format, the shared Compose screens. Android and Windows both run it.
  - `:desktop` — Roshan Khata for Windows (Compose Desktop, MSI)
- About 3,044 string lines (3,038 translatable keys checked by `scripts/check_strings.py`) and 65 plurals in
  values/strings.xml (counted 8 Nov 2026; earlier counts are in git) × 9 locales — English, Urdu, Roman Urdu (`b+ur+Latn`), Sindhi,
  Arabic, Farsi, Hindi, Bengali, Indonesian (`in`)
- Room database, version 44 (44: book_seals, 5 Nov 2026; 43: a party's private note, a cashbook row's and a cheque's picture path, 4 Nov 2026; 42: one covering index in place of another, 3 Nov 2026; 41: indexes only, 30 Oct 2026; schema JSON checked in up to 31; CI writes the rest);
  backup format 19 (the seals, 5 Nov 2026; 18 the note and the two picture paths, 4 Nov 2026; 17 documents, 29 Oct 2026; 16 the payment reference; 15 the bill photo path)
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
  QR start at their ink). Esc closes every dialog window (`escCloses`). Laptop fit (1.8.2, the owner's photos of 1366x768 at 125%, about 1093 dp): no label may wrap letter by letter — pill and chip rows scroll sideways (`horizontalScroll`, `maxLines = 1, softWrap = false`), the header below 1180 dp keeps only icons with hover tips, Home and the rail are tighter; the Home banner turns every 3 s (the phone's TURN_MS) with a cross-fade; an invoice's ten designs are stepped through with ‹ › (`DesignStepper`, Apply keeps one via `updateInvoice`). ScreenProofTest also draws the laptop size. Motion and contrast (1.8.3, `Motion.kt`): every click target uses `Modifier.press` (hand pointer, a hover wash `Look.hover`, ripple) instead of bare `clickable`; chosen colours ease over 160 ms (`eased`), pages cross-fade over 200 ms, Home rises once; nothing bounces. Control edges are `Look.edge` (3:1 on page, card and rail — `Look.line` is only for dividers and card edges) and chosen pills `Look.pickedFill`/`onPicked` (light green by night); `LookContrastTest` measures them. Proof pictures render a second in so animations have ended. PDFs on the computer (1.8.4, `PdfPreview.kt`): every PDF / Statement / Print button (`StatementPrint.show`) opens the sheet in the app's own preview first (pages, zoom), with Save PDF, Print (Windows' dialog, `printDialog`) and Open folder; `Sheets` reads a Printable or an invoice design's own page sizes; `PdfFiles` writes Documents\Roshan Khata\<title>_<date>.pdf with Apache PDFBox 3.0.4, each page a 3.5 px/pt JPEG (about 250 dpi) (looks exactly as printed, Urdu safe; words not selectable), then opens it in Windows' viewer. PdfFilesTest's PDFs go to desktop-test. The MSI adds java.logging for PDFBox's commons-logging — not exercised by the Gradle tests (full JDK), so check a saved PDF on the laptop. MSI size (1.8.1): ICU4J is
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
  `MonoTile` (a tile glyph in one colour) — `StatCard`, `MonoTile`,
  `GlyphChip` and Panes' `TotalCard` were removed 29 Oct 2026 as unused
  (Marhala 2 replaced them). `Look` (Panes.kt) gained the
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
  (home_cash_in_hand — removed 29 Oct 2026, unused since Marhala 2 —,
  home_quick_actions, home_col_time). Phase 2 (the same
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
  Still Material-shaped then: the AlertDialogs (confirmations, small forms) and
  the DialogWindows not yet on DialogFrame (bill, scheme, staff, product) —
  all on the app's dialogs since Marhala 4 (part 10, 25 Oct 2026).
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
  changes and gives the others'; later relays are read by time
  (`OTHERS_MS`, 2 min) and also sent to while a parcel came from them within 24 h — a phone that cannot reach Cloudflare still meets a computer
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
- **Phase 2 — units and relay storage (p2-misc):** `UnitWords.STORED_IN` now has the counter's
  line tables (sale_items, sale_return_items, purchase_order_items, stock_adjustments) and
  products.packUnit; the repair (`UnitWords.repairStored`, both platforms) reads first
  (`repairNeededQuery`, nothing written for a book in order) and runs in one transaction under
  sync_mute — each device repairs its own copy, nothing goes to the sync log (else every label
  row went out at once and both devices' repairs met as money-table conflicts). Relays: an
  account-wide cap — the Worker's `!ledger` keeps each book's size (SQLite's `databaseSize`,
  reported every 64 KiB moved and at the alarm, at least 64 KiB a book) and refuses new books and
  parcels 507 "full" past `ACCOUNT_BYTES` (4 GiB; the free plan's 5 GB is 4.66 GiB); Supabase keeps
  `books.bytes` and refuses past `RK_STORE_BYTES` (256 MiB). test_relay.py `--store` holds all three.
- **Phase 3 — pairing, live sync, App Lock (p3-sync; CI only):** a code's hosts must be on the shop's own
  network (`WifiSync.onThisNetwork`: 10/8, 172.16/12, 192.168/16, 169.254/16, 127/8 — one-off, live, phone and
  give codes alike). A phone taking a shop's book restores with `keepAccess = false`. A phone's pairing asks
  before turning the relay on (`live_phone_relay_ask`), and a pairing or give that is not taken puts it back
  (`setPending(relayWasOff)`, `dropPending(givenUp = true)`). fingerprint skips `ACCESS_KEYS` and reads one
  page per transaction. A WiFi round that worked clears the relay back-off; while the relay backs off the
  WiFi is tried within a minute (`relayRetryAt`). A change held a day (`HELD_GIVE_UP_MS`) goes to
  sync_conflict; the phone's live card says held changes and `live_wake_off`. Relays answer `push` (can
  they ring a phone) in health and wake; `tellWake` takes a `push:false` as not told; a carried parcel needs
  the relay switch on and no halt; taking the address away is always allowed. A bad parcel body is passed
  over; a malformed answer is `Refused("bad answer")`. DPAPI: null only for 13 / NTE_BAD_DATA / NTE_BAD_KEY /
  NTE_BAD_KEY_STATE, any other refusal throws. `AppLock.required` is the one App Lock decision; a prompt
  that cannot run tells and lets in instead of `finishAffinity()`. A Windows pairing offer is asked only of
  the business it came to (`Incoming.bookFile`, `live_pair_left_business`); both replace dialogs name the
  two shops when they differ. MONEY_TABLES has day_close, shifts, loyalty and the order tables.
- **Phase 3 — counter speed and gates (p3-perf; CI only):** the shelf is read once per ticket and once
  per Expiry page (`Fefo.lots/lotOf(..., shelf)` from `Counter.onHand`); Cashbook and Invoices (both
  platforms) read a window that grows as the owner scrolls (`ListWindow`, `observeCashPage`,
  `observeInvoicePage` — each invoice's lines summed once), counts and this month's figure keep their own
  reads; the Windows counter reads only its 8 tickets and 6 returns (`observeRecentSales/Returns`); a
  receipt's balance is `liveBalance` and the phone refreshes only stock and that one balance after a
  ticket. **No schema 39:** the cashbook and invoice date indexes were not added (the windowed lists give
  most of it; the day sums still scan the cashbook). Hold is a snapshot (`Counter.holdTicket`, `clearAfter`)
  and one transaction (`holdSale(dropId)`); a line over `SaleMath.MAX_QTY`/`MAX_RATE` or not finite is
  refused. The day report needs REPORTS; stock count/adjust/transfer, purchase orders (`OrdersScreen(onNeed)`)
  and the phone's Products menu ask a manager's PIN instead of refusing; two staff may not share a name
  (`staffNameTaken`, `staff_name_taken`); counter tickets print on the printer used last without asking
  (`Job.direct`, phone `TextReceipt.print(direct)`, changed in the counter's settings); the phone has the
  round-off chip and keeps the payment step open until the ticket is written (`postedTick`). Tests: PerfReviewTest.

- **Phone, phase 3 (p3-droid: R04, R07, R34, R38, R39, R99-R101, R104, R155, R157; CI only):**
  `TextFit.needed` also relaxes fixed heights at the normal size in Urdu, Sindhi, Arabic and
  Farsi on Android 15+ (targetSdk 35+ turns `elegantTextHeight` on, and Android 16 ignores the
  attribute for targetSdk 36 — no theme switch exists); Roman Urdu (`ur-Latn`) stays as designed.
  A snapshot restore folds the copy's own -wal into a temp file first (`Snapshots.foldedCopy`)
  and only then deletes the live log; the daily sweep takes `.tmp-wal`/`.tmp-shm` too. Picture
  reattach writes a plan (`reattach.plan` → `reattach.staged`) before renaming and finishes a
  killed run at app start (`ImageReattach.recover`); a `.moving` with no plan is set aside, never
  guessed. Cache backups (`RoshanKhata_Backup*.txt`, `left-…`) older than an hour are swept on each
  export (`Backup.sweepCache`; other files in cache/backups untouched). Headerless screens on
  Android 15+ pick status-bar icons from the root's colour (`ScreenInsets.isLight`). Cheque status
  uses the night-aware `*_text` colours; day close keeps its dialog open on a refused save and
  takes the note's focus. Tests: TextFitTest, ImageReattachTest, SnapshotsOnDeviceTest (restore
  with a -wal).
- **Phase 3 — Windows and release gates (p3-win; CI only):** the MSI's runtime has `jdk.crypto.ec`
  (R06: on JDK 17 jlink adds SunEC only when named; without it every HTTPS/WSS call — relays, kill
  switch, update check, the live line — died in the TLS handshake). desktop.yml's "Packaged runtime
  check" runs `RuntimeSelfCheck` with the app image's own `runtime/bin/java` (modules, SunEC, ECDHE
  suites, Urdu month names, commons-logging, SQLite's native library, one HTTPS request) and fails
  on any FAIL line (R36) — **a new module the code needs goes into `modules(...)` and that check**.
  "Back up now" is owner-only `BACKUP` like the phone (R33); CSV exports stay on EXPORT. A book from
  a phone (`from-phone/`) is deleted once its question is answered or unreadable, only the newest
  is kept, and leftovers go on opening (`PhoneSync.forgetIncoming/clearIncoming`, R41). Every CSV
  cell goes through `CsvCell` (shared: quoted, a leading `= + - @` tab/CR text gets `'`, plain
  figures and ProductCsv's `="<digits>"` untouched; `CsvImport.cells` takes the mark off) (R153).
  Window shortcuts do nothing while one of Book's AlertDialogs is open (`KeyActions.busy`, R156) —
  a new AlertDialog in Book goes into `questionOpen`. aab.yml runs the strings job's checks and
  build.yml's tests before the keystore, and refuses a versionCode not above the one in the
  release-aab notes (`versionCode: N`; input `same_version` for a bundle never uploaded) (R37,
  R102); strings.yml also fires on `shared/src/**` (R103); `*.whl` is ignored, the olefile wheel
  is gone (R154). Tests: CsvCellTest, PhoneSyncIncomingTest.

### Khata Report redesign (24 Oct 2026, phone — the owner: "local si, generic si")

- `LedgerReportActivity` after the owner's chosen mockup: period chips on the green band (Today, This
  week, This month, All, Custom — Custom asks the first day then the last; week/month run open-ended),
  opening on This month; a summary card hung 48dp into the band (a `Space` marker — ConstraintLayout
  ignores negative margins) with the window pill, entry/party counts, a gave/got share bar and the two
  totals; entries grouped by day (heading with Today/Yesterday and the day's count, rows with an
  initial, time · note, amount and an I gave/I got tag); Share as the one primary button, Preview and
  Download as icon buttons with tooltips. **No all-customers net and no per-day net** — removed before
  because it belongs to nobody's account and disagreed with the PDF. No new strings (all reused).
  Window survives rotation. CI only.

### The Windows audit, part 1 (25 Oct 2026, kmp-desktop-15 — the owner: "implement one by one")

The 216-point audit of every Windows screen (Claude Doc "Roshan Khata Windows — Deep Analysis aur Fix
Plan", mockups on the Design canvas). Part 1 is the money and safety fixes, one commit each, MSI 1.17.x
(CI stamps the run number: 1.17.216 was the last); part 2 (the phone's look) follows; parts 3–4 (merged
pages, page by page) wait. CI only.

- Entry form: a ticked "paid now" writes its cash "I got" with no goods line (`entryRateType`); an edit
  keeps the goods lines the form does not show (`editedLine`, `otherItems`). Invoice discount as % or
  money (`invoiceDiscount`). Z report and sales report: a loss carries its minus.
- Home's cash card is the drawer by Close the day's rule (`drawerByTheBooks`: last count + cash in −
  cash out since), never the cashbook's all-method net.
- Every balance says its side (`balanceSideWord`, shared): customer page, receipt, statement dialog,
  CSV import (two figures), delete forever (the binned balance first), deleting a business (its sign).
- The restore question scrolls. The week-old backup reminder counts only "Back up now" files
  (`AppPrefs.lastSavedBackup`, per business) and copies in the outside folder (`notif_backup_body_pc`).
  The counter's open ticket belongs to the window (`CounterDraft` in Book), not the page.
- Cheques (both platforms): `settleCheque`, one transaction — bounced/cancelled only from pending, back
  to pending only from bounced/cancelled with no ledger entry; clearing is `clearCheque` alone.
- Business card (both): a blank box never overwrites the shop's name; only SETTINGS renames it.
- Stock adjust says a failure; a bill or invoice line typed but not added stops Save
  (`line_editor_busy`); Khata Report's export asks EXPORT and is audited; Schemes' edit, claim and
  delete ask EDIT_BOOK; the default scale prefix "21" is stored as nothing and is no scale in use.
- Zakat (both, nine languages): the price label names metal and unit (`*_price_hint_tola`);
  `nisab_help` lost its per-gram sentence and shows only for silver.
- The High list (same day): a receipt's "Balance after" is the balance that entry left, in the
  customer's words (`balanceAfterEntry`, `ReceiptBalance`, both platforms); the sales report's net is a
  dash until every item sold has a cost (`grossOrNull`/`netOrNull`); the counter keeps its total and
  buttons in sight on a laptop and finds any ticket of 60 days (`ticketsMatching`); the eye's choice is
  kept (`AppPrefs.balancesHidden`, hidden by default); the Live sync window tells live, quiet, stale
  and never-heard devices apart (`peerState`); daily copies say which were kept before a restore or
  merge (`DailyCopies.kindOf`); Business Profile keeps a Jalali calendar (`calendarWord`); printed
  tables size their figure columns (`TablePrint.columnEnds`) and the dues PDF has its totals; posters'
  closed/hours/Ramazan times are the owner's (`posterDays`); the "Made with" mark follows
  `Branding.ON_POSTERS_AND_CARDS`; an entry's search finds its goods (`entryMatches`).

### The Windows audit, part 2: the phone's look (Marhala 2, 25 Oct 2026, MSI 1.18.x)

The owner's chosen mockup (Design canvas "Roshan Khata Windows — Naya Design": Home, Khata, the entry
dialog) in place of 1.10.0's Fluent face. CI only; ScreenProofTest draws it at the owner's two real
windows (1093 x 535 and 1043 x 590 dp), day and night, English and Urdu.

- **Colours and corners** (`Look`, `LookPalette`): the phone's warm page and white cards, the deep
  green #1B5E3A as accent and chosen fill, a gold #F3C552 edge on chosen and primary controls
  (`Look.goldEdge`), 8/12 dp corners. LookContrastTest measures the green card's words and pills and
  the rail's group names too.
- **One top row** (`PageHead`/`LocalPageHead`, `AppHeader`): a page's `PageHeader`/`PaneTitle` writes
  its title, line and buttons there; the window draws them beside the backup chip (`BackupChip`: a
  cloud, "Backed up" over a short when, or the age in amber over Back up now), the phone, the bell, ⋮
  and the one Add party. The page's buttons take at most 42% of the row (`widthIn`, never an unfilled
  weight — lesson 53). More (⋮) holds only what the rail does not (Data, Business, Display,
  Security, Help).
- **The rail** (`SectionRail`, `Group`, `Section`): the shop's card on top (Business Profile or
  Businesses), Home, EVERY DAY, SALES & STOCK, then Reports, Tools and More folded behind a row with
  their count (`GroupRow`; a group opens when one of its pages is chosen from anywhere). Every place
  wears its own picture: the phone's two-tone tile, else one of its one-colour glyphs in the tile's
  hue (`Section.ink`, `PictureBadge`); fifteen more phone drawables are copied at build time;
  TileIconsTest refuses a picture worn twice.
- **Home** (`HomePane`): the day and "Your shop today"; the deep green Net balance card (`HeroCard`:
  net with its sign, the eye, I have to get / give pills, and the drawer, today's entries and today's
  promises — `HomeFigures.promisedToday` from `observePromises`); Recent dealings (the last eight
  entries, `recentEntries`, `whenWords`); Notifications with each page's picture (`LookRow` draws
  `TileBadge`, no one-colour blobs); six quick actions (You gave · F2 / You got · F3 / Statement ask
  whose first — `HomePick`; Add bill opens Supplier bills with its form — `BillsPane(startAdding)`).
- **Type** (`DeskTheme`/`deskTypography`): Material's sizes without its fixed line heights and
  tracking; every window and the proofs use it (lesson 55).
- **Dialogs** (`AppDialog`): a card inside the window over a dimmed page (Compose's in-window
  `Dialog`, `DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)`), one
  title and one ✕: a body with its own head says `OwnDialogHead()` (DialogFrame, the entry form);
  others get the dialog's title; forms leave their own title out `inDialog()`. `closable = false` for
  a question that must be answered. Window shortcuts wait while any is open (`OpenDialogs`, lesson 54).
  `KeepAppLook` stays for the customer's second-screen window only.
- Strings (nine languages): rail_sales_stock, rail_reports, header_backed_up, home_shop_today,
  home_recent, home_todays_entries, home_gave_got_count, home_promised_today.

### The Windows audit, part 3: pages that do one job, one rule each (Marhala 3, 25 Oct 2026, MSI 1.19.x)

CI only; the proofs draw the merged pages (screen-laptop-expiry-en, -register-ur, -sales-en).

- **Merged pages** (`PageTabs`, `PageTabRow`, Panes.kt): Supplier bills · Purchase rates · Company
  schemes; Follow-up · Dues by age; Products & prices (Products · Price list); Stock & expiry (Stock on
  hand · Expiry · Stock register); Sales report · Sale Insights; Khata Report · Register; Staff · Who
  did what. The first of each list is the page the rail names and opens; the others leave the rail and
  show as pills above the page (34 places, 25 rows). A tab is still its own `Section`, so the bell, Home
  and the gates reach it and the rail marks its page (`PageTabs.hostOf`). Purchase orders keep their own
  row, as the owner's mockup draws the rail (the Doc's table had them under Products). Low stock opens
  Stock on hand. PageTabsTest.
- **"What was sold", one rule** (`KhataSales`, `KhataDao.khataSaleRowsBetween`): the sales report counts
  the khata's sales too — every customer "I gave" Sale Insights counts (not Qarz-e-Hasna, not a refund,
  not a supplier's) that no done ticket stands for — so its revenue equals `salesTotalBetween`. A sale's
  amount is spread over its goods by value (under the value a discount, over it a line of "No item",
  `srep_no_goods`); a lone unpriced line takes it all; who wrote it is the cashier view's name; a line's
  cost is its product's (`Cost.perUnit`). SalesRuleTest.
- **"What is left of a batch", one rule** (`Fefo.lotsOf`; `allLots`, `register`, `expiring`,
  `observeExpiringCount`, `options`, `leftInBillUnit`): the counter, Expiry and its write-off, the
  stock register (both platforms, and the phone's PDF), the bell, Home's chip, the bills banner, the
  khata's batch picker and the return to a supplier all read Fefo's figure — the bill line less what
  was sold from it, came back or was written off, packs in the product's own unit, never more than the
  shelf (an untagged sale comes off the batch that expires first). Gone from the DAO, so nothing can
  read them: `batchOptionsForProduct`, `observeExpiringBatches`, `observeExpiringCount` (each took off
  only tagged khata sales, or those and walk-ins, in the bill's own unit); `allBatchesOnce` is
  `batchRowsOnce` and subtracts nothing. Pages re-read on `observeBatchMoves` (a tick over every table
  that moves stock). A write-off is written in the unit the lot is counted in. The phone's register
  note (`pdf_insp_how_*`, nine languages) says how the figure is made instead of "not more than".
  BatchRuleTest.

### The Windows audit, part 4: every page in the new design (Marhala 4, 25 Oct 2026, MSI 1.20.x)

Page by page in the Doc's order (Khata → Counter → Cashbook/Cheques → Bills/Products/Stock → Invoices →
Reports/Zakat/Calculator → Profile/Card/Poster → Staff/Daily copies/Who did what → Live sync/From phone/
Businesses/Settings), each part with its proof pictures. CI only.

- **Part 1, Khata** (`KhataList.kt`, `KhataCustomer.kt`, `ReminderDialog.kt`; the shared `PartyLedgerScreen`
  page is gone, its file keeps only `balanceSideWord`/`balanceColour`): the list at the mockup's width beside
  the account (38% of the page, 300–400 dp). All · Customers · Suppliers with counts (`PartyTabs`); the two
  totals are also the side filter (`KhataTotals`, the phone's tap); the search with Filter (side, days) and
  Order menus (`KhataFindTools`); the phone's summary line (order, who needs a reminder, settled) with every
  statement, Excel (now a customer/supplier column, `customersCsv(typeOf)`), import and choose several
  (`KhataInfoRow`); choosing several is its own bar, Delete red and Cancel quiet (`KhataSelectBar`), and says
  "moved to the bin"; parties as cards, the initials' colour the party's id's (`Look.avatar`, six pairs, all
  ≥ 4.5:1 in LookContrastTest). The account (`CustomerPane`): face, name, phone · type, the balance in its
  side's words; the promised day and season record as chips; I gave · F2, I got · F3, WhatsApp, statement,
  edit, ⋮ delete; the entries' own search, days and order; the ledger in columns — what (goods, else how it
  was paid, else the note), I gave, I got, the balance after each (summed as decimals; what the shop owes
  carries its minus inside the left-to-right run) — under day headings. Nobody chosen: `NoPartyChosen`.
- **The phone's customer page, the rest (part 1b):** the line under the name says s/o and the village
  (`customerFacts`); a click on the balance copies the phone's ready line (`copy_balance_*`) and the side's
  words say "Copied" a moment; the promised day's chip (and ⋮ → Promised day in agri shops, the phone's menu)
  opens `DueDialog`; ⋮ → Customer QR shows the card (`CustomerQrDialog`: a missing code is made and saved
  before it is drawn; saved as a picture it is the code alone, `customer_qr_save_picture`); ⋮ → Select entries
  to delete ticks several (`EntryPick`, kept by the book) and bins them with `softDeleteOwnEntries` — what a
  bill, plan, cheque, ticket or return wrote stays, and is counted. Proof: screen-laptop-khata-pick-ur.
- **Part 2, the counter on the laptop** (`CounterSalePane`, proof screen-laptop-counter-short-en at the owner's
  1093 x 535): the settings are an icon in the head beside Hold and Clear; the row of counter tools shows only
  when one of them does; Ticket / Items sit beside the item box. The total is said once, in the foot, always in
  sight; the buttons are one row — Complete sale with its F12 on it (`FluentButton(trailing)`) and Complete &
  print beside it (the printer alone on a laptop, `PrintAndComplete`, its words as the hover tip). The ticket's
  discount shows in money under its box (a typed "10%" said nothing); a walk-in's unpaid rest is "Still to
  collect" (`pos_still_due`), never "To account"; dropping a held ticket asks first (`pos_drop_held_confirm`).
- **Part 3, Cashbook and Cheques:** the cashbook's head keeps only Close the day (and the day report with the
  big-shop tools on, as on the counter); Money in / Money out are the phone's two coloured buttons above the
  list (`WayButton`, now shared in the module); its figures say Money in, Money out, Net and Should be in drawer
  — `drawerByTheBooks`, Home's and the day close's own figure, so no third "cash" disagrees. Close the day is the
  app's dialog and waits for its save (the save ran in the dialog's scope and was cancelled with it), saying a
  failure. Cheques: each tab's empty words are its own (`cheque_none_cleared`, `cheque_none_bounced`); the form
  says the cheque rule whole in its body; the actions, Clear and Bounce are the app's dialogs; faces take the
  party's colours (`Avatar(id)`). New shared dialogs: `AskDialog` (a question, its answer red when it removes)
  and `TellDialog` (one OK). Proofs: screen-laptop-cashbook-short-ur, the pages' own rail marks.
- **Part 4a, Bills, Products, Stock and Orders:** Bills show the phone's three figures (bills, what is owed to
  the suppliers — each one's live balance once — and the batches expiring, which opens Expiry); a supplier
  name typed new offers the closest one in the book (`BillScan.similarKnown`, "Use X"), as the phone asks.
  **Expiring is one window, 60 days (`ExpiryWindow`), everywhere** — the bell and Home said 30 (`na_expiring`
  now says 60 in nine languages). Products: an empty book says how products come (`no_products_pc`; the
  phone's words pointed "below" at nothing), a search with no match says so (`no_matching_products`); the
  product editor is in the app's frame and refuses a price, count or rate it cannot read (it saved "no price"
  in silence). Stock on hand and Purchase orders have their own subtitles; the stock table says "Cost price".
  Orders: marked sent only when WhatsApp opened; with no number the order is copied, said
  (`po_copied_no_number`) and left for Mark sent; goods received take each batch's expiry. Proofs:
  screen-laptop-bills-en, screen-laptop-products-ur. Not done: the phone's receive form has no expiry yet.
- **Part 4b, Expiry, the register and labels:** Expiry's batch trace sits above the list (a card in a card
  before) and the page's own empty state shows when nothing expires; the trace says what came and what is left
  (`trace_came`, Fefo's figure), never one bare number. Every lot can be sent back to its supplier
  (`BatchReturnDialog`, the phone's: goods given on the supplier's khata, tagged to the batch, up to what is
  left in the bill's unit) or written off, as the phone offers it; Bills' lines can be sent back too. The stock
  register is in the page frame with figure cards, re-reads when stock moves, and lists expired first, then
  what expires in the window, then the rest by name (the PDF keeps the order). Barcode labels: each bar is one
  exact rectangle over a run of modules (`LabelSheet.barRuns`, drawn unsmoothed) — columns cut to whole points
  printed bars of one width one and two points wide; the price is placed first and the name takes the room
  left. The phone's full inspector PDF came to the computer after part 10.
- **Part 5, Invoices (and the group's Plans):** the list is narrow (36%, 280–380 dp) and the open invoice keeps
  its head (face, name, number · date · due, the total, received and still due) and its actions in one block —
  PDF, then WhatsApp, Edit and Delete as icons, the design stepper in the same row (`DesignStepper(modifier)`) —
  with every page of its design under them in the height left (`InvoicePages` over `Sheets`, the sheet Print
  sends: a design, else the plain layout; each page drawn at the pane's own width, 1.5–3 px a point, when it
  scrolls into sight). The counts are said once (the pills), the head's Add invoice is the one way in, delete
  is the app's titled question, faces wear the khata's colours when one party has that name. **WhatsApp on the
  computer** (`sendOnWhatsApp`, the phone's sendPdfToCustomer as far as Windows goes): the PDF is saved
  (`PdfFiles`) and shown chosen in Explorer (`showInFolder`: `explorer /select,` and the path as two arguments —
  one argument is quoted whole by Java, a form Explorer does not take), the chat opened by the number alone
  (wa.me, no name or amount through the browser), and the owner told to drag the file in or attach it with +
  (`invoice_whatsapp_pc`; WhatsApp's own help names drag and drop — pasting a file is not claimed). The form is
  in the app's frame (Save, Cancel and the total in its foot) with the design and its live picture beside it
  when the dialog is 860 dp or wider. **Product suggestions, one rule on both** (`SmartSuggest` moved to :shared
  with `Shelf`, `asSuggestions`, `withStarters(starters, label)`; the phone keeps the dropdown as the extension
  `SmartSuggest.attach` and `withStarters(context)` in ui/SmartSuggestAndroid.kt): the invoice's and the bill's
  line boxes are `SuggestBox` over `rememberProductShelf(dao)` — the best six, company · unit, a pick fills the
  name and the product's unit when none is typed (never a price: cash or udhaar is the owner's to choose). Esc
  closes the list before the dialog (`EscFirst`, consulted by `escCloses`): the list's popup never takes the
  keys. Plans: Mark complete asks the owner's rule and then the owner, in figures (paid, left); its actions,
  the payment and the new plan are in the app's frame, the party picked with `PartyPicker` (whose chosen chip
  now says the balance's side). Bills', Products' and Who-bought dialogs are the app's too. Proofs:
  screen-laptop-invoices-short-en, screen-laptop-invoices-ur, dialog-laptop-invoice-en; ProductShelfTest.
- **Part 6a, the entry form (the Khata step's rows the first pass left: the phone's dialog_add_entry):** the
  goods are a list on every account (`GoodsList`, EntryGoods.kt): an item added from the boxes, a row clicked
  back into them to correct and put back in its place (Cancel puts it back unchanged; its spray advice, product
  and batch ride with it — `boxesLine`), ✕ removes; the whole list is saved (`EntryDraft.items`, Main writes it
  whole — the phone's other lines were only shown). The name box suggests the shop's products (`SuggestBox`);
  a customer's sale fills the Rate box from the product's price for Credit or Cash (`offeredRate`, RateOffer —
  never over a rate the owner typed; a switch re-prices only the product's rates) and says when that price is
  not set or is per another unit, with this customer's last price for the chip (`lastSaleRate`); a customer's
  return takes the price he was last charged, per the same unit; a return of more than he holds is asked
  first (`netGoodsWithParty`). The amount follows the items' total until the owner writes his own
  (`followedAmount`, the phone's two flags), the difference said (`lines_amount_less/more`) and the total
  offered back. "Last time's items" on a new sale (today's prices, no batches). The time of day beside the date
  (`TimeOfDayDialog`, the shop's clock, never ahead of now). A loan (Qarz-e-Hasna) cannot carry goods. The bill's
  photo is chosen on the form and copied in only when the entry is written (`EntryDraft.billPhoto`,
  `photoPathOf`). The customer's signature (`SignatureDialog`: mouse or touch, `EntrySignatures` — filed by
  entry number per business, `entry_signatures[_b<id>]/sig_<id>.png`, deleted with its business; kept on this
  computer only, said by `sig_hint_pc`). "When will he pay?" is the app's dialog. Tests: EntryLineEditTest
  (rewritten for the list), BusinessesTest; proof dialog-laptop-entry-en.
- **Part 6b, the entry as its receipt (the Doc's "Entry detail"):** a click on an entry shows its receipt
  (`EntryReceiptDialog`/`EntryReceiptCard`, the phone's EntryDetailActivity — it opened the editor at once): way
  and amount on the khata's colour, the party, number and time; the goods as the receipt says them
  (`ReceiptGoods`, moved to :shared from the phone's GoodsText, which now calls it — one text on both), the note,
  how it was paid, the balance it left (ReceiptBalance); an agri sale's season with Change (`setEntrySeason`) and
  the dealer's spray advice per item (`AdviceDialog`, `setLineAdvice`; `TradeKit.sprayAdvice`); the bill's photo
  (opens large) and the signature, taken or changed here. Edit, Delete (an entry another record wrote says
  where to change it), Print (the thermal receipt, `printReceipt`, shared with the editor), and Invoice: the one
  made from this sale opens on its page, else a new one starts from the sale (`InvoiceForm(start, startLines)`,
  EntryInvoice, `sourceEntryId`). The entry flow's questions (twin/limit, linked, delete, a cash sale's other
  half) are the app's dialogs; `PickDialog` is a short list in the app's frame. Tests: ReceiptGoodsTest (the
  phone's GoodsTextTest's lines, on the computer); proof dialog-laptop-receipt-en.
- **Part 7a, the Khata Report and the register (the phone's report screens and documents):** the Khata Report
  is the phone's 24 Oct design — chips (Today, This week, This month, All, Custom: the first day, then the last,
  `TwoDayPicker`; week and month run to now), opening on this month; a summary card (the window as a pill that
  picks days, entries · customers · suppliers, a gave/got share bar, the two totals); the entries newest first
  under day headings (Today · / Yesterday ·, the day's count), each with its face, time · number · note, the
  amount and an I gave / I got tag. Still no all-parties net, for the window or a day. `ReportWindow` (with
  `window()`), `dayAfter`/`dayBefore` (calendar days, never 24 h) and `TwoDayPicker` are shared with the register.
  The register is in the page frame: chips without "all" (the phone's rule), two figure cards, each line with
  what it was (item, quantity, batch, company, number; a purchase's rate, expiry and bill) under its section's
  head, totals under each; it now needs REPORTS like the Khata Report (`Section.need`; the phone's register,
  opened from Products, has no gate). **The phone's documents on paper** (`PhoneSheet`: a Printable that lays its
  pages out once with a `Pen` — measured on a scrap picture, the phone's sizes, colours and page breaks — and
  replays each page; mirrored for RTL): `ReportPrint` is the phone's LedgerReport (band with the period and when
  it was made, the not-a-backup note, the summary, 20/30 pt rows in two money columns, the foot) and
  `RegisterPrint` the phone's RegisterReport (the shop's address and tax number on the band by the phone's rule,
  `identityLine`; the two notes wrapped; summary; two sections with continued heads; signature lines; "Generated
  … · Page n" at every foot — the phone's only on the last). The download banner stays the phone's; no printed document has a watermark since 10 Nov 2026 (the owner: it covered the page; the foot's strip and page line name the app).
  Every other printed table (`TablePrint`: price list, orders, sales report, stock register, dues by age) and the
  customer statement and plain invoice are in the phone's green and gold now (they were navy), and the tables say
  when they were made at every foot (`madeLine`) and hold 29 rows a page, so a totals line never lands at the
  paper's edge. Tests: ReportPrintTest (the phone's page breaks), RegisterPrintTest; proofs
  screen-laptop-report-en-dark, -report-short-ur, -salesregister-en and the statement-proof pictures.
- **Part 7b, the other report and tool pages:** Sale Insights is in the page frame with the rest of the phone's
  screen: today's three figures, this month against last, the profit card (`Profit.thisMonth`/`lastMonth`: the
  figure with its minus, after expenses, last month's, the share of sales it stands on, a dash and what it needs
  when nothing is known), twelve months as bars that carry shape only with the chosen month's figure under them
  (the best first; a click chooses — the bars' English "L"/"k" are gone), top products, top profit and top
  customers ranked with bars, the profit note. **Top products count the counter's walk-ins** (`topProductsBetween`,
  shared, so the phone too; a customer's ticket is his khata entry's goods, counted once; SalesRuleTest). Cash flow
  and Follow-up are in the frame (cash flow's first short week on a red card, none on green; Follow-up's due/late
  line in red, not green). **Reminded today, on the computer:** `ReminderStore.openedToday` (the ladder's last
  opening is today) moves a customer to the foot and out of "to remind today", as the phone's ReminderLog does
  (ReminderStoreTest). The sales report's and the dues page's Excel ask a manager's PIN when refused
  (`Gates.need(EXPORT)`, it said "access denied"), are written in who did what, and say where the file went in a
  dialog (the note used to replace the page's line for good); the dues page says its total and each card wears its
  bar's colour (`FigureCard(swatch)`). Zakat follows the phone's order — the disclaimer above everything, the nisab,
  cash and stock, the ledger — with the phone's dark result card beside the questions on a wide window; the
  doubtful-debts label toggles its box. The calculator keeps its sum and the last three sums while the app is open
  (`CalcMemory`), and "Add to a khata" asks whose and which way, then opens that customer's entry filled in
  (`EntryEditor(startAmount)`; nothing above zero goes nowhere, `calc_khata_positive`). Proofs: screen-laptop-
  insights-en, -cashflow-ur, -followup-en, -aging-en, -zakat-en, -calculator-short-ur.
- **Part 8, Business Profile, card and poster:** the profile is in the phone's four parts with their own help —
  Identity (names, phone, email with the phone's "a shop email is better than your personal one", address, the
  logo, country, currency, calendar), Getting paid (bank, wallet, the payment QR), Invoice & statement (tax number,
  terms), Stamp & signature (it said "Business Profile" twice and titled cards "Bank name" and "Shop logo"). What is
  typed and not saved waits while the app is open (`ProfileDraft`, per business's settings): coming back finds it,
  said as "Unsaved changes" with Discard beside Save (the phone asks before leaving; the computer's pages are
  left by the rail, so the draft waits instead). A picture is deleted only after the owner is told no backup holds
  it (`pic_delete_body_pc`, nine languages). The card and the poster keep what was typed when the page is left
  (`DisposableEffect` + `rememberUpdatedState`: they saved only on a language or design click, a picture or a
  print); the card says the computer's words (`biz_card_note_pc`, `biz_card_save_picture`); the poster's Save sits
  above a preview that fits the height left (it was under a 711 dp picture); tick labels tick their boxes. A new
  business takes the open one's country, currency sign and calendar (`FileShopSettings.copyRegionFrom`, the phone's
  copyRegion; ShopRegionTest). The PDF preview scrolls sideways when zoomed past the window (it cut the page).
  Not changed, the owner's to decide: the poster ticks email by default and the card starts from the profile's
  email (the phone does both) — with a personal email in the profile it reaches posters and cards.
- **Part 9, Staff, who did what and daily copies:** Staff is in the page frame — the owner's PIN, counter mode and
  Add in the head (the counter-mode help is said once, in its window), the month's steps with chevrons that turn
  with the language (`MonthStep`; "‹ Prev" pointed the wrong way in Urdu), each person a card with their face and
  role, and the app's dialogs (add/edit, advance/salary, the day's mark, remove). A day is marked only within the
  open month (`DayPicker(earliest, latest)`: one picked outside it was written where the page never shows it). Who
  did what shows the phone's rows — the action, "by" whom and the detail in words through the shared `auditDetail`
  (now public; the computer showed "staff:12", "damage -3.00"); its check line is plain words on both platforms
  (`audit_seal_ok`/`_broken`: "N rows checked: none was changed or removed outside the app"; "seal" was jargon).
  Daily copies are in the frame and also list the chosen folder's own copies, restorable the same way
  (`snapshots_here_pc`, `auto_folder_copies`): a copy kept on the USB stick or in Drive could not be gone back to
  from here; the restore question and the notices are the app's dialogs. The PIN window names what it guards
  (`whatWord`: stock, void, return, discount, price, credit limit — all read "changing what is already written"); an
  owner-only page says the owner (`access_page_owner_only`). Products' Excel, labels and CSV import ask a manager's
  PIN instead of "access denied", the export is written in who did what, and an import is logged as an import (it
  read "Price changed"). Proofs: screen-laptop-staff-ur, -audit-en, -daily-en. Not changed: App Lock's PIN stays 4–8
  digits while the owner's counter PIN is 6–8 (the owner's to decide; App Lock's hash never leaves this computer).
- **Part 10, Live sync, From phone, Businesses, Help — and the last Material dialogs:** the Live sync window is in
  the app's frame: once the book syncs, where it stands comes first (the devices with when each was heard, the relay,
  held changes, conflicts) and the code to join or give after; Stop asks first (`live_stop_confirm`) and removing a
  device is the app's question; a conflict's fields and values read in the owner's words on both platforms
  (`ConflictWords`, shared: an amount as money, a day as a date, I gave / I got — it showed "timestamp 1761212345678").
  From phone and Report a problem are in the frame (the report shows everything it sends, the whole error, and says
  what Copy or Open mail did beside the buttons; a kind's words choose it too). Help is a page frame: Ctrl+F among the
  shortcuts, Report a problem as the one window (the page's second, weaker form is gone), the support address written
  out, the version. Businesses: each business a card (face, name, This one, the live badge, Open, rename, delete), the
  add box labelled and Enter adds; rename and delete are the app's dialogs, delete in the computer's words
  (`business_delete_confirm_body_pc`); a business with no name reads as the app's name in the owner's language.
  **No Material AlertDialog is left on Windows:** every notice and question is `TellDialog`/`AskDialog`, titled
  (several had no title); the restore question is `AppDialog` + `DialogFrame` (its own scroll with the bar, Merge at
  the start, Cancel, ✕ and Esc one answer, nothing answered while the restore runs); the shop's name, the statement's
  window (two date boxes, the phone's figures on a card, the last day by the calendar — `dayAfter`), the import
  preview, a photo, the merge, the printer choice (the one used last marked) are in the frame. Schemes, Seasons and
  Duplicates are page frames: Schemes has Add in the head and its form in the frame, the supplier chosen from a list
  (`ChoiceBox`, the date box's shape, + `PickDialog`; the whole list stood open in the form), a claim read as the
  phone reads it (the sum's size, 0 allowed), and its writes say a failure; a season's owing list opens the customer,
  as the phone's does; a merge moves the merged-away customer's face to the one kept (`Photos.moveOnMerge`, the
  phone's transferOnMerge). The bin deletes the pictures a purge leaves with no row pointing at them
  (`Photos.purgingOrphans`, the phone's BillPhoto rule, `partyPhotoPaths`): never one another business's book points
  at, nothing when another book cannot be read, and only files in the app's bills and faces folders (`Photos.ours`) —
  a path that came in a backup may point anywhere. About says this computer and its chosen folder
  (`about_privacy_body_pc`, AboutScreen's `privacyBody`), not this phone and Drive. Language, text size and theme
  are one pick list each with the one in use marked (`PickDialog`; `textSizes` draws each size in its own size). Enter
  runs a one-line FluentField's Done exactly once (`enterRunsDone`, the key taken before the box sees it): whether
  Compose's own Enter reaches onDone depends on the IME action a box names, and the PIN boxes named none.
- **After part 10 (the owner's answers, 25 Oct):** App Lock's PIN is 6 to 8 digits like the owner's counter PIN
  (`PinHash.validOwner`, `lock_pin_new_long`; a shorter one set before still opens — AppLockTest); the poster's
  email tick and the card's email stay as the phone has them. The phone's "goods received" form has the batch's
  expiry per line (`OrdersScreen(pickDay)`: the phone passes BrandDatePickerDialog; the shared editor hides the
  box without it) — a lot that came in through an order never reached Expiry (CounterReviewTest). **The phone's
  inspector document on the computer:** `scripts/gen_desktop_inspector.py` copies InspectorReport.kt to
  `desktop/.../invoice/` as the invoices are copied (Context.kt gained `Digits.string` and `BusinessProfile.taxLine`,
  PdfRtl `isRtl`; the strings job checks it with --check); the stock register's PDF button opens it in the app's
  preview (`StockPane(designs)`, `DocumentPrint`). InspectorReportTest draws it (proofs inspector-en, -ur).
  The register's short table stays for a page with no designs. Tests: ConflictWordsTest (shared), BinPhotosTest; proofs screen-laptop-help-en,
  -schemes-ur, -seasons-en, -duplicates-en.
- **Reminders, one rule on both:** `ReminderLadder` and `ReminderText` live in :shared (the phone keeps its
  ladder in preferences, `ReminderLadderAndroid`; the computer in `reminders_b<id>.properties` beside the
  books, `ReminderStore`, deleted with its business). The computer's Khata and Follow-up open a preview
  (`ReminderDialog`): the ladder's next step (Gentle / Plain / Serious, the owner may pick another), the words
  editable, the date the phone quotes (`reminderDateForParty`: a plan's next due day, else the harvest
  promise); a settled account is told "nothing outstanding"; no number, or no WhatsApp on the computer, is
  said (the button did nothing before). Only a reminder to someone who owes climbs a step.
- The audit's smaller points: the head counts customers and suppliers apart (`khataCountLine`); an empty book
  and a search with no match say so; the entry warning is titled for what it is (`confirmTitle`: twin entry or
  credit limit); an entry's day cannot be in the future (`DayPicker(latest)`, the editor clamps it).
  TileIconsTest now reads every glyph copied from the phone, not only the rail's pictures.
- Strings: khata_pick_party, khata_choose_several, customer_qr_save_picture, pos_still_due,
  pos_drop_held_confirm, cheque_none_cleared, cheque_none_bounced, no_products_pc, no_matching_products,
  inventory_subtitle, po_subtitle, po_copied_no_number, trace_came, invoice_whatsapp_pc, sig_hint_pc (nine languages). Tests: ReminderTextTest (shared),
  ReminderStoreTest, BookExportTest (the type column), LookContrastTest, TileIconsTest; proofs
  screen-laptop-khata-ur, screen-laptop-khata-en-dark, screen-laptop-side-khata-en.

### Marhala 5: the payment record, the cleanup, the documents (28–29 Oct 2026, kmp-desktop-16)

After the top-POS comparison (Doc "Roshan Khata — Top POS Systems se Muqabla", the owner: "jo jo
behtar hai implement karo"; agriculture not the focus). CI only — nothing on a real device.

- **Schema 39, backup 16 — the payment reference** (`paymentRef` on transactions and cashbook): a
  Reference box beside any way of paying but cash on both platforms' entry and cashbook forms
  (`PaymentRef`), said on the entry receipt, thermal receipt and statement (`PaymentMethod.said`);
  `PaymentMethod.WALLET` (country-neutral) with one label table (`wordKey`/`label`); a reminder quotes
  the sale it chases (`reminder_ref`, `newestGaveNumber`) and the computer appends how to pay too
  (`ReminderText.paymentBlock`, `ShopSettings` bank/wallet getters); Follow-up says "Reminded <day>,
  nothing received since" (`PaymentRequest.waitingSince`). Strings: payment_method_wallet,
  reminder_ref, followup_waiting.
- **The cleanup** (a 15,913-declaration scan): BusinessReport.kt, the shared EntryForm.kt, 19 unread DAO
  queries, 3 unused writes, RKA1 `sendOffer`, per-gram nisab helpers, 1.10.0 StatCard/MonoTile/GlyphChip/
  TotalCard and 108 strings went; `BLOCK_NEGATIVE_STOCK` became a real counter switch
  (`Counter.ShortStock`, `pos_short_stock`); six duplicates became one shared rule each (DayMath,
  DateWindow, WhenWords, `DayCloseMath.drawerByTheBooks`, `Format.moneyLtr`, the phone's balance side
  word), each with a test holding both platforms' old results equal. Kept on purpose: CustomerLink.setLook
  (the customer page), ImageAutoCrop (the phone's crop start), the gfx stand-in's surface.
- **Schema 40, backup 17 — documents** (`Documents.kt`, `NoteDocument.kt`): the invoice IS the
  document. `invoices.kind` (QUOTATION Q-, PROFORMA PI-, SALES_ORDER SO-, CHALLAN DC-, INVOICE INV-; each
  its own series, the INV- rule unchanged), `status` (OPEN/ACCEPTED/CONVERTED/CANCELLED, changed only in
  the DAO from the state that allows it), `sourceDocId` (indexed; merge re-points it, sync carries it as a
  uid). `convertDocument` copies the row and its lines into the next step (Q→SO→DC→INV, PI→INV), marks the
  source CONVERTED and immutable, audits (`DOC_CONVERTED/ACCEPTED/CANCELLED`). Credit and debit notes print
  from a return / a supplier return with no row of their own (print-only kinds). Every design heads itself
  by kind (`Labels.heading`); a challan prints no prices (`drawItemsTable(showPrices)`, the totals guard).
  `updateInvoiceWithItems` keeps the row's uid/kind/status (the phone's editor renamed the uid on every
  edit — lesson 35). **Screens:** the Invoices page is "Documents" on both (kind pills, status tags, Add
  asks the kind, a challan form hides money, "From Q-…" opens the source, Accept / Convert to … / Cancel
  by `DocActions`, converted/cancelled rows locked with `doc_locked`); Credit note on a return (counter)
  and Debit note on a supplier's goods entry (receipt). Strings: doc_kind_* (6), doc_status_* (4),
  doc_convert_to, doc_converted_from, doc_locked, documents_title, doc_add, doc_pick_kind, doc_accept,
  doc_cancel_confirm, audit_doc_* (3). Tests: Schema40Test, DocumentsTest, BackupCodecDesktopTest,
  InvoiceDesignsTest (quotation/challan proofs), ScreenProofTest (screen-laptop-documents-en/-ur).
- Not built, by decision (the Doc's "what not to build"): FBR/PRA integration, payment gateways,
  WhatsApp Cloud API, restaurant/salon modules, double-entry accounting, recurring invoices.

### The 30 Oct 2026 review (kmp-desktop-16 — the owner: "har angle se review karo, fix karo")

Five reviewers (data, speed, coupling, security, features) and two layout audits (143 XML layouts,
the phone's Compose screens); every point read in the code first. CI only — nothing on a device.

- **Layouts (lesson 58):** the cashbook's drawer line and the counter's total (the owner's phone);
  every shared Compose screen the phone shows at 360 dp (payment/refund chips scroll, three-across
  fields split, the sales report as cards on the phone); 13 XML layouts at 130–150% and in long
  languages (EntryChip and FilterSegment two lines, selection bars without caps). Windows'
  Documents head and tag; printed quotations/challans say DETAILS / No. (`pdf_label_doc_no`).
- **Documents:** a phone challan edit kept its lines' rates (it wrote 0 — critical);
  `updateInvoiceWithItems` gives every line a new uid and refuses a binned document; an advance
  reaches the invoice through the challan (`receivedUpChain`); a void cancels its ticket's invoice;
  a cancelled invoice no longer blocks a new one; delete only from OPEN/ACCEPTED in the DAO
  (`deleteDocument`: reopens the source, `DOC_DELETED`); merge marks the source converted.
- **Schema 41** (`Schema41`, indexes only): transactions (isDeleted, timestamp) and (entryNumber),
  cashbook (isDeleted, timestamp), invoices (isDeleted, kind, invoiceDate). Phone Home/Khata stop
  reading in the back stack; the expiring count is debounced.
- **Security:** the phone deletes only files in `files/bills` (`BillPhoto.delete`); Windows never
  reads a network photo path (`Photos.isNetworkPath`); the PIN strength comment corrected; real
  numbers, an email and the owner's supplier balances replaced with dummies in tests (git history
  keeps them).
- **One rule:** `PartyTotals` (get/give/net) for five hand-written copies.
- **Add product on the phone** (the owner, the same day: the counter found nothing in a new book, and a
  product could be made only by naming it on a bill or an entry): Products has a floating Add product
  (`fabAddProduct`, hidden on a helper's read-only phone) that opens `ProductDetailsDialog` on a blank
  product, the search box's words as its name. `KhataDao.addProduct` checks the name and barcode and
  writes in one transaction (`ProductAdd`: Added / NameTaken / BarcodeTaken); a deleted product of the
  name comes back keeping its id and uid. The form stays open over a missing or taken name. `no_products`
  names the button (nine languages). Windows still has no Add product (its Products page says so).
  Tests: ProductAddTest. CI only.
- **The phone's counter, from the owner's photos (same day):** the customer list scrolls in its own
  box (it stood 260 dp with no scroll, the names below unreachable), recent customers first, 50 shown,
  a search for the rest, a settled account shows no "Rs 0"; the chosen way of paying wears the chips'
  own fill (it wore "I gave"'s red); a line's tax reads "Tax %: 33" (it read "Tax % 33%"); Round off
  is a switch with what it moved (a chip reading "0"). CI only.
- **The phone's price list (same day, the owner's review):** Preview shows the pictures before they go
  (`makeFiles` draws them once; Share in the preview sends the very files shown; pages read back at
  ≥70% of the screen's width, RGB_565); each row says which price is which (`rate_type_cash/credit`, one
  figure when both are the same); the no-price note lists those products (`RateList.unpriced`, the
  count's own rule) and opens one in the product form, names only on a read-only phone; a search box
  from six products (name or company; Select all / Clear all act on what it shows; the picture is not
  narrowed); Preview and Share share the bar by weight, no capitals. Strings: rate_list_unpriced
  (rewritten), rate_list_unpriced_title. RateListTest. CI only.
- **Money forms full screen (same day, the owner's photos of Edit entry and Add bill):** a small card
  floated over the page and its pad scrolled with the form, the last row (0, 00, ., =) cut off below.
  `CalcPad.dock(dialog, content, pad)` after show(): the form and the pad go into one column in the
  form's place (the add-entry layout's own shape; AlertDialogLayout's panels are never touched — it
  gives up its own measuring when a fourth panel is added), the window fills the screen
  (`CalcPad.fullScreen`, page_bg, adjustResize). Edit entry, Add/Edit bill, Money in/out, Close the
  day, Cheque, Plan, Scheme claim, Staff pay and edit; a plan's instalment (the add-entry layout, its
  pad already docked) only `fullScreen`. Not changed: the invoice's options (a bottom sheet) and Zakat
  (a screen of its own). CI only.
- **Left for the owner's decision, with the reason:** a binned khata sale's invoice (binning is
  undone from the bin, cancelling is not); the owner PIN hash in plain backups (needed to restore
  on a new phone, lesson 40) and 120,000 rounds; BLOCK_NEGATIVE_STOCK on khata sales (a change in
  what the switch means); sync_log for a device silent 30 days; two devices converting one document
  between rounds (needs a conflict rule); moving the six phone-only screens out of :shared and
  RecycleBinScreen into :desktop (lesson 58; mechanical, best with a compile at hand).

### Every shop, every country (31 Oct 2026, kmp-desktop-16 — the owner: "agriculture specific nahi honi chahiye")

Two read-only audits (trade bias; the nine languages' countries), then the fixes. CI only.

- **No silent agri shop:** a book with no stored trade is asked for one on Home, after App Lock, before the tour
  (`MainActivity.askTradeIfMissing`; the first-run steps open Home as unlocked, so it is not asked on the way in);
  a helper's phone is not asked. `Trade.DEFAULT` (AGRI) is now reached only by old books' reads. The picker's
  order is `Trade.PICKER` (General, Kiryana, Wholesale first; agri ninth; Other last) — Windows' `Trades.KEYS`
  the same; the enum's order is unchanged.
- **Words every trade sees are neutral:** the counter's and orders' typed line take the trade's item hint
  (`CounterSaleScreen(itemHint)`, `OrdersScreen(itemHint)`, Windows `LocalTrade.itemHint()`); `item_name_hint`
  is "Item name" and the orders button "Add item"; the add-entry units list the trade's first
  (`TradeVocab.unitChoices`); cash flow says "Promised payment" (`cf_kind_promised`) unless the shop has seasons;
  Windows' product type label, plans' empty text, scheme hint and a pharmacy's inspection words follow the
  phone's rules. Invoice note, poster, pack help, bills and batch help have no spray, seed, bori or inspector.
- **Privacy:** the owner's shop name and town were the examples in nine languages (`business_name_hint`,
  `shop_name_hint`, `biz_card_ex_*`, `business_address_hint`): now "Al-Noor Traders", "Main Bazar". Test data
  still names them (not shown to users).
- **Countries:** fifteen added (Iraq, Jordan, Lebanon, Syria, Palestine, Yemen, Morocco, Algeria, Tunisia,
  Libya, Sudan, Mauritania, Nepal, Fiji, Tajikistan) with their currencies in `Currency.CHOICES` (JOD/TND/LYD 3
  decimals; IQD/LBP/SYP/YER none, as IRR) and the week from Wikipedia's "Workweek and weekend"; a blank
  `taxIdName` prints the language's "Tax No.". The profile's IBAN/mobile checks follow the country, not the
  "Rs" sign (lesson 29); their messages carry no Pakistani example; the QR and wallet texts name no
  JazzCash/EasyPaisa (the wallet box's help names the country's own, and follows a new choice). Afghanistan's
  solar months are Hamal…Hut (`ShopCalendar.monthNames(country)`, Wikipedia's Dari column).
- **The registers are every shop's** (the owner, the same day): the stock register and the sales &
  purchase register are on Products (phone) and in the rail (Windows) for all 25 trades. `RegisterStyle`
  (shared: AGRI, MEDICAL, PLAIN, from the trade key) decides only their words: AGRI and MEDICAL print
  "Stock & Compliance Register" with the label columns (MEDICAL's "Generic name"), the label checklist
  and the dealer's and inspector's signatures; PLAIN prints "Stock Register" with no label columns or
  checklist, signed by the owner and "Checked by". The app's own name for the page is "Stock register"
  everywhere (`inspector_title`). `InspectorReport.build/gather(style)` (the desktop copy too —
  gen_desktop_inspector.py imports RegisterStyle), `RegisterPrint(style)`, the phone's RegisterReport reads
  the trade itself. The product form's label fields stay agri/medical only (`TradeFeatures.inspectorReports`).
- **Not done, by decision or size:** lakh grouping (1,00,000) for India/Bangladesh (changes every figure);
  a calling-code box for OTHER (a profile field on both platforms and in the backup); a Tajik (Cyrillic)
  language; "IBAN" stays as printed (the international term); Nepal's Bikram Sambat calendar.

### The 31 Oct 2026 review: every shop, night and large text (kmp-desktop-16)

Five audits (trade words in 9 languages, trade switches, each trade's needs, dark mode, text size); every point
read in the code first. CI only — nothing on a device. Trade words: 25 trades x 4 lists x 9 languages, no gap.

- **Trades:** Windows' customer page showed Rabi/Kharif chips for every trade and country (SeasonBook dates an
  untagged entry itself) — now only with `LocalTrade.seasons`; the phone asked for label details after every
  bill, a question non-agri forms could never answer — only `TradeFeatures.inspectorReports`; Home's sixth daily
  tile is Expiry only where goods go off (`TradeFeatures.expiryDaily`), else Products (Expiry moves to Business,
  nothing hidden; the tour follows); Windows' promise tip and failure title, the scheme hint (seasons, not AGRI),
  the batch help and the products coach say no harvest/inspector/compliance to every shop; the stock register's
  no-expiry card and line warn only for AGRI/MEDICAL; maund and seer are offered only in PK/IN/BD/NP (keys kept);
  a restore's unknown trade key stays unset so Home asks (it became AGRI).
- **Large text:** the Khata totals and the Khata Report totals autosize on one line (`app:autoSizeTextType`,
  match_parent); Home's net balance shrinks to the card (`ui/FitOneLine`) — at 130-150% they broke after "Rs" and
  showed only that; Add Entry's amount row is at least 68 dp, not exactly; the erase-all, spray advice and price
  list controls call `TextFit.relax`.
- **Night:** brand_green (dark both ways) is a fill only; marks, outlines, chart bars and progress use
  brand_green_text / brandGreenText — the Khata filter's segments (1.69:1), the sales chart and 0-30 bucket
  (2.08:1), outline buttons and the editor box's focus edge, step dots and lines, plan progress; dialog buttons'
  words take brand_green_text (`DialogButton`'s textColor, `color/dialog_button_text`; 3.2:1 before — not colorPrimary,
  which fills buttons with white words: check_dark_colours.py refused it); Windows' `FillButton`
  writes dark ink on a light fill (`luminance() > 0.18`; Save on "I got" was white on light green, 1.82:1).
- **Not done:** the entry receipt shared or printed at night is the dark card (render() fills the activity's own
  views; a light copy needs it to bind into a given root); the per-trade features (service items, job cards,
  serials, price tiers — /topics/per-trade-needs-research in the owner's memory); a CI proof of every screen at
  360 dp, 150%/85% text and night.

### Automations (31 Oct 2026, kmp-desktop-16 — the owner: "automation rakh ke behtar tareeqe se manage karein")

Five, in :shared (`Automations`), said at the owner's hours on both platforms; they TELL and open the place to act —
none sends a message or writes a row on its own (a background write on two synced devices makes every draft twice;
WhatsApp sending needs the paid Cloud API, decided against). CI only — nothing on a real device.

- **Morning collection** (9:00): Follow-up's own rule (`FollowUpRank.remindToday`, with a phone, less those reminded
  today) as a count and total; the tap opens Follow-up and starts its one-by-one queue (`EXTRA_START_QUEUE`).
- **Low stock orders** (10:00): `Automations.reorder` — `Valuation.low`, less what an order not yet received carries;
  quantity = max(30 days' sales of that name and unit, twice the low level) less the shelf, at least 1; supplier from
  the newest bill that carried it. "Order the low stock" on both platforms uses it and `firstOrder` (one supplier per
  draft; the rest stay low for the next tap). The tap opens a ready draft (`OrdersScreen(startLow)`, `OrdersPane`).
- **Expiry action** (10:00): the soonest batch by name, what is left and its day (`Fefo.expiring`); opens Expiry.
- **Monthly report** (the 1st, 9:00): last month's gave/got (`lastMonth`, inclusive end); opens the Khata Report on
  that window (`LedgerReportActivity.EXTRA_FROM/TO`, `ReportPane(startWindow)`).
- **Closing summary** (21:00): today's sales, payments and the drawer by the books; opens Close the day
  (`CashbookActivity.EXTRA_CLOSE_DAY`; Windows opens the Cashbook).
- `Automations.due`: on, the hour reached, once a day (the month's once a month) — late, never twice; `noticesDue`
  marks a quiet one said too. Settings per device: the phone's `AndroidAutomationSettings` (prefs "automations",
  More → Automations, `AutomationsDialog`), Windows' `DesktopAutomationSettings` (app.properties, More → Automations).
- Phone: `AutomationWorker`, hourly periodic work, its own channel; a helper's read-only phone says nothing; the tap
  goes through the gate (`MainActivity.EXTRA_OPEN_ARGS` carries the screen's extras through Gate and Lock). Windows:
  Book's loop every 5 minutes while the book is open, as tray notifications; a click on the tray brings the window
  forward on the page (`Reminders.show(open)`). Only the open business's book.
- Strings (nine languages): auto_title, auto_help, auto_*_name/_title, auto_collect_body, auto_reorder_body,
  auto_expiry_more (plurals), auto_dayend_body/_uncounted, auto_month_body, auto_expiry_body, auto_at_time,
  auto_pick_hour. Tests: AutomationsClockTest (shared), AutomationsBookTest (desktop).

### First-open sheets and the saved card (1 Nov 2026, kmp-desktop-16 — the owner: "professionally check karo, implement karo")

Phone only. CI only — nothing on a real device.

- **The saved card** (`ui/SavedCard`, `view_saved_card.xml`, the owner's mockup "A"): after an entry is saved on a
  customer's screen, one card above I gave / I got — the gold-ringed tick drawing itself (`avd_save_check`; a plain
  tick with animations off), "Saved", the line "I Gave · Rs 600 · RK-000006", Undo and, for a customer with a number
  and the share setting on, Send on WhatsApp. It replaces the green disc in the middle of the screen
  (`Motion.savedTick` and `view_save_tick.xml` are gone) and the separate "send this?" bar (`offerEntryShare`).
  Undo bins the entry (the delete path: `softDeleteEntry` + ENTRY_DELETED, AccessUi's PIN where deleting asks one) and
  puts the promised day back; the bin's own Undo bar follows. A paid-now sale's card keeps its old Undo (only the cash
  "I got"). The entry wears "New" (`bg_entry_new`, `tvNewTag`, `EntryAdapter.markNew`) for 4.5 s and is scrolled to.
  Entry edit's signature and Staff's payment say "Saved" on the same card, without actions. Above 120% text the
  actions go under the words (`TextFit.stackWhenLarge`). No balance "+Rs" chip: on a supplier the balance falls when
  "I gave" is written, and a "+" beside it read wrong.
- **First-open sheets** (`ui/FeatureIntro`, `sheet_feature_intro.xml`, `item_intro_step.xml`): three numbered pictures
  (the app's own tile drawings; two-tone tiles untinted, glyphs in brand_green_text), the feature's name, one line on
  what it does for the shop, Got it. Eleven: Counter sale, Documents, Cashbook, Cheques, Customer QR (before the card
  opens), Staff, Plans, Follow-up, Backup, Purchase orders, Expiry. Once per feature per phone (prefs
  "feature_intro"); a rotation while open shows it again; not when a notification opened the screen to act
  (EXTRA_CLOSE_DAY, EXTRA_START_QUEUE, EXTRA_START_LOW). Help → Feature guides lists them (ChoiceSheet). Left out on
  purpose: screens that explain themselves or have the Home tour (Khata, Products, Calculator, Zakat, Price list,
  cards, posters, reports, bin, Automations whose dialog has its own help). **A test that photographs screens calls
  `FeatureIntro.markAllSeen`** (ScreenProofPhoneTest, StoreScreenshotsTest); EveryScreenOpensTest keeps them on, so
  every sheet is opened and rotated there. Proofs: 29-intro-cheques, 30-saved-card.

### The customer statement, redesigned (2 Nov 2026, kmp-desktop-16 — the owner's review: "professional karke implement karo")

Phone only (`data/PdfExport.buildStatement`); Windows' StatementPrint is its own code and unchanged. CI only.

- The band: the shop's name, its address and phone (`BusinessProfile`), a gold rule; on the far side "ACCOUNT
  STATEMENT" and "Made on <day>" (the corner had repeated the period). "Statement for": the customer, phone and the
  photo when that setting is on; the period and entry count (and page n of m) on the right — said once.
- Four boxes: Opening balance, Total taken, Total paid, Still to pay (green, the figure the customer looks for;
  You will receive / Settled when it runs the other way). The columns are "Taken" / "Paid" for a customer — the
  rows are dealings, not what is owed, and "You Owe (-)" / "You Paid (+)" read against an unsigned balance; a
  supplier's stay "Paid to you" / "You supplied". Heads on a green bar, every other row washed, a dash in the empty
  column. `pdf_stmt_you_owe`/`_you_paid` stay: Windows and ReceiptBalance use them.
- Under what is owed: "Please pay by <day>" when a promised day (`payBy`, Party.harvestPromise) is today or later;
  a How to pay box (bank, IBAN, wallet from the profile, the newest sale's number to quote) on a customer's
  statement that owes; "call us on <shop phone>" if anything does not match. The QR and signature stay as they were,
  and the foot line with the DOWNLOAD link stays (the app's publicity, the owner).
- Strings (nine languages): pdf_stmt_doc_title, _made_on, _for, _period_label, _taken, _paid, _total_taken,
  _total_paid, _still_to_pay, _settled_short, _pay_by, _how_to_pay, _bank, _wallet, _mention_ref, _query.
  Proof: 31-statement (ScreenProofPhoneTest, drawn with the app's context, so in English in every look).
- **The second pass, the same day (the owner's photo of a real statement beside the mockup):** How to pay, the shop's
  address/phone and the call-us line print only when the Business Profile holds them, and his was empty — so the
  sheet still never prints a placeholder, but the ready dialog says what is missing (`PdfExport.gapsOf`,
  `PdfShare.presentStatement`: the way to pay only on a customer's statement that owes; nothing on a helper's
  phone) and "Add details" takes Close's place, opening the profile at the payment or phone box. The bar says
  "Amount you still have to pay" on a customer's statement (`pdf_stmt_amount_still_to_pay`), the first column
  "Date & details" (`pdf_stmt_date_details`), the father's name and village sit under the customer's name
  (`PdfExport.identityOf`, the customer page's own line), and every page says "<shop> · Page n of m" above the
  foot (`endPage`; the count line no longer carries it). "Total taken" stays (the mockup's "Goods taken" is wrong
  for cash lent). The closing bar, QR and signature break a page earlier (190/210/180 from the foot): on a full
  page the call-us line, the QR and the signature's name ran into the foot. The proof fills a made-up profile
  for the drawing and puts it back; its first picture showed the IBAN cut to "PK00TEST000…", so the IBAN has its own
  line, account and wallet numbers shrink instead of being cut, and the How to pay box grows to its lines. Strings: pdf_stmt_amount_still_to_pay, _date_details, _gap_title, _gap_pay,
  _gap_phone, _gap_address, _gap_fix.

### Products & stock: the list gets the screen (2 Nov 2026, kmp-desktop-16 — the owner's photos at 150% text)

Phone only. The two register cards, the tie button and Add product stood fixed under the list, leaving room
for one product with the button over it. Now (`activity_products.xml`) the band with the search stays, and the
figures, the registers (one card, two rows: `btnInspectorReport`, `btnRegisterReport` are the rows) and the note
with its tie button inside it (`btnLinkGoods`, a text button under "use the button below") sit in an
AppBarLayout over the list (`scroll|enterAlways`): scrolled away as the list goes up, back as it comes down.
Add product is in the CoordinatorLayout's corner and shrinks to its "+" while the list scrolls down
(`ProductsActivity`, `shrink()`/`extend()`); the list keeps 88 dp under its last row. Nothing removed, no
strings. CI only. The first push left the band at `0dp` wide and the screen blank (lesson 63).

### Close the day: the first count and the figures as a table (2 Nov 2026, kmp-desktop-16 — the owner's screenshot from the 21:00 automation)

- **The rule (both platforms, Home/Cashbook on Windows and the automation too):** a drawer never counted ran from
  the book's first entry (`from = 0`), so the first close added every cash receipt ever written ("Khata cash
  received Rs 1,652") against a blank "at the last count" box. Now `DayCloseMath.drawerByTheBooks` starts a
  never-counted drawer at today's working day (`firstCountFrom`: the start of `businessDayOf(now)`, minus 1 ms for
  the DAO's `from < t`), and `DrawerFigures.from` says where it started. DrawerByTheBooksTest holds it (three-day-old
  cash left out). After the first count nothing changes: the stretch runs from the last count's moment.
- **The phone's dialog** (`dialog_day_close.xml`, `CashbookActivity.showDayClose`): a line on top says the stretch
  ("Since the last count, <day and time>" / "First count: today's cash, from the start of the day"); the first box
  reads "Cash in the drawer when the day started" when there was no count; the five sums are rows
  (`breakdownRows`: each line of `dayclose_breakdown` split at its own %n$s, words at the start, the figure at the
  end, the total bold under a rule — any language's order reads right); the difference shows only once a count is
  typed, green when it matches, red short, amber over; the pad starts in "Cash counted now" when the opening is the
  last count's figure. Windows' dialog has the same top line and first-box words. Strings (nine languages):
  dayclose_opening_first, dayclose_since_last, dayclose_since_first. CI only.

### The Payment Radar (2 Nov 2026, kmp-desktop-16 — the owner: "a blockbuster, not a generic automation; worldwide, every business")

How each customer usually pays, worked out on the device from the shop's own khata — no server, no AI model, no
network, no cost — said with its reasons. Both platforms; CI only.

- **The rule** (`PaymentRadar`, shared; PaymentRadarTest): each "I gave" is a lot, each "I got" pays the oldest
  lots first (FIFO, money paid ahead taken by the next credit); a lot cleared within the hour is a paid-now sale,
  not credit. A customer's pace is the median days of his last 12 repaid lots (from 3), judged against **this
  shop's own** median over every customer (`shopTypicalDays`, from 8 lots; else 30 days; never under 7) — a
  grocer's week and a wholesaler's two months both read right. Light: GREEN at or under 1.5× the norm, AMBER
  over 1.5×, RED over 2.5×; an amount unpaid over max(2× norm, 30 days) is at least AMBER, over max(4×, 90) RED;
  a promised day passed with money owed at least AMBER; NONE (nothing shown) with too little history. The next
  payment: a day of the month he keeps to (three of four of his last six payments within two days of one day,
  over three months; the month's end and start are one habit), else his usual gap from his last payment.
  `KhataDao.radarRows` (read only, four columns) feeds the shop's norm, cached ten minutes per book.
- **Completed the same night (the owner: "jo reh gaya complete karo"):**
  - *One rhythm with Follow-up:* the gap-based next payment and "behind" are `PaymentHabit`'s own (a customer
    Follow-up calls late is at least AMBER); rows are `LedgerPoint` (the old `RadarRow` is gone).
  - *Promise history, no schema change:* every new promised day goes through `KhataDao.promiseDay` (phone's
    saveHarvestPromise, Windows' DueDialog and entry save, the counter's ticket) and is also an audit row
    (`AuditAction.PROMISE_SET`, refId the party, detail the day's millis; "Promised day set" in who did what);
    `promiseLog` reads them, `PromiseLog.of` turns them into promises; kept = money in by two days after the day,
    a promise replaced before its day passed is "moved", not broken. Two broken and more broken than kept is
    AMBER, three broken and none kept RED. Per device, as the audit log is.
  - *The morning list:* `PaymentRadar.likelyNow` (owing, inside his usual paying days) — `FollowUpRank.order`
    puts them after promises come due and `remindToday` includes them; Follow-up rows say "Usually pays around
    now" (both platforms); the 09:00 notice adds "Usually pay around now: N" (`Automations.collect(radar)`,
    `noticesDue(radar)` from each device's switch).
  - *Which tone works:* `ReminderLadder.State.worked` (a fifth stored field; four-field states read as before):
    a payment after a reminder, within the chase's 60 days, teaches that step; a new chase starts at it, and the
    Radar's reasons say "Reminder that worked last: Plain". ReminderLadderTest and ReminderStoreTest follow.
- **Phone** (`ui/RadarUi`): a chip under the customer's name (`tvRadar`: the dot in the ledger's got/gold/gave
  text colours on the season chip's gold, and "Likely 8 Nov – 12 Nov", else the feature's name), never the
  judgement on the face — the customer may be looking across the counter; a tap opens the reasons and Turn off.
  On a new "I gave" the form's band shows the dot and the name only (`tvEntryRadar`). More → Payment Radar is the
  switch (prefs "payment_radar", on by default, per phone). A helper's read-only phone shows none of it. The
  first chip opens its FeatureIntro sheet (`Topic.RADAR`).
- **Windows** (`Radar.kt`): `RadarChip` in the account's chip row, `RadarDialog` with the reasons and Turn off,
  More → Payment Radar (`RadarSettingsDialog`, app.properties `radar_on`, `RadarDesk.attach`).
- Strings (nine languages): radar_title, _on_time, _late, _very_late, _new, _typical, _shop_typical, _oldest,
  _pay_day, _next, _next_short, _promise_passed, _private, _switch_help, _turn_on, _turn_off, intro_radar_body,
  _s1-_s3; plurals radar_days.

### The 3 Nov 2026 audit (kmp-desktop-16 — the owner: "data safety sab se pehle, facts aur figures pehle")

Eight reviewers (data, money, alignment, notifications, stability, release, tests, screens) and the owner's brief;
every point read in the code first; the report is the Claude Doc "Roshan Khata — Poora Audit (Nov 2026)". 3
critical, 48 medium, 52 low. CI only — nothing on a real device; docs/LESSONS.md carries what it taught.

- **Critical (27688b8):** a backup taken after one half of a cash sale was purged could not be restored (the other
  half pointed at nothing): the parser drops dangling `pairedEntryId`s and every purge clears them
  (`clearDanglingPairs`). A paid-now sale of unpriced goods wrote no payment (`RateType.forEntry`, one rule on both,
  `insertEntryWithItemsPaidNow` in one transaction). App Lock shut out every PIN-only Android 7-10 phone
  (`noHistory` lost the device-credential answer; the lock re-asks on return, turning it on needs one success).
- **Medium (6a26212, 459511e):** money compared through `Money` with an epsilon per currency (`LineMath.scale()` =
  `Currency.decimals()`), no E-notation, the statement's period-end balance, an invoice's balance due with its minus;
  writes made one transaction or audited (`restorePartyWithEntries`, `insertCashEntryAudited`, `deleteEntryAudited`);
  returns by `ReturnMath.totalFor`; the drawer counts bills and staff paid in cash (`DrawerFigures.otherOut`, the
  breakdown's 6th line); notices private on the lock screen, timed out, per shop, a notifications-off warning; the
  Radar nets a cash sale's halves and leaves Qarz-e-Hasna out; CSV import reads any currency sign and Dr/Cr and
  counts unreadable balances; the add-entry pad is 40% of the screen (`CalcPad.fitHeight`, 278-330 dp) with a fade
  at the form's foot; decimal boxes take the language's mark and every keyboard's digits (`DecimalBoxes`, run by
  `TextFit.relax` and `FormBox`; `Digits.parse` reads Indonesian "2,5" and "4.000" as the pad does); an edit of one
  half of a cash sale asks about the other (`pairToFollow`, `setPairAmount`) and the bin brings a binned other half
  back with it (`restoreEntryWithPair`, `binnedPairOf`, said first on the phone).
- **Low:** daily-copy restore renames without a gap; the newest day close is never a future one; a bill's entry on
  the bill's day; merges audited (`PARTY_MERGED`); the bell's cash-flow gap counts promises; workers `UPDATE`; a
  deleted bill's own entry is out of the bin (`observeDeletedEntries`, `restoreEntry`); the counter receipt keeps the
  round-off's minus and the balance's side (`ReceiptBalance.text`); the Radar only for REPORTS (owner, manager);
  names sort past a leading bracket or emoji (`NameOrder.key`); Windows' bell counts and lists the late payers as Home
  does; crash records and errors.log clean every message (`LogText`, shared; the phone's `SyncLog.clean` calls it);
  the stock register's date and quantity wrap; `scripts/check_16kb.py` in build.yml.
- **Left, with the reason (the report has each):** M02 Drive auto-backup stays opt-in (offered on connecting;
  `allowBackup=false` on purpose); M39 proof looks on Android 15, 200% text and Windows dark dialogs; M47 the Downloads
  backup's PIN hashes (the owner's decision: a password backup); M48 Play's Data safety form must declare what ML Kit
  sends (the owner, in Play Console) and the listing's "never sent anywhere" line; L36 the checkbox/switch colour
  (3.18:1, a theme-wide look change); L38 Home's pill and slide at 150%; L15 status-guarded writes for invoice design,
  party dialog, order status and cheque; L16 the stock transfer's two books; L20 FCM priority (a Worker deploy);
  L21, L35, L39, L41, L43, L44, L46-L49, L52 as the report says.

### The speed work (3 Nov 2026, kmp-desktop-16 — the owner: "data bohat zyada ho jaye to quick response, koi data loss na ho")

Measured first, then fixed only what the numbers showed; no stored data and no backup format changed. CI only —
not on a real phone or the owner's laptop.

- **The measurement (both platforms, one list):** `BigBookSeed` (shared; the phone's `BigBook` calls it) writes a
  wholesaler's book — 10,000 customers × 30 lines (one of 5,000), 300 products, goods on a third of the sales, two
  bills per supplier with batches, 20,000 cashbook rows, 20,000 walk-in tickets — **by the clock, as a shop writes
  it**, each customer's run ending by now; every seeded row found again by its mark, so `clear` takes it all out;
  refused for a book that syncs. `BigBookProbe` (shared) times every heavy read the screens make (Khata, customer,
  Home, bell, Follow-up, Radar, reports, stock and its six sums, counter, the reads after a save). Windows:
  `BigBookDesktopPerfTest` in a temporary folder, then the whole book backed up, restored into a second book and
  compared row by row (`LiveSync.fingerprint`); numbers to build/perf, one annotation in desktop.yml; test heap 1 GB
  (a 4 GB laptop's). Phone: `BigBookPerfTest.everyHeavyReadIsTimed` and `aSavedEntryReachesTheCustomerScreen`.
- **Schema 42** (`Schema42`, both migrations, Schema42Test, `SchemaRewind.undo42`): transactions (partyId,
  isDeleted, timestamp, isGiven, amount, pairedEntryId, isQarzeHasna) replaces (partyId, isDeleted, timestamp):
  every per-party sum is read off the index. Live sync needs both devices at 42.
- **Big answers a slice at a time** (`LedgerLines`: `ledgerPointsOf`, `liveCustomerIds`, `agingMovesOf`, 400
  ids a read; `KhataSales.rowsBetween`, a fortnight a read): Android hands an answer over 2 MB at a time and runs the
  query again from its first row for each 2 MB — 275,000 owing lines took 10.8 s on the emulator against 0.67 s on
  the computer. `observeOwingLedgerPoints`, `radarRows` and `agingMoves` are gone; LedgerLinesTest holds each slice
  read to the old single query as written.
- The day's safety copy starts 4 s after the Khata list's first rows (`KhataActivity.startDailyCopy`, AppScope): it holds
  the book's one write connection while the file is copied, and Room's first read of the list waited for it (first row
  3.8 s instead of 1.9 s on the emulator's big book, the day's first open).
  The search keys (`NameSearch.Key`, one per name) are folded after the first rows are drawn, not before.
- Stock's khata sums read the goods lines first (CROSS JOIN, `+ei.productId`); Windows Follow-up works out habits
  and order on Dispatchers.Default (the phone already did).
- **Windows runner, before → after:** every party's balance 834 → 197 ms; owing lines 666 → 82; bell 1,214 → 517;
  Dues by age 1,074 → 411; stock 289 → 186; the reads after a save 2,989 → 928; restore heap 327 → 215 MB; the
  restored big book the same row by row. (The "before" seed had 70,000 lines at this moment, so day and month
  windows are not compared.)
- **Phone (CI emulator), before:** owing lines 10.8 s, Radar pace 4.8 s, Dues by age 7.3 s, a year's sales report
  8.8 s, the reads after a save 13.8 s, the Khata list's first row 2.3 s (its 2 s budget failed). After: the
  on-device run's "Big book timings" annotation.
- **Left, with the reason:** reading a 197 MB backup holds it in memory — 141 of the emulator's 192 MB before the
  fixes; a streamed restore is the real fix (it touches the restore core, a task of its own); the Khata Report's
  "All" lists every entry of the book; the bell is worked out again on every page switch on Windows (background).
- The purchase order form (the owner's photo, same day): the supplier list has its heading and scrolls, and Save
  says why it waits (`po_pick_supplier`, `po_no_suppliers`), phone and Windows.

### The animations (3 Nov 2026, kmp-desktop-16 — the owner: "professional animation, mockup pehle", then "implement all animations carefully")

Nine moments, from the owner's approved mockups (the animations design canvas), on the phone and,
where the moment exists, on Windows. Rules kept everywhere: each is feedback for something that really happened
(a tick never comes before its work; dots move only when changes moved); Material's own curves and lengths
(`MotionKit` on both: emphasized decelerate 0.05,0.7,0.1,1; standard 0.2,0,0,1; 50–600 ms); nothing bounces; with the
phone's animations off (`Motion.enabled`) the end state is drawn at once; figures never count up. CI only — nothing
was seen on a real phone or the laptop.

- **Follow-up's Message** (`SendPill`, `Motion.paperPlane`, `PlaneFlight` in MotionShapes, the faceted
  `ic_paper_plane_gold`): the plane crouches, is thrown out of the pill and fades (110/280/150 ms); the pill reads
  "Opened today" with a drawn check (`avd_pill_check`) only once WhatsApp or WhatsApp Business opened
  (`Reminder.sendViaWhatsApp`'s `opened` callback; never a browser — see the review below); the row glides to the foot after the
  owner comes back (`moveDuration` 320, payload `MARKS`), not under the flying plane; `Motion.settleFlights` in
  BaseActivity.onResume ends a flight left behind.
- **Counter sale saved** (`SaleSavedSheet`, `sheet_sale_saved`; Windows `SavedSaleNote` with `DrawnCheck`): the gold
  ringed tick draws itself, the number and receipt rise, the change to hand back arrives last on its gold chip.
- **Back up now** (`BackupButtonMotion`, `ProgressRing`; Windows `BackupChip(progress, done)`, `BackupRing`): the
  ring fills with the real share written (`BackupCodec.exportTo(progress)` over `KhataDao.backupStreamedRows`),
  "Creating backup… 42%", then a disc and check; taps are not taken meanwhile (not disabled, lesson 10).
- **Restore** (`ui/RestoreSteps`, `StepMark`, `dialog_restore_steps`; Windows `RestoreRun`/`RestoreStepsDialog`):
  the steps in their real order — backup checked (before the question), this book kept in Daily copies, replaced,
  and photos (Drive) or live sync joined (a phone taking a shop's book, `LiveReceive.take(step)`; a Windows pairing)
  — each ticked when its call returns; a failure crosses its own step and says what it means (`restore_not_done`;
  `restore_join_failed` when the book was replaced but the join failed); the end says the bin and photo notes and
  "Open the book" (goHome). The several-shops Drive restore ticks one row per shop. `BackupCodec.ParsedBackup`
  carries `exportedAt` ("Backup of …"). `drive_restored_these` went (the ticks say it).
- **Cheques** (`ChequeAdapter.hold/stamp/releaseNow`, item_cheque's `chequeStamp`; Windows `ChequesPane.stamped`,
  `ChequeStamp`): the write happens at once; the list is held while CLEARED / BOUNCED / CANCELLED lands on the row
  (180 ms from 1.5×, an echo of its edge, the status word turns), then the row moves on (phone: DiffUtil with stable
  ids glides it below the pending ones; Windows: `animateItem` fades it out of its tab). Nothing written (another
  device settled it): released at once, no stamp.
- **Close the day** (`ResultMark`, `dayCloseResult`; Windows `DrawerResult`): matches / short / more than the books
  on its own wash with a drawn check, minus or plus — redrawn only when the answer changes kind, not per digit.
- **Documents** (`data/DocChain` shared + DocChainTest; phone `DocConvertedSheet`, `DocChainMarks`; Windows
  `DocChainStrip` on the open document): the real chain by `sourceDocId` (a sales order written fresh starts at
  itself), then the steps still to come; a convert plays the line, the old step's check and the new step's arrival.
  On the phone the sheet's Preview opens the PDF the convert used to open at once.
- **Live sync** (`LiveRunner.carried` / `LiveDesk.traffic` — `LiveCloud.Result.Done.carried`/`wrote`, the WiFi
  parcel's own `c` count; phone `LiveTrafficView` on the Backup card, Windows `LiveRoads` in the window): two roads,
  the one in use green; three gold dots only when a round carried changes, out or in; no road answering — dashes,
  a red cross on the cloud and "N changes waiting to go" (`live_changes_waiting`, `LiveSync.State.waiting`).
- **Statement PDF** (`PdfMaking`, `PageStack`, `dialog_pdf_making`; `PdfExport.buildStatement(onPage)`): nothing for
  a statement made within 0.4 s; else "Preparing statement…" with a page joining the stack as each is really
  finished ("Page 2 of 5"), a check, then the usual ready dialog. Windows' statements already preview page by page.
- Strings (nine languages): restore_backup_of, restore_step_checked/_kept/_kept_why/_replaced/_photos/_joined,
  restore_keep_open, restore_open_book, restore_not_done, restore_join_failed; plural live_changes_waiting.
- **The review (the same day, two reviewers, every point read in the code first; 0 critical, 3 medium, 13 low and 2
  smaller; no data path writes differently):** a restore that replaced the book and then failed to join live sync says
  "Restored" (both platforms; Windows reads the new book — shop name, pages); the phone's "taken" flow likewise when
  only the rounds' restart failed. A reminder counts only when WhatsApp itself opened: wa.me with
  `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` on Android 11+, the one WhatsApp by package before it, then `whatsapp://send`
  (a browser took the link on a phone without WhatsApp and it counted); with both WhatsApps a chooser the owner
  closes still counts (Android does not tell). The four new dialogs go with their screen and let it go when closed
  (`ui/DialogLife`; a counter kept one sale sheet per sale). Also: Windows' convert chain plays when the new document
  arrives and only once (`justMade` cleared by the owner's click, then after 600 ms), a converted document's chain
  shows the step made from it (`KhataDao.newestMadeFrom`) and a cancelled one no step after it, the counter's change
  note survives an item scanned while the sale was written, one backup at a time from every button, the WiFi "in"
  dots only when rows were written (`LiveSync.changes`), every relay's counts summed, an `Error` mid-restore still
  ends the steps; on the phone: "Opened today" keeps only today's, the live card reads once per newest status and
  shows "changes waiting" when no road carries (as Windows), the restore heading stays above the scrolling steps,
  dismiss at once on the screen's end, the wash change stops before a first show, the chain's numbers fit on
  Android 7-8 (AppCompatTextView), the statement's pages mirror in RTL. The proofs call the screens' own builders
  (`RestoreSteps.counts`, `DocConvertedSheet.line`, `restoreCounts`). DocChainTest +2.

### The competitor study, built (3–4 Nov 2026, kmp-desktop-16 — the owner: "jo jo cheezein aapko sahi lagti hain professionally unko implement kar do")

The study of other ledger and counter apps (the Claude Doc "Roshan Khata — Competitors se kya seekhein", rev 14), every
point read in the code first: what the app already had was not built again (a customer's lifetime figure is ⋮ → Report
on "All"; sales by way of paying is the sales report's own view). CI only — nothing on a real device.

- **Price check** (`ProductFind`, shared): a product by name, second name, its barcode whole or 4+ of its digits, or its
  note — the product's note is now its place on the shelf (`product_note_label`, both platforms). The phone's Products
  scans (`btnProductScan`) and shows a price sheet (`PriceCheckSheet`: cash and credit price, stock, place, Who bought,
  Edit); a code no product has offers Add product with it.
- **Return slip** (`ReturnReceiptText`, shared): a return prints on the receipt printer on both platforms, a cash refund
  opening the drawer. The Windows sale receipt's round-off keeps its minus and its balance its side (they did not).
- **WhatsApp after a convert** (phone): the converted document's sheet has Send (`DocConvertedSheet(onSend)`).
- **The phone's sales report to Excel** (EXPORT, audited; `SalesReportScreen(onRows)`).
- **Day book** (`DayBook`, shared `DayBookScreen`, phone `DayBookActivity` in place of the Z dialog; Windows' day report
  lists the same rows and prints them): the day report's figures over every money movement of the day in time order —
  khata entries, cashbook rows, bills paid in cash on delivery, staff paid (`staffPaymentsBetween`) — any day stepped back to.
- **Void and correct** (`CounterDraft.reRing`): a voided ticket's lines come back on an empty counter as a new ticket (no
  held id, no payments); never onto a ticket being rung up.
- **Customers who stopped coming** (`Lapsed`, `KhataDao.buyingHabits`): 3+ buying days, gone past twice the usual gap
  and at least 30 days, not over two years; biggest buyers first; Sale Insights on both (phone `LapsedActivity`).
- **Exchange** (`CounterDraft.startExchange`): after a return, the empty counter for the same customer; a cash refund
  stays as the new ticket's first cash payment, its reference the return's number. Two documents still.
- **WhatsApp order paste** (`OrderPaste`): a copied order message opens a new sales order, a line per item (the shop's
  product and price where the words name one, the customer's words where none does; digits in any script, count words,
  the app's unit words; greetings left out). Nothing is saved until Save.
- **Schema 43, backup 18** (`Schema43`): `parties.note` — the owner's private note (`PartyNote`: 500 characters, kept whole
  by a restore, joined on a merge; never on a statement, a reminder, a receipt or the customer's card; the customer's page
  shows only a chip, its words on a tap, as the Radar's) — and `cashbook.photoPath`, `cheques.photoPath`: a receipt's and
  a cheque's picture by a bill photo's rules (the bills folder; `SyncSchema` local, so a picture set writes nothing to
  the sync's log; the Drive image archive packs and re-points them; a picture goes once no row of any shop points at it,
  `BillPhoto`/`Photos.purgingOrphans`; a helper's copy carries no cheque picture — the payer's account and signature).
  A new money row's or cheque's form takes the picture (phone `BillScanFlow`: scanner, camera, gallery; Windows
  `FormPhoto`, copied in only when the row is written); a written row changes it from its menu (EDIT_BOOK). Live sync
  needs both devices at 43.
- **Store listing** (store/listing): the privacy line in seven languages; English and Urdu rewritten for the next upload
  (Data safety must declare ML Kit and FCM's "Device or other IDs").
- Not built: Pashto (no checker to verify the words).
- Strings (nine languages): product_note_*, pos_void_correct/_corrected/_till_busy, lapsed_*, pos_exchange_*,
  order_paste_*, party_note_label/_help, attach_photo, photo_attached, view_photo, cheque_photo, cash_photo,
  photo_missing. Tests: ProductFindTest, ReturnReceiptTextTest, DayBookTest, VoidCorrectTest, LapsedTest,
  OrderPasteTest, PartyNoteTest, NoteAndPhotosTest, Schema43Test, BinPhotosTest; proofs 36-price-check, 37-day-book,
  38-lapsed (03-customer, 11-cashbook and 12-cheques now show the note's chip and the cameras).

### Finding the invoice (4 Nov 2026, kmp-desktop-16 — the owner: "invoice banane wala koi option nazar nahi aa raha")

Phone only; Windows unchanged. The owner, looking at his own Home, could not
find where to make an invoice: the 29 Oct merge had put it under a tile named
"Documents", and Counter sale wore the same `ic_tile_invoice`, so the two read
as one feature. The tile is now labelled `coach_title_invoice` ("Invoice",
already in all nine languages); Counter sale has its own `ic_tile_counter`
(a cash register, fi_green, in FeatureIntro's TWO_TONE set). On the Documents
screen the big button (`fabAddInvoice`, `add_invoice`) opens an invoice
straight away; a smaller outlined one stacked above it (`fabOtherDocs`,
`doc_add_other`) asks for the other four and keeps "Paste order". Stacked, not
side by side (lesson 58). `fabOtherDocs` is in ViewerMode's WRITE_BUTTONS:
**a new button that writes goes into that list the same commit**, or a
helper's read-only copy is offered a way to make documents.

### The owner's phone round, the seal, payment voice, heir access (4–5 Nov 2026, kmp-desktop-16)

Phone only for the three features (logic in :shared, no Windows UI yet). CI green at 5faccf3 (484 on-device tests);
nothing tried on a real phone, a real TSA, a real wallet notification or the deployed relays.

- **Nine fixes from the owner's photos:** Add Bill keeps what was typed (Back/Cancel/outside ask first;
  `guardTyped`) — the same flaw is still in Cashbook, Close the day, Plans, Cheques, Staff, Schemes and Edit
  entry; a supplier bill says how it was paid (On credit default / Cash / Bank-wallet chips; only cash bills count
  in the drawer; the list says "Paid · Cash" or "You owe Rs X"; old cash bills offer "put on credit"); Close the
  day and the Z report keep the drawer's minus; the promised day shows only while the customer owes; the Saved
  card pads the list; the receipt names shop and customer ("<Shop> gave <Customer>", "Credit slip", the balance by
  name, `ReceiptBalance`); RowToScreen.settle re-lays the list; Backup's intro comes before any Google prompt; the
  goods help says "Optional". The Khata list's default order is most recent activity (a customer whose entries
  are all deleted drops among the rest by name — by design).
- **Kitab ki Muhar (schema 44, backup 19, `BookSeals`, `SealMath`, `Rfc3161`):** `book_seals`, no uid, not
  synced, carried by backups (helper copy too). Once a day from KhataActivity.startDailyCopy, when the book's
  switch is on: leaves = SHA-256 of each new entry's canonical text (unit via `UnitWords.canonical`, amount
  %.3f), chained digest with the AuditChain head, stamped by FreeTSA, Sectigo and DigiCert (RFC 3161, DER by
  hand, no BouncyCastle; cleartext allowed only for the two http TSAs in network_security_config.xml; LiveControl
  `{"seal":false}` stops requests). The app checks a token's form, imprint and nonce and, since 7 Nov, its signature
  by the token's own certificate (no CA chain: see the security round below) — the proof file (entry detail → Seal
  proof) carries the openssl steps. **The leaf format is frozen once shipped**: a
  change means a new leaf version prefix ("e2|").
- **Paisa aaya ki awaaz (`PaymentNotice`, `PaymentListenerService`, `PaymentVoice`):** a NotificationListenerService
  (exported=false, as the class doc's own example) reads only the wallet/bank apps the owner ticks; other apps give
  only their name; the default SMS app, Messages and dialers are never read (Play's SMS/Call Log rule); OTP/PIN
  texts and outgoing words are ignored. Speaks with an Urdu voice when the phone has one, else a chime; "Add to
  khata" opens the existing I-got form prefilled (amount, wallet method, TID), through Gate with a one-time ticket per
  notice (a fixed per-phone key until 7 Nov — see the security round) so no other app can prefill; the owner presses Save. Off on a helper's phone. The patterns in `PaymentNotice.Words`
  are invented — check them against the owner's real JazzCash/Easypaisa screenshots.
- **Waris ki Chaabi (`Waris`, `WarisCrypto`, `WarisActivity`, `WarisWorker`; relays `/v1/w/{vault}/…` on all three,
  test_relay.py `waris()` 61 checks):** the owner names up to 5 heirs (two QRs; the heir's P-256 key seals W, the
  snapshot key); a weekly gzip+AES-GCM copy exported with `secrets = false` waits on the relays in ≤512 KiB chunks
  (≤8 MiB); the heir can only ask; the owner is warned on Home and by notification and can refuse; after the wait
  (default 30 days) the relay releases the box and the heir enters the book read-only through ViewerMode's file
  path. A refusal is re-sent on every status run to any relay that missed it. Waris keys are in
  `BookSettings.ACCESS_KEYS` (never synced) and out of every secrets=false copy; since 7 Nov W is in no export at
  all and a restore follows `Waris.restored` (the security round below). Relays keep Waris data out of live sync's cleanup; a vault idle 1095 days is removed.
  **Not deployed:** the hosting needs relays.yml's relay-upload.zip by hand; Supabase and the Worker need their
  deploy workflows. The Waris card image holds the heir's private key.
- Privacy policy sections 20–22 (both languages); "Notification access" in permissions; the short version no
  longer says there is no server. Data safety: nothing new collected (E2E-encrypted relay copies and on-device
  notification reading are exempt by Play's own definitions, checked 4 Nov 2026).

### The setup, redrawn (5 Nov 2026, kmp-desktop-16 — the owner's report and his approved mockups)

Phone only (Windows keeps its own name and trade questions; the trade icons are shared). CI only.

- **The bug:** a first run left after the shop type (Back on the country sheet, or the app closed) opened Home
  and the walkthrough next time and never asked the name or the country: "language chosen" (written at step 1)
  was all GateActivity read, Back did nothing on steps 2–3, the country sheet's cancel kept the phone's guess
  and went on, and an unsaved country reads as Pakistan. **`SetupProgress`** (data): done only by step 3's
  "Start my khata" or "Maybe later" (`markDone`); the gate opens the first unfinished step (`next`: language,
  shop type, details); `markStarted` is written (commit) just before the first language, so an install that
  chose its language before 5 Nov ("started" absent) counts as done and is never sent back. Tests that open
  Home set the "setup" prefs' `done` (or only "language/chosen", the old-install path) and save the shop's
  country — **the emulator's SIM says US**, and Home asks an unsaved country once (below).
- **Back walks back:** step 2 → the language (the one in use marked), step 3 → the shop type (the trade
  marked; what is typed is saved first), step 1 → leaves the app. ShopType and the details page no longer skip
  themselves when a trade or name exists. `TradePicker.bindActivity(current, onBack)`; a back arrow in the
  header (`btnTradeBack`, `btnShopNameBack`).
- **Step 3, the shop's details** (`ShopNameActivity`, `activity_shop_name.xml`): name, country, phone under a
  preview of the invoice's band (`bg_setup_preview_band`). The country is a box (the phone's own flag emoji,
  name, "+92 · Rs" via `CountryPicker.codeLine`, "Found from your phone" only when the shown country IS the
  phone's region, else "Tap to change"). The phone box carries the calling code (LTR): kept as
  "+92 300 1234567" (a national 0 dropped; + or 00 kept as typed; digits to 0-9). While the setup runs the
  currency sign and calendar follow the country shown; opened after the setup they change only from defaults.
- **`CountryPicker`** (ui, `dialog_country_picker.xml`): a full page — Back, a search (the name in the app's
  language or English, the code letters, the calling code), "From your phone" (`DeviceRegion.of`) first, then
  `ShopCountry.choices`, Other last; each row is the chooser's card (`item_choice_row`) with the flag as the
  glyph. Closing it changes nothing. Business Profile's country uses it (and shows the flag).
  **`DeviceRegion`**: `of` (SIM, network, the phone's languages, the app's) for a new shop's guess;
  `ofTelephony` (SIM, network only) for asking an older book — a phone's language region is weak (many
  Pakistani phones say en-US); `flag` (regional-indicator emoji, a globe for Other).
- **Older books** (`MainActivity.askCountryOnce`, after the trade question, before the tour): no saved country
  and the SIM/network names another known country → the country page once (`SetupProgress.countryAsked`),
  Pakistan ticked as what the book has used; names Pakistan → Pakistan saved silently; no SIM → nothing.
- **The language page** (`activity_language.xml`, `item_language_row.xml`): a compact brand band (splash_logo,
  app_name, `brand_tagline` "Har Hisaab Roshan", not translated), "Step 1 of 3", then the nine as a list — the
  own name in its own script (Arabic-script four in Naskh at 19 sp via textLocale "ar", Devanagari/Bengali
  17.5 sp, Latin 17 sp), the name in the app's language under it (`lang_gloss_*`, hidden for the same
  language), "Phone's language" (`lang_phone_language`) on the system language's row, which is marked to begin
  with (else English), a radio (`bg_lang_radio`). "Bahasa Indonesia". Back during the setup leaves at once
  (it used to clear the mark first). Night-aware. Gone: the splash painting (`splash_art.webp`, `SplashArt`),
  the 3 x 3 grid's drawables and `lang_*` colours.
- Strings (nine languages): lang_phone_language, lang_gloss_en/_ur_latn/_ur/_sd/_fa/_ar/_hi/_bn/_id,
  shop_details_title, shop_preview_label, country_found_phone, country_tap_change, setup_start,
  country_search_hint, country_section_phone, country_section_all, country_search_none.
- Tests: SetupProgressTest (unit), DeviceSmokeTest (the phone's language marked, nothing saved before
  Continue; the language alone does not finish the setup), screen proof 39–42 (the three steps, the country
  page).
- **The 25 shop-type icons** (`ic_trade_*`, phone picker and Windows' TradeKit alike) are one two-tone family:
  every path white, the main shape at full alpha and the second tone at `fillAlpha="0.4"` exactly, a light part
  inside a dark one cut out of the dark path (an alpha layer over a full one composites dark). Both platforms
  tint the whole drawable with SrcIn, which keeps alpha, so the two tones survive with no code change (lesson 56
  is about two COLOURS; two alphas are safe). Symbols: general storefront, kiryana basket, wholesale cartons,
  medical capsule, mobile phone, clothing T-shirt, electronics bulb, hardware hammer, agri sprout, dairy milk
  bottle, bakery cake, meat cut, produce apple, restaurant plate with fork and knife, cosmetics lipstick,
  footwear shoe, stationery notebook and pencil, tailor spool and needle (it was a shirt, the same as clothing),
  salon scissors over a comb, auto car, furniture sofa, building bricks, solar panel and sun, fuel pump, other
  2 x 2 squares. By night the light tone is the dimmer one (alpha over a dark chip). The picker's chip is 40 dp
  with a 24 dp icon (`item_trade_card`).

### Forms keep what was typed (5 Nov 2026, kmp-desktop-16 — the owner: "is dauran koi aur kami nazar aaye to usko bhi fulfill kar lena")

Phone only. CI only. The supplier bill's guard (4 Nov) on every money form that had the same flaw.

- **`ui/FormGuard`:** `guard(activity, dialog, typed, onDiscard, title, message)` (after show(); returns the
  `leave` function for a form left by a button of its own): a tap beside the form no longer closes it; Back (a
  callback on the dialog's own dispatcher) and its Cancel close an untouched form at once and ask "Discard what
  you typed?" (Keep editing / Discard; `form_discard_title`/`_msg`) when anything differs from how the form
  opened — an edit's own row, a new form's blanks and prefills. `onSave(dialog) { true/false }`: Save wired
  after show(), as Close the day's was — a save the form's own checks refuse keeps the dialog open with the same
  message and the focus on the box at fault; one let through closes it and takes no second tap (a flag, lesson 10).
- **Where:** the supplier bill (its own titles), Cashbook money in/out (new only — the phone has no edit form for
  a row; a waiting receipt picture is dropped on Discard), Close the day, Plans (a new plan; a payment — an
  overpay's question no longer loses the typed payment), Cheques (add/edit; its picture), Staff (a person; an
  advance/salary), Schemes (a scheme; a claim), Edit entry (`EntryDetailActivity`) and its spray advice, and the
  add-entry form (`PartyDetailActivity`: Back on the first step and the header arrow; judged on the form's own
  snapshot against the one taken as it opened, `F_BASELINE`, kept across a rebuild; Back with the details open
  still closes the details first).
- **Staff:** an advance/salary writes on AppScope (Back right after Save cannot cancel it); a person's name check
  and write run together off the main thread, and a failure is said ("not saved") with the form kept, instead of
  crashing.
- **Not done:** an accepted save closes the form and writes behind it, as Close the day does — a write that then
  fails says "not saved" (AppFailures) but the form is gone; the "set owner PIN" question can show twice on a
  fast double tap (needs a cancel callback in `AccessUi.requireOwnerPin`).

### Typing stays in the middle (6 Nov 2026, kmp-desktop-16 — the owner's photo of the invoice's options sheet)

Phone only. CI only until the keyboard check's pictures are looked at.

- **The bug:** tapping the Note in the invoice's "Discount, tax & payment" sheet stood the sheet a keyboard's
  height above the keyboard on Android 15+: the title at the top, Apply right under it, every field squeezed out
  between them, an empty band over the keyboard. Bottom sheets were lifted twice — `KeyboardFollow.dialog` padded
  the window's content by the keyboard and handed the insets on whole, and Material's sheet container
  (fitsSystemWindows; its theme is adjustResize) took the keyboard again. Below Android 15 the window's own frame
  took it first and nothing doubled. The keyboard run of 19 Oct had measured it (API 35: the field 867 px above
  the keyboard) and passed it, because it only asked "above the keyboard?".
- **Room, once** (`KeyboardFollow.install`): a full-screen dialog or sheet takes the covered part as its content's
  bottom padding and hands its children insets without the keyboard and without the navigation bar's foot
  (`withoutKeyboard`); a floating dialog is moved clear by the phone (every MaterialAlertDialog is adjustResize
  now, `ThemeOverlay.RoshanKhata.Dialog` — a dialog with no scroll was slid up only as far as its box, OK and
  Cancel under the keyboard). Screens keep ScreenInsets.
- **The middle** (`reveal`, `centre`, `shift`): the focused box goes to the middle of what its scroll shows —
  above the keyboard or the app's pad, below the title, inside a pinned footer — not only to the keyboard's edge;
  a box near a form's start stays (the scroll cannot go above it); a note taller than half the space puts the line
  being typed on there. Measured from the layout (`offsetDescendantRectToMyCoords`), so a sheet still sliding with
  the keyboard is measured where it stops. A smooth scroll; nothing moves for a box in sight within 24 dp of the
  middle. The calculator pad calls `centre` when it takes a box (CalcPad.showPad, again once the keyboard has gone).
  Compose fields (counter, orders, shift, the stock-count and return dialogs) bring themselves into view (Compose's
  own rule, not centred).
- **Every box by itself** (`watchAll`, `watch`): TextFit.relax (every layout made in code, lesson 59) and
  FormBox.style (every box made in code) hand their boxes to KeyboardFollow; once a box is in a window, that window
  gets the room and the middle unless its own code already looks after it (`tag_keyboard_window`, set by
  `keepFocusedVisible`: ScreenInsets, the shop-name page, TradePicker, CountryPicker). The stock adjustment, the
  counter's forms, the heir dialog and the add-entry form had none before. The 32 explicit `KeyboardFollow.dialog`
  calls stay (they set adjustResize on the window too).
- **The first keyboard run (8542391c) found three more:** a screen whose root IS its ScrollView (the business
  card) was measured a page off — `offsetDescendantRectToMyCoords` answers in the root's content, so `frameIn` now
  takes the root's own scroll off (fields scrolled to -1255 px); below Android 15 the window's own frame took part of
  the keyboard and handed the rest on spent, so a sheet stood a navigation bar under the keyboard, Apply hidden — the
  covered part is now settled again after each layout against the window's own keyboard; and the pad's box was left at
  the form's top because a scroll growing as the keyboard leaves stops a smooth scroll under way (`onSizeChanged`) —
  the pad centres with a plain scroll, on every layout while it settles.
- Tests: KeyboardFollowShiftTest (unit); KeyboardDialogsOnDeviceTest now fails a band under a sheet, a field above
  the window, and a field off the middle of a long form, with "by itself" cases; KeyboardInvoiceSheetOnDeviceTest
  opens the real sheet (the note with the keyboard, the amount received with the pad). keyboard.yml runs on kmp-*
  pushes too (API 30 and 35); its pictures go to the `keyboard-proof` release.

### Screens from the owner's photos (6 Nov 2026, kmp-desktop-16 — "jab tak test chal raha hai, in ko dekho")

Phone only. CI only.

- **Duplicate customers** (`item_duplicate_group`, `DuplicateGroupAdapter`): a card per group — the reason and a count
  (`duplicate_count`), a warning where the numbers say something (`DuplicateDetector.caution`: two numbers one digit
  apart, "perhaps a typing mistake"; a name-only group whose numbers all differ, "may be different people"), each
  record on two lines (name; number · balance, Settled at zero), "Not a duplicate" and "Review & merge" as buttons
  sharing the row. Every number in one shape (`DuplicateDetector.displayPhone`: the shop's calling code, ten digits as
  3 + 7; another country's number as typed). Tests in DuplicateDetectorTest.
- **Automations:** a row each — name, a line on what it does (`auto_*_desc`), the hour as a small outlined chip, the
  switch at the end; hairlines between (`bg_row_divider_top`).
- **Payment Radar** (More): where it stands first (`radar_state_on/off`), then what it does, once (the help and
  `radar_private` both said the customer never sees it); Turn off in the gave red.
- **Payment voice:** the switch under the notification access it needs, greyed and saying so until it is given
  (`paisa_switch_needs_access`); an app not on the phone reads muted.
- **Every tick, radio and switch is the app's green** (`colorControlActivated` and `android:colorControlActivated` =
  brand_green_text in Theme.RoshanKhata): they took colorSecondary's gold (3.18:1, the audit's L36) and the phone's own
  Switch was grey. Text size's "Aa" tiles are the follow-up green, not the invoice pink.
- **Add buttons:** the extended Add buttons of Bills, Cashbook, Cheques, Plans, Documents and Products are brand_green
  in sentence case (were near-black, capitals). **Which document?** is a ChoiceSheet: a card per kind with a line on
  what it is for (`doc_desc_*`), one tap opens it, "Paste a WhatsApp order" the last card.
- **Supplier bill:** a paid-now bill has no due day — the box goes while a paid-now chip is chosen and nothing is saved
  for it (`dueToSave`); On credit brings it back with the day picked; a paid bill's edit hides it.
- **Invoice on WhatsApp:** the file goes with a caption in the shop's voice and the app's language — shop and document,
  total (not for a challan), due day, thanks (`InvoicesActivity.captionFor`, `invoice_caption_total`); no advertisement
  for the app (the sheet carries its "Made with"). WhatsApp shows EXTRA_TEXT as the caption on most phones, not all.
- Strings (nine languages): duplicate_caution_one_digit/_numbers, auto_*_desc (5), radar_state_on/_off,
  paisa_switch_needs_access, invoice_caption_total, doc_desc_* (5); plural duplicate_count.

### The customer's khata link and Mera Khata (7 Nov 2026, kmp-desktop-16 — the owner: "zyada log apna balance dekh sakein", credits not spent by a few)

Phone only (Windows has no button yet; a syncing book shares links through the book key). CI only — the hosting part
is the owner's upload (web/khata/README.md), nothing tried on a real phone.

- **The shop's side:** the customer page's ⋮ → "Send khata link" (`action_khata_link`, EXPORT, never on a helper's phone)
  says what goes, then `CustomerLinkSync.share` (CustomerLink.share: the page on the server first) and WhatsApp with
  `clink_message`; a link sent can be sent again or stopped (`CustomerLink.remove` + `flushNow`). Another copy of the
  book asks before claiming (`clink_other_copy`).
- **What the customer sees: the total balance** (`CustomerLink.Mode.of` now defaults to BALANCE; LAST3 stays a book
  setting with no screen yet). Name, shop, side, amount, the day the shop last wrote.
- **Load (the owner's rule):** pages are plain files on the hosting (`CustomerLink.HOME` = https://khata.ehtisham.shop/),
  never live sync's relays or any paid service; the phone sends only changed pages, at most one send waiting
  (`CustomerLinkSync.soon`, 10 minutes after the app leaves the screen, KEEP), nothing at all on a phone that never sent a
  link (`clink_used`); the customer's app asks a page only when Mera Khata opens and the last look is 10 minutes old,
  Refresh once a minute, with the last ETag (`CustomerPage`, 304 when unchanged). api.php's caps hold every shop.
- **The customer's side:** `MeraKhataActivity` (exported; App Link on khata.ehtisham.shop with `autoVerify`, and
  roshankhata://khata?l=… from the web page's "Open in the app" button, which names the app's package and falls back to
  the Play Store), the list kept in prefs "mera_khata" (`MeraKhataStore`, at most 50, a link kept only once its page
  opened — at most 5 wait —, never in a book or backup); More → Mera Khata once there is one; a phone whose setup is not
  done opens on it from the front door (`GateActivity.EXTRA_SETUP` starts the setup from its "Start my own khata" card).
  In BaseActivity's BEFORE_THE_BOOK (no counter sign-in; never the book); since 7 Nov a task of its own
  (`taskAffinity=""`), and it asks App Lock itself when the owner has it on and it is due (`AppLock.Away`).
- **web/khata:** api.php keeps its store beside public_html even from a subdomain's root (it would have been
  public_html/rk-khata-data, served); `.well-known/assetlinks.json` names the Play build's three keys (in use, previous, upload)
  and the test build's, and is live with the page (7 Nov 2026: khata.ehtisham.shop uploaded, the hosting relay
  replaced and checked by relays.yml); `scripts/pack_khata_web.sh` makes the upload zip.
- Tests: CustomerPageTest (shared), MeraKhataStoreTest, EveryScreenOpensTest opens Mera Khata; scripts/test_khata_web.sh.
- Strings (nine languages): clink_* (14; 20 after the security round), mera_khata_* (23; 24).

### The 7 Nov 2026 security round (kmp-desktop-16 — the owner sent "5 Security Checks Before You Launch Your App": "implement with professional skill")

The PDF's five checks (secrets, personal data, launch checklist, login/payment logic, the attacker's view) on this app;
its web-app items (Stripe, JWT, password reset, NEXT_PUBLIC_) do not apply — no accounts, no gateway. CI only: nothing
on a device, a real wallet notice, a real TSA or the deployed relays.

- **History scan** (a full mirror: 1,267 commits, 52 refs, 10,564 file versions): no key, token, password or key file
  in any commit (17 patterns of Gitleaks' kind and Yelp's detect-secrets 1.5.0 — 323 hits, every one a test vector,
  an alphabet or CI's local Postgres). Gitleaks itself could not be fetched here (GitHub releases and the Go proxy are
  closed to the session). Personal data: the 6 Nov Duplicate work had written five customers' numbers and a customer's
  name from the owner's photo into DuplicateDetector's comments. **History rewritten 8 Nov 2026 (the owner's yes):** a
  second scan of all 1,276 commits found those five numbers only on kmp-desktop-16, but real customers' names from his
  book (with his village and nicknames) in the search and voice tests since 25 Jul, on all 41 branches, main and the
  release tags. `git filter-repo --replace-text` (blobs and messages; each real word swapped for an invented one of the
  same length, the list kept outside the repository) left none anywhere; every branch was force-pushed
  one at a time (all at once was refused 403) and the tags moved by a one-time workflow the owner ran (this session may
  not push tags). Every commit id changed: an id quoted in this file from before 8 Nov names the old history. A backup
  bundle of the old history was kept outside the repository. Old commits stay reachable by id on GitHub until GitHub
  Support purges them (the owner's request). This clone commits as Claude <noreply>, never the owner's address.
- **Four reviewers** on what was built after the 3 Nov audit (customer link, Waris, payment voice, seal): 2 critical,
  21 medium, 20 low; every point read in the code first; fixed by five fixers in worktrees, merged by cherry-pick.
- **Relays** (5501b0da, all three alike, test_relay.py `--wlimits`): Waris vaults have their own bytes — hosting 256 MiB
  (`RK_RELAY_WARIS_BYTES`; the books' total moved to `.size-books`), Worker 512 MiB (`wbytes` in the !ledger), Supabase
  128 MiB (`RK_WARIS_BYTES`) — so a vault can never make a live-sync parcel 507 (anyone may make a vault: it stopped
  live sync for every shop); 507 "waris-full"; new vaults 20/100/50 a day (429 "waris-day"; a re-setup is never counted);
  a vault with no heir and no snapshot 14 DAYs after its setup is swept; the Worker refuses an heir body over 16 KiB
  before reading it. No per-IP limits (carrier NAT).
- **Waris** (1cc615ff, a6accbf3): the owner's protective calls (status, refuse, allow, turn off) never wait for the
  kill switch; gone heirs are deleted before any heir is put; a hid a relay holds that this book does not name is
  deleted there and kept for good in `waris_removed` (≤1000; turned-off vaults too); an allow this phone did not give
  is refused again; **W (`waris_key`) is in no export at all** — a book with a vault but no W makes a new one
  (keyVersion+1, `waris_snap_kv`), and every W made before was replaced once (`waris_key_safe`); a restore keeps this
  book's vault and joins the removed lists (`Waris.restored`), a pairing takes none; the background run is twice a day
  for every shop (closed ones through `openOther`, only Waris keys written back, a copy pinned to its shop —
  `Waris.BookMoved`); a refusal some relay missed is asked again at once (`waris_refuse_left`, expedited work) and Home
  says so; Android 13's notification permission is asked when Waris is turned on; the card's words say whoever holds a
  photo of it can ask (nine languages) and its picture is deleted after the share; 403 is kept (`waris_relay_no`), 429
  is `Why.BUSY`; "open to them now" once the day passed. The owner secret stays in full backups by design (the owner
  keeps Waris on a new phone): if such a backup is lost, turn Waris off and on and link the heirs again.
- **Customer link** (607f5e1d): api.php — new pages a day per shop (50 in its first 30 days, 500 after), a fifth of
  MAX_FILES kept for older shops, new shops 10 a day per address (a keyed hash, IPv6 by /64) and 60 an hour, no shop
  record without a page, a page-less shop gone after 7 days, **ids never freed** (`u/`), HSTS for this host only; pages
  sealed under `"rk-clink-v2|" + id` with a counter that only grows (`q`), padded to 512/1024/2048 — readers keep the
  highest and say an older one came (`mera_khata_older`; view.js with localStorage); v1 still opens; "Open in the app"
  names the package; no key in `?l=` on https. App Lock: `AppLock.Away` (elapsedRealtime; only the book's screens
  count; due until answered; a fresh process starts due — `AppLockAwayTest`). Stopping a link says the real result;
  another copy holding the shop offers "Send from this phone"; send and stop are audited (EXPORT); page days outside
  2000–2100 are none. **web/khata must be uploaded before the new build reaches a phone**: the old view.js cannot
  open a v2 page.
- **Payment voice** (ded336db): a reject list (English, Roman Urdu, Urdu — request, failed, refund, loan, offer,
  cashback, bill, easyload, ملے گا…) before the money-in words; Urdu words whole; the sender's number only after
  from/se; chat, email, social, call and ongoing notices never read nor listed; each app's package shown; a known wallet
  ticked by itself only when Play installed it; **a one-time ticket per notice** (`paisa_tickets`: once, 24 h, the
  feature on, its own shop; taken off a launch from Recents) instead of the fixed door key; the notice cancelled when
  opened; a payment reference already in the book says so (`entryWithPaymentRef`, `cashWithPaymentRef`); a masked number
  needs 6 of the 7 digits after 03xx; a sender name that disagrees gives a choice; text cut at 1,000 characters, parsed
  off the main thread, 3 announcements a minute; only a chime while the phone is locked or App Lock is on, no customer's
  name in the notice then; an offline voice or the chime; a binned or other party opens the Khata list. **The patterns
  are still invented** — check them against real JazzCash/Easypaisa/bank notices.
- **Seal** (e21806cd, 0db534d1): `Rfc3161.verify` — one SignerInfo, contentType TSTInfo, its messageDigest, the RSA or
  ECDSA signature by the token's own certificate, its critical timeStamping EKU, valid at genTime, genTime within 24 h
  of the request; a token that fails is not kept and that witness is asked again. No CA chain (the TSAs' CA
  certificates are not in the repository): the screens say "answers received" (●), never verified. A proof works the
  digest out again from header and leaves (`Proof.Broken`), takes times from the tokens and follows prevDigest. Leaf
  **e2** (header `rk-seal-v2`): goods lines, payment method, loan and bin flags, every field length-prefixed, figures
  by BigDecimal; the product uid left out on purpose (the tie button and live sync change it); e1 seals still check.
  At most 5,000 leaves a seal (a catch-up chains several); seals go 8 to a page in a backup; each witness has a 45 s
  whole deadline, the three side by side, a lock per book; tokens written `WHERE id AND digest`; User-Agent
  "RoshanKhata"; both privacy policies say what the time services and GitHub see. The switch (`BOOK_SEAL`) is in
  ACCESS_KEYS — never synced — and audited (`seal_on`/`seal_off`, worded in Who did what).
- **Left, with the reason:** full TSA chain validation (needs the TSAs' CA
  certificates, fetched and checked by hand); a push from the relay to the owner on an heir's ask; a passphrase on the
  Waris card (decided against: an elderly heir would lose the book); `LiveControl.fetch`'s per-read timeouts and default
  User-Agent; deploys — the hosting relay's and web/khata's uploads are the owner's, the Worker and Supabase by their
  workflows.

### 8 Nov 2026 review fixes (kmp-desktop-16, the critical round C1-C3; CI only, nothing deployed)

- **C1, a stranger filling the relays' shared byte budget.** All three relays alike (lesson 43): a book no second
  device has joined may hold only 256 KiB (`YOUNG_BOOK_BYTES`), is swept 3 days after it was made
  (`YOUNG_DAYS`), and — like every NEW book — is refused (the existing words "full" / "book-full", so no client
  changes) once the store holds half its cap; books of two or more devices keep the 16 MiB and the other half of the
  store. So junk books can no longer take the room active shops need (lesson 81: each tier its own budget, what is
  new capped, what is never used swept). Cloudflare's ledger answers `young` beside `full` (header `x-rk-acct`:
  full / young / room); the hosting's forced sweep runs at most once a minute (it opens every book now). Residual,
  said plainly: a caller who says hello with two device ids counts as "joined", so a patient attacker is still held
  only by the per-day new-book caps (CF 100, hosting 300, Supabase 300) and the 30-day idle sweep — the cost to
  him is higher and the first half of the store is no longer his to spend. Honest effect: while junk holds half
  the store a NEW shop cannot start relay sync (WiFi pairing still works). Waris vaults (R01-F2) are not changed
  here. Test: scripts/test_relay.py `young()` in the plain run; the sweeps are not exercised by a test.
- **C2, a supplier's "I got" counted as cash.** Both entry forms pre-ticked Cash for every new "I got", so goods taken
  on credit from a supplier were drawer cash ("40,000 short"). A supplier's "I got" now has no method (the section is
  hidden, the phone also nulls it at save, Windows likewise), and `khataCashBetween` counts money IN only for
  customers (`partyId IN customers`), so old rows marked Cash by that default read right with no change to any row.
  Not done: a supplier's genuine cash refund can no longer be marked Cash (it was never separable from the default);
  R04-F2 (a khata "I gave" can never be cash) is another finding. Test: DrawerByTheBooksTest.
- **C3, a corrupt book deleted by Android.** Room is built with `BookRecovery.SafeFactory`, whose callback keeps the
  file as `khata.db.corrupt-<time>` (newest 3) instead of the framework's delete, lets go of the open instance, and
  leaves a note; the next `KhataDatabase.get` rebuilds the book from the newest daily copy that folds whole
  (`Snapshots.rebuildMissingBook`), or opens an empty one, and Home says so once (`book_recovered_*`, the Daily copies
  door). Until the owner has read it the automatic Drive backup is held (`BookRecovery.holdsBackup`), and an automatic
  backup is also refused when the entry count fell below half of the last upload's (`DriveBackup.shrunkTooFar`; the
  owner's Back up now is never refused and resets it). The daily copies keep 30 instead of 7 while the book holds under
  half its best count, so the last good copy is not rotated out. Screens already open keep their old handle until the
  app restarts (the framework gives no safe moment to close it). Not tried on a device: BookCorruptionOnDeviceTest.
  One new string, `book_recovered_none_phone`, in all nine languages.

**Marhala 2, money and counter (M41-M54, M62-M68, M69 in part, L104-L107, L110, L114-L115, L118; CI only):**
- Money: a cheque both books have, still pending here and settled in the merged copy, takes its status and its
  clearing entry (`BookMerge`, M41); a counter payment's reference is also the payment's `paymentRef`, so the payment
  voice's duplicate check finds it (M42); the ticket's number leads its khata entry's note (L115); Undo of a paid-now
  cash receipt asks `AccessUi.may` and is written in Who did what (M43); a hand-written entry carries `createdBy` and an
  `ENTRY_ADDED` row on both platforms (`audit_entry_added`, nine languages, M44); the Zakat price warning speaks only in
  rupee-like money (M46); `SaleMath.MAX_RATE` is 1,000,000,000 (M47); the day report's "Sales total" is
  `salesTotalBetween`, the figure the 21:00 notice and Insights use (`ZReport.counterSales` keeps the counter's own for
  profit, M48); Insights' cost rule starts with the product's own cost price like the sales report (M49, staff pay was
  already in both); a khata "I gave" past the credit limit asks a manager's PIN like the counter (M50); Follow-up's
  rhythm leaves loans out as the Radar does (`PaymentRadar.settled`, M51); a reminder sent from the customer page is
  logged once WhatsApp opened (M52); the sales report's week is `PartyWhen.THIS_WEEK` (M53); By item is per item and unit
  where an item sold in several (M54).
- Counter: "Void and correct" subtracts the points' money from the carried discount (`reRing(pointsSpent)`,
  `pointsSpentOn`, M62) and drops the old batches (L107); a return gives back each line's own tax (`paidRate`, M63);
  untagged stock covers a sale beside an expired batch (`Fefo.allocate(untagged)`, M64); resuming a held ticket parks the
  current one first (both platforms, M65); Back on a ticket being rung up asks (phone, M66); Complete says why it is grey
  and no change is shown for a wallet overpay (M67, L118); Recent sales lists 100 and reprints a receipt (M68, M69 in
  part: no search, no returns list); points follow the ticket (`pointsSpent`, L104); a pack on credit keeps the credit
  share (`Units.rateFor`, L105); the Windows post lives on `AppScope` (L110); an unknown 6+ digit code is said, not rung
  up (L114). Tests: Review8NovMoneyCounterTest, FefoPackTaxTest, ZakatTest.
- Not done, with the reason: M40 (a chip for the way an "I gave" was paid is a form redesign on both platforms); M45
  (whether a binned customer's goods stay out of the shelf is the owner's rule, and every stock query reads
  `isDeleted`); M70 (needs the held ticket's uid on the sale and a sync rule); L106, L108, L109, L111-L113, L116, L117,
  L119 (device-only drawing, a drawer query by user, or the owner's call).

**Marhala 4, security and hosting (M25-M32 in part, M59, L45-L58; CI only, nothing deployed):**

- **Actions pinned (M29, first half):** every `uses:` in `.github/workflows` is a full commit SHA with the tag in a
  comment (resolved with `git ls-remote`, annotated tags by their `^{}` commit). A new action is pinned the same way.
  Not done: publishing the release from a separate secret-free job.
- **M59:** `network_security_config.xml` has a base-config: the system's roots plus ISRG Root X1
  (`res/raw/isrg_root_x1.pem`, SHA-256 checked against Let's Encrypt's published 96:BC:EC:06...), because Android 7.0
  (minSdk 24) lacks it and every Let's Encrypt host failed there. A public root, not a pin.
- **M30 / M31 (Windows):** the "From phone" window's pull answer is the book **without secrets** (`secrets = false`: no PIN
  hashes, server passes or Waris keys), since the first device to scan its QR gets it. The give code no longer sends at the
  first scan: the window shows who asks (name, address) and sends only on **Allow** (`Giving.Asking`, 60 s, no answer =
  no); a refusal keeps the code for the phone it was meant for. Strings live_give_ask/_allow/_refuse.
- **M28 / L46 (hosting relay):** a book's SQLite is `auto_vacuum = INCREMENTAL` (an older file over 256 KiB is
  vacuumed once on opening) and `prune_taken` frees pages (`incremental_vacuum(1000000)`: the bare pragma frees one
  page through PDO), so acked parcels give their room back to the store's byte limit. New books pass the caps one at a
  time behind a lock file (`.new-book.lock`). The Worker's Durable Object cannot be vacuumed here: not done.
- **L50:** Erase all and Send to computer ask the owner's PIN first (`AccessUi.ownerFirst`; none set: straight on).
- **L55 / L56 / L58:** a backup file too big for the phone's memory says `wifi_take_too_big` instead of crashing; a helper
  copy's pictures are capped (15 MiB each, 400 MiB together, `BackupImages.copyCapped`); a CSV balance that is not finite
  or over 1e12 is an unreadable cell (`CsvImport.MAX_BALANCE`). L47/L48: `scripts/check_khata_headers.sh` checks the
  live host's headers after an upload (run by the owner).
- **For the owner / left, with the reason:** M23 and M24 (Waris: a 2-of-3 split of the heir box and per-relay passes need a
  protocol version on all three relays, and heirs' pass hashes cannot be re-derived per relay from what is kept); M25 (a
  /24 count would hurt carrier users; Tor lists are data the owner chooses); M26 (a Cloudflare custom domain with a rate
  rule is the owner's account work); M27 (the page counter must live in the synced book); M32 (the Waris owner secret and
  customer-link keys stay in full backups by the owner's earlier decision; a passphrase backup is a product choice);
  L44 (an offline signing key for live.json); L49 (loopback is allowed because the tests use it); L51, L52, L53, L57,
  L59 (low confidence and a lock-out risk, lesson 66), L60, L61, L62; L54 (figures in the 21:00 notice are the feature).

### 8 Nov 2026 review fixes, marhala 6A: printed documents (M15-M19, M55-M58, M71-M79, L24-L31, L120-L131; CI only)

- **One wrap/fit helper** in `InvoiceTemplateKit` (copied to Windows): `wrapLines(text, paint, maxW, maxLines)` and
  `drawFit(...)` (shrinks to 65%, then ellipsis). Used by the A4 header (shop name leaves room for the heading, L123),
  the items table, bank rows (a long value goes on a second line, L125), terms and amount in words (wrapped, M76), the
  statement's detail line (up to 3 lines, L124), the register's two notes (M16) and the thermal roll (name, address,
  item names, terms; its page height is estimated with the same helper, L121/L122). Phone numbers in sentences are
  `Format.ltr` (M74); a payment QR is fitted, never stretched (L129); a credit note carries no bank box or QR (L120).
- **Money lines:** a round-off down is a minus line on the A4 invoice, the editor's sum and the roll (M71); a percent
  discount is held to 0..subtotal (L131); `NumberWords.amountInWords` rounds to the currency's decimals first (M72);
  the statement's opening and running balance carry their minus (M73, phone and Windows); a challan roll prints no
  discount, tax or charge (M78). A shop with no name prints no seller name, not "Roshan Khata" (M75).
- **Windows supplier statement** says "Paid to you / You supplied" like the phone (`StatementPrint(isCustomer)`, M79).
- **Cards/posters:** the tagline pill leaves its room to the title (`pillW`, M15); Seal takes three lines (M19); Swiss'
  contact column is 520 px (L26); address/email now draw on Fitr, Ramazan, Jumma and Azadi posters (M18; Eid ul-Adha has
  no free line); the card takes the profile's phone/address when it holds none (L127).
- **Register/inspector:** batch, bill, Reg# and quantities shrink to their cell, the status chip shrinks, the label
  checklist lists every product (L27-L29, L31; `pdf_insp_and_more` is now unused).
- **Words:** no "Mr." in Arabic/Bengali/Farsi reminders (M55); Hindi/Bengali `pos_change` no longer reads "Return" (M56);
  `due_date_set` says due in Urdu, Roman Urdu, Hindi, Bengali (L130); the owner PIN dialogs say 6 to 8 (L25); daily-copy
  names use ASCII digits (M58); plan lines isolate money (M57, PlanAdapter); two-line buttons on Duplicates and Sale
  saved (L24); the shared receipt text heads itself with address, phone and tax number (L126).
- **Not done, for the owner:** L30 (card text 5-7 pt needs a visual redesign of all 24 cards), L128 (a Print-ready PDF
  button is a new feature), M17 (already shrinks to fit, floor 24 pt).

### The shared module (KMP)

```
shared/src/
  jvmSharedMain/   Kotlin that may use the JDK; Android and the computer share it
    data/          KhataDatabase (Room, @ConstructedBy), KhataDao, entities,
                   BackupCodec (the backup format), ShopSettings, Format...
                   One rule on both since 29 Oct 2026: DayMath (dayNumber/startOfDay —
                   13 copies before), DateWindow (PartyWhen/ReportWindow, the phone's
                   DateRangeFilter reads them), WhenWords (Today/Yesterday/day),
                   DayCloseMath.drawerByTheBooks + DrawerFigures (the phone's Close
                   the day and the computer's Home/Cashbook), Format.moneyLtr,
                   PaymentRef/PaymentRequest, PaymentMethod.label/said
    screens/       Shared Compose screens: About, RoshanTheme (PartyLedgerScreen.kt
                   keeps only the balance's side word and colour since Marhala 4;
                   EntryForm.kt, a shared entry form nothing showed, was removed
                   29 Oct 2026 — each platform has its own entry dialog)
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
  MainActivity.kt           Home — the coloured tiles, walkthrough
  KhataActivity.kt          Ledger list — search, voice entry, filters
  PartyDetailActivity.kt    One customer — two-step full-screen entry dialog
  EntryDetailActivity.kt    Receipt + edit
  BusinessCardActivity.kt   Card picker
  BusinessSettingsActivity  QR, signature, photo-on-statement switch
  RecycleBinActivity.kt     Bin + bulk delete
  BaseActivity.kt           Every screen extends this (not AppCompatActivity): text size
  data/TextSize.kt          Owner's text size (Home ⋮) x phone scale, 85%-150% bounds
  ui/TextFit.kt             Fixed-height buttons may grow ONLY when text > 100%, or in
                            ur/sd/ar/fa on Android 15+ (the elegant font, R04);
                            call TextFit.relax(view) after inflating any dialog/row in code
  data/
    VoiceEntry.kt           Spoken sentence -> party, amount, direction
    BusinessProfile.kt      QR, signature, photo-on-statement setting
    PdfExport.kt            Statement PDF — signature, optional photo
    DriveFeature.kt         ENABLED = true since the Cloud setup — the one line to switch Drive off
    KhataDatabaseAndroid.kt get(context): migrations, the phone's file (DB itself in :shared)
  ui/
    CardTemplates.kt        12 business card designs
    NameSearch.kt           One search rule for every screen
    Calc.kt                 evalPad, resolvePercent, trim
    DateRangeFilter.kt      Shared date filter
    DateTimeField.kt        Shared date + time picker

app/src/test/java/.../data/VoiceEntryTest.kt   What the microphone may conclude
```

VoiceEntry, DriveFeature, Calc, NameSearch and SmartSuggest's ranking now live in `:shared`
(`shared/src/jvmSharedMain/...`, same packages); their tests are in
`shared/src/jvmSharedTest` (SmartSuggestTest stays in the app's tests, which run it against the shared code).

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
    `<plurals>`; direction icons on Windows go through `Glyph`
    (flipped in RTL; `MonoTile` is gone); names and figures in a sentence are
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
50. **A box's label follows every choice that changes its meaning.** The
    Zakat price box said "per gram" under a "Per tola" toggle; a gram's price
    read as a tola's put the nisab 11.66 times too low.
51. **A copy on the same disk is not a backup.** The computer's daily copies
    (written on opening, every hour, after sales) counted as backups, so the
    week-old reminder could never come.
52. **A status changes only from the state that allows it, in the DAO.** A
    screen that wrote a status over whatever the row held let a stale dialog
    mark a cheque the other device had cleared as bounced; reopened and
    cleared again, it posted the payment twice.
53. **A weight that does not fill still takes its share.** In a Row,
    `weight(1f, fill = false)` reserves its part of the space even for an
    empty child: Home's day was cut to "Sunday, 25 Oct…" beside no buttons
    at all. A part that should take only what it needs gets `widthIn(max)`.
54. **A dialog drawn in the window passes its keys through the window
    first.** The window's `onPreviewKeyEvent` runs before the scene, so F2
    typed in an in-window dialog opened a second form; `KeyActions` waits
    while `OpenDialogs.count > 0`.
55. **Material's bodyLarge line height is every Text's.** A Text that sets
    only `fontSize` keeps bodyLarge's 24 sp line: 13 sp rows stood 24 sp
    tall. Set the type scale once (`DeskTheme`), not text by text.
56. **A two-tone tile painted in one colour is a blob.** The phone's
    `ic_tile_*` draw themselves (two tones); only its one-colour glyphs
    (`ic_feature_*`, `ic_qr_scan`…) are tinted (`PictureBadge`, `Section.ink`).
57. **A `= runBlocking { … }` test must end on Unit.** JUnit refuses a test
    method that returns a value ("should be void"); a last line like
    `Counter.post(...)` (a Long) failed the whole class at initialisation
    (CounterReviewTest, 29 Oct 2026). End with an assertion.
58. **A row of buttons leaves nothing for the words beside it — and the phone is
    360 dp.** In a horizontal LinearLayout a `0dp`/weight-1 TextView gets only what
    two wrap_content buttons leave; in a Compose `Row` unweighted children are measured
    first and a `weight(1f)` Column gets the rest. On the owner's phone (30 Oct 2026)
    the cashbook's "Drawer not counted yet" ran down the screen a letter a line and the
    counter's total read "Tot / al / R". Words go on their own line above the buttons,
    and buttons sharing a row share it by weight with `maxLines = 1` + ellipsis. **The
    owner's rule:** what is Windows' stays Windows', what is the phone's stays the
    phone's, and only what fits both is shared — a shared screen is checked at the
    phone's width (and at 150% text, in Urdu) before it is shared, not only on the
    laptop's proof pictures.

59. **A hint is one line; a question longer than its box goes above it.** A one-line box cuts its
    hint at the edge, and which hints overrun depends on the trade (TradeVocab's examples), the
    language (Bengali, Indonesian longest), the text size and the phone (30 Oct 2026, the owner's
    photo of the product form). Long questions are labels above the box (`FormFieldLabel`,
    dialog_edit_product); narrow unit boxes say "Unit" (`unit_short`) and list the trade's units
    first on a tap (`TradeVocab.unitChoices`); a TextInputLayout's examples are its helper text.
    Under all of it `HintFit` (run by `TextFit.relax`, so every screen and every dialog that calls
    it) wraps any EditText hint that still does not fit, keeping the keyboard's Next/Done. Every
    layout inflated in code calls `TextFit.relax(view)`. **No `Widget.Material3` style in this
    MaterialComponents theme**: it finds none of its colours (a magenta edge, tiny text).
    **Every form box is boxed** (the owner, 30 Oct 2026: the bill's boxes looked professional, the
    product form's bare lines did not): a layout's box has `style="@style/EditorField"` (70 were bare in
    16 layouts), a box built in code goes through `FormBox.style` (which also runs HintFit) and a dialog
    body built in code is `FormBox.column` (side margins, 8 dp between boxes). A box in a
    TextInputLayout keeps that layout's own outline.

60. **A colour that stays dark at night is a fill, never a mark.** brand_green (#1B5E3A both ways) under white
    words is right; as text, an outline, a chart bar or a progress line on a night card it vanished (1.7-2.4:1).
    Marks take the night-aware `*_text` colour (brand_green_text, green_got_text; Compose `brandGreenText`), and a
    button's words take the colour its fill can carry, never a fixed white.

61. **A TextView's "start" is its text's, not the screen's.** Android aligns a TextView with no gravity by its
    paragraph's own direction: an English name in an Urdu row sits at the LEFT, against the amount ("Rs 4,000Test
    Supplier", the 31 Oct screen proofs — bills, expiry, documents). Every full-width or weighted TextView in the
    layouts carries `android:textAlignment="viewStart"` (227 added), except where gravity or a style sets its own;
    a new one needs it too. And a name beside a figure on a 360 dp phone at 120-150% text gets a few letters' width:
    the row calls `TextFit.stackWhenLarge(nameColumn, figureColumn)` (Khata list, Khata Report), which puts the
    figure under the name above `TextFit.STACK_SCALE`. `screen-proof.yml` takes 28 phone screens in four looks
    (English 150% day, Urdu 150% night, Bengali night, Indonesian 85%) to the `screen-proof` release — look at them.

62. **A shared-element row is hidden until its return ends — in a RecyclerView that row is recycled.** The Khata
    row that grows into the ledger (`RowToScreen`) is made invisible by the framework and shown again only when the
    fold-back finishes; a return cut short (the list changed under it) left it hidden, and the list gave the hidden
    row to another customer: a blank card-sized gap (the owner's photo, 1 Nov 2026). Every bind makes a row plain
    (`RowToScreen.plain`: visible, opaque, no transition name) and the list settles its rows after resuming
    (`RowToScreen.settle`). A party never written to reads "No entries", not "Settled" (`party_no_entries`).

63. **A `0dp` width means "let the constraints decide" only inside a ConstraintLayout.** Moving the Products
    band out of one into a LinearLayout (2 Nov 2026) kept its `layout_width="0dp"`: the band measured at no
    width, its words broke a letter a line, it grew taller than the screen and the list under it got nothing
    — the screen proof was blank in all four looks while every test passed (the screen opened, nothing
    crashed). When a view changes parent, every `0dp` and every `layout_constraint*` on it is re-read; and the
    proof pictures are looked at after any layout change (lesson 16), not only the checks.

64. **`$` in a Kotlin string is a template, in a regex too.** `Regex("%(\\d)\\$s")` read `$s` as a variable
    and the build failed ("Unresolved reference 's'", 266487c); a literal dollar in an ordinary string is `\$`
    (`"%(\\d)\\\$s"`). Python checks cannot catch it — no local Gradle — so a new regex with `$` is read twice
    before the push.

65. **Two rows that must agree are one thing.** A cash sale and the payment taken with it point at each other: they
    are written in one transaction, deleted and restored together, an edit of one asks about the other, and a purge
    clears the link. A backup made after one half was purged could not be restored (27688b8).

66. **`noHistory` and a result never meet.** Android 7-10 asks the phone's PIN on a screen of its own and returns
    its answer to the screen that asked; a `noHistory` lock screen was already gone, and the owner was locked out.
    The lock is tested on the oldest supported Android with a PIN-only phone.

67. **`inputType="numberDecimal"` filters by its own rule, not the language's.** Without `imeHintLocales` it passes
    0-9 and "." only: an Indonesian "2,5" became 25 in silence. Decimal boxes go through `DecimalBoxes` (run by
    `TextFit.relax`); a typed number is read by `Digits.parse`, never `toDouble`.

68. **A failing test hides everything after it.** Gradle stopped at `:shared:desktopTest` (CsvImportTest asserted the
    behaviour the same audit changed), so the app's tests never ran on 6a26212. A behaviour change and its test change
    go in one commit, and a green build is read only on the run that followed the fix.

69. **Measure against a book written the way a shop writes it.** The big book wrote each customer's lines side by
    side, and every read by customer looked twice as fast as on a real book, whose lines lie by the clock among
    everyone else's; lines drawn past today were clamped to now, and 70,000 of them sat "today". Seed by the clock,
    every run ending by now, and look at the day and month counts before believing a figure.

70. **On Android a big answer is read many times over.** A cursor's answer comes 2 MB at a time and the query runs
    again from its first row for each 2 MB: 275,000 rows took 10.8 s on the emulator and 0.67 s on the computer.
    Read big answers a slice at a time (`LedgerLines`, at most 400 ids — SQLite before 3.32 takes 999 values) or a
    window of time at a time, and measure on the phone, not only the computer.

71. **A suspend call only goes inside an inline lambda.** `joinToString { count(it) }` failed both builds
    ("Suspension functions can only be called within coroutine body"); `map { }` is inline.

72. **No heavy work inside a Compose page's drawing.** `remember(points) { PaymentHabit.forAll(points) }` ran on the
    window's thread; work it out in `produceState`/`LaunchedEffect` on Dispatchers.Default, as the phone's screens do.

73. **A DAO name is looked for before it is added.** `staffPaidBetween(from, to)` already summed the staff's pay for
    profit; a second one returning the day's rows failed both builds ("Conflicting overloads", 5a20ebf). As string keys
    (lesson 7): grep the interface for the name first.

74. **A local name hides the screen's own.** In `startReturn` a local `draft` is the ReturnDraft; the exchange's check
    `draft.isEmpty` meant the counter's ticket and failed the build (af5dd4d). Inside a scope with its own, say the
    screen's: `this@CounterSaleActivity.draft`.

75. **A `by` delegate needs its operators imported.** `var failed by androidx.compose.runtime.remember { … }` with the
    names written out in full still needs `import androidx.compose.runtime.getValue` and `setValue`; Photos.kt had
    none and :desktop failed ("has no method 'setValue'", 2d437cd). A file that starts using `by remember` gets the
    two imports first.

76. **A parameter made required is given at every caller, the tests' too.** `CashbookPane(dao, home)` and
    `ChequesPane(…, home)` were updated in Main.kt only; ScreenProofTest still called `CashbookPane(dao)` and
    :desktop:compileTestKotlin failed (ebccb87). grep `src/test` and `src/androidTest` as well as `src/main`.

77. **‹ › ( ) [ ] < > are mirrored by the text itself in a right-to-left page.** They are Unicode Bidi_Mirrored: in an
    Urdu line Android and Skia both draw "‹" as "›". The day book swapped them for RTL as well, so they were turned
    twice and both pointed inwards (the 2d437cd proof). A text arrow is written once, as for LTR; an icon arrow is
    what is flipped by hand (`Glyph`, `check_icon_direction.py`).

78. **A flow's "done" is written at its end, never at its start.** The language picker wrote "chosen" at step 1
    and the front door read it as the whole setup: a first run left at step 2 or 3 opened Home for good, its name
    and country never asked, and the unsaved country read as Pakistan (5 Nov 2026). Write "done" on the last
    step, route to the first unfinished one, and give an older install a way to count as done (`SetupProgress`:
    a "started" marker it never wrote).

79. **Whatever takes the keyboard as padding takes it out of what it hands on.** `KeyboardFollow.dialog` padded a
    sheet's content by the keyboard and passed the insets down whole; Material's sheet container (fitsSystemWindows)
    padded by them again, and on Android 15 the invoice sheet stood a keyboard's height above the keyboard, its form
    squeezed to nothing (6 Nov 2026). Hand children `withoutKeyboard(insets)`. And a test of "above the keyboard"
    passes a field 867 px above it: measure that the panel sits on the keyboard and the box is in the middle.

80. **Nothing read off the owner's photos goes into the repository.** Five customers' numbers and a customer's name from
    his photo of the Duplicate screen were written into a comment of a public file (6 Nov 2026), and the search tests
    had carried names from his real book since July; the whole history had to be rewritten (8 Nov). Say what a photo
    shows in words ("two numbers one digit apart") with made-up figures (0300 1234567, 0300 0000001) and made-up names,
    in code, comments, tests and commit messages alike — "shaped like the real book" means invented in that shape.

81. **A limit anyone can spend is a limit everyone loses.** A server-wide count an anonymous caller can use up (new
    shops an hour, files, one byte budget shared with live sync) is a switch that turns the feature off for every shop
    (7 Nov 2026: Waris vaults could stop live sync; throwaway shops could stop every khata link). Give each feature its
    own budget, cap what is new per day, let a shop spend only its own share, and sweep what was made and never used.

82. **A key in a copy is a key for whoever holds the copy.** What can be made again (Waris' W) stays out of every export
    and is made anew after a restore; a copy that leaves the owner's hands (secrets = false) carries no server's pass or
    keys (the customer pages'); a notice's tap carries a one-time ticket, never a fixed key — Android hands a task its
    first intent again from Recents.

83. **A phone cuts an icon to its own shape.** Android 12+ shows the splash mark inside a circle a third of
    the canvas wide (192 of 288 dp), and a launcher icon is cut to the phone's shape (a circle shows 72 of 108 dp,
    only the middle 66 dp is promised). The splash's rounded square and the icon's gold ring reached past both,
    so their corners went on some phones and not on older ones (10 Nov 2026, the owner's test phones).
    `scripts/check_icon_fit.py` (Strings CI) measures both; artwork is shrunk to fit, never stretched.

84. **`View` has no ELEVATION property.** Its animator properties are ALPHA, TRANSLATION_X/Y/Z, ROTATION(_X/_Y),
    SCALE_X/Y, X, Y, Z; `View.ELEVATION` failed both phone builds (11 Nov 2026). Elevation is animated by name
    (`PropertyValuesHolder.ofFloat("elevation", …)`) or through TRANSLATION_Z.

85. **A keyword is a whole word.** VoiceQuestion looked for its words anywhere in a sentence: "Saleem ka baqaya"
    held "sale" and asked for the month's sales, "Ali Wholesale" too, "Kalabagh" the profit, Urdu سودا the profit
    (12 Nov 2026, found while giving Hudhud names). Words match whole, with only the pieces their language joins to
    them (Arabic's article in front, Bengali's endings behind, an English plural of four letters or more); a phrase
    matches as a run of whole words; a phrase's words are set aside from name search only when the phrase is said.
    A chip's own label must be understood as its own question in all nine languages (HudhudWordsTest) — eight were not.

86. **A page that lets one child draw past its edges lets them all.** Home's root turned clipChildren off so Hudhud's
    crest could rise above his bar; the grid's ScrollView had clipToPadding off, so nothing clipped it any more and
    its tiles were painted past its own edges — under the morning greeting (12 Nov 2026, the 150% proofs) and over
    the header once scrolled. Taking clipToPadding="false" away did not help (4aee94d's proof still showed the
    tiles): a ViewGroup clips to its padding only when it HAS a padding. A scroll on such a page sits in a frame of
    its own, which clips it (`featureFrame`).

87. **An enum class is not a value.** `val w = VoiceQuestion.Window` failed every build of the tests ("does not
    have a companion object", 3b2e3de); import the enum and write `Window.TODAY`.

88. **A size set while a view is being laid out is drawn but never measured.** `doOnLayout { FitOneLine.fit(…) }`
    shrank the greeting's title inside the layout pass: View.layout clears the request the new size made, so the
    words were drawn on one line and the bubble kept two lines' height (a963f5d's proof). Change a size after the
    pass — `post { }` from the layout callback, as Home's balance does.

89. **A result comes back before its screen is resumed.** The voice-search box hands its words to onActivityResult
    while it still holds the microphone and the sound: Hudhud's quick answers (a choice of names) were said into that
    and lost, slow ones (a sum) came late enough to be heard, so "the speaker is on but he says nothing until Hear
    again" (the owner, 13 Nov 2026). Before the speaker or the microphone is used after another screen: wait for
    this one (`lifecycle.withResumed`, then the window's focus, then a breath).

90. **A recycled row keeps every transform an animation left on it.** A shared-element return, the platform's
    reenter transition and an item animator's move set translation, scale or alpha on a row; one cut off mid-flight
    and handed to another customer kept them — cards over each other and gaps between (the owner's photos, 1 and 13
    Nov 2026; making the row visible again was not enough). A row is made plain whole (`RowToScreen.plain`:
    translation X/Y/Z, scale, rotation, clip bounds, animation matrix), the list ends its animations before settling,
    and the first update after coming back has no item animator.

91. **Snackbar's swipe-away belongs to CoordinatorLayout.** A Snackbar (or a card shown like one) in
    `android.R.id.content`, a FrameLayout, cannot be swiped aside; the Saved card carries its own (`SwipeAwayLayout`).

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

**Roshan Khata is public on Google Play** (`com.innovation313.roshankhata`:
production access 16 Sep 2026, the first public release sent for review
19 Sep; the owner confirmed it public on 28 Oct 2026). A review on 28 Oct
called it unpublished from an old line here — read this section and the
owner's memory before saying anything about the store. Every upload needs a
higher versionCode (aab.yml refuses one not above the last). Data safety: NOT "no data
collected" any more (8 Nov 2026): customer khata pages are readable by the host, and
Firebase, ML Kit and the relays handle IDs; the answers are in the 8 Nov marhala 5 notes. The Windows MSI is on the
desktop-test pre-release (not signed yet).

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

**Data import from other ledger apps** is built on Windows only (`CsvImport`:
name, phone, balance as one opening entry each, in one transaction); the phone
has no such screen yet. Merging duplicate parties is built on both platforms.

Smaller and deferred: cashbook and cheque `createdAt` editing, a WhatsApp
number on the Help screen.

**Marhala 3, data and backup safety (M3-M9, M85, M86, M89-M92, L10-L14; CI only, nothing deployed):**
- Kill switch (M3): a process Firebase starts cold reads `live.json` once (`LiveControl.haltedAfterFirstRead`, 5 s + 5 s
  bound) before `takeCarried` writes a pushed parcel. Remove device (M4): the key cannot be rotated without re-pairing,
  so the two confirmation texts now say a lost device keeps the key and how to cut it off (stop live sync, pair again).
- Daily-copy restore (M5, `Snapshots.keepAccess`): this book's access keys and Waris secrets are read before the swap
  and written back after it by `Waris.restored`'s rule (removed heirs of both, this book's vault). RestoreCheck (M6) also
  counts products, plans, staff, schemes (by uid), seals (by count) and the book's own settings a copy lacks. Merge (M8)
  matches a customer by uid first. Waris card (M9) is deleted 10 minutes after the share returns (or when the screen next
  opens). Heir copy (L11) is loaded from the file by `ViewerSync.loadFile`, never one String.
- Drive: the shop's name is a property of the backup file (`shop`, M7; older files stream the JSON skipping the ledger);
  the photo archive uploads from the file and is skipped when its SHA-256 is unchanged (M89); a failed photo upload
  marks `drive_images_dirty` so the next run retries even when the text is unchanged (L10); half-downloaded restore files
  are deleted and swept after an hour (L12); the authorization request is bound to the chosen account (L13); lookups
  take the newest file (L14).
- Daily health check failing (M85) shows a bell row until it passes (`Snapshots.unhealthySince`). Bill lines survive a
  process kill behind the camera (M86), the Cashbook money form keeps its typed boxes across rotation (M90, partly: not the
  chips, the day or the picture).
- Erase and delete shop (M91, M92, `Businesses.endSharing`): before the books go, Waris is turned off on every relay and
  every customer link is stopped and its page taken off the server (`CustomerLink.stopAll` + flush), each shop, 20 s each.
  Best effort: a relay or the hosting not reached leaves its copy until its own sweep.

### 8 Nov 2026 review fixes, marhala 6B: the minor findings m8-m66 (words in nine languages, small code; CI only)

- **Words.** Net balance is "net" (khalis/शुद्ध) in Urdu, Roman Urdu and Hindi; Hindi's second reminder is a noun
  ("दूसरा रिमाइंडर"), Sindhi's step 2 the same noun as step 1; Bengali "Settled" is "পরিশোধিত"/"মিটে গেছে"; Arabic cheque
  "Cleared" is "تم صرفه"; Farsi Quotation is "پیشنهاد قیمت" and Proforma "پیش‌فاکتور", its Bills tile "فاکتورها"; the
  credit-limit help reads right in Arabic; six Arabic plurals have their own one/two/few/many forms; Arabic customer
  words say عميل except the walk-in's "زبون عابر", and the entry message opens "مرحبًا" (no "Mr."; Bengali and Farsi
  first messages were already neutral — Farsi's now bold the name); Urdu says "کھاتہ" (4 stray "کھاتا"), "گاہک" (not
  "کسٹمر"), "قسطیں" for Plans and "بعد میں" for Maybe later; Sindhi uses "اڌار", "ادائيگي", its own app name in the privacy
  and Drive-offer texts, Sindhi letters (کاتو, ٺهي, توله) and a full stop "." not "۔"; Farsi/Sindhi percent is "%";
  Hindi "रकम/बाकी" without the nukta; Bengali "রোশন খাতার/-কে" one form and "Android 13" in digits; the FAQ names the
  Backup screen by its real title. Roman Urdu: one spelling each for udhaar, wusooli, naqd, qistein, zaban, nahi,
  mohtaram, indiraaj, -ein — and `ROMAN_GLOSSARY` in check_strings.py warns on the old ones; its Add buttons are sentence
  case like English. **The brand is "Innovation 313" everywhere** (strings, both privacy policies, the khata page, the
  homepage, README); `SCRIPT_EXEMPT_PREFIX` for screen_privacy_ is gone, so those texts are now held to their script.
- **New keys (nine languages):** `party_entries_hint` (an empty customer page says to tap I Gave / I Got),
  `home_backup_to_drive` (Home's chip said "Continue with Google" in an app with no account), `po_saved`,
  `business_switch_open_named` (the shop's name inside the sentence, not glued after the verb). `po_book` says "On record"
  instead of "Book"; `coach_desc_business` names Counter sale and the sales report.
- **Code.** The saved card reads the number the DAO gave the entry (the entry in hand had none); Windows' counter line is
  "Tax %: 33"; the language list says "Bahasa Indonesia"; the "already open" box speaks the computer's language;
  `DayCloseMath.startOf` finds a day's first moment through the calendar (Cairo, Beirut and Gaza change the clock at
  midnight; java.time is not used — minSdk 24); a cleared cheque's "I got" carries the cheque number as its reference; a
  first Close the day's audit row has its id; a helper's copy (and the Waris snapshot) carries no private party note and no
  cost price; `LogText` scrubs file paths and names; the UDP "who has the book" call is answered only for local addresses;
  the register's dates carry the year; the inspector's monogram skips a word that starts with a figure, its no-expiry card is
  only for AGRI/MEDICAL, its no-total note moves to a new page when the page is full; a card's cut name has no space before
  "…"; Selection bar's Delete is no longer capped at 130 dp; ReminderWorker.countDue lost two unused parameters; the khata
  page's English line under an error is its own LTR block; api.php ids end at `\z`; the relay finds its store beside
  public_html like the khata page; Supabase compares the keep-alive secret in constant time; compiled `.pyc` left git.
- **Skipped / owner:** m20 (a removed staff member keeps his name by design — shifts and audit rows are filed by name, R84),
  m44 (the phone's bell has no late-payers row while Windows' does, and Windows' late line is red where the phone's is gold:
  which bell is right is the owner's call), m58 (the Kitab ki Muhar family keeps "کتاب" as the feature's name; only the stock
  count's word changed), m29 is a comment only. Sindhi and Bengali wording marked in the result file for a native read.

### 8 Nov 2026 review fixes, marhala 5: privacy and Play (M39, M93, M94, M98, L156-L158, m96-m100; text only)

- **Both privacy policies** (`docs/privacy-policy.html`, `-ur.html`, dated 8 Nov 2026; **bump the date on every edit**, m100):
  new section 23 (customer khata link and Mera Khata: name, shop, balance and side, last-written day kept readable on
  khata.ehtisham.shop; anyone with the link opens it; Stop removes it; 180-day idle expiry; the hosting sees address and
  time) and section 24 (every place the app contacts: Drive, the relays, FCM, the hosting, the time services, GitHub's
  stop-switch file, Google's scanner); former 23-28 are now 25-30. Section 22 says Cloudflare and the hosting only
  (Supabase is out of `LiveCloud.others`, m96). The permissions "Internet" line lists those uses, the uninstall bullet says
  what outlives an uninstall (Drive backup, an heir's vault until Waris is turned off or 1,095 idle days, khata pages), and
  a data-protection bullet names Innovation-313 as controller of the little it holds, the legal basis (consent, the feature
  asked for) and the right to complain (GDPR/UK GDPR/DPDP, L158). **Any new feature that sends anything off the phone adds
  itself to section 24, the listing's privacy line and the homepage in the same commit.**
- The homepage and all seven store listings no longer say "we run no server" / "never collect" alone (M98); `photo_privacy_note`
  in nine languages no longer says "never uploaded" (the Drive backup carries it, m97).
- **Play Console answers, Contacts prompt and targetSdk 37 are the owner's / a later bump**: see phase5-result.md in the session
  notes. Data safety is no longer "no data collected" (m99): see the answers there.

### 8 Nov 2026 review fixes, marhala 7: Windows and growth (M80-M84, M95-M97, L132-L139, L160-L168, m84-m90; CI only)

- **Windows (M80-M84, L132-L139):** a Windows account named in Urdu, Hindi or Bengali made the temp folder non-ASCII and
  SQLite's native library could not load: `asciiTempFolder()` moves `java.io.tmpdir` to `ProgramData\RoshanKhata-tmp`
  (else Public, else the drive root) before anything loads (M80). The window's scope is supervised with a handler: a failed
  write (disk full, antivirus) is written to errors.log and told as "not saved" (`save_failed_not_saved`) instead of
  ending the app (M81; the typed form is gone by then, the words say to enter it again). The firewall hint says private
  AND public networks, nine languages (M82). The MSIX manifest turns `desktop6:FileSystemWriteVirtualization` and
  registry virtualisation off with `unvirtualizedResources`, so a Store build writes the real AppData and a reinstall
  keeps the book (M83; **check with a real Store package before upload**). The counter's open ticket is kept by the
  window above Book (`counterDraft`), so App Lock coming back no longer drops it; half-typed dialogs still go (M84). The
  first input after a long quiet locks first (a sleeping laptop, L132). `PRAGMA synchronous = FULL` (L133). Automations'
  "said today" marks are per business (L135; business 1 keeps its old key). PDFs go to OneDrive\Documents when that
  exists (L136). The bell and chips are worked out again when the day changes (L137). Customer Excel keeps a phone's
  first 0 as `="0300…"` (L138, `BookExport.phoneCell`). A lock that throws IOException (a network share) runs without the
  guard instead of saying "already open" (L139). m84 `setReceipt` synchronized; m85 `rk-copy-*.tmp` older than an hour
  swept; m86 the desktop.yml comment corrected (a queued run IS cancelled); m87 stale header comment; m88 a clock set
  back makes the update check due; m89 the mail body is capped at 900 letters and the whole report goes to the clipboard;
  m90 the title bar follows the dark theme (`DwmTitleBar`, JNA named in :desktop's dependencies).
- **Khata-link install path (M95):** the web page's Play link carries `referrer=khata=1`; `InstallSource.checkOnce` (Google's
  free Install Referrer library, 2.2) asks Play once on a first start and GateActivity opens My Khata, whose empty text
  says to open the shop's link, instead of the setup. A flag only: the page's key is NOT put in Play's address (Google
  could read the page). The setup button clears it. Needs web/khata/view.js uploaded.
- **Rating (M96):** `AppRating.maybeAsk` on Home, only after 5 entries on 3 days and 3 days since the first, once in 120 days,
  Play's own card with no question of ours first and no store listing opened when it does not show; the Help button now
  opens the listing (Google forbids tying the card to a button). `AppRatingRuleTest`.
- **Listings (M97, L163-L165):** ar, fa, hi-IN, bn-BD and id rewritten from en-US (counter, documents, Radar, staff, 25 trades), each
  description opening with the shop's name in its own script; a Zakat / Qarz-e-Hasna bullet in every listing; en-US title
  "Udhar". **Paste with the upload that carries the counter, as the notes in each file say.**
- **Growth (L160, L166-L168):** PDF store links carry `utm_medium=pdf`, the landing page `utm_source=landing`, More has Share
  Roshan Khata (`utm_medium=share`); og tags on the landing page and the khata page (no image: the khata page is private
  and an image needs an absolute address); JSON-LD on the landing page.
- **Not done:** L134 (shared desktop-test release: needs a workflow redesign), L140 (signing), L141 (roadmap), L161 (new
  screenshots), L162 (a DOWNLOAD link in Windows PDFs, which are page pictures), L169 (a WhatsApp number cannot be written into
  this public repository), L170 (the owner's decision on posters).

### 8 Nov 2026 review fixes, marhala 8A: the mediums no other phase covered (M2, M10, M11, M13, M14, M20-M22, M33, M38, M60, M61; CI only)

- **M10/M11:** `AgingMove` and `SeasonBook.Line` carry `id`/`pairedEntryId`; Dues by age and the season queue net a paid-now sale's two lines (`PaymentRadar.netPairs`) first, so today's cash neither makes an old debt look young nor pays an old season's credit. **M13:** `SaleInsights.lastMonthSoFar` (last month up to the same moment) is what the percentage and its figure compare. **M14:** shift hours are exact, rounded to 2 places only for display and the weekly sum (not by the currency's decimals).
- **M20/M21 (Windows):** a tick on a customer the search hides is dropped; the credit-limit warning treats a paid-now sale as leaving the balance as it was. **M60:** the poster's WhatsApp share tries each package (no `resolveActivity`, which Android 11+ answers empty). **M61:** the notification reason shows at most once a week. **M38:** Home's empty-book card (`home_add_first_customer`, nine languages). **M2/M33:** `live_relay_help` says a small sealed change may ride in Firebase's message; the Drive section is titled "Cloud backup" (auto is a switch below it). **M22:** `ACCESS_LOCAL_NETWORK` declared; the runtime ask comes with the targetSdk 37 bump.

### 8 Nov 2026 review fixes, marhala 8B: what earlier phases skipped as "design" (M18, M40, M57, M69, M71, M87, M88, M90; CI only)

- **M40:** money PAID to a supplier (an "I gave" with no goods) now asks "How was it paid?" and starts as Cash on both platforms, so
  `khataCashBetween(given = true)` finally sees cash leaving the drawer; goods sent back to a supplier carry none; a customer's sale
  still carries none (its paid-now "I got" half does). Old supplier payments stay unmarked (no migration: their meaning is unknown).
- **M69 (phone):** Recent sales is a searchable list of all 60 days (`SaleSearch`, shared with Windows' `ticketsMatching`) and has a
  Credit note button: the 60 days' returns, searchable, each openable as a credit note or voided (`askVoidReturn`, VOID gate, the
  DAO's `returnVoidRefusal`).
- **M71:** the invoice options sheet reads the extra charge with its sign (`CalcPad.signedFigure`): a ticket's round-off down is a
  negative charge and Apply made it positive. **M57:** every money argument of a phone sentence is `Format.moneyLtr` (51 places) and
  names/bill numbers in the supplier lines are `isolate`/`ltr`.
- **M87/M88/M90 (phone): one pattern for forms that must outlive their screen.** Small forms: `ui/FormDraft` (the Cashbook form's
  pattern, shared) — a screen `open`s a draft with the form's text boxes, `closed` from the dismiss listener (a rotation is not a
  close), `save`s in onSaveInstanceState and reopens the form from `saved()` in onCreate: Close the day, Cheque (new; direction and
  due day ride in `arg`), Plan (new, and a payment), Staff (person, advance/pay; the PIN is never kept), Edit entry. The invoice editor
  saves its whole draft as one JSON text (`draftJson`/`restoreDraft`) and puts it over a new or an edited invoice. Long forms —
  stock count, return, purchase order — keep their text in `PrefsDraftKeep` (`DraftKeep`, shared interface; the computer passes none):
  CountDraft/ReturnDraft/OrderDraft have `snapshot()`/`restore()`; a screen opened fresh clears the keep (`clearOnFreshStart`), a
  rebuilt one reopens the form (count and return with who allowed it). An order being edited (not new) is not kept. Test: OrderDraftKeepTest.
- **M18:** the Eid ul-Adha poster draws the address/email line (the sign-off closes up 10-24 px to make room only when one is ticked);
  the desktop copy is regenerated.
- **Left, with the reason:** M84 half-typed Windows dialogs are discarded when App Lock re-locks after 15 idle minutes — keeping them
  needs the book composed behind the lock (against "nothing composed until the PIN") or every dialog's state hoisted; the counter's
  ticket survives (the valuable one). M27 the customer page's counter `q` follows the device clock: a fix needs the counter in the
  synced book or a server read before every send (hosting load); neither is built.

### 8 Nov 2026 review fixes, marhala 8C: low findings (L1, L15, L18, L20-L22, L32, L34, L36-L38, L40, L42; CI only)

- L1 a failed kill-switch read is retried after 10 minutes, not 6 hours. L18 `CsvImport.decode` reads a file's bytes as UTF-16 (mark), UTF-8 or
  Windows-1252 (Excel's "CSV (Comma delimited)"); the customer and product imports use it. L20 a khata sale's invoice rate keeps 6 decimals
  (3 x 333.333333 = 1,000). L21 a sale line's cost keeps 6 decimals (no-decimal currencies were off up to 6%). L22 dead stock no longer counts
  goods given to a supplier (customer parties only). L32 the phone's register prints its footer on every page. L34 Windows' receipt: signature,
  advice and season ask EDIT_BOOK and write "entry_edited" in who did what. L36 faces decode off the window's thread (`Photos.rememberBitmap`).
  L37 the invoice form's party hits are `remember`ed. L38 the plain invoice print runs to as many pages as it needs, totals on the last.
  L40 turning the relay off while offline leaves the FCM token deletion owed and tried again (`delete_owed`). L42 WarisWorker retries a
  failed daily run and passes a cancel on. L15 was already fixed (the rollback passes `allowedBy`).

### 8 Nov 2026 review fixes, marhala 8D: low findings (L63-L103; CI only)

Each point read in the code first. Money and data: `Calc.evalAmount` rounds to the shop's coin ("1000/3" is a whole coin, L69);
`InvoiceMath.totals` rounds every step (L68); the sales register's SQL takes the coin's decimals as `:scale` (KWD/BHD, L74;
`salesRegister`/`salesAdjustments`/`walkInSales*` gained `scale`); stock value counts no negative shelf (L73); `Aging` and
`PaymentHabit` read `Money` instead of 0.005 (L87); `Digits.decimalComma` is also true for an Indonesian shop with the app in English
(L72); `updateEntryWithItems` never un-bins a row a stale screen wrote back (L70); a customer's "delete forever" notes the RK number first
(L67); `LiveSync.MONEY_TABLES` gained products, schemes, staff (L76); Insights' "Given" uses the sales rule (L80); the "yesterday's
drawer not counted" bell row counts cash that moved, not only counter tickets (L81). Gates and audit: customer/supplier type change
asks EDIT_BOOK and is written (`PARTY_TYPE_CHANGED`, L79); bin restore/purge/empty are written (L75); Windows cheque delete asks first
and is written, the phone's is written (`CHEQUE_DELETED`, L89); Windows Khata Report PDF asks EXPORT (L82); a cashier's Follow-up shows
no Radar (L88); a read-only phone cannot open the counter (L90). Forms: Add Party stays open on a missing name and asks before
discarding (L63); `AppDialog(dirty = …)` asks before Esc/✕ throws typing away — used by Add bill and Edit bill (L83; the other
Windows forms still close at once: pass `dirty` as each is touched); a customer without a number offers the edit form (L65); Windows has
Add product (`ProductEditDialog` with id 0 → `addProduct`, L91). Words: "Tax" for an amount, "Tax %" only for the rate (`pos_tax_total`,
L92); invoices in the Urdu/Sindhi restore warning (L93); Arabic/Farsi menu names carry the English too (L94); counter slip names the
sides (L85, text slip only). Phone: no "never backed up" nag on the first day (L64); crash file names in English digits (L95);
AutomationWorker sets the default locale to the app's for its run (L96); Waris notice and "link stopped" toasts use `AppLanguage.context`
(L97, L98); shared Compose names `TextAlign.Start` (L99); HintFit lets a fixed-height box grow (L100); a note for Android Go phones on
Android 10 or older (`paisa_access_go`, L101); backups saved to Downloads are media-scanned on Android 7-9 (L102); the invoice goes to
WhatsApp Business when only it is installed (L103). New strings (nine languages): audit_cheque_deleted, cheque_delete_confirm,
audit_party_type, audit_bin_restored/_purged/_emptied, pos_tax_total, paisa_access_go. Not done: L66 (Pashto, Punjabi: a language needs a
checker), L71/L77 (two devices clearing or converting one document: a conflict rule), L78 (four reminder counts), L84 (the day report
hidden with the big-shop tools off was an earlier audit's choice), L86 (phone totals whole book, Windows by tab).

### 8 Nov 2026 review fixes, marhala 8E: low and minor findings no phase covered (L142-L155, L159, m1-m7, m67-m83, m91-m95, m101-m103; CI only)

- **Phone:** a rotation or theme change no longer counts as leaving the app (`isChangingConfigurations` in the lifecycle
  watcher: it stopped live sync and queued a backup on every turn); Khata's order, side, tab and day filter survive a
  rotation; Follow-up reads only while on screen (`repeatOnLifecycle`); Sales report's restored range picker is closed
  (its callback was the old screen's); the stock adjust and count run on `AppScope` and say a failure (a failed count
  read "0 saved"); the batch return form stays open over a refused value; a save waiting for the Downloads permission
  survives a rebuilt screen; Android 7.x takes down yesterday's automation notices (no `setTimeoutAfter` before API 26);
  an automation whose computation failed is not marked said; the empty Recent sales says `pos_tickets_none`; the
  after-sale daily copy is written at most once in 30 minutes; the Drive content signature is streamed; PushWake's 8 and
  10 second limits hold (`within`: its own thread, the caller stops waiting); cleartext is refused by default on Android
  7-8 (base-config); an SMS-capable app is never read or listed (`neverRead` asks the phone, a `<queries>` entry);
  notification access has its disclosure (`paisa_disclosure`, nine languages); the rate note no longer says "instead".
- **Printed:** a receipt's discount line no longer includes the points spent (they have their own line) and a line sold
  from two batches prints once; credit and debit notes use the newest document's design (`newestDocumentTemplate`) and
  a walk-in credit note names "Walk-in customer"; the footer strip's page is filled on dark designs; non-invoice
  headings are upper case like INVOICE; percent labels use the reader's decimal mark (`Format.percent`); the Khata
  Report PDF has its page line on every page; the rate-list picture prints native digits when chosen; PDF DOWNLOAD links
  reach pages under nested /Pages nodes (a 12-page statement).
- **Money rules:** the Radar's "nothing left" and the counter's checks use half the shop's smallest coin
  (KWD 0.0005), not 0.5/0.005; the Seasons median of an even count is the mean of the middle two; the report's expenses
  skip only counter refunds ("Return" by hand counts); a shift's refused close says it in the owner's words.
- **Windows:** purchase-order Send reads off the window thread; the phone-pull book is written straight to bytes.
- **Release:** `check_16kb.py` reads an .aab and aab.yml runs it (x86_64 Filament); README says it is published; the
  notes (DriveBackup header, OTHERS_MS, NumberWords, 3.5 px/pt) say what the code does.
- **Left:** m1 (LiveSync.apply/snapshot are used by tests; removing them means rewriting those tests), L146 (auto-hold
  an open ticket on stop: writes held sales on its own, the owner's choice), L153 (known, the streamed restore), m101
  (the feature graphic is a picture the owner re-renders), L152's full temp-file stream (WifiSync takes bytes), m80's
  24-hour time, m68's second raw-pixel view (not found).

### 8 Nov 2026 review fixes, marhala 8F1: low items earlier phases skipped (L2, L4, L5, L6, L7, L9, L19, L23, L33, L35, L39, L49, L51, L53, L57, L59; CI only)

- **L2:** the copy's log line is held (`LiveRound.holdFrom`) the moment it is read, before the book is exported, on the computer's give and the phone's send; a failed give lets it go (`releaseEarlyHold`). **L4:** `takeJoins` runs only after the sender is a member of the book. **L5:** a relay parcel that opens but is not a parcel (JSONException) is passed over and noted, so the cursor still moves.
- **L6/L53:** the live port judges a round's length before it unseals the book key, and the body has one 90 s deadline (`ROUND_BODY_MS`). **L49:** a scanned code naming 127.* is refused (`WifiSync.allowLoopback`, tests only). **L57:** the pull answer is a file streamed from the computer's disk (`receive(giveFile)`), not a byte array.
- **L51:** a PIN wait is also kept on the monotonic clock while the app runs (`Access.monoUntil`; only for callers using the real clock). **L59:** a secure phone that answers NO_DEVICE_CREDENTIAL is asked through the system credential screen (OK opens, else the app closes); only a phone with no screen lock is let in.
- **L7:** the phone's whole-book fingerprint waits for the charger (or 24 h). **L9:** Waris K_OFF is a list of unfinished turn-offs. **L19:** a line split across batches keeps six decimals (a carton's bottles were off by 0.004). **L23/L35:** Who did what is checked a slice at a time (`AuditChain.verifyAll`, `auditSliceAfter`) and again when the newest row changes. **L33:** the Khata ledger's figure columns grow with the text size. **L39:** an invoice design is applied by `setInvoiceTemplate` (a status another device moved stays); the cheque edit already reads the stored row.
- **Not done, with the reason:** L3 (relay-only fingerprint needs a handshake for "same moment"), L8 (a device id on the Waris vault changes all three relays), L43 (the statement's counting pass is the render itself; a dry layout is a rewrite), L52/L61/L62 (what to do when the PIN file is edited, a passphrase backup, counter roles on Windows: the owner's decisions), L54 (the lock-screen face is already the title alone, and titles carry no figures).

### 8 Nov 2026 review fixes, marhala 8F2: the last skipped and partly fixed items (L81-L85, L106, L109, L111-L113, L134, L152, L162, m80; CI only)

- **L109:** a return of a closed shift is not voided (`returnVoidRefusal` asks `closedShiftsCovering` like a sale's void; `pos_return_void_shift_closed`, nine languages, worded on both counters).
- **L106:** a customer chosen after a weighed line moves the line's `labelShelf` with its list price, so the re-price is not a manual price change. **L111:** Hold asks first when payments or a promised day were typed (`CounterDraft.holdLoses`, `pos_hold_loses`, `pos_hold_anyway`; both platforms). **L112:** the phone opens the drawer when a cash sale is saved, printed or not (`TextReceipt.kickDrawer`, silent, the printer used last); Print no longer kicks it again. **L113:** the phone's receipt picture has one text direction for the whole slip (the app language's), not each line's first letter.
- **L81:** the evening notice counts cash moved TODAY (it used the drawer's since-the-last-count cash, so an uncounted drawer made every evening say "moved"). **L82:** the phone's Khata Report Preview, Share and Download ask EXPORT and are audited (`needExport`). **L83:** Windows' product, staff (person, advance/salary) and cashbook forms ask before Esc or the cross throws typing away (`AppDialog(dirty)`). **L85:** return slips and the Windows counter's thermal slip name who owes whom (`ReceiptBalance.named`).
- **m80:** `DateWords.clock` also turns "hh:mm a" into 24-hour where the shop's country writes it; the phone's date-time picker and the poster's time picker follow the shop's clock, not the phone's. **L152:** a pulled-book copy a killed app left in `to-phone` (older than an hour) is deleted when the next pull is prepared (the streaming itself was already done in L57).
- **L134:** desktop.yml does not upload an MSI when the release already holds a newer commit (`compare` says ahead): an old push finishing last no longer makes the old MSI the new one. **L162:** a saved Windows PDF carries a link over the DOWNLOAD pill (`Canvas.links`, noted by `PdfBranding`, written by `PdfFiles.write` as a PDAnnotationLink to the tagged Play URL). **L141:** Windows has Add product since L91; its empty-list words (`no_products_pc`) now say so.
- **Left, with the reason:** L108 (a shift's drawer ignoring khata and cashbook cash: those rows carry no `createdBy`, so one person's share cannot be told from another's without a schema 45 and a rule for who owns an unattributed row — the owner's call); L141's rest (khata link, seal, Waris, payment voice on Windows: a roadmap); L161 (new store shots need a seeded counter/documents book and an emulator run to be looked at); L153 (a streamed restore: BackupCodec.parseFile and the restore core both hold the parsed book — the plan: stream-parse table by table into a temp book file, restore from it in one transaction, keep the in-memory path as the fallback, and a test that restores one book both ways and compares fingerprints; not done blind, with no compile or run here).

### 12 Nov 2026: handover fixes (calculator range, the seal never alone, SMS bold; CI only)

- **Calculator range** (`Calc.MAX_ABS` = 9,999,999,999,999.99): a Double is exact to the paisa only to about 13 digits
  and the paisa, so every number typed, every step of a sum and its answer are held to it; past it `eval` returns
  null (every box's own "not a figure"), never a figure whose last digit is a little wrong. `Calc.tooBig` says why, and
  both calculators show `calc_too_big` (nine languages) instead of a blank answer. CalcRangeTest.
- **The seal never opens a page alone.** Invoices (all designs): before the terms and signature a design checks
  `InvoiceTemplateKit.closingRoom` (terms + signature as laid out, and the seal with its gaps when one prints) against
  the maker's strip (PAGE_H - 96), so the terms, signature and seal move to the next page together. The Khata Report
  keeps its last rows (three at most) with the seal (`keepWithSeal`). The statement's own rule is bf5bca6; the
  register's seal stands between its signatures. The computer's invoice copies are regenerated
  (`gen_desktop_invoice.py`). **The stamp animation (StampMoment) assumes the seal is on the last page** — keep it so.
- **SMS:** the payment block's heading is bold in every language (`pay_heading`, *How to pay*); an SMS reminder
  strips the whole message (`Reminder.buildMessage(forSms)`), not only the body.
- **Lost:** the handover's local calculator commit (7c0b836, uncompiled) was never pushed and is not in this
  repository; c014f1b / 671cc21 / cc0b22f (round keys, the form pad) are.

### 12 Nov 2026: Hudhud, the shop's assistant (Marhala 1; the owner's chosen place and name; CI only)

Phone only (Windows has none yet). Named Billy in the mockups until 12 Nov (the owner: Hudhud, an Islamic name).

- **Where:** a bar at the foot of Home only (`hudhudBar` in activity_main.xml; the grid ends above it, so it never
  covers a tile): his face (`hudhud_face`, its ring centred on the 54 dp box, the crest up to the bar's edge — the
  face's -21 dp top margin keeps the FrameLayout the box's height), the box ("Ask Hudhud…") and a green microphone,
  as a chat's message bar. Face → sheet, box → sheet with the keyboard, microphone → sheet listening
  (`MainActivity.bindHudhud`; he is mirrored in right-to-left and nods on Home the first 3 times). Insights' "Ask
  by voice" opens him listening: its own dialog, `askByVoice`/`compute` and `vq_understood`/`vq_not_understood` went.
- **The sheet** (`ui/HudhudSheet`, `sheet_hudhud.xml`, theme `ThemeOverlay.RoshanKhata.HudhudSheet`: the frame
  itself is `hudhud_sheet` with 24 dp top corners, so the colour runs under the navigation bar): his pose over a
  warm glow at night (`hudhud_glow`), name and state ("Listening…", "Looking in your book…", "Speaking"); the
  speaker switch (prefs "hudhud", kept for good); "Heard: …" for a spoken question, "You asked: …"
  (`hudhud_you_asked`) for a typed one or a chip; the answer card (label, one figure, lines, up to five customers
  that open their page, a note) with Open (the one screen it comes from) and Hear again; chips; box and microphone.
  Poses cross-fade (`hudhud_idle/listening/thinking/talking/happy/alert`), thinking only after 300 ms, glad of good
  news (a hop the first 5 times, `Motion.firstTimes`), concerned while something waits; three breaths on opening;
  a nod per spoken word. With animations off he changes pose without fading and never moves.
- **His ears** (`data/Hudhud`, shared, HudhudTest): "how" asks for a help page first; then a customer or supplier
  the question names (`NameSearch.confidentMatch`, else up to five strong hits near the best — a choice, never a
  guess), unless it asks a figure other than to get / to give; VoiceQuestion's eight figures; the backup; the
  drawer; a help page by IDF over the FAQ and the feature sheets (in the app's language and English, and both Urdu
  scripts on an Urdu phone); salaam; hello; else "I can't answer this yet" with the chips. **VoiceQuestion now
  matches whole words** (lesson 85), learnt faida, baqi, bika, sell, expiring, terbesar and the app's own chip
  phrasings; Farsi بدهی is what the shop owes.
- **His answers** (`data/HudhudBook`) only READ, each by the rule of the screen they name: sales by
  `salesTotalBetween` over Insights' day and month, profit by `Profit.thisMonth`, to get / to give by `PartyTotals`,
  who owes most from the same balances, the drawer by `DayCloseMath.drawerByTheBooks`, the backup as the bell judges
  it. Every figure and the drawer need REPORTS (a cashier is told only the owner or a manager sees it; the drawer's
  expected cash is not told before a count). A feature sheet added later does not build until `screenOf` names its
  screen.
- **His voice** (`ui/HudhudVoice`): the phone's own offline voice only (`PaymentNotice.offlineVoice`; a network
  voice would send names and amounts to a server), Urdu and Roman Urdu said from the Urdu words with ur-PK,
  USAGE_ASSISTANT. **Voice in, voice out:** he speaks only an answer to a spoken question; typed or a chip, he
  writes it and offers Hear again. An amount is never said aloud while Home's balance is hidden (hidden by default),
  and he says so once (`hudhud_quiet`); no offline voice for the language: `hudhud_no_voice`. The engine wakes only
  when the microphone opens and is shut when the sheet closes.
- No new permission (the microphone is the phone's recogniser, RecognizerIntent, as voice entry); the app sends
  nothing (the recogniser is Google's own service, as for voice entry). Both privacy policies' section 11 says so
  (12 Nov 2026). Strings (nine languages): hudhud_* (18). Tests: HudhudTest, VoiceQuestionTest (+5),
  HudhudWordsTest (desktop: every chip label in nine languages), HudhudOnDeviceTest (the face opens the sheet, a turn
  closes it, a made-up customer asked by name is answered with his balance); proofs 50-hudhud-answer,
  51-hudhud-help.
- **Not yet (later marhalas):** "show me" (he points at the screen), the tour in his words, his face on
  notifications, Windows.

### 12 Nov 2026: Hudhud, Marhala 2 (the owner's yes to the review's four recommendations; CI only)

- **An entry said as a fact** ("Ali ko 500 diye": an amount, which way it went, no question word — `Hudhud.QUESTION`;
  kya/kia/کیا ask only as a sentence's first word, later they are "did"): `Ask.Entry`. Nothing is written: the card
  says who, how much and which way, and "Fill in the entry" opens his page with voice entry's own extras
  (`HudhudBook.entryForm`), where the form's checks run and the owner saves. Several or no names: each offered,
  nearest first (voice entry's picker), one tap opens the form filled in for him. A read-only phone is told it cannot.
- **Voice entry reads who gave** (`VoiceEntry.customerActs`, both the Khata microphone and Hudhud): "Ali ne 500 diye"
  is money IN — Urdu and Hindi mark the doer with ne; "main ne", "maine", "ham ne" (and "aap ne") are the shop, any
  other doer the customer. It read as "I gave" before, the wrong side of the book. VoiceEntryTest (+2).
- **When he will pay** (`Ask.WhenPays`: a named party with "kab dega", "when will … pay"; `WILL_PAY`, or `PAY` beside
  `WHEN`/`WHO`): the Payment Radar's reading by its own rules (REPORTS, `RadarUi.on`, customers only; his lines,
  promised day and PromiseLog) with `RadarUi.reasons`; said aloud is only the likely days ("Likely 8 Nov – 12 Nov"),
  never the judgement. No name ("aaj kaun dega", the chip `hudhud_chip_likely`): who is in his usual paying days
  (`PaymentRadar.likelyNow`).
- **Spans of days** (`VoiceQuestion.Window`, `windowOf`: today, yesterday, this week, last week, this month, last
  month in nine languages; Urdu script's yesterday only as "کل کی/کا/کے" or "گزشتہ روز" — کل is also "total"):
  `Ask.Period` for a sale or a profit over a span other than the eight figures' own; `Hudhud.span` gives [from, to)
  as the screens read it (the week from the shop's own first day). Sales by `salesTotalBetween`, opened on the sales
  report over the same days (`SalesReportScreen(startPeriod, startCustom)`, `SalesReportActivity.EXTRA_PERIOD /
  EXTRA_FIRST_DAY / EXTRA_LAST_DAY`); profit by `Profit.between`, opened on Insights for this and last month (a day's
  or a week's profit is on no screen: no button). `Ask.Moved`: what a named customer paid or took over a span ("Ali
  ne kal kitna diya"), a figure question winning over it.
- **The morning greeting** (the owner's rule: "Assalam-o-Alaikum" in Pakistan and the Muslim countries — the OIC's
  member states among the app's countries, checked on Wikipedia 12 Nov 2026 — at any hour; elsewhere the hour's own
  greeting, `Hudhud.greeting`): once a day, the first time Home comes forward after the tour, a bubble between the
  grid and his bar (`hudhudGreeting`, the grid ends above it) with one line of counts — whom to remind
  (`Automations.collect`, now with `top` and `likelyIds`), else the bell's rows, else nothing waiting — never an
  amount, never aloud; a tap opens `Ask.Today` on his sheet (`HudhudSheet.open(today = true)`): the greeting, those
  counts, the bell's own rows and the first three to remind. ✕ puts it away for the day; More → Morning greeting
  (`ui/HudhudGreet`, prefs "hudhud") turns it off. "Good morning", "aaj ka khulasa" and the chip
  `hudhud_chip_today` ask for the same summary.
- Strings (nine languages): hudhud_sales_*/profit_* (9), hudhud_fill_entry, hudhud_entry_check,
  hudhud_radar_customers, hudhud_likely_none, hudhud_radar_see, hudhud_greet_* (8), hudhud_chip_today,
  hudhud_chip_likely; plurals hudhud_greet_remind, hudhud_greet_alerts. Tests: HudhudTest (+7), HudhudWordsTest (his
  own chips in nine languages), VoiceEntryTest (+2), HudhudOnDeviceTest (+2: the entry offered, the greeting once);
  proofs 52-hudhud-greeting, 53-hudhud-today, 54-hudhud-entry, 55-hudhud-radar.
- **From the proofs (the same evening):** Home's grid sits in a frame that clips it (lesson 86: its tiles showed
  under the bubble);
  the greeting is one line at any text size (`FitOneLine`: "Assalam-o-Alaikum!" left its "!" alone at 150%); only
  the sheet's middle scrolls, Hudhud at the top and the chips and box at the foot (`hudScroll`: a help page or the
  Radar's reasons pushed the box under the navigation bar), each new answer read from its top; what was asked is
  isolated in its sentence (`Format.isolate`: a question that begins with a figure keeps it in front in an Urdu
  line — an English question wrapping in an Urdu sentence is the normal layout, not a fault); the bell's expiry row reads
  "Batches expiring within 60 days: 1" (it said "1 batches"; the bell's other counts still need plurals).

### 13 Nov 2026: Hudhud listens himself, says every answer, talks a little (phone; CI only)

The owner's round: no voice-search box ("Hudhud se direct poochhein"), an answer said without Hear again, two or
three ways of speaking, "aap kya bata sakte ho" and a little haal-chaal, and no growth in the app's size. Nothing
bundled: his ears and voice are the phone's own (+0 MB). Not tried on a real phone.

- **His voice says every answer while it is on** (the owner: "jab upar on hai to neeche bhi awaaz aani chahiye"),
  however it was asked; the old "voice in, voice out" is gone. The bug: an answer was said the moment the
  voice-search box gave its words back, before the screen was resumed (lesson 89) — now `sayWhenHere`: resumed, the
  sheet's window focused (at most 1.5 s), 250 ms, then said; not said if the app was left meanwhile (10 s). He turns
  to speak as the answer lands. A part the engine drops is tried once more (700 ms), an 8 s start watchdog, then
  `hudhud_voice_failed` on the card — never silent without saying so. An amount is still not said while Home's
  balance is hidden. His greeting (now with the owner's chosen name, `hudhud_greet_named`) is said once a day when
  the sheet is opened just to talk (`HudhudPrefs.helloDue`).
- **His voice** (`HudhudVoice`, `HudhudStyle`, `HudhudPrefs`; More → Hudhud's voice and the sheet's "Voice
  settings", `HudhudVoiceSheet`): the phone's offline voices only (`PaymentNotice.offlineVoices`, by name so "Voice 1"
  stays Voice 1, at most six offered), three styles — Gentle 0.95/1.0, Lively 1.08/1.15, Serious 0.9/0.86 (rate/pitch)
  — heard at once when chosen, the phone's own voice downloads (`ACTION_INSTALL_TTS_DATA`), what he calls the owner
  (30 letters). An answer is said a sentence at a time with 280 ms between (`HudhudSpeech.parts`, shared); a help
  page is said up to 320 letters of whole sentences, then "the rest is on the screen". Kept on this phone only.
- **His ears** (`HudhudEars`, RECORD_AUDIO — asked only from his microphone or his settings, after `hudhud_mic_why`;
  `uses-feature microphone required=false`): Android's SpeechRecognizer without a box, the words shown as heard, the
  microphone growing with the voice and a stop button while open (a stop before it opens cancels; after, what was
  said is answered). On the phone itself when it has the language (Android 12+; Android 13+ asks the phone which it
  has — `checkRecognitionSupport` — and can download the phone's own pack, `triggerModelDownload`), else online,
  else he says why (`Trouble`: nothing heard, offline, a pack to download, not offline here, busy). Refused the
  microphone: the voice-search box as before, `EXTRA_PREFER_OFFLINE` without internet. Voice entry on Khata keeps
  the box. Both privacy policies (sections 11, 24, permissions) say what goes where.
- **What he can tell and a little talk** (`Hudhud.Ask.Abilities`, `Ask.Chat(Talk, salaam)`, nine languages): his
  first chip is "What can you tell me?" — the list, by what this person may see (REPORTS, the Radar, a helper's
  phone); how are you, who are you, thanks, bye, praise, "theek hoon" — a warm line that turns back to the shop, a
  salaam answered first. Talk is understood only when every word is talk's own (before names: "Ali Raza kaise ho" is
  Ali; names that are talk words — Hafiz, Kamal — stay names), and never takes a figure, today or an entry.
  "I can't answer this yet" points at the list. HudhudTest (+4), HudhudSpeechTest, HudhudWordsTest (the chip in
  nine languages), HudhudOnDeviceTest (+2: the list and "kaise ho"; his settings open).
- **The Khata list** (the owner's photos: cards over each other, gaps between; "fixed on the 12th, still not"):
  lesson 90 — `RowToScreen.plain` resets every transform, `settleNow` ends the list's animations first, the first
  update within 3 s of coming back has no item animator; the card's press is a rounded ripple (`fg_party_card`), not
  a grey square. **The Saved card swipes away** sideways (`SwipeAwayLayout`, lesson 91). **Import from Contacts'
  two prompts are by design**: the app's card says why, then Android's own dialog — once; not changed. **The
  customer link dialog** works and is honest; a preview of what the customer will see is the owner's call.
