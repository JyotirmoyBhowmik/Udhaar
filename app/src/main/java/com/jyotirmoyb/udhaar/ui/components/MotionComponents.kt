package com.jyotirmoyb.udhaar.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.ui.theme.AccentFabGreen
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.CardSurfaceWhite
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.util.InterestCalculator
import kotlinx.coroutines.delay

/**
 * Event model for the non-blocking drawn-checkmark success animation.
 */
data class SuccessAnimationEvent(
    val message: String = "",
    val label: String = message,
    val id: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared-bounds modifier connecting a source element (e.g. ledger card or person row)
 * to its destination screen inside [SharedTransitionLayout].
 * Animation duration is 250ms (strictly between 150ms and 350ms) and respects reduced motion.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.udhaarSharedBounds(key: String): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val visScope = LocalAnimatedVisibilityScope.current
    val reducedMotion = rememberReducedMotionEnabled()
    if (sharedScope != null && visScope != null && !reducedMotion) {
        with(sharedScope) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = visScope,
                boundsTransform = { _, _ ->
                    tween(durationMillis = 250, easing = FastOutSlowInEasing)
                }
            )
        }
    } else {
        this
    }
}

/**
 * Shared-element modifier for avatars, titles, and amounts across screens.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.udhaarSharedElement(key: String): Modifier = composed {
    val sharedScope = LocalSharedTransitionScope.current
    val visScope = LocalAnimatedVisibilityScope.current
    val reducedMotion = rememberReducedMotionEnabled()
    if (sharedScope != null && visScope != null && !reducedMotion) {
        with(sharedScope) {
            this@composed.sharedElement(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = visScope,
                boundsTransform = { _, _ ->
                    tween(durationMillis = 250, easing = FastOutSlowInEasing)
                }
            )
        }
    } else {
        this
    }
}

/**
 * Non-blocking press-scale (0.96f) spring + light haptic feedback modifier.
 * Uses a passive pointer pass that never consumes touch events so native Material ripples,
 * button clicks, and list scrolling execute with zero latency and never block taps.
 */
fun Modifier.pressScaleFeedback(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f
): Modifier = composed {
    if (!enabled) return@composed this
    val view = LocalView.current
    val reducedMotion = rememberReducedMotionEnabled()
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScaleSpring"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                isPressed = true
                runCatching {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                isPressed = false
            }
        }
}

/**
 * Combines standard Material clickable (with ripple) + 0.96 spring press scale + light haptic feedback.
 */
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = this
    .pressScaleFeedback(enabled = enabled, pressedScale = pressedScale)
    .clickable(enabled = enabled, onClick = onClick)

/**
 * List item placement, fade-in, and fade-out animation for LazyColumn and LazyRow items.
 * All durations are between 170ms and 220ms and respect reduced motion.
 */
fun LazyItemScope.udhaarAnimateItem(reducedMotion: Boolean = false): Modifier {
    return if (reducedMotion) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
            placementSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            fadeOutSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
        )
    }
}

/**
 * Smooth count-up / count-down currency formatter for ledger and party totals (260ms tween).
 */
@Composable
fun rememberAnimatedCurrencyText(
    targetAmount: Double,
    currency: CurrencyType,
    hideAmount: Boolean = false,
    isAmountsHidden: Boolean = hideAmount,
    reducedMotion: Boolean = false,
    durationMillis: Int = 260
): String {
    val systemReducedMotion = rememberReducedMotionEnabled()
    val effectiveReducedMotion = reducedMotion || systemReducedMotion
    val effectiveDuration = if (effectiveReducedMotion) 0 else durationMillis.coerceIn(150, 350)
    val animatedValue by animateFloatAsState(
        targetValue = targetAmount.toFloat(),
        animationSpec = tween(
            durationMillis = effectiveDuration,
            easing = FastOutSlowInEasing
        ),
        label = "animatedCurrencyTotal"
    )
    val effectiveAmount = if (kotlin.math.abs(animatedValue - targetAmount.toFloat()) < 0.05f) {
        targetAmount
    } else {
        animatedValue.toDouble()
    }
    return InterestCalculator.formatCurrencyPrivacy(effectiveAmount, currency, isAmountsHidden)
}

