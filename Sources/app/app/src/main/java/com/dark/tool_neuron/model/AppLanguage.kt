package com.dark.tool_neuron.model

enum class AppLanguage(val uiTag: String, val displayName: String) {
    SYSTEM(uiTag = "", displayName = "System default"),
    EN(uiTag = "en", displayName = "English"),
    VI(uiTag = "vi", displayName = "Tiếng Việt");

    companion object {
        fun fromId(id: String?): AppLanguage =
            entries.firstOrNull { it.name == id } ?: SYSTEM

        fun fromUiTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.uiTag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}
