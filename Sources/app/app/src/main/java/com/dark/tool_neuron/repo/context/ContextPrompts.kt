package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.AppLanguage

// EN/VI prompt + preamble constants shared by ConversationSummarizer and
// ContextPackageBuilder. Config-only, no logic, so it is easy to audit
// exactly what text ever gets sent to the local model / cloud gateway.
object ContextPrompts {

    const val KNOWN_FACTS_HEADER = "Known facts:"

    const val SUMMARIZE_MAX_TOKENS = 256
    const val SUMMARIZE_TIMEOUT_MS = 30_000L

    // System-block locale instruction line. VI-only for now; EN/SYSTEM add
    // no instruction line (English is the model's default register).
    fun localePreamble(locale: AppLanguage): String? = when (locale) {
        AppLanguage.VI -> "Trả lời bằng tiếng Việt, tự nhiên và ngắn gọn."
        AppLanguage.EN, AppLanguage.SYSTEM -> null
    }

    // Appended as a trailing user turn to the transcript handed to
    // compactConversation.
    fun summarizePrompt(locale: AppLanguage): String = if (locale == AppLanguage.VI) {
        "Tóm tắt đoạn hội thoại trên trong 2-4 câu ngắn gọn. Giữ lại các sự kiện, " +
            "tên riêng, quyết định cụ thể. Không mở đầu, không markdown."
    } else {
        "Summarize the conversation above in 2-4 short sentences. Keep concrete " +
            "facts, names, decisions. No preamble, no markdown."
    }

    // Prefix marking a summary as the deterministic fallback digest (never a
    // model-generated summary) so downstream UI/debugging can tell them apart.
    fun fallbackPrefix(locale: AppLanguage): String =
        if (locale == AppLanguage.VI) "(rút gọn)" else "(condensed)"
}
