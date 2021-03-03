/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.renderer

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClass
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.resolve.calls.fullyExpandedClass
import org.jetbrains.kotlin.fir.resolve.inference.*
import org.jetbrains.kotlin.fir.resolve.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.idea.fir.low.level.api.util.collectDesignation
import org.jetbrains.kotlin.name.ClassId

internal class ConeTypeRenderer(
    private val session: FirSession,
    private val options: FirConeTypeRendererOptions? = null,
) {
    fun renderType(type: ConeTypeProjection, annotations: List<FirAnnotationCall>? = null): String = buildString {

        if (annotations != null) {
            renderAnnotations(this@ConeTypeRenderer, options, annotations)
        }

        when (type) {
            is ConeKotlinErrorType -> {
                append("???")
                //TODO error type
            }
            //is Dynamic??? -> append("dynamic")
            is ConeClassLikeType -> {
                if (shouldRenderAsPrettyFunctionType(type)) {
                    renderFunctionType(type)
                } else {
                    renderTypeConstructorAndArguments(type)
                }
            }
            is ConeTypeParameterType -> {
                append(type.lookupTag.name.asString())
            }
            is ConeIntersectionType -> {
                type.intersectedTypes.joinTo(this, "&", prefix = "(", postfix = ")") {
                    renderType(it)
                }
            }
            is ConeFlexibleType -> {
                append(renderFlexibleType(renderType(type.lowerBound), renderType(type.upperBound)))
            }
            else -> append("???")
        }

        if (type.type?.isMarkedNullable == true) {
            append("?")
        }
//        if (!coneType.canBeNull) {
//            append("!!")
//        }
    }

    fun shouldRenderAsPrettyFunctionType(type: ConeKotlinType): Boolean {
        return type.type.isBuiltinFunctionalType(session) && type.typeArguments.none { it.kind == ProjectionKind.STAR }
    }

    private fun differsOnlyInNullability(lower: String, upper: String) =
        lower == upper.replace("?", "") || upper.endsWith("?") && ("$lower?") == upper || "($lower)?" == upper

    fun renderFlexibleType(lowerRendered: String, upperRendered: String): String {
        if (differsOnlyInNullability(lowerRendered, upperRendered)) {
            if (upperRendered.startsWith("(")) {
                // the case of complex type, e.g. (() -> Unit)?
                return "($lowerRendered)!"
            }
            return "$lowerRendered!"
        }

        val kotlinCollectionsPrefix = "kotlin.collections."
        val mutablePrefix = "Mutable"
        // java.util.List<Foo> -> (Mutable)List<Foo!>!
        val simpleCollection = replacePrefixes(
            lowerRendered,
            kotlinCollectionsPrefix + mutablePrefix,
            upperRendered,
            kotlinCollectionsPrefix,
            "$kotlinCollectionsPrefix($mutablePrefix)"
        )
        if (simpleCollection != null) return simpleCollection
        // java.util.Map.Entry<Foo, Bar> -> (Mutable)Map.(Mutable)Entry<Foo!, Bar!>!
        val mutableEntry = replacePrefixes(
            lowerRendered,
            kotlinCollectionsPrefix + "MutableMap.MutableEntry",
            upperRendered,
            kotlinCollectionsPrefix + "Map.Entry",
            "$kotlinCollectionsPrefix(Mutable)Map.(Mutable)Entry"
        )
        if (mutableEntry != null) return mutableEntry

        val kotlinPrefix = "kotlin."
        // Foo[] -> Array<(out) Foo!>!
        val array = replacePrefixes(
            lowerRendered,
            kotlinPrefix + "Array<",
            upperRendered,
            kotlinPrefix + "Array<out ",
            kotlinPrefix + "Array<(out) "
        )
        if (array != null) return array

        return "($lowerRendered..$upperRendered)"
    }

    private fun replacePrefixes(
        lowerRendered: String,
        lowerPrefix: String,
        upperRendered: String,
        upperPrefix: String,
        foldedPrefix: String
    ): String? {
        if (lowerRendered.startsWith(lowerPrefix) && upperRendered.startsWith(upperPrefix)) {
            val lowerWithoutPrefix = lowerRendered.substring(lowerPrefix.length)
            val upperWithoutPrefix = upperRendered.substring(upperPrefix.length)
            val flexibleCollectionName = foldedPrefix + lowerWithoutPrefix

            if (lowerWithoutPrefix == upperWithoutPrefix) return flexibleCollectionName

            if (differsOnlyInNullability(lowerWithoutPrefix, upperWithoutPrefix)) {
                return "$flexibleCollectionName!"
            }
        }
        return null
    }

    fun renderTypeArguments(typeArguments: Array<out ConeTypeProjection>): String = if (typeArguments.isEmpty()) ""
    else buildString {
        append("<")
        this.appendTypeProjections(typeArguments)
        append(">")
    }

    private fun StringBuilder.renderTypeConstructorAndArguments(type: ConeClassLikeType) {

        fun renderTypeArguments(typeArguments: Array<out ConeTypeProjection>, range: IntRange) {
            if (range.any()) {
                typeArguments.slice(range).joinTo(this, ", ", prefix = "<", postfix = ">") {
                    renderTypeProjection(it)
                }
            }
        }

        fun tryGetClassById(classId: ClassId): FirRegularClass? =
            session.symbolProvider.getClassLikeSymbolByFqName(classId)?.fir as? FirRegularClass

        val classToRender = type.lookupTag.toSymbol(session).let {
            when (it) {
                is FirTypeAliasSymbol -> it.fir.fullyExpandedClass(session)
                else -> it?.fir
            }
        } as? FirRegularClass

        if (classToRender == null) {
            append("???")
            return
        }

        if (!classToRender.isLocal && options?.shortQualifiedNames == true) {
            append(classToRender.classId.packageFqName.asString()).append(".")
        }

        val designation = classToRender.collectDesignation()
        var renderedTypeArgumentsCount = 0

        var addPoint = false
        fun addPointIfNeeded() {
            if (addPoint) {
                append('.')
            }
            addPoint = true
        }


        for (currentClass in designation) {
            if (currentClass is FirRegularClass) {
                addPointIfNeeded()
                append(currentClass.name)

                val typeParametersCount = currentClass.typeParameters.count { it is FirTypeParameter }
                renderTypeArguments(type.typeArguments, renderedTypeArgumentsCount until renderedTypeArgumentsCount + typeParametersCount)
                renderedTypeArgumentsCount += typeParametersCount
            }
        }

//
//
//
//
//        fun renderWithOuters(currentId: ClassId, typeArguments: Array<out ConeTypeProjection>): Int {
//
//            val currentClassLikeDeclaration = tryGetClassById(currentId)
//            if (currentClassLikeDeclaration == null) {
//                append("???")
//                return 0
//            }
//
//            val outerId = currentId.outerClassId
//            val outerClassParametersCount = if (currentClassLikeDeclaration.isInner && outerId != null) {
//                val outerCount = renderWithOuters(outerId, typeArguments)
//                append(".")
//                append(currentId.shortClassName.identifier)
//                outerCount
//            } else {
//                renderFullClassId(currentId)
//                0
//            }
//
//            val parametersWithOuterCount = currentClassLikeDeclaration.typeParameters.size
//            val parametersStartPosition = typeArguments.size - parametersWithOuterCount
//            val parametersEndPosition = typeArguments.size - outerClassParametersCount - 1
//            renderTypeArguments(typeArguments, parametersStartPosition..parametersEndPosition)
//
//            return parametersWithOuterCount
//        }
//
//        renderWithOuters(type.lookupTag.classId, type.typeArguments)
    }

    private fun renderTypeProjection(typeProjection: ConeTypeProjection): String {
        val type = typeProjection.type?.let(::renderType) ?: "???"
        return when (typeProjection.kind) {
            ProjectionKind.STAR -> "*"
            ProjectionKind.IN -> "in $type"
            ProjectionKind.OUT -> "out $type"
            ProjectionKind.INVARIANT -> type
        }
    }

    private fun StringBuilder.appendTypeProjections(typeProjections: Array<out ConeTypeProjection>) {
        typeProjections.joinTo(this, ", ") {
            renderTypeProjection(it)
        }
    }

    private fun StringBuilder.renderFunctionType(type: ConeClassLikeType) {
        val lengthBefore = length
        // we need special renderer to skip @ExtensionFunctionType
        val hasAnnotations = length != lengthBefore

        val isSuspend = type.isSuspendFunctionType(session)
        val isNullable = type.isMarkedNullable

        val receiverType = type.receiverType(session)

        val needParenthesis = isNullable || (hasAnnotations && receiverType != null)
        if (needParenthesis) {
            if (isSuspend) {
                insert(lengthBefore, '(')
            } else {
                if (hasAnnotations) {
                    assert(last() == ' ')
                    if (get(lastIndex - 1) != ')') {
                        // last annotation rendered without parenthesis - need to add them otherwise parsing will be incorrect
                        insert(lastIndex, "()")
                    }
                }

                append("(")
            }
        }

        if (isSuspend) {
            append("suspend")
            append(" ")
        }

        if (receiverType != null) {
            val surroundReceiver = shouldRenderAsPrettyFunctionType(receiverType) && !receiverType.isMarkedNullable ||
                    (receiverType.isSuspendFunctionType(session) /*TODO: || receiverType.annotations.isNotEmpty()*/)
            if (surroundReceiver) {
                append("(")
            }
            append(renderType(receiverType))
            if (surroundReceiver) {
                append(")")
            }
            append(".")
        }

        append("(")

        val parameterTypes = type.valueParameterTypesIncludingReceiver(session)
        var needComma = false
        for ((index, typeProjection) in parameterTypes.withIndex()) {

            if (typeProjection == null) continue
            if (index == 0 && receiverType != null) continue

            if (needComma) {
                append(", ")
            } else {
                needComma = true
            }

            //TODO support for parameterNamesInFunctionalTypes
            //val name = if (parameterNamesInFunctionalTypes) typeProjection.type.extractParameterNameFromFunctionTypeArgument() else null
            val name: String? = null
            if (name != null) {
                //append(renderName(name, false))
                append(name)
                append(": ")
            }

            appendTypeProjections(arrayOf(typeProjection))
        }

        append(") ").append("->").append(" ")

        val returnType = type.returnType(session)
        if (returnType != null) {
            append(renderType(returnType))
        } else {
            append("???")
        }

        if (needParenthesis) append(")")

        if (isNullable) append("?")
    }
}