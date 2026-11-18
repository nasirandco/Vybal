package com.example.data.model

enum class AdminSection(val label: String) {
    Overview("Overview"),
    Users("Users"),
    Verification("Verification"),
    Moderation("Moderation"),
    Administrators("Admins"),
    Platform("Platform")
}

data class ProfileIdentityBadge(
    val userId: String,
    val username: String,
    val role: String = "user",
    val isVerified: Boolean = false,
    val verificationTier: String? = null
) {
    val isSuperAdmin: Boolean
        get() = role.equals("super_admin", ignoreCase = true)
}

data class AdminDashboardMetrics(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val onlineUsers: Int = 0,
    val totalPosts: Int = 0,
    val totalVideos: Int = 0,
    val totalStories: Int = 0,
    val pendingVerifications: Int = 0,
    val reportedContent: Int = 0,
    val bannedAccounts: Int = 0,
    val newUsers7d: Int = 0,
    val newPosts7d: Int = 0
)

data class VerificationRequestRecord(
    val id: String,
    val userId: String,
    val username: String = "",
    val displayName: String = "",
    val requestedTier: String = "creator",
    val reason: String = "",
    val status: String = "pending",
    val reviewNotes: String? = null,
    val createdAt: String = ""
)

data class ModerationReportRecord(
    val id: String,
    val postId: String,
    val reporterId: String,
    val reporterHandle: String = "",
    val reason: String,
    val createdAt: String = ""
)

data class AdminStoryRecord(
    val id: String,
    val userId: String,
    val authorHandle: String = "",
    val mediaUrl: String? = null,
    val caption: String = "",
    val createdAt: String = ""
)

data class AdminPermissionRecord(
    val userId: String,
    val canManageUsers: Boolean = true,
    val canManageVerifications: Boolean = true,
    val canModerateContent: Boolean = true,
    val canManageSettings: Boolean = false,
    val canSendAnnouncements: Boolean = false
)

data class PlatformAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val audience: String = "all",
    val isActive: Boolean = true,
    val createdAt: String = ""
)

data class PlatformSettings(
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "VYBAL is undergoing scheduled maintenance.",
    val allowRegistrations: Boolean = true,
    val allowPosting: Boolean = true,
    val allowComments: Boolean = true
)

data class AuditLogEntry(
    val id: String,
    val actorId: String? = null,
    val actorEmail: String = "",
    val action: String,
    val targetType: String,
    val targetId: String? = null,
    val details: String = "",
    val severity: String = "info",
    val createdAt: String = ""
)
