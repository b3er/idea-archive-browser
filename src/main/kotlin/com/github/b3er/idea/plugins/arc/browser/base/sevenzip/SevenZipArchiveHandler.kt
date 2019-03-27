package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.Ostermiller.util.CircularByteBuffer
import com.github.b3er.idea.plugins.arc.browser.AppInfoUtil
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.getAndUse
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.FileAccessorCache
import com.intellij.util.text.ByteArrayCharSequence
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchive
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.*


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

    private val appInfo = ApplicationInfo.getInstance()
    override val accessorCache: FileAccessorCache<BaseArchiveHandler<SevenZipArchiveHolder>, SevenZipArchiveHolder> =
        CACHE

    companion object {
        private val CACHE = createCache(
            onCreate = {
                SevenZipArchiveHolder(it.file.canonicalFile)
            },
            onDispose = SevenZipArchiveHolder::close
        )
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
        val shortName = if (AppInfoUtil.baselineVersion >= 183) {
            @Suppress("MissingRecentApi")
            ByteArrayCharSequence.convertToBytesIfPossible(path.second)
        } else {
            @Suppress("DEPRECATION")
            ByteArrayCharSequence.convertToBytesIfAsciiString(path.second)
        }
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
            @Suppress("DEPRECATION")
            info = EntryInfo(
                ByteArrayCharSequence.convertToBytesIfAsciiString(path.second), true, DEFAULT_LENGTH,
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
        val buffer = CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, true)
        buffer.outputStream.use { out ->
            item.extractSlow {
                out.write(it)
                it.size
            }
        }
        return buffer.inputStream
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
                getInputStreamForItem(item).use {
                    FileUtil.loadBytes(it)
                }
            }
        }
    }

    private val ISimpleInArchiveItem.ideaPath
        get() = path?.replace('\\', '/') ?: ""
}