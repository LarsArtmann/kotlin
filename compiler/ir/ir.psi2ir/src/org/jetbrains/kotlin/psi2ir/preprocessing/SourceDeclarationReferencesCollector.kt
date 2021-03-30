/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi2ir.preprocessing

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.synthetics.findClassDescriptor
import org.jetbrains.kotlin.psi2ir.Psi2IrPreprocessingStep
import org.jetbrains.kotlin.psi2ir.generators.Generator
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext

class SourceDeclarationReferencesCollector : Psi2IrPreprocessingStep {
    override fun invoke(ktFiles: Collection<KtFile>, context: GeneratorContext) {
        val processor = Processor(context)
        for (ktFile in ktFiles.toSet()) {
            processor.processFile(ktFile)
        }
    }

    private class Processor(override val context: GeneratorContext) : Generator {
        private val symbolTable = context.symbolTable

        fun processFile(ktFile: KtFile) {
            ktFile.declarations.forEach { processDeclaration(it) }
        }

        private fun processDeclaration(ktDeclaration: KtDeclaration) {
            when (ktDeclaration) {
                is KtClassOrObject ->
                    processClassOrObject(ktDeclaration)
            }
        }

        private fun processClassOrObject(ktClassOrObject: KtClassOrObject) {
            val classDescriptor = ktClassOrObject.findClassDescriptor(context.bindingContext)
            symbolTable.referenceClass(classDescriptor)
            ktClassOrObject.body?.let { ktClassBody ->
                ktClassBody.declarations.forEach { processDeclaration(it) }
            }
            // TODO process synthetic nested classes and companion objects?
        }

    }
}