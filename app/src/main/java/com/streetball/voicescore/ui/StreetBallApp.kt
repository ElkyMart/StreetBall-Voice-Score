package com.streetball.voicescore.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streetball.voicescore.ui.theme.StreetBallVoiceScoreTheme
import com.streetball.voicescore.vm.GameViewModel

@Composable
fun StreetBallApp(gameViewModel: GameViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        gameViewModel.onMicPermissionChanged(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        gameViewModel.onMicPermissionChanged(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    StreetBallVoiceScoreTheme {
        if (uiState.showSettings) {
            SettingsScreen(
                targetScore = uiState.gameState.targetScore,
                winByTwo = uiState.gameState.winByTwo,
                threePointMode = uiState.gameState.threePointMode,
                loudMode = uiState.loudMode,
                onBack = gameViewModel::closeSettings,
                onTargetScoreSelected = gameViewModel::setTargetScore,
                onWinByTwoChanged = gameViewModel::setWinByTwo,
                onThreePointModeChanged = gameViewModel::setThreePointMode,
                onLoudModeChanged = gameViewModel::setLoudMode,
            )
        } else {
            GameScreen(
                state = uiState,
                onTeamAPlus = gameViewModel::incrementTeamA,
                onTeamAMinus = gameViewModel::decrementTeamA,
                onTeamBPlus = gameViewModel::incrementTeamB,
                onTeamBMinus = gameViewModel::decrementTeamB,
                onUndo = gameViewModel::undo,
                onReset = gameViewModel::resetGame,
                onOpenSettings = gameViewModel::openSettings,
                onRequestMicPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        }
    }
}
