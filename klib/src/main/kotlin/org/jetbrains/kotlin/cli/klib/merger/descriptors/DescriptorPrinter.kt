package org.jetbrains.kotlin.cli.klib.merger.descriptors

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.contracts.description.ContractProviderKey
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.js.descriptorUtils.hasPrimaryConstructor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.resolve.DataClassDescriptorResolver
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.descriptorUtil.secondaryConstructors
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isFlexible

data class WhatSuppress(
        val name: String,
        val receiverName: String?,
        val suppress: List<String>
) {
    constructor(name: String, receiverName: String?, suppress: String) : this(name, receiverName, listOf(suppress))
}

// TODO rename
private class SuppressGenerator(
        val classNameToMemberNames: Map<String, List<WhatSuppress>>,
        val topLevelMembers: List<WhatSuppress>
) {
    private fun Collection<WhatSuppress>.findSuppress(descriptor: CallableDescriptor): List<String> =
            this.filter { (name, receiverName, _) ->
                if (name != descriptor.name.toString()) {
                    return@filter false
                }
                val extenstionReceiverName = descriptor.extensionReceiverParameter?.type?.constructor?.declarationDescriptor?.name?.toString()
                if (receiverName != extenstionReceiverName) {
                    return@filter false
                }

                true
            }.flatMap { it.suppress }.distinct()

    fun generateSuppress(descriptor: DeclarationDescriptor): List<String> {
        val containingDeclaration = descriptor.containingDeclaration
        if (containingDeclaration is ClassDescriptor && descriptor is FunctionDescriptor) {
            return classNameToMemberNames[containingDeclaration.name.toString()]?.findSuppress(descriptor)
                    ?: emptyList()
        }

        if (descriptor is CallableDescriptor) {
            return topLevelMembers.findSuppress(descriptor)
        }

        return emptyList()
    }
}


