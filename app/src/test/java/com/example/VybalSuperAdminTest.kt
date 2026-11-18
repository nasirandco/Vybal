package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.example.core.config.VybalSchemaContract
import com.example.core.network.AdminDeleteUserRpcRequest
import com.example.core.network.AdminModerateContentRpcRequest
import com.example.core.network.AdminReviewVerificationRpcRequest
import com.example.core.network.AdminUpdateUserRpcRequest
import com.example.core.network.SupabaseAdminApi
import com.example.core.network.SupabaseAdminPermissionDto
import com.example.core.network.SupabaseAnnouncementDto
import com.example.core.network.SupabaseAuditLogDto
import com.example.core.network.SupabaseAuthApi
import com.example.core.network.SupabaseAuthRequest
import com.example.core.network.SupabaseAuthResponse
import com.example.core.network.SupabaseCommentDto
import com.example.core.network.SupabaseCommentLikeDto
import com.example.core.network.SupabaseCreateAnnouncementRequest
import com.example.core.network.SupabaseCreateAuditLogRequest
import com.example.core.network.SupabaseCreateCollectionRequest
import com.example.core.network.SupabaseCreateCommentRequest
import com.example.core.network.SupabaseCreateFollowRequest
import com.example.core.network.SupabaseCreateNotificationRequest
import com.example.core.network.SupabaseCreatePostCaptionRequest
import com.example.core.network.SupabaseCreatePostRequest
import com.example.core.network.SupabaseCreateProfileRequest
import com.example.core.network.SupabaseCreateRepostRequest
import com.example.core.network.SupabaseCreateVerificationRequest
import com.example.core.network.SupabaseDashboardMetricsDto
import com.example.core.network.SupabaseEngagementApi
import com.example.core.network.SupabaseFollowDto
import com.example.core.network.SupabaseHiddenPostDto
import com.example.core.network.SupabaseHiddenPostRequest
import com.example.core.network.SupabaseMarkNotificationReadRequest
import com.example.core.network.SupabaseMessageDto
import com.example.core.network.SupabaseNotificationDto
import com.example.core.network.SupabasePinCommentRequest
import com.example.core.network.SupabasePlatformSettingsDto
import com.example.core.network.SupabasePostControlPatch
import com.example.core.network.SupabasePostDto
import com.example.core.network.SupabasePostReportDto
import com.example.core.network.SupabaseProfileDto
import com.example.core.network.SupabaseProfileFullNameUpdateRequest
import com.example.core.network.SupabaseProfileUpdateRequest
import com.example.core.network.SupabaseReactionDto
import com.example.core.network.SupabaseRecoverRequest
import com.example.core.network.SupabaseRefreshRequest
import com.example.core.network.SupabaseReportPostRequest
import com.example.core.network.SupabaseRepostDto
import com.example.core.network.SupabaseRestApi
import com.example.core.network.SupabaseSavedCollectionDto
import com.example.core.network.SupabaseSavedPostDto
import com.example.core.network.SupabaseSignUpRequest
import com.example.core.network.SupabaseStoryDto
import com.example.core.network.SupabaseToggleAnnouncementRequest
import com.example.core.network.SupabaseUpdateCommentRequest
import com.example.core.network.SupabaseUpsertReactionRequest
import com.example.core.network.SupabaseUpsertSavedPostRequest
import com.example.core.network.SupabaseUserDto
import com.example.core.network.SupabaseVerificationRequestDto
import com.example.data.admin.AdminRepository
import com.example.data.auth.AuthResult
import com.example.data.auth.SessionStorage
import com.example.data.auth.SupabaseAuthRepository
import com.example.data.model.PlatformSettings
import com.example.data.model.ProfileIdentityBadge
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import com.example.ui.components.PostInteractionCallbacks
import com.example.ui.components.VybalInteractivePostCard
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.VybalTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

private class AdminTestSessionStorage(initial: UserSession? = null) : SessionStorage {
    private val _session = MutableStateFlow(initial)
    private val _dataSaver = MutableStateFlow(false)
    override val sessionFlow: Flow<UserSession?> = _session
    override val dataSaverFlow: Flow<Boolean> = _dataSaver
    override suspend fun currentSession(): UserSession? = _session.value
    override suspend fun saveSession(session: UserSession) { _session.value = session }
    override suspend fun clearSession() { _session.value = null }
    override suspend fun setDataSaver(enabled: Boolean) { _dataSaver.value = enabled }
}

