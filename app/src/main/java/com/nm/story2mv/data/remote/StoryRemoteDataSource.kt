package com.nm.story2mv.data.remote

import com.nm.story2mv.BuildConfig
import com.nm.story2mv.data.model.ShotStatus
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.data.remote.moshi.MoshiConverter
import com.nm.story2mv.data.remote.SceneDto
import kotlinx.coroutines.delay
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.time.Instant
import java.util.UUID
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import retrofit2.HttpException

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
    val previewAudioUrls: List<String> = emptyList(),
    val taskId: String? = null
)

data class VideoTaskDto(
    val storyId: Long,
    val previewUrl: String,
    val state: VideoTaskState,
    val taskId: String? = null
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
    suspend fun requestVideo(storyId: Long, taskId: String, imageUrl: String): Result<VideoTaskDto>
    suspend fun fetchAssets(query: String?): Result<List<AssetDto>>
}


class RemoteStoryRemoteDataSource(
    private val mainApi: MainApi = NetworkModule.mainApi,
    private val imageBaseUrl: String = BuildConfig.BASE_URL_IMAGE
) : StoryRemoteDataSource {

    override suspend fun createStory(request: CreateStoryRequest): Result<StoryBlueprintDto> = runCatching {
        // 仅调用 pipeline，失败直接抛错，不再回退静态链路
        fetchFromPipeline(request)
    }

    private suspend fun fetchFromPipeline(request: CreateStoryRequest): StoryBlueprintDto {
        // 仅使用 pipeline 接口（异步执行）
        // 第1步：启动 pipeline
        val startResponse = mainApi.startPipeline(
            StartPipelineRequest(
                story = request.synopsis,
                style = request.style.label
            )
        )
        val taskId = startResponse.taskId

        // 第2步：轮询检查任务状态
        val status = pollPipelineStatus(taskId)

        // 第3步：下载故事板文件
        val storyboardFile = status.storyboardFile ?: throw IllegalStateException("No storyboard file available")
        val storyboardResponse = mainApi.downloadPipelineFile(taskId, storyboardFile)
        if (!storyboardResponse.isSuccessful) {
            val body = storyboardResponse.errorBody()?.string().orEmpty()
            throw IllegalStateException("Download storyboard failed: code=${storyboardResponse.code()} body=$body")
        }
        val body = storyboardResponse.body() ?: throw IllegalStateException("Empty storyboard body")
        val storyboard = MoshiConverter.parseStoryboard(body.string())
            ?: throw IllegalStateException("Invalid storyboard json")

        // 使用 pipeline 返回的文件名，拼出可访问的下载 URL
        val imageUrls = status.images.orEmpty().map { ensurePipelineFileUrl(taskId, it) }
        val audioUrls = status.audios.orEmpty().map { ensurePipelineFileUrl(taskId, it) }

        val shots = storyboard.scenes.mapIndexed { index, scene ->
            val thumb = imageUrls.getOrNull(index)
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
            previewUrls = emptyList(),
            previewAudioUrls = audioUrls,
            taskId = taskId
        )
    }

    private fun parseLinks(html: String): List<String> {
        // Be tolerant of absolute/relative links, hyphen/underscore and single/double quotes
        val regex = Regex("""href=['\"](?:https?://[^'\"\\s]+)?/?api/file[-_]test/([^'\"\\s]+)['\"]""", RegexOption.IGNORE_CASE)
        return regex.findAll(html).mapNotNull { it.groupValues.getOrNull(1) }.toList()
    }

    override suspend fun regenerateShot(storyId: Long, shotId: String): Result<ShotBlueprintDto> = runCatching {
        error("再生成镜头尚未接入真实后端接口")
    }

    override suspend fun requestVideo(storyId: Long, taskId: String, imageUrl: String): Result<VideoTaskDto> = runCatching {
        val imageBytes = downloadBytes(imageUrl)
        val filename = imageUrl.substringAfterLast('/').ifBlank { "frame.png" }
        val mediaType = guessImageMediaType(filename)
        val imagePart = MultipartBody.Part.createFormData(
            "image",
            filename,
            imageBytes.toRequestBody(mediaType)
        )
        val taskPart = taskId.toRequestBody("text/plain".toMediaType())

        val startResp = mainApi.startVideo(imagePart, taskPart)
        val videoTaskId = startResp.taskId.ifBlank { taskId }

        val status = pollVideoStatus(videoTaskId)
        val videoFile = status.video ?: throw IllegalStateException("视频文件缺失")
        val videoUrl = ensurePipelineFileUrl(videoTaskId, videoFile)
        VideoTaskDto(
            storyId = storyId,
            previewUrl = videoUrl,
            state = VideoTaskState.READY,
            taskId = videoTaskId
        )
    }

    override suspend fun fetchAssets(query: String?): Result<List<AssetDto>> = runCatching {
        emptyList()
    }

    private suspend fun pollPipelineStatus(taskId: String): PipelineStatusResponse {
        repeat(60) { _ -> // 最多轮询约60秒
            val status = mainApi.getPipelineStatus(taskId)
            when (status.overallStatus.lowercase()) {
                "failed" -> throw IllegalStateException("Pipeline failed: ${status.error ?: "Unknown error"}")
                "completed" -> return status
                else -> { /* 继续轮询 */ }
            }
            delay(1000) // 每1s轮询一次
        }
        throw IllegalStateException("Pipeline timeout after 60 attempts")
    }

    private suspend fun pollVideoStatus(taskId: String): PipelineStatusResponse {
        repeat(120) { _ -> // 最多等待约2分钟
            val status = mainApi.getPipelineStatus(taskId)
            when (status.videoStatus?.lowercase()) {
                "failed" -> throw IllegalStateException("Video generation failed: ${status.error ?: "Unknown error"}")
                "ready" -> return status
            }
            delay(1000)
        }
        throw IllegalStateException("Video generation timeout after 120 attempts")
    }

    private fun buildPrompt(scene: SceneDto): String {
        if (scene.prompt.isEmpty()) return scene.narration
        return scene.prompt.entries.joinToString(separator = "；") { (k, v) -> "$k：$v" }
    }

    private fun parseImageUrls(json: String?, taskId: String): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val trimmed = json.trim()
            val moshi = Moshi.Builder().build()
            val listType = Types.newParameterizedType(List::class.java, String::class.java)
            val urls: List<String>? = if (trimmed.startsWith("[")) {
                moshi.adapter<List<String>>(listType).fromJson(trimmed)
            } else {
                // 尝试解析对象中的 urls/data 字段
                val mapAdapter = moshi.adapter<Map<String, Any>>(Map::class.java)
                val map = mapAdapter.fromJson(trimmed)
                (map?.get("urls") as? List<*>)?.mapNotNull { it as? String }
                    ?: (map?.get("data") as? List<*>)?.mapNotNull { it as? String }
            }
            urls.orEmpty().map { url ->
                if (url.startsWith("http", ignoreCase = true)) url
                else BuildConfig.BASE_URL_STATIC.removeSuffix("/") + "/api/download-list/$taskId/" + url.removePrefix("/")
            }
        }.getOrNull().orEmpty()
    }

    private fun downloadBytes(url: String): ByteArray {
        return URL(url).openStream().use { it.readBytes() }
    }

    private fun guessImageMediaType(filename: String): MediaType =
        when {
            filename.lowercase().endsWith(".png") -> "image/png".toMediaType()
            filename.lowercase().endsWith(".jpg") || filename.lowercase().endsWith(".jpeg") -> "image/jpeg".toMediaType()
            else -> "application/octet-stream".toMediaType()
        }

    private fun enrichHttpError(endpoint: String, throwable: Throwable): Throwable {
        if (throwable is HttpException) {
            val body = throwable.response()?.errorBody()?.string().orEmpty()
            return IllegalStateException("请求失败 $endpoint code=${throwable.code()} body=$body", throwable)
        }
        return throwable
    }

    private fun ensurePipelineFileUrl(taskId: String, filename: String): String {
        if (filename.startsWith("http", ignoreCase = true)) return filename
        val clean = filename.removePrefix("/")
        return BuildConfig.BASE_URL_MAIN.removeSuffix("/") + "/download/$taskId/$clean"
    }

    private fun parseFileLinks(html: String, extensions: List<String>): List<String> = emptyList()

    private fun buildStaticFileUrl(filename: String): String = filename
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

    override suspend fun requestVideo(storyId: Long, taskId: String, imageUrl: String): Result<VideoTaskDto> = runCatching {
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
