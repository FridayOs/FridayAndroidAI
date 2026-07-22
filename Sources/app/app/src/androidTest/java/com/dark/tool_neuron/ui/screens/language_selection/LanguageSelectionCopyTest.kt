package com.dark.tool_neuron.ui.screens.language_selection

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import com.dark.tool_neuron.viewmodel.LanguageViewModel
import com.friday.ai.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 — locks the Language screen's P0-verbatim visible copy (design-spec §2,
 * HTML:64-104 + langCards 1987-1990 + langNote 2107 + langCta 1991):
 *  - Each card shows the language NAME plus the full-sentence description, not the
 *    name duplicated ("English" -> "Use FRIDAY AI in English.").
 *  - The note is one bilingual "This changes app text only. · Thiết lập này chỉ đổi
 *    ngôn ngữ giao diện." string (rendered once, not two lines).
 *  - The CTA prompt (no selection) is the bilingual "Select a language / Chọn ngôn ngữ".
 *
 * Hosts the REAL [LanguageSelectionScreen] through a REAL [LanguageViewModel] backed
 * by a REAL on-device [LanguageController] + [AppCompatLocaleWrapper] — no fakes.
 * LanguageController is a concrete final class over the HXS-backed AppPreferences, so
 * this mirrors GatewayConfigRepositoryTest's HXS setup (BootIntegrity relaxed + disk
 * cleanup). LocalFridayAccent falls back to its static default (SUN).
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSelectionCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: AppPreferences

    private fun str(id: Int) = context.getString(id)

    @Before
    fun setup() {
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        prefs = AppPreferences(context, AppKeyStore(context), HxsEncryptor())
    }

    @After
    fun cleanup() {
        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
        File(context.filesDir, "app_prefs").deleteRecursively()
    }

    private fun hostScreen(): LanguageController {
        val controller = LanguageController(prefs)
        val vm = LanguageViewModel(controller, AppCompatLocaleWrapper(context, controller))
        composeTestRule.setContent {
            LanguageSelectionScreen(
                innerPadding = PaddingValues(0.dp),
                onContinue = {},
                viewModel = vm,
            )
        }
        composeTestRule.waitForIdle()
        return controller
    }

    @Test
    fun cards_show_name_plus_full_sentence_description() {
        hostScreen()
        // Card name is the language name; description is the full sentence — never duplicated.
        composeTestRule.onNodeWithText(str(R.string.friday_language_english)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_lang_card_en_desc)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_language_vietnamese)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_lang_card_vi_desc)).assertIsDisplayed()
    }

    @Test
    fun note_is_one_bilingual_string() {
        hostScreen()
        val noteNodes = composeTestRule.onAllNodesWithText(str(R.string.onboarding_lang_note))
        assertEquals(1, noteNodes.fetchSemanticsNodes().size)
    }

    @Test
    fun cta_prompt_when_no_selection_is_bilingual() {
        hostScreen()
        composeTestRule.onNodeWithText(str(R.string.onboarding_lang_cta_prompt)).assertIsDisplayed()
    }

    @Test
    fun selecting_english_updates_state_and_cta_label() {
        val controller = hostScreen()
        composeTestRule.onNodeWithText(str(R.string.onboarding_lang_card_en_desc)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(AppLanguage.EN, controller.selected.value)
        composeTestRule.onNodeWithText(str(R.string.onboarding_lang_cta_en)).assertIsDisplayed()
    }

    @Test
    fun selecting_vietnamese_updates_state_and_cta_label() {
        val controller = hostScreen()
        composeTestRule.onNodeWithText(str(R.string.onboarding_lang_card_vi_desc)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(AppLanguage.VI, controller.selected.value)
        composeTestRule.onNodeWithText(str(R.string.onboarding_lang_cta_vi)).assertIsDisplayed()
    }
}
