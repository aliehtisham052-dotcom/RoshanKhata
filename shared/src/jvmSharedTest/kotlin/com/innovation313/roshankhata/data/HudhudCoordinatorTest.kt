package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.data.HudhudCoordinator.Outcome
import com.innovation313.roshankhata.words.W
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Coordinator's seven outcomes (Marhala 6, 26 Nov 2026; BC-373, BC-378): one sample of every [Hudhud.Ask] subtype — the
 * test fails for a subtype nobody placed here, as HudhudGoldenAnswersTest does — each with the card [AnswerBook] makes for it,
 * held to its outcome. The words are their own keys here ([text] is the identity), as the platforms' lookups return the
 * word for a key; a word with arguments is matched with any text in their place.
 *
 * And the drift guard: every "none"-shaped word the answer layer's sources name is placed in exactly one of
 * [HudhudCoordinator.NO_RECORD], [HudhudCoordinator.STATE_ZERO], [HudhudCoordinator.NOT_AN_OUTCOME] or the refusals, so a new
 * "nothing here" line cannot pass as an answer unnoticed.
 */
class HudhudCoordinatorTest {

    private val text: (String) -> String = { it }

    private fun card(vararg lines: String, figure: String? = null) = AnswerCard(AnswerCard.Mood.CALM, figure = figure, lines = lines.toList())

    private val answered = card("Something the book holds", figure = "Rs 1,200")
    private val entry = Hudhud.Ask.Entry(500.0, true, listOf(1L), listOf("ali"))
    private val w = VoiceQuestion.Window.TODAY

