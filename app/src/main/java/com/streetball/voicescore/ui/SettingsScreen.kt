package com.streetball.voicescore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streetball.voicescore.R

@Composable
fun SettingsScreen(
    targetScore: Int,
    winByTwo: Boolean,
    threePointMode: Boolean,
    loudMode: Boolean,
    onBack: () -> Unit,
    onTargetScoreSelected: (Int) -> Unit,
    onWinByTwoChanged: (Boolean) -> Unit,
    onThreePointModeChanged: (Boolean) -> Unit,
    onLoudModeChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            title = stringResource(R.string.three_point_mode),
            checked = threePointMode,
            onCheckedChange = onThreePointModeChanged,
        )

        ToggleRow(
            title = stringResource(R.string.loud_mode),
            checked = loudMode,
            onCheckedChange = onLoudModeChanged,
        )
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
