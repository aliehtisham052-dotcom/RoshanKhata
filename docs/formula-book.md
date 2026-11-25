# Formula book

One row per figure the app shows or Hudhud tells: the ONE function that computes it, what it reads, its window, what it leaves out, its rounding, its gate and where it is shown. Built 24 Nov 2026 by reading the code (no run). `docs/formula-book.tsv` holds the same rows; `scripts/check_formula_book.py` checks every `path::Symbol` still exists (drift protection, not proof: lesson 125). Not wired into CI: add `python3 scripts/check_formula_book.py` to `.github/workflows/strings.yml` and `scripts/run_release_checks.sh`.

Labels: VERIFIED = read in the code; INFERRED = not read directly. Gates marked 'none found' were not seen in the code read, which is not the same as none.

Conventions: isGiven=1 is +amount, isGiven=0 is -amount. Windows: [from,to) for most sums; the six cash sums are (from,to] with `DaySpans` wrappers; `entriesInRange` is inclusive BETWEEN. Working day runs 04:00 to 04:00. Paths below are under `shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/` (shortened to `data/`).

## Figures

### F01 Net balance (Home hero)
- Function: `data/PartyTotals.kt::PartyTotals.of; data/KhataDao.kt::observePartiesWithBalance`
- Reads: transactions(partyId,isGiven,amount,isDeleted); parties(isDeleted)
- Window: all time, no window
- Left out: binned entries; binned parties (SQL filter); includes OPENING and Qarz-e-Hasna rows; customers and suppliers netted together
- Rounding: none in the sum; Money epsilon when sign is read
- Gate: none found; Home eye hides it, hidden by default
- Shown on: app MainActivity.kt:883; desktop HomePane.kt:88-160; Hudhud AnswerBook.kt:687
- Confidence: SQL+function VERIFIED; exact SQL filter of observePartiesWithBalance INFERRED from the sibling partiesWithBalanceOnce

### F02 To get (you will receive)
- Function: `data/PartyTotals.kt::PartyTotals.of`
- Reads: same as F01
- Window: all time
- Left out: parties with balance <= 0 not counted
- Rounding: none; sum of positive balances (isGiven=1 is +amount)
- Gate: none found; Hudhud REPORTS
- Shown on: app MainActivity.kt:883; desktop HomePane.kt:88-160; Khata totals; Hudhud AnswerBook.kt:687-688
- Confidence: VERIFIED

### F03 To give (you will pay)
- Function: `data/PartyTotals.kt::PartyTotals.of`
- Reads: same as F01
- Window: all time
- Left out: parties with balance >= 0 not counted
- Rounding: none; sum of negative balances as a positive figure
- Gate: none found; Hudhud REPORTS
- Shown on: same as F02
- Confidence: VERIFIED

### F04 One party's balance
- Function: `data/KhataDao.kt::liveBalance`
- Reads: transactions(partyId,isGiven,amount,isDeleted=0)
- Window: all time
- Left out: binned entries; OPENING and Qarz-e-Hasna and MONEY kinds are INCLUDED
- Rounding: none (raw SUM)
- Gate: none (own screens)
- Shown on: customer page, Khata list row, receipts (ReceiptBalance)
- Confidence: VERIFIED

### F05 Running balance through one entry
- Function: `data/KhataDao.kt::balanceThrough`
- Reads: transactions(timestamp,id)
- Window: timestamp < t or (= t and id <= entryId)
- Left out: binned entries
- Rounding: none
- Gate: none
- Shown on: customer ledger column (desktop KhataCustomer.kt), receipt 'balance after'
- Confidence: VERIFIED

### F06 Binned party's balance
- Function: `data/KhataDao.kt::binnedPartyBalance`
- Reads: transactions(partyId)
- Window: all time
- Left out: isDeleted NOT filtered on purpose
- Rounding: none
- Gate: none
- Shown on: delete-forever question, delete business
- Confidence: VERIFIED

