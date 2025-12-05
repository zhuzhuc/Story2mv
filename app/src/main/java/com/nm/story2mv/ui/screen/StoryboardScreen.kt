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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.ui.screen.components.AnimatedDots
import com.nm.story2mv.ui.screen.components.EmptyStateCard
import com.nm.story2mv.ui.screen.components.FullScreenLoading
import androidx.compose.ui.tooling.preview.Preview
import com.nm.story2mv.data.model.StoryStyle
import java.time.Instant
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
    onGenerateAll: () -> Unit,
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
                    onGenerateAll = onGenerateAll,
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
    onGenerateAll: () -> Unit,
    onPreview: () -> Unit,
    onRetry: () -> Unit
) {
    val scrollState = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val pagerHeight = remember(screenHeight) { (screenHeight * 0.42f).coerceIn(220.dp, 360.dp) }
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

    val currentShot by remember(pagerState, project.shots) {
        derivedStateOf { project.shots.getOrNull(pagerState.currentPage) ?: project.shots.firstOrNull() }
    }
    val tryGenerate: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastGenerateClick > generateCooldownMs) {
            lastGenerateClick = now
            onGenerateVideo()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StoryHeroCard(
                project = project,
                currentShot = currentShot,
                readyCount = readyCount,
                generatingCount = generatingCount,
                completionRatio = completionRatio,
                canPreview = canPreview,
                canGenerateVideo = canGenerateVideo,
                isCooling = isCooling,
                waitingCount = waitingCount,
                onPreview = onPreview,
                onGenerateVideo = tryGenerate,
                onGenerateAll = onGenerateAll
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
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    userScrollEnabled = !isRefreshing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pagerHeight)
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

            Spacer(modifier = Modifier.height(8.dp))
            // 底部主操作移除，统一在 Hero 卡片内
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
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                if (shot.thumbnailUrl != null) {
                    AsyncImage(
                        model = shot.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(MaterialTheme.shapes.medium)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.6f to Color.Black.copy(alpha = 0.25f),
                                    1f to Color.Black.copy(alpha = 0.5f)
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                ShotStatusBadge(
                    status = shot.status,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = shot.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (shot.thumbnailUrl != null) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "转场：${shot.transition.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (shot.thumbnailUrl != null) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
                if (shot.thumbnailUrl == null) {
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
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
private fun StoryHeroCard(
    project: StoryProject,
    currentShot: Shot?,
    readyCount: Int,
    generatingCount: Int,
    completionRatio: Float,
    canPreview: Boolean,
    canGenerateVideo: Boolean,
    isCooling: Boolean,
    waitingCount: Int,
    onPreview: () -> Unit,
    onGenerateVideo: () -> Unit,
    onGenerateAll: () -> Unit
) {
    val thumb = currentShot?.thumbnailUrl
    val completionText = "${(completionRatio * 100).toInt()}% · 已就绪 $readyCount/${project.shots.size} · 生成中 $generatingCount"
    val primaryLabel = when (project.videoState) {
        VideoTaskState.GENERATING -> "合成中..."
        VideoTaskState.READY -> "重新生成视频"
        else -> if (canGenerateVideo) "生成视频" else "等待 $waitingCount 个镜头就绪"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                                    )
                                )
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.1f),
                                0.6f to Color.Black.copy(alpha = 0.25f),
                                1f to Color.Black.copy(alpha = 0.45f)
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoStatusChip(videoState = project.videoState, hasPreview = canPreview)
                    currentShot?.let {
                        ShotStatusBadge(status = it.status)
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = currentShot?.title ?: project.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentShot?.prompt ?: project.synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentShot != null) {
                        Text(
                            text = "当前镜头 · 转场 ${currentShot.transition.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = completionText, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { completionRatio },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                if (!canGenerateVideo && waitingCount > 0) {
                    Text(
                        text = "仍有 $waitingCount 个镜头未就绪，完成后可生成视频。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onGenerateVideo,
                        modifier = Modifier.weight(1.3f),
                        enabled = canGenerateVideo && !isCooling
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(primaryLabel)
                    }
                    OutlinedButton(
                        onClick = onPreview,
                        modifier = Modifier.weight(1f),
                        enabled = canPreview
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("预览")
                    }
                }
                FilledTonalButton(
                    onClick = onGenerateAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isCooling
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("一键生成全部镜头")
                }
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
        }
    }
}

@Composable
private fun VideoStatusChip(videoState: VideoTaskState, hasPreview: Boolean) {
    val (label, color) = when (videoState) {
        VideoTaskState.GENERATING -> "合成中" to MaterialTheme.colorScheme.tertiary
        VideoTaskState.READY -> if (hasPreview) "可预览" to MaterialTheme.colorScheme.primary else "已完成" to MaterialTheme.colorScheme.primary
        VideoTaskState.ERROR -> "失败" to MaterialTheme.colorScheme.error
        else -> "待处理" to MaterialTheme.colorScheme.secondary
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
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

@Preview(showBackground = true)
@Composable
private fun StoryboardPreview() {
    val shots = listOf(
        Shot(
            id = "s1",
            storyId = 1L,
            title = "镜头一",
            prompt = "雨夜街头",
            narration = "雨夜街头的摄影师",
            thumbnailUrl = "https://picsum.photos/seed/1/600/360",
            status = ShotStatus.READY
        ),
        Shot(
            id = "s2",
            storyId = 1L,
            title = "镜头二",
            prompt = "旧城小巷",
            narration = "旧城小巷的记忆",
            thumbnailUrl = "https://picsum.photos/seed/2/600/360",
            status = ShotStatus.READY
        ),
        Shot(
            id = "s3",
            storyId = 1L,
            title = "镜头三",
            prompt = "记忆交汇",
            narration = "记忆交汇的瞬间",
            thumbnailUrl = "https://picsum.photos/seed/3/600/360",
            status = ShotStatus.NOT_GENERATED
        )
    )
    StoryboardContent(
        project = StoryProject(
            id = 1L,
            title = "雨夜回忆",
            style = StoryStyle.CINEMATIC,
            synopsis = "一位孤独的摄影师在雨夜的城市中寻找遗失的记忆。",
            createdAt = Instant.now(),
            shots = shots,
            videoState = VideoTaskState.IDLE
        ),
        isRefreshing = false,
        errorMessage = null,
        onShotDetail = {},
        onGenerateVideo = {},
        onGenerateAll = {},
        onPreview = {},
        onRetry = {}
    )
}
