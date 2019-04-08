package org.jetbrains.kotlin.cli.klib.merger.ir

import org.jetbrains.kotlin.backend.konan.descriptors.getPackageFragments
import org.jetbrains.kotlin.cli.klib.merger.comparator.CommonAndTargets
import org.jetbrains.kotlin.cli.klib.merger.comparator.ComparisonResult
import org.jetbrains.kotlin.cli.klib.merger.comparator.FullDescriptorComparator
import org.jetbrains.kotlin.cli.klib.merger.descriptors.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.DeclarationDescriptorVisitorEmptyBodies
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.storage.LockBasedStorageManager

fun List<DeclarationDescriptor>.transformIntoSingleTargetNodes(singleTargetVisitor: SingleTargetDeclarationToMergeIRVisitor, isFirstTarget: Boolean) =
        this.map { it.accept(singleTargetVisitor, isFirstTarget) }

fun ClassDescriptor.getDescriptors() = this.unsubstitutedMemberScope.getDescriptorsFiltered { true }.toList()
fun PackageViewDescriptor.getDescriptors() = memberScope.getDescriptorsFiltered { true }
        .filter {
            it !is PackageViewDescriptor // TODO
        }

class MergeVisitor : DeclarationDescriptorVisitorEmptyBodies<Node, DeclarationDescriptor>() {
    val fullDescriptorComparator = FullDescriptorComparator(LockBasedStorageManager("comparator"))
    val firstTargetDeclarationToMergeIRVisitor = SingleTargetDeclarationToMergeIRVisitor()

    private fun visitMembers(firstTargetMembers: List<DeclarationDescriptor>, secondTargetMembers: List<DeclarationDescriptor>): List<Node> {
        val passed = mutableSetOf<DeclarationDescriptor>()
        val result = mutableListOf<Node>()
        for (firstTargetMember in firstTargetMembers) {
            for (secondTargetMember in secondTargetMembers) {
                val node = firstTargetMember.accept(this, secondTargetMember)
                if (node.commonTargetProperties != null) {
                    result.add(node)
                    passed.add(firstTargetMember)
                    passed.add(secondTargetMember)
                }
            }
        }

        result.addAll(firstTargetMembers.filter { it !in passed }.transformIntoSingleTargetNodes(firstTargetDeclarationToMergeIRVisitor, isFirstTarget = true))
        result.addAll(secondTargetMembers.filter { it !in passed }.transformIntoSingleTargetNodes(firstTargetDeclarationToMergeIRVisitor, isFirstTarget = false))

        return result
    }

    private val excludeNames = Regex("(objc)?[Kk]niBridge\\d+")
    override fun visitFunctionDescriptor(firstTargetFunction: FunctionDescriptor, secondTargetFunction: DeclarationDescriptor): Node {
        if (secondTargetFunction !is FunctionDescriptor) {
            return EmptyNode
        }
        if (excludeNames.matches(firstTargetFunction.name.toString()) || excludeNames.matches(secondTargetFunction.name.toString())) {
            return EmptyNode
        }

//        if (firstTargetFunction.name.toString().contains())

        return fullDescriptorComparator.compareFunctionDescriptors(firstTargetFunction, secondTargetFunction).buildNode(::FunctionNode)
    }


    override fun visitModuleDeclaration(firstTargetModule: ModuleDescriptor, secondTargetModule: DeclarationDescriptor): Node {
        if (firstTargetModule !is ModuleDescriptorImpl || secondTargetModule !is ModuleDescriptorImpl) {
            return EmptyNode
        }

        val result = fullDescriptorComparator.compareModuleDescriptors(firstTargetModule, secondTargetModule)
        return result.buildNode(::ModuleNode).applyIfContainer {
            addMembers(visitMembers(getPackages(firstTargetModule), getPackages(secondTargetModule)))
        }
    }

    override fun visitClassDescriptor(firstTargetClass: ClassDescriptor, secondTargetClass: DeclarationDescriptor): Node {
        if (secondTargetClass !is ClassDescriptor) {
            return EmptyNode
        }

        val result = fullDescriptorComparator.compareClassDescriptors(firstTargetClass, secondTargetClass)
        return result.buildNode(::ClassNode).applyIfContainer {
            addMembers(visitMembers(firstTargetClass.getDescriptors(), secondTargetClass.getDescriptors()))
        }
    }