### F07 Sales total
- Function: `data/KhataDao.kt::salesTotalBetween`
- Reads: transactions(isGiven=1,isCustomer,entryKind='NORMAL'); sales(grandTotal)
- Window: [from,to)
- Left out: Qarz-e-Hasna; refunds; suppliers; OPENING; voided sales; binned entries (NOT binned parties: see D7)
- Rounding: none in SQL; grand total includes tax and round-off
- Gate: Hudhud REPORTS
- Shown on: Insights today/month, Z report 'Sales total', 21:00 notice, Hudhud sales answers
- Confidence: SQL VERIFIED (read ~3268); tax inclusion VERIFIED from grandTotal; party filter INFERRED

### F08 Sales count
- Function: `data/KhataDao.kt::salesCountBetween`
- Reads: as F07
- Window: [from,to)
- Left out: as F07
- Rounding: none
- Gate: Hudhud REPORTS
- Shown on: Insights, Hudhud count
- Confidence: INFERRED from F07's sibling

### F09 Sales report revenue
- Function: `data/SalesReport.kt::SoldLine.revenue`
- Reads: sale_items, entry_items, sales, transactions via soldLinesBetween/khataSaleRowsBetween/walkInSoldLinesBetween
- Window: [from,to) by ticket or entry day
- Left out: held tickets; voided; Qarz-e-Hasna; refunds; suppliers; OPENING; revenue is EX tax
- Rounding: TicketSpread largest-remainder per ticket; LineMath for khata lines
- Gate: REPORTS (page); EXPORT for Excel
- Shown on: Sales report page/PDF/Excel (both platforms)
- Confidence: VERIFIED

### F10 Gross profit (sales report)
- Function: `data/SalesReport.kt::SalesReport.grossProfit`
- Reads: as F09 plus products.costPrice, bill_items, entry_items
- Window: as F09
- Left out: lines with unknown cost give a dash until all costs known (costComplete)
- Rounding: per-line revenue minus qty*cost, Money.round at the end
- Gate: REPORTS
- Shown on: Sales report
- Confidence: VERIFIED

### F11 Net profit (sales report)
- Function: `data/SalesReport.kt::SalesReport.netProfit`
- Reads: F10 minus cashbookExpensesBetween minus staffPaidBetween
- Window: as F10
- Left out: counter refunds not an expense
- Rounding: Money.round
- Gate: REPORTS
- Shown on: Sales report
- Confidence: VERIFIED

### F12 Insights month and day figures
- Function: `data/Insights.kt::Insights.thisMonth`
- Reads: via F07
- Window: this month vs last month 'so far' (SaleInsights.lastMonthSoFar)
- Left out: as F07
- Rounding: percent via SaleInsights.changePercent
- Gate: none found (page); Hudhud REPORTS
- Shown on: Insights page (desktop+phone), Hudhud
- Confidence: VERIFIED

### F13 Profit (Insights, Hudhud)
- Function: `data/Profit.kt::Profit.between`
- Reads: profitSaleLinesBetween, profitPurchaseLinesBefore, cashbookExpensesBetween, staffPaidBetween
- Window: [from,to)
- Left out: Qarz-e-Hasna, refunds; walk-in 'Return' refunds not expense
- Rounding: khata revenue qty*rate*charged UNROUNDED; Money.round of totals
- Gate: REPORTS (Hudhud)
- Shown on: Insights profit card, Hudhud profit answers
- Confidence: VERIFIED

### F14 Cost of a khata sale (Insights)
- Function: `data/Profit.kt::Profit.costFor`
- Reads: products.costPrice; bill_items (batch); purchases dated <= the sale
- Window: sale day
- Left out: none
- Rounding: unit cost times qty
- Gate: REPORTS
- Shown on: Insights profit
- Confidence: VERIFIED; since 25 Nov 2026 one rule with F15 (F57, BC-279)

### F15 Cost of a khata sale (sales report)
- Function: `data/Cost.kt::Cost.perUnit; data/SalesReport.kt::KhataSales.costRate`
- Reads: products.costPrice; weighted average of ALL purchases; last rate
- Window: whole book average (not dated to the sale)
- Left out: none
- Rounding: 6 decimals per unit (L21)
- Gate: REPORTS
- Shown on: Sales report, Z report profit
- Confidence: VERIFIED; since 25 Nov 2026 one rule with F14 (F57, BC-279)

