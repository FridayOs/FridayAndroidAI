package com.dark.tool_neuron.service.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.dark.tool_neuron.activity.MainActivity
import com.dark.tool_neuron.data.PendingAssistInvocation
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// onShow never renders assist UI: it hands off to MainActivity on the Friday Voice route and
// hides immediately, so no overlay surface or mic access happens inside this process. The
// one-shot flag is set in-process (same-APK session service) so an exported-activity caller
// can never forge an assist invocation via extras.
class FridayVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AssistEntryPoint {
        fun pendingAssist(): PendingAssistInvocation
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        EntryPointAccessors
            .fromApplication(context.applicationContext, AssistEntryPoint::class.java)
            .pendingAssist()
            .set()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_ASSIST_VOICE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startAssistantActivity(intent)
        hide()
    }
}
