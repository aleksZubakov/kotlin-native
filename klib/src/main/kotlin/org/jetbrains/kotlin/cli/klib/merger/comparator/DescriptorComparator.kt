package org.jetbrains.kotlin.cli.klib.merger.comparator

import org.jetbrains.kotlin.backend.common.descriptors.explicitParameters
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.merger.ir.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

val KOTLINX_PACKAGE_NAME = Name.identifier("kotlinx")
fun isUnderKotlinOrKotlinxPackage(descriptor: DeclarationDescriptor): Boolean {
    var current: DeclarationDescriptor? = descriptor
    while (current != null) {
        if (current is PackageFragmentDescriptor) {
            return current.fqName.startsWith(KotlinBuiltIns.BUILT_INS_PACKAGE_NAME) ||
                    current.fqName.startsWith(KOTLINX_PACKAGE_NAME)
        }
        current = current.containingDeclaration
    }
    return false
}

class FullDescriptorComparator(storageManaber: StorageManager) {
    private val propertiesFactory = DescriptorPropertiesFactory()
    fun compareModuleDescriptors(o1: ModuleDescriptorImpl, o2: ModuleDescriptorImpl): ComparisonResult<ModuleDescriptorProperties> {
        if (o1.name != o2.name) {
            return Mismatched()
        }

        val firstTargetNewModule = propertiesFactory.createModuleProperties(o1)
        val commonTargetNewModule = propertiesFactory.createModuleProperties(o1)
        val secondTargetNewModule = propertiesFactory.createModuleProperties(o2)

        return CommonAndTargets(firstTargetNewModule, commonTargetNewModule, secondTargetNewModule)
    }

    fun comparePackageViewDescriptors(o1: PackageViewDescriptor, o2: PackageViewDescriptor): ComparisonResult<PackageDescriptorProperties> {
        if (o1.name != o2.name) {
            return Mismatched()
        }

        val firstTargetNewPackage = propertiesFactory.createPackageProperties(o1)
        val secondTargetNewPackage = propertiesFactory.createPackageProperties(o2)
        val commonTargetNewPackage = propertiesFactory.createPackageProperties(o1)

        return CommonAndTargets(firstTargetNewPackage, commonTargetNewPackage, secondTargetNewPackage)
    }

    fun comparePropertyDescriptors(o1: PropertyDescriptor, o2: PropertyDescriptor): ComparisonResult<PropertyDescriptorProperties> {
        // there is no possibility to create expect interface with non abstract member
        if (o1.isNotAbstractInInterface()
                || o2.isNotAbstractInInterface()
                || !compareTypes(o1.type, o2.type)) {
            return Mismatched()
        }

        if (o1.fqNameSafe != o2.fqNameSafe ||
                // expect property cannot be const because expect cannot have initializer
                // and const property must have
                o1.isConst || o2.isConst ||
                // expect property cannot be lateinit
                o1.isLateInit || o2.isLateInit ||
                // TODO is it check required?
                o1.isVar != o2.isVar) {
            return Mismatched()
        }

        val o1Setter = o1.setter
        val o2Setter = o2.setter
        if (haveDifferentNullability(o1Setter, o2Setter)) {
            return Mismatched()
        }

        if (o1Setter != null && o2Setter != null) {
            val equalSetters = o1Setter.modality == o2Setter.modality &&
                    compareVisibilities(o1Setter.visibility, o2Setter.visibility) &&
                    o1Setter.isInline == o2Setter.isInline

            if (!equalSetters) {
                return Mismatched()
            }
        }

        val o1Getter = o1.getter
        val o2Getter = o2.getter
        if (haveDifferentNullability(o1Getter, o2Getter)) {
            return Mismatched()
        }

        if (o1Getter != null && o2Getter != null) {
            val equalGetters = o1Getter.modality == o2Getter.modality &&
                    compareVisibilities(o1Getter.visibility, o2Getter.visibility) &&
                    o1Getter.isInline == o2Getter.isInline

            if (!equalGetters) {
                return Mismatched()
            }
        }

        val o1extensionReceiver = o1.extensionReceiverParameter
        val o2extensionReceiver = o2.extensionReceiverParameter

        if (haveDifferentNullability(o1extensionReceiver, o2extensionReceiver)) {
            return Mismatched()
        }

        if (o1extensionReceiver != null && o2extensionReceiver != null
                && !compareReceiverParameterDescriptors(o1extensionReceiver, o2extensionReceiver)) {
            return Mismatched()

        }

        val firstTargetProperty = propertiesFactory.createPropertyProperties(o1)
        val secondTargetProperty = propertiesFactory.createPropertyProperties(o2)
        val commonTargetProperty = propertiesFactory.createPropertyProperties(o2)

        return CommonAndTargets(firstTargetProperty, commonTargetProperty, secondTargetProperty)
    }

