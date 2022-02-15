package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.simple.ISimpleInArchive
import java.io.Closeable
import java.io.File

class SevenZipArchiveHolder(file: File) : Closeable {
  private val stream = ReusableRandomAccessFileStream(file)
  val archive: IInArchive = toArchive()!!
  val simpleInterface: ISimpleInArchive = archive.simpleInterface!!


  private fun toArchive(): IInArchive? {
    return useStream {
      SevenZip.openInArchive(null, it)
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
      archive.use {
        simpleInterface.close()
      }
    }
  }
}