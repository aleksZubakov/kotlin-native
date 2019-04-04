package org.jetbrains.kotlin.cli.klib.merger.compilerrunner

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.analyzer.common.CommonAnalysisParameters
import org.jetbrains.kotlin.analyzer.common.CommonAnalyzerFacade
import org.jetbrains.kotlin.analyzer.common.CommonPlatform
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.Library
import org.jetbrains.kotlin.cli.klib.libraryInRepoOrCurrentDir
import org.jetbrains.kotlin.cli.klib.merger.DescriptorLoader
import org.jetbrains.kotlin.cli.klib.merger.DescriptorLoader.Companion.versionSpec
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.TargetPlatformVersion
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.useImpl
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.contracts.ContractDeserializerImpl
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProviderImpl
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.konan.SyntheticModulesOrigin
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.utils.KonanFactories
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragmentProvider
import org.jetbrains.kotlin.serialization.deserialization.MetadataPartProvider
import org.jetbrains.kotlin.serialization.konan.impl.ForwardDeclarationsFqNames
import org.jetbrains.kotlin.serialization.konan.impl.ForwardDeclarationsPackageFragmentDescriptor
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager

class NativeModuleInfo() : ModuleInfo {
    override val name: Name
        get() = TODO("not implemented")

    override fun dependencies(): List<ModuleInfo> = emptyList()

    override fun dependencyOnBuiltIns(): ModuleInfo.DependencyOnBuiltIns = ModuleInfo.DependencyOnBuiltIns.LAST
}

class NativeForwardDeclarations() : ModuleInfo {
    override val name: Name
        get() = TODO("not implemented")

    override fun dependencies(): List<ModuleInfo> = emptyList()
}

class NativeDelegateResolver<M : ModuleInfo>(private val nativeStdlib: M, private val nativeForwardDeclarations: M) : ResolverForProject<M>() {
    override val name: String
        get() = "native delegate resolver"

    override fun tryGetResolverForModule(moduleInfo: M): ResolverForModule? = null
    override fun resolverForModuleDescriptor(descriptor: ModuleDescriptor): ResolverForModule =
            throw IllegalStateException("$descriptor is not contained in this resolver")

    private val stdlibModule: ModuleDescriptor
    private val storageManager = LockBasedStorageManager("native")

    init {
        val stdlib = Library(DescriptorLoader.distribution.stdlib, null, "host")
        val library = libraryInRepoOrCurrentDir(stdlib.repository, stdlib.name)
        val stdlibModule = KonanFactories.DefaultDeserializedDescriptorFactory.createDescriptorAndNewBuiltIns(library, versionSpec, storageManager)
        stdlibModule.setDependencies(stdlibModule)
        this.stdlibModule = stdlibModule
    }

    private fun createForwardDeclarationsModule(
            builtIns: KotlinBuiltIns?,
            storageManager: StorageManager): ModuleDescriptorImpl {

        val name = Name.special("<forward declarations>")
        val module = KonanFactories.DefaultDescriptorFactory.createDescriptor(name, storageManager, stdlibModule.builtIns, SyntheticModulesOrigin)
        fun createPackage(fqName: FqName, supertypeName: String, classKind: ClassKind) =
                ForwardDeclarationsPackageFragmentDescriptor(
                        storageManager,
                        module,
                        fqName,
                        Name.identifier(supertypeName),
                        classKind)

        val packageFragmentProvider = PackageFragmentProviderImpl(
                listOf(
                        createPackage(ForwardDeclarationsFqNames.cNamesStructs, "COpaque", ClassKind.CLASS),
                        createPackage(ForwardDeclarationsFqNames.objCNamesClasses, "ObjCObjectBase", ClassKind.CLASS),
                        createPackage(ForwardDeclarationsFqNames.objCNamesProtocols, "ObjCObject", ClassKind.INTERFACE)
                )
        )

        module.initialize(packageFragmentProvider)
        module.setDependencies(module)

        return module
    }


    override fun descriptorForModule(moduleInfo: M): ModuleDescriptor {
        return if (moduleInfo is NativeModuleInfo) {
            stdlibModule
        } else if (moduleInfo is NativeForwardDeclarations) {
            createForwardDeclarationsModule(null, storageManager)
        } else {
            error("")
        }
    }

    override val allModules: Collection<M> = listOf(nativeStdlib, nativeForwardDeclarations)
    override fun diagnoseUnknownModuleInfo(infos: List<ModuleInfo>) = throw IllegalStateException("Should not be called for $infos")
    override val builtIns: KotlinBuiltIns = stdlibModule.builtIns
}

object KlibMergerCommonAnalyzerFacade : ResolverForModuleFactory() {
    private class SourceModuleInfo(
            override val name: Name,
            override val capabilities: Map<ModuleDescriptor.Capability<*>, Any?>,
            private val dependOnOldBuiltIns: Boolean
    ) : ModuleInfo {
        val nativeStdLib = NativeModuleInfo()
        val nativeForwardDeclarations = NativeForwardDeclarations()

        override fun dependencies(): List<ModuleInfo> {
            return listOf(this, nativeStdLib, nativeForwardDeclarations)
        }

        override fun dependencyOnBuiltIns(): ModuleInfo.DependencyOnBuiltIns =
                if (dependOnOldBuiltIns) ModuleInfo.DependencyOnBuiltIns.LAST else ModuleInfo.DependencyOnBuiltIns.NONE
    }

