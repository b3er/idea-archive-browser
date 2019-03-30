package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.Ostermiller.util.CircularByteBuffer
import com.github.b3er.idea.plugins.arc.browser.FSUtils
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.getAndUse
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.FileAccessorCache
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchive
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.*
import java.util.concurrent.Executors


open class SevenZipArchiveHandler(path: String) :
    BaseArchiveHandler<SevenZipArchiveHandler.SevenZipArchiveHolder>(path) {
    class SevenZipArchiveHolder(file: File) : Closeable {
        private val raf = RandomAccessFile(file, "r")
        val archive = SevenZip.openInArchive(null, RandomAccessFileInStream(raf))!!
        val simpleInterface = archive.simpleInterface!!
        override fun close() {
            raf.use {
                archive.use {
                    simpleInterface.close()
                }
            }
        }
    }

    override val accessorCache: FileAccessorCache<BaseArchiveHandler<SevenZipArchiveHolder>, SevenZipArchiveHolder> =
        CACHE

    companion object {
        private val CACHE = createCache(
            onCreate = {
                SevenZipArchiveHolder(it.file.canonicalFile)
            },
            onDispose = SevenZipArchiveHolder::close
        )
        val IO_EXECUTOR = Executors.newCachedThreadPool()
    }

    override fun createEntriesMap(): MutableMap<String, EntryInfo> {
        return getFileHandle().getAndUse { holder ->
            val simpleInArchive = holder.simpleInterface
            val archive = holder.archive
            LinkedHashMap<String, EntryInfo>(simpleInArchive.numberOfItems).also { map ->
                map[""] = createRootEntry()
                // If it's single file archive, we need to construct its name, because 7z leaves it empty
                // For some archives (like xz) format is null
                if (isSingleFileArchive(archive)) {
                    val entry = simpleInArchive.archiveItems[0]
                    var path = entry.ideaPath
                    if (path.isEmpty()) {
                        path = file.name.let { name -> name.substring(0, name.lastIndexOf('.')) }
                    }
                    getOrCreate(entry, map, simpleInArchive, path)
                } else {
                    simpleInArchive.archiveItems.forEach { item ->
                        getOrCreate(item, map, simpleInArchive)
                    }
                }
            }
        }
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
        val simpleInArchive = holder.simpleInterface
        // For single file archives don't look at path
        val archive = holder.archive
        return if (isSingleFileArchive(archive)) {
            simpleInArchive.archiveItems.firstOrNull()
        } else {
            simpleInArchive.archiveItems.firstOrNull {
                it.ideaPath == relativePath
            }
        }
    }

    private fun getInputStreamForItem(item: ISimpleInArchiveItem): InputStream {
        return SevenZipInputStream(item)
    }

    override fun getInputStream(relativePath: String): InputStream {
        return getFileHandle().getAndUse { holder ->
            val item = getItemForPath(holder, relativePath) ?: throw FileNotFoundException("$file!/$relativePath")
            getInputStreamForItem(item)
        }
    }

    override fun contentsToByteArray(relativePath: String): ByteArray {
        return getFileHandle().getAndUse { holder ->
            val item = getItemForPath(holder, relativePath) ?: throw FileNotFoundException("$file!/$relativePath")
            if (FileUtilRt.isTooLarge(item.size ?: DEFAULT_LENGTH)) {
                throw FileTooBigException("$file!/$relativePath")
            } else {
                ByteArrayOutputStream(item.size?.toInt()?: DEFAULT_BUFFER_SIZE).use { stream ->
                    item.extractSlow {
                        stream.write(it)
                        it.size
                    }
                    stream.toByteArray()
                }
            }
        }
    }

    private val ISimpleInArchiveItem.ideaPath
        get() = FSUtils.convertPathToIdea(path)


    class SevenZipInputStream(private val item: ISimpleInArchiveItem) : InputStream() {
        private var buffer: CircularByteBuffer? = null
        private val stream by lazy {
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

        override fun read(): Int = stream.read()

        override fun read(b: ByteArray?, off: Int, len: Int): Int = stream.read(b, off, len)

        override fun available(): Int = stream.available()

        override fun reset() = stream.reset()

        override fun skip(n: Long): Long = stream.skip(n)

        override fun mark(readlimit: Int) = stream.mark(readlimit)

        override fun markSupported(): Boolean = stream.markSupported()

        override fun close() {
            if (buffer != null) {
                stream.close()
            }
        }

        fun directRead(stream: ISequentialOutStream): ExtractOperationResult = item.extractSlow(stream)
        fun directRead(stream: (ByteArray) -> Int): ExtractOperationResult = item.extractSlow(stream)
    }
}