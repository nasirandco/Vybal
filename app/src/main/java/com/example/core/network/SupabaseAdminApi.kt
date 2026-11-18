package com.example.core.network

import com.example.core.config.VybalSchemaContract
import com.squareup.moshi.Json
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

data class SupabaseDashboardMetricsDto(
    @Json(name = "total_users") val totalUsers: Int? = 0,
    @Json(name = "active_users") val activeUsers: Int? = 0,
    @Json(name = "online_users") val onlineUsers: Int? = 0,
    @Json(name = "total_posts") val totalPosts: Int? = 0,
    @Json(name = "total_videos") val totalVideos: Int? = 0,
    @Json(name = "total_stories") val totalStories: Int? = 0,
    @Json(name = "pending_verifications") val pendingVerifications: Int? = 0,
    @Json(name = "reported_content") val reportedContent: Int? = 0,
    @Json(name = "banned_accounts") val bannedAccounts: Int? = 0,
    @Json(name = "new_users_7d") val newUsers7d: Int? = 0,
    @Json(name = "new_posts_7d") val newPosts7d: Int? = 0
)

data class AdminUpdateUserRpcRequest(
    @Json(name = "p_target_user_id") val targetUserId: String,
    @Json(name = "p_display_name") val displayName: String? = null,
    @Json(name = "p_bio") val bio: String? = null,
    @Json(name = "p_role") val role: String? = null,
    @Json(name = "p_is_verified") val isVerified: Boolean? = null,
    @Json(name = "p_verification_tier") val verificationTier: String? = null,
    @Json(name = "p_account_status") val accountStatus: String? = null,
    @Json(name = "p_restriction_reason") val restrictionReason: String? = null
)

data class AdminDeleteUserRpcRequest(
    @Json(name = "p_target_user_id") val targetUserId: String
)

data class AdminReviewVerificationRpcRequest(
    @Json(name = "p_request_id") val requestId: String,
    @Json(name = "p_decision") val decision: String,
    @Json(name = "p_badge_tier") val badgeTier: String = "creator",
    @Json(name = "p_notes") val notes: String = ""
)

data class AdminModerateContentRpcRequest(
    @Json(name = "p_content_type") val contentType: String,
    @Json(name = "p_content_id") val contentId: String,
    @Json(name = "p_report_id") val reportId: String? = null,
    @Json(name = "p_action") val action: String = "remove",
    @Json(name = "p_reason") val reason: String = ""
)

data class SupabaseVerificationRequestDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "requested_tier") val requestedTier: String? = "creator",
    val reason: String? = "",
    val status: String? = "pending",
    @Json(name = "review_notes") val reviewNotes: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCreateVerificationRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "requested_tier") val requestedTier: String,
    val reason: String
)

data class SupabasePostReportDto(
    val id: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "reporter_id") val reporterId: String,
    val reason: String? = "",
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseStoryDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "media_url") val mediaUrl: String? = null,
    val caption: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseAdminPermissionDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "can_manage_users") val canManageUsers: Boolean? = true,
    @Json(name = "can_manage_verifications") val canManageVerifications: Boolean? = true,
    @Json(name = "can_moderate_content") val canModerateContent: Boolean? = true,
    @Json(name = "can_manage_settings") val canManageSettings: Boolean? = false,
    @Json(name = "can_send_announcements") val canSendAnnouncements: Boolean? = false,
    @Json(name = "updated_by") val updatedBy: String? = null
)

data class SupabaseAnnouncementDto(
    val id: String,
    val title: String,
    val body: String,
    val audience: String? = "all",
    @Json(name = "is_active") val isActive: Boolean? = true,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCreateAnnouncementRequest(
    val title: String,
    val body: String,
    val audience: String = "all",
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "created_by") val createdBy: String
)

data class SupabaseToggleAnnouncementRequest(
    @Json(name = "is_active") val isActive: Boolean
)

data class SupabasePlatformSettingsDto(
    val id: String = "default",
    @Json(name = "maintenance_mode") val maintenanceMode: Boolean? = false,
    @Json(name = "maintenance_message") val maintenanceMessage: String? = null,
    @Json(name = "allow_registrations") val allowRegistrations: Boolean? = true,
    @Json(name = "allow_posting") val allowPosting: Boolean? = true,
    @Json(name = "allow_comments") val allowComments: Boolean? = true,
    @Json(name = "updated_by") val updatedBy: String? = null
)

