package com.apophuy.altimeter.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class GnssDiagnosticLogWriterTest {
    @Test
    fun appendPreservesEveryQueuedLineInOrder() = withTempDirectory { directory ->
        val writer = RotatingLogWriter(directory, maxFileBytes = 1_024L)
        writer.appendLine("first")
        writer.appendLine("second")
        val snapshot = directory.resolve("snapshot.jsonl")

        assertTrue(writer.snapshotTo(snapshot))
        assertEquals("first\nsecond\n", snapshot.readText())
    }

    @Test
    fun rotationKeepsOnePreviousFileAndChronologicalSnapshot() = withTempDirectory { directory ->
        val writer = RotatingLogWriter(directory, maxFileBytes = 14L)
        writer.appendLine("first")
        writer.appendLine("second")
        writer.appendLine("third")
        val snapshot = directory.resolve("snapshot.jsonl")

        assertTrue(writer.snapshotTo(snapshot))
        assertEquals("first\nsecond\nthird\n", snapshot.readText())
        assertTrue(directory.resolve("previous.jsonl").isFile)
        assertTrue(directory.resolve("current.jsonl").isFile)
    }

    @Test
    fun clearRemovesBothSourceFiles() = withTempDirectory { directory ->
        val writer = RotatingLogWriter(directory, maxFileBytes = 8L)
        writer.appendLine("first")
        writer.appendLine("second")

        writer.clear()

        assertFalse(directory.resolve("current.jsonl").exists())
        assertFalse(directory.resolve("previous.jsonl").exists())
        assertFalse(writer.snapshotTo(directory.resolve("snapshot.jsonl")))
    }

    private fun withTempDirectory(block: (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("gnss-log-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
