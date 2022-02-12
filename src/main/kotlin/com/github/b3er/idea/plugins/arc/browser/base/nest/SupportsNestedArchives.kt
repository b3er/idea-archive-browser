package com.github.b3er.idea.plugins.arc.browser.base.nest

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.ArchiveHandler

interface SupportsNestedArchives {
  fun getHandlerForFile(file: VirtualFile): ArchiveHandler
}

