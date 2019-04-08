package org.jetbrains.kotlin.cli.klib.merger

import org.jetbrains.kotlin.cli.klib.libraryInRepo
import org.jetbrains.kotlin.cli.klib.merger.compilerrunner.compileNative
import org.jetbrains.kotlin.cli.klib.merger.compilerrunner.compileOnlyCommon
import org.jetbrains.kotlin.cli.klib.merger.descriptors.printDescriptors
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.library.impl.KonanLibraryImpl
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

fun File.resolve(path: String): File {
    // TODO make pr to kotlin native shared
    return File(java.io.File(this.path).resolve(path).absolutePath)
}

fun mergeLib(repository: File, compilerVersion: KonanVersion, platformsPaths: List<String>, whatToMerge: List<String>): Diff<ModuleDescriptorImpl> {
    val distribution = Distribution()
    val platformManager = PlatformManager(distribution)
    val libs = mutableListOf<List<KonanLibraryImpl>>()

    for (platform in platformsPaths) {
        val librariesNames = whatToMerge.map {
            distribution.klib + platform + it
        }

//        val librariesNames = platformsPaths.map { distribution.klib + it + whatToMerge }
//        val libsExist = librariesNames.map(::File).all { it.exists }
//        assert(libsExist) { "Cannot find libraries with given names in repo for $platform" }

        val libraries = librariesNames
                .map(::File)
                .filter { it.exists }
                .map { libraryInRepo(repository, it.absolutePath) }

        libs.add(libraries)
    }

    val (firstTarget, secondTarget) = libs
    val klibMergerFacade = KlibMergerFacade(repository, platformManager, compilerVersion)
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
//        add("CoreText")
//        add("iconv")
//        add("QuartzCore")
//        add("ImageIO")
//        add("CFNetwork")
//        add("CoreVideo")
//        add("CoreImage")
//        add("IOSurface")
//        add("CoreGraphics")
//        add("zlib")
//        add("Accelerate")
//        add("CoreML")
//        add("CoreData")

//        add("objc")
//
//        add("CoreServices")
//        add("CoreFoundation")
//        add("Security")
//        add("Foundation")
//        add("libkern")
//        add("IOKit")
//        add("osx")
//        add("DiskArbitration")
//        add("posix")
//        add("darwin")
//
        add("")
    }.toList()

    val platformsPaths = mutableListOf<String>().apply {
        add("/platform/kot-nat-1/")
        add("/platform/kot-nat-2/")
//        add("/platform/macos_x64/")
//        add("/platform/ios_x64/")
    }.toList()

    val version = KonanVersion.fromString("1.1.2-release-6625")
//    val version = KonanVersion.fromString("0.9.3-release-4223")

    val repository = File("/home/aleks/Documents/work/kotlin-native/dist/klib/platform")
    val output = File("merged-kot-nat-test2")
//    val output = File("merged3")
    output.mkdirs()


    val firstTargetKlib = output.resolve("first_target_klib")
    firstTargetKlib.mkdirs()
    val secondTargetKlib = output.resolve("second_target_klib")
    secondTargetKlib.mkdirs()


    val diff = mergeLib(repository, version, platformsPaths, whatToMerge)
//
    val commonSources = diff.commonModules.flatMap {
        printLib("<common3>", it, output)
    }
//
    val firstTargetSources = diff.firstTargetModules.flatMap {
        printLib("<firstTarget3>", it, output)
    }

    val secondTargetSources = diff.secondTargetModules.flatMap {
        printLib("<secondTarget3>", it, output)
    }
//    val sources = mutableListOf<String>().apply {
//        add("platform.CoreServices.kt")
//        add("platform.CoreFoundation.kt")
//        add("platform.CoreGraphics.kt")
//        add("platform.Security.kt")
//        add("platform.posix.kt")
//        add("platform.Foundation.kt")
//        add("platform.objc.kt")
//        add("platform.darwin.kt")
////        add("sample.kt")
////        add("test.kt")
////
//    }
//////
//    val commonSources = /*emptyList<File>()*/sources.filter { it != "platform.CoreGraphics.kt" }.map { "<common3>$it" }.map { output.resolve(it) }
//    val firstTargetSources = sources.map { "<firstTarget3>$it" }.map { output.resolve(it) }
//    val secondTargetSources = sources.map { "<secondTarget3>$it" }.map { output.resolve(it) }


    output.resolve("common_output.txt").printWriter().use {
        it.println(compileOnlyCommon(commonSources, output.resolve("common")))
    }

    val distribution = Distribution()
    val osxDependencies = listOf(
            "IOKit", "osx", "DiskArbitration"
    ).map { distribution.klib + "/platform/macos_x64/" + it }.map { File(it).absolutePath }

    output.resolve("first_target_output.txt").printWriter().use {
        it.print(compileNative(firstTargetSources, commonSources, firstTargetKlib/*, osxDependencies, repository*/))
    }

    output.resolve("second_target_output.txt").printWriter().use {
        it.print(compileNative(secondTargetSources, commonSources, secondTargetKlib))
    }


//    generateCInteropMetadata()
//    iosXMaco()
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

fun iosXmacos() {
    val macosLibsNames = File("/home/aleks/Documents/work/kotlin-native/dist/klib/platform/macos_x64").listFiles.map { it.name }
    val iosLibsNames = File("/home/aleks/Documents/work/kotlin-native/dist/klib/platform/ios_x64").listFiles.map { it.name }


    for (name in macosLibsNames intersect iosLibsNames) {
        println("add(\"$name\")")
    }
}

