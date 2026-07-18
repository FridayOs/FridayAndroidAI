package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppCompatLocaleWrapper
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.LanguageController
import com.dark.tool_neuron.model.AppLanguage
import com.friday.ai.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FRI-582 Codex-QA round-2 B5#5: [AppCompatLocaleWrapper.wrap] is the real
 * live-locale seam every onboarding/setup screen resolves strings through
 * (see its `wrap(base)` -> `createConfigurationContext` construction). Proves
 * an EN vs VI [LanguageController.setSelected] choice actually flips the
 * resolved string resource at runtime, not just the stored preference flag -
 * using the real HXS-backed [AppPreferences] (no Hilt) same as
 * PrefsPersistenceTest / ModelPathPersistenceTest, and the real
 * android.content.res.Configuration locale-swap path (no mocking of
 * Resources/Configuration).
 */
@RunWith(AndroidJUnit4::class)
class LiveLocaleWrapperTest {

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

    private fun wrapperFor(language: AppLanguage): AppCompatLocaleWrapper {
        val keyStore = AppKeyStore(context)
        val prefs = AppPreferences(context, keyStore, encryptor)
        val languageController = LanguageController(prefs)
        languageController.setSelected(language)
        return AppCompatLocaleWrapper(context, languageController)
    }

    @Test
    fun en_selection_resolves_english_password_subtitle_string() {
        val wrapper = wrapperFor(AppLanguage.EN)

        val resolved = wrapper.wrap(context).getString(R.string.friday_setup_password_create_subtitle)

        assertEquals("At least 6 digits", resolved)
    }

    @Test
    fun vi_selection_resolves_vietnamese_password_subtitle_string() {
        val wrapper = wrapperFor(AppLanguage.VI)

        val resolved = wrapper.wrap(context).getString(R.string.friday_setup_password_create_subtitle)

        assertEquals("Tối thiểu 6 chữ số", resolved)
    }

    @Test
    fun switching_selection_on_the_same_wrapper_flips_the_live_resolved_string() {
        // Guards against a wrapper caching the first-resolved Configuration
        // instead of re-reading LanguageController.selected on every wrap()
        // call - a real regression path since AppCompatLocaleWrapper builds
        // config from currentTag() fresh each time.
        val keyStore = AppKeyStore(context)
        val prefs = AppPreferences(context, keyStore, encryptor)
        val languageController = LanguageController(prefs)
        val wrapper = AppCompatLocaleWrapper(context, languageController)

        languageController.setSelected(AppLanguage.EN)
        assertEquals(
            "At least 6 digits",
            wrapper.wrap(context).getString(R.string.friday_setup_password_create_subtitle),
        )

        languageController.setSelected(AppLanguage.VI)
        assertEquals(
            "Tối thiểu 6 chữ số",
            wrapper.wrap(context).getString(R.string.friday_setup_password_create_subtitle),
        )
    }
}