private fun String.toName() = Name.identifier(this)
private fun createSupressGenerator(): SuppressGenerator {
    val overrideReturnType = "RETURN_TYPE_MISMATCH_ON_OVERRIDE"
    val overloadsConflict = "CONFLICTING_OVERLOADS"
    val nothingToOverride = "NOTHING_TO_OVERRIDE"
    val classNameToMemberNames = mutableListOf<Pair<String, WhatSuppress>>().apply {
        add("NSCalendar" to WhatSuppress("dateWithEra", null, overloadsConflict))
        add("NSCalendar" to WhatSuppress("getEra", null, overloadsConflict))
        add("NSDecimalNumber" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSDistantObject" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSFileCoordinator" to WhatSuppress("itemAtURL", null, overloadsConflict))
        add("NSFileManagerDelegateProtocol" to WhatSuppress("fileManager", null, overloadsConflict))
        add("NSFilePresenterProtocol" to WhatSuppress("presentedSubitemAtURL", null, overloadsConflict))
        add("NSFilePresenterProtocol" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSLogicalTest" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSNetServiceBrowserDelegateProtocol" to WhatSuppress("netServiceBrowser", null, overloadsConflict))
        add("NSNetServiceDelegateProtocol" to WhatSuppress("netService", null, overloadsConflict))
        add("NSNumber" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSOutputStream" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSSocketPort" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSSpellServerDelegateProtocol" to WhatSuppress("spellServer", null, overloadsConflict))
        add("NSURL" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSURLConnectionDelegateProtocol" to WhatSuppress("connection", null, overloadsConflict))
        add("NSURLDownloadDelegateProtocol" to WhatSuppress("download", null, overloadsConflict))
        add("NSURLProtectionSpace" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSURLProtocolClientProtocol" to WhatSuppress("URLProtocol", null, overloadsConflict))
        add("NSURLSessionStreamDelegateProtocol" to WhatSuppress("URLSession", null, overloadsConflict))
        add("NSUserNotificationCenterDelegateProtocol" to WhatSuppress("userNotificationCenter", null, overloadsConflict))
        add("NSUserNotificationCenterDelegateProtocol" to WhatSuppress("userNotificationCenter", null, overloadsConflict))
        add("NSXMLElement" to WhatSuppress("initWithName", null, overloadsConflict))
        add("NSXMLElement" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSXMLNodeMeta" to WhatSuppress("elementWithName", null, overloadsConflict))
        add("NSXMLParserDelegateProtocol" to WhatSuppress("parser", null, overloadsConflict))

        add("NSFileWrapper" to WhatSuppress("<init>", null, overloadsConflict))
        add("NSAppleEventDescriptor" to WhatSuppress("<init>", null, overloadsConflict))




        add("NSArray" to WhatSuppress("init", null, overrideReturnType))
        add("NSArray" to WhatSuppress("initWithCoder", null, overrideReturnType))
        add("NSArrayMeta" to WhatSuppress("alloc", null, overrideReturnType))
        add("NSArrayMeta" to WhatSuppress("allocWithZone", null, overrideReturnType))
        add("NSArrayMeta" to WhatSuppress("new", null, overrideReturnType))
        add("NSDictionary" to WhatSuppress("init", null, overrideReturnType))
        add("NSDictionary" to WhatSuppress("initWithCoder", null, overrideReturnType))
        add("NSDictionaryMeta" to WhatSuppress("alloc", null, overrideReturnType))
        add("NSDictionaryMeta" to WhatSuppress("allocWithZone", null, overrideReturnType))
        add("NSDictionaryMeta" to WhatSuppress("new", null, overrideReturnType))
        add("NSMutableDictionary" to WhatSuppress("init", null, overrideReturnType))
        add("NSMutableDictionary" to WhatSuppress("initWithCoder", null, overrideReturnType))
        add("NSMutableDictionary" to WhatSuppress("initWithObjects", null, overrideReturnType))
        add("NSMutableDictionaryMeta" to WhatSuppress("alloc", null, overrideReturnType))
        add("NSMutableDictionaryMeta" to WhatSuppress("allocWithZone", null, overrideReturnType))
        add("NSMutableDictionaryMeta" to WhatSuppress("new", null, overrideReturnType))
        add("NSMutableSet" to WhatSuppress("init", null, overrideReturnType))
        add("NSMutableSet" to WhatSuppress("initWithCoder", null, overrideReturnType))
        add("NSMutableSet" to WhatSuppress("initWithObjects", null, overrideReturnType))
        add("NSMutableSetMeta" to WhatSuppress("alloc", null, overrideReturnType))
        add("NSMutableSetMeta" to WhatSuppress("allocWithZone", null, overrideReturnType))
        add("NSMutableSetMeta" to WhatSuppress("new", null, overrideReturnType))
        add("NSSet" to WhatSuppress("init", null, overrideReturnType))
        add("NSSet" to WhatSuppress("initWithCoder", null, overrideReturnType))
        add("NSSetMeta" to WhatSuppress("alloc", null, overrideReturnType))
        add("NSSetMeta" to WhatSuppress("allocWithZone", null, overrideReturnType))
        add("NSSetMeta" to WhatSuppress("new", null, overrideReturnType))
        add("NSString" to WhatSuppress("init", null, overrideReturnType))
        add("NSString" to WhatSuppress("initWithCoder", null, overrideReturnType))
        add("NSStringMeta" to WhatSuppress("alloc", null, overrideReturnType))
        add("NSStringMeta" to WhatSuppress("allocWithZone", null, overrideReturnType))
        add("NSStringMeta" to WhatSuppress("new", null, overrideReturnType))



        //TODO remove this when kotlin type are normally updated
        add("NSProxy" to WhatSuppress("debugDescription", null, nothingToOverride))
        add("NSURLSessionTask" to WhatSuppress("debugDescription", null, nothingToOverride))
        add("NSXPCConnection" to WhatSuppress("synchronousRemoteObjectProxyWithErrorHandler", null, nothingToOverride))

        add("NSProxy" to WhatSuppress("debugDescription", null, overrideReturnType))
        add("Test" to WhatSuppress("<init>", null, overrideReturnType))

    }.groupBy(Pair<String, WhatSuppress>::first) { it.second }

    val topLevelFunctionsSuppress = mutableListOf<WhatSuppress>().apply {
        add(WhatSuppress("create", "ObjCClassOf", overloadsConflict))
        add(WhatSuppress("initWithCString", "NSString", overloadsConflict))
        add(WhatSuppress("setObject", "NSMutableOrderedSet", overloadsConflict))
        add(WhatSuppress("setValue", "NSObject", overloadsConflict))
        add(WhatSuppress("stringWithCString", "NSStringMeta", overloadsConflict))
        add(WhatSuppress("takeValue", "NSObject", overloadsConflict))
        add(WhatSuppress("validateValue", "NSObject", overloadsConflict))
    }

    return SuppressGenerator(classNameToMemberNames, topLevelFunctionsSuppress)
}

fun printDescriptors(packageFqName: FqName, descriptors: List<DeclarationDescriptor>): String =
        buildDecompiledText(packageFqName, descriptors,
                DescriptorRenderer.withOptions {
                    classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
                    withDefinedIn = false
                    modifiers = DescriptorRendererModifier.ALL
                    startFromName = false
                    startFromDeclarationKeyword = false
//                    classWithPrimaryConstructor = true
                    verbose = true
                    unitReturnType = false
                    enhancedTypes = false // TODO ???
                    withoutReturnType = false
                    normalizedVisibilities = true // TODO find out
                    renderDefaultVisibility = false
                    overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OPEN_OVERRIDE
                    textFormat = RenderingFormat.PLAIN
                    //                valueParametersHandler
                    withoutTypeParameters = false
                    receiverAfterName = false
                    renderCompanionObjectName = false
                    //                typeNormalizer
                    propertyAccessorRenderingPolicy = PropertyAccessorRenderingPolicy.PRETTY
                    alwaysRenderModifiers = true
                    renderConstructorKeyword = true
                    renderUnabbreviatedType = true
                    presentableUnresolvedTypes = true
//                    defaultParameterValueRenderer = {
//                    }
                    withoutSuperTypes = true
                    includePropertyConstant = false
                    annotationFilter = { false }
                    excludedAnnotationClasses += setOf(KotlinBuiltIns.FQ_NAMES.suppress)
                    secondaryConstructorsAsPrimary = false
                })

val rendererWithoutExpectActual = DescriptorRenderer.withOptions {
    classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
    withDefinedIn = false
    modifiers = DescriptorRendererModifier.ALL.toMutableSet().also {
        it.remove(DescriptorRendererModifier.EXPECT)
        it.remove(DescriptorRendererModifier.ACTUAL)
    }
    startFromName = false
    startFromDeclarationKeyword = false
//    classWithPrimaryConstructor = true
    verbose = true
    unitReturnType = false
    enhancedTypes = false // TODO ???
    withoutReturnType = false
    normalizedVisibilities = true // TODO find out
    renderDefaultVisibility = false
    overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OPEN_OVERRIDE
    textFormat = RenderingFormat.PLAIN
    //                valueParametersHandler
    withoutTypeParameters = false
    receiverAfterName = false
    renderCompanionObjectName = false
    //                typeNormalizer
    propertyAccessorRenderingPolicy = PropertyAccessorRenderingPolicy.PRETTY
    alwaysRenderModifiers = true
    renderConstructorKeyword = true
    renderUnabbreviatedType = true
    presentableUnresolvedTypes = true
//                    defaultParameterValueRenderer = {
//                    }
    withoutSuperTypes = true
    includePropertyConstant = false
    annotationFilter = { false }
    excludedAnnotationClasses += setOf(KotlinBuiltIns.FQ_NAMES.suppress)
    secondaryConstructorsAsPrimary = false
}

val interfaceDescriptorRenderer = DescriptorRenderer.withOptions {
    excludedAnnotationClasses += setOf(KotlinBuiltIns.FQ_NAMES.suppress)
    classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
    withDefinedIn = false
    modifiers = DescriptorRendererModifier.ALL - DescriptorRendererModifier.MODALITY - DescriptorRendererModifier.EXPECT
    startFromName = false
    startFromDeclarationKeyword = false
//    classWithPrimaryConstructor = true
    verbose = true
    unitReturnType = false
    enhancedTypes = false // TODO ???
    withoutReturnType = false
    normalizedVisibilities = true // TODO find out
    renderDefaultVisibility = false
    overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OPEN_OVERRIDE
    textFormat = RenderingFormat.PLAIN
    //                valueParametersHandler
    withoutTypeParameters = false
    receiverAfterName = true
    renderCompanionObjectName = false
    //                typeNormalizer
    propertyAccessorRenderingPolicy = PropertyAccessorRenderingPolicy.PRETTY
    alwaysRenderModifiers = true
    renderConstructorKeyword = true
    renderUnabbreviatedType = true
    presentableUnresolvedTypes = true
//                    defaultParameterValueRenderer = {
//                    }
    withoutSuperTypes = true
    includePropertyConstant = false
    annotationFilter = { false }
    secondaryConstructorsAsPrimary = true

}

val annotationRenderer = DescriptorRenderer.withOptions {
    excludedAnnotationClasses += setOf(KotlinBuiltIns.FQ_NAMES.suppress)
    classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
    withDefinedIn = false
    modifiers = DescriptorRendererModifier.ALL - DescriptorRendererModifier.MODALITY
    startFromName = false
    startFromDeclarationKeyword = false
    classWithPrimaryConstructor = true
    verbose = true
    unitReturnType = false
    enhancedTypes = false // TODO ???
    withoutReturnType = false
    normalizedVisibilities = true // TODO find out
    renderDefaultVisibility = false
    overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OPEN_OVERRIDE
    textFormat = RenderingFormat.PLAIN
    //                valueParametersHandler
    withoutTypeParameters = false
    receiverAfterName = false
    renderCompanionObjectName = false
    //                typeNormalizer
    propertyAccessorRenderingPolicy = PropertyAccessorRenderingPolicy.PRETTY
    alwaysRenderModifiers = true
    renderConstructorKeyword = true
    renderUnabbreviatedType = true
    presentableUnresolvedTypes = true
//                    defaultParameterValueRenderer = {
//                    }
    withoutSuperTypes = true
    includePropertyConstant = false
    annotationFilter = { true }
    secondaryConstructorsAsPrimary = false
    renderPrimaryConstructorParametersAsProperties = true
}

private val suppressGenerator = createSupressGenerator()

val DECOMPILED_COMMENT_FOR_PARAMETER = ""
val FLEXIBLE_TYPE_COMMENT = ""
val DECOMPILED_CONTRACT_STUB = ""
val DECOMPILED_CODE_COMMENT = "TODO()"
val TODO_CODE = "TODO()"

fun buildDecompiledText(
        packageFqName: FqName,
        descriptors: List<DeclarationDescriptor>,
        descriptorRenderer: DescriptorRenderer
): String {
    val builder = StringBuilder()

    val charactersAllowedInKotlinStringLiterals: Set<Char> = mutableSetOf<Char>().apply {
        addAll('a'..'z')
        addAll('A'..'Z')
        addAll('0'..'9')
        addAll(listOf('_', '@', ':', ';', '.', ',', '{', '}', '=', '[', ']', '^', '#', '*', ' '))
    }


    fun String.quoteAsKotlinLiteral(): String = buildString {
        append('"')

        this@quoteAsKotlinLiteral.forEach { c ->
            when (c) {
                in charactersAllowedInKotlinStringLiterals -> append(c)
                '$' -> append("\\$")
                else -> append("\\u" + "%04X".format(c.toInt()))
            }
        }

        append('"')
    }


    fun appendDecompiledTextAndPackageName() {
//        builder.append("// IntelliJ API Decompiler stub source generated from a class file\n" + "// Implementation of methods is not available")
//        builder.append("\n\n")
        val suppress = mutableListOf("UNUSED_VARIABLE", "UNUSED_EXPRESSION").apply {
            if (true) {
//                add("CONFLICTING_OVERLOADS")
//                add("RETURN_TYPE_MISMATCH_ON_INHERITANCE")
//                add("RETURN_TYPE_MISMATCH_ON_OVERRIDE")
//                add("WRONG_MODIFIER_CONTAINING_DECLARATION") // For `final val` in interface.
//                add("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
                add("UNUSED_PARAMETER") // For constructors.
//                add("MANY_IMPL_MEMBER_NOT_IMPLEMENTED") // Workaround for multiple-inherited properties.
//                add("MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED") // Workaround for multiple-inherited properties.
//                add("EXTENSION_SHADOWED_BY_MEMBER") // For Objective-C categories represented as extensions.
                add("REDUNDANT_NULLABLE") // This warning appears due to Obj-C typedef nullability incomplete support.
//                add("DEPRECATION") // For uncheckedCast.
            }
        }

        builder.append("@file:Suppress(${suppress.joinToString { it.quoteAsKotlinLiteral() }})\n\n")
        if (!packageFqName.isRoot) {
            builder.append("package ").append(packageFqName).append("\n\n")
        }

        builder.append("import kotlinx.cinterop.* \n\n")

    }

    fun renderSuperTypeWithConstructorCall(supertype: KotlinType, isExpect: Boolean = false): String {
        if (isExpect) {
            return descriptorRenderer.renderType(supertype)
        }

        val correspondingClassDescriptor = supertype.constructor.declarationDescriptor as ClassDescriptor

        return descriptorRenderer.renderType(supertype) +
                if (correspondingClassDescriptor.constructors.isEmpty()) {
                    "()"
                } else {
                    "(" + correspondingClassDescriptor.constructors.first().valueParameters.joinToString {
                        "TODO() as ${descriptorRenderer.renderType(it.type)}"
                    } + ")"
                }
    }

    fun renderSupertypes(klass: ClassDescriptor, callConstructors: Boolean = true) {
        if (KotlinBuiltIns.isNothing(klass.defaultType)) return

        var supertypes = klass.typeConstructor.supertypes
        if (supertypes.isEmpty() || supertypes.size == 1 && KotlinBuiltIns.isAnyOrNullableAny(supertypes.iterator().next())) return

        if (klass.kind == ClassKind.ENUM_CLASS) {
            supertypes = supertypes.filter {
                val parent = it.constructor.declarationDescriptor
                parent is ClassDescriptor && parent.kind == ClassKind.INTERFACE
            }

            if (supertypes.isNotEmpty()) {
                builder.append(": ")
                builder.append(supertypes.joinToString { descriptorRenderer.renderType(it) })
            }

            return
        }
        builder.append(": ")
        if (klass.kind != ClassKind.INTERFACE && callConstructors) {
            val find = supertypes.find { it.constructor.declarationDescriptor is ClassDescriptor }

            val firstConstructor =
                    if (find != null && klass.kind != ClassKind.INTERFACE) {
                        renderSuperTypeWithConstructorCall(find, klass.isExpect)
                    } else ""

            val map = supertypes.filter { it != find }.map { descriptorRenderer.renderType(it) } + firstConstructor
            builder.append(map.joinToString { it })
        } else {
            builder.append(supertypes.joinToString { descriptorRenderer.renderType(it) })
        }
    }

    fun renderConstant(value: ConstantValue<*>): String {
        return when (value) {
            is ArrayValue -> value.value.joinToString(", ", "{", "}") { renderConstant(it) }
            // TODO annotationValue
//            is AnnotationValue -> renderAnnotation(value.value).removePrefix("@")
            is KClassValue -> when (val classValue = value.value) {
                is KClassValue.Value.LocalClass -> "${classValue.type}::class"
                is KClassValue.Value.NormalClass -> {
                    var type = classValue.classId.asSingleFqName().asString()
                    repeat(classValue.arrayDimensions) { type = "kotlin.Array<$type>" }
                    "$type::class"
                }
            }
            is LongValue -> "0L"
            is UIntValue, is ULongValue, is UShortValue, is UByteValue -> "0u"
            is IntValue, is ByteValue, is ShortValue -> "0"
            else -> value.toString()
        }
    }

    fun ClassDescriptor.enumEntryConstructors() = ((this as ClassDescriptor).typeConstructor.supertypes.first().constructor.declarationDescriptor as ClassDescriptor).constructors

    fun renderSecondaryConstructor(classDescriptor: ClassDescriptor, classConstructor: ClassConstructorDescriptor, indent: String) {
        builder.append(indent)
        val suppress = suppressGenerator.generateSuppress(classConstructor)
        if (suppress.isNotEmpty()) {
            builder.append("@Suppress(${suppress.joinToString { it.quoteAsKotlinLiteral() }})")
        }
        builder.append(descriptorRenderer.render(classConstructor))

        // TODO add primary constructor call?
        val superToCall = classDescriptor.typeConstructor.supertypes.find { it.constructor.declarationDescriptor is ClassDescriptor }
        if (superToCall != null) {
            builder.append(": super")
            val correspondingClassDescriptor = superToCall.constructor.declarationDescriptor as ClassDescriptor

            builder.append(if (correspondingClassDescriptor.constructors.isEmpty()) {
                "()"
            } else {
                "(" + correspondingClassDescriptor.constructors.first().valueParameters.joinToString {
                    "TODO() as ${descriptorRenderer.renderType(it.type)}"
                } + ")"
            })

        }
        builder.append("{ $TODO_CODE } \n")
    }

    fun renderAnnotation(annotation: AnnotationDescriptor) {
        val annotationClass = annotation.type.constructor.declarationDescriptor as ClassDescriptor
        val annotationConstructor = annotationClass.constructors.singleOrNull()
        if (annotationConstructor == null) {
            builder.append(annotationRenderer.renderAnnotation(annotation))
            builder.append("()")
            return
        }

        // TODO name and more checks
        val isBuildable = annotationConstructor.valueParameters.all {
            KotlinBuiltIns.isString(it.type)
                    || KotlinBuiltIns.isInt(it.type)
                    || KotlinBuiltIns.isDouble(it.type)
                    || KotlinBuiltIns.isBoolean(it.type)
        }

        if (!isBuildable) {
//            builder.append("()")
            return
        }

        builder.append(annotationRenderer.renderAnnotation(annotation))
        builder.append("(")
        val arguments = annotationConstructor.valueParameters
                .filter { annotation.allValueArguments.containsKey(it.name) }
                .joinToString {
                    "${it.name} = ${annotation.allValueArguments[it.name]!!}"

//            val type = it.type
//            when {
//                KotlinBuiltIns.isString(type) -> "\"\""
//                KotlinBuiltIns.isInt(type) -> "0"
//                KotlinBuiltIns.isDouble(type) -> "0.0"
//                KotlinBuiltIns.isBoolean(type) -> "false"
//                else -> TODO("wtf")
//            }
                }
        builder.append(arguments)
        builder.append(")")
    }

    fun renderProperty(indent: String, descriptor: PropertyDescriptor) {
        val containingDeclaration = descriptor.containingDeclaration

        val renderInitializer = containingDeclaration !is ClassDescriptor || (containingDeclaration.kind != ClassKind.INTERFACE)
        if (renderInitializer && descriptor.compileTimeInitializer != null) {
            builder.append(" = ")
            builder.append(renderConstant(descriptor.compileTimeInitializer!!))
            return
        }

        if (descriptor.getter != null) {
            builder.append("\n${indent}    get() = TODO()")
        }

        if (descriptor.setter != null) {
            val setter = descriptor.setter!!
            val argumentName = setter.valueParameters.first().name.toString()
            builder.append("\n${indent}    set($argumentName: ${descriptorRenderer.renderType(descriptor.type)}) = TODO()")
        }
    }


    fun renderVisibility(visibility: Visibility) {
        builder.append(visibility.displayName).append(" ")
    }

    fun renderPrimaryConstructor(descriptorRenderer: DescriptorRenderer, konstructor: ClassConstructorDescriptor?, isExpect: Boolean = false) {
        // TODO rewrite
        // add annotations logic
        if (isExpect) {
            return
        }

        if (konstructor != null) {
            builder.append(" ")
            val suppress = suppressGenerator.generateSuppress(konstructor)
            if (suppress.isNotEmpty()) {
                builder.append("@Suppress(${suppress.joinToString { it.quoteAsKotlinLiteral() }})")
            }

            for (annotation in konstructor.annotations) {
                renderAnnotation(annotation)
            }
//            if (isActual) {
//                builder.append("actual ")
//            }
            renderVisibility(konstructor.visibility)
            builder.append("constructor")

            val params = descriptorRenderer.renderValueParameters(konstructor.valueParameters, konstructor.hasSynthesizedParameterNames())
            builder.append(params)
        }
    }

    fun appendDescriptor(descriptorRenderer: DescriptorRenderer, descriptor: DeclarationDescriptor, indent: String, lastEnumEntry: Boolean? = null) {
        val suppress = suppressGenerator.generateSuppress(descriptor)
        if (suppress.isNotEmpty()) {
            builder.append("@Suppress(${suppress.joinToString { it.quoteAsKotlinLiteral() }})")
        }

        if (DescriptorUtils.isEnumEntry(descriptor)) {
            for (annotation in descriptor.annotations) {
                builder.append(descriptorRenderer.renderAnnotation(annotation))
                builder.append(" ")
            }
            builder.append(descriptor.name.asString())

            descriptor as ClassDescriptor

            // TODO similar to supertypes constructors
            val constructors = descriptor.enumEntryConstructors()

            if (!descriptor.isExpect) {
                if (constructors.isEmpty()) {
                    builder.append("()")
                } else {
                    builder.append("(" + constructors.first().valueParameters.joinToString { "TODO()" } + ")")
                }
            }
            builder.append(if (lastEnumEntry!!) ";" else ",")
        } else {
            for (annotation in descriptor.annotations) {
                renderAnnotation(annotation)
            }


            // TODO remove
            val properRenderer = if (DescriptorUtils.isAnnotationClass(descriptor)) {
                annotationRenderer
            } else {
                descriptorRenderer
            }
            val render = properRenderer.render(descriptor)
            builder.append(render.replace("= ...", DECOMPILED_COMMENT_FOR_PARAMETER))

            if (descriptor is ClassDescriptor) {
                if (!descriptor.isCompanionObject && !DescriptorUtils.isAnnotationClass(descriptor)) {
                    renderPrimaryConstructor(properRenderer, descriptor.unsubstitutedPrimaryConstructor, descriptor.isExpect)
                }

                if (descriptor.kind != ClassKind.ANNOTATION_CLASS) {
                    renderSupertypes(descriptor, descriptor.hasPrimaryConstructor())
                }
            }
        }
        if (descriptor is CallableDescriptor) {
            // NOTE: assuming that only return types can be flexible
//            if (descriptor.returnType == null) {
//                println("I'm here")
//            }

            if (descriptor.returnType?.isFlexible() ?: false) {
                builder.append(" ").append(FLEXIBLE_TYPE_COMMENT)
            }
        }

        if (descriptor is FunctionDescriptor || descriptor is PropertyDescriptor) {
            if ((descriptor as MemberDescriptor).modality != Modality.ABSTRACT && !descriptor.isExternal && !descriptor.isExpect) {
                if (descriptor is FunctionDescriptor) {
                    with(builder) {
                        //                        if (!isInterface) { // TODO check by containingDeclaration, not by param
                        append(" { ")
                        if (descriptor.getUserData(ContractProviderKey)?.getContractDescription() != null) {
                            append(DECOMPILED_CONTRACT_STUB).append("; ")
                        }
                        append(DECOMPILED_CODE_COMMENT).append(" }")
//                        }
                    }
                } else {
                    // descriptor instanceof PropertyDescriptor
                    renderProperty(indent, descriptor as PropertyDescriptor)
                }
            }
        } else if (descriptor is ClassDescriptor && !DescriptorUtils.isEnumEntry(descriptor) && !DescriptorUtils.isAnnotationClass(descriptor)) {
            builder.append(" {\n")

            val subindent = indent + "    "

            var firstPassed = false
            fun newlineExceptFirst() {
                if (firstPassed) {
                    builder.append("\n")
                } else {
                    firstPassed = true
                }
            }

            val secondaryConstructors = if (descriptor.isExpect) {
                emptyList()
            } else if (!descriptor.hasPrimaryConstructor() && descriptor.secondaryConstructors.isNotEmpty()) {
                // TODO for all constructors, not in special case
                val first = descriptor.secondaryConstructors.first()
                renderSecondaryConstructor(descriptor, first, indent)
                descriptor.secondaryConstructors.filter { it != first }
            } else {
                descriptor.secondaryConstructors
            }

            val allDescriptors = secondaryConstructors + descriptor.defaultType.memberScope.getContributedDescriptors()
            val (enumEntries, members) = allDescriptors.partition(DescriptorUtils::isEnumEntry)

            for ((index, enumEntry) in enumEntries.withIndex()) {
                newlineExceptFirst()
                builder.append(subindent)
                appendDescriptor(descriptorRenderer, enumEntry, subindent, index == enumEntries.lastIndex)
            }

            val companionObject = descriptor.companionObjectDescriptor
            if (companionObject != null) {
                newlineExceptFirst()
                builder.append(subindent)
                appendDescriptor(descriptorRenderer, companionObject, subindent)
            }

            for (member in members) {
                if (member.containingDeclaration != descriptor) {
                    continue
                }
                if (member == companionObject) {
                    continue
                }
                if (member is CallableMemberDescriptor
                        && member.kind != CallableMemberDescriptor.Kind.DECLARATION
                        //TODO: not synthesized and component like
                        && !DataClassDescriptorResolver.isComponentLike(member.name)) {
                    continue
                }
                newlineExceptFirst()
                builder.append(subindent)
                // TODO inner interfaces
                if (descriptor.kind == ClassKind.INTERFACE) {
                    appendDescriptor(interfaceDescriptorRenderer, member, subindent)
                } else if (descriptor.isExpect) {
                    appendDescriptor(rendererWithoutExpectActual, member, subindent)
                } else {
                    appendDescriptor(descriptorRenderer, member, subindent)
                }

            }

            builder.append(indent).append("}")
        }

        builder.append("\n")
    }

    appendDecompiledTextAndPackageName()
    println(packageFqName)
    for (member in descriptors) {
        appendDescriptor(descriptorRenderer, member, "")
        builder.append("\n")
    }

    return builder.toString()
}