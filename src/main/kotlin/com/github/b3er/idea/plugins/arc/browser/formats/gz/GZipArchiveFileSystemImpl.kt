package com.github.b3er.idea.plugins.arc.browser.formats.gz

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.util.FSUtils
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFileManager

class GZipArchiveFileSystemImpl : BaseArchiveFileSystem(PROTOCOL, FSUtils.FS_SEPARATOR) {

  companion object {
    private const val PROTOCOL = "gz"
    val instance: BaseArchiveFileSystem
      get() = VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as BaseArchiveFileSystem
  }

  override fun getHandlerForPath(localPath: String): BaseArchiveHandler<*> {
    return GZipArchiveHandler(localPath)
  }

  override fun isCorrectFileType(fileType: FileType): Boolean = fileType is GzipArchiveFileType
}

