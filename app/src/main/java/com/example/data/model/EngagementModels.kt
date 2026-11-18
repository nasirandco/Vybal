package com.example.data.model

enum class ReactionType(
    val apiValue: String,
    val emoji: String,
    val label: String
) {
    Like("like", "♥", "Like"),
    Love("love", "❤️", "Love"),
    Haha("haha", "😂", "Haha"),
    Wow("wow", "😮", "Wow"),
    Sad("sad", "😢", "Sad"),
    Support("support", "💜", "Support");

    companion object {
        fun fromApi(value: String?): ReactionType? =
            entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
    }
}

data class PostEngagementSummary(
    val postId: String,
    val reactionCounts: Map<ReactionType, Int> = emptyMap(),
    val myReaction: ReactionType? = null,
    val commentCount: Int = 0,
    val repostCount: Int = 0,
    val isRepostedByMe: Boolean = false,
    val isSavedByMe: Boolean = false,
    val savedCollectionId: String? = null,
    val shareCount: Int = 0,
    val viewCount: Int? = null
) {
    val totalReactions: Int
        get() = reactionCounts.values.sum()

    val likeCount: Int
        get() = totalReactions

    val topReactions: List<Pair<ReactionType, Int>>
        get() = reactionCounts.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key to it.value }
}

data class VybalComment(
    val id: String,
    val postId: String,
    val userId: String,
    val authorUsername: String,
    val parentCommentId: String? = null,
    val content: String,
    val isPinned: Boolean = false,
    val likeCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val createdAt: String = ""
)

data class SavedCollection(
    val id: String,
    val userId: String,
    val name: String,
    val createdAt: String = ""
)

data class SavedPostEntry(
    val id: String,
    val postId: String,
    val collectionId: String?,
    val createdAt: String = ""
)

data class VybalRepost(
    val id: String,
    val userId: String,
    val postId: String,
    val caption: String? = null,
    val createdAt: String = ""
)

data class FollowStats(
    val targetUserId: String,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val mutualCount: Int = 0,
    val isFollowing: Boolean = false,
    val isPending: Boolean = false
)

data class PostInsights(
    val postId: String,
    val likesCount: Int,
    val reactionBreakdown: Map<ReactionType, Int>,
    val commentsCount: Int,
    val repostsCount: Int,
    val savesCount: Int?,
    val sharesCount: Int,
    val viewsCount: Int?
)
