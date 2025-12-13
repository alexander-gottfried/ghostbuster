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
        accepting: List<Int> = emptyList()
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

    fun withNewAccepting(thisOne: Int): StateMachine = this.withNewAccepting(listOf(thisOne))
    fun withNewAccepting(these: List<Int>): StateMachine {
        val states = these.flatMap { this.allStatesThatReach(it) }
        val transitions = this.transitions.filter { (from, to, _) ->
            from in states && to in states
        }

        return StateMachine(
            transitions,
            states.toList(),
            this.methods,
            this.init,
            these)
    }

    fun allStatesThatReach(thisState: Int): Set<Int> {
        val revmap = this.transitions.groupBy(Transition::to)
        val result = mutableSetOf<Int>()
        val stack = mutableListOf(thisState)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            result.add(current)
            val nexts = revmap.get(current) ?: continue
            val forstack = nexts.map(Transition::from).filter { it !in result }
            stack.addAll(forstack)
        }
        return result
    }

    companion object {
        fun makeFromTransitions(
            transitions: List<Transition>,
            init: Int,
            accepting: List<Int> = emptyList()
        ): StateMachine {
            val methods = mutableSetOf<String>()
            val values = mutableSetOf<Int>(init)
            transitions.mapTo(methods, Transition::method)
            transitions.mapTo(values, Transition::to)
            transitions.mapTo(values, Transition::from)
            values.addAll(accepting)
            return StateMachine(
                transitions,
                values.toList(),
                methods.toList(),
                init,
                accepting
            )
        }
    }
}
