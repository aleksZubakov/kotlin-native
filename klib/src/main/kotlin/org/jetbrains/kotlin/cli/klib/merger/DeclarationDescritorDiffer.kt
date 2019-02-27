package org.jetbrains.kotlin.cli.klib.merger

import org.jetbrains.kotlin.backend.common.lower.SimpleMemberScope
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.CommonAndTargets
import org.jetbrains.kotlin.cli.klib.Intersector
import org.jetbrains.kotlin.cli.klib.OnlyCommon
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.konan.KonanModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.SyntheticModulesOrigin
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.ImplicitIntegerCoercion
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.storage.LockBasedStorageManager

internal fun ModuleDescriptor.getPackagesFqNames(): Set<FqName> {
    val result = mutableSetOf<FqName>()

    fun getSubPackages(fqName: FqName) {
        result.add(fqName)
        getSubPackagesOf(fqName) { true }.forEach { getSubPackages(it) }
    }

    getSubPackages(FqName.ROOT)
    return result
}

data class Diff<T : DeclarationDescriptor>(
        val firstTargetModules: List<T>,
        val secondTargetModules: List<T>,
        val commonModules: List<T>
)


class NewDeclarationDescriptorDiffer(val intersector: Intersector, val builtIns: KotlinBuiltIns) {
    private fun getPackages(module: ModuleDescriptorImpl): List<PackageViewDescriptor> {
        // TODO add root package somewhere
        return module.getPackagesFqNames().map { module.getPackage(it) }.filter {
            it.fragments.all { packageFragmentDescriptor ->
                packageFragmentDescriptor.module == module || packageFragmentDescriptor.fqName != FqName.ROOT
            }
        }
    }

    private fun createPackageFragmentDescriptor(descriptors: List<DeclarationDescriptor>, name: FqName, module: ModuleDescriptorImpl): MergerFragmentDescriptor {
        val memberScope = SimpleMemberScope(descriptors)
        return MergerFragmentDescriptor(module, name, memberScope)
    }

    private fun PackageViewDescriptor.getDescriptors(descriptorKindFilter: DescriptorKindFilter) =
            memberScope.getDescriptorsFiltered(descriptorKindFilter)

    private fun PackageViewDescriptor.getAllDescriptors() = memberScope.getDescriptorsFiltered { true }

    fun createModule(moduleName: Name): ModuleDescriptorImpl {
        val storageManager = LockBasedStorageManager()
        val origin = SyntheticModulesOrigin // TODO find out is it ok to use that origins


        return ModuleDescriptorImpl(
                moduleName,
                storageManager,
                builtIns,
                capabilities = mapOf(
                        KonanModuleOrigin.CAPABILITY to origin,
                        ImplicitIntegerCoercion.MODULE_CAPABILITY to false
                ))

    }

    fun diff(firstTargetModules: List<ModuleDescriptorImpl>, secondTargetModules: List<ModuleDescriptorImpl>): Diff<ModuleDescriptorImpl> {
        val finalFirstTargetModules = mutableListOf<ModuleDescriptorImpl>()
        val finalSecondTargetModules = mutableListOf<ModuleDescriptorImpl>()

        val finalCommonModules = mutableListOf<ModuleDescriptorImpl>()

        val passed = mutableSetOf<ModuleDescriptorImpl>()
        for (firstOldModule in firstTargetModules) {
            for (secondOldModule in secondTargetModules) {
                if (firstOldModule.name != secondOldModule.name) {
                    continue
                }

                val commonModule = createModule(firstOldModule.name)

                val firstTargetPackages = getPackages(firstOldModule)
                val secondTargetPackages = getPackages(secondOldModule)

                val firstTargetModule = createModule(firstOldModule.name)
                val secondTargetModule = createModule(secondOldModule.name)
                val (ftPackageFragments, stPackageFragments, comPackageFragments) = diffPackages(firstTargetPackages, secondTargetPackages, CommonAndTargets(firstTargetModule, secondTargetModule, commonModule))

                firstTargetModule.initialize(PackageFragmentProviderImpl(ftPackageFragments))
                secondTargetModule.initialize(PackageFragmentProviderImpl(stPackageFragments))

                finalFirstTargetModules.add(firstTargetModule)
                finalSecondTargetModules.add(secondTargetModule)
            }
        }

        // TODO update ClassDescriptors and TypeAliasDescriptors in target modules
        finalFirstTargetModules.addAll(firstTargetModules.filter { it !in passed })
        finalSecondTargetModules.addAll(secondTargetModules.filter { it !in passed })

        return Diff(finalFirstTargetModules, finalSecondTargetModules, finalCommonModules)
    }


