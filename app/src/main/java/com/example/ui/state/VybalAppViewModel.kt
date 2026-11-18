package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.config.VybalSupabaseConfig
import com.example.core.di.VybalAppContainer
import com.example.core.performance.NetworkStatus
import com.example.data.auth.AuthResult
import com.example.data.auth.AuthValidator
import com.example.data.auth.PasswordResetResult
import com.example.data.model.ResourceState
import com.example.data.model.UserSession
import com.example.data.model.VybalMessage
import com.example.data.model.VybalNotification
import com.example.data.model.VybalPost
import com.example.data.model.VybalProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
    SignIn,
    SignUp,
    ResetPassword
}

data class AuthUiState(
    val isSheetOpen: Boolean = false,
    val mode: AuthMode = AuthMode.SignIn,
    val isSubmitting: Boolean = false,
    val isSigningOut: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val usernameError: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

data class CreatePostUiState(
    val isPublishing: Boolean = false,
    val feedbackMessage: String? = null,
    val isError: Boolean = false,
    val publishedTick: Int = 0
)

class VybalAppViewModel(
    private val container: VybalAppContainer
) : ViewModel() {

    val isSupabaseConfigured: Boolean
        get() = VybalSupabaseConfig.isConfigured

    val session: StateFlow<UserSession?> = container.authRepository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dataSaverEnabled: StateFlow<Boolean> = container.sessionStore.dataSaverFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val networkStatus: StateFlow<NetworkStatus> = container.networkMonitor.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkStatus())

    private val _isSessionLoading = MutableStateFlow(true)
    val isSessionLoading: StateFlow<Boolean> = _isSessionLoading.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _homeFeedState = MutableStateFlow<ResourceState<List<VybalPost>>>(ResourceState.Idle)
    val homeFeedState: StateFlow<ResourceState<List<VybalPost>>> = _homeFeedState.asStateFlow()

    private val _exploreQuery = MutableStateFlow("")
    val exploreQuery: StateFlow<String> = _exploreQuery.asStateFlow()

    private val _exploreState = MutableStateFlow<ResourceState<List<VybalPost>>>(ResourceState.Idle)
    val exploreState: StateFlow<ResourceState<List<VybalPost>>> = _exploreState.asStateFlow()

    private val _createState = MutableStateFlow(CreatePostUiState())
    val createState: StateFlow<CreatePostUiState> = _createState.asStateFlow()

    private val _notificationsState =
        MutableStateFlow<ResourceState<List<VybalNotification>>>(ResourceState.Idle)
    val notificationsState: StateFlow<ResourceState<List<VybalNotification>>> =
        _notificationsState.asStateFlow()

    private val _messagesState =
        MutableStateFlow<ResourceState<List<VybalMessage>>>(ResourceState.Idle)
    val messagesState: StateFlow<ResourceState<List<VybalMessage>>> = _messagesState.asStateFlow()

    private val _profileState = MutableStateFlow<ResourceState<VybalProfile?>>(ResourceState.Idle)
    val profileState: StateFlow<ResourceState<VybalProfile?>> = _profileState.asStateFlow()

    private val _profilePostsState =
        MutableStateFlow<ResourceState<List<VybalPost>>>(ResourceState.Idle)
    val profilePostsState: StateFlow<ResourceState<List<VybalPost>>> =
        _profilePostsState.asStateFlow()

    private var pendingSignUpUsername: String? = null
    private var pendingSignUpDisplayName: String? = null

    init {
        viewModelScope.launch {
            _isSessionLoading.value = true
            container.authRepository.restoreOrRefreshSession()
            _isSessionLoading.value = false
            refreshHomeFeed()
            refreshExplore()
        }
        viewModelScope.launch {
            var lastUserId: String? = null
            var hasObservedInitial = false
            session.collect { activeSession ->
                if (_isSessionLoading.value && activeSession == null) return@collect
                val currentUserId = activeSession?.userId
                if (hasObservedInitial && currentUserId == lastUserId) return@collect
                hasObservedInitial = true
                lastUserId = currentUserId

                if (activeSession != null) {
                    val usernameHint = pendingSignUpUsername
                    val displayHint = pendingSignUpDisplayName
                    pendingSignUpUsername = null
                    pendingSignUpDisplayName = null
                    refreshProfile(
                        fallbackUsername = usernameHint,
                        fallbackDisplayName = displayHint,
                        waitForTrigger = usernameHint != null
                    )
                    refreshInbox()
                } else {
                    val fallback = if (isSupabaseConfigured) {
                        ResourceState.AuthRequired
                    } else {
                        ResourceState.NotConfigured
                    }
                    _profileState.value = fallback
                    _profilePostsState.value = fallback
                    _notificationsState.value = fallback
                    _messagesState.value = fallback
                }
            }
        }
    }

    fun openAuthSheet(mode: AuthMode = AuthMode.SignIn) {
        _authUiState.value = AuthUiState(
            isSheetOpen = true,
            mode = mode
        )
    }

    fun dismissAuthSheet() {
        _authUiState.update {
            it.copy(
                isSheetOpen = false,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                usernameError = null,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun setAuthMode(mode: AuthMode) {
        _authUiState.update {
            it.copy(
                mode = mode,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                usernameError = null,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun submitSignIn(email: String, password: String) {
        val emailErr = AuthValidator.validateEmail(email)
        val passErr = AuthValidator.validatePassword(password)
        if (emailErr != null || passErr != null) {
            _authUiState.update {
                it.copy(
                    emailError = emailErr,
                    passwordError = passErr,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            _authUiState.update {
                it.copy(
                    isSubmitting = true,
                    emailError = null,
                    passwordError = null,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            when (val result = container.authRepository.signIn(email, password)) {
                is AuthResult.Authenticated -> {
                    _authUiState.value = AuthUiState(isSheetOpen = false)
                    refreshHomeFeed()
                    refreshExplore()
                }
                AuthResult.EmailConfirmationRequired -> {
                    _authUiState.update {
                        it.copy(
                            isSubmitting = false,
                            infoMessage = "Please verify your email address before signing in."
                        )
                    }
                }
                is AuthResult.Failure -> {
                    _authUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun submitSignUp(
        email: String,
        password: String,
        confirmPassword: String,
        username: String,
        displayName: String
    ) {
        val emailErr = AuthValidator.validateEmail(email)
        val passErr = AuthValidator.validatePassword(password)
        val confirmErr = AuthValidator.validateConfirmPassword(password, confirmPassword)
        val userErr = AuthValidator.validateUsername(username)

        if (emailErr != null || passErr != null || confirmErr != null || userErr != null) {
            _authUiState.update {
                it.copy(
                    emailError = emailErr,
                    passwordError = passErr,
                    confirmPasswordError = confirmErr,
                    usernameError = userErr,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            _authUiState.update {
                it.copy(
                    isSubmitting = true,
                    emailError = null,
                    passwordError = null,
                    confirmPasswordError = null,
                    usernameError = null,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            pendingSignUpUsername = username.trim().lowercase()
            pendingSignUpDisplayName = displayName.trim().ifBlank { username.trim() }

            when (
                val result = container.authRepository.signUp(
                    email = email,
                    password = password,
                    username = username,
                    displayName = displayName
                )
            ) {
                is AuthResult.Authenticated -> {
                    _authUiState.value = AuthUiState(isSheetOpen = false)
                    refreshHomeFeed()
                    refreshExplore()
                }
                AuthResult.EmailConfirmationRequired -> {
                    pendingSignUpUsername = null
                    pendingSignUpDisplayName = null
                    _authUiState.update {
                        it.copy(
                            isSubmitting = false,
                            mode = AuthMode.SignIn,
                            infoMessage = "Account created. Check your email to confirm your address, then sign in."
                        )
                    }
                }
                is AuthResult.Failure -> {
                    pendingSignUpUsername = null
                    pendingSignUpDisplayName = null
                    _authUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun submitPasswordReset(email: String) {
        val emailErr = AuthValidator.validateEmail(email)
        if (emailErr != null) {
            _authUiState.update {
                it.copy(emailError = emailErr, errorMessage = null, infoMessage = null)
            }
            return
        }

        viewModelScope.launch {
            _authUiState.update {
                it.copy(
                    isSubmitting = true,
                    emailError = null,
                    errorMessage = null,
                    infoMessage = null
                )
            }
            when (val result = container.authRepository.sendPasswordReset(email)) {
                PasswordResetResult.Sent -> {
                    _authUiState.update {
                        it.copy(
                            isSubmitting = false,
                            infoMessage = "Password reset link sent. Check your inbox and follow the instructions."
                        )
                    }
                }
                is PasswordResetResult.Failure -> {
                    _authUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _authUiState.update { it.copy(isSigningOut = true) }
            container.authRepository.signOut()
            _authUiState.update { it.copy(isSigningOut = false) }
            _createState.value = CreatePostUiState()
            refreshHomeFeed()
            refreshExplore()
        }
    }

    fun setDataSaver(enabled: Boolean) {
        viewModelScope.launch {
            container.sessionStore.setDataSaver(enabled)
        }
    }

    fun refreshHomeFeed() {
        viewModelScope.launch {
            _homeFeedState.value = ResourceState.Loading
            _homeFeedState.value = container.vybalRepository.fetchHomeFeed()
        }
    }

    fun updateExploreQuery(query: String) {
        _exploreQuery.value = query
    }

    fun refreshExplore(queryOverride: String? = null) {
        val query = queryOverride ?: _exploreQuery.value
        viewModelScope.launch {
            _exploreState.value = ResourceState.Loading
            _exploreState.value = container.vybalRepository.fetchExplorePosts(query)
        }
    }

    fun publishPost(
        content: String,
        mediaUri: android.net.Uri?,
        context: android.content.Context
    ) {
        if (content.isBlank() && mediaUri == null) {
            _createState.update {
                it.copy(feedbackMessage = "Add text or attach a photo before publishing.", isError = true)
            }
            return
        }
        viewModelScope.launch {
            _createState.update { it.copy(isPublishing = true, feedbackMessage = null, isError = false) }
            val prepared = mediaUri?.let {
                com.example.core.performance.MediaCompressor.prepareImageForUpload(context, it)
            }
            if (mediaUri != null && prepared == null) {
                _createState.update {
                    it.copy(
                        isPublishing = false,
                        feedbackMessage = "Unable to read the selected image.",
                        isError = true
                    )
                }
                return@launch
            }
            when (
                val result = container.vybalRepository.publishPost(
                    content = content,
                    mediaBytes = prepared?.bytes,
                    mimeType = prepared?.mimeType
                )
            ) {
                is ResourceState.Success -> {
                    container.engagementRepository.notifyMentionsInPost(
                        postId = result.data.id,
                        text = content
                    )
                    _createState.update {
                        it.copy(
                            isPublishing = false,
                            feedbackMessage = "Post published to VYBAL.",
                            isError = false,
                            publishedTick = it.publishedTick + 1
                        )
                    }
                    refreshHomeFeed()
                    refreshProfile()
                }
                ResourceState.NotConfigured -> {
                    _createState.update {
                        it.copy(
                            isPublishing = false,
                            feedbackMessage = "Publishing is temporarily unavailable. Please try again shortly.",
                            isError = true
                        )
                    }
                }
                ResourceState.AuthRequired -> {
                    _createState.update {
                        it.copy(
                            isPublishing = false,
                            feedbackMessage = "Sign in to publish posts to VYBAL.",
                            isError = true
                        )
                    }
                    openAuthSheet(AuthMode.SignIn)
                }
                is ResourceState.Error -> {
                    _createState.update {
                        it.copy(isPublishing = false, feedbackMessage = result.message, isError = true)
                    }
                }
                else -> {
                    _createState.update { it.copy(isPublishing = false) }
                }
            }
        }
    }

    fun clearCreateFeedback() {
        _createState.update { it.copy(feedbackMessage = null, isError = false) }
    }

    fun refreshInbox() {
        viewModelScope.launch {
            _notificationsState.value = ResourceState.Loading
            _messagesState.value = ResourceState.Loading
            _notificationsState.value = container.vybalRepository.fetchNotifications()
            _messagesState.value = container.vybalRepository.fetchMessages()
        }
    }

    fun refreshProfile(
        fallbackUsername: String? = null,
        fallbackDisplayName: String? = null,
        waitForTrigger: Boolean = false
    ) {
        viewModelScope.launch {
            _profileState.value = ResourceState.Loading
            _profilePostsState.value = ResourceState.Loading
            _profileState.value = container.vybalRepository.fetchCurrentProfile(
                fallbackUsername = fallbackUsername,
                fallbackDisplayName = fallbackDisplayName,
                waitForTrigger = waitForTrigger
            )
            _profilePostsState.value = container.vybalRepository.fetchCurrentUserPosts()
        }
    }

    fun updateProfile(displayName: String, bio: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val result = container.vybalRepository.updateProfile(displayName, bio)) {
                is ResourceState.Success -> {
                    _profileState.value = ResourceState.Success(result.data)
                    onDone(true, null)
                }
                is ResourceState.Error -> onDone(false, result.message)
                ResourceState.AuthRequired -> onDone(false, "Sign in to update your profile.")
                ResourceState.NotConfigured -> onDone(false, "Supabase is not configured yet.")
                else -> onDone(false, null)
            }
        }
    }

    class Factory(private val container: VybalAppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VybalAppViewModel(container) as T
        }
    }
}
