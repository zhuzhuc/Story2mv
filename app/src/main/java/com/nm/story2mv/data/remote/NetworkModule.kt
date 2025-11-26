package com.nm.story2mv.data.remote

import com.nm.story2mv.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val TIMEOUT_SECONDS = 20L

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val retrofitMain: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL_MAIN)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(okHttpClient)
        .build()

    private val retrofitStatic: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL_STATIC)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(okHttpClient)
        .build()

    val mainApi: MainApi = retrofitMain.create(MainApi::class.java)
    val staticApi: StaticApi = retrofitStatic.create(StaticApi::class.java)
}