    fun compareFunctionDescriptors(o1: FunctionDescriptor, o2: FunctionDescriptor): ComparisonResult<FunctionDescriptorProperties> {
        if (o1.visibility == Visibilities.PRIVATE || o2.visibility == Visibilities.PRIVATE) {
            return Mismatched()
        }

        // there is no possibility to create expect interface with non abstract member
        if (o1.isNotAbstractInInterface()
                || o2.isNotAbstractInInterface()) {
            return Mismatched()
        }

        val simplePropertiesAreEqual = o1.fqNameSafe == o2.fqNameSafe &&
                o1.isSuspend == o2.isSuspend &&
                o1.explicitParameters.size == o2.explicitParameters.size &&
                compareTypes(o1.returnType!!, o2.returnType!!)

        if (!simplePropertiesAreEqual) {
            return Mismatched()
        }

        val parametersAreEqual = (o1.explicitParameters zip o2.explicitParameters).all { (a, b) ->
            compareValueDescriptors(a, b)
        }

        if (!parametersAreEqual) {
            return Mismatched()
        }

        val firstTargetFunction = propertiesFactory.createFunctionProperties(o1)
        val secondTargetFunction = propertiesFactory.createFunctionProperties(o2)
        val commonTargetFunction = propertiesFactory.createFunctionProperties(o2)

        return CommonAndTargets(firstTargetFunction, commonTargetFunction, secondTargetFunction)
    }

    fun compareClassDescriptors(o1: ClassDescriptor, o2: ClassDescriptor): ComparisonResult<ClassDescriptorProperties> {
        // TODO change merged class hierarchy and remove this cast
        val result = compareClassifiers(o1, o2) as ComparisonResult<ClassDescriptorProperties>
        if (result is CommonAndTargets<ClassDescriptorProperties>) {
            val (firstTargetDescriptor, commonDescriptor, secondTargetDescriptor) = result
            val o1Supertypes = o1.typeConstructor.supertypes
            firstTargetDescriptor.addSupertypes(o1Supertypes)

            val o2Supertypes = o2.typeConstructor.supertypes
            secondTargetDescriptor.addSupertypes(o2Supertypes)

            commonDescriptor.addSupertypes(commonTypes(o1Supertypes, o2Supertypes))
        }

        return result
    }

    fun compareTypeAliases(o1: TypeAliasDescriptor, o2: TypeAliasDescriptor): ComparisonResult<TypeAliasProperties> {
        // TODO change merged ir hierarchy and remove this cast
        val result = compareClassifiers(o1, o2) as ComparisonResult<TypeAliasProperties>

        if (result is CommonAndTargets<TypeAliasProperties>) {
            val (_, commonDescriptor, _) = result
            val firstTargetParents = (o1.underlyingType.constructor.declarationDescriptor!! as ClassDescriptor).typeConstructor.supertypes
            val secondTargetParents = (o2.underlyingType.constructor.declarationDescriptor!! as ClassDescriptor).typeConstructor.supertypes
            commonDescriptor.addSupertypes(commonTypes(firstTargetParents, secondTargetParents))
        }

        return result
    }


    private val compareClassifiersMemoizedFunc =
            storageManaber.createMemoizedFunction<Pair<ClassifierDescriptor, ClassifierDescriptor>, ComparisonResult<MergedDescriptor>> { (o1, o2) ->
                if (o1 is TypeAliasDescriptor && o2 is TypeAliasDescriptor) {
                    compareTypeAliasesInternal(o1, o2)
                } else if (o1 is ClassDescriptor && o2 is ClassDescriptor) {
                    compareClassDescriptorsInternal(o1, o2)
                } else {
                    Mismatched()
                }
            }

