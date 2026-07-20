package com.dark.tool_neuron.data

import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.repo.ActiveConversationPersistence
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideHxsEncryptor(): HxsEncryptor = HxsEncryptor()

    // FRI-574 round-5 BLOCKER 2: adapt the HXS-backed AppPreferences vault to the
    // ActiveConversationPersistence seam so DefaultActiveConversationStore survives
    // process death. Same vault PrefsPersistenceTest proves survives HexStorage close.
    @Provides
    @Singleton
    fun provideActiveConversationPersistence(prefs: AppPreferences): ActiveConversationPersistence =
        object : ActiveConversationPersistence {
            override fun loadActiveConversationId(): String? = prefs.fridayActiveConversationId
            override fun saveActiveConversationId(id: String?) {
                prefs.fridayActiveConversationId = id
            }
        }
}
