package com.github.b3er.idea.plugins.arc.browser.base.sevenzip

import com.Ostermiller.util.CircularByteBuffer
import com.github.b3er.idea.plugins.arc.browser.formats.sevenzip.SevenZipArchiveHolder
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class SevenZipInputStream(
  private val holder: SevenZipArchiveHolder, private val item: ISimpleInArchiveItem
) : InputStream() {
  private val stream by lazy { AsyncCircularByteBuffer(BUFFER_SIZE).fill(holder, item) }

  override fun read(): Int = stream.read()

  override fun read(b: ByteArray?, off: Int, len: Int): Int = stream.read(b, off, len)

  override fun available(): Int = stream.available()

  override fun reset() = stream.reset()

  override fun skip(n: Long): Long = stream.skip(n)

  override fun mark(readlimit: Int) = stream.mark(readlimit)

  override fun markSupported(): Boolean = stream.markSupported()

  override fun close() {
    stream.close()
    holder.closeStream()
  }

  fun extract(file: File): ExtractOperationResult {
    val output = Files.newOutputStream(file.toPath())
    return output.use {
      holder.useStream {
        item.extractSlow { data -> data?.also(output::write)?.size ?: 0 }
      }
    }
  }

  companion object {
    const val BUFFER_SIZE = 32768
  }
}

class AsyncCircularByteBuffer(size: Int) : CircularByteBuffer(size, true) {
  private var isClosed = false
  fun fill(holder: SevenZipArchiveHolder, item: ISimpleInArchiveItem): InputStream {
    EXECUTOR.submit {
      outputStream.use { output ->
        holder.useStream {
          item.extractSlow { data ->
            if (!isClosed) {
              data?.also(output::write)?.size ?: 0
            } else {
              throw InterruptedException()
            }
          }
        }
      }
    }
    return inputStream
  }

  override fun getInputStream(): InputStream {
    return object : FilterInputStream(super.getInputStream()) {
      override fun close() {
        super.close()
        isClosed = true
      }
    }
  }

  companion object {
    private val EXECUTOR: ExecutorService = Executors.newCachedThreadPool()
  }
}