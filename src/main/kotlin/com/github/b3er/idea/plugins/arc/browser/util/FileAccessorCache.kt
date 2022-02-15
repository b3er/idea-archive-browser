package com.github.b3er.idea.plugins.arc.browser.util

import com.intellij.util.io.FileAccessorCache

inline fun <T, R> FileAccessorCache.Handle<T>.getAndUse(block: (T) -> R): R {
  var released = false
  try {
    val value = this.get()
    return block(value)
  } catch (e: Exception) {
    released = true
    try {
      this.release()
    } catch (ignore: Exception) {
    }
    throw e
  } finally {
    if (!released) {
      this.release()
    }
  }
}

