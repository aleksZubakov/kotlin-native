package org.jetbrains.kotlin.cli.klib.merger.ir

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe


class DescriptorPropertiesFactory {
    fun createModuleProperties(moduleDescriptorImpl: ModuleDescriptorImpl) = with(moduleDescriptorImpl) {
        ModuleDescriptorProperties(name, stableName)
    }

    fun createPackageProperties(packageViewDescriptor: PackageViewDescriptor) = with(packageViewDescriptor) {
        PackageDescriptorProperties(fqName)
    }

    fun createClassProperties(classDescriptor: ClassDescriptor) = with(classDescriptor) {
        ClassDescriptorProperties(
                fqNameSafe,
                name,
                isExternal,
                modality,
                visibility,
                kind,
                isInline,
                isData,
                isCompanionObject,
                isInner,
                // TODO remove from here becase expect classes should not contain constructors
                constructors.toSet()
        )
    }

    fun createTypeAliasProperties(typeAliasDescriptor: TypeAliasDescriptor) = with(typeAliasDescriptor) {
        TypeAliasProperties(annotations, fqNameSafe, name,
                declaredTypeParameters, underlyingType, expandedType, visibility, isExternal)
    }

    fun createCommonTypeAliasProperties(typeAliasDescriptor: TypeAliasDescriptor) = with(typeAliasDescriptor) {
        val declaration = typeAliasDescriptor.underlyingType.constructor.declarationDescriptor!!
        if (declaration !is ClassDescriptor) {
            error("Common type alias properties cannot be created for typealias if its right-hand side " +
                    "is another typealias")
        }

        CommonTypeAliasProperties(
                annotations, fqNameSafe, name, declaredTypeParameters, underlyingType, expandedType,
                visibility, isExternal, modality, declaration.kind)
    }

    fun createPropertyProperties(propertyDeclarationDescriptor: PropertyDescriptor) = with(propertyDeclarationDescriptor) {
        PropertyDescriptorProperties(annotations, modality, visibility, isVar, name,
                kind, isLateInit, isConst, isExternal)
    }

    fun createFunctionProperties(functionDeclarationDescriptor: FunctionDescriptor) = with(functionDeclarationDescriptor) {
        FunctionDescriptorProperties(
                annotations, name, kind, isExternal, extensionReceiverParameter,
                dispatchReceiverParameter, typeParameters, valueParameters,
                returnType, modality, visibility
        )
    }
}
