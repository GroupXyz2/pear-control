package com.pearcontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val vm: MainViewModel = viewModel()
                PearControlApp(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PearControlApp(vm: MainViewModel) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    if (!uiState.isAuthenticated) {
        LoginScreen(
            uiState = uiState,
            onServerChanged = vm::updateServerUrl,
            onDeviceIdChanged = vm::updateDeviceId,
            onConnect = vm::authenticate
        )
        return
    }

    val background = Brush.linearGradient(
        colors = listOf(Color(0xFF081422), Color(0xFF11314A), Color(0xFF1D4E45)),
        start = Offset.Zero,
        end = Offset(1200f, 2200f)
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(padding)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeaderRow(
                    songTitle = uiState.song?.title ?: "Nothing playing",
                    subtitle = uiState.song?.artist ?: "Pear Desktop",
                    isBusy = uiState.isBusy,
                    onRefresh = { vm.refreshState() },
                    onSettings = { showSettings = true }
                )

                NowPlayingCard(uiState = uiState)

                CoreTransportControls(
                    isBusy = uiState.isBusy,
                    isPaused = uiState.song?.isPaused ?: true,
                    onPrev = vm::sendPrevious,
                    onTogglePlay = vm::sendTogglePlay,
                    onNext = vm::sendNext,
                    onBack10 = { vm.sendGoBackBy(10.0) },
                    onForward10 = { vm.sendGoForwardBy(10.0) }
                )

                VolumeCard(
                    uiState = uiState,
                    onChange = vm::updateVolumeSlider,
                    onCommit = vm::commitVolume,
                    onToggleMute = vm::sendToggleMute
                )

                uiState.error?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x66B3261E),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFFFDAD6)
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            uiState = uiState,
            onDismiss = { showSettings = false },
            onServerChanged = vm::updateServerUrl,
            onDeviceIdChanged = vm::updateDeviceId,
            onReconnect = vm::authenticate,
            onDisconnect = vm::disconnect,
            onLike = vm::sendLike,
            onDislike = vm::sendDislike,
            onShuffle = vm::sendShuffle,
            onFullscreen = vm::sendToggleFullscreen,
            onRepeatIterationChanged = vm::updateRepeatIteration,
            onSwitchRepeat = vm::sendSwitchRepeat,
            onSeekChanged = vm::updateSeekSeconds,
            onGoBack = vm::sendGoBack,
            onGoForward = vm::sendGoForward,
            onSeekTo = vm::sendSeekTo
        )
    }
}

@Composable
private fun LoginScreen(
    uiState: MainUiState,
    onServerChanged: (String) -> Unit,
    onDeviceIdChanged: (String) -> Unit,
    onConnect: () -> Unit
) {
    val bg = Brush.radialGradient(
        colors = listOf(Color(0xFF1E3A5F), Color(0xFF0A1022)),
        radius = 1400f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xE61D2438))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(color = Color(0xFF2A4F87), shape = CircleShape, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White)
                        }
                    }
                    Column {
                        Text("Pear Remote", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text("Connect once to start controlling playback", color = Color(0xFFC4D4F7))
                    }
                }

                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = onServerChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.25:3000") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.deviceId,
                    onValueChange = onDeviceIdChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Auth ID") },
                    placeholder = { Text("pear-device-id") },
                    singleLine = true
                )

                Button(
                    onClick = onConnect,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Connect")
                }

                Text("Status: ${uiState.status}", color = Color(0xFFC4D4F7))
                uiState.error?.let {
                    Text(it, color = Color(0xFFFFB4AB))
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    songTitle: String,
    subtitle: String,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = songTitle,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD1E4FF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onRefresh, enabled = !isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
private fun NowPlayingCard(uiState: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x662C4A6A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF2B5E97), Color(0xFF143455)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.song?.title ?: "No active track",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.song?.artist ?: "Open a song in Pear Desktop",
                        color = Color(0xFFD9E6FA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (uiState.likeState == "LIKE") Color(0xFFFFA9A3) else Color(0x55FFFFFF)
                )
            }

            val progress = run {
                val elapsed = uiState.song?.elapsedSeconds ?: 0.0
                val total = uiState.song?.songDuration ?: 0.0
                if (total <= 0.0) 0f else (elapsed / total).coerceIn(0.0, 1.0).toFloat()
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color(0xFFBFDFFF),
                trackColor = Color(0x553E5F86)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatSeconds(uiState.song?.elapsedSeconds), color = Color(0xFFD1E4FF))
                Text(formatSeconds(uiState.song?.songDuration), color = Color(0xFFD1E4FF))
            }
        }
    }
}

