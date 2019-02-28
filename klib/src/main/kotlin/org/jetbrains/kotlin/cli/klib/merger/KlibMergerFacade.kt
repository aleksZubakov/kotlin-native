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
import org.jetbrains.kotlin.cli.klib.libraryInRepoOrCurrentDir
import org.jetbrains.kotlin.cli.klib.merger.descriptors.ModuleWithTargets
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.KonanLibrary
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.utils.KonanFactories
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import java.util.*

class KlibMergerFacade(private val repository: File, private val hostManager: PlatformManager) {
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

    fun merge(libs: List<KonanLibrary>): ModuleDescriptorImpl {
//        val modulesWithTargets = loadModulesWithTargets(libs)
//        val mergedModules = DeclarationDescriptorMerger(LockBasedStorageManager(), DefaultBuiltIns.Instance).merge(modulesWithTargets)
//        mergedModules.setDependencies(mergedModules)

        return /*serializeModule(mergedModules, konanConfig)*/ TODO()
    }

    fun diff(libs: List<KonanLibrary>): Diff<ModuleDescriptorImpl> {
        val modules = loadModulesWithTargets(libs).map { it.module }
        return NewDeclarationDescriptorDiffer(DummyIntersector(), DefaultBuiltIns.Instance).diff(modules, modules)

    }

    private fun loadModulesWithTargets(libs: List<KonanLibrary>): List<ModuleWithTargets> {
        val modules = loadDescriptors(repository, libs)
        val targets = libs.map { lib -> lib.targetList.map { hostManager.targetByName(it) } }

        return (modules zip targets).map { (module, targets) -> ModuleWithTargets(module, targets) }
    }

    fun mergeProperties(libs: List<KonanLibrary>): Properties = libs.map { it.manifestProperties }.first() // TODO
}

fun loadStdlib(distribution: Distribution,
               versionSpec: LanguageVersionSettings,
               storageManager: StorageManager): ModuleDescriptorImpl {
    val stdlib = Library(distribution.stdlib, null, "host")
    val library = libraryInRepoOrCurrentDir(stdlib.repository, stdlib.name)
    return KonanFactories.DefaultDeserializedDescriptorFactory.createDescriptor(library, versionSpec, storageManager, DefaultBuiltIns.Instance)
}

private val currentLanguageVersion = LanguageVersion.LATEST_STABLE
private val currentApiVersion = ApiVersion.LATEST_STABLE

var stdLibModule: ModuleDescriptorImpl? = null

private fun loadDescriptors(repository: File, libraries: List<KonanLibrary>): List<ModuleDescriptorImpl> {
    // TODO pass [currentLanguageVersion] and [currentApiVersion] as parameters
    val versionSpec = LanguageVersionSettingsImpl(currentLanguageVersion, currentApiVersion)
    val storageManager = LockBasedStorageManager()

    // TODO find out is it required to load and set stdlib as dependency
    stdLibModule = loadStdlib(Distribution(), versionSpec, storageManager)
    stdLibModule?.setDependencies(stdLibModule!!)

    val defaultBuiltins = stdLibModule!!.builtIns/*DefaultBuiltIns.Instance*/
    val modules = mutableListOf<ModuleDescriptorImpl>()
    for (lib in libraries) {
        val konanLibrary = libraryInRepoOrCurrentDir(repository, lib.libraryName)
        val curModule = KonanFactories.DefaultDeserializedDescriptorFactory
                .createDescriptor(konanLibrary, versionSpec, storageManager, defaultBuiltins)

        // TODO is it ok to set curModule as itself dependency?
        /*modules + */listOf(curModule).let { allModules ->
            for (it in allModules) {
                it.setDependencies(listOf(stdLibModule!!, curModule))
            }
        }
        modules.add(curModule)
    }

    return modules
}



