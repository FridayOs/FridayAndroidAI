package com.dark.tool_neuron.ui.screens.setup_screen

import com.friday.ai.R

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel

/*
 * Local-path pack list for ModelSetupScreen (design-spec §7, HTML `PACKS`
 * array). Extracted out of ModelSetupScreen.kt to keep that file under the
 * 200-line guideline; pure display + real pack-id dispatch, no local state.
 */
@Composable
internal fun ModelSetupPackList(
    spacingSm: Dp,
    onPackSelected: (packId: String) -> Unit,
) {
    Text(
        text = stringResource(R.string.friday_model_setup_packs_label).uppercase(),
        fontFamily = jakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(spacingSm))

    ModelPackCard(
        name = stringResource(R.string.friday_model_setup_pack_small_name),
        description = stringResource(R.string.friday_model_setup_pack_small_desc),
        size = stringResource(R.string.friday_model_setup_pack_small_size),
        selected = false,
        onClick = { onPackSelected(ModelStoreViewModel.PACK_CHAT_ONLY) },
    )
    Spacer(Modifier.height(spacingSm))
    ModelPackCard(
        name = stringResource(R.string.friday_model_setup_pack_voice_name),
        description = stringResource(R.string.friday_model_setup_pack_voice_desc),
        size = stringResource(R.string.friday_model_setup_pack_voice_size),
        selected = false,
        onClick = { onPackSelected(ModelStoreViewModel.PACK_CHAT_VOICE) },
    )
    Spacer(Modifier.height(spacingSm))
    ModelPackCard(
        name = stringResource(R.string.friday_model_setup_pack_plus_name),
        description = stringResource(R.string.friday_model_setup_pack_plus_desc),
        size = stringResource(R.string.friday_model_setup_pack_plus_size),
        selected = false,
        onClick = { onPackSelected(ModelStoreViewModel.PACK_LARGE_CHAT_VOICE) },
    )
}
