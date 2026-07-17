package com.dark.tool_neuron.model.friday

// FRI-555: wire shape for a signed inbound event from a trusted agent runtime (OpenClaw/Hermes).
// Arrives as flat String fields inside an FCM data payload. Parsing is defensive: any missing or
// blank required field drops the whole envelope (return null) before any verification runs.
// Pure JVM/data-only — no Android, no crypto here (InboundEventVerifier owns HMAC + replay checks).
data class InboundEventEnvelope(
    val version: Int,
    val eventId: String,
    val sourceId: String,
    val sourceKind: EventSourceKind,
    val issuedAtMs: Long,
    val nonce: String,
    val type: InboundEventType,
    val correlationId: String?,
    val title: String,
    val body: String,
    val urgency: InboundUrgency,
    val actionIntent: String?,
    val signature: String,
) {
    // Deterministic, ambiguity-free byte encoding of every signed field (all but `signature`),
    // in fixed field order. Each field is length-prefixed ("len:value") rather than delimiter-joined
    // so a value containing the delimiter can't shift field boundaries and forge a colliding
    // canonical form for different envelope content (classic length-extension/ambiguity avoidance).
    fun canonicalBytes(): ByteArray {
        val fields = listOf(
            version.toString(),
            eventId,
            sourceId,
            sourceKind.name,
            issuedAtMs.toString(),
            nonce,
            type.name,
            correlationId.orEmpty(),
            title,
            body,
            urgency.name,
            actionIntent.orEmpty(),
        )
        val sb = StringBuilder()
        for (field in fields) {
            val bytes = field.toByteArray(Charsets.UTF_8)
            sb.append(bytes.size).append(':').append(field)
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        // FRI-555 (B5): only this wire version is accepted by the verifier. Parse stays
        // shape-only (does not reject on version) so callers can distinguish "malformed" from
        // "well-formed but unsupported version" -- the version-contract check lives in
        // InboundEventVerifier.verify.
        const val SUPPORTED_VERSION = 1
        private const val FIELD_VERSION = "version"
        private const val FIELD_EVENT_ID = "eventId"
        private const val FIELD_SOURCE_ID = "sourceId"
        private const val FIELD_SOURCE_KIND = "sourceKind"
        private const val FIELD_ISSUED_AT = "issuedAt"
        private const val FIELD_NONCE = "nonce"
        private const val FIELD_TYPE = "type"
        private const val FIELD_CORRELATION_ID = "correlationId"
        private const val FIELD_TITLE = "title"
        private const val FIELD_BODY = "body"
        private const val FIELD_URGENCY = "urgency"
        private const val FIELD_ACTION_INTENT = "actionIntent"
        private const val FIELD_SIGNATURE = "signature"

        // Flat-field parse. Required: version, eventId, sourceId, sourceKind, issuedAt, nonce, type,
        // title, signature. Any missing/blank required field, or an unparseable enum/number -> null.
        // No content logging here or anywhere downstream in the caller chain.
        fun parse(data: Map<String, String>): InboundEventEnvelope? {
            val version = data[FIELD_VERSION]?.trim()?.toIntOrNull() ?: return null
            val eventId = data[FIELD_EVENT_ID]?.takeIf { it.isNotBlank() } ?: return null
            val sourceId = data[FIELD_SOURCE_ID]?.takeIf { it.isNotBlank() } ?: return null
            val sourceKind = data[FIELD_SOURCE_KIND]?.let { parseSourceKind(it) } ?: return null
            val issuedAtMs = data[FIELD_ISSUED_AT]?.trim()?.toLongOrNull() ?: return null
            val nonce = data[FIELD_NONCE]?.takeIf { it.isNotBlank() } ?: return null
            val type = data[FIELD_TYPE]?.let { parseType(it) } ?: return null
            val title = data[FIELD_TITLE]?.takeIf { it.isNotBlank() } ?: return null
            val signature = data[FIELD_SIGNATURE]?.takeIf { it.isNotBlank() } ?: return null
            val urgency = data[FIELD_URGENCY]?.let { parseUrgency(it) } ?: InboundUrgency.NORMAL

            return InboundEventEnvelope(
                version = version,
                eventId = eventId,
                sourceId = sourceId,
                sourceKind = sourceKind,
                issuedAtMs = issuedAtMs,
                nonce = nonce,
                type = type,
                correlationId = data[FIELD_CORRELATION_ID]?.takeIf { it.isNotBlank() },
                title = title,
                body = data[FIELD_BODY].orEmpty(),
                urgency = urgency,
                actionIntent = data[FIELD_ACTION_INTENT]?.takeIf { it.isNotBlank() },
                signature = signature,
            )
        }

        private fun parseSourceKind(raw: String): EventSourceKind? =
            runCatching { EventSourceKind.valueOf(raw.trim().uppercase()) }.getOrNull()

        private fun parseType(raw: String): InboundEventType? =
            runCatching { InboundEventType.valueOf(raw.trim().uppercase()) }.getOrNull()

        private fun parseUrgency(raw: String): InboundUrgency? =
            runCatching { InboundUrgency.valueOf(raw.trim().uppercase()) }.getOrNull()
    }
}

enum class EventSourceKind { OPENCLAW, HERMES }

enum class InboundEventType {
    TASK_ASSIGNED, PROGRESS, COMPLETION, FAILURE, CONFIRMATION_REQUESTED, CANCEL
}
