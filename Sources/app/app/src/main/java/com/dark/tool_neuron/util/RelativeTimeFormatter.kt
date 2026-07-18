package com.dark.tool_neuron.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Pure epoch-millis -> localized relative-time string formatter (FRI-574 phase 1).
 * No Android Context dependency: callers pass `now` (so tests are deterministic) and
 * a `languageTag` ("vi" for Vietnamese, anything else falls back to English) instead
 * of reading system/app locale directly. Buckets: just now / m / h / d / calendar date.
 */
object RelativeTimeFormatter {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS
    private const val WEEK_MS = 7 * DAY_MS

    fun format(
        epochMillis: Long,
        now: Long = System.currentTimeMillis(),
        languageTag: String = "en",
    ): String {
        val isVi = languageTag.equals("vi", ignoreCase = true)
        val delta = (now - epochMillis).coerceAtLeast(0L)
        return when {
            delta < MINUTE_MS -> if (isVi) "Vừa xong" else "Just now"
            delta < HOUR_MS -> {
                val minutes = delta / MINUTE_MS
                if (isVi) "$minutes phút trước" else "${minutes}m ago"
            }
            delta < DAY_MS -> {
                val hours = delta / HOUR_MS
                if (isVi) "$hours giờ trước" else "${hours}h ago"
            }
            delta < WEEK_MS -> {
                val days = delta / DAY_MS
                if (isVi) "$days ngày trước" else "${days}d ago"
            }
            else -> {
                val locale = if (isVi) Locale.forLanguageTag("vi") else Locale.ENGLISH
                SimpleDateFormat("MMM d", locale).format(Date(epochMillis))
            }
        }
    }
}
