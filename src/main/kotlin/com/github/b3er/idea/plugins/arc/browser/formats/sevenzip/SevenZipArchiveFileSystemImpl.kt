package com.github.b3er.idea.plugins.arc.browser.formats.sevenzip

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.sevenzip.SevenZipArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.util.FSUtils
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFileManager

class SevenZipArchiveFileSystemImpl : BaseArchiveFileSystem(PROTOCOL, FSUtils.FS_SEPARATOR) {

  companion object {
    private const val PROTOCOL = "7z"
    val instance: BaseArchiveFileSystem
      get() = VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as BaseArchiveFileSystem
  }

  override fun getHandlerForPath(localPath: String): SevenZipArchiveHandler {
    return SevenZipArchiveHandler(localPath)
  }

  override fun isCorrectFileType(fileType: FileType): Boolean = fileType is SevenZipArchiveFileType
}

