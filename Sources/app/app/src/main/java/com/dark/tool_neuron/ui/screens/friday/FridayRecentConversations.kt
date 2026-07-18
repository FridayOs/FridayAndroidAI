package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.viewmodel.RecentConvoUi
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * Sidebar "Recent conversations" list (FRI-574 phase 3, design spec §2). Pure renderer
 * over FridayRecentConversationsViewModel's already-windowed/joined UI state — this
 * composable owns no business logic beyond the scroll-near-bottom -> onLoadMore()
 * trigger. Row tap invokes onOpenConversation(id) with the real conversation id;
 * navigation itself is hoisted to the caller (Phase 4 wires the destination).
 */
@Composable
fun FridayRecentConversations(
    items: List<RecentConvoUi>,
    canLoadMore: Boolean,
    isLoading: Boolean,
    isEmpty: Boolean,
    onLoadMore: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (canLoadMore && !isLoading && total > 0 && lastVisible >= total - 2) {
                    onLoadMore()
                }
            }
    }

    Column(modifier = modifier) {
        DrawerSectionLabel(stringResource(R.string.friday_recent_convos))
        Spacer(Modifier.height(10.dp))
        if (isEmpty) {
            EmptyRecentConvos()
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(items, key = { it.id }) { convo ->
                    RecentConvoRow(convo = convo, onClick = { onOpenConversation(convo.id) })
                }
                if (isLoading) {
                    item { StatusRow(stringResource(R.string.friday_loading_more)) }
                } else if (!canLoadMore) {
                    item { StatusRow(stringResource(R.string.friday_all_loaded)) }
                }
            }
        }
    }
}

@Composable
private fun RecentConvoRow(convo: RecentConvoUi, onClick: () -> Unit) {
    val providerText = convo.providerLabel
        ?: convo.providerLabelRes?.let { stringResource(it) }
        ?: stringResource(R.string.friday_unknown_provider)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(
                if (convo.isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            TnIcons.MessageCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = convo.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${convo.relativeTime} · $providerText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun EmptyRecentConvos() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = stringResource(R.string.friday_no_convos),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.friday_start_new_chat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
