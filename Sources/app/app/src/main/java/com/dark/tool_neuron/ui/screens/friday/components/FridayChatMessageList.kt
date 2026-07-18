package com.dark.tool_neuron.ui.screens.friday.components

import com.friday.ai.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.viewmodel.FridayChatMessage

// WHY: fdPop/fdFade (design-spec keyframes) have no Compose equivalent; approximate
// with per-item AnimatedVisibility (scaleIn+fadeIn for user, fadeIn for AI) played
// once per stable key so streaming deltas on the same message don't re-trigger it.
private const val BubbleMaxWidthFraction = 0.84f

@Composable
fun FridayChatMessageList(
    messages: List<FridayChatMessage>,
    thinking: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val itemCount = messages.size + if (thinking) 1 else 0
    val isNearBottom by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            layoutInfo.totalItemsCount == 0 || lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(itemCount) {
        if (itemCount > 0 && isNearBottom) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(messages, key = { it.id }) { message -> FridayChatMessageItem(message) }
        if (thinking) {
            item(key = "__friday_thinking__") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FridayThinkingDots()
                    Text(
                        text = stringResource(R.string.friday_thinking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FridayChatMessageItem(message: FridayChatMessage) {
    val visibleState = remember(message.id) { MutableTransitionState(false).apply { targetState = true } }
    if (message.isUser) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)),
            ) {
                FridayUserBubble(text = message.text, modifier = Modifier.fillMaxWidth(BubbleMaxWidthFraction))
            }
        }
    } else {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(250)),
            ) {
                FridayAiBubble(text = message.text, modifier = Modifier.fillMaxWidth(BubbleMaxWidthFraction))
            }
        }
    }
}
