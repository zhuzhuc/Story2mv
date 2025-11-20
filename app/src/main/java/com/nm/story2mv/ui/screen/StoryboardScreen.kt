package com.nm.story2mv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.ui.screen.components.AnimatedDots
import com.nm.story2mv.ui.screen.components.EmptyStateCard
import com.nm.story2mv.ui.screen.components.FullScreenLoading

@Composable
fun StoryboardScreen(
    state: StoryProject?,
    isLoading: Boolean,
    onShotDetail: (Shot) -> Unit,
    onPreview: () -> Unit,
    onGenerateVideo: () -> Unit
) {
    when {
        isLoading -> FullScreenLoading(message = "加载分镜中…")

        state == null -> EmptyStoryboardPlaceholder()

        else -> {
            StoryboardContent(
                project = state,
                onShotDetail = onShotDetail,
                onGenerateVideo = onGenerateVideo,
                onPreview = onPreview
            )
        }
    }
}

@Composable
private fun StoryboardContent(
    project: StoryProject,
    onShotDetail: (Shot) -> Unit,
    onGenerateVideo: () -> Unit,
    onPreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = project.title, style = MaterialTheme.typography.headlineMedium)
        Text(text = project.synopsis, style = MaterialTheme.typography.bodyLarge)
        VideoStatusBanner(project.videoState)

        if (project.shots.isEmpty()) {
            EmptyShots()
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(project.shots) { shot ->
                    ShotCard(
                        shot = shot,
                        onDetail = { onShotDetail(shot) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onGenerateVideo,
            enabled = project.videoState != VideoTaskState.GENERATING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when (project.videoState) {
                    VideoTaskState.GENERATING -> "合成中..."
                    VideoTaskState.READY -> "重新生成视频"
                    else -> "生成视频"
                }
            )
        }
        if (project.videoState == VideoTaskState.GENERATING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AnimatedDots(text = "视频合成中", modifier = Modifier.padding(top = 4.dp))
        }
        if (project.videoState == VideoTaskState.READY) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPreview
            ) {
                Text("查看成品预览")
            }
        }
    }
}

@Composable
private fun EmptyStoryboardPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateCard(
            title = "暂无分镜",
            description = "请先在 Create 页面生成故事内容。"
        )
    }
}

@Composable
private fun ShotCard(
    shot: Shot,
    onDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(240.dp)
            .height(320.dp)
            .clickable { onDetail() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = shot.thumbnailUrl,
                contentDescription = shot.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = shot.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ShotStatusBadge(shot.status)
                AssistChip(onClick = onDetail, label = { Text("详情") })
            }
        }
    }
}

@Composable
private fun ShotStatusBadge(status: ShotStatus) {
    val (label, color) = when (status) {
        ShotStatus.NOT_GENERATED -> "未生成" to Color.Gray
        ShotStatus.GENERATING -> "生成中" to MaterialTheme.colorScheme.tertiary
        ShotStatus.READY -> "已生成" to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = color)
    }
}

@Composable
private fun EmptyShots() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "暂无分镜镜头", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "生成故事后我们会为你自动创建分镜，或者点击下方按钮重新生成。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun VideoStatusBanner(videoState: VideoTaskState) {
    val (label, message, color) = when (videoState) {
        VideoTaskState.GENERATING -> Triple(
            "生成中",
            "正在合成成片，完成后将自动跳转至预览页。",
            MaterialTheme.colorScheme.tertiary
        )

        VideoTaskState.READY -> Triple(
            "完成",
            "最新的视频已准备就绪，可以直接预览或导出。",
            MaterialTheme.colorScheme.primary
        )

        VideoTaskState.ERROR -> Triple(
            "失败",
            "合成失败，可稍后重试。",
            MaterialTheme.colorScheme.error
        )

        else -> Triple(
            "待处理",
            "点击下方按钮即可开始视频合成。",
            MaterialTheme.colorScheme.secondary
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, color = color, style = MaterialTheme.typography.labelLarge)
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
