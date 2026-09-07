package com.example.resouretree.data.transfer

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentStore(private val resolver: ContentResolver) {
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
