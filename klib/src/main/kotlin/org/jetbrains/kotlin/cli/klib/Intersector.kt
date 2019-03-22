package org.jetbrains.kotlin.cli.klib

import org.jetbrains.kotlin.cli.klib.merger.comparators.*
import org.jetbrains.kotlin.cli.klib.merger.ir.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

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
    fun resolveClassDescriptors(firstTargetDescriptor: ClassDescriptor,
                                secondTargetDescriptor: ClassDescriptor/*,
                                commonTargetContainingDeclarationDescriptor: DeclarationDescriptor,
                                firstTargetContainingDeclarationDescriptor: DeclarationDescriptor,
                                secondTargetContainingDeclarationDescriptor: DeclarationDescriptor*/): IntersectionResult<MergedClass> {
        val intersectionByTargets = getResolvedClassDescriptors(firstTargetDescriptor, secondTargetDescriptor)
        if (intersectionByTargets is Mismatched) {
            return intersectionByTargets
        }

        intersectionByTargets as CommonAndTargets
        val (newFirstTargetDescriptor, commonDescriptor, newSecondTargetDescriptor) = intersectionByTargets

        val comparisonResult = comparator.compare(firstTargetDescriptor, secondTargetDescriptor)
        if (comparisonResult !is Success) {
            // TODO mb not Mismatched but CommonAndTargets with old descriptors?

            return Mismatched()
        }

        // TODO remove
//        newFirstTargetDescriptor.containingDeclaration = firstTargetContainingDeclarationDescriptor
//        commonDescriptor.containingDeclaration = commonTargetContainingDeclarationDescriptor
//        newSecondTargetDescriptor.containingDeclaration = secondTargetContainingDeclarationDescriptor

        val allFirstTargetDescriptors = firstTargetDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }
        val allSecondTargetDescriptors = secondTargetDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }

        val firstTargetOnlyAbstractIfInterface = if (firstTargetDescriptor.kind != ClassKind.INTERFACE) {
            allFirstTargetDescriptors
        } else {
            allFirstTargetDescriptors.filter { (it !is SimpleFunctionDescriptor) || it.modality == Modality.ABSTRACT }
        }

        val secondTargetOnlyAbstractIfInterface = if (secondTargetDescriptor.kind != ClassKind.INTERFACE) {
            allSecondTargetDescriptors
        } else {
            allSecondTargetDescriptors.filter { (it !is SimpleFunctionDescriptor) || it.modality == Modality.ABSTRACT }
        }

        val passed = mutableSetOf<DeclarationDescriptor>()
        for (ftDesc in firstTargetOnlyAbstractIfInterface) {
            for (sdDesc in secondTargetOnlyAbstractIfInterface) {
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


        // TODO update and add left descriptor
        newFirstTargetDescriptor.expand(allFirstTargetDescriptors.filter { it !in passed }.map { it.toMergedDescriptor() })
        newSecondTargetDescriptor.expand(allSecondTargetDescriptors.filter { it !in passed }.map { it.toMergedDescriptor() })

        // TODO remove
//        newFirstTargetDescriptor.freeze()
//        newSecondTargetDescriptor.freeze()
//        commonDescriptor.freeze()

        return intersectionByTargets
    }

    val targetDescriptorsToIntersectionResult = mutableMapOf<Pair<ClassDescriptor, ClassDescriptor>, IntersectionResult<MergedClass>>()
    val comparator = NewClassDescriptorComparator(this)

    fun getResolvedClassDescriptors(firstTargetDescriptor: ClassDescriptor, secondTargetDescriptor: ClassDescriptor): IntersectionResult<MergedClass> {
        if (firstTargetDescriptor.fqNameSafe != secondTargetDescriptor.fqNameSafe) {
            return Mismatched()
        }

        // always create commonized descriptors even if don't know if they can be commonized,
        // empty ones will be removed later
        return targetDescriptorsToIntersectionResult.computeIfAbsent(Pair(firstTargetDescriptor, secondTargetDescriptor)) { (first, second) ->
            val newFirstTargetDescriptor = MergedClass(firstTargetDescriptor, isActual = true)
            val newSecondTargetDescriptor = MergedClass(secondTargetDescriptor, isActual = true)
            // TODO mb pass not second target descriptor but something else
            val commonTargetDescriptor = MergedClass(firstTargetDescriptor, isExpect = true)
//            val newFirstTargetDescriptor = descriptorFactory.createClass(firstTargetDescriptor, isAcual = true)
//            val newSecondTargetDescriptor = descriptorFactory.createClass(secondTargetDescriptor, isAcual = true)
//            val commonTargetDescriptor = descriptorFactory.createClass(secondTargetDescriptor, isExpect = true)

            CommonAndTargets(newFirstTargetDescriptor, commonTargetDescriptor, newSecondTargetDescriptor)
        }
    }

    fun intersect(firstTargetDescriptor: DeclarationDescriptor,
                  secondTargetDescriptor: DeclarationDescriptor/*,
                  commonTargetContainingDeclarationDescriptor: DeclarationDescriptor,
                  firstTargetContainingDeclarationDescriptor: DeclarationDescriptor,
                  secondTargetContainingDeclarationDescriptor: DeclarationDescriptor*/): IntersectionResult<MergedDescriptor> {
        if (firstTargetDescriptor.fqNameSafe != secondTargetDescriptor.fqNameSafe) {
            return Mismatched()
        }

        if (firstTargetDescriptor is FunctionDescriptor && secondTargetDescriptor is FunctionDescriptor) {
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
        }

        if (firstTargetDescriptor is ClassDescriptor && secondTargetDescriptor is ClassDescriptor) {
            return resolveClassDescriptors(firstTargetDescriptor, secondTargetDescriptor/*, commonTargetContainingDeclarationDescriptor, firstTargetContainingDeclarationDescriptor, secondTargetContainingDeclarationDescriptor*/)
        }

        if (firstTargetDescriptor is ValueDescriptor && secondTargetDescriptor is ValueDescriptor) {
            // TODO
            // descriptorFactory.createPropertyDescriptor(firstTargetDescriptor)
            return Mismatched()
        }

        if (firstTargetDescriptor is TypeAliasDescriptor && secondTargetDescriptor is TypeAliasDescriptor) {
            // TODO
            // descriptorFactory.createTypeAliasDescriptor(firstTargetDescriptor)
            return Mismatched()
        }

        return Mismatched()


    }
}