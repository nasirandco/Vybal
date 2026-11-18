package com.example.data.model

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtEpochSec: Long? = null
) {
    fun isExpiredOrExpiring(nowEpochSec: Long = System.currentTimeMillis() / 1000L): Boolean {
        val expiry = expiresAtEpochSec ?: return false
        return nowEpochSec >= (expiry - 60L)
    }
}

data class VybalProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarUrl: String?,
    val email: String = "",
    val role: String = "user",
    val isVerified: Boolean = false,
    val verificationTier: String? = null,
    val accountStatus: String = "active",
    val restrictionReason: String? = null,
    val lastSeenAt: String? = null
) {
    val isSuperAdmin: Boolean
        get() = role.equals("super_admin", ignoreCase = true)

    val isStaff: Boolean
        get() = role.lowercase() in setOf("super_admin", "admin", "moderator")
}

data class VybalPost(
    val id: String,
    val userId: String,
    val text: String,
    val mediaUrl: String?,
    val visibility: String,
    val createdAt: String,
    val authorUsername: String = "",
    val isPinned: Boolean = false,
    val commentsDisabled: Boolean = false,
    val shareCount: Int = 0,
    val viewCount: Int? = null,
    val repostedByUsername: String? = null,
    val repostCaption: String? = null
)

data class VybalNotification(
    val id: String,
    val type: String,
    val message: String,
    val createdAt: String,
    val isRead: Boolean,
    val actorId: String? = null,
    val postId: String? = null
)

data class VybalMessage(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val createdAt: String
)

sealed interface ResourceState<out T> {
    data object Idle : ResourceState<Nothing>
    data object Loading : ResourceState<Nothing>
    data object NotConfigured : ResourceState<Nothing>
    data object AuthRequired : ResourceState<Nothing>
    data class Success<T>(val data: T) : ResourceState<T>
    data class Error(val message: String) : ResourceState<Nothing>
}
