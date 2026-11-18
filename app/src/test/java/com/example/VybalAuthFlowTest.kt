package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.example.core.network.SupabaseAuthApi
import com.example.core.network.SupabaseAuthRequest
import com.example.core.network.SupabaseAuthResponse
import com.example.core.network.SupabaseCreatePostCaptionRequest
import com.example.core.network.SupabaseCreatePostRequest
import com.example.core.network.SupabaseCreateProfileRequest
import com.example.core.network.SupabaseMessageDto
import com.example.core.network.SupabaseNotificationDto
import com.example.core.network.SupabasePostDto
import com.example.core.network.SupabaseProfileDto
import com.example.core.network.SupabaseProfileFullNameUpdateRequest
import com.example.core.network.SupabaseProfileUpdateRequest
import com.example.core.network.SupabaseRecoverRequest
import com.example.core.network.SupabaseRefreshRequest
import com.example.core.network.SupabaseRestApi
import com.example.core.network.SupabaseSignUpRequest
import com.example.core.network.SupabaseStorageApi
import com.example.core.network.SupabaseUserDto
import com.example.data.auth.AuthResult
import com.example.data.auth.AuthValidator
import com.example.data.auth.PasswordResetResult
import com.example.data.auth.SessionStorage
import com.example.data.auth.SupabaseAuthRepository
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.social.VybalRepository
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.state.CreatePostUiState
import com.example.ui.theme.VybalTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

private class InMemorySessionStorage : SessionStorage {
    private val _session = MutableStateFlow<UserSession?>(null)
    private val _dataSaver = MutableStateFlow(false)

    override val sessionFlow: Flow<UserSession?> = _session
    override val dataSaverFlow: Flow<Boolean> = _dataSaver

    override suspend fun currentSession(): UserSession? = _session.value
    override suspend fun saveSession(session: UserSession) {
        _session.value = session
    }
    override suspend fun clearSession() {
        _session.value = null
    }
    override suspend fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
    }
}

private class FakeSupabaseAuthApi : SupabaseAuthApi {
    var lastSignUpRequest: SupabaseSignUpRequest? = null
    var lastRecoverRequest: SupabaseRecoverRequest? = null
    var signOutCalled = false
    var signInResponse: Response<SupabaseAuthResponse> = Response.success(
        SupabaseAuthResponse(
            accessToken = "access-jwt-123",
            refreshToken = "refresh-jwt-123",
            expiresIn = 3600,
            user = SupabaseUserDto(id = "user-001", email = "member@vybal.app")
        )
    )
    var signUpResponse: Response<SupabaseAuthResponse> = Response.success(
        SupabaseAuthResponse(
            accessToken = "signup-jwt-456",
            refreshToken = "signup-refresh-456",
            expiresIn = 3600,
            user = SupabaseUserDto(id = "user-002", email = "new@vybal.app")
        )
    )
    var recoverResponse: Response<Unit> = Response.success(Unit)
    var refreshResponse: Response<SupabaseAuthResponse> = Response.success(
        SupabaseAuthResponse(
            accessToken = "refreshed-jwt-789",
            refreshToken = "refreshed-refresh-789",
            expiresIn = 3600,
            user = SupabaseUserDto(id = "user-001", email = "member@vybal.app")
        )
    )
    var currentUserResponse: Response<SupabaseUserDto> = Response.success(
        SupabaseUserDto(id = "user-001", email = "member@vybal.app")
    )

    override suspend fun signInWithPassword(
        grantType: String,
        request: SupabaseAuthRequest
    ): Response<SupabaseAuthResponse> = signInResponse

    override suspend fun signUp(request: SupabaseSignUpRequest): Response<SupabaseAuthResponse> {
        lastSignUpRequest = request
        return signUpResponse
    }

    override suspend fun recoverPassword(request: SupabaseRecoverRequest): Response<Unit> {
        lastRecoverRequest = request
        return recoverResponse
    }

    override suspend fun refreshSession(
        grantType: String,
        request: SupabaseRefreshRequest
    ): Response<SupabaseAuthResponse> = refreshResponse

    override suspend fun getCurrentUser(bearerToken: String): Response<SupabaseUserDto> =
        currentUserResponse

    override suspend fun signOut(bearerToken: String): Response<Unit> {
        signOutCalled = true
        return Response.success(Unit)
    }
}

private class FakeSupabaseRestApi : SupabaseRestApi {
    var existingProfile: SupabaseProfileDto? = null
    var upsertCalled = false
    var useCaptionColumn = false
    var forceRlsForbidden = false
    var lastCreatedMediaUrl: String? = null

