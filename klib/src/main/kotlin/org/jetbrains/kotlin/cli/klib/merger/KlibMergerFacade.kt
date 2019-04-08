package org.jetbrains.kotlin.cli.klib.merger

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.backend.konan.KonanConfig
import org.jetbrains.kotlin.backend.konan.KonanConfigKeys
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.klib.Library
import org.jetbrains.kotlin.cli.klib.defaultRepository
import org.jetbrains.kotlin.cli.klib.libraryInRepoOrCurrentDir
import org.jetbrains.kotlin.cli.klib.merger.ir.MergeVisitor
import org.jetbrains.kotlin.cli.klib.merger.ir.Node
import org.jetbrains.kotlin.cli.klib.merger.ir.SingleTargetDeclarationToMergeIRVisitor
import org.jetbrains.kotlin.cli.klib.merger.ir.transformIntoSingleTargetNodes
import org.jetbrains.kotlin.cli.klib.merger.ir.visitors.BuildClassifiersVisitor
import org.jetbrains.kotlin.cli.klib.merger.ir.visitors.ClassifiersBuildContext
import org.jetbrains.kotlin.cli.klib.merger.ir.visitors.DeclarationsInitializationVisitor
import org.jetbrains.kotlin.cli.klib.merger.ir.visitors.WeirdTriple
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.konan.isKonanStdlib
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.*
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.utils.KonanFactories
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import java.util.*


data class MergeResult(
        val diff: Diff<ModuleDescriptorImpl> // ,
//        val firstTargetDependencies: List<>
)

class KlibMergerFacade(private val repository: File,
                       private val hostManager: PlatformManager,
        /*libs: List<KonanLibrary>*/
                       val compilerVersion: KonanVersion) {
    private val konanConfig: KonanConfig

    init {
        val configuration = CompilerConfiguration()
        configuration.put(KonanConfigKeys.PRODUCE, CompilerOutputKind.valueOf("program".toUpperCase()))
        configuration.put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, LanguageVersionSettingsImpl.DEFAULT)

        val rootDisposable = Disposer.newDisposable()
        val environment = KotlinCoreEnvironment.createForProduction(rootDisposable,
                configuration, EnvironmentConfigFiles.NATIVE_CONFIG_FILES)
        val project = environment.project

        konanConfig = KonanConfig(project, configuration)

    }

    fun merge(firstTargetLibs: List<KonanLibrary>, secondTargetLibs: List<KonanLibrary>): Diff<ModuleDescriptorImpl> {
        assert(firstTargetLibs.size == secondTargetLibs.size)
        val descriptorLoader = DescriptorLoader(hostManager, compilerVersion)

        val firstTargetModules = descriptorLoader.loadDescriptors(repository, firstTargetLibs)
        val secondTargetModules = descriptorLoader.loadDescriptors(repository, secondTargetLibs)
        val mergerVisitor = MergeVisitor()

        val result = mutableListOf<Node>()
        val passed = mutableSetOf<ModuleDescriptorImpl>()
        for (ftModule in firstTargetModules) {
            for (stModule in secondTargetModules) {
                if (ftModule.name != stModule.name) {
                    continue
                }

                result.add(ftModule.accept(mergerVisitor, stModule))
                passed.add(ftModule)
                passed.add(stModule)
            }
        }


        val singleTargetVisitor = SingleTargetDeclarationToMergeIRVisitor()
        result.addAll(firstTargetModules.filter { it !in passed }.transformIntoSingleTargetNodes(singleTargetVisitor, isFirstTarget = true))
        result.addAll(secondTargetModules.filter { it !in passed }.transformIntoSingleTargetNodes(singleTargetVisitor, isFirstTarget = false))

        val buildContext = ClassifiersBuildContext()
        val buildClassifiersVisitor = BuildClassifiersVisitor(DefaultBuiltIns.Instance, buildContext)
        for (moduleNode in result) {
            moduleNode.accept(buildClassifiersVisitor, WeirdTriple(null, null, null))
        }

        val mergerIrToDeclarationDescriptorVisitor = DeclarationsInitializationVisitor(DefaultBuiltIns.Instance, buildContext)
        val totalResult =
                result.map {
                    it.accept(mergerIrToDeclarationDescriptorVisitor, WeirdTriple(null, null, null))
                            as WeirdTriple<ModuleDescriptorImpl>
                }

        return Diff(
                firstTargetModules = totalResult.map { it.firstTargetDescriptor }.filterNotNull().also {
                    it.forEach { mod -> mod.setDependencies(it) }
                },
                commonModules = totalResult.map { it.commonTargetDescriptor }.filterNotNull().also {
                    it.forEach { mod -> mod.setDependencies(it) }
                },
                secondTargetModules = totalResult.map { it.secondTargetDescriptor }.filterNotNull().also {
                    it.forEach { mod -> mod.setDependencies(it) }
                }
        )

////        val descriptorFactory = MergerDescriptorFactory(DefaultBuiltIns.Instance, LockBasedStorageManager())
//        val newDeclarationDescriptorDiffer = DeclarationDescriptorMerger(DummyIntersector())
//
//        return newDeclarationDescriptorDiffer.diff(firstTargetModules, secondTargetModules).let { (firstTarget, secondTarget, common) ->
//            val mergerIRToDeclarationDescriptorVisitor = MergedIRToDescriptorsVisitor(DefaultBuiltIns.Instance)
//
//            val firstTargetModules = firstTarget.map { it.accept(mergerIRToDeclarationDescriptorVisitor, null) }.also {
//                it.forEach { mod ->
//                    mod.setDependencies(it)
//                }
//            }
//            val secondTargetModules = secondTarget.map { it.accept(mergerIRToDeclarationDescriptorVisitor, null) }.also {
//                it.forEach { mod ->
//                    mod.setDependencies(it)
//                }
//            }
//
//            val commonTargetModules = common.map { it.accept(mergerIRToDeclarationDescriptorVisitor, null) }.also {
//                it.forEach { mod ->
//                    mod.setDependencies(it)
//                }
//            }
//
//
////            return commonTargetModules
//            return@let Diff(
//                    firstTargetModules = firstTargetModules,
//                    secondTargetModules = secondTargetModules,
//                    commonModules = commonTargetModules
//            )
//        }
    }

