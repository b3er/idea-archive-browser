package com.github.b3er.idea.plugins.arc.browser.formats.gz

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileType
import com.intellij.icons.AllIcons.FileTypes
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import javax.swing.Icon

class GzipArchiveFileType : BaseArchiveFileType {
  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = FileTypes.Archive

  override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

  override fun getName() = "ARCHIVE_GZIP"

  override fun getDescription() = "Gzip archive"

  override fun isBinary() = true

  override fun createPsiNode(project: Project?, value: PsiFile, viewSettings: ViewSettings?): PsiFileNode {
    return GZipPsiFileNode(project, value, viewSettings)
  }

  companion object {
    @JvmStatic
    val INSTANCE = GzipArchiveFileType()
  }
}