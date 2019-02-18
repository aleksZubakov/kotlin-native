package org.jetbrains.kotlin.cli.klib.merger.comparators

import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.cli.klib.merger.DescriptorHolder
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import javax.xml.ws.FaultAction

interface DescriptorComparator {
    fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult
}

class TotalComparator : DescriptorComparator {
    override fun compare(o1: DeclarationDescriptor, o2: DeclarationDescriptor): ComparisonResult {
        if (o1 === o2) return Sucess()

        return when {
            o1 is FunctionDescriptor && o2 is FunctionDescriptor ->
                compareFunctionDescriptors(o1, o2)

            o1 is ValueDescriptor && o2 is ValueDescriptor ->
                compareValueDescriptors(o1, o2)

            o1 is ClassDescriptor && o2 is ClassDescriptor ->
                compareClassDescriptors(o1, o2)

            o1 is TypeAliasDescriptor && o2 is TypeAliasDescriptor ->
                compareTypeAliasDescriptors(o1, o2)

            else -> Failure(Unknown)
        }
    }

    private fun Collection<DeclarationDescriptor>.wrap(): Set<DescriptorHolder> = map { DescriptorHolder(it) }.toSet()

    private fun compareTypeAliasDescriptors(o1: TypeAliasDescriptor, o2: TypeAliasDescriptor): ComparisonResult =
            (o1.fqNameSafe == o2.fqNameSafe).toResult(FqNameMismatch) and
                    (o1.isActual == o2.isActual).toResult(ActualMismatch) and
                    (o1.isExpect == o2.isExpect).toResult(ExpectMismatch) and
                    (o1.isExternal == o2.isExternal).toResult(ExternalMismatch) and
                    (o1.expandedType == o2.expandedType).toResult(TypeMismatch)

    private fun compareClassDescriptors(o1: ClassDescriptor, o2: ClassDescriptor): ComparisonResult {
        val firstMemberScope = o1.unsubstitutedMemberScope
        val secondMemberScope = o2.unsubstitutedMemberScope

        fun compareDescriptors(kind: DescriptorKindFilter) =
                firstMemberScope.getDescriptorsFiltered(kind).wrap() ==
                        secondMemberScope.getDescriptorsFiltered(kind).wrap()

        return (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqNameMismatch) and
                (o1.kind == o2.kind).toResult(ClassKind) /*and TODO
                && o1.constructors.wrap() == o2.constructors.wrap()
                && compareDescriptors(DescriptorKindFilter.FUNCTIONS)
                && compareDescriptors(DescriptorKindFilter.VARIABLES)
                && compareDescriptors(DescriptorKindFilter.CLASSIFIERS)*/
//                && compareDescriptors(DescriptorKindFilter.TYPE_ALIASES)
    }

    private fun compareParams(o1: List<ParameterDescriptor>, o2: List<ParameterDescriptor>): ComparisonResult {
        if (o1.size != o2.size) {
            return Failure(Size)
        }

        var result: ComparisonResult = Sucess()

        for ((ind, descriptors) in (o1 zip o2).withIndex()) {
            val (a, b) = descriptors
            val compareValueDescriptors = compareValueDescriptors(a, b).map { ParameterMismatch(it, ind) }

            result = result and compareValueDescriptors
        }
//        (o1 zip o2).all { (a, b) -> compareValueParameterDescriptors(a, b) }
        return result
    }


    private fun compareFunctionDescriptors(o1: FunctionDescriptor, o2: FunctionDescriptor): ComparisonResult =
            (o1.name == o2.name).toResult(FqNameMismatch) and
                    (o1.isSuspend == o2.isSuspend).toResult(SuspendMismatch) and
                    (o1.returnType == o2.returnType).toResult(TypeMismatch) and
                    compareParams(o1.explicitParameters, o2.explicitParameters)

    private fun compareValueDescriptors(o1: ValueDescriptor, o2: ValueDescriptor): ComparisonResult = when {
        o1 is VariableDescriptor && o2 is VariableDescriptor -> compareVariableDescriptors(o1, o2)
        o1 is ValueParameterDescriptor && o2 is ValueParameterDescriptor -> compareValueParameterDescriptors(o1, o2)
        else -> Failure(Unknown)
    }


    private fun compareVariableDescriptors(o1: VariableDescriptor, o2: VariableDescriptor): ComparisonResult =
            (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqNameMismatch) and
                    (o1.isConst == o2.isConst).toResult(Const) and
                    (o1.isVar == o2.isVar).toResult(IsVar) // TODO
//                    && (o1.isLateInit == o2.isLateInit)
//                    && (o1.isSuspend == o2.isSuspend)
//                    && o1.type == o2.type
//                    && o1.returnType == o2.returnType

    private fun compareValueParameterDescriptors(o1: ParameterDescriptor, o2: ParameterDescriptor): ComparisonResult =
            (o1.fqNameUnsafe == o2.fqNameUnsafe).toResult(FqNameMismatch) and
                    (o1.returnType == o2.returnType).toResult(TypeMismatch) and
//                    && (o1.type == o2.type)
                    (o1.isSuspend == o2.isSuspend).toResult(SuspendMismatch) and
                    (Visibilities.compare(o1.visibility, o2.visibility) == 0).toResult(Visibility)
}

sealed class ComparisonResult

class Sucess : ComparisonResult()
class Failure(val cause: List<Cause>) : ComparisonResult() {
    constructor(cause: Cause) : this(listOf(cause))
}

infix fun ComparisonResult.and(other: ComparisonResult): ComparisonResult {
    return when (this) {
        is Sucess -> other
        is Failure -> when (other) {
            is Failure -> Failure(this.cause + other.cause)
            is Sucess -> this
        }
    }
}

fun ComparisonResult.map(init: (List<Cause>) -> Cause): ComparisonResult {
    return when (this) {
        is Sucess -> Sucess()
        is Failure -> Failure(init(this.cause))
    }
}

fun Boolean.toResult(cause: Cause) = if (this) Sucess() else Failure(cause)

interface Cause

object Unknown : Cause
object FqNameMismatch : Cause
object SuspendMismatch : Cause
object ActualMismatch : Cause
object ExpectMismatch : Cause
object ExternalMismatch : Cause

object Const : Cause
object ClassKind : Cause
object TypeMismatch : Cause
object IsVar : Cause
object Visibility : Cause
object Size : Cause
class ParameterMismatch(val cause: List<Cause>, val index: Int = -1) : Cause

