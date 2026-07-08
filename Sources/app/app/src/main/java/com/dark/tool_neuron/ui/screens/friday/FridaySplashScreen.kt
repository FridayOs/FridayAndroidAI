package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.screens.friday.components.FRIDAY_LOGO_ASSET
import com.dark.tool_neuron.ui.screens.friday.components.FridayThinkingDots
import com.dark.tool_neuron.ui.screens.friday.components.rememberAssetBitmap
import kotlinx.coroutines.delay

@Composable
fun FridaySplashScreen(
    innerPadding: PaddingValues,
    onResolved: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(2400)
        onResolved()
    }
    val logo = rememberAssetBitmap(FRIDAY_LOGO_ASSET)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (logo != null) {
                Image(bitmap = logo, contentDescription = null, modifier = Modifier.size(128.dp))
            }
            Text(
                text = stringResource(R.string.friday_splash_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        FridayThinkingDots(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 46.dp),
        )
    }
}
