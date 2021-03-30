/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners.codegen

import org.jetbrains.kotlin.test.TestInfrastructureInternals
import org.jetbrains.kotlin.test.model.DependencyDescription
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.model.DependencyRelation
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.services.ModuleStructureTransformer
import org.jetbrains.kotlin.test.services.TestModuleStructure
import org.jetbrains.kotlin.test.services.impl.TestModuleStructureImpl

@OptIn(TestInfrastructureInternals::class)
internal class ModuleTransformerForKotlinAgainstKotlinWithBinaryAndSourcesTest :
    ModuleStructureTransformer() {

    override fun transformModuleStructure(moduleStructure: TestModuleStructure): TestModuleStructure {
        val module = moduleStructure.modules.singleOrNull()
            ?: error("Test should contain only one module")
        val additionalFiles = module.files.filter { it.isAdditional }
        val realFiles = module.files.filterNot { it.isAdditional }
        // This separation is equivalent to:
        //  kotlinc 2.kt 3.kt -d out.jar
        //  kotlinc 1.kt 2.kt -cp out.jar
        val file1 = realFiles.find("1.kt")
        val file2 = realFiles.find("2.kt")
        val file3 = realFiles.find("3.kt")
        val moduleA = module.copy(
            name = "lib",
            files = listOf(file2, file3) + additionalFiles,
            dependencies = emptyList(),
            friends = emptyList()
        )
        val moduleB = module.copy(
            name = "main",
            files = listOf(file1, file2) + additionalFiles,
            dependencies = listOf(DependencyDescription("lib", DependencyKind.Binary, DependencyRelation.Dependency)),
            friends = emptyList()
        )
        return TestModuleStructureImpl(listOf(moduleA, moduleB), moduleStructure.originalTestDataFiles)
    }

    private fun List<TestFile>.find(name: String) =
        firstOrNull { it.name == name }
            ?: error("Test file '$name' not provided: ${map { it.name }}")
}
