package com.github.b3er.idea.plugins.arc.browser.base

import com.github.b3er.idea.plugins.arc.browser.AppInfoUtil
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.util.io.FileAccessorCache
import com.intellij.util.text.ByteArrayCharSequence
import java.io.FileNotFoundException

abstract class BaseArchiveHandler<T>(path: String) : ArchiveHandler(path) {
    @Volatile
    var myFileStamp: Long = DEFAULT_TIMESTAMP

    @Volatile
    var myFileLength: Long = DEFAULT_LENGTH
    abstract val accessorCache: FileAccessorCache<BaseArchiveHandler<T>, T>

    protected fun getFileHandle(): FileAccessorCache.Handle<T> {
        val handle = accessorCache[this]
        val attributes = file.canonicalFile.let {
            FileSystemUtil.getAttributes(it) ?: throw FileNotFoundException(it.toString())
        }
        if (attributes.lastModified == myFileStamp && attributes.length == myFileLength) {
            return handle
        }
        clearCaches()
        handle.release()
        return accessorCache[this]
    }

    override fun clearCaches() {
        accessorCache.remove(this)
        super.clearCaches()
    }

    protected fun convertNameToBytes(name: String?): CharSequence {
        if (name == null) {
            return convertNameToBytes("")
        }
        return if (AppInfoUtil.baselineVersion >= 183) {
            @Suppress("MissingRecentApi")
            ByteArrayCharSequence.convertToBytesIfPossible(name)
        } else {
            @Suppress("DEPRECATION")
            ByteArrayCharSequence.convertToBytesIfAsciiString(name)
        }
    }

    companion object {
        fun <T> createCache(
            protectedQueueSize: Int = 20,
            probationalQueueSize: Int = 20,
            onCreate: (key: BaseArchiveHandler<T>) -> T,
            onDispose: (value: T) -> Unit = {},
            onEqual: (val1: BaseArchiveHandler<T>?, val2: BaseArchiveHandler<T>?) -> Boolean = { v1, v2 -> v1 == v2 }
        ): FileAccessorCache<BaseArchiveHandler<T>, T> {
            return object : FileAccessorCache<BaseArchiveHandler<T>, T>(protectedQueueSize, probationalQueueSize) {
                override fun createAccessor(key: BaseArchiveHandler<T>): T {
                    val attributes = FileSystemUtil.getAttributes(key.file.canonicalFile)
                    key.myFileStamp = attributes?.lastModified ?: ArchiveHandler.DEFAULT_TIMESTAMP
                    key.myFileLength = attributes?.length ?: ArchiveHandler.DEFAULT_LENGTH
                    return onCreate(key)
                }

                override fun disposeAccessor(fileAccessor: T) = onDispose(fileAccessor)

                override fun isEqual(val1: BaseArchiveHandler<T>?, val2: BaseArchiveHandler<T>?): Boolean =
                    onEqual(val1, val2)
            }
        }
    }
}