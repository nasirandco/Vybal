package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.PostEngagementSummary
import com.example.data.model.ReactionType
import com.example.data.model.VybalPost
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalOutlineSubtle
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSurfaceHigh
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VybalInteractivePostCard(
    post: VybalPost,
    authorHandle: String,
    authorBadge: com.example.data.model.ProfileIdentityBadge? = null,
    summary: PostEngagementSummary?,
    imageLoader: ImageLoader,
    dataSaverEnabled: Boolean,
    onAuthorClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onReactionSelect: (ReactionType, Boolean) -> Unit,
    onCommentClick: () -> Unit,
    onRepostClick: () -> Unit,
    onShareClick: () -> Unit,
    onQuickSaveClick: () -> Unit,
    onSaveCollectionClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var manualLoadMedia by rememberSaveable(post.id) { mutableStateOf(false) }
    var showReactionPicker by remember(post.id) { mutableStateOf(false) }
    var showDoubleTapHeart by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(showDoubleTapHeart) {
        if (showDoubleTapHeart) {
            delay(650)
            showDoubleTapHeart = false
        }
    }

    val effectiveSummary = summary ?: PostEngagementSummary(
        postId = post.id,
        shareCount = post.shareCount,
        viewCount = post.viewCount
    )
    val myReaction = effectiveSummary.myReaction
    val displayHandle = authorHandle.ifBlank { post.authorUsername.ifBlank { post.userId.take(8).ifBlank { "member" } } }

    VybalSurfaceCard(
        modifier = modifier.testTag("post_card_${post.id}"),
        contentPadding = PaddingValues(VybalSpacing.md)
    ) {
        if (post.isPinned) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = "Pinned post",
                    tint = VybalLavender,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Pinned Post",
                    style = MaterialTheme.typography.labelSmall,
                    color = VybalLavender
                )
            }
        }

        // Author Header + Options Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAuthorClick(post.userId) }
                    .padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VybalVioletContainer)
                        .border(1.dp, VybalViolet.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayHandle.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = VybalLavender
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "@$displayHandle",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (authorBadge != null && (authorBadge.isVerified || authorBadge.isSuperAdmin)) {
                            VybalVerifiedBadge(
                                isVerified = authorBadge.isVerified,
                                verificationTier = authorBadge.verificationTier,
                                isSuperAdmin = authorBadge.isSuperAdmin,
                                iconSize = 15.dp
                            )
                        }
                        if (authorBadge?.isSuperAdmin == true) {
                            VybalRoleDesignationChip(role = authorBadge.role)
                        }
                    }
                    if (post.createdAt.isNotBlank()) {
                        Text(
                            text = post.createdAt.take(16).replace("T", " "),
                            style = MaterialTheme.typography.bodySmall,
                            color = VybalTextMuted
                        )
                    }
                }
            }

            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("post_options_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Post options",
                    tint = VybalTextSecondary
                )
            }
        }

        // Rich text caption with clickable @mentions and #hashtags
        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            RichSocialTextView(
                text = post.text,
                style = MaterialTheme.typography.bodyLarge,
                onMentionClick = onMentionClick,
                onHashtagClick = onHashtagClick
            )
        }

        // Media with Double-Tap to Like + Animated Heart
        if (!post.mediaUrl.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            if (dataSaverEnabled && !manualLoadMedia) {
                Button(
                    onClick = { manualLoadMedia = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VybalSurfaceHigh,
                        contentColor = VybalLavender
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.DataSaverOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_load_image))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VybalSurfaceHigh)
                        .pointerInput(post.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    showDoubleTapHeart = true
                                    onReactionSelect(ReactionType.Like, true)
                                }
                            )
                        }
                        .testTag("post_media_${post.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(post.mediaUrl)
                            .crossfade(120)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = stringResource(R.string.cd_post_media),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showDoubleTapHeart,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }
        }

        // Reaction Summary Pills (when reactions exist)
        if (effectiveSummary.topReactions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                effectiveSummary.topReactions.forEach { (type, count) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (myReaction == type) VybalVioletContainer else VybalSurfaceHigh,
                        border = BorderStroke(
                            1.dp,
                            if (myReaction == type) VybalViolet.copy(alpha = 0.6f) else VybalOutlineSubtle
                        ),
                        modifier = Modifier.clickable { onReactionSelect(type, false) }
                    ) {
                        Text(
                            text = "${type.emoji} $count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Expandable Reaction Picker (long-press Like button)
        AnimatedVisibility(visible = showReactionPicker) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = VybalSurfaceHigh,
                border = BorderStroke(1.dp, VybalOutline),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag("reaction_picker_${post.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        ReactionType.Love,
                        ReactionType.Haha,
                        ReactionType.Wow,
                        ReactionType.Sad,
                        ReactionType.Support
                    ).forEach { reaction ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (myReaction == reaction) VybalVioletContainer else Color.Transparent
                                )
                                .clickable {
                                    showReactionPicker = false
                                    onReactionSelect(reaction, false)
                                }
                                .testTag("react_${reaction.apiValue}_${post.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = reaction.emoji, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Clean Engagement Action Bar
        val heartScale by animateFloatAsState(
            targetValue = if (myReaction != null) 1.12f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "like_scale"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like / Reaction (Tap = Like/Unlike, Long-Press = Reaction Picker)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = { onReactionSelect(myReaction ?: ReactionType.Like, false) },
                        onLongClick = { showReactionPicker = !showReactionPicker }
                    )
                    .padding(horizontal = 6.dp)
                    .testTag("like_button_${post.id}")
            ) {
                if (myReaction != null && myReaction != ReactionType.Like) {
                    Text(text = myReaction.emoji, style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        imageVector = if (myReaction != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like post (long press for reactions)",
                        tint = if (myReaction != null) VybalError else VybalTextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(heartScale)
                    )
                }
                if (effectiveSummary.totalReactions > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${effectiveSummary.totalReactions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (myReaction != null) MaterialTheme.colorScheme.onBackground else VybalTextSecondary
                    )
                }
            }

            // Comment
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onCommentClick)
                    .padding(horizontal = 6.dp)
                    .testTag("comment_button_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comments",
                    tint = VybalTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                if (effectiveSummary.commentCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${effectiveSummary.commentCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = VybalTextSecondary
                    )
                }
            }

            // Repost
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRepostClick)
                    .padding(horizontal = 6.dp)
                    .testTag("repost_button_${post.id}")
            ) {
                Icon(
                    imageVector = if (effectiveSummary.isRepostedByMe) Icons.Filled.Repeat else Icons.Outlined.Repeat,
                    contentDescription = "Repost",
                    tint = if (effectiveSummary.isRepostedByMe) VybalViolet else VybalTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                if (effectiveSummary.repostCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${effectiveSummary.repostCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (effectiveSummary.isRepostedByMe) VybalLavender else VybalTextSecondary
                    )
                }
            }

            // Share
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onShareClick)
                    .padding(horizontal = 6.dp)
                    .testTag("share_button_${post.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share post",
                    tint = VybalTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                if (effectiveSummary.shareCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${effectiveSummary.shareCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = VybalTextSecondary
                    )
                }
            }

            // Bookmark (Tap = Quick Save/Unsave, Long-Press = Private Collections)
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = onQuickSaveClick,
                        onLongClick = onSaveCollectionClick
                    )
                    .padding(horizontal = 6.dp)
                    .testTag("bookmark_button_${post.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (effectiveSummary.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save post (long press for collections)",
                    tint = if (effectiveSummary.isSavedByMe) VybalLavender else VybalTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
