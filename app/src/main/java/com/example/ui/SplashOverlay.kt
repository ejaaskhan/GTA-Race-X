package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonGlow
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashOverlay(
    onFinished: () -> Unit,
    autoFinishDelayMs: Long = 1750L
) {
    val logoScale = remember { Animatable(0.86f) }
    val logoAlpha = remember { Animatable(0.05f) }
    val taglineAlpha = remember { Animatable(0.05f) }
    val sweepProgress = remember { Animatable(0f) }
    val loadProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(480, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, animationSpec = tween(620, easing = FastOutSlowInEasing))
        }
        launch {
            taglineAlpha.animateTo(
                1f,
                animationSpec = tween(durationMillis = 420, delayMillis = 220, easing = FastOutSlowInEasing)
            )
        }
        launch {
            sweepProgress.animateTo(1f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            val barDuration = if (autoFinishDelayMs > 0L) autoFinishDelayMs.toInt().coerceAtLeast(300) else 900
            loadProgress.animateTo(1f, animationSpec = tween(barDuration, easing = LinearEasing))
        }
        if (autoFinishDelayMs > 0L) {
            delay(autoFinishDelayMs)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF170B1E),
                        CarbonDark,
                        ObsidianBlack
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Fullscreen tap-to-launch layer behind splash typography
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onFinished
                )
                .testTag("splash_screen")
        )

        // Animated neon speedlines & horizon grid glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.62f
            val sweepX = w * sweepProgress.value

            // Subtle perspective speed streaks
            for (i in -3..3) {
                val spread = i * w * 0.14f
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            NitroCyan.copy(alpha = 0.20f),
                            RacingCrimson.copy(alpha = 0.32f)
                        ),
                        start = Offset(w * 0.5f, horizonY),
                        end = Offset(w * 0.5f + spread * 1.9f, h)
                    ),
                    start = Offset(w * 0.5f, horizonY),
                    end = Offset(w * 0.5f + spread * 1.9f, h),
                    strokeWidth = 2.5f
                )
            }

            // Sweeping horizontal neon ignition line across center
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NitroCyan.copy(alpha = 0.85f),
                        RacingCrimson,
                        Color.Transparent
                    ),
                    startX = (sweepX - w * 0.35f).coerceAtLeast(0f),
                    endX = (sweepX + w * 0.25f).coerceAtMost(w)
                ),
                start = Offset(0f, horizonY),
                end = Offset(w, horizonY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .testTag("splash_title")
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
            ) {
                Text(
                    text = "GTA RACE ",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "X",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = RacingCrimson
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Crimson & cyan accent bar
            Box(
                modifier = Modifier
                    .width((240f * sweepProgress.value.coerceAtLeast(0.15f)).dp)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(listOf(NitroCyan, RacingCrimson, CrimsonGlow)),
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = NitroCyan,
                modifier = Modifier
                    .testTag("splash_tagline")
                    .graphicsLayer { alpha = taglineAlpha.value }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = taglineAlpha.value }
                    .background(
                        color = GraphiteSurface.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(NitroCyan.copy(alpha = 0.7f), RacingCrimson.copy(alpha = 0.85f))
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Text(
                    text = stringResource(R.string.splash_creator_credit),
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary,
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .testTag("splash_creator_credit")
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(290.dp)
            ) {
                LinearProgressIndicator(
                    progress = { loadProgress.value },
                    color = RacingCrimson,
                    trackColor = GraphiteSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.splash_loading_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.splash_skip_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonGlow
                    )
                }
            }
        }
    }
}
