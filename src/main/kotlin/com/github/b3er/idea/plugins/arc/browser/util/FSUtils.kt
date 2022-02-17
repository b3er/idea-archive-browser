package com.github.b3er.idea.plugins.arc.browser.util

import com.github.b3er.idea.plugins.arc.browser.base.nest.SupportsNestedArchives
import com.github.b3er.idea.plugins.arc.browser.base.nest.SupportsStreamForVirtualFile
import com.github.b3er.idea.plugins.arc.browser.base.sevenzip.SevenZipInputStream
import com.google.common.hash.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.util.io.URLUtil
import net.sf.sevenzipjbinding.ExtractOperationResult
import org.apache.commons.lang.StringUtils
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files


object FSUtils {
  const val FS_SEPARATOR = URLUtil.JAR_SEPARATOR
  private const val NESTED_FILES_ROOT = "archives"

  fun isNestedFile(path: String): Boolean {
    return StringUtils.countMatches(path, FS_SEPARATOR) > 0
  }

  fun decorateMergedNameWithExtension(fileExtension: String, name: String): String {
    val extension = MERGED_ARCHIVE_EXTENSIONS[fileExtension.toLowerCase()]
    return if (extension != null && !name.endsWith(extension, ignoreCase = true)) {
      "$name.$extension"
    } else {
      name
    }
  }

  private val logger = thisLogger()

  @Suppress("DEPRECATION", "UnstableApiUsage")
  fun copyFileToTemp(file: VirtualFile): File {
    val nestedFilesRoot = File(getPluginTempFolder(), NESTED_FILES_ROOT)
    if (!nestedFilesRoot.exists()) {
      nestedFilesRoot.mkdirs()
    }
    val handler = (file.fileSystem as? SupportsNestedArchives)?.getHandlerForFile(file)

    val hasher = Hashing.md5()
      .newHasher()
      .putString(file.name, Charset.defaultCharset())

    if (handler?.isSingleFileArchive() == true) {
      val attributes = FileSystemUtil.getAttributes(handler.file.canonicalFile)
      hasher.putLong(attributes?.lastModified ?: ArchiveHandler.DEFAULT_TIMESTAMP)
      hasher.putLong(attributes?.length ?: ArchiveHandler.DEFAULT_LENGTH)
    }

    val id = hasher
      .putLong(file.timeStamp)
      .putLong(file.length)
      .hash()
      .toString()

    val outFolder = File(nestedFilesRoot, id)
    if (!outFolder.exists()) {
      outFolder.mkdirs()
    }

    val outFile = File(outFolder, file.name)
    if (!outFile.exists()) {
      if (!tryToDirectCopyFile(file, outFile)) {
        val stream = file.inputStream
        file.inputStream.use {
          Files.copy(stream, outFile.toPath())
        }
      }
    }
    return outFile
  }

  private fun getPluginTempFolder(): File {
    val tmpDir = PathManager.getTempPath()
    return File(tmpDir, PluginUtils.PLUGIN_NAME)
  }

  private fun tryToDirectCopyFile(file: VirtualFile, outFile: File): Boolean {
    return tryToGetCompressStream(file)?.let { stream ->
      if (stream is SevenZipInputStream) {
        stream.extract(outFile) == ExtractOperationResult.OK
      } else {
        stream.use {
          outFile.outputStream().use { output ->
            stream.copyTo(output) == file.length
          }
        }
      }
    } ?: false
  }

  private fun tryToGetCompressStream(file: VirtualFile): InputStream? {
    return (file.fileSystem as? SupportsNestedArchives)?.let { fs ->
      (fs.getHandlerForFile(file) as? SupportsStreamForVirtualFile)?.getInputStreamForFile(file)
    }
  }

  fun convertPathToIdea(path: String?): String {
    return path?.replace('\\', '/') ?: ""
  }

  private val MERGED_ARCHIVE_EXTENSIONS = mapOf(
    "tgz" to "tar",
    "tlz" to "tar",
    "tZ" to "tar",
    "taZ" to "tar",
    "tlz" to "tar",
    "tzst" to "tar",
    "tb2" to "tar",
    "tbz" to "tar",
    "tbz2" to "tar",
    "tz2" to "tar",
    "taz" to "tar",
  )
}


