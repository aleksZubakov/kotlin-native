package org.jetbrains.kotlin.cli.klib.merger.compilerrunner

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.cli.common.messages.OutputMessageUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser
import org.jetbrains.kotlin.cli.metadata.MetadataSerializer
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.metadata.builtins.BuiltInsBinaryVersion
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.utils.KotlinPaths
import java.io.File

class KlibMergerK2MetadataCompiler : CLICompiler<K2MetadataCompilerArguments>() {

    override val performanceManager = K2MetadataCompilerPerformanceManager()

    override fun createArguments() = K2MetadataCompilerArguments()

    override fun setupPlatformSpecificArgumentsAndServices(
            configuration: CompilerConfiguration, arguments: K2MetadataCompilerArguments, services: Services
    ) {
        // No specific arguments yet
    }

    override fun doExecute(
            arguments: K2MetadataCompilerArguments,
            configuration: CompilerConfiguration,
            rootDisposable: Disposable,
            paths: KotlinPaths?
    ): ExitCode {
        val collector = configuration.getNotNull(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY)

        val pluginLoadResult = PluginCliParser.loadPluginsSafe(arguments.pluginClasspaths, arguments.pluginOptions, configuration)
        if (pluginLoadResult != ExitCode.OK) return pluginLoadResult

        for (arg in arguments.freeArgs) {
            configuration.addKotlinSourceRoot(arg, isCommon = true)
        }
        if (arguments.classpath != null) {
            // HERE
            configuration.addJvmClasspathRoots(arguments.classpath!!.split(File.pathSeparatorChar).map(::File))
        }

        configuration.put(CommonConfigurationKeys.MODULE_NAME, arguments.moduleName ?: JvmAbi.DEFAULT_MODULE_NAME)

        configuration.put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, arguments.allowKotlinPackage)

        val destination = arguments.destination
        if (destination != null) {
            if (destination.endsWith(".jar")) {
                // TODO: support .jar destination
                collector.report(
                        CompilerMessageSeverity.STRONG_WARNING,
                        ".jar destination is not yet supported, results will be written to the directory with the given name"
                )
            }
            configuration.put(CLIConfigurationKeys.METADATA_DESTINATION_DIRECTORY, File(destination))
        }

        val environment =
                KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.METADATA_CONFIG_FILES)

        if (environment.getSourceFiles().isEmpty()) {
            if (arguments.version) {
                return ExitCode.OK
            }
            collector.report(CompilerMessageSeverity.ERROR, "No source files")
            return ExitCode.COMPILATION_ERROR
        }

        checkKotlinPackageUsage(environment, environment.getSourceFiles())

        try {
            val metadataVersion =
                    configuration.get(CommonConfigurationKeys.METADATA_VERSION) as? BuiltInsBinaryVersion
                            ?: BuiltInsBinaryVersion.INSTANCE
            KlibMergerMetadataSerializer(metadataVersion, true).serialize(environment)
        } catch (e: CompilationException) {
            collector.report(CompilerMessageSeverity.EXCEPTION, OutputMessageUtil.renderException(e), MessageUtil.psiElementToMessageLocation(e.element))
            return ExitCode.INTERNAL_ERROR
        }

        return ExitCode.OK
    }

    // TODO: update this once a launcher script for K2MetadataCompiler is available
    override fun executableScriptFileName(): String = "kotlinc"

    override fun createMetadataVersion(versionArray: IntArray): BinaryVersion = BuiltInsBinaryVersion(*versionArray)

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            doMain(KlibMergerK2MetadataCompiler(), args)
        }
    }

    protected class K2MetadataCompilerPerformanceManager : CommonCompilerPerformanceManager("Kotlin to Metadata compiler")
}
