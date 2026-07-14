package com.dark.tool_neuron

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.context.PackageInput
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.context.ContextPackageBuilder
import com.dark.tool_neuron.repo.context.MemoryExtractor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure-JVM E2E of the owned Vietnamese-Unicode string path (FRI-557 phase 5).
// Whole pipe is UTF-8 Kotlin String -> JSON (org.json escapes non-ASCII
// safely) -> AIDL String (UTF-16) -> llama.cpp UTF-8. The HXS-vault leg is
// already covered by phase 1 tests; this exercises OUR OWN string handling:
// MemoryExtractor -> ContextPackageBuilder -> GatewayTurn.content, plus the
// JSON encode/decode shape identical to what GatewayRoleRouter builds.
//
// Note: GatewayRoleRouter.buildLocalMessagesJson is a private function on a
// large class with no test seam to extract without a broader refactor (out
// of this phase's file-ownership scope), so the JSON-roundtrip assertion
// below replicates its exact JSONArray/JSONObject shape directly
// (`{"role":..,"content":..}` entries in a JSONArray) instead of calling it.
class VietnamesePipelineTest {

    // Full Vietnamese diacritic coverage (all 6 tone marks + base vowels)
    // plus mixed emoji, matching what a real user turn could contain.
    private val viText =
        "Xin chào! Tôi tên là Nguyễn Văn Ánh. Hôm nay trời đẹp quá 😄🎉. " +
            "Đừng quên: uống nước đầy đủ, ăn uống điều độ, và ngủ đủ giấc 🌙."

    @Test
    fun `MemoryExtractor preserves VI diacritics and emoji byte-identical`() {
        val marker = "đừng quên uống nước đầy đủ 💧🥤"
        val input = "Đừng quên $marker"
        val result = MemoryExtractor.extract(input, "vi")

        assertEquals(marker, result?.content)
        assertEquals("vi", result?.locale)
    }

    @Test
    fun `ContextPackageBuilder carries VI diacritics and emoji into GatewayTurn content untouched`() {
        val turn = FridayTurn(
            id = "t1", conversationId = "c1", role = "user",
            content = viText, timestamp = 1L,
        )
        val pkg = ContextPackageBuilder.build(
            PackageInput(
                turns = listOf(turn),
                memories = emptyList(),
                snippets = emptyList(),
                summary = null,
                locale = AppLanguage.VI,
                tokenBudget = 3072,
            ),
        )

        val rawUserTurn = pkg.history.last { it.role == "user" }
        assertEquals(viText, rawUserTurn.content)
    }

    @Test
    fun `JSON roundtrip of VI content is byte-identical (GatewayRoleRouter buildLocalMessagesJson shape)`() {
        // Same shape as GatewayRoleRouter.buildLocalMessagesJson: one
        // JSONObject per turn with "role"/"content", collected into a
        // JSONArray, serialized to a String, then re-parsed.
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "user").put("content", viText))

        val serialized = arr.toString()
        val reparsed = JSONArray(serialized)
        val roundTripped = reparsed.getJSONObject(0).getString("content")

        assertEquals(viText, roundTripped)
        // Non-ASCII must survive raw (org.json does not \\u-escape by
        // default) - assert the literal diacritic bytes are present in the
        // wire string, not just after re-parsing.
        assertTrue(serialized.contains("Nguyễn Văn Ánh"))
        assertTrue(serialized.contains("😄🎉"))
    }
}
