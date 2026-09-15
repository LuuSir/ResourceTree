package com.example.resouretree.domain.action

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.resouretree.domain.model.*
import java.io.File

class AndroidContentSharer(context: Context) : ContentSharer {
    private val app = context.applicationContext

    fun createIntent(content: ResourceContent, target: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
        if (content.type == ContentType.TEXT) {
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_TEXT, content.text)
        } else {
            require(validMime(content.type, content.mimeType)) { "媒体 MIME 类型不正确" }
            val uri = readableUri(content.path)
            send.type = content.mimeType
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.clipData = ClipData.newRawUri("ResourceTree", uri)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (target.isNotBlank()) send.setPackage(target)
        return if (target.isBlank()) Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun readableUri(path: String): Uri {
        require(path.isNotBlank()) { "请重新选择媒体文件" }
        val parsed = Uri.parse(path)
        val uri = if (parsed.scheme == "content") parsed else {
            require(parsed.scheme == null || parsed.scheme == "file") { "不支持的媒体路径，请重新选择文件" }
            val file = if (parsed.scheme == "file") File(requireNotNull(parsed.path))
                else File(path).let { if (it.isAbsolute) it else File(app.filesDir, path) }
            val root = File(app.filesDir, "media").canonicalFile
            require(file.canonicalPath.startsWith(root.path + File.separator) && file.isFile) {
                "媒体文件不可用，请重新选择文件"
            }
            FileProvider.getUriForFile(app, "${app.packageName}.media", file)
        }
        try {
            requireNotNull(app.contentResolver.openInputStream(uri)).use { /* Verify read access before sending. */ }
        } catch (e: Exception) { throw IllegalArgumentException("媒体文件不可读取，请重新选择文件", e) }
        return uri
    }

    override fun share(content: ResourceContent, target: String) { app.startActivity(createIntent(content, target)) }
}
