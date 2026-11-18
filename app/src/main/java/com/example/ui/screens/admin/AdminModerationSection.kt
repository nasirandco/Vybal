package com.example.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminStoryRecord
import com.example.data.model.ModerationReportRecord
import com.example.data.model.ResourceState
import com.example.data.model.VybalComment
import com.example.data.model.VybalPost
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalSegmentedTabs
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary

@Composable
fun AdminModerationSection(
    reportsState: ResourceState<List<ModerationReportRecord>>,
    postsState: ResourceState<List<VybalPost>>,
    commentsState: ResourceState<List<VybalComment>>,
    storiesState: ResourceState<List<AdminStoryRecord>>,
    onRefresh: () -> Unit,
    onModerate: (contentType: String, contentId: String, reportId: String?, action: String, reason: String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_moderation_section")
    ) {
        VybalSegmentedTabs(
            tabs = listOf("Reports", "Posts", "Comments", "Stories"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = VybalSpacing.md, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(VybalSpacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    when (reportsState) {
                        ResourceState.Idle, ResourceState.Loading -> item { VybalLoadingCard() }
                        is ResourceState.Error -> item {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudOff,
                                title = "Could not load reports",
                                body = reportsState.message,
                                primaryActionLabel = "Retry",
                                onPrimaryAction = onRefresh
                            )
                        }
                        is ResourceState.Success -> {
                            if (reportsState.data.isEmpty()) {
                                item {
                                    VybalStatePanel(
                                        icon = Icons.Outlined.Flag,
                                        title = "No Open Reports",
                                        body = "User-reported posts and moderation flags will appear here."
                                    )
                                }
                            } else {
                                items(reportsState.data, key = { it.id }) { report ->
                                    VybalSurfaceCard {
                                        Text(
                                            text = "Reported Post • ${report.postId.take(8)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Reason: ${report.reason.ifBlank { "Unspecified" }}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = VybalTextSecondary
                                        )
                                        Text(
                                            text = "Reporter: ${report.reporterId.take(8)} • ${report.createdAt.take(16).replace("T", " ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VybalTextMuted
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    onModerate("post", report.postId, report.id, "dismiss", report.reason)
                                                }
                                            ) {
                                                Text("Dismiss", color = VybalLavender)
                                            }
                                            TextButton(
                                                onClick = {
                                                    onModerate("post", report.postId, report.id, "remove", report.reason)
                                                }
                                            ) {
                                                Text("Remove Post", color = VybalError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                1 -> {
                    when (postsState) {
                        ResourceState.Idle, ResourceState.Loading -> item { VybalLoadingCard() }
                        is ResourceState.Error -> item {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudOff,
                                title = "Could not load posts",
                                body = postsState.message,
                                primaryActionLabel = "Retry",
                                onPrimaryAction = onRefresh
                            )
                        }
                        is ResourceState.Success -> {
                            if (postsState.data.isEmpty()) {
                                item {
                                    VybalStatePanel(
                                        icon = Icons.Outlined.PostAdd,
                                        title = "No Posts Found",
                                        body = "Published posts and videos will appear here for review."
                                    )
                                }
                            } else {
                                items(postsState.data, key = { "mod_post_${it.id}" }) { post ->
                                    val isVideo = post.mediaUrl?.lowercase()?.let {
                                        it.contains(".mp4") || it.contains(".mov") || it.contains(".webm")
                                    } == true
                                    VybalSurfaceCard {
                                        Text(
                                            text = "${if (isVideo) "Video" else "Post"} • Author ${post.userId.take(8)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = VybalLavender
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = post.text.ifBlank { "[Media-only post]" },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (!post.mediaUrl.isNullOrBlank()) {
                                            Text(
                                                text = post.mediaUrl,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VybalTextMuted,
                                                maxLines = 1
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    onModerate("post", post.id, null, "remove", "Removed by staff moderation")
                                                }
                                            ) {
                                                Text("Remove", color = VybalError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                2 -> {
                    when (commentsState) {
                        ResourceState.Idle, ResourceState.Loading -> item { VybalLoadingCard() }
                        is ResourceState.Error -> item {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudOff,
                                title = "Could not load comments",
                                body = commentsState.message,
                                primaryActionLabel = "Retry",
                                onPrimaryAction = onRefresh
                            )
                        }
                        is ResourceState.Success -> {
                            if (commentsState.data.isEmpty()) {
                                item {
                                    VybalStatePanel(
                                        icon = Icons.Outlined.ChatBubbleOutline,
                                        title = "No Comments Found",
                                        body = "Recent comments will appear here for moderation."
                                    )
                                }
                            } else {
                                items(commentsState.data, key = { "mod_cmt_${it.id}" }) { cmt ->
                                    VybalSurfaceCard {
                                        Text(
                                            text = "Comment • @${cmt.authorUsername}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = VybalLavender
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(cmt.content, style = MaterialTheme.typography.bodyMedium)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    onModerate("comment", cmt.id, null, "remove", "Removed by staff moderation")
                                                }
                                            ) {
                                                Text("Remove", color = VybalError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                3 -> {
                    when (storiesState) {
                        ResourceState.Idle, ResourceState.Loading -> item { VybalLoadingCard() }
                        is ResourceState.Error -> item {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudOff,
                                title = "Could not load stories",
                                body = storiesState.message,
                                primaryActionLabel = "Retry",
                                onPrimaryAction = onRefresh
                            )
                        }
                        is ResourceState.Success -> {
                            if (storiesState.data.isEmpty()) {
                                item {
                                    VybalStatePanel(
                                        icon = Icons.Outlined.AutoStories,
                                        title = "No Active Stories",
                                        body = "Published stories will appear here for moderation."
                                    )
                                }
                            } else {
                                items(storiesState.data, key = { "mod_story_${it.id}" }) { story ->
                                    VybalSurfaceCard {
                                        Text(
                                            text = "Story • User ${story.userId.take(8)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = VybalLavender
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = story.caption.ifBlank { "[Media Story]" },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    onModerate("story", story.id, null, "remove", "Removed by staff moderation")
                                                }
                                            ) {
                                                Text("Remove", color = VybalError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
