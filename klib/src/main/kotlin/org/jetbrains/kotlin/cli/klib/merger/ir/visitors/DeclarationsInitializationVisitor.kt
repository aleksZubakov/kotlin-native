package org.jetbrains.kotlin.cli.klib.merger.ir.visitors

import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.merger.descriptors.*
import org.jetbrains.kotlin.cli.klib.merger.ir.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.storage.LockBasedStorageManager

data class WeirdTriple<out T : DeclarationDescriptor>(
        val firstTargetDescriptor: T?,
        val commonTargetDescriptor: T?,
        val secondTargetDescriptor: T?
)

val descriptorFactory = MergerDescriptorFactory(DefaultBuiltIns.Instance, LockBasedStorageManager("TODO"))
fun ModuleDescriptorProperties?.createDescriptor() = this?.let {
    descriptorFactory.createModule(it)
}

private fun ModuleDescriptorImpl?.initialize(packageFragmentDescriptor: List<PackageFragmentDescriptor?>) =
        this?.initialize(descriptorFactory.createPackageFragmentProvider(packageFragmentDescriptor.filterNotNull()))

fun PackageDescriptorProperties?.createDescriptor(containingDeclarationDescriptor: DeclarationDescriptor?) = this?.let {
    if (containingDeclarationDescriptor !is ModuleDescriptorImpl) {
        error("ooops")
    }

    descriptorFactory.createPackageFragmentDescriptor(it, containingDeclarationDescriptor)
}

private fun MergerFragmentDescriptor?.initialize(descriptors: List<DeclarationDescriptor?>) =
        this?.initialize(descriptorFactory.createMemberScope(descriptors.filterNotNull()))


fun ClassDescriptorProperties?.createDescriptor(
        containingDeclarationDescriptor: DeclarationDescriptor?,
        isExpect: Boolean = false, isActual: Boolean = false) = this?.let {
    if (containingDeclarationDescriptor == null) {
        error("oops")
    }

    descriptorFactory.createClass(it, containingDeclarationDescriptor, isExpect = isExpect, isActual = isActual)
}

private fun MergedClassDescriptor?.initialize(descriptors: List<DeclarationDescriptor?>, properties: ClassDescriptorProperties?) {
    if (this == null) {
        return
    }

    properties?.let {
        val newConstructors = it.constructors.map {
            // TODO move this chunk into descriptor factory
            ClassConstructorDescriptorImpl.create(
                    this,
                    it.annotations,
                    it.isPrimary,
                    SourceElement.NO_SOURCE
            ).also { constr ->
                constr.initialize(
                        it.valueParameters,
                        it.visibility,
                        it.typeParameters
                )
            }
        }.toMutableSet()


        val primaryConstructor = newConstructors.find { it.isPrimary }/*?.also {
            newConstructors.remove(it)
        }*/


        // TODO move to descriptorFactory
        val memberScope = KlibMergerClassMemberScope(descriptors.filterNotNull(), descriptorFactory.storageManager, this)

        this.initialize(memberScope,
                newConstructors.toSet(),
                primaryConstructor)
    }
}

private fun FunctionDescriptorProperties?.createDescriptor(
        containingDeclarationDescriptor: DeclarationDescriptor?,
        isActual: Boolean = false, isExpect: Boolean = false) = this?.let {
    if (containingDeclarationDescriptor == null) {
        error("msg")
    }

    descriptorFactory.createFunction(
            oldFunctionDescriptor, containingDeclarationDescriptor, isExpect, isActual
    )
}

private fun PropertyDescriptorProperties?.createDescriptor(
        containingDeclarationDescriptor: DeclarationDescriptor?,
        isActual: Boolean = false, isExpect: Boolean = false
) = this?.let {
    if (containingDeclarationDescriptor == null) {
        error("oops")
    }

    descriptorFactory.createProperty(
            oldPropertyDescriptor,
            containingDeclarationDescriptor,
            isExpect,
            isActual
    ).also {
        // TODO check? update getter and setter too?
        it.initialize(
                oldPropertyDescriptor.getter as PropertyGetterDescriptorImpl?,
                oldPropertyDescriptor.setter,
                oldPropertyDescriptor.backingField,
                oldPropertyDescriptor.delegateField
        )

        it.setType(
                oldPropertyDescriptor.type,
                oldPropertyDescriptor.typeParameters,
                oldPropertyDescriptor.dispatchReceiverParameter,
                oldPropertyDescriptor.extensionReceiverParameter
        )

        it.setCompileTimeInitializer(descriptorFactory.storageManager.createNullableLazyValue { oldPropertyDescriptor.compileTimeInitializer })
    }

}

private fun CommonTypeAliasProperties.createDescriptor(
        containingDeclarationDescriptor: DeclarationDescriptor,
        isActual: Boolean = false, isExpect: Boolean = false): MergedClassDescriptor {
    val commonTypeAliasDescriptor = with(this) {
        val oldClassDescriptor = oldTypeAliasDescriptor.expandedType.constructor.declarationDescriptor!! as ClassDescriptor
        descriptorFactory.createEmptyClass(oldTypeAliasDescriptor, oldClassDescriptor, containingDeclarationDescriptor!!, supertypes, isExpect, isActual)
    }

    commonTypeAliasDescriptor.initialize(
            KlibMergerMemberScope(emptyList(), descriptorFactory.storageManager),
            emptySet(),
            null
    )
    return commonTypeAliasDescriptor
}


