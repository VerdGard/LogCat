package com.solosu.logcat.crash

import android.content.Context
import kotlin.system.exitProcess

/**
 * 应用自身崩溃捕获:写入 [CrashStore] 后再交回系统默认处理。
 * 只处理主线程未捕获异常(默认 handler),不吞掉崩溃。
 */
class CrashHandler(
    private val context: Context,
    private val store: CrashStore,
) : Thread.UncaughtExceptionHandler {

    private val previous: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            store.add(
                CrashRecord(
                    id = CrashStore.newId(),
                    time = CrashStore.now(),
                    thread = thread.name,
                    exception = "${throwable.javaClass.name}: ${throwable.message.orEmpty()}",
                    stackTrace = throwable.stackTraceToString(),
                    module = context.packageName,
                    fromSystem = false,
                )
            )
        }
        // 交回原处理器,保持系统崩溃对话框与统计行为
        previous?.uncaughtException(thread, throwable) ?: exitProcess(10)
    }

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }
}
