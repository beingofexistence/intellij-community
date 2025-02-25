// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.ChangeModifierRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElement

fun createAddAnnotationQuickfixes(target: UDeclaration, request: AnnotationRequest): Array<LocalQuickFix> {
  val containingFile = target.sourcePsi?.containingFile ?: return emptyArray()
  return IntentionWrapper.wrapToQuickFixes(createAddAnnotationActions(target, request).toTypedArray(), containingFile)
}

fun createModifierQuickfixes(target: UDeclaration, request: ChangeModifierRequest): Array<LocalQuickFix> {
  val containingFile = target.sourcePsi?.containingFile ?: return emptyArray()
  return IntentionWrapper.wrapToQuickFixes(createModifierActions(target, request).toTypedArray(), containingFile)
}

/** When in preview this method value returns the non preview element to construct JVM actions.*/
val PsiElement.nonPreviewElement: JvmModifiersOwner? get() {
  // workaround because langElement.originalElement doesn't always work
  val physSourcePsi = PsiTreeUtil.findSameElementInCopy(navigationElement, navigationElement?.containingFile?.originalFile ?: return null)
  return physSourcePsi.toUElement()?.javaPsi?.asSafely<JvmModifiersOwner>()
}

fun List<UExpression>.toSmartPsiElementPointers() : List<SmartPsiElementPointer<PsiElement>> = this.map {
  val element = it.sourcePsi ?: return@toSmartPsiElementPointers emptyList()
  SmartPointerManager.createPointer(element)
}