@Composable
private fun CoreTransportControls(
    isBusy: Boolean,
    isPaused: Boolean,
    onPrev: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onBack10: () -> Unit,
    onForward10: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x55344A67))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    onClick = onPrev,
                    enabled = !isBusy,
                    icon = { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous") }
                )
                CircleIconButton(
                    onClick = onTogglePlay,
                    enabled = !isBusy,
                    large = true,
                    icon = {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Play or pause",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                )
                CircleIconButton(
                    onClick = onNext,
                    enabled = !isBusy,
                    icon = { Icon(Icons.Default.SkipNext, contentDescription = "Next") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onBack10,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("10s")
                }
                FilledTonalButton(
                    onClick = onForward10,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("10s")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    large: Boolean = false,
    icon: @Composable () -> Unit
) {
    val size = if (large) 86.dp else 62.dp
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (large) Color(0xFFB8DCFF) else Color(0x553E6B96),
        contentColor = if (large) Color(0xFF0A223A) else Color.White,
        tonalElevation = 6.dp,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            icon()
        }
    }
}

@Composable
private fun VolumeCard(
    uiState: MainUiState,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onToggleMute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x55344A67))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speaker, contentDescription = null, tint = Color.White)
                    Text("Volume", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Text("${uiState.volumeSlider.roundToInt()}%", color = Color(0xFFD1E4FF))
            }

            Slider(
                value = uiState.volumeSlider,
                onValueChange = onChange,
                onValueChangeFinished = onCommit,
                valueRange = 0f..100f,
                enabled = !uiState.isBusy
            )

            Text(
                text = "Set-only mode (server readback is unreliable)",
                color = Color(0xFFC4D4F7),
                style = MaterialTheme.typography.bodySmall
            )

            FilledTonalButton(
                onClick = onToggleMute,
                enabled = !uiState.isBusy,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.volumeState?.isMuted == true) "Unmute" else "Mute")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onServerChanged: (String) -> Unit,
    onDeviceIdChanged: (String) -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShuffle: () -> Unit,
    onFullscreen: () -> Unit,
    onRepeatIterationChanged: (String) -> Unit,
    onSwitchRepeat: () -> Unit,
    onSeekChanged: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onSeekTo: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111D2C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Color.White)

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2B40))) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Connection", color = Color(0xFFD9E6FA), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = uiState.serverUrl,
                        onValueChange = onServerChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.deviceId,
                        onValueChange = onDeviceIdChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Auth ID") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReconnect, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) {
                            Text("Reconnect")
                        }
                        FilledTonalButton(onClick = onDisconnect, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Disconnect")
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2B40))) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Advanced controls", color = Color(0xFFD9E6FA), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onLike, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) { Text("Like") }
                        FilledTonalButton(onClick = onDislike, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) { Text("Dislike") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onShuffle, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) { Text("Shuffle") }
                        FilledTonalButton(onClick = onFullscreen, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) {
                            Text(if (uiState.fullscreenEnabled == true) "Exit Fullscreen" else "Fullscreen")
                        }
                    }
                    OutlinedTextField(
                        value = uiState.repeatIterationInput,
                        onValueChange = onRepeatIterationChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Repeat button presses") },
                        singleLine = true
                    )
                    Button(onClick = onSwitchRepeat, enabled = !uiState.isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text("Switch Repeat")
                    }
                    OutlinedTextField(
                        value = uiState.seekSecondsInput,
                        onValueChange = onSeekChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seek seconds") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onGoBack, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) { Text("Go Back") }
                        FilledTonalButton(onClick = onGoForward, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) { Text("Go Forward") }
                        FilledTonalButton(onClick = onSeekTo, enabled = !uiState.isBusy, modifier = Modifier.weight(1f)) { Text("Seek To") }
                    }
                }
            }

            uiState.error?.let {
                Text(it, color = Color(0xFFFFB4AB))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun formatSeconds(value: Double?): String {
    val total = (value ?: return "--:--").toInt().coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}
