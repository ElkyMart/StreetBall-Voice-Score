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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    targetScore: Int,
    winByTwo: Boolean,
    threePointMode: Boolean,
    loudMode: Boolean,
    ttsAvailable: Boolean,
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
                Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(40.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Target Score",
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
            title = "Win by 2",
            checked = winByTwo,
            onCheckedChange = onWinByTwoChanged,
        )

        ToggleRow(
            title = "3-point mode",
            checked = threePointMode,
            onCheckedChange = onThreePointModeChanged,
        )

        ToggleRow(
            title = "Loud mode",
            checked = loudMode,
            onCheckedChange = onLoudModeChanged,
        )

        if (loudMode && !ttsAvailable) {
            Text(
                text = "Loud mode needs Text-to-Speech data on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
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
