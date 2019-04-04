package org.jetbrains.kotlin.cli.klib.merger.descriptors

import org.jetbrains.kotlin.backend.common.atMostOne
import org.jetbrains.kotlin.backend.konan.descriptors.enumEntries
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.merger.getPackagesFqNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.*
import org.jetbrains.kotlin.descriptors.konan.KonanModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.SyntheticModulesOrigin
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.ImplicitIntegerCoercion
import org.jetbrains.kotlin.resolve.NonReportingOverrideStrategy
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.utils.Printer
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class MergerFragmentDescriptor(moduleDescriptor: ModuleDescriptor,
                               fqName: FqName) : PackageFragmentDescriptorImpl(moduleDescriptor, fqName) {
    private lateinit var memberScope: KlibMergerMemberScope

    fun initialize(memberScope: KlibMergerMemberScope) {
        this.memberScope = memberScope
    }

    override fun getMemberScope(): MemberScope {
        return memberScope
    }
}

open class KlibMergerMemberScope(val members: List<DeclarationDescriptor>, storageManager: StorageManager) : MemberScopeImpl() {
    override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? =
            members.filterIsInstance<ClassifierDescriptor>()
                    .firstOrNull/*atMostOnce*/{ it.name == name }

    override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> =
            members.filterIsInstance<PropertyDescriptor>()
                    .filter { it.name == name }

    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> =
            members.filterIsInstance<SimpleFunctionDescriptor>()
                    .filter { it.name == name }

    override fun getContributedDescriptors(
            kindFilter: DescriptorKindFilter,
            nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> =
            members.filter { kindFilter.accepts(it) && nameFilter(it.name) }

    override fun printScopeStructure(p: Printer) = TODO("not implemented")
}

class KlibMergerClassMemberScope(members: List<DeclarationDescriptor>, storageManager: StorageManager, val classDescriptor: ClassDescriptor) : KlibMergerMemberScope(members, storageManager) {
    private fun <D : CallableMemberDescriptor> generateFakeOverrides(
            name: Name,
            fromSupertypes: Collection<D>,
            result: MutableCollection<D>
    ) {
        val fromCurrent = java.util.ArrayList<CallableMemberDescriptor>(result)
        OverridingUtil.generateOverridesInFunctionGroup(
                name,
                fromSupertypes,
                fromCurrent,
                classDescriptor,
                object : NonReportingOverrideStrategy() {
                    override fun addFakeOverride(fakeOverride: CallableMemberDescriptor) {
                        // TODO: report "cannot infer visibility"
                        OverridingUtil.resolveUnknownVisibilityForMember(fakeOverride, null)
                        @Suppress("UNCHECKED_CAST")
                        result.add(fakeOverride as D)
                    }

                    override fun conflict(
                            fromSuper: CallableMemberDescriptor,
                            fromCurrent: CallableMemberDescriptor
                    ) {
                        // TODO report conflicts
                    }
                })
    }

    private val functions = storageManager.createMemoizedFunction<Name, Collection<SimpleFunctionDescriptor>> { name ->
        val funcs = members.filterIsInstance<SimpleFunctionDescriptor>()
                .filter { it.name == name }.toMutableList()

        computeNonDeclaredFunctions(name, funcs)
        funcs
    }

    private val properties = storageManager.createMemoizedFunction<Name, Collection<PropertyDescriptor>>() { name ->
        val properties = members.filterIsInstance<PropertyDescriptor>()
                .filter { it.name == name }.toMutableList()

        computeNonDeclaredProperties(name, properties)
        properties
    }

    private inline fun <reified T : DeclarationDescriptor> membersNamesByType(): List<Name> = members.filterIsInstance<T>().map { it.name }.distinct()

    private fun computeAllDescriptors(lookupLocation: LookupLocation): List<DeclarationDescriptor> =
            membersNamesByType<SimpleFunctionDescriptor>().flatMap { getContributedFunctions(it, lookupLocation) } +
                    membersNamesByType<PropertyDescriptor>().flatMap { getContributedVariables(it, lookupLocation) } +
                    membersNamesByType<ClassifierDescriptor>().map { getContributedClassifier(it, lookupLocation) }.filterNotNull()

