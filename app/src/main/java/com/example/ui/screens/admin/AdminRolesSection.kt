package com.example.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.VybalSchemaContract
import com.example.data.model.AdminPermissionRecord
import com.example.data.model.AuditLogEntry
import com.example.data.model.ResourceState
import com.example.data.model.VybalProfile
import com.example.ui.components.VybalRoleDesignationChip
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.components.VybalVerifiedBadge
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet

@Composable
fun AdminRolesSection(
    usersState: ResourceState<List<VybalProfile>>,
    permissionsMap: Map<String, AdminPermissionRecord>,
    auditLogsState: ResourceState<List<AuditLogEntry>>,
    isCallerSuperAdmin: Boolean,
    onPromoteOrDemote: (user: VybalProfile, newRole: String) -> Unit,
    onSavePermissions: (AdminPermissionRecord) -> Unit
) {
    val allUsers = (usersState as? ResourceState.Success)?.data.orEmpty()
    val staff = allUsers.filter { it.isStaff }
    val candidates = allUsers.filter { !it.isStaff }.take(10)
    val recentAdminLogs = (auditLogsState as? ResourceState.Success)?.data.orEmpty().take(8)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_roles_section"),
        contentPadding = PaddingValues(VybalSpacing.md),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isCallerSuperAdmin) {
            item(key = "super_admin_only_notice") {
                VybalSurfaceCard {
                    Text(
                        text = "Super Admin Restricted",
                        style = MaterialTheme.typography.titleMedium,
                        color = VybalLavender,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Only the permanent Super Admin (${VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL}) can assign roles or modify administrator permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VybalTextSecondary
                    )
                }
            }
        }

        item(key = "staff_header") {
            Text(
                text = "Current Administrators & Moderators (${staff.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (staff.isEmpty()) {
            item(key = "staff_empty") {
                VybalStatePanel(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = "No Staff Loaded",
                    body = "Run super_admin_dashboard.sql and refresh to manage administrators."
                )
            }
        } else {
            items(staff, key = { "staff_${it.id}" }) { member ->
                val isPermanentSuper = member.isSuperAdmin ||
                    member.email.equals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, ignoreCase = true)
                val perms = permissionsMap[member.id] ?: AdminPermissionRecord(userId = member.id)

                VybalSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = member.displayName.ifBlank { "@${member.username.ifBlank { member.id.take(8) }}" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                VybalVerifiedBadge(
                                    isVerified = member.isVerified,
                                    verificationTier = member.verificationTier,
                                    isSuperAdmin = isPermanentSuper
                                )
                            }
                            if (member.email.isNotBlank()) {
                                Text(member.email, style = MaterialTheme.typography.bodySmall, color = VybalTextSecondary)
                            }
                        }
                        VybalRoleDesignationChip(role = member.role)
                    }

                    if (isPermanentSuper) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Permanent Super Admin — full unrestricted access, protected from demotion or removal.",
                            style = MaterialTheme.typography.labelSmall,
                            color = VybalLavender
                        )
                    } else if (isCallerSuperAdmin) {
                        Spacer(Modifier.height(10.dp))
                        PermissionToggleRow("Manage Users", perms.canManageUsers) {
                            onSavePermissions(perms.copy(canManageUsers = it))
                        }
                        PermissionToggleRow("Manage Verifications", perms.canManageVerifications) {
                            onSavePermissions(perms.copy(canManageVerifications = it))
                        }
                        PermissionToggleRow("Moderate Content", perms.canModerateContent) {
                            onSavePermissions(perms.copy(canModerateContent = it))
                        }
                        PermissionToggleRow("Send Announcements", perms.canSendAnnouncements) {
                            onSavePermissions(perms.copy(canSendAnnouncements = it))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            val altRole = if (member.role == "admin") "moderator" else "admin"
                            TextButton(onClick = { onPromoteOrDemote(member, altRole) }) {
                                Text("Set ${altRole.replaceFirstChar { it.uppercase() }}", color = VybalLavender)
                            }
                            TextButton(onClick = { onPromoteOrDemote(member, "user") }) {
                                Text("Revoke Staff", color = VybalError)
                            }
                        }
                    }
                }
            }
        }

        if (isCallerSuperAdmin && candidates.isNotEmpty()) {
            item(key = "promote_header") {
                Text(
                    text = "Assign Staff Role to Existing Members",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(candidates, key = { "cand_${it.id}" }) { user ->
                VybalSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${user.username.ifBlank { user.id.take(8) }}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (user.email.isNotBlank()) {
                                Text(user.email, style = MaterialTheme.typography.bodySmall, color = VybalTextMuted)
                            }
                        }
                        Row {
                            TextButton(onClick = { onPromoteOrDemote(user, "moderator") }) {
                                Text("+ Mod", color = VybalLavender)
                            }
                            TextButton(onClick = { onPromoteOrDemote(user, "admin") }) {
                                Text("+ Admin", color = VybalViolet)
                            }
                        }
                    }
                }
            }
        }

        if (recentAdminLogs.isNotEmpty()) {
            item(key = "admin_activity_header") {
                Text(
                    text = "Recent Administrator Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(recentAdminLogs, key = { "staff_log_${it.id}" }) { log ->
                VybalSurfaceCard {
                    Text(
                        text = "${log.action} • ${log.targetType}",
                        style = MaterialTheme.typography.labelLarge,
                        color = VybalLavender
                    )
                    if (log.details.isNotBlank()) {
                        Text(log.details, style = MaterialTheme.typography.bodySmall, color = VybalTextSecondary)
                    }
                    Text(
                        text = "${log.actorEmail.ifBlank { "staff" }} • ${log.createdAt.take(16).replace("T", " ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VybalTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = VybalTextSecondary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = VybalViolet
            )
        )
    }
}
