package com.github.b3er.idea.plugins.arc.browser.formats.sevenzip

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SevenZipArchiveHandlerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `lists synthesized directories and reads file metadata and content`() {
        val content = "archive content".toByteArray()
        val archive = writeZip("content.zip", "dir/file.txt" to content)
        val handler = SevenZipArchiveHandler(archive.toString())

        try {
            assertTrue(handler.getAttributes("")!!.isDirectory)
            assertEquals(listOf("dir"), handler.list("").toList())
            assertTrue(handler.getAttributes("dir")!!.isDirectory)
            assertEquals(listOf("file.txt"), handler.list("dir").toList())

            val fileAttributes = handler.getAttributes("dir/file.txt")!!
            assertFalse(fileAttributes.isDirectory)
            assertEquals(content.size.toLong(), fileAttributes.length)
            assertContentEquals(content, handler.contentsToByteArray("dir/file.txt"))
        } finally {
            handler.clearCaches()
        }
    }

    @Test
    fun `rejects traversal and preserves file to directory conflict semantics`() {
        val archive = writeZip(
            "paths.zip",
            "../escape.txt" to byteArrayOf(1),
            "a" to byteArrayOf(2),
            "a/b.txt" to byteArrayOf(3),
            "tail.txt" to byteArrayOf(4)
        )
        val handler = SevenZipArchiveHandler(archive.toString())

        try {
            assertNull(handler.getAttributes("../escape.txt"))
            assertEquals(listOf("a", "tail.txt"), handler.list("").toList())
            assertTrue(handler.getAttributes("a")!!.isDirectory)
            assertEquals(listOf("b.txt"), handler.list("a").toList())
        } finally {
            handler.clearCaches()
        }
    }

    @Test
    fun `clear caches reloads metadata after archive replacement`() {
        val archive = writeZip("reload.zip", "before.txt" to byteArrayOf(1))
        val handler = SevenZipArchiveHandler(archive.toString())

        try {
            assertEquals(listOf("before.txt"), handler.list("").toList())
            writeZip("reload.zip", "after.txt" to byteArrayOf(1, 2))
            handler.clearCaches()
            assertEquals(listOf("after.txt"), handler.list("").toList())
        } finally {
            handler.clearCaches()
        }
    }
    @Test
    fun `deleted archive invalidates root and returns empty listing`() {
        val archive = writeZip("deleted.zip", "dir/file.txt" to byteArrayOf(1))
        val handler = SevenZipArchiveHandler(archive.toString())

        try {
            assertEquals(listOf("dir"), handler.list("").toList())
            Files.delete(archive)
            assertNull(handler.getAttributes(""))
            assertContentEquals(emptyArray(), handler.list(""))
        } finally {
            handler.clearCaches()
        }
    }

    @Test
    fun `corrupt archive returns empty listing`() {
        val archive = tempDir.resolve("corrupt.7z")
        Files.writeString(archive, "not an archive")
        val handler = SevenZipArchiveHandler(archive.toString())

        try {
            assertContentEquals(emptyArray(), handler.list(""))
        } finally {
            handler.clearCaches()
        }
    }


    private fun writeZip(name: String, vararg entries: Pair<String, ByteArray>): Path {
        val archive = tempDir.resolve(name)
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            entries.forEach { (entryName, content) ->
                output.putNextEntry(ZipEntry(entryName))
                output.write(content)
                output.closeEntry()
            }
        }
        return archive
    }
}
