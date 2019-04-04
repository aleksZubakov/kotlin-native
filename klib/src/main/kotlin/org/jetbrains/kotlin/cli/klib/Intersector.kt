package org.jetbrains.kotlin.cli.klib

import org.jetbrains.kotlin.cli.klib.merger.comparators.*
import org.jetbrains.kotlin.cli.klib.merger.ir.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

sealed class IntersectionResult<out T>

data class CommonAndTargets<T : MergedDescriptor>(val firstTargetDescriptor: T,
                                                  val commonDescriptor: T,
                                                  val secondTargetDescriptor: T) : IntersectionResult<T>()

//data class OnlyCommon<T : DeclarationDescriptor>(val commonDescriptor: T) : IntersectionResult<T>()

class Mismatched<T : MergedDescriptor> : IntersectionResult<T>()

//val <T : DeclarationDescriptor> IntersectionResult<T>.commonDescriptor
//    get(): T? = when (this) {
////        is OnlyCommon<T> -> this.commonDescriptor
//        is CommonAndTargets<T> -> this.commonDescriptor
//        else -> null
//    }
//
//val <T : DeclarationDescriptor> IntersectionResult<T>.firstTargetDescriptor
//    get(): T? = when (this) {
//        is CommonAndTargets<T> -> this.firstTargetDescriptor
//        else -> null
//    }
//
//val <T : DeclarationDescriptor> IntersectionResult<T>.secondTargetDescriptor
//    get(): T? = when (this) {
//        is CommonAndTargets<T> -> this.secondTargetDescriptor
//        else -> null
//    }

interface Intersector {
    fun intersect(firstTargetDescriptor: DeclarationDescriptor, secondTargetDescriptor: DeclarationDescriptor): IntersectionResult<DeclarationDescriptor>
}

class DummyIntersector() /*: Intersector */ {
    private val classDescriptorsToIntersectionResult = mutableMapOf<Pair<ClassDescriptor, ClassDescriptor>, IntersectionResult<MergedClass>>()
    private val typealiasDescriptorsToIntersectionResult = mutableMapOf<Pair<TypeAliasDescriptor, TypeAliasDescriptor>, IntersectionResult<MergedTypeAlias>>()

    val comparator = NewClassDescriptorComparator(this)

    fun Collection<DeclarationDescriptor>.filterNotAbstractMembersIfInterface(classDescriptor: ClassDescriptor): Collection<DeclarationDescriptor> =
            if (classDescriptor.kind != ClassKind.INTERFACE) {
                this
            } else {
                this.filter {
                    // TODO decide which descriptors to left
                    (it !is CallableMemberDescriptor)
                            || it.modality == Modality.ABSTRACT
                }
            }

