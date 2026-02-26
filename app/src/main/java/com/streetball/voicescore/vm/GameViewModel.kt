package com.streetball.voicescore.vm

import android.app.Application
import android.os.SystemClock
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.launch
import java.util.Locale

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val parser = NumberWordParser()
    private val validationEngine = ScoreValidationEngine()

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

    private var ttsReady: Boolean = false
    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech = TextToSpeech(getApplication()) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale.US
            }
        }
    }

    fun onMicPermissionChanged(granted: Boolean) {
        _uiState.update {
            it.copy(
                micPermissionGranted = granted,
                permissionDenied = !granted,
                lastError = if (granted) null else "Microphone permission denied",
            )
        }
        refreshListeningState()
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun setTargetScore(targetScore: Int) {
        if (targetScore !in setOf(11, 15, 21)) return
        applyGameConfigChange { it.copy(targetScore = targetScore) }
    }

    fun setWinByTwo(enabled: Boolean) {
        applyGameConfigChange { it.copy(winByTwo = enabled) }
    }

    fun setThreePointMode(enabled: Boolean) {
        applyGameConfigChange { it.copy(threePointMode = enabled) }
    }

    fun setLoudMode(enabled: Boolean) {
        _uiState.update { it.copy(loudMode = enabled) }
    }

    fun incrementTeamA() {
        applyManualScoreChange(newScoreA = _uiState.value.gameState.teamAScore + 1, newScoreB = _uiState.value.gameState.teamBScore)
    }

    fun decrementTeamA() {
        val current = _uiState.value.gameState
        val newScoreA = (current.teamAScore - 1).coerceAtLeast(0)
        applyManualScoreChange(newScoreA = newScoreA, newScoreB = current.teamBScore)
    }

    fun incrementTeamB() {
        applyManualScoreChange(newScoreA = _uiState.value.gameState.teamAScore, newScoreB = _uiState.value.gameState.teamBScore + 1)
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
            targetScore = previous.targetScore,
            winByTwo = previous.winByTwo,
            threePointMode = previous.threePointMode,
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
            )
        }

        lastAcceptedVoiceScore = null
        lastAcceptedVoiceTimeMs = 0L
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

        refreshListeningState()
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

        if (!stateSnapshot.micPermissionGranted || !gameState.gameActive) return

        val confidenceThreshold = 0.55f
        if (confidence < confidenceThreshold) return

        val parsedScores = parser.extractTwoScores(text) ?: return
        val (newScoreA, newScoreB) = parsedScores

        // Noise stability filter: only process real changes.
        if (newScoreA == gameState.teamAScore && newScoreB == gameState.teamBScore) return

        val now = SystemClock.elapsedRealtime()
        if (lastAcceptedVoiceScore == parsedScores && now - lastAcceptedVoiceTimeMs < 3_000L) {
            return
        }

        val validation = validationEngine.validateVoiceTransition(gameState, newScoreA, newScoreB)
        if (!validation.isValid) {
            triggerInvalidFeedback(validation.reason)
            return
        }

        lastAcceptedVoiceScore = parsedScores
        lastAcceptedVoiceTimeMs = now

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
            )
        }

        refreshHighlight(scoringTeam)
        refreshListeningState()

        if (_uiState.value.loudMode) {
            speakScore(newScoreA, newScoreB)
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

    private fun triggerInvalidFeedback(reason: String?) {
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            DeviceFeedback.vibrateShort(getApplication())
            _uiState.update {
                it.copy(
                    invalidFlash = true,
                    lastError = reason,
                )
            }
            delay(180L)
            _uiState.update { it.copy(invalidFlash = false) }
        }
    }

    private fun onListeningChanged(listening: Boolean) {
        _uiState.update { state ->
            state.copy(
                isListening = listening && state.micPermissionGranted && state.gameState.gameActive,
            )
        }
    }

    private fun onVoiceError(message: String) {
        // Ignore noisy no-match type errors to avoid spamming the UI.
        if (message == "No speech match" || message == "Speech timeout") return
        _uiState.update { it.copy(lastError = message) }
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

    private fun speakScore(scoreA: Int, scoreB: Int) {
        val tts = textToSpeech ?: return
        if (!ttsReady) return
        tts.speak(
            "${scoreToSpeechWord(scoreA)} to ${scoreToSpeechWord(scoreB)}.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "score_update",
        )
    }

    private fun scoreToSpeechWord(score: Int): String {
        return when (score) {
            0 -> "zero"
            1 -> "one"
            2 -> "two"
            3 -> "three"
            4 -> "four"
            5 -> "five"
            6 -> "six"
            7 -> "seven"
            8 -> "eight"
            9 -> "nine"
            10 -> "ten"
            11 -> "eleven"
            12 -> "twelve"
            13 -> "thirteen"
            14 -> "fourteen"
            15 -> "fifteen"
            16 -> "sixteen"
            17 -> "seventeen"
            18 -> "eighteen"
            19 -> "nineteen"
            20 -> "twenty"
            21 -> "twenty one"
            else -> score.toString()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
