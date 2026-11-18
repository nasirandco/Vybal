package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.di.VybalAppContainer
import com.example.data.model.AdminDashboardMetrics
import com.example.data.model.AdminPermissionRecord
import com.example.data.model.AdminSection
import com.example.data.model.AdminStoryRecord
import com.example.data.model.AuditLogEntry
import com.example.data.model.ModerationReportRecord
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.PlatformSettings
import com.example.data.model.ResourceState
import com.example.data.model.VerificationRequestRecord
import com.example.data.model.VybalComment
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(
    private val container: VybalAppContainer
) : ViewModel() {

    private val _isDashboardOpen = MutableStateFlow(false)
    val isDashboardOpen: StateFlow<Boolean> = _isDashboardOpen.asStateFlow()

    private val _selectedSection = MutableStateFlow(AdminSection.Overview)
    val selectedSection: StateFlow<AdminSection> = _selectedSection.asStateFlow()

    private val _metricsState = MutableStateFlow<ResourceState<AdminDashboardMetrics>>(ResourceState.Idle)
    val metricsState: StateFlow<ResourceState<AdminDashboardMetrics>> = _metricsState.asStateFlow()

    private val _usersState = MutableStateFlow<ResourceState<List<VybalProfile>>>(ResourceState.Idle)
    val usersState: StateFlow<ResourceState<List<VybalProfile>>> = _usersState.asStateFlow()

    private val _verificationState =
        MutableStateFlow<ResourceState<List<VerificationRequestRecord>>>(ResourceState.Idle)
    val verificationState: StateFlow<ResourceState<List<VerificationRequestRecord>>> =
        _verificationState.asStateFlow()

    private val _reportsState =
        MutableStateFlow<ResourceState<List<ModerationReportRecord>>>(ResourceState.Idle)
    val reportsState: StateFlow<ResourceState<List<ModerationReportRecord>>> = _reportsState.asStateFlow()

    private val _moderationPostsState =
        MutableStateFlow<ResourceState<List<VybalPost>>>(ResourceState.Idle)
    val moderationPostsState: StateFlow<ResourceState<List<VybalPost>>> =
        _moderationPostsState.asStateFlow()

    private val _moderationCommentsState =
        MutableStateFlow<ResourceState<List<VybalComment>>>(ResourceState.Idle)
    val moderationCommentsState: StateFlow<ResourceState<List<VybalComment>>> =
        _moderationCommentsState.asStateFlow()

    private val _moderationStoriesState =
        MutableStateFlow<ResourceState<List<AdminStoryRecord>>>(ResourceState.Idle)
    val moderationStoriesState: StateFlow<ResourceState<List<AdminStoryRecord>>> =
        _moderationStoriesState.asStateFlow()

    private val _permissionsMap = MutableStateFlow<Map<String, AdminPermissionRecord>>(emptyMap())
    val permissionsMap: StateFlow<Map<String, AdminPermissionRecord>> = _permissionsMap.asStateFlow()

    private val _announcementsState =
        MutableStateFlow<ResourceState<List<PlatformAnnouncement>>>(ResourceState.Idle)
    val announcementsState: StateFlow<ResourceState<List<PlatformAnnouncement>>> =
        _announcementsState.asStateFlow()

    private val _platformSettings = MutableStateFlow(PlatformSettings())
    val platformSettings: StateFlow<PlatformSettings> = _platformSettings.asStateFlow()

    private val _auditLogsState =
        MutableStateFlow<ResourceState<List<AuditLogEntry>>>(ResourceState.Idle)
    val auditLogsState: StateFlow<ResourceState<List<AuditLogEntry>>> = _auditLogsState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshPublicPlatformState()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun openDashboard() {
        _isDashboardOpen.value = true
        refreshAllDashboardData()
    }

    fun closeDashboard() {
        _isDashboardOpen.value = false
    }

    fun selectSection(section: AdminSection) {
        _selectedSection.value = section
        when (section) {
            AdminSection.Overview -> refreshMetrics()
            AdminSection.Users -> refreshUsers()
            AdminSection.Verification -> {
                refreshVerifications()
                refreshUsers()
            }
            AdminSection.Moderation -> refreshModeration()
            AdminSection.Administrators -> {
                refreshUsers()
                refreshPermissions()
                refreshAuditLogs()
            }
            AdminSection.Platform -> {
                refreshPublicPlatformState()
                refreshAuditLogs()
            }
        }
    }

    fun refreshPublicPlatformState() {
        viewModelScope.launch {
            val settingsRes = container.adminRepository.fetchPlatformSettings()
            if (settingsRes is ResourceState.Success) {
                _platformSettings.value = settingsRes.data
            }
            _announcementsState.value = container.adminRepository.fetchAnnouncements()
        }
    }

    fun refreshAllDashboardData() {
        refreshMetrics()
        refreshUsers()
        refreshVerifications()
        refreshModeration()
        refreshPermissions()
        refreshPublicPlatformState()
        refreshAuditLogs()
    }

    fun refreshMetrics() {
        viewModelScope.launch {
            _metricsState.value = ResourceState.Loading
            _metricsState.value = container.adminRepository.fetchDashboardMetrics()
        }
    }

    fun refreshUsers() {
        viewModelScope.launch {
            _usersState.value = ResourceState.Loading
            _usersState.value = container.adminRepository.fetchAllUsers()
        }
    }

    fun refreshVerifications() {
        viewModelScope.launch {
            _verificationState.value = ResourceState.Loading
            _verificationState.value = container.adminRepository.fetchVerificationRequests()
        }
    }

    fun refreshModeration() {
        viewModelScope.launch {
            _reportsState.value = ResourceState.Loading
            _moderationPostsState.value = ResourceState.Loading
            _moderationCommentsState.value = ResourceState.Loading
            _moderationStoriesState.value = ResourceState.Loading
            _reportsState.value = container.adminRepository.fetchPostReports()
            _moderationPostsState.value = container.adminRepository.fetchAllPostsForModeration()
            _moderationCommentsState.value = container.adminRepository.fetchRecentCommentsForModeration()
            _moderationStoriesState.value = container.adminRepository.fetchStoriesForModeration()
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val res = container.adminRepository.fetchAdminPermissions()
            if (res is ResourceState.Success) {
                _permissionsMap.value = res.data
            }
        }
    }

    fun refreshAuditLogs() {
        viewModelScope.launch {
            _auditLogsState.value = ResourceState.Loading
            _auditLogsState.value = container.adminRepository.fetchAuditLogs()
        }
    }

    fun updateUser(
        user: VybalProfile,
        displayName: String? = null,
        bio: String? = null,
        role: String? = null,
        isVerified: Boolean? = null,
        verificationTier: String? = null,
        accountStatus: String? = null,
        restrictionReason: String? = null,
        successMessage: String = "User updated."
    ) {
        viewModelScope.launch {
            when (
                val res = container.adminRepository.updateUser(
                    targetUserId = user.id,
                    targetEmail = user.email,
                    displayName = displayName,
                    bio = bio,
                    role = role,
                    isVerified = isVerified,
                    verificationTier = verificationTier,
                    accountStatus = accountStatus,
                    restrictionReason = restrictionReason
                )
            ) {
                is ResourceState.Success -> {
                    _statusMessage.value = successMessage
                    refreshUsers()
                    refreshMetrics()
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun deleteUser(user: VybalProfile) {
        viewModelScope.launch {
            when (val res = container.adminRepository.deleteUserAccount(user.id, user.email)) {
                is ResourceState.Success -> {
                    _statusMessage.value = "Account @${user.username.ifBlank { user.id.take(8) }} deleted."
                    refreshUsers()
                    refreshMetrics()
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun reviewVerification(
        request: VerificationRequestRecord,
        approve: Boolean,
        badgeTier: String,
        notes: String
    ) {
        viewModelScope.launch {
            val decision = if (approve) "approved" else "rejected"
            when (
                val res = container.adminRepository.reviewVerificationRequest(
                    requestId = request.id,
                    decision = decision,
                    badgeTier = badgeTier,
                    notes = notes
                )
            ) {
                is ResourceState.Success -> {
                    _statusMessage.value = if (approve) {
                        "Verification approved ($badgeTier)."
                    } else {
                        "Verification request rejected."
                    }
                    refreshVerifications()
                    refreshUsers()
                    refreshMetrics()
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun submitOwnVerificationRequest(tier: String, reason: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = container.adminRepository.submitVerificationRequest(tier, reason)) {
                is ResourceState.Success -> onDone(true, "Verification request submitted for Super Admin review.")
                is ResourceState.Error -> onDone(false, res.message)
                ResourceState.AuthRequired -> onDone(false, "Sign in to request verification.")
                else -> onDone(false, "Supabase is not configured.")
            }
        }
    }

    fun moderateContent(
        contentType: String,
        contentId: String,
        reportId: String? = null,
        action: String = "remove",
        reason: String = ""
    ) {
        viewModelScope.launch {
            when (
                val res = container.adminRepository.moderateContent(
                    contentType = contentType,
                    contentId = contentId,
                    reportId = reportId,
                    action = action,
                    reason = reason
                )
            ) {
                is ResourceState.Success -> {
                    _statusMessage.value = if (action == "remove") {
                        "Content removed and logged."
                    } else {
                        "Report dismissed and logged."
                    }
                    refreshModeration()
                    refreshMetrics()
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun savePermissions(record: AdminPermissionRecord) {
        viewModelScope.launch {
            when (val res = container.adminRepository.saveAdminPermissions(record)) {
                is ResourceState.Success -> {
                    _statusMessage.value = "Administrator permissions updated."
                    refreshPermissions()
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun createAnnouncement(title: String, body: String, audience: String) {
        viewModelScope.launch {
            when (val res = container.adminRepository.createAnnouncement(title, body, audience)) {
                is ResourceState.Success -> {
                    _statusMessage.value = "Announcement published."
                    refreshPublicPlatformState()
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun toggleAnnouncement(id: String, active: Boolean) {
        viewModelScope.launch {
            when (val res = container.adminRepository.toggleAnnouncement(id, active)) {
                is ResourceState.Success -> {
                    _statusMessage.value = if (active) "Announcement activated." else "Announcement paused."
                    refreshPublicPlatformState()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            when (val res = container.adminRepository.deleteAnnouncement(id)) {
                is ResourceState.Success -> {
                    _statusMessage.value = "Announcement deleted."
                    refreshPublicPlatformState()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun broadcastNotification(message: String) {
        val allUsers = (_usersState.value as? ResourceState.Success)?.data.orEmpty()
        if (allUsers.isEmpty()) {
            _statusMessage.value = "Load users first before sending a platform notification."
            return
        }
        viewModelScope.launch {
            when (val res = container.adminRepository.sendBroadcastNotification(allUsers.map { it.id }, message)) {
                is ResourceState.Success -> {
                    _statusMessage.value = "Notification sent to ${res.data} accounts."
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    fun savePlatformSettings(updated: PlatformSettings) {
        viewModelScope.launch {
            when (val res = container.adminRepository.updatePlatformSettings(updated)) {
                is ResourceState.Success -> {
                    _platformSettings.value = updated
                    _statusMessage.value = "Platform settings saved."
                    refreshAuditLogs()
                }
                is ResourceState.Error -> _statusMessage.value = res.message
                else -> Unit
            }
        }
    }

    class Factory(private val container: VybalAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminViewModel(container) as T
        }
    }
}
