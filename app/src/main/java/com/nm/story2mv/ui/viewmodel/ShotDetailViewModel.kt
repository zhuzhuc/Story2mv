package com.nm.story2mv.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nm.story2mv.data.model.Shot
import com.nm.story2mv.data.model.TransitionType
import com.nm.story2mv.data.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShotDetailUiState(
    val shot: Shot? = null,
    val promptInput: String = "",
    val narrationInput: String = "",
    val transition: TransitionType = TransitionType.CROSSFADE,
    val isRegenerating: Boolean = false,
    val isGeneratingVideo: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val previewTitle: String = ""
)

class ShotDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: StoryRepository
) : ViewModel() {

    private val storyId: Long = checkNotNull(savedStateHandle.get<Long>("storyId"))
    private val shotId: String = checkNotNull(savedStateHandle.get<String>("shotId"))

    private val _uiState = MutableStateFlow(ShotDetailUiState())
    val uiState: StateFlow<ShotDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeStory(storyId)
                .map { project -> project?.shots?.firstOrNull { it.id == shotId } }
                .onStart { _uiState.update { it.copy(isLoading = true, error = null) } }
                .collectLatest { shot ->
                    _uiState.update {
                        if (shot != null) {
                            it.copy(
                                shot = shot,
                                promptInput = shot.prompt,
                                narrationInput = shot.narration,
                                transition = shot.transition,
                                isLoading = false,
                                error = null,
                                previewTitle = shot.title
                            )
                        } else {
                            it.copy(
                                isLoading = false,
                                error = "未找到该镜头，请返回或重试。"
                            )
                        }
                    }
                }
        }
    }

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(promptInput = prompt) }
    }

    fun updateNarration(narration: String) {
        _uiState.update { it.copy(narrationInput = narration) }
    }

    fun updateTransition(transitionType: TransitionType) {
        _uiState.update { it.copy(transition = transitionType) }
    }

    fun saveShot() {
        val state = _uiState.value
        val shot = state.shot ?: return
        viewModelScope.launch {
            repository.updateShotDetails(
                storyId = storyId,
                shotId = shot.id,
                prompt = state.promptInput,
                narration = state.narrationInput,
                transitionType = state.transition
            )
        }
    }

    fun regenerateImage() {
        val shot = _uiState.value.shot ?: return
        if (_uiState.value.isRegenerating) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRegenerating = true, error = null) }
            runCatching {
                repository.regenerateShot(storyId, shot.id)
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message) }
            }.also {
                _uiState.update { it.copy(isRegenerating = false) }
            }
        }
    }

    fun generateVideoForShot() {
        val shot = _uiState.value.shot ?: return
        if (_uiState.value.isGeneratingVideo) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingVideo = true, error = null) }
            runCatching {
                repository.requestVideoForShot(storyId, shot.id)
            }.onFailure { throwable ->
                _uiState.update { it.copy(error = throwable.message) }
            }.also {
                _uiState.update { it.copy(isGeneratingVideo = false) }
            }
        }
    }

    fun reloadShot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val shot = repository.observeStory(storyId)
                .firstOrNull()
                ?.shots
                ?.firstOrNull { it.id == shotId }
            _uiState.update {
                if (shot != null) {
                    it.copy(
                        shot = shot,
                        promptInput = shot.prompt,
                        narrationInput = shot.narration,
                        transition = shot.transition,
                        isLoading = false
                    )
                } else {
                    it.copy(
                        isLoading = false,
                        error = "仍未找到该镜头，请确认故事已存在。"
                    )
                }
            }
        }
    }
}
