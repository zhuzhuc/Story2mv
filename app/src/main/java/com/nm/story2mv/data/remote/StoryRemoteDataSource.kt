package com.nm.story2mv.data.remote

import com.nm.story2mv.BuildConfig
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.data.remote.moshi.MoshiConverter
import com.nm.story2mv.data.remote.SceneDto
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
    val shots: List<ShotBlueprintDto>,
    val previewUrl: String? = null,
    val previewUrls: List<String> = emptyList(),
    val previewAudioUrls: List<String> = emptyList()
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

class RemoteStoryRemoteDataSource(
    private val mainApi: MainApi = NetworkModule.mainApi,
    private val staticApi: StaticApi = NetworkModule.staticApi,
    private val imageBaseUrl: String = BuildConfig.BASE_URL_IMAGE
) : StoryRemoteDataSource {

    override suspend fun createStory(request: CreateStoryRequest): Result<StoryBlueprintDto> = runCatching {
        // 尝试downloads-list
        runCatching { fetchFromDownloadList(request) }
            .recoverCatching { fetchFromPipeline(request) }
            .getOrThrow()
    }

    private suspend fun fetchFromPipeline(request: CreateStoryRequest): StoryBlueprintDto {
        val start = mainApi.startPipeline(StartPipelineRequest(story = request.synopsis, style = request.style.label))
        val taskId = start.taskId
        val status = pollStatus(taskId)
        val storyboardFile = status.storyboardFile ?: throw IllegalStateException("Storyboard not ready")
        val storyboardResponse = mainApi.downloadFile(taskId, storyboardFile)
        if (!storyboardResponse.isSuccessful) {
            throw IllegalStateException("Download storyboard failed: ${storyboardResponse.code()}")
        }
        val body = storyboardResponse.body() ?: throw IllegalStateException("Empty storyboard body")
        val storyboard = MoshiConverter.parseStoryboard(body.string())
            ?: throw IllegalStateException("Invalid storyboard json")
        val shots = storyboard.scenes.mapIndexed { index, scene ->
            val thumb = status.images?.getOrNull(index)?.let { "$imageBaseUrl/download/$taskId/$it" }
            ShotBlueprintDto(
                id = UUID.randomUUID().toString(),
                title = scene.sceneTitle,
                prompt = buildPrompt(scene),
                narration = scene.narration,
                thumbnailUrl = thumb,
                transition = TransitionType.entries[index % TransitionType.entries.size],
                status = ShotStatus.READY
            )
        }
        return StoryBlueprintDto(
            storyId = Instant.now().toEpochMilli(),
            title = request.synopsis.take(18).ifBlank { "新故事" },
            synopsis = request.synopsis,
            style = request.style,
            createdAt = Instant.now(),
            shots = shots,
            previewUrl = null,
            previewUrls = emptyList()
        )
    }

    private suspend fun fetchFromDownloadList(request: CreateStoryRequest): StoryBlueprintDto {
        val htmlResp = staticApi.downloadList()
        if (!htmlResp.isSuccessful) throw IllegalStateException("Download-list failed: ${htmlResp.code()}")
        val html = htmlResp.body()?.string() ?: throw IllegalStateException("Empty download-list body")
        val files = parseLinks(html)
        val jsonName = files.firstOrNull { it.endsWith(".json") } ?: "storyboard.json"
        val resp = staticApi.downloadSimple(jsonName)
        if (!resp.isSuccessful) throw IllegalStateException("Download failed: ${resp.code()}")
        val body = resp.body() ?: throw IllegalStateException("Empty body")
        val storyboard = MoshiConverter.parseStoryboard(body.string())
            ?: throw IllegalStateException("Invalid storyboard json")
        val fileBase = BuildConfig.BASE_URL_STATIC.removeSuffix("/") + "/api/file_test/"
        val mp4s = files.filter { it.endsWith(".mp4") }.sorted().map { "$fileBase$it" }
        val wavs = files.filter { it.endsWith(".wav") }.sorted().map { "$fileBase$it" }
        val imageFiles = files.filter { it.endsWith(".png") || it.endsWith(".jpg") }.sorted()
        val shots = storyboard.scenes.mapIndexed { index, scene ->
            val thumbName = imageFiles.getOrNull(index)
            val thumbUrl = thumbName?.let { "$fileBase$it" }
            ShotBlueprintDto(
                id = UUID.randomUUID().toString(),
                title = scene.sceneTitle,
                prompt = buildPrompt(scene),
                narration = scene.narration,
                thumbnailUrl = thumbUrl,
                transition = TransitionType.entries[index % TransitionType.entries.size],
                status = ShotStatus.READY
            )
        }
        return StoryBlueprintDto(
            storyId = Instant.now().toEpochMilli(),
            title = request.synopsis.take(18).ifBlank { "新故事" },
            synopsis = request.synopsis,
            style = request.style,
            createdAt = Instant.now(),
            shots = shots,
            previewUrl = mp4s.firstOrNull(),
            previewUrls = mp4s,
            previewAudioUrls = wavs
        )
    }

    private fun parseLinks(html: String): List<String> {
        val regex = Regex("""href=\"/api/file_test/([^\"]+)\"""")
        return regex.findAll(html).mapNotNull { it.groupValues.getOrNull(1) }.toList()
    }

    override suspend fun regenerateShot(storyId: Long, shotId: String): Result<ShotBlueprintDto> = runCatching {
        delay(1200)
        ShotBlueprintDto(
            id = shotId,
            title = "镜头 ${shotId.takeLast(4)}",
            prompt = "更新后的镜头描述",
            narration = "重新生成的旁白",
            thumbnailUrl = "$imageBaseUrl/download/sample/$shotId.png",
            transition = TransitionType.KEN_BURNS,
            status = ShotStatus.READY
        )
    }

    override suspend fun requestVideo(storyId: Long): Result<VideoTaskDto> = runCatching {
        delay(1200)
        VideoTaskDto(
            storyId = storyId,
            previewUrl = "$imageBaseUrl/download/sample/sample.mp4",
            state = VideoTaskState.READY
        )
    }

    override suspend fun fetchAssets(query: String?): Result<List<AssetDto>> = runCatching {
        emptyList()
    }

    private suspend fun pollStatus(taskId: String): PipelineStatusResponse {
        repeat(15) { attempt ->
            val status = mainApi.getStatus(taskId)
            when (status.status) {
                "failed" -> throw IllegalStateException("Pipeline failed: ${status.error}")
                "storyboard_ready", "completed" -> return status
            }
            delay(800)
        }
        throw IllegalStateException("Pipeline timeout")
    }

    private fun buildPrompt(scene: SceneDto): String {
        if (scene.prompt.isEmpty()) return scene.narration
        return scene.prompt.entries.joinToString(separator = "；") { (k, v) -> "$k：$v" }
    }
}


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
