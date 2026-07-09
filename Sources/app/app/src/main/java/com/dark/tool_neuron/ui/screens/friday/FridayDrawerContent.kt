package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.friday.components.FRIDAY_AVATAR_ASSET
import com.dark.tool_neuron.ui.screens.friday.components.rememberAssetBitmap
import com.dark.tool_neuron.ui.util.FridayPalette
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.viewmodel.FridayDrawerViewModel

@Composable
fun FridayDrawerContent(
    currentRoute: String?,
    onNavigateVoice: () -> Unit,
    onNavigateChat: () -> Unit,
    onNavigateHistory: () -> Unit,
    onSignedOut: () -> Unit,
    drawerViewModel: FridayDrawerViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val account by accountViewModel.state.collectAsStateWithLifecycle()
    val gateways by drawerViewModel.gateways.collectAsStateWithLifecycle()
    val selectedId by drawerViewModel.selectedId.collectAsStateWithLifecycle()
    val themeMode by drawerViewModel.themeMode.collectAsStateWithLifecycle()
    val avatar = rememberAssetBitmap(FRIDAY_AVATAR_ASSET)

    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddGatewayDialog(
            providers = drawerViewModel.providers,
            onDismiss = { showAddDialog = false },
            onConfirm = { provider, label, baseUrl, apiKey, model ->
                drawerViewModel.addGateway(provider, label, baseUrl, apiKey, model)
                showAddDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 46.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(2.dp, FridayPalette.Primary, CircleShape),
                )
            }
            Spacer(Modifier.size(11.dp))
            Column {
                Text(
                    text = (account as? AccountState.Authenticated)?.displayName
                        ?: stringResource(R.string.friday_brand_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = (account as? AccountState.Authenticated)?.plan
                        ?: stringResource(R.string.friday_drawer_free_plan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        NavRow(
            icon = TnIcons.Mic,
            label = stringResource(R.string.friday_nav_voice),
            active = currentRoute == com.dark.tool_neuron.model.NavScreens.FridayVoice.route,
            onClick = onNavigateVoice,
        )
        NavRow(
            icon = TnIcons.MessageCircle,
            label = stringResource(R.string.friday_nav_chat),
            active = currentRoute == com.dark.tool_neuron.model.NavScreens.FridayChat.route,
            onClick = onNavigateChat,
        )
        NavRow(
            icon = TnIcons.Clock,
            label = stringResource(R.string.friday_nav_history),
            active = currentRoute == com.dark.tool_neuron.model.NavScreens.FridayHistory.route,
            onClick = onNavigateHistory,
        )

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(R.string.friday_gateways_title))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(TnIcons.Plus, contentDescription = stringResource(R.string.friday_gateways_add), modifier = Modifier.size(18.dp), tint = FridayPalette.Primary)
            }
        }
        Spacer(Modifier.height(10.dp))
        FridayProviderRow()
        gateways.forEach { gateway ->
            Spacer(Modifier.height(8.dp))
            GatewayRow(
                gateway = gateway,
                selected = gateway.id == selectedId,
                onClick = { drawerViewModel.selectGateway(gateway.id) },
                onDelete = { drawerViewModel.deleteGateway(gateway.id) },
            )
        }
        if (gateways.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.friday_gateways_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(stringResource(R.string.friday_appearance_section))
        Spacer(Modifier.height(10.dp))
        AppearanceToggle(mode = themeMode, onSelect = drawerViewModel::setThemeMode)

        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable {
                    accountViewModel.signOut()
                    onSignedOut()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.friday_sign_out),
                color = FridayPalette.Danger,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.friday_version_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NavRow(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(13.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FridayProviderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.6f)
            .clip(RoundedCornerShape(13.dp))
            .border(1.5.dp, FridayPalette.Primary, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialTile(text = "FR")
        Spacer(Modifier.size(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.friday_provider_friday),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.friday_provider_coming_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GatewayRow(gateway: GatewayConfig, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val border = if (selected) FridayPalette.Primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(1.5.dp, border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialTile(text = gateway.label.take(2).uppercase())
        Spacer(Modifier.size(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gateway.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "${gateway.provider.displayName} · ${gateway.displayModel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(FridayPalette.Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TnIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = FridayPalette.OnPrimary,
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TnIcons.Trash,
                contentDescription = stringResource(R.string.friday_gateway_delete),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InitialTile(text: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(FridayPalette.Primary, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = FridayPalette.OnPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AddGatewayDialog(
    providers: List<GatewayProvider>,
    onDismiss: () -> Unit,
    onConfirm: (GatewayProvider, String, String, String, String) -> Unit,
) {
    var provider by remember { mutableStateOf(providers.firstOrNull() ?: GatewayProvider.OPENAI) }
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = apiKey.isNotBlank() || provider.defaultBaseUrl.isBlank(),
                onClick = { onConfirm(provider, label, baseUrl, apiKey, model) },
            ) { Text(stringResource(R.string.friday_gateway_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.friday_gateway_cancel)) }
        },
        title = { Text(stringResource(R.string.friday_gateways_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.friday_gateway_provider_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    providers.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { p ->
                                ProviderChip(
                                    label = p.displayName,
                                    selected = p == provider,
                                    modifier = Modifier.weight(1f),
                                    onClick = { provider = p },
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                DialogField(stringResource(R.string.friday_gateway_label_hint), label, provider.displayName) { label = it }
                Spacer(Modifier.height(10.dp))
                DialogField(stringResource(R.string.friday_gateway_api_key_hint), apiKey, "", secret = true) { apiKey = it }
                Spacer(Modifier.height(10.dp))
                DialogField(stringResource(R.string.friday_gateway_model_hint), model, provider.defaultModel) { model = it }
                Spacer(Modifier.height(10.dp))
                DialogField(stringResource(R.string.friday_gateway_base_url_hint), baseUrl, provider.defaultBaseUrl.ifBlank { "https://…" }) { baseUrl = it }
            }
        },
    )
}

@Composable
private fun ProviderChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) FridayPalette.Primary else Color.Transparent
    val fg = if (selected) FridayPalette.OnPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) FridayPalette.Primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    placeholder: String,
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(FridayPalette.Primary),
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppearanceToggle(mode: ThemeController.Mode, onSelect: (ThemeController.Mode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ToggleChip(TnIcons.Settings, stringResource(R.string.friday_appearance_system), mode == ThemeController.Mode.SYSTEM, Modifier.weight(1f)) { onSelect(ThemeController.Mode.SYSTEM) }
        ToggleChip(TnIcons.Sun, stringResource(R.string.friday_appearance_light), mode == ThemeController.Mode.LIGHT, Modifier.weight(1f)) { onSelect(ThemeController.Mode.LIGHT) }
        ToggleChip(TnIcons.Moon, stringResource(R.string.friday_appearance_dark), mode == ThemeController.Mode.DARK, Modifier.weight(1f)) { onSelect(ThemeController.Mode.DARK) }
    }
}

@Composable
private fun ToggleChip(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (selected) FridayPalette.Primary else Color.Transparent
    val fg = if (selected) FridayPalette.OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = fg)
        Spacer(Modifier.size(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
