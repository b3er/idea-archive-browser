package com.github.b3er.idea.plugins.arc.browser.formats.sevenzip

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.Closeable
import java.io.File

class SevenZipArchiveHolder(file: File) : Closeable {
  val archive: SevenZFile = SevenZFile(file)
  override fun close() = archive.close()
}