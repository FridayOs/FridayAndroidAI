package com.dark.tool_neuron.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.NavScreens
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.screens.credits.CreditsScreen
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesScreen
import com.dark.tool_neuron.ui.screens.downloads.DownloadsScreen
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreen
import com.dark.tool_neuron.ui.screens.image_task.ImageTaskScreen
import com.dark.tool_neuron.ui.screens.intro_screen.IntroScreen
import com.dark.tool_neuron.ui.screens.model_config.ModelConfigScreen
import com.dark.tool_neuron.ui.screens.model_manager.ModelManagerScreen
import com.dark.tool_neuron.ui.screens.rag_debug.RagDebugScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsSectionScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsThemingScreen
import com.dark.tool_neuron.ui.screens.storage.StorageScreen
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreen
import com.dark.tool_neuron.ui.screens.guide.AppGuideScreen
import com.dark.tool_neuron.ui.screens.guide.GuideChatScreen
import com.dark.tool_neuron.ui.screens.guide.GuideEntryKeys
import com.dark.tool_neuron.ui.screens.guide.GuideModelsScreen
import com.dark.tool_neuron.ui.screens.guide.GuideRagScreen
import com.dark.tool_neuron.ui.screens.guide.GuideSecurityScreen
import com.dark.tool_neuron.ui.screens.guide.GuideServerScreen
import com.dark.tool_neuron.ui.screens.guide.GuideThemesScreen
import com.dark.tool_neuron.ui.screens.guide.GuideVlmScreen
import com.dark.tool_neuron.ui.screens.guide.GuidePluginsScreen
import com.dark.tool_neuron.ui.screens.guide.GuideImagesScreen
import com.dark.tool_neuron.ui.screens.guide.GuideVoiceScreen
import com.dark.tool_neuron.ui.screens.model_store.ModelStoreScreen
import com.dark.tool_neuron.ui.screens.plugin_hub.PluginHubScreen
import com.dark.tool_neuron.ui.screens.plugin_install.PluginInstallScreen
import com.dark.tool_neuron.ui.screens.hf_explorer.HfExplorerScreen
import com.dark.tool_neuron.ui.screens.hf_explorer.HfRepoDetailScreen
import com.dark.tool_neuron.ui.screens.server.ServerScreen
import java.net.URLDecoder
import com.dark.tool_neuron.ui.screens.setup_screen.ModelSetupScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupPasswordScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupRagScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupThemeScreen
import com.dark.tool_neuron.ui.screens.friday.FridaySplashScreen
import com.dark.tool_neuron.ui.screens.friday.FridayLoginScreen
import com.dark.tool_neuron.ui.screens.friday.FridayVoiceScreen
import com.dark.tool_neuron.ui.screens.friday.FridayChatScreen
import com.dark.tool_neuron.ui.screens.friday.FridayHistoryScreen
import com.dark.tool_neuron.ui.screens.friday.FridaySettingsScreen
import com.dark.tool_neuron.ui.screens.language_selection.LanguageSelectionScreen
import com.dark.tool_neuron.ui.screens.onboarding_tour.FeatureTourScreen
import com.dark.tool_neuron.ui.screens.onboarding_providers.OnboardingProvidersScreen
import com.dark.tool_neuron.ui.screens.onboarding_providers.add.OnboardingAddProviderScreen
import com.dark.tool_neuron.viewmodel.OnboardingAddProviderViewModel
import com.dark.tool_neuron.viewmodel.OnboardingProvidersViewModel
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.ui.screens.terms_conditions.TermsConditionsScreen
import com.dark.tool_neuron.ui.theme.rememberNavTransitions
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.DownloadPackOutcome
import com.dark.tool_neuron.viewmodel.ImageTaskViewModel
import com.dark.tool_neuron.viewmodel.LanguageViewModel
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.PasswordViewModel
import com.dark.tool_neuron.viewmodel.RagDebugViewModel
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import com.dark.tool_neuron.viewmodel.ThemingViewModel
import com.dark.tool_neuron.viewmodel.SetupViewModel
import com.dark.tool_neuron.viewmodel.StorageViewModel
import com.dark.tool_neuron.viewmodel.OnboardingBackNav

