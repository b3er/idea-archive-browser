package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISeekableStream
import net.sf.sevenzipjbinding.SevenZipException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class ReusableRandomAccessFileStream(private val file: File) : IInStream {
  var raf: RandomAccessFile? = null
  var filePointer = 0L

  @Synchronized
  @Throws(SevenZipException::class)
  override fun read(bytes: ByteArray): Int {
    return try {
      val openedFile = getRandomFile()
      val read = openedFile.read(bytes)
      filePointer = openedFile.filePointer
      if (read == -1) 0 else read
    } catch (e: IOException) {
      throw SevenZipException("Error reading random access file", e)
    }
  }

  @Synchronized
  @Throws(SevenZipException::class)
  override fun seek(offset: Long, origin: Int): Long {
    return try {
      val openedFile = getRandomFile()
      when (origin) {
        ISeekableStream.SEEK_SET -> openedFile.seek(offset)
        ISeekableStream.SEEK_CUR -> openedFile.seek(openedFile.filePointer + offset)
        ISeekableStream.SEEK_END -> openedFile.seek(openedFile.length() + offset)
        else -> throw RuntimeException("Seek: unknown origin: $origin")
      }
      filePointer = openedFile.filePointer
      openedFile.filePointer
    } catch (e: IOException) {
      throw SevenZipException("Error while seek operation", e)
    }
  }

  private fun getRandomFile(): RandomAccessFile {
    return raf ?: RandomAccessFile(file, "r").also { newFile ->
      newFile.seek(filePointer)
      raf = newFile
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun close() {
    raf?.also {
      raf = null
      it.close()
    }
  }
}