package com.dark.tool_neuron.repo.context

import com.dark.hxs.HxsRecord
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemoryRecord
import com.dark.tool_neuron.model.context.MemorySource

// HXS tag namespaces for context_store_v1 collections. Each collection's
// tags start at 1 (per-collection namespace, not global).
internal object ContextTags {
    const val MEM_ID = 1
    const val MEM_CONTENT = 2
    const val MEM_CATEGORY = 3
    const val MEM_SOURCE = 4
    const val MEM_LOCALE = 5
    const val MEM_CREATED_AT = 6
    const val MEM_UPDATED_AT = 7

    const val SUM_CONVO_ID = 1
    const val SUM_UP_TO_TURN_ID = 2
    const val SUM_CONTENT = 3
    const val SUM_TOKEN_ESTIMATE = 4
    const val SUM_CREATED_AT = 5
}

internal fun MemoryRecord.toRecord(): HxsRecord {
    val m = this
    return HxsRecord.build {
        putString(ContextTags.MEM_ID, m.id)
        putString(ContextTags.MEM_CONTENT, m.content)
        putString(ContextTags.MEM_CATEGORY, m.category.name)
        putString(ContextTags.MEM_SOURCE, m.source.name)
        putString(ContextTags.MEM_LOCALE, m.locale)
        putTimestamp(ContextTags.MEM_CREATED_AT, m.createdAt)
        putTimestamp(ContextTags.MEM_UPDATED_AT, m.updatedAt)
    }
}

internal fun HxsRecord.toMemory(): MemoryRecord = MemoryRecord(
    id = getString(ContextTags.MEM_ID),
    content = getString(ContextTags.MEM_CONTENT),
    category = runCatching { MemoryCategory.valueOf(getString(ContextTags.MEM_CATEGORY)) }
        .getOrDefault(MemoryCategory.FACT),
    source = runCatching { MemorySource.valueOf(getString(ContextTags.MEM_SOURCE)) }
        .getOrDefault(MemorySource.EXPLICIT_USER),
    locale = getString(ContextTags.MEM_LOCALE),
    createdAt = getTimestamp(ContextTags.MEM_CREATED_AT),
    updatedAt = getTimestamp(ContextTags.MEM_UPDATED_AT),
)

internal fun ConversationSummary.toRecord(): HxsRecord {
    val s = this
    return HxsRecord.build {
        putString(ContextTags.SUM_CONVO_ID, s.conversationId)
        putString(ContextTags.SUM_UP_TO_TURN_ID, s.upToTurnId)
        putString(ContextTags.SUM_CONTENT, s.content)
        putInt(ContextTags.SUM_TOKEN_ESTIMATE, s.tokenEstimate.toLong())
        putTimestamp(ContextTags.SUM_CREATED_AT, s.createdAt)
    }
}

internal fun HxsRecord.toSummary(): ConversationSummary = ConversationSummary(
    conversationId = getString(ContextTags.SUM_CONVO_ID),
    upToTurnId = getString(ContextTags.SUM_UP_TO_TURN_ID),
    content = getString(ContextTags.SUM_CONTENT),
    tokenEstimate = getInt(ContextTags.SUM_TOKEN_ESTIMATE, 0).toInt(),
    createdAt = getTimestamp(ContextTags.SUM_CREATED_AT),
)
