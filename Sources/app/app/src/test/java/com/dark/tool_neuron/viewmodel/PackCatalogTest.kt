package com.dark.tool_neuron.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FRI-582 Codex-QA blocker fix (Blocker 3): [PackCatalog.isKnownPack] must
 * recognize exactly the 3 catalog pack constants and reject unknown ids.
 */
class PackCatalogTest {

    @Test
    fun known_pack_constants_are_recognized() {
        assertTrue(PackCatalog.isKnownPack(PackCatalog.PACK_CHAT_ONLY))
        assertTrue(PackCatalog.isKnownPack(PackCatalog.PACK_CHAT_VOICE))
        assertTrue(PackCatalog.isKnownPack(PackCatalog.PACK_LARGE_CHAT_VOICE))
    }

    @Test
    fun unknown_pack_id_is_rejected() {
        assertFalse(PackCatalog.isKnownPack("unknown_pack"))
        assertFalse(PackCatalog.isKnownPack(""))
    }

    @Test
    fun known_pack_entries_are_non_empty() {
        assertTrue(PackCatalog.entriesFor(PackCatalog.PACK_CHAT_ONLY)?.isNotEmpty() == true)
        assertTrue(PackCatalog.entriesFor(PackCatalog.PACK_CHAT_VOICE)?.isNotEmpty() == true)
        assertTrue(PackCatalog.entriesFor(PackCatalog.PACK_LARGE_CHAT_VOICE)?.isNotEmpty() == true)
    }

    @Test
    fun unknown_pack_entries_are_null() {
        assertTrue(PackCatalog.entriesFor("unknown_pack") == null)
    }
}
