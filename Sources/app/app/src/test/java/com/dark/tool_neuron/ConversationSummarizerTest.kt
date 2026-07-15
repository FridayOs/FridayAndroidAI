package com.dark.tool_neuron

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.context.SummaryResult
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.context.ConversationSummarizer
import com.dark.tool_neuron.repo.context.LocalSummaryModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeLocalSummaryModel(
    private val loaded: Boolean = true,
    private val result: String? = "fake model summary",
    private val delayMs: Long = 0L,
) : LocalSummaryModel {
    var compactCalled: Boolean = false
        private set

    override fun isLoaded(): Boolean = loaded

    override suspend fun compact(messagesJson: String, maxTokens: Int): String? {
        compactCalled = true
        if (delayMs > 0) delay(delayMs)
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSummarizerTest {

    private fun turn(id: String, role: String, content: String, ts: Long) =
        FridayTurn(id = id, conversationId = "c1", role = role, content = content, timestamp = ts)

    private fun turns(count: Int) = (1..count).map { i ->
        turn("t$i", if (i % 2 == 1) "user" else "assistant", "message $i", i.toLong())
    }

    @Test
    fun `below threshold turn count returns NotNeeded without touching the model`() = runTest {
        val model = FakeLocalSummaryModel()
        val summarizer = ConversationSummarizer(model)

        val result = summarizer.summarize(turns(ConversationSummarizer.MIN_TURNS_TO_SUMMARIZE - 1), AppLanguage.EN)

        assertEquals(SummaryResult.NotNeeded, result)
        assertFalse(model.compactCalled)
    }

    @Test
    fun `model returning text yields viaModel true with that content`() = runTest {
        val model = FakeLocalSummaryModel(loaded = true, result = "concrete model summary")
        val summarizer = ConversationSummarizer(model)

        val result = summarizer.summarize(turns(ConversationSummarizer.MIN_TURNS_TO_SUMMARIZE), AppLanguage.EN)

        val success = result as? SummaryResult.Success ?: error("expected Success, got $result")
        assertTrue(success.viaModel)
        assertEquals("concrete model summary", success.summary.content)
    }

    @Test
    fun `model returning null falls back to deterministic digest`() = runTest {
        val model = FakeLocalSummaryModel(loaded = true, result = null)
        val summarizer = ConversationSummarizer(model)
        val input = turns(ConversationSummarizer.MIN_TURNS_TO_SUMMARIZE)

        val result = summarizer.summarize(input, AppLanguage.EN) as SummaryResult.Success

        assertFalse(result.viaModel)
        assertTrue(result.summary.content.startsWith("(condensed)"))
    }

    @Test
    fun `unloaded model skips compact call and falls back immediately`() = runTest {
        val model = FakeLocalSummaryModel(loaded = false)
        val summarizer = ConversationSummarizer(model)

        val result = summarizer.summarize(turns(ConversationSummarizer.MIN_TURNS_TO_SUMMARIZE), AppLanguage.EN) as SummaryResult.Success

        assertFalse(result.viaModel)
        assertFalse("must not trigger a load-inducing compact call when unloaded", model.compactCalled)
    }

    @Test
    fun `fallback digest is exact and byte-deterministic across repeated calls`() = runTest {
        val model = FakeLocalSummaryModel(loaded = false)
        val summarizer = ConversationSummarizer(model)
        val input = turns(ConversationSummarizer.MIN_TURNS_TO_SUMMARIZE)

        val first = (summarizer.summarize(input, AppLanguage.EN) as SummaryResult.Success).summary.content
        val second = (summarizer.summarize(input, AppLanguage.EN) as SummaryResult.Success).summary.content

        assertEquals(first, second)
        assertTrue(first.contains("message 1"))
        assertTrue(first.contains("message 9"))
        assertTrue(first.contains("message 12"))
    }

    @Test
    fun `VI locale selects the Vietnamese fallback prefix`() = runTest {
        val model = FakeLocalSummaryModel(loaded = false)
        val summarizer = ConversationSummarizer(model)

        val result = summarizer.summarize(turns(ConversationSummarizer.MIN_TURNS_TO_SUMMARIZE), AppLanguage.VI) as SummaryResult.Success

        assertTrue(result.summary.content.startsWith("(rút gọn)"))
    }

    @Test
    fun `cancellation mid-compact propagates and never writes a fallback`() = runTest {
        val model = FakeLocalSummaryModel(loaded = true, result = "should never arrive", delayMs = 60_000)
        val summarizer = ConversationSummarizer(model)

        val deferred = async {
            summarizer.summarize(turns(ConversationSummarizer.MIN_TURNS_TO_SUMMARIZE), AppLanguage.EN)
        }
        advanceTimeBy(1)
        deferred.cancel()

        var threw = false
        try {
            deferred.await()
        } catch (e: CancellationException) {
            threw = true
        }
        assertTrue("cancellation must propagate, not resolve to a fallback Success", threw)
    }
}
