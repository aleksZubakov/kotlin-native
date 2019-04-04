package org.jetbrains.kotlin.cli.klib.merger.comparators

import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.CommonAndTargets
import org.jetbrains.kotlin.cli.klib.DummyIntersector
import org.jetbrains.kotlin.cli.klib.Mismatched
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType

//interface DescriptorComparator {
//    fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult
//}
//
//val KOTLINX_PACKAGE_NAME = Name.identifier("kotlinx")
//fun isUnderKotlinOrKotlinxPackage(descriptor: DeclarationDescriptor): Boolean {
//    var current: DeclarationDescriptor? = descriptor
//    while (current != null) {
//        if (current is PackageFragmentDescriptor) {
//            return current.fqName.startsWith(KotlinBuiltIns.BUILT_INS_PACKAGE_NAME) ||
//                    current.fqName.startsWith(KOTLINX_PACKAGE_NAME)
//        }
//        current = current.containingDeclaration
//    }
//    return false
//}
//

//class TotalComparator(val intersector: DummyIntersector) : DescriptorComparator {
//    companion object {
////        val INSTANCE = TotalComparator()
//    }
//
//    private val functionComparator = FunctionDescriptorComparator(intersector)
//    private val valueComparator = ValueDescriptorComparator(intersector)
//    private val classDescriptorComparator = ClassDescriptorComparator(intersector)
//
//
//    override fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult {
//        if (o1 === o2) return Success()
//
//        return when {
//            o1 is FunctionDescriptor && o2 is FunctionDescriptor ->
//                functionComparator.compare(o1, o2).map(::Function)
//
//            o1 is ValueDescriptor && o2 is ValueDescriptor ->
//                valueComparator.compare(o1, o2).map(::Value)
//
//            o1 is ClassDescriptor && o2 is ClassDescriptor ->
//                classDescriptorComparator.compare(o1, o2).map(::Class)
//
//            o1 is TypeAliasDescriptor && o2 is TypeAliasDescriptor ->
//                TypeAliasDescriptorComparator.INSTANCE.compare(o1, o2).map(::TypeAlias)
//
//            else -> Failure(Unknown)
//        }
//    }
//}

private fun compareBuiltinTypes(o1: KotlinType, o2: KotlinType, intersector: DummyIntersector): Boolean {
    return if (o1 is AbbreviatedType && o2 is AbbreviatedType) {
        compareTypes(o1.expandedType, o2.expandedType, intersector) &&
                o1.abbreviation == o2.abbreviation
    } else if (o1 is SimpleType && o2 is SimpleType) {
        o1 == o2 &&
                o1.arguments.size == o2.arguments.size &&
                o1.arguments.zip(o2.arguments).all { (a, b) ->
                    a.projectionKind == b.projectionKind &&
                            compareTypes(a.type, b.type, intersector)
                }
    } else {
        false
    }
}

private fun compareVisibilities(v1: org.jetbrains.kotlin.descriptors.Visibility,
                                v2: org.jetbrains.kotlin.descriptors.Visibility) =
        Visibilities.compare(v1, v2)?.let { it == 0 } ?: false

