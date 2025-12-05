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
import java.net.URL
import java.util.Locale

sealed interface VideoExportResult {
    data class Success(val outputUri: Uri, val destination: ExportDestination) : VideoExportResult
    data class Failure(val error: String) : VideoExportResult
}

enum class ExportDestination {
    GALLERY,
    DOWNLOADS
}

class VideoExportManager(private val context: Context) {

    suspend fun exportPlaylist(
        videoUris: List<Uri>,
        title: String,
        destination: ExportDestination,
        audioUris: List<Uri> = emptyList()
    ): VideoExportResult = withContext(Dispatchers.IO) {
        if (videoUris.isEmpty()) return@withContext VideoExportResult.Failure("没有可导出的片段")
        var listFile: File? = null
        var mergedFile: File? = null
        var outputFile: File? = null
        var mergedAudio: File? = null
        try {
            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val tempInputs = videoUris.mapIndexed { index, uri ->
                copyToCache(uri, cacheDir, index)
            }
            mergedFile = if (tempInputs.size == 1) {
                tempInputs.first()
            } else {
                listFile = File(cacheDir, "list_${System.currentTimeMillis()}.txt").apply {
                    writeText(tempInputs.joinToString("\n") { "file '${it.absolutePath}'" })
                }
                val target = File(cacheDir, "merged_${System.currentTimeMillis()}.mp4")
                val cmd = "-y -f concat -safe 0 -i ${listFile!!.absolutePath} -c copy ${target.absolutePath}"
                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    return@withContext VideoExportResult.Failure("FFmpeg 拼接失败: ${session.failStackTrace}")
                }
                target
            }
            val finalFile = if (audioUris.isNotEmpty()) {
                mergedAudio = mergeAudioForExport(audioUris, cacheDir)
                if (mergedAudio != null) {
                    val out = File(cacheDir, "mux_${System.currentTimeMillis()}.mp4")
                    val cmd = "-y -i ${mergedFile!!.absolutePath} -i ${mergedAudio!!.absolutePath} -c:v copy -c:a aac -shortest ${out.absolutePath}"
                    val session = FFmpegKit.execute(cmd)
                    if (!ReturnCode.isSuccess(session.returnCode)) {
                        return@withContext VideoExportResult.Failure("音频合并失败: ${session.failStackTrace}")
                    }
                    out
                } else mergedFile!!
            } else mergedFile!!

            outputFile = File(cacheDir, "output_${System.currentTimeMillis()}.mp4").also { out ->
                finalFile.copyTo(out, overwrite = true)
            }
            val outputUri = writeToMediaStore(outputFile, title, destination)
                ?: return@withContext VideoExportResult.Failure("无法写入目标存储")
            VideoExportResult.Success(outputUri, destination)
        } catch (throwable: Throwable) {
            VideoExportResult.Failure(throwable.message ?: "未知错误")
        } finally {
            listFile?.delete()
            mergedFile?.takeIf { it.exists() }?.delete()
            mergedAudio?.takeIf { it.exists() }?.delete()
            outputFile?.delete()
            File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
        }
    }

    suspend fun mergeAudioPlaylist(audioUris: List<Uri>): Uri? = withContext(Dispatchers.IO) {
        if (audioUris.isEmpty()) return@withContext null
        var listFile: File? = null
        var mergedFile: File? = null
        try {
            val cacheDir = File(context.cacheDir, "audio_merge").apply { mkdirs() }
            val inputs = audioUris.mapIndexed { index, uri ->
                copyToCacheGeneric(uri, cacheDir, index, "input_${index}_${System.currentTimeMillis()}${inferAudioSuffix(uri)}")
            }
            mergedFile = if (inputs.size == 1) {
                inputs.first()
            } else {
                listFile = File(cacheDir, "list_${System.currentTimeMillis()}.txt").apply {
                    writeText(inputs.joinToString("\n") { "file '${it.absolutePath}'" })
                }
                val target = File(cacheDir, "merged_${System.currentTimeMillis()}.mp3")
                val cmd = "-y -f concat -safe 0 -i ${listFile!!.absolutePath} -c copy ${target.absolutePath}"
                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) return@withContext null
                target
            }
            Uri.fromFile(mergedFile)
        } catch (_: Throwable) {
            null
        } finally {
            listFile?.delete()
        }
    }

    private fun copyToCache(uri: Uri, cacheDir: File, index: Int): File {
        val target = File(cacheDir, "input_${index}_${System.currentTimeMillis()}.mp4")
        val inputStream = when (uri.scheme?.lowercase()) {
            "http", "https" -> runCatching { URL(uri.toString()).openStream() }.getOrNull()
            else -> context.contentResolver.openInputStream(uri)
        } ?: throw IllegalStateException("无法读取视频源: $uri")
        inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun copyToCacheGeneric(uri: Uri, cacheDir: File, index: Int, filename: String): File {
        val target = File(cacheDir, filename)
        val inputStream = when (uri.scheme?.lowercase(Locale.ROOT)) {
            "http", "https" -> runCatching { URL(uri.toString()).openStream() }.getOrNull()
            else -> context.contentResolver.openInputStream(uri)
        } ?: throw IllegalStateException("无法读取源: $uri")
        inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun mergeAudioForExport(audioUris: List<Uri>, cacheDir: File): File? {
        if (audioUris.isEmpty()) return null
        val inputs = audioUris.mapIndexed { index, uri ->
            copyToCacheGeneric(uri, cacheDir, index, "audio_${index}_${System.currentTimeMillis()}${inferAudioSuffix(uri)}")
        }
        if (inputs.size == 1) return inputs.first()
        val listFile = File(cacheDir, "audio_list_${System.currentTimeMillis()}.txt").apply {
            writeText(inputs.joinToString("\n") { "file '${it.absolutePath}'" })
        }
        val target = File(cacheDir, "audio_merged_${System.currentTimeMillis()}.mp3")
        val cmd = "-y -f concat -safe 0 -i ${listFile.absolutePath} -c copy ${target.absolutePath}"
        val session = FFmpegKit.execute(cmd)
        listFile.delete()
        if (!ReturnCode.isSuccess(session.returnCode)) return null
        return target
    }

    private fun inferAudioSuffix(uri: Uri): String {
        val lower = uri.toString().lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".wav") -> ".wav"
            lower.endsWith(".aac") -> ".aac"
            lower.endsWith(".m4a") -> ".m4a"
            lower.endsWith(".flac") -> ".flac"
            else -> ".mp3"
        }
    }

    private fun writeToMediaStore(
        sourceFile: File,
        title: String,
        destination: ExportDestination
    ): Uri? {
        val resolver = context.contentResolver
        val collection = when (destination) {
            ExportDestination.GALLERY -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ExportDestination.DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$title.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, when (destination) {
                ExportDestination.GALLERY -> "Movies/Story2mv"
                ExportDestination.DOWNLOADS -> "Download/Story2mv"
            })
        }
        val outputUri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(outputUri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: return null
        return outputUri
    }
}
