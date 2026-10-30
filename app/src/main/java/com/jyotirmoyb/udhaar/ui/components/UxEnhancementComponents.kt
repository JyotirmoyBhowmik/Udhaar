package com.jyotirmoyb.udhaar.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import kotlin.math.abs

/**
 * Checks whether the user has enabled "Remove animations" / reduced motion in Android system settings.
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

private val AvatarPalette = listOf(
    Color(0xFF14532D) to Color(0xFFDCFCE7),
    Color(0xFF1E3A8A) to Color(0xFFDBEAFE),
    Color(0xFF7C2D12) to Color(0xFFFFEDD5),
    Color(0xFF581C87) to Color(0xFFF3E8FF),
    Color(0xFF831843) to Color(0xFFFCE7F3),
    Color(0xFF134E4A) to Color(0xFFCCFBF1),
    Color(0xFF713F12) to Color(0xFFFEF9C3)
)

fun extractInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
        parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> "U"
    }
}

/**
 * Person avatar showing either a contact photo or high-contrast coloured initials.
 */
@Composable
fun PersonAvatar(
    name: String,
    photoUri: String = "",
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) { extractInitials(name) }
    val (fgColor, bgColor) = remember(name) {
        val index = abs(name.trim().lowercase().hashCode()) % AvatarPalette.size
        AvatarPalette[index]
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .semantics {
                contentDescription = "Avatar for ${name.ifBlank { "Contact" }}"
            },
        contentAlignment = Alignment.Center
    ) {
        if (photoUri.isNotBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Photo of $name",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                color = fgColor,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Status at a glance badge: always combines a semantic icon + clear plain-language label (never colour alone).
 */
@Composable
fun StatusAtAGlanceBadge(
    summary: CalculatedUdhaarSummary,
    modifier: Modifier = Modifier
) {
    val tx = summary.transaction
    val now = System.currentTimeMillis()
    val overdueDays = if (summary.isOverdue && tx.expiryDateMillis != null) {
        maxOf(1L, (now - tx.expiryDateMillis) / (1000L * 60 * 60 * 24))
    } else {
        0L
    }

    data class StatusVisual(
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val containerColor: Color,
        val contentColor: Color,
        val tag: String
    )

    val visual: StatusVisual? = when {
        tx.isSettled -> StatusVisual(
            label = "Settled",
            icon = Icons.Default.CheckCircle,
            containerColor = Color(0xFFDCFCE7),
            contentColor = Color(0xFF14532D),
            tag = "status_badge_settled"
        )
        tx.verificationStatus == VerificationStatus.DISPUTED -> StatusVisual(
            label = "Disputed",
            icon = Icons.Default.Warning,
            containerColor = Color(0xFFFEE2E2),
            contentColor = Color(0xFF7F1D1D),
            tag = "status_badge_disputed"
        )
        summary.isOverdue -> StatusVisual(
            label = if (overdueDays == 1L) "Overdue 1 day" else "Overdue $overdueDays days",
            icon = Icons.Default.ErrorOutline,
            containerColor = Color(0xFFFEE2E2),
            contentColor = Color(0xFF7F1D1D),
            tag = "status_badge_overdue"
        )
        tx.verificationStatus == VerificationStatus.CONFIRMED -> StatusVisual(
            label = "Verified",
            icon = Icons.Default.Verified,
            containerColor = Color(0xFFDCFCE7),
            contentColor = Color(0xFF14532D),
            tag = "status_badge_verified"
        )
        tx.needsMyConfirmation ||
            tx.verificationStatus == VerificationStatus.PENDING ||
            tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM -> StatusVisual(
            label = "Waiting for confirmation",
            icon = Icons.Default.Schedule,
            containerColor = Color(0xFFFEF3C7),
            contentColor = Color(0xFF78350F),
            tag = "status_badge_waiting"
        )
        else -> null
    }

    if (visual != null) {
        Surface(
            modifier = modifier.testTag("${visual.tag}_${tx.id}"),
            color = visual.containerColor,
            contentColor = visual.contentColor,
            shape = UdhaarShapeTokens.ShapeSmall,
            border = BorderStroke(1.dp, visual.contentColor.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = visual.label,
                    tint = visual.contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = visual.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = visual.contentColor
                )
            }
        }
    }
}

/**
 * Single dismissible needs-attention strip above the list when something is overdue, due today,
 * or waiting for confirmation. Tapping it filters the list.
 */
@Composable
fun NeedsAttentionStrip(
    overdueCount: Int,
    dueTodayCount: Int,
    waitingConfirmationCount: Int,
    onFilterSelect: (LedgerFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (overdueCount <= 0 && dueTodayCount <= 0 && waitingConfirmationCount <= 0) return

    val (headline, subtitle, targetFilter, accentColor, containerColor) = when {
        overdueCount > 0 -> AttentionConfig(
            headline = if (overdueCount == 1) "1 udhaar is overdue" else "$overdueCount udhaars are overdue",
            subtitle = "Tap to view overdue records and send a gentle reminder",
            filter = LedgerFilter.OVERDUE,
            accent = Color(0xFF991B1B),
            container = Color(0xFFFEF2F2)
        )
        dueTodayCount > 0 -> AttentionConfig(
            headline = if (dueTodayCount == 1) "1 udhaar is due today" else "$dueTodayCount udhaars are due today",
            subtitle = "Tap to review records due today",
            filter = LedgerFilter.OVERDUE,
            accent = Color(0xFF92400E),
            container = Color(0xFFFFFBEB)
        )
        else -> AttentionConfig(
            headline = if (waitingConfirmationCount == 1) {
                "1 record waiting for confirmation"
            } else {
                "$waitingConfirmationCount records waiting for confirmation"
            },
            subtitle = "Tap to review and confirm shared ledger entries",
            filter = LedgerFilter.NEEDS_CONFIRMATION,
            accent = Color(0xFF1E40AF),
            container = Color(0xFFEFF6FF)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("needs_attention_strip")
            .clickable { onFilterSelect(targetFilter) },
        shape = UdhaarShapeTokens.ShapeMedium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (overdueCount > 0 || dueTodayCount > 0) {
                        Icons.Default.NotificationsActive
                    } else {
                        Icons.Default.Schedule
                    },
                    contentDescription = "Needs attention",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("dismiss_needs_attention_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss needs attention alert",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class AttentionConfig(
    val headline: String,
    val subtitle: String,
    val filter: LedgerFilter,
    val accent: Color,
    val container: Color
)

/**
 * First-run coach-marks card shown after onboarding with three quick tips (Add, Remind, Verify)
 * that can be stepped through or skipped at any time.
 */
@Composable
fun FirstRunCoachMarksCard(
    onSkipOrFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    val tips = listOf(
        Triple(
            "Tip 1 of 3 · Add in seconds",
            "Tap + NEW UDHAAR to pick a person and enter the amount lent or borrowed.",
            Icons.Default.Add
        ),
        Triple(
            "Tip 2 of 3 · Swipe to act",
            "Swipe right on any record to log a payment, or swipe left to send a polite reminder.",
            Icons.Default.NotificationsActive
        ),
        Triple(
            "Tip 3 of 3 · Verify together",
            "Open any record to share a verification link so both sides agree on the balance.",
            Icons.Default.Verified
        )
    )
    val (title, body, icon) = tips[step]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("first_run_coach_marks_card"),
        shape = UdhaarShapeTokens.ShapeLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                TextButton(
                    onClick = onSkipOrFinish,
                    modifier = Modifier.testTag("skip_coach_marks_button")
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        if (step < tips.lastIndex) {
                            step++
                        } else {
                            onSkipOrFinish()
                        }
                    },
                    shape = UdhaarShapeTokens.ShapeMedium,
                    modifier = Modifier.testTag("next_coach_mark_button")
                ) {
                    Text(
                        text = if (step < tips.lastIndex) "Next tip" else "Got it",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Shimmer placeholder shown while ledger records are loading.
 */
@Composable
fun ShimmerLedgerPlaceholder(
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val alpha = if (reducedMotion) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer_transition")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer_alpha"
        )
        animated
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shimmer_loading_placeholder"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = UdhaarShapeTokens.ShapeLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(18.dp)
                                .clip(UdhaarShapeTokens.ShapeSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(18.dp)
                                .clip(UdhaarShapeTokens.ShapeSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(18.dp)
                                        .clip(UdhaarShapeTokens.ShapeSmall)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(12.dp)
                                        .clip(UdhaarShapeTokens.ShapeSmall)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(85.dp)
                                .height(24.dp)
                                .clip(UdhaarShapeTokens.ShapeSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Friendly empty state with custom vector illustration, a single clear sentence, and action button(s).
 */
@Composable
fun FriendlyEmptyStateCard(
    sentence: String,
    primaryButtonLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("friendly_empty_state_card"),
        shape = UdhaarShapeTokens.ShapeLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(accentBg),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Ledger book illustration" }
                ) {
                    val w = size.width
                    val h = size.height
                    // Friendly notebook + checkmark illustration
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.18f),
                        topLeft = Offset(w * 0.15f, h * 0.1f),
                        size = Size(w * 0.7f, h * 0.8f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(w * 0.15f, h * 0.1f),
                        size = Size(w * 0.7f, h * 0.8f),
                        cornerRadius = CornerRadius(12f, 12f),
                        style = Stroke(width = 4f)
                    )
                    drawLine(
                        color = primaryColor,
                        start = Offset(w * 0.3f, h * 0.32f),
                        end = Offset(w * 0.7f, h * 0.32f),
                        strokeWidth = 3.5f
                    )
                    drawLine(
                        color = primaryColor.copy(alpha = 0.7f),
                        start = Offset(w * 0.3f, h * 0.48f),
                        end = Offset(w * 0.6f, h * 0.48f),
                        strokeWidth = 3.5f
                    )
                    val checkPath = Path().apply {
                        moveTo(w * 0.33f, h * 0.67f)
                        lineTo(w * 0.45f, h * 0.77f)
                        lineTo(w * 0.68f, h * 0.56f)
                    }
                    drawPath(
                        path = checkPath,
                        color = primaryColor,
                        style = Stroke(width = 4f)
                    )
                }
            }

            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onPrimaryClick,
                shape = UdhaarShapeTokens.ShapeMedium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_state_primary_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = primaryButtonLabel,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = primaryButtonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (secondaryButtonLabel != null && onSecondaryClick != null) {
                OutlinedButton(
                    onClick = onSecondaryClick,
                    shape = UdhaarShapeTokens.ShapeMedium,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_state_secondary_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = secondaryButtonLabel,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = secondaryButtonLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
