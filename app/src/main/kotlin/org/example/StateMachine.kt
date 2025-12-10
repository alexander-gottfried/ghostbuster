package org.example

data class StateMachine(
    val transitions: List<Transition>,
    val values: List<Int>,
    val init: Int,
    val accepting: List<Int>,
)

fun StateMachine.tail(k: Int): StateMachine {
    check(k > 0) // TODO change to >= after adding 0 case
    check(this.accepting.isNotEmpty())

    val backwards = this.transitions.groupBy(Transition::to)

    val inside = this.accepting.toMutableSet()
    val edge = mutableSetOf<Int>()
    val keepTransitions = mutableListOf<Transition>()

    this.accepting.forEach { it.tailOneStep(k, inside, edge, keepTransitions, backwards) }

    val newInit = this.values.map { it - 1}.min()

    edge.forEach {
        keepTransitions.add(Transition(newInit, it, EPSILON))
    }

    return StateMachine(keepTransitions, inside.toList(), newInit, this.accepting)
}

private fun Int.tailOneStep(
    k: Int,
    inside: MutableSet<Int>,
    edge: MutableSet<Int>,
    collectInto: MutableList<Transition>,
    backwards: Map<Int, List<Transition>>,
) {
    inside.add(this)
    if (k < 1) {
        edge.add(this)
        return
    }

    val incomingEdges = backwards.get(this) ?: return
    collectInto.addAll(incomingEdges)
    incomingEdges.forEach { (from, _, _) ->
        if (from !in inside) from.tailOneStep(k - 1, inside, edge, collectInto, backwards)
    }
}
