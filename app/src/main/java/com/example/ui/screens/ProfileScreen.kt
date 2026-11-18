package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataSaverOn
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.R
import com.example.core.config.VybalSupabaseConfig
import com.example.core.share.VybalShareHelper
import com.example.data.model.FollowStats
import com.example.data.model.ResourceState
import com.example.data.model.SavedCollection
import com.example.data.model.SavedPostEntry
import com.example.data.model.UserSession
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import com.example.ui.components.PostInteractionCallbacks
import com.example.ui.components.VybalBrandTopBar
import com.example.ui.components.VybalInteractivePostCard
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalPrimaryButton
import com.example.ui.components.VybalSecondaryButton
import com.example.ui.components.VybalSegmentedTabs
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.state.AuthMode
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSuccess
import com.example.ui.theme.VybalSurface
import com.example.ui.theme.VybalSurfaceLow
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer
import com.example.ui.theme.VybalWarning

@Composable
fun ProfileScreen(
    session: UserSession?,
    isSessionLoading: Boolean,
    isSigningOut: Boolean,
    profileState: ResourceState<VybalProfile?>,
    userPostsState: ResourceState<List<VybalPost>>,
    followStats: FollowStats?,
    savedCollections: List<SavedCollection>,
    savedEntries: List<SavedPostEntry>,
    savedPosts: List<VybalPost>,
    isSupabaseConfigured: Boolean,
    dataSaverEnabled: Boolean,
    imageLoader: ImageLoader,
    interactions: PostInteractionCallbacks,
    onToggleDataSaver: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenAuth: (AuthMode) -> Unit,
    onSignOut: () -> Unit,
    onUpdateProfile: (displayName: String, bio: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    onRequestVerification: (tier: String, reason: String, onDone: (Boolean, String?) -> Unit) -> Unit = { _, _, cb -> cb(false, null) }
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showVerifyRequestDialog by rememberSaveable { mutableStateOf(false) }
    var showInstallGuide by rememberSaveable { mutableStateOf(false) }
    var appLinkCopied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val profile = (profileState as? ResourceState.Success)?.data
    val isAdminUser = session?.email?.equals(
        com.example.core.config.VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
        ignoreCase = true
    ) == true || profile?.isSuperAdmin == true || profile?.isStaff == true
    val tabs = listOf(
        stringResource(R.string.profile_tab_posts),
        stringResource(R.string.profile_tab_media),
        "Saved"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        VybalBrandTopBar(
            title = stringResource(R.string.nav_profile),
            isSupabaseConfigured = isSupabaseConfigured,
            onRefresh = if (session != null) onRefresh else null,
            trailingContent = {
                if (session != null) {
                    IconButton(
                        onClick = onSignOut,
                        enabled = !isSigningOut,
                        modifier = Modifier.testTag("profile_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = stringResource(R.string.action_sign_out),
                            tint = VybalTextSecondary
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = VybalSpacing.md,
                vertical = VybalSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(VybalSpacing.md)
        ) {
            if (isSessionLoading) {
                item(key = "profile_session_loading") { VybalLoadingCard() }
                return@LazyColumn
            }

            item(key = "profile_header_card") {
                VybalSurfaceCard(
                    borderColor = VybalOutline.copy(alpha = 0.7f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vybal_aura),
                            contentDescription = stringResource(R.string.cd_brand_aura),
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, VybalSurface)
                                    )
                                )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VybalSpacing.md)
                            .padding(bottom = VybalSpacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(VybalVioletContainer)
                                    .border(1.5.dp, VybalViolet, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (session != null) {
                                    val initials = (profile?.displayName?.ifBlank { null } ?: session.email)
                                        .take(2)
                                        .uppercase()
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = VybalLavender
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.PersonOutline,
                                        contentDescription = stringResource(R.string.cd_user_avatar),
                                        tint = VybalLavender,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            val isPermanentSuperAdmin = session?.email?.equals(
                                com.example.core.config.VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
                                ignoreCase = true
                            ) == true || profile?.isSuperAdmin == true
                            val isVerifiedIdentity = isPermanentSuperAdmin || profile?.isVerified == true
                            val effectiveRole = if (isPermanentSuperAdmin) "super_admin" else (profile?.role ?: "user")

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = when {
                                            session == null -> "Guest Session"
                                            !profile?.displayName.isNullOrBlank() -> profile?.displayName.orEmpty()
                                            else -> session.email.substringBefore("@")
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (session != null && isVerifiedIdentity) {
                                        com.example.ui.components.VybalVerifiedBadge(
                                            isVerified = true,
                                            verificationTier = if (isPermanentSuperAdmin) "super_admin" else profile?.verificationTier,
                                            isSuperAdmin = isPermanentSuperAdmin
                                        )
                                    }
                                }
                                Text(
                                    text = when {
                                        session == null -> "Not signed in"
                                        !profile?.username.isNullOrBlank() -> "@${profile?.username}"
                                        else -> session.email
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VybalTextSecondary
                                )
                                if (session != null && (isPermanentSuperAdmin || profile?.isStaff == true || isVerifiedIdentity)) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isPermanentSuperAdmin || profile?.isStaff == true) {
                                            com.example.ui.components.VybalRoleDesignationChip(role = effectiveRole)
                                        }
                                        if (isVerifiedIdentity) {
                                            com.example.ui.components.VybalVerifiedBadge(
                                                isVerified = true,
                                                verificationTier = if (isPermanentSuperAdmin) "super_admin" else profile?.verificationTier,
                                                isSuperAdmin = isPermanentSuperAdmin,
                                                showLabel = true
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!profile?.bio.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = profile?.bio.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (session != null && followStats != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Text(
                                    text = "${followStats.followersCount} Followers",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VybalLavender
                                )
                                Text(
                                    text = "${followStats.followingCount} Following",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VybalTextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        if (session == null) {
                            VybalPrimaryButton(
                                text = stringResource(R.string.action_sign_in),
                                onClick = { onOpenAuth(AuthMode.SignIn) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "profile_sign_in_button"
                            )
                            Spacer(Modifier.height(8.dp))
                            VybalSecondaryButton(
                                text = stringResource(R.string.action_create_account),
                                onClick = { onOpenAuth(AuthMode.SignUp) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "profile_create_account_button"
                            )
                            TextButton(
                                onClick = { onOpenAuth(AuthMode.ResetPassword) },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("profile_reset_password_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_forgot_password),
                                    color = VybalLavender
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                VybalSecondaryButton(
                                    text = stringResource(R.string.action_edit_profile),
                                    icon = Icons.Outlined.Edit,
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.weight(1f),
                                    testTag = "profile_edit_button"
                                )
                                VybalSecondaryButton(
                                    text = if (isSigningOut) {
                                        stringResource(R.string.auth_signing_out)
                                    } else {
                                        stringResource(R.string.action_sign_out)
                                    },
                                    icon = Icons.AutoMirrored.Outlined.Logout,
                                    enabled = !isSigningOut,
                                    onClick = onSignOut,
                                    modifier = Modifier.weight(1f),
                                    testTag = "profile_logout_action_button"
                                )
                            }

                            val isSuperAdminOrStaff = session.email.equals(
                                com.example.core.config.VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
                                ignoreCase = true
                            ) || profile?.isStaff == true
                            val isAlreadyVerified = isSuperAdminOrStaff || profile?.isVerified == true

                            if (isSuperAdminOrStaff) {
                                Spacer(Modifier.height(10.dp))
                                VybalPrimaryButton(
                                    text = if (session.email.equals(
                                            com.example.core.config.VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
                                            ignoreCase = true
                                        ) || profile?.isSuperAdmin == true
                                    ) {
                                        "Open Super Admin Dashboard"
                                    } else {
                                        "Open Staff Console"
                                    },
                                    onClick = onOpenAdminDashboard,
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "open_super_admin_dashboard_button"
                                )
                            } else if (!isAlreadyVerified) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { showVerifyRequestDialog = true },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .testTag("request_verification_button")
                                ) {
                                    Text("Request Creator / Business Verification", color = VybalLavender)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "install_download_app_card") {
                VybalSurfaceCard(
                    borderColor = VybalViolet.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = VybalLavender,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.install_app_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.install_app_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = VybalTextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    VybalPrimaryButton(
                        text = stringResource(R.string.action_install_download_app),
                        onClick = { VybalShareHelper.openAppInstallPage(context) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "install_download_app_button"
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VybalSecondaryButton(
                            text = stringResource(R.string.action_share_app_link),
                            icon = Icons.Outlined.Share,
                            onClick = { VybalShareHelper.shareAppLink(context) },
                            modifier = Modifier.weight(1f),
                            testTag = "share_app_link_button"
                        )
                        VybalSecondaryButton(
                            text = stringResource(R.string.action_copy_app_link),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = {
                                if (VybalShareHelper.copyAppLink(context)) {
                                    appLinkCopied = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "copy_app_link_button"
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { showInstallGuide = true },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("install_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = VybalLavender,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.action_install_guide),
                            color = VybalLavender
                        )
                    }

                    if (appLinkCopied) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.install_link_copied),
                            style = MaterialTheme.typography.labelMedium,
                            color = VybalSuccess
                        )
                    }
                }
            }

            if (isAdminUser) {
                item(key = "supabase_status_card") {
                    VybalSurfaceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isSupabaseConfigured) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = if (isSupabaseConfigured) VybalSuccess else VybalWarning,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSupabaseConfigured) {
                                        stringResource(R.string.profile_status_connected)
                                    } else {
                                        stringResource(R.string.profile_status_pending)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (isSupabaseConfigured) {
                                        VybalSupabaseConfig.projectHostDisplay
                                    } else {
                                        "Set SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VybalTextMuted
                                )
                            }
                        }
                    }
                }
            }

            item(key = "performance_card") {
                VybalSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DataSaverOn,
                            contentDescription = null,
                            tint = VybalLavender,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.label_data_saver),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.desc_data_saver),
                                style = MaterialTheme.typography.bodySmall,
                                color = VybalTextSecondary
                            )
                        }
                        Switch(
                            checked = dataSaverEnabled,
                            onCheckedChange = onToggleDataSaver,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = VybalViolet
                            ),
                            modifier = Modifier.testTag("data_saver_switch")
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = VybalTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.label_low_ram_mode),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.desc_low_ram_mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = VybalTextMuted
                            )
                        }
                    }
                }
            }

            if (session != null) {
                item(key = "profile_tabs") {
                    VybalSegmentedTabs(
                        tabs = tabs,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }

                if (selectedTab == 2) {
                    // Private Saved Posts & Collections
                    if (savedCollections.isNotEmpty()) {
                        item(key = "saved_collections_row") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item(key = "col_all") {
                                    FilterChip(
                                        selected = selectedCollectionId == null,
                                        onClick = { selectedCollectionId = null },
                                        label = { Text("All Saved") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VybalVioletContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }
                                items(savedCollections, key = { it.id }) { col ->
                                    FilterChip(
                                        selected = selectedCollectionId == col.id,
                                        onClick = { selectedCollectionId = col.id },
                                        label = { Text(col.name) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VybalVioletContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val matchingPostIds = if (selectedCollectionId == null) {
                        savedEntries.map { it.postId }.toSet()
                    } else {
                        savedEntries.filter { it.collectionId == selectedCollectionId }.map { it.postId }.toSet()
                    }
                    val filteredSaved = savedPosts.filter { it.id in matchingPostIds }

                    if (filteredSaved.isEmpty()) {
                        item(key = "saved_empty") {
                            VybalStatePanel(
                                icon = Icons.Outlined.BookmarkBorder,
                                title = "No saved posts",
                                body = "Bookmark posts from Home or Explore to organize them privately here."
                            )
                        }
                    } else {
                        items(filteredSaved, key = { "saved_${it.id}" }) { post ->
                            VybalInteractivePostCard(
                                post = post,
                                authorHandle = interactions.usernames[post.userId].orEmpty(),
                                authorBadge = interactions.profileBadges[post.userId],
                                summary = interactions.summaries[post.id],
                                imageLoader = imageLoader,
                                dataSaverEnabled = dataSaverEnabled,
                                onAuthorClick = interactions.onAuthorClick,
                                onMentionClick = interactions.onMentionClick,
                                onHashtagClick = interactions.onHashtagClick,
                                onReactionSelect = { reaction, isDoubleTap ->
                                    interactions.onReactionSelect(post, reaction, isDoubleTap)
                                },
                                onCommentClick = { interactions.onCommentClick(post) },
                                onRepostClick = { interactions.onRepostClick(post) },
                                onShareClick = { interactions.onShareClick(post) },
                                onQuickSaveClick = { interactions.onQuickSaveClick(post) },
                                onSaveCollectionClick = { interactions.onSaveCollectionClick(post) },
                                onOptionsClick = { interactions.onOptionsClick(post) }
                            )
                        }
                    }
                } else {
                    when (userPostsState) {
                        ResourceState.Idle, ResourceState.Loading -> {
                            item(key = "profile_posts_loading") { VybalLoadingCard() }
                        }
                        is ResourceState.Success -> {
                            val sorted = userPostsState.data.sortedByDescending { it.isPinned }
                            val filtered = if (selectedTab == 1) {
                                sorted.filter { !it.mediaUrl.isNullOrBlank() }
                            } else {
                                sorted
                            }
                            if (filtered.isEmpty()) {
                                item(key = "profile_posts_empty") {
                                    VybalStatePanel(
                                        icon = Icons.Outlined.PersonOutline,
                                        title = stringResource(R.string.profile_posts_empty_title),
                                        body = stringResource(R.string.profile_posts_empty_body)
                                    )
                                }
                            } else {
                                items(filtered, key = { it.id }) { post ->
                                    VybalInteractivePostCard(
                                        post = post,
                                        authorHandle = interactions.usernames[post.userId]
                                            ?: profile?.username.orEmpty(),
                                        authorBadge = interactions.profileBadges[post.userId],
                                        summary = interactions.summaries[post.id],
                                        imageLoader = imageLoader,
                                        dataSaverEnabled = dataSaverEnabled,
                                        onAuthorClick = interactions.onAuthorClick,
                                        onMentionClick = interactions.onMentionClick,
                                        onHashtagClick = interactions.onHashtagClick,
                                        onReactionSelect = { reaction, isDoubleTap ->
                                            interactions.onReactionSelect(post, reaction, isDoubleTap)
                                        },
                                        onCommentClick = { interactions.onCommentClick(post) },
                                        onRepostClick = { interactions.onRepostClick(post) },
                                        onShareClick = { interactions.onShareClick(post) },
                                        onQuickSaveClick = { interactions.onQuickSaveClick(post) },
                                        onSaveCollectionClick = { interactions.onSaveCollectionClick(post) },
                                        onOptionsClick = { interactions.onOptionsClick(post) }
                                    )
                                }
                            }
                        }
                        is ResourceState.Error -> {
                            item(key = "profile_posts_error") {
                                VybalStatePanel(
                                    icon = Icons.Outlined.CloudOff,
                                    title = "Could not load your posts",
                                    body = userPostsState.message,
                                    primaryActionLabel = stringResource(R.string.action_retry),
                                    onPrimaryAction = onRefresh
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    if (showEditDialog && session != null) {
        EditProfileDialog(
            initialDisplayName = profile?.displayName.orEmpty(),
            initialBio = profile?.bio.orEmpty(),
            onDismiss = { showEditDialog = false },
            onSave = { name, bio, onDone ->
                onUpdateProfile(name, bio) { success, err ->
                    onDone(success, err)
                    if (success) showEditDialog = false
                }
            }
        )
    }

    if (showVerifyRequestDialog && session != null) {
        RequestVerificationDialog(
            onDismiss = { showVerifyRequestDialog = false },
            onSubmit = { tier, reason, onDone ->
                onRequestVerification(tier, reason) { ok, msg ->
                    onDone(ok, msg)
                    if (ok) showVerifyRequestDialog = false
                }
            }
        )
    }

    if (showInstallGuide) {
        InstallGuideDialog(onDismiss = { showInstallGuide = false })
    }
}

@Composable
private fun InstallGuideDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        title = {
            Text(
                text = stringResource(R.string.install_guide_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.install_guide_android_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = VybalLavender
                    )
                    Text(
                        text = stringResource(R.string.install_guide_android_steps),
                        style = MaterialTheme.typography.bodySmall,
                        color = VybalTextSecondary
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.install_guide_ios_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = VybalLavender
                    )
                    Text(
                        text = stringResource(R.string.install_guide_ios_steps),
                        style = MaterialTheme.typography.bodySmall,
                        color = VybalTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("install_guide_close_button")
            ) {
                Text(stringResource(R.string.install_guide_close), color = VybalViolet)
            }
        }
    )
}

@Composable
private fun RequestVerificationDialog(
    onDismiss: () -> Unit,
    onSubmit: (tier: String, reason: String, onDone: (Boolean, String?) -> Unit) -> Unit
) {
    var selectedTier by rememberSaveable { mutableStateOf("creator") }
    var reason by rememberSaveable { mutableStateOf("") }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        title = { Text("Request Verification Badge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTier == "creator",
                        onClick = { selectedTier = "creator" },
                        label = { Text("Creator") }
                    )
                    FilterChip(
                        selected = selectedTier == "business",
                        onClick = { selectedTier = "business" },
                        label = { Text("Business") }
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Why should this account be verified?") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!feedback.isNullOrBlank()) {
                    Text(
                        text = feedback.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    submitting = true
                    feedback = null
                    onSubmit(selectedTier, reason) { ok, msg ->
                        submitting = false
                        if (!ok) feedback = msg ?: "Could not submit request."
                    }
                }
            ) {
                Text("Submit", color = VybalViolet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VybalTextSecondary)
            }
        }
    )
}

@Composable
private fun EditProfileDialog(
    initialDisplayName: String,
    initialBio: String,
    onDismiss: () -> Unit,
    onSave: (String, String, (Boolean, String?) -> Unit) -> Unit
) {
    var displayName by rememberSaveable { mutableStateOf(initialDisplayName) }
    var bio by rememberSaveable { mutableStateOf(initialBio) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        title = {
            Text(
                text = stringResource(R.string.action_edit_profile),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.profile_display_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(stringResource(R.string.profile_bio_label)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    errorText = null
                    onSave(displayName, bio) { success, err ->
                        saving = false
                        if (!success) errorText = err ?: "Could not save profile."
                    }
                }
            ) {
                Text(stringResource(R.string.action_save), color = VybalViolet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = VybalTextSecondary)
            }
        }
    )
}
