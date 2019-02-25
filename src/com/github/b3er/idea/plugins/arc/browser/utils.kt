package com.github.b3er.idea.plugins.arc.browser

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.BasePsiNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleFileIndex
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.FileAccessorCache
import java.util.*

fun processPsiDirectoryChildren(children: Array<PsiElement>,
    container: MutableList<AbstractTreeNode<*>>, moduleFileIndex: ModuleFileIndex?,
    viewSettings: ViewSettings) {
  for (child in children) {
    if (child !is PsiFileSystemItem) {
      continue
    }
    val vFile = child.virtualFile ?: continue
    if (moduleFileIndex != null && !moduleFileIndex.isInContent(vFile)) {
      continue
    }
    if (child is PsiFile) {
      container.add(PsiFileNode(child.getProject(), child, viewSettings))
    } else if (child is PsiDirectory) {
//      container.add(PsiDirectoryNode(child.getProject(),child,viewSettings))
      container.add(PsiGenericDirectoryNode(child.getProject(), child, viewSettings))
    }
  }
}

@Suppress("NOTHING_TO_INLINE") inline fun BasePsiNode<*>.processChildren(
    dir: PsiDirectory): MutableCollection<AbstractTreeNode<*>> {
  val children = ArrayList<AbstractTreeNode<*>>()
  val project = dir.project
  val fileIndex = ProjectRootManager.getInstance(project).fileIndex
  val module = fileIndex.getModuleForFile(dir.virtualFile)
  val moduleFileIndex = if (module == null) null else ModuleRootManager.getInstance(
      module).fileIndex
  processPsiDirectoryChildren(dir.children, children, moduleFileIndex, settings)
  return children
}

inline fun <T, R> FileAccessorCache.Handle<T>.use(block: (T) -> R): R {
  var released = false
  try {
    val value = this.get()
    return block(value)
  } catch (e: Exception) {
    released = true
    try {
      this.release()
    } catch (releaseException: Exception) {
    }
    throw e
  } finally {
    if (!released) {
      this.release()
    }
  }
}

class PsiGenericDirectoryNode(project: Project?, value: PsiDirectory,
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