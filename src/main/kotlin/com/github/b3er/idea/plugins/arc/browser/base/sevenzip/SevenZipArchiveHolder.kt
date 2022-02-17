package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.Closeable
import java.io.File

class SevenZipArchiveHolder(file: File) : Closeable {
  private val stream = ReusableRandomAccessFileStream(file)
  val archive: IInArchive = SevenZip.openInArchive(null, stream)
  val archiveItems: Array<ISimpleInArchiveItem> by lazy {
    stream.use {
      archive.simpleInterface.archiveItems
    }
  }

  fun <R> useStream(block: (IInStream) -> R): R {
    return stream.use {
      block(it)
    }
  }

  fun closeStream() {
    stream.close()
  }

  override fun close() {
    stream.use {
      archive.close()
    }
  }
}