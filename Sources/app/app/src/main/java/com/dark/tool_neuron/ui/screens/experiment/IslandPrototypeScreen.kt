package com.dark.tool_neuron.ui.screens.experiment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dark.tool_neuron.service.island.IslandAccessibilityService
import com.dark.tool_neuron.service.island.IslandOverlayService
import com.dark.tool_neuron.service.island.IslandPositionStore
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun IslandPrototypeScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val dimens = LocalDimens.current

    LaunchedEffect(Unit) { IslandPositionStore.init(context) }

    val position by IslandPositionStore.position.collectAsState()
    val running  by IslandPositionStore.running.collectAsState()
    val accessibilityBound by IslandPositionStore.accessibilityActive.collectAsState()

    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityEnabledSetting by remember {
        mutableStateOf(context.isIslandAccessibilityEnabled())
    }
    LaunchedEffect(running) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        accessibilityEnabledSetting = context.isIslandAccessibilityEnabled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingLg),
    ) {
        Text(
            text = "Island calibration",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        StatusRow(
            running = running,
            canDrawOverlays = canDrawOverlays,
            accessibilityEnabled = accessibilityEnabledSetting || accessibilityBound,
        )

        if (!canDrawOverlays) {
            Button(
                onClick = { context.requestOverlayPermission() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Grant overlay permission") }
        }

        Button(
            onClick = { context.openAccessibilitySettings() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (accessibilityEnabledSetting || accessibilityBound)
                    "Manage accessibility (smart dodge)"
                else
                    "Enable accessibility (smart dodge)"
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            Button(
                onClick = { context.startIslandService() },
                enabled = canDrawOverlays,
                modifier = Modifier.weight(1f),
            ) { Text(if (running) "Restart" else "Start") }
            OutlinedButton(
                onClick = { context.stopIslandService() },
                enabled = canDrawOverlays,
                modifier = Modifier.weight(1f),
            ) { Text("Stop") }
        }

        SectionLabel("Y offset (${"%.0f".format(position.offsetYDp)} dp)")
        Slider(
            value = position.offsetYDp,
            onValueChange = { IslandPositionStore.setOffset(it) },
            valueRange = 0f..120f,
        )

        OutlinedButton(
            onClick = { IslandPositionStore.setOffset(0f) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset offset") }
    }
}

@Composable
private fun StatusRow(
    running: Boolean,
    canDrawOverlays: Boolean,
    accessibilityEnabled: Boolean,
) {
    val dimens = LocalDimens.current
    Surface(
        shape = LocalTnShapes.current.lg,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Text(
                text = if (running) "Service: running" else "Service: stopped",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (canDrawOverlays)
                    "Overlay permission: granted"
                else
                    "Overlay permission: not granted",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (accessibilityEnabled)
                    "Smart dodge: active"
                else
                    "Smart dodge: off (pill won't move around clickable items)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Context.requestOverlayPermission() {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:$packageName".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun Context.openAccessibilitySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun Context.isIslandAccessibilityEnabled(): Boolean {
    val expected = ComponentName(this, IslandAccessibilityService::class.java).flattenToString()
    val raw = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return raw.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun Context.startIslandService() {
    val intent = Intent(this, IslandOverlayService::class.java)
    ContextCompat.startForegroundService(this, intent)
}

private fun Context.stopIslandService() {
    val intent = Intent(this, IslandOverlayService::class.java)
        .setAction(IslandOverlayService.ACTION_STOP)
    startService(intent)
}
