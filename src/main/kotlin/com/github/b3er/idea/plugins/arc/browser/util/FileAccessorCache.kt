package com.github.b3er.idea.plugins.arc.browser.util

import com.intellij.util.io.FileAccessorCache

inline fun <T, R> FileAccessorCache.Handle<T>.getAndUse(block: (T) -> R): R {
  var released = false
  try {
    val value = get()
    return block(value)
  } catch (e: Exception) {
    released = true
    try {
      release()
    } catch (ignore: Exception) {
    }
    throw e
  } finally {
    if (!released) {
      release()
    }
  }
}

