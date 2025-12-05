package com.nm.story2mv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.model.TaskKind
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.model.VideoTaskState
import java.time.Instant

@Database(
    entities = [StoryEntity::class, ShotEntity::class, AssetEntity::class, TaskEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(StoryTypeConverters::class)
abstract class StoryDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    companion object {
        fun build(context: Context): StoryDatabase =
            Room.databaseBuilder(context.applicationContext, StoryDatabase::class.java, "story_board.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

class StoryTypeConverters {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromStoryStyle(value: StoryStyle?): String? = value?.name

    @TypeConverter
    fun toStoryStyle(value: String?): StoryStyle? = value?.let { StoryStyle.valueOf(it) }

    @TypeConverter
    fun fromShotStatus(value: ShotStatus?): String? = value?.name

    @TypeConverter
    fun toShotStatus(value: String?): ShotStatus? = value?.let { ShotStatus.valueOf(it) }

    @TypeConverter
    fun fromTransitionType(value: TransitionType?): String? = value?.name

    @TypeConverter
    fun toTransitionType(value: String?): TransitionType? = value?.let { TransitionType.valueOf(it) }

    @TypeConverter
    fun fromVideoTaskState(value: VideoTaskState?): String? = value?.name

    @TypeConverter
    fun toVideoTaskState(value: String?): VideoTaskState? = value?.let { VideoTaskState.valueOf(it) }

    @TypeConverter
    fun fromTaskKind(value: TaskKind?): String? = value?.name

    @TypeConverter
    fun toTaskKind(value: String?): TaskKind? = value?.let { TaskKind.valueOf(it) }
}
