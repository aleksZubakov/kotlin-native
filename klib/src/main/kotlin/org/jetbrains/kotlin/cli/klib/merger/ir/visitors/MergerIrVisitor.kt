package org.jetbrains.kotlin.cli.klib.merger.ir.visitors

import org.jetbrains.kotlin.cli.klib.merger.ir.*

interface MergedIRVisitor<U, T> {
    fun visitModuleNode(moduleNode: ModuleNode, data: T): U
    fun visitPackageNode(packageNode: PackageNode, data: T): U
    fun visitClassNode(classNode: ClassNode, data: T): U
    fun visitTypeAliasNode(typeAliasNode: TypeAliasNode, data: T): U
    fun visitFunctionNode(functionNode: FunctionNode, data: T): U
    fun visitPropertyNode(propertyNode: PropertyNode, data: T): U
    fun visitEmptyNode(emptyNode: EmptyNode, data: T): U
}