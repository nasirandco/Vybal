package com.example.core.network

import android.content.Context
import com.example.core.config.VybalSchemaContract
import com.example.core.config.VybalSupabaseConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit

data class SupabaseAuthRequest(
    val email: String,
    val password: String
)

data class SupabaseSignUpMetadata(
    val username: String? = null,
    @Json(name = "display_name") val displayName: String? = null
)

data class SupabaseSignUpRequest(
    val email: String,
    val password: String,
    val data: SupabaseSignUpMetadata? = null
)

data class SupabaseRecoverRequest(
    val email: String
)

data class SupabaseRefreshRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

data class SupabaseUserDto(
    val id: String,
    val email: String? = null
)

data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "expires_at") val expiresAt: Long? = null,
    val user: SupabaseUserDto? = null
)

data class SupabaseProfileDto(
    val id: String,
    val username: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "full_name") val fullName: String? = null,
    val bio: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val email: String? = null,
    val role: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = null,
    @Json(name = "verification_tier") val verificationTier: String? = null,
    @Json(name = "account_status") val accountStatus: String? = null,
    @Json(name = "restriction_reason") val restrictionReason: String? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null
)

data class SupabaseCreateProfileRequest(
    val id: String,
    val username: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val bio: String = ""
)

data class SupabaseProfileUpdateRequest(
    @Json(name = "display_name") val displayName: String,
    val bio: String
)

data class SupabaseProfileFullNameUpdateRequest(
    @Json(name = "full_name") val fullName: String,
    val bio: String
)

data class SupabasePostDto(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "author_id") val authorId: String? = null,
    val content: String? = null,
    val caption: String? = null,
    @Json(name = "media_url") val mediaUrl: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "is_pinned") val isPinned: Boolean? = null,
    @Json(name = "comments_disabled") val commentsDisabled: Boolean? = null,
    @Json(name = "share_count") val shareCount: Int? = null,
    @Json(name = "view_count") val viewCount: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCreatePostRequest(
    @Json(name = "user_id") val userId: String,
    val content: String,
    @Json(name = "media_url") val mediaUrl: String? = null
)

data class SupabaseCreatePostCaptionRequest(
    @Json(name = "user_id") val userId: String,
    val caption: String,
    @Json(name = "media_url") val mediaUrl: String? = null
)

data class SupabaseNotificationDto(
    val id: String,
    val type: String? = null,
    val message: String? = null,
    val content: String? = null,
    @Json(name = "actor_id") val actorId: String? = null,
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "is_read") val isRead: Boolean? = null
)

