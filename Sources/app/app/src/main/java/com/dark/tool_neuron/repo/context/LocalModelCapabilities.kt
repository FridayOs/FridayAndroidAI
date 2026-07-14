package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.repo.RepositoryDataStore

// Declarative Vietnamese/language-capability lookup (FRI-557 phase 5).
// Pure query over the static catalog - no I/O, no DI - so callers
// (ContextEngine.localeSignal, future FRI-583 UI) get a synchronous,
// unit-testable signal. Language marks are config, not a download/runtime
// enforcement mechanism (selection UI is FRI-583/store scope).
//
// Join key: ModelInfo carries no direct field back to its HFRepository
// catalog entry (verified - ModelInfo fields: id, name, path, pathType,
// providerType, fileSize, isActive). Every installed model's id is built in
// ModelCatalog.kt as "${HFRepository.id}__<filename>" (ModelCatalog.kt:247)
// and carried unchanged into ModelInfo.id at install time
// (ModelStoreViewModel.kt insert calls use `id = model.id`). So the repo id
// is recoverable as the ModelInfo.id prefix up to "__" - the same
// id-prefix heuristic already used for catalog matching in
// ModelStoreViewModel.enqueueChatModel (line 366: `it.id.startsWith(modelId)`).
// Built-in models (e.g. "built-in-tts") have no "__" segment and simply
// never match a catalog entry, resolving to null below.
object LocalModelCapabilities {

    // Returns null when the model can't be confidently matched to a catalog
    // entry, or when asked about AppLanguage.SYSTEM (no concrete language to
    // check). Never fakes a "yes" for an unknown model - mirrors
    // GatewayRoleRouter's "never fake" Unavailable semantics.
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