    private fun compareClassifiers(o1: ClassifierDescriptor, o2: ClassifierDescriptor): ComparisonResult<MergedDescriptor> =
            compareClassifiersMemoizedFunc(Pair(o1, o2))

    private fun compareTypeAliasesInternal(o1: TypeAliasDescriptor, o2: TypeAliasDescriptor): ComparisonResult<MergedDescriptor> {
        if (o1.fqNameSafe != o2.fqNameSafe) {
            return Mismatched()
        }

        val o1RightSide = o1.underlyingType.constructor.declarationDescriptor!!
        val o2RightSide = o2.underlyingType.constructor.declarationDescriptor!!

        if (o1RightSide !is ClassDescriptor || o2RightSide !is ClassDescriptor) {
            // right-hand side can only be another class-kind declaration, not type alias
            return Mismatched()
        }

        if (o1.underlyingType.arguments.isNotEmpty()
                || o2.underlyingType.arguments.isNotEmpty()) {
            // TODO something with situation when righ-hand side contains type arguments
            return Mismatched()
        }

        val descriptorsHaveSameKind = !o1RightSide.isInline && !o2RightSide.isInline &&
                o1RightSide.kind == o2RightSide.kind &&
                o1RightSide.modality == o2RightSide.modality &&
                compareVisibilities(o1RightSide.visibility, o2RightSide.visibility)

        if (!descriptorsHaveSameKind) {
            return Mismatched()
        }


        val newFirstTargetTypeAlias = propertiesFactory.createTypeAliasProperties(o1)
        val newSecondTargetTypeAlias = propertiesFactory.createTypeAliasProperties(o2)
        val commonTargetClassifier = propertiesFactory.createCommonTypeAliasProperties(o1)

        return CommonAndTargets(newFirstTargetTypeAlias, commonTargetClassifier, newSecondTargetTypeAlias)
    }

    private fun compareClassDescriptorsInternal(o1: ClassDescriptor, o2: ClassDescriptor): ComparisonResult<MergedDescriptor> {
        if (o1.fqNameSafe != o2.fqNameSafe
                || o1.kind != o2.kind) {
            // TODO visibility, modifier and etc.
            return Mismatched()
        }

        // always create commonized descriptors even if don't know if they can be commonized,
        // empty ones will be removed later
        val newFirstTargetDescriptor = propertiesFactory.createClassProperties(o1)
        val newSecondTargetDescriptor = propertiesFactory.createClassProperties(o2)
        val commonTargetDescriptor = propertiesFactory.createClassProperties(o2)

        return CommonAndTargets(newFirstTargetDescriptor, commonTargetDescriptor, newSecondTargetDescriptor)
    }

    private fun compareValueDescriptors(o1: ValueDescriptor, o2: ValueDescriptor): Boolean = when {
        o1 is ValueParameterDescriptor && o2 is ValueParameterDescriptor -> compareValueParameterDescriptors(o1, o2)
//        o1 is VariableDescriptor && o2 is VariableDescriptor -> compareVariableDescriptors(o1, o2)
        o1 is ReceiverParameterDescriptor && o2 is ReceiverParameterDescriptor -> compareReceiverParameterDescriptors(o1, o2)
        else -> false
    }

    private fun compareVariableDescriptors(o1: VariableDescriptor, o2: VariableDescriptor): Boolean {
        return o1.fqNameUnsafe == o2.fqNameUnsafe &&
                o1.isConst == o2.isConst &&
                o1.isVar == o2.isVar && // TODO
                o1.isLateInit == o2.isLateInit &&
                o1.isSuspend == o2.isSuspend &&
                compareTypes(o1.type, o2.type)
//                    && o1.returnType == o2.returnType
    }


    private fun compareValueParameterDescriptors(o1: ParameterDescriptor, o2: ParameterDescriptor): Boolean {
        val result = (o1.fqNameUnsafe == o2.fqNameUnsafe) &&
                compareTypes(o1.type, o2.type) &&
//                    && (o1.type == o2.type)
                (o1.isSuspend == o2.isSuspend) &&
                compareVisibilities(o1.visibility, o2.visibility)
        return result
    }


