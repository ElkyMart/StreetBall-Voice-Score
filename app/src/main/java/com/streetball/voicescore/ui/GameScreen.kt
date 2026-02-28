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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streetball.voicescore.R
import com.streetball.voicescore.model.Team
import com.streetball.voicescore.ui.theme.AppBackground
import com.streetball.voicescore.ui.theme.AppSurface
import com.streetball.voicescore.ui.theme.ScoreHighlight
import com.streetball.voicescore.ui.theme.SubtleText
import com.streetball.voicescore.ui.theme.WarningRed
import com.streetball.voicescore.vm.GameUiState
import com.streetball.voicescore.vm.VoiceLoopTone
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    state: GameUiState,
    onTeamAAdd: (Int) -> Unit,
    onTeamAMinus: () -> Unit,
    onTeamBAdd: (Int) -> Unit,
    onTeamBMinus: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onApplyPreset: () -> Unit,
    isVideoCaptureMode: Boolean,
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
                        contentDescription = stringResource(R.string.settings_icon),
                        tint = Color.White,
                    )
                }
            }

            if (state.gamePointTeam != null && state.winner == null) {
                val teamLabel = if (state.gamePointTeam == Team.A) {
                    state.gameState.teamAName.ifBlank { stringResource(R.string.team_letter_a) }
                } else {
                    state.gameState.teamBName.ifBlank { stringResource(R.string.team_letter_b) }
                }
                Text(
                    text = stringResource(R.string.game_point_team, teamLabel),
                    color = SubtleText,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (isVideoCaptureMode) {
                Text(
                    text = stringResource(R.string.video_capture_mode_active),
                    color = ScoreHighlight,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            ScoreBoard(
                scoreA = state.gameState.teamAScore,
                scoreB = state.gameState.teamBScore,
                teamAName = state.gameState.teamAName.ifBlank { stringResource(R.string.team_letter_a) },
                teamBName = state.gameState.teamBName.ifBlank { stringResource(R.string.team_letter_b) },
                highlightTeam = state.highlightTeam,
                isVideoCaptureMode = isVideoCaptureMode,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamControls(
                    teamName = state.gameState.teamAName.ifBlank { stringResource(R.string.team_letter_a) },
                    onAdd = onTeamAAdd,
                    onMinus = onTeamAMinus,
                )

                TeamControls(
                    teamName = state.gameState.teamBName.ifBlank { stringResource(R.string.team_letter_b) },
                    onAdd = onTeamBAdd,
                    onMinus = onTeamBMinus,
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            VoiceLoopCard(
                isListening = state.isListening,
                lastHeardText = state.lastHeardText,
                lastInterpretedText = state.lastInterpretedText,
                status = state.voiceLoopStatus,
                hint = state.voiceLoopHint,
                tone = state.voiceLoopTone,
                showDetails = !isVideoCaptureMode,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onUndo) {
                    Text(text = stringResource(R.string.undo))
                }
                if (state.hasSavedPreset) {
                    OutlinedButton(onClick = onApplyPreset) {
                        Text(text = stringResource(R.string.load_preset))
                    }
                }
                HoldToResetButton(onReset = onReset)
            }

            if (!state.presetStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.presetStatusMessage,
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
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
                        text = stringResource(R.string.mic_permission_required),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                    Button(onClick = onRequestMicPermission) {
                        Text(text = stringResource(R.string.allow_microphone))
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

        if (!isVideoCaptureMode) {
            VoiceDebugBox(
                lines = state.voiceDebugLines,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }

        if (state.winner != null) {
            ConfettiOverlay(
                winner = state.winner,
                teamAName = state.gameState.teamAName.ifBlank { stringResource(R.string.team_letter_a) },
                teamBName = state.gameState.teamBName.ifBlank { stringResource(R.string.team_letter_b) },
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
    teamAName: String,
    teamBName: String,
    highlightTeam: Team?,
    isVideoCaptureMode: Boolean,
) {
    val scoreFontSize = if (isVideoCaptureMode) 108.sp else 88.sp
    val separatorFontSize = if (isVideoCaptureMode) 84.sp else 72.sp

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = teamAName,
                    color = SubtleText,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = scores.first.toString(),
                    modifier = Modifier.semantics {
                        contentDescription = "$teamAName ${scores.first}"
                    },
                    color = if (highlightTeam == Team.A) ScoreHighlight else Color.White,
                    fontSize = scoreFontSize,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                text = "  -  ",
                color = Color.White,
                fontSize = separatorFontSize,
                fontWeight = FontWeight.Light,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = teamBName,
                    color = SubtleText,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = scores.second.toString(),
                    modifier = Modifier.semantics {
                        contentDescription = "$teamBName ${scores.second}"
                    },
                    color = if (highlightTeam == Team.B) ScoreHighlight else Color.White,
                    fontSize = scoreFontSize,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun TeamControls(
    teamName: String,
    onAdd: (Int) -> Unit,
    onMinus: () -> Unit,
) {
    val minusDescription = stringResource(
        R.string.remove_points_team,
        1,
        teamName,
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.team_label, teamName),
            color = SubtleText,
            style = MaterialTheme.typography.labelLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddScoreButton(points = 1, teamName = teamName, onAdd = onAdd)
            AddScoreButton(points = 2, teamName = teamName, onAdd = onAdd)
            AddScoreButton(points = 3, teamName = teamName, onAdd = onAdd)
        }

        Button(
            onClick = onMinus,
            colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics {
                    contentDescription = minusDescription
                },
        ) {
            Text(text = "-1")
        }
    }
}

@Composable
private fun AddScoreButton(
    points: Int,
    teamName: String,
    onAdd: (Int) -> Unit,
) {
    val addDescription = stringResource(
        R.string.add_points_team,
        points,
        teamName,
    )

    Button(
        onClick = { onAdd(points) },
        colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
        modifier = Modifier
            .size(width = 56.dp, height = 44.dp)
            .semantics {
                contentDescription = addDescription
            },
    ) {
        Text(text = "+$points")
    }
}

@Composable
private fun VoiceLoopCard(
    isListening: Boolean,
    lastHeardText: String?,
    lastInterpretedText: String?,
    status: String,
    hint: String,
    tone: VoiceLoopTone,
    showDetails: Boolean,
) {
    val statusColor = when (tone) {
        VoiceLoopTone.READY -> SubtleText
        VoiceLoopTone.SUCCESS -> ScoreHighlight
        VoiceLoopTone.WARNING -> Color(0xFFFFC857)
        VoiceLoopTone.ERROR -> Color(0xFFFF7F7F)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = stringResource(R.string.mic_state),
                    tint = if (isListening) ScoreHighlight else SubtleText,
                )
                Text(
                    text = if (isListening) stringResource(R.string.listening) else stringResource(R.string.mic_paused),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            if (showDetails) {
                VoiceDetailRow(
                    label = stringResource(R.string.voice_heard_label),
                    value = lastHeardText ?: stringResource(R.string.voice_none_value),
                )
                VoiceDetailRow(
                    label = stringResource(R.string.voice_interpreted_label),
                    value = lastInterpretedText ?: stringResource(R.string.voice_none_value),
                )
            }

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = SubtleText,
            )
        }
    }
}

@Composable
private fun VoiceDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SubtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}

@Composable
private fun VoiceDebugBox(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier,
        color = AppSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_debug_title),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.voice_debug_empty),
                    color = SubtleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                lines.takeLast(10).forEach { line ->
                    Text(
                        text = line,
                        color = SubtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldToResetButton(onReset: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val holdResetDescription = stringResource(R.string.hold_reset_a11y)

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
        modifier = Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .semantics {
                contentDescription = holdResetDescription
            },
        color = AppSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = if (pressed) stringResource(R.string.hold) else stringResource(R.string.reset),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = Color.White,
        )
    }
}
