/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.components

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.test.util.JUnit4Assertions
import java.io.File

abstract class AbstractRendererTestX : KotlinLightCodeInsightFixtureTestCase() {

    protected fun doTest(path: String) {
        val testDataFile = File(path)
        val ktFile = myFixture.configureByText(testDataFile.name, FileUtil.loadFile(testDataFile)) as KtFile

        ktFile.analyzeWithContent()
        val rendererWithContent = object : KtVisitor<Unit, Unit>() {
            val builder = StringBuilder()

            override fun visitElement(element: PsiElement) {
                element.children.forEach {
                    it.accept(this)
                }
            }

            override fun visitDeclaration(dcl: KtDeclaration, data: Unit?) {
                if (dcl !is KtClassInitializer) {
                    val b = dcl.resolveToDescriptorIfAny(BodyResolveMode.FULL)
                    check(b != null)
                    builder.append(IdeDescriptorRenderers.BASE.render(b))
                    builder.append("\n")
                }
                super.visitDeclaration(dcl, data)
            }
        }

        ktFile.accept(rendererWithContent)

        val actual = rendererWithContent.builder.toString()
        JUnit4Assertions.assertEqualsToFile(File(path + ".rendered"), actual)
    }
}