package com.dark.tool_neuron

import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.repo.context.MemoryExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.Normalizer

class MemoryExtractorTest {

    @Test
    fun `extracts EN remember-that marker`() {
        val result = MemoryExtractor.extract("remember that I have a dog", "en")
        assertEquals("I have a dog", result?.content)
        assertEquals(MemoryCategory.FACT, result?.category)
        assertEquals(MemorySource.EXTRACTED, result?.source)
        assertEquals("en", result?.locale)
    }

    @Test
    fun `extracts EN remember-colon marker`() {
        val result = MemoryExtractor.extract("remember: pick up milk", "en")
        assertEquals("pick up milk", result?.content)
    }

    @Test
    fun `extracts EN dont-forget marker`() {
        val result = MemoryExtractor.extract("don't forget my birthday is June 3", "en")
        assertEquals("my birthday is June 3", result?.content)
    }

    @Test
    fun `extracts EN dont-forget marker with curly apostrophe`() {
        val result = MemoryExtractor.extract("don’t forget the meeting", "en")
        assertEquals("the meeting", result?.content)
    }

    @Test
    fun `extracts VI nho rang marker preserving diacritics`() {
        val result = MemoryExtractor.extract("nhớ rằng tôi ăn chay", "vi")
        assertEquals("tôi ăn chay", result?.content)
    }

    @Test
    fun `extracts VI hay nho marker`() {
        val result = MemoryExtractor.extract("hãy nhớ mua sữa", "vi")
        assertEquals("mua sữa", result?.content)
    }

    @Test
    fun `extracts VI ghi nho colon marker`() {
        val result = MemoryExtractor.extract("ghi nhớ: đi khám răng", "vi")
        assertEquals("đi khám răng", result?.content)
    }

    @Test
    fun `extracts VI dung quen marker`() {
        val result = MemoryExtractor.extract("đừng quên đón con lúc 5 giờ", "vi")
        assertEquals("đón con lúc 5 giờ", result?.content)
    }

    @Test
    fun `NFD input normalizes to same result as NFC`() {
        val nfc = "nhớ rằng tôi ăn chay"
        val nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD)
        val resultNfc = MemoryExtractor.extract(nfc, "vi")
        val resultNfd = MemoryExtractor.extract(nfd, "vi")
        assertEquals(resultNfc?.content, resultNfd?.content)
        assertEquals("tôi ăn chay", resultNfd?.content)
    }

    @Test
    fun `no marker returns null`() {
        assertNull(MemoryExtractor.extract("tôi thích pizza", "vi"))
        assertNull(MemoryExtractor.extract("what's the weather today", "en"))
    }

    @Test
    fun `marker mid-sentence is not matched`() {
        assertNull(MemoryExtractor.extract("Hey, remember that I like pizza", "en"))
        assertNull(MemoryExtractor.extract("À, nhớ rằng tôi ăn chay", "vi"))
    }

    @Test
    fun `content is capped at 500 chars`() {
        val long = "a".repeat(600)
        val result = MemoryExtractor.extract("remember that $long", "en")
        assertEquals(500, result?.content?.length)
    }

    @Test
    fun `blank input returns null`() {
        assertNull(MemoryExtractor.extract("", "en"))
        assertNull(MemoryExtractor.extract("   ", "en"))
    }

    @Test
    fun `marker with empty content returns null`() {
        assertNull(MemoryExtractor.extract("remember that", "en"))
        assertNull(MemoryExtractor.extract("remember:   ", "en"))
    }

    @Test
    fun `extraction is deterministic across repeated calls`() {
        val input = "remember that I prefer dark mode"
        val first = MemoryExtractor.extract(input, "en")
        repeat(100) {
            assertEquals(first, MemoryExtractor.extract(input, "en"))
        }
    }
}
