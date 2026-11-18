package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.model.VybalMessage
import com.example.data.model.VybalNotification
import com.example.ui.components.VybalBrandTopBar
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalSegmentedTabs
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.state.AuthMode
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer

@Composable
fun InboxScreen(
    session: UserSession?,
    isSessionLoading: Boolean,
    notificationsState: ResourceState<List<VybalNotification>>,
    messagesState: ResourceState<List<VybalMessage>>,
    isSupabaseConfigured: Boolean,
    onRefresh: () -> Unit,
    onOpenAuth: (AuthMode) -> Unit,
    onNotificationClick: (VybalNotification) -> Unit = {},
    onMarkAllRead: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.inbox_tab_activity),
        stringResource(R.string.inbox_tab_messages)
    )
    val unreadCount = (notificationsState as? ResourceState.Success)
        ?.data?.count { !it.isRead } ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        VybalBrandTopBar(
            title = stringResource(R.string.nav_inbox),
            isSupabaseConfigured = isSupabaseConfigured,
            onRefresh = if (session != null) onRefresh else null,
            trailingContent = {
                if (session != null && unreadCount > 0) {
                    androidx.compose.material3.TextButton(onClick = onMarkAllRead) {
                        Text("Mark read", color = MaterialTheme.colorScheme.tertiary)
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
                item(key = "inbox_session_loading") { VybalLoadingCard() }
                return@LazyColumn
            }

            if (session == null) {
                item(key = "inbox_protected_gate") {
                    VybalStatePanel(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.protected_inbox_title),
                        body = stringResource(R.string.protected_inbox_body),
                        primaryActionLabel = stringResource(R.string.action_sign_in),
                        onPrimaryAction = { onOpenAuth(AuthMode.SignIn) },
                        secondaryActionLabel = stringResource(R.string.action_create_account),
                        onSecondaryAction = { onOpenAuth(AuthMode.SignUp) },
                        showAuraHeader = true,
                        modifier = Modifier.testTag("protected_inbox_gate")
                    )
                }
                return@LazyColumn
            }

            item(key = "inbox_tabs") {
                VybalSegmentedTabs(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }

            if (selectedTab == 0) {
                when (notificationsState) {
                    ResourceState.Idle, ResourceState.Loading -> {
                        item(key = "notif_loading") { VybalLoadingCard() }
                    }
                    ResourceState.NotConfigured -> {
                        item(key = "notif_not_configured") {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudSync,
                                title = stringResource(R.string.supabase_not_configured_title),
                                body = stringResource(R.string.supabase_not_configured_body),
                                primaryActionLabel = stringResource(R.string.action_retry),
                                onPrimaryAction = onRefresh
                            )
                        }
                    }
                    ResourceState.AuthRequired -> {
                        item(key = "notif_auth") {
                            VybalStatePanel(
                                icon = Icons.Outlined.Lock,
                                title = stringResource(R.string.auth_required_title),
                                body = stringResource(R.string.auth_required_body),
                                primaryActionLabel = stringResource(R.string.action_sign_in),
                                onPrimaryAction = { onOpenAuth(AuthMode.SignIn) }
                            )
                        }
                    }
                    is ResourceState.Error -> {
                        item(key = "notif_error") {
                            VybalStatePanel(
                                icon = Icons.Outlined.ErrorOutline,
                                title = "Could not load activity",
                                body = notificationsState.message,
                                primaryActionLabel = stringResource(R.string.action_retry),
                                onPrimaryAction = onRefresh
                            )
                        }
                    }
                    is ResourceState.Success -> {
                        if (notificationsState.data.isEmpty()) {
                            item(key = "notif_empty") {
                                VybalStatePanel(
                                    icon = Icons.Outlined.NotificationsNone,
                                    title = stringResource(R.string.inbox_activity_empty_title),
                                    body = stringResource(R.string.inbox_activity_empty_body),
                                    primaryActionLabel = stringResource(R.string.action_refresh),
                                    onPrimaryAction = onRefresh
                                )
                            }
                        } else {
                            items(notificationsState.data, key = { it.id }) { item ->
                                VybalSurfaceCard(
                                    modifier = Modifier.clickable { onNotificationClick(item) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(VybalVioletContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.NotificationsNone,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.message.ifBlank { item.type },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            if (item.createdAt.isNotBlank()) {
                                                Text(
                                                    text = item.createdAt.take(16).replace("T", " "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = VybalTextMuted
                                                )
                                            }
                                        }
                                        if (!item.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(VybalViolet)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                when (messagesState) {
                    ResourceState.Idle, ResourceState.Loading -> {
                        item(key = "msg_loading") { VybalLoadingCard() }
                    }
                    ResourceState.NotConfigured -> {
                        item(key = "msg_not_configured") {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudSync,
                                title = stringResource(R.string.supabase_not_configured_title),
                                body = stringResource(R.string.supabase_not_configured_body),
                                primaryActionLabel = stringResource(R.string.action_retry),
                                onPrimaryAction = onRefresh
                            )
                        }
                    }
                    ResourceState.AuthRequired -> {
                        item(key = "msg_auth") {
                            VybalStatePanel(
                                icon = Icons.Outlined.Lock,
                                title = stringResource(R.string.auth_required_title),
                                body = stringResource(R.string.auth_required_body),
                                primaryActionLabel = stringResource(R.string.action_sign_in),
                                onPrimaryAction = { onOpenAuth(AuthMode.SignIn) }
                            )
                        }
                    }
                    is ResourceState.Error -> {
                        item(key = "msg_error") {
                            VybalStatePanel(
                                icon = Icons.Outlined.ErrorOutline,
                                title = "Could not load messages",
                                body = messagesState.message,
                                primaryActionLabel = stringResource(R.string.action_retry),
                                onPrimaryAction = onRefresh
                            )
                        }
                    }
                    is ResourceState.Success -> {
                        if (messagesState.data.isEmpty()) {
                            item(key = "msg_empty") {
                                VybalStatePanel(
                                    icon = Icons.Outlined.Forum,
                                    title = stringResource(R.string.inbox_messages_empty_title),
                                    body = stringResource(R.string.inbox_messages_empty_body),
                                    primaryActionLabel = stringResource(R.string.action_refresh),
                                    onPrimaryAction = onRefresh
                                )
                            }
                        } else {
                            items(messagesState.data, key = { it.id }) { msg ->
                                VybalSurfaceCard {
                                    Text(
                                        text = "From ${msg.senderId.take(8).ifBlank { "Member" }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = msg.body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (msg.createdAt.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = msg.createdAt.take(16).replace("T", " "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = VybalTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