    private val allDescriptors = storageManager.createLazyValue { computeAllDescriptors(NoLookupLocation.FOR_ALREADY_TRACKED) }

    private fun computeNonDeclaredFunctions(name: Name, functions: MutableCollection<SimpleFunctionDescriptor>) {
        val fromSupertypes = ArrayList<SimpleFunctionDescriptor>()
        for (supertype in classDescriptor.typeConstructor.supertypes) {
            fromSupertypes.addAll(supertype.memberScope.getContributedFunctions(name, NoLookupLocation.FOR_ALREADY_TRACKED))
        }

        // TODO always can suppose that functions are available?
//        functions.retainAll {
//            c.components.platformDependentDeclarationFilter.isFunctionAvailable(this@DeserializedClassDescriptor, it)
//        }
        generateFakeOverrides(name, fromSupertypes, functions)
    }

    private fun computeNonDeclaredProperties(name: Name, properties: MutableCollection<PropertyDescriptor>) {
        val fromSupertypes = ArrayList<PropertyDescriptor>()
        for (supertype in classDescriptor.typeConstructor.supertypes) {
            fromSupertypes.addAll(supertype.memberScope.getContributedVariables(name, NoLookupLocation.FOR_ALREADY_TRACKED))
        }

        generateFakeOverrides(name, fromSupertypes, properties)
    }

    override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? =
            members.filterIsInstance<ClassifierDescriptor>()
                    .firstOrNull/*atMostOne*/ { it.name == name }

    override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> =
            properties(name)

    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> =
            functions(name)

    override fun getContributedDescriptors(
            kindFilter: DescriptorKindFilter,
            nameFilter: (Name) -> Boolean
    ): Collection<DeclarationDescriptor> =
            allDescriptors()

    override fun printScopeStructure(p: Printer) = TODO("not implemented")
}


class MergedSimpleFunctionDescriptor(
        containingDeclaration: DeclarationDescriptor,
        original: SimpleFunctionDescriptor?,
        annotations: Annotations,
        name: Name,
        kind: CallableMemberDescriptor.Kind,
        source: SourceElement? = null
) : CallableMemberDescriptor,
        SimpleFunctionDescriptorImpl(
                containingDeclaration, original, annotations, name, kind,
                source ?: SourceElement.NO_SOURCE
        ) {
}

class MergerTypeAliasDescriptor(
        override val storageManager: StorageManager,
        containingDeclaration: DeclarationDescriptor,
        annotations: Annotations,
        name: Name,
        visibility: Visibility,
        private var isExpect: Boolean,
        private var isActual: Boolean,
        private val isExternal: Boolean
) : AbstractTypeAliasDescriptor(containingDeclaration, annotations, name, SourceElement.NO_SOURCE, visibility) {
    override lateinit var constructors: Collection<TypeAliasConstructorDescriptor> private set

    override lateinit var underlyingType: SimpleType private set
    override lateinit var expandedType: SimpleType private set
    private lateinit var typeConstructorParameters: List<TypeParameterDescriptor>
    private lateinit var defaultTypeImpl: SimpleType
    override fun isActual(): Boolean = isActual

    override fun isExpect(): Boolean = isExpect

    override fun isExternal(): Boolean = isExternal

    fun initialize(
            declaredTypeParameters: List<TypeParameterDescriptor>,
            underlyingType: SimpleType,
            expandedType: SimpleType
    ) {

        initialize(declaredTypeParameters)
        this.underlyingType = underlyingType
        this.expandedType = expandedType
        typeConstructorParameters = computeConstructorTypeParameters()
        defaultTypeImpl = computeDefaultType()
        constructors = getTypeAliasConstructors()
    }

    override val classDescriptor: ClassDescriptor?
        get() = if (expandedType.isError) null else expandedType.constructor.declarationDescriptor as? ClassDescriptor

    override fun getDefaultType(): SimpleType = defaultTypeImpl

    override fun substitute(substitutor: TypeSubstitutor): TypeAliasDescriptor {
        if (substitutor.isEmpty) return this
        val substituted = MergerTypeAliasDescriptor(
                storageManager, containingDeclaration, annotations, name, visibility,
                isExpect, isActual, isExternal
        )
        substituted.initialize(
                declaredTypeParameters,
                substitutor.safeSubstitute(underlyingType, Variance.INVARIANT).asSimpleType(),
                substitutor.safeSubstitute(expandedType, Variance.INVARIANT).asSimpleType()
        )

        return substituted
    }

    override fun getTypeConstructorTypeParameters(): List<TypeParameterDescriptor> = typeConstructorParameters
}


// TODO drop
data class ModuleWithTargets(
        val module: ModuleDescriptorImpl,
        val targets: List<KonanTarget>
)


data class PackageWithTargets(
        val packageViewDescriptor: PackageViewDescriptor,
        val targets: List<KonanTarget>
)

fun ModuleDescriptorImpl.getPackageFragmentProviderForModuleContent(): PackageFragmentProvider? {
    val kotlin = this.javaClass.kotlin
    return kotlin.memberProperties.find { it.name == "packageFragmentProviderForModuleContent" }?.let {
        it.isAccessible = true
        return it.get(this) as PackageFragmentProvider?
    }

//    println("whooo")
}

fun getPackages(module: ModuleDescriptorImpl): List<PackageViewDescriptor> {
    // TODO add root package somewhere
    val allPackages = module.getPackagesFqNames()
    val filter = allPackages.map { module.getPackage(it) }.filter {
        it.fragments.all { packageFragmentDescriptor ->
            packageFragmentDescriptor.module == module || packageFragmentDescriptor.fqName != FqName.ROOT
        }
    }


    return filter
}

fun PackageViewDescriptor.getDescriptors(descriptorKindFilter: DescriptorKindFilter) =
        memberScope.getDescriptorsFiltered(descriptorKindFilter)

fun PackageViewDescriptor.getAllDescriptors() = memberScope.getDescriptorsFiltered { true }.filter {
    it !is PackageViewDescriptor // TODO
}


// TODO rename
class MergerDescriptorFactory(val builtIns: KotlinBuiltIns, val storageManager: StorageManager) {
    fun createPackageFragmentDescriptor(name: FqName, module: ModuleDescriptorImpl): MergerFragmentDescriptor {
        return MergerFragmentDescriptor(module, name)
    }

