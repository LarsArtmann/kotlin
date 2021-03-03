/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.renderer

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.idea.frontend.api.fir.types.PublicTypeApproximator
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

internal class FirRenderer private constructor(
    private var containingDeclaration: FirDeclaration?,
    private val options: FirRendererOptions,
    private val typeRenderer: ConeTypeRenderer,
    private val session: FirSession
) : FirVisitor<Unit, StringBuilder>() {

//    private val functionTypeAnnotationsRenderer: FirRenderer by lazy {
//        withOptions {
//            ExcludedTypeAnnotations.internalAnnotationsForResolve += listOf(StandardNames.FqNames.extensionFunctionType)
//        } as FirRenderer
//    }

    private fun StringBuilder.renderAnnotations(annotated: FirAnnotatedDeclaration, target: AnnotationUseSiteTarget? = null) {
        if (RendererModifier.ANNOTATIONS in options.modifiers) {
            renderAnnotations(typeRenderer, options.typeRendererOptions, annotated.annotations, target)
        }
    }

    private fun renderType(type: ConeTypeProjection, annotations: List<FirAnnotationCall>? = null): String =
        typeRenderer.renderType(type, annotations)

    private fun renderType(firRef: FirTypeRef, approximate: Boolean = false): String {
        assert(firRef is FirResolvedTypeRef)

        val approximatedIfNeeded = approximate.ifTrue {
            PublicTypeApproximator.approximateTypeToPublicDenotable(firRef.coneType, session)
        } ?: firRef.coneType
        return renderType(approximatedIfNeeded, firRef.annotations)
    }

    private fun StringBuilder.renderName(declaration: FirDeclaration, rootRenderedElement: Boolean) {
        if (declaration is FirAnonymousObject) {
            append("<no name provided>")
            return
        }

        val name = when (declaration) {
            is FirRegularClass -> declaration.name
            is FirSimpleFunction -> declaration.name
            is FirProperty -> declaration.name
            is FirValueParameter -> declaration.name
            is FirTypeParameter -> declaration.name
            is FirTypeAlias -> declaration.name
            is FirEnumEntry -> declaration.name
            else -> Name.special("INVALID NAME") //TODO
        }
        append(name.render())
    }

    private fun StringBuilder.renderCompanionObjectName(descriptor: FirRegularClass) {
        if (descriptor.name != SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT) {
            renderSpaceIfNeeded()
            append(descriptor.name.render())
        }
    }

    private fun StringBuilder.renderVisibility(visibility: Visibility): Boolean {
        @Suppress("NAME_SHADOWING")
        var visibility = visibility
        if (RendererModifier.VISIBILITY !in options.modifiers) return false
        if (options.normalizedVisibilities) {
            visibility = visibility.normalize()
        }
        if (visibility == Visibilities.DEFAULT_VISIBILITY) return false
        append(visibility.internalDisplayName).append(" ")
        return true
    }

    private fun StringBuilder.renderModality(modality: Modality, defaultModality: Modality) {
        //if (modality == defaultModality) return
        renderModifier(RendererModifier.MODALITY in options.modifiers, modality.name.toLowerCaseAsciiOnly())
    }

    private fun FirMemberDeclaration.implicitModalityWithoutExtensions(containingDeclaration: FirDeclaration?): Modality {
        if (this is FirRegularClass) {
            return if (classKind == ClassKind.INTERFACE) Modality.ABSTRACT else Modality.FINAL
        }
        val containingClassDescriptor = containingDeclaration as? FirRegularClass ?: return Modality.FINAL
        if (this !is FirCallableMemberDeclaration<*>) return Modality.FINAL
        if (overridesSomething(this)) {
            if (containingClassDescriptor.modality != Modality.FINAL) return Modality.OPEN
        }
        return if (containingClassDescriptor.classKind == ClassKind.INTERFACE && this.visibility != DescriptorVisibilities.PRIVATE) {
            if (this.modality == Modality.ABSTRACT) Modality.ABSTRACT else Modality.OPEN
        } else
            Modality.FINAL
    }

    private fun StringBuilder.renderModalityForCallable(
        callable: FirCallableMemberDeclaration<*>,
        containingDeclaration: FirDeclaration?
    ) {
        val modality = callable.modality ?: return
        val isTopLevel = containingDeclaration == null
        if (!isTopLevel || modality != Modality.FINAL) {
            if (/*modality == Modality.OPEN && */overridesSomething(callable)) {
                return
            }
            renderModality(modality, callable.implicitModalityWithoutExtensions(containingDeclaration))
        }
    }

    private fun StringBuilder.renderOverride(callableMember: FirCallableMemberDeclaration<*>) {
        if (RendererModifier.OVERRIDE !in options.modifiers) return
        if (overridesSomething(callableMember)) {
            renderModifier(true, "override")
        }
    }

    private fun StringBuilder.renderModifier(value: Boolean, modifier: String) {
        if (value) {
            append(modifier)
            append(" ")
        }
    }

    private fun StringBuilder.renderMemberModifiers(descriptor: FirMemberDeclaration) {
        renderModifier(descriptor.isExternal, "external")
        renderModifier(RendererModifier.EXPECT in options.modifiers && descriptor.isExpect, "expect")
        renderModifier(RendererModifier.ACTUAL in options.modifiers && descriptor.isActual, "actual")
    }

    private fun StringBuilder.renderAdditionalModifiers(functionDescriptor: FirMemberDeclaration) {
        val isOperator =
            functionDescriptor.isOperator// && functionDescriptor.overriddenDescriptors.none { it.isOperator } //TODO
        val isInfix =
            functionDescriptor.isInfix// && functionDescriptor.overriddenDescriptors.none { it.isInfix } //TODO

        renderModifier(functionDescriptor.isTailRec, "tailrec")
        renderSuspendModifier(functionDescriptor)
        renderModifier(functionDescriptor.isInline, "inline")
        renderModifier(isInfix, "infix")
        renderModifier(isOperator, "operator")
    }

    private fun StringBuilder.renderSuspendModifier(functionDescriptor: FirMemberDeclaration) {
        renderModifier(functionDescriptor.isSuspend, "suspend")
    }

    override fun visitValueParameter(valueParameter: FirValueParameter, data: StringBuilder) {
        data.renderValueParameter(valueParameter, containingDeclaration is FirConstructor, true)
    }

    override fun visitProperty(property: FirProperty, data: StringBuilder) = with(data) {
        appendLine()
        appendTabs()
        renderPropertyAnnotations(property)
        renderVisibility(property.visibility)
        renderModifier(RendererModifier.CONST in options.modifiers && property.isConst, "const")
        renderMemberModifiers(property)
        renderModalityForCallable(property, containingDeclaration)
        renderOverride(property)
        renderModifier(RendererModifier.LATEINIT in options.modifiers && property.isLateInit, "lateinit")
        renderValVarPrefix(property)
        renderTypeParameters(property.typeParameters, true)
        renderReceiver(property)

        renderName(property, true)
        append(": ").append(renderType(property.returnTypeRef, approximate = options.approximateTypes))

        renderWhereSuffix(property.typeParameters)
    }

    override fun visitPropertyAccessor(propertyAccessor: FirPropertyAccessor, data: StringBuilder) {
        require(containingDeclaration is FirProperty) { "Invalid containing declaration" }
        with(data) {
            renderAnnotations(propertyAccessor)
            renderVisibility(propertyAccessor.visibility)
            renderModalityForCallable(propertyAccessor, containingDeclaration)
            renderMemberModifiers(propertyAccessor)
            renderAdditionalModifiers(propertyAccessor)

            val keyword = if (propertyAccessor.isGetter) "get" else "set"
            append(keyword).append(" ")
        }

//        renderValueParameters(function.valueParameters, function.hasSynthesizedParameterNames(), builder)

        if (options.renderContainingDeclarations) {
            propertyAccessor.body?.accept(this, data)
        }
    }

    override fun visitSimpleFunction(simpleFunction: FirSimpleFunction, data: StringBuilder) {
        with(data) {
            appendLine()
            appendTabs()
            renderAnnotations(simpleFunction)
            renderVisibility(simpleFunction.visibility)

            renderModalityForCallable(simpleFunction, containingDeclaration)
            renderMemberModifiers(simpleFunction)
            renderOverride(simpleFunction)
            renderAdditionalModifiers(simpleFunction)
            append("fun ")
            renderTypeParameters(simpleFunction.typeParameters, true)
            renderReceiver(simpleFunction)
            renderName(simpleFunction, true)
            renderValueParameters(simpleFunction.valueParameters, false)

            val returnType = simpleFunction.returnTypeRef
            if (options.unitReturnType || (!returnType.isUnit)) {
                append(": ").append(renderType(returnType, approximate = options.approximateTypes))
            }

            renderWhereSuffix(simpleFunction.typeParameters)
        }

        if (options.renderContainingDeclarations) {
            simpleFunction.body?.let {
                underBlockDeclaration(simpleFunction, it, data)
            }
        }
    }

    override fun visitThisReceiverExpression(thisReceiverExpression: FirThisReceiverExpression, data: StringBuilder) {
        data.append("this") // renders <this>
        //TODO was builder.append(descriptor.name) // renders <this>
    }

    override fun visitAnonymousObject(anonymousObject: FirAnonymousObject, data: StringBuilder) {
        with(data) {
            appendLine()
            appendTabs()

            renderAnnotations(anonymousObject)
            append(getClassifierKindPrefix(anonymousObject))
            renderSuperTypes(anonymousObject)
        }

        if (options.renderContainingDeclarations) {
            data.underBlockDeclaration(anonymousObject) {
                anonymousObject.declarations.forEach {
                    it.accept(this, data)
                }
            }
        }
    }

    override fun visitConstructor(constructor: FirConstructor, data: StringBuilder) {
        with(data) {
            appendLine()
            appendTabs()
            val containingClass = containingDeclaration
            check(containingClass is FirDeclaration && (containingClass is FirClass<*> || containingClass is FirEnumEntry)) {
                "Invalid renderer containing declaration for constructor"
            }
            renderAnnotations(constructor)
//        val visibilityRendered = (options.renderDefaultVisibility || constructor.constructedClass.modality != Modality.SEALED)
//                && renderVisibility(constructor.visibility, builder)

            append("constructor")
            append(" ")
            renderName(containingClass, true)
            renderTypeParameterRefs(constructor.typeParameters, false)
            renderValueParameters(constructor.valueParameters, constructor.isPrimary)
            renderWhereSuffix(constructor.typeParameters)
        }

        if (options.renderContainingDeclarations) {
            constructor.body?.let {
                underBlockDeclaration(constructor, it, data)
            }
        }
    }

    override fun visitTypeParameter(typeParameter: FirTypeParameter, data: StringBuilder) {
        data.renderTypeParameter(typeParameter, true)
    }

    override fun visitRegularClass(regularClass: FirRegularClass, data: StringBuilder) {
        with(data) {
            appendLine()
            appendTabs()

            renderAnnotations(regularClass)
            if (regularClass.classKind != ClassKind.ENUM_ENTRY) {
                renderVisibility(regularClass.visibility)
            }

            val haveNotModality = regularClass.classKind == ClassKind.INTERFACE && regularClass.modality == Modality.ABSTRACT ||
                    regularClass.classKind.isSingleton && regularClass.modality == Modality.FINAL
            if (!haveNotModality) {
                regularClass.modality?.let {
                    renderModality(it, regularClass.implicitModalityWithoutExtensions(containingDeclaration))
                }
            }
            renderMemberModifiers(regularClass)
            renderModifier(RendererModifier.INNER in options.modifiers && regularClass.isInner, "inner")
            renderModifier(RendererModifier.DATA in options.modifiers && regularClass.isData, "data")
            renderModifier(RendererModifier.INLINE in options.modifiers && regularClass.isInline, "inline")
            //TODO renderModifier(data, RendererModifier.VALUE in modifiers && regularClass.isValue, "value")
            renderModifier(RendererModifier.FUN in options.modifiers && regularClass.isFun, "fun")
            data.append(getClassifierKindPrefix(regularClass))

            if (!regularClass.isCompanion) {
                renderSpaceIfNeeded()
                renderName(regularClass, true)
            } else {
                renderCompanionObjectName(regularClass)
            }

            if (regularClass.classKind == ClassKind.ENUM_ENTRY) return

            val typeParameters = regularClass.typeParameters.filterIsInstance<FirTypeParameter>()
            renderTypeParameterRefs(typeParameters, false)
            renderSuperTypes(regularClass)
            renderWhereSuffix(typeParameters)
        }

        if (options.renderContainingDeclarations) {
            data.underBlockDeclaration(regularClass) {
                regularClass.declarations.forEach {
                    it.accept(this, data)
                }
            }
        }
    }

    private var tabbedString = ""

    private inline fun StringBuilder.underBlockDeclaration(firDeclaration: FirDeclaration, body: () -> Unit) {
        val oldLength = length
        append(" {")
        val oldTabbedString = tabbedString
        tabbedString = " ".repeat(tabbedString.length + 4)
        val unchangedLength = length

        val oldContainingDeclaration = containingDeclaration
        containingDeclaration = firDeclaration
        body()
        containingDeclaration = oldContainingDeclaration

        tabbedString = oldTabbedString
        if (unchangedLength != length) {
            appendLine()
            appendTabs()
            append("}")
        } else {
            delete(oldLength, length)
        }
    }

    private fun StringBuilder.appendTabs() = append(tabbedString)

    private fun underBlockDeclaration(firDeclaration: FirDeclaration, firBlock: FirBlock, data: StringBuilder) {
        data.underBlockDeclaration(firDeclaration) {
            firBlock.accept(this, data)
        }
    }

    override fun visitTypeAlias(typeAlias: FirTypeAlias, data: StringBuilder) = with(data) {
        renderAnnotations(typeAlias)
        renderVisibility(typeAlias.visibility)
        renderMemberModifiers(typeAlias)
        append("typealias").append(" ")
        renderName(typeAlias, true)
        renderTypeParameters(typeAlias.typeParameters, false)
        append(" = ").append(renderType(typeAlias.expandedTypeRef))
        Unit
    }

    override fun visitElement(element: FirElement, data: StringBuilder) {
        element.acceptChildren(this, data)
    }

    companion object {
        fun render(
            declarationDescriptor: FirDeclaration,
            containingDeclaration: FirDeclaration?,
            options: FirRendererOptions,
            coneTypeRenderer: ConeTypeRenderer,
            session: FirSession
        ): String {
            val renderer = FirRenderer(
                containingDeclaration,
                options,
                coneTypeRenderer,
                session,
            )
            return buildString {
                declarationDescriptor.accept(renderer, this)
            }.trim(' ', '\n', '\t')
        }
    }


    /* TYPE PARAMETERS */
    private fun StringBuilder.renderTypeParameter(typeParameter: FirTypeParameter, topLevel: Boolean) {
        if (topLevel) {
            append("<")
        }

        renderModifier(typeParameter.isReified, "reified")
        val variance = typeParameter.variance.label
        renderModifier(variance.isNotEmpty(), variance)

        renderAnnotations(typeParameter)

        renderName(typeParameter, topLevel)
        val upperBoundsCount = typeParameter.bounds.size
        if ((upperBoundsCount > 1 && !topLevel) || upperBoundsCount == 1) {
            val upperBound = typeParameter.bounds.iterator().next()
            if (!upperBound.isNullableAny) {
                append(" : ").append(renderType(upperBound))
            }
        } else if (topLevel) {
            var first = true
            for (upperBound in typeParameter.bounds) {
                if (upperBound.isNullableAny) {
                    continue
                }
                if (first) {
                    append(" : ")
                } else {
                    append(" & ")
                }
                append(renderType(upperBound))
                first = false
            }
        } else {
            // rendered with "where"
        }

        if (topLevel) {
            append(">")
        }
    }

    private fun StringBuilder.renderTypeParameterRefs(typeParameters: List<FirTypeParameterRef>, withSpace: Boolean) =
        renderTypeParameters(typeParameters.map { it.symbol.fir }, withSpace)

    private fun StringBuilder.renderTypeParameters(typeParameters: List<FirTypeParameter>, withSpace: Boolean) {
        if (typeParameters.isNotEmpty()) {
            append("<")
            renderTypeParameterList(typeParameters)
            append(">")
            if (withSpace) {
                append(" ")
            }
        }
    }

    private fun StringBuilder.renderTypeParameterList(typeParameters: List<FirTypeParameter>) {
        val iterator = typeParameters.iterator()
        while (iterator.hasNext()) {
            val typeParameterDescriptor = iterator.next()
            renderTypeParameter(typeParameterDescriptor, false)
            if (iterator.hasNext()) {
                append(", ")
            }
        }
    }

    private fun StringBuilder.renderReceiver(callableDescriptor: FirCallableDeclaration<*>) {
        val receiverType = callableDescriptor.receiverTypeRef
        if (receiverType != null) {
            renderAnnotations(callableDescriptor, AnnotationUseSiteTarget.RECEIVER)

            var result = renderType(receiverType)

            if (typeRenderer.shouldRenderAsPrettyFunctionType(receiverType.coneType) && receiverType.isMarkedNullable == true) {
                result = "($result)"
            }
            append(result).append(".")
        }
    }

    private fun StringBuilder.renderWhereSuffix(typeParameters: List<FirTypeParameterRef>) {

        val upperBoundStrings = ArrayList<String>(0)

        for (typeParameter in typeParameters) {
            val typeParameterFir = typeParameter.symbol.fir
            typeParameterFir.bounds
                .drop(1) // first parameter is rendered by renderTypeParameter
                .mapTo(upperBoundStrings) { typeParameterFir.name.render() + " : " + renderType(it) }
        }

        if (upperBoundStrings.isNotEmpty()) {
            append(" ").append("where").append(" ")
            upperBoundStrings.joinTo(this, ", ")
        }
    }

    private fun StringBuilder.renderValueParameters(
        parameters: List<FirValueParameter>,
        isInPrimaryConstructor: Boolean,
    ) {
        val parameterCount = parameters.size

        append("(")
        for ((index, parameter) in parameters.withIndex()) {
            renderValueParameter(parameter, isInPrimaryConstructor, false)
            if (index != parameterCount - 1) {
                append(", ")
            }
        }
        append(")")
    }

    /* VARIABLES */
    private fun StringBuilder.renderValueParameter(
        valueParameter: FirValueParameter,
        isInPrimaryConstructor: Boolean,
        topLevel: Boolean,
    ) {
        if (topLevel) {
            append("value-parameter").append(" ")
        }

        renderAnnotations(valueParameter)
        renderModifier(valueParameter.isCrossinline, "crossinline")
        renderModifier(valueParameter.isNoinline, "noinline")
        renderVariable(valueParameter, topLevel, isInPrimaryConstructor)

        val withDefaultValue = valueParameter.defaultValue != null //TODO check if default value is inherited
        if (withDefaultValue) {
            append(" = ...")
        }
    }

    private fun StringBuilder.renderValVarPrefix(variable: FirVariable<*>, isInPrimaryConstructor: Boolean = false) {
        if (isInPrimaryConstructor || variable !is FirValueParameter) {
            append(if (variable.isVar) "var" else "val").append(" ")
        }
    }

    private fun StringBuilder.renderVariable(
        variable: FirVariable<*>,
        topLevel: Boolean,
        isInPrimaryConstructor: Boolean
    ) {
        val typeToRender = variable.returnTypeRef

        val isVarArg = (variable as? FirValueParameter)?.isVararg ?: false
        renderModifier(isVarArg, "vararg")

        if (isInPrimaryConstructor || topLevel) {
            renderValVarPrefix(variable, isInPrimaryConstructor)
        }

        renderName(variable, topLevel)
        append(": ")
        append(renderType(typeToRender))
    }

    private fun StringBuilder.renderPropertyAnnotations(property: FirProperty) {
        if (RendererModifier.ANNOTATIONS !in options.modifiers) return

        renderAnnotations(property)


//        property.backingField?.let { builder.renderAnnotations(it, AnnotationUseSiteTarget.FIELD) }
//        property.delegateField?.let { builder.renderAnnotations(it, AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD) }

//        if (PropertyAccessorRenderingPolicy.DEBUG == PropertyAccessorRenderingPolicy.NONE) {
//            property.getter?.let {
//                builder.renderAnnotations(it, AnnotationUseSiteTarget.PROPERTY_GETTER)
//            }
//            property.setter?.let { setter ->
//                setter.let {
//                    builder.renderAnnotations(it, AnnotationUseSiteTarget.PROPERTY_SETTER)
//                }
//                setter.valueParameters.single().let {
//                    builder.renderAnnotations(it, AnnotationUseSiteTarget.SETTER_PARAMETER)
//                }
//            }
//        }
    }

    private fun StringBuilder.renderSuperTypes(klass: FirClass<*>) {

        if (klass.defaultType().isNothing) return

        val supertypes = klass.superTypeRefs
        if (supertypes.isEmpty() || klass.superTypeRefs.singleOrNull()?.let { it.isAny || it.isNullableAny } == true) return

        renderSpaceIfNeeded()
        append(": ")
        supertypes.joinTo(this, ", ") { renderType(it) }
    }


    private fun getClassifierKindPrefix(classifier: FirDeclaration): String = when (classifier) {
        is TypeAliasDescriptor -> "typealias"
        is FirRegularClass ->
            if (classifier.isCompanion) {
                "companion object"
            } else {
                when (classifier.classKind) {
                    ClassKind.CLASS -> "class"
                    ClassKind.INTERFACE -> "interface"
                    ClassKind.ENUM_CLASS -> "enum class"
                    ClassKind.OBJECT -> "object"
                    ClassKind.ANNOTATION_CLASS -> "annotation class"
                    ClassKind.ENUM_ENTRY -> "enum entry"
                }
            }
        is FirAnonymousObject -> "object"
        else ->
            throw AssertionError("Unexpected classifier: $classifier")
    }

    private fun StringBuilder.renderSpaceIfNeeded() {
        if (length == 0 || this[length - 1] != ' ') {
            append(' ')
        }
    }

    //TODO
    private fun overridesSomething(callable: FirCallableMemberDeclaration<*>) = (callable.psi as? KtCallableDeclaration)
        ?.hasModifier(KtTokens.OVERRIDE_KEYWORD) ?: false
}
