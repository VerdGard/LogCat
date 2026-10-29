package com.solosu.logcat.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内可见的运行日志(默认模式下用户看不到系统日志,但至少能看到本工具自身的动作记录)。
 * 同时转发给 android.util.Log,在默认模式下能被自身的 logcat 读到。
 */
object AppLog {

    private const val TAG = "LogCat"
    private const val MAX_ENTRIES = 500

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    data class Line(val time: String, val level: Char, val text: String)

    fun info(text: String) = append('I', text)
    fun warn(text: String) = append('W', text)
    fun error(text: String) = append('E', text)

    private fun append(level: Char, text: String) {
        when (level) {
            'E' -> Log.e(TAG, text)
            'W' -> Log.w(TAG, text)
            else -> Log.i(TAG, text)
        }
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val updated = (_lines.value + Line(stamp, level, text)).takeLast(MAX_ENTRIES)
        _lines.value = updated
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
