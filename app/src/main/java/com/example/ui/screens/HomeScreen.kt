package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import coil.ImageLoader
import com.example.R
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.model.VybalPost
import com.example.ui.components.PostInteractionCallbacks
import com.example.ui.components.VybalBrandTopBar
import com.example.ui.components.VybalInteractivePostCard
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalSegmentedTabs
import com.example.ui.components.VybalStatePanel
import com.example.ui.theme.VybalSpacing

@Composable
fun HomeScreen(
    feedState: ResourceState<List<VybalPost>>,
    session: UserSession?,
    isSupabaseConfigured: Boolean,
    dataSaverEnabled: Boolean,
    imageLoader: ImageLoader,
    interactions: PostInteractionCallbacks,
    onRefresh: () -> Unit,
    onOpenAuth: () -> Unit,
    onNavigateCreate: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.home_tab_for_you),
        stringResource(R.string.home_tab_following)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        VybalBrandTopBar(
            title = stringResource(R.string.app_name),
            isSupabaseConfigured = isSupabaseConfigured,
            showWordmark = true,
            onRefresh = onRefresh
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = VybalSpacing.md,
                vertical = VybalSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(VybalSpacing.md)
        ) {
            item(key = "home_tabs") {
                VybalSegmentedTabs(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = { index ->
                        selectedTab = index
                        onRefresh()
                    }
                )
            }

            when (feedState) {
                ResourceState.Idle, ResourceState.Loading -> {
                    item(key = "home_loading") { VybalLoadingCard() }
                }

                ResourceState.NotConfigured -> {
                    item(key = "home_not_configured") {
                        VybalStatePanel(
                            icon = Icons.Outlined.CloudSync,
                            title = stringResource(R.string.supabase_not_configured_title),
                            body = stringResource(R.string.supabase_not_configured_body),
                            primaryActionLabel = stringResource(R.string.action_retry),
                            onPrimaryAction = onRefresh,
                            showAuraHeader = true
                        )
                    }
                }

                ResourceState.AuthRequired -> {
                    item(key = "home_auth_required") {
                        VybalStatePanel(
                            icon = Icons.Outlined.Lock,
                            title = stringResource(R.string.auth_required_title),
                            body = stringResource(R.string.auth_required_body),
                            primaryActionLabel = stringResource(R.string.action_sign_in),
                            onPrimaryAction = onOpenAuth,
                            showAuraHeader = true
                        )
                    }
                }

                is ResourceState.Error -> {
                    item(key = "home_error") {
                        VybalStatePanel(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Could not load feed",
                            body = feedState.message,
                            primaryActionLabel = stringResource(R.string.action_retry),
                            onPrimaryAction = onRefresh
                        )
                    }
                }

                is ResourceState.Success -> {
                    val visiblePosts = feedState.data.filterNot { it.id in interactions.hiddenPostIds }
                    val posts = if (selectedTab == 1 && session == null) emptyList() else visiblePosts

                    if (selectedTab == 1 && session == null) {
                        item(key = "following_auth") {
                            VybalStatePanel(
                                icon = Icons.Outlined.Lock,
                                title = stringResource(R.string.auth_required_title),
                                body = "Sign in to view your Following stream from Supabase.",
                                primaryActionLabel = stringResource(R.string.action_sign_in),
                                onPrimaryAction = onOpenAuth
                            )
                        }
                    } else if (posts.isEmpty()) {
                        item(key = "home_empty") {
                            VybalStatePanel(
                                icon = Icons.Outlined.DynamicFeed,
                                title = stringResource(R.string.home_empty_title),
                                body = stringResource(R.string.home_empty_body),
                                primaryActionLabel = if (session != null) {
                                    stringResource(R.string.nav_create)
                                } else {
                                    stringResource(R.string.action_sign_in)
                                },
                                onPrimaryAction = if (session != null) onNavigateCreate else onOpenAuth,
                                secondaryActionLabel = stringResource(R.string.action_refresh),
                                onSecondaryAction = onRefresh,
                                showAuraHeader = true
                            )
                        }
                    } else {
                        items(items = posts, key = { it.id }) { post ->
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
                }
            }

            item(key = "home_bottom_spacer") {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(VybalSpacing.sm)
                )
            }
        }
    }
}
