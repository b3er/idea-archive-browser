package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.BasePsiFileNode
import com.github.b3er.idea.plugins.arc.browser.base.io.HandleInputStreamWrapper
import com.github.b3er.idea.plugins.arc.browser.use
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.psi.PsiFile
import com.intellij.util.io.FileAccessorCache
import com.intellij.util.io.URLUtil
import com.intellij.util.text.ByteArrayCharSequence
import de.innosystec.unrar.Archive
import de.innosystec.unrar.NativeStorage
import de.innosystec.unrar.exception.RarException
import de.innosystec.unrar.rarfile.FileHeader
import java.io.*
import javax.swing.Icon

class PsiRarFileNode(
    project: Project?, value: PsiFile,
    viewSettings: ViewSettings?
) : BasePsiFileNode(project, value, viewSettings) {
    override fun getChildrenImpl(): MutableCollection<AbstractTreeNode<*>> {
        val gzipRoot = virtualFile?.let { RarFileSystem.instance.getArchiveRootForLocalFile(it) }
        return getChildrenForVirtualFile(gzipRoot)
    }
}

object RarFileType : FileType {
    override fun getDefaultExtension(): String = "rar"

    override fun getIcon(): Icon? = AllIcons.FileTypes.Archive

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    override fun getName() = "RAR"

    override fun getDescription() = "RAR archive"

    override fun isBinary() = true

    override fun isReadOnly() = false
}


abstract class RarFileSystem : BaseArchiveFileSystem(RarFileType, RAR_PROTOCOL, RAR_SEPARATOR) {
    companion object {
        private const val RAR_PROTOCOL = "rar"
        private const val RAR_SEPARATOR = URLUtil.JAR_SEPARATOR
        val instance: RarFileSystem
            get() = VirtualFileManager.getInstance().getFileSystem(RAR_PROTOCOL) as RarFileSystem
    }
}

class RarFileSystemImpl : RarFileSystem() {
    override fun getHandler(entryFile: VirtualFile): RarHandler {
        return VfsImplUtil.getHandler(this, entryFile) { localPath ->
            RarHandler(localPath)
        }
    }
}

class RarHandler(path: String) : BaseArchiveHandler<Archive>(path) {
    override val accessorCache: FileAccessorCache<BaseArchiveHandler<Archive>, Archive> = CACHE
    companion object {
        private val CACHE = createCache(
            onCreate = { Archive(NativeStorage(it.file.canonicalFile)) },
            onDispose = Archive::close
        )
    }

    override fun createEntriesMap(): MutableMap<String, EntryInfo> {
        return getFileHandle().use { archive ->
            LinkedHashMap<String, EntryInfo>(archive.fileHeaders.size).also { map ->
                map[""] = createRootEntry()
                archive.fileHeaders.forEach { header ->
                    getOrCreate(header, map, archive)
                }
            }
        }
    }

    private fun getOrCreate(
        header: FileHeader,
        map: LinkedHashMap<String, EntryInfo>,
        archive: Archive
    ): EntryInfo {
        val entryName = header.fileNameW.replace('\\', '/')
        var info = map[entryName]
        if (info != null) {
            return info
        }
        val path = splitPath(entryName)
        val parentInfo = getOrCreate(path.first, map, archive)
        if ("." == path.second) {
            return parentInfo
        }
        info = EntryInfo(
            ByteArrayCharSequence.convertToBytesIfAsciiString(path.second),
            header.isDirectory,
            header.fullUnpackSize,
            header.mTime?.time ?: DEFAULT_TIMESTAMP,
            parentInfo
        )
        map[entryName] = info
        return info
    }

    private fun getOrCreate(
        entryName: String,
        map: LinkedHashMap<String, EntryInfo>,
        archive: Archive
    ): EntryInfo {
        var info = map[entryName]
        if (info == null) {
            val header = archive.fileHeaders.firstOrNull { it.fileNameW.replace('\\', '/') == entryName }
            if (header != null) {
                return getOrCreate(header, map, archive)
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

    override fun contentsToByteArray(relativePath: String): ByteArray {
        return getFileHandle().use { archive ->
            val header = archive.fileHeaders.firstOrNull { header ->
                header.fileNameW.replace('\\', '/') == relativePath
            } ?: throw FileNotFoundException("$file!/$relativePath")

            if (FileUtilRt.isTooLarge(header.unpSize)) {
                throw FileTooBigException("$file!/$relativePath")
            }
            getInputStreamForHeader(archive, header).use {
                FileUtil.loadBytes(it)
            }
        }
    }

    override fun getInputStream(relativePath: String): InputStream {
        var release = true
        val handle = getFileHandle()
        try {
            val archive = handle.get()
            val header = archive.fileHeaders.firstOrNull { header ->
                header.fileNameW.replace('\\', '/') == relativePath
            } ?: throw FileNotFoundException("$file!/$relativePath")
            return if (!FileUtilRt.isTooLarge(header.unpSize)) {
                getInputStreamForHeader(archive, header).use {
                    BufferExposingByteArrayInputStream(FileUtil.loadBytes(it))
                }
            } else {
                release = false
                HandleInputStreamWrapper(handle, getInputStreamForHeader(archive, header))
            }
        } finally {
            if (release) {
                handle.close()
            }
        }
    }

    private fun getInputStreamForHeader(archive: Archive, header: FileHeader): InputStream {
        val out = ByteArrayOutputStream()
        try {
            archive.extractFile(header, out)
        } catch (e: RarException) {
            throw RarException(e)
        } finally {
            try {
                out.close()
            } catch (e: IOException) {
                throw RarException(e)
            }

        }
        return ByteArrayInputStream(out.toByteArray())
    }
}

