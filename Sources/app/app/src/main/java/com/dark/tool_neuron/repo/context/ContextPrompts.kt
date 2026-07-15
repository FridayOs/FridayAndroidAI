package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.AppLanguage

object ContextPrompts {

    const val KNOWN_FACTS_HEADER = "Known facts:"

    const val SUMMARIZE_MAX_TOKENS = 256
    const val SUMMARIZE_TIMEOUT_MS = 30_000L

    fun localePreamble(locale: AppLanguage): String? = when (locale) {
        AppLanguage.VI -> "Trả lời bằng tiếng Việt, tự nhiên và ngắn gọn."
        AppLanguage.EN, AppLanguage.SYSTEM -> null
    }

    fun summarizePrompt(locale: AppLanguage): String = if (locale == AppLanguage.VI) {
        "Tóm tắt đoạn hội thoại trên trong 2-4 câu ngắn gọn. Giữ lại các sự kiện, " +
            "tên riêng, quyết định cụ thể. Không mở đầu, không markdown."
    } else {
        "Summarize the conversation above in 2-4 short sentences. Keep concrete " +
            "facts, names, decisions. No preamble, no markdown."
    }

    fun fallbackPrefix(locale: AppLanguage): String =
        if (locale == AppLanguage.VI) "(rút gọn)" else "(condensed)"
}
