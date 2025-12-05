package com.nm.story2mv.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.nm.story2mv.data.local.AssetEntity
import com.nm.story2mv.data.local.ShotEntity
import com.nm.story2mv.data.local.StoryDao
import com.nm.story2mv.data.local.StoryDatabase
import com.nm.story2mv.data.local.StoryEntity
import com.nm.story2mv.data.local.StoryWithShots
import com.nm.story2mv.data.local.TaskEntity
import com.nm.story2mv.data.model.AssetItem
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.model.TaskItem
import com.nm.story2mv.data.model.TaskKind
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.data.remote.CreateStoryRequest
import com.nm.story2mv.data.remote.RemoteStoryRemoteDataSource
import com.nm.story2mv.data.remote.StoryRemoteDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

interface StoryRepository {
    val projects: Flow<List<StoryProject>>
    suspend fun ensureSeedData()
    fun observeStory(storyId: Long): Flow<StoryProject?>
    fun observeAssets(query: String?): Flow<List<AssetItem>>
    fun observeTasks(): Flow<List<TaskItem>>
    suspend fun createStory(synopsis: String, style: StoryStyle): Long
    suspend fun regenerateShot(storyId: Long, shotId: String)
    suspend fun updateShotDetails(
        storyId: Long,
        shotId: String,
        prompt: String,
        narration: String,
        transitionType: TransitionType
    )

    suspend fun requestVideo(storyId: Long)
    suspend fun requestVideoForShot(storyId: Long, shotId: String)
    suspend fun requestVideosForAllShots(storyId: Long)
    suspend fun finalizeVideo(storyId: Long, previewUrl: String)

    suspend fun deleteAsset(assetId: Long)
}

