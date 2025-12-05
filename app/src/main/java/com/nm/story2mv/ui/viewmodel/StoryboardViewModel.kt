package com.nm.story2mv.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.data.model.VideoTaskState
import com.nm.story2mv.data.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryboardUiState(
    val project: StoryProject? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val autoPreviewStoryId: Long? = null
)

class StoryboardViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: StoryRepository
) : ViewModel() {

    private val storyId: Long = checkNotNull(savedStateHandle.get<Long>("storyId"))

    private val _uiState = MutableStateFlow(StoryboardUiState())
    val uiState: StateFlow<StoryboardUiState> = _uiState.asStateFlow()

    private var lastVideoState: VideoTaskState? = null

    init {
        viewModelScope.launch {
            repository.observeStory(storyId).collectLatest { project ->
                val shouldNavigate =
                    project?.videoState == VideoTaskState.READY &&
                        lastVideoState != VideoTaskState.READY &&
                        project.previewUrl != null
                lastVideoState = project?.videoState
                _uiState.update {
                    it.copy(
                        project = project,
                        isLoading = false,
                        errorMessage = null,
                        autoPreviewStoryId = if (shouldNavigate) project?.id else null
                    )
                }
            }
        }
    }

    fun generateVideo() {
        val project = _uiState.value.project ?: return
        if (project.videoState == VideoTaskState.GENERATING) return
        viewModelScope.launch {
            repository.requestVideo(project.id)
            _uiState.update { it.copy(toastMessage = "已提交合成，稍等几秒完成") }
        }
    }

    fun generateAllVideos() {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            repository.requestVideosForAllShots(project.id)
            _uiState.update { it.copy(toastMessage = "已提交全部镜头视频合成") }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val project = repository.observeStory(storyId).firstOrNull()
            lastVideoState = project?.videoState
            _uiState.update {
                it.copy(
                    project = project,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = if (project == null) "未找到分镜项目" else null
                )
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun consumeAutoPreview() {
        _uiState.update { it.copy(autoPreviewStoryId = null) }
    }
}