    fun createPackageFragmentProvider(packageFragmentDescriptors: List<PackageFragmentDescriptor>) =
            PackageFragmentProviderImpl(packageFragmentDescriptors)

    fun createMemberScope(descriptors: List<DeclarationDescriptor>): KlibMergerMemberScope =
            KlibMergerMemberScope(descriptors, storageManager)

    // TODO pass builtins as class property
    fun createModule(moduleName: Name): ModuleDescriptorImpl {
        val storageManager = LockBasedStorageManager("TODO")
        val origin = SyntheticModulesOrigin // TODO find out is it ok to use that origins

        return ModuleDescriptorImpl(
                moduleName,
                storageManager,
                builtIns,
                capabilities = mapOf(
                        KonanModuleOrigin.CAPABILITY to origin,
                        ImplicitIntegerCoercion.MODULE_CAPABILITY to false
                ))
    }

    fun createValueParameters(oldValueParameterDescriptor: ValueParameterDescriptor) {

        KotlinBuiltIns.isBuiltIn(oldValueParameterDescriptor)
    }

    fun createFunction(functionDescriptor: CallableMemberDescriptor,
                       containingDeclaration: DeclarationDescriptor,
                       isExpect: Boolean = false,
                       isActual: Boolean = false): SimpleFunctionDescriptorImpl {
        return SimpleFunctionDescriptorImpl.create(
                containingDeclaration,
                functionDescriptor.annotations, // TODO resolve instead of moving from passed decriptor
                functionDescriptor.name,
                functionDescriptor.kind,
                SourceElement.NO_SOURCE/*,
            null // TODO find out is it ok to use null as original declaration always*/
        ).also {
            it.isExpect = isExpect
            it.isActual = isActual
            it.isExternal = functionDescriptor.isExternal
            functionDescriptor.returnType

            it.initialize(
                    functionDescriptor.extensionReceiverParameter,
                    functionDescriptor.dispatchReceiverParameter,
                    functionDescriptor.typeParameters,
                    functionDescriptor.valueParameters,
                    functionDescriptor.returnType,
                    functionDescriptor.modality,
                    functionDescriptor.visibility
            )
        }
    }


