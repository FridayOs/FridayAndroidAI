package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.util.LocalIsExpandedLayout
import com.dark.tool_neuron.ui.util.ProvideWindowMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.repo.ActiveConversationStore
import com.dark.tool_neuron.ui.components.RootWarningDialog
import com.dark.tool_neuron.ui.navigation.ActiveConversationStoreEntryPoint
import com.dark.tool_neuron.ui.navigation.TNavigation
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.ui.screens.friday.FridayDrawerContent
import com.dark.tool_neuron.ui.screens.friday.FridayProviderSelectorSheet
import com.dark.tool_neuron.ui.screens.home_screen.ChatDrawerContent
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.viewmodel.FridayProviderSelectorViewModel
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.ScaffoldViewModel
import com.friday.ai.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@Composable
fun AppScaffold() {
    ProvideWindowMetrics {
        AppScaffoldInner()
    }
}

@Composable
private fun AppScaffoldInner() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val scaffoldViewModel: ScaffoldViewModel = hiltViewModel()
    val actionWindowExpanded by homeViewModel.actionWindowExpanded.collectAsStateWithLifecycle()
    val pillState by homeViewModel.pillState.collectAsStateWithLifecycle()
    val chats by homeViewModel.chats.collectAsStateWithLifecycle()
    val currentChatId by homeViewModel.currentChatId.collectAsStateWithLifecycle()

    val accountViewModel: AccountViewModel = hiltViewModel()
    val accountState by accountViewModel.state.collectAsStateWithLifecycle()

    // FRI-574 phase 7 (B2): the sidebar's "New conversation" action needs to issue a real
    // reset (clears the active id + bumps the newChatRequests counter). Reuse the same
    // Hilt EntryPoint that TNavigation already exposes for the same singleton, so we stay
    // outside ViewModel construction while still accessing SingletonComponent bindings.
    val appContext = LocalContext.current.applicationContext
    val activeConversationStore = remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            ActiveConversationStoreEntryPoint::class.java,
        ).activeConversationStore()
    }

    val initialDestination = remember { scaffoldViewModel.resolveStartDestination() }
    // Resolve fresh at each gate so mid-session state changes route correctly.
    val resolveNext: () -> String = { scaffoldViewModel.resolveStartDestination() }
    val shouldLock by scaffoldViewModel.shouldLock.collectAsStateWithLifecycle()
    val rootWarning by scaffoldViewModel.rootWarning.collectAsStateWithLifecycle()
    val serverRunning by scaffoldViewModel.serverRunning.collectAsStateWithLifecycle()
    val downloadProgress by scaffoldViewModel.downloadProgress.collectAsStateWithLifecycle()

    // Friday screens carry their own internal top/bottom bars per design.
    val isFridayRoute = currentRoute?.startsWith("friday_") == true
    val isFridayDrawerRoute = currentRoute == NavScreens.FridayVoice.route ||
        currentRoute == NavScreens.FridayChat.route ||
        currentRoute == NavScreens.FridayHistory.route

    // Unauthenticated traffic on any Friday surface bounces to Login.
    LaunchedEffect(accountState, currentRoute) {
        if (accountState is AccountState.Unauthenticated && isFridayRoute &&
            currentRoute != NavScreens.FridayLogin.route &&
            currentRoute != NavScreens.FridaySplash.route
        ) {
            navController.navigate(NavScreens.FridayLogin.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LaunchedEffect(serverRunning, currentRoute) {
        if (serverRunning && currentRoute != null && currentRoute != NavScreens.ServerScreen.route) {
            navController.navigate(NavScreens.ServerScreen.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    rootWarning?.let { warning ->
        RootWarningDialog(
            rootEvidence = warning.rootEvidence,
            tamperEvidence = warning.tamperEvidence,
            a11yPackages = warning.a11yPackages,
            onAcknowledge = scaffoldViewModel::acknowledgeRootWarning,
        )
    }

    LaunchedEffect(shouldLock, currentRoute) {
        if (shouldLock && currentRoute != null &&
            currentRoute != NavScreens.PasswordScreen.route &&
            currentRoute != NavScreens.SetupScreen.route &&
            currentRoute != NavScreens.IntroScreen.route &&
            currentRoute != NavScreens.LanguageSelection.route &&
            currentRoute != NavScreens.FeatureTour.route
        ) {
            navController.navigate(NavScreens.PasswordScreen.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val isFullscreen = currentRoute == NavScreens.IntroScreen.route
            || currentRoute == NavScreens.PasswordScreen.route
            || currentRoute == NavScreens.Credits.route
            || currentRoute == NavScreens.LanguageSelection.route
            || currentRoute == NavScreens.FeatureTour.route
            || isFridayRoute

    val showDrawer = (currentRoute == NavScreens.HomeScreen.route && !serverRunning) ||
        isFridayDrawerRoute
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isExpanded = LocalIsExpandedLayout.current

    // FRI-574 Phase 4: provider/model selector overlay, hosted at shell level so
    // both Voice and Chat can request it via the same TNavigation `openSelector`
    // callback without duplicating the sheet per-screen.
    val context = LocalContext.current
    val selectorViewModel: FridayProviderSelectorViewModel = hiltViewModel()
    var selectorOpen by remember { mutableStateOf(false) }
    val selectorGateways by selectorViewModel.gateways.collectAsStateWithLifecycle()
    val selectorBrainId by selectorViewModel.brainId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(selectorViewModel) {
        selectorViewModel.providerSwitched.collect { name ->
            snackbarHostState.showSnackbar(context.getString(R.string.friday_provider_switched_toast, name))
        }
    }

    val fridayDrawerBody: @Composable () -> Unit = {
        FridayDrawerContent(
            currentRoute = currentRoute,
            onNavigateVoice = {
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.FridayVoice.route)
            },
            onNavigateChat = {
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.FridayChat.BASE)
            },
            onNavigateHistory = {
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.FridayHistory.route)
            },
            onSignedOut = {
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.FridayLogin.route) {
                    popUpTo(0) { inclusive = true }
                }
            },
            onOpenConversation = { id ->
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.FridayChat.routeFor(id))
            },
            onOpenSettings = {
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.FridaySettings.route)
            },
            // FRI-574 phase 7 (B2): sidebar's "New conversation" CTA runs the real reset
            // command (clears active id + bumps newChatRequests so FridayChatVM.newChat
            // runs) before navigating to the base Chat route with launchSingleTop. Without
            // this it was a navigation side-effect that only worked when the VM happened to
            // observe a fresh conversation list.
            onNewConversation = {
                scope.launch { drawerState.close() }
                activeConversationStore.requestNewChat()
                navController.navigate(NavScreens.FridayChat.BASE) {
                    launchSingleTop = true
                }
            },
        )
    }

    val chatDrawerBody: @Composable () -> Unit = {
        ChatDrawerContent(
                    chats = chats,
                    currentChatId = currentChatId,
                    onChatSelected = { id ->
                        homeViewModel.selectChat(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        homeViewModel.createNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteChat = homeViewModel::deleteChat,
                    onPinChat = homeViewModel::pinChat,
                    onExportChat = homeViewModel::exportChat,
                    onNavigateToStore = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.ModelStore.route)
                    },
                    onNavigateToGuide = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.AppGuide.route)
                    },
                    onNavigateToDevNotes = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.DevNotes.route)
                    },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.Settings.route)
                    },
                    onNavigateToServer = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.ServerScreen.route)
                    },
                    onNavigateToCredits = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.Credits.route)
                    },
                    onNavigateToImageTask = {
                        scope.launch { drawerState.close() }
                        navController.navigate(NavScreens.ImageTask.route)
                    },
            onNavigateToPlugins = {
                scope.launch { drawerState.close() }
                navController.navigate(NavScreens.PluginInstall.route)
            },
        )
    }

    val drawerBody: @Composable () -> Unit = {
        if (isFridayDrawerRoute) fridayDrawerBody() else chatDrawerBody()
    }

    val mainScaffold: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isFullscreen) AppTopBar(
                    currentRoute = currentRoute,
                    pillState = pillState,
                    actionWindowExpanded = actionWindowExpanded,
                    downloadProgress = downloadProgress,
                    onActionWindowToggle = homeViewModel::toggleActionWindow,
                    onMenuClick = {
                        if (!isExpanded) scope.launch { drawerState.open() }
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateToStore = { navController.navigate(NavScreens.ModelStore.route) },
                )
            },
            bottomBar = {
                if (!isFullscreen) AppBottomBar(
                    currentRoute = currentRoute,
                    navController = navController,
                    onOnboardingComplete = { scaffoldViewModel.markOnboardingComplete() },
                    onThemeSetupComplete = {
                        scaffoldViewModel.markThemeSetupDone()
                        navController.navigate(NavScreens.ModelSetup.route) {
                            popUpTo(NavScreens.SetupTheme.route) { inclusive = true }
                        }
                    },
                    onTermsAccepted = {
                        val cameFromOnboarding = navController.previousBackStackEntry == null
                        scaffoldViewModel.markTermsAccepted()
                        if (cameFromOnboarding) {
                            navController.navigate(NavScreens.FeatureTour.route) {
                                popUpTo(NavScreens.TermsConditions.route) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            },
        ) { innerPadding ->
            TNavigation(
                navController = navController,
                innerPadding = innerPadding,
                startDestination = NavScreens.IntroScreen.route,
                nextDestination = initialDestination,
                actionWindowExpanded = actionWindowExpanded,
                onActionWindowDismiss = homeViewModel::collapseActionWindow,
                onUnlocked = {
                    navController.navigate(NavScreens.HomeScreen.route) {
                        popUpTo(NavScreens.PasswordScreen.route) { inclusive = true }
                    }
                },
                onSetupComplete = {
                    navController.navigate(NavScreens.SetupTheme.route) {
                        popUpTo(NavScreens.SetupScreen.route) { inclusive = true }
                    }
                },
                onModelSetupComplete = {
                    // SetupRag is optional, not mandatory first-run; land on Friday voice.
                    scaffoldViewModel.markModelSetupDone()
                    navController.navigate(NavScreens.FridayVoice.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onChooseGateway = {
                    navController.navigate(NavScreens.OnboardingProviders.route)
                },
                onFeatureTourComplete = {
                    scaffoldViewModel.markOnboardingComplete()
                    navController.navigate(NavScreens.SetupScreen.route) {
                        popUpTo(NavScreens.FeatureTour.route) { inclusive = true }
                    }
                },
                onContinueToFriday = {
                    scaffoldViewModel.markProviderStepDone()
                    scaffoldViewModel.markModelSetupDone()
                    navController.navigate(NavScreens.FridayVoice.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onAddProvider = { catalogId ->
                    navController.navigate(NavScreens.OnboardingAddProvider.routeFor(catalogId))
                },
                resolveNext = resolveNext,
                // FRI-574 Phase 4: Friday screens no longer navigate to History for
                // the hamburger/pill — they request the real sidebar drawer and the
                // provider selector overlay hosted here at the shell level.
                openDrawer = { scope.launch { drawerState.open() } },
                openSelector = { selectorOpen = true },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isExpanded && showDrawer && !isFullscreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(320.dp)) { drawerBody() }
                },
                modifier = Modifier.fillMaxSize(),
            ) { mainScaffold() }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = showDrawer,
                drawerContent = { ModalDrawerSheet { drawerBody() } },
            ) { mainScaffold() }
        }

        // FRI-574 Phase 4: Friday routes only — sheet is always mounted while on a
        // Friday screen so AnimatedVisibility can play its enter/exit transitions;
        // it renders nothing when selectorOpen is false.
        if (isFridayRoute) {
            FridayProviderSelectorSheet(
                visible = selectorOpen,
                providers = selectorGateways,
                activeId = selectorBrainId,
                onSelect = { id -> selectorViewModel.select(id); selectorOpen = false },
                // No distinct "manage existing" screen exists in this codebase;
                // OnboardingProviders is the one catalog/configured-provider list
                // screen and already exposes its own onAddProvider(catalogId) to
                // OnboardingAddProvider — both quick actions land there.
                onAddProvider = {
                    selectorOpen = false
                    navController.navigate(NavScreens.OnboardingProviders.route)
                },
                onManageProviders = {
                    selectorOpen = false
                    navController.navigate(NavScreens.OnboardingProviders.route)
                },
                onDismiss = { selectorOpen = false },
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
