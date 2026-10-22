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
  backup format 14
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
- Not yet: the audit chain stays per device; FCM on the phone (needs the
  owner's google-services file, never in this public repo); sync only while
  the phone's app is on screen.

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
Screenshots, bilingual listing, content rating, data safety form (easy and
strong — no data is collected), feature graphic, signed AAB.

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
