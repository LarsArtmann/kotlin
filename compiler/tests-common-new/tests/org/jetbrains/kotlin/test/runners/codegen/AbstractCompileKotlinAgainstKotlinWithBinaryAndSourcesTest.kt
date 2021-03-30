/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners.codegen

import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestInfrastructureInternals
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.ir.JvmIrBackendFacade
import org.jetbrains.kotlin.test.bind
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2IrConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendFacade
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest

@OptIn(TestInfrastructureInternals::class)
open class AbstractCompileKotlinAgainstKotlinWithBinaryAndSourcesTest :
    AbstractKotlinCompilerWithTargetBackendTest(TargetBackend.JVM_IR) {

    override fun TestConfigurationBuilder.configuration() {
        commonConfigurationForCodegenTest(
            FrontendKinds.ClassicFrontend,
            ::ClassicFrontendFacade,
            ::ClassicFrontend2IrConverter,
            ::JvmIrBackendFacade
        )
        commonHandlersForBoxTest()
        useModuleStructureTransformers(ModuleTransformerForKotlinAgainstKotlinWithBinaryAndSourcesTest())
        useAfterAnalysisCheckers(::BlackBoxCodegenSuppressor.bind(CodegenTestDirectives.IGNORE_BACKEND_MULTI_MODULE))
    }
}
