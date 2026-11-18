package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.VybalDestination
import com.example.ui.theme.VybalBrushes
import com.example.ui.theme.VybalObsidian
import com.example.ui.theme.VybalOutlineSubtle
import com.example.ui.theme.VybalSurfaceLow
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer

@Composable
fun VybalBottomNavBar(
    currentRoute: String?,
    onNavigate: (VybalDestination) -> Unit,
    unreadInboxCount: Int = 0
) {
    Surface(
        color = VybalSurfaceLow.copy(alpha = 0.98f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VybalOutlineSubtle)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VybalDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val isCreate = destination == VybalDestination.Create
                    val showBadge = destination == VybalDestination.Inbox && unreadInboxCount > 0
                    val label = stringResource(destination.labelRes)

                    val iconColor by animateColorAsState(
                        targetValue = when {
                            isCreate -> MaterialTheme.colorScheme.onPrimary
                            selected -> MaterialTheme.colorScheme.tertiary
                            else -> VybalTextMuted
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_icon_color"
                    )

                    val labelColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onBackground else VybalTextMuted,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_label_color"
                    )

                    if (isCreate) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onNavigate(destination) }
                                .testTag(destination.testTag)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 34.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VybalBrushes.primaryAccent)
                                    .border(
                                        width = if (selected) 1.5.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = label,
                                    tint = iconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onNavigate(destination) }
                                .testTag(destination.testTag)
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) VybalVioletContainer else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = label,
                                    tint = iconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (showBadge) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 8.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(VybalViolet)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VybalNavigationRail(
    currentRoute: String?,
    onNavigate: (VybalDestination) -> Unit,
    unreadInboxCount: Int = 0
) {
    NavigationRail(
        containerColor = VybalSurfaceLow,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxHeight()
            .border(width = 1.dp, color = VybalOutlineSubtle)
    ) {
        Spacer(Modifier.height(16.dp))
        VybalDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            val label = stringResource(destination.labelRes)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                modifier = Modifier.testTag(destination.testTag),
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label, maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.tertiary,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = VybalVioletContainer,
                    unselectedIconColor = VybalTextMuted,
                    unselectedTextColor = VybalTextMuted
                )
            )
        }
    }
}
