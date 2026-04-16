package com.pearcontrol.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MainUiState(
    val serverUrl: String = "http://192.168.1.100:3000",
    val deviceId: String = "",
    val isAuthenticated: Boolean = false,
    val isBusy: Boolean = false,
    val status: String = "Enter server URL and auth ID.",
    val error: String? = null,
    val song: SongResponse? = null,
    val volumeState: VolumeStateResponse? = null,
    val likeState: String? = null,
    val repeatMode: String? = null,
    val shuffleEnabled: Boolean? = null,
    val fullscreenEnabled: Boolean? = null,
    val seekSecondsInput: String = "10",
    val repeatIterationInput: String = "1",
    val volumeSlider: Float = 50f
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var repository: PearRepository? = null
    private var autoRefreshJob: Job? = null
    private var volumeUnitInterval: Boolean? = null
    private var consecutiveSuspiciousZeroVolumeReads: Int = 0

    fun updateServerUrl(value: String) {
        _uiState.update { it.copy(serverUrl = value, error = null) }
    }

    fun updateDeviceId(value: String) {
        _uiState.update { it.copy(deviceId = value, error = null) }
    }

    fun updateSeekSeconds(value: String) {
        _uiState.update { it.copy(seekSecondsInput = value, error = null) }
    }

    fun updateRepeatIteration(value: String) {
        _uiState.update { it.copy(repeatIterationInput = value, error = null) }
    }

    fun updateVolumeSlider(value: Float) {
        _uiState.update { it.copy(volumeSlider = value) }
    }

    fun authenticate() {
        val current = _uiState.value
        val normalizedUrl = normalizeBaseUrl(current.serverUrl)
        if (normalizedUrl == null) {
            _uiState.update { it.copy(error = "Invalid server URL") }
            return
        }
        if (current.deviceId.isBlank()) {
            _uiState.update { it.copy(error = "Auth ID is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null, status = "Authenticating...") }
            val repo = PearRepository(normalizedUrl)
            val authResult = repo.authenticate(current.deviceId.trim())
            if (authResult.isSuccess) {
                repo.setToken(authResult.getOrThrow())
                repository = repo
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        isBusy = false,
                        status = "Connected",
                        error = null
                    )
                }
                refreshState(silent = true)
                startAutoRefresh()
            } else {
                _uiState.update {
                    it.copy(
                        isAuthenticated = false,
                        isBusy = false,
                        status = "Not connected",
                        error = authResult.exceptionOrNull()?.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun refreshState(silent: Boolean = false) {
        val repo = repository ?: run {
            _uiState.update { it.copy(error = "Please connect first") }
            return
        }
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isBusy = true, error = null, status = "Refreshing state...") }
            }

            val songResult = repo.fetchSong()
            val volumeResult = repo.fetchVolume()
            val likeResult = repo.fetchLikeState()
            val repeatResult = repo.fetchRepeatMode()
            val shuffleResult = repo.fetchShuffleState()
            val fullscreenResult = repo.fetchFullscreenState()

            val song = songResult.getOrNull()
            val volume = volumeResult.getOrNull()
            val like = likeResult.getOrNull()
            val repeat = repeatResult.getOrNull()
            val shuffle = shuffleResult.getOrNull()
            val fullscreen = fullscreenResult.getOrNull()

            volume?.let {
                volumeUnitInterval = when {
                    it.state > 1.0 -> false
                    it.state > 0.0 -> true
                    else -> volumeUnitInterval
                }
            }

            val serverVolumeSlider = volume?.state?.let(::serverVolumeToSlider)
            val shouldSuppressStaleZero = shouldSuppressStaleZeroVolume(
                serverVolumeSlider = serverVolumeSlider,
                isMuted = volume?.isMuted,
                currentSlider = _uiState.value.volumeSlider
            )

            val hasFailure = listOf(
                songResult.exceptionOrNull(),
                volumeResult.exceptionOrNull(),
                likeResult.exceptionOrNull(),
                repeatResult.exceptionOrNull(),
                shuffleResult.exceptionOrNull(),
                fullscreenResult.exceptionOrNull()
            ).any { it != null }

            _uiState.update {
                it.copy(
                    song = song,
                    volumeState = volume,
                    likeState = like?.state,
                    repeatMode = repeat?.mode,
                    shuffleEnabled = shuffle?.state,
                    fullscreenEnabled = fullscreen?.state,
                    volumeSlider = when {
                        serverVolumeSlider == null -> it.volumeSlider
                        shouldSuppressStaleZero -> it.volumeSlider
                        else -> serverVolumeSlider
                    },
                    isBusy = false,
                    status = if (hasFailure) "Partially refreshed" else "Synced",
                    error = if (hasFailure) "Some state values could not be fetched" else null
                )
            }
        }
    }

    fun sendPlay() = sendCommand("Play") { it.play() }
    fun sendPause() = sendCommand("Pause") { it.pause() }
    fun sendTogglePlay() = sendCommand("Toggle play") { it.togglePlay() }
    fun sendPrevious() = sendCommand("Previous") { it.previous() }
    fun sendNext() = sendCommand("Next") { it.next() }
    fun sendLike() = sendCommand("Like") { it.like() }
    fun sendDislike() = sendCommand("Dislike") { it.dislike() }
    fun sendToggleMute() = sendCommand("Toggle mute") { it.toggleMute() }
    fun sendShuffle() = sendCommand("Shuffle") { it.shuffle() }
    fun sendToggleFullscreen() {
        val target = !(_uiState.value.fullscreenEnabled ?: false)
        sendCommand(if (target) "Fullscreen on" else "Fullscreen off") { it.setFullscreen(target) }
    }

    fun sendSwitchRepeat() {
        val iteration = _uiState.value.repeatIterationInput.toIntOrNull()
        if (iteration == null || iteration < 1) {
            _uiState.update { it.copy(error = "Repeat iteration must be >= 1") }
            return
        }
        sendCommand("Switch repeat") { it.switchRepeat(iteration) }
    }

    fun sendGoBack() {
        val seconds = _uiState.value.seekSecondsInput.toDoubleOrNull()
        if (seconds == null || seconds <= 0.0) {
            _uiState.update { it.copy(error = "Seek seconds must be > 0") }
            return
        }
        sendCommand("Go back") { it.goBack(seconds) }
    }

    fun sendGoForward() {
        val seconds = _uiState.value.seekSecondsInput.toDoubleOrNull()
        if (seconds == null || seconds <= 0.0) {
            _uiState.update { it.copy(error = "Seek seconds must be > 0") }
            return
        }
        sendCommand("Go forward") { it.goForward(seconds) }
    }

    fun sendSeekTo() {
        val seconds = _uiState.value.seekSecondsInput.toDoubleOrNull()
        if (seconds == null || seconds < 0.0) {
            _uiState.update { it.copy(error = "Seek target must be >= 0") }
            return
        }
        sendCommand("Seek") { it.seekTo(seconds) }
    }

    fun commitVolume() {
        val slider = _uiState.value.volumeSlider.roundToInt().coerceIn(0, 100).toDouble()
        val serverValue = sliderToServerVolume(slider)
        sendCommand("Volume set to ${slider.toInt()}%") { it.setVolume(serverValue) }
    }

    fun disconnect() {
        autoRefreshJob?.cancel()
        repository = null
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                isBusy = false,
                status = "Disconnected",
                error = null,
                song = null,
                volumeState = null,
                likeState = null,
                repeatMode = null,
                shuffleEnabled = null,
                fullscreenEnabled = null
            )
        }
    }

    fun sendGoBackBy(seconds: Double) {
        if (seconds <= 0.0) {
            return
        }
        sendCommand("Go back") { it.goBack(seconds) }
    }

    fun sendGoForwardBy(seconds: Double) {
        if (seconds <= 0.0) {
            return
        }
        sendCommand("Go forward") { it.goForward(seconds) }
    }

    private fun sendCommand(label: String, action: suspend (PearRepository) -> Result<Unit>) {
        val repo = repository ?: run {
            _uiState.update { it.copy(error = "Please connect first") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null, status = "$label...") }
            val result = action(repo)
            if (result.isSuccess) {
                _uiState.update { it.copy(isBusy = false, status = "$label sent") }
                refreshState(silent = true)
            } else {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        status = "Command failed",
                        error = result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                refreshState(silent = true)
            }
        }
    }

    private fun serverVolumeToSlider(serverValue: Double): Float {
        val sliderValue = if (volumeUnitInterval == true) {
            serverValue * 100.0
        } else {
            serverValue
        }
        return sliderValue.coerceIn(0.0, 100.0).toFloat()
    }

    private fun sliderToServerVolume(sliderValue: Double): Double {
        return if (volumeUnitInterval == true) {
            (sliderValue / 100.0).coerceIn(0.0, 1.0)
        } else {
            sliderValue.coerceIn(0.0, 100.0)
        }
    }

    private fun shouldSuppressStaleZeroVolume(
        serverVolumeSlider: Float?,
        isMuted: Boolean?,
        currentSlider: Float
    ): Boolean {
        if (serverVolumeSlider == null) {
            return false
        }

        val isSuspiciousZero = (isMuted == false) && serverVolumeSlider <= 0.5f && currentSlider > 1f
        consecutiveSuspiciousZeroVolumeReads = if (isSuspiciousZero) {
            consecutiveSuspiciousZeroVolumeReads + 1
        } else {
            0
        }

        return consecutiveSuspiciousZeroVolumeReads >= 3
    }

    private fun normalizeBaseUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return null
        }

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val pattern = Regex("^https?://.+")
        if (!pattern.matches(withScheme)) {
            return null
        }

        return if (withScheme.endsWith('/')) withScheme else "$withScheme/"
    }

    override fun onCleared() {
        autoRefreshJob?.cancel()
        super.onCleared()
    }
}
