package com.nm.story2mv.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.nm.story2mv.data.repository.StoryRepository
import com.nm.story2mv.media.VideoExportManager

object ViewModelFactories {

    fun createStoryFactory(repository: StoryRepository): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { CreateViewModel(repository) }
        }

    fun storyboardFactory(repository: StoryRepository): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                StoryboardViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = repository
                )
            }
        }

    fun shotDetailFactory(repository: StoryRepository): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ShotDetailViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = repository
                )
            }
        }

    fun assetsFactory(repository: StoryRepository): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { AssetsViewModel(repository) }
        }

    fun previewFactory(
        repository: StoryRepository,
        exportManager: VideoExportManager
    ): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                PreviewViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = repository,
                    exportManager = exportManager
                )
            }
        }

    fun tasksFactory(repository: StoryRepository): ViewModelProvider.Factory =
        viewModelFactory {
            initializer { TasksViewModel(repository) }
        }
}
