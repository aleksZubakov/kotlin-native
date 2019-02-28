package org.jetbrains.kotlin.cli.klib.merger.descriptors

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope

class MergerFragmentDescriptor(moduleDescriptor: ModuleDescriptor,
                               fqName: FqName,
                               private val memberScope: MemberScope) : PackageFragmentDescriptorImpl(moduleDescriptor, fqName) {
    override fun getMemberScope(): MemberScope {
        return memberScope
    }
}

class MergedSimpleFunctionDescriptor(
        containingDeclaration: DeclarationDescriptor,
        original: SimpleFunctionDescriptor?,
        annotations: Annotations,
        name: Name,
        kind: CallableMemberDescriptor.Kind,
        source: SourceElement? = null
) : CallableMemberDescriptor,
        SimpleFunctionDescriptorImpl(
                containingDeclaration, original, annotations, name, kind,
                source ?: SourceElement.NO_SOURCE
        ) {

}

object DescriptorsUtils {

}

// TODO drop
data class ModuleWithTargets(
        val module: ModuleDescriptorImpl,
        val targets: List<KonanTarget>
)


data class PackageWithTargets(
        val packageViewDescriptor: PackageViewDescriptor,
        val targets: List<KonanTarget>
)

