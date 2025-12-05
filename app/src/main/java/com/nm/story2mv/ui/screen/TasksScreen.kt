package com.nm.story2mv.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nm.story2mv.data.model.TaskItem
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.ui.viewmodel.TasksUiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(
    state: TasksUiState,
    onTaskClick: (TaskItem) -> Unit = {},
    onStoryClick: (Long) -> Unit = {},
    onShotClick: (Long, String) -> Unit = { _, _ -> }
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (state.stories.isNotEmpty()) {
            item {
                Text(
                    text = "故事与镜头",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            items(state.stories) { story ->
                StorySummary(
                    story = story,
                    onStoryClick = onStoryClick,
                    onShotClick = onShotClick
                )
            }
        }
        if (state.tasks.isNotEmpty()) {
            item {
                Text(
                    text = "任务记录",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
        items(state.tasks) { task ->
            TaskRow(task, onClick = { onTaskClick(task) })
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = task.title ?: task.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = "类型: ${task.kind}  状态: ${task.status}", style = MaterialTheme.typography.bodyMedium)
            task.message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            val time = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(task.updatedAt)
            Text(text = "更新: $time", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StorySummary(
    story: StoryProject,
    onStoryClick: (Long) -> Unit,
    onShotClick: (Long, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { onStoryClick(story.id) }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = story.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "镜头: ${story.shots.size} · 已生成视频: ${story.shots.count { it.videoUrl != null }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            story.shots.forEach { shot ->
                val videoReady = if (shot.videoUrl != null) "视频已生成" else "视频未生成"
                val imageReady = if (shot.thumbnailUrl != null) "画面已生成" else "画面未生成"
                Text(
                    text = "• ${shot.title}｜$videoReady｜$imageReady",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable { onShotClick(story.id, shot.id) }
                )
            }
        }
    }
}