/**
 * Animated switch with smooth thumb slide, track color cross-fade, press scale, haptic, and ripple.
 */
@Composable
fun UdhaarAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors = SwitchDefaults.colors()
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val duration = if (reducedMotion) 0 else 200
    val trackColor by animateColorAsState(
        targetValue = if (checked) PureBlack else BorderGrey.copy(alpha = 0.65f),
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        label = "switchTrackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) CardSurfaceWhite else PureBlack,
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        label = "switchThumbColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 4.dp,
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        label = "switchThumbOffset"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (checked) 20.dp else 18.dp,
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        label = "switchThumbSize"
    )

    Box(
        modifier = modifier
            .width(48.dp)
            .height(28.dp)
            .clip(UdhaarShapeTokens.Pill)
            .background(trackColor, UdhaarShapeTokens.Pill)
            .border(1.dp, if (checked) PureBlack else BorderGrey, UdhaarShapeTokens.Pill)
            .pressScaleFeedback(enabled = enabled, pressedScale = 0.96f)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor, CircleShape)
        )
    }
}

/**
 * Smooth entrance animation wrapper for bottom sheets and dialogs (220ms, never blocks taps).
 */
@Composable
fun AnimatedSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reducedMotion = rememberReducedMotionEnabled()
    var visible by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val offsetFraction by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 220,
            easing = FastOutSlowInEasing
        ),
        label = "sheetSlideFraction"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 180,
            easing = FastOutSlowInEasing
        ),
        label = "sheetAlpha"
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetFraction * 42f
            this.alpha = alpha
        }
    ) {
        content()
    }
}

/**
 * Short, non-blocking drawn-checkmark success animation (250ms draw duration)
 * shown when a record is saved, a payment is recorded, or a record is verified.
 * Renders at top-center without intercepting touch events so taps are never blocked.
 */
@Composable
fun SuccessCheckmarkOverlay(
    event: SuccessAnimationEvent?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val reducedMotion = rememberReducedMotionEnabled()
    val drawProgress = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }
    var activeLabel by remember { mutableStateOf("") }

    LaunchedEffect(event?.id) {
        if (event != null) {
            activeLabel = event.label
            visible = true
            runCatching {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            drawProgress.snapTo(if (reducedMotion) 1f else 0f)
            if (!reducedMotion) {
                drawProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                )
            }
            delay(520)
            visible = false
            delay(180)
            onFinished()
        } else {
            visible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.88f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + slideInVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 2 },
            exit = fadeOut(tween(160)) + scaleOut(
                targetScale = 0.92f,
                animationSpec = tween(160, easing = FastOutSlowInEasing)
            ) + slideOutVertically(tween(160, easing = FastOutSlowInEasing)) { -it / 2 }
        ) {
            Surface(
                modifier = Modifier
                    .testTag("success_checkmark_banner")
                    .semantics {
                        contentDescription = activeLabel
                    },
                shape = UdhaarShapeTokens.Pill,
                color = PureBlack,
                contentColor = CardSurfaceWhite,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, AccentFabGreen)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AccentFabGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(15.dp)
                                .testTag("success_checkmark_canvas")
                        ) {
                            val w = size.width
                            val h = size.height
                            val fullPath = Path().apply {
                                moveTo(w * 0.16f, h * 0.52f)
                                lineTo(w * 0.42f, h * 0.78f)
                                lineTo(w * 0.85f, h * 0.24f)
                            }
                            val pathMeasure = PathMeasure()
                            pathMeasure.setPath(fullPath, false)
                            val segmentPath = Path()
                            pathMeasure.getSegment(
                                startDistance = 0f,
                                stopDistance = pathMeasure.length * drawProgress.value,
                                destination = segmentPath,
                                startWithMoveTo = true
                            )
                            drawPath(
                                path = segmentPath,
                                color = Color.White,
                                style = Stroke(
                                    width = 2.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    Text(
                        text = activeLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = CardSurfaceWhite
                    )
                }
            }
        }
    }
}
