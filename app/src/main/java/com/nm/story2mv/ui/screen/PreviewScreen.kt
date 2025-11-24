package com.nm.story2mv.ui.screen

import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nm.story2mv.media.VideoExportResult
import com.nm.story2mv.ui.viewmodel.PreviewUiState
import kotlinx.coroutines.delay

@Composable
fun PreviewScreen(
    state: PreviewUiState,
    onExport: () -> Unit,
    onExportResultConsumed: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(state.videoUri) {
        ExoPlayer.Builder(context).build().apply {
            state.videoUri?.let { uri ->
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(state.videoUri) {
        state.videoUri?.let {
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
        }
    }

    var playbackPosition by remember { mutableLongStateOf(0L) }
    var durationState by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(player.volume) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var isLooping by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(player) {
        while (true) {
            playbackPosition = player.currentPosition
            durationState = player.duration.takeIf { it > 0 } ?: 0L
            isPlaying = player.isPlaying
            playbackSpeed = player.playbackParameters.speed
            isLooping = player.repeatMode == Player.REPEAT_MODE_ONE
            delay(400)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
//        Text(text = state.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .requiredHeight(300.dp),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                }
            },
            update = { view ->
                if (view.player != player) {
                    view.player = player
                }
            }
        )

        PlaybackControls(
            isPlaying = isPlaying,
            progress = playbackPosition,
            total = durationState,
            volume = volume,
            playbackSpeed = playbackSpeed,
            isLooping = isLooping,
            onTogglePlay = {
                val target = !isPlaying
                player.playWhenReady = target
                if (target) player.play() else player.pause()
                isPlaying = target
            },
            onScrub = { fraction ->
                if (durationState > 0) {
                    val targetPosition = (durationState * fraction).toLong()
                    player.seekTo(targetPosition)
                    playbackPosition = targetPosition
                }
            },
            onVolumeChange = { newVolume ->
                volume = newVolume
                player.volume = newVolume
            },
            onSpeedChange = { speed ->
                playbackSpeed = speed
                player.playbackParameters = player.playbackParameters.withSpeed(speed)
            },
            onLoopToggle = { looping ->
                isLooping = looping
                player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onExport,
            enabled = state.videoUri != null && !state.isExporting
        ) {
            Text(if (state.isExporting) "导出中..." else "Export Video")
        }

        when (val result = state.exportResult) {
            is VideoExportResult.Success -> {
                Text(
                    text = "已导出至: ${result.outputUri}",
                    color = MaterialTheme.colorScheme.primary
                )
                LaunchedEffect(result) {
                    onExportResultConsumed()
                }
            }

            is VideoExportResult.Failure -> {
                Text(text = result.error, color = MaterialTheme.colorScheme.error)
                LaunchedEffect(result) {
                    onExportResultConsumed()
                }
            }

            null -> Unit
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    progress: Long,
    total: Long,
    volume: Float,
    playbackSpeed: Float,
    isLooping: Boolean,
    onTogglePlay: () -> Unit,
    onScrub: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLoopToggle: (Boolean) -> Unit
) {
    val progressFraction = if (total > 0) progress.toFloat() / total.toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 0.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.requiredSize(24.dp)
            ) { Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }

            Slider(
                modifier = Modifier
                    .weight(1f)
                    .requiredHeight(4.dp),
                value = progressFraction,
                onValueChange = onScrub,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Text(
            text = "${formatTime(progress)} / ${formatTime(total)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .align(Alignment.Start) // 右对齐
        )
        Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(4.dp),
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedSelector(
                current = playbackSpeed,
                onSpeedSelected = onSpeedChange
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("循环播放")
                Switch(checked = isLooping, onCheckedChange = onLoopToggle)
            }
        }
    }
}

@Composable
private fun SpeedSelector(current: Float, onSpeedSelected: (Float) -> Unit) {
    val options = listOf(0.5f, 1f, 1.5f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { speed ->
            val selected = speed == current
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clickable { onSpeedSelected(speed) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${speed}x",
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
