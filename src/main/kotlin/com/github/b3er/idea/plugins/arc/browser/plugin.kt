package com.github.b3er.idea.plugins.arc.browser

import com.github.b3er.idea.plugins.arc.browser.base.sevenzip.SevenZipPsiFileNode
import com.github.b3er.idea.plugins.arc.browser.formats.PsiZipFileNode
import com.github.b3er.idea.plugins.arc.browser.formats.SevenZipArchiveFileType
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import java.util.*

class ArchivePluginStructureProvider : TreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>, children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> {
        return children.asSequence().map { convertArchiveNode(it) }.toCollection(ArrayList())
    }

    override fun getData(
        selected: MutableCollection<AbstractTreeNode<Any>>,
        dataName: String
    ): Any? {
        return null
    }

    private fun convertArchiveNode(node: AbstractTreeNode<*>): AbstractTreeNode<*> {
        if (node is PsiFileNode) {
            return when (node.virtualFile?.fileType) {
                is ArchiveFileType -> PsiZipFileNode(node.project, node.value, node.settings)
                is SevenZipArchiveFileType -> SevenZipPsiFileNode(node.project, node.value, node.settings)
                else -> node
            }
        }
        return node
    }
}

class ArchivePluginFileTypeFactory : FileTypeFactory() {
    override fun createFileTypes(consumer: FileTypeConsumer) {
        consumer.consume(
            SevenZipArchiveFileType,
            "gz;rar;deb;tar;lzma;cpio;bz2;7z;xz;arj;iso;lzh;msi;rpm;squashfs;sfs;xar;z;vmdk;wim;vhd;vdi;uefi;udf;hfs;dmg;ext;fat;ntfs;chm;cab;"
        )
        consumer.consume(ArchiveFileType.INSTANCE, "epub;htmlz;zip;apk;aar;")
    }
}