    private fun resolveClassDescriptors(firstTargetDescriptor: ClassDescriptor,
                                        secondTargetDescriptor: ClassDescriptor): IntersectionResult<MergedClass> {


        val intersectionByTargets = getClassDescriptorsIntersection(firstTargetDescriptor, secondTargetDescriptor)
        if (intersectionByTargets !is CommonAndTargets<MergedClass>) {
            return intersectionByTargets
        }

        val (newFirstTargetDescriptor, commonDescriptor, newSecondTargetDescriptor) = intersectionByTargets

        // TODO check parents match
        val allFirstTargetMembers = firstTargetDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }
        val allSecondTargetMembers = secondTargetDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }

        val passed = mutableSetOf<DeclarationDescriptor>()
        for (ftDesc in allFirstTargetMembers.filterNotAbstractMembersIfInterface(firstTargetDescriptor)) {
            for (sdDesc in allSecondTargetMembers.filterNotAbstractMembersIfInterface(secondTargetDescriptor)) {
                val intersection = intersect(ftDesc, sdDesc/*, commonDescriptor, firstTargetDescriptor, secondTargetDescriptor*/)

                if (intersection is Mismatched) {
                    continue
                }

                intersection as CommonAndTargets
                val (firstTargetToAdd, commonTargetToAdd, secondTargetToAdd) = intersection

                newFirstTargetDescriptor.expand(listOf(firstTargetToAdd))
                commonDescriptor.expand(listOf(commonTargetToAdd))
                newSecondTargetDescriptor.expand(listOf(secondTargetToAdd))

                passed.add(ftDesc)
                passed.add(sdDesc)
            }
        }

        for (ftSupertype in firstTargetDescriptor.typeConstructor.supertypes) {
            for (stSupertype in secondTargetDescriptor.typeConstructor.supertypes) {
                if (compareTypesInternal(ftSupertype, stSupertype)) {
                    commonDescriptor.addSuperType(ftSupertype)
                }
            }
        }

        // TODO update and add left descriptor
        newFirstTargetDescriptor.expand(allFirstTargetMembers.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())
        newSecondTargetDescriptor.expand(allSecondTargetMembers.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())

        return intersectionByTargets
    }


    private fun getClassDescriptorsIntersection(firstTargetDescriptor: ClassDescriptor, secondTargetDescriptor: ClassDescriptor): IntersectionResult<MergedClass> {
        val key = Pair(firstTargetDescriptor, secondTargetDescriptor)
        val candidate = classDescriptorsToIntersectionResult[key]
        if (candidate != null) {
            return candidate
        }

        val comparisonResult = comparator.compare(firstTargetDescriptor, secondTargetDescriptor)
        val result: IntersectionResult<MergedClass> = if (comparisonResult is Success) {
            // always create commonized descriptors even if don't know if they can be commonized,
            // empty ones will be removed later
            val newFirstTargetDescriptor = MergedClass(firstTargetDescriptor, isActual = true).apply {
                addSupertypes(firstTargetDescriptor.typeConstructor.supertypes)
            }
            val newSecondTargetDescriptor = MergedClass(secondTargetDescriptor, isActual = true).apply {
                addSupertypes(secondTargetDescriptor.typeConstructor.supertypes)
            }
            // TODO mb pass not second target descriptor but something else
            val commonTargetDescriptor = MergedClass(firstTargetDescriptor, isExpect = true)

            CommonAndTargets(newFirstTargetDescriptor, commonTargetDescriptor, newSecondTargetDescriptor)
        } else {
            Mismatched()
        }

        classDescriptorsToIntersectionResult[key] = result
        return result
    }

    private fun resolveTypeAlias(firstTargetDescriptor: TypeAliasDescriptor, secondTargetDescriptor: TypeAliasDescriptor): IntersectionResult<MergedTypeAlias> {
        if (NewTypeAliasDescriptorComparator().compare(firstTargetDescriptor, secondTargetDescriptor) is Success) {
            val newFirstTargetTypeAlias = MergedTypeAlias(firstTargetDescriptor, isActual = true)
            val newSecondTargetTypeAlias = MergedTypeAlias(secondTargetDescriptor, isActual = true)

            val firstTargetParents = (firstTargetDescriptor.underlyingType.constructor.declarationDescriptor!! as ClassDescriptor).typeConstructor.supertypes
            val secondTargetParents = (secondTargetDescriptor.underlyingType.constructor.declarationDescriptor!! as ClassDescriptor).typeConstructor.supertypes
            val newSupertypes = commonSuperTypes(firstTargetParents, secondTargetParents)

            // TODO common super types
            val commonTargetClassifier = CommonTypeAlias(firstTargetDescriptor, newSupertypes)

            return CommonAndTargets(newFirstTargetTypeAlias, commonTargetClassifier, newSecondTargetTypeAlias)
        }

        return Mismatched()

    }

    private fun getResolvedTypeAlias(firstTargetDescriptor: TypeAliasDescriptor, secondTargetDescriptor: TypeAliasDescriptor): IntersectionResult<MergedTypeAlias> =
            typealiasDescriptorsToIntersectionResult.computeIfAbsent(Pair(firstTargetDescriptor, secondTargetDescriptor)) {
                resolveTypeAlias(firstTargetDescriptor, secondTargetDescriptor)
            }


    fun getResolvedClassifier(firstTargetDescriptor: ClassifierDescriptor, secondTargetDescriptor: ClassifierDescriptor) =
            when {
                firstTargetDescriptor is TypeAliasDescriptor && secondTargetDescriptor is TypeAliasDescriptor ->
                    getResolvedTypeAlias(firstTargetDescriptor, secondTargetDescriptor)

                firstTargetDescriptor is ClassDescriptor && secondTargetDescriptor is ClassDescriptor ->
                    getClassDescriptorsIntersection(firstTargetDescriptor, secondTargetDescriptor)

                else -> Mismatched()
            }

    fun resolveFunctions(firstTargetDescriptor: FunctionDescriptor, secondTargetDescriptor: FunctionDescriptor): IntersectionResult<MergedFunction> {
        val compare = NewFunctionComparator(this).compare(firstTargetDescriptor, secondTargetDescriptor)
        if (compare is Success) {
            val newFirstTargetFunction = MergedFunction(firstTargetDescriptor, isActual = true)
            val newSecondTargetFunction = MergedFunction(secondTargetDescriptor, isActual = true)
            val commonTargetFunction = MergedFunction(secondTargetDescriptor, isExpect = true)

//                val newFirstTargetFunction = descriptorFactory.createFunction(firstTargetDescriptor, firstTargetContainingDeclarationDescriptor, isAcual = true)
//                val newSecondTargetFunction = descriptorFactory.createFunction(secondTargetDescriptor, secondTargetContainingDeclarationDescriptor, isAcual = true)
//                val commonTargeFunction = descriptorFactory.createFunction(firstTargetDescriptor, commonTargetContainingDeclarationDescriptor, isExpect = true)
            return CommonAndTargets(newFirstTargetFunction, commonTargetFunction, newSecondTargetFunction)
        }

        return Mismatched()
    }

    fun resolvePropertyDescriptors(firstTargetDescriptor: PropertyDescriptor, secondTargetDescriptor: PropertyDescriptor): IntersectionResult<MergedProperty> {
        val comparator = PropertyDescriptorComparator(this)
        if (comparator.compare(firstTargetDescriptor, secondTargetDescriptor) is Success) {
            val newFirstTargetProperty = MergedProperty(firstTargetDescriptor, isActual = true)
            val newSecondTargetProperty = MergedProperty(secondTargetDescriptor, isActual = true)
            val commonTargetProperty = MergedProperty(secondTargetDescriptor, isExpect = true)

            return CommonAndTargets(newFirstTargetProperty, commonTargetProperty, newSecondTargetProperty)
        }

        return Mismatched()
    }

    fun intersect(firstTargetDescriptor: DeclarationDescriptor,
                  secondTargetDescriptor: DeclarationDescriptor/*,
                  commonTargetContainingDeclarationDescriptor: DeclarationDescriptor,
                  firstTargetContainingDeclarationDescriptor: DeclarationDescriptor,
                  secondTargetContainingDeclarationDescriptor: DeclarationDescriptor*/): IntersectionResult<MergedDescriptor> {
        // TODO check should not be executed here
        if (firstTargetDescriptor.fqNameSafe != secondTargetDescriptor.fqNameSafe) {
            return Mismatched()
        }
        if (firstTargetDescriptor is FunctionDescriptor && secondTargetDescriptor is FunctionDescriptor) {
            return resolveFunctions(firstTargetDescriptor, secondTargetDescriptor)
        }

        if (firstTargetDescriptor is ClassDescriptor && secondTargetDescriptor is ClassDescriptor) {
            return resolveClassDescriptors(firstTargetDescriptor, secondTargetDescriptor/*, commonTargetContainingDeclarationDescriptor, firstTargetContainingDeclarationDescriptor, secondTargetContainingDeclarationDescriptor*/)
        }

        if (firstTargetDescriptor is TypeAliasDescriptor && secondTargetDescriptor is TypeAliasDescriptor) {
            return getResolvedTypeAlias(firstTargetDescriptor, secondTargetDescriptor)
        }

        if (firstTargetDescriptor is PropertyDescriptor && secondTargetDescriptor is PropertyDescriptor) {
            return resolvePropertyDescriptors(firstTargetDescriptor, secondTargetDescriptor)
        }

        return Mismatched()
    }

    fun commonSuperTypes(firstTargetParents: Collection<KotlinType>, secondTargetParents: Collection<KotlinType>): List<KotlinType> {
        val newFirstTargetParents = mutableListOf<KotlinType>()
        val newSecondTargetParents = mutableListOf<KotlinType>()
        loop@ for (ftParent in firstTargetParents) {
            for (stParent in secondTargetParents) {
                if (compareTypesInternal(ftParent, stParent)) {
                    newFirstTargetParents.add(ftParent)
                    newSecondTargetParents.add(stParent)
                    continue@loop
                }


//                if (comparator.compare(ftParent, stParent) is Success) {
//                    newFirstTargetParents.add(ftParent)
//                    newSecondTargetParents.add(stParent)
//                    continue@loop
//                }
            }
        }

        return newFirstTargetParents
    }

    fun compareTypes(firstTargetType: KotlinType, secondTargetType: KotlinType) {
        firstTargetType.arguments
    }

    private fun compareSimpleTypes(a: SimpleType, b: SimpleType): Boolean {
        if (a.arguments.size != b.arguments.size
                || a.isMarkedNullable != b.isMarkedNullable
                || (a as? DefinitelyNotNullType == null) != (a as? DefinitelyNotNullType == null)
//                || !isEqualTypeConstructors(a.typeConstructor(), b.typeConstructor())
        ) {
            return false
        }

        val aDescriptor = a.constructor.declarationDescriptor!!
        val bDescriptor = b.constructor.declarationDescriptor!!

        if (getResolvedClassifier(aDescriptor, bDescriptor) !is CommonAndTargets<*>) {
            return false
        }

        if (a.arguments === b.arguments) return true

        for (i in 0 until a.arguments.size) {
            val aArg = a.arguments[i]
            val bArg = b.arguments[i]
            if (aArg.isStarProjection != bArg.isStarProjection) return false

            // both non-star
            if (!aArg.isStarProjection) {
                if (aArg.projectionKind != bArg.projectionKind) return false
                if (!compareTypesInternal(aArg.type, bArg.type)) return false
            }
        }
        return true
    }

    private fun compareTypesInternal(a: KotlinTypeMarker, b: KotlinTypeMarker): Boolean {
        if (a === b) return true

        val simpleA = a as? SimpleType
        val simpleB = b as? SimpleType
        if (simpleA != null && simpleB != null) return compareSimpleTypes(simpleA, simpleB)

        val flexibleA = a as? FlexibleType
        val flexibleB = b as? FlexibleType
        if (flexibleA != null && flexibleB != null) {
            return compareSimpleTypes(flexibleA.lowerBound, flexibleB.lowerBound) &&
                    compareSimpleTypes(flexibleA.upperBound, flexibleB.upperBound)
        }
        return false
    }

    companion object {

    }

}