package org.example

data class Transition(val from: Int, val to: Int, val method: String)
data class StateMachine(
    val transitions: List<Transition>,
    val values: List<Int>,
    val methods: List<String>,
    val init: Int,
    val accepting: List<Int>,
) {
    val map: Map<Int, Map<String, Int>> = run {
        val result = mutableMapOf<Int, MutableMap<String, Int>>()
        this.transitions.forEach { (from, to, method) ->
            val here = result.getOrPut(from, { mutableMapOf<String, Int>() })
            here.put(method, to)
        }
        result
    }

    fun makeWithNormalizedStates(
        transitions: List<Transition>,
        values: List<Int>,
        methods: List<String>,
        init: Int,
        accepting: List<Int>
    ): StateMachine {
        val newStateMap = values.mapIndexed { i, state -> Pair(state, i) }.toMap()
        val new = { s: Int -> newStateMap.get(s)!! }
        return StateMachine(
            transitions.map { (from, to, method) ->
                Transition(new(from), new(to), method)
            },
            values.map(new),
            methods,
            new(init),
            accepting.map(new)
        )
    }

    fun withNewAccepting(these: List<Int>): StateMachine = StateMachine(
        this.transitions,
        this.values,
        this.methods,
        this.init,
        these
    )

    fun withNewAccepting(thisOne: Int): StateMachine = StateMachine(
        this.transitions,
        this.values,
        this.methods,
        this.init,
        listOf(thisOne)
    )
}

/*
fun StateMachine.subsetConstruction(): StateMachine {
    val newInit = this.epsilonClosure(this.init)
    val newTransitions = mutableMapOf<Subset, MutableList<Pair<String, Subset>>>()

    val stack = mutableListOf<Subset>(newInit)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        this.methods.forEach { method ->
            val next 
            val next = current
                .flatMap { this.nextStates(it, method) }
                .flatMap { this.epsilonClosure(it) }

            newTransitions.getOrPut(current, { mutableListOf() })
                .add(Pair(method, next))

            if (!newTransitions.containsKey(next)) {
                stack.add(next)
            }
        }
    }

    val acceptingStates = listStatesToInts.keys.filter { state ->
        state.any { this.accepting.contains(it) }
    }
    .map { listStatesToInts.get(it)!! }
    val resultInitState = listStatesToInts.get(newInitState)!!
    val resultTransitions = newTransitions.flatMap { (from, toPairs) ->
        toPairs.map { (method, to) ->
            val fromInt = listStatesToInts.get(from)!!
            val toInt = listStatesToInts.get(to)!!
            Transition(fromInt, toInt, method)
        }
    }

    val result = StateMachine(
        resultTransitions,
        this.values,
        this.methods,
        resultInitState,
        acceptingStates
    )
    //check(result.isDeterministic())
    return result
}

private data class Subset(var value: Int): Iterable<Int> {
    companion object {
        fun empty() = Subset(0)
    }

    fun set(n: Int) {
        check(n >= 0)
        this.value = this.value or (1 shl n)
    }

    fun unset(n: Int) {
        check(n >= 0)
        this.value = this.value and inv(1 shl n)
    }

    fun intersectsWith(other: Subset) = (this.value xor other.value) != 0

    fun setIndeces(): List<Int> {
        (0..32).mapIndexedNotNull { i, n ->
            if ((1 shl n) and this.value != 0) i else null
        }
        return result
    }

    fun firstSetIndexFrom(start: Int): Int {
        (n..31).indexOfFirst { (1 shl it) and this.value > 0 }
    }

    override operator fun iterator() = Iterator<Int>() {
        private var i = this.firstSetIndexFrom(0)

        override operator fun next(): Int {
            if (i < 0) throw NoSuchElementException()
            val r = i
            i = this.firstSetIndexFrom(i + 1)
            return r
        }

        override operator fun hasNext(): Boolean = i > -1
    }
}

/*
private fun StateMachine.isDeterministic(): Boolean {
    this.map.forEach { (_, list) ->
        val sorted = list.sortedBy { (sym, _) -> sym.name }
        val iterhere = sorted.mapIndexed { i, pair -> Pair(i, pair) }.drop(1)
        for ((i, pair) in iterhere) {
            val (sym, _) = pair
            val (prevSym, _) = sorted.get(i - 1)
            if (sym == prevSym) return false
        }
    }
    return true
}
*/

