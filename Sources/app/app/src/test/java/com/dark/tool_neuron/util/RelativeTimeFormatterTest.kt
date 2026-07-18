package com.dark.tool_neuron.util

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * FRI-574 phase 6 — pure formatter, no Android Context: `now` is injected so every
 * bucket boundary is deterministic (no wall-clock flakiness).
 */
class RelativeTimeFormatterTest {

    private val now = 1_700_000_000_000L // fixed reference instant

    @Test
    fun justNow_underOneMinute_english() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 30_000L, now = now, languageTag = "en")
        assertEquals("Just now", text)
    }

    @Test
    fun justNow_underOneMinute_vietnamese() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 30_000L, now = now, languageTag = "vi")
        assertEquals("Vừa xong", text)
    }

    @Test
    fun minutesBucket_english() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 5 * 60_000L, now = now, languageTag = "en")
        assertEquals("5m ago", text)
    }

    @Test
    fun minutesBucket_vietnamese() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 5 * 60_000L, now = now, languageTag = "vi")
        assertEquals("5 phút trước", text)
    }

    @Test
    fun hoursBucket_english() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 3 * 3_600_000L, now = now, languageTag = "en")
        assertEquals("3h ago", text)
    }

    @Test
    fun hoursBucket_vietnamese() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 3 * 3_600_000L, now = now, languageTag = "vi")
        assertEquals("3 giờ trước", text)
    }

    @Test
    fun daysBucket_english() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 2 * 86_400_000L, now = now, languageTag = "en")
        assertEquals("2d ago", text)
    }

    @Test
    fun daysBucket_vietnamese() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 2 * 86_400_000L, now = now, languageTag = "vi")
        assertEquals("2 ngày trước", text)
    }

    @Test
    fun calendarDate_afterOneWeek_english() {
        val eightDaysAgo = now - 8 * 86_400_000L
        val expected = java.text.SimpleDateFormat("MMM d", java.util.Locale.ENGLISH).format(java.util.Date(eightDaysAgo))
        val text = RelativeTimeFormatter.format(epochMillis = eightDaysAgo, now = now, languageTag = "en")
        assertEquals(expected, text)
    }

    @Test
    fun calendarDate_afterOneWeek_vietnamese() {
        val eightDaysAgo = now - 8 * 86_400_000L
        val expected = java.text.SimpleDateFormat("MMM d", java.util.Locale.forLanguageTag("vi")).format(java.util.Date(eightDaysAgo))
        val text = RelativeTimeFormatter.format(epochMillis = eightDaysAgo, now = now, languageTag = "vi")
        assertEquals(expected, text)
    }

    @Test
    fun futureOrEqualTimestamp_clampsToJustNow() {
        // epochMillis > now (clock skew / same-instant persistence race) must not go negative.
        val text = RelativeTimeFormatter.format(epochMillis = now + 10_000L, now = now, languageTag = "en")
        assertEquals("Just now", text)
    }

    @Test
    fun unknownLanguageTag_fallsBackToEnglish() {
        val text = RelativeTimeFormatter.format(epochMillis = now - 30_000L, now = now, languageTag = "fr")
        assertEquals("Just now", text)
    }
}
