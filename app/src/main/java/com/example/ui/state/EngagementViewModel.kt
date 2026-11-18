package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.config.VybalSchemaContract
import com.example.core.di.VybalAppContainer
import com.example.core.text.RichSocialText
import com.example.data.model.FollowStats
import com.example.data.model.PostEngagementSummary
import com.example.data.model.PostInsights
import com.example.data.model.ReactionType
import com.example.data.model.ResourceState
import com.example.data.model.SavedCollection
import com.example.data.model.SavedPostEntry
import com.example.data.model.VybalComment
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsSheetState(
    val post: VybalPost? = null,
    val comments: List<VybalComment> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val replyingTo: VybalComment? = null,
    val editingComment: VybalComment? = null
)

data class UserProfileSheetState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val profile: VybalProfile? = null,
    val followStats: FollowStats? = null,
    val isFollowBusy: Boolean = false,
    val errorMessage: String? = null
)

class EngagementViewModel(
    private val container: VybalAppContainer
) : ViewModel() {

    private val repo get() = container.engagementRepository

    private val _summaries = MutableStateFlow<Map<String, PostEngagementSummary>>(emptyMap())
    val summaries: StateFlow<Map<String, PostEngagementSummary>> = _summaries.asStateFlow()

    private val _usernames = MutableStateFlow<Map<String, String>>(emptyMap())
    val usernames: StateFlow<Map<String, String>> = _usernames.asStateFlow()

    private val _profileBadges =
        MutableStateFlow<Map<String, com.example.data.model.ProfileIdentityBadge>>(emptyMap())
    val profileBadges: StateFlow<Map<String, com.example.data.model.ProfileIdentityBadge>> =
        _profileBadges.asStateFlow()

    private val _hiddenPostIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPostIds: StateFlow<Set<String>> = _hiddenPostIds.asStateFlow()

    private val _knownHashtags = MutableStateFlow<List<String>>(emptyList())
    val knownHashtags: StateFlow<List<String>> = _knownHashtags.asStateFlow()

    private val _commentsSheet = MutableStateFlow(CommentsSheetState())
    val commentsSheet: StateFlow<CommentsSheetState> = _commentsSheet.asStateFlow()

    private val _saveSheetPost = MutableStateFlow<VybalPost?>(null)
    val saveSheetPost: StateFlow<VybalPost?> = _saveSheetPost.asStateFlow()

    private val _collections = MutableStateFlow<List<SavedCollection>>(emptyList())
    val collections: StateFlow<List<SavedCollection>> = _collections.asStateFlow()

    private val _savedEntries = MutableStateFlow<List<SavedPostEntry>>(emptyList())
    val savedEntries: StateFlow<List<SavedPostEntry>> = _savedEntries.asStateFlow()

    private val _savedPosts = MutableStateFlow<List<VybalPost>>(emptyList())
    val savedPosts: StateFlow<List<VybalPost>> = _savedPosts.asStateFlow()

    private val _repostTarget = MutableStateFlow<VybalPost?>(null)
    val repostTarget: StateFlow<VybalPost?> = _repostTarget.asStateFlow()

    private val _optionsTarget = MutableStateFlow<VybalPost?>(null)
    val optionsTarget: StateFlow<VybalPost?> = _optionsTarget.asStateFlow()

    private val _insightsTarget = MutableStateFlow<PostInsights?>(null)
    val insightsTarget: StateFlow<PostInsights?> = _insightsTarget.asStateFlow()

    private val _profileSheet = MutableStateFlow(UserProfileSheetState())
    val profileSheet: StateFlow<UserProfileSheetState> = _profileSheet.asStateFlow()

    private val _myFollowStats = MutableStateFlow<FollowStats?>(null)
    val myFollowStats: StateFlow<FollowStats?> = _myFollowStats.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun loadEngagementForPosts(posts: List<VybalPost>) {
        if (posts.isEmpty()) return
        // Update hashtag suggestions from real posts
        val tags = posts.flatMap { RichSocialText.extractHashtags(it.text) }.distinct()
        if (tags.isNotEmpty()) {
            _knownHashtags.update { (it + tags).distinct().take(40) }
        }
        viewModelScope.launch {
            val fetched = repo.fetchSummariesForPosts(posts)
            if (fetched.isNotEmpty()) {
                _summaries.update { it + fetched }
            }
            val badges = repo.resolveIdentityBadgesForIds(posts.map { it.userId })
            if (badges.isNotEmpty()) {
                _profileBadges.update { it + badges }
                _usernames.update { it + badges.mapValues { entry -> entry.value.username } }
            }
        }
    }

    fun refreshUserScopedData(userId: String?) {
        if (userId.isNullOrBlank()) {
            _hiddenPostIds.value = emptySet()
            _collections.value = emptyList()
            _savedEntries.value = emptyList()
            _savedPosts.value = emptyList()
            _myFollowStats.value = null
            return
        }
        viewModelScope.launch {
            _hiddenPostIds.value = repo.fetchHiddenPostIds()
            when (val stats = repo.fetchFollowStats(userId)) {
                is ResourceState.Success -> _myFollowStats.value = stats.data
                else -> Unit
            }
            refreshSavedLibrary()
        }
    }

    fun refreshSavedLibrary() {
        viewModelScope.launch {
            when (val cols = repo.fetchSavedCollections()) {
                is ResourceState.Success -> _collections.value = cols.data
                else -> Unit
            }
            when (val saved = repo.fetchSavedPostsWithDetails()) {
                is ResourceState.Success -> {
                    _savedEntries.value = saved.data.first
                    _savedPosts.value = saved.data.second
                    if (saved.data.second.isNotEmpty()) {
                        loadEngagementForPosts(saved.data.second)
                    }
                }
                else -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Optimistic Like & Reaction with Rollback
    // -------------------------------------------------------------------------
    fun toggleLikeOrReaction(
        post: VybalPost,
        targetReaction: ReactionType,
        isDoubleTap: Boolean = false,
        onRequireAuth: () -> Unit
    ) {
        val current = _summaries.value[post.id] ?: PostEngagementSummary(postId = post.id)
        val nextReaction = when {
            isDoubleTap -> current.myReaction ?: ReactionType.Like
            current.myReaction == targetReaction -> null
            else -> targetReaction
        }
        if (isDoubleTap && current.myReaction != null) return // Already liked; animate heart only

        val updatedCounts = current.reactionCounts.toMutableMap()
        current.myReaction?.let { prev ->
            val count = (updatedCounts[prev] ?: 1) - 1
            if (count <= 0) updatedCounts.remove(prev) else updatedCounts[prev] = count
        }
        nextReaction?.let { next ->
            updatedCounts[next] = (updatedCounts[next] ?: 0) + 1
        }
        val optimistic = current.copy(reactionCounts = updatedCounts, myReaction = nextReaction)
        _summaries.update { it + (post.id to optimistic) }

        viewModelScope.launch {
            when (val result = repo.setReaction(post.id, post.userId, nextReaction)) {
                is ResourceState.Success -> Unit
                ResourceState.AuthRequired -> {
                    _summaries.update { it + (post.id to current) }
                    onRequireAuth()
                }
                is ResourceState.Error -> {
                    _summaries.update { it + (post.id to current) }
                    _statusMessage.value = result.message
                }
                else -> _summaries.update { it + (post.id to current) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Comments Sheet
    // -------------------------------------------------------------------------
    fun openComments(post: VybalPost) {
        _commentsSheet.value = CommentsSheetState(post = post, isLoading = true)
        viewModelScope.launch {
            when (val res = repo.fetchComments(post.id, offset = 0)) {
                is ResourceState.Success -> {
                    _commentsSheet.update {
                        it.copy(
                            comments = res.data,
                            isLoading = false,
                            hasMore = res.data.size >= VybalSchemaContract.COMMENTS_PAGE_SIZE,
                            errorMessage = null
                        )
                    }
                }
                is ResourceState.Error -> {
                    _commentsSheet.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                else -> _commentsSheet.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadMoreComments() {
        val state = _commentsSheet.value
        val post = state.post ?: return
        if (state.isLoading || !state.hasMore) return
        _commentsSheet.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val res = repo.fetchComments(post.id, offset = state.comments.size)) {
                is ResourceState.Success -> {
                    _commentsSheet.update {
                        it.copy(
                            comments = (it.comments + res.data).distinctBy { c -> c.id },
                            isLoading = false,
                            hasMore = res.data.size >= VybalSchemaContract.COMMENTS_PAGE_SIZE
                        )
                    }
                }
                is ResourceState.Error -> {
                    _commentsSheet.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                else -> _commentsSheet.update { it.copy(isLoading = false) }
            }
        }
    }

    fun closeComments() {
        _commentsSheet.value = CommentsSheetState()
    }

    fun setReplyingTo(comment: VybalComment?) {
        _commentsSheet.update { it.copy(replyingTo = comment, editingComment = null) }
    }

    fun setEditingComment(comment: VybalComment?) {
        _commentsSheet.update { it.copy(editingComment = comment, replyingTo = null) }
    }

    fun submitComment(text: String, onRequireAuth: () -> Unit) {
        val state = _commentsSheet.value
        val post = state.post ?: return
        val editing = state.editingComment
        if (text.isBlank()) return

        _commentsSheet.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            if (editing != null) {
                when (val res = repo.editComment(editing.id, text)) {
                    is ResourceState.Success -> {
                        _commentsSheet.update { st ->
                            st.copy(
                                isSubmitting = false,
                                editingComment = null,
                                comments = st.comments.map {
                                    if (it.id == editing.id) it.copy(content = text.trim()) else it
                                }
                            )
                        }
                    }
                    ResourceState.AuthRequired -> {
                        _commentsSheet.update { it.copy(isSubmitting = false) }
                        onRequireAuth()
                    }
                    is ResourceState.Error -> {
                        _commentsSheet.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    }
                    else -> _commentsSheet.update { it.copy(isSubmitting = false) }
                }
            } else {
                val parent = state.replyingTo
                when (
                    val res = repo.addComment(
                        postId = post.id,
                        postOwnerId = post.userId,
                        content = text,
                        parentCommentId = parent?.id,
                        parentCommentAuthorId = parent?.userId
                    )
                ) {
                    is ResourceState.Success -> {
                        _commentsSheet.update { st ->
                            st.copy(
                                isSubmitting = false,
                                replyingTo = null,
                                comments = st.comments + res.data
                            )
                        }
                        val prev = _summaries.value[post.id] ?: PostEngagementSummary(postId = post.id)
                        _summaries.update { it + (post.id to prev.copy(commentCount = prev.commentCount + 1)) }
                    }
                    ResourceState.AuthRequired -> {
                        _commentsSheet.update { it.copy(isSubmitting = false) }
                        onRequireAuth()
                    }
                    is ResourceState.Error -> {
                        _commentsSheet.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    }
                    else -> _commentsSheet.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }

    fun deleteComment(comment: VybalComment) {
        val postId = comment.postId
        viewModelScope.launch {
            when (val res = repo.deleteComment(comment.id)) {
                is ResourceState.Success -> {
                    _commentsSheet.update { st ->
                        st.copy(comments = st.comments.filterNot { it.id == comment.id || it.parentCommentId == comment.id })
                    }
                    val prev = _summaries.value[postId]
                    if (prev != null) {
                        _summaries.update {
                            it + (postId to prev.copy(commentCount = (prev.commentCount - 1).coerceAtLeast(0)))
                        }
                    }
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun togglePinComment(comment: VybalComment) {
        val nextPinned = !comment.isPinned
        viewModelScope.launch {
            when (val res = repo.setCommentPinned(comment.id, nextPinned)) {
                is ResourceState.Success -> {
                    _commentsSheet.update { st ->
                        st.copy(
                            comments = st.comments.map {
                                if (it.id == comment.id) it.copy(isPinned = nextPinned) else it
                            }.sortedByDescending { it.isPinned }
                        )
                    }
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun toggleCommentLike(comment: VybalComment, onRequireAuth: () -> Unit) {
        val nextLike = !comment.isLikedByMe
        val delta = if (nextLike) 1 else -1
        _commentsSheet.update { st ->
            st.copy(
                comments = st.comments.map {
                    if (it.id == comment.id) {
                        it.copy(isLikedByMe = nextLike, likeCount = (it.likeCount + delta).coerceAtLeast(0))
                    } else it
                }
            )
        }
        viewModelScope.launch {
            when (val res = repo.toggleCommentLike(comment.id, comment.userId, comment.postId, nextLike)) {
                is ResourceState.Success -> Unit
                ResourceState.AuthRequired -> {
                    // Rollback
                    _commentsSheet.update { st ->
                        st.copy(comments = st.comments.map { if (it.id == comment.id) comment else it })
                    }
                    onRequireAuth()
                }
                is ResourceState.Error -> {
                    _commentsSheet.update { st ->
                        st.copy(comments = st.comments.map { if (it.id == comment.id) comment else it })
                    }
                    _statusMessage.value = res.message
                }
                else -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Bookmarks & Private Collections
    // -------------------------------------------------------------------------
    fun toggleQuickSave(post: VybalPost, onRequireAuth: () -> Unit) {
        val current = _summaries.value[post.id] ?: PostEngagementSummary(postId = post.id)
        val nextSaved = !current.isSavedByMe
        _summaries.update { it + (post.id to current.copy(isSavedByMe = nextSaved)) }

        viewModelScope.launch {
            val res = if (nextSaved) repo.savePost(post.id, null) else repo.unsavePost(post.id)
            when (res) {
                is ResourceState.Success -> {
                    _statusMessage.value = if (nextSaved) "Saved privately to your Profile." else "Removed from saved."
                    refreshSavedLibrary()
                }
                ResourceState.AuthRequired -> {
                    _summaries.update { it + (post.id to current) }
                    onRequireAuth()
                }
                is ResourceState.Error -> {
                    _summaries.update { it + (post.id to current) }
                    _statusMessage.value = res.message
                }
                else -> _summaries.update { it + (post.id to current) }
            }
        }
    }

    fun openSaveSheet(post: VybalPost) {
        _saveSheetPost.value = post
        refreshSavedLibrary()
    }

    fun closeSaveSheet() {
        _saveSheetPost.value = null
    }

    fun createCollectionAndSave(name: String, post: VybalPost) {
        viewModelScope.launch {
            when (val col = repo.createSavedCollection(name)) {
                is ResourceState.Success -> {
                    _collections.update { listOf(col.data) + it }
                    saveToCollection(post, col.data.id)
                }
                is ResourceState.Error -> _statusMessage.value = col.message
                else -> Unit
            }
        }
    }

    fun saveToCollection(post: VybalPost, collectionId: String?) {
        val current = _summaries.value[post.id] ?: PostEngagementSummary(postId = post.id)
        _summaries.update {
            it + (post.id to current.copy(isSavedByMe = true, savedCollectionId = collectionId))
        }
        viewModelScope.launch {
            when (val res = repo.savePost(post.id, collectionId)) {
                is ResourceState.Success -> {
                    _saveSheetPost.value = null
                    _statusMessage.value = "Saved to collection."
                    refreshSavedLibrary()
                }
                is ResourceState.Error -> {
                    _summaries.update { it + (post.id to current) }
                    _statusMessage.value = res.message
                }
                else -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reposts
    // -------------------------------------------------------------------------
    fun openRepostDialog(post: VybalPost) {
        _repostTarget.value = post
    }

    fun closeRepostDialog() {
        _repostTarget.value = null
    }

    fun submitRepost(post: VybalPost, caption: String?, onRequireAuth: () -> Unit) {
        val current = _summaries.value[post.id] ?: PostEngagementSummary(postId = post.id)
        val optimistic = current.copy(
            isRepostedByMe = true,
            repostCount = if (current.isRepostedByMe) current.repostCount else current.repostCount + 1
        )
        _summaries.update { it + (post.id to optimistic) }
        _repostTarget.value = null

        viewModelScope.launch {
            when (val res = repo.createRepost(post.id, post.userId, caption)) {
                is ResourceState.Success -> _statusMessage.value = "Reposted to VYBAL."
                ResourceState.AuthRequired -> {
                    _summaries.update { it + (post.id to current) }
                    onRequireAuth()
                }
                is ResourceState.Error -> {
                    _summaries.update { it + (post.id to current) }
                    _statusMessage.value = res.message
                }
                else -> _summaries.update { it + (post.id to current) }
            }
        }
    }

    fun removeRepost(post: VybalPost) {
        val current = _summaries.value[post.id] ?: PostEngagementSummary(postId = post.id)
        val optimistic = current.copy(
            isRepostedByMe = false,
            repostCount = (current.repostCount - 1).coerceAtLeast(0)
        )
        _summaries.update { it + (post.id to optimistic) }
        _repostTarget.value = null

        viewModelScope.launch {
            when (val res = repo.removeRepost(post.id)) {
                is ResourceState.Success -> _statusMessage.value = "Repost removed."
                is ResourceState.Error -> {
                    _summaries.update { it + (post.id to current) }
                    _statusMessage.value = res.message
                }
                else -> Unit
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shares, Post Options, Insights
    // -------------------------------------------------------------------------
    fun recordShare(post: VybalPost) {
        val current = _summaries.value[post.id] ?: PostEngagementSummary(postId = post.id, shareCount = post.shareCount)
        val nextCount = current.shareCount + 1
        _summaries.update { it + (post.id to current.copy(shareCount = nextCount)) }
        viewModelScope.launch {
            // Best-effort sync to Supabase if share_count column exists
            repo.updatePostControl(postId = post.id, shareCount = nextCount)
        }
    }

    fun openOptions(post: VybalPost) {
        _optionsTarget.value = post
    }

    fun closeOptions() {
        _optionsTarget.value = null
    }

    fun togglePinPost(post: VybalPost, onUpdated: () -> Unit) {
        _optionsTarget.value = null
        viewModelScope.launch {
            when (val res = repo.updatePostControl(postId = post.id, isPinned = !post.isPinned)) {
                is ResourceState.Success -> {
                    _statusMessage.value = if (!post.isPinned) "Post pinned to your profile." else "Post unpinned."
                    onUpdated()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun toggleCommentsDisabled(post: VybalPost, onUpdated: () -> Unit) {
        _optionsTarget.value = null
        viewModelScope.launch {
            when (val res = repo.updatePostControl(postId = post.id, commentsDisabled = !post.commentsDisabled)) {
                is ResourceState.Success -> {
                    _statusMessage.value = if (!post.commentsDisabled) "Comments turned off." else "Comments turned on."
                    onUpdated()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun hidePost(post: VybalPost) {
        _optionsTarget.value = null
        _hiddenPostIds.update { it + post.id }
        viewModelScope.launch {
            when (val res = repo.hidePost(post.id)) {
                is ResourceState.Success -> _statusMessage.value = "Post hidden from your feed."
                is ResourceState.Error -> {
                    _hiddenPostIds.update { it - post.id }
                    _statusMessage.value = res.message
                }
                else -> Unit
            }
        }
    }

    fun reportPost(post: VybalPost, reason: String) {
        _optionsTarget.value = null
        viewModelScope.launch {
            when (val res = repo.reportPost(post.id, reason)) {
                is ResourceState.Success -> _statusMessage.value = "Report submitted. Thank you."
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun openInsights(post: VybalPost) {
        _optionsTarget.value = null
        viewModelScope.launch {
            _insightsTarget.value = repo.buildPostInsights(post, _summaries.value[post.id])
        }
    }

    fun closeInsights() {
        _insightsTarget.value = null
    }

    // -------------------------------------------------------------------------
    // User Profile Sheet & Follow System
    // -------------------------------------------------------------------------
    fun openUserProfile(usernameOrId: String, isUserId: Boolean) {
        _profileSheet.value = UserProfileSheetState(isOpen = true, isLoading = true)
        viewModelScope.launch {
            when (val res = repo.fetchProfileByUsernameOrId(usernameOrId, isUserId)) {
                is ResourceState.Success -> {
                    val profile = res.data
                    if (profile == null) {
                        _profileSheet.value = UserProfileSheetState(
                            isOpen = true,
                            isLoading = false,
                            errorMessage = "Profile not found."
                        )
                    } else {
                        val stats = (repo.fetchFollowStats(profile.id) as? ResourceState.Success)?.data
                        _profileSheet.value = UserProfileSheetState(
                            isOpen = true,
                            isLoading = false,
                            profile = profile,
                            followStats = stats
                        )
                    }
                }
                is ResourceState.Error -> {
                    _profileSheet.value = UserProfileSheetState(
                        isOpen = true,
                        isLoading = false,
                        errorMessage = res.message
                    )
                }
                else -> _profileSheet.value = UserProfileSheetState(isOpen = true, isLoading = false)
            }
        }
    }

    fun closeUserProfile() {
        _profileSheet.value = UserProfileSheetState()
    }

    fun toggleFollowProfile(targetUserId: String, myUserId: String?, onRequireAuth: () -> Unit) {
        if (myUserId == null) {
            onRequireAuth()
            return
        }
        if (targetUserId == myUserId) {
            _statusMessage.value = "You cannot follow your own account."
            return
        }
        val state = _profileSheet.value
        val currentStats = state.followStats ?: FollowStats(targetUserId = targetUserId)
        val nextFollow = !currentStats.isFollowing
        val nextCount = (currentStats.followersCount + if (nextFollow) 1 else -1).coerceAtLeast(0)

        _profileSheet.update {
            it.copy(
                isFollowBusy = true,
                followStats = currentStats.copy(isFollowing = nextFollow, followersCount = nextCount)
            )
        }
        viewModelScope.launch {
            when (val res = repo.toggleFollow(targetUserId, nextFollow)) {
                is ResourceState.Success -> {
                    _profileSheet.update { it.copy(isFollowBusy = false) }
                    refreshUserScopedData(myUserId)
                }
                ResourceState.AuthRequired -> {
                    _profileSheet.update { it.copy(isFollowBusy = false, followStats = currentStats) }
                    onRequireAuth()
                }
                is ResourceState.Error -> {
                    _profileSheet.update { it.copy(isFollowBusy = false, followStats = currentStats) }
                    _statusMessage.value = res.message
                }
                else -> _profileSheet.update { it.copy(isFollowBusy = false, followStats = currentStats) }
            }
        }
    }

    fun openPostFromNotification(postId: String) {
        viewModelScope.launch {
            when (val res = repo.fetchPostById(postId)) {
                is ResourceState.Success -> {
                    val post = res.data
                    if (post != null) {
                        openComments(post)
                    } else {
                        _statusMessage.value = "This post is no longer available."
                    }
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { repo.markNotificationRead(id) }
    }

    fun markAllNotificationsRead(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.markAllNotificationsRead()
            onDone()
        }
    }

    class Factory(private val container: VybalAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EngagementViewModel(container) as T
        }
    }
}