data class SupabaseMessageDto(
    val id: String,
    @Json(name = "sender_id") val senderId: String? = null,
    @Json(name = "recipient_id") val recipientId: String? = null,
    val body: String? = null,
    val content: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseErrorDto(
    val code: String? = null,
    val msg: String? = null,
    val message: String? = null,
    val details: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    val error: String? = null,
    @Json(name = "error_code") val errorCode: String? = null
)

interface SupabaseAuthApi {
    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Query("grant_type") grantType: String = "password",
        @Body request: SupabaseAuthRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SupabaseSignUpRequest
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(
        @Body request: SupabaseRecoverRequest
    ): Response<Unit>

    @POST("auth/v1/token")
    suspend fun refreshSession(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: SupabaseRefreshRequest
    ): Response<SupabaseAuthResponse>

    @Headers("Cache-Control: no-cache")
    @GET("auth/v1/user")
    suspend fun getCurrentUser(
        @Header("Authorization") bearerToken: String
    ): Response<SupabaseUserDto>

    @POST("auth/v1/logout")
    suspend fun signOut(
        @Header("Authorization") bearerToken: String
    ): Response<Unit>
}

interface SupabaseRestApi {
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun getPosts(
        @Header("Authorization") bearerToken: String?,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = VybalSchemaContract.DEFAULT_PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): Response<List<SupabasePostDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun searchPosts(
        @Header("Authorization") bearerToken: String?,
        @Query("select") select: String = "*",
        @Query("content") contentFilter: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = VybalSchemaContract.DEFAULT_PAGE_SIZE
    ): Response<List<SupabasePostDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun searchPostsByCaption(
        @Header("Authorization") bearerToken: String?,
        @Query("select") select: String = "*",
        @Query("caption") captionFilter: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = VybalSchemaContract.DEFAULT_PAGE_SIZE
    ): Response<List<SupabasePostDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun getUserPosts(
        @Header("Authorization") bearerToken: String?,
        @Query("select") select: String = "*",
        @Query("user_id") userIdFilter: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = VybalSchemaContract.DEFAULT_PAGE_SIZE
    ): Response<List<SupabasePostDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun createPost(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseCreatePostRequest
    ): Response<List<SupabasePostDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun createPostWithCaption(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseCreatePostCaptionRequest
    ): Response<List<SupabasePostDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_PROFILES}")
    suspend fun getProfile(
        @Header("Authorization") bearerToken: String?,
        @Query("select") select: String = "*",
        @Query("id") idFilter: String,
        @Query("limit") limit: Int = 1
    ): Response<List<SupabaseProfileDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_PROFILES}")
    suspend fun upsertProfile(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Body request: SupabaseCreateProfileRequest
    ): Response<List<SupabaseProfileDto>>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_PROFILES}")
    suspend fun updateProfile(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idFilter: String,
        @Body request: SupabaseProfileUpdateRequest
    ): Response<List<SupabaseProfileDto>>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_PROFILES}")
    suspend fun updateProfileFullName(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idFilter: String,
        @Body request: SupabaseProfileFullNameUpdateRequest
    ): Response<List<SupabaseProfileDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_NOTIFICATIONS}")
    suspend fun getNotifications(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = VybalSchemaContract.DEFAULT_PAGE_SIZE
    ): Response<List<SupabaseNotificationDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_MESSAGES}")
    suspend fun getMessages(
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = VybalSchemaContract.DEFAULT_PAGE_SIZE
    ): Response<List<SupabaseMessageDto>>
}

interface SupabaseStorageApi {
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadObject(
        @Header("Authorization") bearerToken: String,
        @Header("x-upsert") upsert: String = "false",
        @Header("cache-control") cacheControl: String = "3600",
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Body body: RequestBody
    ): Response<Unit>
}

class SupabaseClientBundle(
    val okHttpClient: OkHttpClient,
    val moshi: Moshi,
    val authApi: SupabaseAuthApi,
    val restApi: SupabaseRestApi,
    val storageApi: SupabaseStorageApi,
    val engagementApi: SupabaseEngagementApi,
    val adminApi: SupabaseAdminApi
)

object SupabaseClientFactory {
    fun create(context: Context): SupabaseClientBundle {
        val cacheDir = File(context.applicationContext.cacheDir, "vybal_http_cache")
        val httpCache = Cache(cacheDir, 10L * 1024L * 1024L) // 10MB disk cache for slow networks

        val okHttpClient = OkHttpClient.Builder()
            .cache(httpCache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("Accept", "application/json")
                if (VybalSupabaseConfig.isConfigured) {
                    builder.header("apikey", VybalSupabaseConfig.anonKey)
                    if (original.header("Authorization") == null) {
                        builder.header("Authorization", "Bearer ${VybalSupabaseConfig.anonKey}")
                    }
                }
                chain.proceed(builder.build())
            }
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(VybalSupabaseConfig.baseUrlWithSlash)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return SupabaseClientBundle(
            okHttpClient = okHttpClient,
            moshi = moshi,
            authApi = retrofit.create(SupabaseAuthApi::class.java),
            restApi = retrofit.create(SupabaseRestApi::class.java),
            storageApi = retrofit.create(SupabaseStorageApi::class.java),
            engagementApi = retrofit.create(SupabaseEngagementApi::class.java),
            adminApi = retrofit.create(SupabaseAdminApi::class.java)
        )
    }
}
