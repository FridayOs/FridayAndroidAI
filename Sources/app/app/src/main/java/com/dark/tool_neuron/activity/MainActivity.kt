package com.dark.tool_neuron.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.AppCompatLocaleWrapper
import com.dark.tool_neuron.data.PendingInboundEvent
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.repo.InboundEventCenter
import com.dark.tool_neuron.ui.screens.system_ui.AppScaffold
import com.dark.tool_neuron.ui.theme.ToolNeuronTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeController: ThemeController

    @Inject lateinit var pendingInboundEvent: PendingInboundEvent

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocaleEntryPoint {
        fun localeWrapper(): AppCompatLocaleWrapper
    }

    override fun attachBaseContext(newBase: Context) {
        // App-scoped entry point, not Activity @Inject: fields aren't guaranteed injected before attachBaseContext.
        val wrapper = EntryPointAccessors
            .fromApplication(newBase.applicationContext, LocaleEntryPoint::class.java)
            .localeWrapper()
        super.attachBaseContext(wrapper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleEventIntent(intent)
        // Inference service binding is owned by TNApplication so background
        // work (RAG ingest, SD pipeline, scheduled jobs) doesn't lose the
        // service the moment MainActivity is recreated or finishes.
        enableEdgeToEdge()
        setContent {
            val mode by themeController.mode.collectAsStateWithLifecycle()
            val palette by themeController.palette.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeController.Mode.SYSTEM -> systemDark
                ThemeController.Mode.LIGHT -> false
                ThemeController.Mode.DARK -> true
            }
            ToolNeuronTheme(darkTheme = darkTheme, palette = palette) {
                AppScaffold()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEventIntent(intent)
    }

    // ACTION_OPEN_EVENT carries the full event payload as extras so a notification tap can re-surface
    // the card even after process death (the InboundEventCenter's in-RAM retention is gone by then).
    // The extras are attacker-reachable on this exported activity, so the reconstructed event is
    // untrusted PUSH data — surfaceFromNotification prefers a retained/coerced copy and otherwise
    // re-coerces it (CONFIRMATION -> capped STATUS), so a forged intent yields only a non-actionable
    // card. The assist flag stays in-process (never via an Intent extra).
    private fun handleEventIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_EVENT) return
        val event = InboundEventCenter.reconstruct(
            eventId = intent.getStringExtra(EXTRA_OPEN_EVENT_ID),
            correlationId = intent.getStringExtra(EXTRA_OPEN_EVENT_CORRELATION),
            kind = intent.getStringExtra(EXTRA_OPEN_EVENT_KIND),
            title = intent.getStringExtra(EXTRA_OPEN_EVENT_TITLE),
            body = intent.getStringExtra(EXTRA_OPEN_EVENT_BODY),
            urgency = intent.getStringExtra(EXTRA_OPEN_EVENT_URGENCY),
            receivedAt = intent.getLongExtra(EXTRA_OPEN_EVENT_RECEIVED_AT, 0L),
        ) ?: return
        pendingInboundEvent.set(event)
    }

    // The assist one-shot flag is set in-process by FridayVoiceInteractionSession (never via an
    // Intent extra), so an exported-activity caller can't forge an assist invocation.
    companion object {
        const val ACTION_ASSIST_VOICE = "com.dark.tool_neuron.action.ASSIST_VOICE"
        const val ACTION_OPEN_EVENT = "com.dark.tool_neuron.action.OPEN_EVENT"
        const val EXTRA_OPEN_EVENT_ID = "com.dark.tool_neuron.extra.OPEN_EVENT_ID"
        const val EXTRA_OPEN_EVENT_CORRELATION = "com.dark.tool_neuron.extra.OPEN_EVENT_CORRELATION"
        const val EXTRA_OPEN_EVENT_KIND = "com.dark.tool_neuron.extra.OPEN_EVENT_KIND"
        const val EXTRA_OPEN_EVENT_TITLE = "com.dark.tool_neuron.extra.OPEN_EVENT_TITLE"
        const val EXTRA_OPEN_EVENT_BODY = "com.dark.tool_neuron.extra.OPEN_EVENT_BODY"
        const val EXTRA_OPEN_EVENT_URGENCY = "com.dark.tool_neuron.extra.OPEN_EVENT_URGENCY"
        const val EXTRA_OPEN_EVENT_RECEIVED_AT = "com.dark.tool_neuron.extra.OPEN_EVENT_RECEIVED_AT"
    }
}
