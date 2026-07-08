package com.dark.tool_neuron.util

fun extractParameterCount(name: String): String? {
    val match = Regex("""(\d+\.?\d*)\s*[Bb]""").find(name)
    return match?.groupValues?.get(0)?.uppercase()?.replace(" ", "")
}

fun extractQuantization(name: String): String? {
    val base = name.substringAfterLast('/').removeSuffix(".gguf")
    val patterns = listOf(
        Regex("""(?:^|[._-])(I?Q\d[A-Z\d_]*?)(?=\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:^|[._-])(BF16|F16|F32)(?=[._-]|$)""", RegexOption.IGNORE_CASE),
    )
    for (p in patterns) {
        val match = p.find(base)
        if (match != null) return match.groupValues[1].uppercase()
    }
    return null
}
