package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// Thin delegate to LiveVoiceSessionFactory/LiveVoiceSession — owns nothing FRI-548 already owns.
@Singleton
class GeminiLiveVoiceAdapter @Inject constructor(
    private val factory: LiveVoiceSessionFactory,
) : LiveVoiceAdapter {

    override fun supports(config: GatewayConfig): Boolean = factory.supports(config)

    override fun open(config: GatewayConfig, voice: String, locale: String, bargeIn: Boolean): LiveVoiceHandle {
        val session = factory.create(config, voice, locale, bargeIn)
        return SessionHandle(session)
    }

    private class SessionHandle(private val session: LiveVoiceSession) : LiveVoiceHandle {
        override fun run(): Flow<LiveEvent> = session.run()
        override fun endUserTurn() = session.endUserTurn()
        override fun onAudioFocusLost() = session.onAudioFocusLost()
        override fun onAudioRouteChanged() = session.onAudioRouteChanged()
        override fun onBackground() = session.onBackground()
        override fun onPermissionRevoked() = session.onPermissionRevoked()

        // session.cancel() already cancels the in-flight brain turn internally — do not double-cancel.
        override fun cancel() = session.cancel()

        override fun brainTurn(history: List<GatewayTurn>, transcript: String): Flow<GatewayEvent> =
            session.cloudBridge.brainTurn(history, transcript)
        override fun brainCancel() = session.cloudBridge.brainCancel()
        override fun brainConfirm(): Boolean = session.cloudBridge.brainConfirm()
    }
}
