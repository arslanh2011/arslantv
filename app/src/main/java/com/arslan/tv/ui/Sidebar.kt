package com.arslan.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arslan.tv.ui.components.NavGlyph
import com.arslan.tv.ui.components.NavIcon
import com.arslan.tv.ui.theme.AppColors
import com.arslan.tv.ui.theme.AppType

/** What the sidebar can point at. */
sealed interface NavTarget {
    data object Search : NavTarget
    data object Home : NavTarget
    data class Category(val name: String) : NavTarget
    data object Settings : NavTarget
}

data class NavEntry(val target: NavTarget, val label: String, val icon: NavIcon)

val CollapsedSidebarWidth = 76.dp
private val ExpandedSidebarWidth = 248.dp

fun iconForCategory(category: String): NavIcon = when (category.lowercase()) {
    "news" -> NavIcon.NEWS
    "sports" -> NavIcon.SPORTS
    "drama", "entertainment" -> NavIcon.DRAMA
    "music" -> NavIcon.MUSIC
    "film", "movies" -> NavIcon.FILM
    else -> NavIcon.STAR
}

/**
 * Collapsed to an icon rail until something inside it takes focus, then it
 * slides open over the content. Content does not reflow, so opening the menu
 * never shifts the rows underneath.
 */
@Composable
fun Sidebar(
    entries: List<NavEntry>,
    selected: NavTarget,
    onSelect: (NavTarget) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocus: FocusRequester? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val width by animateDpAsState(
        targetValue = if (expanded) ExpandedSidebarWidth else CollapsedSidebarWidth,
        animationSpec = tween(200),
        label = "sidebarWidth",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(
                Brush.horizontalGradient(
                    if (expanded) {
                        listOf(AppColors.Background, AppColors.Background.copy(alpha = 0.97f))
                    } else {
                        listOf(AppColors.Background, Color.Transparent)
                    }
                )
            )
            .focusGroup()
            .onFocusChanged { expanded = it.hasFocus }
            // More entries than fit on a short panel must still be reachable.
            .verticalScroll(scroll)
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BrandMark(expanded)
        Spacer(Modifier.height(12.dp))
        entries.forEachIndexed { index, entry ->
            SidebarItem(
                entry = entry,
                expanded = expanded,
                isSelected = entry.target == selected,
                onClick = { onSelect(entry.target) },
                modifier = if (index == 0 && firstItemFocus != null) {
                    Modifier.focusRequester(firstItemFocus)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
private fun BrandMark(expanded: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", style = AppType.Title, color = AppColors.TextPrimary)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row(Modifier.padding(start = 12.dp)) {
                Text("ARSLAN", style = AppType.RowHeader, color = AppColors.TextPrimary)
                Text("TV", style = AppType.RowHeader, color = AppColors.Accent)
            }
        }
    }
}

@Composable
private fun SidebarItem(
    entry: NavEntry,
    expanded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    val background = when {
        focused -> AppColors.Accent
        isSelected -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val tint = when {
        focused -> AppColors.TextPrimary
        isSelected -> AppColors.TextPrimary
        else -> AppColors.TextSecondary
    }

    Row(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavGlyph(icon = entry.icon, tint = tint, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(
                text = entry.label,
                style = AppType.Label,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}