### F16 Cost of a counter ticket line
- Function: `data/CounterDraft.kt::Counter.costOf`
- Reads: products.costPrice, Cost.perUnit
- Window: at posting time
- Left out: none
- Rounding: 6 decimals stored in sale_items.costRate
- Gate: none (stored)
- Shown on: Z report profit, sales report
- Confidence: VERIFIED

### F17 Expenses (profit)
- Function: `data/KhataDao.kt::cashbookExpensesBetween; data/KhataDao.kt::staffPaidBetween`
- Reads: cashbook(isIncome=0,category), staff payments
- Window: [from,to)
- Left out: category 'Return' skipped in profit expenses; binned
- Rounding: none
- Gate: REPORTS
- Shown on: Insights, Sales report net
- Confidence: VERIFIED; differs from cashbook screen out (D9)

### F18 Money received
- Function: `data/KhataDao.kt::customerReceivedBetween`
- Reads: transactions isGiven=0 customers no goods; cashbook Sale income
- Window: [from,to)
- Left out: goods returns; owner's own cash put in cashbook; suppliers; OPENING
- Rounding: none
- Gate: REPORTS (Hudhud)
- Shown on: Insights, 21:00 notice, Hudhud 'collected'
- Confidence: VERIFIED

### F19 Cashbook in / out (net)
- Function: `data/KhataDao.kt::cashbookTotalBetween`
- Reads: cashbook(isIncome,amount,isDeleted,timestamp)
- Window: [from,to)
- Left out: binned
- Rounding: none
- Gate: none (own screen)
- Shown on: Cashbook screen figures: app CashbookActivity.kt:~511, desktop CashbookPane.kt:118
- Confidence: VERIFIED SQL; screen is all-time, includes 'Return' refunds as out (D9)

### F20 Cash in cashbook (drawer part)
- Function: `data/KhataDao.kt::cashbookCashBetween`
- Reads: cashbook where paymentMethod cash
- Window: (from,to] via DaySpans.cashbookCashOver
- Left out: binned; non-cash
- Rounding: none
- Gate: none
- Shown on: drawer, day close
- Confidence: VERIFIED

### F21 Khata cash
- Function: `data/KhataDao.kt::khataCashBetween`
- Reads: transactions cash; customers; MONEY kind for suppliers
- Window: (from,to] via DaySpans.khataCashOver
- Left out: suppliers' old Cash default rows; binned
- Rounding: none
- Gate: none
- Shown on: drawer, day close
- Confidence: VERIFIED

### F22 Bills and staff paid in cash
- Function: `data/KhataDao.kt::billsAndStaffCashBetween`
- Reads: supplier_bills paid cash; staff payments
- Window: (from,to]
- Left out: unpaid bills
- Rounding: none
- Gate: none
- Shown on: drawer (DrawerFigures.otherOut)
- Confidence: VERIFIED

### F23 Drawer by the books
- Function: `data/DayClose.kt::DayCloseMath.drawerByTheBooks`
- Reads: last day_close count + F20 + F21 + counter cash - refunds - F22
- Window: from last count (or start of working day if never counted) to now
- Left out: tickets of closed shifts follow the same rows
- Rounding: Money.round
- Gate: REPORTS for the day report
- Shown on: Home cash card, cashbook, day close, 21:00 notice, Hudhud drawer
- Confidence: VERIFIED

### F24 Day close difference
- Function: `data/DayClose.kt::DayCloseMath.difference`
- Reads: counted cash minus F23
- Window: working day
- Left out: none
- Rounding: Money epsilon decides matches / short / over
- Gate: manager PIN may be asked on save
- Shown on: Close the day dialog, Z report
- Confidence: VERIFIED

### F25 Working day rule
- Function: `data/DayClose.kt::DayCloseMath.businessDayOf`
- Reads: timestamp
- Window: 04:00 to 04:00 (DAY_ENDS_HOUR=4), found through the calendar
- Left out: none
- Rounding: none
- Gate: none
- Shown on: day close filing, bell yesterday's drawer
- Confidence: VERIFIED

### F26 Window wrappers
- Function: `data/DaySpans.kt::DaySpans`
- Reads: six cash sums
- Window: converts (from,to] sums to [from,to)
- Left out: none
- Rounding: none
- Gate: none
- Shown on: automations evening notice, Z report
- Confidence: VERIFIED

