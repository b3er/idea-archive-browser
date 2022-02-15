package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.nest.SupportsStreamForVirtualFile
import com.github.b3er.idea.plugins.arc.browser.util.FSUtils
import com.github.b3er.idea.plugins.arc.browser.util.getAndUse
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream

class SevenZipArchiveHandler(
  path: String
) : BaseArchiveHandler<SevenZipArchiveHolder>(path), SupportsStreamForVirtualFile {

  override val accessorCache
    get() = cache

  private fun IInArchive.isSingleFileArchive(): Boolean {
    return (numberOfItems == 1 && archiveFormat == null) || archiveFormat?.supportMultipleFiles() == false
  }

  private fun SevenZipArchiveHolder.getItemForPath(relativePath: String): ISimpleInArchiveItem {
    // For single file archives don't look at path
    return if (archive.isSingleFileArchive()) {
      simpleInterface.archiveItems.firstOrNull()
    } else {
      simpleInterface.archiveItems.firstOrNull {
        it.ideaPath == relativePath
      }
    } ?: throw FileNotFoundException("$file!/$relativePath")
  }

  override fun contentsToByteArray(relativePath: String): ByteArray {
    return getFileHandle().getAndUse { holder ->
      holder.useStream {
        val item = holder.getItemForPath(relativePath)
        if (FileUtilRt.isTooLarge(item.size ?: DEFAULT_LENGTH)) {
          throw FileTooBigException("$file!/$relativePath")
        } else {
          ByteArrayOutputStream(item.size?.toInt() ?: DEFAULT_BUFFER_SIZE).use { stream ->
            item.extractSlow {
              stream.write(it)
              it.size
            }
            stream.toByteArray()
          }
        }
      }
    }
  }

  private fun createEntryNameForSingleArchive(entry: ISimpleInArchiveItem): String {
    val path = entry.ideaPath
    if (path.isNotEmpty()) {
      return path
    }
    val archiveFileName = file.name
    val nameWithoutEndExtension = archiveFileName.let { name -> name.substring(0, name.lastIndexOf('.')) }
    return FSUtils.decorateMergedNameWithExtension(file.extension, nameWithoutEndExtension)
  }

  override fun createEntriesMap(): MutableMap<String, EntryInfo> {
    return getFileHandle().getAndUse { holder ->
      holder.useStream {
        val simpleInArchive = holder.simpleInterface
        val archive = holder.archive
        if (archive.isSingleFileArchive()) {
          LinkedHashMap<String, EntryInfo>(2).also { map ->
            map[""] = createRootEntry()
            val entry = simpleInArchive.getArchiveItem(0)
            val path = createEntryNameForSingleArchive(entry)
            processEntry(map, path) { parent, name ->
              EntryInfo(name, false, entry.size, entry.creationTime?.time ?: DEFAULT_TIMESTAMP, parent)
            }
          }
        } else {
          val entries = simpleInArchive.archiveItems
          LinkedHashMap<String, EntryInfo>(entries.size).also { map ->
            map[""] = createRootEntry()
            entries.forEach { entry ->
              processEntry(map, entry.ideaPath) { parent, name ->
                EntryInfo(name, false, entry.size, entry.creationTime?.time ?: DEFAULT_TIMESTAMP, parent)
              }
            }
          }
        }
      }
    }
  }

  override fun getInputStream(relativePath: String): InputStream {
    return getFileHandle().getAndUse { holder ->
      val item = holder.getItemForPath(relativePath)
      SevenZipInputStream(holder, item)
    }
  }

  override fun getInputStreamForFile(file: VirtualFile): InputStream {
    return getFileHandle().getAndUse { holder ->
      val item = holder.getItemForPath(file.path.split(FSUtils.FS_SEPARATOR).last())
      SevenZipInputStream(holder, item)
    }
  }

  private val ISimpleInArchiveItem.ideaPath get() = FSUtils.convertPathToIdea(path)

  companion object : CacheProvider<SevenZipArchiveHolder> by cacheProvider(
    onCreate = { SevenZipArchiveHolder(it.file.canonicalFile) }
  )
}
