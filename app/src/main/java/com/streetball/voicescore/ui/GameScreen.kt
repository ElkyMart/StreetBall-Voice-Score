package com.streetball.voicescore.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streetball.voicescore.model.Team
import com.streetball.voicescore.ui.theme.AppBackground
import com.streetball.voicescore.ui.theme.AppSurface
import com.streetball.voicescore.ui.theme.ScoreHighlight
import com.streetball.voicescore.ui.theme.SubtleText
import com.streetball.voicescore.ui.theme.WarningRed
import com.streetball.voicescore.vm.GameUiState
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    state: GameUiState,
    onTeamAPlus: () -> Unit,
    onTeamAMinus: () -> Unit,
    onTeamBPlus: () -> Unit,
    onTeamBMinus: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestMicPermission: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                    )
                }
            }

            if (state.gamePointTeam != null && state.winner == null) {
                val teamLabel = if (state.gamePointTeam == Team.A) "TEAM A" else "TEAM B"
                Text(
                    text = "GAME POINT · $teamLabel",
                    color = SubtleText,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            ScoreBoard(
                scoreA = state.gameState.teamAScore,
                scoreB = state.gameState.teamBScore,
                highlightTeam = state.highlightTeam,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamControls(
                    teamLabel = "A",
                    onPlus = onTeamAPlus,
                    onMinus = onTeamAMinus,
                )

                TeamControls(
                    teamLabel = "B",
                    onPlus = onTeamBPlus,
                    onMinus = onTeamBMinus,
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            ListeningIndicator(isListening = state.isListening)

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onUndo) {
                    Text(text = "Undo")
                }
                HoldToResetButton(onReset = onReset)
            }

            if (state.lastError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.lastError,
                    color = SubtleText,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (!state.micPermissionGranted && state.permissionDenied) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f),
                color = AppSurface,
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Microphone permission is required for voice scoring.",
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                    Button(onClick = onRequestMicPermission) {
                        Text(text = "Allow Microphone")
                    }
                }
            }
        }

        if (state.invalidFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WarningRed),
            )
        }

        if (state.winner != null) {
            ConfettiOverlay(
                winner = state.winner,
                scoreA = state.gameState.teamAScore,
                scoreB = state.gameState.teamBScore,
                onTapToReset = onReset,
            )
        }
    }
}

@Composable
private fun ScoreBoard(
    scoreA: Int,
    scoreB: Int,
    highlightTeam: Team?,
) {
    AnimatedContent(
        targetState = scoreA to scoreB,
        transitionSpec = {
            fadeIn(animationSpec = tween(150)) togetherWith
                fadeOut(animationSpec = tween(150))
        },
        label = "scoreAnimatedContent",
    ) { scores ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = scores.first.toString(),
                color = if (highlightTeam == Team.A) ScoreHighlight else Color.White,
                fontSize = 88.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "  -  ",
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = scores.second.toString(),
                color = if (highlightTeam == Team.B) ScoreHighlight else Color.White,
                fontSize = 88.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun TeamControls(
    teamLabel: String,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Team $teamLabel",
            color = SubtleText,
            style = MaterialTheme.typography.labelLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlus,
                colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
                modifier = Modifier.size(58.dp),
            ) {
                Text(text = "+", fontSize = 24.sp)
            }

            Button(
                onClick = onMinus,
                colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
                modifier = Modifier.size(58.dp),
            ) {
                Text(text = "-", fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun ListeningIndicator(isListening: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
            contentDescription = "Mic State",
            tint = if (isListening) ScoreHighlight else SubtleText,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isListening) "Listening..." else "Mic paused",
            color = SubtleText,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun HoldToResetButton(onReset: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(2_000L)
            if (pressed) {
                onReset()
                pressed = false
            }
        }
    }

    Surface(
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        },
        color = AppSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = if (pressed) "Hold..." else "Reset",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = Color.White,
        )
    }
}
