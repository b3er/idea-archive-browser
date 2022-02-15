package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.Ostermiller.util.CircularByteBuffer
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SevenZipInputStream(val holder: SevenZipArchiveHolder, private val item: ISimpleInArchiveItem) : InputStream() {
  private var buffer: CircularByteBuffer? = null
  private val stream by lazy {
    holder.useStream {
      val b = CircularByteBuffer(item.size.coerceAtMost(4 * 1024 * 1024).toInt(), true)
      buffer = b
      IO_EXECUTOR.submit {
        b.outputStream.use { out ->
          item.extractSlow {
            out.write(it)
            it.size
          }
        }
      }
      b.inputStream
    }
  }

  override fun read(): Int = holder.useStream { stream.read() }

  override fun read(b: ByteArray?, off: Int, len: Int): Int = stream.read(b, off, len)

  override fun available(): Int = stream.available()

  override fun reset() = stream.reset()

  override fun skip(n: Long): Long = stream.skip(n)

  override fun mark(readlimit: Int) = stream.mark(readlimit)

  override fun markSupported(): Boolean = stream.markSupported()

  override fun close() {
    if (buffer != null) {
      stream.close()
      holder.closeStream()
    }
  }

  fun directRead(stream: ISequentialOutStream): ExtractOperationResult =
    item.extractSlow(stream)

  fun directRead(stream: (ByteArray) -> Int): ExtractOperationResult =
    item.extractSlow(stream)

  companion object {
    val IO_EXECUTOR: ExecutorService = Executors.newCachedThreadPool()
  }
}