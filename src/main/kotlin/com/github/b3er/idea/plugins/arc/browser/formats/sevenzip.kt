package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.FSUtils
import com.github.b3er.idea.plugins.arc.browser.base.compress.CompressArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.compress.CompressArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.formats.sevenzip.SevenZipArchiveHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import javax.swing.Icon

class SevenZipArchiveFileSystemImpl : CompressArchiveFileSystem(SZ_PROTOCOL, FSUtils.FS_SEPARATOR) {

  companion object {
    private const val SZ_PROTOCOL = "7z"
    val instance: CompressArchiveFileSystem
      get() = VirtualFileManager.getInstance().getFileSystem(SZ_PROTOCOL) as CompressArchiveFileSystem
  }

  override fun getHandlerForPath(localPath: String): CompressArchiveHandler<*, *> {
    return SevenZipArchiveHandler(localPath)
  }

  override fun isCorrectFileType(fileType: FileType): Boolean = fileType is SevenZipArchiveFileType
}

class SevenZipArchiveFileType : FileType {
  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = AllIcons.FileTypes.Archive

  override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

  override fun getName() = "ARCHIVE_7ZIP"

  override fun getDescription() = "7zip archive"

  override fun isBinary() = true

  companion object {
    @JvmStatic
    val INSTANCE = SevenZipArchiveFileType()
  }
}