    override fun visitPackageViewDescriptor(firstTargetPackage: PackageViewDescriptor, secondTargetPackage: DeclarationDescriptor): Node {
        if (secondTargetPackage !is PackageViewDescriptor) {
            return EmptyNode
        }

        val result = fullDescriptorComparator.comparePackageViewDescriptors(firstTargetPackage, secondTargetPackage)
        return result.buildNode(::PackageNode).applyIfContainer {
            addMembers(visitMembers(firstTargetPackage.getDescriptors(), secondTargetPackage.getDescriptors()))
        }
    }

    override fun visitTypeAliasDescriptor(firstTargetTypeAlias: TypeAliasDescriptor, secondTargetTypeAlias: DeclarationDescriptor): Node {
        if (secondTargetTypeAlias !is TypeAliasDescriptor) {
            return EmptyNode
        }

        return fullDescriptorComparator.compareTypeAliases(firstTargetTypeAlias, secondTargetTypeAlias).buildNode(::TypeAliasNode)
    }

    override fun visitPropertyDescriptor(firstTargetProperty: PropertyDescriptor, secondTargetProperty: DeclarationDescriptor): Node {
        if (secondTargetProperty !is PropertyDescriptor) {
            return EmptyNode
        }

        return fullDescriptorComparator.comparePropertyDescriptors(firstTargetProperty, secondTargetProperty).buildNode(::PropertyNode)
    }

    private fun <T : DescriptorProperties, G : Node> ComparisonResult<T>.buildNode(konstructor: (T, T, T) -> G): Node {
        if (this !is CommonAndTargets<T>) {
            return EmptyNode
        }

        return with(this) {
            konstructor(firstTargetDescriptor, commonDescriptor, secondTargetDescriptor)
        }
    }

    public inline fun Node.applyIfContainer(block: NodeContainer.() -> Unit): Node {
        if (this is NodeContainer) {
            block()
        }
        return this
    }
}

class SingleTargetDeclarationToMergeIRVisitor : DeclarationDescriptorVisitorEmptyBodies<Node, Boolean>() {
    private val propertiesFactory = DescriptorPropertiesFactory()

    private fun <T : DescriptorProperties, G : Node> T.createNodeForTarget(isFirstTarget: Boolean, konstructor: (T?, T?, T?) -> G): G =
            if (isFirstTarget) {
                konstructor(this, null, null)
            } else {
                konstructor(null, null, this)
            }

    override fun visitModuleDeclaration(descriptor: ModuleDescriptor, isFirstTarget: Boolean): ModuleNode {
        // TODO do the same things but with ModuleDescriptor
        descriptor as ModuleDescriptorImpl
        val members = descriptor.getPackageFragments().map {
            it.accept(this, isFirstTarget)
        }

        return propertiesFactory.createModuleProperties(descriptor).createNodeForTarget(isFirstTarget, ::ModuleNode).apply {
            addMembers(members)
        }
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor, isFirstTarget: Boolean): ClassNode {
        val members = descriptor.getDescriptors().map {
            it.accept(this, isFirstTarget)
        }

        val mergedClass = propertiesFactory.createClassProperties(descriptor)
                .apply { addSupertypes(descriptor.typeConstructor.supertypes) }
        return mergedClass.createNodeForTarget(isFirstTarget, ::ClassNode).apply {
            addMembers(members)
        }
    }


    override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, isFirstTarget: Boolean): PackageNode {
        val members = descriptor.getDescriptors().map {
            it.accept(this, isFirstTarget)
        }

        return propertiesFactory.createPackageProperties(descriptor).createNodeForTarget(isFirstTarget, ::PackageNode).apply {
            addMembers(members)
        }
    }

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, isFirstTarget: Boolean): Node =
            FunctionDescriptorProperties(descriptor).createNodeForTarget(isFirstTarget, ::FunctionNode)

    override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, isFirstTarget: Boolean): TypeAliasNode =
            propertiesFactory.createTypeAliasProperties(descriptor).also {
                it.addSupertypes(descriptor.typeConstructor.supertypes)
            }.createNodeForTarget(isFirstTarget, ::TypeAliasNode)

    override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, isFirstTarget: Boolean): PropertyNode =
            propertiesFactory.createPropertyProperties(descriptor).createNodeForTarget(isFirstTarget, ::PropertyNode)
}
