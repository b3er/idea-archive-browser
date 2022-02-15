package com.github.b3er.idea.plugins.arc.browser.formats.gz

import com.github.b3er.idea.plugins.arc.browser.util.FSUtils
import com.jcraft.jzlib.GZIPInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

class GZipFile(private val file: File) {
  var length = 0L
    private set
  var name = ""
    private set
  var timestamp = 0L
    private set

  init {
    GZIPInputStream(file.inputStream()).use { stream ->
      stream.readHeader()
      timestamp = stream.modifiedtime
      name = stream.name.takeIf { it.isNotBlank() } ?: FSUtils.decorateMergedNameWithExtension(
        file.extension,
        file.name.removeSuffix(".${file.extension}")
      )
    }
    //read uncompressed size according to RFC 1952 (http://www.zlib.org/rfc-gzip.html)
    length = RandomAccessFile(file, "r").use {
      it.seek(it.length() - 4)
      it.readUnsignedInt()
    }
  }

  fun inputStream(): InputStream {
    return GZIPInputStream(file.inputStream().buffered())
  }

  private fun RandomAccessFile.readUnsignedInt(): Long {
    val ch1 = this.read()
    val ch2 = this.read()
    val ch3 = this.read()
    val ch4 = this.read()
    if (ch1 or ch2 or ch3 or ch4 < 0) throw EOFException()
    return (ch1 shl 0) + (ch2 shl 8) + (ch3 shl 16) + (ch4.toLong() shl 24)
  }

}