fun compareTypes(o1: KotlinType, o2: KotlinType, intersector: DummyIntersector): Boolean {
    val o1Descriptor = o1.constructor.declarationDescriptor!!
    val o2Descriptor = o2.constructor.declarationDescriptor!!
    if (isUnderKotlinOrKotlinxPackage(o1Descriptor) || isUnderKotlinOrKotlinxPackage(o2Descriptor)) {
        return compareBuiltinTypes(o1, o2, intersector)
    }

    if (o1 is AbbreviatedType && o2 is AbbreviatedType) {
        val o1Descriptor = o1.abbreviation.constructor.declarationDescriptor!!
        val o2Descriptor = o2.abbreviation.constructor.declarationDescriptor!!

        // TODO may o1Descriptor be not TypeAliasDescriptor??
        if (o1Descriptor is TypeAliasDescriptor && o2Descriptor is TypeAliasDescriptor) {
            if (o1Descriptor.fqNameSafe != o2Descriptor.fqNameSafe) {
                return false
            }

            // TODO not empty by passed type Type arguments
            //  but check type parameters of TypeConstructor?
            if (!(o1.abbreviation.arguments.isEmpty() && o2.abbreviation.arguments.isEmpty() &&
                            o1.expandedType.arguments.isEmpty() && o2.expandedType.arguments.isEmpty())) {
                return false
            }

            val o1RightHandDescriptor = o1Descriptor.underlyingType.constructor.declarationDescriptor!!
            val o2RightHandDescriptor = o2Descriptor.underlyingType.constructor.declarationDescriptor!!

            return when {
                o1RightHandDescriptor is ClassDescriptor && o2RightHandDescriptor is ClassDescriptor ->
                    // TODO !(o1RightHandDescriptor.isInner)
                    // TODO companion
                    // class with inline modifier has have primary constructor, but expect class cannot have such one
                    !o1RightHandDescriptor.isInline && !o2RightHandDescriptor.isInline &&
                            o1RightHandDescriptor.kind == o2RightHandDescriptor.kind &&
                            o1RightHandDescriptor.modality == o2RightHandDescriptor.modality &&
                            compareVisibilities(o1RightHandDescriptor.visibility, o2RightHandDescriptor.visibility)


                // it seems that next case cannot be commonized because right-hand side of actual typealias should
                // be class, not another typealias
                o1RightHandDescriptor is TypeAliasDescriptor && o2RightHandDescriptor is TypeAliasDescriptor -> false

                else -> false
            }
        }

        return false
    }

    val o1typeDeclarationDescriptor = o1.constructor.declarationDescriptor!!
    val o2typeDeclarationDescriptor = o2.constructor.declarationDescriptor!!

    if (o1typeDeclarationDescriptor is ClassDescriptor && o2typeDeclarationDescriptor is ClassDescriptor) {
        return intersector.getResolvedClassifier(o1typeDeclarationDescriptor, o2typeDeclarationDescriptor) !is Mismatched &&
                o1.arguments.size == o2.arguments.size &&
                o1.arguments.zip(o2.arguments).all { (a, b) ->
                    a.projectionKind == b.projectionKind &&
                            compareTypes(a.type, b.type, intersector)
                }
    }

    if (o1 is FlexibleType && o2 is FlexibleType) {
        return compareTypes(o1.lowerBound, o2.lowerBound, intersector) &&
                compareTypes(o1.upperBound, o2.upperBound, intersector)
    }



    return false
}


class NewValueComparator(val intersector: DummyIntersector) : DescriptorComparator<ValueDescriptor> {
    companion object {
//        val INSTANCE = ValueDescriptorComparator()
    }

    override fun compare(o1: ValueDescriptor, o2: ValueDescriptor): ComparisonResult {
        if (o1.visibility == Visibilities.PRIVATE || o2.visibility == Visibilities.PRIVATE) {
            return Failure(Unknown)
        }

        return compareValueDescriptors(o1, o2)
    }

    private fun compareValueDescriptors(o1: ValueDescriptor, o2: ValueDescriptor): ComparisonResult = when {
        o1 is VariableDescriptor && o2 is VariableDescriptor -> compareVariableDescriptors(o1, o2)
        o1 is ValueParameterDescriptor && o2 is ValueParameterDescriptor -> compareValueParameterDescriptors(o1, o2)
        o1 is ReceiverParameterDescriptor && o2 is ReceiverParameterDescriptor -> compareReceiverParameterDescriptor(o1, o2)
        else -> Failure(Unknown)
    }

    private fun KotlinType.isBuiltIn(): Boolean = this.constructor.declarationDescriptor?.let {
        KotlinBuiltIns.isBuiltIn(it)
    } ?: false

    private fun compareTypes(o1: KotlinType, o2: KotlinType): Boolean {
/*
        val a = KotlinBuiltIns.isPrimitiveTypeOrNullablePrimitiveType(o1)
        val b = KotlinBuiltIns.isPrimitiveTypeOrNullablePrimitiveType(o2)
        if (a && b) {
            return o1 == o2
        }

        if (o1 is AbbreviatedType && o2 is AbbreviatedType) {
            return o1.abbreviation.constructor.declarationDescriptor!!.fqNameSafe ==
                    o2.abbreviation.constructor.declarationDescriptor!!.fqNameSafe
        }

        val o1DeclarationDescriptor = o1.constructor.declarationDescriptor!!
        val o2DeclarationDescriptor = o2.constructor.declarationDescriptor!!

        if (o1DeclarationDescriptor is ClassDescriptor && o2DeclarationDescriptor is ClassDescriptor) {

            val res = intersector.getClassDescriptorsIntersection(o1DeclarationDescriptor, o1DeclarationDescriptor)
            return res is CommonAndTargets
        }

        // TODO whooooop
        return false
*/
        return compareTypes(o1, o2, intersector)
    }

