package com.github.b3er.idea.plugins.arc.browser.formats.gz

import com.github.b3er.idea.plugins.arc.browser.base.BasePsiFileNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class GZipPsiFileNode(
  project: Project?, value: PsiFile,
  viewSettings: ViewSettings?
) : BasePsiFileNode(project, value, viewSettings) {
  override fun getChildrenImpl(): MutableCollection<AbstractTreeNode<*>> {
    val root = virtualFile?.let { GZipArchiveFileSystemImpl.instance.getArchiveRootForLocalFile(it) }
    return getChildrenForVirtualFile(root)
  }
}