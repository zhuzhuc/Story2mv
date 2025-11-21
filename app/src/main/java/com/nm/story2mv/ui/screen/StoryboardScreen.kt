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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onShotDetail: (Shot) -> Unit,
    onPreview: () -> Unit,
    onGenerateVideo: () -> Unit
) {
    when {
        isLoading -> FullScreenLoading(message = "Loading storyboard…")

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
    val pagerState = rememberPagerState(pageCount = { max(project.shots.size, 1) })
    val readyCount = project.shots.count { it.status == ShotStatus.READY }
    val generatingCount = project.shots.count { it.status == ShotStatus.GENERATING }
    val completionRatio = if (project.shots.isEmpty()) 0f else readyCount.toFloat() / project.shots.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StoryOverviewCard(
            project = project,
            readyCount = readyCount,
            generatingCount = generatingCount,
            completionRatio = completionRatio
        )

        if (project.shots.isEmpty()) {
            EmptyShots()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Storyboard Shots", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Swipe left and right to view shot details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${project.shots.size} shots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
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
            onClick = onGenerateVideo,
            enabled = project.videoState != VideoTaskState.GENERATING,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                when (project.videoState) {
                    VideoTaskState.GENERATING -> "Generating..."
                    VideoTaskState.READY -> "Regenerate Video"
                    else -> "Generate Video"
                }
            )
        }
        if (project.videoState == VideoTaskState.GENERATING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AnimatedDots(text = "Video synthesis in progress", modifier = Modifier.padding(top = 4.dp))
        }
        if (project.videoState == VideoTaskState.READY) {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = onPreview
            ) {
                Text("Preview Final Video")
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
            title = "No Storyboard Yet",
            description = "Please generate story content on the Create page first."
        )
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
                Text(
                    text = "AI generated image will be displayed here",
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
                    text = "Transition: ${shot.transition.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                AssistChip(onClick = onDetail, label = { Text("View Shot Details") })
            }
        }
    }
}

@Composable
private fun ShotStatusBadge(status: ShotStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ShotStatus.NOT_GENERATED -> "Not Generated" to Color.Gray
        ShotStatus.GENERATING -> "Generating" to MaterialTheme.colorScheme.tertiary
        ShotStatus.READY -> "Generated" to MaterialTheme.colorScheme.primary
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
            Text(text = "No Shots Yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "After the story is generated, we will automatically create storyboards for you, or you can click the button below to regenerate them.",
                style = MaterialTheme.typography.bodySmall
            )
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
                    Text(text = "Story Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = project.style.label, style = MaterialTheme.typography.titleSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Completion", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(text = "Ready: $readyCount", style = MaterialTheme.typography.bodySmall)
                Text(text = "Generating: $generatingCount", style = MaterialTheme.typography.bodySmall)
                Text(text = "Total: ${project.shots.size}", style = MaterialTheme.typography.bodySmall)
            }
            VideoStatusBanner(project.videoState)
        }
    }
}

@Composable
private fun VideoStatusBanner(videoState: VideoTaskState) {
    val (label, message, color) = when (videoState) {
        VideoTaskState.GENERATING -> Triple(
            "Generating",
            "Video synthesis in progress, will automatically navigate to preview page when completed.",
            MaterialTheme.colorScheme.tertiary
        )

        VideoTaskState.READY -> Triple(
            "Completed",
            "Latest video is ready, you can preview or export directly.",
            MaterialTheme.colorScheme.primary
        )

        VideoTaskState.ERROR -> Triple(
            "Failed",
            "Synthesis failed, please try again later.",
            MaterialTheme.colorScheme.error
        )

        else -> Triple(
            "Pending",
            "Click the button below to start video synthesis.",
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