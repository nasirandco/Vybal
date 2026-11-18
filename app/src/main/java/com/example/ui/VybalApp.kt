package com.example.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import com.example.core.share.VybalShareHelper
import com.example.data.model.ResourceState
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.PostInsightsBottomSheet
import com.example.ui.components.PostInteractionCallbacks
import com.example.ui.components.PostOptionsBottomSheet
import com.example.ui.components.RepostDialog
import com.example.ui.components.SaveCollectionBottomSheet
import com.example.ui.components.UserProfileBottomSheet
import com.example.ui.components.VybalAuthSheet
import com.example.ui.components.VybalBottomNavBar
import com.example.ui.components.VybalNavigationRail
import com.example.ui.components.VybalNetworkBanner
import com.example.ui.navigation.VybalDestination
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.ProfileScreen
import com.example.core.config.VybalSchemaContract
import com.example.ui.screens.admin.SuperAdminDashboardScreen
import com.example.ui.state.AdminViewModel
import com.example.ui.state.AuthMode
import com.example.ui.state.EngagementViewModel
import com.example.ui.state.VybalAppViewModel
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalObsidian
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalVioletContainer
import com.example.ui.theme.VybalWarning
import kotlinx.coroutines.launch

@Composable
fun VybalApp(
    viewModel: VybalAppViewModel,
    engagementViewModel: EngagementViewModel,
    adminViewModel: AdminViewModel,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val session by viewModel.session.collectAsStateWithLifecycle()
    val isSessionLoading by viewModel.isSessionLoading.collectAsStateWithLifecycle()
    val dataSaverEnabled by viewModel.dataSaverEnabled.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val authUiState by viewModel.authUiState.collectAsStateWithLifecycle()
    val homeFeedState by viewModel.homeFeedState.collectAsStateWithLifecycle()
    val exploreQuery by viewModel.exploreQuery.collectAsStateWithLifecycle()
    val exploreState by viewModel.exploreState.collectAsStateWithLifecycle()
    val createState by viewModel.createState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val profilePostsState by viewModel.profilePostsState.collectAsStateWithLifecycle()

    val summaries by engagementViewModel.summaries.collectAsStateWithLifecycle()
    val usernames by engagementViewModel.usernames.collectAsStateWithLifecycle()
    val profileBadges by engagementViewModel.profileBadges.collectAsStateWithLifecycle()
    val hiddenPostIds by engagementViewModel.hiddenPostIds.collectAsStateWithLifecycle()
    val knownHashtags by engagementViewModel.knownHashtags.collectAsStateWithLifecycle()
    val commentsSheet by engagementViewModel.commentsSheet.collectAsStateWithLifecycle()
    val saveSheetPost by engagementViewModel.saveSheetPost.collectAsStateWithLifecycle()
    val collections by engagementViewModel.collections.collectAsStateWithLifecycle()
    val savedEntries by engagementViewModel.savedEntries.collectAsStateWithLifecycle()
    val savedPosts by engagementViewModel.savedPosts.collectAsStateWithLifecycle()
    val repostTarget by engagementViewModel.repostTarget.collectAsStateWithLifecycle()
    val optionsTarget by engagementViewModel.optionsTarget.collectAsStateWithLifecycle()
    val insightsTarget by engagementViewModel.insightsTarget.collectAsStateWithLifecycle()
    val profileSheet by engagementViewModel.profileSheet.collectAsStateWithLifecycle()
    val myFollowStats by engagementViewModel.myFollowStats.collectAsStateWithLifecycle()
    val statusMessage by engagementViewModel.statusMessage.collectAsStateWithLifecycle()

    val isAdminDashboardOpen by adminViewModel.isDashboardOpen.collectAsStateWithLifecycle()
    val platformSettings by adminViewModel.platformSettings.collectAsStateWithLifecycle()
    val announcementsState by adminViewModel.announcementsState.collectAsStateWithLifecycle()
    val currentProfile = (profileState as? ResourceState.Success)?.data
    val isCurrentUserStaff = session?.email?.equals(
        VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
        ignoreCase = true
    ) == true || currentProfile?.isStaff == true

    if (isAdminDashboardOpen && session != null && isCurrentUserStaff) {
        SuperAdminDashboardScreen(
            adminViewModel = adminViewModel,
            currentProfile = currentProfile,
            currentEmail = session?.email.orEmpty(),
            onExitDashboard = {
                adminViewModel.closeDashboard()
                viewModel.refreshHomeFeed()
                viewModel.refreshProfile()
            }
        )
        return
    }

    val unreadInboxCount = (notificationsState as? ResourceState.Success)
        ?.data?.count { !it.isRead } ?: 0

    // Load engagement summaries whenever feed / explore / profile posts update
    LaunchedEffect(homeFeedState) {
        (homeFeedState as? ResourceState.Success)?.data?.let {
            engagementViewModel.loadEngagementForPosts(it)
        }
    }
    LaunchedEffect(exploreState) {
        (exploreState as? ResourceState.Success)?.data?.let {
            engagementViewModel.loadEngagementForPosts(it)
        }
    }
    LaunchedEffect(profilePostsState) {
        (profilePostsState as? ResourceState.Success)?.data?.let {
            engagementViewModel.loadEngagementForPosts(it)
        }
    }
    LaunchedEffect(session?.userId) {
        engagementViewModel.refreshUserScopedData(session?.userId)
    }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            engagementViewModel.clearStatusMessage()
        }
    }

    val navigateTo: (VybalDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val requireAuth: () -> Unit = { viewModel.openAuthSheet(AuthMode.SignIn) }

    val interactions = remember(summaries, usernames, profileBadges, hiddenPostIds) {
        PostInteractionCallbacks(
            summaries = summaries,
            usernames = usernames,
            profileBadges = profileBadges,
            hiddenPostIds = hiddenPostIds,
            onAuthorClick = { userId -> engagementViewModel.openUserProfile(userId, isUserId = true) },
            onMentionClick = { username -> engagementViewModel.openUserProfile(username, isUserId = false) },
            onHashtagClick = { tag ->
                viewModel.updateExploreQuery("#$tag")
                viewModel.refreshExplore("#$tag")
                navigateTo(VybalDestination.Explore)
            },
            onReactionSelect = { post, reaction, isDoubleTap ->
                engagementViewModel.toggleLikeOrReaction(post, reaction, isDoubleTap, requireAuth)
            },
            onCommentClick = { post -> engagementViewModel.openComments(post) },
            onRepostClick = { post ->
                if (session == null) requireAuth() else engagementViewModel.openRepostDialog(post)
            },
            onShareClick = { post ->
                val handle = usernames[post.userId].orEmpty()
                if (VybalShareHelper.launchNativeShare(context, post, handle)) {
                    engagementViewModel.recordShare(post)
                }
            },
            onQuickSaveClick = { post ->
                engagementViewModel.toggleQuickSave(post, requireAuth)
            },
            onSaveCollectionClick = { post ->
                if (session == null) requireAuth() else engagementViewModel.openSaveSheet(post)
            },
            onOptionsClick = { post -> engagementViewModel.openOptions(post) }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(VybalObsidian)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = VybalObsidian,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    VybalBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = navigateTo,
                        unreadInboxCount = unreadInboxCount
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    VybalNavigationRail(
                        currentRoute = currentRoute,
                        onNavigate = navigateTo,
                        unreadInboxCount = unreadInboxCount
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = VybalSpacing.maxContentWidth)
                    ) {
                        VybalNetworkBanner(
                            networkStatus = networkStatus,
                            dataSaverEnabled = dataSaverEnabled
                        )

                        if (platformSettings.maintenanceMode) {
                            androidx.compose.material3.Surface(
                                color = VybalWarning.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxSize().padding(0.dp).let { Modifier.padding(horizontal = 12.dp, vertical = 4.dp) }
                            ) {
                                androidx.compose.material3.Text(
                                    text = "Maintenance: ${platformSettings.maintenanceMessage}",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                    color = VybalWarning,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            val activeAnn = (announcementsState as? ResourceState.Success)
                                ?.data?.firstOrNull { it.isActive }
                            if (activeAnn != null) {
                                androidx.compose.material3.Surface(
                                    color = VybalVioletContainer.copy(alpha = 0.65f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    androidx.compose.material3.Text(
                                        text = "${activeAnn.title}: ${activeAnn.body}",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                        color = VybalLavender,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = VybalDestination.Home.route,
                            enterTransition = { fadeIn(animationSpec = tween(180)) },
                            exitTransition = { fadeOut(animationSpec = tween(140)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(VybalDestination.Home.route) {
                                HomeScreen(
                                    feedState = homeFeedState,
                                    session = session,
                                    isSupabaseConfigured = viewModel.isSupabaseConfigured,
                                    dataSaverEnabled = dataSaverEnabled,
                                    imageLoader = imageLoader,
                                    interactions = interactions,
                                    onRefresh = viewModel::refreshHomeFeed,
                                    onOpenAuth = requireAuth,
                                    onNavigateCreate = { navigateTo(VybalDestination.Create) }
                                )
                            }

                            composable(VybalDestination.Explore.route) {
                                ExploreScreen(
                                    query = exploreQuery,
                                    knownHashtags = knownHashtags,
                                    exploreState = exploreState,
                                    isSupabaseConfigured = viewModel.isSupabaseConfigured,
                                    dataSaverEnabled = dataSaverEnabled,
                                    imageLoader = imageLoader,
                                    interactions = interactions,
                                    onQueryChange = viewModel::updateExploreQuery,
                                    onSearchSubmit = { viewModel.refreshExplore() },
                                    onOpenAuth = requireAuth
                                )
                            }

                            composable(VybalDestination.Create.route) {
                                CreateScreen(
                                    createState = createState,
                                    session = session,
                                    isSessionLoading = isSessionLoading,
                                    isSupabaseConfigured = viewModel.isSupabaseConfigured,
                                    imageLoader = imageLoader,
                                    knownHashtags = knownHashtags,
                                    onPublish = viewModel::publishPost,
                                    onOpenAuth = { mode -> viewModel.openAuthSheet(mode) },
                                    onClearFeedback = viewModel::clearCreateFeedback
                                )
                            }

                            composable(VybalDestination.Inbox.route) {
                                InboxScreen(
                                    session = session,
                                    isSessionLoading = isSessionLoading,
                                    notificationsState = notificationsState,
                                    messagesState = messagesState,
                                    isSupabaseConfigured = viewModel.isSupabaseConfigured,
                                    onRefresh = viewModel::refreshInbox,
                                    onOpenAuth = { mode -> viewModel.openAuthSheet(mode) },
                                    onNotificationClick = { notif ->
                                        engagementViewModel.markNotificationRead(notif.id)
                                        when {
                                            !notif.postId.isNullOrBlank() ->
                                                engagementViewModel.openPostFromNotification(notif.postId)
                                            !notif.actorId.isNullOrBlank() ->
                                                engagementViewModel.openUserProfile(notif.actorId, isUserId = true)
                                        }
                                        viewModel.refreshInbox()
                                    },
                                    onMarkAllRead = {
                                        engagementViewModel.markAllNotificationsRead {
                                            viewModel.refreshInbox()
                                        }
                                    }
                                )
                            }

                            composable(VybalDestination.Profile.route) {
                                ProfileScreen(
                                    session = session,
                                    isSessionLoading = isSessionLoading,
                                    isSigningOut = authUiState.isSigningOut,
                                    profileState = profileState,
                                    userPostsState = profilePostsState,
                                    followStats = myFollowStats,
                                    savedCollections = collections,
                                    savedEntries = savedEntries,
                                    savedPosts = savedPosts,
                                    isSupabaseConfigured = viewModel.isSupabaseConfigured,
                                    dataSaverEnabled = dataSaverEnabled,
                                    imageLoader = imageLoader,
                                    interactions = interactions,
                                    onToggleDataSaver = viewModel::setDataSaver,
                                    onRefresh = {
                                        viewModel.refreshProfile()
                                        engagementViewModel.refreshUserScopedData(session?.userId)
                                    },
                                    onOpenAuth = { mode -> viewModel.openAuthSheet(mode) },
                                    onSignOut = viewModel::signOut,
                                    onUpdateProfile = viewModel::updateProfile,
                                    onOpenAdminDashboard = { adminViewModel.openDashboard() },
                                    onRequestVerification = { tier, reason, onDone ->
                                        adminViewModel.submitOwnVerificationRequest(tier, reason) { ok, msg ->
                                            onDone(ok, msg)
                                            if (ok && !msg.isNullOrBlank()) {
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Auth Modal
    VybalAuthSheet(
        state = authUiState,
        onDismiss = viewModel::dismissAuthSheet,
        onModeChange = viewModel::setAuthMode,
        onSubmitSignIn = viewModel::submitSignIn,
        onSubmitSignUp = viewModel::submitSignUp,
        onSubmitPasswordReset = viewModel::submitPasswordReset
    )

    // Engagement Modals
    CommentsBottomSheet(
        state = commentsSheet,
        currentUserId = session?.userId,
        onDismiss = engagementViewModel::closeComments,
        onSubmitComment = { text -> engagementViewModel.submitComment(text, requireAuth) },
        onLoadMore = engagementViewModel::loadMoreComments,
        onReply = engagementViewModel::setReplyingTo,
        onEdit = engagementViewModel::setEditingComment,
        onDelete = engagementViewModel::deleteComment,
        onPin = engagementViewModel::togglePinComment,
        onLike = { comment -> engagementViewModel.toggleCommentLike(comment, requireAuth) },
        onMentionClick = { username ->
            engagementViewModel.closeComments()
            engagementViewModel.openUserProfile(username, isUserId = false)
        },
        onHashtagClick = { tag ->
            engagementViewModel.closeComments()
            viewModel.updateExploreQuery("#$tag")
            viewModel.refreshExplore("#$tag")
            navigateTo(VybalDestination.Explore)
        }
    )

    SaveCollectionBottomSheet(
        post = saveSheetPost,
        collections = collections,
        onDismiss = engagementViewModel::closeSaveSheet,
        onSaveDefault = {
            saveSheetPost?.let { engagementViewModel.saveToCollection(it, null) }
        },
        onSaveToCollection = { colId ->
            saveSheetPost?.let { engagementViewModel.saveToCollection(it, colId) }
        },
        onCreateCollection = { name ->
            saveSheetPost?.let { engagementViewModel.createCollectionAndSave(name, it) }
        }
    )

    RepostDialog(
        post = repostTarget,
        isAlreadyReposted = repostTarget?.let { summaries[it.id]?.isRepostedByMe } == true,
        authorHandle = repostTarget?.let { usernames[it.userId] }.orEmpty().ifBlank { "member" },
        onDismiss = engagementViewModel::closeRepostDialog,
        onConfirmRepost = { caption ->
            repostTarget?.let { engagementViewModel.submitRepost(it, caption, requireAuth) }
        },
        onRemoveRepost = {
            repostTarget?.let { engagementViewModel.removeRepost(it) }
        }
    )

    PostOptionsBottomSheet(
        post = optionsTarget,
        isOwner = optionsTarget?.userId != null && optionsTarget?.userId == session?.userId,
        onDismiss = engagementViewModel::closeOptions,
        onCopyLink = { post ->
            if (VybalShareHelper.copyPostLink(context, post.id)) {
                coroutineScope.launch { snackbarHostState.showSnackbar("Post link copied.") }
            }
        },
        onNativeShare = { post ->
            val handle = usernames[post.userId].orEmpty()
            if (VybalShareHelper.launchNativeShare(context, post, handle)) {
                engagementViewModel.recordShare(post)
            }
        },
        onTogglePin = { post ->
            engagementViewModel.togglePinPost(post) {
                viewModel.refreshHomeFeed()
                viewModel.refreshProfile()
            }
        },
        onToggleComments = { post ->
            engagementViewModel.toggleCommentsDisabled(post) {
                viewModel.refreshHomeFeed()
                viewModel.refreshProfile()
            }
        },
        onOpenInsights = engagementViewModel::openInsights,
        onHidePost = engagementViewModel::hidePost,
        onReportPost = engagementViewModel::reportPost
    )

    PostInsightsBottomSheet(
        insights = insightsTarget,
        onDismiss = engagementViewModel::closeInsights
    )

    UserProfileBottomSheet(
        state = profileSheet,
        currentUserId = session?.userId,
        onDismiss = engagementViewModel::closeUserProfile,
        onToggleFollow = { targetId ->
            engagementViewModel.toggleFollowProfile(targetId, session?.userId, requireAuth)
        }
    )
}
