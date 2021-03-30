/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.hierarchical

import org.jetbrains.kotlin.commonizer.LeafCommonizerTarget
import org.jetbrains.kotlin.commonizer.ResultsConsumer
import org.jetbrains.kotlin.commonizer.SharedCommonizerTarget
import org.jetbrains.kotlin.commonizer.parseCommonizerTarget

class HierarchicalPropertyCommonizationTest : AbstractInlineSourcesCommonizationTest() {

    fun `test simple property`() {
        val result = commonize {
            outputTarget = parseCommonizerTarget("((a, b), (c, d))") as SharedCommonizerTarget
            simpleSingleSourceTarget(LeafCommonizerTarget("a"), "val x: Int = 42")
            simpleSingleSourceTarget(LeafCommonizerTarget("b"), "val x: Int = 42")
            simpleSingleSourceTarget(LeafCommonizerTarget("c"), "val x: Int = 42")
            simpleSingleSourceTarget(LeafCommonizerTarget("d"), "val x: Int = 42")
        }

        val commonResult = result[parseCommonizerTarget("((a, b), (c, d))")] ?: kotlin.test.fail("Missing result for target")
        val commonModuleResult = commonResult.single() as ResultsConsumer.ModuleResult.Commonized
        commonModuleResult.metadata
    }
}
