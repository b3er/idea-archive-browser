package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.BasePsiFileNode
import com.github.b3er.idea.plugins.arc.browser.use
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.psi.PsiFile
import com.intellij.util.io.FileAccessorCache
import com.intellij.util.io.URLUtil
import com.jcraft.jzlib.GZIPInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.*
import javax.swing.Icon

class PsiGZipFileNode(
    project: Project?, value: PsiFile,
    viewSettings: ViewSettings?
) : BasePsiFileNode(project, value, viewSettings) {
    override fun getChildrenImpl(): MutableCollection<AbstractTreeNode<*>> {
        val gzipRoot = virtualFile?.let { GZipFileSystem.instance.getArchiveRootForLocalFile(it) }
        return getChildrenForVirtualFile(gzipRoot)
    }
}

object GZipFileType : FileType {
    override fun getDefaultExtension(): String = "gz"

    override fun getIcon(): Icon? = AllIcons.FileTypes.Archive

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    override fun getName() = "GZIP"

    override fun getDescription() = "Gzipped file"

    override fun isBinary() = true

    override fun isReadOnly() = false

}

abstract class GZipFileSystem : BaseArchiveFileSystem(GZipFileType, GZIP_PROTOCOL, GZIP_SEPARATOR) {
    companion object {
        const val GZIP_PROTOCOL = "gzip"
        const val GZIP_SEPARATOR = URLUtil.JAR_SEPARATOR
        val instance: GZipFileSystem
            get() = VirtualFileManager.getInstance().getFileSystem(GZIP_PROTOCOL) as GZipFileSystem
    }
}

class GZipFileSystemImpl : GZipFileSystem() {
    override fun getHandler(entryFile: VirtualFile): GZipHandler {
        return VfsImplUtil.getHandler(this, entryFile) { localPath ->
            GZipHandler(localPath)
        }
    }
}

class GZipHandler(path: String) : BaseArchiveHandler<GZipFile>(path) {
    override val accessorCache: FileAccessorCache<BaseArchiveHandler<GZipFile>, GZipFile> = CACHE

    companion object {
        private val CACHE = createCache<GZipFile>(
            onCreate = { GZipFile(it.file.canonicalFile) }
        )
    }

    override fun contentsToByteArray(relativePath: String): ByteArray {
        val handle = getFileHandle()
        return handle.use { gzip ->
            gzip.inputStream().use {
                // Assume that there is no length, so just give the stream to idea
                FileUtil.loadBytes(it)
            }
        }
    }

    override fun createEntriesMap(): MutableMap<String, EntryInfo> {
        return getFileHandle().use { gzip ->
            val map = HashMap<String, EntryInfo>()
            val root = EntryInfo("", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, null)
            map[""] = root
            map[gzip.name] = EntryInfo(gzip.name, false, gzip.length, gzip.timestamp, root)
            map
        }
    }
}

class GZipFile(private val file: File) {
    companion object {
        private val GZ_EXT_PATTERN = "\\.gz$".toRegex()
    }

    var length = 0L
        private set
    var name = ""
        private set
    var timestamp = 0L
        private set

    init {
        GZIPInputStream(file.inputStream()).use {
            it.readHeader()
            timestamp = it.modifiedtime
            name = if (!it.name.isNullOrEmpty()) it.name else file.name.replace(GZ_EXT_PATTERN, "")
        }
        //read uncompressed size according to RFC 1952 (http://www.zlib.org/rfc-gzip.html)
        length = RandomAccessFile(file, "r").use {
            it.seek(it.length() - 4)
            it.readUnsignedInt()
        }
    }

    fun inputStream(): InputStream {
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
}

