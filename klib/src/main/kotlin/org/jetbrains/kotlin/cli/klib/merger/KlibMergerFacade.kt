package org.jetbrains.kotlin.cli.klib.merger

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.backend.konan.KonanConfig
import org.jetbrains.kotlin.backend.konan.KonanConfigKeys
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.klib.DummyIntersector
import org.jetbrains.kotlin.cli.klib.Intersector
import org.jetbrains.kotlin.cli.klib.Library
import org.jetbrains.kotlin.cli.klib.defaultRepository
import org.jetbrains.kotlin.cli.klib.libraryInRepoOrCurrentDir
import org.jetbrains.kotlin.cli.klib.merger.descriptors.ModuleWithTargets
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.konan.isKonanStdlib
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.*
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.utils.KonanFactories
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import java.util.*

class KlibMergerFacade(private val repository: File, private val hostManager: PlatformManager, libs: List<KonanLibrary>) {
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

    fun merge(libs: List<KonanLibrary>): List<ModuleDescriptorImpl> {
        val newLoadDescriptors = loadDescriptors(repository, libs)
//        val modulesWithTargets = loadModulesWithTargets(libs)
//        val mergedModules = DeclarationDescriptorMerger(LockBasedStorageManager(), DefaultBuiltIns.Instance).merge(modulesWithTargets)
//        mergedModules.setDependencies(mergedModules)

        return /*serializeModule(mergedModules, konanConfig)*/ TODO()
    }

    fun diff(libs: List<KonanLibrary>): List<ModuleWithTargets> {
        val newLoadDescriptors = loadDescriptors(repository, libs)
//        val modules = loadModulesWithTargets(libs)
//        return DeclarationDescriptorDiffer(LockBasedStorageManager(), DefaultBuiltIns.Instance).diff(modules)
        return TODO()
    }

//    private fun loadModulesWithTargets(libs: List<KonanLibrary>): List<ModuleWithTargets> {
//        val modules = loadDescriptors(repository, libs)
//        val targets = libs.map { lib -> lib.targetList.map { hostManager.targetByName(it) } }
//
//        return (modules zip targets).map { (module, targets) -> ModuleWithTargets(module, targets) }
//    }

    fun mergeProperties(libs: List<KonanLibrary>): Properties = libs.map { it.manifestProperties }.first() // TODO
}

fun loadStdlib(distribution: Distribution,
               versionSpec: LanguageVersionSettings,
               storageManager: StorageManager): ModuleDescriptorImpl {
    val stdlib = Library(distribution.stdlib, null, "host")
    val library = libraryInRepoOrCurrentDir(stdlib.repository, stdlib.name)
    val stdlibModule = KonanFactories.DefaultDeserializedDescriptorFactory.createDescriptorAndNewBuiltIns(library, versionSpec, storageManager)
    stdlibModule.setDependencies(stdlibModule)
    return stdlibModule
}

private val currentLanguageVersion = LanguageVersion.LATEST_STABLE
private val currentApiVersion = ApiVersion.LATEST_STABLE

private fun loadDescriptors(repository: File, libs: List<KonanLibrary>, target: KonanTarget = KonanTarget.MACOS_X64): List<ModuleDescriptorImpl> {
    val distribution = Distribution()
    val storageManager = LockBasedStorageManager()
    val versionSpec = LanguageVersionSettingsImpl(currentLanguageVersion, currentApiVersion)

    val stdLib = loadStdlib(distribution, versionSpec, storageManager)
    val libariesNames = libs.map { it.libraryName }
    val libraryResolver = defaultResolver(
            listOf(defaultRepository.absolutePath),
            libariesNames,
            target,
            distribution,
            logger = { println(it) },
            compatibleCompilerVersions = listOf(KonanVersion.CURRENT)
    ).libraryResolver()

    val resolveWithDependencies = libraryResolver.resolveWithDependencies(
            unresolvedLibraries = libariesNames.toUnresolvedLibraries,
            noStdLib = false,
            noDefaultLibs = false
    )

    val resolvedDependencies = KonanFactories.DefaultResolvedDescriptorsFactory.createResolved(
            resolveWithDependencies, storageManager, stdLib.builtIns, versionSpec)

    return resolvedDependencies.resolvedDescriptors.filter { !it.isKonanStdlib() }
}



