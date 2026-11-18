package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.OnlinePrediction
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminDashboardMetrics
import com.example.data.model.AdminSection
import com.example.data.model.ResourceState
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalOutlineSubtle
import com.example.ui.theme.VybalPurpleDeep
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSuccess
import com.example.ui.theme.VybalSurface
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer
import com.example.ui.theme.VybalWarning

@Composable
fun AdminOverviewSection(
    metricsState: ResourceState<AdminDashboardMetrics>,
    onRefresh: () -> Unit,
    onNavigateSection: (AdminSection) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_overview_section"),
        contentPadding = PaddingValues(VybalSpacing.md),
        verticalArrangement = Arrangement.spacedBy(VybalSpacing.md)
    ) {
        item(key = "hero_banner") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(VybalPurpleDeep, VybalViolet.copy(alpha = 0.85f))
                        )
                    )
                    .padding(VybalSpacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "VYBAL Platform Telemetry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Live server-enforced metrics from Supabase. No placeholder numbers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f)
                    )
                }
            }
        }

        when (metricsState) {
            ResourceState.Idle, ResourceState.Loading -> {
                item(key = "metrics_loading") { VybalLoadingCard() }
            }
            is ResourceState.Error -> {
                item(key = "metrics_error") {
                    VybalStatePanel(
                        icon = Icons.Outlined.CloudOff,
                        title = "Metrics Unavailable",
                        body = metricsState.message,
                        primaryActionLabel = "Retry",
                        onPrimaryAction = onRefresh
                    )
                }
            }
            is ResourceState.Success -> {
                val m = metricsState.data
                item(key = "kpi_row_1") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminMetricCard(
                            title = "Total Users",
                            value = "${m.totalUsers}",
                            icon = Icons.Outlined.Group,
                            accent = VybalLavender,
                            onClick = { onNavigateSection(AdminSection.Users) },
                            modifier = Modifier.weight(1f)
                        )
                        AdminMetricCard(
                            title = "Active Users",
                            value = "${m.activeUsers}",
                            icon = Icons.Outlined.Group,
                            accent = VybalSuccess,
                            onClick = { onNavigateSection(AdminSection.Users) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item(key = "kpi_row_2") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminMetricCard(
                            title = "Online (15m)",
                            value = "${m.onlineUsers}",
                            icon = Icons.Outlined.OnlinePrediction,
                            accent = VybalSuccess,
                            onClick = { onNavigateSection(AdminSection.Users) },
                            modifier = Modifier.weight(1f)
                        )
                        AdminMetricCard(
                            title = "Posts",
                            value = "${m.totalPosts}",
                            icon = Icons.Outlined.PostAdd,
                            accent = VybalViolet,
                            onClick = { onNavigateSection(AdminSection.Moderation) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item(key = "kpi_row_3") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminMetricCard(
                            title = "Videos",
                            value = "${m.totalVideos}",
                            icon = Icons.Outlined.VideoLibrary,
                            accent = VybalLavender,
                            onClick = { onNavigateSection(AdminSection.Moderation) },
                            modifier = Modifier.weight(1f)
                        )
                        AdminMetricCard(
                            title = "Stories",
                            value = "${m.totalStories}",
                            icon = Icons.Outlined.AutoStories,
                            accent = VybalViolet,
                            onClick = { onNavigateSection(AdminSection.Moderation) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item(key = "kpi_row_4") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminMetricCard(
                            title = "Pending Verify",
                            value = "${m.pendingVerifications}",
                            icon = Icons.Outlined.VerifiedUser,
                            accent = VybalWarning,
                            onClick = { onNavigateSection(AdminSection.Verification) },
                            modifier = Modifier.weight(1f)
                        )
                        AdminMetricCard(
                            title = "Reported",
                            value = "${m.reportedContent}",
                            icon = Icons.Outlined.Flag,
                            accent = VybalError,
                            onClick = { onNavigateSection(AdminSection.Moderation) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item(key = "kpi_row_5") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminMetricCard(
                            title = "Banned Accounts",
                            value = "${m.bannedAccounts}",
                            icon = Icons.Outlined.Block,
                            accent = VybalError,
                            onClick = { onNavigateSection(AdminSection.Users) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }

                item(key = "growth_card") {
                    VybalSurfaceCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TrendingUp,
                                contentDescription = null,
                                tint = VybalLavender,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "7-Day Platform Growth",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GrowthStatPill(
                                label = "New Users (7d)",
                                value = "+${m.newUsers7d}",
                                modifier = Modifier.weight(1f)
                            )
                            GrowthStatPill(
                                label = "New Posts (7d)",
                                value = "+${m.newPosts7d}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun AdminMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = VybalSurface,
        border = BorderStroke(1.dp, VybalOutlineSubtle)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = VybalTextSecondary
            )
        }
    }
}

@Composable
private fun GrowthStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = VybalVioletContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, VybalViolet.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VybalLavender
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VybalTextMuted
            )
        }
    }
}
