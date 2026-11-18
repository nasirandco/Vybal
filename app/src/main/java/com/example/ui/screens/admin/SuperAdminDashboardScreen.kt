package com.example.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.config.VybalSchemaContract
import com.example.data.model.AdminSection
import com.example.data.model.VybalProfile
import com.example.ui.components.VybalRoleDesignationChip
import com.example.ui.components.VybalVerifiedBadge
import com.example.ui.state.AdminViewModel
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalObsidian
import com.example.ui.theme.VybalOutlineSubtle
import com.example.ui.theme.VybalPurpleDeep
import com.example.ui.theme.VybalSurfaceLow
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer
import kotlinx.coroutines.launch

@Composable
fun SuperAdminDashboardScreen(
    adminViewModel: AdminViewModel,
    currentProfile: VybalProfile?,
    currentEmail: String,
    onExitDashboard: () -> Unit
) {
    BackHandler { onExitDashboard() }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedSection by adminViewModel.selectedSection.collectAsStateWithLifecycle()
    val metricsState by adminViewModel.metricsState.collectAsStateWithLifecycle()
    val usersState by adminViewModel.usersState.collectAsStateWithLifecycle()
    val verificationState by adminViewModel.verificationState.collectAsStateWithLifecycle()
    val reportsState by adminViewModel.reportsState.collectAsStateWithLifecycle()
    val moderationPostsState by adminViewModel.moderationPostsState.collectAsStateWithLifecycle()
    val moderationCommentsState by adminViewModel.moderationCommentsState.collectAsStateWithLifecycle()
    val moderationStoriesState by adminViewModel.moderationStoriesState.collectAsStateWithLifecycle()
    val permissionsMap by adminViewModel.permissionsMap.collectAsStateWithLifecycle()
    val announcementsState by adminViewModel.announcementsState.collectAsStateWithLifecycle()
    val platformSettings by adminViewModel.platformSettings.collectAsStateWithLifecycle()
    val auditLogsState by adminViewModel.auditLogsState.collectAsStateWithLifecycle()
    val statusMessage by adminViewModel.statusMessage.collectAsStateWithLifecycle()

    val isCallerSuperAdmin = currentProfile?.isSuperAdmin == true ||
        currentEmail.equals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, ignoreCase = true)

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            adminViewModel.clearStatusMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = VybalSurfaceLow,
                drawerContentColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(VybalPurpleDeep, VybalSurfaceLow)
                            )
                        )
                        .statusBarsPadding()
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "VYBAL",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VybalRoleDesignationChip(role = if (isCallerSuperAdmin) "super_admin" else (currentProfile?.role ?: "admin"))
                            VybalVerifiedBadge(
                                isVerified = true,
                                verificationTier = "super_admin",
                                isSuperAdmin = isCallerSuperAdmin,
                                showLabel = true
                            )
                        }
                        Text(
                            text = currentEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                AdminSection.entries.forEach { section ->
                    val icon = when (section) {
                        AdminSection.Overview -> Icons.Outlined.Dashboard
                        AdminSection.Users -> Icons.Outlined.Group
                        AdminSection.Verification -> Icons.Outlined.VerifiedUser
                        AdminSection.Moderation -> Icons.Outlined.Gavel
                        AdminSection.Administrators -> Icons.Outlined.AdminPanelSettings
                        AdminSection.Platform -> Icons.Outlined.Tune
                    }
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(section.label) },
                        selected = selectedSection == section,
                        onClick = {
                            adminViewModel.selectSection(section)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = VybalVioletContainer,
                            selectedIconColor = VybalLavender,
                            selectedTextColor = Color.White,
                            unselectedIconColor = VybalTextSecondary,
                            unselectedTextColor = VybalTextSecondary
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("admin_drawer_${section.name.lowercase()}")
                    )
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = VybalOutlineSubtle)
                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null) },
                    label = { Text("Return to VYBAL") },
                    selected = false,
                    onClick = onExitDashboard,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = VybalObsidian,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VybalSurfaceLow)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("admin_menu_button")
                            ) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open Admin Sidebar", tint = VybalLavender)
                            }
                            Column {
                                Text(
                                    text = selectedSection.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isCallerSuperAdmin) "Super Admin Console" else "Staff Console",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VybalLavender
                                )
                            }
                        }
                        Row {
                            IconButton(
                                onClick = adminViewModel::refreshAllDashboardData,
                                modifier = Modifier.testTag("admin_refresh_button")
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Dashboard", tint = VybalTextSecondary)
                            }
                            IconButton(
                                onClick = onExitDashboard,
                                modifier = Modifier.testTag("admin_exit_button")
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Exit Dashboard", tint = VybalTextSecondary)
                            }
                        }
                    }
                    HorizontalDivider(color = VybalOutlineSubtle)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("super_admin_dashboard_root")
            ) {
                when (selectedSection) {
                    AdminSection.Overview -> AdminOverviewSection(
                        metricsState = metricsState,
                        onRefresh = adminViewModel::refreshMetrics,
                        onNavigateSection = adminViewModel::selectSection
                    )

                    AdminSection.Users -> AdminUsersSection(
                        usersState = usersState,
                        isCallerSuperAdmin = isCallerSuperAdmin,
                        onRefresh = adminViewModel::refreshUsers,
                        onUpdateUser = adminViewModel::updateUser,
                        onDeleteUser = adminViewModel::deleteUser
                    )

                    AdminSection.Verification -> AdminVerificationSection(
                        requestsState = verificationState,
                        usersState = usersState,
                        onRefresh = adminViewModel::refreshVerifications,
                        onReviewRequest = adminViewModel::reviewVerification,
                        onManualVerify = { user, verified, tier ->
                            adminViewModel.updateUser(
                                user = user,
                                isVerified = verified,
                                verificationTier = tier,
                                successMessage = if (verified) {
                                    "Verified @${user.username.ifBlank { user.id.take(8) }} ($tier)."
                                } else {
                                    "Removed verification badge."
                                }
                            )
                        }
                    )

                    AdminSection.Moderation -> AdminModerationSection(
                        reportsState = reportsState,
                        postsState = moderationPostsState,
                        commentsState = moderationCommentsState,
                        storiesState = moderationStoriesState,
                        onRefresh = adminViewModel::refreshModeration,
                        onModerate = adminViewModel::moderateContent
                    )

                    AdminSection.Administrators -> AdminRolesSection(
                        usersState = usersState,
                        permissionsMap = permissionsMap,
                        auditLogsState = auditLogsState,
                        isCallerSuperAdmin = isCallerSuperAdmin,
                        onPromoteOrDemote = { user, newRole ->
                            adminViewModel.updateUser(
                                user = user,
                                role = newRole,
                                successMessage = "Updated role to $newRole."
                            )
                        },
                        onSavePermissions = adminViewModel::savePermissions
                    )

                    AdminSection.Platform -> AdminPlatformControlSection(
                        settings = platformSettings,
                        announcementsState = announcementsState,
                        auditLogsState = auditLogsState,
                        isCallerSuperAdmin = isCallerSuperAdmin,
                        onSaveSettings = adminViewModel::savePlatformSettings,
                        onCreateAnnouncement = adminViewModel::createAnnouncement,
                        onToggleAnnouncement = adminViewModel::toggleAnnouncement,
                        onDeleteAnnouncement = adminViewModel::deleteAnnouncement,
                        onBroadcastNotification = adminViewModel::broadcastNotification,
                        onRefreshLogs = adminViewModel::refreshAuditLogs
                    )
                }
            }
        }
    }
}