    private fun compareReceiverParameterDescriptor(o1: ReceiverParameterDescriptor, o2: ReceiverParameterDescriptor): ComparisonResult {
        val a = o1.value.type.constructor.declarationDescriptor!!
        val b = o2.value.type.constructor.declarationDescriptor!!
        return (intersector.getResolvedClassifier(a, b) is CommonAndTargets<*>).toResult(Unknown)
    }

    private fun compareVariableDescriptors(o1: VariableDescriptor, o2: VariableDescriptor): ComparisonResult =
            (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqName) and
                    (o1.isConst == o2.isConst).toResult(Const) and
                    (o1.isVar == o2.isVar).toResult(IsVar) and // TODO
                    (o1.isLateInit == o2.isLateInit).toResult(LateInit) and
                    (o1.isSuspend == o2.isSuspend).toResult(Suspend) and
                    (compareTypes(o1.type, o2.type)).toResult(Type)
//                    && o1.returnType == o2.returnType

    private fun compareValueParameterDescriptors(o1: ParameterDescriptor, o2: ParameterDescriptor): ComparisonResult =
            (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqName) and
                    (o1.returnType == o2.returnType).toResult(Type) and
//                    && (o1.type == o2.type)
                    (o1.isSuspend == o2.isSuspend).toResult(Suspend) and
                    (Visibilities.compare(o1.visibility, o2.visibility) == 0).toResult(Visibility)

}

class NewFunctionComparator(val intersector: DummyIntersector) : DescriptorComparator<FunctionDescriptor> {
    companion object {
//        val INSTANCE = FunctionDescriptorComparator()
    }

    val valueDescriptorComparator = NewValueComparator(intersector)

    override fun compare(o1: FunctionDescriptor, o2: FunctionDescriptor): ComparisonResult {
        if (o1.visibility == Visibilities.PRIVATE || o2.visibility == Visibilities.PRIVATE) {
            return Failure(Unknown)
        }

        return compareFunctionDescriptors(o1, o2)
    }


    private fun compareFunctionDescriptors(o1: FunctionDescriptor, o2: FunctionDescriptor): ComparisonResult =
    // TODO add more parameters
            (o1.fqNameSafe == o2.fqNameSafe).toResult(FqName) and
                    (o1.isSuspend == o2.isSuspend).toResult(Suspend) and
                    (compareTypes(o1.returnType!!, o2.returnType!!, intersector)).toResult(Type) and
                    compareParams(o1.explicitParameters, o2.explicitParameters)

    private fun compareParams(o1: List<ParameterDescriptor>, o2: List<ParameterDescriptor>): ComparisonResult {
        if (o1.size != o2.size) {
            return Failure(Size)
        }

        val parametersMismatched = (o1 zip o2).map { (a, b) ->
            valueDescriptorComparator.compare(a, b)
        }.withIndex().filter { (_, compResult) -> compResult is Failure }
                .map { (ind, compResult) ->
                    compResult as Failure
                    Parameter(compResult.causes, ind)
                }

        return if (parametersMismatched.isNotEmpty()) {
            Failure(ParametersMismatched(parametersMismatched))
        } else {
            Success()
        }
    }

}

class NewClassDescriptorComparator(val intersector: DummyIntersector) : DescriptorComparator<ClassDescriptor> {
    companion object {
//        val INSTANCE = ClassDescriptorComparator()
    }

    override fun compare(o1: ClassDescriptor, o2: ClassDescriptor): ComparisonResult {
//        if (o1 !is ClassDescriptor || o2 !is ClassDescriptor) {
//            return Failure(Unknown)
//        }

        return compareClassDescriptors(o1, o2)
    }

//    private fun Collection<DeclarationDescriptor>.wrap(): Set<DescriptorHolder> = map { DescriptorHolder(it) }.toSet()

    private fun compareClassDescriptors(o1: ClassDescriptor, o2: ClassDescriptor): ComparisonResult {
//        val firstMemberScope = o1.unsubstitutedMemberScope
//        val secondMemberScope = o2.unsubstitutedMemberScope

//        fun compareDescriptors(kind: DescriptorKindFilter) =
//                firstMemberScope.getDescriptorsFiltered(kind).wrap() ==
//                        secondMemberScope.getDescriptorsFiltered(kind).wrap()

        return (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqName) and
                (o1.kind == o2.kind).toResult(ClassKind) /*and TODO
                && o1.constructors.wrap() == o2.constructors.wrap()
                && compareDescriptors(DescriptorKindFilter.FUNCTIONS)
                && compareDescriptors(DescriptorKindFilter.VARIABLES)
                && compareDescriptors(DescriptorKindFilter.CLASSIFIERS)*/
//                && compareDescriptors(DescriptorKindFilter.TYPE_ALIASES)
    }
}

