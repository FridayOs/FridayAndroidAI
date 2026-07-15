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

class VietnamesePipelineTest {

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
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "user").put("content", viText))

        val serialized = arr.toString()
        val reparsed = JSONArray(serialized)
        val roundTripped = reparsed.getJSONObject(0).getString("content")

        assertEquals(viText, roundTripped)
        assertTrue(serialized.contains("Nguyễn Văn Ánh"))
        assertTrue(serialized.contains("😄🎉"))
    }
}
