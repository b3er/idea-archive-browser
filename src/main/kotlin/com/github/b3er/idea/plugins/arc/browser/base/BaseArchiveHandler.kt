package com.github.b3er.idea.plugins.arc.browser.base

import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.util.io.FileAccessorCache
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.io.FileNotFoundException

abstract class BaseArchiveHandler<T>(path: String) : ArchiveHandler(path) {
    @Volatile
    var myFileStamp: Long = DEFAULT_TIMESTAMP

    @Volatile
    var myFileLength: Long = DEFAULT_LENGTH
    abstract val accessorCache: FileAccessorCache<BaseArchiveHandler<T>, T>

    abstract fun isSingleFileArchive(): Boolean

    protected fun getFileHandle(): FileAccessorCache.Handle<T> {
        val handle = accessorCache[this]
        val attributes = path.toFile().canonicalFile.let {
            FileSystemUtil.getAttributes(it) ?: throw FileNotFoundException(it.toString())
        }
        if (attributes.lastModified == myFileStamp && attributes.length == myFileLength) {
            return handle
        }
        accessorCache.remove(this)
        handle.release()
        return accessorCache[this]
    }

    @ApiStatus.OverrideOnly
    override fun clearCaches() {
        accessorCache.remove(this)
        super.clearCaches()
    }

    interface CacheProvider<T> {
        val cache: FileAccessorCache<BaseArchiveHandler<T>, T>
    }

    companion object {
        fun <T : Any> cacheProvider(
            protectedQueueSize: Int = 20,
            probationalQueueSize: Int = 20,
            onCreate: (key: BaseArchiveHandler<T>) -> T,
            onDispose: (value: T) -> Unit = { (it as? Closeable)?.close() },
            onEqual: (val1: BaseArchiveHandler<T>?, val2: BaseArchiveHandler<T>?) -> Boolean = { v1, v2 ->
                v1?.path?.toCanonicalPath() == v2?.path?.toCanonicalPath()
            }
        ): CacheProvider<T> {
            return object : CacheProvider<T> {
                private val _cache = createCache(protectedQueueSize, probationalQueueSize, onCreate, onDispose, onEqual)
                override val cache: FileAccessorCache<BaseArchiveHandler<T>, T>
                    get() = _cache
            }

        }

        fun <T : Any> createCache(
            protectedQueueSize: Int = 20,
            probationalQueueSize: Int = 20,
            onCreate: (key: BaseArchiveHandler<T>) -> T,
            onDispose: (value: T) -> Unit,
            onEqual: (val1: BaseArchiveHandler<T>?, val2: BaseArchiveHandler<T>?) -> Boolean
        ): FileAccessorCache<BaseArchiveHandler<T>, T> {
            return object : FileAccessorCache<BaseArchiveHandler<T>, T>(protectedQueueSize, probationalQueueSize) {
                override fun createAccessor(key: BaseArchiveHandler<T>): T {
                    val attributes = FileSystemUtil.getAttributes(key.path.toFile().canonicalFile)
                    key.myFileStamp = attributes?.lastModified ?: DEFAULT_TIMESTAMP
                    key.myFileLength = attributes?.length ?: DEFAULT_LENGTH
                    return onCreate(key)
                }

                override fun disposeAccessor(fileAccessor: T) = onDispose(fileAccessor)

                override fun isEqual(val1: BaseArchiveHandler<T>?, val2: BaseArchiveHandler<T>?): Boolean =
                    onEqual(val1, val2)
            }
        }
    }
}
