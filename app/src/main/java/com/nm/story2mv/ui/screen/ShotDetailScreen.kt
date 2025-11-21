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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    val status = state.shot?.status ?: ShotStatus.NOT_GENERATED
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShotDetailHeader(shot = state.shot, onBack = onBack)
        PreviewPlaceholder(status = status, isRegenerating = state.isRegenerating)
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
                        state.isRegenerating -> "Regenerating shot image"
                        state.shot?.status == ShotStatus.READY -> "Shot material is ready, you can adjust the prompt and regenerate anytime"
                        state.shot?.status == ShotStatus.GENERATING -> "AI is generating the shot image"
                        else -> "Complete the prompt and click the button below to generate"
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
                        .fillMaxWidth()
                        .height(160.dp),
                    label = { Text("Visual Prompt") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    supportingText = { Text(text = "Describe visual details, style, atmosphere, etc.") }
                )

                OutlinedTextField(
                    value = state.narrationInput,
                    onValueChange = onNarrationChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    label = { Text("Narration") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                TransitionSelector(
                    selected = state.transition,
                    onSelected = onTransitionChanged
                )
            }
        }

        if (state.isRegenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            AnimatedDots(text = "Regenerating shot", modifier = Modifier.padding(start = 4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSave,
                enabled = state.shot != null,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text("Save Changes")
            }
            OutlinedButton(
                enabled = !state.isRegenerating && state.shot != null,
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
                Text("Generate Shot Image")
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
                contentDescription = "Back"
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = shot?.title ?: "Shot Details", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Story ID: ${shot?.storyId ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        ShotStatusChip(status = shot?.status ?: ShotStatus.NOT_GENERATED)
    }
}

@Composable
private fun ShotQuickInfoRow(shot: Shot?, transition: TransitionType) {
    if (shot == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShotInfoCard(
            modifier = Modifier.weight(1f),
            title = "Transition",
            value = transition.label
        )
        ShotInfoCard(
            modifier = Modifier.weight(1f),
            title = "Prompt Length",
            value = "${shot.prompt.length}"
        )
        ShotInfoCard(
            modifier = Modifier.weight(1f),
            title = "Narration Length",
            value = "${shot.narration.length}"
        )
    }
}

@Composable
private fun ShotInfoCard(modifier: Modifier = Modifier, title: String, value: String) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TransitionSelector(
    selected: TransitionType,
    onSelected: (TransitionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Video Transition Effect", style = MaterialTheme.typography.titleMedium)
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
        ShotStatus.NOT_GENERATED -> "Not Generated" to MaterialTheme.colorScheme.outline
        ShotStatus.GENERATING -> "Generating" to MaterialTheme.colorScheme.tertiary
        ShotStatus.READY -> "Ready" to MaterialTheme.colorScheme.primary
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
private fun PreviewPlaceholder(status: ShotStatus, isRegenerating: Boolean) {
    val previewHint = when {
        isRegenerating -> "Regenerating shot..."
        status == ShotStatus.GENERATING -> "AI is generating the shot image"
        status == ShotStatus.READY -> "Image generated, can be updated anytime"
        else -> "Preview will be shown after generation"
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
                Text(text = "Shot Preview", style = MaterialTheme.typography.titleMedium)
                ShotStatusChip(status = status)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                            )
                        ),
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRegenerating || status == ShotStatus.GENERATING) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    }
                    Text(
                        text = previewHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ShotGenerationTimeline(status: ShotStatus) {
    val steps = listOf(
        ShotStatus.NOT_GENERATED to "Not Generated",
        ShotStatus.GENERATING to "Generating",
        ShotStatus.READY to "Ready"
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