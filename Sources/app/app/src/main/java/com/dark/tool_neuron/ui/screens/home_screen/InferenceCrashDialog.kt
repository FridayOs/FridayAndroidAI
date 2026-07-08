package com.dark.tool_neuron.ui.screens.home_screen

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.service.inference.CrashInfo
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.crash_report.CrashReportActivity
import com.dark.tool_neuron.ui.theme.LocalTnShapes

/**
 * Minimal failure surface — module slug + one-line message + two actions:
 * "Details" launches the full [CrashReportActivity] with tabbed history +
 * export. "Dismiss" closes the dialog. The dialog never shows file paths,
 * stack traces, op-ids, or signals — those belong in the Activity.
 */
@Composable
fun InferenceCrashDialog(
    crash: CrashInfo,
    onDismiss: () -> Unit,
    onOpenModelManager: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = if (crash.source == CrashInfo.Source.NATIVE_CRASH)
                    "${crash.lib} crashed"
                else "${crash.lib} reported an error",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = LocalTnShapes.current.md,
                ) {
                    Text(
                        text = crash.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                crash.suggestion?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Log.i("InferenceCrashDialog", "Show details tapped")
                val intent = Intent(context, CrashReportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure { Log.e("InferenceCrashDialog", "startActivity failed", it) }
                onDismiss()
            }) { Text("Show details") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        },
    )
}