class StoryRepositoryImpl(
    private val database: StoryDatabase,
    private val remote: StoryRemoteDataSource = RemoteStoryRemoteDataSource(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : StoryRepository {

    private val dao: StoryDao = database.storyDao()
    private val previewCache: MutableMap<Long, PreviewMedia> = mutableMapOf()

    private data class PreviewMedia(
        val videos: List<String> = emptyList(),
        val audios: List<String> = emptyList()
    )


    override val projects: Flow<List<StoryProject>> =
        dao.observeStories().map { list -> list.map { it.toDomain() } }

    override fun observeStory(storyId: Long): Flow<StoryProject?> =
        dao.observeStory(storyId).map { it?.toDomain() }

    override fun observeAssets(query: String?): Flow<List<AssetItem>> =
        dao.observeAssets(query).map { assets ->
            assets.map {
                AssetItem(
                    id = it.id,
                    title = it.title,
                    style = it.style,
                    thumbnailUrl = it.thumbnailUrl,
                    createdAt = it.createdAt,
                    previewUri = it.previewUri?.let(Uri::parse)
                )
            }
        }

    override fun observeTasks(): Flow<List<TaskItem>> =
        dao.observeTasks().map { list -> list.map { it.toDomain() } }

    override suspend fun ensureSeedData() = withContext(dispatcher) {
        val existing = dao.countStories()
        if (existing == 0) {
            createStory(
                synopsis = "一位孤独的摄影师在雨夜的城市中寻找遗失的记忆。",
                style = StoryStyle.CINEMATIC
            )
        }
    }

    override suspend fun createStory(synopsis: String, style: StoryStyle): Long = withContext(dispatcher) {
        val blueprint = remote.createStory(CreateStoryRequest(synopsis = synopsis, style = style)).getOrThrow()
        val storyId = blueprint.storyId
        val title = blueprint.title
        val createdAt = blueprint.createdAt
        val previewUrl = blueprint.previewUrl
        val previewUrls = blueprint.previewUrls
        val previewAudios = blueprint.previewAudioUrls
        previewCache[storyId] = PreviewMedia(previewUrls, previewAudios)
        val story = StoryEntity(
            id = storyId,
            title = title,
            style = style,
            synopsis = synopsis,
            createdAt = createdAt,
            videoState = if (previewUrl != null) VideoTaskState.READY else VideoTaskState.IDLE,
            previewUrl = previewUrl
        )

        blueprint.taskId?.let { taskId ->
            dao.upsertTask(
                TaskEntity(
                    id = taskId,
                    kind = TaskKind.PIPELINE,
                    status = "completed",
                    message = null,
                    createdAt = createdAt,
                    updatedAt = Instant.now(),
                    storyId = storyId,
                    title = title
                )
            )
        }

        val shots = blueprint.shots.map { dto ->
            ShotEntity(
                id = dto.id,
                storyId = storyId,
                title = dto.title,
                prompt = dto.prompt,
                narration = dto.narration,
                thumbnailUrl = dto.thumbnailUrl,
                status = dto.status,
                transition = dto.transition,
                videoUrl = null,
                audioUrl = null,
                videoStatus = VideoTaskState.IDLE
            )
        }

        database.withTransaction {
            dao.upsertStory(story)
            dao.deleteShotsForStory(storyId)
            dao.upsertShots(shots)
            if (previewUrl != null) {
                val thumb = shots.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl ?: previewUrl
                dao.upsertAsset(
                    AssetEntity(
                        id = storyId,
                        title = title,
                        style = style,
                        thumbnailUrl = thumb,
                        createdAt = createdAt,
                        previewUri = previewUrl,
                        sourceStoryId = storyId
                    )
                )
            }
        }
        storyId
    }

    override suspend fun regenerateShot(storyId: Long, shotId: String) = withContext(dispatcher) {
        val current = dao.getShot(shotId)?.takeIf { it.storyId == storyId } ?: return@withContext
        dao.upsertShot(current.copy(status = ShotStatus.GENERATING))
        try {
            val updated = remote.regenerateShot(storyId, shotId).getOrThrow()
            dao.upsertShot(
                current.copy(
                    prompt = updated.prompt,
                    narration = updated.narration,
                    transition = updated.transition,
                    status = updated.status,
                    thumbnailUrl = updated.thumbnailUrl ?: current.thumbnailUrl
                )
            )
        } catch (e: Exception) {
            dao.upsertShot(current.copy(status = ShotStatus.READY))
            throw e
        }
    }

    override suspend fun updateShotDetails(
        storyId: Long,
        shotId: String,
        prompt: String,
        narration: String,
        transitionType: TransitionType
    ) = withContext(dispatcher) {
        val current = dao.getShot(shotId)?.takeIf { it.storyId == storyId } ?: return@withContext
        val updated = current.copy(
            prompt = prompt,
            narration = narration,
            transition = transitionType
        )
        dao.upsertShot(updated)
    }

    override suspend fun requestVideo(storyId: Long) = withContext(dispatcher) {
        val storyWithShots = dao.observeStory(storyId).firstOrNull() ?: return@withContext
        val story = storyWithShots.story
        val firstShot = storyWithShots.shots.firstOrNull { it.thumbnailUrl != null }
            ?: throw IllegalStateException("没有可用的分镜图片用于生成视频")
        generateShotVideo(story, firstShot)
    }

    override suspend fun requestVideoForShot(storyId: Long, shotId: String) = withContext(dispatcher) {
        val storyWithShots = dao.observeStory(storyId).firstOrNull() ?: return@withContext
        val story = storyWithShots.story
        val shot = storyWithShots.shots.firstOrNull { it.id == shotId }
            ?: throw IllegalStateException("未找到镜头")
        if (shot.thumbnailUrl == null) throw IllegalStateException("该镜头缺少图片，无法生成视频")
        generateShotVideo(story, shot)
    }

    override suspend fun requestVideosForAllShots(storyId: Long) = withContext(dispatcher) {
        val storyWithShots = dao.observeStory(storyId).firstOrNull() ?: return@withContext
        val story = storyWithShots.story
        storyWithShots.shots.forEach { shot ->
            if (shot.thumbnailUrl != null) {
                runCatching { generateShotVideo(story, shot) }
            }
        }
    }

    private suspend fun generateShotVideo(story: StoryEntity, shot: ShotEntity) {
        val taskId = extractTaskIdFromUrl(shot.thumbnailUrl ?: "")
            ?: throw IllegalStateException("无法从图片地址解析任务ID")
        dao.upsertTask(
            TaskEntity(
                id = taskId,
                kind = TaskKind.VIDEO,
                status = "generating",
                message = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                storyId = story.id,
                shotId = shot.id,
                title = shot.title
            )
        )
        dao.updateShotVideo(shot.id, videoUrl = null, videoStatus = VideoTaskState.GENERATING)
        runCatching {
            val video = remote.requestVideo(story.id, taskId, requireNotNull(shot.thumbnailUrl)).getOrThrow()
            dao.updateShotVideo(shot.id, videoUrl = video.previewUrl, videoStatus = VideoTaskState.READY)
            dao.upsertTask(
                TaskEntity(
                    id = video.taskId ?: taskId,
                    kind = TaskKind.VIDEO,
                    status = "ready",
                    message = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    storyId = story.id,
                    shotId = shot.id,
                    title = shot.title,
                    videoUrl = video.previewUrl
                )
            )
            finalizeVideo(story.id, video.previewUrl)
        }.onFailure {
            dao.updateShotVideo(shot.id, videoUrl = null, videoStatus = VideoTaskState.ERROR)
            dao.upsertTask(
                TaskEntity(
                    id = taskId,
                    kind = TaskKind.VIDEO,
                    status = "failed",
                    message = it.message,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    storyId = story.id,
                    shotId = shot.id,
                    title = shot.title
                )
            )
        }
    }

    override suspend fun finalizeVideo(storyId: Long, previewUrl: String) = withContext(dispatcher) {
        val story = dao.getStory(storyId) ?: return@withContext
        dao.upsertStory(
            story.copy(
                videoState = VideoTaskState.READY,
                previewUrl = previewUrl
            )
        )
        dao.upsertAsset(
            AssetEntity(
                id = story.id,
                title = story.title,
                style = story.style,
                thumbnailUrl = previewUrl,
                createdAt = Instant.now(),
                previewUri = previewUrl,
                sourceStoryId = story.id
            )
        )
    }

    override suspend fun deleteAsset(assetId: Long) = withContext(dispatcher) {
        database.storyDao().deleteAsset(assetId)
    }

    private fun StoryWithShots.toDomain(): StoryProject =
        StoryProject(
            id = story.id,
            title = story.title,
            style = story.style,
            synopsis = story.synopsis,
            createdAt = story.createdAt,
            shots = shots.map { it.toDomain() },
            videoState = story.videoState,
            previewUrl = story.previewUrl,
            previewUrls = previewCache[story.id]?.videos.orEmpty(),
            previewAudioUrls = previewCache[story.id]?.audios.orEmpty()
        )

    private fun ShotEntity.toDomain(): Shot =
        Shot(
            id = id,
            storyId = storyId,
            title = title,
            prompt = prompt,
            narration = narration,
            thumbnailUrl = thumbnailUrl,
            status = status,
            transition = transition,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            videoStatus = videoStatus
        )

    private fun extractTaskIdFromUrl(url: String): String? {
        val segments = runCatching { Uri.parse(url).pathSegments }.getOrNull().orEmpty()
        val downloadIndex = segments.indexOf("download")
        return segments.getOrNull(downloadIndex + 1)
    }

    private fun TaskEntity.toDomain(): TaskItem =
        TaskItem(
            id = id,
            kind = kind,
            status = status,
            message = message,
            createdAt = createdAt,
            updatedAt = updatedAt,
            storyId = storyId,
            shotId = shotId,
            title = title,
            videoUrl = videoUrl
        )
}
