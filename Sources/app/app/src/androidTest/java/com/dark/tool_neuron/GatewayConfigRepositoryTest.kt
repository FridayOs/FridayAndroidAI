package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.repo.GatewayConfigRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GatewayConfigRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

    @Before
    fun setup() {
        File(context.filesDir, "gateway_store_v1").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        encryptor = HxsEncryptor()
    }

    @After
    fun cleanup() {
        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
        File(context.filesDir, "gateway_store_v1").deleteRecursively()
    }

    private fun newRepo(): GatewayConfigRepository =
        GatewayConfigRepository(context, AppKeyStore(context), encryptor)

    @Test
    fun crudAndRestartPersistence() {
        val repoA = newRepo()
        val created = repoA.create(GatewayProvider.OPENAI, "Work", "", "sk-secret", "gpt-4o-mini")
        assertEquals(1, repoA.gateways.value.size)
        // First brain-capable gateway auto-selects as the active brain.
        assertEquals(created.id, repoA.brainId.value)

        HexStorage().close()

        val repoB = newRepo()
        assertEquals("gateway must persist across restart", 1, repoB.gateways.value.size)
        val restored = repoB.getById(created.id)!!
        assertEquals("sk-secret", restored.apiKey)
        assertEquals("gpt-4o-mini", restored.model)
        assertEquals("brain pointer must persist", created.id, repoB.brainId.value)
    }

    @Test
    fun multipleInstancesOfSameProvider() {
        val repo = newRepo()
        val a = repo.create(GatewayProvider.OPENAI, "Personal", "", "sk-a", "gpt-4o-mini")
        val b = repo.create(GatewayProvider.OPENAI, "Work", "", "sk-b", "gpt-4o")
        assertEquals(2, repo.gateways.value.size)
        assertNotEquals(a.id, b.id)
        assertEquals("sk-a", repo.getById(a.id)!!.apiKey)
        assertEquals("sk-b", repo.getById(b.id)!!.apiKey)
    }

    @Test
    fun statusPersistsAcrossRestart() {
        val repoA = newRepo()
        val g = repoA.create(GatewayProvider.OPENAI, "Work", "", "sk-a", "gpt-4o-mini")
        assertEquals(GatewayStatus.NOT_TESTED, repoA.getById(g.id)!!.status)
        repoA.recordStatus(g.id, GatewayStatus.READY, "")
        assertEquals(GatewayStatus.READY, repoA.getById(g.id)!!.status)
        assertTrue(repoA.getById(g.id)!!.lastTestedAt > 0)

        HexStorage().close()

        val repoB = newRepo()
        assertEquals("status must persist across restart", GatewayStatus.READY, repoB.getById(g.id)!!.status)
    }

    @Test
    fun failedStatusStoresSanitizedError_notKey() {
        val repo = newRepo()
        val g = repo.create(GatewayProvider.OPENAI, "Work", "", "sk-topsecret", "gpt-4o-mini")
        repo.recordStatus(g.id, GatewayStatus.FAILED, "Auth rejected (HTTP 401)")
        val restored = repo.getById(g.id)!!
        assertEquals(GatewayStatus.FAILED, restored.status)
        assertTrue(restored.statusError.contains("401"))
        assertTrue("error summary must never contain the key", !restored.statusError.contains("sk-topsecret"))
    }

    @Test
    fun brainAndVoiceSelectionAreIndependent() {
        val repo = newRepo()
        // Anthropic is brain-only; Gemini supports both roles.
        val anthropic = repo.create(GatewayProvider.ANTHROPIC, "Claude", "", "sk-ant", "claude-sonnet-4-5")
        val gemini = repo.create(GatewayProvider.GEMINI, "Gem", "", "sk-gem", "gemini-2.0-flash")

        repo.selectBrain(anthropic.id)
        repo.selectVoice(gemini.id)
        assertEquals(anthropic.id, repo.brainGateway()!!.id)
        assertEquals(gemini.id, repo.voiceGateway()!!.id)

        // Switching the brain must not disturb the voice pointer.
        repo.selectBrain(gemini.id)
        assertEquals(gemini.id, repo.brainGateway()!!.id)
        assertEquals(gemini.id, repo.voiceGateway()!!.id)
    }

    @Test
    fun brainOnlyProviderNeverFillsVoiceSlot() {
        val repo = newRepo()
        // Anthropic can't do voice; the voice slot must stay empty.
        repo.create(GatewayProvider.ANTHROPIC, "Claude", "", "sk-ant", "claude-sonnet-4-5")
        assertTrue(repo.brainGateway() != null)
        assertNull("brain-only provider must not become the voice gateway", repo.voiceGateway())
    }

    @Test
    fun deletingActiveBrainFallsBackToRemaining() {
        val repo = newRepo()
        val a = repo.create(GatewayProvider.OPENAI, "A", "", "sk-a", "gpt-4o-mini")
        val b = repo.create(GatewayProvider.OPENAI, "B", "", "sk-b", "gpt-4o")
        repo.selectBrain(a.id)
        repo.delete(a.id)
        assertEquals("deleting active brain falls back to remaining", b.id, repo.brainGateway()!!.id)
        assertEquals(b.id, repo.brainId.value)
    }

    @Test
    fun deletingLastGatewayGoesToNoProviderState() {
        val repo = newRepo()
        val a = repo.create(GatewayProvider.OPENAI, "A", "", "sk-a", "gpt-4o-mini")
        repo.delete(a.id)
        assertNull(repo.brainGateway())
        assertEquals("", repo.brainId.value)
        assertTrue(repo.gateways.value.isEmpty())
    }

    // create() is save-only when a brain is already selected; explicit selectBrain() is save-and-use.
    @Test
    fun createIsSaveOnly_whenAnotherBrainIsAlreadySelected() {
        val repo = newRepo()
        val first = repo.create(GatewayProvider.OPENAI, "First", "", "sk-a", "gpt-4o-mini")
        assertEquals(first.id, repo.brainId.value)
        val second = repo.create(GatewayProvider.OPENAI, "Second", "", "sk-b", "gpt-4o")
        assertEquals("save-only: previously-selected brain must stay selected", first.id, repo.brainId.value)
        // Explicit selectBrain() is the save-and-use step.
        repo.selectBrain(second.id)
        assertEquals(second.id, repo.brainId.value)
    }

    @Test
    fun createIsSaveAndUse_whenNoBrainSelectedYet() {
        val repo = newRepo()
        assertEquals("", repo.brainId.value)
        val g = repo.create(GatewayProvider.OPENAI, "Solo", "", "sk-a", "gpt-4o-mini")
        assertEquals("first brain-capable gateway auto-fills the empty slot", g.id, repo.brainId.value)
    }

    @Test
    fun upsertEditsInPlace_preservingIdAndRoleAutoFill() {
        val repo = newRepo()
        val g = repo.create(GatewayProvider.OPENAI, "Work", "", "sk-a", "gpt-4o-mini")
        val edited = g.copy(label = "Work 2", apiKey = "sk-new", model = "gpt-4o")
        val stored = repo.upsert(edited)
        assertEquals(g.id, stored.id)
        assertEquals("Work 2", repo.getById(g.id)!!.label)
        assertEquals("sk-new", repo.getById(g.id)!!.apiKey)
    }

    @Test
    fun multipleInstancesSameProvider_areDistinguishable() {
        val repo = newRepo()
        val a = repo.create(GatewayProvider.OPENAI, "A", "", "sk-a", "gpt-4o-mini")
        val b = repo.create(GatewayProvider.OPENAI, "B", "", "sk-b", "gpt-4o-mini")
        repo.selectBrain(a.id)
        repo.delete(a.id)
        assertEquals("delete falls back to next role-capable", b.id, repo.brainGateway()!!.id)
    }
}
