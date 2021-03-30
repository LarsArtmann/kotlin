/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.hierarchical

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.commonizer.*
import org.jetbrains.kotlin.commonizer.konan.NativeManifestDataProvider
import org.jetbrains.kotlin.commonizer.utils.*

abstract class AbstractInlineSourcesCommonizationTest : KtInlineSourceCommonizerTestCase() {

    data class Parameters(
        val outputTarget: SharedCommonizerTarget,
        val dependencies: TargetDependent<List<InlineSourceTest.Module>>,
        val targets: List<Target>
    )

    data class Target(
        val target: CommonizerTarget,
        val modules: List<InlineSourceTest.Module>
    )

    @DslMarker
    annotation class InlineSourcesCommonizationTestDsl

    @InlineSourcesCommonizationTestDsl
    class TargetBuilder(private val target: CommonizerTarget) {
        private var modules: List<InlineSourceTest.Module> = emptyList()

        @InlineSourcesCommonizationTestDsl
        fun module(builder: InlineSourceTest.ModuleBuilder.() -> Unit) {
            modules = modules + InlineSourceTest.ModuleBuilder().also(builder).build()
        }

        fun build(): Target = Target(target, modules = modules)
    }

    @InlineSourcesCommonizationTestDsl
    inner class ParametersBuilder {
        private var _outputTarget: SharedCommonizerTarget? = null

        @InlineSourcesCommonizationTestDsl
        var outputTarget: SharedCommonizerTarget
            get() {
                _outputTarget?.let { return it }
                return SharedCommonizerTarget(targets.map { it.target }.toSet())
            }
            set(value) {
                _outputTarget = value
            }

        private val dependencies: MutableMap<CommonizerTarget, MutableList<InlineSourceTest.Module>> = LinkedHashMap()

        private var targets: List<Target> = emptyList()

        @InlineSourcesCommonizationTestDsl
        fun target(target: CommonizerTarget, builder: TargetBuilder.() -> Unit) {
            targets = targets + TargetBuilder(target).also(builder).build()
        }

        @InlineSourcesCommonizationTestDsl
        fun dependency(target: CommonizerTarget, builder: InlineSourceTest.ModuleBuilder.() -> Unit) {
            dependencies.getOrPut(target) { mutableListOf() }.add(InlineSourceTest.ModuleBuilder().also(builder).build())
        }

        @InlineSourcesCommonizationTestDsl
        fun simpleSingleSourceTarget(target: CommonizerTarget, @Language("kotlin") sourceContent: String) {
            target(target) {
                module {
                    source(sourceContent)
                }
            }
        }

        fun build(): Parameters = Parameters(outputTarget, dependencies.toTargetDependent(), targets.toList())
    }

    fun commonize(builder: ParametersBuilder.() -> Unit): Map<CommonizerTarget, List<ResultsConsumer.ModuleResult>> {
        val consumer = MockResultsConsumer()
        val commonizerParameters = ParametersBuilder().also(builder).build().toCommonizerParameters(consumer)
        runCommonization(commonizerParameters)
        assertEquals(ResultsConsumer.Status.DONE, consumer.status)
        return consumer.modulesByTargets.mapValues { (_, collection) -> collection.toList() }
    }

    private fun Parameters.toCommonizerParameters(
        resultsConsumer: ResultsConsumer,
        manifestDataProvider: NativeManifestDataProvider = MockNativeManifestDataProvider()
    ): CommonizerParameters {
        return CommonizerParameters(
            outputTarget = outputTarget,
            manifestProvider = TargetDependent(outputTarget.withAllAncestors()) { manifestDataProvider },
            dependenciesProvider = TargetDependent(outputTarget.withAllAncestors()) { target ->
                val explicitDependencies = dependencies.getOrNull(target).orEmpty().map { module -> createModuleDescriptor(module) }
                val implicitDependencies = listOfNotNull(if (target == outputTarget) DefaultBuiltIns.Instance.builtInsModule else null)
                val dependencies = explicitDependencies + implicitDependencies
                if (dependencies.isEmpty()) null
                else MockModulesProvider.create(dependencies)
            },
            targetProviders = TargetDependent(targets.map { it.target }) { commonizerTarget ->
                val target = targets.single { it.target == commonizerTarget }
                TargetProvider(
                    target = commonizerTarget,
                    modulesProvider = MockModulesProvider.create(target.modules.map { createModuleDescriptor(it) })
                )
            },
            resultsConsumer = resultsConsumer
        )
    }
}

