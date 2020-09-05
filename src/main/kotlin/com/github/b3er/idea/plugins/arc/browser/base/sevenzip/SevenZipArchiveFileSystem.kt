package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.github.b3er.idea.plugins.arc.browser.FSUtils
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.github.b3er.idea.plugins.arc.browser.formats.SevenZipArchiveFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import net.sf.sevenzipjbinding.SevenZip

abstract class SevenZipArchiveFileSystem(
    fileType: FileType,
    fsProtocol: String,
    fsSeparator: String
) : BaseArchiveFileSystem(fsProtocol, fsSeparator) {
    companion object {
        init {
            if (!SevenZip.isInitializedSuccessfully()) {
                val tmpDir = FSUtils.getPluginTempFolder()
                if (!tmpDir.exists()) {
                    tmpDir.mkdirs()
                }
                SevenZip.initSevenZipFromPlatformJAR(tmpDir)
            }
        }
    }

    override fun getHandler(entryFile: VirtualFile): ArchiveHandler {
        return VfsImplUtil.getHandler(this, entryFile) { localPath ->
            SevenZipArchiveHandler(localPath)
        }
    }

    fun getHandlerForFile(file: VirtualFile): ArchiveHandler? {
        return getHandler(file)
    }

    override fun isCorrectFileType(local: VirtualFile): Boolean {
        return FileTypeRegistry.getInstance().getFileTypeByFileName(local.name) is SevenZipArchiveFileType
    }
}