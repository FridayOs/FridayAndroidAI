package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.data.LanguageController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Interface bindings for the context-engine DI graph. ContextRetriever is
// constructed directly by ContextEngine (not Hilt-injected) since it has no
// other consumer - see ContextEngine.kt.
@Module
@InstallIn(SingletonComponent::class)
abstract class ContextModule {

    @Binds
    @Singleton
    abstract fun bindContextHistorySource(impl: ContextEngine): ContextHistorySource

    @Binds
    @Singleton
    abstract fun bindLocalSummaryModel(impl: InferenceSummaryModel): LocalSummaryModel

    @Binds
    @Singleton
    abstract fun bindContextEngineStore(impl: ContextStore): ContextEngineStore

    @Binds
    @Singleton
    abstract fun bindLocaleSource(impl: LanguageController): LocaleSource
}
