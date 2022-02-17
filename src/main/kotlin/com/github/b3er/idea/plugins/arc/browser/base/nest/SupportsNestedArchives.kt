package com.github.b3er.idea.plugins.arc.browser.base.nest

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveHandler
import com.intellij.openapi.vfs.VirtualFile

interface SupportsNestedArchives {
  fun getHandlerForFile(file: VirtualFile): BaseArchiveHandler<*>
}

