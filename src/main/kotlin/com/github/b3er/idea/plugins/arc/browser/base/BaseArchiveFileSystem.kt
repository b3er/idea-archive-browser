package com.github.b3er.idea.plugins.arc.browser.base

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.newvfs.VfsImplUtil

abstract class BaseArchiveFileSystem(
    protected val fileType: FileType,
    protected val fsProtocol: String,
    protected val fsSeparator: String
) : ArchiveFileSystem(), LocalFileProvider {
    private fun getVirtualFileForArchive(entryFile: VirtualFile?): VirtualFile? {
        return if (entryFile == null) null else getLocalByEntry(entryFile)
    }

    fun getArchiveRootForLocalFile(file: VirtualFile): VirtualFile? {
        return getRootByLocal(file)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun getLocalVirtualFileFor(entryVFile: VirtualFile?): VirtualFile? {
        return getVirtualFileForArchive(entryVFile)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun findLocalVirtualFileByPath(_path: String): VirtualFile? {
        var path = _path
        if (!path.contains(fsSeparator)) {
            path += fsSeparator
        }
        return findFileByPath(path)
    }

    override fun getProtocol() = fsProtocol

    override fun isCorrectFileType(local: VirtualFile): Boolean {
        return FileTypeRegistry.getInstance().getFileTypeByFileName(local.name) == fileType
    }

    override fun extractPresentableUrl(path: String) = super.extractPresentableUrl(
        StringUtil.trimEnd(path, fsSeparator)
    )

    override fun normalize(path: String): String? {
        val gzipSeparatorIndex = path.indexOf(fsSeparator)
        if (gzipSeparatorIndex > 0) {
            val root = path.substring(0, gzipSeparatorIndex)
            return FileUtil.normalize(root) + path.substring(gzipSeparatorIndex)
        }
        return super.normalize(path)
    }

    override fun extractRootPath(path: String): String {
        val gzipSeparatorIndex = path.indexOf(fsSeparator)
        assert(
            gzipSeparatorIndex >= 0
        ) { "Path passed to ${this.javaClass.simpleName} must have separator '!/': $path" }
        return path.substring(0, gzipSeparatorIndex + fsSeparator.length)
    }

    override fun extractLocalPath(rootPath: String): String {
        return StringUtil.trimEnd(rootPath, fsSeparator)
    }

    override fun composeRootPath(localPath: String): String {
        return localPath + fsSeparator
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
