package com.dark.tool_neuron.data

enum class BackendMode(val id: String) {
    FIREBASE("firebase"),
    FRIDAY_API("friday_api");

    companion object {
        val DEFAULT = FIREBASE

        fun fromId(id: String?): BackendMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
