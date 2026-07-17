package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.repo.InboundEventCenter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// FRI-555: DI wiring for the event gateway. NonceStore/DedupeStore have no @Inject constructor
// (plain JVM classes shared with pure unit tests) so they need @Provides; the trust/consent seams
// and the delivery sink are interfaces bound to their concrete implementations.
@Module
@InstallIn(SingletonComponent::class)
abstract class EventGatewayModule {

    @Binds
    @Singleton
    abstract fun bindEventSourceRegistry(impl: PrefsEventSourceRegistry): EventSourceRegistry

    @Binds
    @Singleton
    abstract fun bindInboundEventConsent(impl: PrefsInboundEventConsent): InboundEventConsent

    @Binds
    @Singleton
    abstract fun bindEventDeliverySink(impl: InboundEventCenter): EventDeliverySink

    companion object {
        @Provides
        @Singleton
        fun provideNonceStore(): NonceStore = NonceStore()

        @Provides
        @Singleton
        fun provideDedupeStore(): DedupeStore = DedupeStore()
    }
}
