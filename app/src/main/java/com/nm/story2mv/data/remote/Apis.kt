package com.nm.story2mv.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MainApi {
    @POST("start_pipeline/")
    suspend fun startPipeline(@Body body: StartPipelineRequest): StartPipelineResponse

    @GET("status/{taskId}")
    suspend fun getStatus(@Path("taskId") taskId: String): PipelineStatusResponse

    @GET("download/{taskId}/{filename}")
    suspend fun downloadFile(
        @Path("taskId") taskId: String,
        @Path("filename") filename: String
    ): Response<ResponseBody>

    @GET("health")
    suspend fun health(): Map<String, String>

}

interface StaticApi {
    @GET("api/download-list/")
    suspend fun downloadList(): Response<ResponseBody>

    // download-list--- file-test
    @GET("api/file-test/{filename}")
    suspend fun downloadSimple(@Path("filename") filename: String): Response<ResponseBody>

    // 新增：LLM服务器测试接口
    @POST("api/LLM-Server-test/")
    suspend fun llmServerTest(@Body body: LLMServerRequest): LLMServerResponse

    // 新增：下载故事板文件
    @GET("api/download-list/{taskId}/{filename}")
    suspend fun downloadStoryboard(@Path("taskId") taskId: String, @Path("filename") filename: String): Response<ResponseBody>

    // 新增：图片生成服务器测试接口
    @POST("api/ImageGEN-Server-test/")
    suspend fun imageGenServerTest(@Body body: ImageGenRequest): ImageGenResponse
}