fun TypeAliasProperties?.createDescriptor(
        containingDeclarationDescriptor: DeclarationDescriptor?,
        isActual: Boolean = false, isExpect: Boolean = false) = this?.let {
    if (containingDeclarationDescriptor == null) {
        error("oops")
    }

    if (this is CommonTypeAliasProperties) {
        return@let createDescriptor(containingDeclarationDescriptor, isExpect = isExpect, isActual = isActual)
    }

    // TODO split initialization and descriptor creation
    descriptorFactory.createTypeAlias(it, containingDeclarationDescriptor, isExpect, isActual).apply {
        initialize(it.declaredTypeParameters, it.underlyingType, it.expandedType)
    }
}


class DeclarationsInitializationVisitor(builtIns: KotlinBuiltIns, val buildContext: ClassifiersBuildContext) : MergedIRVisitor<WeirdTriple<DeclarationDescriptor>, WeirdTriple<DeclarationDescriptor>> {
    override fun visitModuleNode(moduleNode: ModuleNode, data: WeirdTriple<DeclarationDescriptor>): WeirdTriple<ModuleDescriptorImpl> = with(moduleNode) {
        val name = firstTargetProperties?.name
                ?: commonTargetProperties?.name
                ?: secondTargetProperties?.name
                ?: error("One of them should be not null")


        val result = buildContext.modules[name]!!
        val members = moduleNode.members
                .map { it.accept(this@DeclarationsInitializationVisitor, result) }
                .map { it as WeirdTriple<PackageFragmentDescriptor> }

        with(result) {
            firstTargetDescriptor?.initialize(members.map { it.firstTargetDescriptor })
            commonTargetDescriptor?.initialize(members.map { it.commonTargetDescriptor })
            secondTargetDescriptor?.initialize(members.map { it.secondTargetDescriptor })
        }

        return result
    }

    override fun visitPackageNode(packageNode: PackageNode, data: WeirdTriple<DeclarationDescriptor>): WeirdTriple<PackageFragmentDescriptor> = with(packageNode) {
        val fqName = firstTargetProperties?.fqName
                ?: commonTargetProperties?.fqName
                ?: secondTargetProperties?.fqName
                ?: error("One of them should be not null")

        val result = buildContext.packages[fqName]!!
        val members = packageNode.members.map {
            it.accept(this@DeclarationsInitializationVisitor, result)
        }
        with(result) {
            firstTargetDescriptor?.initialize(members.map { it.firstTargetDescriptor })
            commonTargetDescriptor?.initialize(members.map { it.commonTargetDescriptor })
            secondTargetDescriptor?.initialize(members.map { it.secondTargetDescriptor })
        }

        return result
    }

    override fun visitClassNode(classNode: ClassNode, data: WeirdTriple<DeclarationDescriptor>): WeirdTriple<DeclarationDescriptor> = with(classNode) {
        val fqName = firstTargetProperties?.fqName
                ?: commonTargetProperties?.fqName
                ?: secondTargetProperties?.fqName
                ?: error("One of them should be not null")

        val result = buildContext.classes[fqName]!!
        val members = classNode.members.map {
            it.accept(this@DeclarationsInitializationVisitor, result)
        }

        with(result) {
            firstTargetDescriptor?.initialize(members.map { it.firstTargetDescriptor }, firstTargetProperties)
            commonTargetDescriptor?.initialize(members.map { it.commonTargetDescriptor }, secondTargetProperties)
            secondTargetDescriptor?.initialize(members.map { it.secondTargetDescriptor }, commonTargetProperties)
        }

        return result
    }


    override fun visitTypeAliasNode(typeAliasNode: TypeAliasNode, data: WeirdTriple<DeclarationDescriptor>): WeirdTriple<DeclarationDescriptor> = with(typeAliasNode) {
        val fqName = firstTargetProperties?.fqName
                ?: commonTargetProperties?.fqName
                ?: secondTargetProperties?.fqName
                ?: error("One of them should be not null")

        return buildContext.typeAliases[fqName]!!
    }

    override fun visitFunctionNode(functionNode: FunctionNode, data: WeirdTriple<DeclarationDescriptor>): WeirdTriple<DeclarationDescriptor> = with(functionNode) {
        var isExpect = false
        var isActual = false
        if (commonTargetProperties != null) {
            isExpect = true
            isActual = true
        }


        return WeirdTriple(
                firstTargetProperties.createDescriptor(data.firstTargetDescriptor, isActual = isActual),
                commonTargetProperties.createDescriptor(data.commonTargetDescriptor, isExpect = isExpect),
                secondTargetProperties.createDescriptor(data.secondTargetDescriptor, isActual = isActual)
        )
    }

    override fun visitPropertyNode(propertyNode: PropertyNode, data: WeirdTriple<DeclarationDescriptor>): WeirdTriple<DeclarationDescriptor> = with(propertyNode) {
        var isExpect = false
        var isActual = false
        if (commonTargetProperties != null) {
            isExpect = true
            isActual = true
        }

        return WeirdTriple(
                firstTargetProperties.createDescriptor(data.firstTargetDescriptor, isActual = isActual),
                commonTargetProperties.createDescriptor(data.commonTargetDescriptor, isExpect = isExpect),
                secondTargetProperties.createDescriptor(data.secondTargetDescriptor, isActual = isActual)
        )
    }

    override fun visitEmptyNode(emptyNode: EmptyNode, data: WeirdTriple<DeclarationDescriptor>): WeirdTriple<DeclarationDescriptor> {
        TODO("not implemented")
    }
}

