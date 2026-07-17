package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.repo.InboundEventCenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// FRI-555: DI wiring for the event gateway. NonceStore/DedupeStore (B4: EventStateStore-backed)
// and CorrelationTracker (B3) each carry their own @Inject constructor, so Hilt resolves them by
// constructor injection alone -- no @Provides needed here (adding one would duplicate-bind). The
// trust/consent/state/delivery seams are interfaces bound to their concrete implementations.
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

    @Binds
    @Singleton
    abstract fun bindEventStateStore(impl: PrefsEventStateStore): EventStateStore

    // FRI-555 B1: the approval seam a future brain/outbound integration binds to. Default records
    // the decision durably; it never executes the action (see InboundActionBridge doc).
    @Binds
    @Singleton
    abstract fun bindInboundActionBridge(impl: RecordingInboundActionBridge): InboundActionBridge
}
