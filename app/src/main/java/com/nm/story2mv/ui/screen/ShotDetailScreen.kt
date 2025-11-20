package com.nm.story2mv.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.ui.screen.components.AnimatedDots
import com.nm.story2mv.ui.screen.components.AnimatedStatusHint
import com.nm.story2mv.ui.viewmodel.ShotDetailUiState

@Composable
fun ShotDetailScreen(
    state: ShotDetailUiState,
    onPromptChanged: (String) -> Unit,
    onNarrationChanged: (String) -> Unit,
    onTransitionChanged: (TransitionType) -> Unit,
    onGenerateImage: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShotDetailHeader(shot = state.shot, onBack = onBack)
        ShotGenerationTimeline(status = state.shot?.status ?: ShotStatus.NOT_GENERATED)
        AnimatedStatusHint(
            text = when (state.shot?.status) {
                ShotStatus.GENERATING -> "AI 正在生成新的镜头画面"
                ShotStatus.READY -> "镜头素材已完成，可随时更新"
                else -> "准备好后点击 Generate Image 开始生成"
            }
        )

        OutlinedTextField(
            value = state.promptInput,
            onValueChange = onPromptChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            label = { Text("Prompt") },
            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
        )

        OutlinedTextField(
            value = state.narrationInput,
            onValueChange = onNarrationChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            label = { Text("Narration / 旁白") }
        )

        TransitionSelector(
            selected = state.transition,
            onSelected = onTransitionChanged
        )

        if (state.isRegenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AnimatedDots(text = "重新生成图像中", modifier = Modifier.padding(start = 4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text("保存设置")
            }
            TextButton(
                enabled = !state.isRegenerating,
                onClick = onGenerateImage,
                modifier = Modifier.weight(1f)
            ) {
                if (state.isRegenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Generate Image")
            }
        }

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ShotDetailHeader(
    shot: Shot?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回"
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = shot?.title ?: "镜头详情", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Story ID: ${shot?.storyId ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        ShotStatusChip(status = shot?.status ?: ShotStatus.NOT_GENERATED)
    }
}

@Composable
private fun TransitionSelector(
    selected: TransitionType,
    onSelected: (TransitionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "视频转场效果", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow {
            TransitionType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == selected,
                    onClick = { onSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, TransitionType.entries.lastIndex)
                ) {
                    Text(type.label)
                }
            }
        }
    }
}

@Composable
private fun ShotStatusChip(status: ShotStatus) {
    val (label, color) = when (status) {
        ShotStatus.NOT_GENERATED -> "未生成" to MaterialTheme.colorScheme.outline
        ShotStatus.GENERATING -> "生成中" to MaterialTheme.colorScheme.tertiary
        ShotStatus.READY -> "已生成" to MaterialTheme.colorScheme.primary
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ShotGenerationTimeline(status: ShotStatus) {
    val steps = listOf(
        ShotStatus.NOT_GENERATED to "未生成",
        ShotStatus.GENERATING to "生成中",
        ShotStatus.READY to "已生成"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEach { (step, label) ->
            val isActive = status.ordinal >= step.ordinal
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val indicatorColor = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = indicatorColor.copy(alpha = 0.2f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "${step.ordinal + 1}", color = indicatorColor)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = indicatorColor)
            }
        }
    }
}
