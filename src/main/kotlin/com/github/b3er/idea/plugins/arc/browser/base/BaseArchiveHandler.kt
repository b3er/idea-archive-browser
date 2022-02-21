package com.github.b3er.idea.plugins.arc.browser.base

import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.vfs.impl.ArchiveHandler
import com.intellij.util.io.FileAccessorCache
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
  }

  interface CacheProvider<T> {
    val cache: FileAccessorCache<BaseArchiveHandler<T>, T>
  }

  companion object {
    fun <T> cacheProvider(
      protectedQueueSize: Int = 20,
      probationalQueueSize: Int = 20,
      onCreate: (key: BaseArchiveHandler<T>) -> T,
      onDispose: (value: T) -> Unit = { (it as? Closeable)?.close() },
      onEqual: (val1: BaseArchiveHandler<T>?, val2: BaseArchiveHandler<T>?) -> Boolean = { v1, v2 ->
        v1?.file?.canonicalPath == v2?.file?.canonicalPath
      }
    ): CacheProvider<T> {
      return object : CacheProvider<T> {
        private val _cache = createCache(protectedQueueSize, probationalQueueSize, onCreate, onDispose, onEqual)
        override val cache: FileAccessorCache<BaseArchiveHandler<T>, T>
          get() = _cache
      }

    }

    fun <T> createCache(
      protectedQueueSize: Int = 20,
      probationalQueueSize: Int = 20,
      onCreate: (key: BaseArchiveHandler<T>) -> T,
      onDispose: (value: T) -> Unit,
      onEqual: (val1: BaseArchiveHandler<T>?, val2: BaseArchiveHandler<T>?) -> Boolean
    ): FileAccessorCache<BaseArchiveHandler<T>, T> {
      return object : FileAccessorCache<BaseArchiveHandler<T>, T>(protectedQueueSize, probationalQueueSize) {
        override fun createAccessor(key: BaseArchiveHandler<T>): T {
          val attributes = FileSystemUtil.getAttributes(key.file.canonicalFile)
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