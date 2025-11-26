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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nm.story2mv.media.ExportDestination
import com.nm.story2mv.media.VideoExportResult
import com.nm.story2mv.ui.viewmodel.PreviewUiState
import kotlinx.coroutines.delay

@Composable
fun PreviewScreen(
    state: PreviewUiState,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onExportResultConsumed: () -> Unit,
    onSelectIndex: (Int) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val playerHeight = remember(screenHeight) { (screenHeight * 0.5f).coerceIn(220.dp, 360.dp) }
    val player = remember(state.videoUri) {
        ExoPlayer.Builder(context).build().apply {
            state.videoUri?.let { uri ->
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
    }
    val hasSource = state.videoUri != null || state.playlist.isNotEmpty()
    val audioPlayer = remember(state.audioUri) {
        state.audioUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                volume = 1f
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            audioPlayer?.release()
        }
    }

    LaunchedEffect(state.videoUri) {
        state.videoUri?.let {
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
            player.playWhenReady = true
        }
    }
    LaunchedEffect(state.audioUri) {
        audioPlayer?.apply {
            stop()
            clearMediaItems()
        }
        state.audioUri?.let { uri ->
            audioPlayer?.setMediaItem(MediaItem.fromUri(uri))
            audioPlayer?.prepare()
            audioPlayer?.playWhenReady = true
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
            .verticalScroll(scrollState)
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
                .height(playerHeight),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
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
                if (target) {
                    player.play()
                    audioPlayer?.playWhenReady = true
                } else {
                    player.pause()
                    audioPlayer?.pause()
                }
                isPlaying = target
            },
            onScrub = { fraction ->
                if (durationState > 0) {
                    val targetPosition = (durationState * fraction).toLong()
                    player.seekTo(targetPosition)
                    playbackPosition = targetPosition
                    audioPlayer?.seekTo(targetPosition)
                }
            },
            onVolumeChange = { newVolume ->
                volume = newVolume
                player.volume = newVolume
                audioPlayer?.volume = newVolume
            },
            onSpeedChange = { speed ->
                playbackSpeed = speed
                player.playbackParameters = player.playbackParameters.withSpeed(speed)
                audioPlayer?.playbackParameters = audioPlayer.playbackParameters.withSpeed(speed)
            },
            onLoopToggle = { looping ->
                isLooping = looping
                player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                audioPlayer?.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        )

        if (state.playlist.size > 1) {
            Text(
                text = "将拼接 ${state.playlist.size} 个片段后处理",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onSave,
                enabled = hasSource && !state.isExporting
            ) {
                val saving = state.isExporting && state.exportingDestination == ExportDestination.DOWNLOADS
                Text(if (saving) "保存中..." else "保存到下载")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onExport,
                enabled = hasSource && !state.isExporting
            ) {
                val exporting = state.isExporting && state.exportingDestination == ExportDestination.GALLERY
                Text(if (exporting) "导出中..." else "拼接并导出")
            }
        }

        if (state.playlist.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.playlist.forEachIndexed { index, uri ->
                    val isActive = index == state.currentIndex
                    Button(
                        onClick = { onSelectIndex(index) },
                        enabled = !state.isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(text = "片段${index + 1}", color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        when (val result = state.exportResult) {
            is VideoExportResult.Success -> {
                val label = if (result.destination == ExportDestination.GALLERY) "已导出到相册" else "已保存到下载"
                Text(
                    text = "$label: ${result.outputUri}",
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
