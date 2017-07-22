package com.github.b3er.idea.plugins.arc.browser.formats

import com.github.b3er.idea.plugins.arc.browser.processChildren
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil

class PsiGenericDirectoryNode(project: Project?, value: PsiDirectory?,
    viewSettings: ViewSettings?) : PsiDirectoryNode(project, value, viewSettings) {
  override fun getChildrenImpl(): MutableCollection<AbstractTreeNode<*>> {
    val project = project
    if (project != null) {
      val psiDirectory = value
      if (psiDirectory != null) {
        return processChildren(psiDirectory)
      }
    }
    return ContainerUtil.emptyList<AbstractTreeNode<*>>()
  }
}

class PsiZipFileNode(project: Project?, value: PsiFile?, viewSettings: ViewSettings?) : PsiFileNode(
    project, value, viewSettings) {
  override fun getChildrenImpl(): MutableCollection<AbstractTreeNode<*>> {
    val project = project
    val jarRoot = virtualFile?.let { JarFileSystem.getInstance().getJarRootForLocalFile(it) }
    if (project != null && jarRoot != null) {
      val psiDirectory = PsiManager.getInstance(project).findDirectory(jarRoot)
      if (psiDirectory != null) {
        return processChildren(psiDirectory)
      }
    }
    return ContainerUtil.emptyList<AbstractTreeNode<*>>()
  }
}
