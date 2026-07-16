package com.dark.tool_neuron.repo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InboundEventModule {

    @Binds
    @Singleton
    abstract fun bindInboundEventPort(impl: InboundEventCenter): InboundEventPort
}
