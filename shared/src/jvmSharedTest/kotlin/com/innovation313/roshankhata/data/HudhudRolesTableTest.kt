package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudRolesTable.Gate
import com.innovation313.roshankhata.data.HudhudRolesTable.Held
import com.innovation313.roshankhata.data.HudhudRolesTable.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The roles matrix held to the code (plan B7, 24 Nov 2026): [HudhudRolesTable] says, for every kind of question and every
 * facet, which gate its card asks; this reads the answer files' SOURCE and fails when the code and the table disagree.
 *
 *  - every [Hudhud.Ask] kind and every facet enum entry has a row (a new one fails here until somebody decides);
 *  - a card the table puts behind REPORTS, STAFF or SETTINGS is dispatched to a function whose body asks that gate;
 *  - the facet branches that are gated inside an open card ask it on their own lines;
 *  - the answer functions that tell nothing private ask no gate: the list of them is frozen ([UNGATED]);
 *  - what a card says aloud through its own words (`spoken`) never holds an amount or a phone number, because that path
 *    skips the card's secrets and the hidden-balance rule ([AnswerBook.speechOf]);
 *  - the roles: which role holds which gate.
 *
 * Drift protection, not proof (lesson 125): it shows that a gate is asked, not that it is asked of the right thing; the
 * matrix over a real book (HudhudRolesMatrixTest, desktopTest) is the part that reads cards. Comments are skipped, and
 * "\r\n" is normalised (BC-257).
 */
class HudhudRolesTableTest {

    private val dir: File by lazy { File(HudhudRealPages.root, "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data") }

    private fun lines(file: String): List<String> = File(dir, file).readText().replace("\r\n", "\n").split("\n")

