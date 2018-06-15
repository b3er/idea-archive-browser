package com.github.b3er.idea.plugins.arc.browser

import com.github.b3er.idea.plugins.arc.browser.formats.GZipFileType
import com.github.b3er.idea.plugins.arc.browser.formats.PsiGZipFileNode
import com.github.b3er.idea.plugins.arc.browser.formats.PsiZipFileNode
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import java.util.*

class ArchivePluginStructureProvider : TreeStructureProvider {
  override fun modify(parent: AbstractTreeNode<*>, children: MutableCollection<AbstractTreeNode<*>>,
      settings: ViewSettings?): MutableCollection<AbstractTreeNode<*>> {
    return children.asSequence().map { convertArchiveNode(it) }.toCollection(
        ArrayList<AbstractTreeNode<*>>())
  }

  override fun getData(selected: MutableCollection<AbstractTreeNode<Any>>,
      dataName: String?): Any? {
    return null
  }

  private fun convertArchiveNode(node: AbstractTreeNode<*>): AbstractTreeNode<*> {
    if (node is PsiFileNode) {
      return when (node.virtualFile?.fileType) {
        is ArchiveFileType -> PsiZipFileNode(node.project, node.value, node.settings)
        is GZipFileType -> PsiGZipFileNode(node.project, node.value, node.settings)
        else -> node
      }
    }
    return node
  }
}

class ArchivePluginFileTypeFactory : FileTypeFactory() {
  override fun createFileTypes(consumer: FileTypeConsumer) {
    consumer.consume(ArchiveFileType.INSTANCE, "epub;htmlz;zip;apk;aar;")
    consumer.consume(GZipFileType, "gz;")
  }
}