### F27 Day report lines (ZReport)
- Function: `data/ZReport.kt::ZReport.compute`
- Reads: sales, sale_payments, returns, cashbook, day_close
- Window: working day
- Left out: voids shown apart; held tickets not counted
- Rounding: Money epsilon (literals 0.004/0.005, see D4)
- Gate: REPORTS
- Shown on: Z report / Day book (both platforms)
- Confidence: VERIFIED

### F28 Z report profit
- Function: `data/ZReport.kt::ZReport.profit`
- Reads: counter tickets only: counterSales minus tax minus cost
- Window: working day
- Left out: khata sales not included (by design)
- Rounding: Money.round
- Gate: REPORTS
- Shown on: Z report
- Confidence: VERIFIED; narrower than F10 (D10)

### F29 Dues by age buckets
- Function: `data/Aging.kt::Aging.compute`
- Reads: LedgerLines.agingMoves
- Window: 0-30, 31-60, 61-90, 90+ days; payments clear the oldest first
- Left out: paid-now halves netted (PaymentRadar.netPairs); only customers
- Rounding: Money
- Gate: REPORTS
- Shown on: Dues by age (phone AgingActivity, desktop page), Hudhud aged dues
- Confidence: VERIFIED; epsilon mismatch D4

### F30 Who owes (set of owing customers)
- Function: `data/LedgerLines.kt::LedgerLines.owing`
- Reads: transactions grouped by party, 400 ids a read
- Window: all time
- Left out: balance < 0.005 left out (hard-coded)
- Rounding: literal 0.005
- Gate: none
- Shown on: Aging, Follow-up, Radar inputs
- Confidence: VERIFIED

### F31 Stock on hand
- Function: `data/Stock.kt::Stock.of`
- Reads: bill_items bought; entry_items sold; sale_items; returns; stock_adjustments
- Window: all time
- Left out: binned customers' goods still counted (M45); supplier 'I gave' counted as out
- Rounding: packs converted by Units.toBase
- Gate: none (Stock screen: STOCK_ADJUST for edits)
- Shown on: Products, Stock on hand, counter, Hudhud
- Confidence: VERIFIED

### F32 Stock value
- Function: `data/Inventory.kt::Valuation.compute`
- Reads: Stock.of times cost price else last purchase rate
- Window: as of now
- Left out: negative shelf counts as 0 (L73); no cost means no figure
- Rounding: Money.round per row
- Gate: REPORTS
- Shown on: Stock value screen, Hudhud
- Confidence: VERIFIED

### F33 Cover days
- Function: `data/StockCover.kt::StockCover.of`
- Reads: on hand / (sold last 30 days / 30)
- Window: 30 days, or the book's age if younger
- Left out: customers only (productsSoldSince)
- Rounding: floor; cap 99,999
- Gate: REPORTS (Hudhud)
- Shown on: Hudhud cover answer, low-stock order
- Confidence: VERIFIED; sold basis differs from F31 (D11)

### F34 Reorder quantity
- Function: `data/Automations.kt::Automations.reorder`
- Reads: StockCover.soldBase, products.lowStockLevel, open purchase orders
- Window: 30 days
- Left out: stock carried by unreceived orders
- Rounding: at least 1
- Gate: none
- Shown on: 10:00 notice, Order the low stock
- Confidence: VERIFIED

### F35 Zakat nisab
- Function: `data/Zakat.kt::Zakat.nisab`
- Reads: typed metal price and unit
- Window: now
- Left out: none
- Rounding: Money.round
- Gate: none found
- Shown on: Zakat screens (app ZakatActivity.kt, desktop ZakatPane.kt)
- Confidence: VERIFIED

### F36 Zakatable wealth
- Function: `data/Zakat.kt::Zakat.zakatableWealth`
- Reads: typed cash and stock, plus F38 debts, minus what the shop owes
- Window: now
- Left out: stock and cash are TYPED, not read from Valuation (D12)
- Rounding: Money.round
- Gate: none found
- Shown on: Zakat screens
- Confidence: VERIFIED

