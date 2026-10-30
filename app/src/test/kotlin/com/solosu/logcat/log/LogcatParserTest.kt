package com.solosu.logcat.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatParserTest {

    private fun parse(vararg lines: String, maxLen: Int = 20_000): List<LogEntry> {
        val parser = LogcatParser(maxMessageLength = maxLen)
        val out = ArrayList<LogEntry>()
        lines.forEach { parser.feed(it) { entry -> out += entry } }
        parser.flush { out += it }
        return out
    }

    @Test
    fun `parses threadtime header`() {
        val entries = parse("10-30 15:30:12.345  1234  5678 I ActivityManager: started activity")
        assertEquals(1, entries.size)
        with(entries[0]) {
            assertEquals("10-30", date)
            assertEquals("15:30:12.345", time)
            assertEquals(1234, pid)
            assertEquals(5678, tid)
            assertEquals(LogLevel.INFO, level)
            assertEquals("ActivityManager", tag)
            assertEquals("started activity", message)
        }
    }

    @Test
    fun `parses every level letter`() {
        val lines = "VDIWEFA".mapIndexed { index, letter ->
            "10-30 15:30:12.34$index  1000  1000 $letter SomeTag: msg$index"
        }
        val entries = parse(*lines.toTypedArray())
        assertEquals(
            listOf(
                LogLevel.VERBOSE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN,
                LogLevel.ERROR, LogLevel.FATAL, LogLevel.ASSERT,
            ),
            entries.map { it.level },
        )
    }

    @Test
    fun `drops buffer separator lines`() {
        val entries = parse(
            "--------- beginning of main",
            "10-30 15:30:12.345  1000  1000 I Tag: hello",
            "--------- beginning of system",
            "10-30 15:30:12.346  1000  1000 I Tag: world",
        )
        assertEquals(2, entries.size)
        assertEquals("hello", entries[0].message)
        assertEquals("world", entries[1].message)
        assertFalse(entries.any { it.message.contains(LOGCAT_BUFFER_SEPARATOR) })
    }

    @Test
    fun `merges continuation lines into previous entry`() {
        val entries = parse(
            "10-30 15:30:12.345  1000  1000 E AndroidRuntime: FATAL EXCEPTION: main",
            "java.lang.IllegalStateException: boom",
            "\tat com.example.Foo.bar(Foo.kt:12)",
            "\tat com.example.Foo.baz(Foo.kt:20)",
            "10-30 15:30:12.400  1000  1000 I Tag: next",
        )
        assertEquals(2, entries.size)
        val fatal = entries[0]
        assertEquals(LogLevel.ERROR, fatal.level)
        assertTrue(fatal.message.startsWith("FATAL EXCEPTION: main"))
        assertTrue(fatal.message.contains("IllegalStateException: boom"))
        assertTrue(fatal.message.contains("at com.example.Foo.baz(Foo.kt:20)"))
        assertEquals("next", entries[1].message)
    }

    @Test
    fun `last entry keeps continuation after flush`() {
        val entries = parse(
            "10-30 15:30:12.345  1000  1000 E AndroidRuntime: crash",
            "line one",
            "line two",
        )
        assertEquals(1, entries.size)
        assertEquals("crash\nline one\nline two", entries[0].message)
    }

    @Test
    fun `truncates overlong message and flags overflow`() {
        val body = "x".repeat(50)
        val entries = parse("10-30 15:30:12.345  1000  1000 I Tag: $body", maxLen = 10)
        assertEquals(1, entries.size)
        assertEquals(10, entries[0].message.length)
        assertTrue(entries[0].overflow)
    }

    @Test
    fun `truncates merged continuation and flags overflow`() {
        val entries = parse(
            "10-30 15:30:12.345  1000  1000 E Tag: head",
            "y".repeat(50),
            maxLen = 12,
        )
        assertEquals(1, entries.size)
        assertEquals(12, entries[0].message.length)
        assertTrue(entries[0].overflow)
    }

    @Test
    fun `seq increments and reset restarts it`() {
        val parser = LogcatParser()
        val out = ArrayList<LogEntry>()
        parser.feed("10-30 15:30:12.345  1000  1000 I Tag: a") { out += it }
        parser.feed("10-30 15:30:12.346  1000  1000 I Tag: b") { out += it }
        parser.flush { out += it }
        assertEquals(listOf(1L, 2L), out.map { it.seq })

        parser.reset()
        val again = ArrayList<LogEntry>()
        parser.feed("10-30 15:30:13.000  1000  1000 I Tag: c") { again += it }
        parser.flush { again += it }
        assertEquals(listOf(1L), again.map { it.seq })
    }

    @Test
    fun `ignores leading whitespace before header`() {
        val entries = parse("  10-30 15:30:12.345  1000  1000 I Tag: padded")
        assertEquals(1, entries.size)
        assertEquals("padded", entries[0].message)
    }

    @Test
    fun `non header lines without pending entry are dropped`() {
        val entries = parse("orphan continuation", "", "another")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `rawLine round trips to threadtime shape`() {
        val entries = parse("10-30 15:30:12.345  42  43 W MyTag: careful")
        assertEquals("10-30 15:30:12.345    42    43 W MyTag: careful", entries[0].rawLine())
    }
}
