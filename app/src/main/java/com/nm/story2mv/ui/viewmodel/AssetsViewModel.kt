package com.nm.story2mv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nm.story2mv.data.model.AssetItem
import com.nm.story2mv.data.repository.StoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetsUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val assets: List<AssetItem> = emptyList(),
    val showDeleteDialog: Boolean = false, // 新增：显示删除确认对话框
    val assetToDelete: AssetItem? = null   // 新增：待删除的资产
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AssetsViewModel(
    private val repository: StoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState: StateFlow<AssetsUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState
                .map { it.query }
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query -> repository.observeAssets(query.takeIf { it.isNotBlank() }) }
                .collectLatest { assets ->
                    _uiState.update { it.copy(assets = assets, isLoading = false) }
                }
        }
    }

    fun updateQuery(newValue: String) {
        _uiState.update { it.copy(query = newValue, isLoading = true) }
    }

    // 新增：请求删除资产
    fun requestDeleteAsset(asset: AssetItem) {
        _uiState.update { it.copy(showDeleteDialog = true, assetToDelete = asset) }
    }

    // 新增：确认删除资产
    fun confirmDeleteAsset() {
        val assetToDelete = _uiState.value.assetToDelete
        if (assetToDelete != null) {
            viewModelScope.launch {
                repository.deleteAsset(assetToDelete.id)
                _uiState.update { it.copy(showDeleteDialog = false, assetToDelete = null) }
            }
        }
    }

    // 新增：取消删除
    fun cancelDeleteAsset() {
        _uiState.update { it.copy(showDeleteDialog = false, assetToDelete = null) }
    }
}
