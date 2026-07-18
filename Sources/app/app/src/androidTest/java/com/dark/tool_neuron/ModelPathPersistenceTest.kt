package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.viewmodel.PackCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * FRI-582 Codex-QA round-2 B5#2 (persistence half): [AppPreferences.modelPath]
 * / [AppPreferences.modelPack] are the fields
 * [com.dark.tool_neuron.viewmodel.ModelStoreViewModel.recordModelPathChoice]
 * writes when Model Setup confirms a choice (see ModelSetupCta.kt +
 * ModelSetupScreen.kt onLocalPackConfirmed wiring). Same encrypted-HXS vault
 * as PrefsPersistenceTest, so this follows its exact setup/teardown + manual
 * (no-Hilt) construction + simulated-restart pattern. The paired resume-order
 * assertion (does [OnboardingGateLogic] land on the right step once these
 * flags are set) is covered separately on plain JVM in
 * OnboardingGateLogicTest, since the gate itself has no HXS/Context
 * dependency.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ModelPathPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

    @Before
    fun setup() {
        File(context.filesDir, "app_bootstrap").deleteRecursively()
        File(context.filesDir, "app_prefs").deleteRecursively()
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
    }

    @Test
    fun modelPathAndPackPersistAcrossSimulatedProcessRestart() {
        val keyStoreA = AppKeyStore(context)
        val prefsA = AppPreferences(context, keyStoreA, encryptor)

        prefsA.modelPath = "local"
        prefsA.modelPack = PackCatalog.PACK_CHAT_VOICE
        prefsA.modelSetupDone = true

        assertEquals("local", prefsA.modelPath)
        assertEquals(PackCatalog.PACK_CHAT_VOICE, prefsA.modelPack)

        HexStorage().close()

        val keyStoreB = AppKeyStore(context)
        val prefsB = AppPreferences(context, keyStoreB, encryptor)

        assertEquals(
            "modelPath must persist across simulated process restart",
            "local",
            prefsB.modelPath,
        )
        assertEquals(
            "modelPack must persist across simulated process restart",
            PackCatalog.PACK_CHAT_VOICE,
            prefsB.modelPack,
        )
        assertTrue(
            "modelSetupDone must persist alongside the path/pack choice",
            prefsB.modelSetupDone,
        )
    }

    @Test
    fun modelPackStaysNullWhenGatewayPathChosenNoLocalPack() {
        // recordModelPathChoice(path, packId = null) for the "gateway" path -
        // modelPack must round-trip as null, not an empty-string sentinel.
        val keyStoreA = AppKeyStore(context)
        val prefsA = AppPreferences(context, keyStoreA, encryptor)

        prefsA.modelPath = "gateway"
        prefsA.modelPack = null

        HexStorage().close()

        val keyStoreB = AppKeyStore(context)
        val prefsB = AppPreferences(context, keyStoreB, encryptor)

        assertEquals("gateway", prefsB.modelPath)
        assertNull(
            "modelPack must stay null when no local pack was chosen",
            prefsB.modelPack,
        )
    }
}
