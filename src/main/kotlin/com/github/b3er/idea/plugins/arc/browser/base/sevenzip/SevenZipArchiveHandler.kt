package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.Ostermiller.util.CircularByteBuffer
import com.github.b3er.idea.plugins.arc.browser.FSUtils
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.getAndUse
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.FileAccessorCache
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.simple.ISimpleInArchive
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


open class SevenZipArchiveHandler(path: String) :
    BaseArchiveHandler<SevenZipArchiveHandler.SevenZipArchiveHolder>(path) {
    class SevenZipArchiveHolder(file: File) : Closeable {
        private val stream = ReusableRandomAccessFileStream(file)
        val archive = toArchive()!!
        val simpleInterface = archive.simpleInterface!!
        override fun close() {
            stream.use {
                archive.use {
                    simpleInterface.close()
                }
            }
        }

        private fun toArchive(): IInArchive? {
            return stream.use {
                SevenZip.openInArchive(null, it)
            }
        }

        fun <R> useStream(block: (SevenZipArchiveHolder) -> R): R {
            return stream.use {
                block(this)
            }
        }

        fun closeStream() {
            stream.close()
        }
    }

    override val accessorCache: FileAccessorCache<BaseArchiveHandler<SevenZipArchiveHolder>, SevenZipArchiveHolder> =
        CACHE

    companion object {
        private val CACHE = createCache<SevenZipArchiveHolder>(
            onCreate = {
                SevenZipArchiveHolder(it.file.canonicalFile)
            },
            onDispose = {
                it.close()
            },
            onEqual = { v1, v2 ->
                v1?.file?.canonicalPath == v2?.file?.canonicalPath
            }
        )
        val IO_EXECUTOR: ExecutorService = Executors.newCachedThreadPool()

        private val MERGED_ARCHIVE_EXTENSIONS = mapOf(
            ".tb2" to ".tar",
            ".tbz" to ".tar",
            ".tbz2" to ".tar",
            ".tz2" to ".tar",
            ".taz" to ".tar",
            ".tgz" to ".tar",
            ".tlz" to ".tar",
            ".tZ" to ".tar",
            ".taZ" to ".tar",
            ".tlz" to ".tar",
            ".tzst" to ".tar"
        )
    }

    override fun createEntriesMap(): MutableMap<String, EntryInfo> {
        return getFileHandle().getAndUse { holder ->
            holder.useStream {
                val simpleInArchive = holder.simpleInterface
                val archive = holder.archive
                LinkedHashMap<String, EntryInfo>(simpleInArchive.numberOfItems).also { map ->
                    map[""] = createRootEntry()
                    // If it's single file archive, we need to construct its name
                    // because 7z leaves it empty, respecting cases with merged extensions like "tgz"
                    // For some archives (like xz) format is null
                    if (isSingleFileArchive(archive)) {
                        val entry = simpleInArchive.archiveItems[0]
                        val path = createEntryNameForSingleArchive(entry)
                        getOrCreate(entry, map, simpleInArchive, path)
                    } else {
                        simpleInArchive.archiveItems.forEach { item ->
                            getOrCreate(item, map, simpleInArchive)
                        }
                    }
                }
            }
        }
    }

    private fun createEntryNameForSingleArchive(entry: ISimpleInArchiveItem): String {
        val path = entry.ideaPath
        if (path.isNotEmpty()) {
            return path
        }
        val archiveFileName = file.name
        val nameWithoutEndExtension = archiveFileName.let { name -> name.substring(0, name.lastIndexOf('.')) }
        // Handle merged archive extensions
        return MERGED_ARCHIVE_EXTENSIONS.entries.find { (ext) ->
            archiveFileName.endsWith(ext, ignoreCase = true)
        }?.let { (_, nestedExt) -> "$nameWithoutEndExtension$nestedExt" } ?: nameWithoutEndExtension
    }

    private fun getOrCreate(
        item: ISimpleInArchiveItem,
        map: LinkedHashMap<String, EntryInfo>,
        archive: ISimpleInArchive,
        altEntryName: String? = null
    ): EntryInfo {
        val entryName = altEntryName ?: item.ideaPath
        var info = map[entryName]
        if (info != null) {
            return info
        }
        val path = splitPath(entryName)
        val parentInfo = getOrCreate(path.first, map, archive)
        if ("." == path.second) {
            return parentInfo
        }
        val shortName = convertNameToBytes(path.second)
        info = EntryInfo(
            shortName,
            item.isFolder,
            item.size ?: DEFAULT_LENGTH,
            item.creationTime?.time ?: DEFAULT_TIMESTAMP,
            parentInfo
        )
        map[entryName] = info
        return info
    }

    private fun getOrCreate(
        entryName: String,
        map: LinkedHashMap<String, EntryInfo>,
        archive: ISimpleInArchive
    ): EntryInfo {
        var info = map[entryName]
        if (info == null) {
            val item = archive.archiveItems.firstOrNull { it.ideaPath == entryName }
            if (item != null) {
                return getOrCreate(item, map, archive)
            }
            val path = splitPath(entryName)
            val parentInfo = getOrCreate(path.first, map, archive)
            info = EntryInfo(
                convertNameToBytes(path.second), true, DEFAULT_LENGTH,
                DEFAULT_TIMESTAMP, parentInfo
            )
            map[entryName] = info
        }
        if (!info.isDirectory) {
            info = EntryInfo(info.shortName, true, info.length, info.timestamp, info.parent)
            map[entryName] = info
        }
        return info
    }

    private fun isSingleFileArchive(archive: IInArchive): Boolean {
        return (archive.numberOfItems == 1 && archive.archiveFormat == null)
                || archive.archiveFormat?.supportMultipleFiles() == false
    }

    private fun getItemForPath(holder: SevenZipArchiveHolder, relativePath: String): ISimpleInArchiveItem? {
        return holder.useStream {
            val simpleInArchive = holder.simpleInterface
            // For single file archives don't look at path
            val archive = holder.archive
            if (isSingleFileArchive(archive)) {
                simpleInArchive.archiveItems.firstOrNull()
            } else {
                simpleInArchive.archiveItems.firstOrNull {
                    it.ideaPath == relativePath
                }
            }
        }
    }

    override fun getInputStream(relativePath: String): InputStream {
        return getFileHandle().getAndUse { holder ->
            val item = getItemForPath(holder, relativePath) ?: throw FileNotFoundException("$file!/$relativePath")
            SevenZipInputStream(holder, item)
        }
    }

    fun getInputStreamForFile(file: VirtualFile): SevenZipInputStream {
        return getFileHandle().getAndUse { holder ->
            val item = getItemForPath(holder, file.path.split(FSUtils.FS_SEPARATOR).last())
                ?: throw FileNotFoundException("$file!")
            SevenZipInputStream(holder, item)
        }
    }

    override fun contentsToByteArray(relativePath: String): ByteArray {
        return getFileHandle().getAndUse { holder ->
            holder.useStream {
                val item = getItemForPath(holder, relativePath) ?: throw FileNotFoundException("$file!/$relativePath")
                if (FileUtilRt.isTooLarge(item.size ?: DEFAULT_LENGTH)) {
                    throw FileTooBigException("$file!/$relativePath")
                } else {
                    ByteArrayOutputStream(item.size?.toInt() ?: DEFAULT_BUFFER_SIZE).use { stream ->
                        item.extractSlow {
                            stream.write(it)
                            it.size
                        }
                        stream.toByteArray()
                    }
                }
            }
        }
    }

    private val ISimpleInArchiveItem.ideaPath
        get() = FSUtils.convertPathToIdea(path)


    class SevenZipInputStream(val holder: SevenZipArchiveHolder, private val item: ISimpleInArchiveItem) :
        InputStream() {
        private var buffer: CircularByteBuffer? = null
        private val stream by lazy {
            holder.useStream {
                val b = CircularByteBuffer(1 * 1024 * 1024, true)
                buffer = b
                IO_EXECUTOR.submit {
                    b.outputStream.use { out ->
                        item.extractSlow {
                            out.write(it)
                            it.size
                        }
                    }
                }
                b.inputStream
            }
        }

        override fun read(): Int = holder.useStream { stream.read() }

        override fun read(b: ByteArray?, off: Int, len: Int): Int = stream.read(b, off, len)

        override fun available(): Int = stream.available()

        override fun reset() = stream.reset()

        override fun skip(n: Long): Long = stream.skip(n)

        override fun mark(readlimit: Int) = stream.mark(readlimit)

        override fun markSupported(): Boolean = stream.markSupported()

        override fun close() {
            if (buffer != null) {
                stream.close()
                holder.closeStream()
            }
        }

        fun directRead(stream: ISequentialOutStream): ExtractOperationResult =
            item.extractSlow(stream)

        fun directRead(stream: (ByteArray) -> Int): ExtractOperationResult =
            item.extractSlow(stream)
    }

    class ReusableRandomAccessFileStream(private val file: File) : IInStream {
        var raf: RandomAccessFile? = null
        var filePointer = 0L

        @Synchronized
        @Throws(SevenZipException::class)
        override fun read(bytes: ByteArray): Int {
            return try {
                val openedFile = getRandomFile()
                val read = openedFile.read(bytes)
                filePointer = openedFile.filePointer
                if (read == -1) 0 else read
            } catch (e: IOException) {
                throw SevenZipException("Error reading random access file", e)
            }
        }

        @Synchronized
        @Throws(SevenZipException::class)
        override fun seek(offset: Long, origin: Int): Long {
            return try {
                val openedFile = getRandomFile()
                when (origin) {
                    ISeekableStream.SEEK_SET -> openedFile.seek(offset)
                    ISeekableStream.SEEK_CUR -> openedFile.seek(openedFile.filePointer + offset)
                    ISeekableStream.SEEK_END -> openedFile.seek(openedFile.length() + offset)
                    else -> throw RuntimeException("Seek: unknown origin: $origin")
                }
                filePointer = openedFile.filePointer
                openedFile.filePointer
            } catch (e: IOException) {
                throw SevenZipException("Error while seek operation", e)
            }
        }

        private fun getRandomFile(): RandomAccessFile {
            return raf ?: RandomAccessFile(file, "r").also { newFile ->
                newFile.seek(filePointer)
                raf = newFile
            }
        }

        @Synchronized
        @Throws(IOException::class)
        override fun close() {
            raf?.also {
                raf = null
                it.close()
            }
        }
    }
}