    private fun isComment(l: String): Boolean = l.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }

    private val FUN = Regex("""^ {4}(?:(?:private|internal|public|suspend|inline)\s+)*fun\s+(?:<[^>]*>\s*)?(\w+)""")

    private class Fn(val name: String, val vis: String, val signature: String, val body: String)

    /** Every member function of a file (4-space indent), its body without comment lines, up to the next member function. */
    private fun fns(file: String): List<Fn> {
        val ls = lines(file)
        val starts = ls.indices.filter { FUN.containsMatchIn(ls[it]) }
        return starts.mapIndexed { k, i ->
            val end = if (k + 1 < starts.size) starts[k + 1] else ls.size
            val head = ls[i]
            val vis = when {
                head.trimStart().startsWith("private") -> "private"
                head.trimStart().startsWith("internal") -> "internal"
                else -> "public"
            }
            val sig = ls.subList(i, minOf(i + 8, ls.size)).joinToString(" ").substringBefore("{")
            Fn(FUN.find(head)!!.groupValues[1], vis, sig, ls.subList(i, end).filterNot { isComment(it) }.joinToString("\n"))
        }
    }

    private fun body(file: String, fn: String): String {
        val all = fns(file).filter { it.name == fn }
        assertTrue("$file has no function $fn", all.isNotEmpty())
        return all.joinToString("\n") { it.body }
    }

    private fun token(g: Gate): String = when (g) {
        Gate.REPORTS -> "reports()"
        Gate.STAFF -> "staff()"
        Gate.SETTINGS -> "settings()"
        Gate.OPEN -> error("no token for OPEN")
    }

    // ------------------------------------------------------------------------------------------------ the coverage

    @Test
    fun everyKindOfQuestionHasARowAndNoRowIsStale() {
        val kinds = Hudhud.Ask::class.java.declaredClasses
            .filter { Hudhud.Ask::class.java.isAssignableFrom(it) }.map { it.simpleName }.toSet()
        assertTrue("the kinds were not found by reflection: $kinds", kinds.size > 40)
        assertEquals("kinds with no row (decide who may be told)", emptySet<String>(), kinds - HudhudRolesTable.ASK.keys)
        assertEquals("rows for kinds that are gone", emptySet<String>(), HudhudRolesTable.ASK.keys - kinds)
    }

    private val ENUMS: Map<String, Class<*>> = mapOf(
        "Facet" to Hudhud.Facet::class.java, "GoodsFacet" to Hudhud.GoodsFacet::class.java,
        "ShopFacet" to Hudhud.ShopFacet::class.java, "Thing" to Hudhud.Thing::class.java,
        "StatusOf" to Hudhud.StatusOf::class.java, "RecordFacet" to Hudhud.RecordFacet::class.java,
        "CounterFacet" to Hudhud.CounterFacet::class.java, "PaperOf" to Hudhud.PaperOf::class.java,
        "Measure" to Hudhud.Measure::class.java, "WhyOf" to Hudhud.WhyOf::class.java, "DatedOf" to Hudhud.DatedOf::class.java
    )

    @Test
    fun everyFacetOfEveryEnumHasAGateAndNoRowIsStale() {
        assertEquals("a facet table without its enum, or an enum without its table", ENUMS.keys, HudhudRolesTable.TABLES.keys)
        for ((name, cls) in ENUMS) {
            val real = cls.enumConstants.map { "$name.${(it as Enum<*>).name}" }.toSet()
            val table = HudhudRolesTable.TABLES.getValue(name).keys
            assertEquals("$name: entries with no gate decided", emptySet<String>(), real - table)
            assertEquals("$name: rows for entries that are gone", emptySet<String>(), table - real)
        }
    }

    @Test
    fun aRowThatIsGatedNamesItsGateAndAnOpenOneNamesNone() {
        for ((k, r) in HudhudRolesTable.ASK) {
            if (r.held == Held.GATED) assertTrue("$k is GATED with no gate", r.gate != Gate.OPEN)
            else assertEquals("$k holds a gate but is not GATED", Gate.OPEN, r.gate)
        }
        // A kind that tells the facets' gate must say so: its row is FACETS and its words point at the table.
        for ((k, r) in HudhudRolesTable.ASK) if (r.held == Held.FACETS) assertTrue("$k: FACETS needs its words", r.why.isNotBlank())
    }

    // ------------------------------------------------------------------------------------------------ the dispatch

    /** The call a kind of question is answered by, from [AnswerBook]'s answerOf: the file and the function. */
    private fun dispatchOf(kind: String): Pair<String, String>? {
        val line = lines("AnswerBook.kt").firstOrNull {
            !isComment(it) && ("Hudhud.Ask.$kind ->" in it || Regex("""Hudhud\.Ask\.$kind\s*->""").containsMatchIn(it))
        } ?: return null
        val after = line.substringAfter("->")
        val m = Regex("""(?:(Answer\w+)\.)?(\w+)\(""").find(after.replace("if (ask.save)", "")) ?: return null
        val file = (m.groupValues[1].ifEmpty { "AnswerBook" }) + ".kt"
        return file to m.groupValues[2]
    }

    @Test
    fun aCardBehindAGateIsAnsweredByAFunctionThatAsksIt() {
        var checked = 0
        for ((kind, row) in HudhudRolesTable.ASK) {
            if (row.held != Held.GATED) continue
            val (file, fn) = dispatchOf(kind) ?: error("$kind has no dispatch line in AnswerBook.answerOf")
            val b = body(file, fn)
            assertTrue("$kind -> $file:$fn does not ask ${token(row.gate)}", token(row.gate) in b)
            checked++
        }
        // 12 since 24 Nov 2026: Deliveries, Took and OnDays follow their open screens (supplier bills, a customer's page).
        assertTrue("too few gated kinds were checked: $checked", checked >= 12)
    }

    /** Facet to the function its dispatcher calls, one table per dispatching file. */
    private val CALLS: Map<String, Pair<String, Map<String, String>>> = mapOf(
        "StatusOf" to ("AnswerFacts.kt" to mapOf(
            "CHEQUES" to "cheques", "PLANS" to "plans", "BILLS" to "bills", "BOOK" to "book", "INVOICES" to "invoicesDue",
            "STAFF" to "salaries", "LOW_STOCK" to "lowStock"
        )),
        "RecordFacet" to ("AnswerRecordFacts.kt" to mapOf(
            "STAFF_PAY" to "staffPay", "STAFF_PAID" to "staffPaid", "ATTENDANCE" to "attendance", "STAFF_PERSON" to "staffPerson",
            "PLAN" to "plan", "PLAN_CLOSED" to "plansClosed", "CHEQUE" to "cheques"
        )),
        "PaperOf" to ("AnswerFacts.kt" to mapOf(
            "BILL" to "lastBill", "ORDER" to "lastOrder", "INVOICE" to "lastInvoice", "SCHEMES" to "schemeAnswer"
        )),
        "CounterFacet" to ("AnswerCounterFacts.kt" to HudhudRolesTable.COUNTER.keys.associate { k ->
            val n = k.substringAfter('.')
            n to when (n) {
                "BIGGEST" -> "biggest"; "LAST_TICKET" -> "lastTicket"; "BY_CASHIER" -> "byCashier"
                "DISCOUNTS", "TAX", "ON_ACCOUNT", "PAY_WAYS" -> "dayFigures"; "VOIDED" -> "voided"
                "RETURN_REASONS" -> "returnReasons"; "RETURN_WAYS" -> "returnWays"; "RETURN_GOODS" -> "returnGoods"
                "SHIFTS", "SHIFT_SHORT" -> "shifts"; "DAY_COUNT" -> "dayCount"; "LOYALTY" -> "loyalty"
                "WRITTEN_OFF" -> "writtenOff"; "STOCK_CHANGES" -> "stockChanges"; "LAST_COUNT" -> "lastCount"
                else -> error("a counter facet with no function in this test: $n")
            }
        })
    )

    /** The dispatcher whose `when` holds the facet's branch. */
    private val DISPATCHER: Map<String, Pair<String, String>> = mapOf(
        "StatusOf" to ("AnswerFacts.kt" to "status"), "RecordFacet" to ("AnswerRecordFacts.kt" to "answer"),
        "PaperOf" to ("AnswerFacts.kt" to "paper"), "CounterFacet" to ("AnswerCounterFacts.kt" to "answer")
    )

    @Test
    fun everyFacetBranchCallsTheFunctionThatAsksItsGate() {
        var checked = 0
        for ((enum, spec) in CALLS) {
            val (file, map) = spec
            val (dFile, dFn) = DISPATCHER.getValue(enum)
            val d = body(dFile, dFn).split("\n")
            for ((name, fn) in map) {
                val key = "$enum.$name"
                val gate = HudhudRolesTable.TABLES.getValue(enum).getValue(key)
                val i = d.indexOfFirst { "$enum.$name" in it }
                assertTrue("$dFile:$dFn has no branch for $key", i >= 0)
                val window = d.subList(i, minOf(i + 2, d.size)).joinToString(" ")
                assertTrue("$key does not call $fn: $window", Regex("""\b$fn\(""").containsMatchIn(window))
                if (gate != Gate.OPEN) {
                    assertTrue("$key -> $file:$fn does not ask ${token(gate)}", token(gate) in body(file, fn))
                } else {
                    // An open facet's function may not ask STAFF or SETTINGS (a gate nobody decided on).
                    val b = body(file, fn)
                    assertFalse("$key is open in the table but $fn asks STAFF", "staff()" in b)
                    assertFalse("$key is open in the table but $fn asks SETTINGS", "settings()" in b)
                }
                checked++
            }
        }
        assertTrue("too few facet branches checked: $checked", checked >= 36)
    }

    @Test
    fun theGatedBranchesInsideOpenCardsAskTheirGateOnTheirOwnLines() {
        val goods = body("AnswerFacts.kt", "goods").split("\n")
        for (name in listOf("COST", "SUPPLIERS", "SOLD", "COVER")) {
            val i = goods.indexOfFirst { "Hudhud.GoodsFacet.$name ->" in it }
            assertTrue("goods has no branch for $name", i >= 0)
            assertTrue("GoodsFacet.$name must refuse without REPORTS in its first lines",
                goods.subList(i, minOf(i + 4, goods.size)).any { "if (!reports()) return ownerOnly(" in it })
            assertEquals(Gate.REPORTS, HudhudRolesTable.GOODS.getValue("GoodsFacet.$name"))
        }
        for (key in HudhudRolesTable.GOODS_PARTLY) {
            val name = key.substringAfter('.')
            val i = goods.indexOfFirst { "Hudhud.GoodsFacet.$name ->" in it }
            assertTrue("goods has no branch for $name", i >= 0)
            val end = goods.drop(i + 1).indexOfFirst { Regex("""Hudhud\.GoodsFacet\.\w+ ->""").containsMatchIn(it) }.let { if (it < 0) goods.size else i + 1 + it }
            assertTrue("$key: its rate lines must ask reports() inside the branch", goods.subList(i, end).any { "reports()" in it })
        }

        val shop = body("AnswerFacts.kt", "shop")
        assertTrue("shop: owner must be settings()", "val owner = settings()" in shop)
        assertTrue("shop: the email must follow owner", "val email = if (owner) c.profile.businessEmail() else null" in shop)
        assertTrue("shop: the tax number must follow owner", "val tax = if (owner) c.profile.strn() else null" in shop)
        for (name in listOf("EMAIL", "TAX", "COUNTRY", "BANK")) {
            val i = shop.split("\n").indexOfFirst { "Hudhud.ShopFacet.$name ->" in it }
            assertTrue("shop has no branch for $name", i >= 0)
            assertTrue("ShopFacet.$name must say ownerExact when not the owner",
                shop.split("\n").subList(i, i + 2).any { "if (!owner) ownerExact(" in it })
            assertEquals(Gate.SETTINGS, HudhudRolesTable.SHOP.getValue("ShopFacet.$name"))
        }

        val count = body("AnswerFacts.kt", "count")
        assertTrue("count: the staff head count must need staff()", "Hudhud.Thing.STAFF -> if (!staff())" in count)
        for (key in HudhudRolesTable.COUNT_PARTLY) {
            val name = key.substringAfter('.')
            val c = count.split("\n")
            val i = c.indexOfFirst { "Hudhud.Thing.$name ->" in it }
            assertTrue("count has no branch for $name", i >= 0)
            assertTrue("$key: its amount must ask reports()", c.subList(i, minOf(i + 6, c.size)).any { "val money = reports()" in it })
        }

        val person = body("AnswerFacts.kt", "person").split("\n")
        val note = person.indexOfFirst { "Hudhud.Facet.NOTE ->" in it }
        assertTrue("person has no NOTE branch", note >= 0)
        assertTrue("the private note must say a read-only copy holds none", person.subList(note, minOf(note + 14, person.size)).any { "c.viewer() ->" in it })
    }

    // ------------------------------------------------------------------------------------------------ the frozen lists

    /** The answer functions that return a card and ask no gate, by file: each is a dispatcher or tells nothing private. */
    private val UNGATED: Map<String, Set<String>> = mapOf(
        "AnswerBook.kt" to setOf("intro", "answer", "unknown", "speech"),
        "AnswerFacts.kt" to setOf("about", "clock", "status", "needName", "automations", "needGoods", "paper"),
        "AnswerCounterFacts.kt" to setOf("answer"),
        "AnswerRecordFacts.kt" to setOf("answer"),
        "AnswerWhy.kt" to emptySet(),
        "AnswerQ.kt" to emptySet()
    )

    @Test
    fun noNewCardFunctionIsLeftWithoutAGateUnlessItIsListedHere() {
        val gates = listOf("reports()", "staff()", "settings()", "ownSalesOnly", "viewer()", "AnswerGate.")
        for ((file, allowed) in UNGATED) {
            val found = fns(file).filter { it.vis != "private" && ": AnswerCard" in it.signature && gates.none { g -> g in it.body } }
                .map { it.name }.toSet()
            assertEquals("$file: a card function with no gate (decide it, then list it)", allowed, found)
        }
    }

    @Test
    fun theDispatchersAreOpenOnlyBecauseTheirTargetsAreGated() {
        // status, paper, and the two facet dispatchers carry no gate of their own; the facet test above holds their targets.
        for ((file, fn) in listOf("AnswerFacts.kt" to "status", "AnswerFacts.kt" to "paper", "AnswerCounterFacts.kt" to "answer", "AnswerRecordFacts.kt" to "answer")) {
            val b = body(file, fn)
            assertFalse("$file:$fn is a dispatcher and holds a gate of its own: move the gate or list the facets", "reports()" in b || "staff()" in b)
        }
    }

    @Test
    fun theThreeGatesAreTheAccessPermissionsTheirNamesSay() {
        val gate = lines("AnswerGate.kt").filterNot { isComment(it) }.joinToString("\n")
        assertTrue("reports()", "fun reports() = Access.can(Access.Permission.REPORTS)" in gate)
        assertTrue("staff()", "fun staff() = Access.can(Access.Permission.STAFF)" in gate)
        assertTrue("settings()", "fun settings() = Access.can(Access.Permission.SETTINGS)" in gate)
        // Every Answer file asks the gates only through AnswerGate, never Access itself (the catalog's check_gates).
        for (f in listOf("AnswerBook.kt", "AnswerFacts.kt", "AnswerCounterFacts.kt", "AnswerRecordFacts.kt", "AnswerWhy.kt", "AnswerQ.kt")) {
            val bad = lines(f).filterNot { isComment(it) }.filter { "Access.can(" in it }
            assertEquals("$f asks Access itself", emptyList<String>(), bad)
        }
        val facts = body("AnswerFacts.kt", "reports") + body("AnswerFacts.kt", "staff") + body("AnswerFacts.kt", "settings")
        assertTrue("AnswerFacts' shorthands must call the gate", listOf("AnswerGate.reports()", "AnswerGate.staff()", "AnswerGate.settings()").all { it in facts })
    }

    // ------------------------------------------------------------------------------------------------ the voice path

    /** The text of every `spoken = { ... }` lambda in the answer files, by braces. */
    private fun spokenLambdas(): List<Triple<String, Int, String>> {
        val out = ArrayList<Triple<String, Int, String>>()
        for (f in listOf("AnswerBook.kt", "AnswerFacts.kt", "AnswerCounterFacts.kt", "AnswerRecordFacts.kt", "AnswerWhy.kt", "AnswerQ.kt")) {
            val text = lines(f).filterNot { isComment(it) }.joinToString("\n")
            var from = 0
            while (true) {
                val at = text.indexOf("spoken = {", from)
                if (at < 0) break
                var depth = 0
                var i = at + "spoken = ".length
                val start = i
                while (i < text.length) {
                    if (text[i] == '{') depth++
                    if (text[i] == '}') { depth--; if (depth == 0) break }
                    i++
                }
                out += Triple(f, text.substring(0, at).count { it == '\n' } + 1, text.substring(start, minOf(i + 1, text.length)))
                from = i
            }
        }
        return out
    }

    @Test
    fun whatACardSaysThroughItsOwnWordsHoldsNoAmountAndNoNumber() {
        // speechOf returns a card's `spoken` words as they are: before its secrets are replaced and before the hidden-balance
        // rule. So that path may hold only words, counts and days: no money, no phone, no balance, no amount.
        val lambdas = spokenLambdas()
        assertTrue("the spoken lambdas were not found (${lambdas.size})", lambdas.size >= 8)
        for ((f, n, text) in lambdas) {
            for (bad in listOf("money(", "Format.money", ".phone", "balance", "amount", "strn", "iban", "costPrice", "salary", "note")) {
                assertFalse("$f line $n: a spoken lambda reads '$bad'", bad in text)
            }
        }
        val speech = body("AnswerBook.kt", "speechOf")
        assertTrue("speechOf must replace the secrets in what it reads", "for (secret in secrets)" in speech)
        assertTrue("speechOf must leave amounts out when the balance is hidden", "hideMoney && hasMoney(t)" in speech)
        assertTrue("speechOf says nothing for a quiet card", "if (quiet) return null" in speech)
    }

    @Test
    fun aPrivateNumberOnACardIsListedAsASecretOrBelongsToAnOwnerOnlyBranch() {
        // The cards that show a number say so in `secrets = ...` (phone, code, account, tax number, email).
        val facts = lines("AnswerFacts.kt").filterNot { isComment(it) }.joinToString("\n")
        for (needle in listOf("secrets += it", "secrets = secrets", "secrets = listOf(code)")) {
            assertTrue("AnswerFacts no longer carries '$needle'", needle in facts)
        }
        val shop = body("AnswerFacts.kt", "shop")
        assertTrue("the shop's card must list what it shows as secrets", "secrets" in shop)
    }

    // ------------------------------------------------------------------------------------------------ the roles

    @Test
    fun theRolesHoldTheGatesAsAccessGivesThem() {
        for (g in Gate.values()) assertTrue("the owner holds $g", HudhudRolesTable.may(Role.OWNER, g))
        assertTrue(HudhudRolesTable.may(Role.MANAGER, Gate.REPORTS))
        assertFalse(HudhudRolesTable.may(Role.MANAGER, Gate.STAFF))
        assertFalse(HudhudRolesTable.may(Role.MANAGER, Gate.SETTINGS))
        for (r in listOf(Role.CASHIER, Role.LOCKED, Role.DESK_CASHIER)) {
            assertTrue(HudhudRolesTable.may(r, Gate.OPEN))
            for (g in listOf(Gate.REPORTS, Gate.STAFF, Gate.SETTINGS)) assertFalse("$r holds $g", HudhudRolesTable.may(r, g))
        }
        // Access.can, read from the source: the owner or a device with counter mode off holds all; a manager all but the owner's three.
        val access = File(dir, "Access.kt").readText().replace("\r\n", "\n")
        assertTrue("Access.can changed shape: re-read the roles", "isOwner -> true" in access && "p !in OWNER_ONLY" in access)
        assertTrue("OWNER_ONLY changed", "setOf(Permission.SETTINGS, Permission.BACKUP, Permission.STAFF)" in access)
        // The helper's phone is a read-only book with counter mode off: it is the owner of its copy and refuses the forms.
        assertTrue(HudhudRolesTable.may(Role.HELPER, Gate.STAFF))
        assertTrue(HudhudRolesTable.viewer(Role.HELPER))
        assertFalse(HudhudRolesTable.viewer(Role.OWNER))
        // Every kind of form refuses a read-only copy: both form-offering kinds ask viewer().
        for (kind in HudhudRolesTable.ASK.filterValues { it.held == Held.FORM }.keys) {
            val (file, fn) = dispatchOf(kind)!!
            assertTrue("$kind -> $file:$fn must ask viewer()", "viewer()" in body(file, fn))
        }
    }

    @Test
    fun theMarkersAreInventedAndDistinct() {
        val texts = HudhudRolesTable.MARKERS.map { it.text }
        assertEquals("a marker is used twice", texts.size, texts.toSet().size)
        for (m in HudhudRolesTable.MARKERS) {
            assertFalse("${m.name}: a marker that looks like a Pakistani mobile number", Regex("""0?3\d\d[ \-]?\d{7}""").containsMatchIn(m.text))
            assertTrue("${m.name}: empty", m.text.length >= 4)
        }
    }
}
