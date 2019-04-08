package org.jetbrains.kotlin.cli.klib.merger.comparator

import org.jetbrains.kotlin.cli.klib.merger.ir.DescriptorProperties

sealed class ComparisonResult<out T>

data class CommonAndTargets<T : DescriptorProperties>(val firstTargetDescriptor: T,
                                                      val commonDescriptor: T,
                                                      val secondTargetDescriptor: T) : ComparisonResult<T>()

class Mismatched<T : DescriptorProperties> : ComparisonResult<T>()