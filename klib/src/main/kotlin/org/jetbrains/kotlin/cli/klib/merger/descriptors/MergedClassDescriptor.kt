package org.jetbrains.kotlin.cli.klib.merger.descriptors

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.AbstractClassDescriptor
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorBase
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.*

// TODO [ClassDescriptorBase] inheritance
class MergedClassDescriptor(
        storageManager: StorageManager,
        name: Name,
        private val containingDeclaration: DeclarationDescriptor,
        isExternal: Boolean,
        private val modality: Modality = Modality.FINAL,
        private var kind: ClassKind = ClassKind.CLASS,
        private var isInline: Boolean = false,
        private var visibility: Visibility = Visibilities.DEFAULT_VISIBILITY,
        private var isExpect: Boolean = false,
        private var isActual: Boolean = false,
        private val isData: Boolean = false,
        private val isCompanion: Boolean = false,
        private val isInner: Boolean = false,
        private val supertypes: Collection<KotlinType>,
        override val annotations: Annotations
) : ClassDescriptorBase(storageManager, containingDeclaration, name, SourceElement.NO_SOURCE, isExternal) {
    val typeConstructor = ClassTypeConstructorImpl(this, emptyList(), supertypes, storageManager)

    lateinit var memberScope: KlibMergerMemberScope
    lateinit var constructors: Set<ClassConstructorDescriptor>
    var primaryConstructor: ClassConstructorDescriptor? = null

    fun initialize(
            unsubstitutedMemberScope: KlibMergerMemberScope,
            constructors: Set<ClassConstructorDescriptor>,
            primaryConstructor: ClassConstructorDescriptor?
    ) {
        memberScope = unsubstitutedMemberScope
        this.constructors = constructors
        this.primaryConstructor = primaryConstructor
    }

    override fun getContainingDeclaration(): DeclarationDescriptor = containingDeclaration
    override fun getModality(): Modality = modality
    override fun getKind(): ClassKind = kind
    override fun getSource(): SourceElement = SourceElement.NO_SOURCE
    override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = primaryConstructor

    override fun getStaticScope(): MemberScope {
        // TODO find out wtf is staticScope
        return MemberScope.Empty
    }

    override fun isInline(): Boolean = isInline

    override fun getVisibility(): Visibility = visibility
    override fun isExpect(): Boolean = isExpect
    override fun getUnsubstitutedMemberScope(): MemberScope = memberScope
    override fun isData(): Boolean = isData
    override fun isCompanionObject(): Boolean = isCompanion
    override fun isActual(): Boolean = isActual

    override fun getSealedSubclasses(): Collection<ClassDescriptor> {
        return emptyList()
//        return TODO("mb empty but now it's fine to throw an error")
    }

    override fun getTypeConstructor(): TypeConstructor = typeConstructor

    override fun getConstructors(): Collection<ClassConstructorDescriptor> = constructors

    override fun isInner(): Boolean = isInner

    override fun getDeclaredTypeParameters(): List<TypeParameterDescriptor> {
        // TODO??
        return emptyList()
    }

    override fun getCompanionObjectDescriptor(): ClassDescriptor? {
        // TODO("not implemented")
        return null
    }
}
