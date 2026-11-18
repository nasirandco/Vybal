package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UserSession
import com.example.ui.components.VybalBrandTopBar
import com.example.ui.components.VybalLoadingCard
import com.example.ui.components.VybalPrimaryButton
import com.example.ui.components.VybalSecondaryButton
import com.example.ui.components.VybalStatePanel
import com.example.ui.components.VybalSurfaceCard
import com.example.ui.state.AuthMode
import com.example.ui.state.CreatePostUiState
import com.example.ui.theme.VybalObsidian
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSuccess
import com.example.ui.theme.VybalSurfaceHigh
import com.example.ui.theme.VybalTextMuted
import com.example.ui.theme.VybalViolet

private const val MAX_POST_LENGTH = 500

@Composable
fun CreateScreen(
    createState: CreatePostUiState,
    session: UserSession?,
    isSessionLoading: Boolean,
    isSupabaseConfigured: Boolean,
    imageLoader: ImageLoader,
    knownHashtags: List<String> = emptyList(),
    onPublish: (content: String, mediaUri: Uri?, context: Context) -> Unit,
    onOpenAuth: (AuthMode) -> Unit,
    onClearFeedback: () -> Unit
) {
    val context = LocalContext.current
    var content by rememberSaveable { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(createState.publishedTick) {
        if (createState.publishedTick > 0) {
            content = ""
            selectedMediaUri = null
        }
    }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            onClearFeedback()
        }
    }

    val activePrefix = remember(content) {
        com.example.core.text.RichSocialText.activeHashtagPrefix(content)
    }
    val hashtagSuggestions = remember(activePrefix, knownHashtags) {
        if (activePrefix == null) emptyList()
        else knownHashtags.filter { it.startsWith(activePrefix) && it != activePrefix }.take(6)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VybalBrandTopBar(
            title = stringResource(R.string.create_title),
            isSupabaseConfigured = isSupabaseConfigured
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VybalSpacing.md, vertical = VybalSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VybalSpacing.md)
        ) {
            if (isSessionLoading) {
                VybalLoadingCard()
            } else if (session == null) {
                VybalStatePanel(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.protected_create_title),
                    body = stringResource(R.string.protected_create_body),
                    primaryActionLabel = stringResource(R.string.action_sign_in),
                    onPrimaryAction = { onOpenAuth(AuthMode.SignIn) },
                    secondaryActionLabel = stringResource(R.string.action_create_account),
                    onSecondaryAction = { onOpenAuth(AuthMode.SignUp) },
                    showAuraHeader = true,
                    modifier = Modifier.testTag("protected_create_gate")
                )
            } else {
                VybalSurfaceCard(
                    contentPadding = PaddingValues(VybalSpacing.md),
                    modifier = Modifier.testTag("create_composer_card")
                ) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { updated ->
                            if (updated.length <= MAX_POST_LENGTH) {
                                content = updated
                                onClearFeedback()
                            }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.create_placeholder),
                                color = VybalTextMuted
                            )
                        },
                        minLines = 5,
                        maxLines = 10,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = VybalObsidian.copy(alpha = 0.6f),
                            unfocusedContainerColor = VybalObsidian.copy(alpha = 0.4f),
                            focusedBorderColor = VybalViolet,
                            unfocusedBorderColor = VybalOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_post_input")
                    )

                    if (hashtagSuggestions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(hashtagSuggestions.size) { index ->
                                val tag = hashtagSuggestions[index]
                                androidx.compose.material3.AssistChip(
                                    onClick = {
                                        content = com.example.core.text.RichSocialText
                                            .applyHashtagSuggestion(content, tag)
                                    },
                                    label = { Text("#$tag") }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${content.length} / $MAX_POST_LENGTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (content.length >= MAX_POST_LENGTH) {
                                MaterialTheme.colorScheme.error
                            } else {
                                VybalTextMuted
                            }
                        )
                    }

                    if (selectedMediaUri != null) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VybalSurfaceHigh)
                        ) {
                            AsyncImage(
                                model = selectedMediaUri,
                                imageLoader = imageLoader,
                                contentDescription = stringResource(R.string.cd_post_media),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = CircleShape,
                                color = VybalObsidian.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, VybalOutline),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                IconButton(
                                    onClick = { selectedMediaUri = null },
                                    modifier = Modifier.testTag("remove_selected_media")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_remove_media),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VybalSecondaryButton(
                            text = stringResource(R.string.action_attach_media),
                            icon = Icons.Outlined.AddPhotoAlternate,
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "attach_media_button"
                        )

                        VybalPrimaryButton(
                            text = stringResource(R.string.action_publish),
                            icon = Icons.Outlined.Send,
                            loading = createState.isPublishing,
                            enabled = !createState.isPublishing && (content.isNotBlank() || selectedMediaUri != null),
                            onClick = {
                                onPublish(content, selectedMediaUri, context)
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "publish_post_button"
                        )
                    }

                    if (!createState.feedbackMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = createState.feedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (createState.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                VybalSuccess
                            }
                        )
                    }
                }
            }
        }
    }
}