    private fun compareReceiverParameterDescriptors(o1: ReceiverParameterDescriptor, o2: ReceiverParameterDescriptor): Boolean {
        val a = o1.value.type.constructor.declarationDescriptor!!
        val b = o2.value.type.constructor.declarationDescriptor!!
        return compareClassifiers(a, b) !is Mismatched
    }

    private val KotlinType.declarationDescriptor: ClassifierDescriptor
        get() = if (this is AbbreviatedType) {
            this.abbreviation.constructor.declarationDescriptor!!
        } else {
            this.constructor.declarationDescriptor!!
        }

    private fun KotlinType.arguments() = if (this is AbbreviatedType) {
        this.abbreviation.arguments
    } else {
        this.arguments
    }

    private fun compareSimpleTypes(a: SimpleType, b: SimpleType): Boolean {
        if (a.arguments().size != b.arguments().size
                || a.isMarkedNullable != b.isMarkedNullable
                || haveDifferentNullability(a as? DefinitelyNotNullType, b as? DefinitelyNotNullType)
        ) {
            return false
        }

        val aDescriptor = a.declarationDescriptor
        val bDescriptor = b.declarationDescriptor

        if (isUnderKotlinOrKotlinxPackage(aDescriptor) || isUnderKotlinOrKotlinxPackage(bDescriptor)) {
            // compare builtins or kotlinx package
            if (a.constructor != b.constructor) {
                return false
            }
        } else if (compareClassifiers(aDescriptor, bDescriptor) is Mismatched) {
            return false
        }


        val aArguments = a.arguments()
        val bArgumenst = b.arguments()
        if (aArguments === bArgumenst) return true

        for (i in 0 until aArguments.size) {

            val aArg = aArguments[i]
            val bArg = bArgumenst[i]
            if (aArg.isStarProjection != bArg.isStarProjection) return false

            // both non-star
            if (!aArg.isStarProjection) {
                if (aArg.projectionKind != bArg.projectionKind) return false
                if (!compareTypes(aArg.type, bArg.type)) return false
            }
        }
        return true
    }

    private fun compareTypes(a: KotlinTypeMarker, b: KotlinTypeMarker): Boolean {
        if (a === b) return true

        val simpleA = a as? SimpleType
        val simpleB = b as? SimpleType
        if (simpleA != null && simpleB != null) return compareSimpleTypes(simpleA, simpleB)


        // TODO is it indeed required to compare flexible types?
        val flexibleA = a as? FlexibleType
        val flexibleB = b as? FlexibleType
        if (flexibleA != null && flexibleB != null) {
            return compareSimpleTypes(flexibleA.lowerBound, flexibleB.lowerBound) &&
                    compareSimpleTypes(flexibleA.upperBound, flexibleB.upperBound)
        }
        return false
    }

    private fun commonTypes(o1Types: Collection<KotlinType>, o2Types: Collection<KotlinType>): List<KotlinType> {
        val newFirstTargetParents = mutableListOf<KotlinType>()
        val newSecondTargetParents = mutableListOf<KotlinType>()
        loop@ for (ftParent in o1Types) {
            for (stParent in o2Types) {
                if (compareTypes(ftParent, stParent)) {
                    newFirstTargetParents.add(ftParent)
                    newSecondTargetParents.add(stParent)
                    continue@loop
                }
            }
        }

        return newFirstTargetParents

    }


    companion object {
        private fun <T> haveDifferentNullability(a: T?, b: T?): Boolean = !haveTheSameNullability(a, b)
        private fun <T> haveTheSameNullability(a: T?, b: T?): Boolean = (a != null) == (b != null)

        private fun compareVisibilities(v1: Visibility, v2: Visibility) =
                // TODO mb should not be just equal but compared with specific visibilities
                Visibilities.compare(v1, v2)?.let { it == 0 } ?: false

        private fun MemberDescriptor.isNotAbstractInInterface(): Boolean {
            val containingDeclaration = this.containingDeclaration
            if (containingDeclaration is ClassDescriptor
                    && containingDeclaration.kind == ClassKind.INTERFACE
                    && this.modality != Modality.ABSTRACT) {
                return true
            }

            return false
        }
    }
}