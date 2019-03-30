package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.FSUtils
import com.github.b3er.idea.plugins.arc.browser.base.sevenzip.SevenZipArchiveFileSystem
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import javax.swing.Icon

class SevenZipArchiveFileSystemImpl :
    SevenZipArchiveFileSystem(SevenZipArchiveFileType, SZ_PROTOCOL, FSUtils.FS_SEPARATOR) {
    companion object {
        private const val SZ_PROTOCOL = "7zip"
        val instance: SevenZipArchiveFileSystem
            get() = VirtualFileManager.getInstance().getFileSystem(SZ_PROTOCOL) as SevenZipArchiveFileSystem
    }
}

object SevenZipArchiveFileType : FileType {
    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon? = AllIcons.FileTypes.Archive

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    override fun getName() = "7ZIP"

    override fun getDescription() = "7zip archive"

    override fun isBinary() = true

    override fun isReadOnly() = false
}