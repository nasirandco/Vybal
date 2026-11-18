package com.example.data.admin

import com.example.core.config.VybalSchemaContract
import com.example.core.config.VybalSupabaseConfig
import com.example.core.network.AdminDeleteUserRpcRequest
import com.example.core.network.AdminModerateContentRpcRequest
import com.example.core.network.AdminReviewVerificationRpcRequest
import com.example.core.network.AdminUpdateUserRpcRequest
import com.example.core.network.SupabaseAdminApi
import com.example.core.network.SupabaseAdminPermissionDto
import com.example.core.network.SupabaseClientBundle
import com.example.core.network.SupabaseCreateAnnouncementRequest
import com.example.core.network.SupabaseCreateAuditLogRequest
import com.example.core.network.SupabaseCreateNotificationRequest
import com.example.core.network.SupabaseCreateVerificationRequest
import com.example.core.network.SupabaseEngagementApi
import com.example.core.network.SupabaseErrorDto
import com.example.core.network.SupabasePlatformSettingsDto
import com.example.core.network.SupabaseProfileDto
import com.example.core.network.SupabaseRestApi
import com.example.core.network.SupabaseToggleAnnouncementRequest
import com.example.data.auth.SessionStorage
import com.example.data.model.AdminDashboardMetrics
import com.example.data.model.AdminPermissionRecord
import com.example.data.model.AdminStoryRecord
import com.example.data.model.AuditLogEntry
import com.example.data.model.ModerationReportRecord
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.PlatformSettings
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.model.VerificationRequestRecord
import com.example.data.model.VybalComment
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class AdminRepository(
    private val adminApi: SupabaseAdminApi,
    private val restApi: SupabaseRestApi,
    private val engagementApi: SupabaseEngagementApi,
    private val sessionStore: SessionStorage,
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
    private val isConfiguredProvider: () -> Boolean = { VybalSupabaseConfig.isConfigured },
    private val validSessionProvider: suspend (Boolean) -> UserSession? = { sessionStore.currentSession() }
) {
    constructor(
        clients: SupabaseClientBundle,
        sessionStore: SessionStorage,
        validSessionProvider: suspend (Boolean) -> UserSession? = { sessionStore.currentSession() }
    ) : this(
        adminApi = clients.adminApi,
        restApi = clients.restApi,
        engagementApi = clients.engagementApi,
        sessionStore = sessionStore,
        moshi = clients.moshi,
        validSessionProvider = validSessionProvider
    )

    private suspend fun requireBearer(): Pair<UserSession, String>? {
        val session = validSessionProvider(false) ?: return null
        return session to "Bearer ${session.accessToken}"
    }

    // -------------------------------------------------------------------------
    // 1. Dashboard Overview Metrics (Server-side RPC)
    // -------------------------------------------------------------------------
    suspend fun fetchDashboardMetrics(): ResourceState<AdminDashboardMetrics> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.getDashboardMetrics(bearer)
                if (resp.isSuccessful) {
                    val body = resp.body()
                    ResourceState.Success(
                        AdminDashboardMetrics(
                            totalUsers = body?.totalUsers ?: 0,
                            activeUsers = body?.activeUsers ?: 0,
                            onlineUsers = body?.onlineUsers ?: 0,
                            totalPosts = body?.totalPosts ?: 0,
                            totalVideos = body?.totalVideos ?: 0,
                            totalStories = body?.totalStories ?: 0,
                            pendingVerifications = body?.pendingVerifications ?: 0,
                            reportedContent = body?.reportedContent ?: 0,
                            bannedAccounts = body?.bannedAccounts ?: 0,
                            newUsers7d = body?.newUsers7d ?: 0,
                            newPosts7d = body?.newPosts7d ?: 0
                        )
                    )
                } else if (resp.code() == 401) {
                    ResourceState.AuthRequired
                } else {
                    ResourceState.Error(parseError(resp, "admin_get_dashboard_metrics"))
                }
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    // -------------------------------------------------------------------------
    // 2. User Management & Role Governance (Server-side RPC enforced)
    // -------------------------------------------------------------------------
    suspend fun fetchAllUsers(): ResourceState<List<VybalProfile>> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = adminApi.getAllProfiles(bearer)
            if (resp.isSuccessful) {
                ResourceState.Success(resp.body().orEmpty().map { it.toDomain() })
            } else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "profiles"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun updateUser(
        targetUserId: String,
        targetEmail: String,
        displayName: String? = null,
        bio: String? = null,
        role: String? = null,
        isVerified: Boolean? = null,
        verificationTier: String? = null,
        accountStatus: String? = null,
        restrictionReason: String? = null
    ): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val (session, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired

        val targetIsSuperAdmin = targetEmail.equals(
            VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
            ignoreCase = true
        )
        if (targetIsSuperAdmin &&
            (accountStatus == "banned" || accountStatus == "suspended" || (role != null && role != "super_admin") || isVerified == false)
        ) {
            return@withContext ResourceState.Error(
                "Protected Super Admin account cannot be banned, suspended, demoted, or unverified."
            )
        }
        if (role == "super_admin" && !targetIsSuperAdmin) {
            return@withContext ResourceState.Error(
                "Only the designated permanent Super Admin account can hold the super_admin role."
            )
        }
        if (role != null && !session.email.equals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, ignoreCase = true)) {
            return@withContext ResourceState.Error(
                "Only the Super Admin can assign or remove administrator roles."
            )
        }

        runCatching {
            val resp = adminApi.adminUpdateUser(
                bearerToken = bearer,
                request = AdminUpdateUserRpcRequest(
                    targetUserId = targetUserId,
                    displayName = displayName,
                    bio = bio,
                    role = role,
                    isVerified = isVerified,
                    verificationTier = verificationTier,
                    accountStatus = accountStatus,
                    restrictionReason = restrictionReason
                )
            )
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "admin_update_user"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun deleteUserAccount(targetUserId: String, targetEmail: String): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            if (targetEmail.equals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, ignoreCase = true)) {
                return@withContext ResourceState.Error("Protected Super Admin account cannot be deleted.")
            }
            runCatching {
                val resp = adminApi.adminDeleteUser(bearer, AdminDeleteUserRpcRequest(targetUserId))
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "admin_delete_user_account"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    // -------------------------------------------------------------------------
    // 3. Verification Centre
    // -------------------------------------------------------------------------
    suspend fun fetchVerificationRequests(): ResourceState<List<VerificationRequestRecord>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.getVerificationRequests(bearer)
                if (!resp.isSuccessful) {
                    return@withContext if (resp.code() == 401) ResourceState.AuthRequired
                    else ResourceState.Error(parseError(resp, "verification_requests"))
                }
                val dtos = resp.body().orEmpty()
                val profilesMap = runCatching {
                    val pResp = adminApi.getAllProfiles(bearer)
                    if (pResp.isSuccessful) pResp.body().orEmpty().associateBy { it.id } else emptyMap()
                }.getOrDefault(emptyMap())

                ResourceState.Success(
                    dtos.map { dto ->
                        val profile = profilesMap[dto.userId]
                        VerificationRequestRecord(
                            id = dto.id,
                            userId = dto.userId,
                            username = profile?.username.orEmpty(),
                            displayName = (profile?.displayName ?: profile?.fullName).orEmpty(),
                            requestedTier = dto.requestedTier ?: "creator",
                            reason = dto.reason.orEmpty(),
                            status = dto.status ?: "pending",
                            reviewNotes = dto.reviewNotes,
                            createdAt = dto.createdAt.orEmpty()
                        )
                    }
                )
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun submitVerificationRequest(tier: String, reason: String): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (session, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            val cleanReason = reason.trim()
            if (cleanReason.length < 8) {
                return@withContext ResourceState.Error("Please provide a brief reason (at least 8 characters).")
            }
            runCatching {
                val resp = adminApi.createVerificationRequest(
                    bearerToken = bearer,
                    request = SupabaseCreateVerificationRequest(
                        userId = session.userId,
                        requestedTier = tier,
                        reason = cleanReason
                    )
                )
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "verification_requests"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun reviewVerificationRequest(
        requestId: String,
        decision: String,
        badgeTier: String,
        notes: String
    ): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = adminApi.adminReviewVerification(
                bearerToken = bearer,
                request = AdminReviewVerificationRpcRequest(
                    requestId = requestId,
                    decision = decision,
                    badgeTier = badgeTier,
                    notes = notes.trim()
                )
            )
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "admin_review_verification_request"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    // -------------------------------------------------------------------------
    // 4. Content Moderation (Reports, Posts/Videos, Comments, Stories)
    // -------------------------------------------------------------------------
    suspend fun fetchPostReports(): ResourceState<List<ModerationReportRecord>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.getPostReports(bearer)
                if (resp.isSuccessful) {
                    ResourceState.Success(
                        resp.body().orEmpty().map {
                            ModerationReportRecord(
                                id = it.id,
                                postId = it.postId,
                                reporterId = it.reporterId,
                                reason = it.reason.orEmpty(),
                                createdAt = it.createdAt.orEmpty()
                            )
                        }
                    )
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "post_reports"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun fetchAllPostsForModeration(): ResourceState<List<VybalPost>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = restApi.getPosts(bearerToken = bearer, limit = 50)
                if (resp.isSuccessful) {
                    ResourceState.Success(
                        resp.body().orEmpty().map { dto ->
                            VybalPost(
                                id = dto.id,
                                userId = (dto.userId ?: dto.authorId).orEmpty(),
                                text = (dto.content ?: dto.caption).orEmpty(),
                                mediaUrl = dto.mediaUrl ?: dto.imageUrl,
                                visibility = "public",
                                createdAt = dto.createdAt.orEmpty(),
                                isPinned = dto.isPinned ?: false,
                                commentsDisabled = dto.commentsDisabled ?: false,
                                shareCount = dto.shareCount ?: 0,
                                viewCount = dto.viewCount
                            )
                        }
                    )
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "posts"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun fetchRecentCommentsForModeration(): ResourceState<List<VybalComment>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.getRecentComments(bearer)
                if (resp.isSuccessful) {
                    ResourceState.Success(
                        resp.body().orEmpty().map { dto ->
                            VybalComment(
                                id = dto.id,
                                postId = dto.postId,
                                userId = dto.userId,
                                authorUsername = dto.userId.take(8),
                                parentCommentId = dto.parentCommentId,
                                content = dto.content,
                                isPinned = dto.isPinned ?: false,
                                createdAt = dto.createdAt.orEmpty()
                            )
                        }
                    )
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "comments"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun fetchStoriesForModeration(): ResourceState<List<AdminStoryRecord>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.getStories(bearer)
                if (resp.isSuccessful) {
                    ResourceState.Success(
                        resp.body().orEmpty().map {
                            AdminStoryRecord(
                                id = it.id,
                                userId = it.userId,
                                mediaUrl = it.mediaUrl,
                                caption = it.caption.orEmpty(),
                                createdAt = it.createdAt.orEmpty()
                            )
                        }
                    )
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "stories"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun moderateContent(
        contentType: String,
        contentId: String,
        reportId: String? = null,
        action: String = "remove",
        reason: String = ""
    ): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = adminApi.adminModerateContent(
                bearerToken = bearer,
                request = AdminModerateContentRpcRequest(
                    contentType = contentType,
                    contentId = contentId,
                    reportId = reportId,
                    action = action,
                    reason = reason
                )
            )
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "admin_moderate_content"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    // -------------------------------------------------------------------------
    // 5. Administrator Permissions
    // -------------------------------------------------------------------------
    suspend fun fetchAdminPermissions(): ResourceState<Map<String, AdminPermissionRecord>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.getAdminPermissions(bearer)
                if (resp.isSuccessful) {
                    ResourceState.Success(
                        resp.body().orEmpty().associate { dto ->
                            dto.userId to AdminPermissionRecord(
                                userId = dto.userId,
                                canManageUsers = dto.canManageUsers ?: true,
                                canManageVerifications = dto.canManageVerifications ?: true,
                                canModerateContent = dto.canModerateContent ?: true,
                                canManageSettings = dto.canManageSettings ?: false,
                                canSendAnnouncements = dto.canSendAnnouncements ?: false
                            )
                        }
                    )
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "admin_permissions"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun saveAdminPermissions(record: AdminPermissionRecord): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (session, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.upsertAdminPermission(
                    bearerToken = bearer,
                    request = SupabaseAdminPermissionDto(
                        userId = record.userId,
                        canManageUsers = record.canManageUsers,
                        canManageVerifications = record.canManageVerifications,
                        canModerateContent = record.canModerateContent,
                        canManageSettings = record.canManageSettings,
                        canSendAnnouncements = record.canSendAnnouncements,
                        updatedBy = session.userId
                    )
                )
                if (resp.isSuccessful) {
                    logAuditSilently(
                        bearer = bearer,
                        session = session,
                        action = "update_admin_permissions",
                        targetType = "admin_permissions",
                        targetId = record.userId,
                        details = "Updated granular staff permissions",
                        severity = "warning"
                    )
                    ResourceState.Success(Unit)
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "admin_permissions"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    // -------------------------------------------------------------------------
    // 6. Platform Control: Announcements, Broadcasts, Settings, Audit Logs
    // -------------------------------------------------------------------------
    suspend fun fetchAnnouncements(): ResourceState<List<PlatformAnnouncement>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val bearer = validSessionProvider(false)?.accessToken?.let { "Bearer $it" }
            runCatching {
                val resp = adminApi.getAnnouncements(bearer)
                if (resp.isSuccessful) {
                    ResourceState.Success(
                        resp.body().orEmpty().map {
                            PlatformAnnouncement(
                                id = it.id,
                                title = it.title,
                                body = it.body,
                                audience = it.audience ?: "all",
                                isActive = it.isActive ?: true,
                                createdAt = it.createdAt.orEmpty()
                            )
                        }
                    )
                } else {
                    ResourceState.Error(parseError(resp, "platform_announcements"))
                }
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun createAnnouncement(
        title: String,
        body: String,
        audience: String = "all"
    ): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val (session, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
        if (title.isBlank() || body.isBlank()) {
            return@withContext ResourceState.Error("Title and message body are required.")
        }
        runCatching {
            val resp = adminApi.createAnnouncement(
                bearerToken = bearer,
                request = SupabaseCreateAnnouncementRequest(
                    title = title.trim(),
                    body = body.trim(),
                    audience = audience,
                    isActive = true,
                    createdBy = session.userId
                )
            )
            if (resp.isSuccessful) {
                logAuditSilently(
                    bearer = bearer,
                    session = session,
                    action = "create_announcement",
                    targetType = "announcement",
                    targetId = resp.body()?.firstOrNull()?.id,
                    details = "${title.trim()} ($audience)"
                )
                ResourceState.Success(Unit)
            } else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "platform_announcements"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun toggleAnnouncement(id: String, active: Boolean): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.toggleAnnouncement(bearer, "eq.$id", SupabaseToggleAnnouncementRequest(active))
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else ResourceState.Error(parseError(resp, "platform_announcements"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun deleteAnnouncement(id: String): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.deleteAnnouncement(bearer, "eq.$id")
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else ResourceState.Error(parseError(resp, "platform_announcements"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun sendBroadcastNotification(
        targetUserIds: List<String>,
        message: String
    ): ResourceState<Int> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val (session, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
        val clean = message.trim()
        if (clean.isEmpty()) return@withContext ResourceState.Error("Notification message cannot be empty.")
        var sentCount = 0
        targetUserIds.distinct().forEach { uid ->
            val ok = runCatching {
                engagementApi.createNotification(
                    bearerToken = bearer,
                    request = SupabaseCreateNotificationRequest(
                        userId = uid,
                        actorId = session.userId,
                        postId = null,
                        type = "system",
                        message = clean
                    )
                ).isSuccessful
            }.getOrDefault(false)
            if (ok) sentCount++
        }
        logAuditSilently(
            bearer = bearer,
            session = session,
            action = "broadcast_notification",
            targetType = "notifications",
            targetId = null,
            details = "Sent to $sentCount users: $clean"
        )
        ResourceState.Success(sentCount)
    }

    suspend fun fetchPlatformSettings(): ResourceState<PlatformSettings> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val bearer = validSessionProvider(false)?.accessToken?.let { "Bearer $it" }
            runCatching {
                val resp = adminApi.getPlatformSettings(bearer)
                if (resp.isSuccessful) {
                    val dto = resp.body()?.firstOrNull()
                    ResourceState.Success(
                        PlatformSettings(
                            maintenanceMode = dto?.maintenanceMode ?: false,
                            maintenanceMessage = dto?.maintenanceMessage
                                ?: "VYBAL is undergoing scheduled maintenance.",
                            allowRegistrations = dto?.allowRegistrations ?: true,
                            allowPosting = dto?.allowPosting ?: true,
                            allowComments = dto?.allowComments ?: true
                        )
                    )
                } else {
                    ResourceState.Error(parseError(resp, "platform_settings"))
                }
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun updatePlatformSettings(settings: PlatformSettings): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val (session, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = adminApi.updatePlatformSettings(
                    bearerToken = bearer,
                    request = SupabasePlatformSettingsDto(
                        id = "default",
                        maintenanceMode = settings.maintenanceMode,
                        maintenanceMessage = settings.maintenanceMessage.trim(),
                        allowRegistrations = settings.allowRegistrations,
                        allowPosting = settings.allowPosting,
                        allowComments = settings.allowComments,
                        updatedBy = session.userId
                    )
                )
                if (resp.isSuccessful) {
                    logAuditSilently(
                        bearer = bearer,
                        session = session,
                        action = "update_platform_settings",
                        targetType = "platform_settings",
                        targetId = "default",
                        details = "maintenance=${settings.maintenanceMode}, reg=${settings.allowRegistrations}, post=${settings.allowPosting}, comments=${settings.allowComments}",
                        severity = if (settings.maintenanceMode) "critical" else "warning"
                    )
                    ResourceState.Success(Unit)
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "platform_settings"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun fetchAuditLogs(): ResourceState<List<AuditLogEntry>> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val (_, bearer) = requireBearer() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = adminApi.getAuditLogs(bearer)
            if (resp.isSuccessful) {
                ResourceState.Success(
                    resp.body().orEmpty().map {
                        AuditLogEntry(
                            id = it.id,
                            actorId = it.actorId,
                            actorEmail = it.actorEmail.orEmpty(),
                            action = it.action,
                            targetType = it.targetType,
                            targetId = it.targetId,
                            details = it.details.orEmpty(),
                            severity = it.severity ?: "info",
                            createdAt = it.createdAt.orEmpty()
                        )
                    }
                )
            } else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "admin_audit_logs"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    private suspend fun logAuditSilently(
        bearer: String,
        session: UserSession,
        action: String,
        targetType: String,
        targetId: String?,
        details: String,
        severity: String = "info"
    ) {
        runCatching {
            adminApi.insertAuditLog(
                bearerToken = bearer,
                request = SupabaseCreateAuditLogRequest(
                    actorId = session.userId,
                    actorEmail = session.email,
                    action = action,
                    targetType = targetType,
                    targetId = targetId,
                    details = details,
                    severity = severity
                )
            )
        }
    }

    private fun SupabaseProfileDto.toDomain(): VybalProfile = VybalProfile(
        id = id,
        username = username.orEmpty(),
        displayName = (displayName ?: fullName).orEmpty(),
        bio = bio.orEmpty(),
        avatarUrl = avatarUrl,
        email = email.orEmpty(),
        role = role ?: "user",
        isVerified = isVerified ?: false,
        verificationTier = verificationTier,
        accountStatus = accountStatus ?: "active",
        restrictionReason = restrictionReason,
        lastSeenAt = lastSeenAt
    )

    private fun mapNetworkError(error: Throwable): String =
        if (error is IOException) "Network unavailable. Check your internet connection."
        else error.localizedMessage ?: "Admin operation failed."

    private fun parseError(response: Response<*>, resourceHint: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        if (response.code() == 404 ||
            raw.contains("PGRST205", ignoreCase = true) ||
            raw.contains("PGRST204", ignoreCase = true) ||
            raw.contains("PGRST202", ignoreCase = true)
        ) {
            return "Supabase object '$resourceHint' requires running super_admin_dashboard.sql in SQL Editor."
        }
        if (response.code() == 403 || raw.contains("42501")) {
            return "Permission denied by Supabase Row Level Security policy."
        }
        val parsed = runCatching { moshi.adapter(SupabaseErrorDto::class.java).fromJson(raw) }.getOrNull()
        return parsed?.message ?: parsed?.msg ?: parsed?.error ?: "Admin request failed (${response.code()})"
    }
}
