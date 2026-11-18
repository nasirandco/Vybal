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

data class SupabaseReactionDto(
    val id: String? = null,
    @Json(name = "post_id") val postId: String,
    @Json(name = "user_id") val userId: String,
    val reaction: String = "like"
)

data class SupabaseUpsertReactionRequest(
    @Json(name = "post_id") val postId: String,
    @Json(name = "user_id") val userId: String,
    val reaction: String
)

data class SupabaseCommentDto(
    val id: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "parent_comment_id") val parentCommentId: String? = null,
    val content: String,
    @Json(name = "is_pinned") val isPinned: Boolean? = false,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCreateCommentRequest(
    @Json(name = "post_id") val postId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "parent_comment_id") val parentCommentId: String? = null,
    val content: String
)

data class SupabaseUpdateCommentRequest(
    val content: String
)

data class SupabasePinCommentRequest(
    @Json(name = "is_pinned") val isPinned: Boolean
)

data class SupabaseCommentLikeDto(
    @Json(name = "comment_id") val commentId: String,
    @Json(name = "user_id") val userId: String
)

data class SupabaseSavedCollectionDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val name: String,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCreateCollectionRequest(
    @Json(name = "user_id") val userId: String,
    val name: String
)

data class SupabaseSavedPostDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "collection_id") val collectionId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseUpsertSavedPostRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String,
    @Json(name = "collection_id") val collectionId: String? = null
)

data class SupabaseRepostDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String,
    val caption: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

data class SupabaseCreateRepostRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String,
    val caption: String? = null
)

data class SupabaseFollowDto(
    val id: String? = null,
    @Json(name = "follower_id") val followerId: String,
    @Json(name = "following_id") val followingId: String,
    val status: String? = "accepted"
)

data class SupabaseCreateFollowRequest(
    @Json(name = "follower_id") val followerId: String,
    @Json(name = "following_id") val followingId: String,
    val status: String = "accepted"
)

data class SupabaseHiddenPostRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String
)

data class SupabaseHiddenPostDto(
    @Json(name = "post_id") val postId: String
)

data class SupabaseReportPostRequest(
    @Json(name = "post_id") val postId: String,
    @Json(name = "reporter_id") val reporterId: String,
    val reason: String
)

data class SupabasePostControlPatch(
    @Json(name = "is_pinned") val isPinned: Boolean? = null,
    @Json(name = "comments_disabled") val commentsDisabled: Boolean? = null,
    @Json(name = "share_count") val shareCount: Int? = null
)

data class SupabaseCreateNotificationRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "actor_id") val actorId: String,
    @Json(name = "post_id") val postId: String? = null,
    val type: String,
    val message: String,
    @Json(name = "is_read") val isRead: Boolean = false
)

data class SupabaseMarkNotificationReadRequest(
    @Json(name = "is_read") val isRead: Boolean = true
)

