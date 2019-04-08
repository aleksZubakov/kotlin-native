package org.jetbrains.kotlin.cli.klib.merger

import org.jetbrains.kotlin.cli.klib.merger.comparator.CommonAndTargets
import org.jetbrains.kotlin.cli.klib.merger.comparator.FullDescriptorComparator
import org.jetbrains.kotlin.cli.klib.merger.comparator.ComparisonResult
import org.jetbrains.kotlin.cli.klib.merger.comparator.Mismatched
import org.jetbrains.kotlin.cli.klib.merger.descriptors.*
import org.jetbrains.kotlin.cli.klib.merger.ir.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.storage.LockBasedStorageManager

internal fun ModuleDescriptorImpl.getPackagesFqNames(): Set<FqName> {
    val result = mutableSetOf<FqName>()

    fun getSubPackages(fqName: FqName) {
        result.add(fqName)
        val packageFragmentProviderForModuleContent = getPackageFragmentProviderForModuleContent()

        packageFragmentProviderForModuleContent?.getSubPackagesOf(fqName) { true }?.forEach { getSubPackages(it) }
//        getSubPackagesOf(fqName) { true }.forEach { getSubPackages(it) }
    }

    getSubPackages(FqName.ROOT)
    return result
}

data class Diff<out T>(
        val firstTargetModules: List<T>,
        val secondTargetModules: List<T>,
        val commonModules: List<T>
)