    /** One sample of every subtype with the outcome its usual card comes to. */
    private val samples: List<Triple<Hudhud.Ask, AnswerCard, Outcome>> = listOf(
        Triple(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Parties(listOf(1L)), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.BackupStatus, answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Drawer, answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Help("faq_backup"), card("How to back up"), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Salaam, card("Wa alaikum assalam"), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Hello, card("Hello"), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Period(Hudhud.Measure.SALES, w), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Moved(listOf(1L), w, true), answered, Outcome.ACCEPT),
        Triple(entry, card("I gave", figure = "Rs 500"), Outcome.DRAFT),
        Triple(Hudhud.Ask.Entries(listOf(entry, entry.copy(isGiven = false))), card(W.hudhud_entry_one_at_a_time), Outcome.DRAFT),
        Triple(Hudhud.Ask.WhenPays(listOf(1L)), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Today, answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Abilities, card("I can tell"), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Chat(Hudhud.Talk.HOW_ARE_YOU), card("I am well"), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Unknown, card(W.hudhud_unknown), Outcome.REFUSE),
        Triple(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.GENERAL), card(W.hudhud_off_topic), Outcome.REFUSE),
        Triple(Hudhud.Ask.Person(listOf(1L), Hudhud.Facet.PHONE), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Goods(listOf(1L), Hudhud.GoodsFacet.PRICE), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Shop(Hudhud.ShopFacet.NAME), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.About(Hudhud.AboutTopic.MAKER), card("Innovation 313"), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Clock, answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Status(Hudhud.StatusOf.BILLS), card(W.hudhud_bills_none), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Record(Hudhud.RecordFacet.STAFF_PAID), card(W.hudhud_staff_paid_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.Deliveries(w), card(W.hudhud_deliveries_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.Activity(w), card(W.hudhud_activity_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET)), Hudhud.Unsure.SIDE), card(), Outcome.CLARIFY),
        Triple(Hudhud.Ask.SpanLimit(listOf(Hudhud.Ask.Activity(w)), long = false), card(), Outcome.CLARIFY),
        Triple(Hudhud.Ask.Collected(w), card(W.hudhud_collected_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.NeedName(Hudhud.Facet.PHONE), card(), Outcome.CLARIFY),
        Triple(Hudhud.Ask.Cashbook(w), card(W.hudhud_cashbook_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.Returns(w), card(W.hudhud_returns_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.Counter(Hudhud.CounterFacet.values().first(), w), card(W.hudhud_owner_only), Outcome.REFUSE),
        Triple(Hudhud.Ask.Act(Hudhud.Move.FLY), card("Here I go"), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Voice(Hudhud.VoiceChange.AGAIN), card(W.hudhud_v_again_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.TopGoods(w), card(W.hudhud_top_goods_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.Qarz, card(W.hudhud_qarz_none), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Automations, answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.NeedGoods(Hudhud.GoodsFacet.PRICE), card(), Outcome.CLARIFY),
        Triple(Hudhud.Ask.Paper(Hudhud.PaperOf.ORDER), card(W.po_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.CallMe("Ali bhai"), card("Call you Ali bhai?"), Outcome.DRAFT),
        Triple(Hudhud.Ask.Look(HudhudLook.Request.Show(HudhudLook.Setting.SIZE)), card("Text size: Normal"), Outcome.ACCEPT),
        Triple(
            Hudhud.Ask.LookDone(HudhudLook.Made(HudhudLook.Setting.SIZE, HudhudLook.SIZE_NORMAL, HudhudLook.SIZE_LARGE)),
            card("Text size: Large"), Outcome.ACCEPT
        ),
        Triple(Hudhud.Ask.Guessed(Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS)), answered, Outcome.SAFE_GUESS),
        Triple(Hudhud.Ask.Now(Hudhud.Ask.Count(Hudhud.Thing.CUSTOMERS)), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.OnDays(Hudhud.DatedOf.SALES, VoiceQuestion.Dated(daysBack = 2)), card(W.hudhud_top_goods_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.Both(Hudhud.Ask.Drawer, Hudhud.Ask.Today), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Compare(Hudhud.Ask.Drawer, Hudhud.Ask.Drawer), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Why(Hudhud.WhyOf.SALES, w), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Took(listOf(1L), listOf(2L)), card(W.hudhud_took_none), Outcome.NO_DATA),
        Triple(Hudhud.Ask.NoHistory, card(W.hudhud_no_history), Outcome.REFUSE),
        Triple(Hudhud.Ask.Trend(w), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Advise(Hudhud.AdviseOf.CHASE), answered, Outcome.ACCEPT),
        // Marhala 10: a customer's three facts, the share, the joke answer; more credit with nobody named asks whose.
        Triple(Hudhud.Ask.Advise(Hudhud.AdviseOf.MORE_CREDIT, listOf(1L)), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Advise(Hudhud.AdviseOf.MORE_CREDIT), card(W.hudhud_credit_whose), Outcome.CLARIFY),
        Triple(Hudhud.Ask.Advise(Hudhud.AdviseOf.CONCENTRATION), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Advise(Hudhud.AdviseOf.CONCENTRATION), card(W.vq_nobody), Outcome.ACCEPT),
        Triple(Hudhud.Ask.Advise(Hudhud.AdviseOf.MAGIC), answered, Outcome.ACCEPT),
        // Lane B (Marhala 9): an atom answers, a limit refuses, roles not clear ask back, nothing to say refuses.
        Triple(Hudhud.Ask.Knowledge(KnowKind.GENERAL_GUIDANCE, atom = "K201"), answered, Outcome.ACCEPT),
        Triple(Hudhud.Ask.Knowledge(KnowKind.UNSUPPORTED, limit = KnowLimit.PESTICIDE), answered, Outcome.REFUSE),
        Triple(Hudhud.Ask.Knowledge(KnowKind.NEEDS_CLARIFICATION, asked = KnowMeasure.MARGIN), answered, Outcome.CLARIFY),
        Triple(Hudhud.Ask.Knowledge(KnowKind.UNSUPPORTED), answered, Outcome.REFUSE)
    )

    @Test
    fun everyAskSubtypeHasASampleAndItsOutcome() {
        val base = Hudhud.Ask::class.java
        val subtypes = base.declaredClasses.filter { base.isAssignableFrom(it) && it != base }.map { it.simpleName }.toSortedSet()
        val sampled = samples.map { it.first.javaClass.simpleName }.toSortedSet()
        assertEquals("Ask subtypes with no Coordinator sample", emptySet<String>(), subtypes - sampled)
        for ((ask, c, want) in samples) {
            assertEquals(ask.toString(), want, HudhudCoordinator.outcomeOf(ask, c, text).outcome)
        }
    }

    @Test
    fun aFailedReadIsAnErrorWhateverWasAsked() {
        val failed = card(HudhudReadFailure.words(text))
        for ((ask, _, _) in samples) {
            assertEquals(ask.toString(), Outcome.ERROR, HudhudCoordinator.outcomeOf(ask, failed, text).outcome)
        }
    }

    @Test
    fun aRoleRefusalIsARefusalAndSaysWhy() {
        val v = HudhudCoordinator.outcomeOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.SALES_TODAY), card(W.hudhud_owner_only), text)
        assertEquals(Outcome.REFUSE, v.outcome)
        assertEquals("role", v.why)
        val viewer = HudhudCoordinator.outcomeOf(Hudhud.Ask.Entry(500.0, true, listOf(1L), emptyList()), card(W.viewer_refused, figure = null), text)
        assertEquals(Outcome.REFUSE, viewer.outcome)
        assertEquals("viewer", viewer.why)
        // An entry in a book with nobody in it: no record to fill a form for.
        assertEquals(Outcome.NO_DATA, HudhudCoordinator.outcomeOf(entry, card(W.voice_no_party), text).outcome)
    }

    /** A "none" line beside a figure or rows is part of an answer, not a missing record (no reading of a line alone). */
    @Test
    fun aNoneLineBesideAFigureIsAnAnswer() {
        val withFigure = card(W.hudhud_activity_none, figure = "Rs 0")
        assertEquals(Outcome.ACCEPT, HudhudCoordinator.outcomeOf(Hudhud.Ask.Activity(w), withFigure, text).outcome)
        val withRows = AnswerCard(AnswerCard.Mood.CALM, lines = listOf(W.hudhud_activity_none), rows = listOf(AnswerCard.Row(1L, "Ali", "Rs 5", null)))
        assertEquals(Outcome.ACCEPT, HudhudCoordinator.outcomeOf(Hudhud.Ask.Activity(w), withRows, text).outcome)
    }

    /** A word with arguments ("%1$s has no payment plan.") is matched with the name in its place, in any language's order. */
    @Test
    fun aWordWithArgumentsIsMatchedWithTheArgumentsFilled() {
        val words: (String) -> String = { key -> if (key == W.hudhud_plan_none_for) "%1\$s has no payment plan." else key }
        val c = card("⁨Ali Raza⁩ has no payment plan.")
        assertEquals(Outcome.NO_DATA, HudhudCoordinator.outcomeOf(Hudhud.Ask.Record(Hudhud.RecordFacet.PLAN, listOf(1L)), c, words).outcome)
        val other = card("Ali Raza has a payment plan.")
        assertEquals(Outcome.ACCEPT, HudhudCoordinator.outcomeOf(Hudhud.Ask.Record(Hudhud.RecordFacet.PLAN, listOf(1L)), other, words).outcome)
    }

    /** D15's question is the one CLARIFY a money side makes; a side the reading defaulted is answered and only marked weak. */
    @Test
    fun aDefaultedSideIsMarkedWeakButStillAnswered() {
        val ask = Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET)
        val certainty = HudhudCertainty.of("kitna qarz baqi hai", ask)
        val v = HudhudCoordinator.outcomeOf(ask, answered, text, certainty)
        assertEquals(Outcome.ACCEPT, v.outcome)
        assertTrue(v.weak.toString(), HudhudCertainty.Part.SIDE in v.weak)
        val asked = Hudhud.Ask.DidYouMean(listOf(ask, Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GIVE)), Hudhud.Unsure.SIDE)
        val c = HudhudCoordinator.outcomeOf(asked, card(), text, HudhudCertainty.of("kitna qarz baqi hai", asked))
        assertEquals(Outcome.CLARIFY, c.outcome)
        assertEquals("side", c.why)
    }

    /** A choice answered as its first meaning is a guess told as such (D1), not a question (D15 asks only the side). */
    @Test
    fun aChoiceWithoutTheSideQuestionIsASafeGuess() {
        val ask = Hudhud.Ask.DidYouMean(listOf(Hudhud.Ask.Figure(VoiceQuestion.Kind.TO_GET), Hudhud.Ask.Status(Hudhud.StatusOf.BILLS)))
        assertEquals(Outcome.SAFE_GUESS, HudhudCoordinator.outcomeOf(ask, answered, text).outcome)
        assertEquals(Outcome.REFUSE, HudhudCoordinator.outcomeOf(Hudhud.Ask.DidYouMean(emptyList()), card(W.hudhud_unknown), text).outcome)
        assertEquals(Outcome.ACCEPT, HudhudCoordinator.outcomeOf(Hudhud.Ask.CallMe("Ali", save = true), card("Ali"), text).outcome)
        assertEquals(Outcome.ACCEPT, HudhudCoordinator.outcomeOf(Hudhud.Ask.OffTopic(Hudhud.OffTopicWhy.SUM, "2+2", 4.0), answered, text).outcome)
    }

    /** Every "none"-shaped word the answer layer names is placed once (drift guard, lesson 125). */
    @Test
    fun everyNoneWordOfTheAnswerLayerIsPlaced() {
        val dir = File("src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data")
        val files = dir.listFiles { f -> f.name.startsWith("Answer") && f.name.endsWith(".kt") }.orEmpty()
        assertTrue("the answer layer's sources were not found from " + File(".").absolutePath, files.size >= 5)
        val shaped = Regex("\\bW\\.([a-z0-9_]*(?:_none|_empty|nobody|no_[a-z]|_off\\b|not_)[a-z0-9_]*)")
        val named = files.flatMap { f -> shaped.findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSortedSet()
        val lists = listOf(HudhudCoordinator.NO_RECORD, HudhudCoordinator.STATE_ZERO, HudhudCoordinator.NOT_AN_OUTCOME, HudhudCoordinator.REFUSALS.keys.toList())
        val placed = lists.flatten()
        val twice = placed.groupBy { it }.filterValues { it.size > 1 }.keys
        assertEquals("words placed twice", emptySet<String>(), twice)
        assertEquals("'none' words of the answer layer placed nowhere", emptySet<String>(), named - placed.toSet())
    }
}
