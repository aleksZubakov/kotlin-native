package org.jetbrains.kotlin.cli.klib.merger.compilerrunner

import org.jetbrains.kotlin.cli.bc.K2Native
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.CLITool
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.klib.merger.descriptors.MergerK2MetadataCompiler
import org.jetbrains.kotlin.konan.file.File
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


private fun executeCompiler(compiler: CLITool<*>, args: List<String>): Pair<String, ExitCode> {
    val bytes = ByteArrayOutputStream()
    val origErr = System.err
    try {
        System.setErr(PrintStream(bytes))
        val exitCode = CLITool.doMainNoExit(compiler, args.toTypedArray())
        return Pair(String(bytes.toByteArray()), exitCode)
    } finally {
        System.setErr(origErr)
    }
}

private fun CLICompiler<*>.compile(sources: List<File>, commonSources: List<File>, vararg mainArguments: String): String = buildString {
    val commonSourcesArgs = commonSources.map { "-Xcommon-sources=${it.absolutePath}" }
    val multiplatformFlag = if (commonSources.isNotEmpty()) "-Xmulti-platform" else ""
    val common = if (commonSources.isNotEmpty()) {
        commonSources.map { it.absolutePath.toString() } +
                commonSourcesArgs + multiplatformFlag
    } else {
        listOf()
    }

    val (output, exitCode) = executeCompiler(
            this@compile, sources.map { it.absolutePath.toString() } + common + mainArguments
    )
    appendln("Exit code: $exitCode")
    appendln("Output:")
    appendln(output)
}/*.trimTrailingWhitespacesAndAddNewlineAtEOF().trimEnd('\r', '\n')*/


private fun findStdlibCommon(): java.io.File {
    // Take kotlin-stdlib-common.jar from dist/ when it's there
    val fromDist = java.io.File("../kotlin/dist/kotlinc/lib/kotlin-stdlib-common.jar")
    if (fromDist.isFile) return fromDist

    val stdlibCommonLibsDir = "../kotlin/libraries/stdlib/common/build/libs"
    val commonLibs = Files.newDirectoryStream(Paths.get(stdlibCommonLibsDir)).use(Iterable<Path>::toList)
    return commonLibs.sorted().findLast {
        val name = it.toFile().name
        !name.endsWith("-javadoc.jar") && !name.endsWith("-sources.jar") && !name.contains("coroutines")
    }?.toFile() ?: error("kotlin-stdlib-common is not found in $stdlibCommonLibsDir")
}


fun compileNative(nativeSources: List<File>, commonSources: List<File>, output: File) =
        K2Native().compile(nativeSources, commonSources, "-p", "library", "-nopack", "-Xdisable", "backend", "-o", output.absolutePath.toString())


fun compileOnlyCommon(commonSources: List<File>, output: File) =
        MergerK2MetadataCompiler().compile(commonSources, emptyList(), "-d", output.absolutePath.toString(), "-cp", findStdlibCommon().absolutePath)
