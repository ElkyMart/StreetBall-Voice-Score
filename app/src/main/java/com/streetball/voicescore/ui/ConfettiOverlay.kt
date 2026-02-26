package com.streetball.voicescore.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streetball.voicescore.model.Team
import kotlin.random.Random

private data class ConfettiParticle(
    val xRatio: Float,
    val speed: Float,
    val radius: Float,
    val offsetRatio: Float,
    val color: Color,
)

@Composable
fun ConfettiOverlay(
    winner: Team,
    scoreA: Int,
    scoreB: Int,
    onTapToReset: () -> Unit,
) {
    val random = remember { Random(42) }
    val particles = remember {
        List(120) {
            ConfettiParticle(
                xRatio = random.nextFloat(),
                speed = 0.6f + random.nextFloat() * 1.3f,
                radius = 3f + random.nextFloat() * 7f,
                offsetRatio = random.nextFloat(),
                color = listOf(
                    Color(0xFFFFC857),
                    Color(0xFFFF7F50),
                    Color(0xFF58F6A9),
                    Color(0xFF72A1FF),
                    Color(0xFFFFFFFF),
                )[it % 5],
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(onClick = onTapToReset),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { particle ->
                val x = size.width * particle.xRatio
                val y = ((progress.value * size.height * particle.speed) + (particle.offsetRatio * size.height)) % size.height
                drawCircle(
                    color = particle.color,
                    radius = particle.radius,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }

        Text(
            text = "FINAL SCORE\n$scoreA - $scoreB\n\nWINNER: TEAM ${if (winner == Team.A) "A" else "B"}\n\nTap to reset",
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize()
                .padding(24.dp),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp,
        )
    }
}
