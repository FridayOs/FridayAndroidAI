package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.repo.ActiveConversationPersistence
import com.dark.tool_neuron.repo.DefaultActiveConversationStore
import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.context.ContextHistorySource
import com.dark.tool_neuron.repo.gateway.ChatBrain
import com.dark.tool_neuron.repo.gateway.GatewayDirectory
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.viewmodel.FridayChatViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-574 round-5 BLOCKER 2: repository-backed process-restart proof for the persisted
 * active conversation. Mirrors PrefsPersistenceTest/ContextStoreTest's HXS reset pattern
 * (PolicyEngine.resetForTesting, BootIntegrity.setRelaxedForTesting, HexStorage().close()
 * between instances = simulated process death). Uses the REAL AppPreferences vault, the
 * REAL ActiveConversationPersistence adapter over it (same shape as SecurityModule's
 * @Provides), the REAL DefaultActiveConversationStore, and the REAL HXS-backed
 * FridayConversationRepository — no fakes for the persistence path under test.
 *
 * Also covers the stale-id branch: a persisted id whose conversation was deleted must be
 * cleared by FridayChatViewModel.openIfExists so the screen falls back to the empty state.
 */
@RunWith(AndroidJUnit4::class)
class ActiveConversationPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

    @Before
    fun setup() {
        File(context.filesDir, "app_bootstrap").deleteRecursively()
        File(context.filesDir, "app_prefs").deleteRecursively()
        File(context.filesDir, "friday_store_v1").deleteRecursively()
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
        File(context.filesDir, "friday_store_v1").deleteRecursively()
    }

    private fun newPrefs(): AppPreferences = AppPreferences(context, AppKeyStore(context), encryptor)
    private fun newConvoRepo(): FridayConversationRepository =
        FridayConversationRepository(context, AppKeyStore(context), encryptor)

    private fun persistence(prefs: AppPreferences): ActiveConversationPersistence =
        object : ActiveConversationPersistence {
            override fun loadActiveConversationId(): String? = prefs.fridayActiveConversationId
            override fun saveActiveConversationId(id: String?) { prefs.fridayActiveConversationId = id }
        }

    @Test
    fun activeConversationId_andTurns_surviveSimulatedProcessRestart() {
        val prefsA = newPrefs()
        val repoA = newConvoRepo()
        val storeA = DefaultActiveConversationStore(persistence(prefsA))

        val convo = repoA.createConversation("gw-1")
        repoA.addTurn(FridayTurn("u1", convo.id, "user", "hello from before restart", 1L))
        storeA.set(convo.id)
        assertEquals(convo.id, storeA.activeConversationId.value)
        assertEquals(1, repoA.getTurns(convo.id).size)

        // Simulate process death: close HXS, then reconstruct every component fresh.
        HexStorage().close()

        val prefsB = newPrefs()
        val repoB = newConvoRepo()
        val storeB = DefaultActiveConversationStore(persistence(prefsB))

        assertEquals(
            "active conversation id must persist across process restart",
            convo.id,
            storeB.activeConversationId.value,
        )
        assertNotNull("conversation row must persist across process restart", repoB.getConversation(convo.id))
        assertEquals(
            "turn content must persist across process restart",
            "hello from before restart",
            repoB.getTurns(convo.id).single().content,
        )
    }

    @Test
    fun requestNewChat_clearsPersistedActiveId_acrossProcessRestart() {
        val prefsA = newPrefs()
        val storeA = DefaultActiveConversationStore(persistence(prefsA))
        storeA.set("c-ghost")
        assertEquals("c-ghost", storeA.activeConversationId.value)

        storeA.requestNewChat()
        assertNull(storeA.activeConversationId.value)

        HexStorage().close()
        val prefsB = newPrefs()
        val storeB = DefaultActiveConversationStore(persistence(prefsB))
        assertNull(
            "new chat must clear the persisted id so restart lands on the empty state",
            storeB.activeConversationId.value,
        )
    }

    @Test
    fun openIfExists_clearsStalePersistedId_whenConversationWasDeleted() {
        val prefsA = newPrefs()
        val repoA = newConvoRepo()
        val storeA = DefaultActiveConversationStore(persistence(prefsA))
        val convo = repoA.createConversation("gw-1")
        storeA.set(convo.id)

        // Delete the conversation but leave the persisted active id pointing at it.
        repoA.deleteConversation(convo.id)

        HexStorage().close()

        // Reconstruct the VM's dependencies after restart, with the stale persisted id.
        val prefsB = newPrefs()
        val repoB = newConvoRepo()
        val storeB = DefaultActiveConversationStore(persistence(prefsB))
        assertEquals(convo.id, storeB.activeConversationId.value)

        val vm = buildVm(repoB, storeB)
        assertFalse("stale id does not open", vm.openIfExists(convo.id))
        assertNull(
            "openIfExists clears the stale persisted id so it won't retry",
            storeB.activeConversationId.value,
        )
        assertTrue(vm.messages.value.isEmpty())
    }

    private fun buildVm(
        repo: FridayConversationRepository,
        activeStore: DefaultActiveConversationStore,
    ): FridayChatViewModel {
        val brain = object : ChatBrain {
            override fun brainGateway(): GatewayConfig? = null
            override fun brainUnavailableMessage(): String = "no brain"
            override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = flow { emit(GatewayEvent.Done("ok")) }
            override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = flow { emit(GatewayEvent.Done("ok")) }
            override fun brainCancel() {}
        }
        val gateways = object : GatewayDirectory {
            override val gateways: StateFlow<List<GatewayConfig>> =
                MutableStateFlow<List<GatewayConfig>>(emptyList()).asStateFlow()
            override val brainSelection: StateFlow<String> =
                MutableStateFlow<String>("").asStateFlow()
            override fun brainGateway(): GatewayConfig? = null
            override fun voiceGateway(): GatewayConfig? = null
        }
        val contextEngine = object : ContextHistorySource {
            override suspend fun buildHistory(
                conversationId: String?,
                pendingTurns: List<FridayTurn>?,
            ): List<GatewayTurn> = (pendingTurns ?: emptyList()).map { GatewayTurn(it.role, it.content) }
            override fun onUserTurnPersisted(turn: FridayTurn) {}
            override fun onTurnCompleted(conversationId: String) {}
        }
        return FridayChatViewModel(gateways, repo, brain, contextEngine, activeStore)
    }
}
