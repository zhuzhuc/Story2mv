package com.nm.story2mv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nm.story2mv.data.model.StoryStyle
import com.nm.story2mv.data.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateUiState(
    val synopsis: String = "",
    val selectedStyle: StoryStyle? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val createdStoryId: Long? = null
)

class CreateViewModel(
    private val repository: StoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState

    fun onSynopsisChanged(value: String) {
        _uiState.update { it.copy(synopsis = value, errorMessage = null) }
    }

    fun onStyleSelected(style: StoryStyle) {
        _uiState.update { it.copy(selectedStyle = style) }
    }

    fun generateStory() {
        val snapshot = _uiState.value
        if (snapshot.isGenerating) return

        if (snapshot.synopsis.isBlank() || snapshot.selectedStyle == null) {
            _uiState.update { it.copy(errorMessage = "ERROR") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, errorMessage = null, createdStoryId = null) }
            runCatching {
                repository.createStory(snapshot.synopsis, snapshot.selectedStyle)
            }.onSuccess { storyId ->
                _uiState.update { it.copy(isGenerating = false, createdStoryId = storyId) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isGenerating = false, errorMessage = throwable.message ?: "生成失败")
                }
            }
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(createdStoryId = null) }
    }
}

