package com.nm.story2mv.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.model.TaskKind
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.model.VideoTaskState
import java.time.Instant

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val style: StoryStyle,
    val synopsis: String,
    val createdAt: Instant,
    val videoState: VideoTaskState,
    val previewUrl: String?
)

@Entity(tableName = "shots", primaryKeys = ["id"])
data class ShotEntity(
    val id: String,
    val storyId: Long,
    val title: String,
    val prompt: String,
    val narration: String,
    val thumbnailUrl: String?,
    val status: ShotStatus,
    val transition: TransitionType,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val videoStatus: VideoTaskState = VideoTaskState.IDLE
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
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

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val style: StoryStyle,
    val thumbnailUrl: String?,
    val createdAt: Instant,
    val previewUri: String?,
    val sourceStoryId: Long?
)

data class StoryWithShots(
    @Embedded val story: StoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "storyId",
    )
    val shots: List<ShotEntity>
)