class NewTypeAliasDescriptorComparator : DescriptorComparator<TypeAliasDescriptor> {
    companion object {
        val INSTANCE = NewTypeAliasDescriptorComparator()
    }

    override fun compare(o1: TypeAliasDescriptor, o2: TypeAliasDescriptor): ComparisonResult {
//        if (o1 !is TypeAliasDescriptor || o2 !is TypeAliasDescriptor) {
//            return Failure(Unknown)
//        }
        return compareTypeAliasDescriptors(o1, o2)
    }

    private fun compareTypeAliasDescriptors(o1: TypeAliasDescriptor, o2: TypeAliasDescriptor): ComparisonResult {
        val o1Declaration = o1.underlyingType.constructor.declarationDescriptor!!
        val o2Declaration = o2.underlyingType.constructor.declarationDescriptor!!


        val sameKind = when {
            o1Declaration is ClassDescriptor && o2Declaration is ClassDescriptor ->
//                o1.abbreviation.arguments.isEmpty() && o2.abbreviation.arguments.isEmpty() &&
                !o1Declaration.isInline && !o2Declaration.isInline &&
                        o1.underlyingType.arguments.isEmpty() && o2.underlyingType.arguments.isEmpty() &&
                        o1Declaration.kind == o2Declaration.kind &&
                        o1Declaration.modality == o2Declaration.modality &&
                        Visibilities.compare(o1Declaration.visibility, o2Declaration.visibility)?.let { it == 0 } ?: false

            else -> false
        }

        return (o1.fqNameSafe == o2.fqNameSafe).toResult(FqName) and
                sameKind.toResult(Unknown)
//                    (o1.isActual == o2.isActual).toResult(Actual) and
//                    (o1.isExpect == o2.isExpect).toResult(Expect) and
//                    (o1.isExternal == o2.isExternal).toResult(External) and
//                    (o1.expandedType == o2.expandedType).toResult(Type)
    }


}

private fun <T> haveDifferentNullability(a: T?, b: T?): Boolean = (a != null) != (b != null)

class PropertyDescriptorComparator(val intersector: DummyIntersector) : DescriptorComparator<PropertyDescriptor> {
    companion object {

    }

    private val valueDescriptorComparator = NewValueComparator(intersector)

    override fun compare(o1: PropertyDescriptor, o2: PropertyDescriptor): ComparisonResult {
        if (!compareTypes(o1.type, o2.type, intersector)) {
            return Failure(Type)
        }

        if (o1.fqNameSafe != o2.fqNameSafe ||
                // expect property cannot be const because expect cannot have initializer
                // and const property must have
                o1.isConst || o2.isConst ||
                // expect property cannot be lateinit
                o1.isLateInit || o2.isLateInit ||
                // TODO is it check required?
                o1.isVar != o2.isVar) {
            return Failure(FqName)
        }

        val o1Setter = o1.setter
        val o2Setter = o2.setter
        if (haveDifferentNullability(o1Setter, o2Setter)) {
            return Failure(Unknown)
        }

        if (o1Setter != null && o2Setter != null) {
            val equalSetters = o1Setter.modality == o2Setter.modality &&
                    compareVisibilities(o1Setter.visibility, o2Setter.visibility) &&
                    o1Setter.isInline == o2Setter.isInline

            if (!equalSetters) {
                return Failure(Unknown)
            }
        }

        val o1Getter = o1.getter
        val o2Getter = o2.getter
        if (haveDifferentNullability(o1Getter, o2Getter)) {
            return Failure(Unknown)
        }

        if (o1Getter != null && o2Getter != null) {
            val equalGetters = o1Getter.modality == o2Getter.modality &&
                    compareVisibilities(o1Getter.visibility, o2Getter.visibility) &&
                    o1Getter.isInline == o2Getter.isInline

            if (!equalGetters) {
                return Failure(Unknown)
            }
        }

        val o1extensionReceiver = o1.extensionReceiverParameter
        val o2extensionReceiver = o2.extensionReceiverParameter

        if (haveDifferentNullability(o1extensionReceiver, o2extensionReceiver)) {
            return Failure(Type)
        }

        if (o1extensionReceiver != null && o2extensionReceiver != null) {
            val comparisonResult = valueDescriptorComparator.compare(o1extensionReceiver, o2extensionReceiver)
            if (comparisonResult is Failure) {
                return comparisonResult
            }
        }

        return Success()
    }
}