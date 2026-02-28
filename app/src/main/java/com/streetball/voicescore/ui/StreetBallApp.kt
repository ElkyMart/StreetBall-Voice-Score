package com.streetball.voicescore.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streetball.voicescore.ui.theme.StreetBallVoiceScoreTheme
import com.streetball.voicescore.vm.GameViewModel

@Composable
fun StreetBallApp(gameViewModel: GameViewModel = viewModel()) {
    val context = LocalContext.current
    val rootView = LocalView.current
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

    DisposableEffect(rootView, uiState.keepScreenAwake) {
        rootView.keepScreenOn = uiState.keepScreenAwake
        onDispose {
            rootView.keepScreenOn = false
        }
    }

    StreetBallVoiceScoreTheme {
        if (uiState.showSettings) {
            SettingsScreen(
                targetScore = uiState.gameState.targetScore,
                teamAName = uiState.gameState.teamAName,
                teamBName = uiState.gameState.teamBName,
                winByTwo = uiState.gameState.winByTwo,
                loudMode = uiState.loudMode,
                keepScreenAwake = uiState.keepScreenAwake,
                videoCaptureMode = uiState.videoCaptureMode,
                showVoiceDebug = uiState.showVoiceDebug,
                hasSavedPreset = uiState.hasSavedPreset,
                presetStatusMessage = uiState.presetStatusMessage,
                exportDebugMessage = uiState.exportDebugMessage,
                onBack = gameViewModel::closeSettings,
                onTeamANameChanged = gameViewModel::setTeamAName,
                onTeamBNameChanged = gameViewModel::setTeamBName,
                onTargetScoreSelected = gameViewModel::setTargetScore,
                onWinByTwoChanged = gameViewModel::setWinByTwo,
                onLoudModeChanged = gameViewModel::setLoudMode,
                onKeepScreenAwakeChanged = gameViewModel::setKeepScreenAwake,
                onVideoCaptureModeChanged = gameViewModel::setVideoCaptureMode,
                onShowVoiceDebugChanged = gameViewModel::setShowVoiceDebug,
                onSavePreset = gameViewModel::saveCurrentAsPreset,
                onLoadPreset = gameViewModel::applySavedPreset,
                onExportDebugTimeline = gameViewModel::exportDebugTimelineFiles,
            )
        } else {
            GameScreen(
                state = uiState,
                onTeamAAdd = gameViewModel::incrementTeamABy,
                onTeamAMinus = gameViewModel::decrementTeamA,
                onTeamBAdd = gameViewModel::incrementTeamBBy,
                onTeamBMinus = gameViewModel::decrementTeamB,
                onUndo = gameViewModel::undo,
                onReset = gameViewModel::resetGame,
                onApplyPreset = gameViewModel::applySavedPreset,
                isVideoCaptureMode = uiState.videoCaptureMode,
                showVoiceDebug = uiState.showVoiceDebug,
                onToggleVoiceDebug = gameViewModel::setShowVoiceDebug,
                onOpenSettings = gameViewModel::openSettings,
                onRequestMicPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        }
    }
}
