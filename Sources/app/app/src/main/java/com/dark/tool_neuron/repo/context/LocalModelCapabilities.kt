package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.repo.RepositoryDataStore

object LocalModelCapabilities {

    fun supportsLanguage(model: ModelInfo, lang: AppLanguage): Boolean? {
        if (lang == AppLanguage.SYSTEM) return null
        val repo = resolveRepo(model) ?: return null
        return lang.uiTag in repo.languages
    }

    private fun resolveRepo(model: ModelInfo): HFRepository? =
        RepositoryDataStore.DEFAULT_REPOSITORIES.firstOrNull { repo ->
            model.id == repo.id || model.id.startsWith("${repo.id}__")
        }
}
