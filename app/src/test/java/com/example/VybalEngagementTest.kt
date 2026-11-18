package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import com.example.core.network.SupabaseCommentDto
import com.example.core.network.SupabaseCommentLikeDto
import com.example.core.network.SupabaseCreateCollectionRequest
import com.example.core.network.SupabaseCreateCommentRequest
import com.example.core.network.SupabaseCreateFollowRequest
import com.example.core.network.SupabaseCreateNotificationRequest
import com.example.core.network.SupabaseCreateRepostRequest
import com.example.core.network.SupabaseEngagementApi
import com.example.core.network.SupabaseFollowDto
import com.example.core.network.SupabaseHiddenPostDto
import com.example.core.network.SupabaseHiddenPostRequest
import com.example.core.network.SupabaseMarkNotificationReadRequest
import com.example.core.network.SupabasePinCommentRequest
import com.example.core.network.SupabasePostControlPatch
import com.example.core.network.SupabasePostDto
import com.example.core.network.SupabaseProfileDto
import com.example.core.network.SupabaseReactionDto
import com.example.core.network.SupabaseReportPostRequest
import com.example.core.network.SupabaseRepostDto
import com.example.core.network.SupabaseSavedCollectionDto
import com.example.core.network.SupabaseSavedPostDto
import com.example.core.network.SupabaseUpdateCommentRequest
import com.example.core.network.SupabaseUpsertReactionRequest
import com.example.core.network.SupabaseUpsertSavedPostRequest
import com.example.core.share.VybalShareHelper
import com.example.core.text.RichSocialText
import com.example.data.auth.SessionStorage
import com.example.data.model.PostEngagementSummary
import com.example.data.model.ReactionType
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.model.VybalPost
import com.example.data.social.EngagementRepository
import com.example.ui.components.VybalInteractivePostCard
import com.example.ui.theme.VybalTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

private class FakeEngagementSessionStorage(initial: UserSession? = null) : SessionStorage {
    private val _session = MutableStateFlow(initial)
    private val _dataSaver = MutableStateFlow(false)
    override val sessionFlow: Flow<UserSession?> = _session
    override val dataSaverFlow: Flow<Boolean> = _dataSaver
    override suspend fun currentSession(): UserSession? = _session.value
    override suspend fun saveSession(session: UserSession) { _session.value = session }
    override suspend fun clearSession() { _session.value = null }
    override suspend fun setDataSaver(enabled: Boolean) { _dataSaver.value = enabled }
}

private class FakeSupabaseEngagementApi : SupabaseEngagementApi {
    val reactions = mutableListOf<SupabaseReactionDto>()
    val comments = mutableListOf<SupabaseCommentDto>()
    val commentLikes = mutableListOf<SupabaseCommentLikeDto>()
    val collections = mutableListOf<SupabaseSavedCollectionDto>()
    val savedPosts = mutableListOf<SupabaseSavedPostDto>()
    val reposts = mutableListOf<SupabaseRepostDto>()
    val follows = mutableListOf<SupabaseFollowDto>()
    val hiddenPosts = mutableListOf<SupabaseHiddenPostRequest>()
    val reports = mutableListOf<SupabaseReportPostRequest>()
    val notifications = mutableListOf<SupabaseCreateNotificationRequest>()
    val profiles = mutableListOf<SupabaseProfileDto>()
    val posts = mutableListOf<SupabasePostDto>()
    var lastPostPatch: SupabasePostControlPatch? = null
    var simulateMissingReactionTable = false
    private var idSeq = 1

    override suspend fun getReactionsForPosts(
        bearerToken: String?,
        postIdIn: String,
        select: String
    ): Response<List<SupabaseReactionDto>> = Response.success(reactions.toList())

