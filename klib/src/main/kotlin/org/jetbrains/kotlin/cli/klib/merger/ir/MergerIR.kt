package org.jetbrains.kotlin.cli.klib.merger.ir

import org.jetbrains.kotlin.backend.konan.descriptors.getPackageFragments
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.merger.descriptors.KlibMergerClassMemberScope
import org.jetbrains.kotlin.cli.klib.merger.descriptors.KlibMergerMemberScope
import org.jetbrains.kotlin.cli.klib.merger.descriptors.MergerDescriptorFactory
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperclassesWithoutAny
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.storage.LockBasedStorageManager

interface MergerIR {
    // TODO
    // fun commitDescriptor(): DeclarationDescriptor
    fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): DeclarationDescriptor
}

class MergedModule(val oldModuleDescriptorImpl: ModuleDescriptorImpl) : MergerIR {
    val packages: MutableList<MergedPackage> = mutableListOf()

    fun expand(newPackages: List<MergedPackage>) {
        packages.addAll(newPackages)
    }

    override fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): ModuleDescriptorImpl {
        return visitor.visitModule(this, null)
    }
}

class MergedPackage(val oldPackageDescriptor: DeclarationDescriptor) : MergerIR {
    val descriptors: MutableList<MergedDescriptor> = mutableListOf()

    fun expand(newDescriptors: List<MergedDescriptor>) {
        descriptors.addAll(newDescriptors)
    }

    override fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): PackageFragmentDescriptor {
        return visitor.visitPackage(this, containingDeclarationDescriptor)
    }
}

sealed class MergedDescriptor(val isExpect: Boolean, val isActual: Boolean) : MergerIR {
    init {
        assert(!(isActual && isExpect)) {
            "Descriptor cannot be actual and expect synchronously"
        }
    }
}

class MergedClass(val oldClassDescriptor: ClassDescriptor,
                  isExpect: Boolean = false,
                  isActual: Boolean = false) : MergedDescriptor(isExpect, isActual) {
    val desciptors: MutableList<MergedDescriptor> = mutableListOf()

    fun expand(newDescriptors: List<MergedDescriptor>) {
        desciptors.addAll(newDescriptors)
    }

    override fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): DeclarationDescriptor =
            visitor.visitClass(this, containingDeclarationDescriptor)
}

class MergedFunction(
        val oldFunctionDescriptor: FunctionDescriptor,
        isExpect: Boolean = false,
        isActual: Boolean = false
) : MergedDescriptor(isExpect, isActual) {
    override fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): DeclarationDescriptor =
            visitor.visitFunction(this, containingDeclarationDescriptor)
}

class MergedValue(
        val oldValueDescriptor: ValueDescriptor,
        isExpect: Boolean = false,
        isActual: Boolean = false
) : MergedDescriptor(isExpect, isActual) {
    override fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): DeclarationDescriptor =
            visitor.visitValue(this, containingDeclarationDescriptor)
}

class MergedProperty(
        val oldPropertyDescriptor: PropertyDescriptor,
        isExpect: Boolean = false,
        isActual: Boolean = false
) : MergedDescriptor(isExpect, isActual) {
    override fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): PropertyDescriptor =
            visitor.visitPropertyDescriptor(this, containingDeclarationDescriptor)
}

class MergedTypeAlias(val oldTypeAliasDescriptor: TypeAliasDescriptor,
                      isExpect: Boolean = false,
                      isActual: Boolean = false) : MergedDescriptor(isExpect, isActual) {
    override fun accept(visitor: MergedIRToDescriptorsVisitor, containingDeclarationDescriptor: DeclarationDescriptor?): DeclarationDescriptor {
        return visitor.visitTypeAlias(this, containingDeclarationDescriptor)
    }
}

fun DeclarationDescriptor.toMergedDescriptor(isExpect: Boolean = false, isActual: Boolean = false): MergedDescriptor {
    return when (this) {
        is ClassDescriptor -> MergedClass(this, isExpect, isActual)
        is FunctionDescriptor -> MergedFunction(this, isExpect, isActual)
        is PropertyDescriptor -> MergedProperty(this, isExpect, isActual)
        is TypeAliasDescriptor -> MergedTypeAlias(this, isExpect, isActual)
//        is ValueDescriptor -> MergedValue(this, isExpect, isActual) ! TODO !
        else -> {
            TODO(":(")
        }
    }
}

fun DeclarationDescriptor.toMergedIR(isExpect: Boolean = false, isActual: Boolean = false): MergerIR {
    return this.accept(DeclarationDescriptorToMergedIRVisitor(isExpect, isActual), Unit)
}


class MergedIRToDescriptorsVisitor(builtIns: KotlinBuiltIns) {
    val descriptorFactory = MergerDescriptorFactory(builtIns, LockBasedStorageManager("TODO"))
    fun visitModule(mergedModule: MergedModule, containingDeclarationDescriptor: DeclarationDescriptor?): ModuleDescriptorImpl {
        val moduleDescriptorImpl = descriptorFactory.createModule(
                mergedModule.oldModuleDescriptorImpl.name
        )

        val packageFragmentDescriptors = mergedModule.packages.map { it.accept(this, moduleDescriptorImpl) }
        moduleDescriptorImpl.initialize(descriptorFactory.createPackageFragmentProvider(packageFragmentDescriptors))

        return moduleDescriptorImpl
    }

    fun visitPackage(mergedPackage: MergedPackage, containingDeclarationDescriptor: DeclarationDescriptor?): PackageFragmentDescriptor {
        if (containingDeclarationDescriptor !is ModuleDescriptorImpl) {
            TODO("whoop")
        }

        val packageFragmentDescriptor = descriptorFactory.createPackageFragmentDescriptor(
                mergedPackage.oldPackageDescriptor.fqNameSafe,
                containingDeclarationDescriptor
        )
        val descriptors = mergedPackage.descriptors.map { it.accept(this, packageFragmentDescriptor) }

        packageFragmentDescriptor.initialize(descriptorFactory.createMemberScope(descriptors))
        return packageFragmentDescriptor
    }