    private fun diffPackages(firstTargetPackages: List<PackageViewDescriptor>,
                             secondTargetPackages: List<PackageViewDescriptor>,
                             modules: CommonAndTargets<ModuleDescriptorImpl>): Diff<PackageFragmentDescriptor> {
        val firstTargetResult = mutableListOf<PackageFragmentDescriptor>()
        val secondTargetResult = mutableListOf<PackageFragmentDescriptor>()

        val commonResult = mutableListOf<PackageFragmentDescriptor>()

        val passed = mutableSetOf<PackageViewDescriptor>()
        for (firstPackage in firstTargetPackages) {
            for (secondPackage in secondTargetPackages) {
                if (firstPackage.name != secondPackage.name) {
                    continue
                }

                passed.add(firstPackage)
                passed.add(secondPackage)

                val firstTargetDescriptors = firstPackage.getAllDescriptors()
                val secondTargetDescriptors = secondPackage.getAllDescriptors()

                val (ftDescriptor, sdDesriptors, comDescriptors) = diffDescriptors(firstTargetDescriptors.toList(), secondTargetDescriptors.toList())

                firstTargetResult.add(createPackageFragmentDescriptor(ftDescriptor, firstPackage.fqName, modules.firstTargetDescriptor))
                secondTargetResult.add(createPackageFragmentDescriptor(sdDesriptors, secondPackage.fqName, modules.secondTargetDescriptor))
                commonResult.add(createPackageFragmentDescriptor(comDescriptors, firstPackage.fqName, modules.commonDescriptor))
            }
        }

        // TODO update ClassDescriptors and TypeAliasDescriptors in target modules
        firstTargetResult.addAll(firstTargetPackages.filter { it !in passed }.flatMap { it.fragments })
        secondTargetResult.addAll(secondTargetPackages.filter { it !in passed }.flatMap { it.fragments })

        return Diff(firstTargetResult, secondTargetResult, commonResult)
    }



    private fun diffDescriptors(firstTargetDescriptors: List<DeclarationDescriptor>, secondTargetDescriptors: List<DeclarationDescriptor>): Diff<DeclarationDescriptor> {
        val finalFirstTargetDescriptors = mutableListOf<DeclarationDescriptor>()
        val finalSecondTargetDescriptors = mutableListOf<DeclarationDescriptor>()

        val finalCommonDescriptors = mutableListOf<DeclarationDescriptor>()

        val passed = mutableSetOf<DeclarationDescriptor>()
        for (firstDescriptor in firstTargetDescriptors) {
            for (secondDescriptor in secondTargetDescriptors) {
                if (firstDescriptor.name != secondDescriptor.name) {
                    continue
                }

                passed.add(firstDescriptor)
                passed.add(secondDescriptor)

                val intersectResult = intersector.intersect(firstDescriptor, secondDescriptor)

                when (intersectResult) {
                    is CommonAndTargets -> {
                        passed.add(firstDescriptor)
                        passed.add(secondDescriptor)

                        finalFirstTargetDescriptors.add(intersectResult.firstTargetDescriptor)
                        finalSecondTargetDescriptors.add(intersectResult.secondTargetDescriptor)
                        finalCommonDescriptors.add(intersectResult.commonDescriptor)
                    }

                    is OnlyCommon -> {
                        passed.add(firstDescriptor)
                        passed.add(secondDescriptor)

                        finalCommonDescriptors.add(intersectResult.commonDescriptor)
                    }
                }
            }
        }

        // TODO update ClassDescriptors and TypeAliasDescriptors in target modules
        finalFirstTargetDescriptors.addAll(firstTargetDescriptors.filter { it !in passed })
        finalSecondTargetDescriptors.addAll(secondTargetDescriptors.filter { it !in passed })

        return Diff(finalFirstTargetDescriptors, finalSecondTargetDescriptors, finalCommonDescriptors)
    }
}