private class SuperAdminFakeAuthApi : SupabaseAuthApi {
    var signUpCalled = false
    var accountAlreadyExists = false

    override suspend fun signInWithPassword(
        grantType: String,
        request: SupabaseAuthRequest
    ): Response<SupabaseAuthResponse> {
        return Response.error(
            400,
            """{"error_description":"Invalid login credentials"}"""
                .toResponseBody("application/json".toMediaType())
        )
    }

    override suspend fun signUp(request: SupabaseSignUpRequest): Response<SupabaseAuthResponse> {
        signUpCalled = true
        if (accountAlreadyExists) {
            return Response.error(
                422,
                """{"msg":"User already registered"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        }
        return Response.success(
            SupabaseAuthResponse(
                accessToken = "super-admin-jwt",
                refreshToken = "super-admin-refresh",
                expiresIn = 3600,
                user = SupabaseUserDto(id = "super-admin-uid", email = request.email)
            )
        )
    }

    override suspend fun recoverPassword(request: SupabaseRecoverRequest): Response<Unit> = Response.success(Unit)
    override suspend fun refreshSession(grantType: String, request: SupabaseRefreshRequest): Response<SupabaseAuthResponse> =
        Response.error(401, "{}".toResponseBody("application/json".toMediaType()))
    override suspend fun getCurrentUser(bearerToken: String): Response<SupabaseUserDto> =
        Response.success(SupabaseUserDto("super-admin-uid", VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL))
    override suspend fun signOut(bearerToken: String): Response<Unit> = Response.success(Unit)
}

private class FakeSupabaseAdminApi : SupabaseAdminApi {
    var lastUpdatedUserReq: AdminUpdateUserRpcRequest? = null
    var lastModerateReq: AdminModerateContentRpcRequest? = null
    var lastReviewReq: AdminReviewVerificationRpcRequest? = null
    var lastSettingsSaved: SupabasePlatformSettingsDto? = null
    val auditLogs = mutableListOf<SupabaseCreateAuditLogRequest>()

    override suspend fun getDashboardMetrics(
        bearerToken: String,
        body: Map<String, String>
    ): Response<SupabaseDashboardMetricsDto> = Response.success(
        SupabaseDashboardMetricsDto(
            totalUsers = 42,
            activeUsers = 39,
            onlineUsers = 7,
            totalPosts = 128,
            totalVideos = 14,
            totalStories = 9,
            pendingVerifications = 3,
            reportedContent = 2,
            bannedAccounts = 1,
            newUsers7d = 11,
            newPosts7d = 34
        )
    )

    override suspend fun adminUpdateUser(bearerToken: String, request: AdminUpdateUserRpcRequest): Response<Unit> {
        lastUpdatedUserReq = request
        return Response.success(Unit)
    }

    override suspend fun adminDeleteUser(bearerToken: String, request: AdminDeleteUserRpcRequest): Response<Unit> =
        Response.success(Unit)

    override suspend fun adminReviewVerification(
        bearerToken: String,
        request: AdminReviewVerificationRpcRequest
    ): Response<Unit> {
        lastReviewReq = request
        return Response.success(Unit)
    }

    override suspend fun adminModerateContent(
        bearerToken: String,
        request: AdminModerateContentRpcRequest
    ): Response<Unit> {
        lastModerateReq = request
        return Response.success(Unit)
    }

    override suspend fun getAllProfiles(bearerToken: String, select: String, limit: Int): Response<List<SupabaseProfileDto>> =
        Response.success(emptyList())

    override suspend fun getVerificationRequests(
        bearerToken: String,
        select: String,
        order: String,
        limit: Int
    ): Response<List<SupabaseVerificationRequestDto>> = Response.success(emptyList())

    override suspend fun createVerificationRequest(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreateVerificationRequest
    ): Response<List<SupabaseVerificationRequestDto>> = Response.success(
        listOf(SupabaseVerificationRequestDto("vr-1", request.userId, request.requestedTier, request.reason))
    )

    override suspend fun getPostReports(bearerToken: String, select: String, order: String, limit: Int): Response<List<SupabasePostReportDto>> =
        Response.success(emptyList())

    override suspend fun getRecentComments(bearerToken: String, select: String, order: String, limit: Int): Response<List<SupabaseCommentDto>> =
        Response.success(emptyList())

    override suspend fun getStories(bearerToken: String, select: String, order: String, limit: Int): Response<List<SupabaseStoryDto>> =
        Response.success(emptyList())

    override suspend fun getAdminPermissions(bearerToken: String, select: String): Response<List<SupabaseAdminPermissionDto>> =
        Response.success(emptyList())

    override suspend fun upsertAdminPermission(
        bearerToken: String,
        prefer: String,
        onConflict: String,
        request: SupabaseAdminPermissionDto
    ): Response<List<SupabaseAdminPermissionDto>> = Response.success(listOf(request))

    override suspend fun getAnnouncements(bearerToken: String?, select: String, order: String, limit: Int): Response<List<SupabaseAnnouncementDto>> =
        Response.success(emptyList())

    override suspend fun createAnnouncement(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreateAnnouncementRequest
    ): Response<List<SupabaseAnnouncementDto>> = Response.success(
        listOf(SupabaseAnnouncementDto("ann-1", request.title, request.body, request.audience, true))
    )

    override suspend fun toggleAnnouncement(bearerToken: String, idEq: String, request: SupabaseToggleAnnouncementRequest): Response<Unit> =
        Response.success(Unit)

    override suspend fun deleteAnnouncement(bearerToken: String, idEq: String): Response<Unit> =
        Response.success(Unit)

    override suspend fun getPlatformSettings(bearerToken: String?, idEq: String, limit: Int): Response<List<SupabasePlatformSettingsDto>> =
        Response.success(listOf(lastSettingsSaved ?: SupabasePlatformSettingsDto()))

    override suspend fun updatePlatformSettings(
        bearerToken: String,
        prefer: String,
        idEq: String,
        request: SupabasePlatformSettingsDto
    ): Response<List<SupabasePlatformSettingsDto>> {
        lastSettingsSaved = request
        return Response.success(listOf(request))
    }

    override suspend fun getAuditLogs(bearerToken: String, select: String, order: String, limit: Int): Response<List<SupabaseAuditLogDto>> =
        Response.success(emptyList())

    override suspend fun insertAuditLog(bearerToken: String, request: SupabaseCreateAuditLogRequest): Response<Unit> {
        auditLogs.add(request)
        return Response.success(Unit)
    }
}

private class StubRestApi : SupabaseRestApi {
    override suspend fun getPosts(bearerToken: String?, select: String, order: String, limit: Int, offset: Int) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun searchPosts(bearerToken: String?, select: String, contentFilter: String, order: String, limit: Int) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun searchPostsByCaption(bearerToken: String?, select: String, captionFilter: String, order: String, limit: Int) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun getUserPosts(bearerToken: String?, select: String, userIdFilter: String, order: String, limit: Int) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun createPost(bearerToken: String, prefer: String, request: SupabaseCreatePostRequest) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun createPostWithCaption(bearerToken: String, prefer: String, request: SupabaseCreatePostCaptionRequest) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun getProfile(bearerToken: String?, select: String, idFilter: String, limit: Int) = Response.success(emptyList<SupabaseProfileDto>())
    override suspend fun upsertProfile(bearerToken: String, prefer: String, request: SupabaseCreateProfileRequest) = Response.success(emptyList<SupabaseProfileDto>())
    override suspend fun updateProfile(bearerToken: String, prefer: String, idFilter: String, request: SupabaseProfileUpdateRequest) = Response.success(emptyList<SupabaseProfileDto>())
    override suspend fun updateProfileFullName(bearerToken: String, prefer: String, idFilter: String, request: SupabaseProfileFullNameUpdateRequest) = Response.success(emptyList<SupabaseProfileDto>())
    override suspend fun getNotifications(bearerToken: String, select: String, order: String, limit: Int) = Response.success(emptyList<SupabaseNotificationDto>())
    override suspend fun getMessages(bearerToken: String, select: String, order: String, limit: Int) = Response.success(emptyList<SupabaseMessageDto>())
}

private class StubEngagementApi : SupabaseEngagementApi {
    override suspend fun getReactionsForPosts(bearerToken: String?, postIdIn: String, select: String) = Response.success(emptyList<SupabaseReactionDto>())
    override suspend fun upsertReaction(bearerToken: String, prefer: String, onConflict: String, request: SupabaseUpsertReactionRequest) = Response.success(emptyList<SupabaseReactionDto>())
    override suspend fun deleteReaction(bearerToken: String, postIdEq: String, userIdEq: String) = Response.success(Unit)
    override suspend fun getComments(bearerToken: String?, postIdEq: String, select: String, order: String, limit: Int, offset: Int) = Response.success(emptyList<SupabaseCommentDto>())
    override suspend fun getCommentCountsForPosts(bearerToken: String?, postIdIn: String, select: String) = Response.success(emptyList<SupabaseCommentDto>())
    override suspend fun createComment(bearerToken: String, prefer: String, request: SupabaseCreateCommentRequest) = Response.success(emptyList<SupabaseCommentDto>())
    override suspend fun updateComment(bearerToken: String, prefer: String, idEq: String, request: SupabaseUpdateCommentRequest) = Response.success(emptyList<SupabaseCommentDto>())
    override suspend fun pinComment(bearerToken: String, prefer: String, idEq: String, request: SupabasePinCommentRequest) = Response.success(emptyList<SupabaseCommentDto>())
    override suspend fun deleteComment(bearerToken: String, idEq: String) = Response.success(Unit)
    override suspend fun getCommentLikes(bearerToken: String?, commentIdIn: String, select: String) = Response.success(emptyList<SupabaseCommentLikeDto>())
    override suspend fun likeComment(bearerToken: String, prefer: String, request: SupabaseCommentLikeDto) = Response.success(Unit)
    override suspend fun unlikeComment(bearerToken: String, commentIdEq: String, userIdEq: String) = Response.success(Unit)
    override suspend fun getCollections(bearerToken: String, userIdEq: String, order: String) = Response.success(emptyList<SupabaseSavedCollectionDto>())
    override suspend fun createCollection(bearerToken: String, prefer: String, request: SupabaseCreateCollectionRequest) = Response.success(emptyList<SupabaseSavedCollectionDto>())
    override suspend fun getSavedPosts(bearerToken: String, userIdEq: String, order: String) = Response.success(emptyList<SupabaseSavedPostDto>())
    override suspend fun upsertSavedPost(bearerToken: String, prefer: String, onConflict: String, request: SupabaseUpsertSavedPostRequest) = Response.success(emptyList<SupabaseSavedPostDto>())
    override suspend fun deleteSavedPost(bearerToken: String, userIdEq: String, postIdEq: String) = Response.success(Unit)
    override suspend fun getRepostsForPosts(bearerToken: String?, postIdIn: String, select: String) = Response.success(emptyList<SupabaseRepostDto>())
    override suspend fun createRepost(bearerToken: String, prefer: String, onConflict: String, request: SupabaseCreateRepostRequest) = Response.success(emptyList<SupabaseRepostDto>())
    override suspend fun deleteRepost(bearerToken: String, userIdEq: String, postIdEq: String) = Response.success(Unit)
    override suspend fun getFollowers(bearerToken: String?, followingIdEq: String, select: String) = Response.success(emptyList<SupabaseFollowDto>())
    override suspend fun getFollowing(bearerToken: String?, followerIdEq: String, select: String) = Response.success(emptyList<SupabaseFollowDto>())
    override suspend fun followUser(bearerToken: String, prefer: String, request: SupabaseCreateFollowRequest) = Response.success(Unit)
    override suspend fun unfollowUser(bearerToken: String, followerIdEq: String, followingIdEq: String) = Response.success(Unit)
    override suspend fun getProfilesByIds(bearerToken: String?, idIn: String, select: String) = Response.success(emptyList<SupabaseProfileDto>())
    override suspend fun getProfileByUsername(bearerToken: String?, usernameEq: String, limit: Int) = Response.success(emptyList<SupabaseProfileDto>())
    override suspend fun getPostsByIds(bearerToken: String?, idIn: String, select: String) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun patchPostControls(bearerToken: String, prefer: String, idEq: String, patch: SupabasePostControlPatch) = Response.success(emptyList<SupabasePostDto>())
    override suspend fun getHiddenPosts(bearerToken: String, userIdEq: String, select: String) = Response.success(emptyList<SupabaseHiddenPostDto>())
    override suspend fun hidePost(bearerToken: String, prefer: String, request: SupabaseHiddenPostRequest) = Response.success(Unit)
    override suspend fun reportPost(bearerToken: String, prefer: String, request: SupabaseReportPostRequest) = Response.success(Unit)
    override suspend fun createNotification(bearerToken: String, request: SupabaseCreateNotificationRequest) = Response.success(Unit)
    override suspend fun markNotificationRead(bearerToken: String, idEq: String, request: SupabaseMarkNotificationReadRequest) = Response.success(Unit)
    override suspend fun markAllNotificationsRead(bearerToken: String, isReadEq: String, request: SupabaseMarkNotificationReadRequest) = Response.success(Unit)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VybalSuperAdminTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val superAdminSession = UserSession(
        accessToken = "super-jwt",
        refreshToken = "super-ref",
        userId = "super-admin-uid",
        email = VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL
    )

    @Test
    fun `permanent Super Admin automatically signs in on first time without manual sign up`() = runTest {
        val storage = AdminTestSessionStorage()
        val authApi = SuperAdminFakeAuthApi()
        val authRepo = SupabaseAuthRepository(
            authApi = authApi,
            sessionStore = storage,
            isConfiguredProvider = { true }
        )

        val result = authRepo.signIn(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, "MySecurePass123!")
        assertTrue(result is AuthResult.Authenticated)
        assertTrue(authApi.signUpCalled)
        assertEquals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, storage.currentSession()?.email)
    }

    @Test
    fun `permanent Super Admin preserves password check when account already exists`() = runTest {
        val storage = AdminTestSessionStorage()
        val authApi = SuperAdminFakeAuthApi().apply { accountAlreadyExists = true }
        val authRepo = SupabaseAuthRepository(
            authApi = authApi,
            sessionStore = storage,
            isConfiguredProvider = { true }
        )

        val result = authRepo.signIn(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, "WrongPassword")
        assertTrue(result is AuthResult.Failure)
        assertEquals("Incorrect email or password. Please try again.", (result as AuthResult.Failure).message)
    }

    @Test
    fun `regular user never triggers Super Admin first-time auto sign-in`() = runTest {
        val storage = AdminTestSessionStorage()
        val authApi = SuperAdminFakeAuthApi()
        val authRepo = SupabaseAuthRepository(
            authApi = authApi,
            sessionStore = storage,
            isConfiguredProvider = { true }
        )

        val result = authRepo.signIn("regular@vybal.app", "SomePass123")
        assertTrue(result is AuthResult.Failure)
        assertFalse(authApi.signUpCalled)
    }

    @Test
    fun `Super Admin protection blocks ban demotion deletion and role spoofing`() = runTest {
        val adminApi = FakeSupabaseAdminApi()
        val repo = AdminRepository(
            adminApi = adminApi,
            restApi = StubRestApi(),
            engagementApi = StubEngagementApi(),
            sessionStore = AdminTestSessionStorage(superAdminSession),
            isConfiguredProvider = { true }
        )

        // Cannot ban Super Admin
        val banSuper = repo.updateUser(
            targetUserId = "super-admin-uid",
            targetEmail = VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
            accountStatus = "banned"
        )
        assertTrue(banSuper is ResourceState.Error)

        // Cannot delete Super Admin
        val delSuper = repo.deleteUserAccount("super-admin-uid", VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL)
        assertTrue(delSuper is ResourceState.Error)

        // Cannot grant super_admin to another email
        val spoofRole = repo.updateUser(
            targetUserId = "other-uid",
            targetEmail = "other@vybal.app",
            role = "super_admin"
        )
        assertTrue(spoofRole is ResourceState.Error)

        // Regular admin cannot assign staff roles
        val nonSuperRepo = AdminRepository(
            adminApi = adminApi,
            restApi = StubRestApi(),
            engagementApi = StubEngagementApi(),
            sessionStore = AdminTestSessionStorage(
                UserSession("jwt", "ref", "admin-2", "admin2@vybal.app")
            ),
            isConfiguredProvider = { true }
        )
        val nonSuperPromote = nonSuperRepo.updateUser("u-3", "u3@vybal.app", role = "admin")
        assertTrue(nonSuperPromote is ResourceState.Error)
    }

    @Test
    fun `dashboard metrics verification moderation and platform settings work via AdminRepository`() = runTest {
        val adminApi = FakeSupabaseAdminApi()
        val repo = AdminRepository(
            adminApi = adminApi,
            restApi = StubRestApi(),
            engagementApi = StubEngagementApi(),
            sessionStore = AdminTestSessionStorage(superAdminSession),
            isConfiguredProvider = { true }
        )

        val metrics = repo.fetchDashboardMetrics()
        assertTrue(metrics is ResourceState.Success)
        assertEquals(42, (metrics as ResourceState.Success).data.totalUsers)
        assertEquals(3, metrics.data.pendingVerifications)

        val review = repo.reviewVerificationRequest("vr-9", "approved", "business", "Verified partner")
        assertTrue(review is ResourceState.Success)
        assertEquals("approved", adminApi.lastReviewReq?.decision)

        val mod = repo.moderateContent("post", "post-99", "rep-1", "remove", "Spam")
        assertTrue(mod is ResourceState.Success)
        assertEquals("remove", adminApi.lastModerateReq?.action)

        val settingsRes = repo.updatePlatformSettings(
            PlatformSettings(maintenanceMode = true, maintenanceMessage = "Scheduled upgrade")
        )
        assertTrue(settingsRes is ResourceState.Success)
        assertEquals(true, adminApi.lastSettingsSaved?.maintenanceMode)
        assertTrue(adminApi.auditLogs.any { it.action == "update_platform_settings" && it.severity == "critical" })
    }

    @Test
    fun `ProfileScreen and PostCard display purple Verified and Super Admin badges`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader(context)
        val superProfile = VybalProfile(
            id = "super-admin-uid",
            username = "vybal_super_admin",
            displayName = "VYBAL Super Admin",
            bio = "Platform Founder",
            avatarUrl = null,
            email = VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
            role = "super_admin",
            isVerified = true,
            verificationTier = "super_admin"
        )

        composeTestRule.setContent {
            VybalTheme {
                ProfileScreen(
                    session = superAdminSession,
                    isSessionLoading = false,
                    isSigningOut = false,
                    profileState = ResourceState.Success(superProfile),
                    userPostsState = ResourceState.Success(emptyList()),
                    followStats = null,
                    savedCollections = emptyList(),
                    savedEntries = emptyList(),
                    savedPosts = emptyList(),
                    isSupabaseConfigured = true,
                    dataSaverEnabled = false,
                    imageLoader = imageLoader,
                    interactions = PostInteractionCallbacks(
                        summaries = emptyMap(),
                        usernames = emptyMap(),
                        hiddenPostIds = emptySet(),
                        onAuthorClick = {},
                        onMentionClick = {},
                        onHashtagClick = {},
                        onReactionSelect = { _, _, _ -> },
                        onCommentClick = {},
                        onRepostClick = {},
                        onShareClick = {},
                        onQuickSaveClick = {},
                        onSaveCollectionClick = {},
                        onOptionsClick = {}
                    ),
                    onToggleDataSaver = {},
                    onRefresh = {},
                    onOpenAuth = {},
                    onSignOut = {},
                    onUpdateProfile = { _, _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vybal_super_admin_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vybal_verified_badge_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_super_admin_dashboard_button").assertIsDisplayed()
    }

    @Test
    fun `VybalInteractivePostCard renders Super Admin and Verified badges on posts`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader(context)
        val post = VybalPost(
            id = "sa-post-1",
            userId = "super-admin-uid",
            text = "Welcome to VYBAL",
            mediaUrl = null,
            visibility = "public",
            createdAt = "2026-11-18T12:00:00Z"
        )

        composeTestRule.setContent {
            VybalTheme {
                VybalInteractivePostCard(
                    post = post,
                    authorHandle = "vybal_super_admin",
                    authorBadge = ProfileIdentityBadge(
                        userId = "super-admin-uid",
                        username = "vybal_super_admin",
                        role = "super_admin",
                        isVerified = true,
                        verificationTier = "super_admin"
                    ),
                    summary = null,
                    imageLoader = imageLoader,
                    dataSaverEnabled = false,
                    onAuthorClick = {},
                    onMentionClick = {},
                    onHashtagClick = {},
                    onReactionSelect = { _, _ -> },
                    onCommentClick = {},
                    onRepostClick = {},
                    onShareClick = {},
                    onQuickSaveClick = {},
                    onSaveCollectionClick = {},
                    onOptionsClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vybal_verified_badge_icon", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("vybal_super_admin_badge", useUnmergedTree = true).assertIsDisplayed()
    }
}
