package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VybalError
import com.example.ui.theme.VybalLavender
import com.example.ui.theme.VybalPurpleDeep
import com.example.ui.theme.VybalSurfaceHigh
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet
import com.example.ui.theme.VybalVioletBright
import com.example.ui.theme.VybalVioletContainer
import com.example.ui.theme.VybalWarning

/**
 * Purple verified checkmark icon shown wherever a verified user or Super Admin identity appears.
 */
@Composable
fun VybalVerifiedBadge(
    isVerified: Boolean,
    verificationTier: String? = null,
    isSuperAdmin: Boolean = false,
    iconSize: Dp = 16.dp,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!isVerified && !isSuperAdmin) return

    val badgeColor = when {
        isSuperAdmin || verificationTier.equals("super_admin", ignoreCase = true) -> VybalVioletBright
        verificationTier.equals("business", ignoreCase = true) -> VybalLavender
        else -> VybalViolet
    }

    val tierLabel = when {
        isSuperAdmin || verificationTier.equals("super_admin", ignoreCase = true) -> "VERIFIED"
        verificationTier.equals("business", ignoreCase = true) -> "BUSINESS"
        verificationTier.equals("creator", ignoreCase = true) -> "CREATOR"
        else -> "VERIFIED"
    }

    if (showLabel) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VybalVioletContainer,
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f)),
            modifier = modifier.testTag("vybal_verified_badge_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = "Verified $tierLabel badge",
                    tint = badgeColor,
                    modifier = Modifier.size(iconSize)
                )
                Text(
                    text = tierLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VybalLavender
                )
            }
        }
    } else {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = "Verified $tierLabel badge",
            tint = badgeColor,
            modifier = modifier
                .size(iconSize)
                .testTag("vybal_verified_badge_icon")
        )
    }
}

/**
 * Purple gradient designation pill for the permanent Super Admin or staff roles.
 */
@Composable
fun VybalRoleDesignationChip(
    role: String,
    modifier: Modifier = Modifier
) {
    when (role.lowercase()) {
        "super_admin" -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(VybalPurpleDeep, VybalViolet)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("vybal_super_admin_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Super Admin designation",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "SUPER ADMIN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        "admin", "moderator" -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VybalSurfaceHigh,
                border = BorderStroke(1.dp, VybalViolet.copy(alpha = 0.5f)),
                modifier = modifier
            ) {
                Text(
                    text = role.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VybalLavender,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun VybalAccountStatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status.lowercase()) {
        "banned" -> VybalError.copy(alpha = 0.18f) to VybalError
        "suspended" -> VybalWarning.copy(alpha = 0.18f) to VybalWarning
        else -> VybalSurfaceHigh to VybalTextSecondary
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