    fun visitClass(mergedClass: MergedClass, containingDeclarationDescriptor: DeclarationDescriptor?): ClassDescriptor {
        if (containingDeclarationDescriptor == null) {
            TODO("whoop")
        }


        val classDescriptor = with(mergedClass) {
            // TODO update supertypes KotlinTypes?
            descriptorFactory.createClass(oldClassDescriptor, containingDeclarationDescriptor, oldClassDescriptor.typeConstructor.supertypes, isExpect, isActual)
        }

        val newConstructors = mergedClass.oldClassDescriptor.constructors.map {
            // TODO move this chunk into descriptor factory
            ClassConstructorDescriptorImpl.create(
                    classDescriptor,
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

        val members = mergedClass.desciptors.map { it.accept(this, classDescriptor) }
        // TODO create constructors/*.commonModules*/


        // TODO move to descriptorFactory
        val memberScope = KlibMergerClassMemberScope(members, descriptorFactory.storageManager, classDescriptor)

        classDescriptor.initialize(memberScope,
                newConstructors.toSet(),
                primaryConstructor)

        return classDescriptor
    }

    fun visitFunction(mergedFunction: MergedFunction, containingDeclarationDescriptor: DeclarationDescriptor?): FunctionDescriptor {
        if (containingDeclarationDescriptor == null) {
            TODO("whoooop")
        }

        return with(mergedFunction) {
            descriptorFactory.createFunction(
                    oldFunctionDescriptor, containingDeclarationDescriptor, isExpect, isActual
            )
        }
    }

    fun visitPropertyDescriptor(mergedDescriptor: MergedProperty, containingDeclarationDescriptor: DeclarationDescriptor?): PropertyDescriptor {
        if (containingDeclarationDescriptor == null) {
            TODO("whoooop")
        }

        return with(mergedDescriptor) {
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
    }

    fun visitValue(mergedValue: MergedValue, containingDeclarationDescriptor: DeclarationDescriptor?): ValueDescriptor {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun visitTypeAlias(mergedValue: MergedTypeAlias, containingDeclarationDescriptor: DeclarationDescriptor?): TypeAliasDescriptor {
        if (containingDeclarationDescriptor == null) {
            TODO(":c")
        }

        val typeAliasDescriptor = with(mergedValue) {
            descriptorFactory.createTypeAlias(oldTypeAliasDescriptor, containingDeclarationDescriptor, isExpect, isActual).also {
                it.initialize(oldTypeAliasDescriptor.declaredTypeParameters, oldTypeAliasDescriptor.underlyingType, oldTypeAliasDescriptor.expandedType)
            }
        }

        return typeAliasDescriptor
    }
}

private class DeclarationDescriptorToMergedIRVisitor(val isExpect: Boolean = false, val isActual: Boolean = false) : DeclarationDescriptorVisitorEmptyBodies<MergerIR, Unit>() {
//    override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor?, data: Unit?): MergerIR {
//        return super.visitPropertySetterDescriptor(descriptor, data)
//    }
//
//    override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor?, data: Unit?): MergerIR {
//        return super.visitConstructorDescriptor(constructorDescriptor, data)
//    }
//
//    override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor?, data: Unit?): MergerIR {
//        return super.visitReceiverParameterDescriptor(descriptor, data)
//    }

//    override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor?, data: Unit?): MergerIR {
//        return super.visitPackageViewDescriptor(descriptor, data)
//    }

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: Unit): MergedFunction =
            MergedFunction(descriptor, isExpect, isActual)

    override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: Unit): MergedModule {
        descriptor as ModuleDescriptorImpl
        val map = descriptor.getPackageFragments().map {
            it.accept(this, data) as MergedPackage
        }

        return MergedModule(descriptor).also {
            it.expand(map)
        }
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor, data: Unit): MergedClass {
        val members = descriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }.map {
            it.accept(this, data) as MergedDescriptor
        }

        return MergedClass(
                descriptor,
                isExpect,
                isActual
        ).also {
            it.expand(members)
        }
    }

    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, data: Unit): MergedPackage {
        val descriptors = descriptor.getMemberScope().getDescriptorsFiltered { true }.map {
            it.accept(this, data) as MergedDescriptor
        }

        return MergedPackage(descriptor).also {
            it.expand(descriptors)
        }
    }

//    override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor?, data: Unit?): MergerIR {
//        return super.visitValueParameterDescriptor(descriptor, data)
//    }
//
//    override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor?, data: Unit?): MergerIR {
//        return super.visitTypeParameterDescriptor(descriptor, data)
//    }
//
//    override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor?, data: Unit?): MergerIR {
//        return super.visitScriptDescriptor(scriptDescriptor, data)
//    }

    override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: Unit?): MergerIR {
        // TODO()
        return MergedTypeAlias(descriptor, isExpect, isActual)
    }

//    override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor?, data: Unit?): MergerIR {
//        return super.visitPropertyGetterDescriptor(descriptor, data)
//    }

    override fun visitDeclarationDescriptor(descriptor: DeclarationDescriptor?, data: Unit?): MergerIR {
        return super.visitDeclarationDescriptor(descriptor, data)
    }
//
//    override fun visitVariableDescriptor(descriptor: VariableDescriptor?, data: Unit?): MergerIR {
//        return super.visitVariableDescriptor(descriptor, data)
//    }

    override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: Unit): MergedProperty {
        return MergedProperty(
                descriptor,
                isExpect,
                isActual)
    }
}