//class DeclarationDescriptorMerger {
//    fun diff(firstTargetModules: List<ModuleDescriptorImpl>, secondTargetModules: List<ModuleDescriptorImpl>): Diff<MergedModule> {
//        val finalFirstTargetModules = mutableListOf<MergedModule>()
//        val finalSecondTargetModules = mutableListOf<MergedModule>()
//        val finalCommonModules = mutableListOf<MergedModule>()
//
//        val passed = mutableSetOf<ModuleDescriptorImpl>()
//        for (firstOldModule in firstTargetModules) {
//            for (secondOldModule in secondTargetModules) {
//                val result = fullDescriptorComparator.compareModuleDescriptors(firstOldModule, secondOldModule)
//                if (result !is CommonAndTargets) {
//                    continue
//                }
//                val (firstTargetNewModule, commonTargetNewModule, secondTargetNewModule) = result
//
//                passed.add(firstOldModule)
//                passed.add(secondOldModule)
//
//                val firstTargetPackages = getPackages(firstOldModule)
//                val secondTargetPackages = getPackages(secondOldModule)
//
//                val (ftPackageFragments, stPackageFragments, comPackageFragments) = diffPackages(firstTargetPackages, secondTargetPackages/*, CommonAndTargets(firstTargetModule, commonTargetModule, secondTargetModule)*/)
//
//                firstTargetNewModule.expand(ftPackageFragments)
//                secondTargetNewModule.expand(stPackageFragments)
//                commonTargetNewModule.expand(comPackageFragments)
//
//                finalFirstTargetModules.add(firstTargetNewModule)
//                finalSecondTargetModules.add(secondTargetNewModule)
//                finalCommonModules.add(commonTargetNewModule)
//            }
//        }
//
//        // TODO update descriptors in target modules
//        finalFirstTargetModules.addAll(firstTargetModules.filter { it !in passed }.map { it.toMergedIR() as MergedModule })
//        finalSecondTargetModules.addAll(secondTargetModules.filter { it !in passed }.map { it.toMergedIR() as MergedModule })
//
//        return Diff(finalFirstTargetModules, finalSecondTargetModules, finalCommonModules)
//    }
//
//
//    private fun diffPackages(firstTargetPackages: List<PackageViewDescriptor>,
//                             secondTargetPackages: List<PackageViewDescriptor>): Diff<MergedPackage> {
//        val firstTargetResult = mutableListOf<MergedPackage>()
//        val secondTargetResult = mutableListOf<MergedPackage>()
//
//        val commonResult = mutableListOf<MergedPackage>()
//
//        val passed = mutableSetOf<PackageViewDescriptor>()
//        for (firstPackage in firstTargetPackages) {
//            for (secondPackage in secondTargetPackages) {
//                val result = fullDescriptorComparator.comparePackageViewDescriptors(firstPackage, secondPackage)
//                if (result !is CommonAndTargets) {
//                    continue
//                }
//
//                val (firstTargetNewPackage, commonTargetNewPackage, secondTargetNewPackage) = result
//                passed.add(firstPackage)
//                passed.add(secondPackage)
//
//                val firstTargetDescriptors = firstPackage.getAllDescriptors()
//                val secondTargetDescriptors = secondPackage.getAllDescriptors()
//
//                val (ftDescriptor, sdDesriptors, comDescriptors) =
//                        diffDescriptors(firstTargetDescriptors.toList(), secondTargetDescriptors.toList())
//
//                firstTargetNewPackage.expand(ftDescriptor)
//                secondTargetNewPackage.expand(sdDesriptors)
//                commonTargetNewPackage.expand(comDescriptors)
//
//                firstTargetResult.add(firstTargetNewPackage)
//                secondTargetResult.add(secondTargetNewPackage)
//                commonResult.add(commonTargetNewPackage)
//            }
//        }
//
//        // TODO update descriptors in target modules
//        firstTargetResult.addAll(firstTargetPackages.filter { it !in passed }.map { it.toMergedIR() as MergedPackage })
//        secondTargetResult.addAll(secondTargetPackages.filter { it !in passed }.map { it.toMergedIR() as MergedPackage })
//
//        return Diff(firstTargetResult, secondTargetResult, commonResult)
//    }
//
//
//    private fun diffDescriptors(firstTargetDescriptors: List<DeclarationDescriptor>,
//                                secondTargetDescriptors: List<DeclarationDescriptor>): Diff<MergedDescriptor> {
//        val finalFirstTargetDescriptors = mutableListOf<MergedDescriptor>()
//        val finalSecondTargetDescriptors = mutableListOf<MergedDescriptor>()
//
//        val finalCommonDescriptors = mutableListOf<MergedDescriptor>()
//
//        val passed = mutableSetOf<DeclarationDescriptor>()
//        for (firstDescriptor in firstTargetDescriptors) {
//            if (firstDescriptor is FunctionDescriptor &&
//                    (firstDescriptor.name.toString().contains("kniBridge")
//                            || firstDescriptor.name.toString().contains("KniBridge"))) {
//                continue
//            }
//
//            for (secondDescriptor in secondTargetDescriptors) {
//                if (secondDescriptor is FunctionDescriptor &&
//                        (secondDescriptor.name.toString().contains("kniBridge")
//                                || secondDescriptor.name.toString().contains("KniBridge"))) {
//                    continue
//                }
//
//                if (firstDescriptor.name != secondDescriptor.name) {
//                    continue
//                }
//
//                when (val intersectResult = diffDescriptors(firstDescriptor, secondDescriptor)) {
//                    is CommonAndTargets -> {
//                        passed.add(firstDescriptor)
//                        passed.add(secondDescriptor)
//
//                        finalFirstTargetDescriptors.add(intersectResult.firstTargetDescriptor)
//                        finalSecondTargetDescriptors.add(intersectResult.secondTargetDescriptor)
//                        finalCommonDescriptors.add(intersectResult.commonDescriptor)
//                    }
//                }
//            }
//        }
//
//        // TODO update descriptors in target modules
//        finalFirstTargetDescriptors.addAll(firstTargetDescriptors.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())
//        finalSecondTargetDescriptors.addAll(secondTargetDescriptors.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())
//
//        return Diff(finalFirstTargetDescriptors, finalSecondTargetDescriptors, finalCommonDescriptors)
//    }
//
//    val fullDescriptorComparator = FullDescriptorComparator(LockBasedStorageManager("comparator"))
//
//    private fun Collection<DeclarationDescriptor>.filterNotAbstractMembersIfInterface(classDescriptor: ClassDescriptor): Collection<DeclarationDescriptor> =
//            if (classDescriptor.kind != ClassKind.INTERFACE) {
//                this
//            } else {
//                this.filter {
//                    // TODO decide which descriptors to left
//                    (it !is CallableMemberDescriptor)
//                            || it.modality == Modality.ABSTRACT
//                }
//            }
//
//    private fun diffClassDescriptors(firstTargetDescriptor: ClassDescriptor,
//                                     secondTargetDescriptor: ClassDescriptor): ComparisonResult<MergedClass> {
//        val intersectionByTargets = fullDescriptorComparator.compareClassDescriptors(firstTargetDescriptor, secondTargetDescriptor)
//        if (intersectionByTargets !is CommonAndTargets<MergedClass>) {
//            return intersectionByTargets
//        }
//
//        val (newFirstTargetDescriptor, commonDescriptor, newSecondTargetDescriptor) = intersectionByTargets
//
//        // TODO check parents match
//        val allFirstTargetMembers = firstTargetDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }
//        val allSecondTargetMembers = secondTargetDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }
//
//        val passed = mutableSetOf<DeclarationDescriptor>()
//        for (ftDesc in allFirstTargetMembers.filterNotAbstractMembersIfInterface(firstTargetDescriptor)) {
//            for (sdDesc in allSecondTargetMembers.filterNotAbstractMembersIfInterface(secondTargetDescriptor)) {
//                val intersection = diffDescriptors(ftDesc, sdDesc/*, commonDescriptor, firstTargetDescriptor, secondTargetDescriptor*/)
//
//                if (intersection is Mismatched) {
//                    continue
//                }
//
//                intersection as CommonAndTargets
//                val (firstTargetToAdd, commonTargetToAdd, secondTargetToAdd) = intersection
//
//                newFirstTargetDescriptor.expand(listOf(firstTargetToAdd))
//                commonDescriptor.expand(listOf(commonTargetToAdd))
//                newSecondTargetDescriptor.expand(listOf(secondTargetToAdd))
//
//                passed.add(ftDesc)
//                passed.add(sdDesc)
//            }
//        }
//
//        // TODO update and add left descriptor
//        newFirstTargetDescriptor.expand(allFirstTargetMembers.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())
//        newSecondTargetDescriptor.expand(allSecondTargetMembers.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())
//
//        return intersectionByTargets
//    }
//
//    fun diffDescriptors(firstTargetDescriptor: DeclarationDescriptor,
//                        secondTargetDescriptor: DeclarationDescriptor): ComparisonResult<MergedDescriptor> {
//        // TODO check should not be executed here
//        if (firstTargetDescriptor.fqNameSafe != secondTargetDescriptor.fqNameSafe) {
//            return Mismatched()
//        }
//        if (firstTargetDescriptor is FunctionDescriptor && secondTargetDescriptor is FunctionDescriptor) {
//            return fullDescriptorComparator.compareFunctionDescriptors(firstTargetDescriptor, secondTargetDescriptor)
//        }
//
//        if (firstTargetDescriptor is ClassDescriptor && secondTargetDescriptor is ClassDescriptor) {
//            return diffClassDescriptors(firstTargetDescriptor, secondTargetDescriptor/*, commonTargetContainingDeclarationDescriptor, firstTargetContainingDeclarationDescriptor, secondTargetContainingDeclarationDescriptor*/)
//        }
//
//        if (firstTargetDescriptor is TypeAliasDescriptor && secondTargetDescriptor is TypeAliasDescriptor) {
//            return fullDescriptorComparator.compareTypeAliases(firstTargetDescriptor, secondTargetDescriptor)
//        }
//
//        if (firstTargetDescriptor is PropertyDescriptor && secondTargetDescriptor is PropertyDescriptor) {
//            return fullDescriptorComparator.comparePropertyDescriptors(firstTargetDescriptor, secondTargetDescriptor)
//        }
//
//        return Mismatched()
//    }
//
//
//}
