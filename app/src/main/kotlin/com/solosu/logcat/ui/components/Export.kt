package com.solosu.logcat.ui.components

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通过 SAF 创建的文件写入文本。
 * 写入失败返回 Result.failure,调用方负责提示,避免吞掉错误。
 */
suspend fun writeTextToUri(context: Context, uri: Uri, text: String): Result<Int> =
    withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("无法打开目标文件")
            stream.use { output ->
                val bytes = text.toByteArray(Charsets.UTF_8)
                output.write(bytes)
                output.flush()
                bytes.size
            }
        }
    }

/** 生成带时间戳的默认文件名,例如 logcat-20261030-153012.log */
fun defaultExportName(prefix: String = "logcat"): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "$prefix-$stamp.log"
}
