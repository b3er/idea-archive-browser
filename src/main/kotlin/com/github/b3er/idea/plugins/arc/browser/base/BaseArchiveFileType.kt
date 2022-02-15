package com.github.b3er.idea.plugins.arc.browser.base

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

interface BaseArchiveFileType : FileType {
  fun createPsiNode(
    project: Project?, value: PsiFile, viewSettings: ViewSettings?
  ): PsiFileNode
}