private fun StateMachine.nextStates(
    fromThisState: Int,
    withThisSymbol: String
): Subset {
    val transitions = this.map.get(fromThisState) ?: return Subset.empty()
    val subset = Subset.empty()
    transitions.forEach { (method, nextState) ->
            if (method == withThisSymbol)
                subset.set(nextState)
        }
    return subset
}

private fun StateMachine.epsilonClosure(
    fromThisState: Int
) {
    val r = this.nextStates(fromThisState, EPSILON)
    r.set(fromThisState)
    return r
}
*/

fun StateMachine.tail(k: Int): StateMachine {
    check(k > 0) // TODO change to >= after adding 0 case
    check(this.accepting.isNotEmpty())

    val backwards = this.transitions.groupBy(Transition::to)

    val inside = this.accepting.toMutableSet()
    val edge = mutableSetOf<Int>()
    val keepTransitions = mutableListOf<Transition>()

    this.accepting.forEach { it.tailOneStep(k, inside, edge, keepTransitions, backwards) }

    val newInit = this.values.uniqueFromAll()

    edge.forEach {
        keepTransitions.add(Transition(newInit, it, EPSILON))
    }

    return StateMachine(keepTransitions, inside.toList(), this.methods, newInit, this.accepting)
}

private fun List<Int>.uniqueFromAll() = this.map { it - 1 }.min()

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

fun indecesOfOverlappers(machines: List<StateMachine>): List<Int> {
    val searchPack = SearchPack.makeInit(machines)
    val seenAlready = mutableSetOf<List<Int>>()
    val result = mutableSetOf<Int>()
    overlappersDepthFirstSearch(searchPack, seenAlready, result)
    return result.toList()
}

private fun overlappersDepthFirstSearch(
    searchPack: SearchPack,
    seenAlready: MutableSet<List<Int>>,
    collectInto: MutableSet<Int>,
) {
    val (machines, currentStateTuple, _) = searchPack

    if (seenAlready.contains(currentStateTuple)) return
    seenAlready.add(currentStateTuple)

    val theseAccept = searchPack.acceptingIndeces()
    collectInto.addAll(theseAccept)

    for (method in searchPack.methods) {
        val nextSearchPack = searchPack.afterTransition(method)
        overlappersDepthFirstSearch(nextSearchPack, seenAlready, collectInto)
    }
}

private data class SearchPack(
    val machines: List<StateMachine>,
    val currentStateTuple: List<Int>,
    val errorState: Int,
) {
    // TODO check that all machines' methods are same
    val methods = machines.first().methods

    companion object {
        fun makeInit(machines: List<StateMachine>): SearchPack {
            check(machines.isNotEmpty())
            val currentStateTuple = machines.map(StateMachine::init)
            val errorState = machines.map { it.values.uniqueFromAll() }.min()
            return SearchPack(machines, currentStateTuple, errorState)
        }
    }

    fun acceptingIndeces(): List<Int> {
        val result = mutableListOf<Int>()
        this.currentStateTuple.mapIndexed { i, state ->
            val isAccepting = this.machines.get(i)!!.accepting.contains(state)
            if (isAccepting) result.add(i)
        }
        return result
    }

    fun afterTransition(method: String): SearchPack {
        val newStateTuple = this.currentStateTuple.mapIndexed { i, state ->
            if (state == this.errorState) {
                this.errorState
            } else {
                val nextStates = this.machines.get(i)!!.map.get(state)!!
                nextStates.getOrDefault(method, this.errorState)
            }
        }
        return SearchPack(this.machines, newStateTuple, this.errorState)
    }
}
