package com.nm.story2mv.data.remote

import com.squareup.moshi.Json

data class StartPipelineRequest(
    val story: String,
    val style: String
)

data class StartPipelineResponse(
    @Json(name = "task_id") val taskId: String,
    val status: String
)

data class PipelineStatusResponse(
    @Json(name = "overall_status") val overallStatus: String, // queued, processing, completed, failed
    val story: String? = null,
    val style: String? = null,
    @Json(name = "storyboard_file") val storyboardFile: String? = null,
    val images: List<String>? = null,
    val audios: List<String>? = null,
    @Json(name = "video_status") val videoStatus: String? = null,
    val video: String? = null,
    val error: String? = null
)

data class LLMServerRequest(
    val story: String,
    val style: String
)

data class LLMServerResponse(
    @Json(name = "task_id") val taskId: String,
    val filename: String
)

data class ImageGenRequest(
    @Json(name = "task_id") val taskId: String,
    val storyboard: StoryboardFile
)

data class StartVideoResponse(
    @Json(name = "task_id") val taskId: String,
    val status: String? = null
)

data class StoryboardFile(
    val scenes: List<SceneDto>
)

data class SceneDto(
    @Json(name = "scene_title") val sceneTitle: String,
    val narration: String,
    @Json(name = "bgm_suggestion") val bgmSuggestion: String? = null,
    val prompt: Map<String, String> = emptyMap()
)

enum class TaskOverallStatus(val raw: String) {
    QUEUED("queued"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    companion object {
        fun fromRaw(value: String?): TaskOverallStatus =
            entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: PROCESSING
    }
}
