package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.FSUtils
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.BaseCompressedStreamFile
import com.github.b3er.idea.plugins.arc.browser.getAndUse
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.FileAccessorCache
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class TarGzHandler(path: String) : BaseArchiveHandler<TarFile>(path) {
    override val accessorCache: FileAccessorCache<BaseArchiveHandler<TarFile>, TarFile> = CACHE

    companion object {
        private val CACHE = createCache<TarFile>(
            onCreate = {
                if (supportExtensions(it.file.canonicalPath)) {
                    TarFile(it.file.canonicalFile, GZipFile(it.file.canonicalFile))
                } else {
                    throw IllegalArgumentException("Can't handle file ${it.file}")
                }
            }
        )
        const val TAR_EXTENSION = ".tar.gz"
        const val TAR_GZ_EXTENSION = ".tgz"
        fun supportExtensions(path: String): Boolean {
            val pathLowerCase = path.toLowerCase()
            return pathLowerCase.endsWith(TAR_EXTENSION) || pathLowerCase.endsWith(TAR_GZ_EXTENSION)
        }
    }

    override fun createEntriesMap(): MutableMap<String, EntryInfo> {
        return getFileHandle().getAndUse { holder ->
            val entries = holder.entries
            LinkedHashMap<String, EntryInfo>(entries.size).also { map ->
                map[""] = createRootEntry()
                entries.forEach { entry ->
                    getOrCreate(entry, map, holder)
                }
            }
        }
    }

    override fun contentsToByteArray(relativePath: String): ByteArray {
        return getFileHandle().getAndUse { holder ->
            val item = holder.entries.firstOrNull { FSUtils.convertPathToIdea(it.name) == relativePath }
                ?: throw FileNotFoundException("$file!/$relativePath")
            if (FileUtilRt.isTooLarge(item.size)) {
                throw FileTooBigException("$file!/$relativePath")
            } else {
                holder.getInputStream(item).use { stream ->
                    FileUtil.loadBytes(stream)
                }
            }
        }
    }

    override fun getInputStream(relativePath: String): InputStream {
        return getFileHandle().getAndUse { holder ->
            val item = holder.entries.firstOrNull { FSUtils.convertPathToIdea(it.name) == relativePath }
                ?: throw FileNotFoundException("$file!/$relativePath")
            holder.getInputStream(item)
        }
    }

    private fun getOrCreate(
        item: TarArchiveEntry,
        map: LinkedHashMap<String, EntryInfo>,
        archive: TarFile
    ): EntryInfo {
        val entryName = FSUtils.convertPathToIdea(item.name)
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
            item.isDirectory,
            item.size,
            item.modTime?.time ?: DEFAULT_TIMESTAMP,
            parentInfo
        )
        map[entryName] = info
        return info
    }

    private fun getOrCreate(
        entryName: String,
        map: LinkedHashMap<String, EntryInfo>,
        archive: TarFile
    ): EntryInfo {
        var info = map[entryName]
        if (info == null) {
            val item = archive.entries.firstOrNull { FSUtils.convertPathToIdea(it.name) == entryName }
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
}

class TarFile(private val file: File, private val parent: BaseCompressedStreamFile) {
    fun getInputStream(item: TarArchiveEntry): InputStream {
        val stream = TarArchiveInputStream(parent.inputStream())
        var entry = stream.nextTarEntry
        while (entry != null) {
            if (entry == item) {
                return stream
            }
            entry = stream.nextTarEntry
        }
        stream.close()
        throw FileNotFoundException("$file!/${item.name}")
    }

    val entries: List<TarArchiveEntry> by lazy {
        val result = ArrayList<TarArchiveEntry>()
        TarArchiveInputStream(parent.inputStream()).use { stream ->
            var entry = stream.nextTarEntry
            while (entry != null) {
                result.add(entry)
                entry = stream.nextTarEntry
            }
        }
        result
    }
}