package com.nm.story2mv.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface VideoExportResult {
    data class Success(val outputUri: Uri) : VideoExportResult
    data class Failure(val error: String) : VideoExportResult
}

class VideoExportManager(private val context: Context) {

    suspend fun exportToGallery(
        videoUri: Uri,
        title: String
    ): VideoExportResult = withContext(Dispatchers.IO) {
        var inputFile: File? = null
        var outputFile: File? = null
        try {
            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            inputFile = File(cacheDir, "input_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                inputFile!!.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext VideoExportResult.Failure("无法读取视频源")

            outputFile = File(cacheDir, "output_${System.currentTimeMillis()}.mp4")
            val command = "-y -i ${inputFile.absolutePath} -c copy ${outputFile.absolutePath}"
            val session = FFmpegKit.execute(command)

            if (!ReturnCode.isSuccess(session.returnCode)) {
                return@withContext VideoExportResult.Failure("FFmpeg 转码失败: ${session.failStackTrace}")
            }

            val collection =
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$title.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            }

            val resolver = context.contentResolver
            val outputUri = resolver.insert(collection, values)
                ?: return@withContext VideoExportResult.Failure("无法写入 MediaStore")

            resolver.openOutputStream(outputUri)?.use { output ->
                outputFile.inputStream().use { input -> input.copyTo(output) }
            }
            VideoExportResult.Success(outputUri)
        } catch (throwable: Throwable) {
            VideoExportResult.Failure(throwable.message ?: "未知错误")
        } finally {
            inputFile?.delete()
            outputFile?.delete()
        }
    }
}