    override suspend fun getPosts(
        bearerToken: String?,
        select: String,
        order: String,
        limit: Int,
        offset: Int
    ): Response<List<SupabasePostDto>> = Response.success(emptyList())

    override suspend fun searchPosts(
        bearerToken: String?,
        select: String,
        contentFilter: String,
        order: String,
        limit: Int
    ): Response<List<SupabasePostDto>> = Response.success(emptyList())

    override suspend fun searchPostsByCaption(
        bearerToken: String?,
        select: String,
        captionFilter: String,
        order: String,
        limit: Int
    ): Response<List<SupabasePostDto>> = Response.success(emptyList())

    override suspend fun getUserPosts(
        bearerToken: String?,
        select: String,
        userIdFilter: String,
        order: String,
        limit: Int
    ): Response<List<SupabasePostDto>> = Response.success(emptyList())

    override suspend fun createPost(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreatePostRequest
    ): Response<List<SupabasePostDto>> {
        if (forceRlsForbidden) {
            return Response.error(
                403,
                """{"code":"42501","message":"new row violates row-level security policy"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        }
        if (useCaptionColumn) {
            return Response.error(
                400,
                """{"code":"PGRST204","message":"Could not find the 'content' column of 'posts'"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        }
        lastCreatedMediaUrl = request.mediaUrl
        return Response.success(
            listOf(
                SupabasePostDto(
                    id = "post-001",
                    userId = request.userId,
                    content = request.content,
                    mediaUrl = request.mediaUrl,
                    createdAt = "2026-11-18T00:00:00Z"
                )
            )
        )
    }

    override suspend fun createPostWithCaption(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreatePostCaptionRequest
    ): Response<List<SupabasePostDto>> {
        lastCreatedMediaUrl = request.mediaUrl
        return Response.success(
            listOf(
                SupabasePostDto(
                    id = "post-002",
                    userId = request.userId,
                    caption = request.caption,
                    mediaUrl = request.mediaUrl,
                    createdAt = "2026-11-18T00:00:00Z"
                )
            )
        )
    }

    override suspend fun getProfile(
        bearerToken: String?,
        select: String,
        idFilter: String,
        limit: Int
    ): Response<List<SupabaseProfileDto>> =
        Response.success(existingProfile?.let { listOf(it) } ?: emptyList())

    override suspend fun upsertProfile(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreateProfileRequest
    ): Response<List<SupabaseProfileDto>> {
        upsertCalled = true
        val created = SupabaseProfileDto(
            id = request.id,
            username = request.username,
            displayName = request.displayName,
            bio = request.bio
        )
        existingProfile = created
        return Response.success(listOf(created))
    }

    override suspend fun updateProfile(
        bearerToken: String,
        prefer: String,
        idFilter: String,
        request: SupabaseProfileUpdateRequest
    ): Response<List<SupabaseProfileDto>> {
        val updated = SupabaseProfileDto(
            id = existingProfile?.id ?: "user-001",
            username = existingProfile?.username ?: "vybal_user",
            displayName = request.displayName,
            bio = request.bio
        )
        existingProfile = updated
        return Response.success(listOf(updated))
    }

    override suspend fun updateProfileFullName(
        bearerToken: String,
        prefer: String,
        idFilter: String,
        request: SupabaseProfileFullNameUpdateRequest
    ): Response<List<SupabaseProfileDto>> {
        val updated = SupabaseProfileDto(
            id = existingProfile?.id ?: "user-001",
            username = existingProfile?.username ?: "vybal_user",
            fullName = request.fullName,
            bio = request.bio
        )
        existingProfile = updated
        return Response.success(listOf(updated))
    }

    override suspend fun getNotifications(
        bearerToken: String,
        select: String,
        order: String,
        limit: Int
    ): Response<List<SupabaseNotificationDto>> = Response.success(emptyList())

    override suspend fun getMessages(
        bearerToken: String,
        select: String,
        order: String,
        limit: Int
    ): Response<List<SupabaseMessageDto>> = Response.success(emptyList())
}

private class FakeSupabaseStorageApi : SupabaseStorageApi {
    var lastUploadedBucket: String? = null
    var lastUploadedPath: String? = null

    override suspend fun uploadObject(
        bearerToken: String,
        upsert: String,
        cacheControl: String,
        bucket: String,
        path: String,
        body: RequestBody
    ): Response<Unit> {
        lastUploadedBucket = bucket
        lastUploadedPath = path
        return Response.success(Unit)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VybalAuthFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `auth validator rejects invalid inputs and accepts valid inputs`() {
        assertNotNull(AuthValidator.validateEmail("not-an-email"))
        assertNull(AuthValidator.validateEmail("user@vybal.app"))

        assertNotNull(AuthValidator.validatePassword("123"))
        assertNull(AuthValidator.validatePassword("secure123"))

        assertNotNull(AuthValidator.validateConfirmPassword("secure123", "different"))
        assertNull(AuthValidator.validateConfirmPassword("secure123", "secure123"))

        assertNotNull(AuthValidator.validateUsername("ab"))
        assertNotNull(AuthValidator.validateUsername("bad user!"))
        assertNull(AuthValidator.validateUsername("vybal_creator_01"))

        val mapped = AuthValidator.mapSupabaseAuthError("Invalid login credentials", 400)
        assertEquals("Incorrect email or password. Please try again.", mapped)
    }

    @Test
    fun `registration passes trigger metadata persists session and retrieves trigger profile`() = runTest {
        val storage = InMemorySessionStorage()
        val authApi = FakeSupabaseAuthApi()
        val restApi = FakeSupabaseRestApi().apply {
            existingProfile = SupabaseProfileDto(
                id = "user-002",
                username = "nova_vybal",
                displayName = "Nova",
                bio = ""
            )
        }
        val authRepo = SupabaseAuthRepository(
            authApi = authApi,
            sessionStore = storage,
            isConfiguredProvider = { true }
        )
        val vybalRepo = VybalRepository(
            restApi = restApi,
            storageApi = FakeSupabaseStorageApi(),
            sessionStore = storage,
            isConfiguredProvider = { true },
            rawUrlProvider = { "https://example.supabase.co" }
        )

        val result = authRepo.signUp(
            email = "new@vybal.app",
            password = "password123",
            username = "Nova_Vybal",
            displayName = "Nova"
        )

        assertTrue(result is AuthResult.Authenticated)
        assertEquals("nova_vybal", authApi.lastSignUpRequest?.data?.username)
        assertEquals("Nova", authApi.lastSignUpRequest?.data?.displayName)
        assertNotNull(storage.currentSession())

        val profileState = vybalRepo.fetchCurrentProfile(waitForTrigger = true)
        assertTrue(profileState is ResourceState.Success)
        val profile = (profileState as ResourceState.Success).data
        assertEquals("nova_vybal", profile?.username)
        assertEquals("Nova", profile?.displayName)
        assertEquals(false, restApi.upsertCalled)
    }

    @Test
    fun `post upload uses existing posts storage bucket and saves media_url`() = runTest {
        val storage = InMemorySessionStorage()
        storage.saveSession(
            UserSession(
                accessToken = "valid-jwt",
                refreshToken = "refresh-jwt",
                userId = "user-001",
                email = "member@vybal.app"
            )
        )
        val restApi = FakeSupabaseRestApi()
        val storageApi = FakeSupabaseStorageApi()
        val vybalRepo = VybalRepository(
            restApi = restApi,
            storageApi = storageApi,
            sessionStore = storage,
            isConfiguredProvider = { true },
            rawUrlProvider = { "https://vybal.supabase.co" }
        )

        val result = vybalRepo.publishPost(
            content = "First real VYBAL post",
            mediaBytes = byteArrayOf(1, 2, 3, 4),
            mimeType = "image/jpeg"
        )

        assertTrue(result is ResourceState.Success)
        assertEquals("posts", storageApi.lastUploadedBucket)
        val created = (result as ResourceState.Success).data
        assertEquals("First real VYBAL post", created.text)
        assertTrue(
            created.mediaUrl?.startsWith("https://vybal.supabase.co/storage/v1/object/public/posts/user-001/") == true
        )
        assertEquals(created.mediaUrl, restApi.lastCreatedMediaUrl)
    }

    @Test
    fun `post upload falls back to caption column and surfaces RLS 403 clearly`() = runTest {
        val storage = InMemorySessionStorage()
        storage.saveSession(
            UserSession(
                accessToken = "valid-jwt",
                refreshToken = "refresh-jwt",
                userId = "user-001",
                email = "member@vybal.app"
            )
        )
        val restApi = FakeSupabaseRestApi().apply { useCaptionColumn = true }
        val vybalRepo = VybalRepository(
            restApi = restApi,
            storageApi = FakeSupabaseStorageApi(),
            sessionStore = storage,
            isConfiguredProvider = { true },
            rawUrlProvider = { "https://vybal.supabase.co" }
        )

        val captionResult = vybalRepo.publishPost("Caption schema post", null, null)
        assertTrue(captionResult is ResourceState.Success)
        assertEquals("Caption schema post", (captionResult as ResourceState.Success).data.text)

        restApi.forceRlsForbidden = true
        val rlsResult = vybalRepo.publishPost("Blocked by RLS", null, null)
        assertTrue(rlsResult is ResourceState.Error)
        assertEquals(
            "Permission denied by Supabase Row Level Security policy.",
            (rlsResult as ResourceState.Error).message
        )
    }

    @Test
    fun `login error returns friendly error message without saving session`() = runTest {
        val storage = InMemorySessionStorage()
        val authApi = FakeSupabaseAuthApi().apply {
            signInResponse = Response.error(
                400,
                """{"error_description":"Invalid login credentials"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        }
        val authRepo = SupabaseAuthRepository(
            authApi = authApi,
            sessionStore = storage,
            isConfiguredProvider = { true }
        )

        val result = authRepo.signIn("member@vybal.app", "wrongpass")
        assertTrue(result is AuthResult.Failure)
        assertEquals(
            "Incorrect email or password. Please try again.",
            (result as AuthResult.Failure).message
        )
        assertNull(storage.currentSession())
    }

    @Test
    fun `password reset sends recover request and logout clears session`() = runTest {
        val storage = InMemorySessionStorage()
        val authApi = FakeSupabaseAuthApi()
        val authRepo = SupabaseAuthRepository(
            authApi = authApi,
            sessionStore = storage,
            isConfiguredProvider = { true }
        )

        val reset = authRepo.sendPasswordReset("member@vybal.app")
        assertTrue(reset is PasswordResetResult.Sent)
        assertEquals("member@vybal.app", authApi.lastRecoverRequest?.email)

        val login = authRepo.signIn("member@vybal.app", "password123")
        assertTrue(login is AuthResult.Authenticated)
        assertNotNull(storage.currentSession())

        authRepo.signOut()
        assertTrue(authApi.signOutCalled)
        assertNull(storage.currentSession())
    }

    @Test
    fun `expired persisted session refreshes automatically and clears on revoked token`() = runTest {
        val storage = InMemorySessionStorage()
        val authApi = FakeSupabaseAuthApi()
        val authRepo = SupabaseAuthRepository(
            authApi = authApi,
            sessionStore = storage,
            isConfiguredProvider = { true }
        )

        storage.saveSession(
            UserSession(
                accessToken = "old-jwt",
                refreshToken = "valid-refresh",
                userId = "user-001",
                email = "member@vybal.app",
                expiresAtEpochSec = 1000L
            )
        )

        val restored = authRepo.restoreOrRefreshSession()
        assertNotNull(restored)
        assertEquals("refreshed-jwt-789", restored?.accessToken)

        storage.saveSession(
            UserSession(
                accessToken = "old-jwt",
                refreshToken = "revoked-refresh",
                userId = "user-001",
                email = "member@vybal.app",
                expiresAtEpochSec = 1000L
            )
        )
        authApi.refreshResponse = Response.error(
            401,
            """{"error":"invalid_grant"}""".toResponseBody("application/json".toMediaType())
        )
        val cleared = authRepo.restoreOrRefreshSession()
        assertNull(cleared)
        assertNull(storage.currentSession())
    }

    @Test
    fun `protected Create screen shows auth gate when logged out`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader(context)

        composeTestRule.setContent {
            VybalTheme {
                CreateScreen(
                    createState = CreatePostUiState(),
                    session = null,
                    isSessionLoading = false,
                    isSupabaseConfigured = true,
                    imageLoader = imageLoader,
                    onPublish = { _, _, _ -> },
                    onOpenAuth = {},
                    onClearFeedback = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("protected_create_gate").assertIsDisplayed()
    }

    @Test
    fun `protected Inbox screen shows auth gate when logged out`() {
        composeTestRule.setContent {
            VybalTheme {
                InboxScreen(
                    session = null,
                    isSessionLoading = false,
                    notificationsState = ResourceState.AuthRequired,
                    messagesState = ResourceState.AuthRequired,
                    isSupabaseConfigured = true,
                    onRefresh = {},
                    onOpenAuth = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("protected_inbox_gate").assertIsDisplayed()
    }
}
