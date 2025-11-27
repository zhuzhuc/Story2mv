package com.nm.story2mv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.ui.screen.components.AnimatedDots
import com.nm.story2mv.ui.screen.components.AnimatedStatusHint
import com.nm.story2mv.ui.screen.components.EmptyStateCard
import com.nm.story2mv.ui.screen.components.FullScreenLoading
import com.nm.story2mv.ui.viewmodel.ShotDetailUiState

@Composable
fun ShotDetailScreen(
    state: ShotDetailUiState,
    onPromptChanged: (String) -> Unit,
    onNarrationChanged: (String) -> Unit,
    onTransitionChanged: (TransitionType) -> Unit,
    onGenerateImage: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val status = state.shot?.status ?: ShotStatus.NOT_GENERATED
    val scrollState = rememberScrollState()
    val promptLimit = 400
    val narrationLimit = 300
    val promptCountText = "${state.promptInput.length}/$promptLimit"
    val narrationCountText = "${state.narrationInput.length}/$narrationLimit"
    val promptOverLimit = state.promptInput.length > promptLimit
    val narrationOverLimit = state.narrationInput.length > narrationLimit
    val promptCountColor = if (promptOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val narrationCountColor = if (narrationOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    if (state.isLoading) {
        FullScreenLoading(message = "正在加载镜头…")
        return
    }

    if (state.shot == null) {
        MissingShotState(error = state.error, onRetry = onRetry, onBack = onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShotDetailHeader(shot = state.shot, onBack = onBack)
        PreviewPlaceholder(
            status = status,
            isRegenerating = state.isRegenerating,
            thumbnailUrl = state.shot?.thumbnailUrl,
            onGenerateImage = onGenerateImage
        )
        ShotQuickInfoRow(shot = state.shot, transition = state.transition)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShotGenerationTimeline(status = status)
                AnimatedStatusHint(
                    text = when {
                        state.isRegenerating -> "正在重新生成镜头画面"
                        state.shot?.status == ShotStatus.READY -> "镜头已生成，可随时调整提示词重新生成"
                        state.shot?.status == ShotStatus.GENERATING -> "AI 正在生成镜头画面"
                        else -> "完善提示词后点击下方按钮生成画面"
                    }
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = state.promptInput,
                    onValueChange = onPromptChanged,
                    modifier = Modifier
                        .fillMaxWidth(),
                    label = { Text("画面提示词") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (promptOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (promptOverLimit) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                        cursorColor = if (promptOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 5,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "描述画面细节、风格、氛围等。")
                            Text(text = promptCountText, color = promptCountColor)
                        }
                    }
                )

                OutlinedTextField(
                    value = state.narrationInput,
                    onValueChange = onNarrationChanged,
                    modifier = Modifier
                        .fillMaxWidth(),
                    label = { Text("旁白") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (narrationOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (narrationOverLimit) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                        cursorColor = if (narrationOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    minLines = 3,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "旁白用于合成配音或字幕。")
                            Text(text = narrationCountText, color = narrationCountColor)
                        }
                    }
                )

                TransitionSelector(
                    selected = state.transition,
                    onSelected = onTransitionChanged
                )
            }
        }

        if (state.isRegenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AnimatedDots(text = "重新生成镜头", modifier = Modifier.padding(start = 4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = !state.isRegenerating
            ) {
                Text(if (state.isRegenerating) "生成中..." else "保存修改")
            }
            OutlinedButton(
                enabled = !state.isRegenerating,
                onClick = onGenerateImage,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                if (state.isRegenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("生成镜头画面")
            }
        }

        state.error?.let {
            ErrorBanner(message = it, onRetry = onRetry)
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
            Text(text = shot?.title ?: "分镜详情", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Story ID: ${shot?.storyId ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        ShotStatusChip(status = shot?.status ?: ShotStatus.NOT_GENERATED)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ShotQuickInfoRow(shot: Shot?, transition: TransitionType) {
    if (shot == null) return
    val tags = listOf(
        "转场 · ${transition.label}",
        "提示词 ${shot.prompt.length} 字",
        "旁白 ${shot.narration.length} 字",
        "镜头 ID · ${shot.id.takeLast(6)}"
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(tag) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TransitionSelector(
    selected: TransitionType,
    onSelected: (TransitionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "视频转场效果", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "选择镜头之间的衔接方式，效果会应用在生成的视频中。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransitionType.entries.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelected(type) },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
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
private fun MissingShotState(error: String?, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateCard(
            title = "未找到该镜头",
            description = error ?: "镜头数据缺失或已删除。"
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onBack) {
                Text("返回分镜列表")
            }
            Button(modifier = Modifier.weight(1f), onClick = onRetry) {
                Text("重试加载")
            }
        }
    }
}

@Composable
private fun PreviewPlaceholder(
    status: ShotStatus,
    isRegenerating: Boolean,
    thumbnailUrl: String?,
    onGenerateImage: () -> Unit
) {
    val shimmerTransition = rememberInfiniteTransition(label = "previewShimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 500f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400)),
        label = "previewOffset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset(shimmerOffset - 300f, 0f),
        end = Offset(shimmerOffset, 300f)
    )

    val previewHint = when {
        isRegenerating -> "正在重新生成镜头…"
        status == ShotStatus.GENERATING -> "AI 正在生成镜头画面"
        status == ShotStatus.READY -> "画面已生成，可随时更新"
        else -> "生成后将展示镜头预览"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "镜头预览", style = MaterialTheme.typography.titleMedium)
                ShotStatusChip(status = status)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        brush = if (status == ShotStatus.READY && !isRegenerating) {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                                )
                            )
                        } else {
                            shimmerBrush
                        },
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl != null && status == ShotStatus.READY && !isRegenerating) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onGenerateImage,
                            enabled = !isRegenerating,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "重新生成")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRegenerating || status == ShotStatus.GENERATING) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }
                        if (status != ShotStatus.GENERATING && !isRegenerating) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = previewHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isRegenerating && status != ShotStatus.GENERATING) {
                            OutlinedButton(
                                onClick = onGenerateImage
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "生成镜头画面")
                            }
                        }
                    }
                }
            }
        }
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

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.error,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Text("重试")
            }
        }
    }
}
