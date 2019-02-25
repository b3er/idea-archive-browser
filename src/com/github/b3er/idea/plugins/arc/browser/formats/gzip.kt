package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.processChildren
import com.github.b3er.idea.plugins.arc.browser.use
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.FileAccessorCache
import com.intellij.util.io.URLUtil
import com.jcraft.jzlib.GZIPInputStream
import java.io.*
import java.util.*
import javax.swing.Icon

class PsiGZipFileNode(project: Project?, value: PsiFile,
    viewSettings: ViewSettings?) : PsiFileNode(project, value, viewSettings) {
  override fun getChildrenImpl(): MutableCollection<AbstractTreeNode<*>> {
    val project = project
    val gzipRoot = virtualFile?.let { GZipFileSystem.instance.getGZipRootForLocalFile(it) }
    if (project != null && gzipRoot != null) {
      val psiDirectory = PsiManager.getInstance(project).findDirectory(gzipRoot)
      if (psiDirectory != null) {
        return processChildren(psiDirectory)
      }
    }
    return ContainerUtil.emptyList<AbstractTreeNode<*>>()
  }
}

object GZipFileType : FileType {
  override fun getDefaultExtension(): String = "gz"

  override fun getIcon(): Icon? = AllIcons.FileTypes.Archive

  override fun getCharset(file: VirtualFile, content: ByteArray) = null

  override fun getName() = "GZIP"

  override fun getDescription() = "Gzipped file"

  override fun isBinary() = true

  override fun isReadOnly() = false

}

abstract class GZipFileSystem : ArchiveFileSystem(), LocalFileProvider {
  companion object {
    const val GZIP_PROTOCOL = "gzip"
    const val GZIP_SEPARATOR = URLUtil.JAR_SEPARATOR
    val instance: GZipFileSystem
      get() = VirtualFileManager.getInstance().getFileSystem(GZIP_PROTOCOL) as GZipFileSystem
  }

  private fun getVirtualFileForGZip(entryFile: VirtualFile?): VirtualFile? {
    return if (entryFile == null) null else getLocalByEntry(entryFile)
  }

  fun getGZipRootForLocalFile(file: VirtualFile): VirtualFile? {
    return getRootByLocal(file)
  }

  override fun getLocalVirtualFileFor(entryVFile: VirtualFile?): VirtualFile? {
    return getVirtualFileForGZip(entryVFile)
  }

  override fun findLocalVirtualFileByPath(_path: String): VirtualFile? {
    var path = _path
    if (!path.contains(GZIP_SEPARATOR)) {
      path += GZIP_SEPARATOR
    }
    return findFileByPath(path)
  }

  override fun isCorrectFileType(local: VirtualFile): Boolean {
    return FileTypeRegistry.getInstance().getFileTypeByFileName(local.name) == GZipFileType
  }
}

class GZipFileSystemImpl : GZipFileSystem() {
  override fun getProtocol() = GZIP_PROTOCOL

  override fun extractPresentableUrl(path: String) = super.extractPresentableUrl(
      StringUtil.trimEnd(path, GZIP_SEPARATOR))

  override fun normalize(path: String): String? {
    val gzipSeparatorIndex = path.indexOf(GZIP_SEPARATOR)
    if (gzipSeparatorIndex > 0) {
      val root = path.substring(0, gzipSeparatorIndex)
      return FileUtil.normalize(root) + path.substring(gzipSeparatorIndex)
    }
    return super.normalize(path)
  }

  override fun extractRootPath(path: String): String {
    val gzipSeparatorIndex = path.indexOf(GZIP_SEPARATOR)
    assert(
        gzipSeparatorIndex >= 0) { "Path passed to GZipFileSystem must have gzip separator '!/': $path" }
    return path.substring(0, gzipSeparatorIndex + GZIP_SEPARATOR.length)
  }

  override fun extractLocalPath(rootPath: String): String {
    return StringUtil.trimEnd(rootPath, GZIP_SEPARATOR)
  }

  override fun composeRootPath(localPath: String): String {
    return localPath + GZIP_SEPARATOR
  }

  override fun getHandler(entryFile: VirtualFile): GZipHandler {
    return VfsImplUtil.getHandler(this, entryFile)  { localPath ->
      GZipHandler(localPath)
    }
  }

  override fun findFileByPath(path: String): VirtualFile? {
    return VfsImplUtil.findFileByPath(this, path)
  }

  override fun findFileByPathIfCached(path: String): VirtualFile? {
    return VfsImplUtil.findFileByPathIfCached(this, path)
  }

  override fun refreshAndFindFileByPath(path: String): VirtualFile? {
    return VfsImplUtil.refreshAndFindFileByPath(this, path)
  }

  override fun refresh(asynchronous: Boolean) {
    VfsImplUtil.refresh(this, asynchronous)
  }
}

class GZipHandler(path: String) : ArchiveHandler(path) {
  @Volatile private var myFileStamp: Long = DEFAULT_TIMESTAMP
  @Volatile private var myFileLength: Long = DEFAULT_LENGTH

  companion object {
    private val accessorCache = object : FileAccessorCache<GZipHandler, GZipFile>(20, 10) {
      override fun createAccessor(key: GZipHandler): GZipFile {
        val attributes = FileSystemUtil.getAttributes(key.file.canonicalFile)
        key.myFileStamp = attributes?.lastModified ?: ArchiveHandler.DEFAULT_TIMESTAMP
        key.myFileLength = attributes?.length ?: ArchiveHandler.DEFAULT_LENGTH
        return GZipFile(key.file.canonicalFile)
      }

      override fun disposeAccessor(fileAccessor: GZipFile) {

      }
    }
  }

  override fun contentsToByteArray(relativePath: String): ByteArray {
    val handle = getGZipFileHandle()
    return handle.use { gzip ->
      gzip.inputStream().use {
        FileUtil.loadBytes(it, gzip.length.toInt())
      }
    }
  }

  private fun getGZipFileHandle(): FileAccessorCache.Handle<GZipFile> {
    val handle = accessorCache[this]
    val attributes = file.canonicalFile.let {
      FileSystemUtil.getAttributes(it) ?: throw FileNotFoundException(it.toString())
    }
    if (attributes.lastModified == myFileStamp && attributes.length == myFileLength) {
      return handle
    }
    accessorCache.remove(this)
    handle.release()
    return accessorCache[this]
  }

  override fun createEntriesMap(): MutableMap<String, EntryInfo> {
    return getGZipFileHandle().use { gzip ->
      val map = HashMap<String, EntryInfo>()
      val root = EntryInfo("", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, null)
      map[""] = root
      map[gzip.name] = EntryInfo(gzip.name, false, gzip.length, gzip.timestamp, root)
      map
    }
  }

  override fun dispose() {
    super.dispose()
    accessorCache.remove(this)
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