### F37 Zakat due
- Function: `data/Zakat.kt::Zakat.zakatDue`
- Reads: zakatableWealth * 2.5% if meetsNisab
- Window: now
- Left out: below nisab gives 0
- Rounding: Money.round
- Gate: none found
- Shown on: Zakat screens, Hudhud Qarz card
- Confidence: VERIFIED

### F38 Debts and Qarz-e-Hasna in Zakat
- Function: `data/Zakat.kt::Zakat.qarzOf; data/KhataDao.kt::observeZakatBalancesByParty`
- Reads: transactions isQarzeHasna, per customer
- Window: now
- Left out: Qarz-e-Hasna split out per customer
- Rounding: Money
- Gate: none found
- Shown on: Zakat screens, Hudhud Qarz
- Confidence: VERIFIED

### F39 Payment Radar pace and light
- Function: `data/PaymentRadar.kt::PaymentRadar.read`
- Reads: KhataDao.radarRows lots, FIFO
- Window: last 12 repaid lots, from 3; norm from 8 lots else 30 days, min 7
- Left out: Qarz-e-Hasna and OPENING lots left out of pace; paid-now lots under 1 hour
- Rounding: median FLOORED (D5)
- Gate: REPORTS
- Shown on: Radar chip, Follow-up, Hudhud WhenWays
- Confidence: VERIFIED

### F40 Shop's typical days
- Function: `data/PaymentRadar.kt::PaymentRadar.shopTypicalDays`
- Reads: all customers' repaid lots
- Window: norm cached 10 minutes per book
- Left out: as F39
- Rounding: floor median
- Gate: REPORTS
- Shown on: Radar reasons
- Confidence: VERIFIED

### F41 Likely payment day
- Function: `data/PaymentRadar.kt::PaymentRadar.likelyNow`
- Reads: habit day of month, else usual gap from last payment
- Window: next 4 weeks
- Left out: owing customers only
- Rounding: calendar days
- Gate: REPORTS
- Shown on: Radar, morning list, Hudhud
- Confidence: VERIFIED

### F42 Follow-up rhythm (habit)
- Function: `data/PaymentHabit.kt::PaymentHabit.of`
- Reads: customer lines
- Window: his history
- Left out: loans left out
- Rounding: median rounded half up (D5)
- Gate: none found
- Shown on: Follow-up ordering, Radar
- Confidence: VERIFIED

### F43 Invoice totals
- Function: `data/Invoice.kt::InvoiceMath.totals`
- Reads: invoice_items, discount (percent or money), tax, charge, round-off
- Window: document
- Left out: cancelled documents
- Rounding: Money.round at every step (L68)
- Gate: none (editing is gated)
- Shown on: invoice form and PDF, both platforms
- Confidence: VERIFIED

### F44 Counter ticket totals
- Function: `data/Sale.kt::SaleMath.totals`
- Reads: sale_items, discount, loyalty points, tax inside or on top, round-off
- Window: ticket
- Left out: held tickets not posted
- Rounding: LineMath per line; line tax via SaleMath.lineTaxes; MAX_QTY/MAX_RATE refused
- Gate: DISCOUNT_ANY past 5% for a cashier
- Shown on: counter, receipts
- Confidence: VERIFIED

### F45 Discount spread over lines
- Function: `data/TicketSpread.kt::TicketSpread.split`
- Reads: ticket discount
- Window: ticket
- Left out: none
- Rounding: largest remainder in the currency's decimals
- Gate: none
- Shown on: sale_items, returns, sales report
- Confidence: VERIFIED

### F46 Return total
- Function: `data/SaleReturn.kt::ReturnMath.totalFor`
- Reads: sale_return_items, sale_items paid rate
- Window: return day
- Left out: line may not return more than sold
- Rounding: paidRate per line (own tax)
- Gate: RETURN gate
- Shown on: counter returns, credit note, day report
- Confidence: VERIFIED

### F47 Loyalty points earned and value
- Function: `data/Loyalty.kt::LoyaltyRule.earned`
- Reads: sales, loyalty
- Window: ticket
- Left out: points spent subtracted (pointsSpent)
- Rounding: whole points
- Gate: none
- Shown on: counter
- Confidence: VERIFIED

