package com.nm.story2mv.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryStyle
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
    val transition: TransitionType
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

