package com.example.data.social

import com.example.core.config.VybalSchemaContract
import com.example.core.config.VybalSupabaseConfig
import com.example.core.network.SupabaseClientBundle
import com.example.core.network.SupabaseCreatePostCaptionRequest
import com.example.core.network.SupabaseCreatePostRequest
import com.example.core.network.SupabaseCreateProfileRequest
import com.example.core.network.SupabaseErrorDto
import com.example.core.network.SupabasePostDto
import com.example.core.network.SupabaseProfileDto
import com.example.core.network.SupabaseProfileFullNameUpdateRequest
import com.example.core.network.SupabaseProfileUpdateRequest
import com.example.core.network.SupabaseRestApi
import com.example.core.network.SupabaseStorageApi
import com.example.data.auth.SessionStorage
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.model.VybalMessage
import com.example.data.model.VybalNotification
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class VybalRepository(
    private val restApi: SupabaseRestApi,
    private val storageApi: SupabaseStorageApi,
    private val sessionStore: SessionStorage,
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
    private val isConfiguredProvider: () -> Boolean = { VybalSupabaseConfig.isConfigured },
    private val rawUrlProvider: () -> String = { VybalSupabaseConfig.rawUrl },
    private val validSessionProvider: suspend (Boolean) -> UserSession? = { sessionStore.currentSession() }
) {
    constructor(
        clients: SupabaseClientBundle,
        sessionStore: SessionStorage,
        validSessionProvider: suspend (Boolean) -> UserSession? = { sessionStore.currentSession() }
    ) : this(
        restApi = clients.restApi,
        storageApi = clients.storageApi,
        sessionStore = sessionStore,
        moshi = clients.moshi,
        validSessionProvider = validSessionProvider
    )

    private suspend fun optionalBearerHeader(): String? {
        val session = validSessionProvider(false)
        return session?.accessToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    private suspend fun requireSession(): UserSession? = validSessionProvider(false)

    suspend fun fetchHomeFeed(offset: Int = 0): ResourceState<List<VybalPost>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            runCatching {
                val response = restApi.getPosts(
                    bearerToken = optionalBearerHeader(),
                    limit = VybalSchemaContract.DEFAULT_PAGE_SIZE,
                    offset = offset
                )
                handlePostsResponse(response)
            }.getOrElse { ResourceState.Error(mapNetworkError(it, "Could not load feed.")) }
        }

    suspend fun fetchExplorePosts(query: String): ResourceState<List<VybalPost>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            runCatching {
                val trimmed = query.trim()
                val bearer = optionalBearerHeader()
                val response = if (trimmed.isEmpty()) {
                    restApi.getPosts(
                        bearerToken = bearer,
                        limit = VybalSchemaContract.DEFAULT_PAGE_SIZE,
                        offset = 0
                    )
                } else {
                    val primary = restApi.searchPosts(
                        bearerToken = bearer,
                        contentFilter = "ilike.*$trimmed*",
                        limit = VybalSchemaContract.DEFAULT_PAGE_SIZE
                    )
                    if (!primary.isSuccessful && primary.code() == 400 && isMissingColumnError(primary, "content")) {
                        restApi.searchPostsByCaption(
                            bearerToken = bearer,
                            captionFilter = "ilike.*$trimmed*",
                            limit = VybalSchemaContract.DEFAULT_PAGE_SIZE
                        )
                    } else {
                        primary
                    }
                }
                handlePostsResponse(response)
            }.getOrElse { ResourceState.Error(mapNetworkError(it, "Could not search posts.")) }
        }

    suspend fun publishPost(
        content: String,
        mediaBytes: ByteArray?,
        mimeType: String?
    ): ResourceState<VybalPost> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        val bearer = "Bearer ${session.accessToken}"

        runCatching {
            var uploadedUrl: String? = null
            if (mediaBytes != null && mediaBytes.isNotEmpty()) {
                val extension = when {
                    mimeType?.contains("png") == true -> "png"
                    mimeType?.contains("webp") == true -> "webp"
                    else -> "jpg"
                }
                val objectPath = "${session.userId}/${UUID.randomUUID()}.$extension"
                val requestBody = mediaBytes.toRequestBody(
                    (mimeType ?: "image/jpeg").toMediaTypeOrNull()
                )
                val uploadResp = storageApi.uploadObject(
                    bearerToken = bearer,
                    bucket = VybalSchemaContract.BUCKET_POSTS,
                    path = objectPath,
                    body = requestBody
                )
                if (uploadResp.code() == 401) {
                    return@withContext ResourceState.AuthRequired
                }
                if (!uploadResp.isSuccessful) {
                    return@withContext ResourceState.Error(
                        "Media upload to '${VybalSchemaContract.BUCKET_POSTS}' bucket failed: ${parseError(uploadResp)}"
                    )
                }
                uploadedUrl = "${rawUrlProvider()}/storage/v1/object/public/${VybalSchemaContract.BUCKET_POSTS}/$objectPath"
            }

            val trimmedContent = content.trim()
            val primaryPostResp = restApi.createPost(
                bearerToken = bearer,
                request = SupabaseCreatePostRequest(
                    userId = session.userId,
                    content = trimmedContent,
                    mediaUrl = uploadedUrl
                )
            )
            val postResp = if (
                !primaryPostResp.isSuccessful &&
                primaryPostResp.code() == 400 &&
                isMissingColumnError(primaryPostResp, "content")
            ) {
                restApi.createPostWithCaption(
                    bearerToken = bearer,
                    request = SupabaseCreatePostCaptionRequest(
                        userId = session.userId,
                        caption = trimmedContent,
                        mediaUrl = uploadedUrl
                    )
                )
            } else {
                primaryPostResp
            }

            when {
                postResp.isSuccessful -> {
                    val created = postResp.body()?.firstOrNull()?.toDomain()
                    if (created != null) {
                        ResourceState.Success(created)
                    } else {
                        ResourceState.Error("Post created but no record was returned.")
                    }
                }
                postResp.code() == 401 -> ResourceState.AuthRequired
                else -> ResourceState.Error(parseError(postResp))
            }
        }.getOrElse { ResourceState.Error(mapNetworkError(it, "Unable to publish post.")) }
    }

    suspend fun fetchNotifications(): ResourceState<List<VybalNotification>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            val bearer = "Bearer ${session.accessToken}"
            runCatching {
                val response = restApi.getNotifications(bearerToken = bearer)
                when {
                    response.isSuccessful -> {
                        val items = response.body().orEmpty().map {
                            VybalNotification(
                                id = it.id,
                                type = it.type ?: "activity",
                                message = (it.message ?: it.content).orEmpty(),
                                createdAt = it.createdAt.orEmpty(),
                                isRead = it.isRead ?: false,
                                actorId = it.actorId,
                                postId = it.postId
                            )
                        }
                        ResourceState.Success(items)
                    }
                    response.code() == 401 -> ResourceState.AuthRequired
                    else -> ResourceState.Error(parseError(response))
                }
            }.getOrElse { ResourceState.Error(mapNetworkError(it, "Could not load notifications.")) }
        }

    suspend fun fetchMessages(): ResourceState<List<VybalMessage>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            val bearer = "Bearer ${session.accessToken}"
            runCatching {
                val response = restApi.getMessages(bearerToken = bearer)
                when {
                    response.isSuccessful -> {
                        val items = response.body().orEmpty().map {
                            VybalMessage(
                                id = it.id,
                                senderId = it.senderId.orEmpty(),
                                recipientId = it.recipientId.orEmpty(),
                                body = (it.body ?: it.content).orEmpty(),
                                createdAt = it.createdAt.orEmpty()
                            )
                        }
                        ResourceState.Success(items)
                    }
                    response.code() == 401 -> ResourceState.AuthRequired
                    else -> ResourceState.Error(parseError(response))
                }
            }.getOrElse { ResourceState.Error(mapNetworkError(it, "Could not load messages.")) }
        }

    /**
     * Retrieves the authenticated user's profile from the existing `profiles` table.
     * Respects the existing Supabase profile creation trigger: reads first (with a brief retry
     * right after registration), and only merges a profile row if none exists yet.
     */
    suspend fun fetchCurrentProfile(
        fallbackUsername: String? = null,
        fallbackDisplayName: String? = null,
        waitForTrigger: Boolean = false
    ): ResourceState<VybalProfile?> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        val bearer = "Bearer ${session.accessToken}"

        runCatching {
            val firstResp = restApi.getProfile(
                bearerToken = bearer,
                idFilter = "eq.${session.userId}"
            )
            if (firstResp.code() == 401) {
                return@withContext ResourceState.AuthRequired
            }
            if (!firstResp.isSuccessful) {
                return@withContext ResourceState.Error(parseError(firstResp))
            }

            var dto = firstResp.body()?.firstOrNull()
            if (dto == null && waitForTrigger) {
                delay(350)
                val retryResp = restApi.getProfile(
                    bearerToken = bearer,
                    idFilter = "eq.${session.userId}"
                )
                if (retryResp.isSuccessful) {
                    dto = retryResp.body()?.firstOrNull()
                }
            }

            if (dto == null) {
                val derivedUsername = fallbackUsername?.trim()?.takeIf { it.isNotBlank() }
                    ?: session.email.substringBefore("@").replace(Regex("[^a-zA-Z0-9_]"), "_")
                val derivedDisplay = fallbackDisplayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: derivedUsername
                val upsertResp = restApi.upsertProfile(
                    bearerToken = bearer,
                    request = SupabaseCreateProfileRequest(
                        id = session.userId,
                        username = derivedUsername,
                        displayName = derivedDisplay,
                        bio = ""
                    )
                )
                if (upsertResp.isSuccessful) {
                    dto = upsertResp.body()?.firstOrNull()
                }
            }

            ResourceState.Success(dto?.toDomain())
        }.getOrElse { ResourceState.Error(mapNetworkError(it, "Could not load profile.")) }
    }

    suspend fun fetchCurrentUserPosts(): ResourceState<List<VybalPost>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val response = restApi.getUserPosts(
                    bearerToken = "Bearer ${session.accessToken}",
                    userIdFilter = "eq.${session.userId}"
                )
                handlePostsResponse(response)
            }.getOrElse { ResourceState.Error(mapNetworkError(it, "Could not load user posts.")) }
        }

    suspend fun updateProfile(displayName: String, bio: String): ResourceState<VybalProfile> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            val bearer = "Bearer ${session.accessToken}"
            runCatching {
                val primary = restApi.updateProfile(
                    bearerToken = bearer,
                    idFilter = "eq.${session.userId}",
                    request = SupabaseProfileUpdateRequest(
                        displayName = displayName.trim(),
                        bio = bio.trim()
                    )
                )
                val response = if (
                    !primary.isSuccessful &&
                    primary.code() == 400 &&
                    isMissingColumnError(primary, "display_name")
                ) {
                    restApi.updateProfileFullName(
                        bearerToken = bearer,
                        idFilter = "eq.${session.userId}",
                        request = SupabaseProfileFullNameUpdateRequest(
                            fullName = displayName.trim(),
                            bio = bio.trim()
                        )
                    )
                } else {
                    primary
                }

                when {
                    response.isSuccessful -> {
                        val updated = response.body()?.firstOrNull()
                        if (updated != null) {
                            ResourceState.Success(updated.toDomain())
                        } else {
                            ResourceState.Error("Profile updated, but no record was returned.")
                        }
                    }
                    response.code() == 401 -> ResourceState.AuthRequired
                    else -> ResourceState.Error(parseError(response))
                }
            }.getOrElse { ResourceState.Error(mapNetworkError(it, "Unable to update profile.")) }
        }

    private fun handlePostsResponse(response: Response<List<SupabasePostDto>>): ResourceState<List<VybalPost>> {
        return when {
            response.isSuccessful -> ResourceState.Success(response.body().orEmpty().map { it.toDomain() })
            response.code() == 401 -> ResourceState.AuthRequired
            else -> ResourceState.Error(parseError(response))
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

    private fun SupabasePostDto.toDomain(): VybalPost = VybalPost(
        id = id,
        userId = (userId ?: authorId).orEmpty(),
        text = (content ?: caption).orEmpty(),
        mediaUrl = mediaUrl ?: imageUrl,
        visibility = "public",
        createdAt = createdAt.orEmpty(),
        isPinned = isPinned ?: false,
        commentsDisabled = commentsDisabled ?: false,
        shareCount = shareCount ?: 0,
        viewCount = viewCount
    )

    private fun isMissingColumnError(response: Response<*>, column: String): Boolean {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        return raw.contains("PGRST204", ignoreCase = true) ||
            (raw.contains(column, ignoreCase = true) && raw.contains("does not exist", ignoreCase = true))
    }

    private fun mapNetworkError(error: Throwable, fallback: String): String {
        return if (error is IOException) {
            "Network unavailable. Check your internet connection and try again."
        } else {
            error.localizedMessage ?: fallback
        }
    }

    private fun parseError(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        if (!raw.isNullOrBlank()) {
            val adapter = moshi.adapter(SupabaseErrorDto::class.java)
            val parsed = runCatching { adapter.fromJson(raw) }.getOrNull()
            if (parsed?.code == "42501" || response.code() == 403) {
                return "Permission denied by Supabase Row Level Security policy."
            }
            val message = parsed?.message ?: parsed?.msg ?: parsed?.errorDescription ?: parsed?.error
            if (!message.isNullOrBlank()) return message
        }
        return if (response.code() == 403) {
            "Permission denied by Supabase Row Level Security policy."
        } else {
            "Supabase request failed (${response.code()})"
        }
    }
}
