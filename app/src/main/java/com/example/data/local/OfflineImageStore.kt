package com.example.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Stores a bounded raster copy in app-private files so saved article images survive process death. */
class OfflineImageStore(context: Context, client: OkHttpClient) {
    private val directory = File(context.applicationContext.filesDir, "offline_images")
    private val http = client.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun download(storyId: String, rawUrl: String): String? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return@withContext null
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return@withContext null
        if (!directory.exists() && !directory.mkdirs()) return@withContext null

        val request = Request.Builder()
            .url(rawUrl)
            .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif;q=0.9,*/*;q=0.1")
            .header("User-Agent", "CizgiNews/1.0")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.request.url.isHttps.not()) return@withContext null
            val body = response.body ?: return@withContext null
            val mime = body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }.orEmpty()
            val extension = when (mime) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/avif" -> "avif"
                else -> return@withContext null
            }
            if (body.contentLength() > MAX_IMAGE_BYTES) return@withContext null

            val target = File(directory, "${fileKey(storyId)}.$extension")
            val temporary = File.createTempFile("cizgi-", ".tmp", directory)
            try {
                var copied = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_IMAGE_BYTES) return@withContext null
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (copied == 0L) return@withContext null
                val completed = runCatching {
                    java.nio.file.Files.move(
                        temporary.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    )
                }.isSuccess || (temporary.renameTo(target))
                if (completed) target.absolutePath else null
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    fun localUri(path: String): String? = path.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?.toURI()
        ?.toString()

    fun delete(path: String) {
        if (path.isBlank()) return
        val file = File(path).canonicalFile
        if (file.parentFile == directory.canonicalFile) file.delete()
    }

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
    }

    private fun fileKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_IMAGE_BYTES = 5L * 1024L * 1024L
    }
}
