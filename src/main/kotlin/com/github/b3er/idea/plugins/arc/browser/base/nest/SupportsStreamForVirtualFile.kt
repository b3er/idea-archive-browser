package com.github.b3er.idea.plugins.arc.browser.base.nest

import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStream

interface SupportsStreamForVirtualFile {
  fun getInputStreamForFile(file: VirtualFile): InputStream
}