    fun createClass(oldClassDescriptor: ClassDescriptor,
                    containingDeclaration: DeclarationDescriptor,
                    supertypes: Collection<KotlinType>,
            /*containingDeclaration: DeclarationDescriptor,*/
                    isExpect: Boolean = false,
                    isActual: Boolean = false): MergedClassDescriptor {
        assert(!(isExpect && isActual))

        return MergedClassDescriptor(storageManager,
                oldClassDescriptor.name,
                containingDeclaration,
                oldClassDescriptor.isExternal,
                oldClassDescriptor.modality,
                oldClassDescriptor.kind,
                oldClassDescriptor.isInline,
                oldClassDescriptor.visibility,
                isExpect,
                isActual,
                oldClassDescriptor.isData,
                oldClassDescriptor.isCompanionObject,
                oldClassDescriptor.isInner,
                supertypes

        )
    }

    fun createEmptyClass(oldTypeAliasDescriptor: TypeAliasDescriptor,
                         oldClassDescriptor: ClassDescriptor,
                         containingDeclaration: DeclarationDescriptor,
                         supertypes: Collection<KotlinType>,
                         isExpect: Boolean = false,
                         isActual: Boolean = false): MergedClassDescriptor {
        assert(!(isExpect && isActual))

        return MergedClassDescriptor(
                storageManager,
                oldTypeAliasDescriptor.name,
                containingDeclaration,
                oldClassDescriptor.isExternal, // TODO ??
                oldClassDescriptor.modality,
                oldClassDescriptor.kind,
                false, // TODO ??
                oldClassDescriptor.visibility,
                isExpect,
                isActual,
                false, // TODO ??
                false, // TODO ??
                false, // TODO ??
                supertypes
        )


    }

    fun createProperty(propertyDescriptor: PropertyDescriptor,
                       containingDeclaration: DeclarationDescriptor,
                       isExpect: Boolean = false,
                       isActual: Boolean = false): PropertyDescriptorImpl {
        return PropertyDescriptorImpl.create(
                containingDeclaration,
                propertyDescriptor.annotations,
                propertyDescriptor.modality,
                propertyDescriptor.visibility,
                propertyDescriptor.isVar,
                propertyDescriptor.name,
                propertyDescriptor.kind,
                propertyDescriptor.source,
                propertyDescriptor.isLateInit,
                propertyDescriptor.isConst,
                isExpect,
                isActual,
                propertyDescriptor.isExternal,
                false // TODO
        )
    }

    fun createTypeAlias(typeAliasDescriptor: TypeAliasDescriptor,
                        containingDeclaration: DeclarationDescriptor,
                        isExpect: Boolean,
                        isActual: Boolean): MergerTypeAliasDescriptor {

        return with(typeAliasDescriptor) {
            MergerTypeAliasDescriptor(
                    storageManager, containingDeclaration, annotations,
                    name, visibility, isExpect, isActual, isExternal
            )
        }

    }

//    fun fromDescriptor(oldDeclarationDescriptor: DeclarationDescriptor,
//                       containingDeclaration: DeclarationDescriptor,
//                       isExpect: Boolean,
//                       isActual: Boolean): DeclarationDescriptor? {
//        return when (oldDeclarationDescriptor) {
//            is ClassDescriptor -> createClass(oldDeclarationDescriptor, containingDeclaration, emptySet(), isExpect, isActual)
//            is FunctionDescriptor -> createFunction(oldDeclarationDescriptor, containingDeclaration, isExpect, isActual)
//            is PropertyDescriptor -> createProperty(oldDeclarationDescriptor, containingDeclaration, isExpect, isActual)
////            is TypeAliasDescriptor -> createTypeAlias(oldDeclarationDescriptor, containingDeclaration, isExpect, isActual)
//            // TODO all other types of descriptors
//            else -> null
//        }
//    }

}