data class SupabaseAuditLogDto(
    val id: String,
    @Json(name = "actor_id") val actorId: String? = null,
    @Json(name = "actor_email") val actorEmail: String? = null,
    val action: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String? = null,
    val details: String? = null,
    val severity: String? = "info",
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCreateAuditLogRequest(
    @Json(name = "actor_id") val actorId: String,
    @Json(name = "actor_email") val actorEmail: String,
    val action: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String? = null,
    val details: String = "",
    val severity: String = "info"
)

interface SupabaseAdminApi {
    // Server-side RPCs (strictly enforced via auth.uid() in Postgres)
    @POST("rest/v1/rpc/admin_get_dashboard_metrics")
    suspend fun getDashboardMetrics(
        @Header("Authorization") bearerToken: String,
        @Body body: Map<String, String> = emptyMap()
    ): Response<SupabaseDashboardMetricsDto>

    @POST("rest/v1/rpc/admin_update_user")
    suspend fun adminUpdateUser(
        @Header("Authorization") bearerToken: String,
        @Body request: AdminUpdateUserRpcRequest
    ): Response<Unit>

    @POST("rest/v1/rpc/admin_delete_user_account")
    suspend fun adminDeleteUser(
        @Header("Authorization") bearerToken: String,
        @Body request: AdminDeleteUserRpcRequest
    ): Response<Unit>

    @POST("rest/v1/rpc/admin_review_verification_request")
    suspend fun adminReviewVerification(
        @Header("Authorization") bearerToken: String,
        @Body request: AdminReviewVerificationRpcRequest
    ): Response<Unit>

    @POST("rest/v1/rpc/admin_moderate_content")
    suspend fun adminModerateContent(
        @Header("Authorization") bearerToken: String,
        @Body request: AdminModerateContentRpcRequest
    ): Response<Unit>

    // Users / Profiles
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_PROFILES}")
    suspend fun getAllProfiles(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 100
    ): Response<List<SupabaseProfileDto>>

    // Verification requests
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_VERIFICATION_REQUESTS}")
    suspend fun getVerificationRequests(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 60
    ): Response<List<SupabaseVerificationRequestDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_VERIFICATION_REQUESTS}")
    suspend fun createVerificationRequest(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseCreateVerificationRequest
    ): Response<List<SupabaseVerificationRequestDto>>

    // Moderation (Reports, Posts, Comments, Stories)
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_POST_REPORTS}")
    suspend fun getPostReports(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 60
    ): Response<List<SupabasePostReportDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_COMMENTS}")
    suspend fun getRecentComments(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<SupabaseCommentDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_STORIES}")
    suspend fun getStories(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<SupabaseStoryDto>>

    // Admin Permissions
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_ADMIN_PERMISSIONS}")
    suspend fun getAdminPermissions(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseAdminPermissionDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_ADMIN_PERMISSIONS}")
    suspend fun upsertAdminPermission(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Query("on_conflict") onConflict: String = "user_id",
        @Body request: SupabaseAdminPermissionDto
    ): Response<List<SupabaseAdminPermissionDto>>

    // Platform Announcements
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_PLATFORM_ANNOUNCEMENTS}")
    suspend fun getAnnouncements(
        @Header("Authorization") bearerToken: String?,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 30
    ): Response<List<SupabaseAnnouncementDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_PLATFORM_ANNOUNCEMENTS}")
    suspend fun createAnnouncement(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseCreateAnnouncementRequest
    ): Response<List<SupabaseAnnouncementDto>>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_PLATFORM_ANNOUNCEMENTS}")
    suspend fun toggleAnnouncement(
        @Header("Authorization") bearerToken: String,
        @Query("id") idEq: String,
        @Body request: SupabaseToggleAnnouncementRequest
    ): Response<Unit>

    @DELETE("rest/v1/${VybalSchemaContract.TABLE_PLATFORM_ANNOUNCEMENTS}")
    suspend fun deleteAnnouncement(
        @Header("Authorization") bearerToken: String,
        @Query("id") idEq: String
    ): Response<Unit>

    // Platform Settings
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_PLATFORM_SETTINGS}")
    suspend fun getPlatformSettings(
        @Header("Authorization") bearerToken: String?,
        @Query("id") idEq: String = "eq.default",
        @Query("limit") limit: Int = 1
    ): Response<List<SupabasePlatformSettingsDto>>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_PLATFORM_SETTINGS}")
    suspend fun updatePlatformSettings(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idEq: String = "eq.default",
        @Body request: SupabasePlatformSettingsDto
    ): Response<List<SupabasePlatformSettingsDto>>

    // Audit Logs
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_ADMIN_AUDIT_LOGS}")
    suspend fun getAuditLogs(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 60
    ): Response<List<SupabaseAuditLogDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_ADMIN_AUDIT_LOGS}")
    suspend fun insertAuditLog(
        @Header("Authorization") bearerToken: String,
        @Body request: SupabaseCreateAuditLogRequest
    ): Response<Unit>
}
