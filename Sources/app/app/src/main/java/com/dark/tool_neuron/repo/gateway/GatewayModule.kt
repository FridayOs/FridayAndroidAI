package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.GatewayConfigRepository
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
}