//    fun diff(libs: List<KonanLibrary>): List<ModuleWithTargets> {
//        val newLoadDescriptors = loadDescriptors(repository, libs)
////        val modules = loadModulesWithTargets(libs)
////        return DeclarationDescriptorDiffer(LockBasedStorageManager(), DefaultBuiltIns.Instance).diff(modules)
//        return TODO()
//    }

//    private fun loadModulesWithTargets(libs: List<KonanLibrary>): List<ModuleWithTargets> {
//        val modules = loadDescriptors(repository, libs)
//        val targets = libs.map { lib -> lib.targetList.map { hostManager.targetByName(it) } }
//
//        return (modules zip targets).map { (module, targets) -> ModuleWithTargets(module, targets) }
//    }

    fun mergeProperties(libs: List<KonanLibrary>): Properties = libs.map { it.manifestProperties }.first() // TODO
}

class DescriptorLoader(val hostManager: PlatformManager, val compilerVersion: KonanVersion) {
    companion object {
        private val currentLanguageVersion = LanguageVersion.LATEST_STABLE
        private val currentApiVersion = ApiVersion.LATEST_STABLE
        val distribution = Distribution()
        val versionSpec = LanguageVersionSettingsImpl(currentLanguageVersion, currentApiVersion)
    }


    val storageManager = LockBasedStorageManager("")

    val stdLib = loadStdlib()


    private fun loadStdlib(): ModuleDescriptorImpl {
        val stdlib = Library(distribution.stdlib, null, "host")
        val library = libraryInRepoOrCurrentDir(stdlib.repository, stdlib.name)
        val stdlibModule = KonanFactories.DefaultDeserializedDescriptorFactory.createDescriptorAndNewBuiltIns(library, versionSpec, storageManager)
        stdlibModule.setDependencies(stdlibModule)
        return stdlibModule
    }

    fun loadDescriptors(repository: File, libs: List<KonanLibrary>): List<ModuleDescriptorImpl> {
        val libariesNames = libs.map { it.libraryName }

        val target = hostManager.targetByName(libs.first().targetList.first())
        val libraryResolver = defaultResolver(
                listOf(defaultRepository.absolutePath),
                libariesNames,
                target,
                distribution,
                logger = { println(it) },
                compatibleCompilerVersions = listOf(compilerVersion)
        ).libraryResolver()

        val resolveWithDependencies = libraryResolver.resolveWithDependencies(
                unresolvedLibraries = libariesNames.toUnresolvedLibraries,
                noStdLib = true,
                noDefaultLibs = true
        )

        val resolvedDependencies = KonanFactories.DefaultResolvedDescriptorsFactory.createResolved(
                resolveWithDependencies, storageManager, stdLib.builtIns, versionSpec)

        return resolvedDependencies.resolvedDescriptors.filter { !it.isKonanStdlib() }
    }
}
