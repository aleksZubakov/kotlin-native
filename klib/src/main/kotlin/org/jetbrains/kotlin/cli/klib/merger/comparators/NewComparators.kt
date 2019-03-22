package org.jetbrains.kotlin.cli.klib.merger.comparators

import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.CommonAndTargets
import org.jetbrains.kotlin.cli.klib.DummyIntersector
import org.jetbrains.kotlin.cli.klib.Mismatched
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.name.Name
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

private fun compareTypes(o1: KotlinType, o2: KotlinType, intersector: DummyIntersector): Boolean {
    if (o1 is AbbreviatedType && o2 is AbbreviatedType) {
        return o1.abbreviation.constructor.declarationDescriptor!!.fqNameSafe ==
                o2.abbreviation.constructor.declarationDescriptor!!.fqNameSafe
    }

    if (KotlinBuiltIns.isPrimitiveType(o1) || KotlinBuiltIns.isPrimitiveType(o2)) {
        // TODO simple type comparison ??
        return o1 == o2
    }

    val o1Descriptor = o1.constructor.declarationDescriptor!!
    val o2Descriptor = o2.constructor.declarationDescriptor!!
    if (isUnderKotlinOrKotlinxPackage(o1Descriptor) || isUnderKotlinOrKotlinxPackage(o2Descriptor)) {
        if (o1 is SimpleType && o2 is SimpleType) {
            return o1 == o2
        }
    }

    if (o1 is FlexibleType && o2 is FlexibleType) {
        return compareTypes(o1.lowerBound, o2.lowerBound, intersector) &&
                compareTypes(o1.upperBound, o2.upperBound, intersector)
    }

    val o1typeDeclarationDescriptor = o1.constructor.declarationDescriptor!!
    val o2typeDeclarationDescriptor = o2.constructor.declarationDescriptor!!

    if (o1typeDeclarationDescriptor is ClassDescriptor && o2typeDeclarationDescriptor is ClassDescriptor) {
        return intersector.getResolvedClassDescriptors(o1typeDeclarationDescriptor, o2typeDeclarationDescriptor) !is Mismatched
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
        else -> Success()
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

            val res = intersector.getResolvedClassDescriptors(o1DeclarationDescriptor, o1DeclarationDescriptor)
            return res is CommonAndTargets
        }

        // TODO whooooop
        return false
*/
        return compareTypes(o1, o2, intersector)
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

    private fun compareTypeAliasDescriptors(o1: TypeAliasDescriptor, o2: TypeAliasDescriptor): ComparisonResult =
            (o1.fqNameSafe == o2.fqNameSafe).toResult(FqName) and
                    (o1.isActual == o2.isActual).toResult(Actual) and
                    (o1.isExpect == o2.isExpect).toResult(Expect) and
                    (o1.isExternal == o2.isExternal).toResult(External) and
                    (o1.expandedType == o2.expandedType).toResult(Type)

}