@Composable
fun TNavigation(
    navController: NavHostController,
    innerPadding: PaddingValues,
    startDestination: String,
    nextDestination: String,
    actionWindowExpanded: Boolean,
    onActionWindowDismiss: () -> Unit,
    onUnlocked: () -> Unit = {},
    onSetupComplete: () -> Unit = {},
    onModelSetupComplete: () -> Unit = {},
    onChooseGateway: () -> Unit = {},
    onFeatureTourComplete: () -> Unit = {},
    onContinueToFriday: () -> Unit = {},
    onAddProvider: (String) -> Unit = { catalogId ->
        navController.navigate(NavScreens.OnboardingAddProvider.routeFor(catalogId))
    },
    resolveNext: () -> String = { nextDestination },
) {
    val transitions = rememberNavTransitions()

    // Onboarding back-chevron nav (FRI-582 QA blocker fix): screen-swap only,
    // never unsets HXS done-flags. See OnboardingBackNav for the step order.
    fun onboardingBack(currentRoute: String): () -> Unit = {
        OnboardingBackNav.previousRoute(currentRoute)?.let { prev ->
            navController.navigate(prev) {
                popUpTo(currentRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        composable(
            route = NavScreens.IntroScreen.route,
            exitTransition = { fadeOut(tween(durationMillis = 800)) },
        ) {
            IntroScreen(
                innerPadding = innerPadding,
                onFinish = {
                    navController.navigate(resolveNext()) {
                        popUpTo(NavScreens.IntroScreen.route) { inclusive = true }
                    }
                },
            )
        }
        composable(NavScreens.HomeScreen.route) {
            val activity = LocalContext.current as ComponentActivity
            val homeViewModel: HomeViewModel = hiltViewModel(activity)
            HomeScreen(
                innerPadding = innerPadding,
                actionWindowExpanded = actionWindowExpanded,
                onActionWindowDismiss = onActionWindowDismiss,
                onOpenModelManager = { navController.navigate(NavScreens.ModelManager.route) },
                viewModel = homeViewModel,
            )
        }
        composable(NavScreens.TermsConditions.route) {
            TermsConditionsScreen(
                innerPadding = innerPadding,
                onAccept = {},
                onBack = onboardingBack(NavScreens.TermsConditions.route),
            )
        }
        composable(NavScreens.DevNotes.route) {
            DevNotesScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.Credits.route) {
            CreditsScreen(
                innerPadding = innerPadding,
                onExit = { navController.popBackStack() },
            )
        }
        composable(NavScreens.SetupScreen.route) {
            val viewModel: SetupViewModel = hiltViewModel()
            val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
            val password by viewModel.password.collectAsStateWithLifecycle()
            val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
            val isConfirmStep by viewModel.isConfirmStep.collectAsStateWithLifecycle()
            val errorRes by viewModel.errorRes.collectAsStateWithLifecycle()
            val errorArg by viewModel.errorArg.collectAsStateWithLifecycle()
            var committed by remember { mutableStateOf(false) }

            if (committed && selectedMode == "app_password") {
                SetupPasswordScreen(
                    innerPadding = innerPadding,
                    password = if (isConfirmStep) confirmPassword else password,
                    isConfirmStep = isConfirmStep,
                    errorRes = errorRes,
                    errorArg = errorArg,
                    onDigit = viewModel::appendDigit,
                    onDelete = viewModel::deleteLast,
                    onClear = viewModel::clearAll,
                    onSubmit = {
                        viewModel.submitPassword(onSuccess = onSetupComplete)
                    },
                    onBack = {
                        if (isConfirmStep) {
                            viewModel.goBack()
                        } else {
                            committed = false
                        }
                    },
                )
            } else {
                SetupScreen(
                    innerPadding = innerPadding,
                    selectedMode = selectedMode,
                    onModeSelected = viewModel::selectMode,
                    onContinue = {
                        when (selectedMode) {
                            "none" -> {
                                viewModel.completeWithNoLock()
                                onSetupComplete()
                            }
                            "app_password" -> committed = true
                        }
                    },
                    onBack = onboardingBack(NavScreens.SetupScreen.route),
                )
            }
        }
        composable(NavScreens.SetupTheme.route) {
            SetupThemeScreen(
                innerPadding = innerPadding,
                onBack = onboardingBack(NavScreens.SetupTheme.route),
            )
        }
        composable(NavScreens.SetupRag.route) {
            SetupRagScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.ModelStore.route) {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            ModelStoreScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHfExplorer = { navController.navigate(NavScreens.HfExplorer.route) },
                onNavigateToDownloads = { navController.navigate(NavScreens.Downloads.route) },
            )
        }
        composable(NavScreens.Downloads.route) {
            DownloadsScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.ModelSetup.route) {
            val activity = LocalContext.current as ComponentActivity
            val storeVm: ModelStoreViewModel = hiltViewModel(activity)

            ModelSetupScreen(
                innerPadding = innerPadding,
                // Fail-closed (FRI-582 QA round-2 B1): only persist the local
                // path + complete onboarding when EVERY pack entry resolved
                // and downloads were actually enqueued (Success). Unknown
                // pack ids or unresolved entries return false and the screen
                // shows an inline error instead of advancing.
                onLocalPackConfirmed = { packId ->
                    when (storeVm.downloadPack(packId)) {
                        is DownloadPackOutcome.Success -> {
                            storeVm.recordModelPathChoice(path = "local", packId = packId)
                            onModelSetupComplete()
                            true
                        }
                        else -> false
                    }
                },
                onOpenStore = { navController.navigate(NavScreens.ModelStore.route) },
                onLocalImport = { uri, name, size, type ->
                    storeVm.importLocalModel(uri, name, size, type)
                    storeVm.recordModelPathChoice(path = "local")
                    onModelSetupComplete()
                },
                onSkip = {
                    storeVm.recordModelPathChoice(path = "skip")
                    onModelSetupComplete()
                },
                onBack = onboardingBack(NavScreens.ModelSetup.route),
                onChooseGateway = {
                    storeVm.recordModelPathChoice(path = "gateway")
                    onChooseGateway()
                },
            )
        }
        composable(NavScreens.AppGuide.route) {
            AppGuideScreen(
                innerPadding = innerPadding,
                onOpenEntry = { key ->
                    val route = when (key) {
                        GuideEntryKeys.CHAT -> NavScreens.GuideChat.route
                        GuideEntryKeys.MODELS -> NavScreens.GuideModels.route
                        GuideEntryKeys.VOICE -> NavScreens.GuideVoice.route
                        GuideEntryKeys.RAG -> NavScreens.GuideRag.route
                        GuideEntryKeys.VLM -> NavScreens.GuideVlm.route
                        GuideEntryKeys.SECURITY -> NavScreens.GuideSecurity.route
                        GuideEntryKeys.THEMES -> NavScreens.GuideThemes.route
                        GuideEntryKeys.SERVER -> NavScreens.GuideServer.route
                        GuideEntryKeys.PLUGINS -> NavScreens.GuidePlugins.route
                        GuideEntryKeys.IMAGES -> NavScreens.GuideImages.route
                        else -> null
                    }
                    route?.let { navController.navigate(it) }
                },
            )
        }
        composable(NavScreens.GuideChat.route) { GuideChatScreen(innerPadding) }
        composable(NavScreens.GuideModels.route) { GuideModelsScreen(innerPadding) }
        composable(NavScreens.GuideRag.route) { GuideRagScreen(innerPadding) }
        composable(NavScreens.GuideVlm.route) { GuideVlmScreen(innerPadding) }
        composable(NavScreens.GuideVoice.route) { GuideVoiceScreen(innerPadding) }
        composable(NavScreens.GuideSecurity.route) { GuideSecurityScreen(innerPadding) }
        composable(NavScreens.GuideThemes.route) { GuideThemesScreen(innerPadding) }
        composable(NavScreens.GuideServer.route) { GuideServerScreen(innerPadding) }
        composable(NavScreens.GuidePlugins.route) { GuidePluginsScreen(innerPadding) }
        composable(NavScreens.GuideImages.route) { GuideImagesScreen(innerPadding) }
        composable(NavScreens.PluginHub.route) {
            PluginHubScreen(
                onClose = { navController.popBackStack() }
            )
        }
        composable(NavScreens.Settings.route) {
            SettingsScreen(
                innerPadding = innerPadding,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsChatRag.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_CHAT_RAG,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsVoice.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_VOICE,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsTheming.route) {
            val viewModel: ThemingViewModel = hiltViewModel()
            SettingsThemingScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
            )
        }
        composable(NavScreens.SettingsVision.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_VISION,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsPerformance.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PERFORMANCE,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsModel.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_MODEL,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsPlugins.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PLUGINS,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsPrivacy.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PRIVACY,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsDiagnostics.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_DIAGNOSTICS,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.SettingsAbout.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_ABOUT,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavScreens.RagDebug.route) {
            val viewModel: RagDebugViewModel = hiltViewModel()
            RagDebugScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavScreens.ImageTask.route) {
            val viewModel: ImageTaskViewModel = hiltViewModel()
            ImageTaskScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onOpenStore = { navController.navigate(NavScreens.ModelStore.route) },
            )
        }
        composable(NavScreens.Storage.route) {
            val viewModel: StorageViewModel = hiltViewModel()
            StorageScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateToModelManager = {
                    navController.navigate(NavScreens.ModelManager.route)
                },
                onNavigateToStore = {
                    navController.navigate(NavScreens.ModelStore.route)
                },
            )
        }
        composable(NavScreens.ModelManager.route) {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            ModelManagerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditModel = { modelId ->
                    navController.navigate(NavScreens.ModelConfig.routeFor(modelId))
                },
            )
        }
        composable(
            route = NavScreens.ModelConfig.route,
            arguments = listOf(navArgument(NavScreens.ModelConfig.ARG_MODEL_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            val installed by viewModel.installedModels.collectAsStateWithLifecycle()
            val modelId = backStackEntry.arguments?.getString(NavScreens.ModelConfig.ARG_MODEL_ID)
            val model = installed.firstOrNull { it.id == modelId }
            if (model == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                var initialConfig by remember(model.id) {
                    mutableStateOf<ModelConfig?>(null)
                }
                var loaded by remember(model.id) { mutableStateOf(false) }
                LaunchedEffect(model.id) {
                    initialConfig = viewModel.getModelConfig(model.id)
                    loaded = true
                }
                if (loaded) {
                    ModelConfigScreen(
                        modelInfo = model,
                        initialConfig = initialConfig,
                        onSave = { config ->
                            viewModel.saveModelConfig(config)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        composable(NavScreens.ServerScreen.route) {
            ServerScreen(innerPadding)
        }
        composable(NavScreens.PluginInstall.route) {
            PluginInstallScreen(innerPadding = innerPadding)
        }
        composable(NavScreens.HfExplorer.route) {
            HfExplorerScreen(innerPadding = innerPadding, navController = navController)
        }
        composable(NavScreens.HfRepoDetail.route) { backStack ->
            val raw = backStack.arguments?.getString(NavScreens.HfRepoDetail.ARG_REPO_PATH).orEmpty()
            val decoded = URLDecoder.decode(raw, "UTF-8")
            HfRepoDetailScreen(innerPadding = innerPadding, repoPath = decoded)
        }
        composable(NavScreens.PasswordScreen.route) {
            val viewModel: PasswordViewModel = hiltViewModel()
            val password by viewModel.password.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
            val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
            val lockedUntilMs by viewModel.lockedUntilMs.collectAsStateWithLifecycle()
            val wiped by viewModel.wiped.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { viewModel.reset() }
            LaunchedEffect(unlocked) {
                if (unlocked) onUnlocked()
            }

            PasswordScreen(
                innerPadding = innerPadding,
                password = password,
                error = error,
                isVerifying = isVerifying,
                lockedUntilMs = lockedUntilMs,
                wiped = wiped,
                onDigit = viewModel::appendDigit,
                onDelete = viewModel::deleteLast,
                onClear = viewModel::clearAll,
                onSubmit = viewModel::submit,
            )
        }
        composable(NavScreens.FridaySplash.route) {
            FridaySplashScreen(
                innerPadding = innerPadding,
                onResolved = {
                    navController.navigate(NavScreens.FridayLogin.route) {
                        popUpTo(NavScreens.FridaySplash.route) { inclusive = true }
                    }
                },
            )
        }
        composable(NavScreens.FridayLogin.route) {
            val accountViewModel: AccountViewModel = hiltViewModel()
            val accountState by accountViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(accountState) {
                if (accountState is AccountState.Authenticated) {
                    // Resolve fresh so a no-language/no-onboarding install routes through the gate chain, not straight to FridayVoice.
                    navController.navigate(resolveNext()) {
                        popUpTo(NavScreens.FridayLogin.route) { inclusive = true }
                    }
                }
            }
            FridayLoginScreen(
                innerPadding = innerPadding,
                accountViewModel = accountViewModel,
            )
        }
        composable(NavScreens.FridayVoice.route) {
            FridayVoiceScreen(
                innerPadding = innerPadding,
                onOpenMenu = { navController.navigate(NavScreens.FridayHistory.route) },
                onToChat = { navController.navigate(NavScreens.FridayChat.BASE) },
                // FRI-582 QA round-2 B3: "set up later" CTA opens the real
                // provider selector, not History.
                onOpenProviderSelector = { navController.navigate(NavScreens.OnboardingProviders.route) },
            )
        }
        composable(
            route = NavScreens.FridayChat.route,
            arguments = listOf(navArgument(NavScreens.FridayChat.ARG_CID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { backStack ->
            val cid = backStack.arguments?.getString(NavScreens.FridayChat.ARG_CID)
            FridayChatScreen(
                innerPadding = innerPadding,
                conversationId = cid,
                onOpenMenu = { navController.navigate(NavScreens.FridayHistory.route) },
                onToVoice = { navController.navigate(NavScreens.FridayVoice.route) },
                // FRI-582 QA round-2 B3: no-provider CTA opens the real
                // provider selector, not History.
                onOpenProviderSelector = { navController.navigate(NavScreens.OnboardingProviders.route) },
            )
        }
        composable(NavScreens.FridayHistory.route) {
            FridayHistoryScreen(
                innerPadding = innerPadding,
                onBack = { navController.popBackStack() },
                onOpenConversation = { conversationId ->
                    navController.navigate(NavScreens.FridayChat.routeFor(conversationId))
                },
                onOpenSettings = {
                    navController.navigate(NavScreens.FridaySettings.route)
                },
            )
        }
        composable(NavScreens.FridaySettings.route) {
            FridaySettingsScreen(
                innerPadding = innerPadding,
                onBack = { navController.popBackStack() },
                onLanguageClick = { navController.navigate(NavScreens.LanguageSelection.route) },
            )
        }
        composable(NavScreens.LanguageSelection.route) {
            val viewModel: LanguageViewModel = hiltViewModel()
            LanguageSelectionScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onContinue = { navController.navigate(resolveNext()) {
                    popUpTo(NavScreens.LanguageSelection.route) { inclusive = true }
                } },
            )
        }
        composable(NavScreens.FeatureTour.route) {
            FeatureTourScreen(
                innerPadding = innerPadding,
                onContinue = onFeatureTourComplete,
                onBack = onboardingBack(NavScreens.FeatureTour.route),
            )
        }
        composable(NavScreens.OnboardingProviders.route) {
            val viewModel: OnboardingProvidersViewModel = hiltViewModel()
            OnboardingProvidersScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAddProvider = onAddProvider,
                onContinueToFriday = onContinueToFriday,
            )
        }
        composable(
            route = NavScreens.OnboardingAddProvider.route,
            arguments = listOf(navArgument(NavScreens.OnboardingAddProvider.ARG_CATALOG_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) {
            val viewModel: OnboardingAddProviderViewModel = hiltViewModel()
            OnboardingAddProviderScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
