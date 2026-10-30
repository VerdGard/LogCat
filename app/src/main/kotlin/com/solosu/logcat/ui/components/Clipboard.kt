package com.solosu.logcat.ui.components

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/**
 * 文本复制。Compose 1.9 起 LocalClipboardManager 已废弃,
 * 这里封装 LocalClipboard 的 suspend API,并自动绑定到当前组合的作用域。
 *
 * 返回值约定:[copy] 立即返回,实际写入在协程里完成;
 * 因为剪贴板写入不需要用户等待结果,界面可以直接给出反馈提示。
 */
@Composable
fun rememberTextClipboard(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text ->
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("LogCat", text))
                )
            }
        }
    }
}
