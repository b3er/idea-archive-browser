package com.github.b3er.idea.plugins.arc.browser

import com.github.b3er.idea.plugins.arc.browser.formats.SevenZipArchiveFileType
import com.github.b3er.idea.plugins.arc.browser.formats.sevenzip.SevenZipPsiFileNode
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
          if (FSUtils.isArchiveFile(virtualFile.path)
            && FSUtils.isNestedFile(virtualFile.path)
          ) {
            val tempNestedFile = FSUtils.copyFileToTemp(virtualFile)
            val nestedVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(tempNestedFile)
            if (nestedVirtualFile != null) {
              psiFile = PsiManager.getInstance(node.project!!).findFile(nestedVirtualFile)
            }
          }
          return when (node.virtualFile?.fileType) {
            is ArchiveFileType -> PsiZipFileNode(node.project, psiFile, node.settings)
            is SevenZipArchiveFileType -> SevenZipPsiFileNode(node.project, psiFile, node.settings)
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

val COMMON_ZIP_EXTENSIONS = sortedSetOf("jar", "war", "ear")
val ZIP_EXTENSIONS = sortedSetOf("epub", "htmlz", "zip")
val SEVEN_ZIP_EXTENSIONS = sortedSetOf(
  "rar",
  "deb",
  "tar",
  "lzma",
  "cpio",
  "bz2",
  "7z",
  "xz",
  "arj",
  "iso",
  "lzh",
  "msi",
  "rpm",
  "squashfs",
  "sfs",
  "xar",
  "z",
  "vmdk",
  "wim",
  "vhd",
  "vdi",
  "uefi",
  "udf",
  "hfs",
  "dmg",
  "ext",
  "fat",
  "ntfs",
  "chm",
  "cab",
  "udf",
  "txz",
  "tlz",
  "gem",
  "gz",
  "tar",
  "tgz",
  "tb2",
  "tbz",
  "tbz2",
  "tz2",
  "taz",
  "tlz",
  "tZ",
  "taZ",
  "tlz",
  "tzst"
)
val ALL_EXTENSIONS = COMMON_ZIP_EXTENSIONS + ZIP_EXTENSIONS + SEVEN_ZIP_EXTENSIONS
/*
class ArchivePluginFileTypeFactory : FileTypeFactory() {
  companion object {
    val COMMON_ZIP_EXTENSIONS = sortedSetOf("jar", "war", "ear")
    val ZIP_EXTENSIONS = sortedSetOf("epub", "htmlz", "zip")
    val SEVEN_ZIP_EXTENSIONS = sortedSetOf(
      "rar",
      "deb",
      "tar",
      "lzma",
      "cpio",
      "bz2",
      "7z",
      "xz",
      "arj",
      "iso",
      "lzh",
      "msi",
      "rpm",
      "squashfs",
      "sfs",
      "xar",
      "z",
      "vmdk",
      "wim",
      "vhd",
      "vdi",
      "uefi",
      "udf",
      "hfs",
      "dmg",
      "ext",
      "fat",
      "ntfs",
      "chm",
      "cab",
      "udf",
      "txz",
      "tlz",
      "gem",
      "gz",
      "tar",
      "tgz",
      "tb2",
      "tbz",
      "tbz2",
      "tz2",
      "taz",
      "tlz",
      "tZ",
      "taZ",
      "tlz",
      "tzst"
    )
    val ALL_EXTENSIONS = COMMON_ZIP_EXTENSIONS + ZIP_EXTENSIONS + SEVEN_ZIP_EXTENSIONS
  }

  override fun createFileTypes(consumer: FileTypeConsumer) {
    consumer.consume(CompressArchiveFileType, listOf("7z").joinToString(separator = ";", postfix = ";"))
    consumer.consume(ArchiveFileType.INSTANCE, ZIP_EXTENSIONS.joinToString(separator = ";", postfix = ";"))
  }
}

*/