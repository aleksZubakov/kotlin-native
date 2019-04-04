package org.jetbrains.kotlin.cli.klib.merger

import org.jetbrains.kotlin.cli.klib.CommonAndTargets
import org.jetbrains.kotlin.cli.klib.DummyIntersector
import org.jetbrains.kotlin.cli.klib.merger.descriptors.*
import org.jetbrains.kotlin.cli.klib.merger.ir.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.FqName

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

data class Diff<T>(
        val firstTargetModules: List<T>,
        val secondTargetModules: List<T>,
        val commonModules: List<T>
)

class DeclarationDescriptorMerger(val intersector: DummyIntersector) {
    fun diff(firstTargetModules: List<ModuleDescriptorImpl>, secondTargetModules: List<ModuleDescriptorImpl>): Diff<MergedModule> {
        val finalFirstTargetModules = mutableListOf<MergedModule>()
        val finalSecondTargetModules = mutableListOf<MergedModule>()

        val finalCommonModules = mutableListOf<MergedModule>()

        val passed = mutableSetOf<ModuleDescriptorImpl>()
        for (firstOldModule in firstTargetModules) {
            for (secondOldModule in secondTargetModules) {
                if (firstOldModule.name != secondOldModule.name) {
                    continue
                }

                passed.add(firstOldModule)
                passed.add(secondOldModule)

                val firstTargetPackages = getPackages(firstOldModule)
                val secondTargetPackages = getPackages(secondOldModule)

                val firstTargetNewModule = MergedModule(firstOldModule)
                val commonTargetNewModule = MergedModule(secondOldModule)
                val secondTargetNewModule = MergedModule(secondOldModule)

//                val firstTargetModule = descriptorFactory.createModule(firstOldModule.name, builtIns)
//                val secondTargetModule = descriptorFactory.createModule(secondOldModule.name, builtIns)
//                val commonTargetModule = descriptorFactory.createModule(firstOldModule.name, builtIns)
                val (ftPackageFragments, stPackageFragments, comPackageFragments) = diffPackages(firstTargetPackages, secondTargetPackages/*, CommonAndTargets(firstTargetModule, commonTargetModule, secondTargetModule)*/)

                firstTargetNewModule.expand(ftPackageFragments)
                secondTargetNewModule.expand(stPackageFragments)
                commonTargetNewModule.expand(comPackageFragments)

//                firstTargetModule.initialize(PackageFragmentProviderImpl(ftPackageFragments))
//                secondTargetModule.initialize(PackageFragmentProviderImpl(stPackageFragments))
//                commonTargetModule.initialize(PackageFragmentProviderImpl(comPackageFragments))

                finalFirstTargetModules.add(firstTargetNewModule)
                finalSecondTargetModules.add(secondTargetNewModule)
                finalCommonModules.add(commonTargetNewModule)
            }
        }

        // TODO update descriptors in target modules
        finalFirstTargetModules.addAll(firstTargetModules.filter { it !in passed }.map { it.toMergedIR() as MergedModule })
        finalSecondTargetModules.addAll(secondTargetModules.filter { it !in passed }.map { it.toMergedIR() as MergedModule })

//        finalCommonModules.forEach { it.setDependencies(finalCommonModules) }
//        finalFirstTargetModules.forEach { it.setDependencies(finalFirstTargetModules) }
//        finalSecondTargetModules.forEach { it.setDependencies(finalSecondTargetModules) }

        return Diff(finalFirstTargetModules, finalSecondTargetModules, finalCommonModules)
    }


