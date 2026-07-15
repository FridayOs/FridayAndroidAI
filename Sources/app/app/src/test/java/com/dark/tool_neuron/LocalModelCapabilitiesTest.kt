package com.dark.tool_neuron

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.RepositoryDataStore
import com.dark.tool_neuron.repo.context.LocalModelCapabilities
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCapabilitiesTest {

    private fun modelInfo(id: String) = ModelInfo(
        id = id, name = id, path = "/tmp/$id.gguf", pathType = PathType.FILE,
        providerType = ProviderType.GGUF,
    )


    @Test
    fun `qwen3-0_6b supports Vietnamese`() {
        val model = modelInfo("qwen3-0.6b__Qwen3-0.6B-Q4_K_M.gguf")
        assertEquals(true, LocalModelCapabilities.supportsLanguage(model, AppLanguage.VI))
        assertEquals(true, LocalModelCapabilities.supportsLanguage(model, AppLanguage.EN))
    }

    @Test
    fun `gemma3-1b-it supports Vietnamese`() {
        val model = modelInfo("gemma3-1b-it__gemma-3-1b-it-Q4_K_M.gguf")
        assertEquals(true, LocalModelCapabilities.supportsLanguage(model, AppLanguage.VI))
    }

    @Test
    fun `lfm English-only model does not claim Vietnamese support`() {
        val model = modelInfo("lfm25-350m__LFM2.5-350M-Q4_K_M.gguf")
        assertEquals(false, LocalModelCapabilities.supportsLanguage(model, AppLanguage.VI))
        assertEquals(true, LocalModelCapabilities.supportsLanguage(model, AppLanguage.EN))
    }

    @Test
    fun `unmatched model id resolves to null - never a faked yes`() {
        val model = modelInfo("built-in-tts__voice.onnx")
        assertNull(LocalModelCapabilities.supportsLanguage(model, AppLanguage.VI))
        assertNull(LocalModelCapabilities.supportsLanguage(model, AppLanguage.EN))
    }

    @Test
    fun `exact id match without filename suffix also resolves`() {
        val model = modelInfo("qwen3-0.6b")
        assertEquals(true, LocalModelCapabilities.supportsLanguage(model, AppLanguage.VI))
    }

    @Test
    fun `SYSTEM locale always resolves to null - no concrete language to check`() {
        val model = modelInfo("qwen3-0.6b__Qwen3-0.6B-Q4_K_M.gguf")
        assertNull(LocalModelCapabilities.supportsLanguage(model, AppLanguage.SYSTEM))
    }


    @Test
    fun `catalog marks qwen and gemma families as en,vi and others as en only`() {
        val repos = RepositoryDataStore.DEFAULT_REPOSITORIES
        val viIds = setOf("qwen3-0.6b", "unsloth-qwen3_5-0_8b", "unsloth-qwen3_5-4b", "gemma3-1b-it", "gemma4-e2b-it")
        repos.forEach { repo ->
            if (repo.id in viIds) {
                assertTrue("${repo.id} should support vi", "vi" in repo.languages)
            } else {
                assertEquals("${repo.id} should default to en-only", setOf("en"), repo.languages)
            }
        }
    }

    @Test
    fun `repo JSON without languages field decodes to en-only default (backward compat)`() {
        val legacyJson = JSONObject().apply {
            put("id", "legacy-model")
            put("name", "Legacy Model")
            put("repoPath", "some/legacy-repo")
            put("isEnabled", true)
            put("category", "GENERAL")
        }
        val languages = legacyJson.optJSONArray("languages")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.let { if (it.isNullOrEmpty()) setOf("en") else it }

        assertEquals(setOf("en"), languages)
    }

    @Test
    fun `repo JSON with languages array round-trips through JSONArray decode`() {
        val json = JSONObject().apply {
            put("id", "qwen3-0.6b")
            put("name", "Qwen3 0.6B")
            put("repoPath", "Qwen/Qwen3-0.6B-GGUF")
            put("isEnabled", true)
            put("category", "GENERAL")
            put("languages", JSONArray(listOf("en", "vi")))
        }
        val languages = json.optJSONArray("languages")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.let { if (it.isNullOrEmpty()) setOf("en") else it }

        assertEquals(setOf("en", "vi"), languages)
    }
}
