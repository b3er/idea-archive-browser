package com.github.b3er.idea.plugins.arc.browser.formats.tar

import org.apache.commons.compress.archivers.tar.TarFile
import java.io.Closeable
import java.io.File

class TarArchiveHolder(file: File) : Closeable {
  val archive: TarFile = TarFile(file)
  override fun close() = archive.close()
}