package com.github.b3er.idea.plugins.arc.browser.base.compress

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.base.nest.SupportsNestedArchives
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.openapi.vfs.newvfs.VfsImplUtil

abstract class CompressArchiveFileSystem(
  fsProtocol: String,
  fsSeparator: String
) : BaseArchiveFileSystem(fsProtocol, fsSeparator), SupportsNestedArchives {
  abstract fun getHandlerForPath(localPath: String): CompressArchiveHandler<*, *>
  abstract fun isCorrectFileType(fileType: FileType): Boolean

  override fun getHandler(entryFile: VirtualFile): ArchiveHandler {
    return VfsImplUtil.getHandler(this, entryFile) { localPath ->
      getHandlerForPath(localPath)
    }
  }

  override fun getHandlerForFile(file: VirtualFile): ArchiveHandler {
    return getHandler(file)
  }

  override fun isCorrectFileType(local: VirtualFile): Boolean {
    return isCorrectFileType(FileTypeRegistry.getInstance().getFileTypeByFileName(local.name))
  }
}