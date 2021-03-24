/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.lombok

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaClassDescriptor
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.load.java.structure.JavaField
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.jvm.SyntheticJavaPartsProvider
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

class LombokSyntheticJavaPartsProvider : SyntheticJavaPartsProvider {

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        val getters = makeGetters(thisDescriptor)
        return getters.map { it.name }
    }

    private fun makeGetters(thisDescriptor: ClassDescriptor): List<SimpleFunctionDescriptor> {
        extractClass(thisDescriptor)?.let { jClass ->
            val fieldsToGet = jClass.fields.filter { it.findAnnotation(LombokAnnotationNames.GETTER) != null }
            val getters = fieldsToGet.map { makeGetter(thisDescriptor, it) }
            return getters
        }
        return emptyList()
    }

    private fun makeGetter(classDescriptor: ClassDescriptor, field: JavaField): SimpleFunctionDescriptor {
        val fieldDescriptor =
            classDescriptor.unsubstitutedMemberScope.getContributedVariables(field.name, NoLookupLocation.FROM_SYNTHETIC_SCOPE).single()
        val methodDescriptor = SimpleFunctionDescriptorImpl.create(
            classDescriptor,
            Annotations.EMPTY,
            Name.identifier("get" + field.name.identifier.capitalizeAsciiOnly()),
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            classDescriptor.source
        )
        methodDescriptor.initialize(
            null,
            classDescriptor.thisAsReceiverParameter,
            mutableListOf(),
            emptyList(),
            fieldDescriptor.returnType,
            Modality.OPEN,
            DescriptorVisibilities.PUBLIC
        )
        return methodDescriptor
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        extractClass(thisDescriptor)?.let { jClass ->
            val fieldName = propertyNameByGetMethodName(name)
            val fieldsToGet = jClass.fields.filter { it.name == fieldName && it.findAnnotation(LombokAnnotationNames.GETTER) != null }
            val getters = fieldsToGet.map { makeGetter(thisDescriptor, it) }
            result.addAll(getters)
        }
    }

    private fun extractClass(descriptor: ClassDescriptor): JavaClassImpl? =
        (descriptor as? LazyJavaClassDescriptor)?.jClass as? JavaClassImpl


}
