package com.github.b3er.idea.plugins.arc.browser.formats.sevenzip

import com.github.b3er.idea.plugins.arc.browser.base.compress.CompressArchiveHandler
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import java.io.InputStream

class SevenZipArchiveHandler(
  path: String
) : CompressArchiveHandler<SevenZArchiveEntry, SevenZipArchiveHolder>(path) {
  override val accessorCache = CACHE

  companion object {
    private val CACHE = createCache<SevenZipArchiveHolder>(onCreate = { SevenZipArchiveHolder(it.file.canonicalFile) })
  }

  override fun SevenZipArchiveHolder.getEntries(): Iterable<SevenZArchiveEntry> {
    return archive.entries
  }

  override fun SevenZipArchiveHolder.getInputStream(entry: SevenZArchiveEntry): InputStream {
    return archive.getInputStream(entry)
  }

  override fun SevenZArchiveEntry.hasLastModifiedDate(): Boolean {
    return hasLastModifiedDate
  }
}

