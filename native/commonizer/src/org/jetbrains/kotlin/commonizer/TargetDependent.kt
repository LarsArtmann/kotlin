/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.commonizer

sealed interface TargetDependent<T> : Iterable<T> {
    val size: Int get() = targets.size
    val targets: List<CommonizerTarget>
    fun indexOf(target: CommonizerTarget): Int = targets.indexOf(target)

    fun <R : Any> map(mapper: (target: CommonizerTarget, T) -> R): TargetDependent<R> {
        return TargetDependent(targets) { target ->
            mapper(target, get(target))
        }
    }

    override fun iterator(): Iterator<T> {
        return iterator { for (key in targets) yield(this@TargetDependent[key]) }
    }

    operator fun get(target: CommonizerTarget): T

    fun getOrNull(target: CommonizerTarget): T? {
        return if (target in targets) get(target) else null
    }
}

internal fun <T : Any> TargetDependent<T?>.filterNonNull(): TargetDependent<T> {
    val nonNullTargets = targets.filter { this[it] != null }
    return TargetDependent(nonNullTargets) { target -> this@filterNonNull[target] ?: throw NullPointerException() }
}

internal fun <T> TargetDependent<T>.toMap(): Map<CommonizerTarget, T> {
    return mutableMapOf<CommonizerTarget, T>().apply {
        for (key in keys) {
            put(key, this@toMap[key])
        }
    }
}

internal fun <T> Map<out CommonizerTarget, T>.toTargetDependent(): TargetDependent<T> {
    return TargetDependent(toMap())
}

internal fun <T, R> TargetDependent<T>.mapValue(mapper: (T) -> R): TargetDependent<R> {
    return TargetDependent(targets) { target -> this@mapValue.get(target).let(mapper) }
}

internal fun <T, R> TargetDependent<T>.mapTargets(mapper: (CommonizerTarget) -> R): TargetDependent<R> {
    return TargetDependent(targets) { target -> mapper(target) }
}


internal fun <T> TargetDependent(map: Map<out CommonizerTarget, T>): TargetDependent<T> {
    return MapBasedTargetDependent(map.toMap())
}

internal fun <T> TargetDependent(keys: Iterable<CommonizerTarget>, factory: (target: CommonizerTarget) -> T): TargetDependent<T> {
    return FactoryBasedTargetDependent(keys.toList(), factory)
}

private class MapBasedTargetDependent<T>(private val map: Map<CommonizerTarget, T>) : TargetDependent<T> {
    override val targets: List<CommonizerTarget> = map.keys.toList()
    override fun get(target: CommonizerTarget): T = map.getValue(target)
}

/**
 * Not thread safe!
 */
private class FactoryBasedTargetDependent<T>(
    override val targets: List<CommonizerTarget>,
    private var factory: ((target: CommonizerTarget) -> T)?
) : TargetDependent<T> {

    private object Uninitialized

    private val values: Array<Any?> = Array(targets.size) { Uninitialized }

    @Suppress("UNCHECKED_CAST")
    override fun get(target: CommonizerTarget): T {
        val indexOfTarget = indexOf(target)
        if (indexOfTarget < 0) throw NoSuchElementException("Missing target $target")
        val storedValue = values[indexOfTarget]
        if (storedValue == Uninitialized) {
            val producedValue = factory?.invoke(target)
            values[indexOfTarget] = producedValue

            /* All values initialized. Factory can be released */
            if (values.none { it === Uninitialized }) {
                factory = null
            }
            return producedValue as T
        }

        return storedValue as T
    }
}
