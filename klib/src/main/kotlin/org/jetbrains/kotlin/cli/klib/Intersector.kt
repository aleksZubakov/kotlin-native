package org.jetbrains.kotlin.cli.klib

import org.jetbrains.kotlin.cli.klib.merger.comparators.Success
import org.jetbrains.kotlin.cli.klib.merger.comparators.TotalComparator
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

sealed class IntersectionResult<T : DeclarationDescriptor>

data class CommonAndTargets<T : DeclarationDescriptor>(val commonDescriptor: T,
                                                       val firstTargetDescriptor: T,
                                                       val secondTargetDescriptor: T) : IntersectionResult<T>()

data class OnlyCommon<T : DeclarationDescriptor>(val commonDescriptor: T) : IntersectionResult<T>()
class Mismatched<T : DeclarationDescriptor> : IntersectionResult<T>()

interface Intersector {
    fun intersect(firstTargetDescriptor: DeclarationDescriptor, secondTargetDescriptor: DeclarationDescriptor): IntersectionResult<DeclarationDescriptor>
}

class DummyIntersector : Intersector {
    fun intersect(firstTargetDescriptor: ModuleDescriptorImpl,
                  secondTargetDescriptor: ModuleDescriptorImpl): IntersectionResult<ModuleDescriptorImpl> {
        if (firstTargetDescriptor.fqNameSafe == secondTargetDescriptor.fqNameSafe) {

        }
        return Mismatched()
    }

    override fun intersect(firstTargetDescriptor: DeclarationDescriptor, secondTargetDescriptor: DeclarationDescriptor): IntersectionResult<DeclarationDescriptor> {
        assert(firstTargetDescriptor.fqNameSafe == secondTargetDescriptor.fqNameSafe)

        val compareResult = TotalComparator.INSTANCE.compare(firstTargetDescriptor, secondTargetDescriptor)
        if (compareResult is Success) {
            // TODO update ClassDescriptors and TypeAliasDescriptors in target modules
            return OnlyCommon(firstTargetDescriptor)
        }


        // TODO expect/actual in other cases

        return Mismatched()
    }
}