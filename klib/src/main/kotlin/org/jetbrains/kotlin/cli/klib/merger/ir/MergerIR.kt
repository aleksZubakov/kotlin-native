package org.jetbrains.kotlin.cli.klib.merger.ir

import org.jetbrains.kotlin.cli.klib.merger.ir.visitors.MergedIRVisitor

sealed class Node(
        open val firstTargetProperties: DescriptorProperties?,
        open val commonTargetProperties: DescriptorProperties?,
        open val secondTargetProperties: DescriptorProperties?
) {
    constructor() : this(null, null, null)

    abstract fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U
}

sealed class NodeContainer(
        override val firstTargetProperties: DescriptorProperties?,
        override val commonTargetProperties: DescriptorProperties?,
        override val secondTargetProperties: DescriptorProperties?
) : Node(firstTargetProperties, commonTargetProperties, secondTargetProperties) {
    abstract val members: List<Node>
    abstract fun addMembers(members: List<Node>)
}

class ModuleNode(
        override val firstTargetProperties: ModuleDescriptorProperties?,
        override val commonTargetProperties: ModuleDescriptorProperties?,
        override val secondTargetProperties: ModuleDescriptorProperties?

) : NodeContainer(firstTargetProperties, commonTargetProperties, secondTargetProperties) {
    private val _members = mutableListOf<Node>()
    override val members: List<Node>
        get() = _members

    override fun addMembers(members: List<Node>) {
        _members.addAll(members)
    }

    override fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U {
        return visitor.visitModuleNode(this, data)
    }
}

class PackageNode(
        override val firstTargetProperties: PackageDescriptorProperties?,
        override val commonTargetProperties: PackageDescriptorProperties?,
        override val secondTargetProperties: PackageDescriptorProperties?
) : NodeContainer(firstTargetProperties, commonTargetProperties, secondTargetProperties) {
    private val _members = mutableListOf<Node>()
    override val members: List<Node>
        get() = _members

    override fun addMembers(members: List<Node>) {
        _members.addAll(members)
    }

    override fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U {
        return visitor.visitPackageNode(this, data)
    }
}

class ClassNode(
        override val firstTargetProperties: ClassDescriptorProperties?,
        override val commonTargetProperties: ClassDescriptorProperties?,
        override val secondTargetProperties: ClassDescriptorProperties?
) : NodeContainer(firstTargetProperties, commonTargetProperties, secondTargetProperties) {
    private val _members = mutableListOf<Node>()
    override val members: List<Node>
        get() = _members

    override fun addMembers(members: List<Node>) {
        _members.addAll(members)
    }

    override fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U {
        return visitor.visitClassNode(this, data)
    }
}

class FunctionNode(
        override val firstTargetProperties: FunctionDescriptorProperties?,
        override val commonTargetProperties: FunctionDescriptorProperties?,
        override val secondTargetProperties: FunctionDescriptorProperties?
) : Node(firstTargetProperties, commonTargetProperties, secondTargetProperties) {
    override fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U {
        return visitor.visitFunctionNode(this, data)
    }
}

class PropertyNode(
        override val firstTargetProperties: PropertyDescriptorProperties?,
        override val commonTargetProperties: PropertyDescriptorProperties?,
        override val secondTargetProperties: PropertyDescriptorProperties?
) : Node(firstTargetProperties, commonTargetProperties, secondTargetProperties) {
    override fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U {
        return visitor.visitPropertyNode(this, data)
    }
}

class TypeAliasNode(
        override val firstTargetProperties: TypeAliasProperties?,
        override val commonTargetProperties: TypeAliasProperties?,
        override val secondTargetProperties: TypeAliasProperties?
) : Node(firstTargetProperties, commonTargetProperties, secondTargetProperties) {
    override fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U {
        return visitor.visitTypeAliasNode(this, data)
    }
}

object EmptyNode : Node() {
    override fun <U, T> accept(visitor: MergedIRVisitor<U, T>, data: T): U {
        return visitor.visitEmptyNode(this, data)
    }
}