### F48 Shift drawer
- Function: `data/Shift.kt::ShiftMath.drawer; data/ShiftDrawer.kt::ShiftDrawer.sum`
- Reads: float + person's counter cash - refunds + khataCashBy + cashbookCashBy
- Window: shift [open,close) vs (from,to] drawer (D8)
- Left out: owner's rows carry no writer so stay in no shift
- Rounding: Money.round
- Gate: none found
- Shown on: Shift screen, shift close
- Confidence: VERIFIED

### F49 Cash flow outlook
- Function: `data/CashFlow.kt::CashFlow.outlook`
- Reads: promises, cheques, plans, bills
- Window: next 30 days
- Left out: items with amount <= 0.004 left out
- Rounding: literal 0.004
- Gate: none found
- Shown on: Cash flow page, bell gap row
- Confidence: VERIFIED

### F50 Top products / customers
- Function: `data/KhataDao.kt::topProductsBetween; data/KhataDao.kt::topCustomersBetween`
- Reads: entry_items, sale_items, transactions
- Window: [from,to)
- Left out: topCustomers filters binned parties, salesTotal does not (D7)
- Rounding: none
- Gate: REPORTS (Hudhud)
- Shown on: Insights, Hudhud
- Confidence: VERIFIED

### F51 Sale invoice from a ticket
- Function: `data/SaleInvoice.kt::SaleInvoice.of`
- Reads: sale and lines
- Window: ticket
- Left out: none
- Rounding: as F43/F44
- Gate: none
- Shown on: A4 invoice from the counter
- Confidence: VERIFIED

### F52 Money rounding contract
- Function: `data/Money.kt::Money.round`
- Reads: currency decimals
- Window: n/a
- Left out: n/a
- Rounding: BigDecimal.valueOf, HALF_UP, epsilon 0.5/10^decimals
- Gate: n/a
- Shown on: everywhere
- Confidence: VERIFIED

### F53 What is left of a batch (lot)
- Function: `data/Fefo.kt::Fefo.lotsOf`, `data/Fefo.kt::Fefo.capByShelf`
- Reads: bill_items (qty, unit, expiryDate), entry_items, sale_items, return lines, write-offs (movements per batch), products.defaultUnit
- Window: all time to now
- Left out: a lot with nothing left; a binned party's goods are not left out (D11)
- Rounding: quantity in the product's own unit via Units.toBase, Units.roundQty 6 places; never more than the shelf (capByShelf)
- Gate: none found
- Shown on: counter, Expiry, stock register, Home chip, bell, bills banner, batch picker
- Confidence: VERIFIED

### F54 Soonest batch / expiring list
- Function: `data/Fefo.kt::Fefo.expiring`, `data/ExpiryWindow.kt::ExpiryWindow.cutoff`
- Reads: lots (F53), bill_items.expiryDate
- Window: batches with stock and a day, expiryDate <= cutoff = end of day 60 from today (calendar, not +60x24h)
- Left out: batches with no expiry day; empty lots
- Rounding: day arithmetic by DaySpans.dayStartAfter
- Gate: none found
- Shown on: Expiry page, bell row, Home chip, 10:00 automation, bills banner
- Confidence: VERIFIED

### F55 Unit contract (packs and other units)
- Function: `data/Units.kt::Units.toBase`, `data/Units.kt::Units.agree`, `data/Units.kt::Units.fromBase`
- Reads: products.defaultUnit, packUnit, pack size
- Window: n/a
- Left out: a mixed unit with no conversion gives null, never a guessed figure
- Rounding: Units.roundQty 6 places
- Gate: n/a
- Shown on: every stock, cost and line figure
- Confidence: VERIFIED

### F56 Cost of one unit of a product
- Function: `data/Cost.kt::Cost.rule`, `data/Cost.kt::Cost.average`, `data/Cost.kt::Cost.forUnit`, `data/Cost.kt::Cost.value`
- Reads: products.costPrice, purchase lines (bill_items)
- Window: cost price, else weighted average of purchases, else last rate
- Left out: a pack or other unit with no factor gives null
- Rounding: 6 decimals per unit (RATE_DECIMALS); value once to the coin by LineMath
- Gate: none
- Shown on: valuation, sales report, Insights, counter
- Confidence: VERIFIED

