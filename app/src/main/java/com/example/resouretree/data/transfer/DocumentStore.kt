package com.example.resouretree.data.transfer

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.resouretree.domain.model.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ensureActive

class DocumentStore(private val resolver: ContentResolver, private val mediaDirectory: File? = null) {
    /** Keep one durable private copy. No dependency on a temporary picker grant after saving. */
    suspend fun importMedia(uri: Uri, type: ContentType): ResourceContent = withContext(Dispatchers.IO) {
        require(type != ContentType.TEXT) { "请选择媒体内容类型" }
        val mime = resolver.getType(uri) ?: if (type == ContentType.FILE) "application/octet-stream" else ""
        require(validMime(type, mime)) { "所选文件与内容类型不符" }
        val directory = requireNotNull(mediaDirectory) { "媒体存储不可用" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建媒体目录" }
        val file = File(directory, UUID.randomUUID().toString())
        try {
            requireNotNull(resolver.openInputStream(uri)) { "无法读取所选文件" }.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            ResourceContent(type, path = "media/${file.name}", mimeType = mime)
        } catch (e: Exception) { file.delete(); throw e }
    }
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = requireNotNull(resolver.openInputStream(uri)) { "无法打开所选文件" }
        stream.use {
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val size = it.read(chunk)
                if (size == -1) break
                require(buffer.size() + size <= TreeJson.MAX_BYTES) { "文件超过 10 MB，无法导入" }
                buffer.write(chunk, 0, size)
            }
            buffer.toString("UTF-8")
        }
    }
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入所选位置" }
            .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
