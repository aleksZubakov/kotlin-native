package org.jetbrains.kotlin.cli.klib.merger.ir.visitors

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.klib.merger.descriptors.MergedClassDescriptor
import org.jetbrains.kotlin.cli.klib.merger.descriptors.MergerFragmentDescriptor
import org.jetbrains.kotlin.cli.klib.merger.ir.*
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

data class ClassifiersBuildContext(
        val modules: MutableMap<Name, WeirdTriple<ModuleDescriptorImpl>> = mutableMapOf(),
        val packages: MutableMap<FqName, WeirdTriple<MergerFragmentDescriptor>> = mutableMapOf(),
        val typeAliases: MutableMap<FqName, WeirdTriple<ClassifierDescriptor>> = mutableMapOf(),
        val classes: MutableMap<FqName, WeirdTriple<MergedClassDescriptor>> = mutableMapOf()
)

fun ClassifiersBuildContext.getClassifier(fqName: FqName): WeirdTriple<ClassifierDescriptor> {
    if (fqName in this.typeAliases) {
        return typeAliases[fqName]!!
    }

    if (fqName in this.classes) {
        return typeAliases[fqName]!!
    }

    error("Classifiers weren't resolved")
}

class BuildClassifiersVisitor(builtIns: KotlinBuiltIns, val buildContext: ClassifiersBuildContext) : MergedIRVisitor<Unit, WeirdTriple<DeclarationDescriptor>> {
    override fun visitModuleNode(moduleNode: ModuleNode, data: WeirdTriple<DeclarationDescriptor>) = with(moduleNode) {
        val fqName = firstTargetProperties?.name
                ?: commonTargetProperties?.name
                ?: secondTargetProperties?.name
                ?: error("One of them should be not null")

        val firstTargetDescriptor = firstTargetProperties.createDescriptor()
        val commonTargetDescriptor = commonTargetProperties.createDescriptor()
        val secondTargetDescriptor = secondTargetProperties.createDescriptor()
        val result = WeirdTriple(firstTargetDescriptor, commonTargetDescriptor, secondTargetDescriptor)

        buildContext.modules[fqName] = result

        for (member in moduleNode.members) {
            member.accept(this@BuildClassifiersVisitor, result)
        }
    }

    override fun visitPackageNode(packageNode: PackageNode, data: WeirdTriple<DeclarationDescriptor>) = with(packageNode) {
        val fqName = firstTargetProperties?.fqName
                ?: commonTargetProperties?.fqName
                ?: secondTargetProperties?.fqName
                ?: error("One of them should be not null")

        val firstTargetDescriptor = firstTargetProperties.createDescriptor(data.firstTargetDescriptor)
        val commonTargetDescriptor = commonTargetProperties.createDescriptor(data.commonTargetDescriptor)
        val secondTargetDescriptor = secondTargetProperties.createDescriptor(data.secondTargetDescriptor)
        val result = WeirdTriple(firstTargetDescriptor, commonTargetDescriptor, secondTargetDescriptor)

        buildContext.packages[fqName] = result
        for (member in packageNode.members) {
            member.accept(this@BuildClassifiersVisitor, result)
        }
    }

    override fun visitClassNode(classNode: ClassNode, data: WeirdTriple<DeclarationDescriptor>) = with(classNode) {
        val fqName = firstTargetProperties?.fqName
                ?: commonTargetProperties?.fqName
                ?: secondTargetProperties?.fqName
                ?: error("One of them should be not null")

        var isExpect = false
        var isActual = false
        if (commonTargetProperties != null) {
            isExpect = true
            isActual = true
        }

        val firstTargetDescriptor = firstTargetProperties.createDescriptor(data.firstTargetDescriptor, isActual = isActual)
        val commonTargetDescriptor = commonTargetProperties.createDescriptor(data.commonTargetDescriptor, isExpect = isExpect)
        val secondTargetDescriptor = secondTargetProperties.createDescriptor(data.secondTargetDescriptor, isActual = isActual)
        val result = WeirdTriple(firstTargetDescriptor, commonTargetDescriptor, secondTargetDescriptor)

        buildContext.classes[fqName] = result
        for (member in classNode.members) {
            member.accept(this@BuildClassifiersVisitor, result)
        }
    }

    override fun visitTypeAliasNode(typeAliasNode: TypeAliasNode, data: WeirdTriple<DeclarationDescriptor>) = with(typeAliasNode) {
        val fqName = firstTargetProperties?.fqName
                ?: commonTargetProperties?.fqName
                ?: secondTargetProperties?.fqName
                ?: error("One of them should be not null")

        var isExpect = false
        var isActual = false
        if (commonTargetProperties != null) {
            isExpect = true
            isActual = true
        }

        val result = WeirdTriple(
                firstTargetProperties.createDescriptor(data.firstTargetDescriptor, isActual = isActual),
                commonTargetProperties.createDescriptor(data.commonTargetDescriptor, isExpect = isExpect),
                secondTargetProperties.createDescriptor(data.secondTargetDescriptor, isActual = isActual)
        )
        buildContext.typeAliases[fqName] = result
    }

    override fun visitFunctionNode(functionNode: FunctionNode, data: WeirdTriple<DeclarationDescriptor>) {

    }

    override fun visitPropertyNode(propertyNode: PropertyNode, data: WeirdTriple<DeclarationDescriptor>) {

    }

    override fun visitEmptyNode(emptyNode: EmptyNode, data: WeirdTriple<DeclarationDescriptor>) {
        TODO("not implemented")
    }
}
