package com.github.b3er.idea.plugins.arc.browser.formats.gz

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.github.b3er.idea.plugins.arc.browser.base.nest.SupportsStreamForVirtualFile
import com.github.b3er.idea.plugins.arc.browser.util.getAndUse
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStream

class GZipArchiveHandler(path: String) : BaseArchiveHandler<GZIPArchiveHolder>(path), SupportsStreamForVirtualFile {
  override val accessorCache
    get() = cache

  override fun contentsToByteArray(relativePath: String): ByteArray {
    return getFileHandle().getAndUse { holder ->
      if (holder.archive.length > 0 &&
        holder.archive.length < Long.MAX_VALUE &&
        FileUtilRt.isTooLarge(holder.archive.length)
      ) {
        throw FileTooBigException("$file!/$relativePath")
      }
      holder.archive.inputStream().buffered().use { it.readBytes() }
    }
  }

  override fun createEntriesMap(): MutableMap<String, EntryInfo> {
    return getFileHandle().getAndUse { holder ->
      LinkedHashMap<String, EntryInfo>(2).also { map ->
        map[""] = createRootEntry()
        processEntry(map, holder.archive.name) { parent, name ->
          EntryInfo(name, false, holder.archive.length, holder.archive.timestamp, parent)
        }
      }
    }
  }

  override fun getInputStream(relativePath: String): InputStream {
    return getFileHandle().getAndUse { holder ->
      holder.archive.inputStream().buffered()
    }
  }

  override fun getInputStreamForFile(file: VirtualFile): InputStream {
    return getFileHandle().getAndUse { holder ->
      holder.archive.inputStream().buffered()
    }
  }

  companion object : CacheProvider<GZIPArchiveHolder> by cacheProvider(
    onCreate = { GZIPArchiveHolder(it.file.canonicalFile) },
    onDispose = {}
  )
}

