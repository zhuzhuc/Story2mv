package com.nm.story2mv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nm.story2mv.data.model.TaskItem
import com.nm.story2mv.data.model.StoryProject
import com.nm.story2mv.data.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview

data class TasksUiState(
    val tasks: List<TaskItem> = emptyList(),
    val stories: List<StoryProject> = emptyList()
)

class TasksViewModel(
    repository: StoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeTasks().collectLatest { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
            }
        }
        viewModelScope.launch {
            repository.projects.collectLatest { stories ->
                _uiState.update { it.copy(stories = stories) }
            }
        }
    }
}
