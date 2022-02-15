package com.github.b3er.idea.plugins.arc.browser.base.compress

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.nest.SupportsStreamForVirtualFile
import com.github.b3er.idea.plugins.arc.browser.util.FSUtils
import com.github.b3er.idea.plugins.arc.browser.util.getAndUse
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.compress.archivers.ArchiveEntry
import java.io.FileNotFoundException
import java.io.InputStream


abstract class CompressArchiveHandler<E : ArchiveEntry, H>(path: String) : BaseArchiveHandler<H>(path),
  SupportsStreamForVirtualFile {

  abstract fun H.getEntries(): Iterable<E>
  abstract fun H.getInputStream(entry: E): InputStream
  abstract fun E.hasLastModifiedDate(): Boolean

  private fun H.getItemForPath(
    relativePath: String
  ): E {
    return getEntries().find { it.name == relativePath } ?: throw FileNotFoundException("$file!/$relativePath")
  }

  override fun contentsToByteArray(relativePath: String): ByteArray {
    return getFileHandle().getAndUse { holder ->
      val entry = holder.getItemForPath(relativePath)
      if (FileUtilRt.isTooLarge(entry.size)) {
        throw FileTooBigException("$file!/$relativePath")
      }
      holder.getInputStream(entry).buffered().use { it.readBytes() }
    }
  }

  override fun createEntriesMap(): MutableMap<String, EntryInfo> {
    return getFileHandle().getAndUse { holder ->
      val entries = holder.getEntries()
      LinkedHashMap<String, EntryInfo>(entries.count()).also { map ->
        map[""] = createRootEntry()
        holder.getEntries().forEach { entry ->
          processEntry(map, entry.name) { parent, name ->
            val timeStamp = entry
              .hasLastModifiedDate().takeIf { it }?.let { entry.lastModifiedDate?.time } ?: DEFAULT_TIMESTAMP
            EntryInfo(name, false, entry.size, timeStamp, parent)
          }
        }
      }
    }
  }

  override fun getInputStream(relativePath: String): InputStream {
    return getFileHandle().getAndUse { holder ->
      val item = holder.getItemForPath(relativePath)
      holder.getInputStream(item).buffered()
    }
  }

  override fun getInputStreamForFile(file: VirtualFile): InputStream {
    return getFileHandle().getAndUse { holder ->
      val item = holder.getItemForPath(file.path.split(FSUtils.FS_SEPARATOR).last())
      holder.getInputStream(item).buffered()
    }
  }
}