    private fun diffPackages(firstTargetPackages: List<PackageViewDescriptor>,
                             secondTargetPackages: List<PackageViewDescriptor>/*,
                             modules: CommonAndTargets<ModuleDescriptorImpl>*/): Diff<MergedPackage> {
        val firstTargetResult = mutableListOf<MergedPackage>()
        val secondTargetResult = mutableListOf<MergedPackage>()

        val commonResult = mutableListOf<MergedPackage>()

        val passed = mutableSetOf<PackageViewDescriptor>()
        for (firstPackage in firstTargetPackages) {
            for (secondPackage in secondTargetPackages) {
                if (firstPackage.name != secondPackage.name) {
                    continue
                }

                passed.add(firstPackage)
                passed.add(secondPackage)

//                val firstTargetFragmentDescriptor = descriptorFactory.createPackageFragmentDescriptor(mutableListOf(), firstPackage.fqName, modules.firstTargetDescriptor)
//                val secondTargetFragmentDescriptor = descriptorFactory.createPackageFragmentDescriptor(mutableListOf(), secondPackage.fqName, modules.secondTargetDescriptor)
//                val commonTargetFragmentDescriptor = descriptorFactory.createPackageFragmentDescriptor(mutableListOf(), firstPackage.fqName, modules.commonDescriptor)

                val firstTargetNewPackage = MergedPackage(firstPackage)
                val secondTargetNewPackage = MergedPackage(secondPackage)
                val commonTargetNewPackage = MergedPackage(firstPackage)

                val firstTargetDescriptors = firstPackage.getAllDescriptors()
                val secondTargetDescriptors = secondPackage.getAllDescriptors()

                val (ftDescriptor, sdDesriptors, comDescriptors) = diffDescriptors(
                        firstTargetDescriptors.toList(), secondTargetDescriptors.toList()/*, commonTargetFragmentDescriptor, firstTargetFragmentDescriptor, secondTargetFragmentDescriptor*/
                )

                firstTargetNewPackage.expand(ftDescriptor)
                secondTargetNewPackage.expand(sdDesriptors)
                commonTargetNewPackage.expand(comDescriptors)

//                firstTargetFragmentDescriptor.expandScope(ftDescriptor)
//                secondTargetFragmentDescriptor.expandScope(sdDesriptors)
//                commonTargetFragmentDescriptor.expandScope(comDescriptors)

                firstTargetResult.add(firstTargetNewPackage)
                secondTargetResult.add(secondTargetNewPackage)
                commonResult.add(commonTargetNewPackage)
            }
        }

        // TODO update descriptors in target modules
        firstTargetResult.addAll(firstTargetPackages.filter { it !in passed }.map { it.toMergedIR() as MergedPackage })
        secondTargetResult.addAll(secondTargetPackages.filter { it !in passed }.map { it.toMergedIR() as MergedPackage })

        return Diff(firstTargetResult, secondTargetResult, commonResult)
    }


    private fun diffDescriptors(firstTargetDescriptors: List<DeclarationDescriptor>,
                                secondTargetDescriptors: List<DeclarationDescriptor>/*,
                                commonContainingDeclarationDescriptor: DeclarationDescriptor,
                                firstTargetConainingDeclarationDescriptor: DeclarationDescriptor,
                                secondTargetContainingDeclarationDescriptor: DeclarationDescriptor*/): Diff<MergedDescriptor> {
        val finalFirstTargetDescriptors = mutableListOf<MergedDescriptor>()
        val finalSecondTargetDescriptors = mutableListOf<MergedDescriptor>()

        val finalCommonDescriptors = mutableListOf<MergedDescriptor>()

        val passed = mutableSetOf<DeclarationDescriptor>()
        external@ for (firstDescriptor in firstTargetDescriptors) {
            for (secondDescriptor in secondTargetDescriptors) {
                if (firstDescriptor.name != secondDescriptor.name) {
                    continue
                }

                val intersectResult = intersector.intersect(firstDescriptor, secondDescriptor/*, commonContainingDeclarationDescriptor, firstTargetConainingDeclarationDescriptor,
                        secondTargetContainingDeclarationDescriptor*/)

                when (intersectResult) {
                    is CommonAndTargets -> {
                        passed.add(firstDescriptor)
                        passed.add(secondDescriptor)

                        finalFirstTargetDescriptors.add(intersectResult.firstTargetDescriptor)
                        finalSecondTargetDescriptors.add(intersectResult.secondTargetDescriptor)
                        finalCommonDescriptors.add(intersectResult.commonDescriptor)

//                        continue@external
                    }

                    /*is OnlyCommon -> {
                        passed.add(firstDescriptor)
                        passed.add(secondDescriptor)

                        finalCommonDescriptors.add(intersectResult.commonDescriptor)
                    }*/
                }
            }
        }

        // TODO update descriptors in target modules
        finalFirstTargetDescriptors.addAll(firstTargetDescriptors.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())
        finalSecondTargetDescriptors.addAll(secondTargetDescriptors.filter { it !in passed }.map { it.toMergedDescriptor() }.filterNotNull())

        return Diff(finalFirstTargetDescriptors, finalSecondTargetDescriptors, finalCommonDescriptors)
    }
}
