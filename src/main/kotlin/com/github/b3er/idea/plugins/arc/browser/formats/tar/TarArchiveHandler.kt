package com.github.b3er.idea.plugins.arc.browser.formats.tar

import com.github.b3er.idea.plugins.arc.browser.base.compress.CompressArchiveHandler
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import java.io.InputStream

class TarArchiveHandler(path: String) : CompressArchiveHandler<TarArchiveEntry, TarArchiveHolder>(path) {
  override val accessorCache
    get() = cache

  companion object : CacheProvider<TarArchiveHolder> by cacheProvider(
    onCreate = { TarArchiveHolder(it.file.canonicalFile) }
  )

  override fun TarArchiveHolder.getEntries(): Iterable<TarArchiveEntry> {
    return archive.entries
  }

  override fun TarArchiveHolder.getInputStream(entry: TarArchiveEntry): InputStream {
    return archive.getInputStream(entry)
  }

  override fun TarArchiveEntry.hasLastModifiedDate(): Boolean {
    return true
  }
}
