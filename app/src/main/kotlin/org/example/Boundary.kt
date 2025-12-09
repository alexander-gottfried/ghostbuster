package org.example

enum class Boundary {
    BOUND, UNBOUND, UNRELATED
}

fun Boundary.isBound(): Boolean = this == Boundary.BOUND
fun Boundary.isUnbound(): Boolean = this == Boundary.UNBOUND
fun Boundary.isUnrelated(): Boolean = this == Boundary.UNRELATED

infix fun Boundary.and(other: Boundary): Boundary = when {
    this.isUnbound() || other.isUnbound() -> Boundary.UNBOUND
    this.isBound() || other.isBound() -> Boundary.BOUND
    else -> Boundary.UNRELATED
}

infix fun Boundary.impl(other: Boundary): Boundary = when {
    this.isUnbound() || other.isUnbound() -> Boundary.UNBOUND
    this.isBound() -> Boundary.BOUND
    this.isUnrelated() && other.isBound() -> Boundary.UNBOUND
    else -> Boundary.UNRELATED
}

infix fun Boundary.or(other: Boundary): Boundary = when {
    this.isUnbound() || other.isUnbound() -> Boundary.UNBOUND
    this.isBound() && other.isBound() -> Boundary.BOUND
    this.isBound() && other.isUnrelated() -> Boundary.UNBOUND
    this.isUnrelated() && other.isBound() -> Boundary.UNBOUND
    else -> Boundary.UNRELATED
}

fun Boundary.neg(): Boundary = when {
    this.isUnrelated() -> this
    else -> Boundary.UNBOUND
}
