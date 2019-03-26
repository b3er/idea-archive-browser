package com.github.b3er.idea.plugins.arc.browser.base.io

import com.intellij.util.io.FileAccessorCache
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class HandleInputStreamWrapper(
    private val ref: FileAccessorCache.Handle<*>,
    private val stream: InputStream
) : InputStream() {
    private val closed = AtomicBoolean(false)

    override fun read(): Int = stream.read()

    override fun read(b: ByteArray?, off: Int, len: Int): Int = stream.read(b, off, len)

    override fun available(): Int = stream.available()

    override fun close() {
        if (!closed.getAndSet(true)) {
            try {
                stream.close()
            } finally {
                ref.close()
            }
        }
    }
}