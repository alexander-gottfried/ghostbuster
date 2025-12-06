package org.example

@JvmInline
value class Method(val name: String)
data class Transition(val from: Int, val to: Int, val sym: Method)

class StateMachine(
    val initialState: Int,
    val transitions: Map<Int, List<Pair<Method, Int>>>,
    val methods: Set<Method>,
) {
    val isDeterministic = isDeterministic(transitions)

    companion object {
        fun fromTransitions(initialState: Int, transitions: List<Transition>): StateMachine {
            val methods = mutableSetOf<Method>()
            val transitionMap = transitions
                .groupBy(Transition::from)
                .mapValues {
                    it.value.map { (_, to, sym) ->
                        methods.add(sym)
                        Pair(sym, to)
                    }
                }

            return StateMachine(initialState, transitionMap, methods)
        }
    }
}

private fun isDeterministic(transitions: Map<Int, List<Pair<Method, Int>>>): Boolean {
    transitions.forEach { (_, list) ->
        val sorted = list.sortedBy { (sym, _) -> sym.name }
        sorted.mapIndexed { i, pair -> Pair(i, pair) }
            .drop(1)
            .forEach { (i, pair) ->
                val (sym, _) = pair
                val (prevSym, _) = sorted.get(i - 1)
                if (sym == prevSym) return false
            }
    }
    return true
}

fun subsetConstruction(
    stateMachine: StateMachine
): StateMachine {
    val oldTransitions = stateMachine.transitions
    val methods = stateMachine.methods
    val newInitState = epsilonClosure(oldTransitions, stateMachine.initialState)
    val newTransitions = mutableMapOf<List<Int>, MutableList<Pair<Method, List<Int>>>>()

    var index = 0
    val listStatesToInts = mutableMapOf<List<Int>, Int>()

    val stack = mutableListOf<List<Int>>(newInitState)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        methods.forEach { method ->
            val next = current
                .flatMap { nextStates(oldTransitions, it, method) }
                .flatMap { epsilonClosure(oldTransitions, it) }

            newTransitions.getOrPut(current, { mutableListOf() })
                .add(Pair(method, next))

            if (!newTransitions.containsKey(next)) {
                stack.add(next)

                listStatesToInts.put(next, index)
                index += 1
            }
        }
    }

    val resultInitState = listStatesToInts.get(newInitState)!!
    val resultTransitions = newTransitions
        .mapKeys { (from, _) -> listStatesToInts.get(from)!! }
        .mapValues { (_, pairs) ->
            pairs.map { (sym, next) -> Pair(sym, listStatesToInts.get(next)!!) }
        }
    val result = StateMachine(resultInitState, resultTransitions, methods)
    check(result.isDeterministic)
    return result
}

private fun nextStates(
    allTransitions: Map<Int, List<Pair<Method, Int>>>,
    fromThisState: Int,
    withThisSymbol: Method
): List<Int> {
    val transitions = allTransitions.get(fromThisState) ?: return emptyList()
    val closure = transitions
        .filter { (sym, _) -> sym.name == withThisSymbol.name }
        .map { (_, nextState) -> nextState }
        .plus(fromThisState)
    return closure
}

private val EPSILON = Method("ε")

private fun epsilonClosure(
    allTransitions: Map<Int, List<Pair<Method, Int>>>,
    fromThisState: Int
) = nextStates(allTransitions, fromThisState, EPSILON)

// Subset construction:
// need to know which states reachable from one state

// Gruppen's algorithm:
// need to know all transitions from one state