    fun analyzeFiles(
            files: Collection<KtFile>, moduleName: Name, dependOnBuiltIns: Boolean, languageVersionSettings: LanguageVersionSettings,
            capabilities: Map<ModuleDescriptor.Capability<*>, Any?> = mapOf(MultiTargetPlatform.CAPABILITY to MultiTargetPlatform.Common),
            metadataPartProviderFactory: (ModuleContent<ModuleInfo>) -> MetadataPartProvider
    ): AnalysisResult {
        val moduleInfo = SourceModuleInfo(moduleName, capabilities, false/*dependOnBuiltIns*/)
        val project = files.firstOrNull()?.project ?: throw AssertionError("No files to analyze")

        val multiplatformLanguageSettings = object : LanguageVersionSettings by languageVersionSettings {
            override fun getFeatureSupport(feature: LanguageFeature): LanguageFeature.State =
                    if (feature == LanguageFeature.MultiPlatformProjects) LanguageFeature.State.ENABLED
                    else languageVersionSettings.getFeatureSupport(feature)
        }

        val nativeDelegateResolver = NativeDelegateResolver(moduleInfo.nativeStdLib, moduleInfo.nativeForwardDeclarations)

        @Suppress("NAME_SHADOWING")
        val resolver = ResolverForProjectImpl(
                "sources for metadata serializer",
                ProjectContext(project),
                listOf(moduleInfo),
                delegateResolver = nativeDelegateResolver,
                modulesContent = { ModuleContent(it, files, GlobalSearchScope.allScope(project)) },
                moduleLanguageSettingsProvider = object : LanguageSettingsProvider {
                    override fun getLanguageVersionSettings(
                            moduleInfo: ModuleInfo,
                            project: Project,
                            isReleaseCoroutines: Boolean?
                    ) = multiplatformLanguageSettings

                    override fun getTargetPlatform(
                            moduleInfo: ModuleInfo,
                            project: Project
                    ) = TargetPlatformVersion.NoVersion
                },
                resolverForModuleFactoryByPlatform = { CommonAnalyzerFacade },
                platformParameters = { _ -> CommonAnalysisParameters(metadataPartProviderFactory) }
        )

        val moduleDescriptor = resolver.descriptorForModule(moduleInfo)
        val container = resolver.resolverForModule(moduleInfo).componentProvider

        container.get<LazyTopDownAnalyzer>().analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files)

        return AnalysisResult.success(container.get<BindingTrace>().bindingContext, moduleDescriptor)
    }

    override fun <M : ModuleInfo> createResolverForModule(
            moduleDescriptor: ModuleDescriptorImpl,
            moduleContext: ModuleContext,
            moduleContent: ModuleContent<M>,
            platformParameters: PlatformAnalysisParameters,
            targetEnvironment: TargetEnvironment,
            resolverForProject: ResolverForProject<M>,
            languageVersionSettings: LanguageVersionSettings,
            targetPlatformVersion: TargetPlatformVersion
    ): ResolverForModule {
        val (moduleInfo, syntheticFiles, moduleContentScope) = moduleContent
        val project = moduleContext.project
        val declarationProviderFactory = DeclarationProviderFactoryService.createDeclarationProviderFactory(
                project, moduleContext.storageManager, syntheticFiles,
                moduleContentScope,
                moduleInfo
        )

        val metadataPartProvider = (platformParameters as CommonAnalysisParameters).metadataPartProviderFactory(moduleContent)
        val trace = CodeAnalyzerInitializer.getInstance(project).createTrace()
        val container = createContainerToResolveCommonCode(
                moduleContext, trace, declarationProviderFactory, moduleContentScope, targetEnvironment, metadataPartProvider,
                languageVersionSettings
        )

        val packageFragmentProviders = listOf(
                container.get<ResolveSession>().packageFragmentProvider,
                container.get<MetadataPackageFragmentProvider>()
        )

        return ResolverForModule(CompositePackageFragmentProvider(packageFragmentProviders), container)
    }

    private fun createContainerToResolveCommonCode(
            moduleContext: ModuleContext,
            bindingTrace: BindingTrace,
            declarationProviderFactory: DeclarationProviderFactory,
            moduleContentScope: GlobalSearchScope,
            targetEnvironment: TargetEnvironment,
            metadataPartProvider: MetadataPartProvider,
            languageVersionSettings: LanguageVersionSettings
    ): StorageComponentContainer = createContainer("ResolveCommonCode", targetPlatform) {
        configureModule(moduleContext, targetPlatform, TargetPlatformVersion.NoVersion, bindingTrace)

        useInstance(moduleContentScope)
        useInstance(LookupTracker.DO_NOTHING)
        useInstance(ExpectActualTracker.DoNothing)
        useImpl<ResolveSession>()
        useImpl<LazyTopDownAnalyzer>()
        useInstance(languageVersionSettings)
        useImpl<AnnotationResolverImpl>()
        useImpl<CompilerDeserializationConfiguration>()
        useInstance(metadataPartProvider)
        useInstance(declarationProviderFactory)
        useImpl<MetadataPackageFragmentProvider>()
        useImpl<ContractDeserializerImpl>()

        val metadataFinderFactory = ServiceManager.getService(moduleContext.project, MetadataFinderFactory::class.java)
                ?: error("No MetadataFinderFactory in project")
        useInstance(metadataFinderFactory.create(moduleContentScope))

        targetEnvironment.configure(this)
    }

    override val targetPlatform: TargetPlatform
        get() = CommonPlatform
}
