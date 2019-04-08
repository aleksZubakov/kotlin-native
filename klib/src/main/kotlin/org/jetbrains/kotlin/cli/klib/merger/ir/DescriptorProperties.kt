package org.jetbrains.kotlin.cli.klib.merger.ir

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import sun.java2d.pipe.SpanShapeRenderer

interface DescriptorProperties {}

class ModuleDescriptorProperties(
        val name: Name,
        val stableName: Name? = null
) : DescriptorProperties {}

class PackageDescriptorProperties(
        val fqName: FqName
) : DescriptorProperties {}

sealed class MergedDescriptor() : DescriptorProperties {}

class ClassDescriptorProperties(
        // TODO do leave FqName or Name only, find out how
        val fqName: FqName,
        val name: Name,
        val isExternal: Boolean,
        val modality: Modality,
        val visibility: Visibility,
        val kind: ClassKind,
        val isInline: Boolean,
        val isData: Boolean,
        val isCompanionObject: Boolean,
        val isInner: Boolean,
        val constructors: Set<ClassConstructorDescriptor> = setOf(),
        val annotations: Annotations = Annotations.EMPTY
) : MergedDescriptor() {
    val supertypes = mutableListOf<KotlinType>()

    fun addSupertypes(supertypes: Collection<KotlinType>) {
        this.supertypes.addAll(supertypes)
    }
}

class CommonTypeAliasProperties(
        annotations: Annotations,
        fqName: FqName,
        name: Name,
        // TODO remove types from here
        declaredTypeParameters: List<TypeParameterDescriptor>,
        underlyingType: SimpleType,
        expandedType: SimpleType,
        visibility: Visibility,
        isExternal: Boolean,
        val modality: Modality,
        val kind: ClassKind,
        val supertypes: MutableList<KotlinType> = mutableListOf()
) : TypeAliasProperties(annotations, fqName, name, declaredTypeParameters, underlyingType, expandedType, visibility, isExternal) {
    override fun addSupertypes(supertypes: Collection<KotlinType>) {
        this.supertypes.addAll(supertypes)
    }
}

class FunctionDescriptorProperties(
        val annotations: Annotations,
        val name: Name,
        val kind: CallableMemberDescriptor.Kind,
        val isExternal: Boolean,
        val extensionReceiverParameter: ReceiverParameterDescriptor?,
        val dispatchReceiverParameter: ReceiverParameterDescriptor?,
        val typeParameters: List<TypeParameterDescriptor>,
        val valueParameters: List<ValueParameterDescriptor>,
        val returnType: KotlinType?,
        val modality: Modality,
        val visibility: Visibility
) : MergedDescriptor() {
}

class PropertyDescriptorProperties(
        val annotations: Annotations,
        val modality: Modality,
        val visibility: Visibility,
        val isVar: Boolean,
        val name: Name,
        val kind: CallableMemberDescriptor.Kind,
        val isLateInit: Boolean,
        val isConst: Boolean,
        val isExternal: Boolean
) : MergedDescriptor() {}

open class TypeAliasProperties(
        val annotations: Annotations,
        val fqName: FqName,
        val name: Name,
        // TODO remove types from here
        val declaredTypeParameters: List<TypeParameterDescriptor>,
        val underlyingType: SimpleType,
        val expandedType: SimpleType,
        val visibility: Visibility,
        val isExternal: Boolean
) : MergedDescriptor() {
    open fun addSupertypes(supertypes: Collection<KotlinType>) {
    }
}

class ValueProperties(
        val oldValueDescriptor: ValueDescriptor
) : MergedDescriptor()