package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.BaseCompressedStreamFile
import com.github.b3er.idea.plugins.arc.browser.base.BasePsiFileNode
import com.github.b3er.idea.plugins.arc.browser.getAndUse
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.psi.PsiFile
import com.intellij.util.io.FileAccessorCache
import com.jcraft.jzlib.GZIPInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import javax.swing.Icon

class GZipFileNode(
    project: Project?, value: PsiFile,
    viewSettings: ViewSettings?
) : BasePsiFileNode(project, value, viewSettings) {
    override fun getChildrenImpl(): MutableCollection<AbstractTreeNode<*>> {
        val parentChildren = super.getChildrenImpl()
        // If parent can traverse children, return as is
        return if (parentChildren != null && parentChildren.isNotEmpty()) {
            parentChildren
        } else {
            getChildrenForVirtualFile(virtualFile?.let { GZipFileSystem.instance.getArchiveRootForLocalFile(it) })
        }
    }
}

object GzipFileType : FileType {
    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon? = AllIcons.FileTypes.Archive

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    override fun getName() = "Gzip"

    override fun getDescription() = "Gzip archive"

    override fun isBinary() = true

    override fun isReadOnly() = false
}

class GZipFileSystem : BaseArchiveFileSystem(GZ_PROTOCOL) {
    companion object {
        const val GZ_PROTOCOL = "gzip"
        val instance
            get() = VirtualFileManager.getInstance().getFileSystem(GZ_PROTOCOL) as GZipFileSystem
    }

    override fun getHandler(entryFile: VirtualFile): ArchiveHandler {
        return VfsImplUtil.getHandler(this, entryFile) { localPath ->
            if (TarGzHandler.supportExtensions(localPath)) {
                TarGzHandler(localPath)
            } else {
                GZipHandler(localPath)
            }

        }
    }

    override fun isCorrectFileType(local: VirtualFile): Boolean {
        return FileTypeRegistry.getInstance().getFileTypeByFileName(local.name) === GzipFileType
    }
}

open class GZipHandler(path: String) : BaseArchiveHandler<GZipFile>(path) {
    override val accessorCache: FileAccessorCache<BaseArchiveHandler<GZipFile>, GZipFile> = CACHE

    companion object {
        private val CACHE = createCache<GZipFile>(
            onCreate = { GZipFile(it.file.canonicalFile) }
        )
    }

    override fun contentsToByteArray(relativePath: String): ByteArray {
        return getFileHandle().getAndUse { gzip ->
            gzip.inputStream().use {
                // Assume that there is no length, so just give the stream to idea
                FileUtil.loadBytes(it)
            }
        }
    }

    override fun createEntriesMap(): MutableMap<String, EntryInfo> {
        return getFileHandle().getAndUse { gzip ->
            val map = HashMap<String, EntryInfo>()
            val root = EntryInfo("", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, null)
            map[""] = root
            map[gzip.name] = EntryInfo(gzip.name, false, gzip.length, gzip.timestamp, root)
            map
        }
    }

    override fun getInputStream(relativePath: String): InputStream {
        return getFileHandle().getAndUse { gzip ->
            gzip.inputStream()
        }
    }
}

open class GZipFile(private val file: File) : BaseCompressedStreamFile {
    private val metadata: GzipParameters? by lazy {
        GzipCompressorInputStream(file.inputStream()).use {
            it.metaData
        }
    }
    override val length by lazy {
        RandomAccessFile(file, "r").use {
            it.seek(it.length() - 4)
            it.readUnsignedInt()
        }
    }
    override val name by lazy {
        metadata?.filename.let { fileName ->
            if (fileName.isNullOrEmpty()) {
                file.name.let { name -> name.substring(0, name.lastIndexOf('.')) }
            } else {
                fileName
            }
        }
    }
    override val timestamp by lazy {
        metadata?.modificationTime ?: ArchiveHandler.DEFAULT_TIMESTAMP
    }

    override fun inputStream(): InputStream {
        return GZIPInputStream(file.inputStream())
    }

    private fun RandomAccessFile.readUnsignedInt(): Long {
        val ch1 = this.read()
        val ch2 = this.read()
        val ch3 = this.read()
        val ch4 = this.read()
        if (ch1 or ch2 or ch3 or ch4 < 0) throw EOFException()
        return (ch1 shl 0) + (ch2 shl 8) + (ch3 shl 16) + (ch4.toLong() shl 24)
    }

    companion object {
        const val EXTENSION = ".gz"
    }
}