package com.github.b3er.idea.plugins.arc.browser

import com.github.b3er.idea.plugins.arc.browser.base.BaseArchiveFileType
import com.github.b3er.idea.plugins.arc.browser.formats.zip.PsiZipFileNode
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager

class ArchivePluginStructureProvider : TreeStructureProvider {
  override fun modify(
    parent: AbstractTreeNode<*>, children: MutableCollection<AbstractTreeNode<*>>,
    settings: ViewSettings?
  ): MutableCollection<AbstractTreeNode<*>> {
    return children.asSequence().map { convertArchiveNode(it) }.toCollection(ArrayList())
  }

  private fun convertArchiveNode(node: AbstractTreeNode<*>): AbstractTreeNode<*> {
    if (node is PsiFileNode) {
      val virtualFile = node.virtualFile
      if (virtualFile != null) {
        try {
          var psiFile = node.value
          if ((psiFile.fileType is BaseArchiveFileType || psiFile.fileType is ArchiveFileType)
            && FSUtils.isNestedFile(virtualFile.path)
          ) {
            val tempNestedFile = FSUtils.copyFileToTemp(virtualFile)
            val nestedVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(tempNestedFile)
            if (nestedVirtualFile != null) {
              psiFile = PsiManager.getInstance(node.project!!).findFile(nestedVirtualFile)
            }
          }
          return when (val fileType = node.virtualFile?.fileType) {
            is BaseArchiveFileType -> fileType.createPsiNode(node.project, psiFile, node.settings)
            is ArchiveFileType -> PsiZipFileNode(node.project, psiFile, node.settings)
            else -> node
          }
        } catch (t: Throwable) {
          // return the original node in case of any error
          return node
        }
      }
    }
    return node
  }
}
