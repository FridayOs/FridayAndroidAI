package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.repo.ActiveConversationStore
import com.dark.tool_neuron.repo.DefaultActiveConversationStore
import com.dark.tool_neuron.ui.navigation.ActiveConversationStoreEntryPoint
import com.dark.tool_neuron.viewmodel.FridayChatViewModel
import com.dark.tool_neuron.viewmodel.FridayVoiceViewModel
import dagger.hilt.android.EntryPointAccessors
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/*
 * FRI-574 round-6 BLOCKER 1: proves the production Hilt graph injects ONE shared persisted
 * ActiveConversationStore singleton into BOTH FridayVoiceViewModel and FridayChatViewModel,
 * closing the round-6 gap where Voice's @Inject constructor did NOT declare/forward the
 * dependency (so Hilt production used a NoOp-backed DefaultActiveConversationStore() default
 * instead of the @Singleton bound at GatewayModule.kt:80-82).
 *
 * No @HiltAndroidTest / Hilt test runner infra exists in this repo (no hilt-android-testing
 * dependency), so this uses the REAL production graph via EntryPointAccessors.fromApplication
 * over the existing ActiveConversationStoreEntryPoint (TNavigation.kt:102), plus reflection
 * on the VMs' @Inject constructors to assert the param is declared. The resolved store is
 * the live @Singleton — do NOT delete vault dirs or close HXS here (that would corrupt the
 * production singleton mid-process). Only PolicyEngine/BootIntegrity relaxed-for-testing
 * toggles are flipped, and only because the app process may not have completed boot verify.
 */
@RunWith(AndroidJUnit4::class)
class ProductionActiveConversationStoreDiTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
    }

    @After
    fun cleanup() {
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
    }

    private fun store(): ActiveConversationStore =
        EntryPointAccessors.fromApplication(
            context,
            ActiveConversationStoreEntryPoint::class.java,
        ).activeConversationStore()

    @Test
    fun entryPoint_resolvesSingletonStore_sharedAcrossResolutions() {
        val a = store()
        val b = store()
        assertSame("EntryPoint resolves the same @Singleton across calls", a, b)

        // FRI-574 round-7: the production store is the live HXS-backed @Singleton, so any
        // mutation here persists into the target app's real state. Snapshot the pre-test
        // value and restore it in a finally so a failing assertion cannot leak "di-probe"
        // and an existing active conversation is preserved even when the cross-visibility
        // check below mutates the store.
        val before = b.activeConversationId.value
        try {
            a.set("di-probe")
            assertEquals("singleton: a.set is visible to b", "di-probe", b.activeConversationId.value)
        } finally {
            // Restore exactly — null vs id matters to the persisted vault. set(null) clears
            // the key; set(before) re-persists the original id (or clears it if before was null).
            a.set(before)
        }
    }

    @Test
    fun entryPoint_store_isDefaultActiveConversationStore() {
        val store = store()
        assertTrue(
            "the bound ActiveConversationStore is DefaultActiveConversationStore (GatewayModule @Binds)",
            store is DefaultActiveConversationStore,
        )
    }

    @Test
    fun voiceAndChatViewModels_hiltConstructors_declareActiveConversationStoreParam() {
        val voiceInjectCtor = FridayVoiceViewModel::class.java.declaredConstructors
            .first { it.isAnnotationPresent(Inject::class.java) }
        assertTrue(
            "FridayVoiceViewModel @Inject ctor must declare ActiveConversationStore (round-6 BLOCKER 1)",
            voiceInjectCtor.parameterTypes.any { it == ActiveConversationStore::class.java },
        )

        val chatInjectCtor = FridayChatViewModel::class.java.declaredConstructors
            .first { it.isAnnotationPresent(Inject::class.java) }
        assertTrue(
            "FridayChatViewModel @Inject ctor must declare ActiveConversationStore",
            chatInjectCtor.parameterTypes.any { it == ActiveConversationStore::class.java },
        )
    }

    @Test
    fun production_store_persistence_isNotNoOp() {
        val store = store() as DefaultActiveConversationStore
        val persistenceField = DefaultActiveConversationStore::class.java.getDeclaredField("persistence")
        persistenceField.isAccessible = true
        val persistence = persistenceField.get(store)
        assertNotNull("persistence backing field is set", persistence)
        assertNotEquals(
            "production store is backed by the real AppPreferences adapter, not NoOpPersistence",
            "NoOpPersistence",
            persistence!!::class.java.simpleName,
        )
    }
}
