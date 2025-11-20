package com.nm.story2mv.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nm.story2mv.data.repository.StoryRepository
import com.nm.story2mv.media.VideoExportManager
import com.nm.story2mv.media.VideoExportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewUiState(
    val title: String = "",
    val videoUri: Uri? = null,
    val isExporting: Boolean = false,
    val exportResult: VideoExportResult? = null
)

class PreviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: StoryRepository,
    private val exportManager: VideoExportManager
) : ViewModel() {

    private val storyId: Long? = savedStateHandle.get<Long>("storyId")?.takeIf { it != -1L }
    private val previewUriArg: String? = savedStateHandle.get<String>("previewUri")?.takeIf { it.isNotBlank() }
    private val previewTitleArg: String? = savedStateHandle.get<String>("previewTitle")

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState

    init {
        if (storyId != null) {
            viewModelScope.launch {
                repository.observeStory(storyId).collect { project ->
                    _uiState.update {
                        it.copy(
                            title = project?.title.orEmpty(),
                            videoUri = project?.previewUrl?.let(Uri::parse)
                        )
                    }
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    title = previewTitleArg ?: "预览",
                    videoUri = previewUriArg?.let(Uri::parse)
                )
            }
        }
    }

    fun exportVideo() {
        val uri = _uiState.value.videoUri ?: return
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportResult = null) }
            val result = exportManager.exportToGallery(uri, _uiState.value.title.ifBlank { "storyboard" })
            _uiState.update { it.copy(isExporting = false, exportResult = result) }
        }
    }

    fun consumeExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }
}

