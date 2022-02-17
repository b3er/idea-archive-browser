package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISeekableStream
import net.sf.sevenzipjbinding.SevenZipException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class ReusableRandomAccessFileStream(private val file: File) : IInStream {
  private var raf: RandomAccessFile? = null
  private var filePointer = 0L

  @Throws(SevenZipException::class)
  override fun read(bytes: ByteArray): Int {
    return try {
      val openedFile = ensureFileOpened()
      val read = openedFile.read(bytes)
      filePointer = openedFile.filePointer
      if (read == -1) 0 else read
    } catch (e: IOException) {
      throw SevenZipException("Error reading random access file", e)
    }
  }

  @Throws(SevenZipException::class)
  override fun seek(offset: Long, origin: Int): Long {
    return try {
      val openedFile = ensureFileOpened()
      when (origin) {
        ISeekableStream.SEEK_SET -> openedFile.seek(offset)
        ISeekableStream.SEEK_CUR -> openedFile.seek(openedFile.filePointer + offset)
        ISeekableStream.SEEK_END -> openedFile.seek(openedFile.length() + offset)
        else -> throw RuntimeException("Seek: unknown origin: $origin")
      }
      openedFile.filePointer.also {
        filePointer = it
      }
    } catch (e: IOException) {
      throw SevenZipException("Error while seek operation", e)
    }
  }

  private fun ensureFileOpened(): RandomAccessFile {
    return raf ?: RandomAccessFile(file, "r").also { newRaf ->
      newRaf.seek(filePointer)
      raf = newRaf
    }
  }

  @Throws(IOException::class)
  override fun close() {
    raf?.also {
      raf = null
      it.close()
    }
  }
}