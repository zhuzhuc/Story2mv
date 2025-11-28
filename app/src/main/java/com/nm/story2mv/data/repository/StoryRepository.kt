package com.nm.story2mv.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.nm.story2mv.data.local.AssetEntity
import com.nm.story2mv.data.local.ShotEntity
import com.nm.story2mv.data.local.StoryDao
import com.nm.story2mv.data.local.StoryDatabase
import com.nm.story2mv.data.local.StoryEntity
import com.nm.story2mv.data.local.StoryWithShots
import com.nm.story2mv.data.model.AssetItem
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.data.remote.CreateStoryRequest
import com.nm.story2mv.data.remote.RemoteStoryRemoteDataSource
import com.nm.story2mv.data.remote.StoryRemoteDataSource
import com.nm.story2mv.data.remote.StoryboardFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

interface StoryRepository {
    val projects: Flow<List<StoryProject>>
    suspend fun ensureSeedData()
    fun observeStory(storyId: Long): Flow<StoryProject?>
    fun observeAssets(query: String?): Flow<List<AssetItem>>
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
        val remoteResult = remote.createStory(CreateStoryRequest(synopsis = synopsis, style = style))
        val blueprint = remoteResult.getOrNull()
        val storyId = blueprint?.storyId ?: Instant.now().toEpochMilli()
        val title = blueprint?.title ?: synopsis.take(18).ifBlank { "新故事" }
        val createdAt = blueprint?.createdAt ?: Instant.now()
        val previewUrl = blueprint?.previewUrl
        val previewUrls = blueprint?.previewUrls.orEmpty()
        val previewAudios = blueprint?.previewAudioUrls.orEmpty()
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

        val shots = blueprint?.shots?.map { dto ->
            ShotEntity(
                id = dto.id,
                storyId = storyId,
                title = dto.title,
                prompt = dto.prompt,
                narration = dto.narration,
                thumbnailUrl = dto.thumbnailUrl,
                status = dto.status,
                transition = dto.transition
            )
        } ?: generateShotTemplates(storyId, synopsis, style)

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
        val updated = remote.regenerateShot(storyId, shotId).getOrNull()
        if (updated != null) {
            dao.upsertShot(
                current.copy(
                    prompt = updated.prompt,
                    narration = updated.narration,
                    transition = updated.transition,
                    status = updated.status,
                    thumbnailUrl = updated.thumbnailUrl ?: current.thumbnailUrl
                )
            )
        } else {
            delay(1200)
            dao.upsertShot(
                current.copy(
                    status = ShotStatus.READY,
                    thumbnailUrl = "https://picsum.photos/seed/${UUID.randomUUID()}/640/360"
                )
            )
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
        val story = dao.getStory(storyId) ?: return@withContext
        dao.upsertStory(story.copy(videoState = VideoTaskState.GENERATING))
        val start = System.currentTimeMillis()
        val video = remote.requestVideo(storyId).getOrNull()
        val elapsed = System.currentTimeMillis() - start
        val minDurationMs = 2500L
        if (elapsed < minDurationMs) {
            delay(minDurationMs - elapsed)
        }
        finalizeVideo(storyId, video?.previewUrl ?: DEMO_VIDEO_URL)
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

    companion object {
        private const val DEMO_VIDEO_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
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
            transition = transition
        )

    private fun generateShotTemplates(
        storyId: Long,
        synopsis: String,
        style: StoryStyle
    ): List<ShotEntity> {
        val scenes = listOf("开场", "冲突", "转折", "结局")
        return scenes.mapIndexed { index, label ->
            ShotEntity(
                id = UUID.randomUUID().toString(),
                storyId = storyId,
                title = "$label · ${style.label}",
                prompt = "场景$label: $synopsis",
                narration = "旁白$label：$synopsis",
                thumbnailUrl = "https://picsum.photos/seed/${storyId + index}/600/360",
                status = ShotStatus.READY,
                transition = TransitionType.entries[index % TransitionType.entries.size]
            )
        }
    }
}
