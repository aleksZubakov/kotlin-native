package org.jetbrains.kotlin.cli.klib.merger

import org.jetbrains.kotlin.cli.klib.defaultRepository
import org.jetbrains.kotlin.cli.klib.libraryInRepo
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

fun mergeLib(repository: File, platformsPaths: List<String>, whatToMerge: String): ModuleDescriptorImpl {
    val distribution = Distribution()
    val platformManager = PlatformManager(distribution)

    val librariesNames = platformsPaths.map { distribution.klib + it + whatToMerge }
    val libsExist = librariesNames.map(::File).all { it.exists }
    if (!libsExist) {
        TODO() // TODO
    }

    val output = File("merged/$whatToMerge")
    val klibBuilder = MultiTargetKlibWriter(output, repository, platformManager)

    val libraries = librariesNames.map {
        libraryInRepo(repository, it)
    }

    for (lib in libraries) {
        klibBuilder.addKonanLibrary(lib)
    }

    val klibMerger = KlibMergerFacade(repository, platformManager)
    val linkData = klibMerger.merge(libraries)

    return linkData
//    val properties = klibMerger.mergeProperties(libraries)
//
//    klibBuilder.addLinkData(linkData)
//    klibBuilder.addManifestAddend(properties)
//    klibBuilder.commit()

}

//fun merge(repository: File, platformsPaths: List<String>, whatToMerge: List<String>) {
//    for (what in whatToMerge) {
//        mergeLib(repository, platformsPaths, what)
//    }
//}


fun diff(repository: File, platformsPaths: List<String>, whatToMerge: List<String>): List<ModuleDescriptorImpl> {
    val distribution = Distribution()
    val platformManager = PlatformManager(distribution)

    val klibMerger = KlibMergerFacade(repository, platformManager)
    java.io.File("diffed_lin").mkdir()

    val total = mutableListOf<ModuleDescriptorImpl>()
    for (what in whatToMerge) {
        val librariesNames = platformsPaths.map { distribution.klib + it + what }


        val libsExist = librariesNames.map(::File).all { it.exists }
        if (!libsExist) {
            continue // TODO
        }

        val libraries = librariesNames.map {
            libraryInRepo(repository, it)
        }

        val output = "diffed_lin/$what"
        val a = klibMerger.diff(libraries)

        for ((l, targets) in a) {
            l.setDependencies(l)
            total.add(l)
        }
    }
    return total
}

fun printLib(mergeLib: ModuleDescriptorImpl, output: File): List<File> {
    val packages = mergeLib.getPackagesFqNames()
    val stdlibPackages = stdLibModule!!.getPackagesFqNames()

    val resultPackages = packages - stdlibPackages
    val map = resultPackages.map { it to mergeLib.getPackage(it).memberScope.getDescriptorsFiltered(
            DescriptorKindFilter(
                    DescriptorKindFilter.ALL_KINDS_MASK xor DescriptorKindFilter.PACKAGES_MASK
            )

    ) }

    val sourceFiles = mutableListOf<File>()
    for ((name, descriptors) in map) {
        val sourceCode = printDescriptors(name, descriptors.toList())
        val whereToPrint = output.resolve("$name.kt")
        whereToPrint.printWriter().use {
            it.println(sourceCode)
        }
        sourceFiles.add(whereToPrint)
    }

    return sourceFiles
}

fun main(args: Array<String>) {
    val whatToMerge = listOf(
//            "linux"
//            "posix"
//            "OpenGL3", "IOKit", /*"Metal",*/ "OpenGL",
//            "CoreFoundation", "CoreText", "objc", "Security",
//            /*"AppKit",*/ "DiskArbitration", "iconv", /*"QuartzCore",*/
//            "OpenGLCommon", "Foundation", "ImageIO", "CFNetwork",
            /*"CoreVideo", "CoreImage",*/ "darwin", /*"IOSurface",*/
//            "CoreGraphics", "ApplicationServices", "osx", "zlib",
            /*"Accelerate", "CoreML", "CoreData", */"posix"/*,*/
//            "GLUT", "CoreServices", "libkern"
    )

    val platformsPaths = listOf(
//            "/platform/ios_x64/",
//            "/platform/macos_x64/"
            "/platform/macos_x64/"/*,
//            "/platform/linux_x64/" //,
            "/platform/linux_mips3*/
//            "/platform/android_arm64/"
//            "/platform/linux_arm32_hfpStdlib"
    )

//    val message = File("/home/aleks/Documents/work/kotlin-native/dist/klib/platform/macos_x64").listFiles.map { it.name }
//
//    for ((ind, name) in message.withIndex()) {
//        if (ind % 4 == 3) {
//            println("\"$name\",")
//        } else {
//            print("\"$name\", ")
//        }
//    }
//    print(message)

    val repository = defaultRepository
    val output = File("merged")
    output.mkdirs()

    val mergedLibs = whatToMerge.map { mergeLib(repository, platformsPaths, it) }
    mergedLibs.forEach {it.setDependencies(mergedLibs + stdLibModule!!) }

    val sourceFiles = /*mergedLibs.flatMap { */printLib(mergedLibs.first(), output)/* }*/

    val native = listOf(
            "Nat1.kt"
    ).map { output.resolve(it) }

    val common = emptyList<File>()

    print(compileNative(sourceFiles, common, output.resolve("test")))
}
