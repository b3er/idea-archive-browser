package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.github.b3er.idea.plugins.arc.browser.AppInfoUtil
import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileSystem
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import net.sf.sevenzipjbinding.SevenZip
import java.io.File

abstract class SevenZipArchiveFileSystem(
    fileType: FileType,
    fsProtocol: String,
    fsSeparator: String
) : BaseArchiveFileSystem(fileType, fsProtocol, fsSeparator) {
    companion object {
        init {
            if (!SevenZip.isInitializedSuccessfully()) {
                val tmpDir = System.getProperty("java.io.tmpdir")
                    ?: throw IllegalArgumentException("can't determine tmp directory. Use may use -Djava.io.tmpdir=<path to tmp dir> parameter for jvm to fix this.")
                // SevenZip can't extract platform libs if they already used in other product, so we need to split dirs
                val productTempDir =
                    File(File(tmpDir, "archive-browser-idea"), AppInfoUtil.applicationInfo.build.productCode)
                val buildNumber = AppInfoUtil.applicationInfo.build.asStringWithoutProductCode()
                // Cleanup old builds
                if (productTempDir.exists()) {
                    productTempDir.listFiles().filter { it.name != buildNumber }.forEach { it.deleteRecursively() }
                }
                val targetDir = File(productTempDir, buildNumber)
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                SevenZip.initSevenZipFromPlatformJAR(targetDir)
            }
        }
    }

    override fun getHandler(entryFile: VirtualFile): SevenZipArchiveHandler {
        return VfsImplUtil.getHandler(this, entryFile) { localPath ->
            SevenZipArchiveHandler(localPath)
        }
    }
}