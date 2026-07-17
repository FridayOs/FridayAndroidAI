package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.EventSourceKind
import com.dark.tool_neuron.model.friday.InboundEventEnvelope
import com.dark.tool_neuron.model.friday.InboundEventType
import com.dark.tool_neuron.model.friday.InboundUrgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pure-parse and canonical-encoding behavior of the FRI-555 envelope: defensive field parsing
// (missing/blank required field -> null) and the length-prefixed canonicalBytes() encoding that
// prevents delimiter-collision signature forgery.
class InboundEventEnvelopeTest {

    private fun fullPayload(overrides: Map<String, String> = emptyMap()): Map<String, String> {
        val base = mapOf(
            "version" to "1",
            "eventId" to "ev-1",
            "sourceId" to "openclaw-1",
            "sourceKind" to "OPENCLAW",
            "issuedAt" to "1000",
            "nonce" to "n-1",
            "type" to "TASK_ASSIGNED",
            "correlationId" to "c-1",
            "title" to "Task ready",
            "body" to "Do the thing",
            "urgency" to "HIGH",
            "actionIntent" to "run",
            "signature" to "deadbeef",
        )
        return base + overrides
    }

    @Test
    fun parse_fullPayload_mapsAllFields() {
        val env = InboundEventEnvelope.parse(fullPayload())!!
        assertEquals(1, env.version)
        assertEquals("ev-1", env.eventId)
        assertEquals("openclaw-1", env.sourceId)
        assertEquals(EventSourceKind.OPENCLAW, env.sourceKind)
        assertEquals(1000L, env.issuedAtMs)
        assertEquals("n-1", env.nonce)
        assertEquals(InboundEventType.TASK_ASSIGNED, env.type)
        assertEquals("c-1", env.correlationId)
        assertEquals("Task ready", env.title)
        assertEquals("Do the thing", env.body)
        assertEquals(InboundUrgency.HIGH, env.urgency)
        assertEquals("run", env.actionIntent)
        assertEquals("deadbeef", env.signature)
    }

    @Test
    fun parse_missingUrgency_defaultsToNormal() {
        val env = InboundEventEnvelope.parse(fullPayload() - "urgency")!!
        assertEquals(InboundUrgency.NORMAL, env.urgency)
    }

    @Test
    fun parse_optionalFieldsAbsent_areNull() {
        val env = InboundEventEnvelope.parse(fullPayload() - "correlationId" - "actionIntent")!!
        assertNull(env.correlationId)
        assertNull(env.actionIntent)
    }

    @Test
    fun parse_missingRequiredField_returnsNull() {
        for (key in listOf("version", "eventId", "sourceId", "sourceKind", "issuedAt", "nonce", "type", "title", "signature")) {
            assertNull("missing $key must drop envelope", InboundEventEnvelope.parse(fullPayload() - key))
        }
    }

    @Test
    fun parse_blankRequiredField_returnsNull() {
        assertNull(InboundEventEnvelope.parse(fullPayload(mapOf("eventId" to "  "))))
        assertNull(InboundEventEnvelope.parse(fullPayload(mapOf("title" to ""))))
    }

    @Test
    fun parse_unparseableEnumOrNumber_returnsNull() {
        assertNull(InboundEventEnvelope.parse(fullPayload(mapOf("version" to "not-a-number"))))
        assertNull(InboundEventEnvelope.parse(fullPayload(mapOf("sourceKind" to "UNKNOWN_KIND"))))
        assertNull(InboundEventEnvelope.parse(fullPayload(mapOf("type" to "UNKNOWN_TYPE"))))
        assertNull(InboundEventEnvelope.parse(fullPayload(mapOf("issuedAt" to "not-a-number"))))
    }

    @Test
    fun canonicalBytes_isDeterministic_forSameContent() {
        val a = InboundEventEnvelope.parse(fullPayload())!!
        val b = InboundEventEnvelope.parse(fullPayload())!!
        assertEquals(a.canonicalBytes().toList(), b.canonicalBytes().toList())
    }

    @Test
    fun canonicalBytes_excludesSignature() {
        val a = InboundEventEnvelope.parse(fullPayload())!!
        val b = InboundEventEnvelope.parse(fullPayload(mapOf("signature" to "othersig")))!!
        assertEquals(a.canonicalBytes().toList(), b.canonicalBytes().toList())
    }

    @Test
    fun canonicalBytes_lengthPrefix_avoidsDelimiterCollisionAmbiguity() {
        // Without length-prefixing, title="ab" body="c" and title="a" body="bc" could collide under
        // naive delimiter-joining. Length-prefixed encoding must keep them distinct.
        val a = InboundEventEnvelope.parse(fullPayload(mapOf("title" to "ab", "body" to "c")))!!
        val b = InboundEventEnvelope.parse(fullPayload(mapOf("title" to "a", "body" to "bc")))!!
        assertNotEquals(a.canonicalBytes().toList(), b.canonicalBytes().toList())
    }

    // FRI-555 (B4): identity fields (eventId/nonce/sourceId/correlationId) are persisted downstream
    // as delimiter-joined records ("atMs\tvalue", "sourceId|correlationId"). A value containing the
    // record separator, the composite-key separator, a control char, or exceeding the length cap
    // must be rejected at parse (fail-closed) so a corrupted persisted record can never yield a
    // false "unseen" on read-back.
    @Test
    fun parse_identityFieldWithControlOrDelimiterChar_returnsNull() {
        val dirtyValues = listOf("has\nnewline", "has\rcr", "has\ttab", "has|pipe", "has\u0000nul")
        val identityFields = listOf("eventId", "nonce", "sourceId", "correlationId")
        for (field in identityFields) {
            for (dirty in dirtyValues) {
                assertNull(
                    "field=$field value=${dirty.hashCode()} must be rejected",
                    InboundEventEnvelope.parse(fullPayload(mapOf(field to dirty))),
                )
            }
        }
    }

    @Test
    fun parse_identityFieldOverLengthCap_returnsNull() {
        val tooLong = "a".repeat(InboundEventEnvelope.ID_MAX + 1)
        val identityFields = listOf("eventId", "nonce", "sourceId", "correlationId")
        for (field in identityFields) {
            assertNull("field=$field over ID_MAX must be rejected", InboundEventEnvelope.parse(fullPayload(mapOf(field to tooLong))))
        }
    }

    @Test
    fun parse_identityFieldAtLengthCap_isAccepted() {
        val atCap = "a".repeat(InboundEventEnvelope.ID_MAX)
        val env = InboundEventEnvelope.parse(fullPayload(mapOf("eventId" to atCap)))
        assertEquals(atCap, env?.eventId)
    }
}
