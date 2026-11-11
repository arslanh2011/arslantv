package com.arslan.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arslan.tv.data.Channel
import com.arslan.tv.ui.components.BackChip
import com.arslan.tv.ui.components.ChannelCard
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

private val KEYS: List<String> =
    ('A'..'Z').map(Char::toString) + ('0'..'9').map(Char::toString)

/**
 * Search with a built-in key grid rather than the system IME: a D-pad can drive
 * this reliably on every box, while the leanback keyboard is not present on all
 * of the cheaper Android TV sticks.
 */
@Composable
fun SearchPanel(
    query: String,
    results: List<Channel>,
    onQueryChange: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onChannelFocused: (Channel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstKey = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Row(modifier = modifier.fillMaxSize().padding(start = 28.dp, top = 24.dp, end = 36.dp)) {
        Column(modifier = Modifier.width(312.dp).fillMaxHeight()) {
            BackChip(onBack = onBack)
            Spacer(Modifier.height(14.dp))
            QueryField(query)
            Spacer(Modifier.height(18.dp))
            KeyGrid(
                onKey = { onQueryChange(query + it) },
                onBackspace = { onQueryChange(query.dropLast(1)) },
                onClear = { onQueryChange("") },
                // Enter jumps the remote over to the results, which is the
                // only way to reach them without arrowing across the keys.
                onEnter = { runCatching { firstResult.requestFocus() } },
                onBack = onBack,
                firstKeyFocus = firstKey,
            )
        }

        Spacer(Modifier.width(28.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                text = when {
                    query.isBlank() -> "All channels"
                    results.isEmpty() -> "No channel matches \"$query\""
                    else -> "${results.size} match${if (results.size == 1) "" else "es"}"
                },
                style = AppType.RowHeader,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 200.dp),
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(results, key = { it.id }) { channel ->
                    ChannelCard(
                        channel = channel,
                        onClick = { onChannelClick(channel) },
                        onFocused = { onChannelFocused(channel) },
                        modifier = if (channel.id == results.firstOrNull()?.id) {
                            Modifier.focusRequester(firstResult)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueryField(query: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = query.ifBlank { "Search channels" },
            style = AppType.Title,
            color = if (query.isBlank()) AppColors.TextTertiary else AppColors.TextPrimary,
        )
    }
}

@Composable
private fun KeyGrid(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onEnter: () -> Unit,
    onBack: () -> Unit,
    firstKeyFocus: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KEYS.chunked(6).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIndex, key ->
                    KeyCap(
                        label = key,
                        onClick = { onKey(key) },
                        modifier = if (rowIndex == 0 && colIndex == 0) {
                            Modifier.focusRequester(firstKeyFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyCap(label = "SPACE", onClick = { onKey(" ") }, width = 110.dp)
            KeyCap(label = "DEL", onClick = onBackspace, width = 90.dp)
            KeyCap(label = "CLEAR", onClick = onClear, width = 104.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyCap(label = "↵  ENTER", onClick = onEnter, width = 160.dp)
            KeyCap(label = "◀  BACK", onClick = onBack, width = 148.dp)
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 44.dp,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(width)
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) AppColors.Accent else Color.White.copy(alpha = 0.08f))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) AppColors.FocusRing else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AppType.Label, color = AppColors.TextPrimary)
    }
}
