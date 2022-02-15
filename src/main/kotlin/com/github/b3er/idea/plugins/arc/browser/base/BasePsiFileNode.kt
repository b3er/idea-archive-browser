package com.github.b3er.idea.plugins.arc.browser.base

import com.github.b3er.idea.plugins.arc.browser.util.processChildren
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil

abstract class BasePsiFileNode(
  project: Project?, value: PsiFile,
  viewSettings: ViewSettings?
) : PsiFileNode(project, value, viewSettings) {
  protected fun getChildrenForVirtualFile(rootFile: VirtualFile?): MutableCollection<AbstractTreeNode<*>> {
    val project = project
    if (project != null && rootFile != null) {
      val psiDirectory = PsiManager.getInstance(project).findDirectory(rootFile)
      if (psiDirectory != null) {
        return processChildren(psiDirectory)
      }
    }
    return ContainerUtil.emptyList()
  }
}