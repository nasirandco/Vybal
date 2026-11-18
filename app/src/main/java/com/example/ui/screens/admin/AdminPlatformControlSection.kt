package com.example.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditLogEntry
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.PlatformSettings
import com.example.data.model.ResourceState
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalPrimaryButton
import com.example.ui.components.VybalSegmentedTabs
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalWarning

@Composable
fun AdminPlatformControlSection(
    settings: PlatformSettings,
    announcementsState: ResourceState<List<PlatformAnnouncement>>,
    auditLogsState: ResourceState<List<AuditLogEntry>>,
    isCallerSuperAdmin: Boolean,
    onSaveSettings: (PlatformSettings) -> Unit,
    onCreateAnnouncement: (title: String, body: String, audience: String) -> Unit,
    onToggleAnnouncement: (id: String, active: Boolean) -> Unit,
    onDeleteAnnouncement: (id: String) -> Unit,
    onBroadcastNotification: (message: String) -> Unit,
    onRefreshLogs: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_platform_section")
    ) {
        VybalSegmentedTabs(
            tabs = listOf("Settings", "Broadcasts", "Audit Logs"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = VybalSpacing.md, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(VybalSpacing.md),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item(key = "settings_card") {
                        var maintenanceMode by rememberSaveable(settings) { mutableStateOf(settings.maintenanceMode) }
                        var maintenanceMsg by rememberSaveable(settings) { mutableStateOf(settings.maintenanceMessage) }
                        var allowReg by rememberSaveable(settings) { mutableStateOf(settings.allowRegistrations) }
                        var allowPost by rememberSaveable(settings) { mutableStateOf(settings.allowPosting) }
                        var allowComments by rememberSaveable(settings) { mutableStateOf(settings.allowComments) }

                        VybalSurfaceCard {
                            Text(
                                text = "Platform Controls & Maintenance Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(10.dp))

                            SettingSwitchRow("Maintenance Mode", maintenanceMode, isCallerSuperAdmin) {
                                maintenanceMode = it
                            }
                            OutlinedTextField(
                                value = maintenanceMsg,
                                onValueChange = { maintenanceMsg = it },
                                enabled = isCallerSuperAdmin,
                                label = { Text("Maintenance Banner Message") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            SettingSwitchRow("Allow New Registrations", allowReg, isCallerSuperAdmin) {
                                allowReg = it
                            }
                            SettingSwitchRow("Allow New Posts", allowPost, isCallerSuperAdmin) {
                                allowPost = it
                            }
                            SettingSwitchRow("Allow Comments", allowComments, isCallerSuperAdmin) {
                                allowComments = it
                            }

                            Spacer(Modifier.height(12.dp))
                            VybalPrimaryButton(
                                text = if (isCallerSuperAdmin) "Save Platform Settings" else "Super Admin Only",
                                enabled = isCallerSuperAdmin,
                                onClick = {
                                    onSaveSettings(
                                        PlatformSettings(
                                            maintenanceMode = maintenanceMode,
                                            maintenanceMessage = maintenanceMsg,
                                            allowRegistrations = allowReg,
                                            allowPosting = allowPost,
                                            allowComments = allowComments
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "admin_save_settings_button"
                            )
                        }
                    }
                }

                1 -> {
                    item(key = "create_announcement_card") {
                        var title by rememberSaveable { mutableStateOf("") }
                        var body by rememberSaveable { mutableStateOf("") }
                        var audience by rememberSaveable { mutableStateOf("all") }

                        VybalSurfaceCard {
                            Text(
                                text = "Publish Platform Announcement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it },
                                label = { Text("Announcement Message") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("all", "verified", "staff").forEach { aud ->
                                    FilterChip(
                                        selected = audience == aud,
                                        onClick = { audience = aud },
                                        label = { Text(aud.replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            VybalPrimaryButton(
                                text = "Publish Announcement",
                                onClick = {
                                    onCreateAnnouncement(title, body, audience)
                                    title = ""
                                    body = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "admin_publish_announcement_button"
                            )
                        }
                    }

                    item(key = "broadcast_notif_card") {
                        var notifMsg by rememberSaveable { mutableStateOf("") }
                        VybalSurfaceCard {
                            Text(
                                text = "Send Platform Notification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = notifMsg,
                                onValueChange = { notifMsg = it },
                                label = { Text("Notification text for all registered users") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            VybalPrimaryButton(
                                text = "Send Notification",
                                onClick = {
                                    onBroadcastNotification(notifMsg)
                                    notifMsg = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "admin_send_broadcast_button"
                            )
                        }
                    }

                    when (announcementsState) {
                        is ResourceState.Success -> {
                            if (announcementsState.data.isEmpty()) {
                                item(key = "ann_empty") {
                                    VybalStatePanel(
                                        icon = Icons.Outlined.Campaign,
                                        title = "No Announcements Yet",
                                        body = "Published platform announcements will appear here."
                                    )
                                }
                            } else {
                                items(announcementsState.data, key = { "ann_${it.id}" }) { ann ->
                                    VybalSurfaceCard {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ann.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(ann.body, style = MaterialTheme.typography.bodySmall, color = VybalTextSecondary)
                                                Text(
                                                    "Audience: ${ann.audience.uppercase()}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = VybalTextMuted
                                                )
                                            }
                                            Switch(
                                                checked = ann.isActive,
                                                onCheckedChange = { onToggleAnnouncement(ann.id, it) }
                                            )
                                            IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = VybalError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                2 -> {
                    when (auditLogsState) {
                        ResourceState.Idle, ResourceState.Loading -> item { VybalLoadingCard() }
                        is ResourceState.Error -> item {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudOff,
                                title = "Audit Logs Unavailable",
                                body = auditLogsState.message,
                                primaryActionLabel = "Retry",
                                onPrimaryAction = onRefreshLogs
                            )
                        }
                        is ResourceState.Success -> {
                            if (auditLogsState.data.isEmpty()) {
                                item {
                                    VybalStatePanel(
                                        icon = Icons.Outlined.Security,
                                        title = "No Audit Events",
                                        body = "All privileged administrative and moderation actions are logged here."
                                    )
                                }
                            } else {
                                items(auditLogsState.data, key = { it.id }) { log ->
                                    val sevColor = when (log.severity.lowercase()) {
                                        "critical" -> VybalError
                                        "warning" -> VybalWarning
                                        else -> VybalLavender
                                    }
                                    VybalSurfaceCard {
                                        Text(
                                            text = "${log.action.uppercase()} • ${log.targetType}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = sevColor
                                        )
                                        if (log.details.isNotBlank()) {
                                            Text(log.details, style = MaterialTheme.typography.bodySmall, color = VybalTextSecondary)
                                        }
                                        Text(
                                            text = "${log.actorEmail.ifBlank { "system" }} • ${log.createdAt.take(16).replace("T", " ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VybalTextMuted
                                        )
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = VybalViolet
            )
        )
    }
}