    override suspend fun upsertReaction(
        bearerToken: String,
        prefer: String,
        onConflict: String,
        request: SupabaseUpsertReactionRequest
    ): Response<List<SupabaseReactionDto>> {
        if (simulateMissingReactionTable) {
            return Response.error(
                404,
                """{"code":"PGRST205","message":"Could not find the table 'public.post_reactions'"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        }
        reactions.removeAll { it.postId == request.postId && it.userId == request.userId }
        val dto = SupabaseReactionDto(
            id = "react-${idSeq++}",
            postId = request.postId,
            userId = request.userId,
            reaction = request.reaction
        )
        reactions.add(dto)
        return Response.success(listOf(dto))
    }

    override suspend fun deleteReaction(
        bearerToken: String,
        postIdEq: String,
        userIdEq: String
    ): Response<Unit> {
        val postId = postIdEq.removePrefix("eq.")
        val userId = userIdEq.removePrefix("eq.")
        reactions.removeAll { it.postId == postId && it.userId == userId }
        return Response.success(Unit)
    }

    override suspend fun getComments(
        bearerToken: String?,
        postIdEq: String,
        select: String,
        order: String,
        limit: Int,
        offset: Int
    ): Response<List<SupabaseCommentDto>> {
        val postId = postIdEq.removePrefix("eq.")
        val list = comments.filter { it.postId == postId }
            .sortedWith(compareByDescending<SupabaseCommentDto> { it.isPinned == true }.thenBy { it.createdAt })
            .drop(offset)
            .take(limit)
        return Response.success(list)
    }

    override suspend fun getCommentCountsForPosts(
        bearerToken: String?,
        postIdIn: String,
        select: String
    ): Response<List<SupabaseCommentDto>> = Response.success(comments.toList())

    override suspend fun createComment(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreateCommentRequest
    ): Response<List<SupabaseCommentDto>> {
        val dto = SupabaseCommentDto(
            id = "cmt-${idSeq++}",
            postId = request.postId,
            userId = request.userId,
            parentCommentId = request.parentCommentId,
            content = request.content,
            isPinned = false,
            createdAt = "2026-11-18T12:00:00Z"
        )
        comments.add(dto)
        return Response.success(listOf(dto))
    }

    override suspend fun updateComment(
        bearerToken: String,
        prefer: String,
        idEq: String,
        request: SupabaseUpdateCommentRequest
    ): Response<List<SupabaseCommentDto>> {
        val id = idEq.removePrefix("eq.")
        val idx = comments.indexOfFirst { it.id == id }
        if (idx >= 0) {
            comments[idx] = comments[idx].copy(content = request.content)
            return Response.success(listOf(comments[idx]))
        }
        return Response.success(emptyList())
    }

    override suspend fun pinComment(
        bearerToken: String,
        prefer: String,
        idEq: String,
        request: SupabasePinCommentRequest
    ): Response<List<SupabaseCommentDto>> {
        val id = idEq.removePrefix("eq.")
        val idx = comments.indexOfFirst { it.id == id }
        if (idx >= 0) {
            comments[idx] = comments[idx].copy(isPinned = request.isPinned)
            return Response.success(listOf(comments[idx]))
        }
        return Response.success(emptyList())
    }

    override suspend fun deleteComment(bearerToken: String, idEq: String): Response<Unit> {
        val id = idEq.removePrefix("eq.")
        comments.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun getCommentLikes(
        bearerToken: String?,
        commentIdIn: String,
        select: String
    ): Response<List<SupabaseCommentLikeDto>> = Response.success(commentLikes.toList())

    override suspend fun likeComment(
        bearerToken: String,
        prefer: String,
        request: SupabaseCommentLikeDto
    ): Response<Unit> {
        if (commentLikes.none { it.commentId == request.commentId && it.userId == request.userId }) {
            commentLikes.add(request)
        }
        return Response.success(Unit)
    }

    override suspend fun unlikeComment(
        bearerToken: String,
        commentIdEq: String,
        userIdEq: String
    ): Response<Unit> {
        val cid = commentIdEq.removePrefix("eq.")
        val uid = userIdEq.removePrefix("eq.")
        commentLikes.removeAll { it.commentId == cid && it.userId == uid }
        return Response.success(Unit)
    }

    override suspend fun getCollections(
        bearerToken: String,
        userIdEq: String,
        order: String
    ): Response<List<SupabaseSavedCollectionDto>> {
        val uid = userIdEq.removePrefix("eq.")
        return Response.success(collections.filter { it.userId == uid })
    }

    override suspend fun createCollection(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreateCollectionRequest
    ): Response<List<SupabaseSavedCollectionDto>> {
        val dto = SupabaseSavedCollectionDto(
            id = "col-${idSeq++}",
            userId = request.userId,
            name = request.name,
            createdAt = "2026-11-18T12:00:00Z"
        )
        collections.add(dto)
        return Response.success(listOf(dto))
    }

    override suspend fun getSavedPosts(
        bearerToken: String,
        userIdEq: String,
        order: String
    ): Response<List<SupabaseSavedPostDto>> {
        val uid = userIdEq.removePrefix("eq.")
        return Response.success(savedPosts.filter { it.userId == uid })
    }

    override suspend fun upsertSavedPost(
        bearerToken: String,
        prefer: String,
        onConflict: String,
        request: SupabaseUpsertSavedPostRequest
    ): Response<List<SupabaseSavedPostDto>> {
        savedPosts.removeAll { it.userId == request.userId && it.postId == request.postId }
        val dto = SupabaseSavedPostDto(
            id = "sav-${idSeq++}",
            userId = request.userId,
            postId = request.postId,
            collectionId = request.collectionId,
            createdAt = "2026-11-18T12:00:00Z"
        )
        savedPosts.add(dto)
        return Response.success(listOf(dto))
    }

    override suspend fun deleteSavedPost(
        bearerToken: String,
        userIdEq: String,
        postIdEq: String
    ): Response<Unit> {
        val uid = userIdEq.removePrefix("eq.")
        val pid = postIdEq.removePrefix("eq.")
        savedPosts.removeAll { it.userId == uid && it.postId == pid }
        return Response.success(Unit)
    }

    override suspend fun getRepostsForPosts(
        bearerToken: String?,
        postIdIn: String,
        select: String
    ): Response<List<SupabaseRepostDto>> = Response.success(reposts.toList())

    override suspend fun createRepost(
        bearerToken: String,
        prefer: String,
        onConflict: String,
        request: SupabaseCreateRepostRequest
    ): Response<List<SupabaseRepostDto>> {
        reposts.removeAll { it.userId == request.userId && it.postId == request.postId }
        val dto = SupabaseRepostDto(
            id = "rep-${idSeq++}",
            userId = request.userId,
            postId = request.postId,
            caption = request.caption,
            createdAt = "2026-11-18T12:00:00Z"
        )
        reposts.add(dto)
        return Response.success(listOf(dto))
    }

    override suspend fun deleteRepost(
        bearerToken: String,
        userIdEq: String,
        postIdEq: String
    ): Response<Unit> {
        val uid = userIdEq.removePrefix("eq.")
        val pid = postIdEq.removePrefix("eq.")
        reposts.removeAll { it.userId == uid && it.postId == pid }
        return Response.success(Unit)
    }

    override suspend fun getFollowers(
        bearerToken: String?,
        followingIdEq: String,
        select: String
    ): Response<List<SupabaseFollowDto>> {
        val target = followingIdEq.removePrefix("eq.")
        return Response.success(follows.filter { it.followingId == target })
    }

    override suspend fun getFollowing(
        bearerToken: String?,
        followerIdEq: String,
        select: String
    ): Response<List<SupabaseFollowDto>> {
        val source = followerIdEq.removePrefix("eq.")
        return Response.success(follows.filter { it.followerId == source })
    }

    override suspend fun followUser(
        bearerToken: String,
        prefer: String,
        request: SupabaseCreateFollowRequest
    ): Response<Unit> {
        if (follows.none { it.followerId == request.followerId && it.followingId == request.followingId }) {
            follows.add(
                SupabaseFollowDto(
                    id = "fol-${idSeq++}",
                    followerId = request.followerId,
                    followingId = request.followingId,
                    status = request.status
                )
            )
        }
        return Response.success(Unit)
    }

    override suspend fun unfollowUser(
        bearerToken: String,
        followerIdEq: String,
        followingIdEq: String
    ): Response<Unit> {
        val f1 = followerIdEq.removePrefix("eq.")
        val f2 = followingIdEq.removePrefix("eq.")
        follows.removeAll { it.followerId == f1 && it.followingId == f2 }
        return Response.success(Unit)
    }

    override suspend fun getProfilesByIds(
        bearerToken: String?,
        idIn: String,
        select: String
    ): Response<List<SupabaseProfileDto>> = Response.success(profiles.toList())

    override suspend fun getProfileByUsername(
        bearerToken: String?,
        usernameEq: String,
        limit: Int
    ): Response<List<SupabaseProfileDto>> {
        val handle = usernameEq.removePrefix("eq.")
        return Response.success(profiles.filter { it.username.equals(handle, ignoreCase = true) })
    }

    override suspend fun getPostsByIds(
        bearerToken: String?,
        idIn: String,
        select: String
    ): Response<List<SupabasePostDto>> = Response.success(posts.toList())

    override suspend fun patchPostControls(
        bearerToken: String,
        prefer: String,
        idEq: String,
        patch: SupabasePostControlPatch
    ): Response<List<SupabasePostDto>> {
        lastPostPatch = patch
        return Response.success(posts.take(1))
    }

    override suspend fun getHiddenPosts(
        bearerToken: String,
        userIdEq: String,
        select: String
    ): Response<List<SupabaseHiddenPostDto>> =
        Response.success(hiddenPosts.map { SupabaseHiddenPostDto(it.postId) })

    override suspend fun hidePost(
        bearerToken: String,
        prefer: String,
        request: SupabaseHiddenPostRequest
    ): Response<Unit> {
        hiddenPosts.add(request)
        return Response.success(Unit)
    }

    override suspend fun reportPost(
        bearerToken: String,
        prefer: String,
        request: SupabaseReportPostRequest
    ): Response<Unit> {
        reports.add(request)
        return Response.success(Unit)
    }

    override suspend fun createNotification(
        bearerToken: String,
        request: SupabaseCreateNotificationRequest
    ): Response<Unit> {
        notifications.add(request)
        return Response.success(Unit)
    }

    override suspend fun markNotificationRead(
        bearerToken: String,
        idEq: String,
        request: SupabaseMarkNotificationReadRequest
    ): Response<Unit> = Response.success(Unit)

    override suspend fun markAllNotificationsRead(
        bearerToken: String,
        isReadEq: String,
        request: SupabaseMarkNotificationReadRequest
    ): Response<Unit> = Response.success(Unit)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VybalEngagementTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val activeSession = UserSession(
        accessToken = "jwt-user-a",
        refreshToken = "ref-user-a",
        userId = "user-a",
        email = "alex@vybal.app"
    )

    @Test
    fun `RichSocialText extracts mentions hashtags and applies hashtag suggestions`() {
        val text = "Shoutout to @Nova_Creator and @alex_v! Loving #VybalNight and #Design2026 #vybalnight"
        assertEquals(listOf("nova_creator", "alex_v"), RichSocialText.extractMentions(text))
        assertEquals(listOf("vybalnight", "design2026"), RichSocialText.extractHashtags(text))

        assertEquals("vyb", RichSocialText.activeHashtagPrefix("Building something #vyb"))
        assertNull(RichSocialText.activeHashtagPrefix("Building something #vyb "))
        assertEquals(
            "Building something #vybal ",
            RichSocialText.applyHashtagSuggestion("Building something #vyb", "vybal")
        )
    }

    @Test
    fun `VybalShareHelper generates shareable link and creator attribution`() {
        val post = VybalPost(
            id = "post-42",
            userId = "creator-1",
            text = "Midnight studio session #vybal",
            mediaUrl = null,
            visibility = "public",
            createdAt = "2026-11-18T12:00:00Z",
            authorUsername = "nova"
        )
        assertEquals("https://vybal.app/post/post-42", VybalShareHelper.buildPostLink("post-42"))
        val payload = VybalShareHelper.buildSharePayload(post, "nova")
        assertTrue(payload.contains("@nova on VYBAL"))
        assertTrue(payload.contains("https://vybal.app/post/post-42"))
    }

    @Test
    fun `reactions prevent duplicates switch reaction types and generate notifications`() = runTest {
        val api = FakeSupabaseEngagementApi()
        val repo = EngagementRepository(
            api = api,
            sessionStore = FakeEngagementSessionStorage(activeSession),
            isConfiguredProvider = { true }
        )

        val likeRes = repo.setReaction("post-1", "creator-2", ReactionType.Like)
        assertTrue(likeRes is ResourceState.Success)
        assertEquals(1, api.reactions.size)
        assertEquals("like", api.reactions.first().reaction)
        assertEquals("like", api.notifications.last().type)

        // Switching to Love replaces Like (no duplicates)
        val loveRes = repo.setReaction("post-1", "creator-2", ReactionType.Love)
        assertTrue(loveRes is ResourceState.Success)
        assertEquals(1, api.reactions.size)
        assertEquals("love", api.reactions.first().reaction)
        assertEquals("reaction", api.notifications.last().type)

        val summaries = repo.fetchSummariesForPosts(
            listOf(
                VybalPost(
                    id = "post-1",
                    userId = "creator-2",
                    text = "Hello",
                    mediaUrl = null,
                    visibility = "public",
                    createdAt = "2026-11-18T12:00:00Z"
                )
            )
        )
        val summary = summaries["post-1"]!!
        assertEquals(ReactionType.Love, summary.myReaction)
        assertEquals(1, summary.totalReactions)

        // Removing reaction clears it
        repo.setReaction("post-1", "creator-2", null)
        assertTrue(api.reactions.isEmpty())
    }

    @Test
    fun `threaded comments support add reply edit pin like delete and mention notifications`() = runTest {
        val api = FakeSupabaseEngagementApi().apply {
            profiles.add(SupabaseProfileDto(id = "user-a", username = "alex"))
            profiles.add(SupabaseProfileDto(id = "user-b", username = "nova"))
        }
        val repo = EngagementRepository(
            api = api,
            sessionStore = FakeEngagementSessionStorage(activeSession),
            isConfiguredProvider = { true }
        )

        val added = repo.addComment("post-1", "creator-9", "Great shot @nova!")
        assertTrue(added is ResourceState.Success)
        val rootComment = (added as ResourceState.Success).data
        assertTrue(api.notifications.any { it.type == "comment" && it.userId == "creator-9" })
        assertTrue(api.notifications.any { it.type == "mention" && it.userId == "user-b" })

        val reply = repo.addComment(
            postId = "post-1",
            postOwnerId = "creator-9",
            content = "Thanks!",
            parentCommentId = rootComment.id,
            parentCommentAuthorId = "user-b"
        )
        assertTrue(reply is ResourceState.Success)
        assertTrue(api.notifications.any { it.type == "reply" && it.userId == "user-b" })

        repo.editComment(rootComment.id, "Updated great shot @nova!")
        repo.setCommentPinned(rootComment.id, true)
        repo.toggleCommentLike(rootComment.id, "user-b", "post-1", like = true)

        val fetched = repo.fetchComments("post-1")
        assertTrue(fetched is ResourceState.Success)
        val list = (fetched as ResourceState.Success).data
        assertEquals(2, list.size)
        assertTrue(list.first().isPinned)
        assertEquals("Updated great shot @nova!", list.first().content)
        assertEquals(1, list.first().likeCount)

        repo.deleteComment(rootComment.id)
        assertEquals(1, api.comments.size)
    }

    @Test
    fun `private saved collections and reposts avoid duplicates and stay private`() = runTest {
        val api = FakeSupabaseEngagementApi().apply {
            posts.add(SupabasePostDto(id = "post-7", userId = "creator-2", content = "Saved inspiration"))
        }
        val repo = EngagementRepository(
            api = api,
            sessionStore = FakeEngagementSessionStorage(activeSession),
            isConfiguredProvider = { true }
        )

        val colRes = repo.createSavedCollection("Moodboard")
        assertTrue(colRes is ResourceState.Success)
        val collectionId = (colRes as ResourceState.Success).data.id

        repo.savePost("post-7", collectionId)
        repo.savePost("post-7", collectionId) // duplicate upsert should not duplicate row
        assertEquals(1, api.savedPosts.size)

        val savedDetails = repo.fetchSavedPostsWithDetails()
        assertTrue(savedDetails is ResourceState.Success)
        val (entries, savedPostList) = (savedDetails as ResourceState.Success).data
        assertEquals(collectionId, entries.first().collectionId)
        assertEquals("post-7", savedPostList.first().id)

        repo.unsavePost("post-7")
        assertTrue(api.savedPosts.isEmpty())

        // Reposts
        repo.createRepost("post-7", "creator-2", "Worth sharing")
        repo.createRepost("post-7", "creator-2", "Updated caption")
        assertEquals(1, api.reposts.size)
        assertTrue(api.notifications.any { it.type == "repost" && it.userId == "creator-2" })

        repo.removeRepost("post-7")
        assertTrue(api.reposts.isEmpty())
    }

    @Test
    fun `follow system prevents self follow computes mutuals and toggles follow state`() = runTest {
        val api = FakeSupabaseEngagementApi().apply {
            follows.add(SupabaseFollowDto(followerId = "user-a", followingId = "mutual-1", status = "accepted"))
            follows.add(SupabaseFollowDto(followerId = "mutual-1", followingId = "creator-2", status = "accepted"))
        }
        val repo = EngagementRepository(
            api = api,
            sessionStore = FakeEngagementSessionStorage(activeSession),
            isConfiguredProvider = { true }
        )

        val selfFollow = repo.toggleFollow("user-a", follow = true)
        assertTrue(selfFollow is ResourceState.Error)
        assertEquals("You cannot follow your own account.", (selfFollow as ResourceState.Error).message)

        val followRes = repo.toggleFollow("creator-2", follow = true)
        assertTrue(followRes is ResourceState.Success)
        assertTrue(api.notifications.any { it.type == "follow" && it.userId == "creator-2" })

        val statsRes = repo.fetchFollowStats("creator-2")
        assertTrue(statsRes is ResourceState.Success)
        val stats = (statsRes as ResourceState.Success).data
        assertEquals(2, stats.followersCount)
        assertEquals(1, stats.mutualCount)
        assertTrue(stats.isFollowing)

        repo.toggleFollow("creator-2", follow = false)
        val afterUnfollow = (repo.fetchFollowStats("creator-2") as ResourceState.Success).data
        assertFalse(afterUnfollow.isFollowing)
    }

    @Test
    fun `missing SQL migration table returns clear actionable error`() = runTest {
        val api = FakeSupabaseEngagementApi().apply { simulateMissingReactionTable = true }
        val repo = EngagementRepository(
            api = api,
            sessionStore = FakeEngagementSessionStorage(activeSession),
            isConfiguredProvider = { true }
        )

        val res = repo.setReaction("post-1", "creator-2", ReactionType.Like)
        assertTrue(res is ResourceState.Error)
        assertTrue((res as ResourceState.Error).message.contains("Checkpoint 3 SQL migration"))
    }

    @Test
    fun `VybalInteractivePostCard renders all engagement controls and dispatches actions`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageLoader = ImageLoader(context)
        val post = VybalPost(
            id = "p100",
            userId = "u1",
            text = "Testing #vybal engagement with @alex",
            mediaUrl = null,
            visibility = "public",
            createdAt = "2026-11-18T12:00:00Z",
            authorUsername = "nova",
            isPinned = true,
            shareCount = 2
        )
        val summary = PostEngagementSummary(
            postId = "p100",
            reactionCounts = mapOf(ReactionType.Like to 3, ReactionType.Support to 1),
            myReaction = ReactionType.Like,
            commentCount = 4,
            repostCount = 1,
            isRepostedByMe = true,
            isSavedByMe = true,
            shareCount = 2
        )
        var likeClicked = false
        var commentClicked = false
        var shareClicked = false

        composeTestRule.setContent {
            VybalTheme {
                VybalInteractivePostCard(
                    post = post,
                    authorHandle = "nova",
                    summary = summary,
                    imageLoader = imageLoader,
                    dataSaverEnabled = false,
                    onAuthorClick = {},
                    onMentionClick = {},
                    onHashtagClick = {},
                    onReactionSelect = { _, _ -> likeClicked = true },
                    onCommentClick = { commentClicked = true },
                    onRepostClick = {},
                    onShareClick = { shareClicked = true },
                    onQuickSaveClick = {},
                    onSaveCollectionClick = {},
                    onOptionsClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("like_button_p100").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("comment_button_p100").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("repost_button_p100").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_button_p100").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("bookmark_button_p100").assertIsDisplayed()
        composeTestRule.onNodeWithTag("post_options_p100").assertIsDisplayed()

        assertTrue(likeClicked)
        assertTrue(commentClicked)
        assertTrue(shareClicked)
    }
}
