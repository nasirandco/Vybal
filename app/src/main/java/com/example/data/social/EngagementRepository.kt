package com.example.data.social

import com.example.core.config.VybalSchemaContract
import com.example.core.config.VybalSupabaseConfig
import com.example.core.network.SupabaseClientBundle
import com.example.core.network.SupabaseCommentLikeDto
import com.example.core.network.SupabaseCreateCollectionRequest
import com.example.core.network.SupabaseCreateCommentRequest
import com.example.core.network.SupabaseCreateFollowRequest
import com.example.core.network.SupabaseCreateNotificationRequest
import com.example.core.network.SupabaseCreateRepostRequest
import com.example.core.network.SupabaseEngagementApi
import com.example.core.network.SupabaseErrorDto
import com.example.core.network.SupabaseHiddenPostRequest
import com.example.core.network.SupabasePinCommentRequest
import com.example.core.network.SupabasePostControlPatch
import com.example.core.network.SupabasePostDto
import com.example.core.network.SupabaseProfileDto
import com.example.core.network.SupabaseReportPostRequest
import com.example.core.network.SupabaseUpdateCommentRequest
import com.example.core.network.SupabaseUpsertReactionRequest
import com.example.core.network.SupabaseUpsertSavedPostRequest
import com.example.core.text.RichSocialText
import com.example.data.auth.SessionStorage
import com.example.data.model.FollowStats
import com.example.data.model.PostEngagementSummary
import com.example.data.model.PostInsights
import com.example.data.model.ReactionType
import com.example.data.model.ResourceState
import com.example.data.model.SavedCollection
import com.example.data.model.SavedPostEntry
import com.example.data.model.UserSession
import com.example.data.model.VybalComment
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class EngagementRepository(
    private val api: SupabaseEngagementApi,
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
        api = clients.engagementApi,
        sessionStore = sessionStore,
        moshi = clients.moshi,
        validSessionProvider = validSessionProvider
    )

    private suspend fun optionalBearer(): String? =
        validSessionProvider(false)?.accessToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

    private suspend fun requireSession(): UserSession? = validSessionProvider(false)

    // -------------------------------------------------------------------------
    // Batch Engagement Summary for Feed Posts (2GB RAM friendly: 4 small queries)
    // -------------------------------------------------------------------------
    suspend fun fetchSummariesForPosts(
        posts: List<VybalPost>
    ): Map<String, PostEngagementSummary> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider() || posts.isEmpty()) return@withContext emptyMap()
        val session = validSessionProvider(false)
        val bearer = session?.accessToken?.let { "Bearer $it" }
        val postIds = posts.map { it.id }.distinct()
        val inFilter = "in.(${postIds.joinToString(",")})"

        val reactions = runCatching {
            val r = api.getReactionsForPosts(bearer, inFilter)
            if (r.isSuccessful) r.body().orEmpty() else emptyList()
        }.getOrDefault(emptyList())

        val comments = runCatching {
            val r = api.getCommentCountsForPosts(bearer, inFilter)
            if (r.isSuccessful) r.body().orEmpty() else emptyList()
        }.getOrDefault(emptyList())

        val reposts = runCatching {
            val r = api.getRepostsForPosts(bearer, inFilter)
            if (r.isSuccessful) r.body().orEmpty() else emptyList()
        }.getOrDefault(emptyList())

        val savedMap = if (session != null && bearer != null) {
            runCatching {
                val r = api.getSavedPosts(bearer, "eq.${session.userId}")
                if (r.isSuccessful) r.body().orEmpty().associateBy { it.postId } else emptyMap()
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val myUserId = session?.userId
        val reactionsByPost = reactions.groupBy { it.postId }
        val commentsByPost = comments.groupBy { it.postId }
        val repostsByPost = reposts.groupBy { it.postId }

        posts.associate { post ->
            val postReactions = reactionsByPost[post.id].orEmpty()
            val counts = mutableMapOf<ReactionType, Int>()
            var myReaction: ReactionType? = null
            postReactions.forEach { dto ->
                val type = ReactionType.fromApi(dto.reaction) ?: ReactionType.Like
                counts[type] = (counts[type] ?: 0) + 1
                if (dto.userId == myUserId) myReaction = type
            }
            val postReposts = repostsByPost[post.id].orEmpty()
            val savedEntry = savedMap[post.id]

            post.id to PostEngagementSummary(
                postId = post.id,
                reactionCounts = counts,
                myReaction = myReaction,
                commentCount = commentsByPost[post.id]?.size ?: 0,
                repostCount = postReposts.size,
                isRepostedByMe = myUserId != null && postReposts.any { it.userId == myUserId },
                isSavedByMe = savedEntry != null,
                savedCollectionId = savedEntry?.collectionId,
                shareCount = post.shareCount,
                viewCount = post.viewCount
            )
        }
    }

    suspend fun resolveUsernamesForIds(userIds: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider() || userIds.isEmpty()) return@withContext emptyMap()
            val distinct = userIds.filter { it.isNotBlank() }.distinct()
            if (distinct.isEmpty()) return@withContext emptyMap()
            runCatching {
                val resp = api.getProfilesByIds(optionalBearer(), "in.(${distinct.joinToString(",")})")
                if (resp.isSuccessful) {
                    resp.body().orEmpty().associate { dto ->
                        dto.id to (dto.username?.takeIf { it.isNotBlank() }
                            ?: dto.displayName?.takeIf { it.isNotBlank() }
                            ?: dto.fullName?.takeIf { it.isNotBlank() }
                            ?: dto.id.take(8))
                    }
                } else {
                    emptyMap()
                }
            }.getOrDefault(emptyMap())
        }

    suspend fun resolveIdentityBadgesForIds(
        userIds: List<String>
    ): Map<String, com.example.data.model.ProfileIdentityBadge> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider() || userIds.isEmpty()) return@withContext emptyMap()
            val distinct = userIds.filter { it.isNotBlank() }.distinct()
            if (distinct.isEmpty()) return@withContext emptyMap()
            runCatching {
                val resp = api.getProfilesByIds(optionalBearer(), "in.(${distinct.joinToString(",")})")
                if (resp.isSuccessful) {
                    resp.body().orEmpty().associate { dto ->
                        val handle = dto.username?.takeIf { it.isNotBlank() }
                            ?: dto.displayName?.takeIf { it.isNotBlank() }
                            ?: dto.fullName?.takeIf { it.isNotBlank() }
                            ?: dto.id.take(8)
                        dto.id to com.example.data.model.ProfileIdentityBadge(
                            userId = dto.id,
                            username = handle,
                            role = dto.role ?: "user",
                            isVerified = dto.isVerified ?: false,
                            verificationTier = dto.verificationTier
                        )
                    }
                } else {
                    emptyMap()
                }
            }.getOrDefault(emptyMap())
        }

    // -------------------------------------------------------------------------
    // Likes & Reactions
    // -------------------------------------------------------------------------
    suspend fun setReaction(
        postId: String,
        postOwnerId: String,
        reaction: ReactionType?
    ): ResourceState<ReactionType?> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        val bearer = "Bearer ${session.accessToken}"

        runCatching {
            val resp = if (reaction == null) {
                api.deleteReaction(bearer, "eq.$postId", "eq.${session.userId}")
            } else {
                api.upsertReaction(
                    bearerToken = bearer,
                    request = SupabaseUpsertReactionRequest(
                        postId = postId,
                        userId = session.userId,
                        reaction = reaction.apiValue
                    )
                )
            }
            if (resp.isSuccessful) {
                if (reaction != null && postOwnerId.isNotBlank() && postOwnerId != session.userId) {
                    notifySilently(
                        bearer = bearer,
                        targetUserId = postOwnerId,
                        actorId = session.userId,
                        postId = postId,
                        type = if (reaction == ReactionType.Like) "like" else "reaction",
                        message = if (reaction == ReactionType.Like) {
                            "liked your post"
                        } else {
                            "reacted ${reaction.emoji} to your post"
                        }
                    )
                }
                ResourceState.Success(reaction)
            } else if (resp.code() == 401) {
                ResourceState.AuthRequired
            } else {
                ResourceState.Error(parseError(resp, "post_reactions"))
            }
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    // -------------------------------------------------------------------------
    // Advanced Threaded Comments
    // -------------------------------------------------------------------------
    suspend fun fetchComments(
        postId: String,
        offset: Int = 0
    ): ResourceState<List<VybalComment>> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = validSessionProvider(false)
        val bearer = session?.accessToken?.let { "Bearer $it" }

        runCatching {
            val resp = api.getComments(
                bearerToken = bearer,
                postIdEq = "eq.$postId",
                limit = VybalSchemaContract.COMMENTS_PAGE_SIZE,
                offset = offset
            )
            if (!resp.isSuccessful) {
                return@withContext if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "comments"))
            }
            val dtos = resp.body().orEmpty()
            if (dtos.isEmpty()) return@withContext ResourceState.Success(emptyList())

            val commentIds = dtos.map { it.id }
            val likes = runCatching {
                val r = api.getCommentLikes(bearer, "in.(${commentIds.joinToString(",")})")
                if (r.isSuccessful) r.body().orEmpty() else emptyList()
            }.getOrDefault(emptyList())

            val usernames = resolveUsernamesForIds(dtos.map { it.userId })
            val likesByComment = likes.groupBy { it.commentId }
            val myId = session?.userId

            val mapped = dtos.map { dto ->
                val cLikes = likesByComment[dto.id].orEmpty()
                VybalComment(
                    id = dto.id,
                    postId = dto.postId,
                    userId = dto.userId,
                    authorUsername = usernames[dto.userId] ?: dto.userId.take(8),
                    parentCommentId = dto.parentCommentId,
                    content = dto.content,
                    isPinned = dto.isPinned ?: false,
                    likeCount = cLikes.size,
                    isLikedByMe = myId != null && cLikes.any { it.userId == myId },
                    createdAt = dto.createdAt.orEmpty()
                )
            }
            ResourceState.Success(mapped)
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun addComment(
        postId: String,
        postOwnerId: String,
        content: String,
        parentCommentId: String? = null,
        parentCommentAuthorId: String? = null
    ): ResourceState<VybalComment> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return@withContext ResourceState.Error("Comment cannot be empty.")
        val bearer = "Bearer ${session.accessToken}"

        runCatching {
            val resp = api.createComment(
                bearerToken = bearer,
                request = SupabaseCreateCommentRequest(
                    postId = postId,
                    userId = session.userId,
                    parentCommentId = parentCommentId,
                    content = trimmed
                )
            )
            if (resp.isSuccessful) {
                val dto = resp.body()?.firstOrNull()
                    ?: return@withContext ResourceState.Error("Comment saved but no record returned.")
                val username = resolveUsernamesForIds(listOf(session.userId))[session.userId]
                    ?: session.email.substringBefore("@")

                // Notify post owner or parent comment author
                if (parentCommentId != null && !parentCommentAuthorId.isNullOrBlank() && parentCommentAuthorId != session.userId) {
                    notifySilently(bearer, parentCommentAuthorId, session.userId, postId, "reply", "replied to your comment")
                } else if (postOwnerId.isNotBlank() && postOwnerId != session.userId) {
                    notifySilently(bearer, postOwnerId, session.userId, postId, "comment", "commented on your post")
                }
                notifyMentionedUsers(bearer, session.userId, postId, trimmed)

                ResourceState.Success(
                    VybalComment(
                        id = dto.id,
                        postId = dto.postId,
                        userId = dto.userId,
                        authorUsername = username,
                        parentCommentId = dto.parentCommentId,
                        content = dto.content,
                        isPinned = dto.isPinned ?: false,
                        likeCount = 0,
                        isLikedByMe = false,
                        createdAt = dto.createdAt.orEmpty()
                    )
                )
            } else if (resp.code() == 401) {
                ResourceState.AuthRequired
            } else {
                ResourceState.Error(parseError(resp, "comments"))
            }
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun editComment(commentId: String, newContent: String): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            val trimmed = newContent.trim()
            if (trimmed.isEmpty()) return@withContext ResourceState.Error("Comment cannot be empty.")
            runCatching {
                val resp = api.updateComment(
                    bearerToken = "Bearer ${session.accessToken}",
                    idEq = "eq.$commentId",
                    request = SupabaseUpdateCommentRequest(trimmed)
                )
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "comments"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun deleteComment(commentId: String): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = api.deleteComment("Bearer ${session.accessToken}", "eq.$commentId")
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "comments"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun setCommentPinned(commentId: String, pinned: Boolean): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = api.pinComment(
                    bearerToken = "Bearer ${session.accessToken}",
                    idEq = "eq.$commentId",
                    request = SupabasePinCommentRequest(pinned)
                )
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "comments"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun toggleCommentLike(
        commentId: String,
        commentAuthorId: String,
        postId: String,
        like: Boolean
    ): ResourceState<Boolean> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        val bearer = "Bearer ${session.accessToken}"
        runCatching {
            val resp = if (like) {
                api.likeComment(bearer, request = SupabaseCommentLikeDto(commentId, session.userId))
            } else {
                api.unlikeComment(bearer, "eq.$commentId", "eq.${session.userId}")
            }
            if (resp.isSuccessful) {
                if (like && commentAuthorId.isNotBlank() && commentAuthorId != session.userId) {
                    notifySilently(bearer, commentAuthorId, session.userId, postId, "comment_like", "liked your comment")
                }
                ResourceState.Success(like)
            } else if (resp.code() == 401) {
                ResourceState.AuthRequired
            } else {
                ResourceState.Error(parseError(resp, "comment_likes"))
            }
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    // -------------------------------------------------------------------------
    // Private Bookmarks & Collections
    // -------------------------------------------------------------------------
    suspend fun fetchSavedCollections(): ResourceState<List<SavedCollection>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = api.getCollections("Bearer ${session.accessToken}", "eq.${session.userId}")
                if (resp.isSuccessful) {
                    ResourceState.Success(
                        resp.body().orEmpty().map {
                            SavedCollection(it.id, it.userId, it.name, it.createdAt.orEmpty())
                        }
                    )
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "saved_collections"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun createSavedCollection(name: String): ResourceState<SavedCollection> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            val clean = name.trim()
            if (clean.isEmpty()) return@withContext ResourceState.Error("Collection name cannot be empty.")
            runCatching {
                val resp = api.createCollection(
                    bearerToken = "Bearer ${session.accessToken}",
                    request = SupabaseCreateCollectionRequest(session.userId, clean)
                )
                if (resp.isSuccessful) {
                    val dto = resp.body()?.firstOrNull()
                        ?: return@withContext ResourceState.Error("Collection created without response.")
                    ResourceState.Success(SavedCollection(dto.id, dto.userId, dto.name, dto.createdAt.orEmpty()))
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "saved_collections"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun savePost(postId: String, collectionId: String? = null): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = api.upsertSavedPost(
                    bearerToken = "Bearer ${session.accessToken}",
                    request = SupabaseUpsertSavedPostRequest(session.userId, postId, collectionId)
                )
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "saved_posts"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun unsavePost(postId: String): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = api.deleteSavedPost("Bearer ${session.accessToken}", "eq.${session.userId}", "eq.$postId")
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "saved_posts"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun fetchSavedPostsWithDetails(): ResourceState<Pair<List<SavedPostEntry>, List<VybalPost>>> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            val bearer = "Bearer ${session.accessToken}"
            runCatching {
                val savedResp = api.getSavedPosts(bearer, "eq.${session.userId}")
                if (!savedResp.isSuccessful) {
                    return@withContext if (savedResp.code() == 401) ResourceState.AuthRequired
                    else ResourceState.Error(parseError(savedResp, "saved_posts"))
                }
                val entries = savedResp.body().orEmpty().map {
                    SavedPostEntry(it.id, it.postId, it.collectionId, it.createdAt.orEmpty())
                }
                if (entries.isEmpty()) return@withContext ResourceState.Success(entries to emptyList())
                val ids = entries.map { it.postId }.distinct()
                val postsResp = api.getPostsByIds(bearer, "in.(${ids.joinToString(",")})")
                val posts = if (postsResp.isSuccessful) {
                    postsResp.body().orEmpty().map { it.toDomain() }
                } else emptyList()
                ResourceState.Success(entries to posts)
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    // -------------------------------------------------------------------------
    // Reposts
    // -------------------------------------------------------------------------
    suspend fun createRepost(
        postId: String,
        postOwnerId: String,
        caption: String?
    ): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        val bearer = "Bearer ${session.accessToken}"
        runCatching {
            val resp = api.createRepost(
                bearerToken = bearer,
                request = SupabaseCreateRepostRequest(
                    userId = session.userId,
                    postId = postId,
                    caption = caption?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            if (resp.isSuccessful) {
                if (postOwnerId.isNotBlank() && postOwnerId != session.userId) {
                    notifySilently(bearer, postOwnerId, session.userId, postId, "repost", "reposted your post")
                }
                ResourceState.Success(Unit)
            } else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "reposts"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun removeRepost(postId: String): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = api.deleteRepost("Bearer ${session.accessToken}", "eq.${session.userId}", "eq.$postId")
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "reposts"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    // -------------------------------------------------------------------------
    // Follow System (Prevents self-follow)
    // -------------------------------------------------------------------------
    suspend fun fetchFollowStats(targetUserId: String): ResourceState<FollowStats> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = validSessionProvider(false)
            val bearer = session?.accessToken?.let { "Bearer $it" }
            runCatching {
                val followersResp = api.getFollowers(bearer, "eq.$targetUserId")
                val followingResp = api.getFollowing(bearer, "eq.$targetUserId")
                if (!followersResp.isSuccessful) {
                    return@withContext ResourceState.Error(parseError(followersResp, "follows"))
                }
                val followers = followersResp.body().orEmpty()
                val following = if (followingResp.isSuccessful) followingResp.body().orEmpty() else emptyList()
                val acceptedFollowers = followers.filter { it.status != "pending" }
                val acceptedFollowing = following.filter { it.status != "pending" }

                val myId = session?.userId
                val myRelation = if (myId != null) followers.firstOrNull { it.followerId == myId } else null

                val mutuals = if (myId != null && myId != targetUserId) {
                    val myFollowingResp = runCatching { api.getFollowing(bearer, "eq.$myId") }.getOrNull()
                    val myFollowingIds = myFollowingResp?.body().orEmpty()
                        .filter { it.status != "pending" }
                        .map { it.followingId }
                        .toSet()
                    acceptedFollowers.count { it.followerId in myFollowingIds }
                } else 0

                ResourceState.Success(
                    FollowStats(
                        targetUserId = targetUserId,
                        followersCount = acceptedFollowers.size,
                        followingCount = acceptedFollowing.size,
                        mutualCount = mutuals,
                        isFollowing = myRelation != null && myRelation.status != "pending",
                        isPending = myRelation?.status == "pending"
                    )
                )
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun toggleFollow(targetUserId: String, follow: Boolean): ResourceState<Boolean> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            if (targetUserId == session.userId) {
                return@withContext ResourceState.Error("You cannot follow your own account.")
            }
            val bearer = "Bearer ${session.accessToken}"
            runCatching {
                val resp = if (follow) {
                    api.followUser(
                        bearerToken = bearer,
                        request = SupabaseCreateFollowRequest(session.userId, targetUserId)
                    )
                } else {
                    api.unfollowUser(bearer, "eq.${session.userId}", "eq.$targetUserId")
                }
                if (resp.isSuccessful) {
                    if (follow) {
                        notifySilently(bearer, targetUserId, session.userId, null, "follow", "started following you")
                    }
                    ResourceState.Success(follow)
                } else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "follows"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    // -------------------------------------------------------------------------
    // Post Controls (Pin, Comments Disabled, Hide, Report, Share Count, Insights)
    // -------------------------------------------------------------------------
    suspend fun updatePostControl(
        postId: String,
        isPinned: Boolean? = null,
        commentsDisabled: Boolean? = null,
        shareCount: Int? = null
    ): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = api.patchPostControls(
                bearerToken = "Bearer ${session.accessToken}",
                idEq = "eq.$postId",
                patch = SupabasePostControlPatch(
                    isPinned = isPinned,
                    commentsDisabled = commentsDisabled,
                    shareCount = shareCount
                )
            )
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "posts"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun fetchHiddenPostIds(): Set<String> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext emptySet()
        val session = validSessionProvider(false) ?: return@withContext emptySet()
        runCatching {
            val resp = api.getHiddenPosts("Bearer ${session.accessToken}", "eq.${session.userId}")
            if (resp.isSuccessful) resp.body().orEmpty().map { it.postId }.toSet() else emptySet()
        }.getOrDefault(emptySet())
    }

    suspend fun hidePost(postId: String): ResourceState<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        val session = requireSession() ?: return@withContext ResourceState.AuthRequired
        runCatching {
            val resp = api.hidePost(
                bearerToken = "Bearer ${session.accessToken}",
                request = SupabaseHiddenPostRequest(session.userId, postId)
            )
            if (resp.isSuccessful) ResourceState.Success(Unit)
            else if (resp.code() == 401) ResourceState.AuthRequired
            else ResourceState.Error(parseError(resp, "hidden_posts"))
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun reportPost(postId: String, reason: String): ResourceState<Unit> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            val session = requireSession() ?: return@withContext ResourceState.AuthRequired
            runCatching {
                val resp = api.reportPost(
                    bearerToken = "Bearer ${session.accessToken}",
                    request = SupabaseReportPostRequest(postId, session.userId, reason.trim())
                )
                if (resp.isSuccessful) ResourceState.Success(Unit)
                else if (resp.code() == 401) ResourceState.AuthRequired
                else ResourceState.Error(parseError(resp, "post_reports"))
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun fetchPostById(postId: String): ResourceState<VybalPost?> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
        runCatching {
            val resp = api.getPostsByIds(optionalBearer(), "in.($postId)")
            if (resp.isSuccessful) {
                ResourceState.Success(resp.body()?.firstOrNull()?.toDomain())
            } else {
                ResourceState.Error(parseError(resp, "posts"))
            }
        }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
    }

    suspend fun fetchProfileByUsernameOrId(usernameOrId: String, isUserId: Boolean): ResourceState<VybalProfile?> =
        withContext(Dispatchers.IO) {
            if (!isConfiguredProvider()) return@withContext ResourceState.NotConfigured
            runCatching {
                val resp = if (isUserId) {
                    api.getProfilesByIds(optionalBearer(), "in.($usernameOrId)")
                } else {
                    api.getProfileByUsername(optionalBearer(), "eq.${usernameOrId.lowercase()}")
                }
                if (resp.isSuccessful) {
                    ResourceState.Success(resp.body()?.firstOrNull()?.toDomain())
                } else {
                    ResourceState.Error(parseError(resp, "profiles"))
                }
            }.getOrElse { ResourceState.Error(mapNetworkError(it)) }
        }

    suspend fun buildPostInsights(post: VybalPost, summary: PostEngagementSummary?): PostInsights {
        return PostInsights(
            postId = post.id,
            likesCount = summary?.totalReactions ?: 0,
            reactionBreakdown = summary?.reactionCounts.orEmpty(),
            commentsCount = summary?.commentCount ?: 0,
            repostsCount = summary?.repostCount ?: 0,
            savesCount = if (summary?.isSavedByMe == true) 1 else null, // Saved posts are private per-user via RLS
            sharesCount = summary?.shareCount ?: post.shareCount,
            viewsCount = summary?.viewCount ?: post.viewCount
        )
    }

    // -------------------------------------------------------------------------
    // Notifications helpers
    // -------------------------------------------------------------------------
    suspend fun markNotificationRead(notificationId: String) = withContext(Dispatchers.IO) {
        val session = validSessionProvider(false) ?: return@withContext
        runCatching {
            api.markNotificationRead("Bearer ${session.accessToken}", "eq.$notificationId")
        }
    }

    suspend fun markAllNotificationsRead() = withContext(Dispatchers.IO) {
        val session = validSessionProvider(false) ?: return@withContext
        runCatching {
            api.markAllNotificationsRead("Bearer ${session.accessToken}")
        }
    }

    suspend fun notifyMentionsInPost(postId: String, text: String) = withContext(Dispatchers.IO) {
        val session = validSessionProvider(false) ?: return@withContext
        notifyMentionedUsers("Bearer ${session.accessToken}", session.userId, postId, text)
    }

    private suspend fun notifyMentionedUsers(
        bearer: String,
        actorId: String,
        postId: String,
        text: String
    ) {
        val mentions = RichSocialText.extractMentions(text)
        mentions.forEach { username ->
            runCatching {
                val resp = api.getProfileByUsername(bearer, "eq.$username")
                val target = resp.body()?.firstOrNull()
                if (target != null && target.id != actorId) {
                    notifySilently(bearer, target.id, actorId, postId, "mention", "mentioned you")
                }
            }
        }
    }

    private suspend fun notifySilently(
        bearer: String,
        targetUserId: String,
        actorId: String,
        postId: String?,
        type: String,
        message: String
    ) {
        runCatching {
            api.createNotification(
                bearerToken = bearer,
                request = SupabaseCreateNotificationRequest(
                    userId = targetUserId,
                    actorId = actorId,
                    postId = postId,
                    type = type,
                    message = message
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

    private fun mapNetworkError(error: Throwable): String =
        if (error is IOException) "Network unavailable. Check your internet connection."
        else error.localizedMessage ?: "Engagement action failed."

    private fun parseError(response: Response<*>, tableHint: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        if (response.code() == 404 || raw.contains("PGRST205", ignoreCase = true) || raw.contains("PGRST204", ignoreCase = true)) {
            return "Supabase table/column for '$tableHint' requires running the Checkpoint 3 SQL migration."
        }
        if (response.code() == 403 || raw.contains("42501")) {
            return "Permission denied by Supabase Row Level Security policy."
        }
        val parsed = runCatching { moshi.adapter(SupabaseErrorDto::class.java).fromJson(raw) }.getOrNull()
        return parsed?.message ?: parsed?.msg ?: parsed?.error ?: "Request failed (${response.code()})"
    }
}
