package org.jetbrains.kotlin.cli.klib.merger

import jdk.internal.dynalink.linker.ConversionComparator
import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.cli.klib.merger.comparators.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

class DescriptorHolder(val descriptor: DeclarationDescriptor) {
    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        other as DescriptorHolder
        return TotalComparator().compare(descriptor, other.descriptor) is Sucess
    }

    override fun hashCode(): Int {
        return descriptor.name.hashCode()
    }
}