### F57 Cost of a khata sale line (one rule)
- Function: `data/Cost.kt::Cost.forSale`, `data/Profit.kt::Profit.costFor`
- Reads: products.costPrice, purchases dated <= the sale
- Window: as the sale's day
- Left out: a line with no product gives no cost
- Rounding: as F56
- Gate: REPORTS
- Shown on: Insights profit, sales report, Z report
- Confidence: VERIFIED

### F58 Bell: cheques due
- Function: `data/ChequesDue.kt::ChequesDue.isDue`, `data/ChequesDue.kt::ChequesDue.endOfToday`, `data/ChequesDue.kt::ChequesDue.isLate`
- Reads: cheques status and dueDate
- Window: pending with dueDate before the end of today (calendar day)
- Left out: cleared, bounced, cancelled
- Rounding: none
- Gate: none found
- Shown on: bell, Home chip, 9:00 cheque notice
- Confidence: VERIFIED

### F59 Bell: batches expiring
- Function: `data/LedgerAlerts.kt::LedgerAlerts.compute`, `data/ExpiryWindow.kt::ExpiryWindow.cutoff`
- Reads: as F54
- Window: 60 days, one cutoff on every page and in the bell
- Left out: as F54
- Rounding: count
- Gate: none found
- Shown on: bell, Home chip, automation
- Confidence: VERIFIED

### F60 Bell: low stock
- Function: `data/Inventory.kt::Valuation.isLow`, `data/LedgerAlerts.kt::LedgerAlerts.compute`
- Reads: stock on hand (F31), products.lowStockLevel
- Window: now
- Left out: shelf unknown or no level set
- Rounding: comparison on hand <= level
- Gate: none found
- Shown on: bell, Products, Order the low stock, Hudhud
- Confidence: VERIFIED

### F61 Bell: yesterday's drawer not counted
- Function: `data/LedgerAlerts.kt::LedgerAlerts.yesterdaysDrawer`, `data/DayClose.kt::DayCloseMath.businessDayOf`
- Reads: day_close, cash moved yesterday
- Window: working day [from,to)
- Left out: a day with no cash moved
- Rounding: none
- Gate: none found
- Shown on: bell
- Confidence: VERIFIED

### F62 Bell: cash-flow gap
- Function: `data/CashFlow.kt::CashFlow.outlook`, `data/LedgerAlerts.kt::LedgerAlerts.compute`
- Reads: promises, cheques, plans, bills
- Window: next 30 days
- Left out: as F49
- Rounding: as F49
- Gate: none found
- Shown on: bell
- Confidence: VERIFIED

### F63 Late payers (count)
- Function: `data/FollowUpRank.kt::FollowUpRank.lateCount`
- Reads: customers' balances, ledger lines, promised days
- Window: habit from paid lines; owing customers only
- Left out: settled customers, customers with no lines, a promised day still ahead
- Rounding: count; Money.isPositive on the balance
- Gate: none found
- Shown on: Home line, phone bell row, desktop bell row and badge (not inside LedgerAlerts)
- Confidence: VERIFIED

### F64 Season of an entry and the season book
- Function: `data/SeasonBook.kt::SeasonBook.seasonOf`, `data/SeasonBook.kt::SeasonBook.book`, `data/SeasonBook.kt::SeasonBook.summaries`
- Reads: customers' entries by day or tagged season, payments
- Window: Oct-Dec Rabi that year, Jan-Mar Rabi of the year before, Apr-Sep Kharif; window = this and the previous season
- Left out: a Qarz-e-Hasna loan and an imported OPENING balance are owed but are no season's credit (Line.seasonal); the reading starts at SeasonBook.readFrom so a credit written before the window but tagged to a shown season is that season's (E1 fixed 25 Nov 2026, BC-284)
- Rounding: money in whole paisa (Long); a payment clears the oldest credit first (FIFO)
- Gate: none found
- Shown on: Seasons page, customer page season chips
- Confidence: VERIFIED

## Disagreements between two places computing one figure

Line numbers are as read on 24 Nov 2026. Examples use made-up figures. D1 was fixed on 25 Nov 2026; the others stand.

