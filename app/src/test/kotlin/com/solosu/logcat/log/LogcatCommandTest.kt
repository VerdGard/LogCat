package com.solosu.logcat.log

import com.solosu.logcat.shell.ShellMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatCommandTest {

    @Test
    fun `stream uses threadtime format`() {
        val command = LogcatCommand.stream(ShellMode.DEFAULT, LogBuffer.MAIN)
        assertEquals("logcat", command[0])
        assertTrue(command.containsAll(listOf("-v", "threadtime")))
        assertEquals("*:V", command.last())
    }

    @Test
    fun `all buffer expands to main system and crash`() {
        val command = LogcatCommand.stream(ShellMode.ROOT, LogBuffer.ALL)
        val bufferArgs = command.windowed(2).filter { it[0] == "-b" }.map { it[1] }
        assertEquals(listOf("main", "system", "crash"), bufferArgs)
    }

    @Test
    fun `single buffer passes one -b`() {
        listOf(LogBuffer.MAIN, LogBuffer.SYSTEM, LogBuffer.CRASH).forEach { buffer ->
            val command = LogcatCommand.stream(ShellMode.ROOT, buffer)
            val bufferArgs = command.windowed(2).filter { it[0] == "-b" }.map { it[1] }
            assertEquals(listOf(buffer.id), bufferArgs)
        }
    }

    @Test
    fun `default mode pins pid`() {
        val command = LogcatCommand.stream(ShellMode.DEFAULT, LogBuffer.ALL, pid = 4321)
        val index = command.indexOf("--pid")
        assertTrue(index >= 0)
        assertEquals("4321", command[index + 1])
    }

    @Test
    fun `full access modes omit pid`() {
        listOf(ShellMode.ROOT, ShellMode.SHIZUKU).forEach { mode ->
            val command = LogcatCommand.stream(mode, LogBuffer.ALL, pid = null)
            assertFalse(command.contains("--pid"))
        }
    }

    @Test
    fun `dump asks for trailing lines and min level`() {
        val command = LogcatCommand.dump(LogBuffer.MAIN, lines = 250, level = LogLevel.ERROR)
        assertTrue(command.contains("-d"))
        val tIndex = command.indexOf("-t")
        assertEquals("250", command[tIndex + 1])
        assertEquals("*:E", command.last())
    }

    @Test
    fun `clear uses -c for each buffer`() {
        val all = LogcatCommand.clear(LogBuffer.ALL)
        assertEquals("logcat", all[0])
        assertTrue(all.contains("-c"))
        assertEquals(listOf("main", "system", "crash"), all.windowed(2).filter { it[0] == "-b" }.map { it[1] })

        val single = LogcatCommand.clear(LogBuffer.CRASH)
        assertEquals(listOf("crash"), single.windowed(2).filter { it[0] == "-b" }.map { it[1] })
    }

    @Test
    fun `resolvePid only constrains default mode`() {
        assertEquals(999, com.solosu.logcat.shell.Shells.resolvePid(ShellMode.DEFAULT, 999))
        assertEquals(null, com.solosu.logcat.shell.Shells.resolvePid(ShellMode.ROOT, 999))
        assertEquals(null, com.solosu.logcat.shell.Shells.resolvePid(ShellMode.SHIZUKU, 999))
    }
}
