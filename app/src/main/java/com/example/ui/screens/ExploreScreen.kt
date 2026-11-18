package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.R
import com.example.data.model.ResourceState
import com.example.data.model.VybalPost
import com.example.ui.components.PostInteractionCallbacks
import com.example.ui.components.VybalBrandTopBar
import com.example.ui.components.VybalInteractivePostCard
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalStatePanel
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSurface
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer

@Composable
fun ExploreScreen(
    query: String,
    knownHashtags: List<String>,
    exploreState: ResourceState<List<VybalPost>>,
    isSupabaseConfigured: Boolean,
    dataSaverEnabled: Boolean,
    imageLoader: ImageLoader,
    interactions: PostInteractionCallbacks,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onOpenAuth: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        VybalBrandTopBar(
            title = stringResource(R.string.nav_explore),
            isSupabaseConfigured = isSupabaseConfigured,
            onRefresh = onSearchSubmit
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = VybalSpacing.md,
                vertical = VybalSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(VybalSpacing.md)
        ) {
            item(key = "explore_search_bar") {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.explore_search_placeholder),
                            color = VybalTextMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = VybalTextSecondary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onQueryChange("")
                                    onSearchSubmit()
                                },
                                modifier = Modifier.testTag("explore_clear_search")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    tint = VybalTextSecondary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onSearchSubmit()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VybalSurface,
                        unfocusedContainerColor = VybalSurface,
                        focusedBorderColor = VybalViolet,
                        unfocusedBorderColor = VybalOutline,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_search_input")
                )
            }

            if (knownHashtags.isNotEmpty()) {
                item(key = "explore_hashtags") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(knownHashtags.take(12), key = { it }) { tag ->
                            AssistChip(
                                onClick = {
                                    onQueryChange("#$tag")
                                    onSearchSubmit()
                                },
                                label = { Text("#$tag") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Tag,
                                        contentDescription = null,
                                        tint = VybalLavender
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = VybalVioletContainer.copy(alpha = 0.55f),
                                    labelColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    }
                }
            }

            when (exploreState) {
                ResourceState.Idle, ResourceState.Loading -> {
                    item(key = "explore_loading") { VybalLoadingCard() }
                }

                ResourceState.NotConfigured -> {
                    item(key = "explore_not_configured") {
                        VybalStatePanel(
                            icon = Icons.Outlined.CloudSync,
                            title = stringResource(R.string.supabase_not_configured_title),
                            body = stringResource(R.string.supabase_not_configured_body),
                            primaryActionLabel = stringResource(R.string.action_retry),
                            onPrimaryAction = onSearchSubmit
                        )
                    }
                }

                ResourceState.AuthRequired -> {
                    item(key = "explore_auth_required") {
                        VybalStatePanel(
                            icon = Icons.Outlined.Lock,
                            title = stringResource(R.string.auth_required_title),
                            body = stringResource(R.string.auth_required_body),
                            primaryActionLabel = stringResource(R.string.action_sign_in),
                            onPrimaryAction = onOpenAuth
                        )
                    }
                }

                is ResourceState.Error -> {
                    item(key = "explore_error") {
                        VybalStatePanel(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Search unavailable",
                            body = exploreState.message,
                            primaryActionLabel = stringResource(R.string.action_retry),
                            onPrimaryAction = onSearchSubmit
                        )
                    }
                }

                is ResourceState.Success -> {
                    val visiblePosts = exploreState.data.filterNot { it.id in interactions.hiddenPostIds }
                    if (visiblePosts.isEmpty()) {
                        item(key = "explore_empty") {
                            val isFiltering = query.isNotBlank()
                            VybalStatePanel(
                                icon = Icons.Outlined.Explore,
                                title = if (isFiltering) {
                                    stringResource(R.string.explore_no_results_title)
                                } else {
                                    stringResource(R.string.explore_empty_title)
                                },
                                body = if (isFiltering) {
                                    stringResource(R.string.explore_no_results_body)
                                } else {
                                    stringResource(R.string.explore_empty_body)
                                },
                                primaryActionLabel = if (isFiltering) {
                                    stringResource(R.string.action_clear)
                                } else {
                                    stringResource(R.string.action_refresh)
                                },
                                onPrimaryAction = {
                                    if (isFiltering) {
                                        onQueryChange("")
                                    }
                                    onSearchSubmit()
                                }
                            )
                        }
                    } else {
                        items(items = visiblePosts, key = { it.id }) { post ->
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

            item(key = "explore_spacer") {
                Spacer(Modifier.height(VybalSpacing.sm))
            }
        }
    }
}
