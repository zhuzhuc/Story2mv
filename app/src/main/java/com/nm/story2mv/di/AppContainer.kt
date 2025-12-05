package com.nm.story2mv.di

import android.content.Context
import com.nm.story2mv.data.local.StoryDatabase
import com.nm.story2mv.data.remote.RemoteStoryRemoteDataSource
import com.nm.story2mv.data.repository.StoryRepository
import com.nm.story2mv.data.repository.StoryRepositoryImpl
import com.nm.story2mv.media.VideoExportManager

class AppContainer(context: Context) {

    private val database: StoryDatabase = StoryDatabase.build(context)
    private val remoteDataSource = RemoteStoryRemoteDataSource()

    val storyRepository: StoryRepository = StoryRepositoryImpl(
        database = database,
        remote = remoteDataSource
    )
    val videoExportManager: VideoExportManager = VideoExportManager(context)
}
