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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.VybalSchemaContract
import com.example.data.model.ResourceState
import com.example.data.model.VybalProfile
import com.example.ui.components.VybalAccountStatusChip
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalRoleDesignationChip
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.components.VybalVerifiedBadge
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSurfaceLow
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletContainer
import com.example.ui.theme.VybalWarning

@Composable
fun AdminUsersSection(
    usersState: ResourceState<List<VybalProfile>>,
    isCallerSuperAdmin: Boolean,
    onRefresh: () -> Unit,
    onUpdateUser: (
        user: VybalProfile,
        displayName: String?,
        bio: String?,
        role: String?,
        isVerified: Boolean?,
        verificationTier: String?,
        accountStatus: String?,
        restrictionReason: String?,
        successMessage: String
    ) -> Unit,
    onDeleteUser: (VybalProfile) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableStateOf("all") }
    var editingUser by remember { mutableStateOf<VybalProfile?>(null) }
    var deleteCandidate by remember { mutableStateOf<VybalProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_users_section")
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search users by handle, name, or email") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = VybalTextSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VybalViolet,
                unfocusedBorderColor = VybalOutline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VybalSpacing.md, vertical = 8.dp)
                .testTag("admin_user_search_input")
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = VybalSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "all" to "All",
                "verified" to "Verified",
                "staff" to "Staff",
                "suspended" to "Suspended",
                "banned" to "Banned"
            )
            items(filters) { (key, label) ->
                FilterChip(
                    selected = filterMode == key,
                    onClick = { filterMode = key },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VybalVioletContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(VybalSpacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (usersState) {
                ResourceState.Idle, ResourceState.Loading -> {
                    item(key = "users_loading") { VybalLoadingCard() }
                }
                is ResourceState.Error -> {
                    item(key = "users_error") {
                        VybalStatePanel(
                            icon = Icons.Outlined.CloudOff,
                            title = "Could not load users",
                            body = usersState.message,
                            primaryActionLabel = "Retry",
                            onPrimaryAction = onRefresh
                        )
                    }
                }
                is ResourceState.Success -> {
                    val q = searchQuery.trim().lowercase()
                    val filtered = usersState.data.filter { user ->
                        val matchesQuery = q.isEmpty() ||
                            user.username.lowercase().contains(q) ||
                            user.displayName.lowercase().contains(q) ||
                            user.email.lowercase().contains(q)
                        val matchesFilter = when (filterMode) {
                            "verified" -> user.isVerified || user.isSuperAdmin
                            "staff" -> user.isStaff
                            "suspended" -> user.accountStatus.equals("suspended", ignoreCase = true)
                            "banned" -> user.accountStatus.equals("banned", ignoreCase = true)
                            else -> true
                        }
                        matchesQuery && matchesFilter
                    }

                    if (filtered.isEmpty()) {
                        item(key = "users_empty") {
                            VybalStatePanel(
                                icon = Icons.Outlined.PersonSearch,
                                title = "No matching users",
                                body = "Adjust your search query or filter."
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { user ->
                            val isProtectedSuperAdmin = user.isSuperAdmin ||
                                user.email.equals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, ignoreCase = true)

                            VybalSurfaceCard(
                                modifier = Modifier.testTag("admin_user_card_${user.id}")
                            ) {
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
                                                text = user.displayName.ifBlank { user.username.ifBlank { "Member" } },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            VybalVerifiedBadge(
                                                isVerified = user.isVerified,
                                                verificationTier = user.verificationTier,
                                                isSuperAdmin = isProtectedSuperAdmin
                                            )
                                        }
                                        Text(
                                            text = "@${user.username.ifBlank { user.id.take(8) }}" +
                                                if (user.email.isNotBlank()) " • ${user.email}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = VybalTextSecondary
                                        )
                                    }
                                    VybalAccountStatusChip(status = user.accountStatus)
                                }

                                Spacer(Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (user.role != "user") {
                                        VybalRoleDesignationChip(role = user.role)
                                    }
                                    if (user.isVerified || isProtectedSuperAdmin) {
                                        VybalVerifiedBadge(
                                            isVerified = true,
                                            verificationTier = user.verificationTier,
                                            isSuperAdmin = isProtectedSuperAdmin,
                                            showLabel = true
                                        )
                                    }
                                    if (isProtectedSuperAdmin) {
                                        Icon(
                                            imageVector = Icons.Outlined.Lock,
                                            contentDescription = "Protected Super Admin account",
                                            tint = VybalLavender,
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    TextButton(onClick = { editingUser = user }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = null)
                                        Text(" Manage", color = VybalLavender)
                                    }
                                    if (!isProtectedSuperAdmin) {
                                        val isBanned = user.accountStatus.equals("banned", ignoreCase = true)
                                        TextButton(
                                            onClick = {
                                                onUpdateUser(
                                                    user, null, null, null, null, null,
                                                    if (isBanned) "active" else "banned",
                                                    if (isBanned) null else "Banned by administrator",
                                                    if (isBanned) "Account unbanned." else "Account banned."
                                                )
                                            }
                                        ) {
                                            Text(
                                                if (isBanned) "Unban" else "Ban",
                                                color = if (isBanned) VybalLavender else VybalError
                                            )
                                        }
                                        val isSuspended = user.accountStatus.equals("suspended", ignoreCase = true)
                                        TextButton(
                                            onClick = {
                                                onUpdateUser(
                                                    user, null, null, null, null, null,
                                                    if (isSuspended) "active" else "suspended",
                                                    if (isSuspended) null else "Suspended by administrator",
                                                    if (isSuspended) "Account restored." else "Account suspended."
                                                )
                                            }
                                        ) {
                                            Text(
                                                if (isSuspended) "Restore" else "Suspend",
                                                color = VybalWarning
                                            )
                                        }
                                        if (isCallerSuperAdmin) {
                                            TextButton(onClick = { deleteCandidate = user }) {
                                                Icon(
                                                    Icons.Outlined.DeleteOutline,
                                                    contentDescription = "Delete account",
                                                    tint = VybalError
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    editingUser?.let { target ->
        ManageUserDialog(
            user = target,
            isCallerSuperAdmin = isCallerSuperAdmin,
            onDismiss = { editingUser = null },
            onSave = { name, bio, role, verified, tier ->
                onUpdateUser(
                    target, name, bio, role, verified, tier, null, null,
                    "Updated @${target.username.ifBlank { target.id.take(8) }}."
                )
                editingUser = null
            }
        )
    }

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = VybalSurfaceLow,
            title = { Text("Delete Account?") },
            text = {
                Text(
                    "Permanently delete @${target.username.ifBlank { target.id.take(8) }}? " +
                        "This removes their profile and authentication record and is logged in the audit trail.",
                    color = VybalTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteUser(target)
                        deleteCandidate = null
                    }
                ) {
                    Text("Delete Permanently", color = VybalError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = VybalTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ManageUserDialog(
    user: VybalProfile,
    isCallerSuperAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (displayName: String, bio: String, role: String?, verified: Boolean, tier: String?) -> Unit
) {
    val isProtected = user.isSuperAdmin ||
        user.email.equals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, ignoreCase = true)
    var displayName by rememberSaveable(user.id) { mutableStateOf(user.displayName) }
    var bio by rememberSaveable(user.id) { mutableStateOf(user.bio) }
    var selectedRole by rememberSaveable(user.id) { mutableStateOf(user.role) }
    var isVerified by rememberSaveable(user.id) { mutableStateOf(user.isVerified || isProtected) }
    var tier by rememberSaveable(user.id) { mutableStateOf(user.verificationTier ?: "creator") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VybalSurfaceLow,
        title = { Text("Manage @${user.username.ifBlank { user.id.take(8) }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isProtected) {
                    Text("Verification Badge", style = MaterialTheme.typography.labelMedium, color = VybalTextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !isVerified,
                            onClick = { isVerified = false },
                            label = { Text("None") }
                        )
                        FilterChip(
                            selected = isVerified && tier == "creator",
                            onClick = { isVerified = true; tier = "creator" },
                            label = { Text("Creator") }
                        )
                        FilterChip(
                            selected = isVerified && tier == "business",
                            onClick = { isVerified = true; tier = "business" },
                            label = { Text("Business") }
                        )
                    }

                    if (isCallerSuperAdmin) {
                        Text("Role (Super Admin Only)", style = MaterialTheme.typography.labelMedium, color = VybalTextMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("user", "moderator", "admin").forEach { r ->
                                FilterChip(
                                    selected = selectedRole == r,
                                    onClick = { selectedRole = r },
                                    label = { Text(r.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Permanent Super Admin role and verified status are protected server-side.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VybalLavender
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        displayName.trim(),
                        bio.trim(),
                        if (isProtected || !isCallerSuperAdmin) null else selectedRole,
                        if (isProtected) true else isVerified,
                        if (isProtected) "super_admin" else if (isVerified) tier else null
                    )
                }
            ) {
                Text("Save", color = VybalViolet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VybalTextSecondary)
            }
        }
    )
}
