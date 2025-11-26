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
    val status: String,
    val story: String? = null,
    val style: String? = null,
    @Json(name = "storyboard_file") val storyboardFile: String? = null,
    val images: List<String>? = null,
    val error: String? = null
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
