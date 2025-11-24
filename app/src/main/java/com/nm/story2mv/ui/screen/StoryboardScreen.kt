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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.ui.screen.components.AnimatedDots
import com.nm.story2mv.ui.screen.components.EmptyStateCard
import com.nm.story2mv.ui.screen.components.FullScreenLoading
import kotlin.math.max

@Composable
fun StoryboardScreen(
    state: StoryProject?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    onShotDetail: (Shot) -> Unit,
    onPreview: () -> Unit,
    onGenerateVideo: () -> Unit,
    onRetry: () -> Unit
) {
    when {
        isLoading -> FullScreenLoading(message = "加载分镜中…")

        state == null -> EmptyStoryboardPlaceholder(errorMessage = errorMessage, onRetry = onRetry)

        else -> {
            StoryboardContent(
                project = state,
                isRefreshing = isRefreshing,
                errorMessage = errorMessage,
                onShotDetail = onShotDetail,
                onGenerateVideo = onGenerateVideo,
                onPreview = onPreview,
                onRetry = onRetry
            )
        }
    }
}
@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun StoryboardContent(
    project: StoryProject,
    isRefreshing: Boolean,
    errorMessage: String?,
    onShotDetail: (Shot) -> Unit,
    onGenerateVideo: () -> Unit,
    onPreview: () -> Unit,
    onRetry: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { max(project.shots.size, 1) })
    val readyCount = project.shots.count { it.status == ShotStatus.READY }
    val generatingCount = project.shots.count { it.status == ShotStatus.GENERATING }
    val completionRatio = if (project.shots.isEmpty()) 0f else readyCount.toFloat() / project.shots.size
    val canPreview = project.videoState == VideoTaskState.READY && project.previewUrl != null
    val canGenerateVideo = project.shots.isNotEmpty() &&
        project.shots.all { it.status == ShotStatus.READY } &&
        project.videoState != VideoTaskState.GENERATING
    val waitingCount = project.shots.count { it.status != ShotStatus.READY }
    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRetry)
    val generateCooldownMs = 1800L
    var lastGenerateClick by remember { mutableStateOf(0L) }
    val isCooling = (System.currentTimeMillis() - lastGenerateClick) in 0..generateCooldownMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VideoStatusBanner(project.videoState, project.previewUrl != null)

            StoryOverviewCard(
                project = project,
                readyCount = readyCount,
                generatingCount = generatingCount,
                completionRatio = completionRatio
            )

            errorMessage?.let {
                InlineMessage(text = it, onRetry = onRetry)
            }

            if (project.shots.isEmpty()) {
                EmptyShots(onRetry)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "分镜镜头", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "左右滑动查看镜头详情",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${project.shots.size} 个镜头",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val refreshHint = when {
                    isRefreshing -> "刷新中..."
                    errorMessage != null -> "更新失败，稍后可重试"
                    else -> "刚刚更新"
                }
                Text(
                    text = refreshHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canPreview) {
                    AssistChip(onClick = onPreview, label = { Text("最新预览已就绪") })
                }
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    userScrollEnabled = !isRefreshing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) { page ->
                    val shot = project.shots.getOrNull(page)
                    if (shot != null) {
                        ShotCard(
                            shot = shot,
                            onDetail = { onShotDetail(shot) }
                        )
                    }
                }
                PagerDots(total = project.shots.size, current = pagerState.currentPage)
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastGenerateClick > generateCooldownMs) {
                        lastGenerateClick = now
                        onGenerateVideo()
                    }
                },
                enabled = canGenerateVideo && !isCooling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    when (project.videoState) {
                        VideoTaskState.GENERATING -> "合成中..."
                        VideoTaskState.READY -> "重新生成视频"
                        else -> if (canGenerateVideo) "生成视频" else "等待 $waitingCount 个镜头就绪"
                    }
                )
            }
            if (project.videoState == VideoTaskState.GENERATING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                AnimatedDots(text = "视频合成中", modifier = Modifier.padding(top = 4.dp))
            }
            if (isCooling && canGenerateVideo) {
                Text(
                    text = "请稍候再试（防抖中）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (canPreview) {
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    onClick = onPreview
                ) {
                    Text("查看成品预览")
                }
            }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
        if (isRefreshing) {
            Surface(
                modifier = Modifier
                    .matchParentSize(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedDots(text = "刷新分镜列表", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyStoryboardPlaceholder(
    errorMessage: String?,
    onRetry: () -> Unit
) {
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
        errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRetry
        ) {
            Text("重新加载分镜")
        }
    }
}

@Composable
private fun ShotCard(
    shot: Shot,
    onDetail: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clickable { onDetail() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                ShotStatusBadge(
                    status = shot.status,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
                val label = when (shot.status) {
                    ShotStatus.NOT_GENERATED -> "生成后将展示镜头画面"
                    ShotStatus.GENERATING -> "生成中，完成后将展示画面"
                    ShotStatus.READY -> "已生成，可点击查看详情"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = shot.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = shot.prompt,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = shot.narration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "转场：${shot.transition.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                AssistChip(onClick = onDetail, label = { Text("查看镜头详情") })
            }
        }
    }
}

@Composable
private fun ShotStatusBadge(status: ShotStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ShotStatus.NOT_GENERATED -> "未生成" to Color.Gray
        ShotStatus.GENERATING -> "生成中" to MaterialTheme.colorScheme.tertiary
        ShotStatus.READY -> "已生成" to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyShots(onRetry: () -> Unit) {
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
            Text(text = "暂无镜头", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "生成故事后会自动创建镜头，或点击下方重新加载/生成。",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AssistChip(onClick = onRetry, label = { Text("重新加载") })
            }
        }
    }
}

@Composable
private fun StoryOverviewCard(
    project: StoryProject,
    readyCount: Int,
    generatingCount: Int,
    completionRatio: Float
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = project.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = project.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "故事风格", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = project.style.label, style = MaterialTheme.typography.titleSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "完成度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${(completionRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            LinearProgressIndicator(
                progress = { completionRatio },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "已就绪：$readyCount", style = MaterialTheme.typography.bodySmall)
                Text(text = "生成中：$generatingCount", style = MaterialTheme.typography.bodySmall)
                Text(text = "总计：${project.shots.size}", style = MaterialTheme.typography.bodySmall)
            }
            VideoStatusBanner(project.videoState, project.previewUrl != null)
        }
    }
}

@Composable
private fun VideoStatusBanner(videoState: VideoTaskState, hasPreview: Boolean) {
    val (label, message, color) = when (videoState) {
        VideoTaskState.GENERATING -> Triple(
            "合成中",
            "正在合成成片，完成后将自动跳转至预览页。",
            MaterialTheme.colorScheme.tertiary
        )

        VideoTaskState.READY -> Triple(
            "已完成",
            if (hasPreview) "最新视频可预览/导出。" else "视频生成完成，等待预览地址。",
            MaterialTheme.colorScheme.primary
        )

        VideoTaskState.ERROR -> Triple(
            "失败",
            "合成失败，请稍后重试。",
            MaterialTheme.colorScheme.error
        )

        else -> Triple(
            "待处理",
            "点击下方按钮开始视频合成。",
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

@Composable
private fun PagerDots(total: Int, current: Int) {
    if (total <= 1) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(total) { index ->
            val isActive = index == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun InlineMessage(text: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.error,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = text, style = MaterialTheme.typography.bodySmall)
            AssistChip(onClick = onRetry, label = { Text("重试") })
        }
    }
}
