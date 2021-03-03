/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.components

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.idea.frontend.api.analyse
import org.jetbrains.kotlin.idea.frontend.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractRendererTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun isFirPlugin() = true

    protected fun doTest(path: String) {
        val testDataFile = File(path)
        val ktFile = myFixture.configureByText(testDataFile.name, FileUtil.loadFile(testDataFile)) as KtFile

        val actual = executeOnPooledThreadInReadAction {

            val rendererWithContent = object : KtVisitor<Unit, Unit>() {
                val builder = StringBuilder()

                override fun visitElement(element: PsiElement) {
                    element.children.forEach {
                        it.accept(this)
                    }
                }

                override fun visitFunctionType(type: KtFunctionType, data: Unit?) {

                }

                override fun visitDeclaration(dcl: KtDeclaration, data: Unit?) {
                    if (dcl !is KtClassInitializer) {
                        analyse(dcl) {
                            val symbol = dcl.getSymbol()
                            if (symbol is KtSymbolWithKind) {
                                builder.append(symbol.render())
                                builder.append("\n")
                            }
                        }
                    }
                    super.visitDeclaration(dcl, data)
                }
            }

            ktFile.accept(rendererWithContent)
            rendererWithContent.builder.toString()

//            buildString {
//                ktFile.declarations.forEach {
//                    analyze(ktFile) {
//                        append(it.getSymbol().render())
//                        appendLine()
//                    }
//                }
//            }
        }

        KotlinTestUtils.assertEqualsToFile(File(path + ".rendered"), actual)
    }
}
