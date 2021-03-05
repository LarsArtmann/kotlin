/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isSuperclassOf
import org.jetbrains.kotlin.fir.analysis.checkers.isSupertypeOf
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClass
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.FUN_INTERFACE_ABSTRACT_METHOD_WITH_DEFAULT_VALUE
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.FUN_INTERFACE_ABSTRACT_METHOD_WITH_TYPE_PARAMETERS
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.FUN_INTERFACE_CANNOT_HAVE_ABSTRACT_PROPERTIES
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.FUN_INTERFACE_WITH_SUSPEND_FUNCTION
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.FUN_INTERFACE_WRONG_COUNT_OF_ABSTRACT_MEMBERS
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*

object FirFunInterfaceDeclarationChecker : FirRegularClassChecker() {

    override fun check(declaration: FirRegularClass, context: CheckerContext, reporter: DiagnosticReporter) {
        if (!(declaration.isInterface && declaration.isFun)) return

        var abstractFunction: FirSimpleFunction? = null

        for (superType in declaration.superConeTypes) {
            val superClass = superType.toRegularClass(context.session) ?: continue

            for (innerDeclaration in superClass.declarations) {
                if (innerDeclaration is FirSimpleFunction && innerDeclaration.isAbstract) {
                    abstractFunction = innerDeclaration
                    break
                }
            }

            if (abstractFunction != null) {
                break
            }
        }

        for (innerDeclaration in declaration.declarations) {
            when (innerDeclaration) {
                is FirSimpleFunction -> {
                    if (innerDeclaration.isAbstract) {
                        if (abstractFunction == null) {
                            abstractFunction = innerDeclaration
                        } else {
                            reporter.reportOn(declaration.source, FUN_INTERFACE_WRONG_COUNT_OF_ABSTRACT_MEMBERS, context)
                        }
                    }
                }
                is FirProperty -> {
                    if (innerDeclaration.isAbstract) {
                        reporter.reportOn(innerDeclaration.source, FUN_INTERFACE_CANNOT_HAVE_ABSTRACT_PROPERTIES, context)
                    }
                }
            }
        }

        if (abstractFunction == null) {
            reporter.reportOn(declaration.source, FUN_INTERFACE_WRONG_COUNT_OF_ABSTRACT_MEMBERS, context)
            return
        }

        when {
            abstractFunction.typeParameters.isNotEmpty() ->
                reporter.reportOn(
                    abstractFunction.typeParameters.first().source,
                    FUN_INTERFACE_ABSTRACT_METHOD_WITH_TYPE_PARAMETERS,
                    context
                )

            abstractFunction.isSuspend ->
                reporter.reportOn(abstractFunction.source, FUN_INTERFACE_WITH_SUSPEND_FUNCTION, context)
        }

        abstractFunction.valueParameters.forEach {
            if (it.defaultValue != null) {
                reporter.reportOn(it.source, FUN_INTERFACE_ABSTRACT_METHOD_WITH_DEFAULT_VALUE, context)
            }
        }
    }
}


