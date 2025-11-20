package com.nm.story2mv.di

import android.content.Context
import com.nm.story2mv.data.local.StoryDatabase
import com.nm.story2mv.data.repository.StoryRepository
import com.nm.story2mv.data.repository.StoryRepositoryImpl
import com.nm.story2mv.media.VideoExportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database: StoryDatabase = StoryDatabase.build(context)

    val storyRepository: StoryRepository = StoryRepositoryImpl(database = database)
    val videoExportManager: VideoExportManager = VideoExportManager(context)

    init {
        applicationScope.launch {
            storyRepository.ensureSeedData()
        }
    }
}