interface SupabaseEngagementApi {
    // Reactions & Likes
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_POST_REACTIONS}")
    suspend fun getReactionsForPosts(
        @Header("Authorization") bearerToken: String?,
        @Query("post_id") postIdIn: String,
        @Query("select") select: String = "post_id,user_id,reaction"
    ): Response<List<SupabaseReactionDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_POST_REACTIONS}")
    suspend fun upsertReaction(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Query("on_conflict") onConflict: String = "post_id,user_id",
        @Body request: SupabaseUpsertReactionRequest
    ): Response<List<SupabaseReactionDto>>

    @DELETE("rest/v1/${VybalSchemaContract.TABLE_POST_REACTIONS}")
    suspend fun deleteReaction(
        @Header("Authorization") bearerToken: String,
        @Query("post_id") postIdEq: String,
        @Query("user_id") userIdEq: String
    ): Response<Unit>

    // Comments
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_COMMENTS}")
    suspend fun getComments(
        @Header("Authorization") bearerToken: String?,
        @Query("post_id") postIdEq: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "is_pinned.desc,created_at.asc",
        @Query("limit") limit: Int = VybalSchemaContract.COMMENTS_PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): Response<List<SupabaseCommentDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_COMMENTS}")
    suspend fun getCommentCountsForPosts(
        @Header("Authorization") bearerToken: String?,
        @Query("post_id") postIdIn: String,
        @Query("select") select: String = "id,post_id"
    ): Response<List<SupabaseCommentDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_COMMENTS}")
    suspend fun createComment(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseCreateCommentRequest
    ): Response<List<SupabaseCommentDto>>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_COMMENTS}")
    suspend fun updateComment(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idEq: String,
        @Body request: SupabaseUpdateCommentRequest
    ): Response<List<SupabaseCommentDto>>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_COMMENTS}")
    suspend fun pinComment(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idEq: String,
        @Body request: SupabasePinCommentRequest
    ): Response<List<SupabaseCommentDto>>

    @DELETE("rest/v1/${VybalSchemaContract.TABLE_COMMENTS}")
    suspend fun deleteComment(
        @Header("Authorization") bearerToken: String,
        @Query("id") idEq: String
    ): Response<Unit>

    // Comment Likes
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_COMMENT_LIKES}")
    suspend fun getCommentLikes(
        @Header("Authorization") bearerToken: String?,
        @Query("comment_id") commentIdIn: String,
        @Query("select") select: String = "comment_id,user_id"
    ): Response<List<SupabaseCommentLikeDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_COMMENT_LIKES}")
    suspend fun likeComment(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates",
        @Body request: SupabaseCommentLikeDto
    ): Response<Unit>

    @DELETE("rest/v1/${VybalSchemaContract.TABLE_COMMENT_LIKES}")
    suspend fun unlikeComment(
        @Header("Authorization") bearerToken: String,
        @Query("comment_id") commentIdEq: String,
        @Query("user_id") userIdEq: String
    ): Response<Unit>

    // Saved Collections & Saved Posts
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_SAVED_COLLECTIONS}")
    suspend fun getCollections(
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdEq: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<SupabaseSavedCollectionDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_SAVED_COLLECTIONS}")
    suspend fun createCollection(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: SupabaseCreateCollectionRequest
    ): Response<List<SupabaseSavedCollectionDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_SAVED_POSTS}")
    suspend fun getSavedPosts(
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdEq: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<SupabaseSavedPostDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_SAVED_POSTS}")
    suspend fun upsertSavedPost(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Query("on_conflict") onConflict: String = "user_id,post_id",
        @Body request: SupabaseUpsertSavedPostRequest
    ): Response<List<SupabaseSavedPostDto>>

    @DELETE("rest/v1/${VybalSchemaContract.TABLE_SAVED_POSTS}")
    suspend fun deleteSavedPost(
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdEq: String,
        @Query("post_id") postIdEq: String
    ): Response<Unit>

    // Reposts
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_REPOSTS}")
    suspend fun getRepostsForPosts(
        @Header("Authorization") bearerToken: String?,
        @Query("post_id") postIdIn: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseRepostDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_REPOSTS}")
    suspend fun createRepost(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation",
        @Query("on_conflict") onConflict: String = "user_id,post_id",
        @Body request: SupabaseCreateRepostRequest
    ): Response<List<SupabaseRepostDto>>

    @DELETE("rest/v1/${VybalSchemaContract.TABLE_REPOSTS}")
    suspend fun deleteRepost(
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdEq: String,
        @Query("post_id") postIdEq: String
    ): Response<Unit>

    // Follows
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_FOLLOWS}")
    suspend fun getFollowers(
        @Header("Authorization") bearerToken: String?,
        @Query("following_id") followingIdEq: String,
        @Query("select") select: String = "follower_id,following_id,status"
    ): Response<List<SupabaseFollowDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_FOLLOWS}")
    suspend fun getFollowing(
        @Header("Authorization") bearerToken: String?,
        @Query("follower_id") followerIdEq: String,
        @Query("select") select: String = "follower_id,following_id,status"
    ): Response<List<SupabaseFollowDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_FOLLOWS}")
    suspend fun followUser(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates",
        @Body request: SupabaseCreateFollowRequest
    ): Response<Unit>

    @DELETE("rest/v1/${VybalSchemaContract.TABLE_FOLLOWS}")
    suspend fun unfollowUser(
        @Header("Authorization") bearerToken: String,
        @Query("follower_id") followerIdEq: String,
        @Query("following_id") followingIdEq: String
    ): Response<Unit>

    // Profiles lookup (by username or list of IDs)
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_PROFILES}")
    suspend fun getProfilesByIds(
        @Header("Authorization") bearerToken: String?,
        @Query("id") idIn: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseProfileDto>>

    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_PROFILES}")
    suspend fun getProfileByUsername(
        @Header("Authorization") bearerToken: String?,
        @Query("username") usernameEq: String,
        @Query("limit") limit: Int = 1
    ): Response<List<SupabaseProfileDto>>

    // Posts lookup & controls (by ID list, pin, comments_disabled, share_count)
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun getPostsByIds(
        @Header("Authorization") bearerToken: String?,
        @Query("id") idIn: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabasePostDto>>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_POSTS}")
    suspend fun patchPostControls(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idEq: String,
        @Body patch: SupabasePostControlPatch
    ): Response<List<SupabasePostDto>>

    // Hide & Report
    @Headers("Cache-Control: no-cache")
    @GET("rest/v1/${VybalSchemaContract.TABLE_HIDDEN_POSTS}")
    suspend fun getHiddenPosts(
        @Header("Authorization") bearerToken: String,
        @Query("user_id") userIdEq: String,
        @Query("select") select: String = "post_id"
    ): Response<List<SupabaseHiddenPostDto>>

    @POST("rest/v1/${VybalSchemaContract.TABLE_HIDDEN_POSTS}")
    suspend fun hidePost(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates",
        @Body request: SupabaseHiddenPostRequest
    ): Response<Unit>

    @POST("rest/v1/${VybalSchemaContract.TABLE_POST_REPORTS}")
    suspend fun reportPost(
        @Header("Authorization") bearerToken: String,
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates",
        @Body request: SupabaseReportPostRequest
    ): Response<Unit>

    // Engagement Notifications
    @POST("rest/v1/${VybalSchemaContract.TABLE_NOTIFICATIONS}")
    suspend fun createNotification(
        @Header("Authorization") bearerToken: String,
        @Body request: SupabaseCreateNotificationRequest
    ): Response<Unit>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_NOTIFICATIONS}")
    suspend fun markNotificationRead(
        @Header("Authorization") bearerToken: String,
        @Query("id") idEq: String,
        @Body request: SupabaseMarkNotificationReadRequest = SupabaseMarkNotificationReadRequest(true)
    ): Response<Unit>

    @PATCH("rest/v1/${VybalSchemaContract.TABLE_NOTIFICATIONS}")
    suspend fun markAllNotificationsRead(
        @Header("Authorization") bearerToken: String,
        @Query("is_read") isReadEq: String = "eq.false",
        @Body request: SupabaseMarkNotificationReadRequest = SupabaseMarkNotificationReadRequest(true)
    ): Response<Unit>
}
