package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.gateway.live.BrainGatewayLiveAdapter
import com.dark.tool_neuron.repo.gateway.live.LiveAudioCapture
import com.dark.tool_neuron.repo.gateway.live.LiveAudioPlayer
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSink
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSource
import com.dark.tool_neuron.repo.gateway.live.LiveBrainGateway
import com.dark.tool_neuron.repo.gateway.live.GeminiLiveVoiceAdapter
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceAdapter
import com.dark.tool_neuron.viewmodel.GatewayConfigVoiceGatewayStatePort
import com.dark.tool_neuron.viewmodel.VoiceGatewayStatePort
import com.dark.tool_neuron.voice.VoiceIo
import com.dark.tool_neuron.voice.VoiceModelManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {

    @Binds
    @Singleton
    abstract fun bindBrainBridge(impl: GatewayRoleRouter): BrainBridge

    @Binds
    @Singleton
    abstract fun bindLiveBrainGateway(impl: BrainGatewayLiveAdapter): LiveBrainGateway

    @Binds
    @Singleton
    abstract fun bindLiveAudioSource(impl: LiveAudioCapture): LiveAudioSource

    @Binds
    @Singleton
    abstract fun bindLiveAudioSink(impl: LiveAudioPlayer): LiveAudioSink

    @Binds
    @Singleton
    abstract fun bindChatBrain(impl: GatewayRoleRouter): ChatBrain

    @Binds
    @Singleton
    abstract fun bindVoiceRoutePort(impl: VoiceRouter): VoiceRoutePort

    @Binds
    @Singleton
    abstract fun bindGatewayDirectory(impl: GatewayConfigRepository): GatewayDirectory

    @Binds
    @Singleton
    abstract fun bindFridayConvoStore(impl: FridayConversationRepository): FridayConvoStore

    @Binds
    @Singleton
    abstract fun bindVoiceIo(impl: VoiceModelManager): VoiceIo

    @Binds
    @Singleton
    abstract fun bindLiveVoiceAdapter(impl: GeminiLiveVoiceAdapter): LiveVoiceAdapter

    @Binds
    @Singleton
    abstract fun bindVoiceGatewayStatePort(impl: GatewayConfigVoiceGatewayStatePort): VoiceGatewayStatePort
}
