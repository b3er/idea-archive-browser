package com.github.b3er.idea.plugins.arc.browser

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.BasePsiNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleFileIndex
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.*
import com.intellij.util.containers.ContainerUtil
import java.util.*

class ZipArchiveStructureProvider : TreeStructureProvider {
  override fun modify(parent: AbstractTreeNode<*>,
                      children: MutableCollection<AbstractTreeNode<*>>,
                      settings: ViewSettings?): MutableCollection<AbstractTreeNode<*>> {
    return children.asSequence().map(::convertZipNode).toCollection(
        ArrayList<AbstractTreeNode<*>>())
  }

  override fun getData(selected: MutableCollection<AbstractTreeNode<Any>>?, dataName: String?): Any? {
    return null
  }
}

private fun convertZipNode(node: AbstractTreeNode<*>): AbstractTreeNode<*> {
  if (node is PsiFileNode && node.virtualFile?.fileType is ArchiveFileType) {
    return PsiZipFileNode(node.project, node.value, node.settings)
  }
  return node
}

private fun processPsiDirectoryChildren(children: Array<PsiElement>,
                                        container: MutableList<AbstractTreeNode<*>>,
                                        moduleFileIndex: ModuleFileIndex?,
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
      container.add(PsiZipDirectoryNode(child.getProject(), child, viewSettings))
    }
  }
}

@Suppress("NOTHING_TO_INLINE")
inline private fun BasePsiNode<*>.processChildren(
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

class PsiZipDirectoryNode(project: Project?, value: PsiDirectory?,
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

class PsiZipFileNode(project: Project?,
                     value: PsiFile?,
                     viewSettings: ViewSettings?) : PsiFileNode(project, value, viewSettings) {
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

class ZipFileTypeFactory : FileTypeFactory() {
  override fun createFileTypes(consumer: FileTypeConsumer) {
    consumer.consume(ArchiveFileType.INSTANCE, "epub;htmlz;zip;apk;aar;")
  }
}
