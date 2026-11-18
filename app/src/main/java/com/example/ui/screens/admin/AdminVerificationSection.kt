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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.VybalSchemaContract
import com.example.data.model.ResourceState
import com.example.data.model.VerificationRequestRecord
import com.example.data.model.VybalProfile
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalSegmentedTabs
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.components.VybalVerifiedBadge
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSuccess
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet

@Composable
fun AdminVerificationSection(
    requestsState: ResourceState<List<VerificationRequestRecord>>,
    usersState: ResourceState<List<VybalProfile>>,
    onRefresh: () -> Unit,
    onReviewRequest: (VerificationRequestRecord, approve: Boolean, tier: String, notes: String) -> Unit,
    onManualVerify: (user: VybalProfile, verified: Boolean, tier: String?) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var manualSearch by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_verification_section")
    ) {
        VybalSegmentedTabs(
            tabs = listOf("Requests", "Manual Verify"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = VybalSpacing.md, vertical = 8.dp)
        )

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(VybalSpacing.md),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (requestsState) {
                    ResourceState.Idle, ResourceState.Loading -> {
                        item(key = "ver_loading") { VybalLoadingCard() }
                    }
                    is ResourceState.Error -> {
                        item(key = "ver_error") {
                            VybalStatePanel(
                                icon = Icons.Outlined.CloudOff,
                                title = "Verification Centre Unavailable",
                                body = requestsState.message,
                                primaryActionLabel = "Retry",
                                onPrimaryAction = onRefresh
                            )
                        }
                    }
                    is ResourceState.Success -> {
                        if (requestsState.data.isEmpty()) {
                            item(key = "ver_empty") {
                                VybalStatePanel(
                                    icon = Icons.Outlined.VerifiedUser,
                                    title = "No Verification Requests",
                                    body = "User applications for Creator and Business badges will appear here."
                                )
                            }
                        } else {
                            items(requestsState.data, key = { it.id }) { req ->
                                var selectedTier by rememberSaveable(req.id) { mutableStateOf(req.requestedTier) }
                                VybalSurfaceCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = req.displayName.ifBlank { "@${req.username.ifBlank { req.userId.take(8) }}" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Requested: ${req.requestedTier.uppercase()} • ${req.status.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when (req.status) {
                                                    "approved" -> VybalSuccess
                                                    "rejected" -> VybalError
                                                    else -> VybalLavender
                                                }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = req.reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VybalTextSecondary
                                    )
                                    if (req.status == "pending") {
                                        Spacer(Modifier.height(8.dp))
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    onReviewRequest(req, false, selectedTier, "Rejected by admin")
                                                }
                                            ) {
                                                Text("Reject", color = VybalError)
                                            }
                                            TextButton(
                                                onClick = {
                                                    onReviewRequest(req, true, selectedTier, "Approved by Super Admin")
                                                }
                                            ) {
                                                Text("Approve Badge", color = VybalViolet)
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
        } else {
            OutlinedTextField(
                value = manualSearch,
                onValueChange = { manualSearch = it },
                placeholder = { Text("Find any user to verify or unverify") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = VybalTextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VybalViolet,
                    unfocusedBorderColor = VybalOutline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VybalSpacing.md, vertical = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(VybalSpacing.md),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val users = (usersState as? ResourceState.Success)?.data.orEmpty()
                val q = manualSearch.trim().lowercase()
                val matches = users.filter {
                    q.isEmpty() || it.username.lowercase().contains(q) ||
                        it.displayName.lowercase().contains(q) ||
                        it.email.lowercase().contains(q)
                }
                items(matches, key = { "manual_${it.id}" }) { user ->
                    val isSuperAdmin = user.isSuperAdmin ||
                        user.email.equals(VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL, ignoreCase = true)
                    VybalSurfaceCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "@${user.username.ifBlank { user.id.take(8) }}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            VybalVerifiedBadge(
                                isVerified = user.isVerified,
                                verificationTier = user.verificationTier,
                                isSuperAdmin = isSuperAdmin,
                                showLabel = true
                            )
                        }
                        if (!isSuperAdmin) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(onClick = { onManualVerify(user, true, "creator") }) {
                                    Text("Creator", color = VybalLavender)
                                }
                                TextButton(onClick = { onManualVerify(user, true, "business") }) {
                                    Text("Business", color = VybalViolet)
                                }
                                if (user.isVerified) {
                                    TextButton(onClick = { onManualVerify(user, false, null) }) {
                                        Text("Remove", color = VybalError)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Permanent Super Admin verification is protected.",
                                style = MaterialTheme.typography.labelSmall,
                                color = VybalTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
