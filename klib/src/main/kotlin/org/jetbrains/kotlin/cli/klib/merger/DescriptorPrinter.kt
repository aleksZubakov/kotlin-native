package org.jetbrains.kotlin.cli.klib.merger

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.contracts.description.ContractProviderKey
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.descriptorUtils.hasPrimaryConstructor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.resolve.DataClassDescriptorResolver
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.descriptorUtil.secondaryConstructors
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isFlexible

fun printDescriptors(packageFqName: FqName, descriptors: List<DeclarationDescriptor>): String =
        buildDecompiledText(packageFqName, descriptors,
                DescriptorRenderer.withOptions {
                    classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
                    withDefinedIn = false
                    modifiers = DescriptorRendererModifier.ALL
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
                    annotationFilter = { false }
                    secondaryConstructorsAsPrimary = false
                })

val interfaceDescriptorRenderer = DescriptorRenderer.withOptions {
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

val DECOMPILED_COMMENT_FOR_PARAMETER = ""
val FLEXIBLE_TYPE_COMMENT = ""
val DECOMPILED_CONTRACT_STUB = ""
val DECOMPILED_CODE_COMMENT = "TODO()"
val TODO_CODE = "TODO()"


fun buildDecompiledText(
        packageFqName: FqName,
        descriptors: List<DeclarationDescriptor>,
        descriptorRenderer: DescriptorRenderer
//        indexers: Collection<DecompiledTextIndexer<*>> = listOf(ByDescriptorIndexer)
): String {
    val builder = StringBuilder()

    val charactersAllowedInKotlinStringLiterals: Set<Char> = mutableSetOf<Char>().apply {
        addAll('a' .. 'z')
        addAll('A' .. 'Z')
        addAll('0' .. '9')
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
        builder.append("// IntelliJ API Decompiler stub source generated from a class file\n" + "// Implementation of methods is not available")
        builder.append("\n\n")
        val suppress = mutableListOf("UNUSED_VARIABLE", "UNUSED_EXPRESSION").apply {
            if (true) {
                add("CONFLICTING_OVERLOADS")
                add("RETURN_TYPE_MISMATCH_ON_INHERITANCE")
                add("RETURN_TYPE_MISMATCH_ON_OVERRIDE")
                add("WRONG_MODIFIER_CONTAINING_DECLARATION") // For `final val` in interface.
                add("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
                add("UNUSED_PARAMETER") // For constructors.
                add("MANY_IMPL_MEMBER_NOT_IMPLEMENTED") // Workaround for multiple-inherited properties.
                add("MANY_INTERFACES_MEMBER_NOT_IMPLEMENTED") // Workaround for multiple-inherited properties.
                add("EXTENSION_SHADOWED_BY_MEMBER") // For Objective-C categories represented as extensions.
                add("REDUNDANT_NULLABLE") // This warning appears due to Obj-C typedef nullability incomplete support.
                add("DEPRECATION") // For uncheckedCast.
            }
        }

        builder.append("@file:Suppress(${suppress.joinToString { it.quoteAsKotlinLiteral() }})")
        if (!packageFqName.isRoot) {
            builder.append("package ").append(packageFqName).append("\n\n")
        }

        builder.append("import kotlinx.cinterop.* \n\n")

    }


//    val textIndex = DecompiledTextIndex(indexers)

//    fun indexDescriptor(descriptor: DeclarationDescriptor, startOffset: Int, endOffset: Int) {
//        textIndex.addToIndex(descriptor, TextRange(startOffset, endOffset))
//    }

    fun renderSuperTypeWithConstructorCall(supertype: KotlinType): String {
        val correspondingClassDescriptor = supertype.constructor.declarationDescriptor as ClassDescriptor

        return descriptorRenderer.renderType(supertype) +
                if (correspondingClassDescriptor.constructors.isEmpty()) {
                    "()"
                } else {
                    "(" + correspondingClassDescriptor.constructors.first().valueParameters.joinToString { "TODO()" } + ")"
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
                        renderSuperTypeWithConstructorCall(find)
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
            is KClassValue -> {
                var type = value.classId.asSingleFqName().asString()
                repeat(value.arrayDimensions) { type = "kotlin.Array<$type>" }
                "$type::class"
            }
            is LongValue -> "0L"
            is UIntValue, is ULongValue, is UShortValue, is UByteValue -> "0u"
            else -> value.toString()
        }
    }

    fun ClassDescriptor.enumEntryConstructors() = ((this as ClassDescriptor).typeConstructor.supertypes.first().constructor.declarationDescriptor as ClassDescriptor).constructors

    fun renderSecondaryConstructor(classDescriptor: ClassDescriptor, classConstructor: ClassConstructorDescriptor, indent: String) {
        builder.append(indent).append(descriptorRenderer.render(classConstructor))

        val superToCall = classDescriptor.typeConstructor.supertypes.find { it.constructor.declarationDescriptor is ClassDescriptor }
        if (superToCall != null) {
            builder.append(": super")
            val correspondingClassDescriptor = superToCall.constructor.declarationDescriptor as ClassDescriptor

            builder.append(if (correspondingClassDescriptor.constructors.isEmpty()) {
                "()"
            } else {
                "(" + correspondingClassDescriptor.constructors.first().valueParameters.joinToString { "TODO()" } + ")"
            })

        }
//        renderSupertypes(classDescriptor)
        builder.append("{ $TODO_CODE } \n")
    }

    fun appendDescriptor(descriptorRenderer: DescriptorRenderer, descriptor: DeclarationDescriptor, indent: String, lastEnumEntry: Boolean? = null) {
        val startOffset = builder.length
        if (DescriptorUtils.isEnumEntry(descriptor)) {
            for (annotation in descriptor.annotations) {
                builder.append(descriptorRenderer.renderAnnotation(annotation))
                builder.append(" ")
            }
            builder.append(descriptor.name.asString())

            descriptor as ClassDescriptor

            // TODO similar to supertypes constructors
            val constructors = descriptor.enumEntryConstructors()

            if (constructors.isEmpty()) {
                builder.append("()")
            } else {
                builder.append("(" + constructors.first().valueParameters.joinToString { "TODO()" } + ")")
            }

            builder.append(if (lastEnumEntry!!) ";" else ",")
        } else {
            val render = descriptorRenderer.render(descriptor)
            builder.append(render.replace("= ...", DECOMPILED_COMMENT_FOR_PARAMETER))

            if (descriptor is ClassDescriptor) {
                renderSupertypes(descriptor, descriptor.hasPrimaryConstructor())
            }

        }
        var endOffset = builder.length

        if (descriptor is CallableDescriptor) {
            //NOTE: assuming that only return types can be flexible
            if (descriptor.returnType!!.isFlexible()) {
                builder.append(" ").append(FLEXIBLE_TYPE_COMMENT)
            }
        }

        if (descriptor is FunctionDescriptor || descriptor is PropertyDescriptor) {
            if ((descriptor as MemberDescriptor).modality != Modality.ABSTRACT) {
                if (descriptor is FunctionDescriptor) {
                    with(builder) {
                        append(" { ")
                        if (descriptor.getUserData(ContractProviderKey)?.getContractDescription() != null) {
                            append(DECOMPILED_CONTRACT_STUB).append("; ")
                        }
                        append(DECOMPILED_CODE_COMMENT).append(" }")
                    }
                } else {
                    // descriptor instanceof PropertyDescriptor
                    descriptor as PropertyDescriptor
                    val containingDeclaration = descriptor.containingDeclaration
                    val renderInitializer = containingDeclaration !is ClassDescriptor || (containingDeclaration.kind != ClassKind.INTERFACE)
                    if (renderInitializer) {
                        builder.append(" = ")

                        descriptor.compileTimeInitializer?.let {
                            builder.append(renderConstant(it))
                        } ?: builder.append(TODO_CODE)
                    }

                }
                endOffset = builder.length
            }
        } else if (descriptor is ClassDescriptor && !DescriptorUtils.isEnumEntry(descriptor)) {
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


            val secondaryConstructors = if (!descriptor.hasPrimaryConstructor() && descriptor.secondaryConstructors.isNotEmpty()) {
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
                if (descriptor.kind == ClassKind.INTERFACE) {
                    appendDescriptor(interfaceDescriptorRenderer, member, subindent)
                } else {
                    appendDescriptor(descriptorRenderer, member, subindent)
                }

            }

            builder.append(indent).append("}")
            endOffset = builder.length
        }

        builder.append("\n")
//        indexDescriptor(descriptor, startOffset, endOffset)

//        if (descriptor is ClassDescriptor) {
//            val primaryConstructor = descriptor.unsubstitutedPrimaryConstructor
//            if (primaryConstructor != null) {
//                indexDescriptor(primaryConstructor, startOffset, endOffset)
//            }
//        }
    }

    appendDecompiledTextAndPackageName()
    for (member in descriptors) {
        appendDescriptor(descriptorRenderer, member, "")
        builder.append("\n")
    }

    return builder.toString()
}
