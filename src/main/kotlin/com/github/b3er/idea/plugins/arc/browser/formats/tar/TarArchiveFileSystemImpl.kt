package com.github.b3er.idea.plugins.arc.browser.formats.tar

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.compress.CompressArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.util.FSUtils
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFileManager

class TarArchiveFileSystemImpl : BaseArchiveFileSystem(PROTOCOL, FSUtils.FS_SEPARATOR) {

  companion object {
    private const val PROTOCOL = "tar"
    val instance: BaseArchiveFileSystem
      get() = VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as BaseArchiveFileSystem
  }

  override fun getHandlerForPath(localPath: String): CompressArchiveHandler<*, *> {
    return TarArchiveHandler(localPath)
  }

  override fun isCorrectFileType(fileType: FileType): Boolean = fileType is TarArchiveFileType
}

