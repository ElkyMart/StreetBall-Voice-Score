package com.streetball.voicescore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streetball.voicescore.R

@Composable
fun SettingsScreen(
    targetScore: Int,
    teamAName: String,
    teamBName: String,
    winByTwo: Boolean,
    loudMode: Boolean,
    keepScreenAwake: Boolean,
    videoCaptureMode: Boolean,
    hasSavedPreset: Boolean,
    presetStatusMessage: String?,
    exportDebugMessage: String?,
    onBack: () -> Unit,
    onTeamANameChanged: (String) -> Unit,
    onTeamBNameChanged: (String) -> Unit,
    onTargetScoreSelected: (Int) -> Unit,
    onWinByTwoChanged: (Boolean) -> Unit,
    onLoudModeChanged: (Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onVideoCaptureModeChanged: (Boolean) -> Unit,
    onSavePreset: () -> Unit,
    onLoadPreset: () -> Unit,
    onExportDebugTimeline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(40.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.team_names),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = teamAName,
                onValueChange = onTeamANameChanged,
                label = { Text(text = stringResource(R.string.team_a_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = teamBName,
                onValueChange = onTeamBNameChanged,
                label = { Text(text = stringResource(R.string.team_b_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.target_score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(11, 15, 21).forEach { target ->
                    FilterChip(
                        selected = targetScore == target,
                        onClick = { onTargetScoreSelected(target) },
                        label = { Text(text = target.toString()) },
                    )
                }
            }
        }

        ToggleRow(
            title = stringResource(R.string.win_by_two),
            checked = winByTwo,
            onCheckedChange = onWinByTwoChanged,
        )

        ToggleRow(
            title = stringResource(R.string.loud_mode),
            checked = loudMode,
            onCheckedChange = onLoudModeChanged,
        )

        ToggleRow(
            title = stringResource(R.string.keep_screen_awake),
            checked = keepScreenAwake,
            onCheckedChange = onKeepScreenAwakeChanged,
        )

        ToggleRow(
            title = stringResource(R.string.video_capture_mode),
            checked = videoCaptureMode,
            onCheckedChange = onVideoCaptureModeChanged,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.match_preset),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSavePreset,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.save_preset))
                }
                OutlinedButton(
                    onClick = onLoadPreset,
                    enabled = hasSavedPreset,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.load_preset))
                }
            }
            if (!presetStatusMessage.isNullOrBlank()) {
                Text(
                    text = presetStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExportDebugTimeline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.export_debug_timeline))
            }
            Text(
                text = stringResource(R.string.export_debug_timeline_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!exportDebugMessage.isNullOrBlank()) {
                Text(
                    text = exportDebugMessage,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
