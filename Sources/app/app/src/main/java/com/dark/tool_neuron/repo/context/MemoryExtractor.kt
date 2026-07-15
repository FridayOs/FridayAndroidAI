package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemorySource
import java.text.Normalizer
import java.util.regex.Pattern

data class ExtractionResult(
    val content: String,
    val category: MemoryCategory,
    val source: MemorySource,
    val locale: String,
)

object MemoryExtractor {

    private const val MAX_CONTENT_LENGTH = 500

    private val PATTERNS: List<Regex> = listOf(
        marker("remember that\\s+(.+)"),
        marker("remember:\\s*(.+)"),
        marker("don['’]t forget\\s+(.+)"),
        marker("nhớ rằng\\s+(.+)"),
        marker("hãy nhớ\\s+(.+)"),
        marker("ghi nhớ:\\s*(.+)"),
        marker("đừng quên\\s+(.+)"),
    )

    private fun marker(body: String): Regex {
        val flags = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.DOTALL
        return Pattern.compile("^\\s*$body\$", flags).toRegex()
    }

    fun extract(userText: String, locale: String): ExtractionResult? {
        if (userText.isBlank()) return null
        val normalized = Normalizer.normalize(userText, Normalizer.Form.NFC).trim()
        if (normalized.isEmpty()) return null

        for (pattern in PATTERNS) {
            val match = pattern.find(normalized) ?: continue
            val captured = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (captured.isEmpty()) continue
            val content = if (captured.length > MAX_CONTENT_LENGTH) {
                captured.take(MAX_CONTENT_LENGTH)
            } else {
                captured
            }
            return ExtractionResult(
                content = content,
                category = MemoryCategory.FACT,
                source = MemorySource.EXTRACTED,
                locale = locale,
            )
        }
        return null
    }
}
