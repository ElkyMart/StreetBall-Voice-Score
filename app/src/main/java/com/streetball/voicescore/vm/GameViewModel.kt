package com.streetball.voicescore.vm

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streetball.voicescore.model.GameState
import com.streetball.voicescore.model.ScoreEvent
import com.streetball.voicescore.model.Team
import com.streetball.voicescore.model.UpdateSource
import com.streetball.voicescore.parser.NumberWordParser
import com.streetball.voicescore.util.DeviceFeedback
import com.streetball.voicescore.validation.ScoreValidationEngine
import com.streetball.voicescore.voice.VoiceRecognitionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val parser = NumberWordParser()
    private val validationEngine = ScoreValidationEngine()
    private val maxVoiceDebugLines = 14
    private val teamNameMaxLength = 16
    private val allowedTargetScores = setOf(11, 15, 21)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val voiceManager: VoiceRecognitionManager = VoiceRecognitionManager(
        context = getApplication(),
        scope = viewModelScope,
        onTranscript = ::onVoiceTranscript,
        onListeningChanged = ::onListeningChanged,
        onErrorMessage = ::onVoiceError,
    )

    private var lastAcceptedVoiceScore: Pair<Int, Int>? = null
    private var lastAcceptedVoiceTimeMs: Long = 0L

    private var highlightJob: Job? = null
    private var flashJob: Job? = null
    private var presetStatusJob: Job? = null

    private val prefs = getApplication<Application>()
        .getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var sessionStartTimeMs: Long = System.currentTimeMillis()
    @Volatile
    private var ttsSpeaking: Boolean = false
    @Volatile
    private var suppressVoiceInputUntilMs: Long = 0L
    private var lastRejectedInputKey: String? = null
    private var lastRejectedInputTimeMs: Long = 0L
    private var lastSoftStatusKey: String? = null
    private var lastSoftStatusTimeMs: Long = 0L
    private var lastVoiceEventTimeMs: Long = 0L
    private var lastRejectedSpeechMessage: String? = null
    private var lastRejectedSpeechTimeMs: Long = 0L

    init {
        textToSpeech = TextToSpeech(getApplication()) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) return@TextToSpeech

            textToSpeech?.language = Locale.US
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    ttsSpeaking = true
                    suppressVoiceInputUntilMs = maxOf(
                        suppressVoiceInputUntilMs,
                        SystemClock.elapsedRealtime() + 300L,
                    )
                }

                override fun onDone(utteranceId: String?) {
                    ttsSpeaking = false
                    suppressVoiceInputUntilMs = SystemClock.elapsedRealtime() + 900L
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    ttsSpeaking = false
                    suppressVoiceInputUntilMs = SystemClock.elapsedRealtime() + 900L
                }
            })
        }

        val restoredConfig = readLastConfig()
        _uiState.update { current ->
            var next = current.copy(hasSavedPreset = hasSavedPreset())
            if (restoredConfig == null) return@update next

            val restoredGameState = next.gameState.copy(
                teamAName = restoredConfig.teamAName,
                teamBName = restoredConfig.teamBName,
                targetScore = restoredConfig.targetScore,
                winByTwo = restoredConfig.winByTwo,
            )
            next = next.copy(
                gameState = restoredGameState,
                loudMode = restoredConfig.loudMode,
                keepScreenAwake = restoredConfig.keepScreenAwake,
                videoCaptureMode = restoredConfig.videoCaptureMode,
            )
            next
        }
    }

    fun onMicPermissionChanged(granted: Boolean) {
        _uiState.update {
            it.copy(
                micPermissionGranted = granted,
                permissionDenied = !granted,
                lastError = if (granted) null else "Microphone permission denied",
                voiceLoopStatus = if (granted) {
                    "Ready. Say both scores."
                } else {
                    "Microphone permission denied."
                },
                voiceLoopHint = if (granted) {
                    "Try: one zero"
                } else {
                    "Tap Allow Microphone to continue."
                },
                voiceLoopTone = if (granted) VoiceLoopTone.READY else VoiceLoopTone.ERROR,
            )
        }
        appendVoiceDebugLine(
            if (granted) {
                "mic permission: granted"
            } else {
                "mic permission: denied"
            },
        )
        refreshListeningState()
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun setTargetScore(targetScore: Int) {
        if (targetScore !in allowedTargetScores) return
        applyGameConfigChange { it.copy(targetScore = targetScore) }
    }

    fun setWinByTwo(enabled: Boolean) {
        applyGameConfigChange { it.copy(winByTwo = enabled) }
    }

    fun setLoudMode(enabled: Boolean) {
        updateUiStateAndPersist { it.copy(loudMode = enabled) }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        updateUiStateAndPersist { it.copy(keepScreenAwake = enabled) }
    }

    fun setVideoCaptureMode(enabled: Boolean) {
        updateUiStateAndPersist { it.copy(videoCaptureMode = enabled) }
    }

    fun setTeamAName(name: String) {
        setTeamName(team = Team.A, rawName = name)
    }

    fun setTeamBName(name: String) {
        setTeamName(team = Team.B, rawName = name)
    }

    fun saveCurrentAsPreset() {
        val currentState = _uiState.value
        val game = currentState.gameState
        prefs.edit()
            .putBoolean(KEY_PRESET_SAVED, true)
            .putString(KEY_PRESET_TEAM_A, game.teamAName)
            .putString(KEY_PRESET_TEAM_B, game.teamBName)
            .putInt(KEY_PRESET_TARGET_SCORE, game.targetScore)
            .putBoolean(KEY_PRESET_WIN_BY_TWO, game.winByTwo)
            .putBoolean(KEY_PRESET_LOUD_MODE, currentState.loudMode)
            .putBoolean(KEY_PRESET_KEEP_SCREEN_AWAKE, currentState.keepScreenAwake)
            .putBoolean(KEY_PRESET_VIDEO_CAPTURE_MODE, currentState.videoCaptureMode)
            .apply()

        _uiState.update {
            it.copy(
                hasSavedPreset = true,
            )
        }
        setPresetStatus("Preset saved")
        persistLastConfigSnapshot()
        appendVoiceDebugLine("preset: saved current setup")
    }

    fun applySavedPreset() {
        val preset = readSavedPreset()
        if (preset == null) {
            _uiState.update { it.copy(hasSavedPreset = false) }
            setPresetStatus("No saved preset yet")
            appendVoiceDebugLine("preset: no saved setup")
            return
        }

        val appliedState = GameState(
            teamAScore = 0,
            teamBScore = 0,
            teamAName = preset.teamAName,
            teamBName = preset.teamBName,
            targetScore = preset.targetScore,
            winByTwo = preset.winByTwo,
            gameActive = true,
            lastUpdateSource = UpdateSource.MANUAL,
            history = emptyList(),
        )

        _uiState.update {
            it.copy(
                gameState = appliedState,
                loudMode = preset.loudMode,
                keepScreenAwake = preset.keepScreenAwake,
                videoCaptureMode = preset.videoCaptureMode,
                hasSavedPreset = true,
                winner = null,
                gamePointTeam = null,
                highlightTeam = null,
                invalidFlash = false,
                lastError = null,
                lastHeardText = null,
                lastInterpretedText = null,
                voiceLoopStatus = "Preset loaded. Ready for a new game.",
                voiceLoopHint = "Say both scores clearly.",
                voiceLoopTone = VoiceLoopTone.READY,
            )
        }

        resetVoiceSession()
        setPresetStatus("Preset loaded")
        persistLastConfigSnapshot()
        appendVoiceDebugLine("preset: applied saved setup")
        refreshListeningState()
    }

    fun exportDebugTimelineFiles() {
        val snapshot = _uiState.value.gameState
        val sessionStart = sessionStartTimeMs
        val exportEnd = System.currentTimeMillis()

        viewModelScope.launch {
            val exportMessage = withContext(Dispatchers.IO) {
                runCatching {
                    exportTimelineFiles(
                        history = snapshot.history,
                        sessionStartMs = sessionStart,
                        exportTimeMs = exportEnd,
                        currentScoreA = snapshot.teamAScore,
                        currentScoreB = snapshot.teamBScore,
                        teamAName = snapshot.teamAName,
                        teamBName = snapshot.teamBName,
                    )
                }.fold(
                    onSuccess = { paths ->
                        "Exported:\n${paths.csvPath}\n${paths.srtPath}\n${paths.assPath}\n${paths.notesPath}"
                    },
                    onFailure = { error ->
                        "Export failed: ${error.message ?: "unknown error"}"
                    },
                )
            }

            _uiState.update { current ->
                current.copy(exportDebugMessage = exportMessage)
            }
        }
    }

    fun incrementTeamABy(points: Int) {
        incrementTeam(team = Team.A, points = points)
    }

    fun decrementTeamA() {
        val current = _uiState.value.gameState
        val newScoreA = (current.teamAScore - 1).coerceAtLeast(0)
        applyManualScoreChange(newScoreA = newScoreA, newScoreB = current.teamBScore)
    }

    fun incrementTeamBBy(points: Int) {
        incrementTeam(team = Team.B, points = points)
    }

    fun decrementTeamB() {
        val current = _uiState.value.gameState
        val newScoreB = (current.teamBScore - 1).coerceAtLeast(0)
        applyManualScoreChange(newScoreA = current.teamAScore, newScoreB = newScoreB)
    }

    fun undo() {
        val currentState = _uiState.value.gameState
        val history = currentState.history
        if (history.isEmpty()) return

        val event = history.last()
        val revertedState = currentState.copy(
            teamAScore = event.oldScoreA,
            teamBScore = event.oldScoreB,
            history = history.dropLast(1),
            lastUpdateSource = event.source,
        )

        val (resolvedState, winner, gamePointTeam) = resolveGameMeta(revertedState)

        _uiState.update {
            it.copy(
                gameState = resolvedState,
                winner = winner,
                gamePointTeam = gamePointTeam,
                highlightTeam = null,
                invalidFlash = false,
                lastError = null,
            )
        }

        refreshListeningState()
    }

    fun resetGame() {
        val previous = _uiState.value.gameState
        val resetState = GameState(
            teamAScore = 0,
            teamBScore = 0,
            teamAName = previous.teamAName,
            teamBName = previous.teamBName,
            targetScore = previous.targetScore,
            winByTwo = previous.winByTwo,
            gameActive = true,
            lastUpdateSource = UpdateSource.MANUAL,
            history = emptyList(),
        )

        _uiState.update {
            it.copy(
                gameState = resetState,
                winner = null,
                gamePointTeam = null,
                highlightTeam = null,
                invalidFlash = false,
                lastError = null,
                voiceDebugLines = emptyList(),
                exportDebugMessage = null,
                lastHeardText = null,
                lastInterpretedText = null,
                voiceLoopStatus = "Ready. Say both scores.",
                voiceLoopHint = "Try: one zero",
                voiceLoopTone = VoiceLoopTone.READY,
            )
        }

        resetVoiceSession()
        appendVoiceDebugLine("game reset")
        refreshListeningState()
    }

    private fun applyGameConfigChange(change: (GameState) -> GameState) {
        val current = _uiState.value.gameState
        val changed = change(current)
        val (resolvedState, winner, gamePointTeam) = resolveGameMeta(changed)

        _uiState.update {
            it.copy(
                gameState = resolvedState,
                winner = winner,
                gamePointTeam = gamePointTeam,
            )
        }

        persistLastConfigSnapshot()
        refreshListeningState()
    }

    private fun setTeamName(team: Team, rawName: String) {
        val sanitized = sanitizeTeamName(rawName)
        val current = _uiState.value.gameState
        val alreadySet = if (team == Team.A) {
            current.teamAName == sanitized
        } else {
            current.teamBName == sanitized
        }
        if (alreadySet) return

        applyGameConfigChange { state ->
            if (team == Team.A) {
                state.copy(teamAName = sanitized)
            } else {
                state.copy(teamBName = sanitized)
            }
        }
    }

    private fun incrementTeam(team: Team, points: Int) {
        if (points !in 1..3) return
        val current = _uiState.value.gameState
        val newScoreA = if (team == Team.A) current.teamAScore + points else current.teamAScore
        val newScoreB = if (team == Team.B) current.teamBScore + points else current.teamBScore
        applyManualScoreChange(newScoreA = newScoreA, newScoreB = newScoreB)
    }

    private fun updateUiStateAndPersist(update: (GameUiState) -> GameUiState) {
        _uiState.update(update)
        persistLastConfigSnapshot()
    }

    private fun resetVoiceSession() {
        lastAcceptedVoiceScore = null
        lastAcceptedVoiceTimeMs = 0L
        sessionStartTimeMs = System.currentTimeMillis()
    }

    private fun applyManualScoreChange(newScoreA: Int, newScoreB: Int) {
        val current = _uiState.value.gameState
        if (newScoreA < 0 || newScoreB < 0) return
        if (newScoreA == current.teamAScore && newScoreB == current.teamBScore) return

        val scoringTeam = when {
            newScoreA > current.teamAScore -> Team.A
            newScoreB > current.teamBScore -> Team.B
            else -> null
        }

        commitScoreChange(
            oldState = current,
            newScoreA = newScoreA,
            newScoreB = newScoreB,
            source = UpdateSource.MANUAL,
            scoringTeam = scoringTeam,
        )
    }

    private fun onVoiceTranscript(text: String, confidence: Float) {
        val stateSnapshot = _uiState.value
        val gameState = stateSnapshot.gameState

        appendVoiceDebugLine(
            "heard: \"${truncateForDebug(text)}\" (conf ${formatConfidence(confidence)})",
        )
        _uiState.update { current ->
            current.copy(
                lastHeardText = truncateForDebug(text),
                voiceLoopStatus = "Heard speech",
                voiceLoopHint = "Parsing numbers...",
                voiceLoopTone = VoiceLoopTone.READY,
            )
        }

        if (!stateSnapshot.micPermissionGranted || !gameState.gameActive) return
        if (isVoiceInputSuppressed()) {
            appendVoiceDebugLine("ignored: self-voice suppression")
            updateSoftVoiceLoopStatus(
                status = "Listening...",
                hint = "Hold still and say the full score once.",
                tone = VoiceLoopTone.READY,
                dedupeKey = "self_suppression",
            )
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastVoiceEventTimeMs < 700L) {
            appendVoiceDebugLine("ignored: pacing guard")
            return
        }
        lastVoiceEventTimeMs = now

        val confidenceThreshold = 0.55f
        if (confidence < confidenceThreshold) {
            appendVoiceDebugLine(
                "ignored: low confidence (< ${formatConfidence(confidenceThreshold)})",
            )
            updateSoftVoiceLoopStatus(
                status = "Could not hear clearly",
                hint = "Speak a bit slower and closer to the mic.",
                tone = VoiceLoopTone.WARNING,
                dedupeKey = "low_confidence",
            )
            return
        }

        val parseDebug = parser.parseWithDebug(text)
        val parsedScores = parseDebug.parsedScores
        if (parsedScores == null) {
            appendVoiceDebugLine(
                "parser: ${parseDebug.reason ?: "no parse"}; numbers=${formatNumbersForDebug(parseDebug.detectedNumbers)}",
            )
            when {
                parseDebug.detectedNumbers.isEmpty() -> {
                    updateSoftVoiceLoopStatus(
                        status = "No score numbers heard yet",
                        hint = "Try: one zero",
                        tone = VoiceLoopTone.READY,
                        dedupeKey = "parse_none",
                    )
                }

                parseDebug.detectedNumbers.size < 2 -> {
                    updateSoftVoiceLoopStatus(
                        status = "Waiting for second score",
                        hint = "Include both teams, e.g. one zero.",
                        tone = VoiceLoopTone.READY,
                        dedupeKey = "parse_one_${formatNumbersForDebug(parseDebug.detectedNumbers)}",
                    )
                }

                else -> {
                    val rejectionReason = "Rejected: too many numbers, say only two scores"
                    appendVoiceDebugLine("rejected: $rejectionReason")
                    rejectVoiceUpdate(
                        message = rejectionReason,
                        flash = false,
                        dedupeKey = "parse_too_many_${formatNumbersForDebug(parseDebug.detectedNumbers)}",
                    )
                }
            }
            return
        }

        val (newScoreA, newScoreB) = parsedScores
        val heuristicSuffix = parseDebug.heuristic?.let { " [$it]" } ?: ""
        appendVoiceDebugLine("parser: interpreted as $newScoreA-$newScoreB$heuristicSuffix")
        _uiState.update { current ->
            current.copy(
                lastInterpretedText = "$newScoreA-$newScoreB",
                voiceLoopStatus = "Interpreted as $newScoreA-$newScoreB",
                voiceLoopHint = "Validating game rules...",
                voiceLoopTone = VoiceLoopTone.READY,
            )
        }

        // Noise stability filter: only process real changes.
        if (newScoreA == gameState.teamAScore && newScoreB == gameState.teamBScore) {
            appendVoiceDebugLine("ignored: no score change")
            updateSoftVoiceLoopStatus(
                status = "Same score heard",
                hint = "Say the next score after a basket.",
                tone = VoiceLoopTone.READY,
                dedupeKey = "no_change_${newScoreA}_${newScoreB}",
            )
            return
        }

        val acceptedNow = SystemClock.elapsedRealtime()
        if (lastAcceptedVoiceScore == parsedScores && acceptedNow - lastAcceptedVoiceTimeMs < 3_000L) {
            appendVoiceDebugLine("ignored: duplicate within 3s")
            return
        }

        val validation = validationEngine.validateVoiceTransition(gameState, newScoreA, newScoreB)
        if (!validation.isValid) {
            appendVoiceDebugLine("validation: rejected (${validation.reason ?: "invalid transition"})")
            rejectVoiceUpdate(
                message = "Rejected: ${validation.reason ?: "invalid transition"}",
                flash = false,
                dedupeKey = "validation_${newScoreA}_${newScoreB}_${validation.reason ?: "invalid"}",
            )
            return
        }

        lastAcceptedVoiceScore = parsedScores
        lastAcceptedVoiceTimeMs = acceptedNow
        appendVoiceDebugLine("validation: accepted")
        _uiState.update { current ->
            current.copy(
                voiceLoopStatus = "Score updated to $newScoreA-$newScoreB",
                voiceLoopHint = "Keep calling the next score.",
                voiceLoopTone = VoiceLoopTone.SUCCESS,
                lastError = null,
            )
        }

        commitScoreChange(
            oldState = gameState,
            newScoreA = newScoreA,
            newScoreB = newScoreB,
            source = UpdateSource.VOICE,
            scoringTeam = validation.scoringTeam,
        )
    }

    private fun commitScoreChange(
        oldState: GameState,
        newScoreA: Int,
        newScoreB: Int,
        source: UpdateSource,
        scoringTeam: Team?,
    ) {
        val event = ScoreEvent(
            timestamp = System.currentTimeMillis(),
            oldScoreA = oldState.teamAScore,
            oldScoreB = oldState.teamBScore,
            newScoreA = newScoreA,
            newScoreB = newScoreB,
            source = source,
        )

        val updated = oldState.copy(
            teamAScore = newScoreA,
            teamBScore = newScoreB,
            lastUpdateSource = source,
            history = oldState.history + event,
        )

        val (resolvedState, winner, gamePointTeam) = resolveGameMeta(updated)

        _uiState.update {
            it.copy(
                gameState = resolvedState,
                winner = winner,
                gamePointTeam = gamePointTeam,
                highlightTeam = scoringTeam,
                invalidFlash = false,
                lastError = null,
                voiceLoopStatus = if (source == UpdateSource.MANUAL) {
                    "Score updated manually to $newScoreA-$newScoreB"
                } else {
                    it.voiceLoopStatus
                },
                voiceLoopHint = if (source == UpdateSource.MANUAL) {
                    "Voice can continue from this score."
                } else {
                    it.voiceLoopHint
                },
                voiceLoopTone = if (source == UpdateSource.MANUAL) {
                    VoiceLoopTone.READY
                } else {
                    it.voiceLoopTone
                },
            )
        }

        refreshHighlight(scoringTeam)
        refreshListeningState()

        if (_uiState.value.loudMode) {
            DeviceFeedback.playScoreAcceptedBeep()
            speakAcceptedScore(newScoreA, newScoreB)
        }
    }

    private fun resolveGameMeta(state: GameState): Triple<GameState, Team?, Team?> {
        val winner = validationEngine.winnerFor(
            scoreA = state.teamAScore,
            scoreB = state.teamBScore,
            targetScore = state.targetScore,
            winByTwo = state.winByTwo,
        )

        val resolved = state.copy(gameActive = winner == null)
        val gamePointTeam = validationEngine.gamePointTeam(resolved)

        return Triple(resolved, winner, gamePointTeam)
    }

    private fun refreshHighlight(team: Team?) {
        highlightJob?.cancel()
        if (team == null) return

        highlightJob = viewModelScope.launch {
            delay(700L)
            _uiState.update { current ->
                if (current.highlightTeam == team) {
                    current.copy(highlightTeam = null)
                } else {
                    current
                }
            }
        }
    }

    private fun rejectVoiceUpdate(
        message: String,
        flash: Boolean,
        dedupeKey: String? = null,
    ) {
        if (dedupeKey != null && isDuplicateRejectedInput(dedupeKey)) return
        suppressVoiceInputUntilMs = maxOf(
            suppressVoiceInputUntilMs,
            SystemClock.elapsedRealtime() + 900L,
        )

        _uiState.update { current ->
            current.copy(
                lastError = message,
                voiceLoopStatus = message,
                voiceLoopHint = rejectionHint(message),
                voiceLoopTone = if (flash) VoiceLoopTone.ERROR else VoiceLoopTone.WARNING,
            )
        }

        if (_uiState.value.loudMode) {
            speakRejectedUpdate(message)
        }

        if (!flash) return

        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    invalidFlash = true,
                )
            }
            delay(180L)
            _uiState.update { it.copy(invalidFlash = false) }
        }
    }

    private fun onListeningChanged(listening: Boolean) {
        _uiState.update { state ->
            val active = listening && state.micPermissionGranted && state.gameState.gameActive
            state.copy(
                isListening = active,
                voiceLoopStatus = if (active && state.voiceLoopTone == VoiceLoopTone.READY) {
                    "Listening..."
                } else {
                    state.voiceLoopStatus
                },
                voiceLoopHint = if (active && state.voiceLoopTone == VoiceLoopTone.READY) {
                    "Say both scores clearly."
                } else {
                    state.voiceLoopHint
                },
            )
        }
    }

    private fun onVoiceError(message: String) {
        appendVoiceDebugLine("recognizer: $message")
        // Ignore noisy no-match type errors to avoid spamming the UI.
        if (message == "No speech match" || message == "Speech timeout") return
        _uiState.update {
            it.copy(
                lastError = message,
                voiceLoopStatus = message,
                voiceLoopHint = "Try speaking again.",
                voiceLoopTone = VoiceLoopTone.WARNING,
            )
        }
    }

    private fun refreshListeningState() {
        val snapshot = _uiState.value
        val shouldListen = snapshot.micPermissionGranted && snapshot.gameState.gameActive

        if (shouldListen) {
            voiceManager.start()
        } else {
            voiceManager.stop()
        }
    }

    private fun appendVoiceDebugLine(line: String) {
        _uiState.update { current ->
            current.copy(
                voiceDebugLines = (current.voiceDebugLines + line).takeLast(maxVoiceDebugLines),
            )
        }
    }

    private fun setPresetStatus(message: String?) {
        presetStatusJob?.cancel()
        _uiState.update { current -> current.copy(presetStatusMessage = message) }
        if (message == null) return

        presetStatusJob = viewModelScope.launch {
            delay(2_400L)
            _uiState.update { current ->
                if (current.presetStatusMessage == message) {
                    current.copy(presetStatusMessage = null)
                } else {
                    current
                }
            }
        }
    }

    private fun speakAcceptedScore(scoreA: Int, scoreB: Int) {
        val tts = textToSpeech ?: return
        if (!ttsReady) return
        tts.speak(
            "$scoreA to $scoreB",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "accepted_score_update",
        )
    }

    private fun speakRejectedUpdate(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (lastRejectedSpeechMessage == message && now - lastRejectedSpeechTimeMs < 3_000L) {
            return
        }
        lastRejectedSpeechMessage = message
        lastRejectedSpeechTimeMs = now

        val tts = textToSpeech ?: return
        if (!ttsReady) return
        val spokenMessage = message
            .removePrefix("Rejected:")
            .trim()
            .ifBlank { "Rejected update" }
        tts.speak(
            spokenMessage,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "rejected_score_update",
        )
    }

    private fun isVoiceInputSuppressed(): Boolean {
        val now = SystemClock.elapsedRealtime()
        return ttsSpeaking || now < suppressVoiceInputUntilMs
    }

    private fun isDuplicateRejectedInput(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastRejectedInputKey == key && now - lastRejectedInputTimeMs < 2_500L) {
            appendVoiceDebugLine("ignored: repeated rejection")
            return true
        }
        lastRejectedInputKey = key
        lastRejectedInputTimeMs = now
        return false
    }

    private fun updateSoftVoiceLoopStatus(
        status: String,
        hint: String,
        tone: VoiceLoopTone,
        dedupeKey: String,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSoftStatusTimeMs < 650L) return
        if (lastSoftStatusKey == dedupeKey && now - lastSoftStatusTimeMs < 1_400L) return
        lastSoftStatusKey = dedupeKey
        lastSoftStatusTimeMs = now
        _uiState.update { current ->
            current.copy(
                lastError = null,
                voiceLoopStatus = status,
                voiceLoopHint = hint,
                voiceLoopTone = tone,
            )
        }
    }

    private fun sanitizeTeamName(rawName: String): String {
        return rawName
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(teamNameMaxLength)
    }

    private fun exportTimelineFiles(
        history: List<ScoreEvent>,
        sessionStartMs: Long,
        exportTimeMs: Long,
        currentScoreA: Int,
        currentScoreB: Int,
        teamAName: String,
        teamBName: String,
    ): ExportPaths {
        val documentsRoot = getApplication<Application>()
            .getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: getApplication<Application>().filesDir
        val exportDir = File(documentsRoot, "exports")
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            error("Unable to create export directory: ${exportDir.absolutePath}")
        }

        val filenameStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(exportTimeMs))
        val csvFile = File(exportDir, "score_timeline_$filenameStamp.csv")
        val srtFile = File(exportDir, "score_timeline_$filenameStamp.srt")
        val assFile = File(exportDir, "score_timeline_$filenameStamp.ass")
        val notesFile = File(exportDir, "score_timeline_${filenameStamp}_video_notes.txt")

        val points = buildTimelinePoints(
            history = history,
            sessionStartMs = sessionStartMs,
            currentScoreA = currentScoreA,
            currentScoreB = currentScoreB,
        )

        csvFile.writeText(
            buildCsvTimeline(points = points, sessionStartMs = sessionStartMs),
            Charsets.UTF_8,
        )
        srtFile.writeText(
            buildSrtTimeline(
                points = points,
                sessionStartMs = sessionStartMs,
                exportTimeMs = exportTimeMs,
                teamAName = teamAName,
                teamBName = teamBName,
            ),
            Charsets.UTF_8,
        )
        assFile.writeText(
            buildAssTimeline(
                points = points,
                sessionStartMs = sessionStartMs,
                exportTimeMs = exportTimeMs,
                teamAName = teamAName,
                teamBName = teamBName,
            ),
            Charsets.UTF_8,
        )
        notesFile.writeText(
            buildVideoOverlayNotes(
                csvPath = csvFile.absolutePath,
                srtPath = srtFile.absolutePath,
                assPath = assFile.absolutePath,
                teamAName = teamAName,
                teamBName = teamBName,
            ),
            Charsets.UTF_8,
        )

        return ExportPaths(
            csvPath = csvFile.absolutePath,
            srtPath = srtFile.absolutePath,
            assPath = assFile.absolutePath,
            notesPath = notesFile.absolutePath,
        )
    }

    private fun buildTimelinePoints(
        history: List<ScoreEvent>,
        sessionStartMs: Long,
        currentScoreA: Int,
        currentScoreB: Int,
    ): List<TimelinePoint> {
        if (history.isEmpty()) {
            return listOf(
                TimelinePoint(
                    timestampMs = sessionStartMs,
                    scoreA = currentScoreA,
                    scoreB = currentScoreB,
                    source = "INITIAL",
                ),
            )
        }

        val first = history.first()
        val points = mutableListOf(
            TimelinePoint(
                timestampMs = sessionStartMs,
                scoreA = first.oldScoreA,
                scoreB = first.oldScoreB,
                source = "INITIAL",
            ),
        )

        history.forEach { event ->
            points += TimelinePoint(
                timestampMs = maxOf(event.timestamp, sessionStartMs),
                scoreA = event.newScoreA,
                scoreB = event.newScoreB,
                source = event.source.name,
            )
        }

        return points
    }

    private fun buildCsvTimeline(
        points: List<TimelinePoint>,
        sessionStartMs: Long,
    ): String {
        val wallClock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val rows = mutableListOf("relative_ms,wall_clock,score_a,score_b,source")
        points.forEach { point ->
            val relativeMs = (point.timestampMs - sessionStartMs).coerceAtLeast(0L)
            rows += listOf(
                relativeMs.toString(),
                wallClock.format(Date(point.timestampMs)),
                point.scoreA.toString(),
                point.scoreB.toString(),
                point.source,
            ).joinToString(",")
        }
        return rows.joinToString(separator = "\n", postfix = "\n")
    }

    private fun buildSrtTimeline(
        points: List<TimelinePoint>,
        sessionStartMs: Long,
        exportTimeMs: Long,
        teamAName: String,
        teamBName: String,
    ): String {
        val safeSessionEnd = maxOf(exportTimeMs, sessionStartMs + 1_000L)
        val sessionDurationMs = (safeSessionEnd - sessionStartMs).coerceAtLeast(1_000L)

        return buildString {
            points.forEachIndexed { index, point ->
                val startMs = (point.timestampMs - sessionStartMs).coerceAtLeast(0L)
                val nextStartMs = points.getOrNull(index + 1)
                    ?.let { (it.timestampMs - sessionStartMs).coerceAtLeast(0L) }
                    ?: sessionDurationMs
                val endMs = maxOf(nextStartMs, startMs + 700L)
                append(index + 1)
                append('\n')
                append(formatSrtTime(startMs))
                append(" --> ")
                append(formatSrtTime(endMs))
                append('\n')
                append("$teamAName ${point.scoreA} - ${point.scoreB} $teamBName (${point.source})")
                append("\n\n")
            }
        }
    }

    private fun buildVideoOverlayNotes(
        csvPath: String,
        srtPath: String,
        assPath: String,
        teamAName: String,
        teamBName: String,
    ): String {
        return buildString {
            appendLine("StreetBall Voice Score - Video Overlay Notes")
            appendLine("Teams: $teamAName vs $teamBName")
            appendLine()
            appendLine("Exported timeline files:")
            appendLine("CSV: $csvPath")
            appendLine("SRT: $srtPath")
            appendLine("ASS: $assPath")
            appendLine()
            appendLine("Example ffmpeg burn-in command:")
            appendLine("ffmpeg -i input.mp4 -vf ass=score_timeline_xxx.ass -c:a copy output_with_score.mp4")
            appendLine("Fallback if ASS is unavailable:")
            appendLine("ffmpeg -i input.mp4 -vf subtitles=score_timeline_xxx.srt -c:a copy output_with_score.mp4")
            appendLine()
            appendLine("Tip: Keep your source recording untouched and generate overlays as separate outputs.")
        }
    }

    private fun buildAssTimeline(
        points: List<TimelinePoint>,
        sessionStartMs: Long,
        exportTimeMs: Long,
        teamAName: String,
        teamBName: String,
    ): String {
        val safeSessionEnd = maxOf(exportTimeMs, sessionStartMs + 1_000L)
        val sessionDurationMs = (safeSessionEnd - sessionStartMs).coerceAtLeast(1_000L)
        val safeTeamA = escapeAssText(teamAName)
        val safeTeamB = escapeAssText(teamBName)

        val events = buildString {
            points.forEachIndexed { index, point ->
                val startMs = (point.timestampMs - sessionStartMs).coerceAtLeast(0L)
                val nextStartMs = points.getOrNull(index + 1)
                    ?.let { (it.timestampMs - sessionStartMs).coerceAtLeast(0L) }
                    ?: sessionDurationMs
                val endMs = maxOf(nextStartMs, startMs + 700L)
                append("Dialogue: 0,")
                append(formatAssTime(startMs))
                append(',')
                append(formatAssTime(endMs))
                append(",Score,,0,0,30,,")
                append("$safeTeamA ${point.scoreA} - ${point.scoreB} $safeTeamB")
                append("\\N")
                append("[${escapeAssText(point.source)}]")
                append('\n')
            }
        }

        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: 1920")
            appendLine("PlayResY: 1080")
            appendLine("WrapStyle: 2")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding")
            appendLine("Style: Score,Arial,56,&H00FFFFFF,&H000000FF,&H00111111,&H78000000,1,0,0,0,100,100,0,0,1,3,0,8,40,40,54,1")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text")
            append(events)
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000
        val minutes = (clamped % 3_600_000) / 60_000
        val seconds = (clamped % 60_000) / 1_000
        val millis = clamped % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun formatAssTime(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000
        val minutes = (clamped % 3_600_000) / 60_000
        val seconds = (clamped % 60_000) / 1_000
        val centiseconds = (clamped % 1_000) / 10
        return String.format(Locale.US, "%01d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
    }

    private fun escapeAssText(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")
    }

    private fun rejectionHint(message: String): String {
        return when {
            message.contains("too many numbers", ignoreCase = true) -> "Say only two scores, e.g. two one."
            message.contains("need two scores", ignoreCase = true) -> "Include both teams, e.g. one zero."
            message.contains("both teams cannot score at once", ignoreCase = true) -> "Call the next legal score after each basket."
            message.contains("jump too large", ignoreCase = true) -> "Use the next reachable score, then continue."
            message.contains("no score change", ignoreCase = true) -> "Say the next score, not the current one."
            else -> "Try: one zero"
        }
    }

    private fun truncateForDebug(text: String, maxChars: Int = 72): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "..."
    }

    private fun formatConfidence(confidence: Float): String {
        return String.format(Locale.US, "%.2f", confidence)
    }

    private fun formatNumbersForDebug(values: List<Int>): String {
        return if (values.isEmpty()) "[]" else values.joinToString(prefix = "[", postfix = "]")
    }

    private fun hasSavedPreset(): Boolean {
        return prefs.getBoolean(KEY_PRESET_SAVED, false)
    }

    private fun persistLastConfigSnapshot() {
        val state = _uiState.value
        val game = state.gameState
        prefs.edit()
            .putBoolean(KEY_LAST_CONFIG_SAVED, true)
            .putString(KEY_LAST_TEAM_A, game.teamAName)
            .putString(KEY_LAST_TEAM_B, game.teamBName)
            .putInt(KEY_LAST_TARGET_SCORE, game.targetScore)
            .putBoolean(KEY_LAST_WIN_BY_TWO, game.winByTwo)
            .putBoolean(KEY_LAST_LOUD_MODE, state.loudMode)
            .putBoolean(KEY_LAST_KEEP_SCREEN_AWAKE, state.keepScreenAwake)
            .putBoolean(KEY_LAST_VIDEO_CAPTURE_MODE, state.videoCaptureMode)
            .apply()
    }

    private fun readLastConfig(): SavedPreset? {
        if (!prefs.getBoolean(KEY_LAST_CONFIG_SAVED, false)) return null

        val targetScore = prefs.getInt(KEY_LAST_TARGET_SCORE, 21)
        if (targetScore !in allowedTargetScores) return null

        return SavedPreset(
            teamAName = sanitizeTeamName(prefs.getString(KEY_LAST_TEAM_A, "A").orEmpty()),
            teamBName = sanitizeTeamName(prefs.getString(KEY_LAST_TEAM_B, "B").orEmpty()),
            targetScore = targetScore,
            winByTwo = prefs.getBoolean(KEY_LAST_WIN_BY_TWO, true),
            loudMode = prefs.getBoolean(KEY_LAST_LOUD_MODE, false),
            keepScreenAwake = prefs.getBoolean(KEY_LAST_KEEP_SCREEN_AWAKE, true),
            videoCaptureMode = prefs.getBoolean(KEY_LAST_VIDEO_CAPTURE_MODE, false),
        )
    }

    private fun readSavedPreset(): SavedPreset? {
        if (!hasSavedPreset()) return null

        val targetScore = prefs.getInt(KEY_PRESET_TARGET_SCORE, 21)
        if (targetScore !in allowedTargetScores) return null

        return SavedPreset(
            teamAName = sanitizeTeamName(prefs.getString(KEY_PRESET_TEAM_A, "A").orEmpty()),
            teamBName = sanitizeTeamName(prefs.getString(KEY_PRESET_TEAM_B, "B").orEmpty()),
            targetScore = targetScore,
            winByTwo = prefs.getBoolean(KEY_PRESET_WIN_BY_TWO, true),
            loudMode = prefs.getBoolean(KEY_PRESET_LOUD_MODE, false),
            keepScreenAwake = prefs.getBoolean(KEY_PRESET_KEEP_SCREEN_AWAKE, true),
            videoCaptureMode = prefs.getBoolean(KEY_PRESET_VIDEO_CAPTURE_MODE, false),
        )
    }

    override fun onCleared() {
        super.onCleared()
        presetStatusJob?.cancel()
        voiceManager.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        DeviceFeedback.release()
    }

    private data class TimelinePoint(
        val timestampMs: Long,
        val scoreA: Int,
        val scoreB: Int,
        val source: String,
    )

    private data class ExportPaths(
        val csvPath: String,
        val srtPath: String,
        val assPath: String,
        val notesPath: String,
    )

    private data class SavedPreset(
        val teamAName: String,
        val teamBName: String,
        val targetScore: Int,
        val winByTwo: Boolean,
        val loudMode: Boolean,
        val keepScreenAwake: Boolean,
        val videoCaptureMode: Boolean,
    )

    companion object {
        private const val PREFS_FILE_NAME = "voice_score_prefs"
        private const val KEY_PRESET_SAVED = "preset.saved"
        private const val KEY_PRESET_TEAM_A = "preset.team_a"
        private const val KEY_PRESET_TEAM_B = "preset.team_b"
        private const val KEY_PRESET_TARGET_SCORE = "preset.target_score"
        private const val KEY_PRESET_WIN_BY_TWO = "preset.win_by_two"
        private const val KEY_PRESET_LOUD_MODE = "preset.loud_mode"
        private const val KEY_PRESET_KEEP_SCREEN_AWAKE = "preset.keep_screen_awake"
        private const val KEY_PRESET_VIDEO_CAPTURE_MODE = "preset.video_capture_mode"
        private const val KEY_LAST_CONFIG_SAVED = "last.saved"
        private const val KEY_LAST_TEAM_A = "last.team_a"
        private const val KEY_LAST_TEAM_B = "last.team_b"
        private const val KEY_LAST_TARGET_SCORE = "last.target_score"
        private const val KEY_LAST_WIN_BY_TWO = "last.win_by_two"
        private const val KEY_LAST_LOUD_MODE = "last.loud_mode"
        private const val KEY_LAST_KEEP_SCREEN_AWAKE = "last.keep_screen_awake"
        private const val KEY_LAST_VIDEO_CAPTURE_MODE = "last.video_capture_mode"
    }
}
