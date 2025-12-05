package com.nm.story2mv.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nm.story2mv.BuildConfig
import com.nm.story2mv.data.repository.StoryRepository
import com.nm.story2mv.data.remote.NetworkModule
import com.nm.story2mv.media.ExportDestination
import com.nm.story2mv.media.VideoExportManager
import com.nm.story2mv.media.VideoExportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Response
import java.util.regex.Pattern

data class PreviewUiState(
    val title: String = "",
    val videoUri: Uri? = null,
    val playlist: List<Uri> = emptyList(),
    val playlistLabels: List<String> = emptyList(),
    val segments: List<PreviewSegment> = emptyList(),
    val currentIndex: Int = 0,
    val audioUri: Uri? = null,
    val audioPlaylist: List<Uri> = emptyList(),
    val isExporting: Boolean = false,
    val exportingDestination: ExportDestination? = null,
    val exportResult: VideoExportResult? = null,
    val toastMessage: String? = null
)

data class PreviewSegment(
    val label: String,
    val uri: Uri?,
    val ready: Boolean
)

class PreviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: StoryRepository,
    private val exportManager: VideoExportManager
) : ViewModel() {

    private val storyId: Long? = savedStateHandle.get<Long>("storyId")?.takeIf { it != -1L }
    private val previewUriArg: String? = savedStateHandle.get<String>("previewUri")?.takeIf { it.isNotBlank() }
    private val previewTitleArg: String? = savedStateHandle.get<String>("previewTitle")
    private val staticApi = NetworkModule.staticApi

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState

    init {
        if (storyId != null) {
            viewModelScope.launch {
            repository.observeStory(storyId).collect { project ->
                val segments = project?.shots.orEmpty().map { shot ->
                    val uri = shot.videoUrl?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    PreviewSegment(
                        label = shot.title,
                        uri = uri,
                        ready = uri != null
                    )
                }
                val playlist = segments.mapNotNull { it.uri }
                    .ifEmpty { project?.previewUrls?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.orEmpty() }
                val labels = segments.map { it.label }
                val audioList = project?.previewAudioUrls?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.orEmpty()
                viewModelScope.launch { prepareAudio(audioList) }
                _uiState.update {
                    it.copy(
                        title = project?.title.orEmpty(),
                        videoUri = playlist.firstOrNull() ?: project?.previewUrl?.let(Uri::parse),
                        playlist = if (playlist.isEmpty()) playlistFromArgs() else playlist,
                        playlistLabels = if (playlist.isEmpty()) emptyList() else labels,
                        segments = segments,
                        currentIndex = 0,
                        audioPlaylist = audioList
                    )
                }
            }
            }
        } else {
            _uiState.update {
                it.copy(
                    title = previewTitleArg ?: "预览",
                    videoUri = previewUriArg?.let(Uri::parse),
                    playlist = playlistFromArgs(),
                    playlistLabels = if (previewUriArg != null) listOf("视频") else emptyList(),
                    segments = emptyList(),
                    currentIndex = 0
                )
            }
        }
    }

    fun exportVideo() {
        startExport(ExportDestination.GALLERY)
    }

    fun saveVideo() {
        startExport(ExportDestination.DOWNLOADS)
    }

    fun consumeExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    fun playAt(index: Int) {
        val seg = _uiState.value.segments.getOrNull(index) ?: return
        val audio = _uiState.value.audioPlaylist.getOrNull(index) ?: _uiState.value.audioPlaylist.firstOrNull()
        _uiState.update { it.copy(videoUri = seg.uri, currentIndex = index, audioUri = audio) }
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun playlistFromArgs(): List<Uri> =
        previewUriArg?.let { uri -> listOfNotNull(runCatching { Uri.parse(uri) }.getOrNull()) } ?: emptyList()

    private fun currentSources(): List<Uri> =
        _uiState.value.playlist.takeIf { it.isNotEmpty() }
            ?: _uiState.value.videoUri?.let { listOf(it) }.orEmpty()

    private fun startExport(destination: ExportDestination) {
        if (_uiState.value.isExporting) return
        val sources = currentSources()
        if (sources.isEmpty()) {
            _uiState.update { it.copy(exportResult = VideoExportResult.Failure("没有可导出的片段")) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportingDestination = destination,
                    exportResult = null
                )
            }
            val safeTitle = _uiState.value.title.ifBlank { "storyboard" }
            val result = exportManager.exportPlaylist(
                videoUris = sources,
                title = safeTitle,
                destination = destination,
                audioUris = _uiState.value.audioPlaylist
            )
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportingDestination = null,
                    exportResult = result,
                    toastMessage = when (result) {
                        is VideoExportResult.Success -> {
                            if (result.destination == ExportDestination.GALLERY) "已导出到相册"
                            else "已保存到下载"
                        }
                        is VideoExportResult.Failure -> result.error
                    }
                )
            }
        }
    }

    private suspend fun fetchPlaylistFromStatic() {
        // download-list 已移除，不再从静态服务器拉取 demo
    }

    private suspend fun prepareAudio(audios: List<Uri>) {
        if (audios.isEmpty()) return
        val merged = exportManager.mergeAudioPlaylist(audios)
        _uiState.update {
            it.copy(
                audioPlaylist = audios,
                audioUri = merged ?: audios.firstOrNull()
            )
        }
    }

    private fun parseLinks(html: String, ext: String): List<String> = emptyList()
}
