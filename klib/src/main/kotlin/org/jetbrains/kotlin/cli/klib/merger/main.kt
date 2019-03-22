package org.jetbrains.kotlin.cli.klib.merger

import org.jetbrains.kotlin.cli.klib.libraryInRepo
import org.jetbrains.kotlin.cli.klib.merger.compilerrunner.compileOnlyCommon
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.impl.KonanLibraryImpl
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

fun mergeLib(repository: File, platformsPaths: List<String>, whatToMerge: List<String>): Diff<ModuleDescriptorImpl> {
    val distribution = Distribution()
    val platformManager = PlatformManager(distribution)
    val libs = mutableListOf<List<KonanLibraryImpl>>()

    for (platform in platformsPaths) {
        val librariesNames = whatToMerge.map {
            distribution.klib + platform /*+ it*/
        }

//        val librariesNames = platformsPaths.map { distribution.klib + it + whatToMerge }
        val libsExist = librariesNames.map(::File).all { it.exists }
        assert(libsExist) { "Cannot find libraries with given names in repo for $platform" }

        val libraries = librariesNames.map {
            libraryInRepo(repository, it)
        }

        libs.add(libraries)
    }

    val (firstTarget, secondTarget) = libs
    val klibMergerFacade = KlibMergerFacade(repository, platformManager)
    val linkData = klibMergerFacade.merge(firstTarget, secondTarget)
    return linkData
}

//    val properties = klibMerger.mergeProperties(libraries)
//
//    klibBuilder.addLinkData(linkData)
//    klibBuilder.addManifestAddend(properties)
//    klibBuilder.commit()


//fun merge(repository: File, platformsPaths: List<String>, whatToMerge: List<String>) {
//    for (what in whatToMerge) {
//        mergeLib(repository, platformsPaths, what)
//    }
//}


//fun diff(repository: File, platformsPaths: List<String>, whatToMerge: List<String>): List<ModuleDescriptorImpl> {
//    val distribution = Distribution()
//    val platformManager = PlatformManager(distribution)
//
//    val klibMerger = KlibMergerFacade(repository, platformManager)
//    java.io.File("diffed_lin").mkdir()
//
//    val total = mutableListOf<ModuleDescriptorImpl>()
//    for (what in whatToMerge) {
//        val librariesNames = platformsPaths.map { distribution.klib + it + what }
//
//
//        val libsExist = librariesNames.map(::File).all { it.exists }
//        if (!libsExist) {
//            continue // TODO
//        }
//
//        val libraries = librariesNames.map {
//            libraryInRepo(repository, it)
//        }
//
//        val output = "diffed_lin/$what"
//        val a = klibMerger.diff(libraries)
//
//        for ((l, targets) in a) {
//            l.setDependencies(l)
//            total.add(l)
//        }
//    }
//    return total
//}

fun printLib(prefix: String, mergeLib: ModuleDescriptorImpl, output: File): List<File> {
    val packages = mergeLib.getPackagesFqNames()
//    val stdlibPackages = stdLibModule!!.getPackagesFqNames()

    val resultPackages = packages/* - stdlibPackages*/
    val map = resultPackages.map {
        it to mergeLib.getPackage(it).memberScope.getDescriptorsFiltered(
                DescriptorKindFilter(
                        DescriptorKindFilter.ALL_KINDS_MASK xor DescriptorKindFilter.PACKAGES_MASK
                )

        )
    }

    val sourceFiles = mutableListOf<File>()
    for ((name, descriptors) in map) {
        val sourceCode = printDescriptors(name, descriptors.toList())
        val whereToPrint = output.resolve("$prefix$name.kt")
        whereToPrint.printWriter().use {
            it.println(sourceCode)
        }
        sourceFiles.add(whereToPrint)
    }

    return sourceFiles
}

fun main(args: Array<String>) {
    val whatToMerge = mutableListOf<String>().apply {
        //        add("Metal")
//        add("CoreFoundation")
//        add("CoreText")
//        add("objc")
//        add("Security")
//        add("iconv")
//        add("QuartzCore")
//        add("Foundation")
//        add("ImageIO")
//        add("CFNetwork")
//        add("CoreVideo")
//        add("CoreImage")
        add("darwin")
//        add("IOSurface")
//        add("CoreGraphics")
//        add("zlib")
//        add("Accelerate")
//        add("CoreML")
//        add("CoreData")
//        add("posix")
//        add("CoreServices")
    }

    val platformsPaths = mutableListOf<String>().apply {
        add("/platform/kot-nat1/")
        add("/platform/kot-nat2/")
//            "/platform/macos_x64/"
//,
//            "/platform/linux_x64/" //,
//        "/platform/linux_mips3

//            "/platform/android_arm64/"
//            "/platform/linux_arm32_hfpStdlib"
    }

//    val message = File("/home/aleks/Documents/work/kotlin-native/dist/klib/platform/macos_x64").listFiles.map { it.name }
//    val message2 = File("/home/aleks/Documents/work/kotlin-native/dist/klib/platform/ios_x64").listFiles.map { it.name }


//    for (name in message intersect message2) {
//        println("add(\"$name\")")
//    }

    val repository = File("/home/aleks/Documents/work/kotlin-native/dist/klib")
    val output = File("merged")
    output.mkdirs()

    val diff = mergeLib(repository, platformsPaths, whatToMerge)

    val commonSources = diff.commonModules.flatMap {
        printLib("<common3>", it, output)
    }

    val firstTargetSources = diff.firstTargetModules.flatMap {
        printLib("<firstTarget3>", it, output)
    }

    val secondTargetSources = diff.secondTargetModules.flatMap {
        printLib("<secondTarget3>", it, output)
    }

    output.resolve("common_output.txt").printWriter().use {
        it.println(compileOnlyCommon(commonSources, output.resolve("coom.out")))
    }

//    output.resolve("3firstTarget.txt").printWriter().use {
//        it.print(compileNative(firstTargetSources, commonSources, output))
//    }
//
//    output.resolve("3secondTarget.txt").printWriter().use {
//        it.print(compileNative(secondTargetSources, commonSources, output))
//    }


    println("whoopp")
//    val sourceFiles = printLib(mergedLibs.first(), output).distinct()
//            mergedLibs.flatMap { printLib(mergedLibs.first(), output) }.distinct()
//
//    val native = listOf(
////            "platform.Foundation.kt",
//            "platform.darwin.kt",
//            "platform.objc.kt",
//            "platform.osx.kt",
//            "platform.posix.kt"//,
////            "platform.FOundation.kt",
//    ).map { output.resolve(it) }
//
//    val common = emptyList<File>()
//
//    output.resolve("Log7.txt").printWriter().use {
//        it.print(compileNative(sourceFiles, common, output.resolve("test")))
//    }

//    generateCInteropMetadata()
}


fun generateCInteropMetadata() {
    val cinteropDirs = mutableListOf<File>().also {
        it.add(File("/home/aleks/Documents/work/kotlin-native/Interop/Runtime/src/main/kotlin/kotlinx/cinterop"))
        it.add(File("/home/aleks/Documents/work/kotlin-native/Interop/Runtime/src/native/kotlin/kotlinx/cinterop"))
    }

    val cinteropSourceFiles = cinteropDirs.flatMap { it.listFiles }.filter { it.name != "package-info.java" }
    val out = File("merged")

    out.resolve("log").printWriter().use {
        it.println(compileOnlyCommon(cinteropSourceFiles, out.resolve("com")))
    }

}