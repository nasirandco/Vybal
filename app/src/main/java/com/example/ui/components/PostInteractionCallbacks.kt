package com.example.ui.components

import com.example.data.model.PostEngagementSummary
import com.example.data.model.ReactionType
import com.example.data.model.VybalPost

data class PostInteractionCallbacks(
    val summaries: Map<String, PostEngagementSummary>,
    val usernames: Map<String, String>,
    val profileBadges: Map<String, com.example.data.model.ProfileIdentityBadge> = emptyMap(),
    val hiddenPostIds: Set<String>,
    val onAuthorClick: (userId: String) -> Unit,
    val onMentionClick: (username: String) -> Unit,
    val onHashtagClick: (hashtag: String) -> Unit,
    val onReactionSelect: (post: VybalPost, reaction: ReactionType, isDoubleTap: Boolean) -> Unit,
    val onCommentClick: (post: VybalPost) -> Unit,
    val onRepostClick: (post: VybalPost) -> Unit,
    val onShareClick: (post: VybalPost) -> Unit,
    val onQuickSaveClick: (post: VybalPost) -> Unit,
    val onSaveCollectionClick: (post: VybalPost) -> Unit,
    val onOptionsClick: (post: VybalPost) -> Unit
)
