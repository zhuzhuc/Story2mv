package com.nm.story2mv.data.model

import android.net.Uri
import java.time.Instant
import java.util.UUID

enum class StoryStyle(val label: String) {
    CINEMATIC("Movie"),
    ANIMATION("Animation"),
    REALISTIC("Realistic");

    companion object {
        fun fromLabel(label: String): StoryStyle =
            entries.firstOrNull { it.label == label } ?: CINEMATIC
    }
}

enum class ShotStatus {
    NOT_GENERATED,
    GENERATING,
    READY
}

enum class TransitionType(val label: String) {
    KEN_BURNS("Ken Burns"),
    CROSSFADE("Crossfade"),
    VOLUME_MIX("Volume Mix")
}

enum class VideoTaskState {
    IDLE,
    GENERATING,
    READY,
    ERROR
}
enum class TaskKind {
    PIPELINE,
    VIDEO
}

data class Shot(
    val id: String = UUID.randomUUID().toString(),
    val storyId: Long,
    val title: String,
    val prompt: String,
    val narration: String,
    val thumbnailUrl: String? = null,
    val status: ShotStatus = ShotStatus.NOT_GENERATED,
    val transition: TransitionType = TransitionType.CROSSFADE,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val videoStatus: VideoTaskState = VideoTaskState.IDLE
)

data class TaskItem(
    val id: String,
    val kind: TaskKind,
    val status: String,
    val message: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val storyId: Long? = null,
    val shotId: String? = null,
    val title: String? = null,
    val videoUrl: String? = null
)

data class StoryProject(
    val id: Long,
    val title: String,
    val style: StoryStyle,
    val synopsis: String,
    val createdAt: Instant,
    val shots: List<Shot> = emptyList(),
    val videoState: VideoTaskState = VideoTaskState.IDLE,
    val previewUrl: String? = null,
    val previewUrls: List<String> = emptyList(),
    val previewAudioUrls: List<String> = emptyList()
)

data class AssetItem(
    val id: Long,
    val title: String,
    val style: StoryStyle,
    val thumbnailUrl: String?,
    val createdAt: Instant,
    val previewUri: Uri?
)
