package com.github.b3er.idea.plugins.arc.browser.formats.sevenzip

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.nest.SupportsStreamForVirtualFile
import com.github.b3er.idea.plugins.arc.browser.base.sevenzip.SevenZipInputStream
import com.github.b3er.idea.plugins.arc.browser.util.FSUtils
import com.github.b3er.idea.plugins.arc.browser.util.getAndUse
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.pathString

class SevenZipArchiveHandler(
    path: String
) : BaseArchiveHandler<SevenZipArchiveHolder>(path), SupportsStreamForVirtualFile {

    override val accessorCache
        get() = cache

    override fun contentsToByteArray(relativePath: String): ByteArray {
        return getFileHandle().getAndUse { holder ->
            val item = holder.getItemForPath(relativePath)
            if (
                (item.size ?: DEFAULT_LENGTH) > maxOf(
                    20 * FileUtilRt.MEGABYTE,
                    FileUtilRt.getUserFileSizeLimit(),
                    FileUtilRt.getUserContentLoadLimit()
                )
            ) {
                throw FileTooBigException("$path/$relativePath")
            } else {
                holder.useStream {
                    ByteArrayOutputStream(
                        item.size?.toInt()?.coerceAtLeast(DEFAULT_BUFFER_SIZE) ?: SevenZipInputStream.BUFFER_SIZE
                    ).use { stream ->
                        item.extractSlow { bytes ->
                            stream.write(bytes)
                            bytes.size
                        }
                        stream.toByteArray()
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
        val archiveFileName = this.path.fileName.pathString
        val nameWithoutEndExtension = archiveFileName.let { name -> name.substring(0, name.lastIndexOf('.')) }
        return FSUtils.decorateMergedNameWithExtension(this.path.extension, nameWithoutEndExtension)
    }

    override fun getAttributes(relativePath: String): FileAttributes? {
        if (relativePath.isEmpty()) {
            return if (Files.exists(path)) DIRECTORY_ATTRIBUTES else null
        }
        return try {
            getFileHandle().getAndUse { holder ->
                metadataIndex(holder).entries[relativePath]?.let { entry ->
                    FileAttributes(
                        entry.isDirectory,
                        false,
                        false,
                        false,
                        entry.length,
                        entry.timestamp,
                        false,
                        FileAttributes.CaseSensitivity.SENSITIVE
                    )
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to read archive metadata: $path", e)
            null
        }
    }

    override fun list(relativePath: String): Array<String> {
        return try {
            getFileHandle().getAndUse { holder ->
                val index = metadataIndex(holder)
                if (index.entries[relativePath]?.isDirectory != true) {
                    emptyArray()
                } else {
                    index.children[relativePath]?.toTypedArray() ?: emptyArray()
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to list archive entries: $path", e)
            emptyArray()
        }
    }

    override fun clearCaches() {
        synchronized(metadataLock) {
            metadataCache = null
        }
        super.clearCaches()
    }

    override fun isSingleFileArchive(): Boolean {
        return getFileHandle().getAndUse { holder ->
            holder.archive.isSingleFileArchive()
        }
    }

    private fun metadataIndex(holder: SevenZipArchiveHolder): MetadataIndex {
        metadataCache?.let { cached ->
            if (cached.holder.get() === holder) {
                return cached.index
            }
        }
        return synchronized(metadataLock) {
            metadataCache?.let { cached ->
                if (cached.holder.get() === holder) {
                    return@synchronized cached.index
                }
            }
            createMetadataIndex(holder).also { index ->
                metadataCache = CachedMetadata(WeakReference(holder), index)
            }
        }
    }

    private fun createMetadataIndex(holder: SevenZipArchiveHolder): MetadataIndex {
        val archiveItems = holder.archiveItems
        val entries = LinkedHashMap<String, EntryMetadata>(archiveItems.size + 1)
        entries[""] = EntryMetadata(
            isDirectory = true,
            length = DEFAULT_LENGTH,
            timestamp = DEFAULT_TIMESTAMP,
            itemIndex = null
        )
        if (holder.archive.isSingleFileArchive()) {
            val entry = archiveItems.first()
            addMetadataEntry(
                entries,
                entry,
                createEntryNameForSingleArchive(entry),
                isDirectory = false,
                itemIndex = 0
            )
        } else {
            for (itemIndex in archiveItems.indices) {
                val entry = archiveItems[itemIndex]
                addMetadataEntry(entries, entry, entry.ideaPath, entry.isFolder, itemIndex)
            }
        }
        val children = LinkedHashMap<String, MutableList<String>>()
        for (entryPath in entries.keys) {
            if (entryPath.isEmpty()) {
                continue
            }
            val separator = entryPath.lastIndexOf('/')
            val parentPath = if (separator < 0) "" else entryPath.substring(0, separator)
            val name = if (separator < 0) entryPath else entryPath.substring(separator + 1)
            children.getOrPut(parentPath) { ArrayList() }.add(name)
        }
        return MetadataIndex(
            entries = entries.toMap(),
            children = children.mapValues { (_, names) -> names.toList() }
        )
    }

    private fun addMetadataEntry(
        entries: LinkedHashMap<String, EntryMetadata>,
        archiveItem: ISimpleInArchiveItem,
        entryPath: String,
        isDirectory: Boolean,
        itemIndex: Int
    ) {
        val normalizedPath = normalizeName(entryPath)
        if (normalizedPath.isEmpty() || normalizedPath.hasParentTraversalSegment()) {
            return
        }
        if (entries.containsKey(normalizedPath)) {
            return
        }
        val separator = normalizedPath.lastIndexOf('/')
        val parentPath = if (separator < 0) "" else normalizedPath.substring(0, separator)
        ensureDirectory(entries, parentPath)
        entries[normalizedPath] = EntryMetadata(
            isDirectory = isDirectory,
            length = archiveItem.size ?: DEFAULT_LENGTH,
            timestamp = archiveItem.creationTime?.time ?: DEFAULT_TIMESTAMP,
            itemIndex = itemIndex
        )
    }

    private fun ensureDirectory(
        entries: LinkedHashMap<String, EntryMetadata>,
        directoryPath: String
    ) {
        if (entries[directoryPath]?.isDirectory == true) {
            return
        }
        if (directoryPath.isNotEmpty()) {
            val separator = directoryPath.lastIndexOf('/')
            val parentPath = if (separator < 0) "" else directoryPath.substring(0, separator)
            ensureDirectory(entries, parentPath)
        }
        entries[directoryPath] = EntryMetadata(
            isDirectory = true,
            length = DEFAULT_LENGTH,
            timestamp = DEFAULT_TIMESTAMP,
            itemIndex = null
        )
    }

    private fun String.hasParentTraversalSegment(): Boolean {
        return this == ".." || startsWith("../") || endsWith("/..") || contains("/../")
    }

    override fun getInputStream(relativePath: String): InputStream {
        return getFileHandle().getAndUse { holder ->
            holder.inputStream(holder.getItemForPath(relativePath))
        }
    }

    override fun getInputStreamForFile(file: VirtualFile): InputStream {
        return getFileHandle().getAndUse { holder ->
            val item = holder.getItemForPath(file.path.split(FSUtils.FS_SEPARATOR).last())
            holder.inputStream(item)
        }
    }

    private fun IInArchive.isSingleFileArchive(): Boolean {
        return (numberOfItems == 1 && archiveFormat == null) || archiveFormat?.supportMultipleFiles() == false
    }

    private fun SevenZipArchiveHolder.getItemForPath(relativePath: String): ISimpleInArchiveItem {
        val itemIndex = if (archive.isSingleFileArchive()) {
            0
        } else {
            metadataIndex(this).entries[relativePath]?.itemIndex
        }
        return itemIndex?.let { archiveItems.getOrNull(it) }
            ?: throw FileNotFoundException("$path!/$relativePath")
    }

    private fun SevenZipArchiveHolder.inputStream(entry: ISimpleInArchiveItem): InputStream {
        return SevenZipInputStream(this, entry)
    }

    private val ISimpleInArchiveItem.ideaPath get() = FSUtils.convertPathToIdea(path)

    @Volatile
    private var metadataCache: CachedMetadata? = null
    private val metadataLock = Any()

    private data class EntryMetadata(
        val isDirectory: Boolean,
        val length: Long,
        val timestamp: Long,
        val itemIndex: Int?
    )

    private data class MetadataIndex(
        val entries: Map<String, EntryMetadata>,
        val children: Map<String, List<String>>
    )

    private data class CachedMetadata(
        val holder: WeakReference<SevenZipArchiveHolder>,
        val index: MetadataIndex
    )

    companion object : CacheProvider<SevenZipArchiveHolder> by cacheProvider(
        onCreate = { SevenZipArchiveHolder(it.path.toFile().canonicalFile) }
    ) {
        private val LOG = Logger.getInstance(SevenZipArchiveHandler::class.java)
    }
}
