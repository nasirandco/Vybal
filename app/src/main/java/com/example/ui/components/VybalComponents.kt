package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.performance.NetworkStatus
import com.example.data.model.VybalPost
import com.example.ui.theme.VybalBrushes
import com.example.ui.theme.VybalObsidian
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalOutlineSubtle
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSuccess
import com.example.ui.theme.VybalSurface
import com.example.ui.theme.VybalSurfaceHigh
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer
import com.example.ui.theme.VybalWarning

@Composable
fun VybalBrandTopBar(
    title: String,
    isSupabaseConfigured: Boolean,
    showWordmark: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        color = VybalObsidian.copy(alpha = 0.96f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VybalSpacing.md, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showWordmark) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_vybal_monogram),
                            contentDescription = stringResource(R.string.cd_brand_logo),
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = title,
                        style = if (showWordmark) {
                            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusDotBadge(connected = isSupabaseConfigured)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    trailingContent?.invoke()
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_bar_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh_feed),
                                tint = VybalTextSecondary
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VybalOutlineSubtle)
            )
        }
    }
}

@Composable
fun StatusDotBadge(connected: Boolean) {
    val dotColor = if (connected) VybalSuccess else VybalWarning
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor)
    )
}

@Composable
fun VybalNetworkBanner(
    networkStatus: NetworkStatus,
    dataSaverEnabled: Boolean
) {
    val showOffline = !networkStatus.isOnline
    val showMeteredHint = networkStatus.isOnline && networkStatus.isMetered && !dataSaverEnabled

    AnimatedVisibility(
        visible = showOffline || showMeteredHint,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val bgColor = if (showOffline) MaterialTheme.colorScheme.errorContainer else VybalVioletContainer
        val icon = if (showOffline) Icons.Outlined.CloudOff else Icons.Outlined.SignalCellularAlt
        val text = if (showOffline) {
            stringResource(R.string.banner_offline)
        } else {
            stringResource(R.string.banner_metered)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(horizontal = VybalSpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.cd_network_status),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun VybalSurfaceCard(
    modifier: Modifier = Modifier,
    borderColor: Color = VybalOutlineSubtle,
    contentPadding: PaddingValues = PaddingValues(VybalSpacing.md),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = VybalSurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun VybalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    testTag: String = "vybal_primary_button"
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                brush = if (enabled) VybalBrushes.primaryAccent else Brush.linearGradient(
                    listOf(VybalSurfaceHigh, VybalSurfaceHigh)
                )
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimary else VybalTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimary else VybalTextMuted
                )
            }
        }
    }
}

@Composable
fun VybalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String = "vybal_secondary_button"
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, VybalOutline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = VybalSurfaceHigh.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onBackground
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VybalSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = VybalSurface,
        border = BorderStroke(1.dp, VybalOutlineSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val bgColor by animateColorAsState(
                    targetValue = if (selected) VybalVioletContainer else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "segment_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onBackground else VybalTextSecondary,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "segment_text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) VybalViolet.copy(alpha = 0.55f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(index) }
                        .testTag("segment_tab_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun VybalStatePanel(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    showAuraHeader: Boolean = false
) {
    VybalSurfaceCard(
        modifier = modifier,
        borderColor = VybalOutline.copy(alpha = 0.7f),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (showAuraHeader) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vybal_aura),
                    contentDescription = stringResource(R.string.cd_brand_aura),
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, VybalSurface)
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VybalSpacing.lg, vertical = VybalSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(VybalVioletContainer)
                    .border(1.dp, VybalViolet.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(VybalSpacing.md))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(VybalSpacing.sm))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = VybalTextSecondary,
                textAlign = TextAlign.Center
            )

            if (primaryActionLabel != null && onPrimaryAction != null) {
                Spacer(Modifier.height(VybalSpacing.lg))
                VybalPrimaryButton(
                    text = primaryActionLabel,
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "state_panel_primary_action"
                )
            }

            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(Modifier.height(VybalSpacing.sm))
                VybalSecondaryButton(
                    text = secondaryActionLabel,
                    onClick = onSecondaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "state_panel_secondary_action"
                )
            }
        }
    }
}

@Composable
fun VybalLoadingCard(modifier: Modifier = Modifier) {
    VybalSurfaceCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = VybalSpacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = VybalViolet
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Syncing with VYBAL…",
                style = MaterialTheme.typography.bodyMedium,
                color = VybalTextSecondary
            )
        }
    }
}

@Composable
fun VybalPostCard(
    post: VybalPost,
    imageLoader: ImageLoader,
    dataSaverEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var manualLoadMedia by rememberSaveable(post.id) { mutableStateOf(false) }

    VybalSurfaceCard(
        modifier = modifier.testTag("post_card_${post.id}"),
        contentPadding = PaddingValues(VybalSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(VybalVioletContainer)
                        .border(1.dp, VybalViolet.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.userId.take(2).uppercase().ifBlank { "VY" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Column {
                    Text(
                        text = if (post.userId.isNotBlank()) {
                            "Member • ${post.userId.take(8)}"
                        } else {
                            "VYBAL Member"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (post.createdAt.isNotBlank()) {
                        Text(
                            text = post.createdAt.take(16).replace("T", " "),
                            style = MaterialTheme.typography.bodySmall,
                            color = VybalTextMuted
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    tint = VybalTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = post.visibility.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = VybalTextMuted
                )
            }
        }

        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (!post.mediaUrl.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            if (dataSaverEnabled && !manualLoadMedia) {
                Button(
                    onClick = { manualLoadMedia = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VybalSurfaceHigh,
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DataSaverOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_load_image))
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(post.mediaUrl)
                        .crossfade(120)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = stringResource(R.string.cd_post_media),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VybalSurfaceHigh)
                )
            }
        }
    }
}