- **D1 Cost of a khata sale (FIXED 25 Nov 2026, BC-279).** Cause found: Insights alone costed a line tagged to a batch at that bill line's rate (100 and 200 purchases: Insights 200, report 150). `Profit.costFor` now calls `Cost.forSale` like every other figure. Older text:  `data/Profit.kt:144-158` (Profit.costFor) takes the average of purchases dated up to the sale; `data/Cost.kt:38-43` via `data/SalesReport.kt:147-157` (KhataSales.costRate) takes the average of all purchases. Example: no cost price, 10 bought at 60 on 1 Oct, 10 at 80 on 15 Nov, sold 1 Nov: Insights cost 60, sales report cost 70. VERIFIED.
- **D2 Sales total against sales report revenue.** `KhataDao.kt` ~3268 (salesTotalBetween) uses the grand total with tax and round-off; `data/SalesReport.kt:46-53,286` revenue is ex tax. Example: Rs 1,000 plus 18% tax: 1,180 against 1,000. ReportsAgreeTest covers only no-tax books. VERIFIED.
- **D3 Returned line revenue.** `data/SalesReport.kt:222-230` (ReturnedLine.revenue) ignores the ticket spread that `SoldLines.spread` applied. Example: Rs 100 off three lines of 1,000 spreads 966.67/966.67/966.66; returning line 3 reverses 966.67, 0.01 off gross profit. VERIFIED.
- **D4 Zero tests.** `data/LedgerLines.kt:27,49` hard-code 0.005 where `data/Money.kt` uses half the coin (0.5/10^decimals); other literals: CashFlow.kt:55, ZReport.kt:138-154, SaleReturn.kt:179-180. Example (KWD): a 0.004 balance is in Aging.receivable but has no moves, so `data/Aging.kt:70` puts it in day 0. Code VERIFIED, consequence INFERRED.
- **D5 Medians.** `data/PaymentRadar.kt` median floors ([3,4] gives 3); `data/PaymentHabit.kt` median rounds half up ([3,4] gives 4). VERIFIED.
- **D6 Qarz-e-Hasna.** Radar balance excludes it (by design, M34); party balance, Aging, Home include it. Example: a Rs 5,000 loan-only customer: Home to-get 5,000, Radar balance 0. VERIFIED.
- **D7 Binned parties.** `KhataDao.kt` ~3377 topCustomersBetween filters `p.isDeleted=0`; ~3268 salesTotalBetween does not. A binned customer's sales are in the total but not in the ranking. INFERRED from reading both queries.
- **D8 Window edges.** [from,to) in most sums, (from,to] in the six cash sums, inclusive BETWEEN in entriesInRange (~1008), a fixed 24 h 'today' in HomePane.kt, ShiftMath [from,to) against the drawer's (from,to]. An entry at exactly the shift's closing millisecond is in one and not the other. INFERRED for that edge.
- **D9 Cashbook screen against profit expenses.** The Cashbook screen's in/out/net (CashbookPane.kt:118, CashbookActivity.kt:~511) are all-time and count 'Return' refunds as out (the phone's comment says month); `cashbookExpensesBetween` (~5260) skips them and adds staff pay. VERIFIED for the queries; the screen's all-time scope INFERRED.
- **D10 Z report profit.** `data/ZReport.kt` profit covers counter tickets only; `SalesReport.grossProfit` also covers khata sales. By design, minor.
- **D11 Stock sold basis.** Stock counts supplier 'I gave' and binned parties' goods (M45); `productsSoldSince` (~5306) and cover use customers only. Low confidence.
- **D12 Zakat.** Cash and stock are typed, not read from Valuation or the drawer. VERIFIED.

## Not done

Gates and screens for several rows are INFERRED. F53-F64 (lots, units, cost, bell rows, seasons) were added 25 Nov 2026. Faults found by those rows and fixed: cost of a batch-tagged line (BC-279), three copies of the late-payers count (BC-280), two ends of the 60-day expiry window (BC-281), two copies of the low-stock test (BC-282). Season edge (E1), fixed 25 Nov 2026 (BC-284): the reading starts at the first credit tagged to a shown season, and a loan or an opening balance is no season's credit. The other balance queries (KhataDao ~152, 849, 1675) have no row of their own. The checker proves names exist, not that a row's text is true.
