package com.nm.story2mv.data.remote

import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.model.VideoTaskState
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID

data class CreateStoryRequest(
    val synopsis: String,
    val style: StoryStyle
)

data class ShotBlueprintDto(
    val id: String,
    val title: String,
    val prompt: String,
    val narration: String,
    val thumbnailUrl: String?,
    val transition: TransitionType,
    val status: ShotStatus
)

data class StoryBlueprintDto(
    val storyId: Long,
    val title: String,
    val synopsis: String,
    val style: StoryStyle,
    val createdAt: Instant,
    val shots: List<ShotBlueprintDto>
)

data class VideoTaskDto(
    val storyId: Long,
    val previewUrl: String,
    val state: VideoTaskState
)

data class AssetDto(
    val id: Long,
    val title: String,
    val style: StoryStyle,
    val thumbnailUrl: String?,
    val previewUrl: String?,
    val createdAt: Instant
)

interface StoryRemoteDataSource {
    suspend fun createStory(request: CreateStoryRequest): Result<StoryBlueprintDto>
    suspend fun regenerateShot(storyId: Long, shotId: String): Result<ShotBlueprintDto>
    suspend fun requestVideo(storyId: Long): Result<VideoTaskDto>
    suspend fun fetchAssets(query: String?): Result<List<AssetDto>>
}

/**
 * 当前项目尚未接入真实后端，提供一个耗时模拟的数据源，便于对接 Compose 层与仓库层。
 */
class StubStoryRemoteDataSource : StoryRemoteDataSource {

    override suspend fun createStory(request: CreateStoryRequest): Result<StoryBlueprintDto> = runCatching {
        delay(1200)
        StoryBlueprintDto(
            storyId = Instant.now().toEpochMilli(),
            title = request.synopsis.take(18).ifBlank { "新故事" },
            synopsis = request.synopsis,
            style = request.style,
            createdAt = Instant.now(),
            shots = buildScenes(request.synopsis, request.style)
        )
    }

    override suspend fun regenerateShot(storyId: Long, shotId: String): Result<ShotBlueprintDto> = runCatching {
        delay(1800)
        ShotBlueprintDto(
            id = shotId,
            title = "镜头 ${shotId.takeLast(4)}",
            prompt = "更新后的镜头描述",
            narration = "重新生成的旁白",
            thumbnailUrl = "https://picsum.photos/seed/${UUID.randomUUID()}/640/360",
            transition = TransitionType.KEN_BURNS,
            status = ShotStatus.READY
        )
    }

    override suspend fun requestVideo(storyId: Long): Result<VideoTaskDto> = runCatching {
        delay(2500)
        VideoTaskDto(
            storyId = storyId,
            previewUrl = DEMO_VIDEO_URL,
            state = VideoTaskState.READY
        )
    }

    override suspend fun fetchAssets(query: String?): Result<List<AssetDto>> = runCatching {
        delay(600)
        val now = Instant.now()
        listOf(
            AssetDto(
                id = now.toEpochMilli(),
                title = "雨夜回忆",
                style = StoryStyle.CINEMATIC,
                thumbnailUrl = DEMO_VIDEO_URL,
                previewUrl = DEMO_VIDEO_URL,
                createdAt = now
            ),
            AssetDto(
                id = now.minusSeconds(86400).toEpochMilli(),
                title = "晨曦之城",
                style = StoryStyle.REALISTIC,
                thumbnailUrl = DEMO_VIDEO_URL,
                previewUrl = DEMO_VIDEO_URL,
                createdAt = now.minusSeconds(86400)
            )
        ).filter { query.isNullOrBlank() || it.title.contains(query, ignoreCase = true) }
    }

    private fun buildScenes(synopsis: String, style: StoryStyle): List<ShotBlueprintDto> {
        val scenes = listOf("开场", "冲突", "转折", "结局")
        return scenes.mapIndexed { index, label ->
            ShotBlueprintDto(
                id = UUID.randomUUID().toString(),
                title = "$label · ${style.label}",
                prompt = "场景$label: $synopsis",
                narration = "旁白$label：$synopsis",
                thumbnailUrl = "https://picsum.photos/seed/${synopsis.hashCode() + index}/640/360",
                transition = TransitionType.entries[index % TransitionType.entries.size],
                status = ShotStatus.READY
            )
        }
    }

    companion object {
        private const val DEMO_VIDEO_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }
}
