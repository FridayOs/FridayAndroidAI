package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.viewmodel.FridayRecentConversationsViewModel
import kotlinx.coroutines.launch

/*
 * FRI-574 phase 3 sidebar rebuild (design spec §2). AppScaffold.kt (phase 4-owned,
 * not editable here) is the sole caller today and still passes exactly the 5 legacy
 * named args below — kept unchanged, including names, so that call site keeps
 * compiling untouched by this phase. The two new spec callbacks
 * (onOpenConversation/onOpenSettings) are added as trailing DEFAULTED params so the
 * legacy 5-arg call still resolves; Phase 4 should pass real implementations for both,
 * and may rename onNavigateVoice/onNavigateChat -> onOpenVoice/onNewConversation at
 * the call site if it wants literal spec naming (semantics are unchanged: onNavigateChat
 * already means "go to the base/new chat route").
 */
@Composable
fun FridayDrawerContent(
    currentRoute: String?,
    onNavigateVoice: () -> Unit,
    onNavigateChat: () -> Unit,
    onNavigateHistory: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenConversation: (String) -> Unit = { onNavigateHistory() },
    onOpenSettings: () -> Unit = {},
    accountViewModel: AccountViewModel = hiltViewModel(),
    recentViewModel: FridayRecentConversationsViewModel = hiltViewModel(),
) {
    val account by accountViewModel.state.collectAsStateWithLifecycle()
    val windowed by recentViewModel.windowed.collectAsStateWithLifecycle()
    val canLoadMore by recentViewModel.canLoadMore.collectAsStateWithLifecycle()
    val isLoading by recentViewModel.isLoading.collectAsStateWithLifecycle()
    val isEmpty by recentViewModel.isEmpty.collectAsStateWithLifecycle()
    val providerHint by recentViewModel.providerHint.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoonMessage = stringResource(R.string.friday_coming_milestone)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .padding(top = 46.dp, bottom = 20.dp),
        ) {
            FridayDrawerHeader(account = account)
            Spacer(Modifier.height(26.dp))
            FridaySidebarFeatureSection(
                onOpenVoice = onNavigateVoice,
                onNewConversation = onNavigateChat,
                onComingSoon = {
                    scope.launch { snackbarHostState.showSnackbar(comingSoonMessage) }
                },
            )
            Spacer(Modifier.height(20.dp))
            FridayRecentConversations(
                items = windowed,
                canLoadMore = canLoadMore,
                isLoading = isLoading,
                isEmpty = isEmpty,
                onLoadMore = recentViewModel::loadMore,
                onOpenConversation = onOpenConversation,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(16.dp))
            FridaySidebarFooter(
                providerHint = providerHint,
                onOpenSettings = onOpenSettings,
                onSignOut = {
                    accountViewModel.signOut()
                    onSignedOut()
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

/*
 * Shared small-caps section label used by the feature section and recent-conversations
 * list. Named distinctly from FridayHistoryScreen's file-private SectionLabel to avoid
 * an overload-resolution clash: same-package private top-level declarations still enter
 * the candidate set for calls inside their own file, so an identical public signature
 * here would make FridayHistoryScreen.kt ambiguous (that file is out of this phase's
 * ownership and must not be touched).
 */
@Composable
fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
