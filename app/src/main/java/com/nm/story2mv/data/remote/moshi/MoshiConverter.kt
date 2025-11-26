package com.nm.story2mv.data.remote.moshi

import com.nm.story2mv.data.remote.StoryboardFile
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object MoshiConverter {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val storyboardAdapter: JsonAdapter<StoryboardFile> = moshi.adapter(StoryboardFile::class.java)

    fun parseStoryboard(json: String): StoryboardFile? = storyboardAdapter.fromJson(json)
}
