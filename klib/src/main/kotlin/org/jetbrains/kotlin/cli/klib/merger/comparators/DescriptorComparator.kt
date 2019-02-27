package org.jetbrains.kotlin.cli.klib.merger.comparators

import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.cli.klib.merger.DescriptorHolder
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

interface DescriptorComparator {
    fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult
}

class TotalComparator : DescriptorComparator {
    override fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult {
        if (o1 === o2) return Sucess()

        return when {
            o1 is FunctionDescriptor && o2 is FunctionDescriptor ->
                FunctionDescriptorComparator.INSTANCE.compare(o1, o2).map(::Function)

            o1 is ValueDescriptor && o2 is ValueDescriptor ->
                ValueDescriptorComparator.INSTANCE.compare(o1, o2).map(::Value)

            o1 is ClassDescriptor && o2 is ClassDescriptor ->
                ClassDescriptorComparator.INSTANCE.compare(o1, o2).map(::Class)

            o1 is TypeAliasDescriptor && o2 is TypeAliasDescriptor ->
                TypeAliasDescriptorComparator.INSTANCE.compare(o1, o2).map(::TypeAlias)

            else -> Failure(Unknown)
        }
    }
}

class ValueDescriptorComparator : DescriptorComparator {
    companion object {
        val INSTANCE = ValueDescriptorComparator()
    }

    override fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult {
        if (o1 !is ValueDescriptor || o2 !is ValueDescriptor) {
            return Failure(Unknown)
        }

        return compareValueDescriptors(o1, o2)
    }

    private fun compareValueDescriptors(o1: ValueDescriptor, o2: ValueDescriptor): ComparisonResult = when {
        o1 is VariableDescriptor && o2 is VariableDescriptor -> compareVariableDescriptors(o1, o2)
        o1 is ValueParameterDescriptor && o2 is ValueParameterDescriptor -> compareValueParameterDescriptors(o1, o2)
        else -> Failure(Unknown)
    }

    private fun compareVariableDescriptors(o1: VariableDescriptor, o2: VariableDescriptor): ComparisonResult =
            (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqName) and
                    (o1.isConst == o2.isConst).toResult(Const) and
                    (o1.isVar == o2.isVar).toResult(IsVar) and // TODO
                    (o1.isLateInit == o2.isLateInit).toResult(LateInit) and
                    (o1.isSuspend == o2.isSuspend).toResult(Suspend) and
                    (o1.type == o2.type).toResult(Type)
//                    && o1.returnType == o2.returnType

    private fun compareValueParameterDescriptors(o1: ParameterDescriptor, o2: ParameterDescriptor): ComparisonResult =
            (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqName) and
                    (o1.returnType == o2.returnType).toResult(Type) and
//                    && (o1.type == o2.type)
                    (o1.isSuspend == o2.isSuspend).toResult(Suspend) and
                    (Visibilities.compare(o1.visibility, o2.visibility) == 0).toResult(Visibility)

}

class FunctionDescriptorComparator : DescriptorComparator {
    companion object {
        val INSTANCE = FunctionDescriptorComparator()
    }

    override fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult {
        if (o1 !is FunctionDescriptor || o2 !is FunctionDescriptor) {
            return Failure(Unknown)
        }

        return compareFunctionDescriptors(o1, o2)
    }

    private fun compareFunctionDescriptors(o1: FunctionDescriptor, o2: FunctionDescriptor): ComparisonResult =
    // TODO add more parameters
            (o1.fqNameSafe == o2.fqNameSafe).toResult(FqName) and
                    (o1.isSuspend == o2.isSuspend).toResult(Suspend) and
                    (o1.returnType == o2.returnType).toResult(Type) and
                    compareParams(o1.explicitParameters, o2.explicitParameters)

    private fun compareParams(o1: List<ParameterDescriptor>, o2: List<ParameterDescriptor>): ComparisonResult {
        if (o1.size != o2.size) {
            return Failure(Size)
        }

        val parametersMismatched = (o1 zip o2).map { (a, b) ->
            ValueDescriptorComparator.INSTANCE.compare(a, b)
        }.withIndex().filter { (_, compResult) -> compResult is Failure }
                .map { (ind, compResult) ->
                    compResult as Failure
                    Parameter(compResult.causes, ind)
                }

        return if (parametersMismatched.isNotEmpty()) {
            Failure(ParametersMismatched(parametersMismatched))
        } else {
            Sucess()
        }
    }

}

class ClassDescriptorComparator : DescriptorComparator {
    companion object {
        val INSTANCE = ClassDescriptorComparator()
    }

    override fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult {
        if (o1 !is ClassDescriptor || o2 !is ClassDescriptor) {
            return Failure(Unknown)
        }

        return compareClassDescriptors(o1, o2)
    }

    private fun Collection<DeclarationDescriptor>.wrap(): Set<DescriptorHolder> = map { DescriptorHolder(it) }.toSet()

    private fun compareClassDescriptors(o1: ClassDescriptor, o2: ClassDescriptor): ComparisonResult {
        val firstMemberScope = o1.unsubstitutedMemberScope
        val secondMemberScope = o2.unsubstitutedMemberScope

        fun compareDescriptors(kind: DescriptorKindFilter) =
                firstMemberScope.getDescriptorsFiltered(kind).wrap() ==
                        secondMemberScope.getDescriptorsFiltered(kind).wrap()

        return (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqName) and
                (o1.kind == o2.kind).toResult(ClassKind) /*and TODO
                && o1.constructors.wrap() == o2.constructors.wrap()
                && compareDescriptors(DescriptorKindFilter.FUNCTIONS)
                && compareDescriptors(DescriptorKindFilter.VARIABLES)
                && compareDescriptors(DescriptorKindFilter.CLASSIFIERS)*/
//                && compareDescriptors(DescriptorKindFilter.TYPE_ALIASES)
    }
}

class TypeAliasDescriptorComparator : DescriptorComparator {
    companion object {
        val INSTANCE = TypeAliasDescriptorComparator()
    }

    override fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult {
        if (o1 !is TypeAliasDescriptor || o2 !is TypeAliasDescriptor) {
            return Failure(Unknown)
        }

        return compareTypeAliasDescriptors(o1, o2)
    }

    private fun compareTypeAliasDescriptors(o1: TypeAliasDescriptor, o2: TypeAliasDescriptor): ComparisonResult =
            (o1.fqNameSafe == o2.fqNameSafe).toResult(FqName) and
                    (o1.isActual == o2.isActual).toResult(Actual) and
                    (o1.isExpect == o2.isExpect).toResult(Expect) and
                    (o1.isExternal == o2.isExternal).toResult(External) and
                    (o1.expandedType == o2.expandedType).toResult(Type)

}