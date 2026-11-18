package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CommentsDisabled
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PostInsights
import com.example.data.model.ReactionType
import com.example.data.model.SavedCollection
import com.example.data.model.VybalComment
import com.example.data.model.VybalPost
import com.example.ui.state.CommentsSheetState
import com.example.ui.state.UserProfileSheetState
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalObsidian
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalOutlineSubtle
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSurface
import com.example.ui.theme.VybalSurfaceHigh
import com.example.ui.theme.VybalSurfaceLow
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    state: CommentsSheetState,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onSubmitComment: (String) -> Unit,
    onLoadMore: () -> Unit,
    onReply: (VybalComment?) -> Unit,
    onEdit: (VybalComment?) -> Unit,
    onDelete: (VybalComment) -> Unit,
    onPin: (VybalComment) -> Unit,
    onLike: (VybalComment) -> Unit,
    onMentionClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit
) {
    val post = state.post ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by rememberSaveable(post.id) { mutableStateOf("") }

    LaunchedEffect(state.editingComment?.id) {
        val editing = state.editingComment
        if (editing != null) inputText = editing.content
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VybalSurfaceLow,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = VybalSpacing.md)
                .padding(bottom = VybalSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Comments", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close comments", tint = VybalTextSecondary)
                }
            }

            if (post.commentsDisabled) {
                Text(
                    text = "Comments are turned off by the post creator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VybalTextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val topLevel = state.comments.filter { it.parentCommentId == null }
            val repliesByParent = state.comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (state.isLoading && state.comments.isEmpty()) {
                    item { VybalLoadingCard() }
                } else if (topLevel.isEmpty()) {
                    item {
                        Text(
                            text = "No comments yet. Start the discussion.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VybalTextSecondary,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                } else {
                    items(topLevel, key = { it.id }) { comment ->
                        CommentItemRow(
                            comment = comment,
                            isPostCreatorComment = comment.userId == post.userId,
                            canManagePin = currentUserId == post.userId,
                            isOwnComment = currentUserId == comment.userId,
                            isReply = false,
                            onReply = { onReply(comment) },
                            onEdit = { onEdit(comment) },
                            onDelete = { onDelete(comment) },
                            onPin = { onPin(comment) },
                            onLike = { onLike(comment) },
                            onMentionClick = onMentionClick,
                            onHashtagClick = onHashtagClick
                        )
                        val replies = repliesByParent[comment.id].orEmpty()
                        replies.forEach { reply ->
                            Spacer(Modifier.height(6.dp))
                            CommentItemRow(
                                comment = reply,
                                isPostCreatorComment = reply.userId == post.userId,
                                canManagePin = false,
                                isOwnComment = currentUserId == reply.userId,
                                isReply = true,
                                onReply = { onReply(comment) },
                                onEdit = { onEdit(reply) },
                                onDelete = { onDelete(reply) },
                                onPin = {},
                                onLike = { onLike(reply) },
                                onMentionClick = onMentionClick,
                                onHashtagClick = onHashtagClick
                            )
                        }
                    }

                    if (state.hasMore) {
                        item {
                            TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Load more comments", color = VybalLavender)
                            }
                        }
                    }
                }
            }

            if (!post.commentsDisabled) {
                HorizontalDivider(color = VybalOutlineSubtle)
                Spacer(Modifier.height(8.dp))

                if (state.replyingTo != null || state.editingComment != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.editingComment != null) {
                                "Editing your comment"
                            } else {
                                "Replying to @${state.replyingTo?.authorUsername}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = VybalLavender
                        )
                        TextButton(onClick = {
                            onReply(null)
                            onEdit(null)
                            inputText = ""
                        }) {
                            Text("Cancel", color = VybalTextSecondary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= 1000) inputText = it },
                        placeholder = { Text("Add a comment…", color = VybalTextMuted) },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VybalViolet,
                            unfocusedBorderColor = VybalOutline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input_field")
                    )
                    IconButton(
                        enabled = !state.isSubmitting && inputText.isNotBlank(),
                        onClick = {
                            val toSend = inputText
                            inputText = ""
                            onSubmitComment(toSend)
                        },
                        modifier = Modifier.testTag("comment_send_button")
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = VybalViolet)
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send comment", tint = VybalViolet)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItemRow(
    comment: VybalComment,
    isPostCreatorComment: Boolean,
    canManagePin: Boolean,
    isOwnComment: Boolean,
    isReply: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onLike: () -> Unit,
    onMentionClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPostCreatorComment) VybalVioletContainer.copy(alpha = 0.45f) else VybalSurface,
        border = BorderStroke(
            1.dp,
            if (comment.isPinned) VybalViolet.copy(alpha = 0.7f) else VybalOutlineSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 28.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "@${comment.authorUsername}",
                        style = MaterialTheme.typography.titleSmall,
                        color = VybalLavender,
                        modifier = Modifier.clickable { onMentionClick(comment.authorUsername) }
                    )
                    if (isPostCreatorComment) {
                        Text(
                            text = "Creator",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VybalViolet)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (comment.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pinned comment",
                            tint = VybalLavender,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (comment.createdAt.isNotBlank()) {
                    Text(
                        text = comment.createdAt.take(16).replace("T", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = VybalTextMuted
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            RichSocialTextView(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                onMentionClick = onMentionClick,
                onHashtagClick = onHashtagClick
            )

            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onLike)
                ) {
                    Icon(
                        imageVector = if (comment.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like comment",
                        tint = if (comment.isLikedByMe) VybalError else VybalTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    if (comment.likeCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text("${comment.likeCount}", style = MaterialTheme.typography.labelSmall, color = VybalTextSecondary)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onReply)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = "Reply", tint = VybalTextMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reply", style = MaterialTheme.typography.labelSmall, color = VybalTextSecondary)
                }

                if (canManagePin) {
                    Text(
                        text = if (comment.isPinned) "Unpin" else "Pin",
                        style = MaterialTheme.typography.labelSmall,
                        color = VybalLavender,
                        modifier = Modifier.clickable(onClick = onPin)
                    )
                }
                if (isOwnComment) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit comment",
                        tint = VybalTextMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onEdit)
                    )
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete comment",
                        tint = VybalError,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onDelete)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveCollectionBottomSheet(
    post: VybalPost?,
    collections: List<SavedCollection>,
    onDismiss: () -> Unit,
    onSaveDefault: () -> Unit,
    onSaveToCollection: (String) -> Unit,
    onCreateCollection: (String) -> Unit
) {
    if (post == null) return
    var newName by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = VybalSpacing.lg)
                .padding(bottom = VybalSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Save to Private Collection", style = MaterialTheme.typography.titleLarge)
            Text(
                "Saved posts and collections are private to your account.",
                style = MaterialTheme.typography.bodySmall,
                color = VybalTextSecondary
            )

            VybalSecondaryButton(
                text = "All Saved Posts",
                icon = Icons.Outlined.BookmarkAdd,
                onClick = onSaveDefault,
                modifier = Modifier.fillMaxWidth()
            )

            collections.forEach { col ->
                VybalSecondaryButton(
                    text = col.name,
                    onClick = { onSaveToCollection(col.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 50) newName = it },
                    placeholder = { Text("New collection name…", color = VybalTextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                VybalPrimaryButton(
                    text = "Create",
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onCreateCollection(newName)
                        newName = ""
                    }
                )
            }
        }
    }
}

@Composable
fun RepostDialog(
    post: VybalPost?,
    isAlreadyReposted: Boolean,
    authorHandle: String,
    onDismiss: () -> Unit,
    onConfirmRepost: (String?) -> Unit,
    onRemoveRepost: () -> Unit
) {
    if (post == null) return
    var caption by rememberSaveable(post.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        title = {
            Text(
                text = if (isAlreadyReposted) "Manage Repost" else "Repost @$authorHandle",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isAlreadyReposted) {
                    Text(
                        "You have already reposted this post. You can remove your repost below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VybalTextSecondary
                    )
                } else {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { if (it.length <= 280) caption = it },
                        label = { Text("Add an optional caption") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Original creator @$authorHandle will remain attributed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VybalTextMuted
                    )
                }
            }
        },
        confirmButton = {
            if (isAlreadyReposted) {
                TextButton(onClick = onRemoveRepost) {
                    Text("Remove Repost", color = VybalError)
                }
            } else {
                TextButton(onClick = { onConfirmRepost(caption.takeIf { it.isNotBlank() }) }) {
                    Text("Repost", color = VybalViolet)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VybalTextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostOptionsBottomSheet(
    post: VybalPost?,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: (VybalPost) -> Unit,
    onNativeShare: (VybalPost) -> Unit,
    onTogglePin: (VybalPost) -> Unit,
    onToggleComments: (VybalPost) -> Unit,
    onOpenInsights: (VybalPost) -> Unit,
    onHidePost: (VybalPost) -> Unit,
    onReportPost: (VybalPost, String) -> Unit
) {
    if (post == null) return
    var showReportPicker by rememberSaveable(post.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = VybalSpacing.md)
                .padding(bottom = VybalSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showReportPicker) {
                Text("Report Post", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                listOf("Spam or misleading", "Harassment or abuse", "Inappropriate content", "Copyright or impersonation").forEach { reason ->
                    OptionSheetRow(
                        icon = Icons.Outlined.Flag,
                        label = reason,
                        tint = VybalError,
                        onClick = { onReportPost(post, reason) }
                    )
                }
            } else {
                OptionSheetRow(Icons.Outlined.Share, "Share via…") {
                    onDismiss()
                    onNativeShare(post)
                }
                OptionSheetRow(Icons.Outlined.ContentCopy, "Copy post link") {
                    onDismiss()
                    onCopyLink(post)
                }
                if (isOwner) {
                    OptionSheetRow(
                        Icons.Outlined.PushPin,
                        if (post.isPinned) "Unpin from profile" else "Pin to profile"
                    ) { onTogglePin(post) }
                    OptionSheetRow(
                        Icons.Outlined.CommentsDisabled,
                        if (post.commentsDisabled) "Turn on comments" else "Turn off comments"
                    ) { onToggleComments(post) }
                    OptionSheetRow(Icons.Outlined.BarChart, "View post insights") {
                        onOpenInsights(post)
                    }
                } else {
                    OptionSheetRow(Icons.Outlined.VisibilityOff, "Hide post") {
                        onHidePost(post)
                    }
                    OptionSheetRow(Icons.Outlined.Flag, "Report post", tint = VybalError) {
                        showReportPicker = true
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSheetRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostInsightsBottomSheet(
    insights: PostInsights?,
    onDismiss: () -> Unit
) {
    if (insights == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = VybalSpacing.lg)
                .padding(bottom = VybalSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Post Insights", style = MaterialTheme.typography.titleLarge)
            Text(
                "Verified real engagement metrics from Supabase.",
                style = MaterialTheme.typography.bodySmall,
                color = VybalTextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightMetricTile("Reactions", "${insights.likesCount}", Modifier.weight(1f))
                InsightMetricTile("Comments", "${insights.commentsCount}", Modifier.weight(1f))
                InsightMetricTile("Reposts", "${insights.repostsCount}", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightMetricTile("Shares", "${insights.sharesCount}", Modifier.weight(1f))
                InsightMetricTile("Views", insights.viewsCount?.toString() ?: "—", Modifier.weight(1f))
                InsightMetricTile("Saves", insights.savesCount?.toString() ?: "Private", Modifier.weight(1f))
            }

            if (insights.reactionBreakdown.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    insights.reactionBreakdown.forEach { (type, count) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VybalSurfaceHigh,
                            border = BorderStroke(1.dp, VybalOutlineSubtle)
                        ) {
                            Text(
                                text = "${type.emoji} $count",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = VybalSurface,
        border = BorderStroke(1.dp, VybalOutlineSubtle)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = VybalLavender)
            Text(label, style = MaterialTheme.typography.labelSmall, color = VybalTextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileBottomSheet(
    state: UserProfileSheetState,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onToggleFollow: (String) -> Unit
) {
    if (!state.isOpen) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = VybalSpacing.lg)
                .padding(bottom = VybalSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                state.isLoading -> VybalLoadingCard()
                state.profile == null -> {
                    Text(
                        text = state.errorMessage ?: "User profile unavailable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VybalTextSecondary
                    )
                }
                else -> {
                    val profile = state.profile
                    val stats = state.followStats
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(VybalVioletContainer)
                                .border(1.5.dp, VybalViolet, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (profile.displayName.ifBlank { profile.username }).take(2).uppercase().ifBlank { "VY" },
                                style = MaterialTheme.typography.titleLarge,
                                color = VybalLavender
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = profile.displayName.ifBlank { profile.username.ifBlank { "VYBAL Member" } },
                                    style = MaterialTheme.typography.titleLarge
                                )
                                VybalVerifiedBadge(
                                    isVerified = profile.isVerified,
                                    verificationTier = profile.verificationTier,
                                    isSuperAdmin = profile.isSuperAdmin
                                )
                            }
                            if (profile.username.isNotBlank()) {
                                Text("@${profile.username}", style = MaterialTheme.typography.bodySmall, color = VybalTextSecondary)
                            }
                            if (profile.isStaff) {
                                Spacer(Modifier.height(4.dp))
                                VybalRoleDesignationChip(role = profile.role)
                            }
                        }
                    }

                    if (profile.bio.isNotBlank()) {
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (stats != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("${stats.followersCount} Followers", style = MaterialTheme.typography.titleSmall, color = VybalLavender)
                            Text("${stats.followingCount} Following", style = MaterialTheme.typography.titleSmall, color = VybalTextSecondary)
                            if (stats.mutualCount > 0) {
                                Text("${stats.mutualCount} Mutual", style = MaterialTheme.typography.titleSmall, color = VybalTextMuted)
                            }
                        }
                    }

                    if (currentUserId != profile.id) {
                        val isFollowing = stats?.isFollowing == true
                        val isPending = stats?.isPending == true
                        if (isFollowing || isPending) {
                            VybalSecondaryButton(
                                text = if (isPending) "Requested" else "Following",
                                enabled = !state.isFollowBusy,
                                onClick = { onToggleFollow(profile.id) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "profile_sheet_follow_button"
                            )
                        } else {
                            VybalPrimaryButton(
                                text = "Follow",
                                loading = state.isFollowBusy,
                                onClick = { onToggleFollow(profile.id) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "profile_sheet_follow_button"
                            )
                        }
                    }
                }
            }
        }
    }
}
