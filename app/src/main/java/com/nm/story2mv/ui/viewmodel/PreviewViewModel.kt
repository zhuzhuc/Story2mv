package com.nm.story2mv.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val currentIndex: Int = 0,
    val audioUri: Uri? = null,
    val audioPlaylist: List<Uri> = emptyList(),
    val isExporting: Boolean = false,
    val exportingDestination: ExportDestination? = null,
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
    private val staticApi = NetworkModule.staticApi

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState

    init {
        if (storyId != null) {
            viewModelScope.launch {
                repository.observeStory(storyId).collect { project ->
                    val playlist = project?.previewUrls?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.orEmpty()
                    val audioList = project?.previewAudioUrls?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.orEmpty()
                    val labels = playlist.mapIndexed { index, _ -> "视频${index + 1}" }
                    _uiState.update {
                        it.copy(
                            title = project?.title.orEmpty(),
                            videoUri = playlist.firstOrNull() ?: project?.previewUrl?.let(Uri::parse),
                            playlist = if (playlist.isEmpty()) playlistFromArgs() else playlist,
                            playlistLabels = if (playlist.isEmpty()) emptyList() else labels,
                            currentIndex = 0,
                            audioPlaylist = audioList,
                            audioUri = audioList.firstOrNull()
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
                    currentIndex = 0
                )
            }
        }
        viewModelScope.launch { fetchPlaylistFromStatic() }
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
        val list = _uiState.value.playlist
        if (index in list.indices) {
            val audio = _uiState.value.audioPlaylist.getOrNull(index) ?: _uiState.value.audioPlaylist.firstOrNull()
            _uiState.update { it.copy(videoUri = list[index], currentIndex = index, audioUri = audio) }
        }
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
                destination = destination
            )
            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportingDestination = null,
                    exportResult = result
                )
            }
        }
    }

    private suspend fun fetchPlaylistFromStatic() {
        runCatching {
            val resp: Response<ResponseBody> = staticApi.downloadList()
            if (!resp.isSuccessful) return
            val html = resp.body()?.string() ?: return
            val mp4s = parseLinks(html, ".mp4")
            if (mp4s.isEmpty()) return
            val wavs = parseLinks(html, ".wav")
            _uiState.update { state ->
                val uris = mp4s.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                val audios = wavs.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                val labels = mp4s.mapIndexed { index, _ -> "视频${index + 1}" }
                state.copy(
                    playlist = uris,
                    videoUri = state.videoUri ?: uris.firstOrNull(),
                    playlistLabels = labels,
                    currentIndex = if (uris.isNotEmpty()) 0 else state.currentIndex,
                    audioPlaylist = audios,
                    audioUri = audios.firstOrNull()
                )
            }
        }
    }

    private fun parseLinks(html: String, ext: String): List<String> {
        val pattern = Pattern.compile("""href=\"/api/file_test/([^\"]+\\$ext)\"""")
        val matcher = pattern.matcher(html)
        val result = mutableListOf<String>()
        while (matcher.find()) {
            val name = matcher.group(1)
            result.add("http://42.121.99.17/api/file_test/$name")
        }
        return result.sorted()
    }
}
